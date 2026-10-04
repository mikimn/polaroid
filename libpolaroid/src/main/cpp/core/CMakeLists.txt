# Pure C++ logic with no Android/libuvc dependencies, so it can be unit tested on the host
# (see ../tests) as well as linked into the Android library.
add_library(polaroid_core STATIC blit.cpp stream_mode.cpp)
target_include_directories(polaroid_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_features(polaroid_core PUBLIC cxx_std_17)
