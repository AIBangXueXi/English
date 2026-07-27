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
}
