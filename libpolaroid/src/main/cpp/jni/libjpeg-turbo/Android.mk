LOCAL_PATH := $(abspath $(call my-dir))
include $(CLEAR_VARS)

LOCAL_MODULE := libjpeg-turbo

SOURCE_PATH := libjpeg-turbo-3.1.1

LOCAL_CFLAGS := $(LOCAL_C_INCLUDES:%=-I%)
LOCAL_CFLAGS += -DANDROID_NDK
LOCAL_ARM_MODE := arm

#ifneq ($(filter $(TARGET_ARCH_ABI), armeabi-v7a armeabi-v7a-hard x86),)
#LOCAL_ARM_NEON := true
#LOCAL_CFLAGS += -D__ARM_HAVE_NEON
#endif

LOCAL_ASMFLAGS += -DELF

ifeq ($(TARGET_ARCH_ABI),x86_64)
LOCAL_SRC_FILES += \
	$(SOURCE_PATH)/simd/x86_64/jsimd.c \
	$(SOURCE_PATH)/simd/x86_64/jfdctflt-sse.asm \
	$(SOURCE_PATH)/simd/x86_64/jfdctfst-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jfdctint-avx2.asm \
	$(SOURCE_PATH)/simd/x86_64/jfdctint-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jidctflt-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jidctfst-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jidctint-avx2.asm \
	$(SOURCE_PATH)/simd/x86_64/jidctint-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jidctred-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jccolor-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jccolor-avx2.asm \
	$(SOURCE_PATH)/simd/x86_64/jcgray-avx2.asm \
	$(SOURCE_PATH)/simd/x86_64/jcgray-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jcsample-avx2.asm \
	$(SOURCE_PATH)/simd/x86_64/jcsample-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jdcolor-avx2.asm \
	$(SOURCE_PATH)/simd/x86_64/jdcolor-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jdmerge-avx2.asm \
	$(SOURCE_PATH)/simd/x86_64/jdmerge-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jdsample-avx2.asm \
	$(SOURCE_PATH)/simd/x86_64/jdsample-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jquantf-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jquanti-avx2.asm \
	$(SOURCE_PATH)/simd/x86_64/jquanti-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jsimdcpu.asm \
	$(SOURCE_PATH)/simd/x86_64/jchuff-sse2.asm \
	$(SOURCE_PATH)/simd/x86_64/jcphuff-sse2.asm \


LOCAL_CFLAGS += \
	-DSIZEOF_SIZE_T=8 \

LOCAL_ASMFLAGS += -D__x86_64__

else ifeq ($(TARGET_ARCH_ABI),x86)
LOCAL_SRC_FILES += \
	$(SOURCE_PATH)/simd/i386/jsimd.c \
	$(SOURCE_PATH)/simd/i386/jsimdcpu.asm \
	$(SOURCE_PATH)/simd/i386/jccolor-avx2.asm \
	$(SOURCE_PATH)/simd/i386/jccolor-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jccolor-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jcgray-avx2.asm \
	$(SOURCE_PATH)/simd/i386/jcgray-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jcgray-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jchuff-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jcphuff-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jcsample-avx2.asm \
	$(SOURCE_PATH)/simd/i386/jcsample-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jcsample-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jdcolor-avx2.asm \
	$(SOURCE_PATH)/simd/i386/jdcolor-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jdcolor-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jdmerge-avx2.asm \
	$(SOURCE_PATH)/simd/i386/jdmerge-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jdmerge-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jdsample-avx2.asm \
	$(SOURCE_PATH)/simd/i386/jdsample-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jdsample-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jfdctflt-3dn.asm \
	$(SOURCE_PATH)/simd/i386/jfdctflt-sse.asm \
	$(SOURCE_PATH)/simd/i386/jfdctfst-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jfdctfst-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jfdctint-avx2.asm \
	$(SOURCE_PATH)/simd/i386/jfdctint-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jfdctint-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jidctflt-3dn.asm \
	$(SOURCE_PATH)/simd/i386/jidctflt-sse.asm \
	$(SOURCE_PATH)/simd/i386/jidctflt-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jidctfst-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jidctfst-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jidctint-avx2.asm \
	$(SOURCE_PATH)/simd/i386/jidctint-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jidctint-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jidctred-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jidctred-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jquant-3dn.asm \
	$(SOURCE_PATH)/simd/i386/jquant-mmx.asm \
	$(SOURCE_PATH)/simd/i386/jquant-sse.asm \
	$(SOURCE_PATH)/simd/i386/jquantf-sse2.asm \
	$(SOURCE_PATH)/simd/i386/jquanti-avx2.asm \
	$(SOURCE_PATH)/simd/i386/jquanti-sse2.asm \

LOCAL_CFLAGS += \
	-DSIZEOF_SIZE_T=4 \

LOCAL_ASMFLAGS += -DPIC

else ifneq ($(filter $(TARGET_ARCH_ABI), armeabi-v7a armeabi-v7a-hard),)
LOCAL_SRC_FILES += \
	$(SOURCE_PATH)/simd/arm/jsimd.c \
	$(SOURCE_PATH)/simd/arm/jsimd_neon.S \

LOCAL_CFLAGS += \
	-DSIZEOF_SIZE_T=4 \

else ifeq ($(TARGET_ARCH_ABI),armeabi)
LOCAL_CFLAGS += \
	-DSIZEOF_SIZE_T=4 \

else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_SRC_FILES += \
	$(SOURCE_PATH)/simd/arm/aarch64/jsimd.c \
	$(SOURCE_PATH)/simd/arm/aarch64/jsimd_neon.S \
	$(SOURCE_PATH)/simd/arm/jccolor-neon.c \
    $(SOURCE_PATH)/simd/arm/jcsample-neon.c \
    $(SOURCE_PATH)/simd/arm/jdmrgext-neon.c \
    $(SOURCE_PATH)/simd/arm/jidctfst-neon.c \
    $(SOURCE_PATH)/simd/arm/jcgray-neon.c \
    $(SOURCE_PATH)/simd/arm/jdcolext-neon.c \
    $(SOURCE_PATH)/simd/arm/jdsample-neon.c \
    $(SOURCE_PATH)/simd/arm/jidctint-neon.c \
    $(SOURCE_PATH)/simd/arm/jcgryext-neon.c \
    $(SOURCE_PATH)/simd/arm/jdcolor-neon.c \
    $(SOURCE_PATH)/simd/arm/jfdctfst-neon.c \
    $(SOURCE_PATH)/simd/arm/jidctred-neon.c \
    $(SOURCE_PATH)/simd/arm/jcphuff-neon.c \
    $(SOURCE_PATH)/simd/arm/jdmerge-neon.c \
    $(SOURCE_PATH)/simd/arm/jfdctint-neon.c \
    $(SOURCE_PATH)/simd/arm/jquanti-neon.c \

LOCAL_CFLAGS += \
	-DSIZEOF_SIZE_T=8 \

endif

SOURCE_PATH_INNER := $(LOCAL_PATH)/$(SOURCE_PATH)/src

# libjpeg_la_SOURCES from Makefile.am
LOCAL_SRC_FILES += \
	$(SOURCE_PATH_INNER)/wrapper/jcapistd-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jcapistd-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jcapistd-16.c \
    $(SOURCE_PATH_INNER)/wrapper/jccoefct-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jccoefct-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jccolor-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jccolor-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jccolor-16.c \
    $(SOURCE_PATH_INNER)/wrapper/jcdctmgr-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jcdctmgr-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jcdiffct-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jcdiffct-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jcdiffct-16.c \
    $(SOURCE_PATH_INNER)/jchuff.c \
    $(SOURCE_PATH_INNER)/jcicc.c \
    $(SOURCE_PATH_INNER)/jcinit.c \
    $(SOURCE_PATH_INNER)/jclhuff.c \
    $(SOURCE_PATH_INNER)/wrapper/jclossls-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jclossls-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jclossls-16.c \
    $(SOURCE_PATH_INNER)/wrapper/jcmainct-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jcmainct-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jcmainct-16.c \
    $(SOURCE_PATH_INNER)/jcmarker.c \
    $(SOURCE_PATH_INNER)/jcmaster.c \
    $(SOURCE_PATH_INNER)/jcomapi.c \
    $(SOURCE_PATH_INNER)/jcparam.c \
    $(SOURCE_PATH_INNER)/jcphuff.c \
    $(SOURCE_PATH_INNER)/wrapper/jcprepct-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jcprepct-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jcprepct-16.c \
    $(SOURCE_PATH_INNER)/wrapper/jcsample-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jcsample-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jcsample-16.c \
    $(SOURCE_PATH_INNER)/jctrans.c \
    $(SOURCE_PATH_INNER)/jdapimin.c \
    $(SOURCE_PATH_INNER)/wrapper/jdapistd-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jdapistd-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jdapistd-16.c \
    $(SOURCE_PATH_INNER)/jdatadst.c \
    $(SOURCE_PATH_INNER)/jdatasrc.c \
    $(SOURCE_PATH_INNER)/wrapper/jdcoefct-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jdcoefct-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jdcolor-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jdcolor-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jdcolor-16.c \
    $(SOURCE_PATH_INNER)/wrapper/jddctmgr-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jddctmgr-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jddiffct-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jddiffct-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jddiffct-16.c \
    $(SOURCE_PATH_INNER)/jdhuff.c \
    $(SOURCE_PATH_INNER)/jdicc.c \
    $(SOURCE_PATH_INNER)/jdinput.c \
    $(SOURCE_PATH_INNER)/jdlhuff.c \
    $(SOURCE_PATH_INNER)/wrapper/jdlossls-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jdlossls-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jdlossls-16.c \
    $(SOURCE_PATH_INNER)/wrapper/jdmainct-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jdmainct-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jdmainct-16.c \
    $(SOURCE_PATH_INNER)/jdmarker.c \
    $(SOURCE_PATH_INNER)/jdmaster.c \
    $(SOURCE_PATH_INNER)/wrapper/jdmerge-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jdmerge-12.c \
    $(SOURCE_PATH_INNER)/jdphuff.c \
    $(SOURCE_PATH_INNER)/wrapper/jdpostct-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jdpostct-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jdpostct-16.c \
    $(SOURCE_PATH_INNER)/wrapper/jdsample-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jdsample-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jdsample-16.c \
    $(SOURCE_PATH_INNER)/jdtrans.c \
    $(SOURCE_PATH_INNER)/jerror.c \
    $(SOURCE_PATH_INNER)/jfdctflt.c \
    $(SOURCE_PATH_INNER)/wrapper/jfdctfst-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jfdctfst-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jfdctint-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jfdctint-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jidctflt-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jidctflt-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jidctfst-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jidctfst-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jidctint-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jidctint-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jidctred-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jidctred-12.c \
    $(SOURCE_PATH_INNER)/jmemmgr.c \
    $(SOURCE_PATH_INNER)/jmemnobs.c \
    $(SOURCE_PATH_INNER)/jpeg_nbits.c \
    $(SOURCE_PATH_INNER)/wrapper/jquant1-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jquant1-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jquant2-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jquant2-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jutils-8.c \
    $(SOURCE_PATH_INNER)/wrapper/jutils-12.c \
    $(SOURCE_PATH_INNER)/wrapper/jutils-16.c \
    
#	$(SOURCE_PATH_INNER)/jutils.c \
#	$(SOURCE_PATH_INNER)/jcapimin.c \
#	$(SOURCE_PATH_INNER)/jcapistd.c \
#	$(SOURCE_PATH_INNER)/jccoefct.c \
#	$(SOURCE_PATH_INNER)/jccolor.c \
#	$(SOURCE_PATH_INNER)/jcdctmgr.c \
#	$(SOURCE_PATH_INNER)/jchuff.c \
#	$(SOURCE_PATH_INNER)/jcinit.c \
#	$(SOURCE_PATH_INNER)/jcmainct.c \
#	$(SOURCE_PATH_INNER)/jcmarker.c \
#	$(SOURCE_PATH_INNER)/jcmaster.c \
#	$(SOURCE_PATH_INNER)/jcomapi.c \
#	$(SOURCE_PATH_INNER)/jcparam.c \
#	$(SOURCE_PATH_INNER)/jcphuff.c \
#	$(SOURCE_PATH_INNER)/jcprepct.c \
#	$(SOURCE_PATH_INNER)/jcsample.c \
#	$(SOURCE_PATH_INNER)/jctrans.c \
#	$(SOURCE_PATH_INNER)/jdapimin.c \
#	$(SOURCE_PATH_INNER)/jdapistd.c \
#	$(SOURCE_PATH_INNER)/jdatadst.c \
#	$(SOURCE_PATH_INNER)/jdatasrc.c \
#	$(SOURCE_PATH_INNER)/jdcoefct.c \
#	$(SOURCE_PATH_INNER)/jdcolor.c \
#	$(SOURCE_PATH_INNER)/jddctmgr.c \
#	$(SOURCE_PATH_INNER)/jdhuff.c \
#	$(SOURCE_PATH_INNER)/jdinput.c \
#	$(SOURCE_PATH_INNER)/jdmainct.c \
#	$(SOURCE_PATH_INNER)/jdmarker.c \
#	$(SOURCE_PATH_INNER)/jdmaster.c \
#	$(SOURCE_PATH_INNER)/jdmerge.c \
#	$(SOURCE_PATH_INNER)/jdphuff.c \
#	$(SOURCE_PATH_INNER)/jdpostct.c \
#	$(SOURCE_PATH_INNER)/jdsample.c \
#	$(SOURCE_PATH_INNER)/jdtrans.c \
#	$(SOURCE_PATH_INNER)/jerror.c \
#	$(SOURCE_PATH_INNER)/jfdctflt.c \
#	$(SOURCE_PATH_INNER)/jfdctfst.c \
#	$(SOURCE_PATH_INNER)/jfdctint.c \
#	$(SOURCE_PATH_INNER)/jidctflt.c \
#	$(SOURCE_PATH_INNER)/jidctfst.c \
#	$(SOURCE_PATH_INNER)/jidctint.c \
#	$(SOURCE_PATH_INNER)/jidctred.c \
#	$(SOURCE_PATH_INNER)/jquant1.c \
#	$(SOURCE_PATH_INNER)/jquant2.c \
#	$(SOURCE_PATH_INNER)/jmemmgr.c \
#	$(SOURCE_PATH_INNER)/jmemnobs.c \
#	$(SOURCE_PATH_INNER)/jdlossls.c \
#	$(SOURCE_PATH_INNER)/jdlhuff.c \
#	$(SOURCE_PATH_INNER)/jddiffct.c \

# if WITH_ARITH_ENC from Makefile.am
LOCAL_SRC_FILES += \
	$(SOURCE_PATH_INNER)/jaricom.c \
	$(SOURCE_PATH_INNER)/jcarith.c \
	$(SOURCE_PATH_INNER)/jdarith.c \

# libturbojpeg_la_SOURCES from Makefile.am
LOCAL_SRC_FILES += \
	$(SOURCE_PATH_INNER)/turbojpeg.c \
	$(SOURCE_PATH_INNER)/transupp.c \
	$(SOURCE_PATH_INNER)/jdatadst-tj.c \
	$(SOURCE_PATH_INNER)/jdatasrc-tj.c \

LOCAL_C_INCLUDES := \
	$(SOURCE_PATH_INNER) \
	$(LOCAL_PATH)/$(SOURCE_PATH) \
	$(LOCAL_PATH)/$(SOURCE_PATH)/src \

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/include \

LOCAL_C_INCLUDES += \
    $(LOCAL_PATH)/$(SOURCE_PATH)/simd \
	$(LOCAL_PATH)/$(SOURCE_PATH)/simd/arm \
	$(LOCAL_PATH)/$(SOURCE_PATH)/simd/i386 \
	$(LOCAL_PATH)/$(SOURCE_PATH)/simd/mips \
	$(LOCAL_PATH)/$(SOURCE_PATH)/simd/mips64 \
	$(LOCAL_PATH)/$(SOURCE_PATH)/simd/nasm \
	$(LOCAL_PATH)/$(SOURCE_PATH)/simd/powerpc \
	$(LOCAL_PATH)/$(SOURCE_PATH)/simd/x86_64 \
	$(LOCAL_PATH)/$(SOURCE_PATH) \

LOCAL_EXPORT_C_INCLUDES := \
	$(LOCAL_PATH)/include \
	$(LOCAL_PATH)/$(SOURCE_PATH) \
	$(LOCAL_PATH)/$(SOURCE_PATH)/src \
	$(SOURCE_PATH_INNER) \

LOCAL_CFLAGS += \
	-DBUILD="\"20181112\"" \
	-DPACKAGE_NAME="\"libjpeg-turbo\"" \
	-DVERSION="\"3.1.1\"" \
	-DLIBJPEG_TURBO_VERSION="3.1.1" \
	-DJPEG_LIB_VERSION=62 \
	-DC_ARITH_CODING_SUPPORTED=1 \
	-DD_ARITH_CODING_SUPPORTED=1 \
	-DBITS_IN_JSAMPLE=8 \
	-DHAVE_DLFCN_H=1 \
	-DHAVE_INTTYPES_H=1 \
	-DHAVE_LOCALE_H=1 \
	-DHAVE_MEMCPY=1 \
	-DHAVE_MEMORY_H=1 \
	-DHAVE_MEMSET=1 \
	-DHAVE_STDDEF_H=1 \
	-DHAVE_STDINT_H=1 \
	-DHAVE_STDLIB_H=1 \
	-DHAVE_STRINGS_H=1 \
	-DHAVE_STRING_H=1 \
	-DHAVE_SYS_STAT_H=1 \
	-DHAVE_SYS_TYPES_H=1 \
	-DHAVE_UNISTD_H=1 \
	-DHAVE_UNSIGNED_CHAR=1 \
	-DHAVE_UNSIGNED_SHORT=1 \
	-DINLINE="inline __attribute__((always_inline))" \
	-DMEM_SRCDST_SUPPORTED=1 \
	-DNEED_SYS_TYPES_H=1 \
	-DSTDC_HEADERS=1 \
	-DWITH_SIMD=1 \
	-DTHREAD_LOCAL=__thread \

LOCAL_CPPFLAGS += -Wno-incompatible-pointer-types
LOCAL_DISABLE_FATAL_LINKER_WARNINGS := true

include $(BUILD_STATIC_LIBRARY)
