---
name: Compatibility report
about: Tell us how Theatre Bar HUD works with your TV and soundbar, whether it worked or not
title: "Compatibility: <TV model> + <soundbar>"
labels: compatibility
---

**TV model**
<!-- Settings → System → About, or: adb shell getprop ro.product.model -->

**Android version**
<!-- adb shell getprop ro.build.version.release -->

**Firmware**
<!-- Settings → System → About → Android TV OS build, or: adb shell getprop ro.build.display.id -->

**Soundbar / AV receiver**
<!-- Brand and model -->

**Connection**
- [ ] HDMI eARC
- [ ] HDMI ARC
- [ ] Other:

**Does it work?**
- [ ] Yes, HUD shows the correct volume
- [ ] Partly (describe below)
- [ ] No

| | Yes | No | Not tried |
| --- | --- | --- | --- |
| HUD appears on volume keys | | | |
| Number matches the soundbar's display | | | |
| Mute shows | | | |
| HUD has the TV's native style | | | |
| Volume per input restores | | | |

**What does step 3 of the setup screen say?**
<!-- "Soundbar connected · volume …" or "No soundbar active" -->

**Screenshot**
<!-- adb exec-out screencap -p > hud.png, or a phone photo. HDMI inputs show up black in screenshots. -->

**Anything else**
<!-- Logs help: adb logcat -s VolumeController TvVolumeSource HudContent (after adb shell setprop log.tag.TvVolumeSource DEBUG) -->
