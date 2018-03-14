package com.example.jorge_caro.fingerprint_test.fingerprint_auth

import android.annotation.TargetApi
import android.app.FragmentManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.preference.PreferenceManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.support.annotation.RequiresApi
import com.example.jorge_caro.fingerprint_test.R
import java.io.IOException
import java.security.*
import java.security.cert.CertificateException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey

/**
 * Created by jorge_caro on 3/13/18.
 */
@TargetApi(Build.VERSION_CODES.N)
@RequiresApi(Build.VERSION_CODES.M)
class FingerPrintService(private val context: Context,
                         private val fragmentManager: FragmentManager): FingerprintAuthenticationDialogFragment.Callback {

    private lateinit var keyStore: KeyStore
    private lateinit var keyGenerator: KeyGenerator
    private var sharedPreferences: SharedPreferences
    private lateinit var cipher: Cipher
    private var fragment: FingerprintAuthenticationDialogFragment
    private lateinit var func: () -> Unit

    init {
        fragment = FingerprintAuthenticationDialogFragment()
        setupKeyStoreAndKeyGenerator()
        setupCiphers()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        createKey(DEFAULT_KEY_NAME)
    }

    /**
     * Sets up KeyStore and KeyGenerator
     */
    private fun setupKeyStoreAndKeyGenerator() {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        } catch (e: KeyStoreException) {
            throw RuntimeException("Failed to get an instance of KeyStore", e)
        }

        try {
            keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        } catch (e: Exception) {
            when (e) {
                is NoSuchAlgorithmException,
                is NoSuchProviderException ->
                    throw RuntimeException("Failed to get an instance of KeyGenerator", e)
                else -> throw e
            }
        }
    }

    /**
     * Sets up default cipher and a non-invalidated cipher
     * method to set encryptation, line 149 means algorithm/ mode/ padding
     * for more information https://developer.android.com/reference/javax/crypto/Cipher.html
     */
    private fun setupCiphers(){
        try {
            val cipherString = "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"
            cipher = Cipher.getInstance(cipherString)
        } catch (e: Exception) {
            when (e) {
                is NoSuchAlgorithmException,
                is NoSuchPaddingException ->
                    throw RuntimeException("Failed to get an instance of Cipher", e)
                else -> throw e
            }
        }
    }

    private fun initCipher(keyName: String): Boolean {
        try {
            keyStore.load(null)
            cipher.init(Cipher.ENCRYPT_MODE,
                    keyStore.getKey(
                            keyName, null) as SecretKey)
            return true
        } catch (e: Exception) {
            when (e) {
                is KeyPermanentlyInvalidatedException -> return false
                is KeyStoreException,
                is CertificateException,
                is UnrecoverableKeyException,
                is IOException,
                is NoSuchAlgorithmException,
                is InvalidKeyException -> throw RuntimeException("Failed to init Cipher", e)
                else -> throw e
            }
        }
    }

    fun openFingerPrintDialog(){
        fragment.setCryptoObject(FingerprintManager.CryptoObject(cipher))
        fragment.setCallback(this@FingerPrintService)

        // Set up the crypto object for later, which will be authenticated by fingerprint usage.
        if (initCipher(DEFAULT_KEY_NAME)) {

            // Show the fingerprint dialog. The user has the option to use the fingerprint with
            // crypto, or can fall back to using a server-side verified password.
            val useFingerprintPreference = sharedPreferences
                    .getBoolean(context.getString(R.string.use_fingerprint_to_authenticate_key), true)
            if (useFingerprintPreference) {
                fragment.setStage(Stage.FINGERPRINT)
            } else {
                fragment.setStage(Stage.PASSWORD)
            }
        } else {
            // This happens if the lock screen has been disabled or or a fingerprint was
            // enrolled. Thus, show the dialog to authenticate with their password first and ask
            // the user if they want to authenticate with a fingerprint in the future.
            fragment.setStage(Stage.NEW_FINGERPRINT_ENROLLED)
        }
        fragment.show(fragmentManager, DIALOG_FRAGMENT_TAG)
    }

    /**
     * Proceed with the purchase operation
     *
     * @param withFingerprint `true` if the purchase was made by using a fingerprint
     * @param crypto the Crypto object
     * Important, happens after validation succefull
     */
    override fun onPurchased(withFingerprint: Boolean, crypto: FingerprintManager.CryptoObject?){
        if (withFingerprint) {
            // If the user authenticated with fingerprint, verify using cryptography and then show
            // the confirmation message.
            if (crypto != null) {
                func()
            }
        }
    }

    fun setEvent(func: () -> Unit){
        this.func = func
    }



    @RequiresApi(Build.VERSION_CODES.N)
    override fun createKey(keyName: String, invalidatedByBiometricEnrollment: Boolean) {
        // The enrolling flow for fingerprint. This is where you ask the user to set up fingerprint
        // for your flow. Use of keys is necessary if you need to know if the set of enrolled
        // fingerprints has changed.
        try {
            keyStore.load(null)

            val keyProperties = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            val builder = KeyGenParameterSpec.Builder(keyName, keyProperties)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setInvalidatedByBiometricEnrollment(invalidatedByBiometricEnrollment)

            keyGenerator.run {
                init(builder.build())
                generateKey()
            }
        } catch (e: Exception) {
            when (e) {
                is NoSuchAlgorithmException,
                is InvalidAlgorithmParameterException,
                is CertificateException,
                is IOException -> throw RuntimeException(e)
                else -> throw e
            }
        }
    }

    companion object {
        private val ANDROID_KEY_STORE = "AndroidKeyStore"
        private val DIALOG_FRAGMENT_TAG = "myFragment"
    }
}