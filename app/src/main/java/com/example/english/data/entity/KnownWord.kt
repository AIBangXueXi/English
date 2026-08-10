package com.example.english.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_words")
data class KnownWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: String,
    val word: String,
    val phonetic: String,
    val meaning: String,
    val pronunciation: String = "",
    val etymologyJson: String = "",
    val etymologyPhoneticJson: String = "",
    val etymologyPronunciationJson: String = "",
    val plural: String = "",
    val thirdPersonSingular: String = "",
    val presentParticiple: String = "",
    val pastTense: String = "",
    val categoryName: String = "",
    val remark: String = "",
    val repeatVoice: String = ""
)
