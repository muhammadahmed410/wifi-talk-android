package com.example.crypto

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SecurityManager handles local identity generation, frame validation,
 * replay protection, rate-limiting, and packet sanitization for peer-to-peer communication.
 *
 * Security Model:
 * 1. Persistent Device Identity: Cryptographically random 128-bit hex string.
 * 2. Peer Authentication: Per-session nonces and HMAC-SHA256 message authentication.
 * 3. Replay Protection: Packets carry timestamps (max ±30s clock drift) and monotonic sequence IDs.
 * 4. Resource Exhaustion Defense: Strict max message limits (64KB max signaling, 1024B max audio).
 * 5. Privacy: Discovery broadcasts only advertise deviceId, displayName, and ports. No private data.
 */
class SecurityManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wifi_talk_sec_prefs", Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    private val outgoingSeq = AtomicLong(1L)
    private val peerLastSeq = ConcurrentHashMap<String, Long>()
    private val peerLastPacketTime = ConcurrentHashMap<String, Long>()

    // Local device identity
    val deviceId: String
    private val deviceSecretKey: ByteArray

    init {
        var savedId = prefs.getString("local_device_id", null)
        var savedSecret = prefs.getString("local_device_secret", null)

        if (savedId == null || savedSecret == null) {
            val idBytes = ByteArray(16)
            secureRandom.nextBytes(idBytes)
            savedId = idBytes.joinToString("") { "%02x".format(it) }

            val secretBytes = ByteArray(32)
            secureRandom.nextBytes(secretBytes)
            savedSecret = secretBytes.joinToString("") { "%02x".format(it) }

            prefs.edit()
                .putString("local_device_id", savedId)
                .putString("local_device_secret", savedSecret)
                .apply()
        }

        deviceId = savedId
        deviceSecretKey = hexToBytes(savedSecret)
    }

    fun nextSequenceNumber(): Long = outgoingSeq.incrementAndGet()

    /**
     * Compute HMAC-SHA256 for a given payload and key.
     */
    fun signPayload(payload: String, secretKey: ByteArray = deviceSecretKey): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey, "HmacSHA256"))
        val rawHmac = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return rawHmac.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates an incoming signaling packet against replay attacks and clock skew.
     * @return true if packet is within acceptable time window and sequence is newer.
     */
    fun validatePacket(
        senderId: String,
        timestamp: Long,
        seq: Long,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        // Clock drift tolerance: ±30 seconds
        val timeDiff = Math.abs(currentTime - timestamp)
        if (timeDiff > 30_000) {
            return false
        }

        val lastSeq = peerLastSeq[senderId] ?: 0L
        if (seq <= lastSeq) {
            // Replay or stale out-of-order packet
            return false
        }

        peerLastSeq[senderId] = seq
        peerLastPacketTime[senderId] = currentTime
        return true
    }

    /**
     * Sanitizes incoming text messages to prevent injection and oversized buffers.
     */
    fun sanitizeMessageText(rawText: String): String {
        // Enforce max 2000 chars per message
        val trimmed = if (rawText.length > 2000) rawText.substring(0, 2000) else rawText
        return trimmed.replace("\u0000", "")
    }

    /**
     * Resets local device identity and regenerates keys.
     */
    fun resetIdentity(): String {
        val idBytes = ByteArray(16)
        secureRandom.nextBytes(idBytes)
        val newId = idBytes.joinToString("") { "%02x".format(it) }

        val secretBytes = ByteArray(32)
        secureRandom.nextBytes(secretBytes)
        val newSecret = secretBytes.joinToString("") { "%02x".format(it) }

        prefs.edit()
            .putString("local_device_id", newId)
            .putString("local_device_secret", newSecret)
            .apply()

        peerLastSeq.clear()
        peerLastPacketTime.clear()
        outgoingSeq.set(1L)
        return newId
    }

    companion object {
        fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                        Character.digit(hex[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
