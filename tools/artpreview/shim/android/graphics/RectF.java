package android.graphics;
/* Desktop stand-in for android.graphics.RectF (tools/artpreview only). */
public class RectF {
    public float left, top, right, bottom;
    public RectF() { }
    public RectF(float l, float t, float r, float b) { set(l, t, r, b); }
    public RectF(RectF o) { set(o); }
    public void set(float l, float t, float r, float b) { left = l; top = t; right = r; bottom = b; }
    public void set(RectF o) { set(o.left, o.top, o.right, o.bottom); }
    public final float width() { return right - left; }
    public final float height() { return bottom - top; }
    public final float centerX() { return (left + right) / 2f; }
    public final float centerY() { return (top + bottom) / 2f; }
    public void inset(float dx, float dy) { left += dx; right -= dx; top += dy; bottom -= dy; }
    public void offset(float dx, float dy) { left += dx; right += dx; top += dy; bottom += dy; }
    public boolean contains(float x, float y) { return x >= left && x < right && y >= top && y < bottom; }
    public void roundOut(Rect r) { r.set((int) Math.floor(left), (int) Math.floor(top), (int) Math.ceil(right), (int) Math.ceil(bottom)); }
}
