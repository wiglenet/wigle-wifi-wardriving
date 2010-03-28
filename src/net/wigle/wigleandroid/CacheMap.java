package net.wigle.wigleandroid;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU cache
 */
public class CacheMap<K,V> extends LinkedHashMap<K,V> {
  private static final long serialVersionUID = 2010032701L;
  private final int maxEntries;
  
  public CacheMap( int initialCapacity, int maxEntries ) {
    super( initialCapacity, 0.75f, true );
    this.maxEntries = maxEntries;
  }
  
  protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
    return size() > maxEntries;
  }

}
