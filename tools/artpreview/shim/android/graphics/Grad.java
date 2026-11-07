package android.graphics;
/* Helpers for the gradient stand-ins (tools/artpreview only). */
final class Grad {
    static float[] fr(int[] colors, float[] pos) {
        int n = colors.length;
        float[] f = new float[n];
        for (int i = 0; i < n; i++) {
            f[i] = pos != null ? pos[i] : (n == 1 ? 0 : i / (float) (n - 1));
        }
        for (int i = 1; i < n; i++) {   // Java2D needs strictly increasing stops
            if (f[i] <= f[i - 1]) { f[i] = Math.min(1f, f[i - 1] + 1e-4f); }
        }
        for (int i = n - 2; i >= 0; i--) {
            if (f[i] >= f[i + 1]) { f[i] = f[i + 1] - 1e-4f; }
        }
        return f;
    }
    static java.awt.Color[] cols(int[] colors) {
        java.awt.Color[] c = new java.awt.Color[colors.length];
        for (int i = 0; i < c.length; i++) { c[i] = new java.awt.Color(colors[i], true); }
        return c;
    }
}
