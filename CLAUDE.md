# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Android project (Gradle, Kotlin) with two modules, early-stage/scaffolding. The goal appears to be driving a USB UVC camera from native code (libusb + libuvc + libjpeg-turbo) and showing it in a Jetpack Compose app.

- `:app` (`com.mikimn.polaroid`, `app/build.gradle.kts`): Compose + Material3 UI. `ui/camera/` holds small composables/state holders: `CameraScreen` (orchestrates), `ModePicker` (scrolling buttons; if starting a picked mode fails, `CameraScreen` falls back to the default mode and shows the error for `supportedModes()`; picking one restarts the stream on the same TextureView through `CameraPreview`'s `PreviewController`, which serializes stop/start on one executor with a generation counter, so nothing relies on when Compose swaps views), `CameraPreview` (TextureView in `AndroidView`, starts/stops the stream with the texture and sizes itself to the stream aspect ratio; a SurfaceView never got its surface inside Compose on the OnePlus test device), `rememberUvcDevices` / `rememberUsbPermission` (`UsbState.kt`, USB broadcasts), `rememberUvcCamera` (`CameraState.kt`, opens the USB device, the camera and lists its modes together off the main thread (`NonCancellable`) and closes camera + `UsbDeviceConnection` on dispose), `rememberIsStarted` (`Lifecycle.kt`; `CameraScreen` only holds the camera while the activity is STARTED, so it is released in the background). `CameraPreview` starts the stream on a single-thread executor and, in `onSurfaceTextureDestroyed`, runs `stop` on that same executor and waits for it (so a start can never begin on a released surface); error/aspect updates are posted to the main thread; `UvcCamera.start/stop/close` are `@Synchronized`, so a stop waits for a start in flight. USB detach removes the device from `rememberUvcDevices`, which leaves the composition and closes camera then connection.
- `:libpolaroid` (`com.mikimn.libpolaroid`, `libpolaroid/build.gradle`): Android library wrapping native code via CMake/JNI. `UvcCamera` (Kotlin, `AutoCloseable`) wraps `src/main/cpp/uvc_camera.cpp` through the internal `UvcNative` interface (`NativeUvc` is the JNI implementation; tests inject a fake): `open(fd)` takes the fd from `UsbDeviceConnection` (libusb `NO_DEVICE_DISCOVERY` + `uvc_wrap`, since Android forbids enumerating /dev/bus/usb), `supportedModes()` lists the advertised renderable modes (MJPEG/YUYV only; `StreamMode`/`StreamFormat` data types, codes pinned against the C++ `PixelFormatCode` by a test on each side; `start` rejects `OTHER` with `IllegalArgumentException`; a continuous-interval size is listed at its default and fastest rate) and `start(surface, w, h, fps, preferredFormat)` / `start(surface, mode)` negotiates the closest mode (preferred format first, then MJPEG, YUYV, any) and returns the actual `StreamMode`; the JNI layer exchanges modes as `IntArray`s of `{format, width, height, fps}` and blits frames converted to RGBX onto the `ANativeWindow`. `UvcDevices.kt` has `UsbDevice.isUvc`. The connection must stay open while the camera is in use. Untested against real hardware.

Note the mixed build-script styles: `app` uses Kotlin DSL, `libpolaroid` and the root use Groovy. `:libpolaroid` deliberately declares no `implementation` dependencies (they would land in the published POM and raise consumers' compileSdk); keep it that way unless the library really uses them. Dependency and plugin versions live in the version catalog `gradle/libs.versions.toml` (AGP 8.7.0, Kotlin 1.9.25 with Compose compiler 1.5.15, Compose BOM 2024.09.03; the compiler version must match the Kotlin version). compileSdk 35, minSdk 24.

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

`./gradlew :libpolaroid:nativeTest` (also part of `check`) builds and runs `libpolaroid/src/main/cpp/tests`, a standalone host CMake project (SDK CMake 3.22.1 + Ninja, tied to the version in the task; fetches GoogleTest 1.15.2 with FetchContent and a pinned `URL_HASH`, so the first run needs network; the task declares inputs/outputs so it is up to date when nothing changed). It tests `src/main/cpp/core/`: `blit.*` (RGB→RGBX copy with strides) and `stream_mode.*` (closest-size ordering, `intervalToFps`). `core/` must stay free of Android/libuvc/JNI includes; `uvc_camera.cpp` is the thin JNI/libuvc layer on top of it.

## CI

`.github/workflows/ci.yml` runs on pushes to `main` and on PRs (read-only token, in-progress runs cancelled except on `main`): installs JDK 17, NDK `30.0.16248370` and CMake `3.22.1` (keep in sync with `libpolaroid/build.gradle`), runs `./gradlew test lint :libpolaroid:nativeTest :app:assembleDebug`, and fails if any submodule under `third_party/` has local modifications. `app/build.gradle.kts` reads `compose_version` from `rootProject.extra` (the old `by ext` delegate resolved to `null` and broke `lint`).

## Publishing

`libpolaroid/build.gradle` applies `com.vanniktech.maven.publish` (version in the root `plugins {}` block); coordinates, version (`VERSION_NAME`) and POM fields are in `gradle.properties`. `./gradlew :libpolaroid:publishToMavenLocal` produces the AAR (verified to contain `jni/{arm64-v8a,x86_64}/libpolaroid.so`), sources/javadoc jars and a full Apache-2.0 POM. Central Portal upload (`publishAndReleaseToMavenCentral`) needs `ORG_GRADLE_PROJECT_mavenCentralUsername/Password` and, for signing, `signingInMemoryKey`/`signingInMemoryKeyPassword`; signing is skipped when the key is absent. Not yet published: the namespace verification and secrets must be set up by the repo owner.

**Releases:** `.github/workflows/release.yml` runs on `v*` tags, requires the tag to equal `VERSION_NAME`, and that it is reachable from `main`, builds the AAR + APK, creates a *draft* GitHub Release, publishes to Maven Central (irreversible, so last; skipped if the version is already on Central, fails on partially-set secrets, warns and skips with none) and finally un-drafts the release. The write token is job-scoped, `persist-credentials` is off, and the tag name is passed through `env`. `app/build.gradle.kts` derives `versionName`/`versionCode` from `VERSION_NAME` and signs release builds only when `RELEASE_KEYSTORE_FILE` (+ `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) are in the environment. The workflow has never run; the first tag is its real test.

## Native build architecture (the non-obvious part)

Requires JDK 17 (e.g. `JAVA_HOME` pointing at Android Studio's JBR; the default JDK may be too new/old), NDK `30.0.16248370` (pinned via `ndkVersion` in `libpolaroid/build.gradle`) and SDK CMake 3.22.1. Clone with `--recurse-submodules`.

`libpolaroid/build.gradle` points `externalNativeBuild.cmake` at `src/main/cpp/CMakeLists.txt` (ABIs: arm64-v8a, x86_64). It builds the shared lib `libpolaroid.so` (Kotlin loads it as `"polaroid"`) from `uvc_camera.cpp`, linked against static libuvc.

Third-party code lives in `src/main/cpp/third_party/{libusb,libuvc,libjpeg-turbo}` as **git submodules that must stay pristine** so they can be updated (`git submodule update --remote`). All build glue lives outside them, in `src/main/cpp/cmake/`:
- `libjpeg-turbo.cmake`: libjpeg-turbo forbids `add_subdirectory`, so it is built via `ExternalProject_Add` with the same NDK toolchain and exposed as imported `JPEG::JPEG`.
- `libusb.cmake`: upstream has no Android CMake build (only ndk-build), so the `usb-1.0` static target is defined here, aliased as `LibUSB::LibUSB`.
- libuvc is added with `add_subdirectory`; it picks up the two pre-defined targets (its own `FindLibUSB`/`FindJpegPkg` modules skip when the targets exist). `JPEG_FOUND` is set manually and `CMAKE_SKIP_INSTALL_RULES` is on because its `install(EXPORT)` can't reference our in-tree libusb.

Legacy ndk-build files (`Android.mk`, `libuvc.mk`, `pkg-config.py`) were removed. `UVCCamera.cpp/.h`, `UVCPreview.h`, `objectarray.h`, `utilbase.h`, `utilsbase.cpp` (in the style of saki4510t/UVCCamera) are not yet part of the CMake target. JNI function names in `uvc_camera.cpp` must match the `NativeUvc` object in `UvcNative.kt` (`Java_com_mikimn_libpolaroid_NativeUvc_{open,start,stop,close}`).
