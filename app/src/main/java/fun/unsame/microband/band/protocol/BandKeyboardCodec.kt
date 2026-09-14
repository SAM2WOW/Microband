package com.unsame.microband.band.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object BandKeyboardCodec {
    const val INIT = 0
    const val STROKE = 1
    const val CANDIDATES_FOR_NEXT_WORD = 2
    const val CANDIDATES_FOR_WORD = 3
    const val END = 4
    const val PRE_INIT = 5
    const val TRY_RELEASE = 6
    const val PRE_INIT_V2 = 7

    fun eventType(payload: ByteArray): Int? = payload.firstOrNull()?.toInt()?.and(0xFF)

    fun command(type: Int, candidates: List<String> = emptyList()): ByteArray {
        val encoded = ByteArray(400)
        var offset = 0
        var count = 0
        candidates.take(4).forEach { candidate ->
            val bytes = candidate.toByteArray(Charsets.UTF_16LE)
            if (offset + bytes.size + 2 <= encoded.size) {
                bytes.copyInto(encoded, offset)
                offset += bytes.size + 2
                count++
            }
        }
        return ByteBuffer.allocate(407).order(ByteOrder.LITTLE_ENDIAN)
            .put(type.toByte()).put(count.toByte()).put(0).putInt(offset).put(encoded).array()
    }
}
