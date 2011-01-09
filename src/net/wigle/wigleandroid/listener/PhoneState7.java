package net.wigle.wigleandroid.listener;

import android.telephony.SignalStrength;

public final class PhoneState7 extends PhoneState {
  @Override
  public void onSignalStrengthsChanged (SignalStrength signalStrength) {
    // ListActivity.info("signalStrength: " + signalStrength);
    if (signalStrength.isGsm()) {
      strength = signalStrength.getGsmSignalStrength();
    }
    else {
      strength = signalStrength.getCdmaDbm();
    }
  }  
  
  @Override
  public void onSignalStrengthChanged(final int asu) {
    // do nothing
  }
}
