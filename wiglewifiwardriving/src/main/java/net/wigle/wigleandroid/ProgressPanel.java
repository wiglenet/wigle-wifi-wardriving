package net.wigle.wigleandroid;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.w3c.dom.Text;

/**
 * Created by arkasha on 5/22/17.
 */

public class ProgressPanel {

    private final LinearLayout container;
    private final TextView label;
    private final ProgressBar progressBar;

    public ProgressPanel(LinearLayout layout, TextView label, ProgressBar progressBar) {
        this.container = layout;
        this.label = label;
        this.progressBar = progressBar;
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
        if (null != label) label.setText(text);
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
}
