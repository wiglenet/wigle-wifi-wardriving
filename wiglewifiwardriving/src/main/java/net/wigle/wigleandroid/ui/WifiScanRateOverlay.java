package net.wigle.wigleandroid.ui;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.util.WifiScanMinute;
import net.wigle.wigleandroid.util.WifiScanRate;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Live, translucent view of the most recent WiFi scan-rate minutes drawn over the activity content.
 * The view is neither clickable nor focusable, so touches fall through to the app beneath it.
 * Main thread only.
 */
public final class WifiScanRateOverlay {
    private static final long REFRESH_MS = 1000L;
    private static final int ROWS = 20;
    private static final String LEGEND =
            "S=scan F=fg G=svc-fg O=screen I=idle B=batt-unrestricted T=throttle Y=in-flight\n"
                    + "rj=total/bg (bg: importance>125)  sk=in-flight/held  pr=idle probes/accepted  imp=max importance at reject";

    private final MainActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, REFRESH_MS);
        }
    };
    private TextView view;

    public WifiScanRateOverlay(final MainActivity activity) {
        this.activity = activity;
    }

    public boolean isShowing() {
        return view != null;
    }

    public void show() {
        if (view != null) {
            return;
        }
        final ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        final TextView text = new TextView(activity);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        final int nightMode = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        text.setTextColor(nightMode == Configuration.UI_MODE_NIGHT_YES ? Color.YELLOW : Color.WHITE);
        text.setShadowLayer(2f, 0f, 0f, Color.BLACK);
        text.setBackgroundColor(0x66000000);
        final int pad = dp(4);
        text.setPadding(pad, pad, pad, pad);
        text.setClickable(false);
        text.setLongClickable(false);
        text.setFocusable(false);
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        final int baseBottom = dp(56);
        lp.bottomMargin = baseBottom;
        ViewCompat.setOnApplyWindowInsetsListener(text, (v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            final ViewGroup.LayoutParams p = v.getLayoutParams();
            if (p instanceof ViewGroup.MarginLayoutParams) {
                final ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) p;
                if (m.bottomMargin != baseBottom + bars.bottom) {
                    m.bottomMargin = baseBottom + bars.bottom;
                    v.setLayoutParams(m);
                }
            }
            return insets;
        });
        content.addView(text, lp);
        ViewCompat.requestApplyInsets(text);
        view = text;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    public void hide() {
        handler.removeCallbacks(refresh);
        if (view == null) {
            return;
        }
        final ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            parent.removeView(view);
        }
        view = null;
    }

    private void render() {
        if (view == null) {
            return;
        }
        final MainActivity.State state = activity.getState();
        final WifiScanRate rate = state == null ? null : state.wifiScanRate;
        if (rate == null) {
            view.setText(activity.getString(R.string.wifi_scan_rate_empty));
            return;
        }
        final WifiScanRate.Sample sample = activity.captureWifiScanSample();
        final StringBuilder text = new StringBuilder(ROWS * 80 + 160);
        text.append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("  SDK ").append(Build.VERSION.SDK_INT)
                .append("  up ").append(WifiScanRate.formatUptime(
                        SystemClock.elapsedRealtime() - rate.getProcessStartElapsedMs()))
                .append('\n').append(LEGEND);
        final List<WifiScanMinute> minutes = rate.recent(ROWS, sample);
        for (final WifiScanMinute minute : minutes) {
            text.append('\n').append(minute.formatCompactLine(timeFormat));
        }
        view.setText(text);
    }

    private int dp(final int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                activity.getResources().getDisplayMetrics()));
    }
}
