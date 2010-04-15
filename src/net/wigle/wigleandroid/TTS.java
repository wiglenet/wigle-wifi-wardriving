package net.wigle.wigleandroid;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;

import android.content.Context;

@SuppressWarnings("unchecked")
public class TTS {
  private static Class SPEECH_CLASS;
  private static Class LISTENER_CLASS;
  
  @SuppressWarnings("unused")
  private static int QUEUE_FLUSH;
  private static int QUEUE_ADD;
  private static Method speak;
  private static Method shutdown;
  
  private Object speech;
  private Object listener;
  
  static {
    try {
      SPEECH_CLASS = Class.forName("android.speech.tts.TextToSpeech");
      for ( Class clazz : SPEECH_CLASS.getClasses() ) {
        WigleAndroid.info("class: " + clazz.getCanonicalName() );
        if ( "android.speech.tts.TextToSpeech.OnInitListener".equals( clazz.getCanonicalName() ) ) {
          LISTENER_CLASS = clazz;
          break;
        }
      }      
      
      QUEUE_FLUSH = SPEECH_CLASS.getDeclaredField("QUEUE_FLUSH").getInt(null);
      QUEUE_ADD = SPEECH_CLASS.getDeclaredField("QUEUE_ADD").getInt(null);
      speak = SPEECH_CLASS.getMethod( "speak", String.class, int.class, HashMap.class );
      shutdown = SPEECH_CLASS.getMethod( "shutdown", new Class[]{} );
    }
    catch ( ClassNotFoundException ex ) {
      // don't have it
    }
    catch ( NoSuchFieldException ex ) {
      WigleAndroid.error( "no such field: " + ex );
    }
    catch ( IllegalAccessException ex ) {
      WigleAndroid.error( "illegal in static: " + ex );
    }
    catch ( NoSuchMethodException ex ) {
      WigleAndroid.error( "no such method: " + ex );
    }
    
  }
  
  public static boolean hasTTS() {
    return SPEECH_CLASS != null;
  }
  
  public TTS( Context context ) {
    try {
      Constructor construct = SPEECH_CLASS.getConstructor( Context.class, LISTENER_CLASS );
      InvocationHandler handler = new InvocationHandler() {
        public Object invoke( Object object, Method method, Object[] args ) {
          WigleAndroid.info("invoke: " + method.getName() );
          return null;
        }
      };
      listener = Proxy.newProxyInstance(LISTENER_CLASS.getClassLoader(), 
          new Class[]{ LISTENER_CLASS }, handler);
      
      speech = construct.newInstance( context, listener );
      //Method setLocation = SPEECH_CLASS.getMethod( "setLanguage", Locale.class );
      //setLocation.invoke( speech, Locale.UK );
    }
    catch ( NoSuchMethodException ex ) {
      WigleAndroid.error( "no such method: " + ex );
    }
    catch ( IllegalAccessException ex ) {
      WigleAndroid.error( "illegal: " + ex );
    }
    catch ( InstantiationException ex ) {
      WigleAndroid.error( "instantiation: " + ex );
    }
    catch ( InvocationTargetException ex ) {
      WigleAndroid.error( "invocation: " + ex );
    }
  }
  
  public void speak( String string ) {
    try {
      // WigleAndroid.info("saying: " + string );
      speak.invoke( speech, string, QUEUE_ADD, (HashMap<String,String>) null );
    }
    catch ( IllegalAccessException ex ) {
      WigleAndroid.error( "illegal: " + ex );
    }
    catch ( InvocationTargetException ex ) {
      WigleAndroid.error( "invocation: " + ex );
    }
  }
  
  public void shutdown() {
    try {
      // WigleAndroid.info("saying: " + string );
      shutdown.invoke( speech, new Object[]{} );
    }
    catch ( IllegalAccessException ex ) {
      WigleAndroid.error( "illegal: " + ex );
    }
    catch ( InvocationTargetException ex ) {
      WigleAndroid.error( "invocation: " + ex );
    }
  }

}
