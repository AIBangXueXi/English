package com.example.english.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.english.MainActivity
import com.example.english.R
import com.example.english.data.DailyTaskStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * “今日生词”桌面 Widget。
 *
 * 交互（模拟上滑解锁）：
 * - Android 桌面 Widget 无法监听滑动手势（竖滑被启动器消费），因此用点击计数代替：
 *   每点击一次计一次“上滑”，累计 5 次后解锁展示内容。
 * - 未解锁时：显示进度 x/5；第 5 次点击时根据当日数据展示：
 *   a) 今天找到了不认识的单词 → 展示单词列表；
 *   b) 今天还没有 → 提示去 App 里背单词找生词。
 * - 已解锁后再点击 → 打开 App（方便按提示去背单词）。
 * - 计数与解锁状态按自然日重置。
 */
class UnknownWordWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_COUNT_SWIPE = "com.example.english.widget.ACTION_COUNT_SWIPE"
        const val REQUIRED_SWIPES = 5

        private const val PREFS_NAME = "unknown_word_widget"
        private const val KEY_DATE = "swipe_date"
        private const val KEY_COUNT = "swipe_count"
        private const val KEY_REVEALED = "revealed"

        /** 今日已展示的生词最多完整列出 8 个，其余用“等 N 个”省略 */
        private const val MAX_WORDS_SHOWN = 8

        private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        /**
         * 数据变化时（如刚标记了新的不认识单词）刷新所有实例，
         * 已解锁的 Widget 会即时显示最新生词列表。
         */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UnknownWordWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                updateAll(context, manager, ids)
            }
        }

        private fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context))
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val today = dayFormat.format(Date())
            val isToday = prefs.getString(KEY_DATE, "") == today
            val count = if (isToday) prefs.getInt(KEY_COUNT, 0) else 0
            val revealed = isToday && prefs.getBoolean(KEY_REVEALED, false)

            val views = RemoteViews(context.packageName, R.layout.widget_unknown_word)

            // 整个 Widget 是唯一点击目标：未解锁 → 计数；已解锁 → 打开 App
            val clickIntent = Intent(context, UnknownWordWidgetProvider::class.java).apply {
                action = ACTION_COUNT_SWIPE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            if (!revealed) {
                views.setTextViewText(
                    R.id.widget_message,
                    "上滑（点击）$REQUIRED_SWIPES 次查看今天找到的生词"
                )
                views.setTextViewText(R.id.widget_progress, "$count / $REQUIRED_SWIPES")
                views.setTextViewText(R.id.widget_hint, "点击 +1")
            } else {
                val words = DailyTaskStore(context).getToday().unknownWords.distinct()
                if (words.isNotEmpty()) {
                    val shown = words.take(MAX_WORDS_SHOWN).joinToString("、")
                    val suffix = if (words.size > MAX_WORDS_SHOWN) " 等 ${words.size} 个" else ""
                    views.setTextViewText(
                        R.id.widget_message,
                        "今天已找到 ${words.size} 个不认识的单词：$shown$suffix"
                    )
                    views.setTextViewText(R.id.widget_progress, "✅ 已解锁")
                    views.setTextViewText(R.id.widget_hint, "点击打开应用")
                } else {
                    views.setTextViewText(
                        R.id.widget_message,
                        "今天还没有找到不认识的单词，快去应用里背单词，发现并收集生词吧！"
                    )
                    views.setTextViewText(R.id.widget_progress, "⚠️ 空空如也")
                    views.setTextViewText(R.id.widget_hint, "点击打开应用去背单词")
                }
            }
            return views
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_COUNT_SWIPE) {
            handleSwipeTap(context)
        }
    }

    private fun handleSwipeTap(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = dayFormat.format(Date())
        val isToday = prefs.getString(KEY_DATE, "") == today
        val count = if (isToday) prefs.getInt(KEY_COUNT, 0) else 0
        val revealed = isToday && prefs.getBoolean(KEY_REVEALED, false)

        if (revealed) {
            // 已解锁：点击打开 App 去背单词
            val launch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launch)
            return
        }

        val newCount = count + 1
        prefs.edit()
            .putString(KEY_DATE, today)
            .putInt(KEY_COUNT, newCount)
            .putBoolean(KEY_REVEALED, newCount >= REQUIRED_SWIPES)
            .apply()

        refreshAll(context)
    }
}
