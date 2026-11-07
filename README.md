# Xroar-Android-64

[XRoar](https://www.6809.org.uk/xroar/), Ciaran Anscomb's Dragon and Tandy
Color Computer emulator, built for current Android phones and trimmed to the
**Tandy Color Computer 1 and 2** (no CoCo 3, no Dragons, no MC-10): **64-bit ARM**
(`arm64-v8a`), 32-bit ARM (`armeabi-v7a`) and `x86_64` (Chromebooks), Android
7.0 (API 24) and later, with 16 KB page alignment for Android 15+.

It also adds **CoCoSDC** support (the emulation comes from the
[nowhereman999 fork of XRoar](https://github.com/nowhereman999/xroar), moved
onto current upstream XRoar) and an Android front end modelled on
[XRoar Online](https://colorcomputerarchive.com/xroar-online/): a CoCo
on-screen keyboard, an on-screen joystick, and a settings screen.

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

Everything lives in `Android/media/io.github.carloscamacho.xroar/`, which the app
creates on first launch. (Not `Android/data/`: Android 11 and later stop file
managers writing there, but `Android/media/<package>/` stays open to them.)

| Path | What it is |
|---|---|
| `paks/` | Cartridge images (`.rom`, `.ccc`) for the Paks browser |
| `roms/` | ROM images (`bas13.rom`, `extbas11.rom`, `disk11.rom`, `sdcdos.rom`, ...); the folder itself is searched too |
| `settings.conf` | What the settings screen saves (see below); read at start-up |
| `xroar.conf` | XRoar's own config, for anything the settings screen doesn't cover; the settings screen overrides it |
| `disks/`, `tapes/` | Copies of disk and tape images picked from the settings screen |
| `sdcard/` | The CoCoSDC's SD card: `.DSK` images, optional `STARTUP.CFG` (`0=GAME.DSK`) |
| `xroar-log.txt` | Everything XRoar printed this run; send it with any bug report |
| `opened/` | Copies of files opened with "Open with XRoar" |

Copy ROMs there with any file manager that can reach `Android/media`, from a
computer over USB, or with
`adb push FILE /sdcard/Android/media/io.github.carloscamacho.xroar/roms/`.
Launch the app once first so the folder exists, then restart it after adding files.

### The screen

In portrait the picture is at the top (4:3, full width) with a toolbar above it
and the keyboard, joystick pad, settings or Paks below. In landscape the picture
is on the left at full height, and the right-hand column has the toolbar and a
**Keyboard / Joystick** tab; Settings and Paks replace the tab while open.

| Toolbar | |
|---|---|
| **Keys** | Show or hide the CoCo keyboard |
| **Pad** | Show or hide the on-screen joystick (and point a joystick port at it) |
| **Type...** | Type text with your phone's own keyboard, for pasting programs |
| **Reset** | Soft reset (the RESET button) or hard reset (power cycle) |
| **Paks** | The cartridge browser (below) |
| **Menu** | The settings screen |

**CoCo keyboard.** The real CoCo layout (stepped rows, two SHIFT keys, ENTER and
CLEAR, red BREAK, the arrows) with each shifted symbol printed above its key.
Menu > Input > Keyboard style picks the keycaps: white (CoCo 2, the default),
cream (TRS-80), black (Hi-Tek) or grey (TRS-80). It presses CoCo keys.
SHIFT is sticky: tap once for the next key only, tap twice quickly to lock it,
tap again to let go (Menu > Input turns this off). Several fingers work at once.

**Joystick.** A direction pad and two fire buttons. It drives XRoar's keyboard
joystick, so a port has to use "On-screen pad" (the Pad button sets the right
port for you). The other option is "Touch screen", where the joystick follows
your finger over the picture.

**Type...** opens your phone's keyboard, or a text file (Open text file…); the
text is typed into the CoCo (in capitals unless you turn that off, since BASIC
wants capitals). For a BASIC program every line must start with a line number and
be at most **249** characters: a real Color BASIC cuts a longer line off without
a word (measured), so the dialog shows the problem lines and will not type until
they are fixed or you tick "Leave out lines with problems". A Bluetooth keyboard
also works directly.

**Paks.** Put `.rom` / `.ccc` cartridge images in `paks/` (or use Add… in the Paks
browser). Tap one to insert it: the CoCo resets and starts it, because a Disk BASIC
machine cannot take a cartridge swap without a reset. Known Paks show their real
titles; Eject puts the normal cartridge back.

**ROMs.** Machine > ROM files replaces or removes Color BASIC, Extended BASIC, the
disk controller ROM and SDC-DOS from a file you pick. The app recognizes the file
by checksum and files it under the right name.

**Sound.** Machine > Sound cartridge: Games Master Cartridge or CoCoXSID (SID chip
at $FF40). One at a time, in the Multi-Pak with the disk controller.

**Settings** apply at once while the emulator keeps running, and are saved:

| Tab | What it has |
|---|---|
| Machine | CoCo model (NTSC/PAL, 2B), RAM (4/16/32/64 KB), cartridge (disk controller, CoCoSDC, none), sound cartridge, soft and hard reset, ROM files (replace/remove) |
| Video | TV input (composite blue-red / red-blue for artifact colors, S-Video, RGB), color renderer, picture size, scaling, brightness, contrast, color, hue, 60 Hz scaling, color killer, inverse text |
| Audio | Mute and volume |
| Input | Right and left joystick, on-screen pad, keyboard options, Type text |
| Files | Four disk drives (insert/eject), cassette (insert, play/pause, rewind, eject), Load file and Run file |

Disk, tape and program files are picked with Android's file picker and copied
into the app folder.

**Which machine starts.** An NTSC CoCo (`cocous`) unless Machine says
otherwise. It needs `bas13.rom` (or `bas12`/`bas11`/`bas10`) for Color BASIC,
`extbas11.rom` (or `extbas10`) for Extended BASIC, and `disk11.rom` (or `disk10`)
as the disk controller; the Machine tab shows which it found.

### CoCoSDC

Put the SDC-DOS ROM in `roms/` as `sdcdos.rom` (it is not bundled). When it is
there and Cartridge is "Automatic", the app plugs in a CoCoSDC and serves
`sdcard/` as its SD card. Without the ROM you keep the normal floppy controller.
Machine > Cartridge chooses explicitly.

With a CoCoSDC, drives 1 and 2 (SD drives 0 and 1) are the SD card's: a disk
picked or opened is copied to `sdcard/OPENED/` and mounted, and writes from the
CoCo go to that copy. The same disk image can only be in one SD drive; the app
says so rather than quietly using a floppy drive. "Open with" on a disk image
mounts it as SD drive 0 and types `DOS`.

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
  directory to the `Android/media/<package>/` folder that `MainActivity` passes
  in `XROAR_HOME`, redirects the log, writes the starter `xroar.conf`, and turns
  `settings.conf` into XRoar command-line options (so the first boot already has
  the chosen machine, cartridge and picture settings).
- `android/glue/android_control.c` is the control layer. `build.sh` compiles XRoar
  unchanged apart from the patches, then rebuilds two objects with one rename
  each: `main_unix.c` with `-Dmain=xroar_unix_main`, and `sdl2/ui_sdl2.c` with
  `-Dxroar_run=android_xroar_run`. Each time XRoar's loop gives the emulator a
  slice, `android_xroar_run()` runs the commands the Android UI queued (key
  presses, joystick, resets, disks, tape, typing, live settings), keeps the
  picture sized to the real surface, and logs the sizes it sees, then calls the
  real `xroar_run()`. Everything XRoar is asked to do happens on its own thread.
  Live settings use XRoar's own `ui_update_state()` messages, the same ones its
  web build's controls use; a RAM or machine change makes XRoar rebuild the
  machine.
- `MainActivity.java` loads `libSDL2.so` and `libmain.so` and builds the UI around
  SDL's view: `CocoKeyboardView` (the keyboard; it sends CoCo key numbers, XRoar's
  `DSCAN_*`), `PadView` (joystick), `SettingsPanel` (the tabs) and `Settings`
  (`settings.conf`). It copies `content://` files into the app folder, since
  native code can't open them.
- Build-time settings, each forced by Android:
  - CoCo 1/2 only: configure is run with `--disable-coco3 --disable-mc10`, and
    patch 0002 removes the Dragon machine and cart definitions and stops
    building the Dragon-only parts. The Dragon and CoCo 1/2 machines share one
    code base, so there is no configure switch for the Dragons alone.
  - minSdk 24, for bionic's complex-math functions and 32-bit `fseeko`.
  - The GDB target is off, because Android has no `pthread_cancel`.
  - `SDL_MAIN_HANDLED` is set, so SDL doesn't rename `main()`.
  - The data folder is `Android/media/<package>/`, not `Android/data/`, so
    file managers can write to it on Android 11+.
  - ROMPATH/CONFPATH are rewritten to `~/roms:~/.` and `~/.`, because XRoar only expands `~/`.

## Testing status

Venues: **[HW]** real phone, **[EMU]** Android emulator, **[SIM]** our desktop
harness, **[SRC]** read from source.

- [HW] The owner's phone ran an earlier build (CoCo 1/2 only, before the new
  screen) and showed the Disk Extended Color BASIC 1.1 banner and `OK` prompt,
  from the owner's own `bas13`, `extbas11` and `disk11` ROMs. **That build drew the
  picture as a small box in the bottom-left corner** and had no way to type.
  The new screen layout, keyboard, joystick pad and settings panel have **not been
  run on a phone**: the build machine has no KVM, so no Android emulator boots.
  The display fix is a best effort: the layout now gives SDL an exact 4:3 surface
  and the native side asks the renderer for its real size and logs it
  (`android: display: ...` in `xroar-log.txt`).
- [SIM] `tools/hosttest/run.sh` runs the glue against a Linux build of the same
  patched XRoar (offscreen SDL, stand-ins for the Android-only calls, real JNI
  entry points called through a fake `JNIEnv`) with the owner's real ROMs, and
  checks what the emulated CoCo actually sees, through its printer port
  (`PRINT #-2`; the cassette file is closed by a reset, the printer is not):
  - key presses through the on-screen keyboard's path type into BASIC, and so
    does auto-typing; a shifted key typed with SHIFT latched earlier needs the
    guard that holds SHIFT until the key's release (the test fails without it);
  - the on-screen joystick reads `JOYSTK(0)` = 0 with Left held, 31 centred;
  - settings become the right XRoar options at start-up;
  - live settings, soft and hard reset, a machine change, tape and disk commands
    run without a crash, and a live RAM change shows in BASIC (`MEM` 24871 on 64K,
    8487 on 16K);
  - with a CoCoSDC (a placeholder `sdcdos.rom`, so no SDC-DOS actually runs),
    disks mount on the SD card, a second mount of the same image is refused with
    no floppy fallback, eject works, and drive 2 stays a floppy.
  - a Pak (a tiny 6809 program) inserted live resets and starts; a program of
    long lines types intact up to 249 characters and is cut there (measured);
  - the CoCoXSID and the Games Master Cartridge load in the Multi-Pak, live or at
    start-up, and the SID registers read and write from BASIC (`tools/sid_test.c`
    checks the SID core against the data sheet);
  - the picture geometry (100% x 100%, 4:3) and the scaling filters, measured from
    dumped frames.
  Java logic with no Android calls (ROM identify/install/remove, the Paks scan and
  filter, the BASIC line checker) has unit tests in `tools/javatest/`.
  Not covered: all of the Java that draws or touches (keyboard, layout, panels,
  dialogs, pickers), audio you can hear.
- [SRC] The CoCoSDC code was reviewed but its own test programs have not been
  run here. Paths stay inside the SD folder, buffers are bounded, and there is
  no networking except an opt-in Becker port.
- Android lint on the Java reports only style warnings in our files.
  Packaging is checked by `build.sh` on every build: `apksigner` verify,
  `zipalign -c -P 16`, `aapt` badging.

## Licence

GPL-3.0-or-later, as XRoar (see `LICENSE`). SDL2 is zlib-licensed; its Java
glue is copied in at build time.
