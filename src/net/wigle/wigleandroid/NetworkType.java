package net.wigle.wigleandroid;

import java.util.HashMap;
import java.util.Map;

public enum NetworkType {
  WIFI("W"),
  GSM("G"),
  CDMA("C");
  
  public static Map<String,NetworkType> types = new HashMap<String,NetworkType>();
  
  static {
    for( NetworkType type : NetworkType.values() ) {
      types.put( type.getCode(), type );
    }
  }
  
  private final String code;
  private NetworkType(final String code) {
    this.code = code;
  }
  
  public String getCode() {
    return code;
  }
  
  public static NetworkType typeForCode(final String code) {
    return types.get(code);
  }
}
