package com.example.english.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.net.ssl.HttpsURLConnection

class SpeechService(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile var isRecording = false
        private set

    var lastWavFile: File? = null
        private set
    var lastPcmData: ByteArray? = null
        private set
    private var pcmFile: File? = null

    companion object {
        private const val TAG = "SpeechService"
        private const val APP_KEY = "***REMOVED***"
        private const val ASR_ENDPOINT = "https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/asr"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("没有录音权限，请先授权麦克风权限")
        }
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBuf, SAMPLE_RATE * 2) // at least 1 second buffer

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            throw IllegalStateException("AudioRecord 初始化失败，请检查麦克风权限")
        }

        audioRecord?.startRecording()
        isRecording = true

        pcmFile = File(context.cacheDir, "recording.pcm")
        pcmFile?.delete()

        val pcm = pcmFile
        recordingThread = Thread {
            try {
                val buffer = ByteArray(bufferSize)
                FileOutputStream(pcm).use { out ->
                    while (isRecording) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                        if (read > 0) {
                            out.write(buffer, 0, read)
                        } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "AudioRecord ERROR_INVALID_OPERATION")
                            break
                        } else if (read == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "AudioRecord ERROR_BAD_VALUE")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
            }
        }
        recordingThread?.start()
        Log.d(TAG, "Recording started")
    }

    suspend fun stopAndRecognize(): Result<String> = withContext(Dispatchers.IO) {
        isRecording = false
        recordingThread?.join(3000)
        recordingThread = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord?.release()
        audioRecord = null

        Log.d(TAG, "Recording stopped")

        val pcm = pcmFile
        if (pcm == null || !pcm.exists() || pcm.length() == 0L) {
            return@withContext Result.failure(Exception("未录制到声音"))
        }

        val pcmData = pcm.readBytes()
        lastPcmData = pcmData
        val durationSec = pcmData.size / (SAMPLE_RATE * 2.0)
        Log.d(TAG, "PCM data size: ${pcmData.size} bytes (${"%.2f".format(durationSec)}s)")

        // Analyze audio for silence
        val maxLevel = analyzeAudioLevel(pcmData)
        Log.d(TAG, "Audio max level: $maxLevel (0=completely silent, >100=audible)")
        if (maxLevel < 10) {
            Log.w(TAG, "Recording appears to be silent — check emulator microphone settings")
            return@withContext Result.failure(Exception("录音音量过低，请检查麦克风是否正常工作"))
        }

        if (durationSec < 1.0) {
            return@withContext Result.failure(Exception("录音时间太短，请长按说话"))
        }

        // Write WAV file for playback
        lastWavFile = File(context.cacheDir, "recording.wav")
        writeWav(lastWavFile!!, pcmData)
        Log.d(TAG, "WAV file written: ${lastWavFile!!.length()} bytes")

        // Send raw PCM to ASR（断网/超时等任何网络异常都不能崩溃，统一转为失败结果）
        try {
            val token = TokenGenerator.getToken()
            Log.d(TAG, "Got token: ${token.take(10)}...")

            val url = java.net.URL("$ASR_ENDPOINT?appkey=$APP_KEY&format=pcm&sample_rate=$SAMPLE_RATE&enable_intermediate_result=false")
            val conn = url.openConnection() as HttpsURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-NLS-Token", token)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("Content-Length", pcmData.size.toString())
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.doOutput = true

            conn.outputStream.use { it.write(pcmData) }

            val responseCode = conn.responseCode
            Log.d(TAG, "ASR response code: $responseCode")

            val responseText = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            Log.d(TAG, "ASR response body: $responseText")

            if (responseText.isBlank()) {
                return@withContext Result.failure(Exception("语音识别失败：网络异常，请检查网络后重试"))
            }

            val json = JSONObject(responseText)
            val status = json.optInt("status", -1)
            if (status == 20000000) {
                val result = json.optString("result", "")
                if (result.isEmpty()) {
                    Result.failure(Exception("未识别到语音内容，请大声朗读单词"))
                } else {
                    Result.success(result.trim())
                }
            } else {
                val errorMsg = json.optString("message", "识别失败")
                Result.failure(Exception("$errorMsg (code: $status)"))
            }
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "ASR failed: no network", e)
            Result.failure(Exception("语音识别失败：当前没有网络连接，请联网后重试"))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "ASR failed: timeout", e)
            Result.failure(Exception("语音识别超时，请检查网络后重试"))
        } catch (e: java.io.IOException) {
            Log.e(TAG, "ASR failed: io error", e)
            Result.failure(Exception("语音识别失败：网络异常，请检查网络后重试"))
        } catch (e: Exception) {
            Log.e(TAG, "ASR failed", e)
            Result.failure(Exception("语音识别失败：${e.message ?: "未知错误"}"))
        }
    }

    fun cleanup() {
        isRecording = false
        recordingThread?.interrupt()
        audioRecord?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        audioRecord = null
        pcmFile?.delete()
        pcmFile = null
        lastPcmData = null
    }

    /**
     * Returns the peak audio level from PCM 16-bit data.
     * < 10  = essentially silent (emulator mic not working)
     * 10-100 = quiet
     * > 100  = normal speech
     */
    private fun analyzeAudioLevel(pcmData: ByteArray): Int {
        var maxSample = 0
        for (i in 0 until pcmData.size - 1 step 2) {
            val sample = ((pcmData[i + 1].toInt() shl 8) or (pcmData[i].toInt() and 0xFF)).toShort()
            val abs = if (sample < 0) -sample.toInt() else sample.toInt()
            if (abs > maxSample) maxSample = abs
        }
        return maxSample // 0–32767 range for 16-bit audio
    }

    private fun writeWav(file: File, pcmData: ByteArray) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val dataSize = pcmData.size
        val fileSize = dataSize + 36

        FileOutputStream(file).use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.write(intToLittleEndian(fileSize))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.write(intToLittleEndian(16))
            out.write(shortToLittleEndian(1))     // PCM = 1
            out.write(shortToLittleEndian(channels.toShort()))
            out.write(intToLittleEndian(SAMPLE_RATE))
            out.write(intToLittleEndian(byteRate))
            out.write(shortToLittleEndian(blockAlign))
            out.write(shortToLittleEndian(bitsPerSample.toShort()))
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.write(intToLittleEndian(dataSize))
            out.write(pcmData)
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    private fun shortToLittleEndian(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            (value.toInt() shr 8 and 0xFF).toByte()
        )
    }
}
