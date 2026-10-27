First public release of **Theatre Bar HUD**: see your soundbar's real volume on a Google TV.

A Sony soundbar on HDMI eARC only shows its volume on Sony's own TVs. This app shows the level the soundbar
reports over HDMI-CEC in the TV's vertical volume HUD, over every app and HDMI input, and remembers the volume
per app and input.

### Highlights

- Real soundbar volume and mute state, no network access
- Native HUD look on Xiaomi TVs, generic style elsewhere
- Volume per app and HDMI input, restored when you switch
- Optional Netflix button mapping for Google TV remotes on Xiaomi TVs

### Install

1. Download `theatre-bar-hud-1.0.0.apk` below and install it with `adb install -r theatre-bar-hud-1.0.0.apk`,
   or sideload it with an app like Downloader.
2. Enable the accessibility service **Theatre Bar volume overlay**. See the
   [README](https://github.com/heynol/theatre-bar-hud#setup) for the adb command and the
   *Allow restricted settings* step for sideloaded apps.

**Tested on** Xiaomi `MiTV-MOEU0` (Android TV 14, firmware V816.0.26.3.19) with a Sony HT-A8000 on eARC.
Tried it on another TV or soundbar? Please
[open a compatibility report](https://github.com/heynol/theatre-bar-hud/issues/new?template=compatibility_report.md).

### Verify

- SHA-256 of the APK: see `theatre-bar-hud-1.0.0.apk.sha256`
- Signing certificate (`apksigner verify --print-certs`): `CN=Pascal Heynol`, SHA-256
  `A0:BD:A1:33:7D:24:4E:53:D8:11:6A:2D:A6:A0:BA:26:AD:2B:90:57:C5:87:6E:07:AD:EB:BD:E1:00:94:B3:81`

Not affiliated with, endorsed by or sponsored by Xiaomi or Sony. All trademarks belong to their respective owners.
