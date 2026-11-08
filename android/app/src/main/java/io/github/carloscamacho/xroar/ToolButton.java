package io.github.carloscamacho.xroar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/* A toolbar or tab button: an icon with its label underneath.  Tinted with
 * the current theme's text color, or its red when "active" (a toggle that is
 * on, the open tab). */
final class ToolButton extends LinearLayout {
    private final ImageView icon;
    private final TextView label;
    private final int iconRes;
    private final int iconDp;
    private boolean active;

    ToolButton(Context c, int iconRes, String text, int iconDp) {
        super(c);
        this.iconRes = iconRes;
        this.iconDp = iconDp;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setClickable(true);
        setFocusable(true);
        setContentDescription(text);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FF5C70), null,
                Theme.box(c, 0xFFFFFFFF, 0, 0)));
        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(icon, new LayoutParams(Theme.dp(c, iconDp), Theme.dp(c, iconDp)));
        label = new TextView(c);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.P5);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(c, Theme.SPACE_XXS / 2);
        addView(label, lp);
        setActive(false);
    }

    /* Show or hide the label (an icon-only button keeps its content description). */
    void setLabelShown(boolean on) {
        label.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    /* Re-tint after the theme changes. */
    void refreshTheme() {
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FF5C70), null,
                Theme.box(getContext(), 0xFFFFFFFF, 0, 0)));
        setActive(active);
    }

    void setActive(boolean on) {
        active = on;
        int color = on ? Theme.PRIMARY : Theme.TEXT_STRONG;
        icon.setImageDrawable(Theme.icon(getContext(), iconRes, color, iconDp));
        label.setTextColor(color);
        label.setTypeface(on ? Theme.heavyFace(getContext()) : Theme.bodyFace(getContext()));
    }
}
