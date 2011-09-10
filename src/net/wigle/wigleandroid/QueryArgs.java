package net.wigle.wigleandroid;

import android.location.Address;

public class QueryArgs {
  private Address address;
  private String ssid;
  
  public Address getAddress() {
    return address;
  }
  public void setAddress(Address address) {
    this.address = address;
  }
  
  public String getSSID() {
    return ssid;
  }
  public void setSSID(String ssid) {
    this.ssid = ssid;
  }
}
