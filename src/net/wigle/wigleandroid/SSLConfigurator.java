// -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
// vim:ts=2:sw=2:tw=80:et

// $Id$
/* 
 * Copyright (c) 2003-2010, Hugh Kennedy
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. Neither the name of the WiGLE.net nor Mimezine nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.wigle.wigleandroid;

import android.content.res.Resources; 

import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;

// for ssl:
//
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;


import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;


/**
 * utility class for configuring ssl for the wigle self-signed cert.
 * separate class to avoid classloading for non-ssl clients.
 */
public final class SSLConfigurator {

  /** the default server cert file (must be included in the apk) for the API */
  // "res/raw/ssl.crt";

  /** lock for static config */
  private static final Object lock = new Object();
  
  /** factory instance */
  private static SSLConfigurator config = null;

  /** our self-signed client socket factory */
  private SSLSocketFactory ssf = null;

  /** hostname verifier confirms the server is ours */
  private HostnameVerifier hv = null; 

  /** default constructor sets up an SSLConfigurator */
  SSLConfigurator( Resources res ) {
     setupSSL( res );
  }

  /**
   * factory method.
   * @return an instance 
   */
  public static SSLConfigurator getInstance( Resources res ) {
     synchronized ( lock ) {
       if ( null == config ) {
          config = new SSLConfigurator( res );
       }
       
       return config;
     }
  }

  /**
   * set the socket factory and verifier for urlConn
   * @param urlConn the HttpsURLConnection to set up
   */
  public void configure( HttpsURLConnection urlConn ) {
        urlConn.setSSLSocketFactory( ssf );
        urlConn.setHostnameVerifier( hv );
  }


  /**
   * do the dirty work.
   * @parma res the android Resources to load the cert via.
   */
  private boolean setupSSL( Resources res ) {
    boolean result = false;
    try {

      // GET CERT GOO FROM R.raw
      InputStream certstream = res.openRawResource( R.raw.ssl );
      
      CertificateFactory cf = CertificateFactory.getInstance( "X.509" );

      java.security.cert.Certificate cert = cf.generateCertificate( certstream );

      KeyStore ks = KeyStore.getInstance( KeyStore.getDefaultType() );
      ks.load( null, null );
      ks.setCertificateEntry( "wigle.net", cert );
     
      TrustManagerFactory tmf = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
      tmf.init( ks );
      SSLContext ssc = SSLContext.getInstance( "TLSv1" );
      ssc.init( null, tmf.getTrustManagers(), null );
      ssf = ssc.getSocketFactory();
      hv = new ReflexiveHostnameVerifier( cert ); // XXX: make less dumb
      result = true;  
    } catch ( IOException e ) {
        WigleAndroid.error( "Cannot read cert file", e );
    } catch ( Throwable e) {
        WigleAndroid.error( "error initializing", e );
    }
    
    return result;
  }

  /**
   * makes sure we connected to someone we expected.
   */
  static class ReflexiveHostnameVerifier implements HostnameVerifier {

    /** the wigle.net cert */
    private Certificate cert;
  
    ReflexiveHostnameVerifier( Certificate cert ) {
      this.cert = cert;
    }

    // inherit docs
    public boolean verify( String hostname, SSLSession session ) {
       // we don't care about the hostname.
       
       try {
           // is our expected cert part of the chain?
           return Arrays.asList( session.getPeerCertificates() ).contains( cert );
       } catch ( SSLPeerUnverifiedException e ) {
           WigleAndroid.error( "hostname: '"+hostname+
                               "' dosen't match up with my WiGLE.net certificate. upgrade!\n"+
                               "or contact wigle-admin@wigle.net with this error:", e );
         return false;
       }
    }
  }

}
