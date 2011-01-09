package net.wigle.wigleandroid.listener;

import net.wigle.wigleandroid.ListActivity;

public final class PhoneStateFactory {
  private PhoneStateFactory() {}
  
  public static PhoneState createPhoneState() {
    try {
      Class.forName("android.telephony.SignalStrength");
      return new PhoneState7();
    } catch (Exception ex) {
      ListActivity.info("Using original PhoneState");
    }
    return new PhoneState();
  }
}
