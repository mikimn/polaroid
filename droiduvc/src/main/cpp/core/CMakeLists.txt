# Pure C++ logic with no Android/libuvc dependencies, so it can be unit tested on the host
# (see ../tests) as well as linked into the Android library.
add_library(droiduvc_core STATIC blit.cpp stream_mode.cpp)
target_include_directories(droiduvc_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_features(droiduvc_core PUBLIC cxx_std_17)
