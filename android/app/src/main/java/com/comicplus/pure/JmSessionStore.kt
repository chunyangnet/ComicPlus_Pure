package com.comicplus.pure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists the JM session without ever writing the user's password.  The AVS
 * value is encrypted with a key kept in Android Keystore; if the keystore is
 * unavailable the session is simply treated as non-persistent.
 */
class JmSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): JmSession? {
        val uid = preferences.getString(KEY_UID, null)?.trim().orEmpty()
        val username = preferences.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val encrypted = preferences.getString(KEY_AVS, null).orEmpty()
        if (uid.length > MAX_FIELD_LENGTH || username.length > MAX_FIELD_LENGTH || encrypted.isBlank()) return null
        val avs = runCatchingNonFatal { decrypt(encrypted) }.getOrNull()?.trim().orEmpty()
        if (uid.isBlank() || username.isBlank() || avs.isBlank() || avs.length > MAX_AVS_LENGTH) return null
        return JmSession(uid = uid, username = username, avs = avs)
    }

    /** Returns false when the platform keystore cannot persist the session. */
    fun save(session: JmSession): Boolean {
        if (
            session.uid.isBlank() || session.username.isBlank() ||
            session.uid.length > MAX_FIELD_LENGTH || session.username.length > MAX_FIELD_LENGTH ||
            session.avs.isBlank() || session.avs.length > MAX_AVS_LENGTH
        ) return false
        return runCatchingNonFatal {
            preferences.edit()
                .putString(KEY_UID, session.uid)
                .putString(KEY_USERNAME, session.username)
                .putString(KEY_AVS, encrypt(session.avs))
                .commit()
        }.getOrDefault(false)
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_UID)
            .remove(KEY_USERNAME)
            .remove(KEY_AVS)
            .commit()
    }

    private fun encrypt(value: String): String {
        val iv = ByteArray(GCM_IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    private fun decrypt(value: String): String {
        val packed = Base64.getDecoder().decode(value)
        require(packed.size > GCM_IV_BYTES)
        val iv = packed.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = packed.copyOfRange(GCM_IV_BYTES, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "comicplus_pure_jm_auth"
        private const val KEY_ALIAS = "comicplus_pure_jm_avs"
        private const val KEY_UID = "uid"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVS = "avs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val MAX_FIELD_LENGTH = 128
        private const val MAX_AVS_LENGTH = 4 * 1024
    }
}
