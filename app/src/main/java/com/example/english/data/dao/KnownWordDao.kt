package com.example.english.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.english.data.entity.KnownWord

@Dao
interface KnownWordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: KnownWord)

    @Query("SELECT COUNT(*) FROM known_words")
    suspend fun count(): Int

    @Query("DELETE FROM known_words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM known_words ORDER BY id DESC")
    suspend fun getAll(): List<KnownWord>

    @Query("UPDATE known_words SET repeatVoice = :repeatVoice WHERE wordId = :wordId")
    suspend fun updateRepeatVoice(wordId: String, repeatVoice: String)

    @Query("""UPDATE known_words SET
        pronunciation = :pron, repeatVoice = :repeatVoice, phonetic = :phonetic,
        meaning = :meaning, etymologyJson = :etym, etymologyPhoneticJson = :etymPh,
        etymologyPronunciationJson = :etymPron, plural = :plural,
        thirdPersonSingular = :third, presentParticiple = :presp, pastTense = :past,
        categoryName = :cat, remark = :remark
        WHERE id = :id""")
    suspend fun updateFromServer(
        id: Long, pron: String, repeatVoice: String, phonetic: String, meaning: String,
        etym: String, etymPh: String, etymPron: String, plural: String,
        third: String, presp: String, past: String, cat: String, remark: String
    )
}
