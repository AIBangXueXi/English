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

    @Query("SELECT * FROM unknown_words ORDER BY correctCount ASC, id ASC")
    suspend fun getAll(): List<UnknownWord>

    @Query("SELECT * FROM unknown_words ORDER BY correctCount ASC, id ASC LIMIT 1")
    suspend fun getFirst(): UnknownWord?

    @Query("SELECT COUNT(*) FROM unknown_words")
    suspend fun count(): Int

    @Query("UPDATE unknown_words SET correctCount = correctCount + 1 WHERE id = :id")
    suspend fun incrementCorrectCount(id: Long)

    @Query("DELETE FROM unknown_words WHERE id = :id")
    suspend fun deleteById(id: Long)
}
