package com.example.english.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.english.data.api.ApiWord
import com.example.english.data.api.WordApiService
import com.example.english.data.entity.KnownWord
import com.example.english.data.entity.UnknownWord
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
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

/**
 * Download a remote audio URL to a local temp file, then create a MediaPlayer
 * from the local file. This avoids HTTPS/MediaPlayer compatibility issues on
 * some devices (e.g. OPPO) that fail to stream directly from setDataSource(url).
 */
suspend fun createPlayerFromUrl(context: Context, url: String): MediaPlayer? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tempFile = java.io.File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "EnglishApp")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.connect()
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }
            conn.inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
        } catch (_: Exception) {
            return@withContext null
        }
        if (!tempFile.exists() || tempFile.length() < 44) {
            tempFile.delete()
            return@withContext null
        }
        android.media.MediaPlayer().apply {
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(tempFile.absolutePath)
            setOnCompletionListener {
                it.release()
                tempFile.delete()
            }
            setOnErrorListener { m, _, _ ->
                m.release()
                tempFile.delete()
                true
            }
            prepare()
            start()
        }
    }

/**
 * Download a remote audio URL, play it to completion, and suspend until it
 * finishes (or errors / the coroutine is cancelled).
 *
 * Unlike [createPlayerFromUrl] (which auto-releases the player on completion,
 * so polling `isPlaying()` on the released instance can throw on some devices),
 * this is driven by `setOnCompletionListener` / `setOnErrorListener`. The player
 * and temp file are always released/deleted, and no method is ever called on a
 * released player.
 *
 * [onSecondElapsed] is invoked roughly once per second of actual playback.
 * [speed] is the playback rate (1.0 = normal); applied via
 * MediaPlayer.setPlaybackParams (API 23+, minSdk here is 24).
 * Returns true when playback completed normally, false on error/cancellation.
 */
suspend fun playAudioAwait(
    context: Context,
    url: String,
    onSecondElapsed: () -> Unit,
    speed: Float = 1f
): Boolean = withContext(Dispatchers.IO) {
    val tempFile = java.io.File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")
    // 优先使用本地缓存，断网时也能播放
    var cached = AudioCache.get(context, url)
    if (cached != null) {
        // 直接复用缓存文件，播放完不删除（下次还能用）
    } else {
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "EnglishApp")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.connect()
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext false
            }
            conn.inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
        } catch (_: Exception) {
            tempFile.delete()
            return@withContext false
        }
        if (!tempFile.exists() || tempFile.length() < 44) {
            tempFile.delete()
            return@withContext false
        }
        // 下载成功则写入持久缓存；失败不影响本次播放
        cached = AudioCache.put(context, url, tempFile)
    }

    val fileToPlay = cached ?: tempFile

    val finished = java.util.concurrent.atomic.AtomicBoolean(false)
    val completedOk = java.util.concurrent.atomic.AtomicBoolean(false)
    val mp = MediaPlayer()
    try {
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setDataSource(fileToPlay.absolutePath)
        mp.setOnCompletionListener {
            completedOk.set(true)
            finished.set(true)
        }
        mp.setOnErrorListener { _, _, _ ->
            finished.set(true)
            true
        }
        mp.prepare()
        // 倍速播放（1.0 = 正常；设备不支持时忽略）
        if (speed > 0f && java.lang.Float.compare(speed, 1f) != 0) {
            try {
                val params = mp.playbackParams
                params.speed = speed
                mp.playbackParams = params
            } catch (_: Exception) { }
        }
        mp.start()

        var lastTick = System.currentTimeMillis()
        while (!finished.get()) {
            if (!isActive) break
            Thread.sleep(300)
            val now = System.currentTimeMillis()
            if (now - lastTick >= 1000) {
                lastTick = now
                onSecondElapsed()
            }
        }
    } finally {
        try { mp.release() } catch (_: Exception) {}
        // 只删除一次性临时文件；缓存文件保留供离线播放
        if (!tempFile.absolutePath.equals(fileToPlay.absolutePath, ignoreCase = true)) {
            tempFile.delete()
        }
    }
    completedOk.get()
}

class WordRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val knownDao = db.knownWordDao()
    private val unknownDao = db.unknownWordDao()
    private val prefs = context.getSharedPreferences("english_seq", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val taskStore = DailyTaskStore(context)

    /** 每日发现不认识单词相关偏好键 */
    private companion object {
        const val KEY_DAILY_UNKNOWN_LIMIT = "daily_unknown_found_limit"
        const val KEY_DAILY_UNKNOWN_DATE = "daily_unknown_found_date"
        const val KEY_DAILY_UNKNOWN_COUNT = "daily_unknown_found_count"
        const val DEFAULT_DAILY_UNKNOWN_LIMIT = 5
        const val DAILY_LIMIT_MIN = 5
        const val DAILY_LIMIT_MAX = 50

        // 连续两天答对才转认识的机制（背单词复习）
        const val CONSECUTIVE_DAYS_REQUIRED = 2
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    }

    /** 每日发现（标记）不认识单词的数量上限，达到后今日学习任务完成 */
    fun getDailyUnknownLimit(): Int =
        prefs.getInt(KEY_DAILY_UNKNOWN_LIMIT, DEFAULT_DAILY_UNKNOWN_LIMIT)

    fun setDailyUnknownLimit(limit: Int) {
        prefs.edit().putInt(KEY_DAILY_UNKNOWN_LIMIT, limit.coerceIn(DAILY_LIMIT_MIN, DAILY_LIMIT_MAX)).apply()
    }

    private fun todayKey(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

    /** 当天已发现的不认识单词数量（跨天自动重置） */
    fun dailyUnknownFoundCount(): Int {
        val today = todayKey()
        val storedDate = prefs.getString(KEY_DAILY_UNKNOWN_DATE, "") ?: ""
        return if (storedDate == today) prefs.getInt(KEY_DAILY_UNKNOWN_COUNT, 0) else 0
    }

    /** 今日学习任务是否已完成（当天发现数达到上限） */
    fun isDailyTaskDone(): Boolean =
        dailyUnknownFoundCount() >= getDailyUnknownLimit()

    /**
     * 记录一个新发现的不认识单词，返回今日任务是否因此完成。
     * 只在背单词过程中新词被标记为“不认识”时调用（复习词不算）。
     */
    fun recordUnknownFoundAndCheckDone(): Boolean {
        val today = todayKey()
        val storedDate = prefs.getString(KEY_DAILY_UNKNOWN_DATE, "") ?: ""
        val count = if (storedDate == today) prefs.getInt(KEY_DAILY_UNKNOWN_COUNT, 0) else 0
        val newCount = count + 1
        prefs.edit()
            .putString(KEY_DAILY_UNKNOWN_DATE, today)
            .putInt(KEY_DAILY_UNKNOWN_COUNT, newCount)
            .apply()
        // 同步记录到每日任务存储（用于首页任务/历史展示）
        taskStore.recordUnknownFound()
        return newCount >= getDailyUnknownLimit()
    }

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

    suspend fun resetAll() = withContext(Dispatchers.IO) {
        knownDao.deleteAll()
        unknownDao.deleteAll()
        updateStoredSeq(1)
    }

    // 连续两天答对才转认识的机制（背单词复习）：
    // - stage 表示「已连续答对天数」：0 = 今天刚标记不认识 / 答错后清零重来
    // - 复习时答对一次 stage+1；连续答对满 CONSECUTIVE_DAYS_REQUIRED 天即转认识
    // - 任何一天答错则 stage 归 0（连续天数清零，重新累计两天，即“往后顺延”）
    // - 每次复习间隔固定 +1 天，保证是“连续两天”而非同一天内多次

    suspend fun onUnknownWordCorrect(word: UnknownWord): Boolean = withContext(Dispatchers.IO) {
        val nextStreak = word.stage + 1
        if (nextStreak >= CONSECUTIVE_DAYS_REQUIRED) {
            knownDao.insert(word.toKnownWord())
            unknownDao.deleteById(word.id)
            true
        } else {
            unknownDao.updateStage(word.id, nextStreak, System.currentTimeMillis() + ONE_DAY_MS)
            false
        }
    }

    suspend fun onUnknownWordWrong(word: UnknownWord) = withContext(Dispatchers.IO) {
        // 任何一天答错：连续答对天数清零，明天重新开始累计
        unknownDao.updateStage(word.id, 0, System.currentTimeMillis() + ONE_DAY_MS)
    }

    suspend fun addToKnown(apiWord: ApiWord) = withContext(Dispatchers.IO) {
        knownDao.insert(apiWord.toKnownWord())
    }

    suspend fun addToUnknown(apiWord: ApiWord) = withContext(Dispatchers.IO) {
        // 今天标记为不认识：连续答对天数 0，明天开始第一次复习
        unknownDao.insert(
            apiWord.toUnknownWord(
                stage = 0,
                nextReviewTime = System.currentTimeMillis() + ONE_DAY_MS
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

    /** Sync: fetch all words from server, update all local known/unknown words with latest data. */
    suspend fun syncFromServer(): Int = withContext(Dispatchers.IO) {
        var updated = 0
        try {
            // 1) Fetch all words from server
            val serverWords = mutableMapOf<String, ApiWord>()
            var seq = 1
            while (true) {
                val response = api.getWords(seq = seq, num = 100)
                if (response.code != 0 || response.data.isEmpty()) break
                for (w in response.data) {
                    serverWords[w.id] = w
                }
                seq += response.data.size
                if (response.data.size < 100) break
            }
            if (serverWords.isEmpty()) return@withContext 0

            // 2) Update local known words
            val knownWords = knownDao.getAll()
            for (kw in knownWords) {
                val sw = serverWords[kw.wordId] ?: continue
                knownDao.updateFromServer(
                    kw.id, sw.pronunciation, sw.repeatVoice,
                    sw.phonetic, sw.translation, gson.toJson(sw.etymology),
                    gson.toJson(sw.etymologyPhonetic), gson.toJson(sw.etymologyPronunciation),
                    sw.plural, sw.thirdPersonSingular, sw.presentParticiple, sw.pastTense,
                    sw.categoryName, sw.remark
                )
                updated++
            }

            // 3) Update local unknown words
            val unknownWords = unknownDao.getAll()
            for (uw in unknownWords) {
                val sw = serverWords[uw.wordId] ?: continue
                unknownDao.updateFromServer(
                    uw.id, sw.pronunciation, sw.repeatVoice,
                    sw.phonetic, sw.translation, gson.toJson(sw.etymology),
                    gson.toJson(sw.etymologyPhonetic), gson.toJson(sw.etymologyPronunciation),
                    sw.plural, sw.thirdPersonSingular, sw.presentParticiple, sw.pastTense,
                    sw.categoryName, sw.remark
                )
                updated++
            }
        } catch (_: Exception) { }
        updated
    }

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
        remark = remark,
        repeatVoice = repeatVoice
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
        repeatVoice = repeatVoice,
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
        remark = remark,
        repeatVoice = repeatVoice
    )
}
