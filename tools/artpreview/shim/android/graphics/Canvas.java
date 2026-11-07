package android.graphics;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
/* Desktop stand-in for android.graphics.Canvas on Java2D, so the app's drawing
 * code can be rendered to PNG files and looked at (tools/artpreview only). */
public class Canvas {
    private final BufferedImage img;
    private final Graphics2D g;
    private final ArrayDeque<Object[]> stack = new ArrayDeque<>();
    public Canvas(Bitmap b) {
        img = b.img;
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
    public int getWidth() { return img.getWidth(); }
    public int getHeight() { return img.getHeight(); }
    public int save() { stack.push(new Object[] { g.getTransform(), g.getClip() }); return stack.size(); }
    public void restore() { Object[] o = stack.pop(); g.setTransform((AffineTransform) o[0]); g.setClip((Shape) o[1]); }
    public void translate(float x, float y) { g.translate(x, y); }
    public void scale(float x, float y) { g.scale(x, y); }
    public void rotate(float deg) { g.rotate(Math.toRadians(deg)); }
    public void rotate(float deg, float px, float py) { g.rotate(Math.toRadians(deg), px, py); }
    public boolean clipRect(RectF r) { g.clip(new Rectangle2D.Float(r.left, r.top, r.width(), r.height())); return true; }
    public boolean clipRect(float l, float t, float r, float b) { g.clip(new Rectangle2D.Float(l, t, r - l, b - t)); return true; }
    public boolean clipPath(Path p) { g.clip(p.p); return true; }
    public void drawColor(int c) {
        AffineTransform t = g.getTransform();
        g.setTransform(new AffineTransform());
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new java.awt.Color(c, true));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setTransform(t);
    }
    private void paint(Shape s, Paint p) {
        Shape fillShape = null, strokeShape = null;
        if (p.style != Paint.Style.STROKE) { fillShape = s; }
        if (p.style != Paint.Style.FILL) {
            int cap = p.cap == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : p.cap == Paint.Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT;
            int join = p.join == Paint.Join.ROUND ? BasicStroke.JOIN_ROUND : p.join == Paint.Join.BEVEL ? BasicStroke.JOIN_BEVEL : BasicStroke.JOIN_MITER;
            strokeShape = new BasicStroke(Math.max(p.strokeWidth, 1f), cap, join).createStrokedShape(s);
        }
        for (Shape sh : new Shape[] { fillShape, strokeShape }) {
            if (sh != null) { fill(sh, p); }
        }
    }
    private void fill(Shape sh, Paint p) {
        float alpha = (p.color >>> 24) / 255f;
        if (p.mask instanceof BlurMaskFilter) {
            float r = ((BlurMaskFilter) p.mask).radius;
            BufferedImage tmp = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D t = tmp.createGraphics();
            t.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            t.setTransform(g.getTransform());
            t.setPaint(p.shader != null ? p.shader.awt() : new java.awt.Color(p.color | 0xFF000000));
            t.fill(sh);
            t.dispose();
            blur(tmp, Math.max(1, Math.round(r / 1.7f)));
            AffineTransform tr = g.getTransform();
            g.setTransform(new AffineTransform());
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.drawImage(tmp, 0, 0, null);
            g.setTransform(tr);
            g.setComposite(AlphaComposite.SrcOver);
            return;
        }
        if (p.shader != null) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setPaint(p.shader.awt());
        } else {
            g.setComposite(AlphaComposite.SrcOver);
            g.setPaint(new java.awt.Color(p.color, true));
        }
        g.fill(sh);
        g.setComposite(AlphaComposite.SrcOver);
    }
    private static void blur(BufferedImage im, int r) {   // three box blurs ~ a Gaussian
        int w = im.getWidth(), h = im.getHeight();
        int[] a = im.getRGB(0, 0, w, h, null, 0, w), b = new int[a.length];
        for (int pass = 0; pass < 3; pass++) { box(a, b, w, h, r, true); box(b, a, w, h, r, false); }
        im.setRGB(0, 0, w, h, a, 0, w);
    }
    private static void box(int[] src, int[] dst, int w, int h, int r, boolean horiz) {
        int n = horiz ? w : h, lines = horiz ? h : w, div = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            long sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[idx(Math.min(Math.max(i, 0), n - 1), l, w, horiz)];
                int al = c >>> 24;
                sa += al; sr += ((c >> 16) & 255) * al; sg += ((c >> 8) & 255) * al; sb += (c & 255) * al;
            }
            for (int i = 0; i < n; i++) {
                int A = (int) (sa / div);
                dst[idx(i, l, w, horiz)] = sa == 0 ? 0 : (A << 24) | ((int) (sr / sa) << 16) | ((int) (sg / sa) << 8) | (int) (sb / sa);
                int cin = src[idx(Math.min(i + r + 1, n - 1), l, w, horiz)], cout = src[idx(Math.max(i - r, 0), l, w, horiz)];
                int ai = cin >>> 24, ao = cout >>> 24;
                sa += ai - ao;
                sr += ((cin >> 16) & 255) * ai - ((cout >> 16) & 255) * ao;
                sg += ((cin >> 8) & 255) * ai - ((cout >> 8) & 255) * ao;
                sb += (cin & 255) * ai - (cout & 255) * ao;
            }
        }
    }
    private static int idx(int i, int l, int w, boolean horiz) { return horiz ? l * w + i : i * w + l; }
    public void drawRect(RectF r, Paint p) { paint(new Rectangle2D.Float(r.left, r.top, r.width(), r.height()), p); }
    public void drawRect(float l, float t, float r, float b, Paint p) { paint(new Rectangle2D.Float(l, t, r - l, b - t), p); }
    public void drawRoundRect(RectF r, float rx, float ry, Paint p) {
        paint(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), p);
    }
    public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint p) {
        paint(new RoundRectangle2D.Float(l, t, r - l, b - t, rx * 2, ry * 2), p);
    }
    public void drawCircle(float x, float y, float r, Paint p) { paint(new Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r), p); }
    public void drawOval(RectF r, Paint p) { paint(new Ellipse2D.Float(r.left, r.top, r.width(), r.height()), p); }
    public void drawPath(Path path, Paint p) { paint(path.p, p); }
    public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        Paint.Style s = p.style;
        p.style = Paint.Style.STROKE;
        paint(new Line2D.Float(x0, y0, x1, y1), p);
        p.style = s;
    }
    public void drawText(String text, float x, float y, Paint p) {
        java.awt.Font f = p.font();
        java.awt.font.GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), text);
        float w = (float) gv.getLogicalBounds().getWidth();
        if (p.align == Paint.Align.CENTER) { x -= w / 2; } else if (p.align == Paint.Align.RIGHT) { x -= w; }
        Shape s = gv.getOutline(x, y);
        Paint.Style st = p.style;
        if (st == Paint.Style.FILL) { fill(s, p); } else { paint(s, p); }
    }
    public void drawBitmap(Bitmap b, float x, float y, Paint p) {
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(b.img, Math.round(x), Math.round(y), null);
    }
    public void drawBitmap(Bitmap b, Rect src, RectF dst, Paint p) {
        g.setComposite(AlphaComposite.SrcOver);
        int sl = src == null ? 0 : src.left, st = src == null ? 0 : src.top;
        int sr = src == null ? b.getWidth() : src.right, sb = src == null ? b.getHeight() : src.bottom;
        g.drawImage(b.img, Math.round(dst.left), Math.round(dst.top), Math.round(dst.right), Math.round(dst.bottom), sl, st, sr, sb, null);
    }
    public void drawBitmap(Bitmap b, Rect src, Rect dst, Paint p) {
        drawBitmap(b, src, new RectF(dst.left, dst.top, dst.right, dst.bottom), p);
    }
}
