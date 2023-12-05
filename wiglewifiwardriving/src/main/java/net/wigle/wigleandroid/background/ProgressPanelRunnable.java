package net.wigle.wigleandroid.background;

import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.ProgressPanel;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.util.Logging;

public abstract class ProgressPanelRunnable extends AbstractProgressRunnable implements AlertSettable {
    protected ProgressPanel pp;
    protected final Object lock = new Object();
    protected final BackgroundGuiHandler handler;

    protected ProgressPanelRunnable(final FragmentActivity activity, final UniqueTaskExecutorService executorService, final boolean showProgress) {
        super(executorService, activity);
        if (showProgress) activateProgressPanel(activity);
        this.handler = new BackgroundGuiHandler(activity, lock, pp, this);
    }
    protected void activateProgressPanel(final FragmentActivity context) {
        final LinearLayout progressLayout = context.findViewById(R.id.inline_status_bar);
        //ALIBI: test and avoid re-init if panel is already showing.
        if (null == progressLayout || VISIBLE != progressLayout.getVisibility()) {
            final TextView progressLabel = context.findViewById(R.id.inline_progress_status);
            final ProgressBar progressBar = context.findViewById(R.id.inline_status_progress);
            final TextView queueLabel = context.findViewById(R.id.inline_progress_queue_status);
            if ((null != progressLayout) && (null != progressLabel) && (null != progressBar)) {
                pp = new ProgressPanel(progressLayout, progressLabel, progressBar, queueLabel);
                pp.show();
                final Button taskCancelButton = context.findViewById(R.id.inline_status_cancel);
                taskCancelButton.setVisibility(View.GONE);
                //taskCancelButton.setOnClickListener(v -> {
                // TODO: make these subclasses cancellable, link to cancel button
                //  });
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
    }

    @Override
    public final void clearProgressDialog() {
        if (null != pp && (executorService == null || executorService.getQueue().size() == 0) ) {
            pp.hide();
            pp = null;
        }
    }

    @Override
    protected void onProgressUpdate(Integer percent) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //DEBUG: Logging.info("progress: "+percent);
                if (100 == percent) {
                    if (null != pp && (executorService == null || executorService.getQueue().size() == 0) ) {
                        pp.hide();
                    }
                    return;
                }
                if (percent > lastSentPercent) {
                    pp.setProgress(percent);
                    lastSentPercent = percent;
                }
                if (null != executorService) {
                    final int curSize = executorService.getQueue().size();
                    if (curSize != lastTaskQueueDepth) {
                        if (curSize == 0) {
                            pp.hideQueue();
                        } else {
                            pp.setQueue(activity.getString(R.string.queued_jobs, curSize));
                        }
                        lastTaskQueueDepth = executorService.getQueue().size();
                    }
                }
            }
        });
    }

    @Override
    protected void setProgressStatus(final int id) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pp != null) {
                    pp.show();
                    pp.setMessage(activity.getString(id));
                } else {
                    Logging.error("Null panel on setProgressStatus");
                }
            }});
    }

    //TODO: do we ever need this?
    protected final void sendBundledMessage(final int what, final Bundle bundle) {
        final Message msg = new Message();
        msg.what = what;
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    @Override
    protected void reactivateProgressBar(final int id) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pp.show();
                setProgressStatus(id);
                pp.setIndeterminate();
            }});
    }

    @Override
    protected void setProgressIndeterminate() {
        pp.setIndeterminate();
    }
}
