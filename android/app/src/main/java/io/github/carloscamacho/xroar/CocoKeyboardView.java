package io.github.carloscamacho.xroar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
 * Layout, 13 columns by 5 rows:
 *   1 2 3 4 5 6 7 8 9 0 : - BRK
 *   Q W E R T Y U I O P @ UP CLR
 *   A S D F G H J K L ;  ENTER
 *   SHIFT Z X C V B N M , . /  DOWN
 *   LEFT  SPACE  RIGHT
 *
 * SHIFT is sticky when enabled: one tap shifts the next key, a quick second
 * tap locks it, and a third tap (or any tap while locked) lets go.  Several
 * fingers can be down at once (hold a key while tapping SHIFT, or hold arrows
 * in a game). */
final class CocoKeyboardView extends View {
    interface Listener {
        /* dscan is a CoCo key number; down is true on press, false on release. */
        void onKey(int dscan, boolean down);
    }

    static final int D_SHIFT = 0x37;

    private static final int COLS = 13;
    private static final int ROWS = 5;
    private static final long DOUBLE_TAP_MS = 450;

    private static final class Key {
        final String label;     // as printed on the key
        final String shifted;   // legend when SHIFT is down, or null
        final int code;
        final int row;
        final float x, w;
        final RectF rect = new RectF();
        boolean down;

        Key(String label, String shifted, int code, int row, float x, float w) {
            this.label = label;
            this.shifted = shifted;
            this.code = code;
            this.row = row;
            this.x = x;
            this.w = w;
        }
    }

    private final List<Key> keys = new ArrayList<>();
    private final SparseIntArray pointerKey = new SparseIntArray();  // pointer id -> key index

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legend = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Listener listener;
    private boolean sticky = true;
    private boolean haptic = true;
    private boolean overlay = false;

    private boolean shiftLatched;
    private boolean shiftLocked;
    private long lastShiftTap;

    CocoKeyboardView(Context c) {
        super(c);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFFFFF);
        text.setFakeBoldText(true);
        legend.setTextAlign(Paint.Align.RIGHT);
        legend.setColor(0xFFB0B8C0);
        buildKeys();
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

    /* Overlay mode (landscape): keys are drawn translucent over the picture. */
    void setOverlay(boolean on) {
        overlay = on;
        invalidate();
    }

    /* ---------------------------------------------------------------- */

    private void add(int row, float x, float w, String label, String shifted, int code) {
        keys.add(new Key(label, shifted, code, row, x, w));
    }

    private void buildKeys() {
        // Row 0: digits, colon, minus, BREAK
        String[] digits = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" };
        String[] digitsShift = { "!", "\"", "#", "$", "%", "&", "'", "(", ")", null };
        int[] digitCodes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 };
        float x = 0;
        for (int i = 0; i < 10; i++) {
            add(0, x++, 1, digits[i], digitsShift[i], digitCodes[i]);
        }
        add(0, x++, 1, ":", "*", 0x0a);
        add(0, x++, 1, "-", "=", 0x0d);
        add(0, x, 1, "BRK", null, 0x32);

        // Row 1: QWERTYUIOP @ UP CLEAR
        x = 0;
        for (char ch : "QWERTYUIOP".toCharArray()) {
            add(1, x++, 1, String.valueOf(ch), null, 0x11 + (ch - 'A'));
        }
        add(1, x++, 1, "@", null, 0x10);
        add(1, x++, 1, "↑", null, 0x2b);
        add(1, x, 1, "CLR", null, 0x31);

        // Row 2: ASDFGHJKL ; ENTER
        x = 0;
        for (char ch : "ASDFGHJKL".toCharArray()) {
            add(2, x++, 1, String.valueOf(ch), null, 0x11 + (ch - 'A'));
        }
        add(2, x++, 1, ";", "+", 0x0b);
        add(2, x, 3, "ENTER", null, 0x30);

        // Row 3: SHIFT ZXCVBNM , . / DOWN
        add(3, 0, 2, "SHIFT", null, D_SHIFT);
        x = 2;
        for (char ch : "ZXCVBNM".toCharArray()) {
            add(3, x++, 1, String.valueOf(ch), null, 0x11 + (ch - 'A'));
        }
        add(3, x++, 1, ",", "<", 0x0c);
        add(3, x++, 1, ".", ">", 0x0e);
        add(3, x++, 1, "/", "?", 0x0f);
        add(3, x, 1, "↓", null, 0x2c);

        // Row 4: LEFT SPACE RIGHT
        add(4, 0, 2.5f, "←", null, 0x2d);
        add(4, 2.5f, 8, "SPACE", null, 0x2f);
        add(4, 10.5f, 2.5f, "→", null, 0x2e);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float cw = w / (float) COLS;
        float ch = h / (float) ROWS;
        float gap = Math.max(1.5f, Math.min(cw, ch) * 0.06f);
        for (Key k : keys) {
            k.rect.set(k.x * cw + gap, k.row * ch + gap, (k.x + k.w) * cw - gap, (k.row + 1) * ch - gap);
        }
        float unit = Math.min(cw, ch);
        text.setTextSize(unit * 0.42f);
        legend.setTextSize(unit * 0.24f);
    }

    private boolean shiftActive() {
        for (Key k : keys) {
            if (k.code == D_SHIFT && k.down) {
                return true;
            }
        }
        return shiftLatched || shiftLocked;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(overlay ? 0x66101214 : 0xFF141518);
        boolean shifted = shiftActive();
        for (Key k : keys) {
            int color;
            if (k.code == D_SHIFT && shiftLocked) {
                color = 0xFFD29A2D;
            } else if (k.code == D_SHIFT && (shiftLatched || k.down)) {
                color = 0xFF2D7DD2;
            } else if (k.down) {
                color = 0xFF7A828C;
            } else {
                boolean special = k.label.length() > 1 || k.code == 0x2b || k.code == 0x2c;
                color = special ? 0xFF2B2E33 : 0xFF3A3E44;
            }
            fill.setColor(overlay ? (color & 0x00FFFFFF) | 0xCC000000 : color);
            float r = Math.min(k.rect.width(), k.rect.height()) * 0.14f;
            c.drawRoundRect(k.rect, r, r, fill);

            String label = k.label;
            if (shifted && k.shifted != null) {
                label = k.shifted;
            }
            float size = text.getTextSize();
            if (label.length() > 2) {
                text.setTextSize(size * 0.55f);
            }
            float cy = k.rect.centerY() - (text.descent() + text.ascent()) / 2f;
            c.drawText(label, k.rect.centerX(), cy, text);
            text.setTextSize(size);
            if (!shifted && k.shifted != null) {
                c.drawText(k.shifted, k.rect.right - k.rect.width() * 0.08f,
                        k.rect.top + legend.getTextSize() * 1.05f, legend);
            }
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
