// -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
// vim:ts=2:sw=2:tw=80:et

package net.wigle.wigleandroid;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;

import android.content.Context;

@SuppressWarnings({ "rawtypes", "unchecked" })
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

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static int QUEUE_FLUSH;
    private static int QUEUE_ADD;
    private static Method speak;
    private static Method stop;
    private static Method shutdown;
    private static Method setlanguage;
    private static Method getlanguage;

    private Object speech;

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
                    MainActivity.info("class: " +cname );
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
                    stop = SPEECH_CLASS.getMethod( "stop");
                    shutdown = SPEECH_CLASS.getMethod( "shutdown");
                    QUEUE_ADD = 1;
                    setlanguage = SPEECH_CLASS.getMethod( "setLanguage", String.class );
                } else {
                    QUEUE_FLUSH = SPEECH_CLASS.getDeclaredField("QUEUE_FLUSH").getInt(null);
                    QUEUE_ADD = SPEECH_CLASS.getDeclaredField("QUEUE_ADD").getInt(null);
                    speak = SPEECH_CLASS.getMethod( "speak", String.class, int.class, HashMap.class );
                    stop = SPEECH_CLASS.getMethod( "stop");
                    shutdown = SPEECH_CLASS.getMethod( "shutdown");
                    setlanguage = SPEECH_CLASS.getMethod( "setLanguage", Locale.class );
                    getlanguage = SPEECH_CLASS.getMethod( "getLanguage");
                }
            }
            catch ( final NoSuchFieldException ex ) {
                MainActivity.error( "no such field: " + ex, ex );
            }
            catch ( final IllegalAccessException ex ) {
                MainActivity.error( "illegal in static: " + ex, ex );
            }
            catch ( final NoSuchMethodException ex ) {
                MainActivity.error( "no such method: " + ex, ex );
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
                    MainActivity.info("invoke: " + method.getName() );
                    // call our init to set the language to engrish.
                    onInit();
                    return null;
                }
            };
            Object listener = Proxy.newProxyInstance(LISTENER_CLASS.getClassLoader(),
                    new Class[]{LISTENER_CLASS}, handler);

            if ( useEyesFree ) {
                speech = construct.newInstance( context, listener, true );
                // hrm. do we need this?
                // PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("toggle_use_default_tts_settings",0).commit();
            } else {
                speech = construct.newInstance( context, listener);
            }
        }
        catch ( final NoSuchMethodException ex ) {
            MainActivity.error( "no such method: " + ex, ex );
        }
        catch ( final IllegalAccessException ex ) {
            MainActivity.error( "illegal: " + ex, ex );
        }
        catch ( final InstantiationException ex ) {
            MainActivity.error( "instantiation: " + ex, ex );
        }
        catch ( final InvocationTargetException ex ) {
            MainActivity.error( "invocation: " + ex, ex );
        }
    }

    public void onInit() {
        try {
            if ( useEyesFree ) {
                setlanguage.invoke( speech, "en-US" ); // english, motherfucker. do you speak it? // XXX: should this be "eng-USA" ?
            } else {
                Object loc = getlanguage.invoke( speech );
                boolean doLanguage = true;
                if ( loc != null && loc instanceof Locale ) {
                    Locale locale = (Locale) loc;
                    // these are cool, no need to change
                    if ( Locale.US.equals( locale )
                            || Locale.CANADA.equals( locale )
                            || Locale.UK.equals( locale )
                            || Locale.ENGLISH.getLanguage().equals( locale.getLanguage() )
                            || "eng".equals( locale.getLanguage() )) {
                        doLanguage = false;
                    }
                    MainActivity.info("locale: " + locale + " doLanguage: " + doLanguage + " lang: " + locale.getLanguage() );
                }

                if ( doLanguage ) {
                    setlanguage.invoke( speech, Locale.US ); // english, motherfucker. do you speak it?
                }
            }
            //      MainActivity.info("should be talkin' english now");
        } catch ( final IllegalAccessException ex ) {
            MainActivity.error( "init illegal: " + ex, ex );
        } catch ( final InvocationTargetException ex ) {
            MainActivity.error( "init invocation: " + ex, ex );
        }
    }

    public void speak( final String string ) {
        try {
            // MainActivity.info("saying: " + string );
            if ( speech != null ) {
                if ( useEyesFree ) {
                    speak.invoke( speech, string, QUEUE_ADD, params );
                } else {
                    speak.invoke( speech, string, QUEUE_ADD, null);
                }
            }
        }
        catch ( final IllegalAccessException ex ) {
            MainActivity.error( "illegal: " + ex, ex );
        }
        catch ( final InvocationTargetException ex ) {
            MainActivity.error( "invocation: " + ex, ex );
        }
        catch ( final NullPointerException ex ) {
            MainActivity.error( "npe: " + ex, ex );
        }
    }

    public void stop() {
        try {
            // MainActivity.info( "tts: stop" );
            if ( speech != null ) {
                stop.invoke( speech, (Object[])null );
            }
        }
        catch ( final IllegalAccessException ex ) {
            MainActivity.error( "illegal: " + ex, ex );
        }
        catch ( final InvocationTargetException ex ) {
            MainActivity.error( "invocation: " + ex, ex );
        }
    }

    public void shutdown() {
        try {
            // MainActivity.info( "tts: shutdown" );
            if ( speech != null ) {
                shutdown.invoke( speech, (Object[])null );
            }
        }
        catch ( final IllegalAccessException ex ) {
            MainActivity.error( "illegal: " + ex, ex );
        }
        catch ( final InvocationTargetException ex ) {
            MainActivity.error( "invocation: " + ex, ex );
        }
    }

}
