# libusb ships no CMake build for Android (only ndk-build files), so the target is
# defined here, keeping third_party/libusb an untouched submodule.
set(LIBUSB_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb)

add_library(usb-1.0 STATIC
        ${LIBUSB_ROOT}/libusb/core.c
        ${LIBUSB_ROOT}/libusb/descriptor.c
        ${LIBUSB_ROOT}/libusb/hotplug.c
        ${LIBUSB_ROOT}/libusb/io.c
        ${LIBUSB_ROOT}/libusb/sync.c
        ${LIBUSB_ROOT}/libusb/strerror.c
        ${LIBUSB_ROOT}/libusb/os/linux_usbfs.c
        ${LIBUSB_ROOT}/libusb/os/events_posix.c
        ${LIBUSB_ROOT}/libusb/os/threads_posix.c
        ${LIBUSB_ROOT}/libusb/os/linux_netlink.c)

target_include_directories(usb-1.0
        PRIVATE ${LIBUSB_ROOT}/android ${LIBUSB_ROOT}/libusb ${LIBUSB_ROOT}/libusb/os
        PUBLIC ${LIBUSB_ROOT}/libusb)
target_compile_options(usb-1.0 PRIVATE -fvisibility=hidden -pthread)
target_link_libraries(usb-1.0 PRIVATE log)

# Name expected by libuvc's FindLibUSB.cmake
add_library(LibUSB::LibUSB ALIAS usb-1.0)
