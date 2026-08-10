package com.example.english.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.english.data.entity.UnknownWord

@Dao
interface UnknownWordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: UnknownWord)

    @Query("SELECT * FROM unknown_words WHERE nextReviewTime <= :now ORDER BY nextReviewTime ASC, id ASC")
    suspend fun getDueForReview(now: Long): List<UnknownWord>

    @Query("SELECT * FROM unknown_words WHERE nextReviewTime <= :now AND id != :excludeId ORDER BY nextReviewTime ASC, id ASC LIMIT 1")
    suspend fun getFirstDue(now: Long, excludeId: Long): UnknownWord?

    @Query("SELECT COUNT(*) FROM unknown_words")
    suspend fun count(): Int

    @Query("UPDATE unknown_words SET stage = :stage, nextReviewTime = :nextReviewTime WHERE id = :id")
    suspend fun updateStage(id: Long, stage: Int, nextReviewTime: Long)

    @Query("SELECT * FROM unknown_words ORDER BY id DESC")
    suspend fun getAll(): List<UnknownWord>

    @Query("DELETE FROM unknown_words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE unknown_words SET repeatVoice = :repeatVoice WHERE wordId = :wordId")
    suspend fun updateRepeatVoice(wordId: String, repeatVoice: String)
}
