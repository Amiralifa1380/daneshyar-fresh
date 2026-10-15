package ir.daneshyar.offline;

import android.content.Context;
import androidx.core.graphics.ColorUtils;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/** Small reusable Material components; no network, web views or remote assets. */
public final class UiParts {
    private UiParts() {}
    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    private static int legible(Context context,int color){
        boolean dark=(context.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return dark?ColorUtils.blendARGB(color,Color.WHITE,0.40f):color;
    }
    public static TextView badge(Context context, String label, int color) {
        color=legible(context,color);
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(12);
        view.setTextColor(color);
        view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(22, Color.red(color), Color.green(color), Color.blue(color)));
        background.setCornerRadius(dp(context, 30));
        view.setBackground(background);
        return view;
    }
    public static MaterialCardView metric(Context context, String value, String label, int color) {
        color=legible(context,color);
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(context, 20));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(MaterialColors.getColor(context,com.google.android.material.R.attr.colorSurfaceVariant,Color.LTGRAY));
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(context, 8), dp(context, 17), dp(context, 8), dp(context, 17));
        TextView number = new TextView(context);
        number.setText(value);
        number.setTextSize(24);
        number.setTextColor(color);
        number.setTypeface(null, Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        TextView caption = new TextView(context);
        caption.setText(label);
        caption.setTextSize(12);
        caption.setTextColor(MaterialColors.getColor(context,com.google.android.material.R.attr.colorOnSurfaceVariant,Color.GRAY));
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(context, 6), 0, 0);
        layout.addView(number);
        layout.addView(caption);
        card.addView(layout);
        return card;
    }
    public static LinearProgressIndicator progress(Context context, int percentage, int color) {
        LinearProgressIndicator indicator = new LinearProgressIndicator(context);
        indicator.setIndicatorColor(color);
        indicator.setTrackColor(MaterialColors.getColor(context,com.google.android.material.R.attr.colorSurfaceVariant,Color.LTGRAY));
        indicator.setTrackThickness(dp(context, 6));
        indicator.setTrackCornerRadius(dp(context, 3));
        indicator.setProgress(Math.max(0, Math.min(100, percentage)));
        return indicator;
    }
}
