package io.github.carloscamacho.xroar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

/* A menu that drops down from a button: BUIK list rows (48 dp, 16 dp insets)
 * with an icon and a label, on a raised box with a 1 dp line.  Replaces the
 * system PopupMenu, which can't show icons and ignores the theme. */
final class PopupList {
    static final class Item {
        final int icon;
        final String label;
        final Runnable action;

        Item(int icon, String label, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.action = action;
        }
    }

    private PopupList() {
    }

    /* The menu's right edge lines up with the anchor's. */
    static void show(Context c, View anchor, List<Item> items) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Theme.popupBackground(c));
        int pad = Theme.dp(c, Theme.LINE_DP);
        box.setPadding(pad, pad, pad, pad);

        final PopupWindow pw = new PopupWindow(box, Theme.dp(c, 260), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new ColorDrawable(0x00000000));   // lets a tap outside close it
        pw.setOutsideTouchable(true);

        for (final Item it : items) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Theme.dp(c, Theme.CONTROL_HEIGHT_DP));
            row.setPadding(Theme.dp(c, Theme.SPACE_S), 0, Theme.dp(c, Theme.SPACE_S), 0);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FF5C70), null,
                    Theme.box(c, 0xFFFFFFFF, 0, 0)));
            row.addView(Theme.iconView(c, it.icon, Theme.TEXT_STRONG, Theme.ICON_SMALL_DP),
                    new LinearLayout.LayoutParams(Theme.dp(c, Theme.ICON_SMALL_DP), Theme.dp(c, Theme.ICON_SMALL_DP)));
            TextView t = Theme.body(c, it.label, Theme.P4, Theme.TEXT_STRONG);
            t.setLineSpacing(0, 1f);
            t.setPadding(Theme.dp(c, Theme.SPACE_S), 0, 0, 0);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> {
                pw.dismiss();
                it.action.run();
            });
            box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        pw.showAsDropDown(anchor, 0, 0, Gravity.END);
    }
}
