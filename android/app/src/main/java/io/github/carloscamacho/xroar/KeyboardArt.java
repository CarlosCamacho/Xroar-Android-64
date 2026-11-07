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
        final int face;           // letter and number caps
        final int mod;            // arrows, SHIFT, ENTER, CLEAR (whiter on the CoCo 2)
        final int legend;
        final int body;           // what the keys sit in
        final float gap;          // between caps, fraction of a key unit
        final float round;        // corner radius, fraction of the cap's short side
        final float insX, insTop, insBottom;   // top face inside the cap's footprint (sculpted caps: tall front skirt)
        final boolean sculpted;   // dished typewriter cap, or flat chiclet
        final boolean topLeft;    // legends printed at the top left (else centred)
        final float legendSize;   // single-character legend height, fraction of the face height
        final int grain;          // plastic texture strength
        final int bodyGrain;

        Style(int face, int mod, int legend, int body, float gap, float round, float insX, float insTop,
              float insBottom, boolean sculpted, boolean topLeft, float legendSize, int grain, int bodyGrain) {
            this.face = face;
            this.mod = mod;
            this.legend = legend;
            this.body = body;
            this.gap = gap;
            this.round = round;
            this.insX = insX;
            this.insTop = insTop;
            this.insBottom = insBottom;
            this.sculpted = sculpted;
            this.topLeft = topLeft;
            this.legendSize = legendSize;
            this.grain = grain;
            this.bodyGrain = bodyGrain;
        }
    }

    /* From the owner's photos of the four keyboards. */
    private static final Style[] STYLES = {
        // White CoCo 2 (Tandy 64K Color Computer 2): cream letter caps, whiter
        // function caps, tall sculpted caps close together in a dark grey well,
        // small legends at the top left.
        new Style(0xFFE9E2CF, 0xFFF7F6F2, 0xFF1E1E1E, 0xFF3D3E42, 0.055f, 0.10f, 0.13f, 0.05f, 0.27f, true, true, 0.38f, 0x0C, 0x14),
        // Cream TRS-80 Color Computer (silver case): off-white sculpted caps on black.
        new Style(0xFFF0EDE3, 0xFFF0EDE3, 0xFF262626, 0xFF18181A, 0.07f, 0.10f, 0.12f, 0.05f, 0.23f, true, true, 0.36f, 0x0C, 0x18),
        // Black Hi-Tek: charcoal sculpted caps nearly touching, white legends.
        new Style(0xFF2D2E32, 0xFF2D2E32, 0xFFF0F0F0, 0xFF161618, 0.035f, 0.09f, 0.12f, 0.06f, 0.26f, true, true, 0.40f, 0x18, 0x10),
        // Grey TRS-80 (first CoCo): small flat grey chiclets, wide gaps, on a black
        // rubber mat; legends centred.
        new Style(0xFFCBCCC8, 0xFFE8E8E4, 0xFF1A1A1A, 0xFF1D1D1F, 0.17f, 0.12f, 0.05f, 0.04f, 0.10f, false, false, 0.40f, 0x10, 0x30),
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
        ArtUtil.texture(c, P, ArtUtil.grain(), r, 0, st.bodyGrain);
        // the keys sit down in a well: shade under the front lip of the case
        P.setShader(new LinearGradient(0, 0, 0, h * 0.07f, 0x80000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.07f, P);
        P.setShader(null);
        // the lip itself catches the light
        P.setColor(0x50FFFFFF);
        c.drawRect(0, 0, w, Math.max(1f, h * 0.006f), P);
    }

    private static boolean isMod(KeyDef k) {
        return k.code == D_SHIFT || k.code == 0x30 || k.code == 0x31
                || k.code == 0x2b || k.code == 0x2c || k.code == 0x2d || k.code == 0x2e;
    }

    private static final android.graphics.Path PATH = new android.graphics.Path();

    private static void quad(Canvas c, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                             int color) {
        PATH.reset();
        PATH.moveTo(x0, y0);
        PATH.lineTo(x1, y1);
        PATH.lineTo(x2, y2);
        PATH.lineTo(x3, y3);
        PATH.close();
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawPath(PATH, P);
    }

    /* Lighter or darker, in a way that still works on a near-black cap. */
    private static int lit(int color, float amount, boolean dark) {
        if (amount >= 0) {
            return dark ? ArtUtil.mix(color, 0xFFFFFFFF, amount * 0.5f) : ArtUtil.shade(color, 1 + amount * 0.5f);
        }
        return dark ? ArtUtil.shade(color, 1 + amount * 1.1f) : ArtUtil.shade(color, 1 + amount);
    }

    /* One keycap, seen from above and a little in front: the footprint is the
     * bottom of its skirt; the top face is smaller and sits toward the back, so
     * the front skirt shows most, the left skirt catches the light and the
     * right one is in shade.  tint (0 for none) recolors the cap (SHIFT on). */
    static void drawKey(Canvas c, RectF r, KeyDef k, Style st, boolean pressed, boolean shifted, int tint,
                        boolean shadow) {
        boolean isBreak = k.code == D_BREAK;
        int face = isBreak ? BREAK_FACE : (isMod(k) ? st.mod : st.face);
        int ink = isBreak ? 0xFFFFFFFF : st.legend;
        if (tint != 0) {
            face = ArtUtil.mix(face, tint, 0.55f);
        }
        boolean dark = lum(face) < 0.3f;
        float kw = r.width(), kh = r.height(), u = Math.min(kw, kh);
        float rad = u * st.round;
        float sink = pressed ? kh * st.insBottom * 0.45f : 0;
        if (pressed) {
            face = lit(face, -0.08f, dark);
        }

        if (shadow) {
            P.setMaskFilter(new BlurMaskFilter(Math.max(1f, kh * 0.06f), BlurMaskFilter.Blur.NORMAL));
            P.setStyle(Paint.Style.FILL);
            P.setShader(null);
            P.setColor(0xA0000000);
            c.drawRoundRect(new RectF(r.left + kw * 0.01f, r.top + kh * 0.06f, r.right + kw * 0.01f, r.bottom + kh * 0.05f),
                    rad, rad, P);
            P.setMaskFilter(null);
        }

        // The footprint, then the four skirts inside it.
        RectF base = new RectF(r.left, r.top + sink * 0.25f, r.right, r.bottom);
        float fx = u * st.insX;
        RectF f = new RectF(base.left + fx, r.top + kh * st.insTop + sink, base.right - fx,
                r.bottom - kh * st.insBottom + sink);
        int front = lit(face, -0.30f, dark), left = lit(face, -0.12f, dark), right = lit(face, -0.42f, dark),
                back = lit(face, st.sculpted ? 0.05f : 0.02f, dark);
        PATH.reset();
        PATH.addRoundRect(base, rad, rad, android.graphics.Path.Direction.CW);
        c.save();
        c.clipPath(PATH);
        P.setStyle(Paint.Style.FILL);
        P.setColor(front);
        c.drawRect(base, P);
        quad(c, base.left, base.top, f.left, f.top, f.left, f.bottom, base.left, base.bottom, left);
        quad(c, base.right, base.top, f.right, f.top, f.right, f.bottom, base.right, base.bottom, right);
        quad(c, base.left, base.top, base.right, base.top, f.right, f.top, f.left, f.top, back);
        // the front skirt darkens toward the bottom
        P.setShader(new LinearGradient(0, f.bottom, 0, base.bottom, 0x00000000, dark ? 0x60000000 : 0x38000000,
                Shader.TileMode.CLAMP));
        c.drawRect(base.left, f.bottom, base.right, base.bottom, P);
        P.setShader(null);
        c.restore();

        // The top face.
        float frad = Math.max(rad * 0.6f, u * 0.05f);
        if (st.sculpted) {
            // cylindrical dish: shade under the back lip, light on the front edge
            ArtUtil.vgrad(c, P, f, frad, new int[] { lit(face, -0.10f, dark), face, face, lit(face, 0.06f, dark) },
                    new float[] { 0f, 0.3f, 0.8f, 1f });
        } else {
            ArtUtil.vgrad(c, P, f, frad, new int[] { lit(face, 0.05f, dark), face, lit(face, -0.03f, dark) },
                    new float[] { 0f, 0.5f, 1f });
        }
        ArtUtil.texture(c, P, ArtUtil.grain(), f, frad, st.grain);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, u * 0.012f));
        P.setColor(dark ? 0x26FFFFFF : 0x1E000000);
        c.drawRoundRect(f, frad, frad, P);
        P.setStyle(Paint.Style.FILL);

        legends(c, f, k, ink, shifted, st);
    }

    private static void legends(Canvas c, RectF f, KeyDef k, int ink, boolean shifted, Style st) {
        if (k.code == 0x2f) {
            return;   // the space bar is blank on every CoCo
        }
        float fw = f.width(), fh = f.height();
        T.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        T.setFakeBoldText(false);
        T.setColor(ink);
        String main = k.label;
        boolean word = main.length() > 1;
        boolean arrow = k.code == 0x2b || k.code == 0x2c || k.code == 0x2d || k.code == 0x2e;
        if (word || arrow) {
            // words and arrows: centred, sized to fit with a margin
            T.setTextAlign(Paint.Align.CENTER);
            float size = word ? fh * 0.26f : fh * 0.42f;
            T.setTextSize(size);
            float tw = T.measureText(main);
            if (tw > fw * 0.78f) {
                T.setTextSize(size * fw * 0.78f / tw);
            }
            float cy = f.centerY() - (T.descent() + T.ascent()) / 2f;
            c.drawText(main, f.centerX(), cy, T);
            return;
        }
        String sub = k.shifted;
        boolean two = sub != null;
        float size = fh * (two ? st.legendSize * 0.82f : st.legendSize);
        T.setTextSize(size);
        float x;
        if (st.topLeft) {
            T.setTextAlign(Paint.Align.LEFT);
            x = f.left + Math.max(fw * 0.16f, size * 0.30f);
        } else {
            T.setTextAlign(Paint.Align.CENTER);
            x = f.centerX();
        }
        float capH = -T.ascent() * 0.72f;   // about a capital's height
        if (two) {
            // shifted symbol above, the key's own below; the one SHIFT would give is the stronger
            float y1 = f.top + fh * 0.14f + capH;
            float y2 = y1 + capH * 1.35f;
            if (y2 > f.bottom - fh * 0.10f) {
                float d = y2 - (f.bottom - fh * 0.10f);
                y1 -= d / 2;
                y2 -= d;
            }
            boolean zero = k.label.equals("0");
            T.setColor(ArtUtil.alpha(ink, shifted && !zero ? 0xFF : (shifted ? 0xFF : 0xE0)));
            c.drawText(sub, x, y1, T);
            T.setColor(ArtUtil.alpha(ink, shifted && !zero ? 0x70 : 0xFF));
            c.drawText(main, x, y2, T);
        } else if (st.topLeft) {
            c.drawText(main, x, f.top + fh * 0.16f + capH, T);
        } else {
            c.drawText(main, x, f.centerY() + capH / 2, T);
        }
    }

    private static float lum(int c) {
        return (0.3f * ((c >> 16) & 255) + 0.59f * ((c >> 8) & 255) + 0.11f * (c & 255)) / 255f;
    }
}
