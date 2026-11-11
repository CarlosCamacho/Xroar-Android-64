package io.github.carloscamacho.xroar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.SparseIntArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/* An on-screen joystick, one of four:
 *
 *   "std"     the Radio Shack black joystick (26-3008): concentric rings, a black
 *             stick, one red button at the top edge.
 *   "deluxe"  the TRS-80 Deluxe Joystick (26-3012): cream body, a black pyramid
 *             well, red button at the top left, name plate along the front.
 *   "wico"    the Wico-style stick: black base with a red rim, red ball on the
 *             stick, a red base button and a button in the ball's white tip.
 *   "virtual" no case: a see-through ring and two round buttons that hover over
 *             the picture, so they work in full-screen mode too.
 *
 * Every one behaves the same: the stick follows your finger and stays wherever
 * you hold it, however far you drag, and springs back to the centre only when
 * you lift the finger.  A second finger can press the buttons meanwhile.
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

    static final String[] TYPE_IDS = { "std", "deluxe", "wico", "virtual" };
    static final String[] TYPE_NAMES = {
        "Joystick (Radio Shack)", "Deluxe joystick", "Wico joystick (2 buttons)",
        "Virtual (see-through, over the picture)" };
    static final String[] SIDE_IDS = { "left", "center", "right" };
    static final String[] SIDE_NAMES = { "Left", "Center", "Right" };
    static final String[] VSIDE_IDS = { "left", "right" };
    static final String[] VSIDE_NAMES = { "Left (buttons on the right)", "Right (buttons on the left)" };
    // how the virtual stick moves
    static final String[] MOTION_IDS = { "center", "free", "float" };
    static final String[] MOTION_NAMES = {
        "Center: springs back when you lift your finger",
        "Free: stays where you leave it",
        "Floating: the ring appears where you touch" };

    // what a finger that is down is doing
    private static final int R_STICK = 1, R_FIRE1 = 2, R_FIRE2 = 3, R_TIP = 4;

    private Listener listener;
    private boolean haptic = true;
    private String type = "std";
    private String side = "center";
    private String vside = "left";      // the virtual joystick's own side, and how it moves
    private String vmotion = "center";
    private int opacity = 45;           // percent, for the virtual joystick
    private final boolean[] state = new boolean[6];
    private final SparseIntArray roles = new SparseIntArray();   // pointer id -> what it is doing

    private int vw, vh;
    private JoystickArt.Geo geo;
    private Bitmap staticBmp;           // the case and everything on it that doesn't move
    private float homeX, homeY;         // where the virtual ring rests (a floating one moves)
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
        releaseAll();
        type = t;
        layout();
        invalidate();
    }

    void setSide(String s) {
        if (s == null || s.equals(side)) {
            return;
        }
        side = s;
        layout();
        invalidate();
    }

    void setVirtualSide(String s) {
        if (s == null || s.equals(vside)) {
            return;
        }
        vside = s;
        layout();
        invalidate();
    }

    void setVirtualMotion(String m) {
        if (m == null || m.equals(vmotion)) {
            return;
        }
        releaseAll();
        vmotion = m;
        layout();
        invalidate();
    }

    /* How solid the virtual joystick is, 10-90 percent. */
    void setOpacity(int percent) {
        opacity = Math.max(10, Math.min(90, percent));
        invalidate();
    }

    private void releaseAll() {
        roles.clear();
        stickX = 0;
        stickY = 0;
        fire1 = false;
        fire2 = false;
        publish();
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
        geo = JoystickArt.layout(type, vw, vh, type.equals("virtual") ? vside : side);
        homeX = geo.sx;
        homeY = geo.sy;
        staticBmp = null;
    }

    /* ---------------------------------------------------------------- */
    /* Drawing: JoystickArt draws it; the still parts are cached in a bitmap. */

    @Override
    protected void onDraw(Canvas c) {
        if (geo == null) {
            return;
        }
        if (geo.virtual) {
            int a = opacity * 255 / 100;
            // a floating ring that nobody is holding is only a hint of where to touch
            JoystickArt.drawVirtual(c, geo, stickX, stickY, fire1, fire2, a, floating() && !stickHeld() ? a * 55 / 100 : a);
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

    private boolean floating() {
        return geo != null && geo.virtual && "float".equals(vmotion);
    }

    /* A floating ring starts wherever a finger comes down on the stick's half of the picture. */
    private boolean inFloatZone(float x) {
        return "right".equals(vside) ? x >= vw / 2f : x < vw / 2f;
    }

    private boolean onStick(float x, float y) {
        if (floating()) {
            return inFloatZone(x);
        }
        float reach = geo.virtual ? geo.vR * 1.45f : geo.R * 1.6f;
        return dist(x, y, geo.sx, geo.sy) <= reach;
    }

    private boolean onFire1(float x, float y) {
        return dist(x, y, geo.f1x, geo.f1y) <= geo.f1r * (geo.virtual ? 1.25f : 1.5f);
    }

    /* The Wico's tip button rides on the stick; the virtual joystick's second button is fixed. */
    private boolean onFire2(float x, float y) {
        if (!geo.hasFire2) {
            return false;
        }
        if (geo.virtual) {
            return dist(x, y, geo.f2x, geo.f2y) <= geo.f2r * 1.25f;
        }
        return dist(x, y, JoystickArt.tipX(geo, stickX), JoystickArt.tipY(geo, stickY)) <= geo.f2r * 1.6f;
    }

    private boolean stickHeld() {
        for (int i = 0; i < roles.size(); i++) {
            if (roles.valueAt(i) == R_STICK) {
                return true;
            }
        }
        return false;
    }

    /* What a finger that just came down on (x, y) is for, or 0 for nothing of ours. */
    private int classify(float x, float y) {
        if (!stickHeld()) {
            if (!geo.virtual && onFire2(x, y)) {
                return R_TIP;             // a tap fires; dragging off it moves the stick
            }
            if (onStick(x, y)) {
                return R_STICK;
            }
        }
        if (onFire1(x, y) && !onStick(x, y)) {
            return R_FIRE1;
        }
        if (geo.virtual && onFire2(x, y)) {
            return R_FIRE2;
        }
        return 0;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public boolean onTouchEvent(MotionEvent e) {
        if (geo == null) {
            return false;
        }
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            roles.clear();
            // fall through
        case MotionEvent.ACTION_POINTER_DOWN: {
            int role = classify(e.getX(idx), e.getY(idx));
            if (role == 0) {
                if (action == MotionEvent.ACTION_DOWN) {
                    return false;         // not ours: let the picture have it
                }
                break;
            }
            roles.put(e.getPointerId(idx), role);
            if (role == R_STICK && floating()) {
                // the ring opens under the finger (kept inside the picture)
                geo.sx = Math.max(geo.vR, Math.min(vw - geo.vR, e.getX(idx)));
                geo.sy = Math.max(geo.vR, Math.min(vh - geo.vR, e.getY(idx)));
            }
            break;
        }
        case MotionEvent.ACTION_MOVE:
            for (int i = 0; i < e.getPointerCount(); i++) {
                int id = e.getPointerId(i);
                if (roles.get(id, 0) == R_TIP && !onFire2(e.getX(i), e.getY(i))) {
                    // the finger left the tip: it is moving the stick after all
                    if (stickHeld()) {
                        roles.delete(id);
                    } else {
                        roles.put(id, R_STICK);
                    }
                }
            }
            break;
        case MotionEvent.ACTION_POINTER_UP:
            roles.delete(e.getPointerId(idx));
            break;
        default:   // UP, CANCEL
            roles.clear();
            break;
        }

        boolean f1 = false, f2 = false, held = false;
        float nx = 0, ny = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (action == MotionEvent.ACTION_POINTER_UP && i == idx) {
                continue;
            }
            switch (roles.get(e.getPointerId(i), 0)) {
            case R_STICK: {
                // follow the finger wherever it goes; past the travel it just pins to the edge
                float dx = (e.getX(i) - geo.sx) / geo.R, dy = (e.getY(i) - geo.sy) / geo.R;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 1f) {
                    dx /= d;
                    dy /= d;
                }
                nx = dx;
                ny = dy;
                held = true;
                break;
            }
            case R_FIRE1:
                f1 = true;
                break;
            case R_FIRE2:
            case R_TIP:
                f2 = true;
                break;
            default:
                break;
            }
        }
        if (held) {
            stickX = nx;
            stickY = ny;
        } else if (!(geo.virtual && "free".equals(vmotion))) {
            stickX = 0;              // no finger on the stick: back to the centre...
            stickY = 0;              // ...unless it is the virtual one set to stay where it is left
        }
        if (!held && floating()) {
            geo.sx = homeX;
            geo.sy = homeY;
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
