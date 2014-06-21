package net.wigle.wigleandroid;

import android.support.v4.app.FragmentActivity;

public interface DialogListener {
  public void handleDialog(int dialogId);
  public FragmentActivity getActivity();
}
