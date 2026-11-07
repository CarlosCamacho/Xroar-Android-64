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
	mkdir -p "$T/paks"; python3 -I -c "
# A tiny Program Pak: at \$C000, print PAKSTART OK on the printer through BASIC's CHROUT, then spin.
code = bytes([0x86,0xFE, 0x97,0x6F, 0x8E,0xC0,0x15, 0xA6,0x80, 0x27,0x06, 0xAD,0x9F,0xA0,0x02, 0x20,0xF6, 0x0F,0x6F, 0x20,0xFE]) + b'PAKSTART OK\\r\\0'
open('$T/paks/test.rom','wb').write(code + bytes(8192 - len(code)))"; head -c 161280 /dev/zero > "$T/blank.dsk"; head -c 161280 /dev/zero > "$T/blank2.dsk"
	(cd /; XROAR_HOME=$T TEST_PAK=$T/paks/test.rom TEST_DSK=$T/blank.dsk TEST_DSK2=$T/blank2.dsk SCENARIO=$sc SDL_VIDEODRIVER=offscreen SDL_AUDIODRIVER=dummy \
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
echo "== pak: a cartridge in paks/ can be inserted and ejected"
T=$(run pak pak "cart=rsdos")
paks=$(tr '\r\n' '  ' < "$T/out.txt")
echo "  printer: [$paks]"
check "disk ROM at \$C000 before (68)" "$T/out.txt" "A= 68"
check "the pak started itself and printed (PAKSTART OK)" "$T/out.txt" "PAKSTART OK"
check "disk ROM back at \$C000 after ejecting (68)" "$T/out.txt" "C= 68"
check "message for the app" "$T/xroar-log.txt" "Cartridge: test.rom"
echo "== program: a typed multi-line program arrives intact, and 249 is the real line limit"
python3 -I -c "
lines = ['10 REM TEST PROGRAM', '20 FOR I=1 TO 3', '30 PRINT I', '40 NEXT I', '50 PRINT \"DONE\"',
         '60 REM ' + 'X' * 242,            # exactly 249 characters
         '70 DATA 1,2,3', '80 A\$=\"ABC\"', '90 PRINT A\$',
         '100 REM ' + 'Y' * 248,           # 256 characters: past the limit
         '110 END']
open('$W/program.bas', 'w').write(chr(10).join(lines) + chr(10))"
T=$(TEST_PROGRAM=$W/program.bas TYPE_WAIT=14 run program program)
python3 -I - "$T/out.txt" <<'PY' && echo "  ok   all 11 lines listed; the 249-character line intact; the 256-character line cut to 249" || { echo "  FAIL program did not come back as expected"; fail=1; }
import re, sys
got = {}
for l in open(sys.argv[1], 'rb').read().decode('latin-1').replace('\r', '\n').split('\n'):
    m = re.match(r'(\d+) ', l)
    if m: got[int(m.group(1))] = l
ok = sorted(got) == [10,20,30,40,50,60,70,80,90,100,110]
ok = ok and got[10] == '10 REM TEST PROGRAM' and got[20] == '20 FOR I=1 TO 3' and got[80] == '80 A$="ABC"'
ok = ok and len(got[60]) == 249 and got[60] == '60 REM ' + 'X' * 242
ok = ok and len(got[100]) == 249
print('lines:', sorted(got), 'len60:', len(got.get(60, '')), 'len100:', len(got.get(100, '')), file=sys.stderr)
sys.exit(0 if ok else 1)
PY
echo "== sound: CoCoXSID and Games Master Cartridge in a Multi-Pak with the disk controller"
sidcheck() {  # run dir
	local T=$1
	check "ENV3 reads full level (the SID is answering in SCS)" "$T/out.txt" "ENV= 255"
	check "the disk ROM is still at \$C000 (CTS stayed on the disk slot)" "$T/out.txt" "C= 68"
	python3 -I -c "
import re,sys
t=open(sys.argv[1],'rb').read().decode('latin-1').replace('\r','\n')
m=re.search(r'OSC=\s*(\d+)\s+(\d+)\s+(\d+)',t)
sys.exit(0 if m and len({m.group(1),m.group(2),m.group(3)})>=2 else 1)" "$T/out.txt" \
		&& echo "  ok   OSC3 changes between reads (the oscillator runs)" || { echo "  FAIL OSC3 did not change"; fail=1; }
	check "Multi-Pak was built with the SID in slot 1" "$T/xroar-log.txt" "part:cocoxsid"
}
T=$(run sid sid "sound=cocoxsid" "cart=rsdos"); echo "  printer: [$(tr '\r\n' '  ' < "$T/out.txt")]"; sidcheck "$T"
check "SID produced audio (peak logged when the cartridge was removed)" "$T/xroar-log.txt" "cocoxsid\] [0-9]* audio frames, peak level 0\.[1-9]"
T=$(run sidlive sidlive "cart=rsdos"); echo "  live: [$(tr '\r\n' '  ' < "$T/out.txt")]"; sidcheck "$T"
check "app told the user" "$T/xroar-log.txt" "Cartridge: Multi-Pak with cocoxsid and disk"
T=$(run gmc gmc "sound=gmc" "cart=rsdos"); echo "  gmc: [$(tr '\r\n' '  ' < "$T/out.txt")]"
check "GMC in slot 1 took SN76489 writes and BASIC carried on" "$T/out.txt" "GMC DONE C= 68"
check "Multi-Pak was built with the GMC" "$T/xroar-log.txt" "part:gmc"
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
echo "== picture: default settings fill the width at a true 4:3, sharply"
shot() {  # name, settings.conf text ("" for none) -> "fill_w fill_h aspect ramp"
	local T=$W/run-$1; rm -rf "$T"; mkdir -p "$T/roms"
	cp "$ROMS"/bas13.rom "$ROMS"/extbas11.rom "$ROMS"/disk11.rom "$T/roms/"
	printf "$2" > "$T/settings.conf"
	(cd /; XROAR_HOME=$T SCENARIO=screenfull TAIL=8 XROAR_DUMP_FRAME=$T/frame.ppm XROAR_DUMP_DELAY_MS=9000 \
		SDL_VIDEODRIVER=offscreen SDL_AUDIODRIVER=dummy timeout 40 "$B/xroar-hosttest" -g 1080x810 > /dev/null 2>&1 || true)
	python3 -I "$HERE/tools/hosttest/measure.py" "$T/frame.ppm"
}
read fw fh asp ramp <<< "$(shot pic-default '')"
echo "  default: fills $fw x $fh of the surface, aspect $asp, edge ramp $ramp px"
python3 -I -c "import sys; fw,fh,a,r=map(float,sys.argv[1:]); sys.exit(0 if fw>0.99 and fh>0.99 and 1.31<a<1.36 else 1)" $fw $fh $asp $ramp \
	&& echo "  ok   fills the surface at 4:3" || { echo "  FAIL picture does not fill the surface at 4:3"; fail=1; }
python3 -I -c "import sys; fw,fh,a,r=map(float,sys.argv[1:]); sys.exit(0 if r<1.9 else 1)" $fw $fh $asp $ramp \
	&& echo "  ok   pixel edges are sharp (under 1.9 px)" || { echo "  FAIL pixel edges are soft"; fail=1; }
read fw2 fh2 asp2 ramp2 <<< "$(shot pic-old 'picture=auto\nntsc_scaling=1\ngl_filter=linear\n')"
echo "  old settings: fills $fw2 x $fh2, aspect $asp2, ramp $ramp2 (what v5 showed)"
python3 -I -c "import sys; fw,fh,a,r=map(float,sys.argv[1:]); sys.exit(0 if (fw<0.9 or a<1.2) and r>2.2 else 1)" $fw2 $fh2 $asp2 $ramp2 \
	&& echo "  ok   the test does tell the old look from the new one" || { echo "  FAIL measure.py cannot tell old from new"; fail=1; }
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
