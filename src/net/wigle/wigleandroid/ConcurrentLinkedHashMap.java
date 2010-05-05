package net.wigle.wigleandroid;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConcurrentLinkedHashMap<K,V> {
  private final ConcurrentHashMap<K,V> map;
  private final ConcurrentLinkedQueue<K> queue;
  private final AtomicInteger count = new AtomicInteger();
  
  public ConcurrentLinkedHashMap() {
    map = new ConcurrentHashMap<K,V>();
    queue = new ConcurrentLinkedQueue<K>();
  }
}
