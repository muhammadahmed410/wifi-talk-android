package com.example.audio

/**
 * ITU-T G.711 standard mu-law (PCMU) codec implementation.
 * Encodes 16-bit linear PCM audio into 8-bit logarithmic PCM.
 * Halves packet size and bandwidth, zero external native libraries required,
 * deterministic and ultra-low CPU overhead.
 */
object G711Codec {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    private val muLawCompressTable = ByteArray(256)
    private val muLawDecompressTable = ShortArray(256)

    init {
        for (i in 0 until 256) {
            muLawDecompressTable[i] = decodeMuLawByte(i.toByte())
        }
    }

    /**
     * Encodes linear 16-bit PCM buffer into 8-bit G.711 mu-law buffer.
     */
    fun encodeMuLaw(pcm: ShortArray, pcmLength: Int, output: ByteArray): Int {
        for (i in 0 until pcmLength) {
            output[i] = encodeMuLawSample(pcm[i])
        }
        return pcmLength
    }

    /**
     * Decodes 8-bit G.711 mu-law buffer into linear 16-bit PCM buffer.
     */
    fun decodeMuLaw(muLaw: ByteArray, muLawLength: Int, output: ShortArray): Int {
        for (i in 0 until muLawLength) {
            val unsignedIndex = muLaw[i].toInt() and 0xFF
            output[i] = muLawDecompressTable[unsignedIndex]
        }
        return muLawLength
    }

    private fun encodeMuLawSample(sample: Short): Byte {
        var sign = 0
        var pcm = sample.toInt()
        if (pcm < 0) {
            pcm = -pcm
            sign = 0x80
        }
        if (pcm > CLIP) pcm = CLIP
        pcm += BIAS

        var exponent = 7
        var mask = 0x4000
        while ((pcm and mask) == 0 && exponent > 0) {
            exponent--
            mask = mask shr 1
        }

        val mantissa = (pcm shr (exponent + 3)) and 0x0F
        val muLaw = (sign or (exponent shl 4) or mantissa).inv()
        return muLaw.toByte()
    }

    private fun decodeMuLawByte(muLawByte: Byte): Short {
        val inverted = (muLawByte.toInt().inv()) and 0xFF
        val sign = inverted and 0x80
        val exponent = (inverted shr 4) and 0x07
        val mantissa = inverted and 0x0F
        var sample = ((mantissa shl 3) + BIAS) shl exponent
        sample -= BIAS
        return (if (sign != 0) -sample else sample).toShort()
    }
}
