package io.github.carloscamacho.xroar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/* The settings screen, modelled on XRoar Online's tabs: Machine, Video, Audio,
 * Input and Files.  Everything applies at once (the emulator keeps running
 * behind it) and is saved in settings.conf. */
final class SettingsPanel extends LinearLayout {
    private static final int BG = Theme.SURFACE;
    private static final int CARD = Theme.SURFACE_RAISED;
    private static final int TEXT = Theme.TEXT_STRONG;
    private static final int DIM = Theme.TEXT_MUTED;
    private static final int OUTLINE = Theme.BORDER_STRONG;
    private static final int ACCENT = Theme.PRIMARY;
    private static final int TONAL = Theme.PRIMARY_CONTAINER;
    private static final int ON_TONAL = Theme.ON_PRIMARY_CONTAINER;

    private static final String[] TAB_NAMES = { "Machine", "Video", "Audio", "Input", "Files" };

    private interface OnPick { void pick(String value); }
    private interface OnInt { void value(int v); }
    private interface OnBool { void value(boolean b); }

    private final MainActivity host;
    private final LinearLayout tabRow;
    private final ScrollView scroll;
    private final Button[] tabButtons = new Button[TAB_NAMES.length];
    private final View[] tabIndicators = new View[TAB_NAMES.length];
    private LinearLayout body;                               // the open accordion section's body
    private final Map<String, Boolean> openState = new HashMap<>();
    private int sectionCount;
    private LinearLayout content;
    private int tab = 0;

    SettingsPanel(MainActivity host) {
        super(host);
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        setClickable(true);   // swallow touches so they don't reach the picture

        LinearLayout header = new LinearLayout(host);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(host);
        title.setText("Settings");
        title.setTextColor(TEXT);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT);
        title.setPadding(dp(14), dp(6), dp(8), dp(6));
        header.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = flatButton("Close");
        close.setOnClickListener(v -> host.closeSettings());
        header.addView(close);
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tabRow = new LinearLayout(host);
        tabRow.setOrientation(HORIZONTAL);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int n = i;
            Button b = flatButton(TAB_NAMES[i]);
            b.setOnClickListener(v -> showTab(n));
            tabButtons[i] = b;
            View ind = new View(host);
            tabIndicators[i] = ind;
            LinearLayout cell = new LinearLayout(host);
            cell.setOrientation(VERTICAL);
            cell.addView(b, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.addView(ind, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
            tabRow.addView(cell, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        addView(tabRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(host);
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        showTab(0);
    }

    /* Rebuild the current tab from the saved settings (after a file pick, say). */
    void refresh() {
        showTab(tab);
    }

    /* ---------------------------------------------------------------- */
    /* Widgets */

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Button flatButton(String label) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(TEXT);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setBackgroundColor(0x00000000);
        return b;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            g.setStroke(dp(1), strokeColor);
        }
        return g;
    }

    /* A Material filled-tonal button. */
    private Button actionButton(String label, Runnable r) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(ON_TONAL);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), round(TONAL, Theme.BUTTON_RADIUS_DP, 0), null));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    /* An accordion section: a titled card that opens and closes.  Everything
     * added with add() until the next section() goes inside it. */
    private void section(String title) {
        final String key = tab + "/" + title;
        Boolean saved = openState.get(key);
        final boolean[] open = { saved != null ? saved : sectionCount == 0 };
        sectionCount++;
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setBackground(round(CARD, Theme.CARD_RADIUS_DP, 0));
        LinearLayout.LayoutParams cp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, dp(6), 0, dp(2));
        content.addView(card, cp);

        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(14), dp(16), dp(14));
        head.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null, round(0xFFFFFFFF, Theme.CARD_RADIUS_DP, 0)));
        TextView t = new TextView(getContext());
        String nice = title.substring(0, 1) + title.substring(1).toLowerCase(java.util.Locale.US);
        t.setText(nice);
        t.setTextColor(TEXT);
        t.setTextSize(16);
        head.addView(t, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView chev = new TextView(getContext());
        chev.setTextColor(DIM);
        chev.setTextSize(18);
        head.addView(chev);
        card.addView(head);

        final LinearLayout inner = new LinearLayout(getContext());
        inner.setOrientation(VERTICAL);
        inner.setPadding(dp(16), 0, dp(16), dp(14));
        card.addView(inner);
        final Runnable apply = () -> {
            inner.setVisibility(open[0] ? View.VISIBLE : View.GONE);
            chev.setText(open[0] ? "▴" : "▾");
        };
        apply.run();
        head.setOnClickListener(v -> {
            open[0] = !open[0];
            openState.put(key, open[0]);
            apply.run();
        });
        body = inner;
    }

    /* Add to the open section (or straight to the page if there is none). */
    private void add(View v) {
        (body != null ? body : content).addView(v);
    }

    private TextView note(String t) {
        TextView v = new TextView(getContext());
        v.setText(t);
        v.setTextColor(DIM);
        v.setTextSize(12);
        v.setPadding(0, dp(2), 0, dp(2));
        return v;
    }

    private TextView line(String t, int color) {
        TextView v = new TextView(getContext());
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(14);
        v.setPadding(0, dp(2), 0, dp(2));
        return v;
    }

    private LinearLayout labeled(String label, View control) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = line(label, TEXT);
        row.addView(l, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));
        row.addView(control, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));
        return row;
    }

    private LinearLayout buttonRow(Button... buttons) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        for (Button b : buttons) {
            LinearLayout.LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), dp(4), dp(3), dp(4));
            row.addView(b, lp);
        }
        return row;
    }

    private View spinner(String[] labels, final String[] values, String current, final OnPick cb) {
        Spinner sp = new Spinner(getContext());
        sp.setBackground(round(0x00000000, Theme.BUTTON_RADIUS_DP, OUTLINE));
        sp.setPadding(dp(8), dp(6), dp(8), dp(6));
        ArrayAdapter<String> ad = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                idx = i;
            }
        }
        sp.setSelection(idx, false);
        final int[] last = { idx };
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos == last[0]) {
                    return;
                }
                last[0] = pos;
                cb.pick(values[pos]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        FrameLayout box = new FrameLayout(getContext());
        box.addView(sp, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView arrow = new TextView(getContext());
        arrow.setText("▾");
        arrow.setTextColor(DIM);
        arrow.setTextSize(16);
        arrow.setPadding(0, 0, dp(12), 0);
        box.addView(arrow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
        arrow.setClickable(false);
        return box;
    }

    private CheckBox check(String label, boolean on, final OnBool cb) {
        CheckBox c = new CheckBox(getContext());
        c.setText(label);
        c.setTextColor(TEXT);
        c.setChecked(on);
        c.setButtonTintList(ColorStateList.valueOf(ACCENT));
        c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                cb.value(checked);
            }
        });
        return c;
    }

    /* A slider that applies while dragging (live) and saves when released. */
    private View slider(final String name, final int min, final int max, int value, final int def,
                        final String unit, final OnInt live, final OnInt commit) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(VERTICAL);
        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        final TextView label = line(name + ": " + value + unit, TEXT);
        top.addView(label, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button reset = flatButton("Reset");
        top.addView(reset);
        box.addView(top);
        final SeekBar sb = new SeekBar(getContext());
        sb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        sb.setThumbTintList(ColorStateList.valueOf(ACCENT));
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                label.setText(name + ": " + (p + min) + unit);
                if (fromUser) {
                    live.value(p + min);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                commit.value(s.getProgress() + min);
            }
        });
        reset.setOnClickListener(v -> {
            sb.setProgress(def - min);
            live.value(def);
            commit.value(def);
        });
        box.addView(sb);
        return box;
    }

    /* ---------------------------------------------------------------- */
    /* Tabs */

    private void showTab(int n) {
        tab = n;
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].setTextColor(i == n ? ACCENT : DIM);
            tabButtons[i].setTypeface(i == n ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabIndicators[i].setBackgroundColor(i == n ? ACCENT : 0x00000000);
        }
        content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        content.setPadding(dp(12), dp(4), dp(12), dp(24));
        body = null;
        sectionCount = 0;
        switch (n) {
        case 0: buildMachine(); break;
        case 1: buildVideo(); break;
        case 2: buildAudio(); break;
        case 3: buildInput(); break;
        default: buildFiles(); break;
        }
        scroll.removeAllViews();
        scroll.addView(content);
        scroll.scrollTo(0, 0);
    }

    private void buildMachine() {
        final Settings s = host.settings();
        section("MACHINE");
        add(labeled("Machine", spinner(
                new String[] { "CoCo (NTSC)", "CoCo (PAL)", "CoCo 2B (NTSC)", "CoCo 2B (PAL)" },
                new String[] { "cocous", "coco", "coco2bus", "coco2b" },
                s.get("machine", "cocous"), v -> host.applyMachineSetting("machine", v))));
        add(labeled("RAM", spinner(
                new String[] { "4 KB", "16 KB", "32 KB", "64 KB" },
                new String[] { "4", "16", "32", "64" },
                s.get("ram", "64"), v -> host.applyMachineSetting("ram", v))));
        add(labeled("Cartridge", spinner(
                new String[] { "Automatic", "Disk controller (RS-DOS)", "CoCoSDC", "emesDOS09 smart cartridge", "None" },
                new String[] { "auto", "rsdos", "cocosdc", "emesdos09", "none" },
                s.get("cart", "auto"), v -> host.apply("cart", v))));
        add(note("Automatic uses the CoCoSDC when roms/sdcdos.rom is present, "
                + "otherwise the disk controller. Changing these restarts the emulated CoCo."));


        section("ROM FILES");
        for (final RomManager.Slot slot : RomManager.Slot.values()) {
            String label = host.romLabel(slot);
            boolean missing = label == null;
            add(line(slot.title, TEXT));
            add(line(missing ? (slot.required ? "MISSING" : "not present (optional)") : label,
                    missing ? (slot.required ? Theme.ERROR : DIM) : Theme.TERTIARY_CONTAINER));
            add(buttonRow(
                    actionButton(missing ? "Add…" : "Replace…", () -> host.pickRom(slot)),
                    actionButton("Remove", () -> host.removeRom(slot))));
        }
        add(note("Pick a ROM file from your phone. The app recognizes it by its checksum, "
                + "names it correctly, and restarts the CoCo with it. Files go in "
                + host.homeDir().getPath() + "/roms/."));
    }

    private void buildVideo() {
        final Settings s = host.settings();
        section("PICTURE");
        add(labeled("TV input", spinner(
                new String[] { "Automatic", "Composite (blue-red)", "Composite (red-blue)", "S-Video", "RGB" },
                new String[] { "auto", "cmp-br", "cmp-rb", "cmp", "rgb" },
                s.get("tv_input", "auto"), v -> host.apply("tv_input", v))));
        add(note("Pick blue-red or red-blue to see the orange/blue artifact colors "
                + "that CoCo games use. If they look swapped, choose the other one."));
        add(labeled("Color renderer", spinner(
                new String[] { "Automatic", "None", "Simple", "5-bit", "Partial NTSC", "Simulated" },
                new String[] { "auto", "none", "simple", "5bit", "partial", "simulated" },
                s.get("ccr", "auto"), v -> host.apply("ccr", v))));
        add(labeled("Picture size", spinner(
                new String[] { "Zoomed (fills the width)", "Automatic (with border)", "Title", "Action", "Underscan" },
                new String[] { "zoomed", "auto", "title", "action", "underscan" },
                s.get("picture", "zoomed"), v -> host.apply("picture", v))));
        add(labeled("Scaling", spinner(
                new String[] { "Sharp bilinear (best)", "Hard pixels (nearest)", "Smooth (linear)" },
                new String[] { "auto", "nearest", "linear" },
                s.get("gl_filter", "auto"), v -> host.apply("gl_filter", v))));

        section("ADJUST");
        add(adjust(s, "brightness", "Brightness", 0, 100, 52, ""));
        add(adjust(s, "contrast", "Contrast", 0, 100, 52, ""));
        add(adjust(s, "saturation", "Color", 0, 100, 50, ""));
        add(adjust(s, "hue", "Hue", -179, 180, 0, "°"));

        section("OPTIONS");
        add(check("60 Hz scaling", s.getBool("ntsc_scaling", false),
                b -> host.apply("ntsc_scaling", b ? "1" : "0")));
        add(check("Color killer", s.getBool("cmp_colour_killer", false),
                b -> host.apply("cmp_colour_killer", b ? "1" : "0")));
        add(check("Inverse text", s.getBool("vdg_inverse", false),
                b -> host.apply("vdg_inverse", b ? "1" : "0")));
    }

    private View adjust(final Settings s, final String key, String name, int min, int max, int def, String unit) {
        return slider(name, min, max, s.getInt(key, def), def, unit,
                v -> host.sendSet(key, Integer.toString(v)),
                v -> s.setInt(key, v));
    }

    private void buildAudio() {
        final Settings s = host.settings();
        section("AUDIO");
        add(check("Mute", s.getBool("mute", false), b -> {
            s.setBool("mute", b);
            host.sendSet("gain", b ? "-50" : Integer.toString(s.getInt("gain", -3)));
        }));
        add(slider("Volume", -49, 3, s.getInt("gain", -3), -3, " dB",
                v -> {
                    if (!s.getBool("mute", false)) {
                        host.sendSet("gain", Integer.toString(v));
                    }
                },
                v -> s.setInt("gain", v)));
        section("SOUND CARTRIDGE");
        add(labeled("Sound cartridge", spinner(
                new String[] { "None", "Games Master Cartridge", "CoCoXSID (SID chip)" },
                new String[] { "none", "gmc", "cocoxsid" },
                s.get("sound", "none"), v -> host.apply("sound", v))));
        add(note("Sound cartridges share the Multi-Pak with the disk controller. Only one at a time. "
                + "CoCoXSID is the SID chip board (registers at $FF40); the Games Master Cartridge adds "
                + "a sound chip and also runs GMC games."));
        add(note("Gain in decibels: 0 is full scale, -3 is the default, lower is quieter. "
                + "The phone's volume keys control the overall level."));
    }

    private void buildInput() {
        final Settings s = host.settings();
        final String[] joyLabels = { "None", "Touch screen (mouse)", "On-screen pad (keyboard)" };
        final String[] joyValues = { "none", "mjoy0", "kjoy0" };
        section("JOYSTICKS");
        add(labeled("Right joystick", spinner(joyLabels, joyValues,
                s.get("joy_right", "mjoy0"), v -> host.applyJoystick("joy_right", v))));
        add(labeled("Left joystick", spinner(joyLabels, joyValues,
                s.get("joy_left", "none"), v -> host.applyJoystick("joy_left", v))));
        add(note("Most games use the right joystick. \"Touch screen\" moves the joystick to where "
                + "you touch the picture. \"On-screen pad\" gives a direction pad and two fire buttons."));
        add(labeled("Joystick type", spinner(PadView.TYPE_NAMES, PadView.TYPE_IDS,
                s.get("ui_joy_type", "std"), v -> host.setUiString("ui_joy_type", v))));
        add(note("Drawn from above like the real controllers. The Radio Shack joystick stays where you "
                + "leave it (push it back to center); the Deluxe and Wico spring back. The Wico has two buttons: "
                + "the red one on the base and the white tip of the ball."));
        add(check("Show on-screen pad", s.getBool("ui_pad", false), b -> host.setPadVisible(b)));

        section("KEYBOARD");
        add(check("Show CoCo keyboard", s.getBool("ui_kbd", true), b -> host.setKeyboardVisible(b)));
        add(check("Sticky SHIFT (tap once for one key, twice to lock)", s.getBool("ui_sticky", true),
                b -> host.setUi("ui_sticky", b)));
        add(check("Vibrate on key press", s.getBool("ui_haptic", true), b -> host.setUi("ui_haptic", b)));
        add(labeled("Keyboard style", spinner(CocoKeyboardView.STYLE_NAMES, CocoKeyboardView.STYLE_IDS,
                s.get("ui_kbd_style", "white"), v -> host.setUiString("ui_kbd_style", v))));
    }

    private String shortName(String path) {
        if (path == null || path.isEmpty()) {
            return "(empty)";
        }
        return new File(path).getName();
    }

    private void buildFiles() {
        final Settings s = host.settings();
        section("DISK DRIVES");
        for (int d = 0; d < 4; d++) {
            final int drive = d;
            add(line("Drive " + (d + 1) + ":  " + shortName(s.get("disk" + d, "")), TEXT));
            add(buttonRow(
                    actionButton("Insert…", () -> host.pickFile(MainActivity.REQ_DISK + drive)),
                    actionButton("Eject", () -> host.ejectDisk(drive))));
        }
        add(note("With a CoCoSDC selected, drives 1 and 2 are the SD card's, and each disk image "
                + "can be in only one of them."));

        section("EMESDOS09 SD CARD");
        add(line("SD card:  " + shortName(s.get("emes_image", "")), TEXT));
        add(buttonRow(
                actionButton("Insert…", () -> host.pickFile(MainActivity.REQ_EMES_IMAGE)),
                actionButton("Eject", () -> host.ejectEmesImage())));
        add(note("For the emesDOS09 cartridge (Machine > Cartridge): a raw FAT16 image, superfloppy or "
                + "partitioned. Its ROM goes in Machine > ROM files, or tap the 16K ROM in Paks. Changes the "
                + "CoCo writes go to the app's copy in emesdos/."));

        section("CASSETTE");
        add(line("Tape:  " + shortName(host.tapeName()), TEXT));
        add(buttonRow(
                actionButton("Insert…", () -> host.pickFile(MainActivity.REQ_TAPE)),
                actionButton("Play/Pause", () -> host.command(MainActivity.OP_TAPE_PLAYPAUSE, 0, 0, null))));
        add(buttonRow(
                actionButton("Rewind", () -> host.command(MainActivity.OP_TAPE_REWIND, 0, 0, null)),
                actionButton("Eject", () -> host.ejectTape())));
        add(note("Then type CLOAD, CLOADM or LOAD on the CoCo; Play starts the tape."));

        section("PROGRAMS");
        add(buttonRow(
                actionButton("Load file…", () -> host.pickFile(MainActivity.REQ_LOAD)),
                actionButton("Run file…", () -> host.pickFile(MainActivity.REQ_RUN))));
        add(note("Load attaches a disk, tape or program. Run also tries to start it."));

        section("FOLDER");
        add(note(host.homeDir().getPath()));
        add(note("ROMs, xroar.conf, the SD card (sdcard/), and the log (xroar-log.txt) live here."));
    }
}
