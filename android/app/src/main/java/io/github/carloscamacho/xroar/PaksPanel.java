package io.github.carloscamacho.xroar;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/* The media browser for paks/: scroll or search the cartridges there, tap one
 * to insert it (the CoCo resets and starts it, as turning it on with the Pak
 * in would), long-press to delete it. */
final class PaksPanel extends LinearLayout {
    private static final int BG = Theme.SURFACE;
    private static final int TEXT = Theme.TEXT_STRONG;
    private static final int DIM = Theme.TEXT_MUTED;
    private static final int WARN = Theme.ERROR;

    private final MainActivity host;
    private final PakLibrary library;
    private final TextView status;
    private final TextView countView;
    private final EditText search;
    private final ListView list;
    private List<PakLibrary.Pak> all = new ArrayList<>();
    private List<PakLibrary.Pak> shown = new ArrayList<>();

    PaksPanel(MainActivity host, PakLibrary library) {
        super(host);
        this.host = host;
        this.library = library;
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        setClickable(true);

        LinearLayout header = new LinearLayout(host);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(Theme.SPACE_S), dp(Theme.SPACE_XS), dp(Theme.SPACE_XS), dp(Theme.SPACE_XS));
        TextView title = Theme.heading(host, "Paks", Theme.H4);
        header.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button eject = Theme.textButton(host, "Eject");
        eject.setOnClickListener(v -> {
            host.ejectPak();
            updateStatus();
        });
        header.addView(eject);
        Button add = Theme.textButton(host, "Add…");
        add.setOnClickListener(v -> host.pickPaks());
        header.addView(add);
        Button close = Theme.textButton(host, "Close");
        close.setOnClickListener(v -> host.closePaks());
        header.addView(close);
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        search = new EditText(host);
        search.setHint("Search");
        search.setSingleLine(true);
        Theme.styleInput(search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { applyFilter(); }
        });
        LinearLayout.LayoutParams sp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(dp(Theme.SPACE_S), 0, dp(Theme.SPACE_S), 0);
        addView(search, sp);

        status = Theme.body(host, "", Theme.P5, DIM);
        status.setPadding(dp(Theme.SPACE_S), dp(Theme.SPACE_XS), dp(Theme.SPACE_S), dp(Theme.SPACE_XS));
        addView(status);

        /* BUIK list: 48 dp rows with a 1 dp line under each, a separator on top. */
        addView(Theme.separator(host), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(Theme.LINE_DP)));
        list = new ListView(host);
        list.setDividerHeight(0);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
        addView(list, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        list.setOnItemClickListener((AdapterView<?> p, View v, int pos, long id) -> insert(shown.get(pos)));
        list.setOnItemLongClickListener((AdapterView<?> p, View v, int pos, long id) -> {
            confirmDelete(shown.get(pos));
            return true;
        });

        countView = Theme.body(host, "", Theme.P5, DIM);
        countView.setPadding(dp(Theme.SPACE_S), dp(Theme.SPACE_XS), dp(Theme.SPACE_S), dp(Theme.SPACE_S));
        addView(countView);
    }

    private int dp(int v) {
        return Theme.dp(getContext(), v);
    }

    /* Rescan paks/ and show it. */
    void refresh() {
        all = library.scan();
        applyFilter();
        updateStatus();
    }

    private void updateStatus() {
        String slot = host.currentPakName();
        status.setText(slot == null ? "No Pak in the slot." : "In the slot: " + slot);
        status.setTextColor(slot == null ? DIM : Theme.BRAND_GREEN);
        countView.setText(all.size() + " in " + library.dir().getPath()
                + (all.isEmpty() ? "\nTap Add… or copy .rom / .ccc files there." : ""));
    }

    private void applyFilter() {
        shown = PakLibrary.filter(all, search.getText().toString());
        list.setAdapter(new ArrayAdapter<PakLibrary.Pak>(getContext(), 0, shown) {
            @Override
            public View getView(int position, View convert, ViewGroup parent) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(VERTICAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(Theme.CONTROL_HEIGHT_DP));
                row.setPadding(dp(Theme.SPACE_S), dp(Theme.SPACE_XS), dp(Theme.SPACE_S), dp(Theme.SPACE_XS));
                row.setBackground(Theme.listRow(getContext()));
                PakLibrary.Pak p = getItem(position);
                TextView t = Theme.body(getContext(), p.title, Theme.P3, p.needsCoco3 ? DIM : TEXT);
                t.setLineSpacing(0, Theme.HEADING_LINE);
                row.addView(t);
                TextView d = Theme.body(getContext(), p.detail, Theme.P5, p.needsCoco3 ? WARN : DIM);
                d.setLineSpacing(0, Theme.HEADING_LINE);
                row.addView(d);
                return row;
            }
        });
    }

    private void insert(PakLibrary.Pak p) {
        if (p.needsCoco3) {
            new AlertDialog.Builder(getContext())
                    .setTitle(p.title)
                    .setMessage("This is a CoCo 3 program. This app emulates a CoCo 1/2, so it will not run.\n\nInsert it anyway?")
                    .setPositiveButton("Insert anyway", (d, w) -> host.runPak(p.file.getPath(), p.title))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        host.runPak(p.file.getPath(), p.title);
        updateStatus();
    }

    private void confirmDelete(PakLibrary.Pak p) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete this Pak?")
                .setMessage(p.title + "\n" + p.file.getName() + "\n\nThe file is removed from paks/.")
                .setPositiveButton("Delete", (d, w) -> {
                    library.delete(p);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
