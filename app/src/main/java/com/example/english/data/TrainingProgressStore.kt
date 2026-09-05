package com.example.english.data

import android.content.Context

/**
 * 训练进度持久化（按训练模式记录当前训练到的单词下标）。
 *
 * 三个训练（默写 / 发音 / 意思）各自独立记录进度，退出后再次进入时
 * 从中断位置继续，而不是从头开始；本轮训练全部完成后清除进度。
 */
class TrainingProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("training_progress", Context.MODE_PRIVATE)

    /** 记录某个训练模式下当前进行到的单词下标（0 开始）。 */
    fun saveIndex(modeKey: String, index: Int, wordCount: Int) {
        if (wordCount <= 0 || index < 0 || index >= wordCount) {
            // 词表为空 / 已完成 / 无效位置：清除记录
            clear(modeKey)
            return
        }
        prefs.edit().putInt(modeKey, index).apply()
    }

    /** 读取某个训练模式下保存的下标；没有记录时返回 0。 */
    fun getIndex(modeKey: String): Int = prefs.getInt(modeKey, 0)

    /** 是否记录过该训练模式的进度（用于区分“未开始”和“在第 1 个词”）。 */
    fun isStarted(modeKey: String): Boolean = prefs.contains(modeKey)

    /** 清除某个训练模式的进度（一轮训练完成时调用）。 */
    fun clear(modeKey: String) {
        prefs.edit().remove(modeKey).apply()
    }
}
