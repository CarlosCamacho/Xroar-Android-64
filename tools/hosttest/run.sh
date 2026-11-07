#!/usr/bin/env bash
# Desktop test of the Android glue against a Linux build of XRoar.
#
#   tools/hosttest/run.sh <xroar-source-dir> <sdl2-prefix> <work-dir> <rom-dir>
#
#   xroar-source-dir  XRoar patched like build.sh does (build/src/xroar), autogen.sh run
#   sdl2-prefix       a desktop SDL2 install (headers in include/SDL2)
#   work-dir          scratch directory (built and run in here)
#   rom-dir           folder with bas13.rom, extbas11.rom, disk11.rom
#
# It builds XRoar for the desktop with the same configure options as
# build.sh, links in android/glue/*.c with stand-ins for the Android calls
# (stub.c), and runs scenarios that check what the emulated CoCo sees.
# Needs: a JDK (for jni.h), xvfb is not needed (SDL's offscreen driver).

set -euo pipefail

HERE=$(cd "$(dirname "$0")/../.." && pwd)
SRC=$(cd "$1" && pwd); P=$(cd "$2" && pwd); W=$3; ROMS=$(cd "$4" && pwd)
mkdir -p "$W"; W=$(cd "$W" && pwd)
B=$W/xroar
JNI=$(dirname "$(find /usr/lib/jvm -name jni.h | head -1)")

if [ ! -x "$B/xroar-hosttest" ] || [ -n "${REBUILD:-}" ]; then
	rm -rf "$B"; mkdir -p "$B/tools"
	(cd "$B" && "$SRC/configure" CFLAGS="-O1 -g -fPIC -Wall" PKG_CONFIG=/bin/false \
		SDL_CFLAGS="-I$P/include/SDL2 -D_REENTRANT -DSDL_MAIN_HANDLED" SDL_LIBS="-L$P/lib -lSDL2" \
		--enable-dragon --disable-coco3 --disable-mc10 \
		--without-x --without-gtk3 --without-gtk2 --without-gtkgl --without-sdl3 \
		--without-opengl --without-alsa --without-oss --without-pulse --without-evdev \
		--without-joydev --without-libpng --disable-sdltest --disable-gdb-target > "$W/configure.log" 2>&1)
	sed -i -e 's|-DROMPATH=\\"[^\\]*\\"|-DROMPATH=\\"~/roms:~/.:~/.xroar/roms\\"|' \
	       -e 's|-DCONFPATH=\\"[^\\]*\\"|-DCONFPATH=\\"~/.:~/.xroar\\"|' "$B/src/Makefile"
	cp "$SRC/tools/win2rc" "$B/tools/"
	make -C "$B/portalib" -j4 > "$W/make.log" 2>&1
	make -C "$B/src" -j4 >> "$W/make.log" 2>&1
	grep -q 'ROMPATH=\\"~/roms' "$B/src/Makefile" || { echo "ROMPATH substitution lost"; exit 1; }
	rm -f "$B/src/xroar-main_unix.o" "$B/src/sdl2/xroar-ui_sdl2.o"
	make -C "$B/src" xroar-main_unix.o CFLAGS="-O1 -g -fPIC -Dmain=xroar_unix_main" >> "$W/make.log" 2>&1
	make -C "$B/src" sdl2/xroar-ui_sdl2.o CFLAGS="-O1 -g -fPIC -Dxroar_run=android_xroar_run" >> "$W/make.log" 2>&1
	mkdir -p "$W/inc/android"
	printf '#define ANDROID_LOG_ERROR 6\n#define ANDROID_LOG_INFO 4\nint __android_log_print(int, const char *, const char *, ...);\nconst char *SDL_AndroidGetExternalStoragePath(void);\nconst char *SDL_AndroidGetInternalStoragePath(void);\n' > "$W/inc/android/log.h"
	CF="-g -O1 -Wall -Wextra -Wno-unused-parameter -I$P/include/SDL2 -I$B -I$SRC -I$SRC/src -I$SRC/portalib -I$HERE/android/glue -I$W/inc -I$JNI -I$JNI/linux -DHAVE_CONFIG_H -D_REENTRANT -DSDL_MAIN_HANDLED"
	for f in "$HERE"/android/glue/*.c; do
		gcc $CF -include android/log.h -c "$f" -o "$B/glue-$(basename "$f" .c).o"
	done
	gcc $CF -c "$HERE/tools/hosttest/stub.c" -o "$B/stub.o"
	gcc -o "$B/xroar-hosttest" $(cd "$B" && find src -name 'xroar-*.o' | sort | sed "s|^|$B/|") \
		"$B"/glue-*.o "$B/stub.o" "$B/portalib/libporta.a" -L"$P/lib" -lSDL2 -Wl,-rpath,"$P/lib" -lz -lm -lpthread
fi

fail=0
run() {  # name scenario [settings.conf lines...]
	local name=$1 sc=$2; shift 2
	local T=$W/run-$name; rm -rf "$T"; mkdir -p "$T/roms"
	cp "$ROMS"/bas13.rom "$ROMS"/extbas11.rom "$ROMS"/disk11.rom "$T/roms/"
	[ -n "${SDCDOS:-}" ] && cp "$SDCDOS" "$T/roms/sdcdos.rom"
	: > "$T/settings.conf"; for l in "$@"; do echo "$l" >> "$T/settings.conf"; done
	head -c 161280 /dev/zero > "$T/blank.dsk"; head -c 161280 /dev/zero > "$T/blank2.dsk"
	(cd /; XROAR_HOME=$T TEST_DSK=$T/blank.dsk TEST_DSK2=$T/blank2.dsk SCENARIO=$sc SDL_VIDEODRIVER=offscreen SDL_AUDIODRIVER=dummy \
		timeout 90 "$B/xroar-hosttest" -lp-file "$T/out.txt" > /dev/null 2>&1 || true)
	echo "$T"
}
check() {  # description, file, grep pattern
	if grep -aq -- "$3" "$2" 2>/dev/null; then echo "  ok   $1"; else echo "  FAIL $1   (wanted /$3/ in $2)"; fail=1; fi
}

echo "== keys: OP_KEY presses type into BASIC"
T=$(run keys keys); check "BASIC received the typed line" "$T/out.txt" "KEYS OK"
echo "== shift: SHIFT is not released before the key typed with it"
T=$(run shiftrapid shiftrapid); check "shifted # and quotes arrive shifted" "$T/out.txt" "RAPID OK"
echo "== text: OP_TYPE auto-types"
T=$(run text text); check "BASIC received the typed line" "$T/out.txt" "TEXT OK"
echo "== joy: on-screen joystick through kjoy0"
joyval() {  # the number BASIC printed after "JOY=" (it may be split across tape blocks)
	python3 -I -c "
import re,sys
d=open(sys.argv[1],'rb').read(); i=d.find(b'JOY=')
m=re.search(rb' (\\d+)', d[i+4:i+40]) if i >= 0 else None
print(m.group(1).decode() if m else 'none')" "$1"
}
T=$(run joy joy "joy_right=kjoy0"); v=$(joyval "$T/out.txt")
[ "$v" = 0 ] && echo "  ok   Left held reads JOYSTK(0)=0" || { echo "  FAIL Left held: JOYSTK(0)=$v, wanted 0"; fail=1; }
T=$(run nojoy nojoy "joy_right=kjoy0"); v=$(joyval "$T/out.txt")
{ [ "$v" = 31 ] || [ "$v" = 32 ]; } && echo "  ok   centred reads JOYSTK(0)=$v" || { echo "  FAIL centred: JOYSTK(0)=$v, wanted 31 or 32"; fail=1; }
echo "== ram: a live RAM change rebuilds the machine"
memval() {  # the number BASIC printed after "MEM="
	python3 -I -c "
import re,sys
d=open(sys.argv[1],'rb').read(); i=d.find(b'MEM=')
m=re.search(rb' (\\d+)', d[i+4:i+40]) if i >= 0 else None
print(m.group(1).decode() if m else '0')" "$1"
}
T=$(run ram64 ram64); m64=$(memval "$T/out.txt")
T=$(run ram16 ram16); m16=$(memval "$T/out.txt")
echo "  MEM on 64K = $m64, after a live change to 16K = $m16"
{ [ "$m64" -gt 20000 ] && [ "$m16" -gt 1000 ] && [ "$m16" -lt 16000 ]; } && echo "  ok   16K machine has much less free memory" || { echo "  FAIL RAM change not visible to BASIC"; fail=1; }
echo "== settings.conf becomes start-up options"
T=$(run settings text "machine=coco2bus" "ram=32" "tv_input=cmp-br" "ccr=simple" "brightness=61" "ntsc_scaling=1" "cart=none" "gain=-6")
for a in "-machine" "coco2bus" "-ram" "-tv-input" "-ccr" "-vo-brightness" "-vo-scale-60hz" "-no-cart" "-ao-gain"; do check "argument $a" "$T/xroar-log.txt" "= $a\$"; done
check "machine really is a 2B" "$T/xroar-log.txt" "part:coco\]"
echo "== misc: live settings, resets, tape, disks (must not crash)"
T=$(run misc misc "cart=rsdos"); check "ran to the end" "$T/xroar-log.txt" "android: set gain = -50"
for a in "set machine = coco2bus" "set cart = none" "set cart = rsdos" "Floppy drive 0: blank.dsk" "Floppy drive 1: blank.dsk"; do check "$a" "$T/xroar-log.txt" "$a"; done
echo "== display: the picture follows the surface"
check "display line logged" "$T/xroar-log.txt" "android: display: window"
if [ -n "${SDCDOS:-}" ]; then
	echo "== CoCoSDC: disks go to the SD card"
	T=$(run sdc sdcdisk "cart=cocosdc")
	for a in "SD drive 0: blank.dsk" "blank.dsk is already in the other SD drive" "SD drive 1: blank2.dsk" "SD drive 1 ejected" "SD drive 0 ejected" "Floppy drive 2: blank.dsk"; do check "$a" "$T/xroar-log.txt" "$a"; done
	if grep -aq "Floppy drive 1" "$T/xroar-log.txt"; then echo "  FAIL SDC drive 1 fell back to a floppy"; fail=1; else echo "  ok   no floppy fallback for SD drives"; fi
	check "image copied onto the SD card" "$T/sdcard/OPENED/blank.dsk" ""
fi
[ $fail = 0 ] && echo "ALL PASSED" || { echo "FAILURES"; exit 1; }
