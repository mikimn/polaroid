#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <libusb.h>
#include <libuvc/libuvc.h>
#include <libuvc/libuvc_internal.h>

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
                          int width, int height, int fps) {
    const enum uvc_frame_format formats[] = {
        UVC_FRAME_FORMAT_MJPEG,
        UVC_FRAME_FORMAT_YUYV,
        UVC_FRAME_FORMAT_ANY,
    };

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

    // 3. Find best matching supported frame descriptor
    uvc_streaming_interface_t *stream_if = nullptr;
    uvc_format_desc_t *format = nullptr;
    uvc_frame_desc_t *frame = nullptr;

    struct FrameCandidate {
        uvc_frame_desc_t *frame;
        int diff;
    };

    std::vector<FrameCandidate> candidates;

    DL_FOREACH(devh->info->stream_ifs, stream_if) {
        DL_FOREACH(stream_if->format_descs, format) {
            DL_FOREACH(format->frame_descs, frame) {
                int diff = std::abs(static_cast<int>(frame->wWidth) - width) +
                           std::abs(static_cast<int>(frame->wHeight) - height);
                candidates.push_back({frame, diff});
            }
        }
    }

    std::sort(candidates.begin(), candidates.end(), [](const FrameCandidate &a, const FrameCandidate &b) {
        return a.diff < b.diff;
    });

    for (const auto &c : candidates) {
        for (auto fmt : formats) {
            if (uvc_get_stream_ctrl_format_size(devh, ctrl, fmt, c.frame->wWidth, c.frame->wHeight, 0) == UVC_SUCCESS) {
                return UVC_SUCCESS;
            }
        }
    }

    return UVC_ERROR_INVALID_MODE;
}

// Converts the incoming frame (YUYV/MJPEG/...) to RGB and blits it to the window.
void onFrame(uvc_frame_t *frame, void *user) {
    auto *cam = static_cast<Camera *>(user);
    std::lock_guard<std::mutex> lock(cam->mutex);
    if (!cam->window) {
        if (cam->failedFrames++ < 5) LOGE("Frame arrived but no window");
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
        if (cam->failedFrames++ < 5) LOGE("Dropping frame: %s", uvc_strerror(err));
        return;
    }

    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(cam->window, &buf, nullptr) != 0) {
        if (cam->failedFrames++ < 5) LOGE("ANativeWindow_lock failed");
        return;
    }

    const auto *src = static_cast<const uint8_t *>(cam->rgb->data);
    auto *dst = static_cast<uint8_t *>(buf.bits);
    const int rows = std::min<int>(buf.height, cam->rgb->height);
    const int cols = std::min<int>(buf.width, cam->rgb->width);
    for (int y = 0; y < rows; y++) {
        const uint8_t *s = src + y * cam->rgb->step;
        uint8_t *d = dst + y * buf.stride * 4;
        for (int x = 0; x < cols; x++, s += 3, d += 4) {
            d[0] = s[0];
            d[1] = s[1];
            d[2] = s[2];
            d[3] = 0xFF;
        }
    }
    ANativeWindow_unlockAndPost(cam->window);
    if (cam->frames++ % 30 == 0) {
        LOGI("Frame #%ld posted: %ux%u src format %d, window buffer %dx%d stride %d fmt %d",
             cam->frames - 1, frame->width, frame->height, frame->frame_format,
             buf.width, buf.height, buf.stride, buf.format);
    }
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
Java_com_mikimn_libpolaroid_UvcCamera_nativeOpen(JNIEnv *env, jobject, jint fd) {
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

JNIEXPORT jlong JNICALL
Java_com_mikimn_libpolaroid_UvcCamera_nativeStart(JNIEnv *env, jobject, jlong handle,
                                                  jobject surface, jint width, jint height,
                                                  jint fps) {
    auto *cam = reinterpret_cast<Camera *>(handle);
    uvc_stop_streaming(cam->devh);
    releaseWindow(cam);

    uvc_stream_ctrl_t ctrl;
    uvc_error_t err = getStreamCtrl(cam->devh, &ctrl, width, height, fps);
    if (err != UVC_SUCCESS) {
        LOGE("No usable stream mode for %dx%d@%d", width, height, fps);
        throwIOException(env, "Unsupported stream mode", err);
        return 0;
    }

    uvc_frame_desc_t *frame_desc = uvc_find_frame_desc(cam->devh, ctrl.bFormatIndex, ctrl.bFrameIndex);
    if (frame_desc) {
        width = frame_desc->wWidth;
        height = frame_desc->wHeight;
    }

    LOGI("Starting stream: %dx%d format index %d frame index %d interval %u", width, height,
         ctrl.bFormatIndex, ctrl.bFrameIndex, ctrl.dwFrameInterval);
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        env->ThrowNew(env->FindClass("java/io/IOException"), "Surface is not valid");
        return 0;
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
    } else {
        LOGI("Streaming started, waiting for frames");
    }
    // Packed (width << 32 | height) so Kotlin can size the preview to the real stream.
    return (static_cast<jlong>(width) << 32) | static_cast<jlong>(height);
}

JNIEXPORT void JNICALL
Java_com_mikimn_libpolaroid_UvcCamera_nativeStop(JNIEnv *, jobject, jlong handle) {
    auto *cam = reinterpret_cast<Camera *>(handle);
    LOGI("Stopping stream");
    uvc_stop_streaming(cam->devh);  // joins the callback thread; must not hold the mutex
    releaseWindow(cam);
}

JNIEXPORT void JNICALL
Java_com_mikimn_libpolaroid_UvcCamera_nativeClose(JNIEnv *, jobject, jlong handle) {
    auto *cam = reinterpret_cast<Camera *>(handle);
    uvc_stop_streaming(cam->devh);
    releaseWindow(cam);
    uvc_close(cam->devh);
    uvc_exit(cam->ctx);
    delete cam;
}

}  // extern "C"
