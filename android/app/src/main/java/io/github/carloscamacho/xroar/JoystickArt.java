package io.github.carloscamacho.xroar;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/* How the joysticks look, from above, lit from the top left.
 *
 *   "std"    Radio Shack black joystick (26-3008): concentric rings, a black
 *            stick, one red button at the top edge (seen from above with its
 *            "Radio Shack" lettering the right way up).
 *   "deluxe" TRS-80 Deluxe Joystick (26-3012): pebbled cream case, black
 *            pyramid well, red button top left, aluminium name plate.
 *   "wico"   Wico-style: black case on a red base, domed boss, red base button
 *            top left, red ball top with a white button in its tip.
 *   "virtual" no case: a see-through ring with a knob and two round buttons,
 *            drawn over the picture (drawVirtual).
 *
 * drawStatic() is everything that doesn't move (PadView caches it as a
 * bitmap, so it may use blur); drawDynamic() is the stick and the buttons,
 * drawn every frame, with gradients only. */
final class JoystickArt {
    static final class Geo {
        float S, cx, cy;          // case: side and centre
        float sx, sy, R;          // stick pivot and travel
        float f1x, f1y, f1r;      // fire 1 (hit radius)
        float f2r;                // fire 2 (Wico tip, or the virtual button) hit radius
        boolean hasFire2;
        boolean virtual;          // the transparent overlay
        float vR;                 // virtual: radius of the drawn base ring
        float f2x, f2y;           // virtual: fire 2 button centre
    }

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint T = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();

    private JoystickArt() {
    }

    /* The case takes 80% of the smaller side of the area, sits a little below the
     * middle (so it keeps its distance from the picture and is nearer the thumbs),
     * and goes to the left, centre or right.  "virtual" is different: no case, a
     * ring and two buttons in the bottom corners of the area. */
    static Geo layout(String type, int w, int h, String side) {
        Geo g = new Geo();
        if (type.equals("virtual")) {
            return layoutVirtual(g, w, h, side);
        }
        g.S = 0.80f * Math.min(w, h);
        float slackX = w - g.S, slackY = h - g.S;
        g.cy = slackY * 0.6f + g.S / 2f;
        if ("left".equals(side)) {
            g.cx = g.S / 2f + slackX * 0.15f;
        } else if ("right".equals(side)) {
            g.cx = w - g.S / 2f - slackX * 0.15f;
        } else {
            g.cx = w / 2f;
        }
        float S = g.S;
        switch (type) {
        case "deluxe":
            g.sx = g.cx + S * 0.10f;
            g.sy = g.cy - S * 0.06f;
            g.R = S * 0.23f;
            g.f1x = g.cx - S * 0.36f;
            g.f1y = g.cy - S * 0.36f;
            g.f1r = S * 0.15f;
            break;
        case "wico":
            g.sx = g.cx + S * 0.02f;
            g.sy = g.cy + S * 0.05f;
            g.R = S * 0.26f;
            g.f1x = g.cx - S * 0.32f;
            g.f1y = g.cy - S * 0.32f;
            g.f1r = S * 0.15f;
            g.hasFire2 = true;
            g.f2r = S * 0.08f;
            break;
        default:
            g.sx = g.cx;
            g.sy = g.cy + S * 0.05f;
            g.R = S * 0.28f;
            g.f1x = g.cx;
            g.f1y = g.cy - S * 0.47f;
            g.f1r = S * 0.15f;
            break;
        }
        return g;
    }

    private static Geo layoutVirtual(Geo g, int w, int h, String side) {
        float Rb = 0.20f * Math.min(w, h);          // the ring's radius
        float edge = Rb * 0.30f;
        boolean stickLeft = !"right".equals(side);   // "center" puts the stick on the left too
        g.virtual = true;
        g.vR = Rb;
        g.S = 2 * Rb;
        g.R = Rb * 0.85f;                            // travel: a full push is 85% of the way to the ring
        g.sx = stickLeft ? edge + Rb : w - edge - Rb;
        g.sy = h - edge - Rb;
        g.f1r = Rb * 0.55f;
        g.f1x = stickLeft ? w - edge - g.f1r : edge + g.f1r;
        g.f1y = h - edge - g.f1r;
        g.hasFire2 = true;
        g.f2r = Rb * 0.42f;
        float toCentre = stickLeft ? -1f : 1f;
        g.f2x = g.f1x + toCentre * Rb * 0.95f;
        g.f2y = g.f1y - Rb * 1.05f;
        return g;
    }

    /* Where the stick's top is drawn, for a stick position of -1..1 each way. */
    static float topX(Geo g, String type, float stickX) {
        return g.sx + stickX * g.R * (type.equals("wico") ? 0.75f : 0.62f);
    }

    static float topY(Geo g, String type, float stickY) {
        return g.sy + stickY * g.R * (type.equals("wico") ? 0.75f : 0.62f);
    }

    /* ---------------------------------------------------------------- */

    private static int alpha(int rgb, float a) {
        return (Math.round(Math.max(0f, Math.min(255f, a))) << 24) | (rgb & 0xFFFFFF);
    }

    /* The transparent overlay: a ring with a knob, and two round buttons, all
     * see-through at `a` (0-255) so the picture shows behind them.  Dark edges
     * under the light ones keep them readable on a white screen. */
    static void drawVirtual(Canvas c, Geo g, float stickX, float stickY, boolean f1, boolean f2, int a, int ringA) {
        float Rb = g.vR;
        int full = a;
        a = ringA;          // the ring, arrows and knob; the buttons below use `full`
        P.setShader(null);
        P.setMaskFilter(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(alpha(0xFFFFFF, a * 0.20f));
        c.drawCircle(g.sx, g.sy, Rb, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Rb * 0.07f);
        P.setColor(alpha(0x000000, a * 0.55f));
        c.drawCircle(g.sx, g.sy, Rb + Rb * 0.04f, P);
        P.setStrokeWidth(Rb * 0.045f);
        P.setColor(alpha(0xFFFFFF, a * 0.85f));
        c.drawCircle(g.sx, g.sy, Rb, P);
        // four small arrows: up, down, left, right
        P.setStyle(Paint.Style.FILL);
        P.setColor(alpha(0xFFFFFF, a * 0.8f));
        for (int k = 0; k < 4; k++) {
            float dx = (k == 0 ? 0 : k == 1 ? 1 : k == 2 ? 0 : -1), dy = (k == 0 ? -1 : k == 1 ? 0 : k == 2 ? 1 : 0);
            float tx = g.sx + dx * Rb * 0.84f, ty = g.sy + dy * Rb * 0.84f;
            float u = Rb * 0.07f;
            PATH.reset();
            PATH.moveTo(tx + dx * u, ty + dy * u);
            PATH.lineTo(tx - dx * u + dy * u * 1.2f, ty - dy * u + dx * u * 1.2f);
            PATH.lineTo(tx - dx * u - dy * u * 1.2f, ty - dy * u - dx * u * 1.2f);
            PATH.close();
            c.drawPath(PATH, P);
        }
        // the knob
        float kx = g.sx + stickX * Rb * 0.55f, ky = g.sy + stickY * Rb * 0.55f, kr = Rb * 0.42f;
        P.setShader(new android.graphics.RadialGradient(kx - kr * 0.3f, ky - kr * 0.35f, kr * 1.3f,
                new int[] { alpha(0xFFFFFF, a * 0.95f), alpha(0xC8C8C8, a * 0.8f), alpha(0x707070, a * 0.8f) },
                new float[] { 0f, 0.6f, 1f }, Shader.TileMode.CLAMP));
        c.drawCircle(kx, ky, kr, P);
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Rb * 0.035f);
        P.setColor(alpha(0x000000, a * 0.5f));
        c.drawCircle(kx, ky, kr, P);

        virtualButton(c, g.f1x, g.f1y, g.f1r, f1, 0xC8102E, 0xFF3A55, "1", full);
        virtualButton(c, g.f2x, g.f2y, g.f2r, f2, 0xF2F2F2, 0xFFFFFF, "2", full);
        P.setStyle(Paint.Style.FILL);
    }

    private static void virtualButton(Canvas c, float x, float y, float r, boolean down, int rgb, int rgbDown,
                                      String label, int a) {
        float rr = down ? r * 0.93f : r;
        P.setStyle(Paint.Style.FILL);
        P.setColor(alpha(down ? rgbDown : rgb, a * (down ? 1.0f : 0.8f)));
        c.drawCircle(x, y, rr, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(r * 0.09f);
        P.setColor(alpha(0x000000, a * 0.5f));
        c.drawCircle(x, y, rr + r * 0.05f, P);
        P.setStrokeWidth(r * 0.06f);
        P.setColor(alpha(0xFFFFFF, a * 0.9f));
        c.drawCircle(x, y, rr, P);
        P.setStyle(Paint.Style.FILL);
        T.setTextAlign(Paint.Align.CENTER);
        T.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        T.setTextSize(r * 0.9f);
        T.setColor(alpha(rgb == 0xF2F2F2 ? 0x202020 : 0xFFFFFF, a * 0.95f));
        c.drawText(label, x, y + r * 0.32f, T);
    }

    static void drawStatic(Canvas c, String type, Geo g) {
        switch (type) {
        case "deluxe": deluxeStatic(c, g); break;
        case "wico": wicoStatic(c, g); break;
        default: stdStatic(c, g); break;
        }
    }

    static void drawDynamic(Canvas c, String type, Geo g, float stickX, float stickY, boolean f1, boolean f2) {
        switch (type) {
        case "deluxe": deluxeDynamic(c, g, stickX, stickY, f1); break;
        case "wico": wicoDynamic(c, g, stickX, stickY, f1, f2); break;
        default: stdDynamic(c, g, stickX, stickY, f1); break;
        }
    }

    private static RectF box(Geo g, float inset) {
        float h = g.S / 2f - inset;
        return new RectF(g.cx - h, g.cy - h, g.cx + h, g.cy + h);
    }

    private static void dropShadow(Canvas c, RectF r, float rad, float S) {
        P.setMaskFilter(new BlurMaskFilter(S * 0.035f, BlurMaskFilter.Blur.NORMAL));
        P.setStyle(Paint.Style.FILL);
        P.setColor(0xC0000000);
        c.drawRoundRect(new RectF(r.left + S * 0.01f, r.top + S * 0.03f, r.right + S * 0.025f, r.bottom + S * 0.045f),
                rad, rad, P);
        P.setMaskFilter(null);
    }

    /* A rim of light along the top-left edges and shade along the bottom-right. */
    private static void bevel(Canvas c, RectF r, float rad, float w, int light, int dark) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setColor(0xFFFFFFFF);
        P.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[] { light, 0x00FFFFFF, 0x00000000, dark }, new float[] { 0f, 0.45f, 0.55f, 1f },
                Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, P);
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
    }

    private static void ring(Canvas c, float x, float y, float r, float w, int color) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setShader(null);
        P.setColor(color);
        c.drawCircle(x, y, r, P);
        P.setStyle(Paint.Style.FILL);
    }

    /* Text moulded into the case: a dark edge below, a light edge above. */
    private static void emboss(Canvas c, String s, float x, float y, float size, int color, boolean italic) {
        T.setTextAlign(Paint.Align.CENTER);
        T.setTypeface(Typeface.create(Typeface.SANS_SERIF, italic ? Typeface.BOLD_ITALIC : Typeface.BOLD));
        T.setTextSize(size);
        float d = Math.max(1f, size * 0.05f);
        T.setColor(0x90000000);
        c.drawText(s, x + d, y + d, T);
        T.setColor(0x28FFFFFF);
        c.drawText(s, x - d * 0.6f, y - d * 0.6f, T);
        T.setColor(color);
        c.drawText(s, x, y, T);
    }

    private static void print(Canvas c, String s, float x, float y, float size, int color) {
        T.setTextAlign(Paint.Align.CENTER);
        T.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        T.setTextSize(size);
        T.setColor(color);
        c.drawText(s, x, y, T);
    }

    /* A tapered rod from (x0,y0) to (x1,y1), lit across its width. */
    private static void rod(Canvas c, float x0, float y0, float x1, float y1, float w0, float w1, int[] colors) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) {
            return;
        }
        float nx = -dy / len, ny = dx / len;
        PATH.reset();
        PATH.moveTo(x0 + nx * w0 / 2, y0 + ny * w0 / 2);
        PATH.lineTo(x1 + nx * w1 / 2, y1 + ny * w1 / 2);
        PATH.lineTo(x1 - nx * w1 / 2, y1 - ny * w1 / 2);
        PATH.lineTo(x0 - nx * w0 / 2, y0 - ny * w0 / 2);
        PATH.close();
        // light comes from the top left: the side whose normal faces it is bright
        float sgn = (nx * -0.6f + ny * -0.8f) > 0 ? 1 : -1;
        float mx = (x0 + x1) / 2, my = (y0 + y1) / 2, hw = Math.max(w0, w1) / 2;
        P.setStyle(Paint.Style.FILL);
        P.setColor(0xFFFFFFFF);
        P.setShader(new LinearGradient(mx + nx * hw * sgn, my + ny * hw * sgn, mx - nx * hw * sgn, my - ny * hw * sgn,
                colors, null, Shader.TileMode.CLAMP));
        c.drawPath(PATH, P);
        P.setShader(null);
    }

    /* The shadow a stick casts: away from the light, down and to the right. */
    private static void stickShadow(Canvas c, float bx, float by, float tx, float ty, float S, float width) {
        float ex = tx + S * 0.07f, ey = ty + S * 0.10f;
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setShader(null);
        P.setColor(0x22000000);
        P.setStrokeWidth(width * 2.0f);
        c.drawLine(bx, by, ex, ey, P);
        P.setColor(0x40000000);
        P.setStrokeWidth(width * 1.2f);
        c.drawLine(bx, by, ex, ey, P);
        P.setStyle(Paint.Style.FILL);
        P.setStrokeCap(Paint.Cap.BUTT);
    }

    /* ---------------------------------------------------------------- */
    /* Radio Shack joystick */

    private static void stdStatic(Canvas c, Geo g) {
        float S = g.S;
        RectF body = box(g, 0);
        float rad = S * 0.07f;
        dropShadow(c, body, rad, S);
        ArtUtil.vgrad(c, P, body, rad, new int[] { 0xFF19191B, 0xFF0C0C0D, 0xFF050506 }, null);
        RectF top = box(g, S * 0.022f);
        ArtUtil.dgrad(c, P, top, rad * 0.85f, 0xFF2E2E31, 0xFF141416);
        ArtUtil.texture(c, P, ArtUtil.grain(), top, rad * 0.85f, 0x30);
        bevel(c, top, rad * 0.85f, Math.max(1.5f, S * 0.008f), 0x50FFFFFF, 0x70000000);

        // the stepped cone of rings round the stick
        float sx = g.sx, sy = g.sy, R = g.R;
        P.setColor(0xFFFFFFFF);
        P.setShader(new android.graphics.RadialGradient(sx - R * 0.25f, sy - R * 0.3f, R * 1.4f,
                new int[] { 0xFF303034, 0xFF1D1D20, 0xFF141416 }, new float[] { 0f, 0.6f, 1f }, Shader.TileMode.CLAMP));
        c.drawCircle(sx, sy, R * 1.18f, P);
        P.setShader(null);
        float w = Math.max(1.2f, S * 0.007f);
        for (int i = 0; i < 7; i++) {
            float rr = R * (1.18f - i * 0.12f);
            ring(c, sx + w * 0.7f, sy + w, rr, w, 0xB0000000);      // groove shade
            ring(c, sx - w * 0.5f, sy - w * 0.7f, rr, w * 0.8f, 0x2CFFFFFF);  // ridge light
        }
        // the boot where the stick goes in
        ArtUtil.sphere(c, P, sx, sy, S * 0.055f, new int[] { 0xFF2A2A2D, 0xFF0E0E0F, 0xFF050505 }, null);
        ring(c, sx, sy, S * 0.055f, Math.max(1f, S * 0.005f), 0x60000000);

        // triangle marker pointing at the button, and the moulded name
        float ty = sy - R * 1.30f;
        PATH.reset();
        PATH.moveTo(sx, ty - S * 0.035f);
        PATH.lineTo(sx - S * 0.035f, ty + S * 0.02f);
        PATH.lineTo(sx + S * 0.035f, ty + S * 0.02f);
        PATH.close();
        P.setStyle(Paint.Style.FILL);
        P.setColor(0xFF0B0B0C);
        c.drawPath(PATH, P);
        emboss(c, "Radio Shack", g.cx + S * 0.25f, g.cy - S * 0.33f, S * 0.058f, 0xFF232326, true);

        // the slot in the case top the button stands in
        float bt = body.top;
        P.setColor(0xFF000000);
        c.drawRoundRect(new RectF(g.f1x - S * 0.145f, bt - S * 0.01f, g.f1x + S * 0.145f, bt + S * 0.06f),
                S * 0.012f, S * 0.012f, P);
    }

    private static void stdDynamic(Canvas c, Geo g, float stickX, float stickY, boolean f1) {
        float S = g.S;
        // the red button at the top edge
        float bt = g.cy - S / 2f;
        RectF b = new RectF(g.f1x - S * 0.13f, bt - S * 0.03f + (f1 ? S * 0.008f : 0), g.f1x + S * 0.13f,
                bt + S * 0.05f);
        if (!f1) {
            ArtUtil.vgrad(c, P, new RectF(b.left, b.top + S * 0.01f, b.right + S * 0.008f, b.bottom + S * 0.018f),
                    S * 0.012f, new int[] { 0x00000000, 0x90000000 }, null);
        }
        redCap(c, b, S * 0.012f, f1);

        float tx = JoystickArt.topX(g, "std", stickX), ty = JoystickArt.topY(g, "std", stickY);
        stickShadow(c, g.sx, g.sy, tx, ty, S, S * 0.05f);
        rod(c, g.sx, g.sy, tx, ty, S * 0.075f, S * 0.058f,
                new int[] { 0xFF46464A, 0xFF1A1A1C, 0xFF060606, 0xFF000000 });
        float r = S * 0.034f;
        ArtUtil.sphere(c, P, tx, ty, r, new int[] { 0xFF5E5E63, 0xFF222224, 0xFF060606 }, new float[] { 0f, 0.5f, 1f });
        // the hollow in the stick's top
        ArtUtil.sphere(c, P, tx + r * 0.05f, ty + r * 0.05f, r * 0.42f, new int[] { 0xFF000000, 0xFF0A0A0A, 0xFF2C2C2F },
                new float[] { 0f, 0.6f, 1f });
    }

    /* A red plastic button cap, seen from above. */
    private static void redCap(Canvas c, RectF b, float rad, boolean down) {
        ArtUtil.vgrad(c, P, b, rad, down
                ? new int[] { 0xFFC93A2F, 0xFFA8231A, 0xFF7C140E }
                : new int[] { 0xFFFF7465, 0xFFE33A2E, 0xFFB01E15 }, new float[] { 0f, 0.5f, 1f });
        P.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.35f, down ? 0x30FFFFFF : 0x90FFFFFF,
                0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(b.left + rad, b.top + rad * 0.3f, b.right - rad, b.top + b.height() * 0.35f),
                rad * 0.6f, rad * 0.6f, P);
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, b.height() * 0.03f));
        P.setColor(0x60000000);
        c.drawRoundRect(b, rad, rad, P);
        P.setStyle(Paint.Style.FILL);
    }

    /* ---------------------------------------------------------------- */
    /* TRS-80 Deluxe Joystick */

    private static void deluxeStatic(Canvas c, Geo g) {
        float S = g.S;
        RectF body = box(g, 0);
        float rad = S * 0.06f;
        dropShadow(c, body, rad, S);
        ArtUtil.vgrad(c, P, body, rad, new int[] { 0xFFC9C2AE, 0xFFB2AB97, 0xFF948D7A }, null);
        RectF top = box(g, S * 0.02f);
        ArtUtil.dgrad(c, P, top, rad * 0.9f, 0xFFE9E3D2, 0xFFCFC8B4);
        ArtUtil.texture(c, P, ArtUtil.grain(), top, rad * 0.9f, 0x46);   // pebbled finish
        bevel(c, top, rad * 0.9f, Math.max(1.5f, S * 0.008f), 0x90FFFFFF, 0x50000000);

        // the square well and its pyramid funnel
        float sx = g.sx, sy = g.sy;
        float o = S * 0.32f, i = S * 0.297f, in = S * 0.10f;
        RectF frame = new RectF(sx - o, sy - o, sx + o, sy + o);
        P.setMaskFilter(new BlurMaskFilter(S * 0.012f, BlurMaskFilter.Blur.NORMAL));
        P.setColor(0x90000000);
        c.drawRoundRect(new RectF(frame.left, frame.top + S * 0.008f, frame.right + S * 0.006f, frame.bottom + S * 0.014f),
                S * 0.02f, S * 0.02f, P);
        P.setMaskFilter(null);
        ArtUtil.dgrad(c, P, frame, S * 0.02f, 0xFF2A2A2A, 0xFF0A0A0A);
        bevel(c, frame, S * 0.02f, Math.max(1f, S * 0.006f), 0x40FFFFFF, 0x80000000);
        float l = sx - i, t = sy - i, r = sx + i, b = sy + i;
        facet(c, l, t, r, t, sx + in, sy - in, sx - in, sy - in, 0xFF0D0D0D, 0xFF151515);   // back: away from light
        facet(c, l, t, l, b, sx - in, sy + in, sx - in, sy - in, 0xFF101010, 0xFF1A1A1A);   // left
        facet(c, r, t, r, b, sx + in, sy + in, sx + in, sy - in, 0xFF3A3A3A, 0xFF262626);   // right: lit
        facet(c, l, b, r, b, sx + in, sy + in, sx - in, sy + in, 0xFF424242, 0xFF2A2A2A);   // front: lit
        // seams between the facets
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, S * 0.004f));
        P.setColor(0x50000000);
        c.drawLine(l, t, sx - in, sy - in, P);
        c.drawLine(r, t, sx + in, sy - in, P);
        c.drawLine(l, b, sx - in, sy + in, P);
        c.drawLine(r, b, sx + in, sy + in, P);
        P.setStyle(Paint.Style.FILL);
        // the opening at the bottom, with the round rubber boot
        P.setColor(0xFF060606);
        c.drawRect(sx - in, sy - in, sx + in, sy + in, P);
        ArtUtil.sphere(c, P, sx, sy, in * 0.85f, new int[] { 0xFF3A3A3C, 0xFF1E1E1F, 0xFF0A0A0A }, null);
        for (int k = 1; k <= 3; k++) {
            ring(c, sx, sy, in * 0.85f * k / 3.6f, Math.max(1f, S * 0.004f), 0x30000000);
        }
        // trim tabs: left of the well, and under it
        trimTab(c, frame.left - S * 0.012f, sy - S * 0.07f, S * 0.022f, S * 0.14f);
        trimTab(c, sx - S * 0.07f, frame.bottom - S * 0.012f, S * 0.14f, S * 0.022f);

        // the red button's bezel
        float bx = g.f1x, by = g.f1y, bs = S * 0.075f;
        RectF bez = new RectF(bx - bs, by - bs, bx + bs, by + bs);
        ArtUtil.dgrad(c, P, bez, S * 0.006f, 0xFF3C3C3C, 0xFF0E0E0E);
        P.setColor(0xFFB8B8B8);
        c.drawRect(bez.left, bez.bottom - S * 0.018f, bez.right, bez.bottom, P);   // the silver lower edge

        // the name plate
        float py = g.cy + S * 0.375f, ph = S * 0.095f;
        float pl = g.cx - S * 0.43f, pm = g.cx - S * 0.05f, pr = g.cx + S * 0.43f;
        RectF silver = new RectF(pl, py, pm, py + ph);
        ArtUtil.vgrad(c, P, silver, 0, new int[] { 0xFFF2F2F2, 0xFFC4C4C4, 0xFFDCDCDC, 0xFFB0B0B0 }, null);
        ArtUtil.texture(c, P, ArtUtil.streaks(), silver, 0, 0x30);
        RectF black = new RectF(pm, py, pr, py + ph);
        ArtUtil.vgrad(c, P, black, 0, new int[] { 0xFF2A2A2A, 0xFF101010 }, null);
        ArtUtil.texture(c, P, ArtUtil.streaks(), black, 0, 0x18);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, S * 0.004f));
        P.setColor(0x80000000);
        c.drawRect(pl, py, pr, py + ph, P);
        P.setStyle(Paint.Style.FILL);
        print(c, "TRS-80", (pl + pm) / 2, py + ph * 0.68f, ph * 0.52f, 0xFF1E1E1E);
        print(c, "DELUXE", (pm + pr) / 2, py + ph * 0.43f, ph * 0.36f, 0xFFF2F2F2);
        print(c, "JOYSTICK", (pm + pr) / 2, py + ph * 0.86f, ph * 0.40f, 0xFFF2F2F2);
    }

    private static void facet(Canvas c, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                              int outer, int inner) {
        PATH.reset();
        PATH.moveTo(x0, y0);
        PATH.lineTo(x1, y1);
        PATH.lineTo(x2, y2);
        PATH.lineTo(x3, y3);
        PATH.close();
        float ox = (x0 + x1) / 2, oy = (y0 + y1) / 2, ix = (x2 + x3) / 2, iy = (y2 + y3) / 2;
        P.setStyle(Paint.Style.FILL);
        P.setColor(0xFFFFFFFF);
        P.setShader(new LinearGradient(ox, oy, ix, iy, outer, inner, Shader.TileMode.CLAMP));
        c.drawPath(PATH, P);
        P.setShader(null);
    }

    private static void trimTab(Canvas c, float x, float y, float w, float h) {
        RectF r = new RectF(x, y, x + w, y + h);
        ArtUtil.dgrad(c, P, r, Math.min(w, h) * 0.3f, 0xFF4A4A4A, 0xFF151515);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(1f);
        P.setColor(0x40000000);
        boolean vertical = h > w;
        for (int k = 1; k < 6; k++) {   // ridges for the thumb
            if (vertical) {
                c.drawLine(x + w * 0.2f, y + h * k / 6f, x + w * 0.8f, y + h * k / 6f, P);
            } else {
                c.drawLine(x + w * k / 6f, y + h * 0.2f, x + w * k / 6f, y + h * 0.8f, P);
            }
        }
        P.setStyle(Paint.Style.FILL);
    }

    private static void deluxeDynamic(Canvas c, Geo g, float stickX, float stickY, boolean f1) {
        float S = g.S;
        float bs = S * 0.058f, d = f1 ? S * 0.006f : 0;
        RectF b = new RectF(g.f1x - bs, g.f1y - bs - S * 0.008f + d, g.f1x + bs, g.f1y + bs - S * 0.008f + d);
        // a square cap with sloped sides: side, then top
        ArtUtil.vgrad(c, P, b, S * 0.008f, f1 ? new int[] { 0xFF9E1C14, 0xFF6E0E09 } : new int[] { 0xFFC2261C, 0xFF7E120B },
                null);
        RectF topf = new RectF(b.left + bs * 0.18f, b.top + bs * 0.12f, b.right - bs * 0.18f, b.bottom - bs * 0.32f);
        redCap(c, topf, S * 0.006f, f1);

        float tx = topX(g, "deluxe", stickX), ty = topY(g, "deluxe", stickY);
        stickShadow(c, g.sx, g.sy, tx, ty, S, S * 0.05f);
        rod(c, g.sx, g.sy, tx, ty, S * 0.06f, S * 0.055f, new int[] { 0xFF4A4A4D, 0xFF151516, 0xFF000000 });
        float r = S * 0.04f;
        ArtUtil.sphere(c, P, tx, ty, r, new int[] { 0xFF4E4E52, 0xFF1C1C1E, 0xFF050505 }, null);
        // knurled top
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, S * 0.004f));
        for (int k = 0; k < 28; k++) {
            double a = k * Math.PI * 2 / 28;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            P.setColor((k % 2 == 0) ? 0x50FFFFFF : 0x90000000);
            c.drawLine(tx + cs * r * 0.68f, ty + sn * r * 0.68f, tx + cs * r * 0.97f, ty + sn * r * 0.97f, P);
        }
        P.setStyle(Paint.Style.FILL);
    }

    /* ---------------------------------------------------------------- */
    /* Wico */

    private static void wicoStatic(Canvas c, Geo g) {
        float S = g.S;
        RectF body = box(g, 0);
        float rad = S * 0.16f;
        dropShadow(c, body, rad, S);
        // the red base shows below the black case
        RectF red = new RectF(body.left, body.top + S * 0.03f, body.right, body.bottom);
        ArtUtil.vgrad(c, P, red, rad, new int[] { 0xFFB8261A, 0xFFD43426, 0xFF7E1209 }, new float[] { 0f, 0.7f, 1f });
        RectF casing = new RectF(body.left, body.top, body.right, body.bottom - S * 0.03f);
        ArtUtil.vgrad(c, P, casing, rad, new int[] { 0xFF202022, 0xFF0E0E0F }, null);
        RectF top = new RectF(casing.left + S * 0.018f, casing.top + S * 0.015f, casing.right - S * 0.018f,
                casing.bottom - S * 0.03f);
        ArtUtil.dgrad(c, P, top, rad * 0.9f, 0xFF36363A, 0xFF19191B);
        ArtUtil.texture(c, P, ArtUtil.grain(), top, rad * 0.9f, 0x34);
        bevel(c, top, rad * 0.9f, Math.max(1.5f, S * 0.008f), 0x40FFFFFF, 0x80000000);

        // the domed boss round the stick, with its ring of moulded marks
        float sx = g.sx, sy = g.sy, R = g.R;
        P.setMaskFilter(new BlurMaskFilter(S * 0.02f, BlurMaskFilter.Blur.NORMAL));
        P.setColor(0xA0000000);
        c.drawCircle(sx + S * 0.01f, sy + S * 0.02f, R * 1.28f, P);
        P.setMaskFilter(null);
        ArtUtil.sphere(c, P, sx, sy, R * 1.28f, new int[] { 0xFF414146, 0xFF1F1F22, 0xFF0C0C0D }, new float[] { 0f, 0.55f, 1f });
        ring(c, sx, sy, R * 1.28f, Math.max(1f, S * 0.006f), 0x70000000);
        for (int k = 0; k < 36; k++) {
            double a = k * Math.PI * 2 / 36;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            float x0 = sx + cs * R * 1.02f, y0 = sy + sn * R * 1.02f, x1 = sx + cs * R * 1.15f, y1 = sy + sn * R * 1.15f;
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeCap(Paint.Cap.ROUND);
            P.setStrokeWidth(Math.max(1.2f, S * 0.012f));
            P.setColor(0x90000000);
            c.drawLine(x0 + 1, y0 + 1.5f, x1 + 1, y1 + 1.5f, P);
            P.setColor(0x30FFFFFF);
            c.drawLine(x0, y0, x1, y1, P);
        }
        P.setStrokeCap(Paint.Cap.BUTT);
        P.setStyle(Paint.Style.FILL);
        // dished collar the shaft comes out of
        ArtUtil.sphere(c, P, sx, sy, R * 0.85f, new int[] { 0xFF0C0C0D, 0xFF1B1B1E, 0xFF2E2E32 }, new float[] { 0f, 0.6f, 1f });
        ArtUtil.sphere(c, P, sx, sy, S * 0.05f, new int[] { 0xFF9A9A98, 0xFF4A4A4A, 0xFF1A1A1A }, null);

        // the raised pad the base button sits on
        P.setMaskFilter(new BlurMaskFilter(S * 0.012f, BlurMaskFilter.Blur.NORMAL));
        P.setColor(0x90000000);
        c.drawCircle(g.f1x + S * 0.008f, g.f1y + S * 0.014f, S * 0.095f, P);
        P.setMaskFilter(null);
        ArtUtil.sphere(c, P, g.f1x, g.f1y, S * 0.095f, new int[] { 0xFF38383C, 0xFF151517, 0xFF0A0A0B }, null);

        // BASE / STICK button selector
        float ly = g.cy + S * 0.415f;
        print(c, "BASE", g.cx - S * 0.25f, ly + S * 0.012f, S * 0.04f, 0xFF8A8A88);
        print(c, "STICK", g.cx + S * 0.0f, ly + S * 0.012f, S * 0.04f, 0xFF8A8A88);
        RectF slot = new RectF(g.cx - S * 0.165f, ly - S * 0.02f, g.cx - S * 0.085f, ly + S * 0.016f);
        P.setColor(0xFF050505);
        c.drawRoundRect(slot, S * 0.006f, S * 0.006f, P);
        ArtUtil.dgrad(c, P, new RectF(slot.left + S * 0.005f, slot.top + S * 0.004f, slot.left + S * 0.04f,
                slot.bottom - S * 0.004f), S * 0.004f, 0xFF6A6A6A, 0xFF2A2A2A);
        print(c, "BUTTON SELECTOR", g.cx + S * 0.27f, ly + S * 0.010f, S * 0.03f, 0xFF7A7A78);
    }

    /* Where the Wico's tip button is, for the current stick position. */
    static float tipX(Geo g, float stickX) {
        return topX(g, "wico", stickX) + stickX * g.S * 0.02f;
    }

    static float tipY(Geo g, float stickY) {
        return topY(g, "wico", stickY) + stickY * g.S * 0.02f;
    }

    private static void wicoDynamic(Canvas c, Geo g, float stickX, float stickY, boolean f1, boolean f2) {
        float S = g.S;
        // red base button
        float br0 = S * 0.062f * (f1 ? 0.96f : 1f);
        ArtUtil.sphere(c, P, g.f1x, g.f1y + (f1 ? S * 0.004f : 0), br0, f1
                ? new int[] { 0xFFE0574A, 0xFFB0241A, 0xFF6A0E08 }
                : new int[] { 0xFFFF8C7E, 0xFFE8392C, 0xFF8E160E }, new float[] { 0f, 0.5f, 1f });
        ArtUtil.specular(c, P, g.f1x - br0 * 0.35f, g.f1y - br0 * 0.4f, br0 * 0.45f, f1 ? 0x50 : 0xB0);

        // shaft, ball and tip
        float bx = topX(g, "wico", stickX), by = topY(g, "wico", stickY);
        float br = S * 0.15f;
        ArtUtil.softShadow(c, P, bx + S * 0.07f, by + S * 0.10f, br * 1.3f, 0x90);
        rod(c, g.sx, g.sy, bx, by, S * 0.045f, S * 0.04f, new int[] { 0xFF3A3A3A, 0xFFEDEDED, 0xFF8A8A8A, 0xFF2A2A2A });
        ArtUtil.sphere(c, P, bx, by, br, new int[] { 0xFFFFB0A4, 0xFFEC3F31, 0xFFB81C12, 0xFF4E0603 },
                new float[] { 0f, 0.22f, 0.62f, 1f });
        ArtUtil.specular(c, P, bx - br * 0.42f, by - br * 0.48f, br * 0.38f, 0xD0);
        float tx = tipX(g, stickX), ty = tipY(g, stickY);
        float tr = S * 0.052f * (f2 ? 0.93f : 1f);
        ArtUtil.softShadow(c, P, tx + tr * 0.25f, ty + tr * 0.35f, tr * 1.35f, 0x70);
        ArtUtil.sphere(c, P, tx, ty, tr, f2
                ? new int[] { 0xFFDAD4C6, 0xFFB9B2A2, 0xFF8E8778 }
                : new int[] { 0xFFFFFFFF, 0xFFF2EEE4, 0xFFBDB6A6 }, new float[] { 0f, 0.5f, 1f });
    }
}
