package io.github.carloscamacho.xroar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.SparseIntArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/* An on-screen Tandy Color Computer keyboard.  It presses CoCo keys, not
 * Android characters: each key reports the CoCo's own key number (the DSCAN_*
 * values of XRoar's dkbd.h), so BREAK, CLEAR, the arrows and SHIFT combinations
 * behave exactly as on the real machine.
 *
 * The look (layout, keycaps, the four styles) is KeyboardArt.  This view draws
 * the idle keyboard once into a bitmap (shadows use a blur, which wants a
 * software canvas) and on each frame only redraws the keys that are held.
 *
 * SHIFT is sticky when enabled: one tap shifts the next key, a quick second
 * tap locks it, and a third tap (or any tap while locked) lets go.  Several
 * fingers can be down at once. */
final class CocoKeyboardView extends View {
    interface Listener {
        /* dscan is a CoCo key number; down is true on press, false on release. */
        void onKey(int dscan, boolean down);
    }

    static final int D_SHIFT = KeyboardArt.D_SHIFT;
    static final float COLS = KeyboardArt.COLS;
    static final String[] STYLE_IDS = KeyboardArt.STYLE_IDS;
    static final String[] STYLE_NAMES = KeyboardArt.STYLE_NAMES;

    private static final long DOUBLE_TAP_MS = 450;
    private static final int TINT_LATCHED = 0xFF5B9BE0;
    private static final int TINT_LOCKED = 0xFFE0A030;

    private static final class Key {
        final KeyboardArt.KeyDef def;
        final int code;
        final RectF rect = new RectF();
        boolean down;

        Key(KeyboardArt.KeyDef d) {
            def = d;
            code = d.code;
        }
    }

    private final List<Key> keys = new ArrayList<>();
    private final SparseIntArray pointerKey = new SparseIntArray();  // pointer id -> key index

    private Listener listener;
    private boolean sticky = true;
    private boolean haptic = true;
    private int style = 0;

    private boolean shiftLatched;
    private boolean shiftLocked;
    private long lastShiftTap;

    // Cached art: the body alone, and the body with every key up.
    private Bitmap bodyBmp, idleBmp;
    private String idleState = "";
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    CocoKeyboardView(Context c) {
        super(c);
        for (KeyboardArt.KeyDef d : KeyboardArt.layout()) {
            keys.add(new Key(d));
        }
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setSticky(boolean on) {
        sticky = on;
        if (!on && (shiftLatched || shiftLocked)) {
            shiftLatched = false;
            shiftLocked = false;
            sendShift(false);
        }
        invalidate();
    }

    void setHaptic(boolean on) {
        haptic = on;
    }

    void setStyle(String id) {
        int st = KeyboardArt.styleIndex(id);
        if (st != style) {
            style = st;
            bodyBmp = null;
            layoutKeys(getWidth(), getHeight());
        }
        invalidate();
    }

    /* Kept for MainActivity; the keyboard is always drawn opaque now. */
    void setOverlay(boolean on) {
    }

    /* ---------------------------------------------------------------- */

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bodyBmp = null;
        layoutKeys(w, h);
    }

    private void layoutKeys(int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        KeyboardArt.Style st = KeyboardArt.style(style);
        for (Key k : keys) {
            KeyboardArt.keyRect(k.def, w, h, st, k.rect);
        }
    }

    private boolean shiftActive() {
        for (Key k : keys) {
            if (k.code == D_SHIFT && k.down) {
                return true;
            }
        }
        return shiftLatched || shiftLocked;
    }

    private int shiftTint() {
        return shiftLocked ? TINT_LOCKED : (shiftLatched ? TINT_LATCHED : 0);
    }

    private void ensureArt(int w, int h) {
        KeyboardArt.Style st = KeyboardArt.style(style);
        if (bodyBmp == null || bodyBmp.getWidth() != w || bodyBmp.getHeight() != h) {
            bodyBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            KeyboardArt.drawBody(new Canvas(bodyBmp), w, h, st);
            idleBmp = null;
        }
        boolean shifted = shiftActive();
        String state = style + "/" + shifted + "/" + shiftTint();
        if (idleBmp == null || !state.equals(idleState)) {
            if (idleBmp == null) {
                idleBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            Canvas c = new Canvas(idleBmp);
            c.drawBitmap(bodyBmp, 0, 0, null);
            for (Key k : keys) {
                KeyboardArt.drawKey(c, k.rect, k.def, st, false, shifted,
                        k.code == D_SHIFT ? shiftTint() : 0, true);
            }
            idleState = state;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        ensureArt(w, h);
        c.drawBitmap(idleBmp, 0, 0, null);
        KeyboardArt.Style st = KeyboardArt.style(style);
        boolean shifted = shiftActive();
        for (Key k : keys) {
            if (!k.down) {
                continue;
            }
            // put the bare body back under the key (its shadow included), then the key pressed down
            // (only out to the gap round it, so the neighbours are left alone)
            float m = KeyboardArt.gapPx(w, h, st) * 0.95f;
            src.set(Math.max(0, (int) (k.rect.left - m)), Math.max(0, (int) (k.rect.top - m)),
                    Math.min(w, (int) (k.rect.right + m) + 1), Math.min(h, (int) (k.rect.bottom + m) + 1));
            dst.set(src.left, src.top, src.right, src.bottom);
            c.drawBitmap(bodyBmp, src, dst, null);
            KeyboardArt.drawKey(c, k.rect, k.def, st, true, shifted, k.code == D_SHIFT ? shiftTint() : 0, false);
        }
    }

    /* ---------------------------------------------------------------- */

    private int keyAt(float x, float y) {
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            // slightly generous hit area: nearest key whose padded rect holds the point
            float pad = Math.min(k.rect.width(), k.rect.height()) * 0.12f;
            if (x >= k.rect.left - pad && x <= k.rect.right + pad
                    && y >= k.rect.top - pad && y <= k.rect.bottom + pad) {
                float dx = x - k.rect.centerX();
                float dy = y - k.rect.centerY();
                float d = dx * dx + dy * dy;
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
        }
        return best;
    }

    private void sendShift(boolean down) {
        if (listener != null) {
            listener.onKey(D_SHIFT, down);
        }
    }

    private void press(int index) {
        Key k = keys.get(index);
        k.down = true;
        if (haptic) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (k.code == D_SHIFT) {
            if (!sticky) {
                sendShift(true);
            } else {
                long now = SystemClock.uptimeMillis();
                if (shiftLatched && !shiftLocked && now - lastShiftTap < DOUBLE_TAP_MS) {
                    shiftLocked = true;               // quick second tap: lock
                } else if (shiftLatched || shiftLocked) {
                    shiftLatched = false;             // tap while on: off
                    shiftLocked = false;
                } else {
                    shiftLatched = true;              // tap while off: one-shot
                }
                lastShiftTap = now;
                sendShift(shiftLatched || shiftLocked);
            }
        } else if (listener != null) {
            listener.onKey(k.code, true);
        }
        invalidate();
    }

    private void release(int index) {
        Key k = keys.get(index);
        k.down = false;
        if (k.code == D_SHIFT) {
            if (!sticky) {
                sendShift(false);
            }
        } else {
            if (listener != null) {
                listener.onKey(k.code, false);
            }
            if (sticky && shiftLatched && !shiftLocked) {
                // one-shot SHIFT is used up: let go after the key (the native
                // side keeps SHIFT down until that key's release has happened)
                shiftLatched = false;
                sendShift(false);
            }
        }
        invalidate();
    }

    private void releaseAll() {
        for (int i = 0; i < pointerKey.size(); i++) {
            release(pointerKey.valueAt(i));
        }
        pointerKey.clear();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN: {
            int idx = e.getActionIndex();
            int key = keyAt(e.getX(idx), e.getY(idx));
            if (key >= 0) {
                pointerKey.put(e.getPointerId(idx), key);
                press(key);
            }
            return true;
        }
        case MotionEvent.ACTION_MOVE:
            // A finger sliding onto another key releases the old one, presses the new.
            for (int i = 0; i < e.getPointerCount(); i++) {
                int id = e.getPointerId(i);
                int cur = pointerKey.get(id, -1);
                int now = keyAt(e.getX(i), e.getY(i));
                if (cur >= 0 && now != cur && now >= 0) {
                    release(cur);
                    pointerKey.put(id, now);
                    press(now);
                }
            }
            return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP: {
            int idx = e.getActionIndex();
            int id = e.getPointerId(idx);
            int cur = pointerKey.get(id, -1);
            if (cur >= 0) {
                pointerKey.delete(id);
                release(cur);
            }
            return true;
        }
        case MotionEvent.ACTION_CANCEL:
            releaseAll();
            return true;
        default:
            return true;
        }
    }
}
