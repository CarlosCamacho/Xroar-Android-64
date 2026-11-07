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
 * Layout follows the real keyboard (14 units wide, 5 rows), each row stepped
 * in a little further than the one above:
 *   1 2 3 4 5 6 7 8 9 0 : -  BREAK
 *   UP Q W E R T Y U I O P @ LEFT RIGHT
 *   DOWN A S D F G H J K L ; ENTER CLEAR
 *   SHIFT Z X C V B N M , . / SHIFT
 *   SPACE
 * Keys with a second legend (! over 1, and so on) print the SHIFTed one above.
 * Four keycap styles copy the keyboards the CoCo family shipped with.
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

    static final float COLS = 14f;
    private static final int ROWS = 5;

    /* Keycap styles: id, menu name, key face, key legend, body (behind keys), special-key face. */
    static final String[] STYLE_IDS = { "white", "cream", "hitek", "grey" };
    static final String[] STYLE_NAMES = {
        "White (CoCo 2)", "Cream (TRS-80 Color Computer)", "Black (Hi-Tek)", "Grey (TRS-80)" };
    private static final int[][] STYLES = {
        // face,      legend,     body,       special face
        { 0xFFF1F1EC, 0xFF2B2B2B, 0xFFBDBDB6, 0xFFE2E2DC },
        { 0xFFEDE4C6, 0xFF2E2A22, 0xFF4A4438, 0xFFDCD2B0 },
        { 0xFF232326, 0xFFEDEDED, 0xFF0B0B0D, 0xFF17171A },
        { 0xFFB9BBBD, 0xFF161616, 0xFF5E6165, 0xFFA3A6A9 },
    };
    private static final int BREAK_FACE = 0xFFC8342B;
    // How the keycaps sit, per style: gap between caps (fraction of a key unit),
    // corner roundness, and how tall the cap's side shows.  The Hi-Tek keys
    // nearly touch; the CoCo 2's are rounder with visible gaps.
    private static final float[] GAP = { 0.06f, 0.035f, 0.012f, 0.03f };
    private static final float[] ROUND = { 0.20f, 0.10f, 0.05f, 0.12f };
    private static final float[] SIDE = { 0.08f, 0.05f, 0.04f, 0.06f };
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
    private int style = 0;

    private boolean shiftLatched;
    private boolean shiftLocked;
    private long lastShiftTap;

    CocoKeyboardView(Context c) {
        super(c);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFFFFF);
        text.setFakeBoldText(true);
        legend.setTextAlign(Paint.Align.CENTER);
        legend.setFakeBoldText(true);
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

    void setStyle(String id) {
        int st = 0;
        for (int i = 0; i < STYLE_IDS.length; i++) {
            if (STYLE_IDS[i].equals(id)) {
                st = i;
            }
        }
        if (st != style) {
            style = st;
            layoutKeys(getWidth(), getHeight());
        }
        invalidate();
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
        // Row 0: 1-0 : - then BREAK, stepped in half a key
        String[] digits = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" };
        String[] digitsShift = { "!", "\"", "#", "$", "%", "&", "'", "(", ")", "Ø" };
        int[] digitCodes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 };
        float x = 0.5f;
        for (int i = 0; i < 10; i++) {
            add(0, x++, 1, digits[i], digitsShift[i], digitCodes[i]);
        }
        add(0, x++, 1, ":", "*", 0x0a);
        add(0, x++, 1, "-", "=", 0x0d);
        add(0, x, 1.2f, "BREAK", null, 0x32);

        // Row 1: UP Q-P @ LEFT RIGHT
        add(1, 0, 1, "↑", null, 0x2b);
        x = 1;
        for (char ch : "QWERTYUIOP".toCharArray()) {
            add(1, x++, 1, String.valueOf(ch), null, 0x11 + (ch - 'A'));
        }
        add(1, x++, 1, "@", null, 0x10);
        add(1, x++, 1, "←", null, 0x2d);
        add(1, x, 1, "→", null, 0x2e);

        // Row 2: DOWN A-L ; ENTER CLEAR
        add(2, 0.25f, 1, "↓", null, 0x2c);
        x = 1.25f;
        for (char ch : "ASDFGHJKL".toCharArray()) {
            add(2, x++, 1, String.valueOf(ch), null, 0x11 + (ch - 'A'));
        }
        add(2, x++, 1, ";", "+", 0x0b);
        add(2, x, 1.6f, "ENTER", null, 0x30);
        add(2, x + 1.6f, 1.1f, "CLEAR", null, 0x31);

        // Row 3: SHIFT Z-M , . / SHIFT
        add(3, 0, 1.5f, "SHIFT", null, D_SHIFT);
        x = 1.5f;
        for (char ch : "ZXCVBNM".toCharArray()) {
            add(3, x++, 1, String.valueOf(ch), null, 0x11 + (ch - 'A'));
        }
        add(3, x++, 1, ",", "<", 0x0c);
        add(3, x++, 1, ".", ">", 0x0e);
        add(3, x++, 1, "/", "?", 0x0f);
        add(3, x, 1.5f, "SHIFT", null, D_SHIFT);

        // Row 4: SPACE
        add(4, 3f, 8f, "SPACE", null, 0x2f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutKeys(w, h);
    }

    private void layoutKeys(int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        float cw = w / COLS;
        float ch = h / (float) ROWS;
        float gap = Math.max(0.8f, Math.min(cw, ch) * GAP[style]);
        for (Key k : keys) {
            k.rect.set(k.x * cw + gap, k.row * ch + gap, (k.x + k.w) * cw - gap, (k.row + 1) * ch - gap * 1.8f);
        }
        float unit = Math.min(cw, ch);
        text.setTextSize(unit * 0.44f);
        legend.setTextSize(unit * 0.27f);
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
        int[] st = STYLES[style];
        c.drawColor(overlay ? (st[2] & 0x00FFFFFF) | 0xAA000000 : st[2]);
        boolean shifted = shiftActive();
        int alpha = overlay ? 0xE0000000 : 0xFF000000;
        for (Key k : keys) {
            boolean isBreak = k.code == 0x32;
            int face = isBreak ? BREAK_FACE : (k.label.length() > 1 || k.code == 0x2b ? st[3] : st[0]);
            int ink = isBreak ? 0xFFFFFFFF : st[1];
            if (k.code == D_SHIFT && shiftLocked) {
                face = 0xFFE0A030;
            } else if (k.code == D_SHIFT && (shiftLatched || k.down)) {
                face = 0xFF5B9BE0;
            }
            float r = Math.min(k.rect.width(), k.rect.height()) * ROUND[style];
            float lift = k.down ? 0 : k.rect.height() * SIDE[style];
            RectF f = new RectF(k.rect.left, k.rect.top + (k.down ? lift : 0),
                    k.rect.right, k.rect.bottom - (k.down ? 0 : lift));
            // the key's side: a darker copy below the face
            if (!k.down) {
                fill.setColor((shade(face, 0.62f) & 0x00FFFFFF) | alpha);
                c.drawRoundRect(new RectF(f.left, f.top + lift, f.right, f.bottom + lift), r, r, fill);
            }
            fill.setColor((face & 0x00FFFFFF) | alpha);
            c.drawRoundRect(f, r, r, fill);
            // a soft highlight across the top of the cap
            fill.setColor((shade(face, 1.18f) & 0x00FFFFFF) | (overlay ? 0x90000000 : 0xB0000000));
            c.drawRoundRect(new RectF(f.left + r * 0.4f, f.top + r * 0.3f, f.right - r * 0.4f,
                    f.top + f.height() * 0.38f), r * 0.6f, r * 0.6f, fill);

            text.setColor(ink);
            legend.setColor(ink);
            String label = k.label;
            String sub = (k.shifted != null) ? k.shifted : null;
            float size = text.getTextSize();
            if (shifted && sub != null && !k.label.equals("0")) {
                // SHIFT is down: the shifted symbol is what you'll get; bring it forward
                label = sub;
                sub = k.label;
            }
            if (label.length() > 2) {
                text.setTextSize(Math.min(size * 0.55f, f.width() / label.length() * 1.1f));
            }
            boolean two = sub != null;
            float cy = (two ? f.top + f.height() * 0.68f : f.centerY())
                    - (text.descent() + text.ascent()) / 2f;
            c.drawText(label, f.centerX(), cy, text);
            text.setTextSize(size);
            if (two) {
                legend.setTextAlign(Paint.Align.CENTER);
                legend.setAlpha(shifted ? 0x70 : 0xFF);
                c.drawText(sub, f.centerX(), f.top + f.height() * 0.36f, legend);
                legend.setAlpha(0xFF);
            }
        }
    }

    private static int shade(int color, float m) {
        int r = Math.min(255, Math.round(((color >> 16) & 255) * m));
        int g = Math.min(255, Math.round(((color >> 8) & 255) * m));
        int b = Math.min(255, Math.round((color & 255) * m));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
