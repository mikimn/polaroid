# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Android project (Gradle, Kotlin) with two modules, early-stage/scaffolding. The goal appears to be driving a USB UVC camera from native code (libusb + libuvc + libjpeg-turbo) and showing it in a Jetpack Compose app.

- `:app` (`com.mikimn.polaroid`, `app/build.gradle.kts`): Compose + Material3 UI. `MainActivity` is still the template "Hello Android" greeting. It depends on `:libpolaroid`.
- `:libpolaroid` (`com.mikimn.libpolaroid`, `libpolaroid/build.gradle`): Android library wrapping native code via CMake/JNI. Kotlin entry point is `NativePolaroidLibrary` (`System.loadLibrary("polaroid")`, `external fun stringFromJNI()`), implemented in `src/main/cpp/libpolaroid.cpp` (currently just a hello-world stub).

Note the mixed build-script styles: `app` uses Kotlin DSL, `libpolaroid` and the root use Groovy. Root `build.gradle` defines `compose_version` (1.2.0) in `buildscript.ext`; plugin versions (AGP 8.7.0, Kotlin 1.7.0) are in the root `plugins {}` block. compileSdk 35, minSdk 24.

## Commands

Use the Gradle wrapper from the repo root (`local.properties` holds the SDK path and is gitignored).

```bash
./gradlew :app:assembleDebug            # build the app (also builds native lib)
./gradlew :libpolaroid:assembleDebug    # build only the library + native code
./gradlew test                          # JVM unit tests (both modules)
./gradlew :app:testDebugUnitTest --tests "com.mikimn.polaroid.ExampleUnitTest"   # single test
./gradlew connectedAndroidTest          # instrumented tests (needs device/emulator)
```

No lint/format tooling is configured beyond Android defaults (`./gradlew lint`).

## Native build architecture (the non-obvious part)

Requires JDK 17 (e.g. `JAVA_HOME` pointing at Android Studio's JBR; the default JDK may be too new/old), NDK `30.0.16248370` (pinned via `ndkVersion` in `libpolaroid/build.gradle`) and SDK CMake 3.22.1. Clone with `--recurse-submodules`.

`libpolaroid/build.gradle` points `externalNativeBuild.cmake` at `src/main/cpp/CMakeLists.txt` (ABIs: arm64-v8a, x86_64). It builds the shared lib `libpolaroid.so` (Kotlin loads it as `"polaroid"`) from `libpolaroid.cpp`, linked against static libuvc.

Third-party code lives in `src/main/cpp/third_party/{libusb,libuvc,libjpeg-turbo}` as **git submodules that must stay pristine** so they can be updated (`git submodule update --remote`). All build glue lives outside them, in `src/main/cpp/cmake/`:
- `libjpeg-turbo.cmake`: libjpeg-turbo forbids `add_subdirectory`, so it is built via `ExternalProject_Add` with the same NDK toolchain and exposed as imported `JPEG::JPEG`.
- `libusb.cmake`: upstream has no Android CMake build (only ndk-build), so the `usb-1.0` static target is defined here, aliased as `LibUSB::LibUSB`.
- libuvc is added with `add_subdirectory`; it picks up the two pre-defined targets (its own `FindLibUSB`/`FindJpegPkg` modules skip when the targets exist). `JPEG_FOUND` is set manually and `CMAKE_SKIP_INSTALL_RULES` is on because its `install(EXPORT)` can't reference our in-tree libusb.

Legacy ndk-build files (`Android.mk`, `libuvc.mk`, `pkg-config.py`) were removed. `UVCCamera.cpp/.h`, `UVCPreview.h`, `objectarray.h`, `utilbase.h`, `utilsbase.cpp` (in the style of saki4510t/UVCCamera) are not yet part of the CMake target. JNI function names in `libpolaroid.cpp` must match the Kotlin package/class (`Java_com_mikimn_libpolaroid_NativePolaroidLibrary_*`).
