package io.github.carloscamacho.xroar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

import java.io.File;
import java.util.List;

/* Renders the app's keyboard and joystick art (KeyboardArt, JoystickArt) to PNG
 * on a desktop, through the Java2D stand-ins in shim/, so it can be looked at
 * before it goes on a phone.  See run.sh. */
public final class Preview {
    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : ".");
        out.mkdirs();
        int w = 1080, h = 400;
        List<KeyboardArt.KeyDef> keys = KeyboardArt.layout();
        for (int s = 0; s < KeyboardArt.STYLE_IDS.length; s++) {
            KeyboardArt.Style st = KeyboardArt.style(s);
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            KeyboardArt.drawBody(c, w, h, st);
            RectF r = new RectF();
            for (KeyboardArt.KeyDef k : keys) {
                KeyboardArt.keyRect(k, w, h, st, r);
                boolean pressed = k.label.equals("G");     // show one key held down
                int tint = k.code == KeyboardArt.D_SHIFT && r.left < w / 2 ? 0xFF5B9BE0 : 0;
                KeyboardArt.drawKey(c, r, k, st, pressed, false, tint, !pressed);
            }
            write(b, new File(out, "kbd-" + KeyboardArt.STYLE_IDS[s] + ".png"));
        }
        for (String t : new String[] { "std", "deluxe", "wico" }) {
            int jw = 1080, jh = 540;
            Bitmap b = Bitmap.createBitmap(jw * 2, jh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(0xFF000000);
            for (int half = 0; half < 2; half++) {
                c.save();
                c.translate(half * jw, 0);
                JoystickArt.Geo g = JoystickArt.layout(t, jw, jh);
                JoystickArt.drawStatic(c, t, g);
                if (half == 0) {
                    JoystickArt.drawDynamic(c, t, g, 0, 0, false, false);
                } else {
                    JoystickArt.drawDynamic(c, t, g, -0.75f, -0.65f, true, false);
                }
                c.restore();
            }
            write(b, new File(out, "joy-" + t + ".png"));
        }
    }

    private static void write(Bitmap b, File f) throws Exception {
        javax.imageio.ImageIO.write(b.javaImage(), "png", f);
        System.out.println("wrote " + f);
    }
}
