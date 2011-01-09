package net.wigle.wigleandroid.listener;

import net.wigle.wigleandroid.ListActivity;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

public class PhoneState extends PhoneStateListener {
  private boolean isPhoneActive = false;
  protected int strength = 0;
  private ServiceState serviceState;
  
  @Override
  public void onCallStateChanged( int state, String incomingNumber ) {
    switch ( state ) {
      case TelephonyManager.CALL_STATE_IDLE:
        isPhoneActive = false;
        ListActivity.info( "setting phone inactive. state: " + state );
        break;
      case TelephonyManager.CALL_STATE_RINGING:
      case TelephonyManager.CALL_STATE_OFFHOOK:
        isPhoneActive = true;
        ListActivity.info( "setting phone active. state: " + state );
        break;
      default:
        ListActivity.info( "unhandled call state: " + state );
    }
  }
  
  @Override
  public void onServiceStateChanged(ServiceState serviceState) {
    // ListActivity.info("serviceState: " + serviceState);
    this.serviceState = serviceState;
  }
  
  @Override
  public void onSignalStrengthChanged(final int asu) {
    // ListActivity.info("strength: " + asu);
    strength = asu;
  }
  
  @Override
  public void onCellLocationChanged(CellLocation cellLoc){  
    if ( cellLoc.getClass().getSimpleName().equals("CdmaCellLocation") ) {
      ListActivity.info("cell location changed: cdma: " + cellLoc);
    }
    else if ( cellLoc instanceof GsmCellLocation) {
      GsmCellLocation gsmCell = (GsmCellLocation) cellLoc;
      ListActivity.info("cell location changed: gsm Cid: " + gsmCell.getCid());
      ListActivity.info("cell location changed: gsm Lac: " + gsmCell.getLac());
    }
  }
  
  public boolean isPhoneActive() {
    return isPhoneActive;
  }
  
  public int getStrength() {
    return strength;
  }
  
  public ServiceState getServiceState() {
    return serviceState;
  }
}
