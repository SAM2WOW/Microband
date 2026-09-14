package com.unsame.microband.assistant

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject

class GeminiBandAssistant(private val keyStore: GeminiKeyStore) {
    fun isConfigured(): Boolean = keyStore.isConfigured()
    fun saveKey(value: String) = keyStore.save(value)
    fun clearKey() = keyStore.clear()

    fun answer(rawBandAudio: ByteArray, dictation: Boolean): String {
        val apiKey = keyStore.load() ?: error("Add a Gemini API key in Settings")
        require(rawBandAudio.isNotEmpty()) { "The Band did not send any voice audio" }
        val wav = if (dictation) pcm8MonoWav(decodeDictation(rawBandAudio)) else pcm16MonoWav(rawBandAudio)
        val instruction = if (dictation) {
            "Transcribe the audio exactly. Return only the transcription, at most 150 characters."
        } else {
            "You are the voice assistant on a Microsoft Band 2. Answer the spoken request directly in at most 150 characters. Plain text only."
        }
        val request = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put(
                "text",
                instruction,
            ))))
            put("contents", JSONArray().put(JSONObject().put("role", "user").put(
                "parts",
                JSONArray().put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "audio/wav")
                    .put("data", Base64.encodeToString(wav, Base64.NO_WRAP))))
                    .put(JSONObject().put("text", if (dictation) "Transcribe this audio." else "Transcribe this request, then answer it.")),
            )))
        }
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        connection.outputStream.use { it.write(request.toString().toByteArray()) }
        val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) {
            val message = runCatching { JSONObject(responseText).getJSONObject("error").getString("message") }.getOrNull()
            error(message ?: "Gemini request failed (${connection.responseCode})")
        }
        val response = JSONObject(responseText)
        return response.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            .replace(Regex("\\s+"), " ").trim().take(160)
            .ifBlank { error("Gemini returned an empty response") }
    }

    private fun pcm16MonoWav(pcm: ByteArray): ByteArray {
        return wav(pcm, bitsPerSample = 16, blockAlign = 2, bytesPerSecond = SAMPLE_RATE * 2)
    }

    private fun pcm8MonoWav(pcm: ByteArray): ByteArray {
        return wav(pcm, bitsPerSample = 8, blockAlign = 1, bytesPerSecond = SAMPLE_RATE)
    }

    private fun wav(pcm: ByteArray, bitsPerSample: Int, blockAlign: Int, bytesPerSecond: Int): ByteArray {
        val output = ByteArrayOutputStream(44 + pcm.size)
        fun text(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
        fun int(value: Int) = output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        fun short(value: Int) = output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
        text("RIFF"); int(36 + pcm.size); text("WAVEfmt "); int(16); short(1); short(1)
        int(SAMPLE_RATE); int(bytesPerSecond); short(blockAlign); short(bitsPerSample); text("data"); int(pcm.size); output.write(pcm)
        return output.toByteArray()
    }

    private fun decodeDictation(data: ByteArray): ByteArray = ByteArray(data.size / 2) { index ->
        (data[index * 2 + 1].toInt() + 128).toByte()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent"
    }
}
