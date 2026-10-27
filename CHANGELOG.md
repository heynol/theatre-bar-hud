# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-10-27

First public release.

### Added

- Volume HUD for soundbars on HDMI eARC/ARC: shows the volume and mute state the soundbar reports over
  HDMI-CEC, over every app and HDMI input.
- Native look on Xiaomi TVs, built from TvSystemUI's own layout at runtime, with a generic fallback style.
- Volume per input: remembers the volume per app and HDMI input and walks the soundbar back to it on switch.
  Any volume change during the walk cancels it and is remembered.
- Optional mapping of the Netflix button of Google TV remotes on Xiaomi TVs.
- Setup screen with service status, soundbar status, stored volumes, overlay preview and app version.
- adb-only demo trigger (`dev.heynol.barhud.DEMO`) for screenshots and screen recordings.
- Release APK signed with a dedicated key (SHA-256
  `A0:BD:A1:33:7D:24:4E:53:D8:11:6A:2D:A6:A0:BA:26:AD:2B:90:57:C5:87:6E:07:AD:EB:BD:E1:00:94:B3:81`).

[1.0.0]: https://github.com/heynol/theatre-bar-hud/releases/tag/v1.0.0
