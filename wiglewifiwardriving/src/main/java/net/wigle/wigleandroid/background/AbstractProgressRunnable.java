package net.wigle.wigleandroid.background;

import android.os.Bundle;
import android.os.Message;

import androidx.fragment.app.FragmentActivity;

public abstract class AbstractProgressRunnable implements AlertSettable{
    protected final UniqueTaskExecutorService executorService;
    protected final FragmentActivity activity;
    protected int lastSentPercent = -1;
    protected int lastTaskQueueDepth = -1;

    public AbstractProgressRunnable(UniqueTaskExecutorService executorService, FragmentActivity fragmentActivity) {
        this.executorService = executorService;
        this.activity = fragmentActivity;
    }

    @Override
    public abstract void clearProgressDialog();
    protected abstract  void setProgressStatus(final int id);
    protected abstract void onProgressUpdate(Integer percent);
    protected abstract void setProgressIndeterminate();
    protected abstract void onPreExecute();
    protected abstract void onPostExecute(final String result);
    protected abstract void reactivateProgressBar(final int id);
}
