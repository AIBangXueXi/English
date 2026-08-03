package com.example.english.data

import android.content.Context
import com.example.english.data.api.ApiWord
import com.example.english.data.api.WordApiService
import com.example.english.data.entity.KnownWord
import com.example.english.data.entity.UnknownWord
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Base URL for static assets (word pronunciation audio, etc.). */
const val STATIC_BASE_URL = "https://static.aibangxuexi.com"

/**
 * Resolve a pronunciation/audio path returned by the API into a full URL.
 * The API stores these as relative paths, so we prefix the static CDN host.
 * Already-absolute URLs (http/https) are returned unchanged.
 */
fun resolveStaticUrl(raw: String): String {
    if (raw.isEmpty()) return raw
    if (raw.startsWith("http://", ignoreCase = true) ||
        raw.startsWith("https://", ignoreCase = true)
    ) return raw
    return STATIC_BASE_URL + if (raw.startsWith("/")) raw else "/$raw"
}

/**
 * Resolve an audio source string into a playable value.
 * - Local bundled resources are written as "raw:<resName>" and returned unchanged.
 * - Everything else (relative path or absolute URL) is passed through [resolveStaticUrl].
 */
fun resolveAudioSource(raw: String): String =
    if (raw.startsWith("raw:", ignoreCase = true)) raw else resolveStaticUrl(raw)

/** Resolve a "raw:<resName>" reference to an Android raw resource id, or 0 if not found. */
fun resolveRawResId(context: Context, source: String): Int {
    if (!source.startsWith("raw:", ignoreCase = true)) return 0
    val name = source.substring(4).trim()
    if (name.isEmpty()) return 0
    return context.resources.getIdentifier(name, "raw", context.packageName)
}

class WordRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val knownDao = db.knownWordDao()
    private val unknownDao = db.unknownWordDao()
    private val prefs = context.getSharedPreferences("english_seq", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val api: WordApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://bxxapi.aibangxuexi.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WordApiService::class.java)
    }

    suspend fun getNextUnknownWord(excludeId: Long = -1L): UnknownWord? = withContext(Dispatchers.IO) {
        unknownDao.getFirstDue(System.currentTimeMillis(), excludeId)
    }

    suspend fun fetchNewWords(): List<ApiWord> = withContext(Dispatchers.IO) {
        val seq = getStoredSeq()
        val response = api.getWords(seq = seq, num = 20)
        if (response.code == 0 && response.data.isNotEmpty()) {
            response.data
        } else {
            emptyList()
        }
    }

    fun advanceSeq(count: Int) {
        val seq = getStoredSeq()
        updateStoredSeq(seq + count)
    }

    fun resetSeq() {
        updateStoredSeq(1)
    }

    // Ebbinghaus intervals in millis: 1d, 2d, 4d, 7d
    // (15min and 1h stages removed)
    private val ebbinghausIntervals = longArrayOf(
        24 * 60 * 60 * 1000L,
        2 * 24 * 60 * 60 * 1000L,
        4 * 24 * 60 * 60 * 1000L,
        7 * 24 * 60 * 60 * 1000L
    )

    suspend fun onUnknownWordCorrect(word: UnknownWord): Boolean = withContext(Dispatchers.IO) {
        val nextStage = word.stage + 1
        if (nextStage >= ebbinghausIntervals.size) {
            knownDao.insert(word.toKnownWord())
            unknownDao.deleteById(word.id)
            true
        } else {
            val nextTime = System.currentTimeMillis() + ebbinghausIntervals[word.stage]
            unknownDao.updateStage(word.id, nextStage, nextTime)
            false
        }
    }

    suspend fun onUnknownWordWrong(word: UnknownWord) = withContext(Dispatchers.IO) {
        // 答错后回到第 1 阶段，但下一次复习按第一阶段间隔排期（+1天），而不是立即（0=立即可复习）
        unknownDao.updateStage(word.id, 0, System.currentTimeMillis() + ebbinghausIntervals.first())
    }

    suspend fun addToKnown(apiWord: ApiWord) = withContext(Dispatchers.IO) {
        knownDao.insert(apiWord.toKnownWord())
    }

    suspend fun addToUnknown(apiWord: ApiWord) = withContext(Dispatchers.IO) {
        // 首次标记为不认识：进入待复习，第一次复习按第一阶段间隔排期（+1天），而非立即（0）
        val firstInterval = ebbinghausIntervals.first()
        unknownDao.insert(
            apiWord.toUnknownWord(
                stage = 0,
                nextReviewTime = System.currentTimeMillis() + firstInterval
            )
        )
    }

    fun getStoredSeq(): Int = prefs.getInt("next_seq", 1)

    private fun updateStoredSeq(seq: Int) {
        prefs.edit().putInt("next_seq", seq).apply()
    }

    suspend fun getUnknownCount(): Int = withContext(Dispatchers.IO) { unknownDao.count() }
    suspend fun getKnownCount(): Int = withContext(Dispatchers.IO) { knownDao.count() }

    suspend fun getKnownWords(): List<KnownWord> = withContext(Dispatchers.IO) { knownDao.getAll() }
    suspend fun getUnknownWords(): List<UnknownWord> = withContext(Dispatchers.IO) { unknownDao.getAll() }
    suspend fun deleteKnownWord(id: Long) = withContext(Dispatchers.IO) { knownDao.deleteById(id) }
    suspend fun deleteUnknownWord(id: Long) = withContext(Dispatchers.IO) { unknownDao.deleteById(id) }

    private fun ApiWord.toKnownWord() = KnownWord(
        wordId = id,
        word = word,
        phonetic = phonetic,
        meaning = translation,
        pronunciation = pronunciation,
        etymologyJson = gson.toJson(etymology),
        etymologyPhoneticJson = gson.toJson(etymologyPhonetic),
        etymologyPronunciationJson = gson.toJson(etymologyPronunciation),
        plural = plural,
        thirdPersonSingular = thirdPersonSingular,
        presentParticiple = presentParticiple,
        pastTense = pastTense,
        categoryName = categoryName,
        remark = remark
    )

    private fun ApiWord.toUnknownWord(stage: Int = 0, nextReviewTime: Long = 0) = UnknownWord(
        wordId = id,
        word = word,
        phonetic = phonetic,
        meaning = translation,
        pronunciation = pronunciation,
        etymologyJson = gson.toJson(etymology),
        etymologyPhoneticJson = gson.toJson(etymologyPhonetic),
        etymologyPronunciationJson = gson.toJson(etymologyPronunciation),
        plural = plural,
        thirdPersonSingular = thirdPersonSingular,
        presentParticiple = presentParticiple,
        pastTense = pastTense,
        categoryName = categoryName,
        remark = remark,
        stage = stage,
        nextReviewTime = nextReviewTime
    )

    private fun UnknownWord.toKnownWord() = KnownWord(
        wordId = wordId,
        word = word,
        phonetic = phonetic,
        meaning = meaning,
        pronunciation = pronunciation,
        etymologyJson = etymologyJson,
        etymologyPhoneticJson = etymologyPhoneticJson,
        etymologyPronunciationJson = etymologyPronunciationJson,
        plural = plural,
        thirdPersonSingular = thirdPersonSingular,
        presentParticiple = presentParticiple,
        pastTense = pastTense,
        categoryName = categoryName,
        remark = remark
    )
}
