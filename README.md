> [!IMPORTANT]
> **This is the InterCom fork of element-call-android**, the native call component of
> [InterCom for Android](https://github.com/c4g7-dev/InterCom#android). It is not an Element product and is not
> affiliated with, endorsed by or supported by Element. Please report problems with this fork at
> <https://github.com/c4g7-dev/InterCom/issues>, not upstream. Below the "InterCom changes" section is the upstream
> README, kept for reference.

## InterCom changes

Branch `intercom`, based on the upstream tag `v0.1.2`; versions are `<upstream version>-intercom.<n>`. The artifacts
are not on Maven Central: `./gradlew publishToMavenLocal` puts `io.element.android:element-call-*:0.1.2-intercom.2`
into `~/.m2`, where the InterCom fork of Element X Android resolves them.

The matrix-rust-rtc core is InterCom's own build as well, `io.element.android:matrix-rtc-android:0.5.0-intercom.1`
from the fork [c4g7-dev/intercom-matrix-rust-rtc](https://github.com/c4g7-dev/intercom-matrix-rust-rtc) (branch
`intercom`, on `v0.5.0`). It is resolved from `~/.m2` too: build and publish it first with that repository's
`scripts/build-intercom-android.sh` (arm64-v8a only). What it changes is under "The core" below.

- **Phone audio in a screen share, at music quality.** While the screen is shared, what the phone plays (media, games,
  untagged playback) is captured with Android 10's playback capture on the same projection and published as a LiveKit
  `ScreenShareAudio` stream: 48 kHz stereo PCM into the core, no echo canceller, noise suppressor or gain control,
  encoded as stereo Opus at 160 kbit/s (128 and 192 can be chosen), with neither DTX nor RED. The microphone is
  published as before. It stops with the share. Apps that opt out of capture, and call audio, are silent by design;
  the app's own sounds are left out.
- **A shared screen's audio is played.** The audio of somebody else's screen share (`ScreenShareAudio`) is now
  opened and played beside their voice, in stereo where it was sent so, as a call stream: it follows the call's route
  (earpiece, speaker, wired or Bluetooth headset) and volume keys. Over a Bluetooth headset's call profile it is
  therefore mono and narrow, as the voices are.
- **A volume for every tile.** A long press on somebody's tile opens its volume: 0 to 200 % and a mute, for a
  person's voice and separately for the audio of their shared screen. Only this phone hears the difference, and the
  choice is kept per user and source for later calls. The curve is the desktop's: the square of the slider up to
  100 %, then 0 to +12 dB. The gain is applied to the decoded samples on their way to the `AudioTrack`, with a
  limiter above 100 % (down at once, back over 150 ms, both channels alike) so that a boosted voice is held back
  rather than clipped. `AudioTrack.setVolume` stops at 100 %, and the platform's `LoudnessEnhancer` is per audio
  session and differs between devices; the samples are in our hands anyway.
- **Sharper screen share.** The screen is sent at its own resolution up to 1440p on the long edge (1080p and 720p can
  be chosen), at 30 or 15 frames a second, as one layer without simulcast, capped by picture size at up to 10 Mbit/s
  (about 8 for 1080 by 2400). The core keeps the resolution and drops frames when the uplink runs short.
- **A share sheet.** The share button opens a sheet with "Share phone audio" (on by default), its quality, the
  resolution and the frame rate, kept for the next share, before the system's capture dialog.
- **Equal grid.** A second stage layout, chosen in Preferences and kept: every tile the same size, shared screens
  included and shown whole, as large as the stage allows for their number and its orientation; nothing is spotlit.
  A double tap still fills the stage with a tile.

### The core

`0.5.0-intercom.1` is matrix-rust-rtc `v0.5.0` with these differences, and no others:

- `FfiPublishOptions` gains `audioEncoding` (bitrate cap, stereo, DTX, RED), `null` by default. A track published
  without it, the microphone included, is published exactly as 0.5.0 does: LiveKit's defaults.
- The `livekit` crate 0.9.7 is replaced by a patched copy that can announce an audio track as stereo. The flag is
  off unless asked for.
- A received `ScreenShareAudio` stream is handed over in two channels instead of one. Voices stay mono.
- For every call: the answer to the SFU's offer repeats `stereo=1` on the audio sections where the offer has it, so
  that a stereo track is decoded in stereo. An answer to an offer without one is unchanged.
- Built for arm64-v8a only, on a developer's machine with Rust 1.100 (stable), not by upstream's release workflow.
  Its exported symbols are those of the 0.5.0 library plus the metadata of the one new record.

API additions a host sees: `MatrixRtcScreenShareOptions` (with `audioBitrate`), `ElementCallStageLayout`,
`MatrixRtcPlaybackSource`, `MatrixRtcPlaybackVolume`, `ElementCallPreferences.screenShare` / `.stageLayout` /
`.playbackVolumes` (and the same three on `ElementCallPreferencesStore`), `ElementCallController.setScreenShareOptions`
/ `setStageLayout` / `setPlaybackVolume`, `MatrixRtcMediaSession.setPlaybackVolumes`, and an `options` parameter on
`MatrixRtcMediaSession.setScreenShareEnabled`. A host that keeps the default preferences store has nothing to change.

None of this has been run on a device or in a call: it compiles, and the unit tests and the quality checks pass. What
the encoder is asked for is covered by tests on both sides of the FFI; what an SFU and a receiver make of it is not.

---

# Element Call Android

A native [MatrixRTC](https://github.com/matrix-org/matrix-spec-proposals/blob/main/proposals/4143-matrix-rtc.md)
call implementation for android: media, session, user interface and the Matrix side, built on
[matrix-rust-rtc](https://github.com/element-hq/matrix-rust-rtc).

<p align="center">
  <img src="docs/call_screen.gif" width="320" alt="A group call: a shared screen spotlighted above a grid of members, scrolled, a hand raised, then the screen share opened full screen.">
</p>


**Status: release candidates.** Versions are `0.x` and every one of them may change the API; releases are published
to Maven Central, see [Consuming a release](#consuming-a-release).

<!--- TOC -->

* [Modules](#modules)
* [Consuming a release](#consuming-a-release)
* [Building](#building)
* [The sample app](#the-sample-app)
* [Host requirements](#host-requirements)
* [Documentation](#documentation)
* [Copyright & License](#copyright-license)

<!--- END -->

## Modules

Four published artifacts under `io.element.android`, one version, plus `element-call-bom` to pin them together:

| Artifact | Project | What it is |
| :--- | :--- | :--- |
| `element-call-api` | `call/api` | the contract: controller, ports, state types, options |
| `element-call` | `call/impl` | the stack, the controller, the foreground service, the Rust core wrapper; no Compose |
| `element-call-ui` | `call/ui` | the call screen (`ElementCallActivity`), the minimized bar, picture-in-picture, style port |
| `element-call-test` | `call/test` | fakes and fixtures for hosts' tests, and the colour-bar test pattern |
| not published | `sample` | the harness: a Compose app over the fakes, a host Activity and the instrumented tests |

See [AGENTS.md](AGENTS.md) for the boundaries between them.

## Consuming a release

The artifacts are on Maven Central, and so is the `matrix-rust-rtc` core they depend on,
`io.element.android:matrix-rtc-android`. With `mavenCentral()` among the repositories, through the BOM so one
version pins the four:

```kotlin
implementation(platform("io.element.android:element-call-bom:<version>"))
implementation("io.element.android:element-call-ui")
implementation("io.element.android:element-call")
testImplementation("io.element.android:element-call-test")
```

The core's version is fixed by the library's metadata; a host never names it. An unreleased build is consumed
from the local Maven repository instead ([docs/local_stack.md](docs/local_stack.md), layer 3).

**From a GitHub release instead.** Each [release](https://github.com/element-hq/element-call-android/releases) also
carries the artifacts as assets, with their module metadata, POMs and sources, and a `SHA256SUMS`, so that a build
can resolve them straight from the release, a fork's included. The core still comes from Maven Central:

```kotlin
dependencyResolutionManagement {
    repositories {
        ivy {
            url = uri("https://github.com/element-hq/element-call-android/releases/download")
            patternLayout { artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]") }
            metadataSources { gradleMetadata() }
            content {
                includeModule("io.element.android", "element-call-bom")
                includeModule("io.element.android", "element-call-api")
                includeModule("io.element.android", "element-call")
                includeModule("io.element.android", "element-call-ui")
                includeModule("io.element.android", "element-call-test")
            }
        }
        google()
        mavenCentral()
    }
}
```

## Building

```
./gradlew assemble test runQualityChecks
```

The `matrix-rust-rtc` core comes from Maven Central, at the version in `gradle/libs.versions.toml`.
[docs/local_stack.md](docs/local_stack.md) explains how to build against a local core, and how to work on the core,
this library and Element X at once.

## The sample app

`sample/` is how the UI is developed without Element X: a Compose app over the fakes, with no server, no login and
no camera. Every row of its picker opens a call - one to one, a group with a spotlight, nine people with a paging
strip, a shared screen, the minimized bar, and the connecting, failed and permission states - with colour bars where
a camera would be, drawn through the real renderer. Its controls do what they say (mute mutes, minimize minimizes,
into picture-in-picture for a video call), a switch overrides the style with a deliberately loud one, and "Answer on
the lock screen" opens a call over the keyguard five seconds later, through a full-screen notification.

```
./gradlew :sample:installDebug
adb shell am start -n io.element.android.call.sample/.SampleActivity --es fixture group   # or any SampleFixture key
./gradlew :sample:connectedDebugAndroidTest                                                # the gesture and pixel tests
```

## Host requirements

Numbered so an integration can be checked against them. Items marked *pending* are settled when the corresponding
code lands.

1. `minSdk` 24 or higher.
2. ABIs `armeabi-v7a`, `arm64-v8a`, `x86_64`. There is no `x86` build of the core; a host that ships `x86` must
   exclude it or accept that the call is unavailable there.
3. A `MatrixBackend` over the host's own Matrix client, handed to `ElementCallStack.Builder`. The sample's
   `SdkMatrixBackend` is one over the Rust SDK ([docs/INTEGRATION.md](docs/INTEGRATION.md), §3).
4. The call screen is the library's `ElementCallActivity`, on top of the host's Activity. It is shown over the lock
   screen while a call is live, so a call answered on a locked phone never shows the host's Activity. The host:
   - implements `ElementCallActivity.Host` on its `Application`: the controller; `leaveCallScreenIntent`, the
     Activity the call screen leaves for; `onCallActivityCreated`, which calls
     `ElementCallPictureInPicture.attach(activity, controller)` and applies anything every Activity of the host
     gets, such as `FLAG_SECURE`; and `CallScreenContent`, for the style;
   - wraps its content in `ElementCallOverlay`, which draws the minimized bar and opens the call screen whenever the
     call is maximized while the host is in front;
   - calls `ElementCallActivity.start(context)` where nothing of the host is in front (answering from a ringing
     screen), and sets `ElementCallNotificationConfig.contentIntent` to `ElementCallActivity::pendingIntent`.

   Minimizing asks the keyguard first. A video call then shrinks into picture-in-picture over the host's Activity;
   an audio call, or one refused picture-in-picture, leaves for the host's Activity with the bar. The host's own
   app lock applies at that Activity, never to a live call. The host Activity needs no picture-in-picture
   attribute.
5. Screen sharing is off by default. A host turns it on with `ElementCallOptions(isScreenSharingEnabled = true)`
   and, in the same change, adds `FOREGROUND_SERVICE_MEDIA_PROJECTION` and the `mediaProjection` type to
   `ElementCallForegroundService` in its own manifest, with `tools:node="merge"`; the library's service declares
   only `microphone|camera`. That type is Play-reviewed, so the library does not declare it, and without it the
   first share is a `SecurityException` on Android 14+.
   `ElementCallOptions(isSfuFallbackEnabled = true)`, also off, lets a call whose own media server is down borrow another
   member's; turn it on only where those servers accept users from other homeservers.
6. Optional, and worth it if the host's client can tell: `controller.reportConnectivity(isOnline)`, `false` when its
   Matrix client goes offline and `true` once a request has succeeded again, not when sync merely starts. The call then
   says "your server is unreachable" at once and retries its sends the moment the server is back; without it, it
   notices at its next send.

## Documentation

- [AGENTS.md](AGENTS.md): boundaries, commands, conventions.
- [docs/local_stack.md](docs/local_stack.md): the local development stack.
- [docs/screenshot_testing.md](docs/screenshot_testing.md): the Paparazzi screenshot tests.
- [RELEASING.md](RELEASING.md): versioning, cutting a release, and what must never be published.
- [CHANGES.md](CHANGES.md): the changelog.
- [CONTRIBUTING.md](CONTRIBUTING.md): how to submit a change.

## Copyright & License

Copyright (c) 2026 Element Creations Ltd.

This software is dual licensed by Element Creations Ltd (Element). It can be used either:

(1) for free under the terms of the GNU Affero General Public License (as published by the Free
Software Foundation, either version 3 of the License, or (at your option) any later version); OR

(2) under the terms of a paid-for Element Commercial License agreement between you and Element (the
terms of which may vary depending on what you and Element have agreed to).

Unless required by applicable law or agreed to in writing, software distributed under the Licenses is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the Licenses for the specific language governing permissions and limitations under the
Licenses.
