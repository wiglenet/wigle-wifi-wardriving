package net.wigle.wigleandroid;

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
        setProgress(0);
        progressBar.setIndeterminate(true);
        container.setVisibility(View.GONE);
    }

    public void setMessage(final String text) {
        if (null != progressLabel) progressLabel.setText(text);
    }

    public void setProgress(final int progress) {
        progressBar.setIndeterminate(false);
        progressBar.setProgress(progress);
    }

    public void setIndeterminate() {
        if (null != progressBar) {
            progressBar.setIndeterminate(true);
        }
    }

    public void hideQueue() {
        queueLabel.setVisibility(View.GONE);
    }

    public void setQueue(final CharSequence text) {
        queueLabel.setVisibility(View.VISIBLE);
        queueLabel.setText(text);
    }

}
