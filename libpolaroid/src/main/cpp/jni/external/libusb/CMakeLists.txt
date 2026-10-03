cmake_minimum_required(VERSION 3.10)
project(usb-1.0 LANGUAGES C)

# Allow users to configure the library name using an option
option(USE_PC_NAME "Use usb-1.0 as the library name" OFF)

set(LIBUSB_SRC_DIR ../../libusb)

# Set the source files
set(LIBUSB_SRC
        ${LIBUSB_SRC_DIR}/libusb/core.c
        ${LIBUSB_SRC_DIR}/libusb/descriptor.c
        ${LIBUSB_SRC_DIR}/libusb/hotplug.c
        ${LIBUSB_SRC_DIR}/libusb/io.c
        ${LIBUSB_SRC_DIR}/libusb/sync.c
        ${LIBUSB_SRC_DIR}/libusb/strerror.c
        ${LIBUSB_SRC_DIR}/libusb/os/linux_usbfs.c
        ${LIBUSB_SRC_DIR}/libusb/os/events_posix.c
        ${LIBUSB_SRC_DIR}/libusb/os/threads_posix.c
        ${LIBUSB_SRC_DIR}/libusb/os/linux_netlink.c
)

# Create the shared library
add_library(${PROJECT_NAME} STATIC ${LIBUSB_SRC})

# Define include directories
target_include_directories(${PROJECT_NAME}
        PRIVATE
        ${LIBUSB_SRC_DIR}/android
        ${LIBUSB_SRC_DIR}/libusb
        ${LIBUSB_SRC_DIR}/libusb/os
        PUBLIC
        ${LIBUSB_SRC_DIR}/libusb
)

# Compiler flags
target_compile_options(${PROJECT_NAME} PRIVATE -fvisibility=hidden -pthread)

# Linker libraries
target_link_libraries(${PROJECT_NAME} PRIVATE log)

# Set output name depending on the option
if(USE_PC_NAME)
    set_target_properties(${PROJECT_NAME} PROPERTIES OUTPUT_NAME usb-1.0)
else()
    message(WARNING "Building to legacy library name libusb1.0, which differs from pkg-config.")
    message(WARNING "Use -DUSE_PC_NAME=ON to change the module name to the compatible usb-1.0.")
    message(WARNING "USE_PC_NAME=ON may be the default in the future.")
    set_target_properties(${PROJECT_NAME} PROPERTIES OUTPUT_NAME libusb1.0)
endif()
