#!/bin/sh
# Render the keyboard and joystick art to PNG on a desktop: tools/artpreview/run.sh OUT_DIR
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=$HERE/../../android/app/src/main/java/io/github/carloscamacho/xroar
OUT=${1:-$HERE/out}
CLS=$(mktemp -d)
javac -nowarn -d "$CLS" "$HERE"/shim/android/graphics/*.java "$SRC/ArtUtil.java" "$SRC/KeyboardArt.java" \
	"$SRC/JoystickArt.java" "$HERE/Preview.java" 2>&1 | grep -v "^Picked up" || true
java -Djava.awt.headless=true -cp "$CLS" io.github.carloscamacho.xroar.Preview "$OUT" 2>&1 | grep -v "^Picked up"
rm -rf "$CLS"
