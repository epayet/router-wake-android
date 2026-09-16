package com.jakspinning.wakemypc

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Everything needed to talk to one FritzBox and wake one PC. */
data class FritzBoxConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val pcMacAddress: String,
    val pcLanIp: String,
)

/**
 * Persists [FritzBoxConfig] in a plain SharedPreferences file — replaces the old
 * gitignored, hardcoded `Config.kt` from v0. Every field is encrypted at rest
 * with an AES-256-GCM key held in the Android Keystore (key material never
 * leaves hardware/TEE-backed storage). This is hand-rolled rather than using
 * androidx.security:security-crypto's EncryptedSharedPreferences, which Google
 * deprecated in 2025 with no replacement.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("fritzbox_config", Context.MODE_PRIVATE)
    private val cipher = ConfigCipher()

    // Wrapped in runCatching: a config saved under an older storage format
    // (e.g. host/port used to be plain, not encrypted) would otherwise throw
    // here — falling back to null just re-runs onboarding instead of crashing.
    fun load(): FritzBoxConfig? = runCatching {
        val encryptedHost = prefs.getString(KEY_HOST, null) ?: return@runCatching null
        val encryptedPort = prefs.getString(KEY_PORT, null) ?: return@runCatching null
        val encryptedUsername = prefs.getString(KEY_USERNAME, null) ?: return@runCatching null
        val encryptedPassword = prefs.getString(KEY_PASSWORD, null) ?: return@runCatching null
        val encryptedMac = prefs.getString(KEY_MAC, null) ?: return@runCatching null
        val encryptedIp = prefs.getString(KEY_IP, null) ?: return@runCatching null
        FritzBoxConfig(
            host = cipher.decrypt(encryptedHost),
            port = cipher.decrypt(encryptedPort).toInt(),
            username = cipher.decrypt(encryptedUsername),
            password = cipher.decrypt(encryptedPassword),
            pcMacAddress = cipher.decrypt(encryptedMac),
            pcLanIp = cipher.decrypt(encryptedIp),
        )
    }.getOrNull()

    fun save(config: FritzBoxConfig) {
        prefs.edit()
            .putString(KEY_HOST, cipher.encrypt(config.host))
            .putString(KEY_PORT, cipher.encrypt(config.port.toString()))
            .putString(KEY_USERNAME, cipher.encrypt(config.username))
            .putString(KEY_PASSWORD, cipher.encrypt(config.password))
            .putString(KEY_MAC, cipher.encrypt(config.pcMacAddress))
            .putString(KEY_IP, cipher.encrypt(config.pcLanIp))
            .apply()
    }

    companion object {
        const val DEFAULT_HOST = "fritz.box"
        const val DEFAULT_PORT = 49000

        private const val KEY_HOST = "fritzbox_host"
        private const val KEY_PORT = "fritzbox_port"
        private const val KEY_USERNAME = "fritzbox_username"
        private const val KEY_PASSWORD = "fritzbox_password"
        private const val KEY_MAC = "pc_mac_address"
        private const val KEY_IP = "pc_lan_ip"
    }
}

/** AES-256-GCM encrypt/decrypt of short strings using an Android Keystore key. */
private class ConfigCipher {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "$iv:$data"
    }

    fun decrypt(stored: String): String {
        val (ivPart, dataPart) = stored.split(":", limit = 2)
        val iv = Base64.decode(ivPart, Base64.NO_WRAP)
        val data = Base64.decode(dataPart, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "wakemypc_config_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
