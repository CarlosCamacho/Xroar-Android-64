package android.graphics;
/* Desktop stand-in (tools/artpreview only). */
public class BitmapShader extends Shader {
    private final java.awt.Paint p;
    public BitmapShader(Bitmap b, TileMode x, TileMode y) {
        p = new java.awt.TexturePaint(b.img, new java.awt.geom.Rectangle2D.Float(0, 0, b.getWidth(), b.getHeight()));
    }
    @Override java.awt.Paint awt() { return p; }
}
