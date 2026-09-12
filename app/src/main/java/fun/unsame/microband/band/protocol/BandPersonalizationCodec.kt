package com.unsame.microband.band.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BandThemeColors(
    val base: Int,
    val highlight: Int,
    val lowlight: Int,
    val secondaryText: Int,
    val highContrast: Int,
    val muted: Int,
)

object BandPersonalizationCodec {
    const val ME_TILE_WIDTH = 310
    const val BAND_2_ME_TILE_HEIGHT = 128
    const val ME_TILE_BYTE_COUNT = ME_TILE_WIDTH * BAND_2_ME_TILE_HEIGHT * 2

    fun themeFromAccent(accent: Int): BandThemeColors {
        val opaque = accent or (0xFF shl 24)
        return BandThemeColors(
            base = opaque,
            highlight = blend(opaque, 0xFFFFFFFF.toInt(), 0.22f),
            lowlight = blend(opaque, 0xFF000000.toInt(), 0.28f),
            secondaryText = 0xFFD8D8D8.toInt(),
            highContrast = 0xFFFFFFFF.toInt(),
            muted = blend(opaque, 0xFF777777.toInt(), 0.55f),
        )
    }

    fun encodeTheme(theme: BandThemeColors): ByteArray = ByteBuffer.allocate(24)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(theme.base)
        .putInt(theme.highlight)
        .putInt(theme.lowlight)
        .putInt(theme.secondaryText)
        .putInt(theme.highContrast)
        .putInt(theme.muted)
        .array()

    private fun blend(left: Int, right: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = left ushr shift and 0xFF
            val b = right ushr shift and 0xFF
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
