package net.wigle.wigleandroid.model;

import android.annotation.TargetApi;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.os.Build;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.util.InsufficientSpaceException;
import net.wigle.wigleandroid.util.Logging;

import java.util.HashMap;
import java.util.Map;

public class GsmOperator {

    private static final String KEY_SEP = "_";

    private String mcc;
    private String mnc;
    private int xac;
    private int cellId;
    private final int fcn;

    private static Map<String, Map<String,String>> OPERATOR_CACHE;
    static {
        OPERATOR_CACHE = new HashMap<>();
    }


    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
    public GsmOperator(final CellIdentityGsm cellIdentG) throws GsmOperatorException {
        cellId = cellIdentG.getCid();
        xac = cellIdentG.getLac();
        mcc = android.os.Build.VERSION.SDK_INT >= 28?cellIdentG.getMccString():cellIdentG.getMcc()+"";
        mnc = android.os.Build.VERSION.SDK_INT >= 28?cellIdentG.getMncString():determineMnc(cellIdentG.getMnc(), mcc);

        fcn = android.os.Build.VERSION.SDK_INT >= 24 && cellIdentG.getArfcn() != Integer.MAX_VALUE ? cellIdentG.getArfcn() : 0;

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

            if (android.os.Build.VERSION.SDK_INT >= 24) {
                res += "\n\tBSIC: " + cellIdentG.getBsic();
            }
        }
        if (!validCellId(this.cellId) || !validXac(xac) || !validMccMnc(mcc,mnc)) {
            if (MainActivity.DEBUG_CELL_DATA) {
                if (android.os.Build.VERSION.SDK_INT >= 24) {
                    Logging.info("Discarding GSM cell with invalid ID for ARFCN: " + cellIdentG.getArfcn());
                } else {
                    Logging.info("Discarding GSM cell with invalid ID");
                }
            }
            throw new GsmOperatorException("invalid GSM Cell Identity values: "+getOperatorKeyString());
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
    public GsmOperator(final CellIdentityLte cellIdentL) throws GsmOperatorException{
        cellId = cellIdentL.getCi();
        xac = cellIdentL.getTac();
        mcc = android.os.Build.VERSION.SDK_INT >= 28?cellIdentL.getMccString():cellIdentL.getMcc()+"";
        mnc = android.os.Build.VERSION.SDK_INT >= 28?cellIdentL.getMncString():determineMnc(cellIdentL.getMnc(), mcc);

        fcn = android.os.Build.VERSION.SDK_INT >= 24 && cellIdentL.getEarfcn() != Integer.MAX_VALUE ? cellIdentL.getEarfcn() : 0;
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


        if (!validCellId(this.cellId) || !validXac(xac) || !validMccMnc(mcc,mnc)) {
            if (MainActivity.DEBUG_CELL_DATA) {
                if (android.os.Build.VERSION.SDK_INT >= 24) {
                    Logging.info("Discarding LTE cell with invalid ID for EARFCN: " + cellIdentL.getEarfcn());
                } else {
                    Logging.info("Discarding LTE cell with invalid ID");
                }
            }
            throw new GsmOperatorException("invalid LTE Cell Identity values "+getOperatorKeyString());
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)
    public GsmOperator(final CellIdentityWcdma cellIdentW) throws GsmOperatorException {
        cellId = cellIdentW.getCid();
        xac = cellIdentW.getLac();
        mcc = android.os.Build.VERSION.SDK_INT >= 28 ? cellIdentW.getMccString() : cellIdentW.getMcc() + "";
        mnc = android.os.Build.VERSION.SDK_INT >= 28 ? cellIdentW.getMncString() : determineMnc(cellIdentW.getMnc(), mcc);

        fcn = android.os.Build.VERSION.SDK_INT >= 24 && cellIdentW.getUarfcn() != Integer.MAX_VALUE ? cellIdentW.getUarfcn() : 0;

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

        if (!validCellId(this.cellId) || !validXac(xac) || !validMccMnc(mcc, mnc)) {
            if (MainActivity.DEBUG_CELL_DATA) {
                if (android.os.Build.VERSION.SDK_INT >= 24) {
                    Logging.info("Discarding WCDMA cell with invalid ID for UARFCN: " + cellIdentW.getUarfcn());
                } else {
                    Logging.info("Discarding WCDMA cell with invalid ID");
                }
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

    private boolean validCellId(final int cellId) {
        if ((cellId > 0) && (cellId < Integer.MAX_VALUE)) {
            return true;
        }
        return false;
    }

    private boolean validXac(final int lacOrTac) {
        //TODO: seeing values of 65535 - value limit, but almost certainly invalid
        if ((lacOrTac > 0) && (lacOrTac < Integer.MAX_VALUE)) {
            return true;
        }
        return false;
    }

    private String determineMnc(final int mncInt, final String mcc) {
        if (mncInt > 99) {
            //safe
            return mncInt+"";
        }
        String mncBase = ""+mncInt;
        if (mncInt < 10) {
            mncBase = "0"+mncBase;
        }
        if (recognizedMnc(mcc, mncBase)) {
            return mncBase;
        } else {
            mncBase = "0"+mncBase;
            if (recognizedMnc(mcc, mncBase)) {
                return mncBase;
            }
        }

        return null;
    }

    private boolean validMccMnc(final String mcc, final String mnc) {
        try {
            int mccInt = Integer.parseInt(mcc);
            int mncInt = Integer.parseInt(mnc);
            if (validMccMncValues(mccInt, mncInt)) {
                return true;
            }
        } catch (Exception ex) {

        }
        return false;
    }

    private boolean validMccMncValues(final int mcc, final int mnc) {
        if ((mcc > 0) && (mcc < 1000) && (mnc > 0) && (mnc < 1000)) {
            return true;
        }
        return false;
    }

    /**
     * Map the 5-6 digit operator PLMN code against the database of operator names
     * @param operatorCode
     * @return
     */
    public static String getOperatorName(final String operatorCode) {
        //ALIBI: MCC is always 3 chars, MNC may be 2 or 3.
        if (null != operatorCode && operatorCode.length() >= 5) {


            final String mnc = operatorCode.substring(3, operatorCode.length());
            final String mcc = operatorCode.substring(0, 3);
            //DEBUG:  MainActivity.info("Operator MCC: "+mcc+" MNC: "+mnc);

            Map<String, String> mccMap = OPERATOR_CACHE.get(mcc);
            if (null == mccMap) {
                mccMap = new HashMap<>();
                OPERATOR_CACHE.put(mcc, mccMap);
            }

            String operator = mccMap.get(mnc);
            if (null != operator) {
                //DEBUG: MainActivity.info("matched operator: "+operator+" ("+mcc+":"+mnc+")");
                return operator;
            }

            MainActivity.State s = MainActivity.getMainActivity().getState();
            if (null != s) {
                operator = s.mxcDbHelper.networkNameForMccMnc(mcc,mnc);
                mccMap.put(mnc, operator);
                return operator;
            }
        }
        return null;
    }

    private static boolean recognizedMnc(final String mcc, final String mnc) {
        if (null != mcc && null != mnc && mcc.length() == 3 && mnc.length() > 1) {
            Map<String, String> mccMap = OPERATOR_CACHE.get(mcc);
            if (null == mccMap) {
                mccMap = new HashMap<>();
                OPERATOR_CACHE.put(mcc, mccMap);
            }

            String operator = mccMap.get(mnc);
            if (null != operator) {
                //DEBUG: MainActivity.info("matched operator L1: "+operator+" ("+mcc+":"+mnc+")");
                return true;
            }
            MainActivity.State s = MainActivity.getMainActivity().getState();
            if ((null != s) && (null != s.mxcDbHelper)) {
                try {
                    operator = s.mxcDbHelper.networkNameForMccMnc(mcc, mnc);
                } catch (SQLiteDatabaseCorruptException sqldbex) {
                    // this case seems isolated to LG android 4.4 devices
                    Logging.warn("Mxc DB corrupt - unable to lookup carrier", sqldbex);
                    try {
                        //recopy the MccMnc DB file to see whether we can recover.
                        s.mxcDbHelper.implantMxcDatabase();
                        //TODO: too aggressive? operator = s.mxcDbHelper.networkNameForMccMnc(mcc, mnc);
                    } catch (InsufficientSpaceException sex) {
                        Logging.error("GSMOp implant failed: ",sex);
                    } catch (Exception ex) {
                        Logging.warn("Mxc DB recopy failed", ex);
                    }
                }
                if (operator != null) {
                    //DEBUG: MainActivity.info("matched operator L2: "+operator+" ("+mcc+":"+mnc+")");
                    mccMap.put(mnc, operator);
                    return true;
                }
            }
        }
        return false;
    }

}
