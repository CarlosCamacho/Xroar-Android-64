package android.graphics;
import java.awt.image.BufferedImage;
/* Desktop stand-in (tools/artpreview only). */
public class Bitmap {
    public enum Config { ARGB_8888 }
    final BufferedImage img;
    private Bitmap(BufferedImage i) { img = i; }
    public static Bitmap createBitmap(int w, int h, Config c) {
        return new Bitmap(new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB));
    }
    public static Bitmap createBitmap(int[] colors, int w, int h, Config c) {
        Bitmap b = createBitmap(w, h, c);
        b.img.setRGB(0, 0, w, h, colors, 0, w);
        return b;
    }
    public int getWidth() { return img.getWidth(); }
    public int getHeight() { return img.getHeight(); }
    public void eraseColor(int c) {
        java.awt.Graphics2D g = img.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.setColor(new java.awt.Color(c, true));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }
    public boolean isRecycled() { return false; }
    public void recycle() { }
    /* Preview only: the image behind it. */
    public BufferedImage javaImage() { return img; }
}
