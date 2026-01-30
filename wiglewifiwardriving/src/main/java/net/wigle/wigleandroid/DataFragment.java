package net.wigle.wigleandroid;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import net.wigle.wigleandroid.background.BackupRunnable;
import net.wigle.wigleandroid.background.GpxExportRunnable;
import net.wigle.wigleandroid.background.MagicEightBallRunnable;
import net.wigle.wigleandroid.background.ObservationImporter;
import net.wigle.wigleandroid.background.ObservationUploader;
import net.wigle.wigleandroid.background.KmlWriter;
import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.model.NetworkFilterType;
import net.wigle.wigleandroid.ui.LayoutUtil;
import net.wigle.wigleandroid.ui.NetworkTypeArrayAdapter;
import net.wigle.wigleandroid.ui.WiFiSecurityTypeArrayAdapter;
import net.wigle.wigleandroid.ui.WiGLEConfirmationDialog;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.SearchUtil;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import static net.wigle.wigleandroid.MainActivity.ACTION_GPX_MGMT;
import static net.wigle.wigleandroid.background.GpxExportRunnable.EXPORT_GPX_DIALOG;

import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.Future;

/**
 * configure database settings
 * @author bobzilla, arkasha
 */
public final class DataFragment extends Fragment implements DialogListener {

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

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        // set language
        Activity a = getActivity();
        if (null != a) {
            MainActivity.setLocale(getActivity());
            // force media volume controls
            a.setVolumeControlStream( AudioManager.STREAM_MUSIC );
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.data, container, false);

        setupQueryInputs( view );
        setupQueryButtons( view );
        setupCsvButtons( view );
        setupKmlButtons(view);
        setupBackupDbButton(view);
        setupImportObservedButton(view);
        setupMarkerButtons(view);
        setupM8bExport(view);
        setupGpxExport(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            final Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(0, 0, 0, navBars.bottom);
            return insets;
        });
        //hack manual padding
        view.post(() -> {
            final Context context = getContext();
            int navBarHeight = context == null ? 0 : LayoutUtil.getNavigationBarHeight(getActivity(), context.getResources());
            if (navBarHeight > 0 && view.getPaddingBottom() == 0) {
                view.setPadding(0, 0, 0, navBarHeight);
            }
            if (view.isAttachedToWindow()) {
                ViewCompat.requestApplyInsets(view);
            }
        });
    }

    private void setupQueryInputs( final View view ) {
        final TextInputLayout addressLayout = view.findViewById(R.id.query_address_layout);
        if (null != addressLayout) {
            //ALIBI: keeping old-school address layout in the database tab for consistency's sake. Do we need this?
            addressLayout.setVisibility(View.VISIBLE);
        }
        //ALIBI: query bounds are always null (at least until addr. input) since this view has no map
        if (null != ListFragment.lameStatic && null != ListFragment.lameStatic.queryArgs) {
            ListFragment.lameStatic.queryArgs.setLocationBounds(null);
        }
        final Spinner networkTypeSpinner = view.findViewById(R.id.type_spinner);
        final Spinner wifiEncryptionSpinner = view.findViewById(R.id.encryption_spinner);

        NetworkTypeArrayAdapter adapter = new NetworkTypeArrayAdapter(getContext());
        networkTypeSpinner.setAdapter(adapter);
        networkTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View compontentView, int position, long id) {
                if (position == 0 || position == 1) {
                    if (null != wifiEncryptionSpinner) {
                        wifiEncryptionSpinner.setClickable(true);
                        wifiEncryptionSpinner.setEnabled(true);
                    } else {
                        Logging.error("Unable to disable the security type spinner");
                    }
                } else {
                    if (null != wifiEncryptionSpinner) {
                        wifiEncryptionSpinner.setSelection(0);
                        wifiEncryptionSpinner.setClickable(false);
                        wifiEncryptionSpinner.setEnabled(false);
                        Logging.info("TODO: need to clear wifi security in query");
                    } else {
                        Logging.error("Unable to disable the security type spinner");
                    }
                }
                LinearLayout cell = view.findViewById(R.id.cell_netid_layout);
                TextView macHint =  view.findViewById(R.id.query_bssid_layout);
                EditText maskedMac = view.findViewById(R.id.query_bssid);
                if (position == 3) {
                    cell.setVisibility(VISIBLE);
                    macHint.setVisibility(GONE);
                    maskedMac.setVisibility(GONE);
                    maskedMac.setText("");
                } else {
                    cell.setVisibility(GONE);
                    macHint.setVisibility(VISIBLE);
                    maskedMac.setVisibility(VISIBLE);
                    SearchUtil.clearCellId(view);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (null != wifiEncryptionSpinner) {
                    wifiEncryptionSpinner.setClickable(true);
                    wifiEncryptionSpinner.setEnabled(true);
                    LinearLayout cell = view.findViewById(R.id.cell_netid_layout);
                    TextView macHint =  view.findViewById(R.id.query_bssid_layout);
                    EditText maskedMac = view.findViewById(R.id.query_bssid);
                    cell.setVisibility(GONE);
                    macHint.setVisibility(VISIBLE);
                    maskedMac.setVisibility(VISIBLE);
                    SearchUtil.clearCellId(view);
                } else {
                    Logging.error("Unable to disable the security type spinner");
                }
            }
        });

        if (null != ListFragment.lameStatic.queryArgs && ListFragment.lameStatic.queryArgs.getType() != null) {
            networkTypeSpinner.setSelection(ListFragment.lameStatic.queryArgs.getType().ordinal());
        }
        WiFiSecurityTypeArrayAdapter securityAdapter = new WiFiSecurityTypeArrayAdapter(getContext());
        wifiEncryptionSpinner.setAdapter(securityAdapter);
        if (null != ListFragment.lameStatic.queryArgs &&
                ListFragment.lameStatic.queryArgs.getCrypto() != null) {
            if (ListFragment.lameStatic.queryArgs.getType() != null &&
                    (NetworkFilterType.ALL.equals(ListFragment.lameStatic.queryArgs.getType()) ||
                            NetworkFilterType.WIFI.equals(ListFragment.lameStatic.queryArgs.getType()))) {
                wifiEncryptionSpinner.setSelection(ListFragment.lameStatic.queryArgs.getCrypto().ordinal());
            }
        }

    }

    private void setupQueryButtons( final View view ) {
        Button button = view.findViewById( R.id.perform_search_button);
        button.setOnClickListener(buttonView -> {

        final String fail = SearchUtil.setupQuery(view, getActivity(), true);
        if (null != ListFragment.lameStatic.queryArgs) {
            ListFragment.lameStatic.queryArgs.setSearchWiGLE(false);
        }
        if (fail != null) {
            WiGLEToast.showOverFragment(getActivity(), R.string.error_general, fail);
        } else {
            // start db result activity
            final Intent settingsIntent = new Intent(getActivity(), DBResultActivity.class);
            startActivity(settingsIntent);
        }
    });

    button = view.findViewById( R.id.reset_button );
    button.setOnClickListener(buttonView -> SearchUtil.clearSearchFields(view));

    }

    /**
     * TransferListener interface
     */

    private void setupCsvButtons( final View view ) {
        // actually need this Activity context, for dialogs

        final Button csvRunExportButton = view.findViewById( R.id.csv_run_export_button );
        csvRunExportButton.setOnClickListener(buttonView -> {
            final FragmentActivity fa = getActivity();
            if (fa != null) {
                WiGLEConfirmationDialog.createConfirmation(fa,
                        DataFragment.this.getString(R.string.data_export_csv), R.id.nav_data, CSV_RUN_DIALOG);
            } else {
                Logging.error("Null FragmentActivity setting up CSV run export button");
            }
        });

        final Button csvExportButton = view.findViewById( R.id.csv_export_button );
        csvExportButton.setOnClickListener(buttonView -> {
            final FragmentActivity fa = getActivity();
            if (fa != null) {
                WiGLEConfirmationDialog.createConfirmation( fa,
                    DataFragment.this.getString(R.string.data_export_csv_db), R.id.nav_data, CSV_DB_DIALOG);
            } else {
                Logging.error("Null FragmentActivity setting up CSV export button");
            }
        });
    }

    private void setupKmlButtons( final View view ) {
        final Button kmlRunExportButton = view.findViewById( R.id.kml_run_export_button );
        kmlRunExportButton.setOnClickListener(buttonView -> {
            final FragmentActivity fa = getActivity();
            if (fa != null) {
                WiGLEConfirmationDialog.createConfirmation( fa,
                    DataFragment.this.getString(R.string.data_export_kml_run), R.id.nav_data, KML_RUN_DIALOG);
            } else {
                Logging.error("Null FragmentActivity setting up KML run export button");
            }
        });


        final Button kmlExportButton = view.findViewById( R.id.kml_export_button );
        kmlExportButton.setOnClickListener(buttonView -> {
            final FragmentActivity fa = getActivity();
            if (fa != null) {
                WiGLEConfirmationDialog.createConfirmation( fa,
                    DataFragment.this.getString(R.string.data_export_kml_db), R.id.nav_data, KML_DB_DIALOG);
            } else {
                Logging.error("Null FragmentActivity setting up KML export button");
            }
        });
    }

    private void setupBackupDbButton( final View view ) {
        final Button dbBackupButton = view.findViewById( R.id.backup_db_button );
        dbBackupButton.setOnClickListener(buttonView -> {
            final FragmentActivity fa = getActivity();
            if (fa != null) {
                WiGLEConfirmationDialog.createConfirmation( fa,
                    DataFragment.this.getString(R.string.data_backup_db), R.id.nav_data, BACKUP_DIALOG);
            } else {
                Logging.error("Null FragmentActivity setting up backup confirmation");
            }
        });
    }

    private void setupImportObservedButton( final View view ) {
        final Button importObservedButton = view.findViewById( R.id.import_observed_button );
        SharedPreferences prefs = null;
        Activity a = getActivity();
        MainActivity m = MainActivity.getMainActivity();
        if (null != a) {
            prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        }
        String authname = null;
        if (prefs != null) {
            authname = prefs.getString(PreferenceKeys.PREF_AUTHNAME, null);
        }

        if (null == authname) {
            importObservedButton.setEnabled(false);
        } else if (null != m &&  m.isTransferring()) {
                importObservedButton.setEnabled(false);
        }
        importObservedButton.setOnClickListener(buttonView -> {
            final FragmentActivity fa = getActivity();
            if (null != fa) {
                WiGLEConfirmationDialog.createConfirmation(fa,
                        DataFragment.this.getString(R.string.data_import_observed),
                        R.id.nav_data, IMPORT_DIALOG);
            } else {
                Logging.error("unable to get fragment activity");
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
                (object, cached) -> {
                    if (mainActivity != null) {
                        try {
                            mainActivity.getState().dbHelper.getNetworkCountFromDB();
                        } catch (DBException dbe) {
                            Logging.warn("failed DB count update on import-observations", dbe);
                        }
                        mainActivity.transferComplete();
                    }
                });
        try {
            task.startDownload(this);
        } catch (WiGLEAuthException waex) {
            Logging.info("failed to authorize user on request");
        }
    }

    @SuppressLint("SetTextI18n")
    private void setupMarkerButtons( final View view ) {
        SharedPreferences prefs = null;
        final Activity a = getActivity();
        if (null != a) {
            prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        }

        // db marker reset button and text
        final TextView tv = view.findViewById(R.id.reset_maxid_text);
        if (null != prefs) {
            tv.setText(getString(R.string.setting_high_up) + " " + prefs.getLong(PreferenceKeys.PREF_DB_MARKER, 0L));
        }

        final Button resetMaxidButton = view.findViewById(R.id.reset_maxid_button);
        resetMaxidButton.setOnClickListener(buttonView -> {
            final FragmentActivity fa = getActivity();
            if (null != fa) {
                WiGLEConfirmationDialog.createConfirmation(fa, getString(R.string.setting_zero_out),
                        R.id.nav_data, ZERO_OUT_DIALOG);
            } else {
                Logging.error("unable to get fragment activity");
            }
        });

        // db marker maxout button and text
        if (null != prefs) {
            final TextView maxtv = view.findViewById(R.id.maxout_maxid_text);
            final long maxDB = prefs.getLong(PreferenceKeys.PREF_MAX_DB, 0L);
            maxtv.setText(getString(R.string.setting_max_start) + " " + maxDB);
        }

        final Button maxoutMaxidButton = view.findViewById(R.id.maxout_maxid_button);
        maxoutMaxidButton.setOnClickListener(buttonView -> {
            final FragmentActivity fa = getActivity();
            if (null != fa) {
                WiGLEConfirmationDialog.createConfirmation(fa, getString(R.string.setting_max_out),
                        R.id.nav_data, MAX_OUT_DIALOG);
            } else {
                Logging.error("unable to get fragment activity");
            }
        });

        //ALIBI: not technically a marker button, but clearly belongs with them visually/logically
        final Button deleteDbButton = view.findViewById(R.id.clear_db);
        deleteDbButton.setOnClickListener(buttonView -> {
            final FragmentActivity fa = getActivity();
            if (null != fa) {
                WiGLEConfirmationDialog.createConfirmation(fa, getString(R.string.delete_db_confirm),
                        R.id.nav_data, DELETE_DIALOG);
            } else {
                Logging.error("unable to get fragment activity");
            }
        });

    }

    private void setupGpxExport( final View view ) {
        final Activity a = getActivity();
        if (a != null) {
            final SharedPreferences prefs = getActivity().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            //ONLY ENABLE IF WE ARE LOGGING
            if (prefs.getBoolean(PreferenceKeys.PREF_LOG_ROUTES, false)) {
                final View exportGpxTools = view.findViewById(R.id.export_gpx_tools);
                exportGpxTools.setVisibility(View.VISIBLE);
                final Button exportGpxButton = view.findViewById(R.id.export_gpx_button);
                exportGpxButton.setOnClickListener(buttonView -> {
                    final FragmentActivity fa = getActivity();
                    if (null != fa) {
                        WiGLEConfirmationDialog.createConfirmation(fa, getString(R.string.export_gpx_detail),
                                R.id.nav_data, EXPORT_GPX_DIALOG);
                    } else {
                        Logging.error("unable to get fragment activity");
                    }
                });
                final boolean useFossMaps = prefs.getBoolean(PreferenceKeys.PREF_USE_FOSS_MAPS, false);
                final Button manageGpxButton = view.findViewById(R.id.manage_gpx_button);
                manageGpxButton.setOnClickListener(v -> {
                    final Intent gpxIntent = new Intent(a.getApplicationContext(),
                            useFossMaps ? FossGpxManagementActivity.class : GpxManagementActivity.class);
                    a.startActivityForResult(gpxIntent, ACTION_GPX_MGMT);
                });
            }
        }
    }

    private void setupM8bExport( final View view ) {
        final Button exportM8bButton = view.findViewById(R.id.export_m8b_button);
        exportM8bButton.setOnClickListener(buttonView -> {
            final FragmentActivity fa = getActivity();
            if (null != fa) {
                WiGLEConfirmationDialog.createConfirmation(fa, getString(R.string.export_m8b_detail),
                        R.id.nav_data, EXPORT_M8B_DIALOG);
            } else {
                Logging.error("unable to get fragment activity");
            }
        });
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void handleDialog(final int dialogId) {
        SharedPreferences prefs = null;
        if (getActivity() != null) {
            prefs = getActivity().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        }
        SharedPreferences.Editor editor = null;
        if (null != prefs) {
            editor = prefs.edit();
        }
        final View view = getView();

        switch (dialogId) {
            case CSV_RUN_DIALOG: {
                // actually need this Activity context, for dialogs
                ObservationUploader observationUploader = new ObservationUploader(getActivity(),
                        ListFragment.lameStatic.dbHelper, null, true, false, true);
                observationUploader.start();
                break;
            }
            case CSV_DB_DIALOG: {
                ObservationUploader observationUploader = new ObservationUploader(getActivity(),
                        ListFragment.lameStatic.dbHelper, null, true, true, false);
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
                try {
                    BackupRunnable backupRunnable = new BackupRunnable(this.getActivity(), ListFragment.lameStatic.executorService, true);
                    Future<?> bFuture = ListFragment.lameStatic.executorService.submit(backupRunnable);
                } catch (IllegalArgumentException e) {
                    final FragmentActivity a = getActivity();
                    if (null != a) {
                        WiGLEToast.showOverFragment(a, R.string.backup_in_progress,
                                a.getResources().getString(R.string.duplicate_job));
                    }
                }
                break;
            }
            case IMPORT_DIALOG: {
                this.createAndStartImport();
                break;
            }
            case ZERO_OUT_DIALOG: {
                if (null != editor) {
                    editor.putLong(PreferenceKeys.PREF_DB_MARKER, 0L);
                    editor.apply();
                    if (view != null) {
                        final TextView tv = view.findViewById(R.id.reset_maxid_text);
                        tv.setText(getString(R.string.setting_max_id) + " 0");
                    }
                } else {
                    Logging.error("Null editor - unable to update DB marker");
                }
                break;
            }
            case MAX_OUT_DIALOG: {
                if (prefs != null && editor != null) {
                    final long maxDB = prefs.getLong( PreferenceKeys.PREF_MAX_DB, 0L );
                    editor.putLong( PreferenceKeys.PREF_DB_MARKER, maxDB );
                    editor.apply();
                    if (view != null) {
                        // set the text on the other button
                        final TextView tv = view.findViewById(R.id.reset_maxid_text);
                        tv.setText(getString(R.string.setting_max_id) + " " + maxDB);
                    }
                } else {
                    Logging.error("Null prefs/editor - unable to update DB marker");
                }

                break;
            }
            case DELETE_DIALOG: {
                //blow away the DB
                ListFragment.lameStatic.dbHelper.clearDatabase();
                //update markers
                if (null != editor) {
                    editor.putLong(PreferenceKeys.PREF_DB_MARKER, 0L);
                    editor.putLong(PreferenceKeys.PREF_DB_MARKER, 0L);
                    editor.apply();
                    if (view != null) {
                        final TextView tv = view.findViewById(R.id.reset_maxid_text);
                        tv.setText(getString(R.string.setting_max_id) + " " + 0L);
                    }
                    try {
                        ListFragment.lameStatic.dbHelper.getNetworkCountFromDB();
                    } catch (DBException dbe) {
                        Logging.warn("Failed to update network count on DB clear: ", dbe);
                    }
                } else {
                    Logging.error("Null editor - unable to update DB marker");
                }

                break;
            }
            case EXPORT_M8B_DIALOG: {
                if (!exportM8bFile()) {
                    Logging.warn("Failed to export m8b.");
                    WiGLEToast.showOverFragment(getActivity(), R.string.error_general,
                            getActivity().getResources().getString(R.string.m8b_failed));
                }
                break;
            }
            case EXPORT_GPX_DIALOG: {
                if (!exportRouteGpxFile()) {
                    Logging.warn("Failed to export gpx.");
                    final FragmentActivity fa = getActivity();
                    if (null != fa) {
                        WiGLEToast.showOverFragment(fa, R.string.error_general,
                                fa.getResources().getString(R.string.gpx_failed));
                    }
                }
                break;
            }
            default:
                Logging.warn("Data unhandled dialogId: " + dialogId);
        }
    }
    @Override
    public void onResume() {
        Logging.info( "resume data." );
        super.onResume();
        try {
            final FragmentActivity fa = getActivity();
            if (null != fa) {
                fa.setTitle(R.string.data_activity_name);
            } else {
                Logging.error("Failed to set title on null activity onResume");
            }
        } catch (NullPointerException npe) {
            //Nothing to do here.
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * Query DB for pairs, generate intermediate source file, process, fire share intent.
     * @return boolean successful
     */
    private boolean exportM8bFile() {
        final long totalDbNets = ListFragment.lameStatic.dbHelper.getNetworkCount();
        try {
            MagicEightBallRunnable m8bRunnable = new MagicEightBallRunnable(this.getActivity(), ListFragment.lameStatic.executorService, true, totalDbNets);
            Future<?> mFuture = ListFragment.lameStatic.executorService.submit(m8bRunnable);
        } catch (IllegalArgumentException e) {
            final FragmentActivity a = getActivity();
            if (null != a) {
                WiGLEToast.showOverFragment(a, R.string.m8b_failed,
                        a.getResources().getString(R.string.duplicate_job));
            }
        }
        return true;
    }

    private boolean exportRouteGpxFile() {
        final long totalRoutePoints = ListFragment.lameStatic.dbHelper.getCurrentRoutePointCount();
        if (totalRoutePoints > 1) {
            try {
                GpxExportRunnable gpxRunnable = new GpxExportRunnable(this.getActivity(), ListFragment.lameStatic.executorService, true, totalRoutePoints);
                Future<?> gFuture = ListFragment.lameStatic.executorService.submit(gpxRunnable);
            } catch (IllegalArgumentException e) {
                final FragmentActivity a = getActivity();
                if (null != a) {
                    WiGLEToast.showOverFragment(a, R.string.gpx_failed, a.getResources().getString(R.string.duplicate_job));
                }
            }
        } else {
            Logging.error("no points to create route");
            final FragmentActivity a = getActivity();
            if (null != a) {
                WiGLEToast.showOverFragment(a, R.string.gpx_failed,
                        a.getResources().getString(R.string.gpx_no_points));
            }
            //NO POINTS
        }
        return true;
    }
}
