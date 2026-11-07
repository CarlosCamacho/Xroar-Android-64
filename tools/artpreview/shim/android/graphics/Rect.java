package android.graphics;
/* Desktop stand-in for android.graphics.Rect (tools/artpreview only). */
public class Rect {
    public int left, top, right, bottom;
    public Rect() { }
    public Rect(int l, int t, int r, int b) { set(l, t, r, b); }
    public void set(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
    public final int width() { return right - left; }
    public final int height() { return bottom - top; }
    public boolean intersect(int l, int t, int r, int b) {
        if (left < r && l < right && top < b && t < bottom) {
            left = Math.max(left, l); top = Math.max(top, t); right = Math.min(right, r); bottom = Math.min(bottom, b);
            return true;
        }
        return false;
    }
}
