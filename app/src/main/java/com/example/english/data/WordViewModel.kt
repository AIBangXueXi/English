package com.example.english.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.english.data.api.ApiWord
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
        val knownCount: Int
    ) : QuizState()
    data object Complete : QuizState()
    data class Error(val message: String) : QuizState()
}

class WordViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WordRepository(application)
    private val gson = Gson()

    private val _state = MutableStateFlow<QuizState>(QuizState.Loading)
    val state: StateFlow<QuizState> = _state

    private var pendingApiWords: List<ApiWord> = emptyList()
    private var currentApiIndex: Int = 0
    private var currentUnknownWord: UnknownWord? = null
    private var unknownCount: Int = 0
    private var knownCount: Int = 0

    init {
        loadNextWord()
    }

    fun loadNextWord() {
        viewModelScope.launch {
            _state.value = QuizState.Loading
            try {
                unknownCount = repository.getUnknownCount()
                knownCount = repository.getKnownCount()

                val unknown = repository.getNextUnknownWord(currentUnknownWord?.id ?: -1L)
                if (unknown != null) {
                    currentUnknownWord = unknown
                    _state.value = QuizState.Active(
                        word = unknown.toWord(),
                        isReview = true,
                        unknownCount = unknownCount,
                        knownCount = knownCount
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
                        knownCount = knownCount
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
                        knownCount = knownCount
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
                }
                currentApiIndex++
            } catch (e: Exception) {
                _state.value = QuizState.Error(e.message ?: "未知错误")
            }
        }
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

    private fun ApiWord.toWord() = Word(
        word = word,
        phonetic = phonetic,
        meaning = translation,
        pronunciation = resolveStaticUrl(pronunciation),
        etymology = etymology,
        etymologyPhonetic = etymologyPhonetic,
        etymologyPronunciation = etymologyPronunciation,
        plural = plural,
        thirdPersonSingular = thirdPersonSingular,
        presentParticiple = presentParticiple,
        pastTense = pastTense,
        categoryName = categoryName,
        remark = remark
    )

    private fun UnknownWord.toWord() = Word(
        word = word,
        phonetic = phonetic,
        meaning = meaning,
        pronunciation = resolveStaticUrl(pronunciation),
        etymology = fromJsonList(etymologyJson),
        etymologyPhonetic = fromJsonList(etymologyPhoneticJson),
        etymologyPronunciation = fromJsonList(etymologyPronunciationJson),
        plural = plural,
        thirdPersonSingular = thirdPersonSingular,
        presentParticiple = presentParticiple,
        pastTense = pastTense,
        categoryName = categoryName,
        remark = remark
    )

    private fun fromJsonList(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, listType)
    }
}
