package com.example.english.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 训练进度持久化（按训练模式记录当前训练到的单词下标）。
 *
 * 三个训练（默写 / 发音 / 意思）各自独立记录进度，退出后再次进入时
 * 从中断位置继续，而不是从头开始；本轮训练全部完成后清除进度。
 *
 * 进度属于"当天"：跨天后所有模式的进度自动清零，保证与每日任务状态一致。
 */
class TrainingProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        ensureToday()
    }

    /**
     * 跨天检查：若记录的日期不是今天，清空全部训练进度并写入新日期。
     * 构造与每次读写前都调用，保证 App 长期后台运行跨天后也能重置。
     */
    private fun ensureToday() {
        val today = dayFormat.format(Date())
        if (prefs.getString(KEY_DATE, "") != today) {
            val editor = prefs.edit()
            MODE_KEYS.forEach { editor.remove(it) }
            editor.putString(KEY_DATE, today).apply()
        }
    }

    /** 记录某个训练模式下当前进行到的单词下标（0 开始）。 */
    fun saveIndex(modeKey: String, index: Int, wordCount: Int) {
        ensureToday()
        if (wordCount <= 0 || index < 0 || index >= wordCount) {
            // 词表为空 / 已完成 / 无效位置：清除记录
            clear(modeKey)
            return
        }
        prefs.edit().putInt(modeKey, index).apply()
    }

    /** 读取某个训练模式下保存的下标；没有记录时返回 0。 */
    fun getIndex(modeKey: String): Int {
        ensureToday()
        return prefs.getInt(modeKey, 0)
    }

    /** 是否记录过该训练模式的进度（用于区分"未开始"和"在第 1 个词"）。 */
    fun isStarted(modeKey: String): Boolean {
        ensureToday()
        return prefs.contains(modeKey)
    }

    /** 清除某个训练模式的进度（一轮训练完成时调用）。 */
    fun clear(modeKey: String) {
        prefs.edit().remove(modeKey).apply()
    }

    companion object {
        private const val PREFS_NAME = "training_progress"
        private const val KEY_DATE = "_date"
        private val MODE_KEYS = listOf("Meaning", "Pronunciation", "Dictation", "Matching")
    }
}
