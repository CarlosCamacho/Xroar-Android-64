package android.graphics;
/* Desktop stand-in (tools/artpreview only). */
public class BlurMaskFilter extends MaskFilter {
    public enum Blur { NORMAL, SOLID, OUTER, INNER }
    final float radius;
    public BlurMaskFilter(float radius, Blur style) { this.radius = radius; }
}
