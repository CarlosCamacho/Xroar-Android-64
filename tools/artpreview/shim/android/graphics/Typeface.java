package android.graphics;
/* Desktop stand-in (tools/artpreview only). */
public class Typeface {
    public static final int NORMAL = 0, BOLD = 1, ITALIC = 2, BOLD_ITALIC = 3;
    final String family; final int style;
    private Typeface(String f, int s) { family = f; style = s; }
    public static final Typeface DEFAULT = new Typeface("SansSerif", NORMAL);
    public static final Typeface DEFAULT_BOLD = new Typeface("SansSerif", BOLD);
    public static final Typeface SANS_SERIF = DEFAULT;
    public static Typeface create(String family, int style) { return new Typeface("SansSerif", style); }
    public static Typeface create(Typeface t, int style) { return new Typeface(t == null ? "SansSerif" : t.family, style); }
}
