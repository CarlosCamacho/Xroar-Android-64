package io.github.carloscamacho.xroar;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.system.Os;
import android.text.InputType;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.libsdl.app.SDLActivity;

/* XRoar on SDL2, with an Android front end.
 *
 * libmain.so holds XRoar plus the glue in android/glue/.  This activity wraps
 * SDL's own view (the emulator picture) with a toolbar, the CoCo keyboard, an
 * on-screen joystick and a settings panel, and talks to the emulator through
 * nativeCommand(), whose OP_* constants must match android_glue.h.
 *
 * Layout: in portrait the picture sits at the top (4:3, full width) with the
 * keyboard, pad or settings below it.  In landscape the picture fills the
 * screen and the keyboard, pad, toolbar and settings float over it.
 */
public class MainActivity extends SDLActivity {
    private static final String TAG = "XRoar";

    // Keep in step with android_glue.h
    static final int OP_KEY = 1;
    static final int OP_JOY = 2;
    static final int OP_SOFT_RESET = 3;
    static final int OP_HARD_RESET = 4;
    static final int OP_DISK_INSERT = 5;
    static final int OP_DISK_EJECT = 6;
    static final int OP_TAPE_INSERT = 7;
    static final int OP_TAPE_EJECT = 8;
    static final int OP_TAPE_PLAYPAUSE = 9;
    static final int OP_TAPE_REWIND = 10;
    static final int OP_TYPE = 11;
    static final int OP_RUN = 12;
    static final int OP_LOAD = 13;
    static final int OP_SET = 14;

    // File picker request codes
    static final int REQ_DISK = 100;   // + drive 0-3
    static final int REQ_TAPE = 110;
    static final int REQ_LOAD = 111;
    static final int REQ_RUN = 112;

    static native void nativeCommand(int op, int a, int b, String s);
    static native String nativePollMessage();

    private Settings settings;
    private FrameLayout root;
    private LinearLayout toolbar;
    private Button keysButton;
    private Button padButton;
    private CocoKeyboardView kbd;
    private PadView pad;
    private SettingsPanel panel;

    private boolean settingsOpen;
    private int insetL, insetT, insetR, insetB;
    private long lastBack;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling;
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            try {
                String m = nativePollMessage();
                if (m != null) {
                    Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show();
                }
            } catch (UnsatisfiedLinkError e) {
                return;   // native library not loaded: stop polling
            }
            if (polling) {
                handler.postDelayed(this, 400);
            }
        }
    };

    /* The app's folder for ROMs, xroar.conf, the SD card and the log:
     * Android/media/<package>/.  File managers can write there on Android 11+,
     * unlike Android/data/<package>/.  Falls back to the files directory. */
    File homeDir() {
        File[] media = getExternalMediaDirs();
        if (media != null) {
            for (File d : media) {
                if (d != null && (d.isDirectory() || d.mkdirs())) {
                    return d;
                }
            }
        }
        File d = getExternalFilesDir(null);
        return d != null ? d : getFilesDir();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Before super: SDLActivity starts the native thread later, and
        // android_main.c reads XROAR_HOME when it starts.
        try {
            Os.setenv("XROAR_HOME", homeDir().getAbsolutePath(), true);
        } catch (Exception e) {
            Log.e(TAG, "can't set XROAR_HOME", e);
        }
        settings = new Settings(homeDir());
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mLayout != null) {
            buildUi();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        polling = true;
        handler.postDelayed(poll, 400);
    }

    @Override
    protected void onPause() {
        polling = false;
        handler.removeCallbacks(poll);
        super.onPause();
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
    }

    /* "Open with XRoar" when the app is not running: passed to XRoar's
     * command line as "-run <path>"; android_main.c queues it for after start-up. */
    @Override
    protected String[] getArguments() {
        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return new String[0];
        }
        Uri uri = intent.getData();
        if (uri == null) {
            return new String[0];
        }
        try {
            String path = copyToHome(uri, "opened");
            if (path != null) {
                return new String[] { "-run", path };
            }
        } catch (Exception e) {
            Log.e(TAG, "can't open " + uri, e);
        }
        return new String[0];
    }

    /* "Open with XRoar" while it is running. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) {
            return;
        }
        setIntent(intent);
        try {
            String path = copyToHome(intent.getData(), "opened");
            if (path != null) {
                command(OP_RUN, 0, 0, path);
            }
        } catch (Exception e) {
            Log.e(TAG, "can't open " + intent.getData(), e);
        }
    }

    /* ---------------------------------------------------------------- */
    /* Commands to the emulator */

    void command(int op, int a, int b, String s) {
        try {
            nativeCommand(op, a, b, s);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "native library not loaded", e);
        }
    }

    Settings settings() {
        return settings;
    }

    /* Apply a setting live and remember it. */
    void apply(String key, String value) {
        settings.set(key, value);
        sendSet(key, value);
    }

    void sendSet(String key, String value) {
        command(OP_SET, 0, 0, key + "=" + value);
    }

    /* Machine and RAM changes rebuild the emulated machine, which drops its
     * cartridge: put the chosen cartridge back. */
    void applyMachineSetting(String key, String value) {
        apply(key, value);
        sendSet("cart", settings.get("cart", "auto"));
    }

    void applyJoystick(String key, String value) {
        apply(key, value);
        if ("kjoy0".equals(value) && !settings.getBool("ui_pad", false)) {
            setPadVisible(true);
        }
    }

    /* The first of these names found in roms/ or the app folder, or null. */
    String findRom(String[] labelAndNames) {
        File home = homeDir();
        File[] dirs = { new File(home, "roms"), home };
        String[] exts = { ".rom", ".ROM", "" };
        for (int i = 1; i < labelAndNames.length; i++) {
            for (File d : dirs) {
                for (String e : exts) {
                    File f = new File(d, labelAndNames[i] + e);
                    if (f.isFile()) {
                        return f.getName();
                    }
                }
            }
        }
        return null;
    }

    /* ---------------------------------------------------------------- */
    /* UI */

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Button toolButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackgroundColor(0x00000000);
        b.setOnClickListener(l);
        return b;
    }

    private void buildUi() {
        ViewGroup parent = (ViewGroup) mLayout.getParent();
        if (parent != null) {
            parent.removeView(mLayout);
        }
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(mLayout, new FrameLayout.LayoutParams(-1, -1));

        pad = new PadView(this);
        pad.setListener((which, down) -> command(OP_JOY, which, down ? 1 : 0, null));
        root.addView(pad, new FrameLayout.LayoutParams(0, 0));

        kbd = new CocoKeyboardView(this);
        kbd.setListener((code, down) -> command(OP_KEY, code, down ? 1 : 0, null));
        root.addView(kbd, new FrameLayout.LayoutParams(0, 0));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(0xE6000000);
        keysButton = toolButton("Keys", v -> setKeyboardVisible(!settings.getBool("ui_kbd", true)));
        padButton = toolButton("Pad", v -> setPadVisible(!settings.getBool("ui_pad", false)));
        Button typeButton = toolButton("Type…", v -> showTypeDialog());
        final Button resetButton = toolButton("Reset", null);
        resetButton.setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, resetButton);
            m.getMenu().add(0, 1, 0, "Soft reset (like the RESET button)");
            m.getMenu().add(0, 2, 1, "Hard reset (power off and on)");
            m.setOnMenuItemClickListener(item -> {
                command(item.getItemId() == 1 ? OP_SOFT_RESET : OP_HARD_RESET, 0, 0, null);
                return true;
            });
            m.show();
        });
        Button menuButton = toolButton("Menu", v -> openSettings());
        for (Button b : new Button[] { keysButton, padButton, typeButton, resetButton, menuButton }) {
            toolbar.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        root.addView(toolbar, new FrameLayout.LayoutParams(0, 0));

        panel = new SettingsPanel(this);
        panel.setVisibility(View.GONE);
        root.addView(panel, new FrameLayout.LayoutParams(0, 0));

        applyUiSettings();

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int t = ins.getSystemWindowInsetTop(), b = ins.getSystemWindowInsetBottom();
            int l = ins.getSystemWindowInsetLeft(), r = ins.getSystemWindowInsetRight();
            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout dc = ins.getDisplayCutout();
                if (dc != null) {
                    t = Math.max(t, dc.getSafeInsetTop());
                    b = Math.max(b, dc.getSafeInsetBottom());
                    l = Math.max(l, dc.getSafeInsetLeft());
                    r = Math.max(r, dc.getSafeInsetRight());
                }
            }
            insetL = l;
            insetT = t;
            insetR = r;
            insetB = b;
            layoutAll();
            return ins;
        });
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) {
                layoutAll();
            }
        });
        setContentView(root);
    }

    private void applyUiSettings() {
        boolean sticky = settings.getBool("ui_sticky", true);
        boolean haptic = settings.getBool("ui_haptic", true);
        kbd.setSticky(sticky);
        kbd.setHaptic(haptic);
        pad.setHaptic(haptic);
        boolean kv = settings.getBool("ui_kbd", true);
        boolean pv = settings.getBool("ui_pad", false);
        keysButton.setTextColor(kv ? 0xFF6CB0FF : Color.WHITE);
        padButton.setTextColor(pv ? 0xFF6CB0FF : Color.WHITE);
    }

    private static void place(View v, int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(w, h);
        }
        if (lp.leftMargin != x || lp.topMargin != y || lp.width != w || lp.height != h
                || lp.gravity != (Gravity.TOP | Gravity.LEFT)) {
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.leftMargin = x;
            lp.topMargin = y;
            lp.width = w;
            lp.height = h;
            v.setLayoutParams(lp);
        }
    }

    private static void show(View v, boolean on) {
        v.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void layoutAll() {
        if (root == null) {
            return;
        }
        int W = root.getWidth(), H = root.getHeight();
        if (W <= 0 || H <= 0) {
            return;
        }
        int l = insetL, r = insetR, t = insetT, b = insetB;
        int aw = W - l - r;
        int tb = dp(44);
        boolean kv = settings.getBool("ui_kbd", true);
        boolean pv = settings.getBool("ui_pad", false);

        if (H >= W) {
            // Portrait: toolbar, picture (4:3), then keyboard / pad / settings.
            int sdlH = aw * 3 / 4;
            int y = t + tb + sdlH;
            int avail = Math.max(0, H - b - y);
            place(toolbar, l, t, aw, tb);
            place(mLayout, l, t + tb, aw, sdlH);
            kbd.setOverlay(false);
            show(toolbar, true);
            if (settingsOpen) {
                place(panel, l, y, aw, avail);
                show(panel, true);
                show(kbd, false);
                show(pad, false);
            } else {
                show(panel, false);
                int kh = kv ? Math.min(Math.round(aw * 5 / 13f * 1.1f), avail) : 0;
                place(kbd, l, y, aw, Math.max(kh, 1));
                show(kbd, kv && kh > 0);
                int ph = pv ? Math.min(avail - kh, Math.round(aw * 0.5f)) : 0;
                place(pad, l, y + kh, aw, Math.max(ph, 1));
                show(pad, pv && ph > 0);
            }
        } else {
            // Landscape: the picture fills the screen; everything else floats over it.
            int ah = H - t - b;
            place(mLayout, l, t, aw, ah);
            int tw = Math.min(aw, dp(360));
            place(toolbar, l + aw - tw, t, tw, tb);
            show(toolbar, true);
            if (settingsOpen) {
                int pw = Math.min(aw * 55 / 100, dp(460));
                place(panel, l + aw - pw, t, pw, ah);
                show(panel, true);
                show(kbd, false);
                show(pad, false);
            } else {
                show(panel, false);
                // at most 40% of the screen height, and 70% of its width
                int kh = Math.round(ah * 0.40f);
                int kw = Math.min(Math.round(kh * 13f / 5f), aw * 7 / 10);
                kh = Math.round(kw * 5 / 13f);
                place(kbd, l + (aw - kw) / 2, H - b - kh, kw, kh);
                kbd.setOverlay(true);
                show(kbd, kv);
                int ph = Math.min(ah / 2, Math.round(aw * 0.3f));
                place(pad, l, H - b - ph, aw, ph);
                show(pad, pv);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (root != null) {
            root.post(this::layoutAll);
        }
    }

    void openSettings() {
        settingsOpen = true;
        panel.refresh();
        layoutAll();
    }

    void closeSettings() {
        settingsOpen = false;
        layoutAll();
    }

    void setKeyboardVisible(boolean on) {
        settings.setBool("ui_kbd", on);
        applyUiSettings();
        layoutAll();
    }

    /* Showing the pad makes sure a joystick port uses it. */
    void setPadVisible(boolean on) {
        settings.setBool("ui_pad", on);
        if (on && !"kjoy0".equals(settings.get("joy_right", "mjoy0"))
                && !"kjoy0".equals(settings.get("joy_left", "none"))) {
            apply("joy_right", "kjoy0");
            Toast.makeText(this, "Right joystick set to the on-screen pad", Toast.LENGTH_SHORT).show();
        }
        applyUiSettings();
        layoutAll();
    }

    void setUi(String key, boolean on) {
        settings.setBool(key, on);
        applyUiSettings();
    }

    @Override
    public void onBackPressed() {
        if (settingsOpen) {
            closeSettings();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastBack < 2000) {
            super.onBackPressed();
        } else {
            lastBack = now;
            Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show();
        }
    }

    /* ---------------------------------------------------------------- */
    /* Typing with the phone's own keyboard */

    void showTypeDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText et = new EditText(this);
        et.setHint("Text to type into the CoCo");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setMinLines(3);
        et.setGravity(Gravity.TOP | Gravity.START);
        box.addView(et);
        final CheckBox upper = new CheckBox(this);
        upper.setText("Convert to UPPERCASE (BASIC needs it)");
        upper.setChecked(true);
        box.addView(upper);
        final CheckBox enter = new CheckBox(this);
        enter.setText("Press ENTER at the end");
        enter.setChecked(true);
        box.addView(enter);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Type text")
                .setView(box)
                .setPositiveButton("Type", (dlg, which) -> {
                    String text = et.getText().toString();
                    if (upper.isChecked()) {
                        text = text.toUpperCase(java.util.Locale.US);
                    }
                    if (enter.isChecked() && !text.endsWith("\n")) {
                        text += "\n";
                    }
                    if (!text.isEmpty()) {
                        command(OP_TYPE, 0, 0, text);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    /* ---------------------------------------------------------------- */
    /* Files */

    void pickFile(int requestCode) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, requestCode);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try {
            if (req >= REQ_DISK && req < REQ_DISK + 4) {
                int drive = req - REQ_DISK;
                String path = copyToHome(data.getData(), "disks");
                if (path != null) {
                    settings.set("disk" + drive, path);
                    command(OP_DISK_INSERT, drive, 0, path);
                }
            } else if (req == REQ_TAPE) {
                String path = copyToHome(data.getData(), "tapes");
                if (path != null) {
                    settings.set("tape", path);
                    command(OP_TAPE_INSERT, 0, 0, path);
                }
            } else if (req == REQ_LOAD || req == REQ_RUN) {
                String path = copyToHome(data.getData(), "opened");
                if (path != null) {
                    command(req == REQ_RUN ? OP_RUN : OP_LOAD, 0, 0, path);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "can't use " + data.getData(), e);
            Toast.makeText(this, "Couldn't read that file", Toast.LENGTH_LONG).show();
        }
        if (settingsOpen && panel != null) {
            panel.refresh();
        }
    }

    void ejectDisk(int drive) {
        settings.remove("disk" + drive);
        command(OP_DISK_EJECT, drive, 0, null);
        if (panel != null) {
            panel.refresh();
        }
    }

    void ejectTape() {
        settings.remove("tape");
        command(OP_TAPE_EJECT, 0, 0, null);
        if (panel != null) {
            panel.refresh();
        }
    }

    String tapeName() {
        return settings.get("tape", "");
    }

    /* Copy a picked file (a content:// URI has no path native code can open)
     * into <app folder>/<subdir>/ and return the new path. */
    private String copyToHome(Uri uri, String subdir) throws Exception {
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }
        String name = null;
        try (Cursor c = getContentResolver().query(uri,
                new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                name = c.getString(0);
            }
        }
        if (name == null || name.isEmpty()) {
            name = uri.getLastPathSegment();
        }
        if (name == null || name.isEmpty()) {
            name = "file.bin";
        }
        name = name.replaceAll("[/\\\\:]", "_");
        File dir = new File(homeDir(), subdir);
        dir.mkdirs();
        File out = new File(dir, name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
        return out.getAbsolutePath();
    }
}
