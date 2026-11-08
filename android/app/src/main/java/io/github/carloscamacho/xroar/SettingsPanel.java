package io.github.carloscamacho.xroar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/* The settings screen, modelled on XRoar Online's tabs: Machine, Video, Audio,
 * Input, Files and Look.  Everything applies at once (the emulator keeps running
 * behind it) and is saved in settings.conf.  A new panel is built when the
 * theme changes, so the palette is read once, here, not from constants. */
final class SettingsPanel extends LinearLayout {
    private final int BG = Theme.SURFACE;
    private final int TEXT = Theme.TEXT_STRONG;
    private final int DIM = Theme.TEXT_MUTED;
    private final int ACCENT = Theme.PRIMARY;

    private static final String[] TAB_NAMES = { "Machine", "Video", "Audio", "Input", "Files", "Look" };
    private static final int[] TAB_ICONS = { R.drawable.ic_chip, R.drawable.ic_monitor, R.drawable.ic_speaker,
        R.drawable.ic_gamepad, R.drawable.ic_folder, R.drawable.ic_contrast };

    private interface OnPick { void pick(String value); }
    private interface OnInt { void value(int v); }
    private interface OnBool { void value(boolean b); }

    private final MainActivity host;
    private final LinearLayout tabRow;
    private final ScrollView scroll;
    private final ToolButton[] tabButtons = new ToolButton[TAB_NAMES.length];
    private final View[] tabIndicators = new View[TAB_NAMES.length];
    private LinearLayout body;                               // the open accordion section's body
    private final Map<String, Boolean> openState = new HashMap<>();
    private int sectionCount;
    private LinearLayout content;
    private int tab = 0;

    SettingsPanel(MainActivity host, int firstTab) {
        super(host);
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        setClickable(true);   // swallow touches so they don't reach the picture

        LinearLayout header = new LinearLayout(host);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(Theme.SPACE_S), dp(Theme.SPACE_XS), dp(Theme.SPACE_XS), dp(Theme.SPACE_XS));
        TextView title = Theme.heading(host, "Settings", Theme.H4);
        header.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = Theme.withIcon(Theme.textButton(host, "Close"), R.drawable.ic_close);
        close.setOnClickListener(v -> host.closeSettings());
        header.addView(close);
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /* BUIK tabs: an icon and label in a cell over a 2 dp bar, side by side with no gaps. */
        tabRow = new LinearLayout(host);
        tabRow.setOrientation(HORIZONTAL);
        tabRow.setPadding(dp(Theme.SPACE_XS), 0, dp(Theme.SPACE_XS), 0);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int n = i;
            ToolButton b = new ToolButton(host, TAB_ICONS[i], TAB_NAMES[i], Theme.ICON_SMALL_DP);
            b.setOnClickListener(v -> showTab(n));
            tabButtons[i] = b;
            View ind = new View(host);
            tabIndicators[i] = ind;
            LinearLayout cell = new LinearLayout(host);
            cell.setOrientation(VERTICAL);
            cell.addView(b, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(Theme.CONTROL_HEIGHT_DP + Theme.SPACE_XS)));
            cell.addView(ind, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(Theme.TAB_INDICATOR_DP)));
            // wrap-content plus an equal share of what is left, so "Machine" is never squeezed
            tabRow.addView(cell, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        addView(tabRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(host);
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        showTab(firstTab);
    }

    int currentTab() {
        return tab;
    }

    /* Rebuild the current tab from the saved settings (after a file pick, say). */
    void refresh() {
        showTab(tab);
    }

    /* ---------------------------------------------------------------- */
    /* Widgets */

    private int dp(int v) {
        return Theme.dp(getContext(), v);
    }

    /* BUIK solid button (brand-red fill) for the main action of a row. */
    private Button actionButton(String label, int icon, Runnable r) {
        Button b = Theme.withIcon(Theme.solidButton(getContext(), label), icon);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    /* BUIK outlined button for the second action (Remove, Eject, Rewind). */
    private Button secondaryButton(String label, int icon, Runnable r) {
        Button b = Theme.withIcon(Theme.outlinedButton(getContext(), label), icon);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    /* An accordion section: a BUIK card (4 dp corners, 1 dp line) that opens
     * and closes.  Everything added with add() until the next section() goes inside it. */
    private void section(String title) {
        final String key = tab + "/" + title;
        Boolean saved = openState.get(key);
        final boolean[] open = { saved != null ? saved : sectionCount == 0 };
        sectionCount++;
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setBackground(Theme.card(getContext()));
        LinearLayout.LayoutParams cp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, dp(Theme.SPACE_XS), 0, 0);
        content.addView(card, cp);

        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(Theme.CONTROL_HEIGHT_DP + Theme.SPACE_XS));
        head.setPadding(dp(Theme.SPACE_S), 0, dp(Theme.SPACE_S), 0);
        head.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null,
                Theme.box(getContext(), 0xFFFFFFFF, 0, 0)));
        String nice = title.substring(0, 1) + title.substring(1).toLowerCase(java.util.Locale.US);
        head.addView(Theme.iconView(getContext(), sectionIcon(title), ACCENT, Theme.ICON_SMALL_DP),
                new LayoutParams(dp(Theme.ICON_SMALL_DP), dp(Theme.ICON_SMALL_DP)));
        TextView t = Theme.heading(getContext(), nice, Theme.H6);
        t.setPadding(dp(Theme.SPACE_S), 0, 0, 0);
        head.addView(t, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final ImageView chev = Theme.iconView(getContext(), R.drawable.ic_chevron_down, DIM, Theme.ICON_SMALL_DP);
        head.addView(chev, new LayoutParams(dp(Theme.ICON_SMALL_DP), dp(Theme.ICON_SMALL_DP)));
        card.addView(head);

        final LinearLayout inner = new LinearLayout(getContext());
        inner.setOrientation(VERTICAL);
        inner.setPadding(dp(Theme.SPACE_S), 0, dp(Theme.SPACE_S), dp(Theme.SPACE_S));
        card.addView(inner);
        final Runnable apply = () -> {
            inner.setVisibility(open[0] ? View.VISIBLE : View.GONE);
            chev.setRotation(open[0] ? 180f : 0f);
        };
        apply.run();
        head.setOnClickListener(v -> {
            open[0] = !open[0];
            openState.put(key, open[0]);
            apply.run();
        });
        body = inner;
    }

    private static int sectionIcon(String title) {
        switch (title) {
        case "MACHINE": return R.drawable.ic_chip;
        case "ROM FILES": return R.drawable.ic_chip;
        case "PICTURE": return R.drawable.ic_monitor;
        case "ADJUST": return R.drawable.ic_settings;
        case "OPTIONS": return R.drawable.ic_settings;
        case "AUDIO": return R.drawable.ic_speaker;
        case "SOUND CARTRIDGE": return R.drawable.ic_cartridge;
        case "JOYSTICKS": return R.drawable.ic_gamepad;
        case "KEYBOARD": return R.drawable.ic_keyboard;
        case "DISK DRIVES": return R.drawable.ic_disk;
        case "EMESDOS09 SD CARD": return R.drawable.ic_sdcard;
        case "CASSETTE": return R.drawable.ic_tape;
        case "PROGRAMS": return R.drawable.ic_play;
        case "FOLDER": return R.drawable.ic_folder;
        case "APPEARANCE": return R.drawable.ic_contrast;
        case "TOOLBAR": return R.drawable.ic_menu;
        default: return R.drawable.ic_info;
        }
    }

    /* Add to the open section (or straight to the page if there is none). */
    private void add(View v) {
        (body != null ? body : content).addView(v);
    }

    /* BUIK P5: notes under a control. */
    private TextView note(String t) {
        TextView v = Theme.body(getContext(), t, Theme.P5, DIM);
        v.setPadding(0, dp(Theme.SPACE_XXS), 0, dp(Theme.SPACE_XXS));
        return v;
    }

    /* BUIK P4: a line of text the size of the controls'. */
    private TextView line(String t, int color) {
        TextView v = Theme.body(getContext(), t, Theme.P4, color);
        v.setPadding(0, dp(Theme.SPACE_XXS), 0, dp(Theme.SPACE_XXS));
        return v;
    }

    private LinearLayout labeled(String label, View control) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(Theme.SPACE_XXS), 0, dp(Theme.SPACE_XXS));
        TextView l = line(label, TEXT);
        l.setPadding(0, 0, dp(Theme.SPACE_XS), 0);
        row.addView(l, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));
        row.addView(control, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));
        return row;
    }

    /* Buttons side by side, 8 dp apart. */
    private LinearLayout buttonRow(Button... buttons) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, dp(Theme.SPACE_XXS), 0, dp(Theme.SPACE_XXS));
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(i == 0 ? 0 : dp(Theme.SPACE_XXS), 0, i == buttons.length - 1 ? 0 : dp(Theme.SPACE_XXS), 0);
            row.addView(buttons[i], lp);
        }
        return row;
    }

    /* A BUIK drop-down: a 48 dp field with a 1 dp line (3 dp red when open),
     * P4 text, the chevron 16 dp from the right; the list is a raised box of 48 dp rows. */
    private View spinner(String[] labels, final String[] values, String current, final OnPick cb) {
        final Spinner sp = new Spinner(getContext(), Spinner.MODE_DROPDOWN);
        sp.setBackground(Theme.fieldBackground(getContext()));
        sp.setPadding(0, 0, 0, 0);
        sp.setPopupBackgroundDrawable(Theme.popupBackground(getContext()));
        sp.setDropDownVerticalOffset(dp(Theme.CONTROL_HEIGHT_DP + Theme.SPACE_XXS));
        ArrayAdapter<String> ad = new ArrayAdapter<String>(getContext(), 0, labels) {
            @Override
            public View getView(int position, View convert, ViewGroup parent) {
                return Theme.dropdownRow(getContext(), getItem(position), false, false);
            }

            @Override
            public View getDropDownView(int position, View convert, ViewGroup parent) {
                return Theme.dropdownRow(getContext(), getItem(position), true,
                        position == sp.getSelectedItemPosition());
            }
        };
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
                dp(Theme.CONTROL_HEIGHT_DP)));
        ImageView arrow = Theme.iconView(getContext(), R.drawable.ic_chevron_down, TEXT, Theme.ICON_SMALL_DP);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(dp(Theme.ICON_SMALL_DP), dp(Theme.ICON_SMALL_DP),
                Gravity.END | Gravity.CENTER_VERTICAL);
        alp.setMarginEnd(dp(Theme.SPACE_S));
        box.addView(arrow, alp);
        arrow.setClickable(false);
        return box;
    }

    /* A BUIK toggle row: the label on the left, the 48 x 24 toggle on the right. */
    private CompoundButton check(String label, boolean on, final OnBool cb) {
        Switch c = new Switch(getContext());
        c.setText(label);
        c.setTextColor(TEXT);
        c.setTypeface(Theme.bodyFace(getContext()));
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.P4);
        c.setMinHeight(dp(Theme.CONTROL_HEIGHT_DP));
        c.setMinimumHeight(dp(Theme.CONTROL_HEIGHT_DP));
        Theme.styleToggle(c);
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
        Button reset = Theme.textButton(getContext(), "Reset");
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
            tabButtons[i].setActive(i == n);
            // BUIK: a 2 dp bar, brand red under the open tab and a faint line under the rest
            tabIndicators[i].setBackground(Theme.box(getContext(), i == n ? Theme.BRAND_RED : Theme.BORDER_FAINT, 0, 0));
        }
        content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        content.setPadding(dp(Theme.SPACE_S), dp(Theme.SPACE_XS), dp(Theme.SPACE_S), dp(Theme.SPACE_M));
        body = null;
        sectionCount = 0;
        switch (n) {
        case 0: buildMachine(); break;
        case 1: buildVideo(); break;
        case 2: buildAudio(); break;
        case 3: buildInput(); break;
        case 4: buildFiles(); break;
        default: buildLook(); break;
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
                    missing ? (slot.required ? Theme.ERROR : DIM) : Theme.GOOD));
            add(buttonRow(
                    actionButton(missing ? "Add…" : "Replace…", R.drawable.ic_add, () -> host.pickRom(slot)),
                    secondaryButton("Remove", R.drawable.ic_trash, () -> host.removeRom(slot))));
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
        add(note("Drawn from above like the real controllers. Push the stick with your finger: it stays where "
                + "you hold it, and springs back to the center when you lift your finger. The Wico has two "
                + "buttons: the red one on the base and the white tip of the ball. The virtual joystick has "
                + "no case: it hovers see-through over the picture, so it works in full-screen mode too."));
        add(labeled("Joystick position", spinner(PadView.SIDE_NAMES, PadView.SIDE_IDS,
                s.get("ui_joy_side", "center"), v -> host.setUiString("ui_joy_side", v))));
        add(note("Where the joystick sits under the picture in portrait. The virtual joystick puts its stick on "
                + "that side and the fire buttons on the other (Center acts as Left)."));
        add(slider("Virtual joystick opacity", 10, 90, s.getInt("ui_vpad_alpha", 45), 45, "%",
                v -> host.previewVpadAlpha(v), v -> host.setUiInt("ui_vpad_alpha", v)));
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
                    actionButton("Insert…", R.drawable.ic_disk, () -> host.pickFile(MainActivity.REQ_DISK + drive)),
                    secondaryButton("Eject", R.drawable.ic_eject, () -> host.ejectDisk(drive))));
        }
        add(note("With a CoCoSDC selected, drives 1 and 2 are the SD card's, and each disk image "
                + "can be in only one of them."));

        section("EMESDOS09 SD CARD");
        add(line("SD card:  " + shortName(s.get("emes_image", "")), TEXT));
        add(buttonRow(
                actionButton("Insert…", R.drawable.ic_sdcard, () -> host.pickFile(MainActivity.REQ_EMES_IMAGE)),
                secondaryButton("Eject", R.drawable.ic_eject, () -> host.ejectEmesImage())));
        add(note("For the emesDOS09 cartridge (Machine > Cartridge): a raw FAT16 image, superfloppy or "
                + "partitioned. Its ROM goes in Machine > ROM files, or tap the 16K ROM in Paks. Changes the "
                + "CoCo writes go to the app's copy in emesdos/."));

        section("CASSETTE");
        add(line("Tape:  " + shortName(host.tapeName()), TEXT));
        add(buttonRow(
                actionButton("Insert…", R.drawable.ic_tape, () -> host.pickFile(MainActivity.REQ_TAPE)),
                actionButton("Play/Pause", R.drawable.ic_play, () -> host.command(MainActivity.OP_TAPE_PLAYPAUSE, 0, 0, null))));
        add(buttonRow(
                secondaryButton("Rewind", R.drawable.ic_rewind, () -> host.command(MainActivity.OP_TAPE_REWIND, 0, 0, null)),
                secondaryButton("Eject", R.drawable.ic_eject, () -> host.ejectTape())));
        add(note("Then type CLOAD, CLOADM or LOAD on the CoCo; Play starts the tape."));

        section("PROGRAMS");
        add(buttonRow(
                actionButton("Load file…", R.drawable.ic_file, () -> host.pickFile(MainActivity.REQ_LOAD)),
                actionButton("Run file…", R.drawable.ic_play, () -> host.pickFile(MainActivity.REQ_RUN))));
        add(note("Load attaches a disk, tape or program. Run also tries to start it."));

        folderRow();
        add(note("ROMs, xroar.conf, the SD card (sdcard/), and the log (xroar-log.txt) live there. "
                + "Tap Folder to open it in your file manager."));
    }

    /* "Folder" is a row, not an accordion: tapping it opens the folder in the file manager. */
    private void folderRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(Theme.CONTROL_HEIGHT_DP + Theme.SPACE_XS));
        row.setPadding(dp(Theme.SPACE_S), dp(Theme.SPACE_XS), dp(Theme.SPACE_S), dp(Theme.SPACE_XS));
        row.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x33FF5C70),
                Theme.card(getContext()), Theme.box(getContext(), 0xFFFFFFFF, 0, 0)));
        row.setClickable(true);
        row.setContentDescription("Folder: open in the file manager");
        row.addView(Theme.iconView(getContext(), R.drawable.ic_folder, ACCENT, Theme.ICON_SMALL_DP),
                new LayoutParams(dp(Theme.ICON_SMALL_DP), dp(Theme.ICON_SMALL_DP)));
        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(VERTICAL);
        text.setPadding(dp(Theme.SPACE_S), 0, dp(Theme.SPACE_S), 0);
        text.addView(Theme.heading(getContext(), "Folder", Theme.H6));
        TextView path = note(host.homeDir().getPath());
        text.addView(path);
        row.addView(text, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView go = Theme.iconView(getContext(), R.drawable.ic_chevron_down, DIM, Theme.ICON_SMALL_DP);
        go.setRotation(-90f);   // pointing right: "open"
        row.addView(go, new LayoutParams(dp(Theme.ICON_SMALL_DP), dp(Theme.ICON_SMALL_DP)));
        row.setOnClickListener(v -> host.openHomeFolder());
        LinearLayout.LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(Theme.SPACE_XS), 0, 0);
        content.addView(row, lp);
        body = null;
    }

    private void buildLook() {
        final Settings s = host.settings();
        section("APPEARANCE");
        add(labeled("Theme", spinner(
                new String[] { "Dark", "Light", "Same as the system" },
                new String[] { "dark", "light", "system" },
                s.get("ui_theme", "dark"), v -> host.setThemePref(v))));
        add(note("The brand red and green stay the same. In the light theme red text is the brand red "
                + "itself and good-news text a darker green, so both stay readable on white."));
        section("TOOLBAR");
        add(check("Show labels under the toolbar icons", s.getBool("ui_labels", true),
                b -> host.setUi("ui_labels", b)));
    }
}
