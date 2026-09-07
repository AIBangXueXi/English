package com.example.english.guard

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 刷视频管控的状态存储（按自然日）。
 *
 * - [videoCount]：今天在抖音里刷过的视频数（跨天自动归零）
 * - [isLocked]：是否已触发锁屏、等待复习完成。复习没做完就回抖音会立刻重新锁定。
 */
class GuardStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val PREFS_NAME = "douyin_guard"

        /** 默认：刷 10 个视频触发一次复习 */
        const val DEFAULT_VIDEO_THRESHOLD = 10
        /** 每次锁屏要复习的单词数 */
        const val REVIEW_WORD_COUNT = 5

        const val MIN_THRESHOLD = 3
        const val MAX_THRESHOLD = 30

        private const val KEY_DATE = "date"
        private const val KEY_COUNT = "count"
        private const val KEY_LOCKED = "locked"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_ENABLED = "enabled"
    }

    /** 管控总开关 */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 触发复习的视频个数阈值 */
    var threshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, DEFAULT_VIDEO_THRESHOLD)
        set(value) = prefs.edit()
            .putInt(KEY_THRESHOLD, value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD))
            .apply()

    private fun today(): String = dayFormat.format(Date())

    /** 跨天重置计数与锁定状态 */
    private fun ensureToday() {
        if (prefs.getString(KEY_DATE, "") != today()) {
            prefs.edit()
                .putString(KEY_DATE, today())
                .putInt(KEY_COUNT, 0)
                .putBoolean(KEY_LOCKED, false)
                .apply()
        }
    }

    fun videoCount(): Int {
        ensureToday()
        return prefs.getInt(KEY_COUNT, 0)
    }

    fun isLocked(): Boolean {
        ensureToday()
        return prefs.getBoolean(KEY_LOCKED, false)
    }

    /** 记一次视频切换，返回今日累计数 */
    fun countVideo(): Int {
        ensureToday()
        val next = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit().putInt(KEY_COUNT, next).apply()
        return next
    }

    fun lock() {
        ensureToday()
        prefs.edit().putBoolean(KEY_LOCKED, true).apply()
    }

    /** 复习完成：解锁并把计数归零 */
    fun unlockAndReset() {
        prefs.edit()
            .putString(KEY_DATE, today())
            .putInt(KEY_COUNT, 0)
            .putBoolean(KEY_LOCKED, false)
            .apply()
    }
}
