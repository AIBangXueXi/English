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
    val meaningDone: Boolean = false
) {
    fun completedCount(unknownLimit: Int): Int {
        var c = 0
        if (unknownFound >= unknownLimit) c++
        if (moErSeconds >= MO_ER_TARGET_SECONDS) c++
        if (dictationDone) c++
        if (pronunciationDone) c++
        if (meaningDone) c++
        return c
    }

    val totalTasks: Int get() = 5
}

/**
 * 每日任务持久化（按自然日，key = yyyy-MM-dd）。
 * 磨耳时长、训练完成标记在此记录；"发现不认识数"由 [WordRepository] 记录时同步写入。
 */
class DailyTaskStore(context: Context) {
    private val prefs = context.getSharedPreferences("daily_tasks", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayKey(): String = dayFormat.format(Date())

    fun getToday(): DailyTask = read(todayKey())

    fun updateToday(transform: (DailyTask) -> DailyTask) {
        val key = todayKey()
        val updated = transform(read(key))
        prefs.edit().putString(key, gson.toJson(updated)).apply()
    }

    fun recordUnknownFound() = updateToday { it.copy(unknownFound = it.unknownFound + 1) }

    fun recordMoErSecond() = updateToday { it.copy(moErSeconds = it.moErSeconds + 1) }

    fun markDictationDone() = updateToday { it.copy(dictationDone = true) }

    fun markPronunciationDone() = updateToday { it.copy(pronunciationDone = true) }

    fun markMeaningDone() = updateToday { it.copy(meaningDone = true) }

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

    private fun read(key: String): DailyTask {
        val json = prefs.getString(key, null) ?: return DailyTask()
        return try {
            gson.fromJson(json, DailyTask::class.java) ?: DailyTask()
        } catch (_: Exception) {
            DailyTask()
        }
    }
}
