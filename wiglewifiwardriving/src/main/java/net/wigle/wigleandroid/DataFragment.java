package net.wigle.wigleandroid;

import net.wigle.m8b.geodesy.mgrs;
import net.wigle.m8b.geodesy.utm;
import net.wigle.m8b.siphash.SipKey;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.ObservationImporter;
import net.wigle.wigleandroid.background.ObservationUploader;
import net.wigle.wigleandroid.background.QueryThread;
import net.wigle.wigleandroid.background.TransferListener;
import net.wigle.wigleandroid.background.KmlWriter;
import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.model.Pair;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.MagicEightUtil;
import net.wigle.wigleandroid.util.SearchUtil;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.core.content.FileProvider;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * configure settings
 */
public final class DataFragment extends Fragment implements ApiListener, TransferListener, DialogListener {

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

    // constants for Magic (8) Ball export
    //private static final String M8B_SEP = "|";
    private static final String M8B_FILE_PREFIX = "export";
    //private static final String M8B_SOURCE_EXTENSION = ".m8bs";
    private static final String M8B_DIR = "/wiglewifi/m8b/";
    private static final String M8B_EXTENSION = ".m8b";
    private static final int SLICE_BITS = 30;

    ProgressDialog pd = null;
    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
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
                        ListFragment.lameStatic.runNetworks, ListFragment.lameStatic.runBtNetworks );
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

            MainActivity.info("DB backup postExe");

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

    /**
     * Query DB for pairs, generate intermediate source file, process, fire share intent.
     * @return boolean successful
     */
    private boolean exportM8bFile() {
        final long totalDbNets = ListFragment.lameStatic.dbHelper.getNetworkCount();
        new AsyncMagicEightBallExportTask().execute(
                (int)(totalDbNets/1000.0d
                        /*ALIBI: total DB nets in thousands as a "guess" for total size to process*/),
                0, 0);
        return true;
    }

    /**
     * Asynchronous execution wrapping m8b generation. half-redundant with the async query, half not
     */
    class AsyncMagicEightBallExportTask extends AsyncTask<Integer, Integer, String> {

        @Override
        protected String doInBackground(Integer... dbKRecords) {

            // try and get the actual accurate matching record count. this is slow with large DBs.
            // TODO: is this worth it?
            long dbCount = 0;
            try {
                dbCount = ListFragment.lameStatic.dbHelper.getNetsWithLocCountFromDB();
            } catch (DBException dbe) {
                // fall back to the total number of records.
            }

            // replace our placeholder if we get a proper number
            final long thousandDbRecords = dbCount == 0 ? dbKRecords[0]: dbCount/1000;

            //DEBUG: MainActivity.info("matching values: " + thousandDbRecords);

            // TODO: there's a real case for refusing to export on devices that don't have SD...
            final boolean hasSD = MainActivity.hasSD();

            File m8bDestFile;
            final FileChannel out;

            try {
                if ( hasSD ) {
                    final String basePath = MainActivity.safeFilePath(
                            Environment.getExternalStorageDirectory() ) + M8B_DIR;
                    final File path = new File( basePath );
                    //noinspection ResultOfMethodCallIgnored
                    path.mkdirs();
                    if (!path.exists()) {
                        MainActivity.info("Got '!exists': " + path);
                    }
                    String openString = basePath + M8B_FILE_PREFIX +  M8B_EXTENSION;
                    //DEBUG: MainActivity.info("Opening file: " + openString);
                    m8bDestFile = new File( openString );

                } else {
                    m8bDestFile = new File(getActivity().getApplication().getFilesDir(),
                            M8B_FILE_PREFIX + M8B_EXTENSION);
                }
                //ALIBI: always start fresh
                boolean append = false;
                out = new FileOutputStream(m8bDestFile, append).getChannel();

            } catch (IOException ioex) {
                MainActivity.error("Unable to open ouput: ", ioex);
                return "ERROR";
            }

            final File outputFile = m8bDestFile;

            final SipKey sipkey = new SipKey(new byte[16]);
            final byte[] macbytes = new byte[6];
            final Map<Integer,Set<mgrs>> mjg = new TreeMap<Integer,Set<mgrs>>();

            final long genStart = System.currentTimeMillis();

            // write intermediate file
            // ALIBI: redundant thread, but this gets us queue, progress
            final QueryThread.Request request = new QueryThread.Request(
                    ListFragment.lameStatic.dbHelper.LOCATED_NETS_QUERY, new QueryThread.ResultHandler() {

                int non_utm=0;
                int rows = 0;
                int records = 0;

                @Override
                public boolean handleRow(final Cursor cursor) {
                    final String bssid = cursor.getString(0);
                    final float lat = cursor.getFloat(1);
                    final float lon = cursor.getFloat(2);
                    if (!(-80<=lat && lat<=84)) {
                        non_utm++;
                    } else {
                        mgrs m = mgrs.fromUtm(utm.fromLatLon(lat,lon));

                        Integer kslice2 = MagicEightUtil.extractKeyFrom(bssid,macbytes,sipkey,SLICE_BITS);

                        Set<mgrs> locs = mjg.get(kslice2);
                        if (locs==null){
                            locs = new HashSet<mgrs>();
                            mjg.put(kslice2,locs);
                        }
                        if(locs.add(m)){
                            records++;
                        }
                    }


                    rows++;
                    if (rows % 1000 == 0) {
                        //DEBUG: MainActivity.info("\tprogress: rows: "+rows+" / "+thousandDbRecords + " = "+ (int) ((rows / (double) 1000 / (double) thousandDbRecords) * 100));
                        publishProgress((int) ((rows / (double) 1000 / (double) thousandDbRecords) * 100));
                    }
                    return true;
                }

                /**
                 * once the intermediate file's written, run the generate pipeline, setup and enqueue the intent to share
                 */
                @Override
                public void complete() {
                    MainActivity.info("m8b source export complete...");

                    // Tidy up the finished writer
                    if (null != out) {
                        try {
                            Charset utf8  = Charset.forName("UTF-8");

                            ByteBuffer bb = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN); // screw you, java

                            // write header
                            bb.put("MJG\n".getBytes(utf8)); // magic number
                            bb.put("2\n".getBytes(utf8)); // version
                            bb.put("SIP-2-4\n".getBytes(utf8)); // hash
                            bb.put(String.format("%x\n",SLICE_BITS).getBytes(utf8)); // slice bits (hex)
                            bb.put("MGRS-1000\n".getBytes(utf8)); // coords
                            bb.put("4\n".getBytes(utf8)); // id size in bytes (hex)
                            bb.put("9\n".getBytes(utf8)); // coords size in bytes (hex)
                            bb.put(String.format("%x\n",records).getBytes(utf8)); // record count (hex)

                            int recordsize = 4+9;

                            bb.flip();
                            while (bb.hasRemaining()){
                                out.write(bb);
                            }

                            // back to fill mode
                            bb.clear();
                            byte[] mstr = new byte[9];
                            int outElements = 0;
                            for ( Map.Entry<Integer,Set<mgrs>> me : mjg.entrySet()) {
                                int key = me.getKey().intValue();
                                for ( mgrs m : me.getValue() ) {
                                    if (bb.remaining() < recordsize ) {
                                        bb.flip();
                                        while (bb.hasRemaining()){
                                            out.write(bb);
                                        }
                                        bb.clear();
                                    }
                                    m.populateBytes(mstr);
                                    bb.putInt(key).put(mstr);
                                }
                                outElements++;
                                if (outElements % 100 == 0) {
                                    publishProgress(100, (int)(outElements / (double)mjg.size() * 100));
                                }
                            }

                            bb.flip();
                            while (bb.hasRemaining()) {
                                out.write(bb);
                            }

                            bb.clear();
                            out.close();

                        } catch (IOException ioex) {
                            MainActivity.error("Failed to close m8b writer", ioex);
                        }
                    }

                    final long duration = System.currentTimeMillis() - genStart;
                    MainActivity.info("completed m8b generation. Generation time: "+((double)duration * 0.001d)+"s");

                    publishProgress(100, 100); //ALIBI: will close the dialog in case fractions didn't work out.

                    // fire share intent?
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE.m8b");
                    intent.setType("application/wigle.m8b");

                    //TODO: verify local-only storage case/m8b_paths.xml
                    final Uri fileUri = FileProvider.getUriForFile(getContext(),
                            MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                                    ".m8bprovider", outputFile);

                        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, getResources().getText(R.string.send_to)));
                }
            });
            ListFragment.lameStatic.dbHelper.addToQueue( request );
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (null != result) { //launch task will exist with bg thread enqueued with null return
                MainActivity.error("POST EXECUTE: " + result);
                if (pd.isShowing()) {
                    pd.dismiss();
                }
            }
        }

        @Override
        protected void onPreExecute() {
            //TODO: tri-bar progress indicator instead of single bar?
            pd = new ProgressDialog(getContext());
            pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            pd.setCancelable(false);
            pd.setMessage(getString(R.string.m8b_sizing));
            pd.setIndeterminate(true);
            pd.show();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (values.length == 2) { // actually 2-stage?
                if (values[1] > 0) {
                    if (100 == values[1]) {
                        if (pd.isShowing()) {
                            pd.dismiss();
                        }
                        return;
                    }
                    pd.setMessage(getString(R.string.exporting_m8b_final));
                    pd.setProgress(values[1]);
                } else {
                    pd.setIndeterminate(false);
                    pd.setMessage(getString(R.string.calculating_m8b));
                    pd.setProgress(values[0]);
                }
            } else { // default single progress bar - trust the message already set?
                pd.setIndeterminate(false);
                pd.setMessage(getString(R.string.calculating_m8b));
                pd.setProgress(values[0]);
            }
        }
    }
}
