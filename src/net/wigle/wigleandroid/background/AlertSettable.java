package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.background.BackgroundGuiHandler.BackgroundAlertDialog;

public interface AlertSettable {
  public void setAlertDialog(final BackgroundAlertDialog ad);
  public void clearProgressDialog();
}
