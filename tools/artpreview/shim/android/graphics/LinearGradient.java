package android.graphics;
import java.awt.MultipleGradientPaint;
/* Desktop stand-in (tools/artpreview only). */
public class LinearGradient extends Shader {
    private final java.awt.Paint p;
    public LinearGradient(float x0, float y0, float x1, float y1, int c0, int c1, TileMode m) {
        this(x0, y0, x1, y1, new int[] { c0, c1 }, null, m);
    }
    public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] pos, TileMode m) {
        if (x0 == x1 && y0 == y1) { y1 += 0.01f; }
        p = new java.awt.LinearGradientPaint(x0, y0, x1, y1, Grad.fr(colors, pos), Grad.cols(colors),
                m == TileMode.REPEAT ? MultipleGradientPaint.CycleMethod.REPEAT
                : m == TileMode.MIRROR ? MultipleGradientPaint.CycleMethod.REFLECT : MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }
    @Override java.awt.Paint awt() { return p; }
}
