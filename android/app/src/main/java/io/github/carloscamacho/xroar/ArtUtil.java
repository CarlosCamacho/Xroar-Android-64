package io.github.carloscamacho.xroar;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/* Small drawing helpers shared by KeyboardArt and JoystickArt.  Only
 * android.graphics is used, so tools/artpreview can render the same code on a
 * desktop and the result can be looked at before it goes on a phone. */
final class ArtUtil {
    private static Bitmap grain, streaks;

    private ArtUtil() {
    }

    /* color * m, per channel, alpha kept */
    static int shade(int color, float m) {
        int r = clamp(Math.round(((color >> 16) & 255) * m));
        int g = clamp(Math.round(((color >> 8) & 255) * m));
        int b = clamp(Math.round((color & 255) * m));
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /* a blended toward b by t (0..1) */
    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        int al = Math.round((a >>> 24) * (1 - t) + (b >>> 24) * t);
        return (al << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
    }

    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 255) << 24);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /* Fine plastic grain: random light and dark specks, mostly transparent. */
    static synchronized Bitmap grain() {
        if (grain == null) {
            int n = 128;
            int[] px = new int[n * n];
            Random r = new Random(6809);
            for (int i = 0; i < px.length; i++) {
                int a = (int) (Math.pow(r.nextFloat(), 2.2) * 255);
                px[i] = (a << 24) | (r.nextBoolean() ? 0xFFFFFF : 0x000000);
            }
            grain = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        }
        return grain;
    }

    /* Horizontal streaks, for brushed aluminium. */
    static synchronized Bitmap streaks() {
        if (streaks == null) {
            int w = 256, h = 64;
            int[] px = new int[w * h];
            Random r = new Random(1980);
            for (int y = 0; y < h; y++) {
                float base = r.nextFloat();
                for (int x = 0; x < w; x++) {
                    float v = base * 0.7f + r.nextFloat() * 0.3f;
                    int a = (int) (Math.abs(v - 0.5f) * 2 * 140);
                    px[y * w + x] = (a << 24) | (v > 0.5f ? 0xFFFFFF : 0x000000);
                }
            }
            streaks = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        }
        return streaks;
    }

    static void texture(Canvas c, Paint p, Bitmap tex, RectF r, float radius, int strength) {
        p.setShader(new BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(0xFFFFFFFF, strength));
        c.drawRoundRect(r, radius, radius, p);
        p.setShader(null);
    }

    static void vgrad(Canvas c, Paint p, RectF r, float radius, int[] colors, float[] pos) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, colors, pos, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, p);
        p.setShader(null);
    }

    static void dgrad(Canvas c, Paint p, RectF r, float radius, int from, int to) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, from, to, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, p);
        p.setShader(null);
    }

    /* A sphere-lit disc: light from the top left. */
    static void sphere(Canvas c, Paint p, float x, float y, float r, int[] colors, float[] pos) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(x - r * 0.35f, y - r * 0.4f, r * 1.45f, colors, pos, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
    }

    /* A soft round shadow without a blur filter (cheap enough to draw every frame). */
    static void softShadow(Canvas c, Paint p, float x, float y, float r, int strength) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(x, y, r, new int[] { alpha(0, strength), alpha(0, strength / 2), 0 },
                new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
    }

    static void specular(Canvas c, Paint p, float x, float y, float r, int strength) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(x, y, r, alpha(0xFFFFFFFF, strength), 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
    }
}
