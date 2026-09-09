package com.example.english.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.english.data.api.ApiWord
import com.example.english.data.entity.KnownWord
import com.example.english.data.entity.UnknownWord
import com.example.english.ui.screen.Word
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class QuizState {
    data object Loading : QuizState()
    data class Active(
        val word: Word,
        val isReview: Boolean,
        val unknownCount: Int,
        val knownCount: Int,
        val stage: Int = 0,
        /** 今日已发现的不认识单词数（背单词过程中标记"不认识"计入） */
        val dailyUnknownFound: Int = 0,
        /** 今日需要发现的不认识单词目标数 */
        val dailyUnknownLimit: Int = 0
    ) : QuizState()
    data object Complete : QuizState()
    data object DailyComplete : QuizState()
    data class Error(val message: String) : QuizState()
}

class WordViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WordRepository(application)
    private val gson = Gson()

    private val _state = MutableStateFlow<QuizState>(QuizState.Loading)
    val state: StateFlow<QuizState> = _state

    private val _libraryKnownWords = MutableStateFlow<List<KnownWord>>(emptyList())
    val libraryKnownWords: StateFlow<List<KnownWord>> = _libraryKnownWords

    private val _libraryUnknownWords = MutableStateFlow<List<UnknownWord>>(emptyList())
    val libraryUnknownWords: StateFlow<List<UnknownWord>> = _libraryUnknownWords

    private var pendingApiWords: List<ApiWord> = emptyList()
    private var currentApiIndex: Int = 0
    private var currentUnknownWord: UnknownWord? = null
    private var unknownCount: Int = 0
    private var knownCount: Int = 0
    /** 今日发现数已达上限，等磨耳音频播完后再切到 DailyComplete 界面 */
    private val _pendingDailyComplete = MutableStateFlow(false)
    val pendingDailyComplete: StateFlow<Boolean> = _pendingDailyComplete

    init {
        loadNextWord()
    }

    fun loadNextWord() {
        viewModelScope.launch {
            _state.value = QuizState.Loading
            try {
                // 今日发现的不认识单词已达到上限 → 今日学习任务完成
                if (repository.isDailyTaskDone()) {
                    _state.value = QuizState.DailyComplete
                    return@launch
                }

                unknownCount = repository.getUnknownCount()
                knownCount = repository.getKnownCount()
                val dailyUnknownFound = repository.dailyUnknownFoundCount()
                val dailyUnknownLimit = repository.getDailyUnknownLimit()

                val unknown = repository.getNextUnknownWord(currentUnknownWord?.id ?: -1L)
                if (unknown != null) {
                    currentUnknownWord = unknown
                    _state.value = QuizState.Active(
                        word = unknown.toWord(),
                        isReview = true,
                        unknownCount = unknownCount,
                        knownCount = knownCount,
                        stage = unknown.stage,
                        dailyUnknownFound = dailyUnknownFound,
                        dailyUnknownLimit = dailyUnknownLimit
                    )
                    return@launch
                }

                if (currentApiIndex < pendingApiWords.size) {
                    val apiWord = pendingApiWords[currentApiIndex]
                    currentUnknownWord = null
                    _state.value = QuizState.Active(
                        word = apiWord.toWord(),
                        isReview = false,
                        unknownCount = unknownCount,
                        knownCount = knownCount,
                        dailyUnknownFound = dailyUnknownFound,
                        dailyUnknownLimit = dailyUnknownLimit
                    )
                    return@launch
                }

                pendingApiWords = repository.fetchNewWords()
                currentApiIndex = 0
                if (pendingApiWords.isNotEmpty()) {
                    val apiWord = pendingApiWords[currentApiIndex]
                    currentUnknownWord = null
                    _state.value = QuizState.Active(
                        word = apiWord.toWord(),
                        isReview = false,
                        unknownCount = unknownCount,
                        knownCount = knownCount,
                        dailyUnknownFound = dailyUnknownFound,
                        dailyUnknownLimit = dailyUnknownLimit
                    )
                } else {
                    _state.value = QuizState.Complete
                }
            } catch (e: Exception) {
                _state.value = QuizState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun onCorrectAnswer() {
        viewModelScope.launch {
            try {
                val unknown = currentUnknownWord
                if (unknown != null) {
                    repository.onUnknownWordCorrect(unknown)
                } else {
                    val apiWord = pendingApiWords.getOrNull(currentApiIndex) ?: return@launch
                    repository.addToKnown(apiWord)
                    repository.advanceSeq(1)
                }
                currentApiIndex++
            } catch (e: Exception) {
                _state.value = QuizState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun onWrongAnswer() {
        viewModelScope.launch {
            try {
                val unknown = currentUnknownWord
                if (unknown != null) {
                    repository.onUnknownWordWrong(unknown)
                } else {
                    val apiWord = pendingApiWords.getOrNull(currentApiIndex) ?: return@launch
                    repository.addToUnknown(apiWord)
                    repository.advanceSeq(1)
                    // 新词被标记为不认识：计入今日发现数，达到上限则今日任务完成。
                    // 此时先记录 pending，不立即置 DailyComplete——等磨耳音频播完后再由
                    // completeDailyIfPending() 切换界面，避免音频被中断。
                    val done = repository.recordUnknownFoundAndCheckDone(apiWord.word)
                    updateDailyFoundCount()
                    if (done) {
                        _pendingDailyComplete.value = true
                        return@launch
                    }
                }
                currentApiIndex++
            } catch (e: Exception) {
                _state.value = QuizState.Error(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 磨耳音频播放完毕后由 UI 调用；若今日发现数已达上限则切换到 DailyComplete 界面。
     */
    fun completeDailyIfPending() {
        if (_pendingDailyComplete.value) {
            _pendingDailyComplete.value = false
            _state.value = QuizState.DailyComplete
        }
    }

    /** 刷新当前 Active 状态里的"今日已发现/目标"计数，供背单词界面实时展示。 */
    private fun updateDailyFoundCount() {
        val current = _state.value as? QuizState.Active ?: return
        _state.value = current.copy(
            dailyUnknownFound = repository.dailyUnknownFoundCount(),
            dailyUnknownLimit = repository.getDailyUnknownLimit()
        )
    }

    fun resetProgress() {
        viewModelScope.launch {
            repository.resetSeq()
            pendingApiWords = emptyList()
            currentApiIndex = 0
            currentUnknownWord = null
            loadNextWord()
        }
    }

    fun resetAll(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.resetAll()
            pendingApiWords = emptyList()
            currentApiIndex = 0
            currentUnknownWord = null
            refreshLibrary()
            onDone()
        }
    }

    /** Load the full contents of both local library tables. */
    fun refreshLibrary() {
        viewModelScope.launch {
            _libraryKnownWords.value = repository.getKnownWords()
            _libraryUnknownWords.value = repository.getUnknownWords()
        }
    }

    fun deleteKnownWord(id: Long) {
        viewModelScope.launch {
            repository.deleteKnownWord(id)
            refreshLibrary()
        }
    }

    fun deleteUnknownWord(id: Long) {
        viewModelScope.launch {
            repository.deleteUnknownWord(id)
            refreshLibrary()
        }
    }

    /** Sync: re-fetch all words from the server to update local repeatVoice fields. */
    fun syncFromServer(onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = repository.syncFromServer()
            refreshLibrary()
            onResult(count)
        }
    }

    /** Get list of unknown words that have repeatVoice for 磨耳 playback. */
    suspend fun getMoErWords(): List<String> {
        return repository.getUnknownWords()
            .filter { it.repeatVoice.isNotBlank() }
            .map { resolveStaticUrl(it.repeatVoice) }
    }

    /**
     * Get the word pool for standalone training modes (默写/发音/意思):
     * only unknown words (不认识) from the local library.
     */
    suspend fun getTrainingWords(): List<Word> {
        return repository.getUnknownWords().map { it.toWord() }
    }

    private fun ApiWord.toWord() = Word(
        word = word,
        phonetic = phonetic,
        meaning = translation,
        pronunciation = resolveAudioSource(pronunciation),
        etymology = etymology,
        etymologyPhonetic = etymologyPhonetic,
        etymologyPronunciation = etymologyPronunciation.map { resolveAudioSource(it) },
        plural = plural,
        thirdPersonSingular = thirdPersonSingular,
        presentParticiple = presentParticiple,
        pastTense = pastTense,
        categoryName = categoryName,
        remark = remark,
        repeatVoice = resolveStaticUrl(repeatVoice)
    )

    private fun UnknownWord.toWord() = Word(
        word = word,
        phonetic = phonetic,
        meaning = meaning,
        pronunciation = resolveStaticUrl(pronunciation),
        etymology = fromJsonList(etymologyJson),
        etymologyPhonetic = fromJsonList(etymologyPhoneticJson),
        etymologyPronunciation = fromJsonList(etymologyPronunciationJson).map { resolveAudioSource(it) },
        plural = plural,
        thirdPersonSingular = thirdPersonSingular,
        presentParticiple = presentParticiple,
        pastTense = pastTense,
        categoryName = categoryName,
        remark = remark,
        repeatVoice = resolveStaticUrl(repeatVoice)
    )

    private fun KnownWord.toWord() = Word(
        word = word,
        phonetic = phonetic,
        meaning = meaning,
        pronunciation = resolveStaticUrl(pronunciation),
        etymology = fromJsonList(etymologyJson),
        etymologyPhonetic = fromJsonList(etymologyPhoneticJson),
        etymologyPronunciation = fromJsonList(etymologyPronunciationJson).map { resolveAudioSource(it) },
        plural = plural,
        thirdPersonSingular = thirdPersonSingular,
        presentParticiple = presentParticiple,
        pastTense = pastTense,
        categoryName = categoryName,
        remark = remark,
        repeatVoice = resolveStaticUrl(repeatVoice)
    )

    private fun fromJsonList(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, listType)
    }
}
