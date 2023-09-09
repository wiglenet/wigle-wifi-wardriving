package net.wigle.wigleandroid;

import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Methods for enc/dec the API Token via the Android KeyStore if supported
 * Intended to improve secret cred storage in-place
 * Created by rksh on 3/2/17.
 */

public class TokenAccess {

    // identify WiGLE entry in the KeyStore
    public static final String KEYSTORE_WIGLE_CREDS_KEY_V0 = "WiGLEKeyOld";
    public static final String KEYSTORE_WIGLE_CREDS_KEY_V1 = "WiGLEKey";
    public static final String KEYSTORE_WIGLE_CREDS_KEY_V2 = "WiGLEKeyAES";
    public static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String AES_CIPHER = "AES/GCM/NoPadding";
    private static final String RSA_OLD_CIPHER = "RSA/ECB/PKCS1Padding";
    private static final String RSA_CIPHER = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * test presence of a necessary API key, Keystore entry if applicable
     * @return true if present, otherwise false
     */
    public static boolean hasApiToken(SharedPreferences prefs) {
        if (!prefs.getString(PreferenceKeys.PREF_TOKEN,"").isEmpty()) {

            try {
                final KeyStore keyStore = getKeyStore();

                if (keyStore.containsAlias(KEYSTORE_WIGLE_CREDS_KEY_V1)
                        || keyStore.containsAlias(KEYSTORE_WIGLE_CREDS_KEY_V2)) {
                    //TODO: it would be best to test decrypt here, but makes this heavier
                    return true;
                }
            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                Logging.error("[TOKEN] Error trying to test token existence: ", e);
                return false;
            }
        }
        return false;
    }

    /**
     * remove the token preference
     */
    public static void clearApiToken(SharedPreferences prefs) {
        final SharedPreferences.Editor editor = prefs.edit();
        editor.remove(PreferenceKeys.PREF_TOKEN);
        editor.apply();
    }

    private static boolean setApiTokenVersion2(final SharedPreferences prefs, final String apiToken)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException,
            NoSuchPaddingException, InvalidKeyException, UnrecoverableEntryException, IllegalBlockSizeException,
            BadPaddingException {

        if (apiToken == null) {
            Logging.error("[TOKEN] ERROR: unreachable condition, apiToken NULL. APIv" + Build.VERSION.SDK_INT);
            return false;
        }

        final KeyStore keyStore = getKeyStore();
        final SecretKey key = (SecretKey) keyStore.getKey(KEYSTORE_WIGLE_CREDS_KEY_V2, null);
        if (null == key) {
            Logging.warn("unable to retrieve KEYSTORE_WIGLE_CREDS_KEY_V2");
            throw new InvalidKeyException("Unable to fetch key");
        }
        Cipher encrypt = Cipher.getInstance(AES_CIPHER);
        encrypt.init(Cipher.ENCRYPT_MODE, key);

        final byte[] input = apiToken.getBytes();
        final byte[] cypherToken = encrypt.doFinal(input);
        if (cypherToken == null) {
            Logging.error("[TOKEN] ERROR: unreachable condition, cypherToken NULL. APIv" + Build.VERSION.SDK_INT);
            return false;
        }
        final byte[] iv = encrypt.getIV();
        // thanks stack overflow, grr android security team. number of bits difference.
        // https://stackoverflow.com/questions/33995233/android-aes-encryption-decryption-using-gcm-mode-in-android
        // https://medium.com/@ericfu/securely-storing-secrets-in-an-android-application-501f030ae5a3
        final int tagLength = (cypherToken.length - input.length) * 8;

        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PreferenceKeys.PREF_TOKEN, Base64.encodeToString(cypherToken, Base64.DEFAULT));
        editor.putString(PreferenceKeys.PREF_TOKEN_IV, Base64.encodeToString(iv, Base64.DEFAULT));
        editor.putInt(PreferenceKeys.PREF_TOKEN_TAG_LENGTH, tagLength);
        final boolean success = editor.commit();
        Logging.info("[TOKEN] setApiTokenVersion2 success: "+success+" setting token length: " + apiToken.length());
        return success;
    }

    private static String getApiTokenVersion2(SharedPreferences prefs) throws KeyStoreException, IOException,
            NoSuchAlgorithmException, CertificateException, NoSuchPaddingException, InvalidKeyException,
            UnrecoverableEntryException, IllegalBlockSizeException, BadPaddingException,
            InvalidAlgorithmParameterException {

        try {
                final KeyStore keyStore = getKeyStore();
                final SecretKey key = (SecretKey) keyStore.getKey(KEYSTORE_WIGLE_CREDS_KEY_V2, null);
                if (null == key ) {
                    Logging.warn("Null key in getApiTokenVersion2");
                    return null;
                }
                final Cipher decrypt = Cipher.getInstance(AES_CIPHER);

                final byte[] cypherToken = Base64.decode(prefs.getString(PreferenceKeys.PREF_TOKEN, ""), Base64.DEFAULT);
                final byte[] iv = Base64.decode(prefs.getString(PreferenceKeys.PREF_TOKEN_IV, ""), Base64.DEFAULT);
                if (iv.length == 0) {
                    Logging.warn("IV is zero length, cannot decrypt token");
                    return null;
                }
                final int tagLength = prefs.getInt(PreferenceKeys.PREF_TOKEN_TAG_LENGTH, 128);

                decrypt.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(tagLength, iv));
                final byte[] done = decrypt.doFinal(cypherToken);
                final String token = new String(done, StandardCharsets.UTF_8);
                Logging.info("[TOKEN] aes decrypted token length: " + token.length());
                return token;
        } catch (Exception ex) {
            Logging.error("Failed to decrypt token with AES-GCM (v2 cipher): ", ex);
            return null;
        }
    }

    /**
     * Set the appropriate API Token, stored with KeyStore crypto if suitable
     * @param prefs the shared preferences object in which to store the token
     * @param apiToken the token value to store
     * @return true if successful, otherwise false
     */
    public static boolean setApiToken(SharedPreferences prefs, String apiToken) {
        try {
            return setApiTokenVersion2(prefs, apiToken);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException |
                IOException | UnrecoverableEntryException | NoSuchPaddingException |
                InvalidKeyException | BadPaddingException | IllegalBlockSizeException ex) {
            Logging.error("[TOKEN] Failed to set token: ",ex);
            ex.printStackTrace();
        } catch (Exception e) {
            Logging.error("[TOKEN] Other error - failed to set token: ",e);
            e.printStackTrace();
        }
        return false;
    }

    /**
     * get the get the API token independent of secure/insecure storage
     * @param prefs a SharedPreferences instance from which to retrieve the token
     * @return the String token or null if unavailable
     */
    public static String getApiToken(SharedPreferences prefs) {
        try {
            final KeyStore keyStore = getKeyStore();

            KeyStore.PrivateKeyEntry privateKeyEntry;

            // prefer v2 key -> v1 key -> v0 key, nada as applicable
            int versionThreshold = android.os.Build.VERSION_CODES.M;
            if (keyStore.containsAlias(KEYSTORE_WIGLE_CREDS_KEY_V2)) {
                //DEBUG: MainActivity.info("Using v2: " + KEYSTORE_WIGLE_CREDS_KEY_V2);
                return getApiTokenVersion2(prefs);
            } else if (keyStore.containsAlias(KEYSTORE_WIGLE_CREDS_KEY_V1)) {
                privateKeyEntry = (KeyStore.PrivateKeyEntry)
                        keyStore.getEntry(KEYSTORE_WIGLE_CREDS_KEY_V1, null);
            } else if (keyStore.containsAlias(KEYSTORE_WIGLE_CREDS_KEY_V0)) {
                privateKeyEntry = (KeyStore.PrivateKeyEntry)
                        keyStore.getEntry(KEYSTORE_WIGLE_CREDS_KEY_V0, null);
                versionThreshold = Build.VERSION_CODES.JELLY_BEAN_MR2;
            } else {
                Logging.warn("[TOKEN] Compatible build, but no key set: " +
                        android.os.Build.VERSION.SDK_INT + " - returning plaintext.");
                return prefs.getString(PreferenceKeys.PREF_TOKEN, "");
            }


            if (null != privateKeyEntry) {
                String encodedCypherText = prefs.getString(PreferenceKeys.PREF_TOKEN, "");
                if (!encodedCypherText.isEmpty()) {
                    byte[] cypherText = Base64.decode(encodedCypherText, Base64.DEFAULT);
                    PrivateKey privateKey = privateKeyEntry.getPrivateKey();

                    Cipher c;
                    if (versionThreshold >= android.os.Build.VERSION_CODES.M) {
                        c = Cipher.getInstance(RSA_CIPHER);
                    } else {
                        c = Cipher.getInstance(RSA_OLD_CIPHER);
                    }
                    c.init(Cipher.DECRYPT_MODE, privateKey);
                    return new String(c.doFinal(cypherText), StandardCharsets.UTF_8);
                } else {
                    Logging.error("[TOKEN] NULL encoded cyphertext on token decrypt.");
                    return null;
                }
            } else {
                Logging.error("[TOKEN] NULL Private Key on token decrypt.");
                return null;
            }
        } catch (CertificateException | NoSuchAlgorithmException | IOException |
                KeyStoreException | UnrecoverableEntryException | NoSuchPaddingException |
                InvalidKeyException | BadPaddingException | IllegalBlockSizeException |
                InvalidAlgorithmParameterException ex) {
            Logging.error("[TOKEN] Failed to get API Token: ", ex);
            return null;
        }
    }

    /**
     * Initialization method - only intended for run at app onCreate
     * @param prefs preferences from root context
     * @return true if successful encryption takes place, else false.
     */
    public static boolean checkMigrateKeystoreVersion(SharedPreferences prefs) {
        final boolean firstMigrate = checkMigrateKeystoreVersion1(prefs);
        checkMigrateKeystoreVersion2(prefs);
        return firstMigrate;
    }

    private static KeyStore getKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        // have to call "load" before you do anything
        keyStore.load(null);
        return keyStore;
    }

    private static void checkMigrateKeystoreVersion2(SharedPreferences prefs) {
        try {
            final KeyStore keyStore = getKeyStore();
            if (keyStore.containsAlias(KEYSTORE_WIGLE_CREDS_KEY_V2)) {
                Logging.info("[TOKEN] Key present and up-to-date V2 AES - no change.");
                return;
            }

            // get old token
            final String token = getApiToken(prefs);
            Logging.info("Got old token, length: " + (token == null ? null : token.length()));

            // set up aes key
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            keyGenerator.init(
                    new KeyGenParameterSpec.Builder(KEYSTORE_WIGLE_CREDS_KEY_V2,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build());
            keyGenerator.generateKey();

            if (token != null && !token.isEmpty()) setApiToken(prefs, token);
        }
        catch (Exception ex) {
            Logging.error("Exception migrating to version 2: " + ex, ex);
        }
    }

    private static boolean checkMigrateKeystoreVersion1(SharedPreferences prefs) {
        boolean initOnly = false;
        if (prefs.getString(PreferenceKeys.PREF_TOKEN, "").isEmpty()) {
            Logging.info("[TOKEN] No auth token stored - no preference migration possible.");
            initOnly = true;
        }

        try {
            Logging.info("[TOKEN] Using Android Keystore; check need for new key...");
            final KeyStore keyStore = getKeyStore();
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE);

            {
                if (keyStore.containsAlias(KEYSTORE_WIGLE_CREDS_KEY_V1)) {
                    Logging.info("[TOKEN] Key present and up-to-date M - no change.");
                    return false;
                }

                Logging.info("[TOKEN] Initializing SDKv23 Key...");
                String token = "";
                if (keyStore.containsAlias(KEYSTORE_WIGLE_CREDS_KEY_V0)) {
                    //ALIBI: fetch token with V0 key if it's stored that way
                    token = TokenAccess.getApiToken(prefs);
                }
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                        KEYSTORE_WIGLE_CREDS_KEY_V1,
                        KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                        .build();

                kpg.initialize(spec);
                kpg.generateKeyPair();

                if (keyStore.containsAlias(KEYSTORE_WIGLE_CREDS_KEY_V0)) {
                    Logging.info("[TOKEN] Upgrading from v0->v1 token...");
                    if ((null == token) || token.isEmpty()) return false;
                    keyStore.deleteEntry(KEYSTORE_WIGLE_CREDS_KEY_V0);
                } else {
                    token = prefs.getString(PreferenceKeys.PREF_TOKEN, "");
                    //DEBUG: MainActivity.info("[TOKEN] +"+token+"+");
                    Logging.info("[TOKEN] Encrypting token at v1...");
                    if (token.isEmpty()) {
                        Logging.info("[TOKEN] ...no token, returning after init.");
                        return false;
                    }
                }
                if (!initOnly) {
                    if (TokenAccess.setApiToken(prefs, token)) {
                        Logging.info("[TOKEN] ...token set at v1.");
                        return true;
                    } else {
                        /*
                         * ALIBI: if you can't migrate it, clear it to force re-authentication.
                         * this isn't optimal, but it beats the alternative.
                         * This is vital here, since Marshmallow and up can backup/restore
                         * SharedPreferences, but NOT keystore entries
                         */
                        Logging.error("[TOKEN] ...Failed token encryption; clearing.");
                        clearApiToken(prefs);
                    }
                } else {
                    Logging.error("[TOKEN] v1 Keystore initialized, but no token present.");
                }
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException |
                IOException | NoSuchProviderException | InvalidAlgorithmParameterException |
                ProviderException ex) {
            Logging.error("Upgrade/init of token storage failed: ", ex);
            ex.printStackTrace();
            //TODO: should we clear here?
            //clearApiToken(prefs);
            return false;
        } catch (Exception e) {
            /*
             * ALIBI: after production evidence of a ProviderException (runtime), adding belt to
             * suspenders
             */
            Logging.error("Unexpected error in upgrade/init of token storage failed: ", e);
            e.printStackTrace();
            return false;
        }
        return false;
    }
}
