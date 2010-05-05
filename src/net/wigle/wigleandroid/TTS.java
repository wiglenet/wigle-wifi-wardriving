// -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
// vim:ts=2:sw=2:tw=80:et

package net.wigle.wigleandroid;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;

import android.content.Context;

@SuppressWarnings("unchecked")
public final class TTS {
  private static Class SPEECH_CLASS;
  private static Class LISTENER_CLASS;

  // http://groups.google.com/group/tts-for-android/browse_thread/thread/a6db26ac63a5bbb3 "Using TTS Extended on Android 1.5"
  // we link against a copy of http://eyes-free.googlecode.com/svn/trunk/commonlibs/TTS_library_stub.jar
  /* 
     quote:

     I had great trouble, but finally got it working.

     Key lessons I learned:

     1.  Use Library stub 1.7 -- nothing later.  Later ones are
     incompatible with Android 1.5
     2.  Any APK for the extender versioned 1.7 to at least 2.0 seems to
     work
     3.  When using TTS on Android 1.5, you have to use the old TTS class,
     not TextToSpeechBeta
     4.  To get the end-of-speech callbacks to work, you have to set the
     listener FIRST thing in your init listener -- no later.
     
     These lessons were hard to discover and learn, and the documentation
     could easily be improved to minimize the ramp-up time. 
   */

  /** this had been a lovely interface/innerclass dance; but we do love us some hax 
      (if there is ever a third engine/case be sure to go and do this right, you bastard). 
      set true on the bad (pre-donut) path */
  private static boolean useEyesFree = false;
  /** hint on the 1.5 tts voice to use */
  private static String[] params = new String[]{"VOICE_FEMALE"};

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
    }
    catch ( ClassNotFoundException ex ) {
        // don't have it
    }
    // try the eyes-free TTS.
    if ( SPEECH_CLASS == null ) {
        try {
          SPEECH_CLASS = Class.forName("com.google.tts.TTS"); // naboo foxes, TTS out for the jedi!
            useEyesFree = true;
        } catch ( ClassNotFoundException ex ) {
            // don't have it
        }
    }

    if ( SPEECH_CLASS != null ) {
      try {
        for ( Class clazz : SPEECH_CLASS.getClasses() ) {
          String cname =  clazz.getCanonicalName();
          WigleAndroid.info("class: " +cname );
          if ( "android.speech.tts.TextToSpeech.OnInitListener".equals( cname )
               ||
               ( useEyesFree && "com.google.tts.TTS.InitListener".equals( cname ) ) 
               ) {
            LISTENER_CLASS = clazz;
            break;
          }
        }
      
        if ( useEyesFree ) {
          speak = SPEECH_CLASS.getMethod( "speak", String.class, int.class, String[].class );
          shutdown = SPEECH_CLASS.getMethod( "shutdown", new Class[]{} );
          QUEUE_ADD = 1;
        } else {
          QUEUE_FLUSH = SPEECH_CLASS.getDeclaredField("QUEUE_FLUSH").getInt(null);
          QUEUE_ADD = SPEECH_CLASS.getDeclaredField("QUEUE_ADD").getInt(null);
          speak = SPEECH_CLASS.getMethod( "speak", String.class, int.class, HashMap.class );
          shutdown = SPEECH_CLASS.getMethod( "shutdown", new Class[]{} );
        }
      }
      catch ( final NoSuchFieldException ex ) {
        WigleAndroid.error( "no such field: " + ex );
      }
      catch ( final IllegalAccessException ex ) {
        WigleAndroid.error( "illegal in static: " + ex );
      }
      catch ( final NoSuchMethodException ex ) {
        WigleAndroid.error( "no such method: " + ex );
      }
    }
    
  }
  
  public static boolean hasTTS() {
    return SPEECH_CLASS != null;
  }
  
  public TTS( final Context context ) {
    try {
      Constructor construct;
      if (useEyesFree) {
        construct = SPEECH_CLASS.getConstructor( Context.class, LISTENER_CLASS, boolean.class );
      } else {
        construct = SPEECH_CLASS.getConstructor( Context.class, LISTENER_CLASS );
      }
      final InvocationHandler handler = new InvocationHandler() {
        public Object invoke( Object object, Method method, Object[] args ) {
          WigleAndroid.info("invoke: " + method.getName() );
          return null;
        }
      };
      listener = Proxy.newProxyInstance(LISTENER_CLASS.getClassLoader(), 
          new Class[]{ LISTENER_CLASS }, handler);
      
      if ( useEyesFree ) {
        speech = construct.newInstance( context, listener, true );
      } else {
        speech = construct.newInstance( context, listener );
      }
      //Method setLocation = SPEECH_CLASS.getMethod( "setLanguage", Locale.class );
      //setLocation.invoke( speech, Locale.UK );
    }
    catch ( final NoSuchMethodException ex ) {
      WigleAndroid.error( "no such method: " + ex );
    }
    catch ( final IllegalAccessException ex ) {
      WigleAndroid.error( "illegal: " + ex );
    }
    catch ( final InstantiationException ex ) {
      WigleAndroid.error( "instantiation: " + ex );
    }
    catch ( final InvocationTargetException ex ) {
      WigleAndroid.error( "invocation: " + ex );
    }
  }
  
  public void speak( final String string ) {
    try {
      // WigleAndroid.info("saying: " + string );
      if ( useEyesFree ) {
        speak.invoke( speech, string, QUEUE_ADD, params );
      } else {
        speak.invoke( speech, string, QUEUE_ADD, (HashMap<String,String>) null );
      }
    }
    catch ( final IllegalAccessException ex ) {
      WigleAndroid.error( "illegal: " + ex );
    }
    catch ( final InvocationTargetException ex ) {
      WigleAndroid.error( "invocation: " + ex );
    }
  }
  
  public void shutdown() {
    try {
      // WigleAndroid.info("saying: " + string );
      shutdown.invoke( speech, (Object[])null );
    }
    catch ( final IllegalAccessException ex ) {
      WigleAndroid.error( "illegal: " + ex );
    }
    catch ( final InvocationTargetException ex ) {
      WigleAndroid.error( "invocation: " + ex );
    }
  }

}
