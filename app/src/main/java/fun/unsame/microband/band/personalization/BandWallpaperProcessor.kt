package com.unsame.microband.band.personalization

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.unsame.microband.band.protocol.BandPersonalizationCodec

object BandWallpaperProcessor {
    fun decodeAndCrop(contentResolver: ContentResolver, uri: Uri): ByteArray {
        val source = ImageDecoder.createSource(contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
        val width = BandPersonalizationCodec.ME_TILE_WIDTH
        val height = BandPersonalizationCodec.BAND_2_ME_TILE_HEIGHT
        val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val sourceRatio = decoded.width.toFloat() / decoded.height
        val targetRatio = width.toFloat() / height
        val crop = if (sourceRatio > targetRatio) {
            val cropWidth = (decoded.height * targetRatio).toInt()
            val left = (decoded.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, decoded.height)
        } else {
            val cropHeight = (decoded.width / targetRatio).toInt()
            val top = (decoded.height - cropHeight) / 2
            Rect(0, top, decoded.width, top + cropHeight)
        }
        Canvas(target).drawBitmap(
            decoded,
            crop,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        decoded.recycle()

        val pixels = IntArray(width * height)
        target.getPixels(pixels, 0, width, 0, 0, width, height)
        target.recycle()
        return ByteArray(pixels.size * 2).also { output ->
            pixels.forEachIndexed { index, color ->
                val red = color ushr 16 and 0xFF
                val green = color ushr 8 and 0xFF
                val blue = color and 0xFF
                val rgb565 = ((red shr 3) shl 11) or ((green shr 2) shl 5) or (blue shr 3)
                output[index * 2] = rgb565.toByte()
                output[index * 2 + 1] = (rgb565 ushr 8).toByte()
            }
        }
    }
}
