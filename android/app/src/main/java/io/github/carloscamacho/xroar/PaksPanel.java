package io.github.carloscamacho.xroar;

import android.app.AlertDialog;
import android.graphics.Typeface;
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
        TextView title = new TextView(host);
        title.setText("Paks");
        title.setTextColor(TEXT);
        title.setTextSize(22);
        title.setPadding(dp(14), dp(6), dp(8), dp(6));
        header.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button eject = flat("Eject");
        eject.setOnClickListener(v -> {
            host.ejectPak();
            updateStatus();
        });
        header.addView(eject);
        Button add = flat("Add…");
        add.setOnClickListener(v -> host.pickPaks());
        header.addView(add);
        Button close = flat("Close");
        close.setOnClickListener(v -> host.closePaks());
        header.addView(close);
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        search = new EditText(host);
        search.setHint("Search");
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(DIM);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { applyFilter(); }
        });
        LinearLayout.LayoutParams sp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(dp(12), 0, dp(12), 0);
        addView(search, sp);

        status = new TextView(host);
        status.setTextColor(DIM);
        status.setTextSize(12);
        status.setPadding(dp(14), dp(4), dp(14), dp(4));
        addView(status);

        list = new ListView(host);
        list.setDividerHeight(0);
        addView(list, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        list.setOnItemClickListener((AdapterView<?> p, View v, int pos, long id) -> insert(shown.get(pos)));
        list.setOnItemLongClickListener((AdapterView<?> p, View v, int pos, long id) -> {
            confirmDelete(shown.get(pos));
            return true;
        });

        countView = new TextView(host);
        countView.setTextColor(DIM);
        countView.setTextSize(12);
        countView.setPadding(dp(14), dp(4), dp(14), dp(8));
        addView(countView);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Button flat(String label) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Theme.PRIMARY);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setBackgroundColor(0x00000000);
        return b;
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
                row.setPadding(dp(14), dp(8), dp(14), dp(8));
                PakLibrary.Pak p = getItem(position);
                TextView t = new TextView(getContext());
                t.setText(p.title);
                t.setTextColor(p.needsCoco3 ? DIM : TEXT);
                t.setTextSize(15);
                row.addView(t);
                TextView d = new TextView(getContext());
                d.setText(p.detail);
                d.setTextColor(p.needsCoco3 ? WARN : DIM);
                d.setTextSize(12);
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
