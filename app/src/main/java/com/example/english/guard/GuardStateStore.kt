package com.example.english.guard

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 娱乐 App 管控的状态存储（按自然日）。
 *
 * - [packages]：被管控的 app 包名集合（默认抖音 + 抖音极速版，用户可在设置页增删）
 * - [thresholdMinutes]：在管控 app 内累计使用多少分钟后触发复习锁屏
 * - 用时累计：进入管控 app 时记录起始时间戳，离开时把秒数累加进 [dailySeconds]，
 *   跨天自动清零；复习完成（[unlockAndReset]）也会清零。
 */
class GuardStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val PREFS_NAME = "guard_state"

        /** 默认阈值：累计 10 分钟触发一次复习 */
        const val DEFAULT_THRESHOLD_MINUTES = 10
        const val MIN_THRESHOLD_MINUTES = 5
        const val MAX_THRESHOLD_MINUTES = 60

        /** 默认管控的 app（抖音 + 抖音极速版） */
        const val DEFAULT_PACKAGES = "com.ss.android.ugc.aweme,com.ss.android.ugc.aweme.lite"

        /** 家长密码默认值：进入「娱乐管控」设置需输入密码 */
        const val DEFAULT_PASSWORD = "88888888"

        private const val KEY_PASSWORD = "password"
        private const val KEY_DATE = "date"
        private const val KEY_SECONDS = "seconds"
        private const val KEY_LOCKED = "locked"
        private const val KEY_THRESHOLD = "threshold_minutes"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PACKAGES = "packages"
    }

    /** 运行时：进入管控 app 的起始时间戳（毫秒）；0 表示当前不在管控 app 内 */
    @Volatile var sessionStartMs: Long = 0L

    /** 管控总开关 */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 阈值（分钟） */
    var thresholdMinutes: Int
        get() = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD_MINUTES)
        set(value) = prefs.edit()
            .putInt(KEY_THRESHOLD, value.coerceIn(MIN_THRESHOLD_MINUTES, MAX_THRESHOLD_MINUTES))
            .apply()

    /** 被管控的 app 包名集合 */
    var packages: Set<String>
        get() {
            val raw = prefs.getString(KEY_PACKAGES, DEFAULT_PACKAGES) ?: DEFAULT_PACKAGES
            return raw.split(',').filter { it.isNotBlank() }.toSet()
        }
        set(value) {
            prefs.edit().putString(KEY_PACKAGES, value.joinToString(",")).apply()
        }

    /** 家长密码（默认 [DEFAULT_PASSWORD]） */
    var password: String
        get() = prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
        set(value) {
            val v = value.trim()
            if (v.isBlank()) return
            prefs.edit().putString(KEY_PASSWORD, v).apply()
        }

    fun isPasswordCorrect(input: String): Boolean = input.trim() == password

    fun addPackage(pkg: String) {
        if (pkg.isBlank()) return
        packages = packages + pkg
    }

    fun removePackage(pkg: String) {
        packages = packages - pkg
    }

    private fun today(): String = dayFormat.format(Date())

    /** 跨天重置用时与锁定状态 */
    private fun ensureToday() {
        if (prefs.getString(KEY_DATE, "") != today()) {
            prefs.edit()
                .putString(KEY_DATE, today())
                .putInt(KEY_SECONDS, 0)
                .putBoolean(KEY_LOCKED, false)
                .apply()
        }
    }

    /** 今日累计使用秒数（不含当前 session 的实时累加） */
    fun dailySeconds(): Int {
        ensureToday()
        return prefs.getInt(KEY_SECONDS, 0)
    }

    /** 实时累计：今日已 + 当前正在使用的 session 时长 */
    fun currentUsageSeconds(): Int {
        val base = dailySeconds()
        val start = sessionStartMs
        return if (start > 0) base + ((System.currentTimeMillis() - start) / 1000L).toInt()
        else base
    }

    fun isLocked(): Boolean {
        ensureToday()
        return prefs.getBoolean(KEY_LOCKED, false)
    }

    /** 进入管控 app：开始计时（重复调用不会重置） */
    fun enterSession() {
        if (sessionStartMs == 0L) {
            sessionStartMs = System.currentTimeMillis()
        }
    }

    /** 离开管控 app：把这次 session 的时长累加到今日，再清零 sessionStartMs */
    fun exitSession() {
        val start = sessionStartMs
        if (start > 0) {
            val added = ((System.currentTimeMillis() - start) / 1000L).toInt()
            if (added > 0) {
                ensureToday()
                val total = prefs.getInt(KEY_SECONDS, 0) + added
                prefs.edit().putInt(KEY_SECONDS, total).apply()
            }
            sessionStartMs = 0L
        }
    }

    fun lock() {
        ensureToday()
        prefs.edit().putBoolean(KEY_LOCKED, true).apply()
    }

    /** 复习完成：解锁并清空今日用时，重置 session */
    fun unlockAndReset() {
        prefs.edit()
            .putString(KEY_DATE, today())
            .putInt(KEY_SECONDS, 0)
            .putBoolean(KEY_LOCKED, false)
            .apply()
        sessionStartMs = 0L
    }
}