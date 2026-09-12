package pt.nunosid.tgmedialibrary.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small Android Keystore-backed encrypted store.
 * Values are encrypted with AES/GCM. The AES key never leaves AndroidKeyStore.
 */
class SecureStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun putString(name: String, value: String) = putBytes(name, value.toByteArray(Charsets.UTF_8))

    fun getString(name: String): String? = getBytes(name)?.toString(Charsets.UTF_8)

    fun putBytes(name: String, value: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value)
        val packed = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, packed, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, packed, cipher.iv.size, encrypted.size)
        prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun getBytes(name: String): ByteArray? {
        val encoded = prefs.getString(name, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_BYTES)
            val iv = packed.copyOfRange(0, IV_BYTES)
            val encrypted = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        }.getOrNull()
    }

    fun getOrCreateRandomBytes(name: String, size: Int): ByteArray {
        getBytes(name)?.let { if (it.size == size) return it }
        val generated = ByteArray(size).also(SecureRandom()::nextBytes)
        putBytes(name, generated)
        return generated
    }

    fun clearEncryptedValues() {
        prefs.edit().clear().commit()
    }

    fun destroy() {
        clearEncryptedValues()
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "secure_store_v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "tg_media_library_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
