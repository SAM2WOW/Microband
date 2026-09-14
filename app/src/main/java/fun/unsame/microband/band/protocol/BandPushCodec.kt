package com.unsame.microband.band.protocol

data class BandReply(val notificationId: Int, val text: String)

object BandPushCodec {
    /** Decodes the fixed-size Envoy SMS response delivered by push service packet 100. */
    fun decodeReply(payload: ByteArray): BandReply? {
        if (payload.size < TEXT_OFFSET) return null
        val notificationId = BandPacketCodec.readInt(payload, 20)
        val requestedLength = BandPacketCodec.readShort(payload, 24)
        val available = payload.size - TEXT_OFFSET
        val byteCount = requestedLength.coerceIn(0, available).let { it - (it % 2) }
        val text = payload.copyOfRange(TEXT_OFFSET, TEXT_OFFSET + byteCount)
            .toString(Charsets.UTF_16LE).trim('\u0000', ' ')
        return text.takeIf { it.isNotBlank() }?.let { BandReply(notificationId, it) }
    }

    private const val TEXT_OFFSET = 26
}
