package io.github.carloscamacho.xroar;

/* The app's colors.  Brand: red #C8102E with its complementary green #00B140
 * (the owner's choice, 2026-11-08), on the dark surfaces of the earlier
 * MaterialKolor scheme.  Contrast (WCAG) against SURFACE #120D0E:
 *   #C8102E as text 3.3:1 (too faint), white text on #C8102E 5.9:1 (AA):
 *     so brand red is a FILL (buttons) with white text, and red TEXT uses the
 *     lighter tint #FF5C70 (6.4:1);
 *   #00B140 as text 6.8:1: the "good" state color (found, ready, all fine).
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

    // "Slightly rounded", as the owner asked: not Material's pill buttons.
    static final int BUTTON_RADIUS_DP = 6;
    static final int CARD_RADIUS_DP = 8;

    private Theme() {
    }
}
