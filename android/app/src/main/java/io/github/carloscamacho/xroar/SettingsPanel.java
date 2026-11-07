package io.github.carloscamacho.xroar;

import android.content.Context;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;

/* The settings screen, modelled on XRoar Online's tabs: Machine, Video, Audio,
 * Input and Files.  Everything applies at once (the emulator keeps running
 * behind it) and is saved in settings.conf. */
final class SettingsPanel extends LinearLayout {
    private static final int BG = 0xF0181A1D;
    private static final int TEXT = 0xFFE8EAED;
    private static final int DIM = 0xFF9AA0A6;
    private static final int ACCENT = 0xFF2D7DD2;

    private static final String[] TAB_NAMES = { "Machine", "Video", "Audio", "Input", "Files" };

    private interface OnPick { void pick(String value); }
    private interface OnInt { void value(int v); }
    private interface OnBool { void value(boolean b); }

    private final MainActivity host;
    private final LinearLayout tabRow;
    private final ScrollView scroll;
    private final Button[] tabButtons = new Button[TAB_NAMES.length];
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
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
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
            tabRow.addView(b, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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

    private Button actionButton(String label, Runnable r) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private TextView heading(String t) {
        TextView v = new TextView(getContext());
        v.setText(t);
        v.setTextColor(ACCENT);
        v.setTextSize(13);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(14), 0, dp(4));
        return v;
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
            row.addView(b, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private Spinner spinner(String[] labels, final String[] values, String current, final OnPick cb) {
        Spinner sp = new Spinner(getContext());
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
        return sp;
    }

    private CheckBox check(String label, boolean on, final OnBool cb) {
        CheckBox c = new CheckBox(getContext());
        c.setText(label);
        c.setTextColor(TEXT);
        c.setChecked(on);
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
            tabButtons[i].setTextColor(i == n ? ACCENT : TEXT);
            tabButtons[i].setTypeface(i == n ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        content.setPadding(dp(14), dp(2), dp(14), dp(24));
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
        content.addView(heading("MACHINE"));
        content.addView(labeled("Machine", spinner(
                new String[] { "CoCo (NTSC)", "CoCo (PAL)", "CoCo 2B (NTSC)", "CoCo 2B (PAL)" },
                new String[] { "cocous", "coco", "coco2bus", "coco2b" },
                s.get("machine", "cocous"), v -> host.applyMachineSetting("machine", v))));
        content.addView(labeled("RAM", spinner(
                new String[] { "4 KB", "16 KB", "32 KB", "64 KB" },
                new String[] { "4", "16", "32", "64" },
                s.get("ram", "64"), v -> host.applyMachineSetting("ram", v))));
        content.addView(labeled("Cartridge", spinner(
                new String[] { "Automatic", "Disk controller (RS-DOS)", "CoCoSDC", "None" },
                new String[] { "auto", "rsdos", "cocosdc", "none" },
                s.get("cart", "auto"), v -> host.apply("cart", v))));
        content.addView(note("Automatic uses the CoCoSDC when roms/sdcdos.rom is present, "
                + "otherwise the disk controller. Changing these restarts the emulated CoCo."));
        content.addView(buttonRow(
                actionButton("Soft reset", () -> host.command(MainActivity.OP_SOFT_RESET, 0, 0, null)),
                actionButton("Hard reset", () -> host.command(MainActivity.OP_HARD_RESET, 0, 0, null))));

        content.addView(heading("ROM FILES"));
        String[][] roms = {
            { "Color BASIC", "bas13", "bas12", "bas11", "bas10" },
            { "Extended BASIC", "extbas11", "extbas10" },
            { "Disk controller", "disk11", "disk10" },
            { "SDC-DOS (CoCoSDC)", "sdcdos", "sdc-dos", "sdc_dos" },
        };
        for (String[] r : roms) {
            String found = host.findRom(r);
            boolean optional = r[0].startsWith("SDC");
            if (found != null) {
                content.addView(line(r[0] + ":  " + found, 0xFF81C995));
            } else {
                content.addView(line(r[0] + ":  " + (optional ? "not present (optional)" : "MISSING")
                        + "  (" + r[1] + ".rom)", optional ? DIM : 0xFFF28B82));
            }
        }
        content.addView(note("Put ROM images in " + host.homeDir().getPath() + "/roms/ and restart the app."));
    }

    private void buildVideo() {
        final Settings s = host.settings();
        content.addView(heading("PICTURE"));
        content.addView(labeled("TV input", spinner(
                new String[] { "Automatic", "Composite (blue-red)", "Composite (red-blue)", "S-Video", "RGB" },
                new String[] { "auto", "cmp-br", "cmp-rb", "cmp", "rgb" },
                s.get("tv_input", "auto"), v -> host.apply("tv_input", v))));
        content.addView(note("Pick blue-red or red-blue to see the orange/blue artifact colors "
                + "that CoCo games use. If they look swapped, choose the other one."));
        content.addView(labeled("Color renderer", spinner(
                new String[] { "Automatic", "None", "Simple", "5-bit", "Partial NTSC", "Simulated" },
                new String[] { "auto", "none", "simple", "5bit", "partial", "simulated" },
                s.get("ccr", "auto"), v -> host.apply("ccr", v))));
        content.addView(labeled("Picture size", spinner(
                new String[] { "Automatic", "Zoomed", "Title", "Action", "Underscan" },
                new String[] { "auto", "zoomed", "title", "action", "underscan" },
                s.get("picture", "auto"), v -> host.apply("picture", v))));
        content.addView(labeled("Scaling", spinner(
                new String[] { "Automatic", "Sharp (nearest)", "Smooth (linear)" },
                new String[] { "auto", "nearest", "linear" },
                s.get("gl_filter", "auto"), v -> host.apply("gl_filter", v))));

        content.addView(heading("ADJUST"));
        content.addView(adjust(s, "brightness", "Brightness", 0, 100, 52, ""));
        content.addView(adjust(s, "contrast", "Contrast", 0, 100, 52, ""));
        content.addView(adjust(s, "saturation", "Color", 0, 100, 50, ""));
        content.addView(adjust(s, "hue", "Hue", -179, 180, 0, "°"));

        content.addView(heading("OPTIONS"));
        content.addView(check("60 Hz scaling", s.getBool("ntsc_scaling", false),
                b -> host.apply("ntsc_scaling", b ? "1" : "0")));
        content.addView(check("Color killer", s.getBool("cmp_colour_killer", false),
                b -> host.apply("cmp_colour_killer", b ? "1" : "0")));
        content.addView(check("Inverse text", s.getBool("vdg_inverse", false),
                b -> host.apply("vdg_inverse", b ? "1" : "0")));
    }

    private View adjust(final Settings s, final String key, String name, int min, int max, int def, String unit) {
        return slider(name, min, max, s.getInt(key, def), def, unit,
                v -> host.sendSet(key, Integer.toString(v)),
                v -> s.setInt(key, v));
    }

    private void buildAudio() {
        final Settings s = host.settings();
        content.addView(heading("AUDIO"));
        content.addView(check("Mute", s.getBool("mute", false), b -> {
            s.setBool("mute", b);
            host.sendSet("gain", b ? "-50" : Integer.toString(s.getInt("gain", -3)));
        }));
        content.addView(slider("Volume", -49, 3, s.getInt("gain", -3), -3, " dB",
                v -> {
                    if (!s.getBool("mute", false)) {
                        host.sendSet("gain", Integer.toString(v));
                    }
                },
                v -> s.setInt("gain", v)));
        content.addView(note("Gain in decibels: 0 is full scale, -3 is the default, lower is quieter. "
                + "The phone's volume keys control the overall level."));
    }

    private void buildInput() {
        final Settings s = host.settings();
        final String[] joyLabels = { "None", "Touch screen (mouse)", "On-screen pad (keyboard)" };
        final String[] joyValues = { "none", "mjoy0", "kjoy0" };
        content.addView(heading("JOYSTICKS"));
        content.addView(labeled("Right joystick", spinner(joyLabels, joyValues,
                s.get("joy_right", "mjoy0"), v -> host.applyJoystick("joy_right", v))));
        content.addView(labeled("Left joystick", spinner(joyLabels, joyValues,
                s.get("joy_left", "none"), v -> host.applyJoystick("joy_left", v))));
        content.addView(note("Most games use the right joystick. \"Touch screen\" moves the joystick to where "
                + "you touch the picture. \"On-screen pad\" gives a direction pad and two fire buttons."));
        content.addView(check("Show on-screen pad", s.getBool("ui_pad", false), b -> host.setPadVisible(b)));

        content.addView(heading("KEYBOARD"));
        content.addView(check("Show CoCo keyboard", s.getBool("ui_kbd", true), b -> host.setKeyboardVisible(b)));
        content.addView(check("Sticky SHIFT (tap once for one key, twice to lock)", s.getBool("ui_sticky", true),
                b -> host.setUi("ui_sticky", b)));
        content.addView(check("Vibrate on key press", s.getBool("ui_haptic", true), b -> host.setUi("ui_haptic", b)));
        content.addView(buttonRow(actionButton("Type text…", () -> host.showTypeDialog())));
        content.addView(note("\"Type text\" uses your phone's own keyboard and types the text into the CoCo, "
                + "so you can paste programs. A Bluetooth keyboard also works."));
    }

    private String shortName(String path) {
        if (path == null || path.isEmpty()) {
            return "(empty)";
        }
        return new File(path).getName();
    }

    private void buildFiles() {
        final Settings s = host.settings();
        content.addView(heading("DISK DRIVES"));
        for (int d = 0; d < 4; d++) {
            final int drive = d;
            content.addView(line("Drive " + (d + 1) + ":  " + shortName(s.get("disk" + d, "")), TEXT));
            content.addView(buttonRow(
                    actionButton("Insert…", () -> host.pickFile(MainActivity.REQ_DISK + drive)),
                    actionButton("Eject", () -> host.ejectDisk(drive))));
        }
        content.addView(note("With a CoCoSDC selected, drives 1 and 2 are the SD card's, and each disk image "
                + "can be in only one of them."));

        content.addView(heading("CASSETTE"));
        content.addView(line("Tape:  " + shortName(host.tapeName()), TEXT));
        content.addView(buttonRow(
                actionButton("Insert…", () -> host.pickFile(MainActivity.REQ_TAPE)),
                actionButton("Play/Pause", () -> host.command(MainActivity.OP_TAPE_PLAYPAUSE, 0, 0, null))));
        content.addView(buttonRow(
                actionButton("Rewind", () -> host.command(MainActivity.OP_TAPE_REWIND, 0, 0, null)),
                actionButton("Eject", () -> host.ejectTape())));
        content.addView(note("Then type CLOAD, CLOADM or LOAD on the CoCo; Play starts the tape."));

        content.addView(heading("PROGRAMS"));
        content.addView(buttonRow(
                actionButton("Load file…", () -> host.pickFile(MainActivity.REQ_LOAD)),
                actionButton("Run file…", () -> host.pickFile(MainActivity.REQ_RUN))));
        content.addView(note("Load attaches a disk, tape or program. Run also tries to start it."));

        content.addView(heading("FOLDER"));
        content.addView(note(host.homeDir().getPath()));
        content.addView(note("ROMs, xroar.conf, the SD card (sdcard/), and the log (xroar-log.txt) live here."));
    }
}
