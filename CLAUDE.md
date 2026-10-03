# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Android project (Gradle, Kotlin) with two modules, early-stage/scaffolding. The goal appears to be driving a USB UVC camera from native code (libusb + libuvc + libjpeg-turbo) and showing it in a Jetpack Compose app. Not a git repository.

- `:app` (`com.mikimn.polaroid`, `app/build.gradle.kts`): Compose + Material3 UI. `MainActivity` is still the template "Hello Android" greeting. It depends on `:libpolaroid`.
- `:libpolaroid` (`com.mikimn.libpolaroid`, `libpolaroid/build.gradle`): Android library wrapping native code via CMake/JNI. Kotlin entry point is `NativePolaroidLibrary` (`System.loadLibrary("libpolaroid")`, `external fun stringFromJNI()`), implemented in `src/main/cpp/libpolaroid.cpp` (currently just a hello-world stub).

Note the mixed build-script styles: `app` uses Kotlin DSL, `libpolaroid` and the root use Groovy. Root `build.gradle` defines `compose_version` (1.2.0) in `buildscript.ext`; plugin versions (AGP 8.7.0, Kotlin 1.7.0) are in the root `plugins {}` block. compileSdk 33, minSdk 24.

## Commands

Use the Gradle wrapper from the repo root (requires an Android SDK/NDK and CMake 3.22.1; `local.properties` holds the SDK path and is gitignored).

```bash
./gradlew :app:assembleDebug            # build the app (also builds native lib)
./gradlew :libpolaroid:assembleDebug    # build only the library + native code
./gradlew test                          # JVM unit tests (both modules)
./gradlew :app:testDebugUnitTest --tests "com.mikimn.polaroid.ExampleUnitTest"   # single test
./gradlew connectedAndroidTest          # instrumented tests (needs device/emulator)
```

No lint/format tooling is configured beyond Android defaults (`./gradlew lint`).

## Native build architecture (the non-obvious part)

`libpolaroid/build.gradle` points `externalNativeBuild.cmake` at `src/main/cpp/CMakeLists.txt`, restricted to `abiFilters "arm64-v8a"` (marked `TODO Remove later`) with targets `usb-1.0`, `uvc`, `libpolaroid`.

- `CMakeLists.txt` currently compiles only `libpolaroid.cpp` into the shared lib (`UVCCamera.cpp` is commented out). It pulls in third-party libs under `src/main/cpp/jni/`:
  - `external/libusb` (CMake wrapper dir) and `libusb/` (sources) — added via `add_subdirectory` and imported as a static lib from `./jni/libusb/outputs/<ABI>/libusb.a`.
  - `libuvc/` — same pattern, outputs under `./jni/libuvc/outputs`.
  - `libjpeg-turbo/libjpeg-turbo-3.1.1/` — vendored source; the CMake file also has an `ExternalProject_Add` that clones libjpeg-turbo from GitHub (needs network).
  - `external/pkg-config.py` / `fake-pkg-config` stand in for pkg-config when building these.
- `jni/Android.mk` is a leftover ndk-build path (the `ndkBuild` block in `build.gradle` is commented out); it is not the active build. The CMake file is the source of truth.
- `UVCCamera.cpp/.h`, `UVCPreview.h`, `objectarray.h`, `utilbase.h`, `utilsbase.cpp` are C++ sources (UVCCamera-style, in the style of saki4510t/UVCCamera) not yet wired into the CMake target.
- Changes to the JNI function names in `libpolaroid.cpp` must match the Kotlin package/class (`Java_com_mikimn_libpolaroid_NativePolaroidLibrary_*`).

Do not edit vendored code under `jni/libjpeg-turbo`, `jni/libusb`, `jni/libuvc` unless intentionally patching a dependency.
