package com.example.uhid

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.conscrypt.Conscrypt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

/**
 * Generates & persists a persistent RSA-2048 ADB keypair and self-signed X.509 certificate
 * inside app-private storage so target TV/Phone trusts LinkPad permanently after one approval.
 */
object AdbIdentityStore {

    private const val KEY_FILE = "linkpad_adb_key.pkcs8"
    private const val CERT_FILE = "linkpad_adb_cert.der"

    data class Identity(
        val privateKey: PrivateKey,
        val certificate: X509Certificate
    )

    fun loadOrCreate(context: Context): Identity {
        val dir = context.filesDir
        val keyFile = File(dir, KEY_FILE)
        val certFile = File(dir, CERT_FILE)

        if (keyFile.exists() && certFile.exists()) {
            runCatching {
                val keyBytes = keyFile.readBytes()
                val certBytes = certFile.readBytes()
                val priv = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                return Identity(priv, cert)
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val pair = kpg.generateKeyPair()
        val certBytes = buildSelfSignedX509Der(pair.public, pair.private)
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate

        runCatching {
            keyFile.writeBytes(pair.private.encoded)
            certFile.writeBytes(certBytes)
        }
        return Identity(pair.private, cert)
    }

    /**
     * Minimal ASN.1 DER encoder for an X.509 v1 certificate signed with SHA256withRSA.
     * Avoids private JDK sun.security classes so it works identically on Android API 24..36 and JVM tests.
     */
    fun buildSelfSignedX509Der(publicKey: PublicKey, privateKey: PrivateKey): ByteArray {
        val sha256WithRsaAlgId = derSeq(
            derOid(intArrayOf(1, 2, 840, 113549, 1, 1, 11)), // sha256WithRSAEncryption
            byteArrayOf(0x05, 0x00) // NULL
        )
        val dn = derSeq(
            derSet(
                derSeq(
                    derOid(intArrayOf(2, 5, 4, 3)), // commonName
                    derUtf8("LinkPad")
                )
            )
        )
        val validity = derSeq(
            derUtcTime("240101000000Z"),
            derUtcTime("491231235959Z")
        )
        val serial = BigInteger(62, SecureRandom()).abs().add(BigInteger.ONE)
        val tbsCertificate = derSeq(
            derInteger(serial),
            sha256WithRsaAlgId,
            dn,
            validity,
            dn,
            publicKey.encoded
        )

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(tbsCertificate)
        val signatureBytes = signer.sign()

        return derSeq(
            tbsCertificate,
            sha256WithRsaAlgId,
            derBitString(signatureBytes)
        )
    }

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        when {
            len < 0x80 -> out.write(len)
            len <= 0xFF -> {
                out.write(0x81)
                out.write(len)
            }
            len <= 0xFFFF -> {
                out.write(0x82)
                out.write((len ushr 8) and 0xFF)
                out.write(len and 0xFF)
            }
            else -> {
                out.write(0x83)
                out.write((len ushr 16) and 0xFF)
                out.write((len ushr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun derSeq(vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        parts.forEach { body.write(it) }
        return derTag(0x30, body.toByteArray())
    }

    private fun derSet(vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        parts.forEach { body.write(it) }
        return derTag(0x31, body.toByteArray())
    }

    private fun derInteger(value: BigInteger): ByteArray = derTag(0x02, value.toByteArray())

    private fun derBitString(bytes: ByteArray): ByteArray =
        derTag(0x03, byteArrayOf(0x00) + bytes)

    private fun derUtf8(text: String): ByteArray =
        derTag(0x0C, text.toByteArray(Charsets.UTF_8))

    private fun derUtcTime(utc: String): ByteArray =
        derTag(0x17, utc.toByteArray(Charsets.US_ASCII))

    private fun derOid(nodes: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(nodes[0] * 40 + nodes[1])
        for (i in 2 until nodes.size) {
            var v = nodes[i]
            val stack = ArrayList<Int>(5)
            stack.add(v and 0x7F)
            v = v ushr 7
            while (v > 0) {
                stack.add((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            for (k in stack.size - 1 downTo 0) {
                out.write(stack[k])
            }
        }
        return derTag(0x06, out.toByteArray())
    }

    private val ADB_SHA1_DIGEST_INFO_PREFIX = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
        0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    )

    /**
     * Signs ADB's 20-byte AUTH challenge token using RSA/ECB/PKCS1Padding with SHA-1 DigestInfo prefix
     * (matching AOSP adbd's RSA_verify(NID_sha1, token, 20, ...) without double-hashing).
     */
    fun signAdbToken(privateKey: PrivateKey, token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        return cipher.doFinal(ADB_SHA1_DIGEST_INFO_PREFIX + token)
    }

    /**
     * Encodes an RSA-2048 public key into AOSP libcrypto_utils RSAPublicKey binary struct + Base64 + label\0.
     * Uses Java standard library Base64 (android.util.Base64 not required) so JVM unit tests verify exact bytes.
     */
    fun encodeAdbPublicKey(publicKey: RSAPublicKey, label: String = "linkpad@android"): ByteArray {
        val modBits = publicKey.modulus.bitLength()
        val keySizeWords = (modBits + 31) / 32 // 64 for 2048-bit RSA
        val keyBytes = keySizeWords * 4

        val n = publicKey.modulus
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32)).toLong() and 0xFFFFFFFFL
        val r = BigInteger.ONE.shiftLeft(keySizeWords * 32)
        val rr = r.multiply(r).mod(n)

        val buf = ByteBuffer.allocate(4 + 4 + keyBytes + keyBytes + 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(keySizeWords)
        buf.putInt(n0inv.toInt())

        val nWords = toLittleEndianWords(n, keySizeWords)
        val rrWords = toLittleEndianWords(rr, keySizeWords)
        nWords.forEach { buf.putInt(it) }
        rrWords.forEach { buf.putInt(it) }
        buf.putInt(publicKey.publicExponent.toInt())

        val b64 = java.util.Base64.getEncoder().encodeToString(buf.array())
        return "$b64 $label\u0000".toByteArray(Charsets.UTF_8)
    }

    private fun toLittleEndianWords(value: BigInteger, wordCount: Int): IntArray {
        val words = IntArray(wordCount)
        var cur = value
        val mask = BigInteger.valueOf(0xFFFFFFFFL)
        for (i in 0 until wordCount) {
            words[i] = cur.and(mask).toLong().toInt()
            cur = cur.shiftRight(32)
        }
        return words
    }
}

class LinkPadAdbConnectionManager(
    context: Context,
    private val label: String = "LinkPadHost"
) : AbsAdbConnectionManager() {

    private val identity: AdbIdentityStore.Identity

    init {
        runCatching {
            if (Security.getProvider("Conscrypt") == null) {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            }
        }
        setApi(maxOf(Build.VERSION.SDK_INT, Build.VERSION_CODES.R))
        identity = AdbIdentityStore.loadOrCreate(context.applicationContext)
    }

    override fun getPrivateKey(): PrivateKey = identity.privateKey

    override fun getCertificate(): Certificate = identity.certificate

    fun publicKeyPayload(): ByteArray =
        AdbIdentityStore.encodeAdbPublicKey(identity.certificate.publicKey as RSAPublicKey, "linkpad@android")

    fun signToken(token: ByteArray): ByteArray =
        AdbIdentityStore.signAdbToken(identity.privateKey, token)

    override fun getDeviceName(): String = label
}
