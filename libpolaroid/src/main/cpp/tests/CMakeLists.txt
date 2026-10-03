# Standalone host build of the native unit tests (no NDK needed):
#   cmake -S . -B build && cmake --build build && ctest --test-dir build --output-on-failure
cmake_minimum_required(VERSION 3.22.1)
project(polaroid_native_tests LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
if(POLICY CMP0135)
    cmake_policy(SET CMP0135 NEW)  # use extraction time for downloaded archives
endif()
FetchContent_Declare(googletest
        URL https://github.com/google/googletest/archive/refs/tags/v1.15.2.tar.gz)
set(INSTALL_GTEST OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

add_subdirectory(../core core)

enable_testing()
add_executable(polaroid_core_tests blit_test.cpp stream_mode_test.cpp)
target_link_libraries(polaroid_core_tests PRIVATE polaroid_core GTest::gtest_main)

include(GoogleTest)
gtest_discover_tests(polaroid_core_tests)
