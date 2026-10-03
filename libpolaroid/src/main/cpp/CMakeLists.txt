cmake_minimum_required(VERSION 3.22.1)
project(polaroid LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# --- Third-party dependencies (git submodules under third_party/, never modified) ---

include(cmake/libjpeg-turbo.cmake)

# libusb
include(cmake/libusb.cmake)

# libuvc: consumes the LibUSB::LibUSB and JPEG::JPEG targets defined above
set(JPEG_FOUND TRUE)
set(CMAKE_BUILD_TARGET Static CACHE STRING "" FORCE)
set(BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
set(CMAKE_SKIP_INSTALL_RULES ON)  # its install(EXPORT) can't reference our in-tree libusb target
add_subdirectory(third_party/libuvc EXCLUDE_FROM_ALL)
unset(CMAKE_SKIP_INSTALL_RULES)

# --- Native library loaded by NativePolaroidLibrary ---

add_library(polaroid SHARED uvc_camera.cpp)
target_link_libraries(polaroid PRIVATE LibUVC::UVCStatic usb-1.0 android log)
