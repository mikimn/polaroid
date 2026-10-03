PROJ_PATH	:= $(call my-dir)
include $(CLEAR_VARS)

CFLAGS := -Werror

LOCAL_C_INCLUDES := \
		$(PROJ_PATH)/ \
		$(PROJ_PATH)/../

LOCAL_CFLAGS := $(LOCAL_C_INCLUDES:%=-I%)
LOCAL_CFLAGS += -DANDROID_NDK
LOCAL_CFLAGS += -DLOG_NDEBUG
LOCAL_CFLAGS += -DACCESS_RAW_DESCRIPTORS
LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays

LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -ldl
LOCAL_LDLIBS += -llog
LOCAL_LDLIBS += -landroid

LOCAL_SRC_FILES := \
		libpolaroid.cpp \
		UVCCamera.cpp

LOCAL_MODULE    := UVCCamera

include $(PROJ_PATH)/libjpeg-turbo/Android.mk
include $(PROJ_PATH)/libusb/android/jni/Android.mk
include $(PROJ_PATH)/external/libuvc.mk