package net.wigle.wigleandroid.background;

import android.os.Bundle;
import android.os.Message;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.ProgressPanel;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.util.Logging;

public abstract class ProgressRunnable implements AlertSettable {
    protected ProgressPanel pp;
    protected final Object lock = new Object();
    protected final BackgroundGuiHandler handler;
    protected final FragmentActivity activity;
    private int lastSentPercent = -1;

    protected ProgressRunnable(final FragmentActivity activity, final boolean showProgress) {
        this.activity = activity;
        if (showProgress) activateProgressPanel(activity);
        this.handler = new BackgroundGuiHandler(activity, lock, pp, this);
    }
    protected void activateProgressPanel(final FragmentActivity context) {
        final LinearLayout progressLayout = context.findViewById(R.id.inline_status_bar);
        final TextView progressLabel = context.findViewById(R.id.inline_progress_status);
        final ProgressBar progressBar = context.findViewById(R.id.inline_status_progress);

        if ((null != progressLayout) && (null != progressLabel) && (null != progressBar)) {
            pp = new ProgressPanel(progressLayout, progressLabel, progressBar);
            pp.show();
            final Button taskCancelButton = context.findViewById(R.id.inline_status_cancel);
            taskCancelButton.setOnClickListener(v -> {

                //TODO: uhg, now we need a task queue
                //if (null != latestTask) {
                //    latestTask.setInterrupted();
                //}
                clearProgressDialog();
                //updateTransferringState(false, uploadButton, importObservedButton);
            });
            //ALIBI: this will get replaced as soon as the progress is set for the first time
            progressBar.setIndeterminate(true);

            //ALIBI: prevent multiple simultaneous large transfers by disabling visible buttons,
            // setting global state to make sure they get set on show
            //updateTransferringState(true, uploadButton, importObservedButton);
            pp.setMessage(context.getString(R.string.status_working));
            pp.setIndeterminate();
        } else {
            Logging.warn("Progress panel is null");
        }
    }

    @Override
    public final void clearProgressDialog() {
        if (null != pp) {
            pp.hide();
        }
        pp = null;
    }

    //TODO: do we ever need this?
    protected final void sendBundledMessage(final int what, final Bundle bundle) {
        final Message msg = new Message();
        msg.what = what;
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    protected void onProgressUpdate(Integer percent) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //DEBUG: Logging.info("progress: "+percent);
                if (100 == percent) {
                    pp.hide();
                    return;
                }
                if (percent > lastSentPercent) {
                    pp.setProgress(percent);
                    lastSentPercent = percent;
                }
            }
        });
    }

    protected void setProgressStatus(final int id) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pp.show();
                pp.setMessage(activity.getString(id));
                pp.setIndeterminate();
            }});
    }

    protected abstract void onPreExecute();
    protected abstract void onPostExecute(final String result);

}
