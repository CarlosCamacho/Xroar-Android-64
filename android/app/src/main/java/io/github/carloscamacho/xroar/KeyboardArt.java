package io.github.carloscamacho.xroar;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

/* How the CoCo keyboard looks: the key layout and the drawing of the body and
 * each keycap, in four styles.  CocoKeyboardView handles touch and caching;
 * tools/artpreview renders this same code to PNG on a desktop.
 *
 * A keycap is drawn as it looks from slightly in front: a soft shadow on the
 * body, the cap's sloped sides (the front skirt shows most), and the top face,
 * dished or flat, with a highlight along its back edge and the legends printed
 * on it. */
final class KeyboardArt {
    static final float COLS = 14f;
    static final int ROWS = 5;

    static final String[] STYLE_IDS = { "white", "cream", "hitek", "grey" };
    static final String[] STYLE_NAMES = {
        "White (CoCo 2)", "Cream (TRS-80 Color Computer)", "Black (Hi-Tek)", "Grey (TRS-80)" };

    static final int D_SHIFT = 0x37;
    static final int D_BREAK = 0x32;

    static final class KeyDef {
        final String label;     // as printed on the key
        final String shifted;   // second legend, printed above, or null
        final int code;         // CoCo key number (XRoar DSCAN_*)
        final int row;
        final float x, w;       // in key units

        KeyDef(String label, String shifted, int code, int row, float x, float w) {
            this.label = label;
            this.shifted = shifted;
            this.code = code;
            this.row = row;
            this.x = x;
            this.w = w;
        }

        boolean special() {
            return label.length() > 1 || code == 0x2b || code == 0x2c || code == 0x2d || code == 0x2e;
        }
    }

    static final class Style {
        final int face, legend, body, special;
        final float gap;          // between caps, fraction of a key unit
        final float round;        // corner radius, fraction of the cap's short side
        final float insX, insTop, insBottom;   // how far the top face sits inside the cap's footprint
        final boolean dished;     // sculpted (concave) face, or flat chiclet
        final int gloss;          // highlight strength on the face's back edge
        final int grain;          // plastic texture strength

        Style(int face, int legend, int body, int special, float gap, float round,
              float insX, float insTop, float insBottom, boolean dished, int gloss, int grain) {
            this.face = face;
            this.legend = legend;
            this.body = body;
            this.special = special;
            this.gap = gap;
            this.round = round;
            this.insX = insX;
            this.insTop = insTop;
            this.insBottom = insBottom;
            this.dished = dished;
            this.gloss = gloss;
            this.grain = grain;
        }
    }

    private static final Style[] STYLES = {
        // White CoCo 2: rounded sculpted caps with visible gaps.
        new Style(0xFFF2F1EC, 0xFF262626, 0xFFC3C2BB, 0xFFE4E3DC, 0.055f, 0.17f, 0.085f, 0.045f, 0.17f, true, 0x90, 0x0C),
        // Cream TRS-80: typewriter caps, tighter.
        new Style(0xFFEEE5C8, 0xFF2C281F, 0xFF4B4539, 0xFFDDD3B2, 0.035f, 0.11f, 0.08f, 0.04f, 0.15f, true, 0x80, 0x0E),
        // Black Hi-Tek: tall square caps that nearly touch.
        new Style(0xFF26262A, 0xFFF0F0F0, 0xFF09090A, 0xFF1C1C20, 0.012f, 0.07f, 0.07f, 0.05f, 0.16f, true, 0x38, 0x16),
        // Grey TRS-80: flat chiclets.
        new Style(0xFFBBBDBF, 0xFF151515, 0xFF55585C, 0xFFA6A9AC, 0.03f, 0.12f, 0.05f, 0.03f, 0.10f, false, 0x70, 0x10),
    };

    static final int BREAK_FACE = 0xFFCC3228;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint T = new Paint(Paint.ANTI_ALIAS_FLAG);

    private KeyboardArt() {
    }

    static int styleIndex(String id) {
        for (int i = 0; i < STYLE_IDS.length; i++) {
            if (STYLE_IDS[i].equals(id)) {
                return i;
            }
        }
        return 0;
    }

    static Style style(int i) {
        return STYLES[Math.max(0, Math.min(STYLES.length - 1, i))];
    }

    /* The real layout, 14 units wide, each row stepped in a little:
     *   1 2 3 4 5 6 7 8 9 0 : -  BREAK
     *   UP Q W E R T Y U I O P @ LEFT RIGHT
     *   DOWN A S D F G H J K L ; ENTER CLEAR
     *   SHIFT Z X C V B N M , . / SHIFT
     *   SPACE */
    static List<KeyDef> layout() {
        List<KeyDef> k = new ArrayList<>();
        String[] digits = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" };
        String[] digitsShift = { "!", "\"", "#", "$", "%", "&", "'", "(", ")", "Ø" };
        float x = 0.5f;
        for (int i = 0; i < 10; i++) {
            k.add(new KeyDef(digits[i], digitsShift[i], i == 9 ? 0 : i + 1, 0, x++, 1));
        }
        k.add(new KeyDef(":", "*", 0x0a, 0, x++, 1));
        k.add(new KeyDef("-", "=", 0x0d, 0, x++, 1));
        k.add(new KeyDef("BREAK", null, D_BREAK, 0, x, 1.2f));

        k.add(new KeyDef("↑", null, 0x2b, 1, 0, 1));
        x = 1;
        for (char ch : "QWERTYUIOP".toCharArray()) {
            k.add(new KeyDef(String.valueOf(ch), null, 0x11 + (ch - 'A'), 1, x++, 1));
        }
        k.add(new KeyDef("@", null, 0x10, 1, x++, 1));
        k.add(new KeyDef("←", null, 0x2d, 1, x++, 1));
        k.add(new KeyDef("→", null, 0x2e, 1, x, 1));

        k.add(new KeyDef("↓", null, 0x2c, 2, 0.25f, 1));
        x = 1.25f;
        for (char ch : "ASDFGHJKL".toCharArray()) {
            k.add(new KeyDef(String.valueOf(ch), null, 0x11 + (ch - 'A'), 2, x++, 1));
        }
        k.add(new KeyDef(";", "+", 0x0b, 2, x++, 1));
        k.add(new KeyDef("ENTER", null, 0x30, 2, x, 1.6f));
        k.add(new KeyDef("CLEAR", null, 0x31, 2, x + 1.6f, 1.1f));

        k.add(new KeyDef("SHIFT", null, D_SHIFT, 3, 0, 1.5f));
        x = 1.5f;
        for (char ch : "ZXCVBNM".toCharArray()) {
            k.add(new KeyDef(String.valueOf(ch), null, 0x11 + (ch - 'A'), 3, x++, 1));
        }
        k.add(new KeyDef(",", "<", 0x0c, 3, x++, 1));
        k.add(new KeyDef(".", ">", 0x0e, 3, x++, 1));
        k.add(new KeyDef("/", "?", 0x0f, 3, x++, 1));
        k.add(new KeyDef("SHIFT", null, D_SHIFT, 3, x, 1.5f));

        k.add(new KeyDef("SPACE", null, 0x2f, 4, 3f, 8f));
        return k;
    }

    /* The space between two caps, in pixels. */
    static float gapPx(int w, int h, Style st) {
        return Math.max(0.8f, Math.min(w / COLS, h / (float) ROWS) * st.gap);
    }

    /* Where a key's cap sits, in a keyboard w x h pixels. */
    static void keyRect(KeyDef k, int w, int h, Style st, RectF out) {
        float cw = w / COLS, ch = h / (float) ROWS;
        float gap = gapPx(w, h, st);
        float top = h * 0.03f;   // the body's lip above the first row
        float rh = (h - top) / ROWS;
        out.set(k.x * cw + gap, top + k.row * rh + gap, (k.x + k.w) * cw - gap, top + (k.row + 1) * rh - gap);
    }

    /* The keyboard body the keys sit in. */
    static void drawBody(Canvas c, int w, int h, Style st) {
        RectF r = new RectF(0, 0, w, h);
        ArtUtil.vgrad(c, P, r, 0, new int[] { ArtUtil.shade(st.body, 1.12f), st.body, ArtUtil.shade(st.body, 0.86f) },
                new float[] { 0f, 0.4f, 1f });
        ArtUtil.texture(c, P, ArtUtil.grain(), r, 0, st.grain + 6);
        // the keys sit down in a well: shade under the front lip of the case
        P.setShader(new LinearGradient(0, 0, 0, h * 0.07f, 0x80000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.07f, P);
        P.setShader(null);
        // the lip itself catches the light
        P.setColor(0x50FFFFFF);
        c.drawRect(0, 0, w, Math.max(1f, h * 0.006f), P);
    }

    /* One keycap.  kind: the face color is the style's face unless this is BREAK
     * or a special key; tint (0 for none) recolors the cap (SHIFT on). */
    static void drawKey(Canvas c, RectF r, KeyDef k, Style st, boolean pressed, boolean shifted, int tint,
                        boolean shadow) {
        boolean isBreak = k.code == D_BREAK;
        int face = isBreak ? BREAK_FACE : (k.special() ? st.special : st.face);
        int ink = isBreak ? 0xFFFFFFFF : st.legend;
        if (tint != 0) {
            face = ArtUtil.mix(face, tint, 0.55f);
        }
        float kw = r.width(), kh = r.height();
        float rad = Math.min(kw, kh) * st.round;
        boolean dark = lum(face) < 0.25f;
        float press = pressed ? kh * st.insBottom * 0.55f : 0;
        if (pressed) {
            face = ArtUtil.shade(face, dark ? 1.15f : 0.93f);
        }

        // soft contact shadow on the body
        if (shadow) {
            P.setMaskFilter(new BlurMaskFilter(Math.max(1f, kh * 0.07f), BlurMaskFilter.Blur.NORMAL));
            P.setStyle(Paint.Style.FILL);
            P.setColor(dark ? 0xC0000000 : 0x90000000);
            c.drawRoundRect(new RectF(r.left + kw * 0.02f, r.top + kh * 0.08f, r.right - kw * 0.02f, r.bottom + kh * 0.06f),
                    rad, rad, P);
            P.setMaskFilter(null);
        }

        // the cap's sides: darker toward the bottom, darker at the left and right edges
        RectF base = new RectF(r.left, r.top + press, r.right, r.bottom);
        ArtUtil.vgrad(c, P, base, rad, new int[] {
            ArtUtil.shade(face, dark ? 1.45f : 0.86f), ArtUtil.shade(face, dark ? 1.10f : 0.74f),
            ArtUtil.shade(face, dark ? 0.70f : 0.56f) }, new float[] { 0f, 0.6f, 1f });
        P.setShader(new LinearGradient(base.left, 0, base.right, 0,
                new int[] { 0x38000000, 0x00000000, 0x00000000, 0x48000000 }, new float[] { 0f, 0.14f, 0.86f, 1f },
                Shader.TileMode.CLAMP));
        c.drawRoundRect(base, rad, rad, P);
        P.setShader(null);

        // the top face
        float bot = kh * st.insBottom - press;
        RectF f = new RectF(r.left + kw * st.insX * (kh / kw > 1 ? 1 : kh / kw) + kh * 0.01f,
                r.top + kh * st.insTop + press, r.right - kw * st.insX * (kh / kw > 1 ? 1 : kh / kw) - kh * 0.01f,
                r.bottom - Math.max(bot, kh * 0.035f));
        float frad = rad * 0.85f;
        if (st.dished) {
            // concave: the back of the dish is in shade, the front catches light
            ArtUtil.vgrad(c, P, f, frad, new int[] {
                ArtUtil.shade(face, dark ? 0.85f : 0.91f), face, ArtUtil.shade(face, dark ? 1.25f : 1.03f) },
                    new float[] { 0f, 0.45f, 1f });
        } else {
            ArtUtil.vgrad(c, P, f, frad, new int[] {
                ArtUtil.shade(face, dark ? 1.3f : 1.04f), face, ArtUtil.shade(face, dark ? 0.9f : 0.97f) },
                    new float[] { 0f, 0.5f, 1f });
        }
        ArtUtil.texture(c, P, ArtUtil.grain(), f, frad, st.grain);
        // edge where face meets side
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, kh * 0.012f));
        P.setColor(dark ? 0x30FFFFFF : 0x22000000);
        c.drawRoundRect(f, frad, frad, P);
        P.setStyle(Paint.Style.FILL);
        // light along the back edge of the face
        P.setShader(new LinearGradient(0, f.top, 0, f.top + kh * 0.12f,
                ArtUtil.alpha(0xFFFFFFFF, st.gloss), 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(f.left + frad * 0.3f, f.top, f.right - frad * 0.3f, f.top + kh * 0.12f),
                frad * 0.6f, frad * 0.6f, P);
        P.setShader(null);

        legends(c, f, k, ink, shifted, kh);
    }

    private static void legends(Canvas c, RectF f, KeyDef k, int ink, boolean shifted, float kh) {
        String main = k.label;
        String sub = k.shifted;
        if (shifted && sub != null && !k.label.equals("0")) {
            // SHIFT is down: the shifted symbol is what you'll get; bring it forward
            String t = main;
            main = sub;
            sub = t;
        }
        T.setTextAlign(Paint.Align.CENTER);
        T.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        T.setColor(ink);
        float size = kh * 0.40f;
        if (main.length() > 2) {
            size = Math.min(kh * 0.22f, f.width() / (main.length() * 0.68f));
        }
        T.setTextSize(size);
        boolean two = sub != null;
        float cy = (two ? f.top + f.height() * 0.74f : f.centerY()) - (T.descent() + T.ascent()) / 2f;
        c.drawText(main, f.centerX(), cy, T);
        if (two) {
            T.setTextSize(kh * 0.25f);
            T.setColor(ArtUtil.alpha(ink, shifted ? 0x80 : 0xFF));
            c.drawText(sub, f.centerX(), f.top + f.height() * 0.40f, T);
        }
    }

    private static float lum(int c) {
        return (0.3f * ((c >> 16) & 255) + 0.59f * ((c >> 8) & 255) + 0.11f * (c & 255)) / 255f;
    }
}
