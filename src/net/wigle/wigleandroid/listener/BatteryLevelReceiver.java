package net.wigle.wigleandroid.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * keep track of battery
 */
public final class BatteryLevelReceiver extends BroadcastReceiver {
  private int batteryLevel = -1;
  
  public BatteryLevelReceiver() {
  }
  
  @Override
  public void onReceive(Context context, Intent intent) {
    int rawlevel = intent.getIntExtra("level", -1);
    int scale = intent.getIntExtra("scale", -1);
    int level = -1;
    if (rawlevel >= 0 && scale > 0) {
      level = (rawlevel * 100) / scale;
    }
    batteryLevel = level;
  }
  
  public int getBatteryLevel() {
    return batteryLevel;
  }
}
