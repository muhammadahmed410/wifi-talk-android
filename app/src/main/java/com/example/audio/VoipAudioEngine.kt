package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Full-duplex VoIP audio engine for direct peer-to-peer Wi-Fi calls.
 * Uses low-latency 8 kHz 16-bit PCM companded with ITU-T G.711 mu-law,
 * hardware Acoustic Echo Cancellation (AEC), and Noise Suppression (NS).
 */
class VoipAudioEngine(private val context: Context) {

    private val tag = "VoipAudioEngine"

    companion object {
        const val SAMPLE_RATE = 8000
        const val SAMPLES_PER_FRAME = 160 // 20ms frame at 8 kHz
        const val PACKET_HEADER_SIZE = 20
        const val MAGIC_HEADER = 0x5746544B // "WFTK"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val isRunning = AtomicBoolean(false)
    val isMuted = AtomicBoolean(false)
    val isSpeakerphone = AtomicBoolean(false)

    private var recordThread: Thread? = null
    private var playThread: Thread? = null
    private var udpSocket: DatagramSocket? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private val jitterQueue = LinkedBlockingQueue<ByteArray>(20)
    private val sendSeq = AtomicLong(0L)

    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeakerphone: Boolean = false

    /**
     * Start full-duplex VoIP session with target peer.
     * @param localPort Local UDP port to bind for incoming audio
     * @param remoteIp Target peer's IP address
     * @param remotePort Target peer's audio UDP port
     * @param callId Unique identifier for this call
     */
    @Synchronized
    fun startCall(localPort: Int, remoteIp: String, remotePort: Int, callId: String) {
        if (isRunning.get()) {
            stopCall()
        }

        try {
            savedAudioMode = audioManager.mode
            savedSpeakerphone = audioManager.isSpeakerphoneOn

            // Set communication mode for hardware echo canceling and earpiece routing
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = isSpeakerphone.get()

            udpSocket = DatagramSocket(localPort).apply {
                soTimeout = 2000
                reuseAddress = true
            }

            isRunning.set(true)
            jitterQueue.clear()
            sendSeq.set(0L)

            startRecording(remoteIp, remotePort, callId)
            startPlayback()
            startReceiving(callId)

            Log.i(tag, "VoIP Audio Engine started on port $localPort -> $remoteIp:$remotePort")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start VoIP Audio Engine", e)
            stopCall()
        }
    }

    private fun startRecording(remoteIp: String, remotePort: Int, callId: String) {
        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = Math.max(minBufSize, SAMPLES_PER_FRAME * 4)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            // Attach hardware AEC & NS if supported
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                    enabled = true
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply {
                    enabled = true
                }
            }

            record.startRecording()
            audioRecord = record

            recordThread = Thread({
                val pcmBuffer = ShortArray(SAMPLES_PER_FRAME)
                val encodedBytes = ByteArray(SAMPLES_PER_FRAME)
                val packetBytes = ByteArray(PACKET_HEADER_SIZE + SAMPLES_PER_FRAME)
                val targetAddress = InetAddress.getByName(remoteIp)
                val callHash = callId.hashCode()

                while (isRunning.get()) {
                    val readSamples = record.read(pcmBuffer, 0, SAMPLES_PER_FRAME)
                    if (readSamples == SAMPLES_PER_FRAME) {
                        if (isMuted.get()) {
                            // Muted: fill payload with silence
                            encodedBytes.fill(0x7F.toByte())
                        } else {
                            G711Codec.encodeMuLaw(pcmBuffer, readSamples, encodedBytes)
                        }

                        // Build packet header
                        val bb = ByteBuffer.wrap(packetBytes)
                        bb.putInt(MAGIC_HEADER)
                        bb.putInt(callHash)
                        bb.putLong(sendSeq.incrementAndGet())
                        bb.putInt((System.currentTimeMillis() and 0x7FFFFFFF).toInt())
                        bb.put(encodedBytes, 0, SAMPLES_PER_FRAME)

                        try {
                            val packet = DatagramPacket(
                                packetBytes,
                                packetBytes.size,
                                targetAddress,
                                remotePort
                            )
                            udpSocket?.send(packet)
                        } catch (e: Exception) {
                            if (!isRunning.get()) break
                        }
                    }
                }
            }, "VoipRecordThread").apply { start() }
        } catch (e: Exception) {
            Log.e(tag, "AudioRecord initialization error", e)
        }
    }

    private fun startPlayback() {
        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = Math.max(minBufSize, SAMPLES_PER_FRAME * 6)

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track

            playThread = Thread({
                val pcmOut = ShortArray(SAMPLES_PER_FRAME)
                var lastGoodFrame: ShortArray? = null

                while (isRunning.get()) {
                    // Pull frame from jitter queue (wait up to 30ms)
                    val frame = jitterQueue.poll(30, TimeUnit.MILLISECONDS)
                    if (frame != null && frame.size == SAMPLES_PER_FRAME) {
                        G711Codec.decodeMuLaw(frame, SAMPLES_PER_FRAME, pcmOut)
                        track.write(pcmOut, 0, SAMPLES_PER_FRAME)
                        lastGoodFrame = pcmOut.clone()
                    } else if (lastGoodFrame != null) {
                        // Packet loss concealment: attenuate last frame slightly
                        for (i in lastGoodFrame.indices) {
                            lastGoodFrame[i] = (lastGoodFrame[i] * 0.7f).toInt().toShort()
                        }
                        track.write(lastGoodFrame, 0, SAMPLES_PER_FRAME)
                    }
                }
            }, "VoipPlayThread").apply { start() }
        } catch (e: Exception) {
            Log.e(tag, "AudioTrack initialization error", e)
        }
    }

    private fun startReceiving(callId: String) {
        Thread({
            val receiveBuffer = ByteArray(PACKET_HEADER_SIZE + SAMPLES_PER_FRAME + 64)
            val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
            val callHash = callId.hashCode()

            while (isRunning.get()) {
                try {
                    udpSocket?.receive(packet)
                    if (packet.length >= PACKET_HEADER_SIZE + SAMPLES_PER_FRAME) {
                        val bb = ByteBuffer.wrap(packet.data, 0, packet.length)
                        val magic = bb.getInt()
                        val pktCallHash = bb.getInt()

                        // Verify magic header and call ID hash
                        if (magic == MAGIC_HEADER && pktCallHash == callHash) {
                            val seq = bb.getLong()
                            val ts = bb.getInt()
                            val audioData = ByteArray(SAMPLES_PER_FRAME)
                            bb.get(audioData, 0, SAMPLES_PER_FRAME)

                            // Offer to jitter buffer, dropping oldest if full to maintain low latency
                            if (!jitterQueue.offer(audioData)) {
                                jitterQueue.poll()
                                jitterQueue.offer(audioData)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is SocketException && !isRunning.get()) break
                    // Timeout is expected every 2000ms if peer silent
                }
            }
        }, "VoipReceiveThread").apply { start() }
    }

    fun setMute(muted: Boolean) {
        isMuted.set(muted)
    }

    fun setSpeakerphone(speakerOn: Boolean) {
        isSpeakerphone.set(speakerOn)
        audioManager.isSpeakerphoneOn = speakerOn
    }

    @Synchronized
    fun stopCall() {
        if (!isRunning.compareAndSet(true, false)) return

        try {
            recordThread?.interrupt()
            playThread?.interrupt()

            audioRecord?.apply {
                try { stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
            audioRecord = null

            audioTrack?.apply {
                try { stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
            audioTrack = null

            echoCanceler?.apply {
                try { release() } catch (_: Exception) {}
            }
            echoCanceler = null

            noiseSuppressor?.apply {
                try { release() } catch (_: Exception) {}
            }
            noiseSuppressor = null

            udpSocket?.close()
            udpSocket = null

            // Restore previous audio settings
            audioManager.mode = savedAudioMode
            audioManager.isSpeakerphoneOn = savedSpeakerphone

            jitterQueue.clear()
            Log.i(tag, "VoIP Audio Engine stopped")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping VoIP Audio Engine", e)
        }
    }
}
