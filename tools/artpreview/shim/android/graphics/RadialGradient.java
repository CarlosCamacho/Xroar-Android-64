package android.graphics;
/* Desktop stand-in (tools/artpreview only). */
public class RadialGradient extends Shader {
    private final java.awt.Paint p;
    public RadialGradient(float cx, float cy, float r, int c0, int c1, TileMode m) {
        this(cx, cy, r, new int[] { c0, c1 }, null, m);
    }
    public RadialGradient(float cx, float cy, float r, int[] colors, float[] pos, TileMode m) {
        p = new java.awt.RadialGradientPaint(cx, cy, Math.max(r, 0.01f), Grad.fr(colors, pos), Grad.cols(colors));
    }
    @Override java.awt.Paint awt() { return p; }
}
