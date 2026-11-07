package android.graphics;
/* Desktop stand-in for android.graphics.Paint (tools/artpreview only). */
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }
    public enum Align { LEFT, CENTER, RIGHT }
    int color = 0xFF000000;
    Style style = Style.FILL;
    float strokeWidth = 0;
    Cap cap = Cap.BUTT;
    Join join = Join.MITER;
    Shader shader;
    MaskFilter mask;
    float textSize = 12;
    Align align = Align.LEFT;
    boolean fakeBold;
    Typeface typeface = Typeface.DEFAULT;
    float letterSpacing;
    public Paint() { }
    public Paint(int flags) { }
    public void setAntiAlias(boolean a) { }
    public void setColor(int c) { color = c; }
    public int getColor() { return color; }
    public void setAlpha(int a) { color = (color & 0x00FFFFFF) | ((a & 255) << 24); }
    public int getAlpha() { return color >>> 24; }
    public void setStyle(Style s) { style = s; }
    public void setStrokeWidth(float w) { strokeWidth = w; }
    public void setStrokeCap(Cap c) { cap = c; }
    public void setStrokeJoin(Join j) { join = j; }
    public Shader setShader(Shader s) { shader = s; return s; }
    public MaskFilter setMaskFilter(MaskFilter m) { mask = m; return m; }
    public void setTextSize(float s) { textSize = s; }
    public float getTextSize() { return textSize; }
    public void setTextAlign(Align a) { align = a; }
    public void setFakeBoldText(boolean b) { fakeBold = b; }
    public Typeface setTypeface(Typeface t) { typeface = t; return t; }
    public void setLetterSpacing(float s) { letterSpacing = s; }
    java.awt.Font font() {
        boolean bold = fakeBold || (typeface != null && (typeface.style & Typeface.BOLD) != 0);
        boolean it = typeface != null && (typeface.style & Typeface.ITALIC) != 0;
        java.awt.Font f = new java.awt.Font("SansSerif", (bold ? java.awt.Font.BOLD : 0) | (it ? java.awt.Font.ITALIC : 0), 12);
        return f.deriveFont(textSize);
    }
    private java.awt.FontMetrics fm() {
        java.awt.image.BufferedImage i = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = i.createGraphics();
        java.awt.FontMetrics m = g.getFontMetrics(font());
        g.dispose();
        return m;
    }
    public float measureText(String s) { return (float) font().getStringBounds(s, new java.awt.font.FontRenderContext(null, true, true)).getWidth(); }
    public float ascent() { return -fm().getAscent(); }
    public float descent() { return fm().getDescent(); }
}
