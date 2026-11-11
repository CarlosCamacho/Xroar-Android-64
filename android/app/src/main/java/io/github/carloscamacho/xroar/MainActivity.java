package io.github.carloscamacho.xroar;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
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
import android.provider.DocumentsContract;
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
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.libsdl.app.SDLActivity;

/* XRoar on SDL2, with an Android front end.
 *
 * libmain.so holds XRoar plus the glue in android/glue/.  This activity wraps
 * SDL's own view (the emulator picture) with a toolbar, the CoCo keyboard, an
 * on-screen joystick and a settings panel, and talks to the emulator through
 * nativeCommand(), whose OP_* constants must match android_glue.h.
 *
 * Layout: in portrait the picture sits at the top (4:3, full width) with the
 * keyboard, pad, settings or Paks below it.  In landscape the picture is on
 * the left at full height and a column on the right holds the toolbar and a
 * Keyboard / Joystick tab (or the settings or Paks, which replace the tab).
 * The "virtual" joystick instead hovers over the picture, and full-screen mode
 * hides everything but the picture and that joystick.
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
    static final int OP_SURFACE = 15;

    // File picker request codes
    static final int REQ_DISK = 100;   // + drive 0-3
    static final int REQ_TAPE = 110;
    static final int REQ_LOAD = 111;
    static final int REQ_RUN = 112;
    static final int REQ_PAKADD = 113;
    static final int REQ_TYPEFILE = 114;
    static final int REQ_EMES_IMAGE = 115;
    static final int REQ_ROM = 120;    // + RomManager.Slot ordinal

    static native void nativeCommand(int op, int a, int b, String s);
    static native String nativePollMessage();

    private Settings settings;
    private FrameLayout root;
    private LinearLayout toolbar;
    private ToolButton keysButton;
    private ToolButton padButton;
    private ToolButton typeButton;
    private ToolButton resetButton;
    private ToolButton paksButton;
    private ToolButton menuButton;
    private ImageView exitFullscreen;
    private CocoKeyboardView kbd;
    private PadView pad;
    private SettingsPanel panel;
    private PaksPanel paksPanel;
    private ToolButton tabKeys;
    private ToolButton tabJoy;
    private final PakLibrary[] pakLibraryRef = new PakLibrary[1];
    private RomManager roms;
    private String currentPak;
    private EditText typeBox;          // the Type… dialog's text box, while it is open
    private Runnable typeChanged;

    private boolean settingsOpen;
    private boolean paksOpen;
    private boolean fullscreen;
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
        Theme.setDark(Theme.resolveDark(settings.get("ui_theme", "system"), this));
        pakLibraryRef[0] = new PakLibrary(homeDir());
        pakLibraryRef[0].dir().mkdirs();
        roms = new RomManager(homeDir());
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
        currentPak = null;
        apply(key, value);
        sendSet("cart", settings.get("cart", "auto"));
    }

    void applyJoystick(String key, String value) {
        apply(key, value);
        if ("kjoy0".equals(value) && !settings.getBool("ui_pad", false)) {
            setPadVisible(true);
        }
    }

    /* ---------------------------------------------------------------- */
    /* UI */

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private ToolButton iconButton(int icon, String label, View.OnClickListener l) {
        ToolButton b = new ToolButton(this, icon, label, Theme.ICON_DP);
        b.setOnClickListener(l);
        return b;
    }

    private void buildUi() {
        ViewGroup parent = (ViewGroup) mLayout.getParent();
        if (parent != null) {
            parent.removeView(mLayout);
        }
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.STAGE);
        root.addView(mLayout, new FrameLayout.LayoutParams(-1, -1));

        pad = new PadView(this);
        pad.setListener((which, down) -> command(OP_JOY, which, down ? 1 : 0, null));
        root.addView(pad, new FrameLayout.LayoutParams(0, 0));

        kbd = new CocoKeyboardView(this);
        kbd.setListener((code, down) -> command(OP_KEY, code, down ? 1 : 0, null));
        root.addView(kbd, new FrameLayout.LayoutParams(0, 0));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(Theme.SURFACE);
        keysButton = iconButton(R.drawable.ic_keyboard, "Keys", v -> setKeyboardVisible(!settings.getBool("ui_kbd", true)));
        padButton = iconButton(R.drawable.ic_gamepad, "Pad", v -> setPadVisible(!settings.getBool("ui_pad", false)));
        typeButton = iconButton(R.drawable.ic_text, "Type", v -> showTypeDialog());
        resetButton = iconButton(R.drawable.ic_reset, "Reset", v -> showResetMenu());
        paksButton = iconButton(R.drawable.ic_cartridge, "Paks", v -> openPaks());
        // The hamburger has no label: it is the one icon everybody knows.
        menuButton = iconButton(R.drawable.ic_menu, "Menu", v -> showMenu());
        menuButton.setLabelShown(false);
        for (ToolButton b : new ToolButton[] { keysButton, padButton, typeButton, resetButton, paksButton }) {
            toolbar.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        toolbar.addView(menuButton, new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(toolbar, new FrameLayout.LayoutParams(0, 0));

        panel = new SettingsPanel(this, 0);
        panel.setVisibility(View.GONE);
        root.addView(panel, new FrameLayout.LayoutParams(0, 0));

        paksPanel = new PaksPanel(this, pakLibraryRef[0]);
        paksPanel.setVisibility(View.GONE);
        root.addView(paksPanel, new FrameLayout.LayoutParams(0, 0));

        // Landscape: these two pick what the right-hand column shows.
        tabKeys = iconButton(R.drawable.ic_keyboard, "Keyboard", v -> setLandscapeTab("keys"));
        tabJoy = iconButton(R.drawable.ic_gamepad, "Joystick", v -> setLandscapeTab("joy"));
        root.addView(tabKeys, new FrameLayout.LayoutParams(0, 0));
        root.addView(tabJoy, new FrameLayout.LayoutParams(0, 0));

        // Full screen: the one control left, a translucent button in the corner.
        exitFullscreen = new ImageView(this);
        exitFullscreen.setImageDrawable(Theme.icon(this, R.drawable.ic_fullscreen_exit, Theme.TEXT_STRONG, Theme.ICON_DP));
        exitFullscreen.setScaleType(ImageView.ScaleType.CENTER);
        exitFullscreen.setBackground(Theme.box(this, Theme.SCRIM, 0, 0));
        exitFullscreen.setContentDescription("Exit full screen");
        exitFullscreen.setOnClickListener(v -> setFullscreen(false));
        exitFullscreen.setVisibility(View.GONE);
        root.addView(exitFullscreen, new FrameLayout.LayoutParams(0, 0));

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
        mLayout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l > 0 && b - t > 0) {
                command(OP_SURFACE, r - l, b - t, null);
            }
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
        kbd.setStyle(settings.get("ui_kbd_style", "white"));
        // In full screen only the see-through joystick makes sense, whichever one is chosen.
        pad.setType(fullscreen ? "virtual" : settings.get("ui_joy_type", "std"));
        pad.setSide(settings.get("ui_joy_side", "center"));
        pad.setVirtualSide(settings.get("ui_vpad_side", "left"));
        pad.setVirtualMotion(settings.get("ui_vpad_mode", "center"));
        pad.setOpacity(settings.getInt("ui_vpad_alpha", 45));
        pad.setHaptic(haptic);
        boolean joyTab = "joy".equals(settings.get("ui_tab", "keys"));
        tabKeys.setActive(!joyTab);
        tabJoy.setActive(joyTab);
        boolean labels = settings.getBool("ui_labels", true);
        for (ToolButton b : new ToolButton[] { keysButton, padButton, typeButton, resetButton, paksButton }) {
            b.setLabelShown(labels);
        }
        keysButton.setActive(settings.getBool("ui_kbd", true));
        padButton.setActive(settings.getBool("ui_pad", false));
        typeButton.setActive(false);
        resetButton.setActive(false);
        paksButton.setActive(paksOpen);
        menuButton.setActive(settingsOpen);
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
        boolean labels = settings.getBool("ui_labels", true);
        int tb = dp(labels ? 56 : 48);
        boolean kv = settings.getBool("ui_kbd", true);
        boolean pv = settings.getBool("ui_pad", false);
        boolean sheet = settingsOpen || paksOpen;
        boolean virtual = "virtual".equals(settings.get("ui_joy_type", "std"));
        float kbdAspect = CocoKeyboardView.COLS / 5f;

        if (fullscreen) {
            // The picture, 4:3, as large as fits and centred; the see-through
            // joystick over the whole screen, so its corners sit on the black bars.
            int ah = H - t - b;
            int pw = aw, ph = aw * 3 / 4;
            if (ph > ah) {
                ph = ah;
                pw = ah * 4 / 3;
            }
            place(mLayout, l + (aw - pw) / 2, t + (ah - ph) / 2, pw, ph);
            for (View v : new View[] { toolbar, kbd, panel, paksPanel, tabKeys, tabJoy }) {
                show(v, false);
            }
            place(pad, l, t, aw, ah);
            show(pad, pv);
            int eb = dp(44);
            place(exitFullscreen, W - r - eb - dp(8), t + dp(8), eb, eb);
            show(exitFullscreen, true);
            return;
        }
        show(exitFullscreen, false);

        if (H >= W) {
            // Portrait: toolbar, picture (4:3), then keyboard / pad / settings / Paks.
            int sdlH = aw * 3 / 4;
            int y = t + tb + sdlH;
            int avail = Math.max(0, H - b - y);
            place(toolbar, l, t, aw, tb);
            place(mLayout, l, t + tb, aw, sdlH);
            kbd.setOverlay(false);
            show(toolbar, true);
            show(tabKeys, false);
            show(tabJoy, false);
            keysButton.setVisibility(View.VISIBLE);
            padButton.setVisibility(View.VISIBLE);
            show(panel, settingsOpen);
            show(paksPanel, paksOpen);
            if (settingsOpen) {
                place(panel, l, y, aw, avail);
            } else if (paksOpen) {
                place(paksPanel, l, y, aw, avail);
            }
            if (sheet) {
                show(kbd, false);
                show(pad, false);
            } else {
                int kh = kv ? Math.min(Math.round(aw / kbdAspect * 1.1f), avail) : 0;
                place(kbd, l, y, aw, Math.max(kh, 1));
                show(kbd, kv && kh > 0);
                if (virtual) {
                    // hovers over the picture, so it takes no room of its own
                    place(pad, l, t + tb, aw, sdlH);
                    show(pad, pv);
                } else {
                    // the rest of the black area: the joystick fills 80% of it
                    int ph = pv ? Math.max(0, avail - kh) : 0;
                    place(pad, l, y + kh, aw, Math.max(ph, 1));
                    show(pad, pv && ph > 0);
                }
            }
        } else {
            // Landscape: the picture (4:3) on the left at full height; on the
            // right the toolbar and a Keyboard / Joystick tab.
            int ah = H - t - b;
            int picW = Math.min(ah * 4 / 3, Math.max(aw - dp(300), aw / 2));
            int picH = picW * 3 / 4 > ah ? ah : picW * 3 / 4;
            int rw = aw - picW;
            int rx = l + picW;
            place(mLayout, l, t, picW, picH);
            place(toolbar, rx, t, rw, tb);
            show(toolbar, true);
            keysButton.setVisibility(View.GONE);
            // the see-through joystick has no tab, so the toolbar's Pad button turns it on and off
            padButton.setVisibility(virtual ? View.VISIBLE : View.GONE);
            kbd.setOverlay(false);
            int top = t + tb;
            int avail = Math.max(0, H - b - top);
            show(panel, settingsOpen);
            show(paksPanel, paksOpen);
            if (settingsOpen) {
                place(panel, rx, top, rw, avail);
            } else if (paksOpen) {
                place(paksPanel, rx, top, rw, avail);
            }
            if (sheet) {
                show(tabKeys, false);
                show(tabJoy, false);
                show(kbd, false);
                show(pad, false);
            } else if (virtual) {
                show(tabKeys, false);
                show(tabJoy, false);
                int kh = Math.min(avail, Math.round(rw / kbdAspect));
                place(kbd, rx, top, rw, Math.max(kh, 1));
                show(kbd, kh > 0);
                place(pad, l, t, picW, picH);
                show(pad, pv);
            } else {
                boolean joyTab = "joy".equals(settings.get("ui_tab", "keys"));
                int th = dp(labels ? 56 : 48);
                place(tabKeys, rx, top, rw / 2, th);
                place(tabJoy, rx + rw / 2, top, rw - rw / 2, th);
                show(tabKeys, true);
                show(tabJoy, true);
                int body = Math.max(0, avail - th);
                int kh = Math.min(body, Math.round(rw / kbdAspect));
                place(kbd, rx, top + th, rw, Math.max(kh, 1));
                show(kbd, !joyTab && kh > 0);
                place(pad, rx, top + th, rw, Math.max(body, 1));
                show(pad, joyTab);
            }
        }
    }

    void setLandscapeTab(String tab) {
        settings.set("ui_tab", tab);
        if ("joy".equals(tab) && !"kjoy0".equals(settings.get("joy_right", "mjoy0"))
                && !"kjoy0".equals(settings.get("joy_left", "none"))) {
            apply("joy_right", "kjoy0");
            Toast.makeText(this, "Right joystick set to the on-screen pad", Toast.LENGTH_SHORT).show();
        }
        applyUiSettings();
        layoutAll();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (root != null) {
            if ("system".equals(settings.get("ui_theme", "system"))
                    && Theme.resolveDark("system", this) != Theme.dark) {
                applyTheme();
            }
            root.post(this::layoutAll);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && root != null) {
            applySystemBars();
        }
    }

    void openSettings() {
        settingsOpen = true;
        paksOpen = false;
        panel.refresh();
        applyUiSettings();
        layoutAll();
    }

    void closeSettings() {
        settingsOpen = false;
        applyUiSettings();
        layoutAll();
    }

    void openPaks() {
        paksOpen = true;
        settingsOpen = false;
        paksPanel.refresh();
        applyUiSettings();
        layoutAll();
    }

    void closePaks() {
        paksOpen = false;
        applyUiSettings();
        layoutAll();
    }

    /* ---------------------------------------------------------------- */
    /* Menus, full screen, theme, the app folder */

    private void showResetMenu() {
        List<PopupList.Item> items = new ArrayList<>();
        items.add(new PopupList.Item(R.drawable.ic_reset, "Soft reset (like the RESET button)",
                () -> command(OP_SOFT_RESET, 0, 0, null)));
        items.add(new PopupList.Item(R.drawable.ic_power, "Hard reset (power off and on)",
                () -> command(OP_HARD_RESET, 0, 0, null)));
        PopupList.show(this, resetButton, items);
    }

    /* The hamburger menu. */
    private void showMenu() {
        List<PopupList.Item> items = new ArrayList<>();
        items.add(new PopupList.Item(R.drawable.ic_settings, "Settings", this::openSettings));
        items.add(new PopupList.Item(R.drawable.ic_fullscreen, "Full screen", () -> setFullscreen(true)));
        items.add(new PopupList.Item(R.drawable.ic_folder, "Open the app folder", this::openHomeFolder));
        items.add(new PopupList.Item(R.drawable.ic_contrast, Theme.dark ? "Switch to light theme" : "Switch to dark theme",
                () -> setThemePref(Theme.dark ? "light" : "dark")));
        items.add(new PopupList.Item(R.drawable.ic_info, "About", this::showAbout));
        PopupList.show(this, menuButton, items);
    }

    void setFullscreen(boolean on) {
        fullscreen = on;
        if (on) {
            settingsOpen = false;
            paksOpen = false;
            Toast.makeText(this, settings.getBool("ui_pad", false)
                    ? "Full screen. Back or the corner button exits."
                    : "Full screen. Turn on Pad first to get a joystick. Back exits.", Toast.LENGTH_SHORT).show();
        }
        applySystemBars();
        applyUiSettings();
        layoutAll();
    }

    /* "dark", "light" or "system". */
    void setThemePref(String pref) {
        settings.set("ui_theme", pref);
        // not inside the drop-down's own callback: that rebuilds the panel it came from
        handler.post(this::applyTheme);
    }

    private void applyTheme() {
        Theme.setDark(Theme.resolveDark(settings.get("ui_theme", "system"), this));
        root.setBackgroundColor(Theme.STAGE);
        toolbar.setBackgroundColor(Theme.SURFACE);
        exitFullscreen.setImageDrawable(Theme.icon(this, R.drawable.ic_fullscreen_exit, Theme.TEXT_STRONG, Theme.ICON_DP));
        exitFullscreen.setBackground(Theme.box(this, Theme.SCRIM, 0, 0));
        for (ToolButton b : new ToolButton[] { keysButton, padButton, typeButton, resetButton, paksButton, menuButton,
                tabKeys, tabJoy }) {
            b.refreshTheme();
        }
        // the two sheets build their widgets from the palette, so make new ones
        int tab = panel.currentTab();
        root.removeView(panel);
        panel = new SettingsPanel(this, tab);
        panel.setVisibility(View.GONE);
        root.addView(panel, new FrameLayout.LayoutParams(0, 0));
        root.removeView(paksPanel);
        paksPanel = new PaksPanel(this, pakLibraryRef[0]);
        paksPanel.setVisibility(View.GONE);
        root.addView(paksPanel, new FrameLayout.LayoutParams(0, 0));
        exitFullscreen.bringToFront();
        if (settingsOpen) {
            panel.refresh();
        } else if (paksOpen) {
            paksPanel.refresh();
        }
        applySystemBars();
        applyUiSettings();
        layoutAll();
    }

    /* Status and navigation bars: the stage's color, dark icons on a light stage,
     * and hidden (swipe to peek) in full screen. */
    @SuppressWarnings("deprecation")
    private void applySystemBars() {
        getWindow().setStatusBarColor(Theme.STAGE);
        getWindow().setNavigationBarColor(Theme.STAGE);
        View decor = getWindow().getDecorView();
        int f = decor.getSystemUiVisibility();
        int hide = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        f = fullscreen ? (f | hide) : (f & ~hide);
        f = Theme.dark ? (f & ~light) : (f | light);
        decor.setSystemUiVisibility(f);
    }

    /* Show the app's folder (ROMs, disks, settings) in the phone's file manager.
     * It lives in Android/media/<package>/, which the system file manager can open;
     * if nothing will open that folder directly, the folder picker starts there. */
    void openHomeFolder() {
        File dir = homeDir();
        String path = dir.getAbsolutePath();
        String docId = null;
        String[] prefixes = { "/storage/emulated/0/", "/sdcard/" };
        for (String pre : prefixes) {
            if (path.startsWith(pre)) {
                docId = "primary:" + path.substring(pre.length());
            }
        }
        if (docId == null && path.startsWith("/storage/")) {
            int slash = path.indexOf('/', "/storage/".length());
            if (slash > 0) {
                docId = path.substring("/storage/".length(), slash) + ":" + path.substring(slash + 1);
            }
        }
        if (docId != null) {
            Uri uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId);
            try {
                Intent view = new Intent(Intent.ACTION_VIEW);
                view.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
                view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(view);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.i(TAG, "no file manager takes the folder directly: " + e);
            }
            try {
                Intent tree = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                tree.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
                startActivity(tree);
                Toast.makeText(this, "Your files are in " + path, Toast.LENGTH_LONG).show();
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.i(TAG, "no folder picker: " + e);
            }
        }
        Toast.makeText(this, "No file manager found. The folder is " + path, Toast.LENGTH_LONG).show();
    }

    private void showAbout() {
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        Theme.dialog(this)
                .setTitle("XRoar for Android")
                .setMessage("Version " + version
                        + "\n\nXRoar, the Dragon and Tandy Color Computer emulator, by Ciaran Anscomb (GPL)."
                        + "\nSDL2 (zlib)."
                        + "\nFonts: Archivo and Archivo Black (SIL Open Font License)."
                        + "\nInterface after the BUIK Basic UI Kit."
                        + "\n\nYour files: " + homeDir().getPath())
                .setPositiveButton("OK", null)
                .show();
    }

    /* ---------------------------------------------------------------- */
    /* Paks */

    String currentPakName() {
        return currentPak;
    }

    /* Insert a cartridge and start it.  The CoCo is reset first: Disk BASIC
     * keeps hooks that point into the disk ROM, so swapping the cartridge under
     * it would crash the machine. */
    void runPak(String path, String title) {
        currentPak = title;
        command(OP_RUN, 0, 0, path);
        closePaks();
    }

    void ejectPak() {
        currentPak = null;
        sendSet("cart", settings.get("cart", "auto"));
    }

    void pickPaks() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, REQ_PAKADD);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_LONG).show();
        }
    }

    private void addPaks(Intent data) {
        java.util.List<Uri> uris = new java.util.ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        int added = 0;
        for (Uri u : uris) {
            String name = displayName(u);
            if (!PakLibrary.isPakName(name)) {
                Toast.makeText(this, name + " isn't a .rom or .ccc cartridge image", Toast.LENGTH_LONG).show();
                continue;
            }
            try (InputStream in = getContentResolver().openInputStream(u)) {
                pakLibraryRef[0].add(in, name);
                added++;
            } catch (Exception e) {
                Log.e(TAG, "can't add " + u, e);
            }
        }
        if (added > 0) {
            Toast.makeText(this, added + (added == 1 ? " Pak" : " Paks") + " added", Toast.LENGTH_SHORT).show();
        }
        if (paksPanel != null) {
            paksPanel.refresh();
        }
    }

    /* ---------------------------------------------------------------- */
    /* ROM files */

    String romLabel(RomManager.Slot slot) {
        return roms.label(slot);
    }

    void pickRom(RomManager.Slot slot) {
        pickFile(REQ_ROM + slot.ordinal());
    }

    private void handleRomPick(RomManager.Slot slot, Uri uri) throws Exception {
        final byte[] data;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 2 * 1024 * 1024) {
                bo.write(buf, 0, n);
            }
            data = bo.toByteArray();
        }
        final String name = displayName(uri);
        final RomManager.Identity id = RomManager.identify(data);
        final RomCatalog.Entry e = id.entry;
        if (e != null && (e.kind.equals("PAK") || e.kind.equals("GMC"))) {
            Theme.dialog(this)
                    .setTitle("That's a cartridge")
                    .setMessage(name + " is " + e.desc + ", a Program Pak, not a system ROM.\n\nAdd it to your Paks instead?")
                    .setPositiveButton("Add to Paks", (d, w) -> {
                        try {
                            pakLibraryRef[0].add(new ByteArrayInputStream(data), name);
                            Toast.makeText(this, "Added to Paks", Toast.LENGTH_SHORT).show();
                        } catch (Exception ex) {
                            Toast.makeText(this, "Couldn't add it", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else if (e != null && !e.kind.equals(slot.kind)) {
            final RomManager.Slot right = slotForKind(e.kind);
            if (right == null) {
                Toast.makeText(this, name + " is " + e.desc + " and can't be used here", Toast.LENGTH_LONG).show();
                return;
            }
            Theme.dialog(this)
                    .setTitle("Wrong ROM?")
                    .setMessage(name + " is " + e.desc + ".\n\nThat's the " + right.title
                            + " ROM, not " + slot.title + ". Install it as " + right.title + " instead?")
                    .setPositiveButton("Install as " + right.title, (d, w) -> installRom(right, data, id))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else if (e == null && slot == RomManager.Slot.EMES && data.length != 16384) {
            Toast.makeText(this, name + " is " + data.length + " bytes; an emesDOS09 ROM is 16384 (two 8K banks)", Toast.LENGTH_LONG).show();
        } else if (e == null && slot != RomManager.Slot.SDC && slot != RomManager.Slot.EMES) {
            String size = data.length == 8192 || data.length == 16384 ? "" :
                    "\n\nIts size (" + data.length + " bytes) isn't what a CoCo ROM is (8192 or 16384), so it may be damaged.";
            Theme.dialog(this)
                    .setTitle("Unrecognized ROM")
                    .setMessage(name + " isn't one of the ROMs XRoar knows (CRC " + String.format("%08x", id.crc)
                            + ")." + size + "\n\nInstall it as " + slot.title + " anyway?")
                    .setPositiveButton("Install anyway", (d, w) -> installRom(slot, data, id))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            installRom(slot, data, id);
        }
    }

    private static RomManager.Slot slotForKind(String kind) {
        for (RomManager.Slot s : RomManager.Slot.values()) {
            if (s.kind.equals(kind)) {
                return s;
            }
        }
        return null;
    }

    private void installRom(RomManager.Slot slot, byte[] data, RomManager.Identity id) {
        try {
            String file = roms.install(slot, data, id);
            Toast.makeText(this, slot.title + " replaced (" + file + ")", Toast.LENGTH_SHORT).show();
            rebuildMachine();
        } catch (Exception ex) {
            Toast.makeText(this, "Couldn't install the ROM: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (panel != null) {
            panel.refresh();
        }
    }

    void removeRom(final RomManager.Slot slot) {
        String what = roms.label(slot);
        if (what == null) {
            return;
        }
        Theme.dialog(this)
                .setTitle("Remove " + slot.title + "?")
                .setMessage(what + "\n\nThe file is deleted from the roms folder."
                        + (slot.required ? "\nThe CoCo can't start without it until you add another." : ""))
                .setPositiveButton("Remove", (d, w) -> {
                    roms.remove(slot);
                    rebuildMachine();
                    if (panel != null) {
                        panel.refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* Rebuild the machine so it reads the ROM files again. */
    private void rebuildMachine() {
        applyMachineSetting("machine", settings.get("machine", "cocous"));
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

    void setUiString(String key, String value) {
        settings.set(key, value);
        applyUiSettings();
        layoutAll();
    }

    void setUi(String key, boolean on) {
        settings.setBool(key, on);
        applyUiSettings();
        layoutAll();
    }

    void setUiInt(String key, int value) {
        settings.setInt(key, value);
        applyUiSettings();
    }

    /* While the opacity slider is dragged: show it, save it when it is let go. */
    void previewVpadAlpha(int percent) {
        pad.setOpacity(percent);
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            setFullscreen(false);
            return;
        }
        if (settingsOpen) {
            closeSettings();
            return;
        }
        if (paksOpen) {
            closePaks();
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
        et.setHint("Text to type into the CoCo, or open a text file");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setMinLines(4);
        et.setMaxLines(8);
        et.setGravity(Gravity.TOP | Gravity.START);
        box.addView(et);
        Button open = new Button(this);
        open.setText("Open text file…");
        open.setAllCaps(false);
        open.setOnClickListener(v -> pickFile(REQ_TYPEFILE));
        box.addView(open);
        final CheckBox program = new CheckBox(this);
        program.setText("A BASIC program: every line starts with a line number");
        program.setChecked(true);
        box.addView(program);
        final CheckBox skip = new CheckBox(this);
        skip.setText("Leave out lines with problems");
        box.addView(skip);
        final CheckBox upper = new CheckBox(this);
        upper.setText("Convert to UPPERCASE (BASIC needs it)");
        upper.setChecked(true);
        box.addView(upper);
        final CheckBox enter = new CheckBox(this);
        enter.setText("Press ENTER at the end");
        enter.setChecked(true);
        box.addView(enter);
        final TextView info = new TextView(this);
        info.setTextSize(12);
        info.setPadding(0, dp(6), 0, dp(6));
        box.addView(info);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);

        final AlertDialog d = Theme.dialog(this)
                .setTitle("Type text")
                .setView(sv)
                .setPositiveButton("Type", (dlg, which) -> {
                    String text = et.getText().toString();
                    if (program.isChecked()) {
                        text = BasicText.forTyping(text, true, skip.isChecked());
                    }
                    if (upper.isChecked()) {
                        text = text.toUpperCase(java.util.Locale.US);
                    }
                    if (enter.isChecked() && !text.isEmpty() && !text.endsWith("\n")) {
                        text += "\n";
                    }
                    if (!text.isEmpty()) {
                        command(OP_TYPE, 0, 0, text);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        final Runnable recheck = () -> {
            String text = et.getText().toString();
            java.util.List<BasicText.Problem> probs = BasicText.check(text, program.isChecked());
            boolean errors = BasicText.hasErrors(probs);
            int n = BasicText.lines(text).size();
            String msg;
            if (text.trim().isEmpty()) {
                msg = "A CoCo line can hold " + BasicText.MAX_LINE_LENGTH + " characters (a longer one is silently cut off).";
            } else if (probs.isEmpty()) {
                msg = n + (n == 1 ? " line" : " lines") + ", all fine.";
            } else {
                msg = BasicText.summary(probs, 4);
            }
            info.setText(msg);
            info.setTextColor(errors ? Theme.ERROR : (probs.isEmpty() ? Theme.GOOD : Theme.SECONDARY));
            android.widget.Button ok = d.getButton(AlertDialog.BUTTON_POSITIVE);
            if (ok != null) {
                ok.setEnabled(!text.isEmpty() && (!errors || skip.isChecked()));
            }
        };
        et.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) { recheck.run(); }
        });
        program.setOnCheckedChangeListener((v, on) -> recheck.run());
        skip.setOnCheckedChangeListener((v, on) -> recheck.run());
        d.setOnDismissListener(x -> {
            typeBox = null;
            typeChanged = null;
        });
        typeBox = et;
        typeChanged = recheck;
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        recheck.run();
    }

    private void loadTypeFile(Uri uri) {
        if (typeBox == null) {
            return;
        }
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 1024 * 1024) {
                bo.write(buf, 0, n);
            }
            typeBox.setText(new String(bo.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't read that file", Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri,
                new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                name = c.getString(0);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.isEmpty()) {
            name = uri.getLastPathSegment();
        }
        return name == null || name.isEmpty() ? "file.bin" : name;
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
        if (result != RESULT_OK || data == null || (data.getData() == null && data.getClipData() == null)) {
            return;
        }
        try {
            if (req == REQ_PAKADD) {
                addPaks(data);
                return;
            }
            if (req == REQ_EMES_IMAGE) {
                String path = copyToHome(data.getData(), "emesdos");
                if (path != null) {
                    apply("emes_image", path);
                }
                if (settingsOpen && panel != null) {
                    panel.refresh();
                }
                return;
            }
            if (req == REQ_TYPEFILE) {
                loadTypeFile(data.getData());
                return;
            }
            if (req >= REQ_ROM && req < REQ_ROM + RomManager.Slot.values().length) {
                handleRomPick(RomManager.Slot.values()[req - REQ_ROM], data.getData());
                return;
            }
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

    void ejectEmesImage() {
        apply("emes_image", "");
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
