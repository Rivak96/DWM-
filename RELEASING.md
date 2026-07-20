# Releasing an update

The in-app updater reads `version.json` from this repo's `main` branch and pulls the
APK from the matching GitHub Release. To ship an update:

1. **Bump the version** in `app/build.gradle.kts` (`versionCode` +1, `versionName`).
2. **Run the release script** (updates `version.json` + builds the signed APK):
   ```powershell
   ./release.ps1 -Notes "What changed in this version"
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`, and `version.json`
   updated to match.
3. **Commit & push**:
   ```
   git add -A && git commit -m "vX.Y.Z" && git push
   ```
4. **Create the GitHub Release**: tag `vX.Y.Z` (must match `version.json`'s `tag`),
   and **attach `app-release.apk`** as a release asset.
   - Web: Releases → Draft a new release → choose the tag → drag in the APK.
   - Or with the `gh` CLI:
     ```
     gh release create vX.Y.Z app/build/outputs/apk/release/app-release.apk -t "vX.Y.Z" -n "notes"
     ```

On the deck, DWM will now find the update (Settings → About → Check for updates, or
auto-check on start) and offer one-tap install.

## Rules
- **Same keystore every time.** `dwm-release.keystore` must never change or updates
  won't install over the app. Back it up; it is gitignored (never committed).
- The repo must be **public** (or release assets public) for the deck to download
  without a login.
- `version.json` `versionCode` must exactly match the built APK's `versionCode`.
