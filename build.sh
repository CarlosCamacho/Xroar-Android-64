#!/usr/bin/env bash
# Build XRoar for Android (64-bit and 32-bit ARM, x86_64), with CoCoSDC.
#
#   ./build.sh            fetch sources, build SDL2 + XRoar per ABI, build the APK
#   ABIS=arm64-v8a ./build.sh      one ABI only (faster; the APK then has only it)
#
# Needs: ANDROID_HOME (or ANDROID_SDK_ROOT) with ndk;$NDK_VERSION,
# build-tools;$BUILD_TOOLS and platforms;android-35 installed; JDK 17+;
# Gradle 8.9+ on PATH (or GRADLE=/path/to/gradle); git, cmake, ninja, make,
# autoconf, automake, and Perl's Text::Iconv (XRoar's win2rc script uses it).
#
# Release signing: XROAR_KEYSTORE=/path/to/key.p12 XROAR_KEYPASS=... ./build.sh
# (XROAR_KEYALIAS names the key in it, default "xroar".)  An environment that can
# only hold text can pass the keystore as XROAR_KEYSTORE_B64, its base64.
# Without those, the APK is signed with the Android debug key.
#
# Everything is built under $WORK (default ./build), outside the source trees.

set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)

# - - - Pinned inputs - - - - - - - - - - - - - - - - - - - - - - - - - - - -

XROAR_URL=https://www.6809.org.uk/git/xroar.git
XROAR_COMMIT=128d7c645fc2ed96cc9425f163dc5b927ca32d91   # 2026-11-04
SDL_URL=https://github.com/libsdl-org/SDL.git
SDL_TAG=release-2.30.9
SDL_COMMIT=c98c4fbff6d8f3016a3ce6685bf8f43433c3efcc
NDK_VERSION=27.3.13750724                               # r27d
BUILD_TOOLS=35.0.1
API=24                                                  # minSdk; see README

ABIS=${ABIS:-"arm64-v8a armeabi-v7a x86_64"}
WORK=${WORK:-$HERE/build}
JOBS=${JOBS:-$(nproc 2>/dev/null || echo 4)}
GRADLE=${GRADLE:-gradle}

# - - - Tools - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

die() { echo "build.sh: $*" >&2; exit 1; }
step() { echo; echo "==> $*"; }

# A keystore given as text: write it out for gradle (mode 600, under $WORK, which is git-ignored).
if [ -n "${XROAR_KEYSTORE_B64:-}" ] && [ -z "${XROAR_KEYSTORE:-}" ]; then
	mkdir -p "$WORK"
	(umask 077; printf '%s' "$XROAR_KEYSTORE_B64" | base64 -d > "$WORK/release-key.p12") \
		|| die "XROAR_KEYSTORE_B64 is not valid base64"
	export XROAR_KEYSTORE=$WORK/release-key.p12
fi

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[ -n "$SDK" ] || die "set ANDROID_HOME to the Android SDK"
NDK=$SDK/ndk/$NDK_VERSION
[ -d "$NDK" ] || die "NDK $NDK_VERSION not found: sdkmanager \"ndk;$NDK_VERSION\""
case "$(uname -s)" in
	Linux) HOSTTAG=linux-x86_64 ;;
	Darwin) HOSTTAG=darwin-x86_64 ;;
	*) die "unsupported build host $(uname -s)" ;;
esac
TC=$NDK/toolchains/llvm/prebuilt/$HOSTTAG/bin
for t in git cmake ninja make autoreconf perl; do
	command -v $t >/dev/null || die "missing tool: $t"
done
perl -MText::Iconv -e1 2>/dev/null || die "missing Perl module Text::Iconv (Debian/Ubuntu: libtext-iconv-perl)"

abi_triple() {
	case "$1" in
		arm64-v8a)   echo aarch64-linux-android ;;
		armeabi-v7a) echo armv7a-linux-androideabi ;;
		x86_64)      echo x86_64-linux-android ;;
		*) die "unknown ABI $1" ;;
	esac
}

# - - - Sources - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

SRC=$WORK/src
mkdir -p "$SRC"

step "XRoar $XROAR_COMMIT + patches"
if [ ! -d "$SRC/xroar/.git" ]; then
	git clone -q "$XROAR_URL" "$SRC/xroar"
fi
git -C "$SRC/xroar" cat-file -e "$XROAR_COMMIT^{commit}" 2>/dev/null || git -C "$SRC/xroar" fetch -q origin
git -C "$SRC/xroar" checkout -q -f --detach "$XROAR_COMMIT"
git -C "$SRC/xroar" clean -q -f -d -x
for p in "$HERE"/patches/*.patch; do
	echo "    $(basename "$p")"
	git -C "$SRC/xroar" apply --whitespace=nowarn "$p"
done
(cd "$SRC/xroar" && ./autogen.sh > "$WORK/autogen.log" 2>&1) || die "autogen failed: $WORK/autogen.log"

step "SDL $SDL_TAG"
if [ ! -d "$SRC/SDL/.git" ]; then
	git clone -q --depth 1 -b "$SDL_TAG" "$SDL_URL" "$SRC/SDL"
fi
[ "$(git -C "$SRC/SDL" rev-parse HEAD)" = "$SDL_COMMIT" ] || die "SDL checkout is not $SDL_COMMIT"

# - - - Per-ABI native build - - - - - - - - - - - - - - - - - - - - - - - - -

JNILIBS=$HERE/android/app/src/main/jniLibs
rm -rf "$JNILIBS"

for ABI in $ABIS; do
	HOST=$(abi_triple "$ABI")
	CC=$TC/${HOST}${API}-clang
	PREFIX=$WORK/$ABI/prefix
	B=$WORK/$ABI/xroar
	LOG=$WORK/$ABI/log
	mkdir -p "$LOG"

	step "$ABI: SDL2"
	cmake -S "$SRC/SDL" -B "$WORK/$ABI/sdl" -G Ninja \
		-DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
		-DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-21 \
		-DCMAKE_BUILD_TYPE=Release -DSDL_STATIC=OFF -DSDL_SHARED=ON -DSDL_TEST=OFF \
		-DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
		-DCMAKE_INSTALL_PREFIX="$PREFIX" > "$LOG/sdl.log" 2>&1 \
		&& ninja -C "$WORK/$ABI/sdl" install >> "$LOG/sdl.log" 2>&1 \
		|| die "SDL build failed: $LOG/sdl.log"

	step "$ABI: XRoar"
	rm -rf "$B"; mkdir -p "$B/tools"
	# CoCo 1/2 only: "--enable-dragon" is the Dragon/CoCo 1/2 machine family
	# (one code base; patch 0002 removes the Dragon machine definitions),
	# CoCo 3 and MC-10 are switched off.
	# SDL_MAIN_HANDLED: SDL's Android headers otherwise rename main() and
	# configure's link test fails.  The GDB target needs pthread_cancel,
	# which Android lacks.
	(cd "$B" && "$SRC/xroar/configure" --host="$HOST" \
		CC="$CC" AR="$TC/llvm-ar" RANLIB="$TC/llvm-ranlib" STRIP="$TC/llvm-strip" \
		CFLAGS="-O2 -fPIC" LDFLAGS="-Wl,-z,max-page-size=16384" PKG_CONFIG=/bin/false \
		SDL_CFLAGS="-I$PREFIX/include/SDL2 -D_REENTRANT -DSDL_MAIN_HANDLED" \
		SDL_LIBS="-L$PREFIX/lib -lSDL2" \
		--enable-dragon --disable-coco3 --disable-mc10 \
		--without-x --without-gtk3 --without-gtk2 --without-gtkgl --without-sdl3 \
		--without-opengl --without-alsa --without-oss --without-pulse --without-evdev \
		--without-joydev --without-libpng --disable-sdltest --disable-gdb-target \
		> "$LOG/configure.log" 2>&1) || die "configure failed: $LOG/configure.log"
	grep -q '^#define HAVE_SDL2 1' "$B/config.h" || die "configure did not find SDL2: $LOG/configure.log"

	# ROMs and xroar.conf live in the app's files directory (HOME).  XRoar
	# only expands "~/", so a bare "~" element would never be searched.
	sed -i.bak \
		-e 's|-DROMPATH=\\"[^\\]*\\"|-DROMPATH=\\"~/roms:~/.:~/.xroar/roms\\"|' \
		-e 's|-DCONFPATH=\\"[^\\]*\\"|-DCONFPATH=\\"~/.:~/.xroar\\"|' "$B/src/Makefile"
	grep -q 'ROMPATH=\\"~/roms:~/\.:' "$B/src/Makefile" || die "ROMPATH substitution failed"

	cp "$SRC/xroar/tools/win2rc" "$B/tools/"   # VPATH builds look for it here
	make -C "$B/portalib" -j"$JOBS" > "$LOG/make.log" 2>&1 \
		&& make -C "$B/src" -j"$JOBS" >> "$LOG/make.log" 2>&1 \
		|| die "make failed: $LOG/make.log"
	# Rebuild two objects with one rename each, same flags otherwise:
	#   main_unix.c  main() -> xroar_unix_main(), called from SDL_main()
	#   ui_sdl2.c    xroar_run() -> android_xroar_run(), the file hand-off
	rm -f "$B/src/xroar-main_unix.o" "$B/src/sdl2/xroar-ui_sdl2.o"
	make -C "$B/src" xroar-main_unix.o CFLAGS="-O2 -fPIC -Dmain=xroar_unix_main" >> "$LOG/make.log" 2>&1
	make -C "$B/src" sdl2/xroar-ui_sdl2.o CFLAGS="-O2 -fPIC -Dxroar_run=android_xroar_run" >> "$LOG/make.log" 2>&1
	for f in "$HERE"/android/glue/*.c; do
		"$CC" -O2 -fPIC -Wall \
			-I"$PREFIX/include/SDL2" -I"$B" -I"$SRC/xroar" -I"$SRC/xroar/src" -I"$SRC/xroar/portalib" \
			-I"$HERE/android/glue" -DHAVE_CONFIG_H -D_REENTRANT -DSDL_MAIN_HANDLED \
			-c "$f" -o "$B/glue-$(basename "$f" .c).o"
	done
	"$CC" -shared -o "$B/libmain.so" $(cd "$B" && find src -name 'xroar-*.o' | sort | sed "s|^|$B/|") \
		"$B"/glue-*.o "$B/portalib/libporta.a" \
		-L"$PREFIX/lib" -lSDL2 -lz -lm -llog -landroid -lEGL \
		-Wl,-z,max-page-size=16384 -Wl,--no-undefined -Wl,-soname,libmain.so
	"$TC/llvm-strip" --strip-unneeded "$B/libmain.so"

	mkdir -p "$JNILIBS/$ABI"
	cp "$PREFIX/lib/libSDL2.so" "$B/libmain.so" "$JNILIBS/$ABI/"
	"$TC/llvm-strip" --strip-unneeded "$JNILIBS/$ABI/libSDL2.so"
	echo "    libmain.so $(wc -c < "$JNILIBS/$ABI/libmain.so") bytes"
done

# - - - APK - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

step "APK"
SDLJAVA=$HERE/android/app/src/main/java/org/libsdl/app
rm -rf "$SDLJAVA"; mkdir -p "$SDLJAVA"
cp "$SRC/SDL"/android-project/app/src/main/java/org/libsdl/app/*.java "$SDLJAVA/"
echo "sdk.dir=$SDK" > "$HERE/android/local.properties"
# Only the ABIs just built go into the APK.
"$GRADLE" -p "$HERE/android" --no-daemon -q assembleRelease -PxroarAbis="$(echo $ABIS | tr ' ' ',')" \
	> "$WORK/gradle.log" 2>&1 || { tail -30 "$WORK/gradle.log"; die "gradle failed: $WORK/gradle.log"; }

mkdir -p "$WORK/out"
APK=$WORK/out/xroar-android.apk
cp "$HERE/android/app/build/outputs/apk/release/app-release.apk" "$APK"

BT=$SDK/build-tools/$BUILD_TOOLS
if [ -x "$BT/apksigner" ]; then
	"$BT/apksigner" verify "$APK" || die "apksigner rejected $APK"
	"$BT/zipalign" -c -P 16 4 "$APK" || die "native libraries not 16 KB aligned"
	"$BT/aapt" dump badging "$APK" | grep -E "^package|sdkVersion|native-code"
fi
step "done: $APK"
