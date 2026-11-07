# Xroar-Android-64

[XRoar](https://www.6809.org.uk/xroar/), Ciaran Anscomb's Dragon and Tandy
Color Computer emulator, built for current Android phones: **64-bit ARM**
(`arm64-v8a`), 32-bit ARM (`armeabi-v7a`) and `x86_64` (Chromebooks), Android
7.0 (API 24) and later, with 16 KB page alignment for Android 15+.

It also adds **CoCoSDC** support. The emulation comes from the
[nowhereman999 fork of XRoar](https://github.com/nowhereman999/xroar), moved
onto current upstream XRoar.

This repository holds only our code: the Android glue, the Gradle project, the
CoCoSDC patch and the build script. `build.sh` fetches XRoar and SDL2 at pinned
revisions.

| Input | Revision |
|---|---|
| XRoar | `128d7c64` (2026-11-04), from `https://www.6809.org.uk/git/xroar.git` |
| CoCoSDC code | nowhereman999/xroar `39ff2609` (2026-09-27), in `patches/` |
| SDL2 | `release-2.30.9` |
| Android NDK | r27d (`27.3.13750724`), build-tools 35.0.1, platform android-35 |

## Using it on a phone

On first launch the app creates `Android/data/io.github.carloscamacho.xroar/files/`:

| Path | What it is |
|---|---|
| `roms/` | ROM images (`coco3.rom`, `disk11.rom`, `sdcdos.rom`, ...); the files folder itself is searched too |
| `xroar.conf` | Starter config: CoCo 3, touch as the right joystick; documents the CoCoSDC options |
| `sdcard/` | The CoCoSDC's SD card: `.DSK` images, optional `STARTUP.CFG` (`0=GAME.DSK`) |
| `xroar-log.txt` | Everything XRoar printed this run; send it with any bug report |
| `opened/` | Copies of files opened with "Open with XRoar" |

Android 11 and later keep most file managers out of `Android/data`, so copy
files there from a computer over USB, or use
`adb push FILE /sdcard/Android/data/io.github.carloscamacho.xroar/files/roms/`.

- **Back button** shows or hides the on-screen keyboard. A Bluetooth keyboard
  gives you BREAK, CLEAR and XRoar's shortcut keys.
- **Touch** drives the right joystick (`joy-right mjoy0`); a touch is the fire button.
- **"Open with XRoar"** on a `.DSK`, `.CAS`, `.WAV`, `.BIN` or `.ROM` file loads
  it and tries to autorun it, whether or not XRoar is already running.

### CoCoSDC

Put the SDC-DOS ROM in `roms/` as `sdcdos.rom` (it is not bundled). When it is
there, the default machine is a CoCo, and `xroar.conf` has no `machine-cart`
line, the app plugs in a CoCoSDC and serves `sdcard/` as its SD card. Without
the ROM you keep the normal floppy controller.

"Open with" on a disk image then copies it to `sdcard/OPENED/`, mounts it as
SDC drive 0 and types `DOS`, as XRoar does for a floppy. Writes from the CoCo go
to that copy, and opening the same file again replaces it.

To choose the cartridge yourself, add to `xroar.conf`:

```
machine coco3
  machine-cart cocosdc     # or: machine-cart rsdos
```

## Building

```
export ANDROID_HOME=/path/to/android-sdk     # with ndk;27.3.13750724, build-tools;35.0.1, platforms;android-35
./build.sh                                   # -> build/out/xroar-android.apk
ABIS=arm64-v8a ./build.sh                    # one ABI, faster
XROAR_KEYSTORE=key.p12 XROAR_KEYPASS=... ./build.sh   # sign with your own key
```

The build host needs JDK 17+, Gradle 8.9+ (`GRADLE=` to point at one), git,
cmake, ninja, make, autoconf, automake and Perl's `Text::Iconv`
(`libtext-iconv-perl`). It has been run on Linux only.

Without a keystore the APK is signed with the Android debug key. An APK signed
with one key cannot be installed over one signed with another; uninstall first.

### How the port works

- `android/glue/android_main.c` is `SDL_main()`. It sets `HOME` and the working
  directory to the app's files folder, redirects the log, writes the starter
  config, adds `-sdc-root` and `-cart cocosdc` when they apply, and maps Back
  to the on-screen keyboard.
- `build.sh` compiles XRoar unchanged apart from the patch, then rebuilds two
  objects with one rename each: `main_unix.c` with `-Dmain=xroar_unix_main`, and
  `sdl2/ui_sdl2.c` with `-Dxroar_run=android_xroar_run`. That second hook lets
  a file opened while XRoar runs be loaded on the emulator thread.
- `MainActivity.java` loads `libSDL2.so` and `libmain.so`, copies a
  `content://` file to a real path, and passes it to native code.
- Build-time settings, each forced by Android:
  - minSdk 24, for bionic's complex-math functions and 32-bit `fseeko`.
  - The GDB target is off, because Android has no `pthread_cancel`.
  - `SDL_MAIN_HANDLED` is set, so SDL doesn't rename `main()`.
  - ROMPATH/CONFPATH are rewritten to `~/roms:~/.` and `~/.`, because XRoar only expands `~/`.

## Testing status

Venues: **[HW]** real phone, **[EMU]** Android emulator, **[SIM]** our desktop
harness, **[SRC]** read from source.

- **Not yet run on any Android device or emulator.** The build machine had no
  KVM, so no Android emulator could boot.
- [SIM] Earlier, before CoCoSDC was added: the glue ran against the same XRoar code in a Linux
  desktop build (`tools/hosttest/`, offscreen SDL, stand-ins for the two
  Android-only SDL calls). That covered the files-folder setup, config and ROM
  search paths, starting as a CoCo 3, `-run` at start-up, and a disk opened
  while running. One bug was found that way and fixed (the bare `~` path
  entries above).
- [SRC] The CoCoSDC code was reviewed but **has not been executed**. Paths stay
  inside the SD folder, buffers are bounded, and there is no networking except
  an opt-in Becker port. The fork's own test programs are included in the patch
  (`tools/cocosdc_*_test.c`, `tools/run-cocosdc-tests.sh`) and have not been run here.
- Android packaging is checked by `build.sh` on every build: `apksigner` verify,
  `zipalign -c -P 16`, `aapt` badging.

## Licence

GPL-3.0-or-later, as XRoar (see `LICENSE`). SDL2 is zlib-licensed; its Java
glue is copied in at build time.
