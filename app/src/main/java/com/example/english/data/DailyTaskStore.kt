package com.example.english.data

import android.content.Context
import com.google.gson.Gson
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

/** 磨耳训练每日目标：15 分钟 */
const val MO_ER_TARGET_SECONDS = 15 * 60

/**
 * 某一天的学习任务完成情况。
 * 任务：背单词发现 [unknownLimit] 个不认识 / 磨耳 15 分钟 / 训练专区三个各执行一遍。
 */
data class DailyTask(
    val unknownFound: Int = 0,
    val moErSeconds: Int = 0,
    val dictationDone: Boolean = false,
    val pronunciationDone: Boolean = false,
    val meaningDone: Boolean = false,
    val matchingDone: Boolean = false,
    /** 今天找到的不认识单词（供桌面 Widget 展示） */
    val unknownWords: List<String> = emptyList()
) {
    fun completedCount(unknownLimit: Int): Int {
        var c = 0
        if (unknownFound >= unknownLimit) c++
        if (moErSeconds >= MO_ER_TARGET_SECONDS) c++
        if (dictationDone) c++
        if (pronunciationDone) c++
        if (meaningDone) c++
        if (matchingDone) c++
        return c
    }

    val totalTasks: Int get() = 6
}

/**
 * 每日任务持久化（按自然日，key = yyyy-MM-dd）。
 * 磨耳时长、训练完成标记在此记录；"发现不认识数"由 [WordRepository] 记录时同步写入。
 */
class DailyTaskStore(context: Context) {
    private val prefs = context.getSharedPreferences("daily_tasks", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        pruneOldRecords()
    }

    fun todayKey(): String = dayFormat.format(Date())

    fun getToday(): DailyTask = read(todayKey())

    fun updateToday(transform: (DailyTask) -> DailyTask) {
        val key = todayKey()
        val updated = transform(read(key))
        prefs.edit().putString(key, gson.toJson(updated)).apply()
    }

    fun recordUnknownFound(word: String) = updateToday {
        it.copy(unknownFound = it.unknownFound + 1, unknownWords = it.unknownWords + word)
    }

    fun recordMoErSecond() = updateToday { it.copy(moErSeconds = it.moErSeconds + 1) }

    fun markDictationDone() = updateToday { it.copy(dictationDone = true) }

    fun markPronunciationDone() = updateToday { it.copy(pronunciationDone = true) }

    fun markMeaningDone() = updateToday { it.copy(meaningDone = true) }

    fun markMatchingDone() = updateToday { it.copy(matchingDone = true) }

    /** 最近 [days] 天（含今天）的完成情况，按时间从近到远。 */
    fun getHistory(days: Int = 7): List<Pair<String, DailyTask>> {
        val cal = Calendar.getInstance()
        val result = mutableListOf<Pair<String, DailyTask>>()
        repeat(days) { i ->
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val key = dayFormat.format(cal.time)
            result.add(key to read(key))
        }
        return result
    }

    /**
     * 第 [weekOffset] 周（0=本周，1=上周…）的每日完成情况，按周一到周日返回 7 天。
     */
    fun getWeekDays(weekOffset: Int): List<Pair<String, DailyTask>> {
        val today = Calendar.getInstance()
        val dow = today.get(Calendar.DAY_OF_WEEK) // SUNDAY=1 ... SATURDAY=7
        val daysSinceMonday = if (dow == Calendar.SUNDAY) 6 else dow - 2
        val monday = Calendar.getInstance().apply {
            time = Date()
            add(Calendar.DAY_OF_YEAR, -daysSinceMonday - weekOffset * 7)
        }
        return List(7) { d ->
            val day = Calendar.getInstance().apply {
                time = monday.time
                add(Calendar.DAY_OF_YEAR, d)
            }
            val key = dayFormat.format(day.time)
            key to read(key)
        }
    }

    /**
     * 清理 [KEEP_DAYS] 天前的历史记录，避免 SharedPreferences 长期累积。
     * 每天首次构造时执行一次；日期 key 为 yyyy-MM-dd，可直接按字符串大小比较。
     */
    private fun pruneOldRecords() {
        if (prefs.getString(KEY_LAST_PRUNE, "") == todayKey()) return
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -KEEP_DAYS)
        val threshold = dayFormat.format(cal.time)
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            // 只匹配 yyyy-MM-dd 形式的日期 key，跳过 KEY_LAST_PRUNE 等内部标记
            if (key.length == 10 && key[4] == '-' && key < threshold) {
                editor.remove(key)
            }
        }
        editor.putString(KEY_LAST_PRUNE, todayKey()).apply()
    }

    private companion object {
        /** 历史记录保留天数（需覆盖「本周 + 前四周」，共最多 35 天） */
        const val KEEP_DAYS = 42
        const val KEY_LAST_PRUNE = "_last_prune"
    }

    private fun read(key: String): DailyTask {
        val json = prefs.getString(key, null) ?: return DailyTask()
        return try {
            // Gson 通过反射构造对象时不走默认参数，旧版本 JSON 缺 unknownWords 字段会得到 null，这里兜底
            val task = gson.fromJson(json, DailyTask::class.java) ?: return DailyTask()
            @Suppress("SENSELESS_COMPARISON")
            if (task.unknownWords == null) task.copy(unknownWords = emptyList()) else task
        } catch (_: Exception) {
            DailyTask()
        }
    }
}
