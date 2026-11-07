# Changelog

## Unreleased

### v11 (versionCode 11)

- **emesDOS09 smart cartridge** (patch 0006), Carlos Camacho's own cartridge DOS
  (github.com/CarlosCamacho/EmEsDOS09): the 16K ROM in two 8K banks at `$C000`
  (bit 7 of `$FF40` picks the bank) and the SD controller at `$FF40-$FF4F`. The
  controller is the project's own PIC32 firmware command code (`main.c` and the
  FAT16 modules, MIT, commit `a41c25a`), with two changes: its SD sector hooks read
  the image file, and one `memcpy` of a 7-byte literal no longer reads 8 bytes. The
  SD card is a raw FAT16 image attached as XRoar's hard disk 0. `EMESDOS09_TRACE=1`
  logs every controller command.
- App: Machine > Cartridge > "emesDOS09 smart cartridge"; Files > emesDOS09 SD card
  (Insert / Eject, copied to `emesdos/`); Machine > ROM files > emesDOS09 ROM (saved
  as `roms/emesdos09.rom`). Tapping a 16K emesDOS09 ROM in Paks (recognized by "DK"
  plus its banner) now selects this cartridge instead of a plain ROM Pak.

### Learned

- [SIM] The shipped 1.0.0-rc2 ROM (CRC32 `b1e58f8f`) with its FAT16 image, on this
  cartridge: RESET, IDENTIFY ("E9CT", protocol 2.2), STATUS, MEDIA, VOLUME_MOUNT,
  RTC and both LED commands succeed, bank 1 passes the ROM test, and the ROM
  launches the shell loader. The loader then issues commands `$3F`, `$5E`, `$15`
  carrying 4 bytes of garbage, and the screen stops at an orange console. That
  matches emesDOS09 issue #3 as the owner logged it on 2026-10-18 [EMU] (SWI2
  vectors into the `E9RV` data at `$0103`, and `NEG <$40...` with DP=$FF writes the
  `$FF4x` registers). It is a ROM bug, not the cartridge; a ROM with #3 fixed is
  needed to go further.
- [SIM] ~~That matches #3~~ Confirmed at the CPU, not just matched: with a
  breakpoint (`EMES_BP=0103` in `tools/hosttest`), the 6809 executes `$0103` right
  after the last good controller command (LED off), and every garbage command comes
  after it. Fixing #3 alone would not be enough: the next step stages `COMMAND.D9`
  at `$E000` (emesDOS09 issue #4), which reads back as cartridge ROM on this
  cartridge too, since the SAM's TY bit is never set.
- [SIM] The firmware model reports version 0.17.0; the rc2 ROM prints "CTRL UNKNOWN"
  for anything but 0.11.0. That is cosmetic: it accepts protocol 2.2.
- [SRC] The firmware's `command_dir_open` copies 8 bytes from the 7-byte literal
  `"A:\\*.*"` (gcc `-Wstringop-overread`). Reported here, not changed in that repo.

### v10 (versionCode 10)

- Keycaps redrawn from the owner's four keyboard photos: the top face smaller
  than the footprint and set toward the back, so the front skirt shows tallest,
  the left skirt is lit and the right one is in shade. Small medium-weight legends
  at the top left (centered on the grey chiclets), words and arrows sized to fit
  inside the face with a margin, the space bar blank. The CoCo 2's arrow, SHIFT,
  ENTER and CLEAR caps are whiter than its cream letter caps. Spacing per keyboard:
  close on the CoCo 2 and TRS-80, nearly touching on the Hi-Tek, wide on the grey
  chiclets on their black mat.

### v9 (versionCode 9)

- The owner's MaterialKolor theme (dark scheme) for the whole UI: `Theme.java`
  holds the roles, `res/values/styles.xml` gives the same colors to the system
  widgets (the app theme is now `Theme.Material`, not the old pre-Holo theme).
  Buttons, cards and dialogs have slightly rounded (6-8 dp) corners, not pills.
- Keyboard and joysticks redrawn for a photographic look: soft shadows, sloped
  keycap sides, dished faces, plastic grain, brushed aluminium, glossy plastic.
  The drawing moved to `KeyboardArt` / `JoystickArt` (android.graphics only);
  the views cache the still parts in bitmaps.
- The Radio Shack joystick's button is at its top edge.
- `tools/artpreview/run.sh` renders that same drawing code to PNG on a desktop,
  through Java2D stand-ins for android.graphics, so the art can be checked
  without a phone.

### Learned

- [SIM] tools/artpreview is a check of the drawing, not of the phone: Java2D's
  gradients, text and blur are close to Android's but not identical.

### v8 (versionCode 8)

- Settings restyled after Material Design 3 (dark): tonal buttons, outlined
  drop-downs, tinted switches and sliders, tab bar with an indicator. Every tab is a
  set of accordion cards that open and close (the first one starts open; the state
  is kept while the app runs).
- Sound cartridge moved from Machine to Audio. "Type text" removed from Input (the
  toolbar has it).

### v7 (versionCode 7)

- **Tiny picture fix (a guess, needs your phone).** The v6 screenshot showed the
  picture as a 47% box in the bottom-left of its view, which matches SDL still
  thinking the window is 640x480 (its default) inside a larger surface. The glue now
  asks EGL for the real buffer size, and the app also reports its view size
  (`OP_SURFACE`); the glue resizes SDL's window and viewport to match and logs
  `android: display: window ... egl/app ...` in `xroar-log.txt`. [SIM] only the
  "follows the reported size" part is tested (`== surface`); that this is the
  phone's cause is [SRC] inference from the screenshot.
- Three joysticks drawn from above (Input > Joystick type): Radio Shack black
  (not self-centering, one button), TRS-80 Deluxe (self-centering), Wico (red ball,
  two buttons: base and ball tip).
- Keycap gap and roundness differ per style (Hi-Tek keys nearly touch).
- Soft/hard reset removed from the Machine tab (the toolbar's Reset has them).

### v6 (versionCode 6)

- **ROM manager.** Machine > ROM files: Replace… and Remove for Color BASIC,
  Extended BASIC, the disk controller and SDC-DOS. A picked file is identified by
  CRC-32 against XRoar's own table (`RomCatalog.java`, generated by
  `tools/gen_romcatalog.py`), named correctly, and any other version of that ROM is
  removed so the new one is the one XRoar finds. A wrong-kind ROM offers the right
  slot; a Pak offers to go in Paks; an unknown file asks first. The CoCo is rebuilt
  so it reads the new ROM.
- **Paks.** `paks/` folder and a Paks browser (toolbar): search, tap to insert and
  start, long-press to delete, Add… (several files), Eject. Known Paks show their
  catalog titles; CoCo 3 titles are flagged.
- **Keyboard.** The real CoCo layout (stepped rows, two SHIFTs, red BREAK, ENTER and
  CLEAR, a long SPACE) with the shifted symbol printed above each key, in four
  keycap styles: white CoCo 2 (default), cream TRS-80, black Hi-Tek, grey TRS-80.
- **Landscape.** Picture on the left, full height, 4:3; on the right the toolbar
  and a Keyboard / Joystick tab. Settings and Paks replace the tab.
- **Type…** can open a text file. Program text is checked as it changes (line
  numbers, 249-character lines); Type is off while a line has a problem, unless
  "Leave out lines with problems" is on.
- **Sound cartridges.** Games Master Cartridge and CoCoXSID (an emulated SID chip,
  patch 0005) in the Multi-Pak beside the disk controller. One at a time.
- **Picture.** Fills the width by default: zoomed picture with 60 Hz scaling off
  (100% x 100% of the 4:3 area, all 16 rows). New "sharp bilinear" scaling is the
  default (patch 0004).

### Learned

- [SIM] Real Color BASIC keeps a typed line to 249 characters and silently cuts
  the rest off. It is not 255. A longer program line would be quietly corrupted, so
  the Type dialog refuses it.
- [SIM] A Pak inserted into a running Disk BASIC machine hangs it: Disk BASIC's
  RAM hooks point into the disk ROM. Inserting a Pak must reset the machine.
- [SIM] With XRoar's default picture ("auto") and 60 Hz scaling on, the green area
  is 80% x 96% of the window and stretched (aspect 1.108). That was the small
  picture. `zoomed` with 60 Hz scaling off is 100% x 100% at 4:3.
- [SIM] RGB and S-Video give identical pixels in XRoar's active area; only the
  border level and the palette differ. A sharpness difference seen on a phone is
  the scaler, not the input. Measured edge widths: linear 2.92 px, sharp bilinear
  1.50 px, nearest 1.00 px.
- [SIM] The SID core is checked against the data sheet in `tools/sid_test.c`
  (pitch, pulse width, envelope times, volume, filters, sync, readback).
- [HW] None of the v6 Java (keyboard drawing, landscape layout, panels, dialogs,
  pickers) has run on a phone; the build machine has no emulator.

- **Android front end.** CoCo on-screen keyboard (sticky SHIFT, BREAK, CLEAR,
  arrows), on-screen joystick pad, Type... dialog using the phone's own keyboard,
  toolbar (Keys, Pad, Type, Reset, Menu), and a settings screen modelled on
  XRoar Online (Machine, Video, Audio, Input, Files). Settings apply live and are
  saved in `settings.conf`. Disk, tape and program files come from Android's file
  picker. versionCode 5.
- Native control layer (`android_control.c`): commands queued from the UI and run
  on the emulator thread; a message channel so the app can tell the user what
  happened (a disk mounted, "already in the other SD drive"); a display-size
  check that logs what the renderer reports.
- CoCoSDC drive eject hook (patch 0003). A disk that fails to mount on an SD
  drive no longer falls back to a floppy drive.
- `tools/hosttest/run.sh`: a desktop test suite for the glue (see README).

- **CoCo 1/2 only.** CoCo 3 and MC-10 are configured out; patch 0002 removes the
  Dragon machines and the DragonDOS and Delta carts, and makes `cocous` the
  default. The `coco3.rom` / machine auto-selection is gone. versionCode 4.

- Data folder moved from `Android/data/<package>/files/` to
  `Android/media/<package>/`, so file managers can put ROMs there.
- Machine chosen from the ROMs when `xroar.conf` has no `default-machine`:
  `coco3.rom` gives `coco3`, `bas13.rom` etc. give `cocous`. The starter config no
  longer forces a CoCo 3.

### Learned

- [SIM] XRoar's virtual cassette output file is closed by a reset, so a test that
  reads BASIC's output from `-tape-write` fails after any reset and looks like
  broken typing. The printer (`-lp-file`, `PRINT #-2`) survives resets and
  machine rebuilds.
- [SIM] XRoar rebuilds the whole machine from its config whenever it is told to
  select a machine (`ui_tag_machine`), so a live RAM change works by setting
  `mc->ram`, resetting `mc->ram_org` to auto (else it keeps the 64K chip layout
  and builds 0K), and selecting the same machine again. That also drops the
  cartridge, so the app re-sends its cartridge setting.
- [SIM] A sticky SHIFT needs a guard: its release must wait for the pending
  release of the key typed with it, or the key can be seen unshifted for a few
  frames. A test where SHIFT had only just been pressed passed without the guard
  (SHIFT's own minimum hold covered it) and proved nothing; the test has to
  latch SHIFT first.
- [SRC] XRoar's `-ccr`, `-vo-picture`, `-vo-colour`, `-invert-text` and
  `-vo-scale-60hz` are the option names for the web UI's Renderer, Picture,
  Color, Inverse text and 60Hz scaling; `-no-cart` is `-cart` with its argument
  inverted; muting is `-ao-gain -50` (the sound code treats below -49.9 as zero).
- [HW] Android 11+ gives file managers "Access denied" on `Android/data/<pkg>/`
  (reported on the owner's phone). `Android/media/<pkg>/` is writable by file
  managers and by the app, with no permission, via `getExternalMediaDirs()`.
- [SIM] The desktop test build's Makefile is regenerated by `make` when
  `Makefile.am` is newer, which silently drops the ROMPATH substitution and makes
  every ROM "unpopulated". `build.sh` greps for the substitution after applying it.

- [SIM] The Dragons cannot be switched off at configure time: XRoar builds the
  Dragon and CoCo 1/2 machines as one family (`--enable-dragon`). Removing them
  takes a patch to the built-in machine list and the part registry.
- [SIM] A way to check a headless emulator boots a BASIC machine without a
  screen: type `OPEN "O",#-1,"X"` / `PRINT #-1,"TEXT"` through `-type`, give
  `-tape-write FILE`, and read the text back out of the cassette file.

## First import (2026-11-07)

Moved from a Claude Code working session into this repository.

- XRoar `128d7c64` for Android 7.0+ on `arm64-v8a`, `armeabi-v7a` and
  `x86_64`, SDL2 2.30.9, 16 KB-aligned native libraries.
- CoCoSDC from the nowhereman999 fork (`39ff2609`), moved onto upstream
  (`patches/0001-...`). Auto-inserted when `sdcdos.rom` is present; SD card =
  `files/sdcard/`; "Open with" mounts disk images as SDC drive 0.
- `build.sh`: pinned, out-of-tree, reproducible build of all of the above.

### Learned

- [SIM] XRoar's path expansion only handles a leading `~/`. A bare `~` in
  ROMPATH/CONFPATH is never searched, so the starter `xroar.conf` was silently
  ignored and the app started as a Dragon 64. Use `~/.`.
- [SRC] Android's bionic provides `creal`/`cimag` from API 23, and `fseeko` with
  64-bit offsets on 32-bit ABIs from API 24. Hence minSdk 24.
- [SRC] Android has no `pthread_cancel`, so XRoar's GDB target must be configured out.
- [SRC] SDL2's Android headers rename `main` to `SDL_main`, so XRoar's
  configure link test fails unless `SDL_MAIN_HANDLED` is defined.
- [SRC] Upstream XRoar's own `cocosdc.c` (behind `--enable-experimental`)
  is a ROM-and-latch stub with no disk access. The working emulation is the
  fork's.
- [SRC] Android 15+ refuses an APK targeting below API 24, and 64-bit-only
  phones refuse 32-bit-only native code. Those were the two reasons the 2014
  XRoar 0.31 APK would not install.
