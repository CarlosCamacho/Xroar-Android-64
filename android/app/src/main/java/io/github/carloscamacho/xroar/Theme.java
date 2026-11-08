package io.github.carloscamacho.xroar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

/* The app's colors, type and shapes.
 *
 * Colors (v12, kept): brand red #C8102E with its complementary green #00B140
 * (the owner's choice, 2026-11-08), on the dark surfaces of the earlier
 * MaterialKolor scheme.  Contrast (WCAG) against SURFACE #120D0E:
 *   #C8102E as text 3.3:1 (too faint), white text on #C8102E 5.9:1 (AA):
 *     so brand red is a FILL (buttons) with white text, and red TEXT uses the
 *     lighter tint #FF5C70 (6.4:1);
 *   #00B140 as text 6.8:1: the "good" state color (found, ready, all fine).
 *
 * Type, spacing, radii, strokes and component shapes (v13) come from the
 * BUIK - Basic UI Kit Figma file (PSKgIFYjTuinNisxM59Etf), read 2026-11-08.
 * BUIK's own palette maps onto ours: its highlight #F12633 is our brand red,
 * its foreground #1C1C1C / background #FFFFFF are our light text / dark
 * surface, its positive #73E664 is our green.  BUIK sets everything in
 * Archivo (body) and Archivo Black (headings, button labels, active tabs).
 * Both are bundled in res/font (SIL OFL 1.1, licences in assets/licenses):
 * Archivo cut to a static Regular instance (wght 400, wdth 100) and both
 * subset to Latin-1, Latin Extended-A and common punctuation.  Android 8+
 * loads them; on 7.x the system sans-serif / sans-serif-black stand in.
 *
 * res/values/styles.xml gives the same values to the system widgets. */
final class Theme {
    static final int BRAND_RED = 0xFFC8102E;
    static final int BRAND_GREEN = 0xFF00B140;

    static final int PRIMARY = 0xFFFF5C70;              // red text / accents on dark
    static final int ON_PRIMARY = 0xFF000000;
    static final int PRIMARY_CONTAINER = BRAND_RED;     // filled buttons
    static final int ON_PRIMARY_CONTAINER = 0xFFFFFFFF;
    static final int PRIMARY_PRESSED = 0xFFA50D26;
    static final int SECONDARY = 0xFFF2B35A;            // warnings (amber)
    static final int SECONDARY_CONTAINER = 0xFF5A0A16;
    static final int ON_SECONDARY_CONTAINER = 0xFFFFD9DE;
    static final int TERTIARY = BRAND_GREEN;
    static final int TERTIARY_CONTAINER = BRAND_GREEN;  // "found / ready / all fine"
    static final int ERROR = 0xFFFF8A50;                // orange, so it isn't mistaken for the brand red
    static final int SURFACE = 0xFF120D0E;
    static final int SURFACE_RAISED = 0xFF241E1F;
    static final int ON_SURFACE = 0xFFFDF1F2;
    static final int TEXT_STRONG = 0xFFEEDFE1;
    static final int TEXT_MUTED = 0xFFB5A8AA;
    static final int BORDER_FAINT = 0xFF3B3334;
    static final int BORDER_SOFT = 0xFF53494B;
    static final int BORDER_STRONG = 0xFFA79B9D;

    /* ---- BUIK type scale (sp).  Headings: Archivo Black, line height 1.3. */
    static final int H1 = 40;
    static final int H2 = 32;
    static final int H3 = 28;
    static final int H4 = 24;
    static final int H5 = 20;
    static final int H6 = 16;
    /* Paragraphs: Archivo, line height 1.6.  L1-L5 are the same sizes in Archivo Black. */
    static final int P1 = 20;
    static final int P2 = 18;
    static final int P3 = 16;
    static final int P4 = 14;   // inputs, drop-downs, button labels
    static final int P5 = 12;   // notes, list headers
    static final float HEADING_LINE = 1.3f;
    static final float BODY_LINE = 1.6f;

    /* ---- BUIK spacing: an 8 dp grid, with 4 for hairline gaps. */
    static final int SPACE_XXS = 4;
    static final int SPACE_XS = 8;
    static final int SPACE_S = 16;     // control insets, row padding, icon-to-label gap
    static final int SPACE_M = 24;
    static final int SPACE_L = 32;     // between a heading and its block
    static final int SPACE_XL = 40;    // between items in a list of headings
    static final int SPACE_XXL = 64;   // between sections

    /* ---- BUIK shapes. */
    static final int RADIUS_DP = 4;            // every box: buttons, inputs, drop-downs, toggles, cards, checkboxes
    static final int BUTTON_RADIUS_DP = RADIUS_DP;
    static final int CARD_RADIUS_DP = RADIUS_DP;
    static final int LINE_DP = 1;              // "Atom/Bordered"
    static final int FOCUS_LINE_DP = 3;        // "Atom/Bordered Focus"
    static final int CONTROL_HEIGHT_DP = 48;   // buttons, inputs, drop-downs, list rows, tabs
    static final int TAB_INDICATOR_DP = 2;
    static final int TOGGLE_W_DP = 48;
    static final int TOGGLE_H_DP = 24;
    static final int TOGGLE_THUMB_DP = 14;
    static final int CHEVRON_DP = 8;

    private static Typeface bodyFace;
    private static Typeface heavyFace;

    private Theme() {
    }

    /* Archivo, or the system sans-serif before Android 8. */
    static Typeface bodyFace(Context c) {
        if (bodyFace == null) {
            bodyFace = load(c, R.font.archivo_regular, "sans-serif");
        }
        return bodyFace;
    }

    /* Archivo Black, or the system sans-serif-black before Android 8. */
    static Typeface heavyFace(Context c) {
        if (heavyFace == null) {
            heavyFace = load(c, R.font.archivo_black, "sans-serif-black");
        }
        return heavyFace;
    }

    private static Typeface load(Context c, int id, String fallback) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                Typeface t = c.getResources().getFont(id);
                if (t != null) {
                    return t;
                }
            } catch (RuntimeException e) {
                // fall through to the system face
            }
        }
        return Typeface.create(fallback, Typeface.NORMAL);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /* A BUIK box: 4 dp corners, an optional fill and stroke. */
    static GradientDrawable box(Context c, int fill, int strokeDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, RADIUS_DP));
        if (strokeDp > 0) {
            g.setStroke(dp(c, strokeDp), stroke);
        }
        return g;
    }

    /* ---- Text */

    static TextView heading(Context c, String text, int sizeSp) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(TEXT_STRONG);
        t.setTypeface(heavyFace(c));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setLineSpacing(0, HEADING_LINE);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView body(Context c, String text, int sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(color);
        t.setTypeface(bodyFace(c));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setLineSpacing(0, BODY_LINE);
        return t;
    }

    /* ---- Buttons: 48 dp, 4 dp corners, P4 label in the heavy face. */

    private static Button baseButton(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(heavyFace(c));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, P4);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(c, CONTROL_HEIGHT_DP));
        b.setMinimumHeight(dp(c, CONTROL_HEIGHT_DP));
        b.setPadding(dp(c, SPACE_S), 0, dp(c, SPACE_S), 0);
        b.setStateListAnimator(null);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    /* BUIK "Solid": a brand-red fill, white label. */
    static Button solidButton(Context c, String label) {
        Button b = baseButton(c, label);
        b.setTextColor(ON_PRIMARY_CONTAINER);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { -android.R.attr.state_enabled }, box(c, BORDER_FAINT, 0, 0));
        s.addState(new int[] { android.R.attr.state_focused }, focusRing(c, box(c, BRAND_RED, 0, 0)));
        s.addState(new int[] {}, box(c, BRAND_RED, 0, 0));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), s, null));
        return b;
    }

    /* BUIK "Outlined": a 1 dp red line, red label, no fill. */
    static Button outlinedButton(Context c, String label) {
        Button b = baseButton(c, label);
        b.setTextColor(PRIMARY);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { android.R.attr.state_focused }, box(c, 0x00000000, FOCUS_LINE_DP, PRIMARY));
        s.addState(new int[] {}, box(c, 0x00000000, LINE_DP, PRIMARY));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FF5C70), s, box(c, 0xFFFFFFFF, 0, 0)));
        return b;
    }

    /* A label-only button (header actions, Reset): red label, no box until pressed. */
    static Button textButton(Context c, String label) {
        Button b = baseButton(c, label);
        b.setTextColor(PRIMARY);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FF5C70), null, box(c, 0xFFFFFFFF, 0, 0)));
        return b;
    }

    private static Drawable focusRing(Context c, Drawable inner) {
        return new LayerDrawable(new Drawable[] { inner, box(c, 0x00000000, FOCUS_LINE_DP, PRIMARY) });
    }

    /* ---- Inputs and drop-downs: 48 dp, 1 dp line, 3 dp red line on focus, 16 dp insets. */

    static Drawable fieldBackground(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { -android.R.attr.state_enabled }, box(c, BORDER_FAINT, 0, 0));
        s.addState(new int[] { android.R.attr.state_focused }, box(c, 0x00000000, FOCUS_LINE_DP, PRIMARY));
        s.addState(new int[] { android.R.attr.state_pressed }, box(c, 0x00000000, FOCUS_LINE_DP, PRIMARY));
        s.addState(new int[] {}, box(c, 0x00000000, LINE_DP, BORDER_STRONG));
        return s;
    }

    static void styleInput(EditText e) {
        Context c = e.getContext();
        e.setBackground(fieldBackground(c));
        e.setTypeface(bodyFace(c));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, P4);
        e.setTextColor(TEXT_STRONG);
        e.setHintTextColor(TEXT_MUTED);
        e.setMinHeight(dp(c, CONTROL_HEIGHT_DP));
        e.setMinimumHeight(dp(c, CONTROL_HEIGHT_DP));
        e.setPadding(dp(c, SPACE_S), 0, dp(c, SPACE_S), 0);
        e.setGravity(Gravity.CENTER_VERTICAL);
    }

    /* The open drop-down list: a raised box with the same 1 dp line. */
    static Drawable popupBackground(Context c) {
        return box(c, SURFACE_RAISED, LINE_DP, BORDER_SOFT);
    }

    /* One drop-down row (closed or in the list): 48 dp, P4, 16 dp insets. */
    static TextView dropdownRow(Context c, CharSequence text, boolean inList, boolean selected) {
        TextView t = body(c, text.toString(), P4, selected && inList ? PRIMARY : TEXT_STRONG);
        t.setLineSpacing(0, 1f);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(c, CONTROL_HEIGHT_DP));
        // The closed field leaves room on the right for the 8 dp chevron.
        t.setPadding(dp(c, SPACE_S), 0, dp(c, inList ? SPACE_S : SPACE_S + CHEVRON_DP + SPACE_XS), 0);
        if (inList && selected) {
            t.setTypeface(heavyFace(c));
        }
        return t;
    }

    /* ---- Toggle: a 48 x 24 box with a 14 dp square knob, all 4 dp corners.
     * On: brand-red fill, white knob at the right.  Off: 1 dp line, light knob at the left. */

    static void styleToggle(Switch s) {
        Context c = s.getContext();
        StateListDrawable track = new StateListDrawable();
        GradientDrawable on = box(c, BRAND_RED, 0, 0);
        on.setSize(dp(c, TOGGLE_W_DP), dp(c, TOGGLE_H_DP));
        GradientDrawable off = box(c, 0x00000000, LINE_DP, BORDER_STRONG);
        off.setSize(dp(c, TOGGLE_W_DP), dp(c, TOGGLE_H_DP));
        GradientDrawable dis = box(c, BORDER_FAINT, 0, 0);
        dis.setSize(dp(c, TOGGLE_W_DP), dp(c, TOGGLE_H_DP));
        track.addState(new int[] { -android.R.attr.state_enabled }, dis);
        track.addState(new int[] { android.R.attr.state_checked }, on);
        track.addState(new int[] {}, off);

        int inset = (TOGGLE_H_DP - TOGGLE_THUMB_DP) / 2;   // 5 dp all round
        StateListDrawable thumb = new StateListDrawable();
        thumb.addState(new int[] { android.R.attr.state_checked }, knob(c, 0xFFFFFFFF, inset));
        thumb.addState(new int[] {}, knob(c, TEXT_STRONG, inset));

        s.setTrackDrawable(track);
        s.setThumbDrawable(thumb);
        s.setTrackTintList(null);
        s.setThumbTintList(null);
        s.setSwitchMinWidth(dp(c, TOGGLE_W_DP));
        s.setShowText(false);
        s.setSwitchPadding(dp(c, SPACE_S));
    }

    private static Drawable knob(Context c, int color, int insetDp) {
        GradientDrawable k = box(c, color, 0, 0);
        k.setSize(dp(c, TOGGLE_THUMB_DP), dp(c, TOGGLE_THUMB_DP));
        return new InsetDrawable(k, dp(c, insetDp));
    }

    /* ---- Tabs: label in a 48 dp cell over a 2 dp bar.  Active: red heavy label, red bar.
     * Inactive: regular light label, a 1 dp light line (BUIK draws a thin foreground bar). */

    static void styleTab(Button label, View bar, boolean active) {
        label.setTextColor(active ? PRIMARY : TEXT_MUTED);
        label.setTypeface(active ? heavyFace(label.getContext()) : bodyFace(label.getContext()));
        if (active) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(BRAND_RED);
            g.setCornerRadius(dp(bar.getContext(), RADIUS_DP));
            bar.setBackground(g);
        } else {
            // Bottom 1 dp of the 2 dp bar, so the row reads as one baseline.
            GradientDrawable line = new GradientDrawable();
            line.setColor(BORDER_SOFT);
            bar.setBackground(new InsetDrawable(line, 0, dp(bar.getContext(), 1), 0, 0));
        }
    }

    /* ---- Cards: BUIK's bordered box, on a raised surface (shadows don't read on dark). */

    static Drawable card(Context c) {
        return box(c, SURFACE_RAISED, LINE_DP, BORDER_FAINT);
    }

    /* ---- List rows: 48 dp minimum, 16 dp insets, a 1 dp separator at the bottom.
     * Pressed: BUIK's "Active" row, a brand-red fill. */

    static Drawable listRow(Context c) {
        GradientDrawable sep = new GradientDrawable();
        sep.setColor(BORDER_FAINT);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0x00000000);
        LayerDrawable normal = new LayerDrawable(new Drawable[] { fill, sep });
        normal.setLayerInset(1, 0, 0, 0, 0);
        normal.setLayerGravity(1, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        normal.setLayerHeight(1, dp(c, LINE_DP));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(BRAND_RED);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { android.R.attr.state_pressed }, pressed);
        s.addState(new int[] {}, normal);
        return s;
    }

    static View separator(Context c) {
        View v = new View(c);
        v.setBackgroundColor(BORDER_FAINT);
        v.setMinimumHeight(dp(c, LINE_DP));
        return v;
    }
}
