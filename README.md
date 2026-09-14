# Theatre Bar HUD

A volume overlay for a **Xiaomi Google TV** with a **Sony Bravia Theatre Bar 8** (HT-A8000) on eARC.
Sony only shows the soundbar's volume number on its own TVs. On the Xiaomi you get no feedback at all. This app shows the soundbar's real volume in the TV's native vertical volume HUD, marked with a soundbar icon. It works over every input, including HDMI sources such as a PlayStation 5, and remembers the volume per
app and HDMI input.

- Android TV / Google TV, API 30+, ~220 KB, no network access, no special permissions
- Tested on Xiaomi `MiTV-MOEU0` (Android 14, firmware V816.0.26.3.19) with HT-A8000

## How it works

```
Remote ─► TV ──HDMI-CEC <User Control Pressed>──► Soundbar
 │                                                  │
 │        AudioService  ◄─ <Report Audio Status> ──┘   (volume 0–100 + mute bit)
 │             │ STREAM_MUSIC index for hdmi_earc
 ▼             ▼
AudioService / VOLUME_CHANGED ──► HUD (~0.3–0.5 s after the key press)
```

1. **Volume source (no network).** With an eARC audio system Android 14 uses *adjust-only absolute
   volume*. Each volume key goes to the soundbar over CEC. The soundbar answers with
   `<Report Audio Status>`, and Android stores that value as the media volume of the HDMI eARC device.
   [`TvVolumeSource`](app/src/main/java/dev/heynol/barhud/volume/TvVolumeSource.kt) reads it through
   `AudioManager` and listens for `VOLUME_CHANGED_ACTION`. Changes made on the soundbar remote or in the
   Bravia Connect app show up as well.
2. **Feedback timing.** On the Xiaomi, volume keys never reach
   [`VolumeKeyService`](app/src/main/java/dev/heynol/barhud/service/VolumeKeyService.kt)'s accessibility key
   filter: Xiaomi's `MitvAudioService` handles them earlier. So the HUD follows the soundbar's reported value,
   ~0.3–0.5 s after a press. On devices where the filter does get the keys, the first press immediately shows
   "last volume ± 1" and the report corrects it. While keys are still arriving, reports are held back.
3. **Soundbar only.** The HUD appears only while media audio is routed to HDMI ARC/eARC. With TV speakers
   the native HUD stays in charge.
4. **Native look.** [`HudContent`](app/src/main/java/dev/heynol/barhud/hud/HudContent.kt) inflates TvSystemUI's
   own `mi_volume_vertical_dialog_layout` at runtime, with its themed pill background, spacing and text style.
   Only Xiaomi's `ProgressView` is replaced by [`VerticalTrackView`](app/src/main/java/dev/heynol/barhud/hud/VerticalTrackView.kt)
   (track `#E7E7E7`, fill `#0E98E8`, muted `#33000000`, taken from TvSystemUI). The speaker icon becomes a
   soundbar glyph. If the resources are missing, an equivalent layout is built from
   [`HudStyle`](app/src/main/java/dev/heynol/barhud/hud/HudStyle.kt).
5. **Overlay.** A non-focusable, non-touchable accessibility overlay on the right edge, vertically centred,
   hidden after 3 s. It sits above apps and HDMI inputs and never takes focus. The window is attached once
   and parked at alpha 0 when hidden. The compositor skips it, but the GPU pipeline stays warm, so showing it
   costs a single frame.

## Volume per input

Streaming apps come out at very different loudness. The app remembers the soundbar volume you settle on in each
input and restores it when you switch there:

- **Input detection:** [`ForegroundInputTracker`](app/src/main/java/dev/heynol/barhud/service/ForegroundInputTracker.kt)
  uses accessibility window-state events (package name only). Launchable TV apps count as inputs (`app:com.netflix.ninja`).
  All HDMI inputs run inside Xiaomi's LiveTV app, so for LiveTV the port comes from the `TvSourcesStatus` global
  setting (`hdmi1|HDR10` → `hdmi:hdmi1`). Overlays such as quick settings or the assistant keep the current input.
  The home screen isn't an input, so nothing is learned or restored there.
- **Learning:** every volume the soundbar settles at while an input is in front is stored for that input,
  including changes made with the soundbar remote.
- **Restoring:** in adjust-only absolute volume mode Android ignores `setStreamVolume`, so the controller walks
  the soundbar to the target with `adjustStreamVolume`, one CEC step at a time. Each step waits for the next
  `<Report Audio Status>` (~2–3 steps/s). The walk starts 1.5 s after the switch, so zapping through inputs changes
  nothing. Any volume key press cancels it, and it is skipped while muted. The HUD shows the steps.
- The setup screen lists the stored volumes, and you can switch the feature off or forget all values.

## Remote workarounds

Setup screen, section 5. All are on by default.

- **Undo mute from pressing "down" twice.** Xiaomi's `MitvAudioService` mutes 360 ms after two "volume down"
  presses within 750 ms, and there is no setting for it. A report that turns mute on within 1.5 s of the volume
  going down is undone at once (`ADJUST_UNMUTE`, again over CEC). Sound drops for about a second. The mute key
  on its own is left alone. If you press mute right after turning the volume down, that mute is undone too.
- **Netflix button.** Google TV remotes (e.g. from a Google TV Streamer paired to the TV) send `BUTTON_4` for
  Netflix. Xiaomi's firmware ignores it, so the service consumes it and launches `com.netflix.ninja`.
- **Handle volume keys in this app.** Consumes volume up/down while the soundbar is active, applies them with
  `adjustStreamVolume` and repeats held keys one confirmed step at a time. This only works where volume keys
  reach accessibility filters. On the tested Xiaomi they don't, so it has no effect there.

## Install

On the TV: Settings → System → About → click *Android TV OS build* 7× → Developer options → enable
*Wireless debugging* → *Pair device with pairing code*.

```bash
adb pair <tv-ip>:<pairing-port> <code>
```

```bash
adb connect <tv-ip>:<connect-port>
```

```bash
./gradlew assembleRelease
```

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Enable the service. This replaces the list of enabled accessibility services, so check
`adb shell settings get secure enabled_accessibility_services` first and append with `:` if it isn't `null`:

```bash
adb shell settings put secure enabled_accessibility_services dev.heynol.barhud/dev.heynol.barhud.service.VolumeKeyService
```

Alternatively, open *Theatre Bar HUD* from the launcher. The setup screen links to Accessibility settings and shows
whether the soundbar is connected. On a sideloaded app you may first need App info → *Allow restricted settings*.

The service is bound by the system at boot and after app updates. *Clear data* in App info unbinds it,
so enable it again afterwards. Wireless debugging is only needed for
installing and can be switched off afterwards.

## Tuning

- Fades: the native HUD pops in and out without animation. Set `FADE_IN_MS` / `FADE_OUT_MS` in `HudStyle` to `0`
  for an exact match.
- Debug logging: `adb shell setprop log.tag.TvVolumeSource DEBUG` logs the detected media output on every event.

## Known limitations

- **Holding a volume key** moves the soundbar only two steps: `MitvAudioService` sends one step on key down and
  one on key up, nothing in between. Apps can't intercept these keys on the Xiaomi (see above). Repeated presses
  work normally.
- `adb shell input keyevent` bypasses accessibility filters. Injected keys still change the volume and the HUD
  follows the reported value, but without the instant prediction.
