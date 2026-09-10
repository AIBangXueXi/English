package com.example.english.data.api

import com.example.english.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DeepSeekService {

    private val API_KEY = BuildConfig.DEEPSEEK_API_KEY
    private const val API_URL = "https://api.deepseek.com/chat/completions"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun compareMeaning(userAnswer: String, expectedMeaning: String): Boolean =
        withContext(Dispatchers.IO) {
            val prompt = """
Compare a language learner's answer with the expected meaning. Judge if they match.

Expected meaning: "$expectedMeaning"
Learner's answer: "$userAnswer"

Rules:
- Match if the learner's answer captures at least one core meaning, even partially
- Accept synonyms, paraphrases, and approximate expressions
- Accept answers that express any one of multiple meanings (e.g. if expected is "昂贵的，贵重的", "贵的" or "不便宜" both match)
- Only reject if the answer is clearly about a completely different concept

Reply with ONLY one word: "true" or "false".
""".trimIndent()

            val json = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.0)
                put("max_tokens", 10)
            }

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody(JSON))
                .build()

            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext false
                val responseJson = JSONObject(body)
                val content = responseJson
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                content.lowercase().contains("true")
            } catch (e: Exception) {
                false
            }
        }
}
