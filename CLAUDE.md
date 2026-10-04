# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Android project (Gradle, Kotlin) with two modules, early-stage/scaffolding. The goal appears to be driving a USB UVC camera from native code (libusb + libuvc + libjpeg-turbo) and showing it in a Jetpack Compose app.

- `:app` (`com.mikimn.polaroid`, `app/build.gradle.kts`): Compose + Material3 UI. `ui/camera/` holds small composables/state holders: `CameraScreen` (orchestrates), `CameraPreview` (TextureView in `AndroidView`, starts/stops the stream with the texture and sizes itself to the stream aspect ratio; a SurfaceView never got its surface inside Compose on the OnePlus test device), `rememberUvcDevices` / `rememberUsbPermission` (`UsbState.kt`, USB broadcasts), `rememberUvcCamera` (`CameraState.kt`, opens the device and closes camera + `UsbDeviceConnection` on dispose).
- `:libpolaroid` (`com.mikimn.libpolaroid`, `libpolaroid/build.gradle`): Android library wrapping native code via CMake/JNI. `UvcCamera` (Kotlin, `AutoCloseable`) wraps `src/main/cpp/uvc_camera.cpp` through the internal `UvcNative` interface (`NativeUvc` is the JNI implementation; tests inject a fake): `open(fd)` takes the fd from `UsbDeviceConnection` (libusb `NO_DEVICE_DISCOVERY` + `uvc_wrap`, since Android forbids enumerating /dev/bus/usb), `start(surface)` negotiates the closest mode to 640x480@30 (returns the actual `StreamSize`) and blits frames converted to RGBX onto the `ANativeWindow`. `UvcDevices.kt` has `UsbDevice.isUvc`. The connection must stay open while the camera is in use. Untested against real hardware.

Note the mixed build-script styles: `app` uses Kotlin DSL, `libpolaroid` and the root use Groovy. Root `build.gradle` defines `compose_version` (1.2.0) in `buildscript.ext`; plugin versions (AGP 8.7.0, Kotlin 1.7.0) are in the root `plugins {}` block. compileSdk 35, minSdk 24.

## Commands

Use the Gradle wrapper from the repo root (`local.properties` holds the SDK path and is gitignored).

```bash
./gradlew :app:assembleDebug            # build the app (also builds native lib)
./gradlew :libpolaroid:assembleDebug    # build only the library + native code
./gradlew test                          # JVM unit tests (both modules)
./gradlew :libpolaroid:nativeTest       # native C++ GoogleTest suite, built for the host
./gradlew :app:testDebugUnitTest --tests "com.mikimn.polaroid.ExampleUnitTest"   # single test
./gradlew connectedAndroidTest          # instrumented tests (needs device/emulator)
```

No lint/format tooling is configured beyond Android defaults (`./gradlew lint`).

## Native tests

`./gradlew :libpolaroid:nativeTest` (also part of `check`) builds and runs `libpolaroid/src/main/cpp/tests`, a standalone host CMake project (SDK CMake 3.22.1 + Ninja, tied to the version in the task; fetches GoogleTest 1.15.2 with FetchContent and a pinned `URL_HASH`, so the first run needs network; the task declares inputs/outputs so it is up to date when nothing changed). It tests `src/main/cpp/core/`: `blit.*` (RGB→RGBX copy with strides) and `stream_mode.*` (closest-size ordering, size packing). `core/` must stay free of Android/libuvc/JNI includes; `uvc_camera.cpp` is the thin JNI/libuvc layer on top of it.

## CI

`.github/workflows/ci.yml` runs on pushes to `main` and on PRs (read-only token, in-progress runs cancelled except on `main`): installs JDK 17, NDK `30.0.16248370` and CMake `3.22.1` (keep in sync with `libpolaroid/build.gradle`), runs `./gradlew test lint :libpolaroid:nativeTest :app:assembleDebug`, and fails if any submodule under `third_party/` has local modifications. `app/build.gradle.kts` reads `compose_version` from `rootProject.extra` (the old `by ext` delegate resolved to `null` and broke `lint`).

## Publishing

`libpolaroid/build.gradle` applies `com.vanniktech.maven.publish` (version in the root `plugins {}` block); coordinates, version (`VERSION_NAME`) and POM fields are in `gradle.properties`. `./gradlew :libpolaroid:publishToMavenLocal` produces the AAR (verified to contain `jni/{arm64-v8a,x86_64}/libpolaroid.so`), sources/javadoc jars and a full Apache-2.0 POM. Central Portal upload (`publishAndReleaseToMavenCentral`) needs `ORG_GRADLE_PROJECT_mavenCentralUsername/Password` and, for signing, `signingInMemoryKey`/`signingInMemoryKeyPassword`; signing is skipped when the key is absent. Not yet published: the namespace verification and secrets must be set up by the repo owner.

## Native build architecture (the non-obvious part)

Requires JDK 17 (e.g. `JAVA_HOME` pointing at Android Studio's JBR; the default JDK may be too new/old), NDK `30.0.16248370` (pinned via `ndkVersion` in `libpolaroid/build.gradle`) and SDK CMake 3.22.1. Clone with `--recurse-submodules`.

`libpolaroid/build.gradle` points `externalNativeBuild.cmake` at `src/main/cpp/CMakeLists.txt` (ABIs: arm64-v8a, x86_64). It builds the shared lib `libpolaroid.so` (Kotlin loads it as `"polaroid"`) from `uvc_camera.cpp`, linked against static libuvc.

Third-party code lives in `src/main/cpp/third_party/{libusb,libuvc,libjpeg-turbo}` as **git submodules that must stay pristine** so they can be updated (`git submodule update --remote`). All build glue lives outside them, in `src/main/cpp/cmake/`:
- `libjpeg-turbo.cmake`: libjpeg-turbo forbids `add_subdirectory`, so it is built via `ExternalProject_Add` with the same NDK toolchain and exposed as imported `JPEG::JPEG`.
- `libusb.cmake`: upstream has no Android CMake build (only ndk-build), so the `usb-1.0` static target is defined here, aliased as `LibUSB::LibUSB`.
- libuvc is added with `add_subdirectory`; it picks up the two pre-defined targets (its own `FindLibUSB`/`FindJpegPkg` modules skip when the targets exist). `JPEG_FOUND` is set manually and `CMAKE_SKIP_INSTALL_RULES` is on because its `install(EXPORT)` can't reference our in-tree libusb.

Legacy ndk-build files (`Android.mk`, `libuvc.mk`, `pkg-config.py`) were removed. `UVCCamera.cpp/.h`, `UVCPreview.h`, `objectarray.h`, `utilbase.h`, `utilsbase.cpp` (in the style of saki4510t/UVCCamera) are not yet part of the CMake target. JNI function names in `uvc_camera.cpp` must match the `NativeUvc` object in `UvcNative.kt` (`Java_com_mikimn_libpolaroid_NativeUvc_{open,start,stop,close}`).
