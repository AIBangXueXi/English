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

    suspend fun getNextUnknownWord(): UnknownWord? = withContext(Dispatchers.IO) {
        unknownDao.getFirst()
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

    suspend fun onUnknownWordCorrect(word: UnknownWord): Boolean = withContext(Dispatchers.IO) {
        val newCount = word.correctCount + 1
        if (newCount >= 5) {
            knownDao.insert(word.toKnownWord())
            unknownDao.deleteById(word.id)
            true
        } else {
            unknownDao.incrementCorrectCount(word.id)
            false
        }
    }

    suspend fun addToKnown(apiWord: ApiWord) = withContext(Dispatchers.IO) {
        knownDao.insert(apiWord.toKnownWord())
    }

    suspend fun addToUnknown(apiWord: ApiWord) = withContext(Dispatchers.IO) {
        unknownDao.insert(apiWord.toUnknownWord())
    }

    fun getStoredSeq(): Int = prefs.getInt("next_seq", 1)

    private fun updateStoredSeq(seq: Int) {
        prefs.edit().putInt("next_seq", seq).apply()
    }

    suspend fun getUnknownCount(): Int = withContext(Dispatchers.IO) { unknownDao.count() }
    suspend fun getKnownCount(): Int = withContext(Dispatchers.IO) { knownDao.count() }

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

    private fun ApiWord.toUnknownWord() = UnknownWord(
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
        correctCount = 0
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
