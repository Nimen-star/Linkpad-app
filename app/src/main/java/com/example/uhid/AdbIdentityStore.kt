package com.example.uhid

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.conscrypt.Conscrypt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
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
import java.security.spec.PKCS8EncodedKeySpec

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
        setApi(Build.VERSION.SDK_INT)
        identity = AdbIdentityStore.loadOrCreate(context.applicationContext)
    }

    override fun getPrivateKey(): PrivateKey = identity.privateKey

    override fun getCertificate(): Certificate = identity.certificate

    override fun getDeviceName(): String = label
}
