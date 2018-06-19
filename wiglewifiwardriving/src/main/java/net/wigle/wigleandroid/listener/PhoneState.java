package net.wigle.wigleandroid.listener;

import net.wigle.wigleandroid.MainActivity;

import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

import java.util.List;

public class PhoneState extends PhoneStateListener {
    private boolean isPhoneActive = false;
    protected int strength = 0;

    @Override
    public void onCallStateChanged( int state, String incomingNumber ) {
        switch ( state ) {
            case TelephonyManager.CALL_STATE_IDLE:
                isPhoneActive = false;
                MainActivity.info( "setting phone inactive. state: " + state );
                break;
            case TelephonyManager.CALL_STATE_RINGING:
            case TelephonyManager.CALL_STATE_OFFHOOK:
                isPhoneActive = true;
                MainActivity.info( "setting phone active. state: " + state );
                break;
            default:
                MainActivity.info( "unhandled call state: " + state );
        }
    }

    @Override
    public void onSignalStrengthsChanged (SignalStrength signalStrength) {
        // ListActivity.info("signalStrength: " + signalStrength);
        if (signalStrength.isGsm()) {
            strength = signalStrength.getGsmSignalStrength();
            //DEBUG: MainActivity.info("==> GSM SS: "+strength);
        }
        else {
            strength = signalStrength.getCdmaDbm();
        }
    }

    @Override
    public void onCellLocationChanged(CellLocation cellLoc){
        if ( cellLoc.getClass().getSimpleName().equals("CdmaCellLocation") ) {
            MainActivity.info("cell location changed: cdma: " + cellLoc);
        }
        else if ( cellLoc instanceof GsmCellLocation) {
            GsmCellLocation gsmCell = (GsmCellLocation) cellLoc;
            MainActivity.info("cell location changed: gsm Cid: " + gsmCell.getCid());
            MainActivity.info("cell location changed: gsm Lac: " + gsmCell.getLac());
        } else if (cellLoc != null) {
            MainActivity.info("cell location changed on unknown class: "+cellLoc.getClass().getSimpleName());
        }
    }

    @Override
    public void onCellInfoChanged(List<CellInfo> cellInfo) {
        if (cellInfo != null) {
            for (CellInfo info: cellInfo) {
                MainActivity.info("Cell Info Change:"+info.toString());
            }
        }
    }

    public boolean isPhoneActive() {
        return isPhoneActive;
    }

    public int getStrength() {
        return strength;
    }
}
