# Polaroid

[![CI](https://github.com/mikimn/polaroid/actions/workflows/ci.yml/badge.svg)](https://github.com/mikimn/polaroid/actions/workflows/ci.yml)

This library enables working with UVC cameras on Android. It is based on `libuvc` and is heavily inspired by the [`UVCCamera`](https://github.com/saki4510t/UVCCamera) project.

<img src="assets/icon-original.png" alt="Polaroid Logo" width="256" height="256">


## Getting Started

Polaroid is an Android library (`:libpolaroid`) that opens a USB Video Class camera and streams its frames to a `Surface`. It is not published to a Maven repository yet, so add it to your project as a module.

### Requirements

- Android `minSdk 24`, arm64-v8a or x86_64 device (USB host support required)
- JDK 17, Android SDK 35, NDK `30.0.16248370` and CMake `3.22.1` (installable from the SDK Manager)
- The library is built with Kotlin 1.9, so consuming projects need Kotlin 1.8 or newer to read its metadata. Its only runtime dependency is the Kotlin standard library.

### Add the library

**From Maven Central** (once the first release is published, see [Releasing](#releasing)):

```kotlin
// settings.gradle(.kts) / build.gradle(.kts)
repositories { mavenCentral() }

// app/build.gradle.kts
dependencies { implementation("io.github.mikimn:libpolaroid:0.1.0") }
```

The AAR bundles the native libraries for `arm64-v8a` and `x86_64`.

**From source** (e.g. to try unreleased changes): either run `./gradlew :libpolaroid:publishToMavenLocal` and add `mavenLocal()` to your repositories, or include the module directly:

1. Add this repository as a git submodule (or copy it) and include the module in `settings.gradle`:

   ```bash
   git submodule add https://github.com/mikimn/polaroid.git polaroid
   git submodule update --init --recursive
   ```

   ```groovy
   include ':libpolaroid'
   project(':libpolaroid').projectDir = new File('polaroid/libpolaroid')
   ```

2. Depend on it from your app module:

   ```kotlin
   implementation(project(":libpolaroid"))
   ```

### Declare permissions

Declare USB host support and the camera permission in your `AndroidManifest.xml`. Android requires `CAMERA` to open UVC devices over USB:

```xml
<uses-feature android:name="android.hardware.usb.host" android:required="false" />
<uses-permission android:name="android.permission.CAMERA" />
```

### Jetpack Compose

Compose apps can use the `libpolaroid-compose` module, which brings the camera plumbing as small composables and state holders (it depends on `libpolaroid` via `api`, so you only add this one):

```kotlin
dependencies { implementation("io.github.mikimn:libpolaroid-compose:0.1.0") } // or project(":libpolaroid-compose")
```

```kotlin
@Composable
fun CameraScreen() {
    val devices by rememberUvcDevices()                         // updates on plug / unplug
    val device = devices.firstOrNull() ?: return Text("Connect a UVC camera")
    val permission = rememberUsbPermission(device)              // CAMERA + per-device USB permission
    if (!permission.granted) {
        Button(onClick = permission.request) { Text("Allow camera access") }
        return
    }
    if (!rememberIsStarted()) return                            // release the camera in the background
    when (val state = rememberUvcCamera(device)) {              // opens off the main thread, closes on dispose
        UvcCameraState.Opening -> Text("Opening…")
        is UvcCameraState.Failed -> Text(state.message)
        is UvcCameraState.Ready -> UvcCameraPreview(state.camera, Modifier.fillMaxWidth())
    }
}
```

`UvcCameraPreview(camera, modifier, requestedMode, onError)` streams while its `TextureView` exists and sizes itself to the stream's aspect ratio; pass a `StreamMode` from `UvcCameraState.Ready.modes` as `requestedMode` to switch modes. The module declares the `CAMERA` permission and the optional USB-host feature in its manifest, so they **merge into your app's manifest** (apps that review their permissions will see `CAMERA`; Android requires it to open UVC devices), and its public API is explicit (`explicitApi()`). It builds against Compose BOM 2024.09 and needs `compileSdk` 34+.
### Camera controls

`camera.controls` exposes what the connected device supports, discovered from the camera's own capability bitmaps, so an unsupported control is `null` rather than an error at call time. It works before and during streaming.

```kotlin
val zoom = camera.controls.zoom                  // null if the camera has no zoom
if (zoom != null) {
    val range = zoom.range!!                     // min, max, step, default as reported by the device
    zoom.set(((range.min + range.max) / 2))      // SET_CUR; IllegalArgumentException outside the range
    println(zoom.value)                          // GET_CUR
    zoom.reset()                                 // back to the device default
}
camera.controls.supported                        // Set<ControlId>
camera.controls.autoExposureMode?.options        // e.g. {AutoExposureMode.MANUAL, AutoExposureMode.AUTO}
```

**These calls are blocking USB transfers** (bounded by a one second timeout, reported as `ControlException.Reason.TIMEOUT`): use them from a background thread, not from the main thread or from composition. A write the device refuses, because the control is read-only or an auto mode currently owns it (for example exposure time while auto-exposure is on), fails with `UNSUPPORTED_OR_INVALID`, just like an unsupported control; the `GET_INFO` bits that would tell them apart are not read yet.

Covered: auto-exposure mode, exposure time, focus (+ auto), iris, zoom, pan/tilt, brightness, contrast, saturation, sharpness, gamma, hue, gain, backlight compensation, power-line frequency and white balance temperature (+ auto). Values are the raw UVC values (see the `ControlId` docs for units). Device failures throw `ControlException` with a `reason` (`UNSUPPORTED_OR_INVALID`, `DISCONNECTED`, `TIMEOUT`). `EXPOSURE_TIME` is an unsigned 32-bit field exposed as `Int` (values from 2^31 would read back negative). Not yet covered: observing values that change by themselves under an auto mode (read `value` again), and relative controls.

### Use the API

The API is deliberately small:

| API | Purpose |
| --- | --- |
| `UsbManager.uvcDevices()` / `UsbDevice.isUvc` | Find attached UVC cameras |
| `UvcCamera.open(fd)` | Open a camera from the file descriptor of a `UsbDeviceConnection` |
| `UvcCamera.supportedModes()` | List the camera's renderable modes (MJPEG and YUYV) as `StreamMode(format, width, height, fps)` |
| `UvcCamera.start(surface, width, height, fps, preferredFormat)` or `start(surface, mode)` | Stream to a `Surface`; returns the `StreamMode` actually negotiated |
| `UvcCamera.info` | `CameraInfo`: vendor/product ids, manufacturer/product/serial strings and UVC version (works without streaming) |
| `UvcCamera.controls` | Camera controls (zoom, exposure, focus, brightness, ...): `supported`, `zoom`, `focus`, ..., `get(ControlId)`. Each `Control` has `range`, `value`, `set(v)` and `reset()` |
| `UvcCamera.stop()` / `close()` | Stop streaming / release the camera |

```kotlin
val usb = context.getSystemService(UsbManager::class.java)
val device = usb.uvcDevices().first()

// 1. Ask the user for access (shows the system dialog; handle the result via your own broadcast).
usb.requestPermission(device, pendingIntent)

// 2. Once granted, open the device. Keep the connection open for as long as the camera is used.
val connection = usb.openDevice(device)!!
val camera = UvcCamera.open(connection.fileDescriptor) // throws IOException on failure

// 3. Stream to a Surface, e.g. from TextureView.SurfaceTextureListener.onSurfaceTextureAvailable.
val size = camera.start(Surface(surfaceTexture), width = 640, height = 480, fps = 30)

// 4. Clean up. Stop before the surface is destroyed, and close the camera before the connection.
camera.stop()
camera.close()
connection.close()
```

Notes:

- `start` picks the closest mode the camera supports to the requested size and rate, trying `preferredFormat` first if you pass one (default order MJPEG, YUYV, anything else). Use the returned `StreamMode` (which carries the real size, frame rate and pixel format) to set your preview's aspect ratio. Sizes and modes are plain data classes (`StreamMode`, `StreamFormat`) rather than `android.util.Size`, which keeps the logic unit-testable on the JVM. Formats the library cannot render (NV12, H.264, ...) are left out of `supportedModes()`, and asking `start` for `StreamFormat.OTHER` throws `IllegalArgumentException`. The example app lists `supportedModes()` in a mode picker under the preview.
- API change since the first commits: `start` returns a `StreamMode` (format, size and frame rate) instead of a `StreamSize`, which was never released.
- Use a `TextureView` (or a surface you know is valid). A `SurfaceView` inside Jetpack Compose did not reliably receive its surface on some devices.
- `UvcCamera.start` negotiates the stream and can take up to a second: call it off the main thread. `stop`/`close` are synchronized with `start`, so calling `stop` from your surface-destroyed callback safely waits for a start in flight.
- The example app's `ui/camera/` package shows the full flow as small composables, including releasing the camera when the app is backgrounded.

## Example Application

The `:app` module is a Jetpack Compose app that shows a live preview of the first connected UVC camera. It depends on `:libpolaroid-compose` exactly like a third-party app would (it contains only screen-level UI, no USB or camera plumbing), so `app/src/main/java/com/mikimn/polaroid/ui/camera/CameraScreen.kt` doubles as an integration reference matching the example in [Jetpack Compose](#jetpack-compose). Besides the preview and the mode picker it has a **Controls** panel built from what the connected camera reports: sliders for zoom, exposure, focus, brightness, ... using the device's own ranges, switches for the auto modes (the matching manual slider is disabled while auto is on), a reset per control and "Reset all", and pinch-to-zoom on the preview when the camera has a zoom control. **Camera info** opens a plain-text report (device identity, UVC version, every stream mode, and every control with min/max/step/default/current, supported or not) that you can copy or share and paste into a bug report; its layout is fixed so two devices' reports diff cleanly, the serial number is masked to its last four characters so it is safe to paste publicly, and it works without streaming.

```bash
git clone --recurse-submodules https://github.com/mikimn/polaroid.git
cd polaroid
```

If you already cloned without submodules, run `git submodule update --init --recursive`.

**Build prerequisites:** JDK 17 (Android Studio's bundled JBR works) and the Android SDK with NDK `30.0.16248370` and CMake `3.22.1`. Point Gradle at your SDK with a `local.properties` file (Android Studio creates it for you):

```properties
sdk.dir=/path/to/Android/sdk
```

**Build and install** on a device with USB host (OTG) support and a UVC camera:

```bash
./gradlew :app:assembleDebug     # build only
./gradlew :app:installDebug      # build and install on the connected device
```

Plug the camera in, open the app and tap **Allow camera access**, accepting the camera permission and the USB permission dialogs. The preview appears once access is granted.

### Development builds

Every CI run (pull requests and pushes to `main`) uploads the example app as a `polaroid-debug-<commit sha>` artifact (kept for 14 days): open the run under the repository's **Actions** tab and download it from the *Artifacts* section, unzip, then `adb install app-debug.apk`. These are debug-signed with the CI runner's throwaway debug key, so you must uninstall a previous development build before installing one from another run.

## Contributing

Contributions are welcome. Please open an issue to discuss larger changes first.

- Build the example app (`./gradlew :app:assembleDebug`) and run the unit tests (`./gradlew test`) before opening a pull request. `:libpolaroid` also has instrumented tests that exercise the real JNI layer (no camera needed): `./gradlew :libpolaroid:connectedDebugAndroidTest` on a device or emulator. Test camera-related changes on a real device and camera if you can.
- Third-party code lives in `libpolaroid/src/main/cpp/third_party/` as git submodules (`libusb`, `libuvc`, `libjpeg-turbo`). **Keep them pristine** so they can be updated; never edit files inside them. Build integration belongs in `libpolaroid/src/main/cpp/cmake/` and the top-level `CMakeLists.txt`.
- Native C++ unit tests (GoogleTest, built for the host, no device needed): `./gradlew :libpolaroid:nativeTest`. Put new logic that does not need Android or libuvc in `libpolaroid/src/main/cpp/core/` so it can be tested. The first run downloads GoogleTest (pinned by hash), so `check` needs network access once.
- CI (`.github/workflows/ci.yml`) runs `./gradlew test lint :libpolaroid:nativeTest :app:assembleDebug` on pushes to `main` and on pull requests, and fails if a third-party submodule was modified. Run the same command locally before opening a PR.
- The native build is CMake only. Do not reintroduce ndk-build (`Android.mk`) files.
- Keep components small and reusable, in line with the existing composables.
- See `CLAUDE.md` for an overview of the architecture and build setup.

### Releasing

The version lives in one place, `VERSION_NAME` in `gradle.properties`; it is used for the library publication and for the example app's `versionName`/`versionCode` (`MAJOR*10000 + MINOR*100 + PATCH`).

To cut a release, first set `VERSION_NAME=X.Y.Z` in `gradle.properties` and merge it to `main`, then either:

- **From GitHub (no tag needed):** Actions → **Release** → *Run workflow*, on the `main` branch. The tag `vX.Y.Z` is taken from `VERSION_NAME` and created on that commit when the release is published. With **"Publish" unchecked** (the default) it only builds, tests and creates or updates a **draft** GitHub Release with the AARs and APK so you can inspect them; run it again with **"Publish" checked** to publish to Maven Central (irreversible) and publish the release. The manual run refuses to start off `main` or when `vX.Y.Z` already points at another commit.
- **From a terminal:** tag and push, `git tag vX.Y.Z && git push origin vX.Y.Z`; a tag push always publishes.

The `Release` workflow (`.github/workflows/release.yml`) then checks that the tag matches `VERSION_NAME` and points at a commit on `main`, runs the tests and lint, builds the library AAR and the example APK, creates a **draft** GitHub Release with generated notes and the AAR and APK attached, publishes to Maven Central, and only then publishes the release (versions containing `-` are marked pre-release). Maven Central cannot be undone or re-run for a version, so it runs last, and every step is safe to re-run after a failure (an already-published version is skipped). If only some of the four Central secrets are set the run fails instead of silently skipping publication; with none set it skips with a warning. Release notes are generated from merged PRs, so there is no separate changelog file.

Repository secrets used by the workflow (steps are skipped, not failed, when their secrets are missing):

| Secret | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | Sign the release APK (base64 of the `.jks`). Without them the debug-signed APK is attached. |
| `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` | Central Portal user token |
| `SIGNING_KEY`, `SIGNING_KEY_PASSWORD` | ASCII-armored GPG key used to sign the Maven artifacts |

Publishing locally: `./gradlew :libpolaroid:publishToMavenLocal` to inspect the artifacts, or `./gradlew :libpolaroid:publishAndReleaseToMavenCentral` with the secrets above provided as `ORG_GRADLE_PROJECT_mavenCentralUsername`, `ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKey` and `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`. The `io.github.mikimn` namespace must be verified on the [Central Portal](https://central.sonatype.com) first.

## How AI is Used in the Project

This project is developed with the help of [Claude Code](https://claude.com/claude-code), an AI coding assistant from Anthropic. It was used for tasks such as modernizing the native build (CMake, NDK upgrade, submodule layout), writing the JNI/libuvc streaming layer and the Compose example app, debugging preview issues on a physical device from logs, and drafting documentation (including `CLAUDE.md`, which gives AI agents context about the repository).

AI-generated changes are reviewed by the maintainer and verified by building and running them, including on real hardware. Commits that were co-written with an AI assistant carry a `Co-Authored-By` trailer. Contributors are welcome to use AI tools too, but you remain responsible for understanding, testing and standing behind what you submit.

## License

Polaroid is licensed under the [Apache License 2.0](LICENSE).

It bundles third-party libraries as git submodules that are under their own licenses:

| Library | License |
| --- | --- |
| [libuvc](https://github.com/libuvc/libuvc) | BSD-3-Clause |
| [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo) | IJG, BSD-3-Clause and zlib |
| [libusb](https://github.com/libusb/libusb) | LGPL-2.1-or-later |

Note that libusb is LGPL and is statically linked into `libpolaroid.so`; if you distribute an app built on Polaroid, review the LGPL's requirements for your distribution.
