package net.wigle.wigleandroid;

import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * An in-app progress panel
 * TODO: since multiple tasks can be in-progress at once, this should eventually become a list
 * Created by arkasha on 5/22/17.
 */

public class ProgressPanel {

    private final LinearLayout container;
    private final TextView progressLabel;
    private final ProgressBar progressBar;
    private final TextView queueLabel;

    /** Base message text without any animated dot suffix. */
    private String baseMessage = "";
    /** Lazily created; only used when system animations are disabled. */
    private Handler dotAnimationHandler;
    private Runnable dotAnimationRunnable;
    private int dotCount;

    private static final long DOT_ANIMATION_INTERVAL_MS = 400L;

    public ProgressPanel(LinearLayout layout, TextView progressLabel, ProgressBar progressBar, TextView queueLabel) {
        this.container = layout;
        this.progressLabel = progressLabel;
        this.progressBar = progressBar;
        this.queueLabel = queueLabel;
    }

    public void show() {
        container.setVisibility(View.VISIBLE);
    }

    public void hide() {
        stopDotAnimation();
        setProgress(0);
        progressBar.setIndeterminate(true);
        container.setVisibility(View.GONE);
    }

    public void setMessage(final String text) {
        baseMessage = (text == null) ? "" : text;
        if (null != progressLabel) {
            progressLabel.setText(buildLabelText());
        }
    }

    public void setProgress(final int progress) {
        // Determinate progress works fine even when system animations are off;
        // tear down any textual fallback and ensure the bar is visible.
        stopDotAnimation();
        if (null != progressBar) {
            progressBar.setVisibility(View.VISIBLE);
        }
        progressBar.setIndeterminate(false);
        progressBar.setProgress(progress);
    }

    public void setIndeterminate() {
        if (null != progressBar) {
            progressBar.setIndeterminate(true);
        }
        // ProgressBar.setIndeterminate(true) renders nothing on API 21-27 when
        // Battery Saver / Reduced Animations is on (animator scale = 0). Hide
        // the bar and animate the existing label as a textual fallback so the
        // user still sees that work is in progress. See WiGLE issue #699.
        if (animatorsDisabled()) {
            if (null != progressBar) progressBar.setVisibility(View.GONE);
            startDotAnimation();
        } else {
            if (null != progressBar) progressBar.setVisibility(View.VISIBLE);
            stopDotAnimation();
        }
    }

    public void hideQueue() {
        queueLabel.setVisibility(View.GONE);
    }

    public void setQueue(final CharSequence text) {
        queueLabel.setVisibility(View.VISIBLE);
        queueLabel.setText(text);
    }

    private boolean animatorsDisabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return !ValueAnimator.areAnimatorsEnabled();
        }
        // API 24/25: no public way to detect animator scale = 0, and the
        // ProgressBar bug is rare on those versions. Preserve original behavior.
        return false;
    }

    private void startDotAnimation() {
        if (null == progressLabel) return;
        if (null == dotAnimationHandler) {
            dotAnimationHandler = new Handler(Looper.getMainLooper());
        }
        if (null != dotAnimationRunnable) return; // already running
        dotAnimationRunnable = new Runnable() {
            @Override public void run() {
                dotCount = (dotCount + 1) % 4;
                progressLabel.setText(buildLabelText());
                if (null != dotAnimationHandler && null != dotAnimationRunnable) {
                    dotAnimationHandler.postDelayed(this, DOT_ANIMATION_INTERVAL_MS);
                }
            }
        };
        dotAnimationHandler.post(dotAnimationRunnable);
    }

    private void stopDotAnimation() {
        if (null == dotAnimationRunnable) return;
        if (null != dotAnimationHandler) {
            dotAnimationHandler.removeCallbacks(dotAnimationRunnable);
        }
        dotAnimationRunnable = null;
        dotCount = 0;
        if (null != progressLabel) {
            progressLabel.setText(buildLabelText());
        }
    }

    private String buildLabelText() {
        if (dotCount <= 0) return baseMessage;
        StringBuilder sb = new StringBuilder(baseMessage.length() + dotCount);
        sb.append(baseMessage);
        for (int i = 0; i < dotCount; i++) sb.append('.');
        return sb.toString();
    }

}
