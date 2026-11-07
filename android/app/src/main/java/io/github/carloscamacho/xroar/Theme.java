package io.github.carloscamacho.xroar;

/* The app's colors: the owner's MaterialKolor theme (dark scheme), Material
 * Design 3 roles.  res/values/styles.xml uses the same values for the system
 * widgets (dialogs, check boxes, drop-downs). */
final class Theme {
    static final int PRIMARY = 0xFFFF8E83;
    static final int ON_PRIMARY = 0xFF650006;
    static final int PRIMARY_CONTAINER = 0xFFFF766B;
    static final int ON_PRIMARY_CONTAINER = 0xFF4F0003;
    static final int PRIMARY_PRESSED = 0xFFFF897E;
    static final int SECONDARY = 0xFFFD81A8;
    static final int SECONDARY_CONTAINER = 0xFF822047;
    static final int ON_SECONDARY_CONTAINER = 0xFFFFBFCF;
    static final int TERTIARY = 0xFFCCFFE6;
    static final int TERTIARY_CONTAINER = 0xFF90FACE;
    static final int ERROR = 0xFFFF7351;
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
