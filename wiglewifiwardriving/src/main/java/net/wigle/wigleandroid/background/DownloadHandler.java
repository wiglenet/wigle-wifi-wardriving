package net.wigle.wigleandroid.background;

import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import java.text.NumberFormat;

/**
 * Created by bobzilla on 7/3/16
 */
public abstract class DownloadHandler extends Handler {
    protected final View view;
    protected final NumberFormat numberFormat;
    protected final String packageName;
    protected final Resources resources;

    public DownloadHandler(final View view, final NumberFormat numberFormat, final String packageName,
                            final Resources resources) {
        this.view = view;
        this.numberFormat = numberFormat;
        this.packageName = packageName;
        this.resources = resources;
    }

    @Override
    abstract public void handleMessage(final Message msg);
}