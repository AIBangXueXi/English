package com.example.english.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.constraint.ResultBody
import com.xs.SingEngine
import com.xs.impl.ResultListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 驰声(ssound)口语评测——发音打分。
 *
 * 替代旧的 ASR 识别 + 字符串比对：用 en.word.score 题型返回 0-100 的发音得分。
 * 引擎内部自行录音（push-to-talk：startWord 开始录音，stop 结束并出分），
 * 因此无需 VAD 资源、无需离线 resource.zip。
 *
 * 鉴权凭证来自后端 `POST /bxx_en_android/oral-eval/warrant`（appKey/secretKey/warrantId/expireTime）。
 */
class OralEvalService(private val context: Context) {

    data class EvalResult(
        val score: Int,          // 0-100 发音得分
        val passed: Boolean,     // 是否达到通过线
        val rawJson: String,     // 完整评测结果 JSON（调试用）
        val error: String?       // 失败原因，成功为 null
    )

    companion object {
        private const val TAG = "OralEvalService"
        private const val BASE_URL = "https://bxxapi.aibangxuexi.com"
        private const val CORE_TYPE = "en.word.score"
        private const val PASS_SCORE = 60
    }

    private data class Warrant(
        val appKey: String,
        val secretKey: String,
        val warrantId: String,
        val expireTime: Long // Unix 秒级时间戳
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: SingEngine? = null
    @Volatile private var warrant: Warrant? = null
    private var userId: String = "guest"
    @Volatile private var callback: ((EvalResult) -> Unit)? = null
    @Volatile private var lastResultJson: String? = null

    private val listener = object : ResultListener {
        override fun onResult(result: JSONObject) {
            // 最终评测结果（含得分）经 onResult 回调；onEnd 只给 code/message。
            lastResultJson = result.toString()
            Log.d(TAG, "onResult: $lastResultJson")
        }

        override fun onEnd(body: ResultBody) {
            Log.d(TAG, "onEnd code=${body.code} msg=${body.message}")
            val cb = callback ?: return
            if (body.code == 0) {
                val score = parseScore(lastResultJson ?: "")
                mainHandler.post { cb(EvalResult(score, score >= PASS_SCORE, lastResultJson ?: "", null)) }
            } else {
                mainHandler.post { cb(EvalResult(0, false, "", body.message ?: "评测失败")) }
            }
        }

        override fun onBegin() {}
        override fun onUpdateVolume(volume: Int) {}
        override fun onFrontVadTimeOut() {}
        override fun onBackVadTimeOut() {}
        override fun onRecordingBuffer(buffer: ByteArray, length: Int) {}
        override fun onRecordLengthOut() {}
        override fun onReady() {}
        override fun onPlayCompeleted() {}
        override fun onRecordStop() {}
    }

    /** 拉取鉴权凭证并初始化评测引擎（幂等；凭证过期会自动刷新）。 */
    suspend fun ensureReady(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (engine == null || warrant == null || isExpired()) {
                userId = deviceUserId()
                val w = fetchWarrant(userId)
                initEngine(w)
                warrant = w
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "ensureReady failed", e)
            Result.failure(e)
        }
    }

    /** 开始评测一个单词；结果经 [onResult] 在主线程回调。 */
    fun startWord(word: String, onResult: (EvalResult) -> Unit) {
        val e = engine
        if (e == null) {
            onResult(EvalResult(0, false, "", "评测引擎未初始化"))
            return
        }
        callback = onResult
        lastResultJson = null
        try {
            val request = JSONObject().apply {
                put("coreType", CORE_TYPE)
                put("refText", word)
                put("rank", 100)
            }
            e.setStartCfg(e.buildStartJson(userId, request))
            e.start()
        } catch (ex: Exception) {
            Log.e(TAG, "startWord failed", ex)
            callback = null
            onResult(EvalResult(0, false, "", "启动评测失败: ${ex.message}"))
        }
    }

    /** 结束当前录音并触发评测出分（按住松开时调用）。 */
    fun stop() {
        try { engine?.stop() } catch (e: Exception) { Log.w(TAG, "stop failed", e) }
    }

    fun release() {
        callback = null
        try { engine?.deleteSafe() } catch (_: Exception) {}
        engine = null
        warrant = null
    }

    private fun initEngine(w: Warrant) {
        val e = SingEngine.newInstance(context.applicationContext)
        e.setListener(listener)
        // authTimeout 为凭证过期时间戳（Unix 秒），buildStartJson 时写入 warrantId
        e.setAuthInfo(w.warrantId, w.expireTime)
        e.setNewCfg(e.buildInitJson(w.appKey, w.secretKey))
        e.createEngine()
        engine = e
    }

    private fun isExpired(): Boolean {
        val w = warrant ?: return true
        return System.currentTimeMillis() / 1000 + 300 >= w.expireTime
    }

    private fun deviceUserId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "guest"

    private fun fetchWarrant(userId: String): Warrant {
        val conn = URL("$BASE_URL/bxx_en_android/oral-eval/warrant").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.doOutput = true
        val payload = JSONObject().put("userId", userId).toString()
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw Exception("获取评测凭证失败 HTTP $code")

        val root = JSONObject(text)
        if (root.optInt("code", -1) != 0) throw Exception(root.optString("message", "获取评测凭证失败"))
        val data = root.optJSONObject("data") ?: throw Exception("评测凭证数据为空")
        return Warrant(
            appKey = data.optString("appKey"),
            secretKey = data.optString("secretKey"),
            warrantId = data.optString("warrantId"),
            expireTime = data.optString("expireTime", "0").toLongOrNull() ?: 0L
        )
    }

    /** 从评测结果 JSON 取总分（0-100）。字段名按 en.word.score 常见结构兜底，拿不到记 0 分。 */
    private fun parseScore(json: String): Int {
        if (json.isBlank()) return 0
        return try {
            parseScore(JSONObject(json))
        } catch (_: Exception) {
            0
        }
    }

    private fun parseScore(o: JSONObject): Int {
        val keys = listOf("overall", "score", "totalScore", "total_score")
        for (k in keys) {
            val v = o.optDouble(k, -1.0)
            if (v in 0.0..100.0) return v.toInt()
        }
        o.optJSONObject("result")?.let { r ->
            for (k in keys) {
                val v = r.optDouble(k, -1.0)
                if (v in 0.0..100.0) return v.toInt()
            }
        }
        o.optJSONArray("lines")?.optJSONObject(0)?.let { l ->
            val v = l.optDouble("score", -1.0)
            if (v in 0.0..100.0) return v.toInt()
            l.optJSONArray("words")?.optJSONObject(0)?.let { w ->
                val vw = w.optDouble("score", -1.0)
                if (vw in 0.0..100.0) return vw.toInt()
            }
        }
        return 0
    }
}
