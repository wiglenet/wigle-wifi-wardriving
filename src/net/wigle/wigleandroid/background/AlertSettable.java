package net.wigle.wigleandroid.background;

import android.app.AlertDialog;

public interface AlertSettable {
  public void setAlertDialog(final AlertDialog ad);     
  public void clearProgressDialog();
}
