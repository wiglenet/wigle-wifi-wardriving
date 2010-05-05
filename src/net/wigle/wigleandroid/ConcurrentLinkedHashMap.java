package net.wigle.wigleandroid;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * try to be all things to all people. concurrent, with a remove-eldest feature, except during put, 
 * which is usually single-threaded in this app anyway.
 * @param <K> key
 * @param <V> value
 */
public final class ConcurrentLinkedHashMap<K,V> {
  private final ConcurrentHashMap<K,V> map;
  private final ConcurrentLinkedQueue<K> queue;
  private int count = 0;
  
  private final int maxSize;
  private static final Object WRITE_LOCK = new Object();
  
  public ConcurrentLinkedHashMap() {
    this( Integer.MAX_VALUE );
  }
  
  public ConcurrentLinkedHashMap( final int maxSize ) {
    map = new ConcurrentHashMap<K,V>();
    queue = new ConcurrentLinkedQueue<K>();
    this.maxSize = maxSize;
  }
  
  public V put( final K key, final V value ) {
    V previous = null;
    synchronized( WRITE_LOCK ) {
      previous = map.put(key, value);
      if ( previous != null ) {
        // new key! add to queue
        queue.add( key );
        // check if this puts us over
        if ( count + 1 > maxSize ) {
          // remove eldest
          K eldest = queue.remove();
          map.remove( eldest );
        }
        else {
          // this doesn't put us over, just add to the count
          count++;
        }
      }
    }
    return previous;
  }
  
  public V get( K key ) {
    return map.get( key );
  }
  
  public Set<Map.Entry<K,V>> entrySet() {
    return map.entrySet();
  }
  
  public int size() {
    return count; 
  }
  
}
