package com.example.english.data.api

data class ApiWord(
    val id: String,
    val seq: Int,
    val word: String,
    val phonetic: String,
    val pronunciation: String,
    val etymology: List<String>,
    val etymologyPhonetic: List<String>,
    val etymologyPronunciation: List<String>,
    val translation: String,
    val plural: String,
    val thirdPersonSingular: String,
    val presentParticiple: String,
    val pastTense: String,
    val categoryId: String,
    val categoryName: String,
    val remark: String,
    val createTime: String
)

data class ApiResponse(
    val code: Int,
    val data: List<ApiWord>,
    val message: String
)
