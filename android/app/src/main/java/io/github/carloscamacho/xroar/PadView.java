package io.github.carloscamacho.xroar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/* An on-screen joystick drawn from above, as one of three real controllers:
 *
 *   "std"    the Radio Shack black joystick (26-3008): concentric rings, a black
 *            stick, one red button on the front edge.  NOT self-centering: the
 *            stick stays where you leave it until you push it back.
 *   "deluxe" the TRS-80 Deluxe Joystick (26-3012): cream body, a black pyramid
 *            well, red button at the top left, name plate along the front.
 *            Self-centering.
 *   "wico"   the Wico-style stick: black base with a red rim, red ball on the
 *            stick, a red base button and a button in the ball's white tip.
 *            Self-centering, two buttons.
 *
 * It drives XRoar's keyboard joystick ("kjoy0": cursor keys and the two
 * buttons), so a joystick port has to be set to "On-screen pad".  Only touches
 * that land on the stick or a button are taken; others pass through to the
 * picture behind. */
final class PadView extends View {
    interface Listener {
        /* which: 0 Left, 1 Right, 2 Up, 3 Down, 4 Fire 1, 5 Fire 2 */
        void onJoy(int which, boolean down);
    }

    static final String[] TYPE_IDS = { "std", "deluxe", "wico" };
    static final String[] TYPE_NAMES = {
        "Joystick (not self-centering)", "Deluxe joystick (self-centering)", "Wico joystick (self-centering, 2 buttons)" };

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    private Listener listener;
    private boolean haptic = true;
    private String type = "std";
    private final boolean[] state = new boolean[6];

    private int vw, vh;
    private float S, cx, cy;            // body square: side and centre
    private float sx, sy, R;            // stick centre and travel radius
    private float f1x, f1y, f1r;        // fire 1 hit circle
    private float f2x, f2y, f2r;        // fire 2 hit circle (Wico ball tip, when the stick is centred)
    private boolean hasFire2;
    private float stickX, stickY;       // stick position, -1..1 each way
    private boolean fire1, fire2;

    PadView(Context c) {
        super(c);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setHaptic(boolean on) {
        haptic = on;
    }

    void setType(String t) {
        if (t == null || t.equals(type)) {
            return;
        }
        // let go of everything held by the old stick
        stickX = 0;
        stickY = 0;
        fire1 = false;
        fire2 = false;
        publish();
        type = t;
        layout();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        vw = w;
        vh = h;
        layout();
    }

    private void layout() {
        S = Math.min(vh * 0.96f, vw * 0.66f);
        cx = vw / 2f;
        cy = vh / 2f;
        hasFire2 = false;
        switch (type) {
        case "deluxe":
            sx = cx + S * 0.06f;
            sy = cy - S * 0.07f;
            R = S * 0.27f;
            f1x = cx - S * 0.36f;
            f1y = cy - S * 0.37f;
            f1r = S * 0.17f;
            break;
        case "wico":
            sx = cx;
            sy = cy + S * 0.06f;
            R = S * 0.30f;
            f1x = cx - S * 0.34f;
            f1y = cy - S * 0.34f;
            f1r = S * 0.16f;
            hasFire2 = true;
            f2r = S * 0.07f;
            break;
        default:
            sx = cx;
            sy = cy - S * 0.04f;
            R = S * 0.31f;
            f1x = cx;
            f1y = cy + S * 0.43f;
            f1r = S * 0.17f;
            break;
        }
    }

    private boolean selfCentering() {
        return !type.equals("std");
    }

    /* ---------------------------------------------------------------- */
    /* Drawing */

    @Override
    protected void onDraw(Canvas c) {
        if (S <= 0) {
            return;
        }
        switch (type) {
        case "deluxe": drawDeluxe(c); break;
        case "wico": drawWico(c); break;
        default: drawStd(c); break;
        }
    }

    private void fillRound(Canvas c, float l, float t, float r, float b, float rad, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        rf.set(l, t, r, b);
        c.drawRoundRect(rf, rad, rad, p);
    }

    private void ring(Canvas c, float x, float y, float r, float w, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(color);
        c.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void disc(Canvas c, float x, float y, float r, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawCircle(x, y, r, p);
    }

    /* Radio Shack black joystick. */
    private void drawStd(Canvas c) {
        float h = S / 2f;
        // body, with a lighter top edge so it reads as a moulded block
        fillRound(c, cx - h, cy - h, cx + h, cy + h * 0.80f, S * 0.07f, 0xFF0E0E0F);
        fillRound(c, cx - h + S * 0.012f, cy - h + S * 0.012f, cx + h - S * 0.012f, cy + h * 0.80f - S * 0.02f,
                S * 0.06f, 0xFF1D1D1F);
        // concentric rings round the stick base
        float w = Math.max(1.5f, S * 0.012f);
        for (int i = 0; i < 6; i++) {
            ring(c, sx, sy, R * (1.12f - i * 0.15f), w, i % 2 == 0 ? 0xFF2F2F32 : 0xFF111112);
        }
        // the little triangle marker in front of the rings
        path.reset();
        path.moveTo(sx, sy + R * 1.10f);
        path.lineTo(sx - S * 0.04f, sy + R * 1.02f);
        path.lineTo(sx + S * 0.04f, sy + R * 1.02f);
        path.close();
        p.setColor(0xFF343437);
        c.drawPath(path, p);
        // the red button on the front face
        fillRound(c, f1x - S * 0.17f, cy + h * 0.80f - S * 0.01f, f1x + S * 0.17f, cy + h + S * 0.0f, S * 0.025f,
                0xFF0A0A0A);
        fillRound(c, f1x - S * 0.15f, cy + h * 0.80f + S * 0.005f, f1x + S * 0.15f, cy + h - S * 0.01f, S * 0.025f,
                fire1 ? 0xFFFF6A55 : 0xFFD6342A);
        // the stick, from above: its foot at the rings' centre, its top displaced
        float tx = sx + stickX * R * 0.55f, ty = sy + stickY * R * 0.55f;
        p.setColor(0xFF050505);
        p.setStrokeWidth(S * 0.05f);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(sx, sy, tx, ty, p);
        disc(c, tx, ty, S * 0.045f, 0xFF161617);
        disc(c, tx - S * 0.012f, ty - S * 0.012f, S * 0.016f, 0xFF4A4A4D);
        label(c, "Radio Shack", cx, cy - h * 0.88f, S * 0.045f, 0xFF3A3A3D);
    }

    /* TRS-80 Deluxe Joystick. */
    private void drawDeluxe(Canvas c) {
        float h = S / 2f;
        fillRound(c, cx - h, cy - h, cx + h, cy + h, S * 0.06f, 0xFFB9B3A2);          // shadow edge
        fillRound(c, cx - h + S * 0.01f, cy - h + S * 0.01f, cx + h - S * 0.01f, cy + h - S * 0.03f, S * 0.055f,
                0xFFDDD7C4);                                                           // cream top
        // the black square well with its pyramid funnel
        float wl = sx - S * 0.34f, wt = sy - S * 0.34f, wr = sx + S * 0.34f, wb = sy + S * 0.34f;
        fillRound(c, wl - S * 0.015f, wt - S * 0.015f, wr + S * 0.015f, wb + S * 0.015f, S * 0.01f, 0xFF101010);
        fillRound(c, wl, wt, wr, wb, S * 0.008f, 0xFF1C1C1C);
        float in = S * 0.13f;
        int[] shade = { 0xFF2A2A2A, 0xFF171717, 0xFF222222, 0xFF0F0F0F };   // top, right, bottom, left
        float[][] tri = {
            { wl, wt, wr, wt, sx + in, sy - in, sx - in, sy - in },
            { wr, wt, wr, wb, sx + in, sy + in, sx + in, sy - in },
            { wl, wb, wr, wb, sx + in, sy + in, sx - in, sy + in },
            { wl, wt, wl, wb, sx - in, sy + in, sx - in, sy - in },
        };
        for (int i = 0; i < 4; i++) {
            path.reset();
            path.moveTo(tri[i][0], tri[i][1]);
            path.lineTo(tri[i][2], tri[i][3]);
            path.lineTo(tri[i][4], tri[i][5]);
            path.lineTo(tri[i][6], tri[i][7]);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(shade[i]);
            c.drawPath(path, p);
        }
        // the stick, a black rod with a knurled top
        float tx = sx + stickX * R * 0.7f, ty = sy + stickY * R * 0.7f;
        p.setColor(0xFF000000);
        p.setStrokeWidth(S * 0.05f);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(sx, sy, tx, ty, p);
        disc(c, tx, ty, S * 0.055f, 0xFF262626);
        ring(c, tx, ty, S * 0.04f, Math.max(1f, S * 0.008f), 0xFF5A5A5A);
        // red button, top left, in a silver bezel
        fillRound(c, f1x - S * 0.095f, f1y - S * 0.085f, f1x + S * 0.095f, f1y + S * 0.105f, S * 0.01f, 0xFF8F8F8F);
        fillRound(c, f1x - S * 0.075f, f1y - S * 0.075f, f1x + S * 0.075f, f1y + S * 0.075f, S * 0.008f,
                fire1 ? 0xFFFF6A55 : 0xFFD6201A);
        // the name plate: silver "TRS-80", black "DELUXE JOYSTICK"
        float py = cy + h * 0.72f, ph = S * 0.13f;
        fillRound(c, cx - h * 0.92f, py, cx - h * 0.12f, py + ph, 0, 0xFFD0D0D0);
        fillRound(c, cx - h * 0.12f, py, cx + h * 0.92f, py + ph, 0, 0xFF161616);
        label(c, "TRS-80", cx - h * 0.52f, py + ph * 0.68f, S * 0.07f, 0xFF202020);
        label(c, "DELUXE JOYSTICK", cx + h * 0.40f, py + ph * 0.68f, S * 0.06f, 0xFFF0F0F0);
    }

    /* Wico-style stick. */
    private void drawWico(Canvas c) {
        float h = S / 2f;
        fillRound(c, cx - h, cy - h, cx + h, cy + h, S * 0.14f, 0xFFB52A1E);          // red rim underneath
        fillRound(c, cx - h, cy - h, cx + h, cy + h - S * 0.025f, S * 0.14f, 0xFF262628);
        // dished base round the stick, with its ring of little marks
        disc(c, sx, sy, R * 1.12f, 0xFF161617);
        ring(c, sx, sy, R * 1.12f, Math.max(1.5f, S * 0.012f), 0xFF3C3C40);
        ring(c, sx, sy, R * 0.86f, Math.max(2f, S * 0.02f), 0xFF2A2A2D);
        // button selector slot, bottom left
        fillRound(c, cx - h * 0.78f, cy + h * 0.80f, cx - h * 0.50f, cy + h * 0.88f, S * 0.01f, 0xFF0D0D0E);
        label(c, "BASE   STICK", cx - h * 0.10f, cy + h * 0.89f, S * 0.035f, 0xFF77777A);
        // round red base button, top left
        disc(c, f1x, f1y, f1r * 0.9f, 0xFF150808);
        disc(c, f1x, f1y, f1r * 0.78f, fire1 ? 0xFFFF6A55 : 0xFFE0372C);
        disc(c, f1x - f1r * 0.2f, f1y - f1r * 0.25f, f1r * 0.25f, 0x66FFFFFF);
        // the stick: metal shaft, red ball, white button in the ball's tip
        float tx = sx + stickX * R * 0.75f, ty = sy + stickY * R * 0.75f;
        p.setColor(0xFF8A8A84);
        p.setStrokeWidth(S * 0.05f);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(sx, sy, tx, ty, p);
        float br = S * 0.15f;
        disc(c, tx + S * 0.012f, ty + S * 0.02f, br, 0x66000000);                     // shadow
        disc(c, tx, ty, br, 0xFFD4261C);
        disc(c, tx - br * 0.3f, ty - br * 0.35f, br * 0.28f, 0x55FFFFFF);             // gloss
        disc(c, tx, ty, S * 0.055f, fire2 ? 0xFFFFE08A : 0xFFF4EFE2);                // tip button
        f2x = tx;
        f2y = ty;
    }

    private void label(Canvas c, String t, float x, float y, float size, int color) {
        text.setTextSize(size);
        text.setColor(color);
        c.drawText(t, x, y, text);
    }

    /* ---------------------------------------------------------------- */
    /* Touch */

    private static float dist(float x, float y, float cx, float cy) {
        float dx = x - cx, dy = y - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private boolean onFire1(float x, float y) {
        return dist(x, y, f1x, f1y) <= f1r * 1.5f;
    }

    private boolean onFire2(float x, float y) {
        return hasFire2 && dist(x, y, f2x, f2y) <= f2r * 1.6f;
    }

    private boolean onStick(float x, float y) {
        return dist(x, y, sx, sy) <= R * 1.6f;
    }

    private boolean hits(MotionEvent e) {
        for (int i = 0; i < e.getPointerCount(); i++) {
            float x = e.getX(i), y = e.getY(i);
            if (onFire1(x, y) || onFire2(x, y) || onStick(x, y)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && !hits(e)) {
            return false;   // not ours: let the picture have it
        }
        boolean up = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        int skip = (action == MotionEvent.ACTION_POINTER_UP) ? e.getActionIndex() : -1;
        boolean f1 = false, f2 = false, grabbed = false;
        float nx = 0, ny = 0;
        if (!up) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                if (i == skip) {
                    continue;
                }
                float x = e.getX(i), y = e.getY(i);
                if (onFire2(x, y) && !grabbed && Math.abs(stickX) + Math.abs(stickY) < 0.2f) {
                    f2 = true;
                } else if (onFire1(x, y) && !onStick(x, y)) {
                    f1 = true;
                } else if (onStick(x, y)) {
                    float dx = (x - sx) / R, dy = (y - sy) / R;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d > 1f) {
                        dx /= d;
                        dy /= d;
                    }
                    nx = dx;
                    ny = dy;
                    grabbed = true;
                }
            }
        }
        if (grabbed) {
            stickX = nx;
            stickY = ny;
        } else if (selfCentering()) {
            stickX = 0;
            stickY = 0;
        }
        fire1 = f1;
        fire2 = f2;
        publish();
        invalidate();
        return true;
    }

    /* Turn the stick position and buttons into the six keyboard-joystick lines. */
    private void publish() {
        float dead = 0.30f;
        boolean[] now = {
            stickX < -dead, stickX > dead, stickY < -dead, stickY > dead, fire1, fire2
        };
        for (int i = 0; i < 6; i++) {
            if (now[i] != state[i]) {
                state[i] = now[i];
                if (now[i] && haptic) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                if (listener != null) {
                    listener.onJoy(i, now[i]);
                }
            }
        }
    }
}
