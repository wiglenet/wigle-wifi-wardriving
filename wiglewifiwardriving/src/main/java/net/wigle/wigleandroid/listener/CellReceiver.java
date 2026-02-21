package net.wigle.wigleandroid.listener;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

import net.wigle.wigleandroid.FilterMatcher;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.GsmOperator;
import net.wigle.wigleandroid.model.GsmOperatorException;
import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.ui.NetworkListUtil;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.util.CellNetworkLegend;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.ScanUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles cellular network scanning and logging on its own timer, using the same period settings
 * as WifiReceiver. Refactored out of WiFiReceiver.
 * @author bobzilla, arkasha
 */
public class CellReceiver {
    public static final int CELL_MIN_STRENGTH = -113;

    /**
     * data transfer object from binder-call laden background processing
     */
    private static class RawCellData {
        final List<CellInfo> cellInfo;
        final CellLocation cellLocation;

        final String networkOperator;

        final String networkOperatorName;

        final int networkTypeId;

        final String networkCountryIso;

        RawCellData(final List<CellInfo> cellInfo, final CellLocation cellLocation, final String networkOperator, final String networkOperatorName, final int networkTypeId, final String networkCountryIso) {
            this.cellInfo = cellInfo;
            this.cellLocation = cellLocation;
            this.networkOperator = networkOperator;
            this.networkOperatorName = networkOperatorName;
            this.networkTypeId = networkTypeId;
            this.networkCountryIso = networkCountryIso;
        }
    }

    private MainActivity mainActivity;
    private final DatabaseHelper dbHelper;
    private final ExecutorService cellExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler cellTimer;
    private SetNetworkListAdapter listAdapter;
    private final Set<String> runCells = new HashSet<>();

    public CellReceiver(final MainActivity mainActivity, final DatabaseHelper dbHelper, final Context context) {
        this.mainActivity = mainActivity;
        this.dbHelper = dbHelper;
    }

    public void setListAdapter(final SetNetworkListAdapter listAdapter) {
        this.listAdapter = listAdapter;
    }

    /**
     * scan period access
     */
    public long getScanPeriod() {
        if (mainActivity == null) {
            return MainActivity.SCAN_DEFAULT;
        }
        final android.content.SharedPreferences prefs = mainActivity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        Location location = null;
        final GNSSListener gpsListener = mainActivity.getGPSListener();
        if (gpsListener != null) {
            location = gpsListener.getCurrentLocation();
        }
        return ScanUtil.getWifiScanPeriod(prefs, location);
    }

    public void setupCellTimer(final boolean delayFirstScan) {
        Logging.info("create cell timer");
        if (cellTimer == null) {
            cellTimer = new Handler();
            final Runnable mUpdateTimeTask = new Runnable() {
                @Override
                public void run() {
                    if (mainActivity != null && !mainActivity.isFinishing() && mainActivity.isScanning()) {
                        doCellScan();
                        long period = getScanPeriod();
                        if (period == 0L) {
                            period = MainActivity.SCAN_DEFAULT;
                        }
                        cellTimer.postDelayed(this, period);
                    } else {
                        Logging.info("cell timer: finishing or not scanning");
                    }
                }
            };
            cellTimer.removeCallbacks(mUpdateTimeTask);
            cellTimer.postDelayed(mUpdateTimeTask, delayFirstScan ? 500 : 100);
            if (!delayFirstScan) {
                doCellScan();
            }
        }
    }

    public void stopCellTimer() {
        if (cellTimer != null) {
            cellTimer.removeCallbacksAndMessages(null);
            cellTimer = null;
        }
    }

    private void doCellScan() {
        if (mainActivity == null || !mainActivity.isScanning()) {
            return;
        }
        final GNSSListener gpsListener = mainActivity.getGPSListener();
        final android.content.SharedPreferences prefs = mainActivity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        Location location = null;
        if (gpsListener != null) {
            location = gpsListener.checkGetLocation(prefs);
        }
        final Location loc = location;
        final Context ctx = mainActivity;
        //ALIBI: moving into executor due to ANRs caused by the TelephonyManager binder calls in
        // getRawCellData then posting processCellData back onto main
        cellExecutor.execute(() -> {
            final RawCellData raw = getRawCellData(ctx);
            mainHandler.post(() -> {
                if (mainActivity == null || mainActivity.isFinishing()) {
                    return;
                }
                Map<String, Network> cellNetworks = processCellData(raw, loc);
                if (cellNetworks == null) {
                    cellNetworks = Collections.emptyMap();
                }
                final int newCellForRun = cellNetworks.size();
                if (loc == null && newCellForRun > 0) {
                    ListFragment.lameStatic.pendingCellCount = Math.min(25,
                            ListFragment.lameStatic.pendingCellCount + newCellForRun);
                } else if (loc != null) {
                    ListFragment.lameStatic.pendingCellCount = 0;
                }
                ListFragment.lameStatic.runCells = runCells.size();
                ListFragment.lameStatic.newCells = dbHelper.getNewCellCount();
                ListFragment.lameStatic.currCells = cellNetworks.size();
                ListFragment.lameStatic.currNets = ListFragment.lameStatic.currWifi + ListFragment.lameStatic.currCells;
                final boolean showCurrent = prefs.getBoolean(PreferenceKeys.PREF_SHOW_CURRENT, true);
                if (showCurrent && listAdapter != null) {
                    final java.util.regex.Matcher ssidMatcher = FilterMatcher.getSsidFilterMatcher(prefs, PreferenceKeys.FILTER_PREF_PREFIX);
                    final java.util.regex.Matcher bssidMatcher = mainActivity.getBssidFilterMatcher(PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS);
                    for (Network cellNetwork : cellNetworks.values()) {
                        if (cellNetwork != null && FilterMatcher.isOk(ssidMatcher, bssidMatcher, prefs, PreferenceKeys.FILTER_PREF_PREFIX, cellNetwork)) {
                            listAdapter.addCell(cellNetwork);
                        }
                    }
                }
                NetworkListUtil.sort(prefs, listAdapter);
                mainActivity.setNetCountUI();
            });
        });
    }

    /**
     * binder-call - do not perform on main thread
     * @param context the context of the request
     * @return a RawCellData object with the current scan's cell and operator information
     */
    private static RawCellData getRawCellData(final Context context) {
        if (context == null) {
            return new RawCellData(null, null, null, null, -1, null);
        }
        TelephonyManager tele = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tele == null) {
            return new RawCellData(null, null, null, null, -1, null);
        }
        try {
            List<CellInfo> infos = tele.getAllCellInfo();
            CellLocation cellLocation = null;
            if (infos == null && Build.VERSION.SDK_INT < 26) {
                cellLocation = tele.getCellLocation();
            }
            return new RawCellData(infos, cellLocation, tele.getNetworkOperator(), tele.getNetworkOperatorName(),
                    tele.getNetworkType(), tele.getNetworkCountryIso());
        } catch (SecurityException sex) {
            Logging.warn("unable to scan cells due to permission issue: ", sex);
            return new RawCellData(null, null, null, null, -1, null);
        } catch (NullPointerException ex) {
            Logging.warn("NPE on cell scan: ", ex);
            return new RawCellData(null, null, null, null, -1, null);
        }
    }

    /**
     * handle a RawCellData result - main thread safe.
     * @param raw the raw data from the getRawCellData call
     * @param location current location (from MainActivity)
     * @return a Map of Network objects indexed by "BSSID" (cell op_lac_cid or system_net_bss for cdma)
     */
    private Map<String, Network> processCellData(final RawCellData raw, final Location location) {
        Map<String, Network> networks = new HashMap<>();
        if (mainActivity == null || raw == null) {
            return networks;
        }
        try {
            List<CellInfo> infos = raw.cellInfo;
            if (infos != null) {
                for (final CellInfo cell : infos) {
                    Network network = handleSingleCellInfo(cell, raw.networkOperator, location);
                    if (network != null) {
                        networks.put(network.getBssid(), network);
                    }
                }
            } else if (raw.cellLocation != null) {
                Network currentNetwork = handleSingleCellLocation(raw.cellLocation, raw.networkOperator, raw.networkOperatorName, raw.networkTypeId, raw.networkCountryIso, location);
                if (currentNetwork != null) {
                    networks.put(currentNetwork.getBssid(), currentNetwork);
                }
            }
        } catch (SecurityException sex) {
            Logging.warn("unable to scan cells due to permission issue: ", sex);
        } catch (NullPointerException ex) {
            Logging.warn("NPE on cell scan: ", ex);
        }
        return networks;
    }

    private Network handleSingleCellInfo(final CellInfo cellInfo, final String networkOperator, final Location location) {
        if (cellInfo == null) {
            return null;
        }
        if (MainActivity.DEBUG_CELL_DATA) {
            Logging.info("cell: " + cellInfo + " class: " + cellInfo.getClass().getCanonicalName());
        }
        try {
            switch (cellInfo.getClass().getSimpleName()) {
                case "CellInfoCdma":
                    return handleSingleCdmaInfo((CellInfoCdma) cellInfo, networkOperator, location);
                case "CellInfoGsm": {
                    GsmOperator g = new GsmOperator(((CellInfoGsm) cellInfo).getCellIdentity());
                    CellSignalStrengthGsm cellStrengthG = ((CellInfoGsm) cellInfo).getCellSignalStrength();
                    return addOrUpdateCell(g.getOperatorKeyString(), g.getOperatorString(), g.getXfcn(), "GSM",
                            cellStrengthG.getDbm(), NetworkType.typeForCode("G"), location);
                }
                case "CellInfoLte": {
                    GsmOperator g = new GsmOperator(((CellInfoLte) cellInfo).getCellIdentity());
                    CellSignalStrengthLte cellStrengthL = ((CellInfoLte) cellInfo).getCellSignalStrength();
                    return addOrUpdateCell(g.getOperatorKeyString(), g.getOperatorString(), g.getXfcn(), "LTE",
                            cellStrengthL.getDbm(), NetworkType.typeForCode("L"), location);
                }
                case "CellInfoWcdma": {
                    GsmOperator g = new GsmOperator(((CellInfoWcdma) cellInfo).getCellIdentity());
                    CellSignalStrengthWcdma cellStrengthW = ((CellInfoWcdma) cellInfo).getCellSignalStrength();
                    return addOrUpdateCell(g.getOperatorKeyString(), g.getOperatorString(), g.getXfcn(), "WCDMA",
                            cellStrengthW.getDbm(), NetworkType.typeForCode("D"), location);
                }
                case "CellInfoNr":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        GsmOperator g = new GsmOperator((CellIdentityNr) ((CellInfoNr) cellInfo).getCellIdentity());
                        CellSignalStrength cellStrength = ((CellInfoNr) cellInfo).getCellSignalStrength();
                        return addOrUpdateCell(g.getOperatorKeyString(), g.getOperatorString(), g.getXfcn(), "NR",
                                cellStrength.getDbm(), NetworkType.typeForCode("N"), location);
                    }
                    break;
                default:
                    Logging.warn("Unknown cell case: " + cellInfo.getClass().getSimpleName());
                    break;
            }
        } catch (GsmOperatorException e) {
            // skip invalid cell data
        }
        return null;
    }

    private Network handleSingleCellLocation(final CellLocation cellLocation,
                                             final String networkOperator,
                                             final String networkOperatorName,
                                             final int networkTypeId,
                                             final String networkCountryIso,
                                             final Location location) {
        String bssid = null;
        NetworkType type = null;
        String ssid = null;
        Network network;

        if (cellLocation == null) {
            return null;
        }
        if (cellLocation.getClass().getSimpleName().equals("CdmaCellLocation")) {
            try {
                CdmaCellLocation cdma = (CdmaCellLocation) cellLocation;
                final int systemId = cdma.getSystemId();
                final int networkId = cdma.getNetworkId();
                final int baseStationId = cdma.getBaseStationId();
                if (systemId > 0 && networkId >= 0 && baseStationId >= 0) {
                    bssid = systemId + "_" + networkId + "_" + baseStationId;
                    type = NetworkType.CDMA;
                }
                ssid = networkOperatorName;
            } catch (Exception ex) {
                Logging.error("CDMA reflection exception: " + ex);
            }
        } else if (cellLocation instanceof GsmCellLocation) {
            GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
            if (gsmCellLocation.getLac() >= 0 && gsmCellLocation.getCid() >= 0) {
                bssid = networkOperator + "_" + gsmCellLocation.getLac() + "_" + gsmCellLocation.getCid();
                try {
                    ssid = GsmOperator.getOperatorName(networkOperator);
                } catch (android.database.SQLException sex) {
                    Logging.error("failed to get op for " + networkOperator);
                }
                type = NetworkType.GSM;
            }
            if (networkOperator == null || networkOperator.isEmpty()) {
                return null;
            }
        } else {
            Logging.warn("Unhandled CellLocation type: " + cellLocation.getClass().getSimpleName());
            return null;
        }

        if (bssid == null) {
            return null;
        }

        final String networkType = CellNetworkLegend.getNetworkTypeName(networkTypeId);
        final String capabilities = networkType + ";" + networkCountryIso;
        int strength = 0;
        PhoneState phoneState = mainActivity.getPhoneState();
        if (phoneState != null) {
            strength = phoneState.getStrength();
        }
        if (NetworkType.GSM.equals(type)) {
            strength = gsmDBmMagicDecoderRing(strength);
        }

        final ConcurrentLinkedHashMap<String, Network> networkCache = MainActivity.getNetworkCache();
        final boolean newForRun = runCells.add(bssid);
        network = networkCache.get(bssid);
        if (network == null) {
            network = new Network(bssid, ssid, 0, capabilities, strength, type);
            networkCache.put(network.getBssid(), network);
        } else {
            network.setLevel(strength);
        }
        if (location != null && (newForRun || network.getLatLng() == null)) {
            network.setLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
        }
        if (location != null) {
            dbHelper.addObservation(network, location, newForRun);
        }
        return network;
    }

    @SuppressWarnings("SameParameterValue")
    private int gsmDBmMagicDecoderRing(int strength) {
        if (strength == 99) {
            return CELL_MIN_STRENGTH;
        }
        return strength * 2 + CELL_MIN_STRENGTH;
    }

    private Network handleSingleCdmaInfo(final CellInfoCdma cellInfo, final String networkOperator, final Location location) {
        CellIdentityCdma cellIdentC = cellInfo.getCellIdentity();
        CellSignalStrengthCdma cellStrengthC = cellInfo.getCellSignalStrength();
        final int bssIdInt = cellIdentC.getBasestationId();
        final int netIdInt = cellIdentC.getNetworkId();
        final int systemIdInt = cellIdentC.getSystemId();
        if (bssIdInt == Integer.MAX_VALUE || netIdInt == Integer.MAX_VALUE || systemIdInt == Integer.MAX_VALUE) {
            Logging.info("Discarding CDMA cell with invalid ID");
            return null;
        }
        final String networkKey = systemIdInt + "_" + netIdInt + "_" + bssIdInt;
        final int dBmLevel = cellStrengthC.getDbm();
        return addOrUpdateCell(networkKey, networkOperator, 0, "CDMA", dBmLevel, NetworkType.typeForCode("C"), location);
    }

    private Network addOrUpdateCell(final String bssid, final String operator,
                                    final int frequency, final String networkTypeName,
                                    final int strength, final NetworkType type,
                                    final Location location) {
        final String capabilities = networkTypeName + ";" + operator;
        final ConcurrentLinkedHashMap<String, Network> networkCache = MainActivity.getNetworkCache();
        final boolean newForRun = runCells.add(bssid);

        Network network = networkCache.get(bssid);
        try {
            final String operatorName = GsmOperator.getOperatorName(operator);
            if (network == null) {
                network = new Network(bssid, operatorName, frequency, capabilities,
                        (strength == Integer.MAX_VALUE) ? CELL_MIN_STRENGTH : strength, type);
                networkCache.put(network.getBssid(), network);
            } else {
                network.setLevel((strength == Integer.MAX_VALUE) ? CELL_MIN_STRENGTH : strength);
                network.setFrequency(frequency);
            }
            if (location != null && (newForRun || network.getLatLng() == null)) {
                network.setLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
            }
            if (location != null) {
                dbHelper.addObservation(network, location, newForRun);
            }
        } catch (android.database.SQLException sex) {
            Logging.error("Error in add/update cell:", sex);
        }
        return network;
    }
}
