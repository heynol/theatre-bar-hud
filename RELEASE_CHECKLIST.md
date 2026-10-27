# Release checklist: v1.0.0

Everything technical is prepared on `release/open-source-1.0`. What is left needs the maintainer's
approval. Run the commands from the repository root, in this order.

## Before you start

- [ ] **Back up the signing key.** `~/Keystores/heynol-sideload.jks` **and**
      `~/Keystores/heynol-sideload.properties` (contains the passwords) to a password manager or an
      encrypted backup. Without them no update with the same signature can ever be published. The key is
      shared with Afterglow.
- [ ] The release APK exists locally (it is git-ignored). Rebuild it if needed:

  ```bash
  ./gradlew clean testDebugUnitTest assembleRelease && cp app/build/outputs/apk/release/app-release.apk dist/theatre-bar-hud-1.0.0.apk && (cd dist && shasum -a 256 theatre-bar-hud-1.0.0.apk > theatre-bar-hud-1.0.0.apk.sha256)
  ```

  Expected signer: `CN=Pascal Heynol`, SHA-256
  `A0:BD:A1:33:7D:24:4E:53:D8:11:6A:2D:A6:A0:BA:26:AD:2B:90:57:C5:87:6E:07:AD:EB:BD:E1:00:94:B3:81`.

## 1. Merge the PR

```bash
gh pr merge release/open-source-1.0 --merge --delete-branch
```

```bash
git switch main && git pull
```

## 2. Rename the repository

```bash
gh repo rename theatre-bar-hud --repo heynol/Xiaomi-TV-Theatre-Bar-Visuals --yes
```

```bash
git remote set-url origin https://github.com/heynol/theatre-bar-hud.git
```

(Use `git@github.com:heynol/theatre-bar-hud.git` instead if you clone over SSH.) GitHub redirects the old
name, but every link in the repo already points to the new one.

## 3. Description and topics

```bash
gh repo edit heynol/theatre-bar-hud --description "Shows your soundbar's real volume (HDMI-CEC over eARC) in the TV's native volume HUD on Android TV / Google TV, and remembers the volume per app and HDMI input." --homepage "https://github.com/heynol/theatre-bar-hud/releases/latest" --add-topic android-tv --add-topic google-tv --add-topic hdmi-cec --add-topic earc --add-topic soundbar --add-topic accessibility-service --add-topic kotlin
```

## 4. Make it public

```bash
gh repo edit heynol/theatre-bar-hud --visibility public --accept-visibility-change-consequences
```

Optional, once public: enable private vulnerability reporting and create the `compatibility` label used
by the issue template.

```bash
gh label create compatibility --repo heynol/theatre-bar-hud --color 0E8A16 --description "Compatibility report for a TV or soundbar"
```

## 5. Release v1.0.0

On `main` after the merge, so the tag points at the merged commit:

```bash
gh release create v1.0.0 dist/theatre-bar-hud-1.0.0.apk dist/theatre-bar-hud-1.0.0.apk.sha256 --repo heynol/theatre-bar-hud --target main --title "Theatre Bar HUD 1.0.0" --notes-file dist/RELEASE_NOTES.md
```

Check afterwards that the download link from the README works:
<https://github.com/heynol/theatre-bar-hud/releases/latest>

## 6. Social preview

Web UI only: *Settings → General → Social preview → Edit → Upload an image* →
`docs/media/social-preview.png` (1280×640).

## Later

- Phone video of the real setup following [`docs/media/SHOTLIST.md`](docs/media/SHOTLIST.md), then add it to
  the README.
- Portfolio section "Experiments": link the repo and use `docs/media/hero.webp` or `social-preview.png`.
