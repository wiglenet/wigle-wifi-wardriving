package net.wigle.wigleandroid;

//import net.wigle.m8b;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.ObservationImporter;
import net.wigle.wigleandroid.background.ObservationUploader;
import net.wigle.wigleandroid.background.QueryThread;
import net.wigle.wigleandroid.background.TransferListener;
import net.wigle.wigleandroid.background.KmlWriter;
import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.Pair;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.SearchUtil;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.FileProvider;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;


/**
 * configure settings
 */
public final class DataFragment extends Fragment implements ApiListener, TransferListener, DialogListener {

    private static final int MENU_ERROR_REPORT = 13;

    private static final int CSV_RUN_DIALOG = 120;
    private static final int CSV_DB_DIALOG = 121;
    private static final int KML_RUN_DIALOG = 122;
    private static final int KML_DB_DIALOG = 123;
    private static final int BACKUP_DIALOG = 124;
    private static final int IMPORT_DIALOG = 125;
    private static final int ZERO_OUT_DIALOG = 126;
    private static final int MAX_OUT_DIALOG = 127;
    private static final int DELETE_DIALOG = 128;
    private static final int EXPORT_M8B_DIALOG = 129;

    private static final String M8B_SEP = "|";
    private static final String M8B_SOURCE_FILE_PREFIX = "export";
    private static final String M8B_SOURCE_FILE_SUFFIX = "m8bs";

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setHasOptionsMenu(true);
        // set language
        MainActivity.setLocale( getActivity() );

        // force media volume controls
        getActivity().setVolumeControlStream( AudioManager.STREAM_MUSIC );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.data, container, false);

        setupQueryButtons( view );
        setupCsvButtons( view );
        setupKmlButtons(view);
        setupBackupDbButton(view);
        setupImportObservedButton(view);
        setupMarkerButtons(view);
        setupM8bExport(view);

        return view;
    }

    private void setupQueryButtons( final View view ) {
        Button button = (Button) view.findViewById( R.id.search_button );
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View buttonView) {

                final String fail = SearchUtil.setupQuery(view, getActivity(), true);
                if (null != ListFragment.lameStatic.queryArgs) {
                    ListFragment.lameStatic.queryArgs.setSearchWiGLE(false);
                }
                if (fail != null) {
                    // toast!
                    WiGLEToast.showOverFragment(getActivity(), R.string.error_general, fail);
                } else {
                    // start db result activity
                    final Intent settingsIntent = new Intent(getActivity(), DBResultActivity.class);
                    startActivity(settingsIntent);
                }
            }
        });

        button = (Button) view.findViewById( R.id.reset_button );
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View buttonView) {
                SearchUtil.clearWiFiBtFields(view);
            }
        });

    }

    /**
     * TransferListener interface
     */
    @Override
    public void requestComplete(final JSONObject json, final boolean isCache)
            throws WiGLEAuthException {
        // nothing
    }

    @Override
    public void transferComplete() {
        // also nothing
    }

    private void setupCsvButtons( final View view ) {
        // actually need this Activity context, for dialogs

        final Button csvRunExportButton = (Button) view.findViewById( R.id.csv_run_export_button );
        csvRunExportButton.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick( final View buttonView ) {
                MainActivity.createConfirmation( getActivity(),
                        DataFragment.this.getString(R.string.data_export_csv), R.id.nav_data, CSV_RUN_DIALOG);
            }
        });

        final Button csvExportButton = (Button) view.findViewById( R.id.csv_export_button );
        csvExportButton.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick( final View buttonView ) {
                MainActivity.createConfirmation( getActivity(),
                        DataFragment.this.getString(R.string.data_export_csv_db), R.id.nav_data, CSV_DB_DIALOG);
            }
        });
    }

    private void setupKmlButtons( final View view ) {
        final Button kmlRunExportButton = (Button) view.findViewById( R.id.kml_run_export_button );
        kmlRunExportButton.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick( final View buttonView ) {
                MainActivity.createConfirmation( getActivity(),
                        DataFragment.this.getString(R.string.data_export_kml_run), R.id.nav_data, KML_RUN_DIALOG);
            }
        });

        final Button kmlExportButton = (Button) view.findViewById( R.id.kml_export_button );
        kmlExportButton.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick( final View buttonView ) {
                MainActivity.createConfirmation( getActivity(),
                        DataFragment.this.getString(R.string.data_export_kml_db), R.id.nav_data, KML_DB_DIALOG);
            }
        });
    }

    private void setupBackupDbButton( final View view ) {
        final Button kmlExportButton = (Button) view.findViewById( R.id.backup_db_button );
        if ( ! MainActivity.hasSD() ) {
            kmlExportButton.setEnabled(false);
        }

        kmlExportButton.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick( final View buttonView ) {
                MainActivity.createConfirmation( getActivity(),
                        DataFragment.this.getString(R.string.data_backup_db), R.id.nav_data, BACKUP_DIALOG);
            }
        });
    }

    private void setupImportObservedButton( final View view ) {
        final Button importObservedButton = (Button) view.findViewById( R.id.import_observed_button );
        final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final String authname = prefs.getString(ListFragment.PREF_AUTHNAME, null);

        if (null == authname) {
            importObservedButton.setEnabled(false);
        } else if (MainActivity.getMainActivity().isTransferring()) {
                importObservedButton.setEnabled(false);
        }
        importObservedButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View buttonView) {
                MainActivity.createConfirmation(getActivity(),
                        DataFragment.this.getString(R.string.data_import_observed),
                        R.id.nav_data, IMPORT_DIALOG);
            }
        });
    }

    private void createAndStartImport() {
        final MainActivity mainActivity = MainActivity.getMainActivity(DataFragment.this);
        if (mainActivity != null) {
            mainActivity.setTransferring();
        }

        // actually need this Activity context, for dialogs
        if (Build.VERSION.SDK_INT >= 11) {

            final ObservationImporter task = new ObservationImporter(getActivity(),
                    ListFragment.lameStatic.dbHelper,
                    new ApiListener() {
                        @Override
                        public void requestComplete(JSONObject object, boolean cached) {
                            if (mainActivity != null) {
                                try {
                                    mainActivity.getState().dbHelper.getNetworkCountFromDB();
                                } catch (DBException dbe) {
                                    MainActivity.warn("failed DB count update on import-observations", dbe);
                                }
                                mainActivity.transferComplete();
                            }
                        }
                    });
            try {
                task.startDownload(this);
            } catch (WiGLEAuthException waex) {
                MainActivity.info("failed to authorize user on request");
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void setupMarkerButtons( final View view ) {
        final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);

        // db marker reset button and text
        final TextView tv = (TextView) view.findViewById(R.id.reset_maxid_text);
        tv.setText( getString(R.string.setting_high_up) + " " + prefs.getLong( ListFragment.PREF_DB_MARKER, 0L ) );

        final Button resetMaxidButton = (Button) view.findViewById(R.id.reset_maxid_button);
        resetMaxidButton.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick( final View buttonView ) {
                MainActivity.createConfirmation( getActivity(), getString(R.string.setting_zero_out),
                        R.id.nav_data, ZERO_OUT_DIALOG);
            }
        });

        // db marker maxout button and text
        final TextView maxtv = (TextView) view.findViewById(R.id.maxout_maxid_text);
        final long maxDB = prefs.getLong( ListFragment.PREF_MAX_DB, 0L );
        maxtv.setText( getString(R.string.setting_max_start) + " " + maxDB );

        final Button maxoutMaxidButton = (Button) view.findViewById(R.id.maxout_maxid_button);
        maxoutMaxidButton.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick( final View buttonView ) {
                MainActivity.createConfirmation( getActivity(), getString(R.string.setting_max_out),
                        R.id.nav_data, MAX_OUT_DIALOG);
            }
        } );

        //ALIBI: not technically a marker button, but clearly belongs with them visually/logically
        final Button deleteDbButton = (Button) view.findViewById(R.id.clear_db);
        deleteDbButton.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick( final View buttonView ) {
                MainActivity.createConfirmation( getActivity(), getString(R.string.delete_db_confirm),
                        R.id.nav_data, DELETE_DIALOG);
            }
        } );

    }

    public void setupM8bExport( final View view ) {
        final Button exportM8bButton = (Button) view.findViewById(R.id.export_m8b_button);
        exportM8bButton.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick(final View buttonView) {
                MainActivity.createConfirmation( getActivity(), getString(R.string.export_m8b_detail),
                        R.id.nav_data, EXPORT_M8B_DIALOG);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void handleDialog(final int dialogId) {
        final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final SharedPreferences.Editor editor = prefs.edit();
        final View view = getView();

        switch (dialogId) {
            case CSV_RUN_DIALOG: {
                // actually need this Activity context, for dialogs
                ObservationUploader observationUploader = new ObservationUploader(getActivity(),
                        ListFragment.lameStatic.dbHelper, DataFragment.this, true, false, true);
                observationUploader.start();
                break;
            }
            case CSV_DB_DIALOG: {
                ObservationUploader observationUploader = new ObservationUploader(getActivity(),
                        ListFragment.lameStatic.dbHelper, DataFragment.this, true, true, false);
                observationUploader.start();
                break;
            }
            case KML_RUN_DIALOG: {
                KmlWriter kmlWriter = new KmlWriter( getActivity(), ListFragment.lameStatic.dbHelper,
                        ListFragment.lameStatic.runNetworks );
                kmlWriter.start();
                break;
            }
            case KML_DB_DIALOG: {
                KmlWriter kmlWriter = new KmlWriter( getActivity(), ListFragment.lameStatic.dbHelper );
                kmlWriter.start();
                break;
            }
            case BACKUP_DIALOG: {
                BackupTask task = new BackupTask(DataFragment.this, MainActivity.getMainActivity(DataFragment.this));
                task.execute();
                break;
            }
            case IMPORT_DIALOG: {
                this.createAndStartImport();
                break;
            }
            case ZERO_OUT_DIALOG: {
                editor.putLong( ListFragment.PREF_DB_MARKER, 0L );
                editor.apply();
                if (view != null) {
                    final TextView tv = (TextView) view.findViewById(R.id.reset_maxid_text);
                    tv.setText(getString(R.string.setting_max_id) + " 0");
                }
                break;
            }
            case MAX_OUT_DIALOG: {
                final long maxDB = prefs.getLong( ListFragment.PREF_MAX_DB, 0L );
                editor.putLong( ListFragment.PREF_DB_MARKER, maxDB );
                editor.apply();
                if (view != null) {
                    // set the text on the other button
                    final TextView tv = (TextView) view.findViewById(R.id.reset_maxid_text);
                    tv.setText(getString(R.string.setting_max_id) + " " + maxDB);
                }
                break;
            }
            case DELETE_DIALOG: {
                //blow away the DB
                ListFragment.lameStatic.dbHelper.clearDatabase();
                //update markers
                editor.putLong( ListFragment.PREF_DB_MARKER, 0L );
                editor.putLong( ListFragment.PREF_DB_MARKER, 0L );
                editor.apply();
                if (view != null) {
                    final TextView tv = (TextView) view.findViewById(R.id.reset_maxid_text);
                    tv.setText(getString(R.string.setting_max_id) + " " + 0L);
                }
                try {
                    ListFragment.lameStatic.dbHelper.getNetworkCountFromDB();
                } catch (DBException dbe) {
                    MainActivity.warn("Failed to update network count on DB clear: ", dbe);
                }

                break;
            }
            case EXPORT_M8B_DIALOG: {
                if (!exportM8bFile()) {
                    MainActivity.warn("Failed to export m8b.");
                    WiGLEToast.showOverFragment(getActivity(), R.string.error_general,
                            getString(R.string.m8b_failed));
                }
                break;
            }
            default:
                MainActivity.warn("Data unhandled dialogId: " + dialogId);
        }
    }

    /**
     * way to background load the data and show progress on the gui thread
     */
    public static class BackupTask extends AsyncTask<Object, Integer, Integer> {
        private final Fragment fragment;
        private final MainActivity mainActivity;
        private Pair<Boolean,String> dbResult;

        public BackupTask ( final Fragment fragment, final MainActivity mainActivity ) {
            this.fragment = fragment;
            this.mainActivity = mainActivity;
            mainActivity.setTransferring();
        }

        @Override
        protected Integer doInBackground( Object... obj ) {
            dbResult = ListFragment.lameStatic.dbHelper.copyDatabase(this);
            // dbResult = new Pair<Boolean,String>(Boolean.TRUE, "meh");
            return 0;
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onProgressUpdate( Integer... progress ) {
            final View view = fragment.getView();
            if (view != null) {
                final TextView tv = (TextView) view.findViewById( R.id.backup_db_text );
                if (tv != null) {
                    tv.setText( mainActivity.getString(R.string.backup_db_text) + "\n" + progress[0] + "%" );
                }
            }
        }

        @Override
        protected void onPostExecute( Integer result ) {
            mainActivity.transferComplete();

            final View view = fragment.getView();
            if (view != null) {
                final TextView tv = (TextView) view.findViewById( R.id.backup_db_text );
                if (tv != null) {
                    tv.setText( mainActivity.getString(R.string.backup_db_text) );
                }
            }

            final BackupDialog dialog = BackupDialog.newInstance(dbResult.getFirst(), dbResult.getSecond());
            final FragmentManager fm = mainActivity.getSupportFragmentManager();
            try {
                dialog.show(fm, "backup-dialog");
            }
            catch (final IllegalStateException ex) {
                MainActivity.error("Error showing backup dialog: " + ex, ex);
            }
        }

        public void progress( int progress ) {
            publishProgress(progress);
        }

        public static class BackupDialog extends DialogFragment {
            public static BackupDialog newInstance(final boolean status, final String message) {
                final BackupDialog frag = new BackupDialog();
                final Bundle args = new Bundle();
                args.putBoolean("status", status);
                args.putString("message", message);
                frag.setArguments(args);
                return frag;
            }

            @NonNull
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                final FragmentActivity activity = getActivity();
                final AlertDialog.Builder builder = new AlertDialog.Builder( activity );

                builder.setCancelable( true );
                final Bundle bundle = getArguments();
                builder.setTitle( activity.getString( bundle.getBoolean("status") ? R.string.status_success : R.string.status_fail ));
                builder.setMessage( bundle.getString("message") );
                final AlertDialog ad = builder.create();
                // ok
                ad.setButton( DialogInterface.BUTTON_POSITIVE, activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick( final DialogInterface dialog, final int which ) {
                        try {
                            dialog.dismiss();
                        }
                        catch ( Exception ex ) {
                            // guess it wasn't there anyways
                            MainActivity.info( "exception dismissing alert dialog: " + ex );
                        }
                    } });

                return ad;
            }
        }
    }

    @Override
    public void onResume() {
        MainActivity.info( "resume data." );
        super.onResume();
        getActivity().setTitle(R.string.data_activity_name);
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater) {
        // MenuItem item = menu.add( 0, MENU_SETTINGS, 0, getString(R.string.menu_settings) );
        // item.setIcon( android.R.drawable.ic_menu_preferences );

        MenuItem item = menu.add( 0, MENU_ERROR_REPORT, 0, getString(R.string.menu_error_report) );
        item.setIcon( android.R.drawable.ic_menu_report_image );

        // item = menu.add( 0, MENU_EXIT, 0, getString(R.string.menu_exit) );
        // item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );

        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        switch ( item.getItemId() ) {
            case MENU_ERROR_REPORT:
                final Intent errorReportIntent = new Intent( getActivity(), ErrorReportActivity.class );
                startActivity( errorReportIntent );
                break;
        }
        return false;
    }

    /**
     * Query DB for pairs, generate source file.
     * TODO: process intermediate file, share that instead of the source file.
     * @return boolean successful
     */
    private boolean exportM8bFile() {
        final String sql = "SELECT bssid, bestlat, bestlon FROM " + DatabaseHelper.NETWORK_TABLE
                + " WHERE bestlat != 0.0 AND bestlon != 0.0 ";
        //ALIBI: Sqlite types are dynamic, so usual warnings about doubles and zero == should be moot

        final boolean hasSD = MainActivity.hasSD();
        // TODO: there's a real case for refusing to export on devices that don't have SD, but we can dig in on this

        // use temp directory to write intermediate file
        //final File outputDir = getActivity().getApplicationContext().getCacheDir(); // context being the Activity pointer

        File m8bSourceFile = null;
        OutputStreamWriter writer;
        //FileWriter writer; - for temp intermediate

        try {

            if ( hasSD ) {
                final String filepath = MainActivity.safeFilePath(
                        Environment.getExternalStorageDirectory() ) + "/wiglewifi/m8b/";
                final File path = new File( filepath );
                //noinspection ResultOfMethodCallIgnored
                path.mkdirs();
                if (!path.exists()) {
                    MainActivity.info("Got '!exists': " + path);
                }
                String openString = filepath + M8B_SOURCE_FILE_PREFIX + "." + M8B_SOURCE_FILE_SUFFIX;
                //DEBUG: MainActivity.info("Opening file: " + openString);
                m8bSourceFile = new File( openString );

                //ALIBI: always recreate
                if (m8bSourceFile.exists()) {
                    m8bSourceFile.delete();
                }
                if (!m8bSourceFile.createNewFile()) {
                    throw new IOException("Could not create file: " + openString);
                }
            }

            final FileOutputStream rawFos = hasSD ? new FileOutputStream( m8bSourceFile )
                    : getActivity().getApplicationContext().openFileOutput( M8B_SOURCE_FILE_PREFIX + "." + M8B_SOURCE_FILE_SUFFIX, Context.MODE_PRIVATE );



            //TODO: any reason to version these?
            //m8bSourceFile = File.createTempFile(M8B_SOURCE_FILE_PREFIX, M8B_SOURCE_FILE_SUFFIX, outputDir);
            //writer = new FileWriter(m8bSourceFile);
            writer = new OutputStreamWriter(rawFos);
        } catch (IOException ioex) {
            MainActivity.error("Unable to open tempfile: ", ioex);
            return false;
        }

        if (writer == null) {
            MainActivity.error("unable to build writer for cache.");
            return false;
        }

        final BufferedWriter buffWriter = new BufferedWriter(writer);

        if (!m8bSourceFile.exists()) {
            MainActivity.error("file does not exist: " + m8bSourceFile.getAbsolutePath());
        } else {
            MainActivity.info(m8bSourceFile.getAbsolutePath());
        }
        final Uri fileUri = FileProvider.getUriForFile(getContext(),
                MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                        ".m8bprovider", m8bSourceFile /*TODO: writing temp to export, just for debugging*/);

        // write tempfile
        final QueryThread.Request request = new QueryThread.Request( sql, new QueryThread.ResultHandler() {

            @Override
            public boolean handleRow(final Cursor cursor) {
                final String bssid = cursor.getString(0);
                final float lat = cursor.getFloat(1);
                final float lon = cursor.getFloat(2);
                try {
                    if (null != buffWriter) {
                        buffWriter.write(bssid + M8B_SEP + lat + M8B_SEP + lon + "\n");
                        //DEBUG: MainActivity.info("Line...");
                        return true;
                    } else {
                        MainActivity.error("null writer ");
                        return false;
                    }
                } catch (IOException ioex) {
                    MainActivity.error("Unable to write line: ", ioex);
                    return false;
                }

                //TODO: write to file
            }

            /**
             * once the extract file's been written, run the pipeline, setup and enqueue the intent to share
             */
            @Override
            public void complete() {
                MainActivity.info("m8b source export complete...");
                if (null != buffWriter) {
                    try {
                        buffWriter.close();
                    } catch (IOException ioex) {
                        MainActivity.error("Failed to close m8bTemp writer", ioex);
                    }
                }

                //TODO: run pipeline on intermediate file, write to output file
                //m8b.generate(M8B_SOURCE_FILE_PREFIX+"."+M8B_SOURCE_FILE_SUFFIX,
                //        M8B_SOURCE_FILE_PREFIX+"."+"m8b", 8, false);

                // fire share intent?
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE m8b");
                intent.setType("application/wigle.m8b");

                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, getResources().getText(R.string.send_to)));
            }
        });
        ListFragment.lameStatic.dbHelper.addToQueue( request );
        return true;
    }
}
