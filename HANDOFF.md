# Current Baton Pass
- **Current State:** `main` (85d7335) is v13, the BUIK restyle with bundled Archivo fonts, and builds cleanly. Branch `claude/dazzling-brahmagupta-oukj0i` is 3 commits ahead (v14 big self-centering joysticks, virtual joystick, full screen, light/dark theme, icons, hamburger menu; v15 system theme default, virtual joystick side and motion, keystore as text). v15 builds cleanly (`build.sh`, `apksigner` verify) but v14 and v15 are not merged and have not been run on a phone.
- **Immediate Next Step:**
  - Open a PR for `claude/dazzling-brahmagupta-oukj0i` into `main` once the owner has tried the v15 APK on a phone (virtual joystick motion, Folder opening the file manager, light theme are untested).
  - Add `XROAR_KEYPASS` and `XROAR_KEYSTORE_B64` (from the key file given to the owner) to the cloud environment so every build is signed with one key; until then a build in a fresh container will not install over an earlier APK.
- **Dead Ends / Do Not Repeat:** None recorded yet.
- **Verification:** Run `ANDROID_HOME=/path/to/android-sdk ./build.sh` and confirm zero errors before committing.
