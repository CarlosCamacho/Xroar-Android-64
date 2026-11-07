package android.graphics;
import java.awt.geom.Path2D;
/* Desktop stand-in (tools/artpreview only). */
public class Path {
    public enum Direction { CW, CCW }
    final Path2D.Float p = new Path2D.Float();
    public void reset() { p.reset(); }
    public void moveTo(float x, float y) { p.moveTo(x, y); }
    public void lineTo(float x, float y) { p.lineTo(x, y); }
    public void quadTo(float x1, float y1, float x2, float y2) { p.quadTo(x1, y1, x2, y2); }
    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) { p.curveTo(x1, y1, x2, y2, x3, y3); }
    public void close() { p.closePath(); }
    public void addRoundRect(RectF r, float rx, float ry, Direction d) {
        p.append(new java.awt.geom.RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), false);
    }
    public void addCircle(float x, float y, float r, Direction d) {
        p.append(new java.awt.geom.Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r), false);
    }
    public void addOval(RectF r, Direction d) {
        p.append(new java.awt.geom.Ellipse2D.Float(r.left, r.top, r.width(), r.height()), false);
    }
}
