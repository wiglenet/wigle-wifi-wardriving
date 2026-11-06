package net.wigle.wigleandroid.model;

import android.annotation.TargetApi;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.os.Build;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.util.Logging;

import java.util.HashMap;
import java.util.Map;

/**
 * GSM Operator normalization and lookup tools.
 */
public class GsmOperator {

    private static final String KEY_SEP = "_";

    private String mcc;
    private String mnc;
    private int xac;
    private final long cellId;
    private final int fcn;

    private static final Map<String, Map<String, MccMncRecord>> OPERATOR_CACHE = new HashMap<>();


    public GsmOperator(final CellIdentityGsm cellIdentG) throws GsmOperatorException {
        cellId = cellIdentG.getCid();
        xac = cellIdentG.getLac();
        if (Build.VERSION.SDK_INT >= 28) {
            mcc = MccMnc.normalizeMcc(cellIdentG.getMccString());
            mnc = MccMnc.normalizeMnc(cellIdentG.getMncString());
        } else {
            mcc = MccMnc.mccFor(cellIdentG.getMcc());
            mnc = MccMnc.mncFor(cellIdentG.getMnc());
        }

        fcn = cellIdentG.getArfcn() != Integer.MAX_VALUE ? cellIdentG.getArfcn() : 0;

        /*DEBUG: disabled.
        if (MainActivity.DEBUG_CELL_DATA) {
            String res = "GSM Cell:" +
                    "\n\tCID: " + cellId +
                    "\n\tLAC: " + xac +
                    "\n\tPSC: " + cellIdentG.getPsc() +
                    "\n\tMCC: " + mcc +
                    "\n\tMNC: " + mnc +
                    "\n\tNetwork Key: " + getOperatorKeyString() +
                    "\n\toperator: " + getOperatorString() +
                    "\n\tARFCN: " + fcn;

            res += "\n\tBSIC: " + cellIdentG.getBsic();
            Logging.info(res);
        }*/
        if (!validCellId(this.cellId, "GSM") || !validXac(xac) || mcc == null || mnc == null) {
            if (MainActivity.DEBUG_CELL_DATA) {
                Logging.info("Discarding GSM cell with invalid ID for ARFCN: " + cellIdentG.getArfcn());
            }
            throw new GsmOperatorException("invalid GSM Cell Identity values: "+getOperatorKeyString());
        }
    }

    public GsmOperator(final CellIdentityLte cellIdentL) throws GsmOperatorException{
        cellId = cellIdentL.getCi();
        xac = cellIdentL.getTac();
        if (Build.VERSION.SDK_INT >= 28) {
            mcc = MccMnc.normalizeMcc(cellIdentL.getMccString());
            mnc = MccMnc.normalizeMnc(cellIdentL.getMncString());
        } else {
            mcc = MccMnc.mccFor(cellIdentL.getMcc());
            mnc = MccMnc.mncFor(cellIdentL.getMnc());
        }

        fcn = cellIdentL.getEarfcn() != Integer.MAX_VALUE ? cellIdentL.getEarfcn() : 0;
        if (MainActivity.DEBUG_CELL_DATA) {
            String res = "LTE Cell: " +
                    "\n\tCI: " + cellId +
                    "\n\tPCI: " + cellIdentL.getPci() +
                    "\n\tTAC: " + xac +
                    "\n\tMCC: " + mcc +
                    "\n\tMNC: " + mnc +
                    "\n\tNetwork Key: " + getOperatorKeyString() +
                    "\n\toperator: " + getOperatorString() +
                    "\n\tEARFCN:" + fcn;

            if (Build.VERSION.SDK_INT >= 28) {
                //TODO: res += "\n\tBandwidth: "+cellIdentL.getBandwidth()
            }

            Logging.info(res);
        }


        if (!validCellId(this.cellId, "LTE") || !validXac(xac) || mcc == null || mnc == null) {
            if (MainActivity.DEBUG_CELL_DATA) {
                Logging.info("Discarding LTE cell with invalid ID for EARFCN: " + cellIdentL.getEarfcn());
            }
            throw new GsmOperatorException("invalid LTE Cell Identity values "+getOperatorKeyString());
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.Q)
    public GsmOperator(final CellIdentityNr cellIdentN) throws GsmOperatorException{
        cellId = cellIdentN.getNci();
        xac = cellIdentN.getTac();
        mcc = MccMnc.normalizeMcc(cellIdentN.getMccString());
        mnc = MccMnc.normalizeMnc(cellIdentN.getMncString());

        fcn = cellIdentN.getNrarfcn() != Integer.MAX_VALUE ? cellIdentN.getNrarfcn() : 0;
        if (MainActivity.DEBUG_CELL_DATA) {
            String res = "NR Cell: " +
                    "\n\tNCI: " + cellId +
                    "\n\tPCI: " + cellIdentN.getPci() +
                    "\n\tTAC: " + xac +
                    "\n\tMCC: " + mcc +
                    "\n\tMNC: " + mnc +
                    "\n\tNetwork Key: " + getOperatorKeyString() +
                    "\n\toperator: " + getOperatorString() +
                    "\n\tNRARFCN:" + fcn;

            if (Build.VERSION.SDK_INT >= 28) {
                //TODO: res += "\n\tBandwidth: "+cellIdentL.getBandwidth()
            }

            Logging.info(res);
        }

        if (!validCellId(this.cellId, "NR") || !validXac(xac) || mcc == null || mnc == null) {
            if (MainActivity.DEBUG_CELL_DATA) {
                Logging.info("Discarding NR cell with invalid ID for NRARFCN: " + cellIdentN.getNrarfcn());
            }
            throw new GsmOperatorException("invalid NR Cell Identity values "+getOperatorKeyString());
        }
    }

    public GsmOperator(final CellIdentityWcdma cellIdentW) throws GsmOperatorException {
        cellId = cellIdentW.getCid();
        xac = cellIdentW.getLac();
        if (Build.VERSION.SDK_INT >= 28) {
            mcc = MccMnc.normalizeMcc(cellIdentW.getMccString());
            mnc = MccMnc.normalizeMnc(cellIdentW.getMncString());
        } else {
            mcc = MccMnc.mccFor(cellIdentW.getMcc());
            mnc = MccMnc.mncFor(cellIdentW.getMnc());
        }

        fcn = cellIdentW.getUarfcn() != Integer.MAX_VALUE ? cellIdentW.getUarfcn() : 0;

        if (MainActivity.DEBUG_CELL_DATA) {
            String res = "WCDMA Cell:" +
                    "\n\tCI: " + cellId +
                    "\n\tLAC: " + xac +
                    "\n\tMCC: " + mcc +
                    "\n\tMNC: " + mnc +
                    "\n\tNetwork Key: " + getOperatorKeyString() +
                    "\n\toperator: " + getOperatorString() +
                    "\n\tUARFCN:" + fcn;

            Logging.info(res);
        }

        if (!validCellId(this.cellId, "WCDMA") || !validXac(xac) || mcc == null || mnc == null) {
            if (MainActivity.DEBUG_CELL_DATA) {
                Logging.info("Discarding WCDMA cell with invalid ID for UARFCN: " + cellIdentW.getUarfcn());
            }
            throw new GsmOperatorException("invalid WCDMA Cell Identity values "+getOperatorKeyString());
        }
    }

    public String getOperatorString() {
        return mcc+mnc;
    }

    public String getOperatorKeyString() {
        return getOperatorString()+KEY_SEP+xac+KEY_SEP+cellId;
    }

    public int getXfcn() {
        return fcn;
    }

    private boolean validCellId(final long cellId, final String type) {
        switch (type) {
            case "LTE":
            case "WCDMA":
            case "GSM":
                if ((cellId > 0) && (cellId < Integer.MAX_VALUE)) {
                    return true;
                }
            case "NR":
                if ((cellId > 0) && (cellId < Long.MAX_VALUE)) {
                    return true;
                }
        }
        return false;
    }

    private boolean validXac(final int lacOrTac) {
        //TODO: seeing values of 65535 - value limit, but almost certainly invalid
        return (lacOrTac > 0) && (lacOrTac < Integer.MAX_VALUE);
    }

    /**
     * Operator name for an uploaded PLMN, nullsafe wrapper
     */
    public static String getOperatorName(final String operatorCode) {
        final MccMncRecord record = recordForOperator(operatorCode);
        if (record == null) {
            return null;
        }
        return record.getOperator();
    }

    /**
     * Operator record for a PLMN or a cell key such as "310260_253_22671".
     * Returns null when the token is missing *or* no candidate pair is known.
     */
    public static MccMncRecord recordForOperator(final String operatorId) {
        final String token = MccMnc.operatorToken(operatorId);
        if (token == null) {
            return null;
        }
        final String mcc = token.substring(0, 3);
        final Map<String, MccMncRecord> mccMap = cacheFor(mcc);
        for (final String candidate : MccMnc.mncCandidates(token.substring(3))) {
            if (mccMap.containsKey(candidate)) {
                final MccMncRecord cached = mccMap.get(candidate);
                if (cached != null) {
                    return cached;
                }
                continue;
            }
            final boolean[] cacheable = new boolean[] {false};
            final MccMncRecord record = lookupRecord(mcc, candidate, cacheable);
            if (cacheable[0]) {
                mccMap.put(candidate, record);
            }
            if (record != null) {
                return record;
            }
        }
        return null;
    }

    private static Map<String, MccMncRecord> cacheFor(final String mcc) {
        Map<String, MccMncRecord> mccMap = OPERATOR_CACHE.get(mcc);
        if (mccMap == null) {
            mccMap = new HashMap<>();
            OPERATOR_CACHE.put(mcc, mccMap);
        }
        return mccMap;
    }

    private static MccMncRecord lookupRecord(final String mcc, final String mnc, final boolean[] cacheable) {
        final MainActivity activity = MainActivity.getMainActivity();
        if (activity == null) {
            return null;
        }
        final MainActivity.State state = activity.getState();
        if (state == null || state.mxcDbHelper == null) {
            return null;
        }
        try {
            final MccMncRecord record = state.mxcDbHelper.networkRecordForMccMnc(mcc, mnc);
            cacheable[0] = true;
            return record;
        } catch (SQLiteDatabaseCorruptException sqldbex) {
            // this case seems isolated to LG android 4.4 devices
            Logging.warn("Mxc DB corrupt - unable to lookup carrier", sqldbex);
            state.mxcDbHelper.implantMxcDatabase(activity, false);
        } catch (SQLException sex) {
            Logging.error("Unable to access Mxc Database: ", sex);
        }
        return null;
    }

}
