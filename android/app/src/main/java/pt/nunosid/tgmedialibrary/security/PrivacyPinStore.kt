package pt.nunosid.tgmedialibrary.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores only a salted PBKDF2 verifier for the user's privacy PIN.
 * The verifier and salt are additionally encrypted by SecureStore/AndroidKeyStore.
 */
class PrivacyPinStore(context: Context) {
    private val secureStore = SecureStore(context.applicationContext)

    fun isConfigured(): Boolean =
        secureStore.getBytes(KEY_SALT)?.size == SALT_BYTES &&
            secureStore.getBytes(KEY_HASH)?.size == HASH_BYTES

    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        secureStore.putBytes(KEY_SALT, salt)
        secureStore.putBytes(KEY_HASH, hash)
        return true
    }

    fun verify(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = secureStore.getBytes(KEY_SALT) ?: return false
        val expected = secureStore.getBytes(KEY_HASH) ?: return false
        if (salt.size != SALT_BYTES || expected.size != HASH_BYTES) return false
        val actual = derive(pin, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val MIN_LENGTH = 6
        const val MAX_LENGTH = 12
        private const val ITERATIONS = 210_000
        private const val SALT_BYTES = 16
        private const val HASH_BYTES = 32
        private const val KEY_SALT = "privacy_pin_salt_v1"
        private const val KEY_HASH = "privacy_pin_hash_v1"

        fun isValidPin(pin: String): Boolean =
            pin.length in MIN_LENGTH..MAX_LENGTH && pin.all(Char::isDigit)
    }
}
