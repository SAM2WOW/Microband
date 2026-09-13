package com.unsame.microband.band.protocol

import com.unsame.microband.band.model.BandTileInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BandTileCodec {
    const val RECORD_SIZE = 88

    fun decode(payload: ByteArray): List<BandTileInfo> {
        require(payload.size >= 4)
        val count = BandPacketCodec.readInt(payload).coerceIn(0, (payload.size - 4) / RECORD_SIZE)
        return (0 until count).map { index ->
            val raw = payload.copyOfRange(4 + index * RECORD_SIZE, 4 + (index + 1) * RECORD_SIZE)
            val nameLength = BandPacketCodec.readShort(raw, 24).coerceIn(0, 29)
            BandTileInfo(
                id = raw.copyOfRange(0, 16).joinToString("") { "%02X".format(it) },
                name = raw.copyOfRange(28, 28 + nameLength * 2).toString(Charsets.UTF_16LE).ifBlank { "Unnamed tile" },
                order = BandPacketCodec.readInt(raw, 16),
                settingsMask = BandPacketCodec.readShort(raw, 26),
                wireData = raw,
            )
        }.sortedBy { it.order }
    }

    fun encode(tiles: List<BandTileInfo>): ByteArray {
        val output = ByteBuffer.allocate(4 + tiles.size * RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        output.putInt(tiles.size)
        tiles.forEachIndexed { index, tile ->
            val raw = tile.wireData.copyOf()
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putInt(16, index)
            output.put(raw)
        }
        return output.array()
    }
}
