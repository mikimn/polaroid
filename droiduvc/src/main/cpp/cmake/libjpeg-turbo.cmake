# libjpeg-turbo refuses add_subdirectory() integration, so it is built as an
# ExternalProject with the same NDK toolchain and exposed as the JPEG::JPEG target
# that libuvc's FindJpegPkg.cmake expects.
include(ExternalProject)

set(JPEG_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libjpeg-turbo)
set(JPEG_PREFIX ${CMAKE_CURRENT_BINARY_DIR}/libjpeg-turbo)
set(JPEG_INCLUDE_DIR ${JPEG_PREFIX}/include)
set(JPEG_LIBRARY_FILE ${JPEG_PREFIX}/lib/libjpeg.a)

ExternalProject_Add(libjpeg-turbo-build
        SOURCE_DIR ${JPEG_SRC_DIR}
        PREFIX ${JPEG_PREFIX}
        INSTALL_DIR ${JPEG_PREFIX}
        CMAKE_ARGS
            -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
            -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
            -DANDROID_ABI=${ANDROID_ABI}
            -DANDROID_PLATFORM=${ANDROID_PLATFORM}
            -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
            -DCMAKE_INSTALL_PREFIX=${JPEG_PREFIX}
            -DCMAKE_INSTALL_LIBDIR=lib
            -DCMAKE_POSITION_INDEPENDENT_CODE=ON
            -DENABLE_SHARED=OFF
            -DENABLE_STATIC=ON
            -DWITH_TURBOJPEG=OFF
            -DWITH_JAVA=OFF
        BUILD_BYPRODUCTS ${JPEG_LIBRARY_FILE})

file(MAKE_DIRECTORY ${JPEG_INCLUDE_DIR})
add_library(JPEG::JPEG STATIC IMPORTED GLOBAL)
set_target_properties(JPEG::JPEG PROPERTIES
        IMPORTED_LOCATION ${JPEG_LIBRARY_FILE}
        INTERFACE_INCLUDE_DIRECTORIES ${JPEG_INCLUDE_DIR})
add_dependencies(JPEG::JPEG libjpeg-turbo-build)
