package net.wigle.wigleandroid.background;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Pair;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;

import java.io.File;

public class BackupRunnable extends ProgressRunnable implements Runnable, AlertSettable{
    private Pair<Boolean,String> dbResult;

    MainActivity mainActivity;
    public BackupRunnable(final FragmentActivity activity, final boolean showProgress, final MainActivity mainActivity) {
        super(activity, showProgress);
        this.mainActivity = mainActivity;
    }

    @Override
    protected void onPreExecute() {
        mainActivity.setTransferring();
    }

    @Override
    protected void onPostExecute(String result) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.transferComplete();

                Logging.info("DB backup postExe");
                final Context c = activity.getApplicationContext();
                final TextView tv = activity.findViewById(R.id.backup_db_text);
                if (tv != null) {
                    tv.setText(activity.getString(R.string.backup_db_text));
                }
                if (null != result) { //launch task will exist with bg thread enqueued with null return
                    if (null != dbResult && dbResult.getFirst()) {
                        // fire share intent
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE Database Backup");
                        intent.setType("application/xsqlite-3");

                        //TODO: verify local-only storage case/gpx_paths.xml
                        if (null == c) {
                            Logging.error("null context in DB backup postExec");
                        } else {
                            final File backupFile = new File(dbResult.getSecond());
                            Logging.info("backupfile: " + backupFile.getAbsolutePath()
                                    + " exists: " + backupFile.exists() + " read: " + backupFile.canRead());
                            final Uri fileUri = FileProvider.getUriForFile(c,
                                    MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                                            ".sqliteprovider", new File(dbResult.getSecond()));

                            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            activity.startActivity(Intent.createChooser(intent, activity.getResources().getText(R.string.send_to)));
                        }
                    } else {
                        Logging.error("null or empty DB result in DB backup postExec");
                        WiGLEToast.showOverFragment(activity, R.string.error_general,
                                activity.getString(R.string.error_general));
                    }
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
        onPreExecute();
        dbResult = ListFragment.lameStatic.dbHelper.copyDatabase(this);
        onPostExecute(dbResult.toString());
    }
}
