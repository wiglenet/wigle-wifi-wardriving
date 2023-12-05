package net.wigle.wigleandroid.background;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.WindowManager;

import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Pair;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;

import java.io.File;

public class BackupRunnable extends ProgressPanelRunnable implements Runnable, AlertSettable{
    private Pair<Boolean,String> dbResult;

    MainActivity mainActivity;
    public BackupRunnable(final FragmentActivity activity, final UniqueTaskExecutorService executorService, final boolean showProgress, final MainActivity mainActivity) {
        super(activity, executorService, showProgress);
        this.mainActivity = mainActivity;
    }

    @Override
    protected void onPreExecute() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //ALIBI: Android like killing long-running tasks like this if you let the screen shut off
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setProgressStatus(R.string.backup_preparing);
                mainActivity.setTransferring();
            }
        });
    }

    @Override
    protected void onPostExecute(String result) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                mainActivity.transferComplete();
                final Context c = activity.getApplicationContext();
                if (null != result) {
                    if (null != dbResult && dbResult.getFirst()) {
                        // fire share intent
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE Database Backup");
                        intent.setType("application/xsqlite-3");

                        if (null == c) {
                            Logging.error("null context in DB backup postExec");
                        } else {
                            MainActivity ma = MainActivity.getMainActivity();
                            if (null != ma) {
                                final File backupFile = new File(dbResult.getSecond());
                                Logging.info("backupfile: " + backupFile.getAbsolutePath()
                                        + " exists: " + backupFile.exists() + " read: " + backupFile.canRead());
                                final Uri fileUri = FileProvider.getUriForFile(c,
                                        ma.getApplicationContext().getPackageName() +
                                                ".sqliteprovider", new File(dbResult.getSecond()));

                                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                                //intent.setClipData(ClipData.newRawUri("", fileUri));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                activity.startActivity(Intent.createChooser(intent, activity.getResources().getText(R.string.send_to)));
                            } else {
                                Logging.error("null MainActivity DB backup postExec");
                            }
                        }
                    } else {
                        Logging.error("null or empty DB result in DB backup postExec");
                        WiGLEToast.showOverFragment(activity, R.string.error_general,
                                activity.getString(R.string.error_general));
                    }
                } else {
                    Logging.error("Null result in postExecute - unable to share sqlite backup.");
                }
            }
        });
    }

    public void setProgress(final int progress) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onProgressUpdate(progress);
            }
        });
    }

    @Override
    public void run() {
        String result = null;
        onPreExecute();
        try {
            setProgressStatus(R.string.backup_in_progress);
            dbResult = ListFragment.lameStatic.dbHelper.copyDatabase(this);
            result = dbResult.toString();
        } catch (Exception e) {
            Logging.error("Failed to backup SQLite DB: ",e);
            result = "ERROR";
        } finally {
            onPostExecute(result);
        }
    }
}
