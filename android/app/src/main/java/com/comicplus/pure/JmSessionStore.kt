package com.comicplus.pure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Persists the JM session and, when enabled, encrypted auto-login credentials.
 * The AVS and password values prefer a key kept in Android Keystore. Devices with an unavailable
 * Keystore fall back to an app-private, non-backed-up software key; on rooted
 * devices that fallback has the same limits as the app sandbox itself.
 */
internal data class JmSessionSaveResult(
    val saved: Boolean,
    val failureMessage: String? = null,
)

class JmSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val fallbackKeyFile = File(appContext.noBackupFilesDir, FALLBACK_KEY_FILE_NAME)

    fun load(): JmSession? {
        val uid = preferences.getString(KEY_UID, null)?.trim().orEmpty()
        val username = preferences.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val encrypted = preferences.getString(KEY_AVS, null).orEmpty()
        if (uid.length > MAX_FIELD_LENGTH || username.length > MAX_FIELD_LENGTH || encrypted.isBlank()) return null
        // A read must never create or replace the key.  On some rooted/custom
        // Android builds the Keystore can be temporarily unavailable during
        // early startup; creating a new key here would make the old ciphertext
        // permanently undecryptable on the next attempt.
        val avs = runCatchingNonFatal { decrypt(encrypted) }.getOrNull()?.trim().orEmpty()
        if (uid.isBlank() || username.isBlank() || avs.isBlank() || avs.length > MAX_AVS_LENGTH) return null
        return JmSession(uid = uid, username = username, avs = avs)
    }

    fun hasStoredSession(): Boolean =
        preferences.getString(KEY_UID, null)?.isNotBlank() == true &&
            preferences.getString(KEY_USERNAME, null)?.isNotBlank() == true &&
            preferences.getString(KEY_AVS, null)?.isNotBlank() == true

    fun loadCredentials(): JmCredentials? {
        val username = preferences.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val encryptedPassword = preferences.getString(KEY_PASSWORD, null).orEmpty()
        if (
            username.isBlank() || username.length > MAX_FIELD_LENGTH ||
            encryptedPassword.isBlank()
        ) return null
        val password = runCatchingNonFatal { decrypt(encryptedPassword) }.getOrNull().orEmpty()
        if (password.isBlank() || password.length > MAX_PASSWORD_LENGTH) return null
        return JmCredentials(username = username, password = password)
    }

    fun hasStoredCredentials(): Boolean =
        preferences.getString(KEY_USERNAME, null)?.isNotBlank() == true &&
            preferences.getString(KEY_PASSWORD, null)?.isNotBlank() == true

    /** Returns a user-safe failure reason without exposing the session value. */
    internal fun save(session: JmSession, credentials: JmCredentials? = null): JmSessionSaveResult {
        if (
            session.uid.isBlank() || session.username.isBlank() ||
            session.uid.length > MAX_FIELD_LENGTH || session.username.length > MAX_FIELD_LENGTH ||
            session.avs.isBlank() || session.avs.length > MAX_AVS_LENGTH
        ) {
            return JmSessionSaveResult(saved = false, failureMessage = INVALID_SESSION_MESSAGE)
        }
        if (
            credentials != null &&
            (credentials.username.isBlank() || credentials.username.length > MAX_FIELD_LENGTH ||
                credentials.password.isBlank() || credentials.password.length > MAX_PASSWORD_LENGTH)
        ) {
            return JmSessionSaveResult(saved = false, failureMessage = INVALID_CREDENTIALS_MESSAGE)
        }
        val encrypted = encryptedSessionValue(session.avs)
            ?: return JmSessionSaveResult(saved = false, failureMessage = SECURE_STORAGE_FAILURE_MESSAGE)
        val encryptedPassword = credentials?.let { encryptedSessionValue(it.password) }
        if (credentials != null && encryptedPassword == null) {
            return JmSessionSaveResult(saved = false, failureMessage = SECURE_STORAGE_FAILURE_MESSAGE)
        }
        val committed = runCatchingNonFatal {
            preferences.edit()
                .putString(KEY_UID, session.uid)
                .putString(KEY_USERNAME, session.username)
                .putString(KEY_AVS, encrypted)
                .apply {
                    if (encryptedPassword != null) putString(KEY_PASSWORD, encryptedPassword)
                }
                .commit()
        }.onFailure { error -> Log.w(TAG, "JM session preference commit failed", error) }
            .getOrDefault(false)
        val persistedPassword = preferences.getString(KEY_PASSWORD, null).orEmpty()
        val usesSoftwareKey = encrypted.startsWith(SOFTWARE_VALUE_PREFIX) ||
            encryptedPassword?.startsWith(SOFTWARE_VALUE_PREFIX) == true ||
            persistedPassword.startsWith(SOFTWARE_VALUE_PREFIX)
        if (committed && !usesSoftwareKey) {
            runCatchingNonFatal { fallbackKeyFile.delete() }
        }
        return JmSessionSaveResult(
            saved = committed,
            failureMessage = STORAGE_FAILURE_MESSAGE.takeUnless { committed },
        )
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_UID)
            .remove(KEY_USERNAME)
            .remove(KEY_AVS)
            .remove(KEY_PASSWORD)
            .commit()
        runCatchingNonFatal { fallbackKeyFile.delete() }
    }

    fun clearSession() {
        preferences.edit()
            .remove(KEY_UID)
            .remove(KEY_AVS)
            .commit()
    }

    private fun encryptedSessionValue(value: String): String? {
        val keystoreValue = runCatchingNonFatal { encryptWithKeystore(value) }
            .onFailure { error -> Log.w(TAG, "JM Keystore encryption failed; using app-private fallback", error) }
            .getOrNull()
        if (keystoreValue != null) return KEYSTORE_VALUE_PREFIX + keystoreValue
        return runCatchingNonFatal { SOFTWARE_VALUE_PREFIX + encryptWithKey(value, softwareKey()) }
            .onFailure { error -> Log.w(TAG, "JM app-private session encryption failed", error) }
            .getOrNull()
    }

    private fun encryptWithKeystore(value: String): String {
        return try {
            encryptWithKey(value, encryptionKey())
        } catch (error: Throwable) {
            error.rethrowCancellation()
            Log.w(TAG, "Replacing an unusable JM session key", error)
            replaceKey()
            encryptWithKey(value, encryptionKey())
        }
    }

    private fun encryptWithKey(value: String, key: SecretKey): String {
        val iv = ByteArray(GCM_IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    private fun decrypt(value: String): String {
        return when {
            value.startsWith(KEYSTORE_VALUE_PREFIX) ->
                decryptWithKey(value.removePrefix(KEYSTORE_VALUE_PREFIX), existingKey())
            value.startsWith(SOFTWARE_VALUE_PREFIX) ->
                decryptWithKey(value.removePrefix(SOFTWARE_VALUE_PREFIX), softwareKey(createIfMissing = false))
            else -> decryptWithKey(value, existingKey())
        }
    }

    private fun decryptWithKey(value: String, key: SecretKey): String {
        val packed = Base64.getDecoder().decode(value)
        require(packed.size > GCM_IV_BYTES)
        val iv = packed.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = packed.copyOfRange(GCM_IV_BYTES, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    @Synchronized
    private fun softwareKey(createIfMissing: Boolean = true): SecretKey {
        if (fallbackKeyFile.isFile && fallbackKeyFile.length() == SOFTWARE_KEY_BYTES.toLong()) {
            val keyBytes = ByteArray(SOFTWARE_KEY_BYTES)
            FileInputStream(fallbackKeyFile).use { input ->
                var offset = 0
                while (offset < keyBytes.size) {
                    val read = input.read(keyBytes, offset, keyBytes.size - offset)
                    if (read < 0) throw IllegalStateException("JM app-private session key is truncated")
                    offset += read
                }
                if (input.read() >= 0) throw IllegalStateException("JM app-private session key is oversized")
            }
            return SecretKeySpec(keyBytes, KeyProperties.KEY_ALGORITHM_AES)
        }
        if (!createIfMissing) throw IllegalStateException("JM app-private session key is unavailable")
        if (fallbackKeyFile.exists() && !fallbackKeyFile.delete()) {
            throw IllegalStateException("JM app-private session key cannot be replaced")
        }
        val keyBytes = ByteArray(SOFTWARE_KEY_BYTES).also(SecureRandom()::nextBytes)
        val parent = fallbackKeyFile.parentFile
            ?: throw IllegalStateException("JM app-private storage is unavailable")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IllegalStateException("JM app-private storage cannot be created")
        }
        val temporary = File(parent, "$FALLBACK_KEY_FILE_NAME.tmp")
        if (temporary.exists() && !temporary.delete()) {
            throw IllegalStateException("JM app-private temporary key cannot be replaced")
        }
        try {
            FileOutputStream(temporary).use { output ->
                output.write(keyBytes)
                output.fd.sync()
            }
            if (!temporary.renameTo(fallbackKeyFile)) {
                throw IllegalStateException("JM app-private session key cannot be committed")
            }
            fallbackKeyFile.setReadable(false, false)
            fallbackKeyFile.setWritable(false, false)
            fallbackKeyFile.setReadable(true, true)
            fallbackKeyFile.setWritable(true, true)
        } finally {
            temporary.delete()
        }
        return SecretKeySpec(keyBytes, KeyProperties.KEY_ALGORITHM_AES)
    }

    private fun replaceKey() {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
            if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS)
        }
    }

    private fun existingKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (store.getKey(KEY_ALIAS, null) as? SecretKey)
            ?: throw IllegalStateException("JM session key is unavailable")
    }

    private fun encryptionKey(): SecretKey {
        return runCatchingNonFatal { existingKey() }.getOrNull() ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val TAG = "JmSessionStore"
        private const val PREFERENCES_NAME = "comicplus_pure_jm_auth"
        private const val KEY_ALIAS = "comicplus_pure_jm_avs"
        private const val KEY_UID = "uid"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVS = "avs"
        private const val KEY_PASSWORD = "password"
        private const val FALLBACK_KEY_FILE_NAME = "jm_session.key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEYSTORE_VALUE_PREFIX = "ks1:"
        private const val SOFTWARE_VALUE_PREFIX = "sw1:"
        private const val KEY_SIZE_BITS = 128
        private const val SOFTWARE_KEY_BYTES = 32
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val MAX_FIELD_LENGTH = 128
        private const val MAX_AVS_LENGTH = 4 * 1024
        private const val MAX_PASSWORD_LENGTH = 512
        private const val INVALID_SESSION_MESSAGE = "JM 会话数据不完整，无法保存登录状态"
        private const val INVALID_CREDENTIALS_MESSAGE = "JM 账号凭据不完整，无法保存自动登录信息"
        private const val SECURE_STORAGE_FAILURE_MESSAGE = "系统 Keystore 不可用，应用兼容存储也写入失败"
        private const val STORAGE_FAILURE_MESSAGE = "应用数据写入失败；请检查剩余存储空间"
    }
}
