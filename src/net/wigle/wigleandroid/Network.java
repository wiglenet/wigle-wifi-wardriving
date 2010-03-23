package net.wigle.wigleandroid;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.location.Location;
import android.net.wifi.ScanResult;

/**
 * get/set observations is synchronized, so this class should be thread-safe
 */
public class Network {
  private final String bssid;
  private final String ssid;
  private final int frequency;
  private final String capabilities;
  private int level;
  private final Set<Observation> observations = new HashSet<Observation>();
  private final Integer channel;
  private final String showCapabilities;
  
  private static final Map<Integer,Integer> freqToChan;
  static {
    Map<Integer,Integer> freqToChanTemp = new HashMap<Integer,Integer>();
    
    freqToChanTemp.put(2412, 1);
    freqToChanTemp.put(2417, 2);
    freqToChanTemp.put(2422, 3);
    freqToChanTemp.put(2427, 4);
    freqToChanTemp.put(2432, 5);
    freqToChanTemp.put(2437, 6);
    freqToChanTemp.put(2442, 7);
    freqToChanTemp.put(2447, 8);
    freqToChanTemp.put(2452, 9);
    freqToChanTemp.put(2457, 10);
    freqToChanTemp.put(2462, 11);
    freqToChanTemp.put(2467, 12);
    freqToChanTemp.put(2472, 13);
    freqToChanTemp.put(2484, 14);
    
    freqToChanTemp.put(5170, 34);
    freqToChanTemp.put(5180, 36);
    freqToChanTemp.put(5190, 38);
    freqToChanTemp.put(5200, 40);
    freqToChanTemp.put(5210, 42);
    freqToChanTemp.put(5220, 44);
    freqToChanTemp.put(5230, 46);
    freqToChanTemp.put(5240, 48);
    freqToChanTemp.put(5260, 52);
    freqToChanTemp.put(5280, 56);
    freqToChanTemp.put(5300, 58);
    freqToChanTemp.put(5320, 60);
    
    freqToChanTemp.put(5500, 100);
    freqToChanTemp.put(5520, 104);
    freqToChanTemp.put(5540, 108);
    freqToChanTemp.put(5560, 112);
    freqToChanTemp.put(5570, 116);
    freqToChanTemp.put(5600, 120);
    freqToChanTemp.put(5620, 124);
    freqToChanTemp.put(5640, 128);
    freqToChanTemp.put(5660, 132);
    freqToChanTemp.put(5680, 136);
    freqToChanTemp.put(5700, 140);
    
    freqToChanTemp.put(5745, 149);
    freqToChanTemp.put(5765, 153);
    freqToChanTemp.put(5785, 157);
    freqToChanTemp.put(5805, 161);
    freqToChanTemp.put(5825, 165);
    
    freqToChan = Collections.unmodifiableMap( freqToChanTemp );
  }
  
  public Network( ScanResult scanResult ) {
    this.bssid = scanResult.BSSID;
    this.ssid = scanResult.SSID;
    this.frequency = scanResult.frequency;
    this.capabilities = scanResult.capabilities;
    this.level = scanResult.level;
    this.channel = freqToChan.get( frequency );
    
    if ( capabilities.length() > 16 ) {
      this.showCapabilities = capabilities.replaceAll("(\\[\\w+)\\-.*?\\]", "$1...]");
    }
    else {
      this.showCapabilities = null;
    }
  }
  
  public String getBssid() {
    return bssid;
  }

  public String getSsid() {
    return ssid;
  }

  public int getFrequency() {
    return frequency;
  }

  public String getCapabilities() {
    return capabilities;
  }
  
  public String getShowCapabilities() {
    if ( showCapabilities == null ) {
      return capabilities;
    }
    return showCapabilities;
  }

  public int getLevel() {
    return level;
  }
  
  /**
   * thread-safely copies observations and returns them
   * @return copy of observations
   */
  public Set<Observation> getObservations() {
    Set<Observation> retval = null;
    synchronized ( observations ) {
      retval = new HashSet<Observation>( observations );
    }
    return retval;
  }
  
  public Integer getChannel() {
    return channel;
  }
  
  public void addObservation( int level, Location location ) {
    this.level = level;
    
    if ( location != null ) {
      Observation observation = new Observation( level, location );
      synchronized ( observations ) {
        observations.add( observation );
      }
    }
  }

}
