package io.github.carloscamacho.xroar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/* An on-screen joystick drawn from above, as one of three real controllers:
 *
 *   "std"    the Radio Shack black joystick (26-3008): concentric rings, a black
 *            stick, one red button at the top edge.  NOT self-centering: the
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


    private Listener listener;
    private boolean haptic = true;
    private String type = "std";
    private final boolean[] state = new boolean[6];

    private int vw, vh;
    private JoystickArt.Geo geo;
    private Bitmap staticBmp;           // the case and everything on it that doesn't move
    private float sx, sy, R;            // stick centre and travel radius
    private float f1x, f1y, f1r;        // fire 1 hit circle
    private float f2r;                  // fire 2 hit circle (Wico ball tip)
    private boolean hasFire2;
    private float stickX, stickY;       // stick position, -1..1 each way
    private boolean fire1, fire2;

    PadView(Context c) {
        super(c);
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
        if (vw <= 0 || vh <= 0) {
            return;
        }
        geo = JoystickArt.layout(type, vw, vh);
        sx = geo.sx;
        sy = geo.sy;
        R = geo.R;
        f1x = geo.f1x;
        f1y = geo.f1y;
        f1r = geo.f1r;
        f2r = geo.f2r;
        hasFire2 = geo.hasFire2;
        staticBmp = null;
    }

    private boolean selfCentering() {
        return !type.equals("std");
    }

    /* ---------------------------------------------------------------- */
    /* Drawing: JoystickArt draws it; the still parts are cached in a bitmap. */

    @Override
    protected void onDraw(Canvas c) {
        if (geo == null) {
            return;
        }
        if (staticBmp == null || staticBmp.getWidth() != vw || staticBmp.getHeight() != vh) {
            staticBmp = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888);
            JoystickArt.drawStatic(new Canvas(staticBmp), type, geo);
        }
        c.drawBitmap(staticBmp, 0, 0, null);
        JoystickArt.drawDynamic(c, type, geo, stickX, stickY, fire1, fire2);
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
        return hasFire2 && geo != null
                && dist(x, y, JoystickArt.tipX(geo, stickX), JoystickArt.tipY(geo, stickY)) <= f2r * 1.6f;
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
