#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <libusb.h>
#include <libuvc/libuvc.h>
#include <libuvc/libuvc_internal.h>

#include "blit.h"
#include "stream_mode.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <vector>

#define TAG "UvcCamera"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" uvc_frame_desc_t *uvc_find_frame_desc(uvc_device_handle_t *devh, uint16_t format_id, uint16_t frame_id);

namespace {

// Native state behind the Kotlin `UvcCamera`. The libuvc frame callback runs on a
// libuvc thread, so `mutex` guards `window` and `rgb` against start/stop/close.
struct Camera {
    uvc_context_t *ctx = nullptr;
    uvc_device_handle_t *devh = nullptr;
    ANativeWindow *window = nullptr;
    uvc_frame_t *rgb = nullptr;
    int failedFrames = 0;
    long frames = 0;
    std::mutex mutex;
};

void throwIOException(JNIEnv *env, const char *what, uvc_error_t err) {
    char msg[160];
    snprintf(msg, sizeof(msg), "%s: %s (%d)", what, uvc_strerror(err), err);
    env->ThrowNew(env->FindClass("java/io/IOException"), msg);
}

uvc_error_t getStreamCtrl(uvc_device_handle_t *devh, uvc_stream_ctrl_t *ctrl,
                          int width, int height, int fps, int preferredFormat) {
    // The preferred format (if any) is tried first, then the default order MJPEG, YUYV, any.
    std::vector<enum uvc_frame_format> formats;
    if (preferredFormat == polaroid::kMjpeg) formats.push_back(UVC_FRAME_FORMAT_MJPEG);
    if (preferredFormat == polaroid::kYuyv) formats.push_back(UVC_FRAME_FORMAT_YUYV);
    for (auto fmt : {UVC_FRAME_FORMAT_MJPEG, UVC_FRAME_FORMAT_YUYV, UVC_FRAME_FORMAT_ANY}) {
        if (std::find(formats.begin(), formats.end(), fmt) == formats.end()) formats.push_back(fmt);
    }

    // 1. Try exact format/size/fps
    for (auto fmt : formats) {
        if (uvc_get_stream_ctrl_format_size(devh, ctrl, fmt, width, height, fps) == UVC_SUCCESS) {
            return UVC_SUCCESS;
        }
    }

    // 2. Try exact format/size with fps = 0 (accept first available rate)
    for (auto fmt : formats) {
        if (uvc_get_stream_ctrl_format_size(devh, ctrl, fmt, width, height, 0) == UVC_SUCCESS) {
            return UVC_SUCCESS;
        }
    }

    // 3. Try the sizes the camera supports, closest to the request first
    uvc_streaming_interface_t *stream_if = nullptr;
    uvc_format_desc_t *format = nullptr;
    uvc_frame_desc_t *frame = nullptr;
    std::vector<polaroid::FrameSize> supported;
    DL_FOREACH(devh->info->stream_ifs, stream_if) {
        DL_FOREACH(stream_if->format_descs, format) {
            DL_FOREACH(format->frame_descs, frame) {
                supported.push_back({frame->wWidth, frame->wHeight});
            }
        }
    }

    for (const auto &size : polaroid::orderBySimilarity(supported, width, height)) {
        for (auto fmt : formats) {
            if (uvc_get_stream_ctrl_format_size(devh, ctrl, fmt, size.width, size.height, 0) == UVC_SUCCESS) {
                return UVC_SUCCESS;
            }
        }
    }

    return UVC_ERROR_INVALID_MODE;
}

int classifyFormat(const uvc_format_desc_t *format) {
    if (format->bDescriptorSubtype == UVC_VS_FORMAT_MJPEG) return polaroid::kMjpeg;
    if (format->bDescriptorSubtype == UVC_VS_FORMAT_UNCOMPRESSED &&
        memcmp(format->guidFormat, "YUY2", 4) == 0) {
        return polaroid::kYuyv;
    }
    return polaroid::kOtherFormat;
}

const uvc_format_desc_t *findFormat(uvc_device_handle_t *devh, uint8_t formatIndex) {
    uvc_streaming_interface_t *stream_if = nullptr;
    uvc_format_desc_t *format = nullptr;
    DL_FOREACH(devh->info->stream_ifs, stream_if) {
        DL_FOREACH(stream_if->format_descs, format) {
            if (format->bFormatIndex == formatIndex) return format;
        }
    }
    return nullptr;
}

// Converts the incoming frame (YUYV/MJPEG/...) to RGB and blits it to the window.
void onFrame(uvc_frame_t *frame, void *user) {
    auto *cam = static_cast<Camera *>(user);
    std::lock_guard<std::mutex> lock(cam->mutex);
    if (!cam->window) {
        if (cam->failedFrames++ < 5) {
            LOGE("Frame arrived but no window");
        }
        return;
    }

    if (!cam->rgb || cam->rgb->width != frame->width || cam->rgb->height != frame->height) {
        if (cam->rgb) uvc_free_frame(cam->rgb);
        cam->rgb = uvc_allocate_frame(frame->width * frame->height * 3);
    }

    if (!cam->rgb) return;
    uvc_error_t err = uvc_any2rgb(frame, cam->rgb);
    if (err != UVC_SUCCESS) {
        // Corrupt frames are common right after a stream starts; log only a few.
        if (cam->failedFrames++ < 5) {
            LOGE("Dropping frame: %s", uvc_strerror(err));
        }
        return;
    }

    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(cam->window, &buf, nullptr) != 0) {
        if (cam->failedFrames++ < 5) {
            LOGE("ANativeWindow_lock failed");
        }
        return;
    }

    polaroid::blitRgbToRgbx(static_cast<const uint8_t *>(cam->rgb->data), cam->rgb->step,
                            cam->rgb->width, cam->rgb->height, static_cast<uint8_t *>(buf.bits),
                            static_cast<size_t>(buf.stride) * 4, buf.width, buf.height);
    ANativeWindow_unlockAndPost(cam->window);
}

void releaseWindow(Camera *cam) {
    std::lock_guard<std::mutex> lock(cam->mutex);
    if (cam->window) ANativeWindow_release(cam->window);
    cam->window = nullptr;
    if (cam->rgb) uvc_free_frame(cam->rgb);
    cam->rgb = nullptr;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_mikimn_libpolaroid_NativeUvc_open(JNIEnv *env, jobject, jint fd) {
    // Android forbids enumerating /dev/bus/usb; we only wrap the fd from UsbManager.
    libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);

    auto *cam = new Camera();
    uvc_error_t err = uvc_init(&cam->ctx, nullptr);
    if (err == UVC_SUCCESS) err = uvc_wrap(fd, cam->ctx, &cam->devh);
    if (err != UVC_SUCCESS) {
        if (cam->ctx) uvc_exit(cam->ctx);
        delete cam;
        throwIOException(env, "Failed to open UVC device", err);
        return 0;
    }
    return reinterpret_cast<jlong>(cam);
}

// Returns {format, width, height, fps} for every mode the camera advertises. A frame size with a
// continuous interval range is reported at its default and its fastest rate.
JNIEXPORT jintArray JNICALL
Java_com_mikimn_libpolaroid_NativeUvc_listModes(JNIEnv *env, jobject, jlong handle) {
    auto *cam = reinterpret_cast<Camera *>(handle);
    std::vector<jint> modes;
    auto add = [&](int format, const uvc_frame_desc_t *frame, uint32_t interval) {
        const int fps = polaroid::intervalToFps(interval);
        if (fps <= 0) return;
        modes.insert(modes.end(), {format, frame->wWidth, frame->wHeight, fps});
    };
    uvc_streaming_interface_t *stream_if = nullptr;
    uvc_format_desc_t *format = nullptr;
    uvc_frame_desc_t *frame = nullptr;
    DL_FOREACH(cam->devh->info->stream_ifs, stream_if) {
        DL_FOREACH(stream_if->format_descs, format) {
            const int code = classifyFormat(format);
            DL_FOREACH(format->frame_descs, frame) {
                if (frame->intervals != nullptr) {
                    for (const uint32_t *i = frame->intervals; *i != 0; i++) add(code, frame, *i);
                } else {
                    add(code, frame, frame->dwDefaultFrameInterval);
                    if (frame->dwMinFrameInterval != frame->dwDefaultFrameInterval) {
                        add(code, frame, frame->dwMinFrameInterval);
                    }
                }
            }
        }
    }
    jintArray result = env->NewIntArray(static_cast<jsize>(modes.size()));
    if (result == nullptr) return nullptr;  // OutOfMemoryError is already pending
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(modes.size()), modes.data());
    return result;
}

// Returns the negotiated mode as {format, width, height, fps}.
JNIEXPORT jintArray JNICALL
Java_com_mikimn_libpolaroid_NativeUvc_start(JNIEnv *env, jobject, jlong handle,
                                                  jobject surface, jint width, jint height,
                                                  jint fps, jint preferredFormat) {
    auto *cam = reinterpret_cast<Camera *>(handle);
    uvc_stop_streaming(cam->devh);
    releaseWindow(cam);

    uvc_stream_ctrl_t ctrl;
    uvc_error_t err = getStreamCtrl(cam->devh, &ctrl, width, height, fps, preferredFormat);
    if (err != UVC_SUCCESS) {
        LOGE("No usable stream mode for %dx%d@%d", width, height, fps);
        throwIOException(env, "Unsupported stream mode", err);
        return nullptr;
    }

    uvc_frame_desc_t *frame_desc = uvc_find_frame_desc(cam->devh, ctrl.bFormatIndex, ctrl.bFrameIndex);
    if (frame_desc) {
        width = frame_desc->wWidth;
        height = frame_desc->wHeight;
    }

    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        env->ThrowNew(env->FindClass("java/io/IOException"), "Surface is not valid");
        return nullptr;
    }
    cam->failedFrames = 0;
    ANativeWindow_setBuffersGeometry(window, width, height, WINDOW_FORMAT_RGBX_8888);
    {
        std::lock_guard<std::mutex> lock(cam->mutex);
        cam->window = window;
    }

    err = uvc_start_streaming(cam->devh, &ctrl, onFrame, cam, 0);
    if (err != UVC_SUCCESS) {
        releaseWindow(cam);
        LOGE("uvc_start_streaming failed: %s", uvc_strerror(err));
        throwIOException(env, "Failed to start streaming", err);
        return nullptr;
    }
    const uvc_format_desc_t *negotiated = findFormat(cam->devh, ctrl.bFormatIndex);
    const jint mode[4] = {negotiated ? classifyFormat(negotiated) : polaroid::kOtherFormat, width, height,
                          polaroid::intervalToFps(ctrl.dwFrameInterval)};
    jintArray result = env->NewIntArray(4);
    if (result == nullptr) return nullptr;  // OutOfMemoryError is already pending
    env->SetIntArrayRegion(result, 0, 4, mode);
    return result;
}

JNIEXPORT void JNICALL
Java_com_mikimn_libpolaroid_NativeUvc_stop(JNIEnv *, jobject, jlong handle) {
    auto *cam = reinterpret_cast<Camera *>(handle);
    uvc_stop_streaming(cam->devh);  // joins the callback thread; must not hold the mutex
    releaseWindow(cam);
}

JNIEXPORT void JNICALL
Java_com_mikimn_libpolaroid_NativeUvc_close(JNIEnv *, jobject, jlong handle) {
    auto *cam = reinterpret_cast<Camera *>(handle);
    uvc_stop_streaming(cam->devh);
    releaseWindow(cam);
    uvc_close(cam->devh);
    uvc_exit(cam->ctx);
    delete cam;
}

}  // extern "C"
