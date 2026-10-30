package com.lifelink119.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Installation-local, non-exportable Android Keystore keys. No hardware-backed claim. */
object MedicalCrypto {
    private const val STORE="AndroidKeyStore"
    private const val DATA_ALIAS="lifelink.medical.demo.storage.v1"
    private const val PIN_ALIAS="lifelink.medical.pin.pepper.v1"
    @Synchronized private fun key(alias: String,algorithm: String,purposes: Int): SecretKey {
        val store=KeyStore.getInstance(STORE).apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        val spec=KeyGenParameterSpec.Builder(alias,purposes).setKeySize(256)
        if(algorithm==KeyProperties.KEY_ALGORITHM_AES)spec.setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        else spec.setDigests(KeyProperties.DIGEST_SHA256)
        return KeyGenerator.getInstance(algorithm,STORE).apply{init(spec.build())}.generateKey()
    }
    fun encrypt(raw: String): String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(DATA_ALIAS,KeyProperties.KEY_ALGORITHM_AES,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT))
        val bytes=raw.toByteArray(Charsets.UTF_8)
        return try{Base64.encodeToString(cipher.iv+cipher.doFinal(bytes),Base64.NO_WRAP)}finally{bytes.fill(0)}
    }
    fun decrypt(raw: String): String {
        val bytes=Base64.decode(raw,Base64.NO_WRAP);require(bytes.size in 29..40_000)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(DATA_ALIAS,KeyProperties.KEY_ALGORITHM_AES,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        val plain=cipher.doFinal(bytes,12,bytes.size-12)
        return try{plain.toString(Charsets.UTF_8)}finally{plain.fill(0);bytes.fill(0)}
    }
    fun pepper(bytes: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").apply{init(key(PIN_ALIAS,KeyProperties.KEY_ALGORITHM_HMAC_SHA256,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY))}.doFinal(bytes)
    fun clear(){val s=KeyStore.getInstance(STORE).apply{load(null)};listOf(DATA_ALIAS,PIN_ALIAS).forEach{if(s.containsAlias(it))s.deleteEntry(it)}}
}
