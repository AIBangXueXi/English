package com.example.english.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.english.data.dao.KnownWordDao
import com.example.english.data.dao.UnknownWordDao
import com.example.english.data.entity.KnownWord
import com.example.english.data.entity.UnknownWord

@Database(entities = [KnownWord::class, UnknownWord::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knownWordDao(): KnownWordDao
    abstract fun unknownWordDao(): UnknownWordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "english_words.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
