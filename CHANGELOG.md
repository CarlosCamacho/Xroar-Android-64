# Changelog

## Unreleased: first import (2026-11-07)

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
