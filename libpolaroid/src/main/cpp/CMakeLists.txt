# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.22.1 FATAL_ERROR)

# Declares and names the project.

project("libpolaroid")
include(ExternalProject)

set(PKG_CONFIG_EXECUTABLE "${CMAKE_CURRENT_SOURCE_DIR}/jni/external/pkg-config.py")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        libpolaroid

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        libpolaroid.cpp
        # UVCCamera.cpp
)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        libpolaroid

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})


# Sets lib_src_DIR to the path of the target CMake project.
set( lib_src_DIR ./jni/libjpeg-turbo/libjpeg-turbo-3.1.1 )

# Sets lib_build_DIR to the path of the desired output directory.
set( lib_build_DIR ./jni/libjpeg-turbo/outputs )
file(MAKE_DIRECTORY ${lib_build_DIR})







# Sets lib_src_DIR to the path of the target CMake project.
set(LIBUSB_CMAKE_DIR ./jni/external/libusb )
set(LIBUSB_PROJECT_DIR ./jni/libusb )
set(LIBUSB_BUILD_DIR ./jni/libusb/outputs )
# set(LIBUSB_PROJECT_NAME LibUSB::LibUSB)
set(LIBUSB_PROJECT_NAME libusb)

file(MAKE_DIRECTORY ${LIBUSB_CMAKE_DIR})

# Adds the CMakeLists.txt file located in the specified directory
# as a build dependency.
add_subdirectory(${LIBUSB_CMAKE_DIR} ${LIBUSB_BUILD_DIR})
add_library(${LIBUSB_PROJECT_NAME} STATIC IMPORTED)
set_target_properties(
        ${LIBUSB_PROJECT_NAME}
        PROPERTIES IMPORTED_LOCATION
        ${LIBUSB_BUILD_DIR}/${ANDROID_ABI}/${LIBUSB_PROJECT_NAME}.a
)
include_directories(${LIBUSB_PROJECT_DIR}/libusb)

# Links the top-level CMake build output against libusb.
target_link_libraries(${LIBUSB_PROJECT_NAME})



# Sets lib_src_DIR to the path of the target CMake project.
set(LIBUVC_CMAKE_DIR ./jni/libuvc )
set(LIBUVC_PROJECT_DIR ./jni/libuvc )
set(LIBUVC_BUILD_DIR ./jni/libuvc/outputs )
set(LIBUVC_PROJECT_NAME libuvc)

file(MAKE_DIRECTORY ${LIBUVC_CMAKE_DIR})

# Adds the CMakeLists.txt file located in the specified directory
# as a build dependency.
add_subdirectory(${LIBUVC_CMAKE_DIR} ${LIBUVC_BUILD_DIR})
add_library(${LIBUVC_PROJECT_NAME} STATIC IMPORTED)
set_target_properties(
        ${LIBUVC_PROJECT_NAME}
        PROPERTIES IMPORTED_LOCATION
        ${LIBUVC_BUILD_DIR}/${ANDROID_ABI}/${LIBUVC_PROJECT_DIR}.a
)
include_directories(${LIBUVC_PROJECT_DIR}/include)

# Links the top-level CMake build output against lib_gmath.
target_link_libraries(${LIBUVC_PROJECT_NAME})






ExternalProject_Add(libjpeg-turbo
        GIT_REPOSITORY https://github.com/libjpeg-turbo/libjpeg-turbo.git)
