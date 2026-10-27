# Shotlist: phone video of the real setup

Goal: one 5–10 s loop that proves the HUD is real. TV with the HUD **and** the soundbar in one frame, a hand
with the remote pressing volume, the HUD counting up.

## Settings

- **Phone:** 4K or 1080p, **30 fps** (matches the TV's refresh better than 60 and avoids flicker), main
  1× lens. Not ultra-wide (distorts the TV edges).
- **Lock exposure and focus** on the TV screen (long-press on iPhone, *Pro* mode on Android). Expose for the
  screen so the HUD isn't blown out, then brighten the room a little if the soundbar disappears.
- **Shutter** 1/60 s or 1/30 s if the camera allows it, to avoid rolling bands on the TV.
- **Tripod** or phone resting on a stack of books. No handheld: the loop has to cut seamlessly.
- **Landscape**, 16:9.

## Frame

- TV slightly off-centre, the right third of the screen (where the HUD appears) clearly visible.
- Soundbar fully in frame below the TV, its front display readable if possible (it shows the same number).
- Remote in a hand entering from the bottom right, pointing at the TV. Volume key visible, no logo close-ups.
- On screen: a calm, bright but not white app UI or the home screen. Avoid content with logos or faces;
  HDMI sources are fine here (they are only black in screen recordings).
- Nothing personal in view: no photos, mail, notifications, Wi-Fi names, account avatars.

## Avoid reflections and moiré

- Lights off behind the camera, curtains closed or shoot at dusk; no window or lamp reflected in the screen.
- Wear dark clothes, stand slightly to the side: you will otherwise appear in the black parts of the screen.
- **Moiré:** don't fill the frame with the screen. Step back, zoom 1×, and focus slightly *in front of* the
  screen (on the soundbar) if stripes appear. Check at 100 % on the phone before recording the take.
- Lower the TV brightness one or two steps if the HUD halos.

## Action (one take, 8–10 s)

1. 0–1 s: still frame, HUD hidden.
2. 1–4 s: press volume up 4–6 times at a steady pace. The HUD appears and counts up; the soundbar display
   follows.
3. 4–6 s: press mute. Bar and icon turn grey.
4. 6–7 s: press mute again.
5. 7–10 s: hand leaves the frame, HUD fades out after 3 s → loop point matches frame 1.

Tip: the demo trigger (`adb shell am broadcast -a dev.heynol.barhud.DEMO -n dev.heynol.barhud/.service.DemoReceiver`)
plays the same sequence without changing the volume. Use it for a version without a hand, but then the
soundbar display won't move.

## Deliver

- Original file plus a trimmed loop (`setup-loop.mp4`, H.264, 1280×720, no audio, < 5 MB).
- One still from the take as `setup-photo.jpg` (< 400 KB). Both go to `docs/media/`.
