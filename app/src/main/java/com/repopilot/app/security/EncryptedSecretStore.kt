package com.repopilot.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedSecretStore(private val context: Context) {
    private val alias = "repopilot-api-key"
    private val prefs = context.getSharedPreferences("repopilot-secure", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return gen.generateKey()
    }
    fun put(id: String, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        val packed = c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(id, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }
    fun get(id: String): String? = runCatching {
        val packed = Base64.decode(prefs.getString(id, null) ?: return null, Base64.NO_WRAP)
        val iv = packed.copyOfRange(0, 12); val data = packed.copyOfRange(12, packed.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(c.doFinal(data), Charsets.UTF_8)
    }.getOrNull()
    fun has(id: String) = prefs.contains(id)
    fun remove(id: String) = prefs.edit().remove(id).apply()
}
