package io.github.carloscamacho.xroar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/* An on-screen joystick: a direction pad on the left and two fire buttons on
 * the right.  It drives XRoar's keyboard joystick ("kjoy0": cursor keys and the
 * two buttons), so a joystick port has to be set to "On-screen pad".
 *
 * Only touches that land on the pad or a button are taken; others pass through
 * to the picture behind (where the touch screen can still act as a mouse
 * joystick). */
final class PadView extends View {
    interface Listener {
        /* which: 0 Left, 1 Right, 2 Up, 3 Down, 4 Fire 1, 5 Fire 2 */
        void onJoy(int which, boolean down);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tri = new Path();

    private Listener listener;
    private boolean haptic = true;
    private final boolean[] state = new boolean[6];

    private float padCx, padCy, padR;
    private float b1x, b1y, b2x, b2y, btnR;

    PadView(Context c) {
        super(c);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFFFFF);
        text.setFakeBoldText(true);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setHaptic(boolean on) {
        haptic = on;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        padR = Math.min(w * 0.21f, h * 0.46f);
        padCx = Math.max(padR * 1.1f, w * 0.20f);
        padCy = h * 0.5f;
        btnR = padR * 0.52f;
        b1x = w - Math.max(btnR * 3.6f, w * 0.23f);
        b1y = h * 0.62f;
        b2x = w - Math.max(btnR * 1.5f, w * 0.09f);
        b2y = h * 0.36f;
        text.setTextSize(btnR * 0.8f);
    }

    @Override
    protected void onDraw(Canvas c) {
        // D-pad ring
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55202428);
        c.drawCircle(padCx, padCy, padR, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, padR * 0.04f));
        paint.setColor(0x99AAB2BC);
        c.drawCircle(padCx, padCy, padR, paint);
        paint.setStyle(Paint.Style.FILL);
        drawArrow(c, 0, state[0]);
        drawArrow(c, 1, state[1]);
        drawArrow(c, 2, state[2]);
        drawArrow(c, 3, state[3]);
        // Fire buttons
        drawButton(c, b1x, b1y, "1", state[4]);
        drawButton(c, b2x, b2y, "2", state[5]);
    }

    private void drawArrow(Canvas c, int dir, boolean on) {
        float r = padR;
        float cx = padCx, cy = padCy;
        float tip = r * 0.80f, base = r * 0.36f, half = r * 0.26f;
        tri.reset();
        switch (dir) {
        case 0: tri.moveTo(cx - tip, cy); tri.lineTo(cx - base, cy - half); tri.lineTo(cx - base, cy + half); break;
        case 1: tri.moveTo(cx + tip, cy); tri.lineTo(cx + base, cy - half); tri.lineTo(cx + base, cy + half); break;
        case 2: tri.moveTo(cx, cy - tip); tri.lineTo(cx - half, cy - base); tri.lineTo(cx + half, cy - base); break;
        default: tri.moveTo(cx, cy + tip); tri.lineTo(cx - half, cy + base); tri.lineTo(cx + half, cy + base); break;
        }
        tri.close();
        paint.setColor(on ? 0xEE2D7DD2 : 0x88C8CED6);
        c.drawPath(tri, paint);
    }

    private void drawButton(Canvas c, float x, float y, String label, boolean on) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(on ? 0xEED24A3A : 0x88502A26);
        c.drawCircle(x, y, btnR, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, btnR * 0.06f));
        paint.setColor(0x99AAB2BC);
        c.drawCircle(x, y, btnR, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawText(label, x, y - (text.descent() + text.ascent()) / 2f, text);
    }

    private boolean hits(MotionEvent e) {
        for (int i = 0; i < e.getPointerCount(); i++) {
            float x = e.getX(i), y = e.getY(i);
            if (dist(x, y, padCx, padCy) <= padR * 1.45f
                    || dist(x, y, b1x, b1y) <= btnR * 1.5f
                    || dist(x, y, b2x, b2y) <= btnR * 1.5f) {
                return true;
            }
        }
        return false;
    }

    private static float dist(float x, float y, float cx, float cy) {
        float dx = x - cx, dy = y - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && !hits(e)) {
            return false;   // not ours: let the picture have it
        }
        boolean[] now = new boolean[6];
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            int skip = (action == MotionEvent.ACTION_POINTER_UP) ? e.getActionIndex() : -1;
            float dead = padR * 0.28f;
            for (int i = 0; i < e.getPointerCount(); i++) {
                if (i == skip) {
                    continue;
                }
                float x = e.getX(i), y = e.getY(i);
                if (dist(x, y, padCx, padCy) <= padR * 1.45f) {
                    float dx = x - padCx, dy = y - padCy;
                    if (dx < -dead) now[0] = true;
                    if (dx > dead) now[1] = true;
                    if (dy < -dead) now[2] = true;
                    if (dy > dead) now[3] = true;
                } else if (dist(x, y, b1x, b1y) <= btnR * 1.5f) {
                    now[4] = true;
                } else if (dist(x, y, b2x, b2y) <= btnR * 1.5f) {
                    now[5] = true;
                }
            }
        }
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
        invalidate();
        return true;
    }
}
