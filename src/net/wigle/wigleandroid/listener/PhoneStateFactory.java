package net.wigle.wigleandroid.listener;

import net.wigle.wigleandroid.MainActivity;

public final class PhoneStateFactory {
  private PhoneStateFactory() {}
  
  public static PhoneState createPhoneState() {
    try {
      Class.forName("android.telephony.SignalStrength");
      MainActivity.info("Using PhoneState7");
      return new PhoneState7();
    } catch (Exception ex) {
      MainActivity.info("Using original PhoneState");
    }
    return new PhoneState();
  }
}
