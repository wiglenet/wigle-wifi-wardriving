package net.wigle.wigleandroid.listener;

import net.wigle.wigleandroid.ListActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class PhoneState extends PhoneStateListener {
  private boolean isPhoneActive = false;
  
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
 
  public boolean isPhoneActive() {
    return isPhoneActive;
  }
}
