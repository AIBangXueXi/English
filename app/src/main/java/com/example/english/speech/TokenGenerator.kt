package com.example.english.speech

import com.example.english.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import android.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection

object TokenGenerator {
    private val ACCESS_KEY_ID = BuildConfig.ALIYUN_ACCESS_KEY_ID
    private val ACCESS_KEY_SECRET = BuildConfig.ALIYUN_ACCESS_KEY_SECRET
    private const val TOKEN_ENDPOINT = "https://nls-meta.cn-shanghai.aliyuncs.com/pop/2018-05-18/tokens"

    private var cachedToken: String? = null
    private var tokenExpireTime: Long = 0

    suspend fun getToken(): String = withContext(Dispatchers.IO) {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireTime - 60_000) {
            return@withContext cachedToken!!
        }
        generateToken()
    }

    private fun generateToken(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = dateFormat.format(Date())
        val nonce = UUID.randomUUID().toString()

        val params = sortedMapOf(
            "AccessKeyId" to ACCESS_KEY_ID,
            "Action" to "CreateToken",
            "Format" to "JSON",
            "RegionId" to "cn-shanghai",
            "SignatureMethod" to "HMAC-SHA1",
            "SignatureNonce" to nonce,
            "SignatureVersion" to "1.0",
            "Timestamp" to timestamp,
            "Version" to "2019-02-28"
        )

        val canonicalizedQuery = params.map { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }.joinToString("&")

        val stringToSign = "GET&${urlEncode("/")}&${urlEncode(canonicalizedQuery)}"

        val signingKey = SecretKeySpec("${ACCESS_KEY_SECRET}&".toByteArray(), "HmacSHA1")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(signingKey)
        val rawSignature = mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
        val signature = Base64.encodeToString(rawSignature, Base64.NO_WRAP)

        val url = URL("$TOKEN_ENDPOINT?$canonicalizedQuery&Signature=${urlEncode(signature)}")
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        val response = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        val tokenObj = json.getJSONObject("Token")
        val token = tokenObj.getString("Id")
        val expireTime = tokenObj.getLong("ExpireTime")

        cachedToken = token
        tokenExpireTime = expireTime * 1000
        return token
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }
}
