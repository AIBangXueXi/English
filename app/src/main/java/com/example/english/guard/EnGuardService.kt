package com.example.english.guard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * 娱乐 App 管控服务（无障碍）。
 *
 * 监听前台窗口包名，判断是否在 [GuardStateStore.packages] 列表中：
 * - 进入 → [GuardStateStore.enterSession] 开始计时
 * - 离开 → [GuardStateStore.exitSession] 把本次 session 秒数累加进今日
 *
 * 每秒检查累计用时，达到阈值（thresholdMinutes * 60 秒）则触发锁屏；复习没做完
 * 就跑回被管控应用，会再次触发锁屏。
 *
 * 之所以用包名切换作为唯一信号：管控的对象是"打开就计时间"的娱乐 App（抖音/快手
 * 等），不依赖滑动/点击事件，兼容各类 App。
 */
class EnGuardService : AccessibilityService() {

    companion object {
        /** 每秒检查一次累计用时（毫秒） */
        private const val TICK_MS = 1_000L

        /** 系统无障碍服务是否已授予本服务 */
        fun isServiceEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${EnGuardService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun openAccessibilitySettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private lateinit var store: GuardStateStore
    private val handler = Handler(Looper.getMainLooper())
    private var inTarget = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (::store.isInitialized && store.enabled && inTarget) {
                val threshold = store.thresholdMinutes * 60
                val used = store.currentUsageSeconds()
                if (used >= threshold || store.isLocked()) {
                    // 达阈值或用户跑回（已锁定但还在前台）→ 触发锁屏
                    triggerLock()
                }
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = GuardStateStore(this)
        handler.postDelayed(tickRunnable, TICK_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::store.isInitialized || !store.enabled) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        val targets = store.packages
        val nowInTarget = pkg in targets

        if (nowInTarget == inTarget) return
        inTarget = nowInTarget

        if (nowInTarget) {
            store.enterSession()
            // 复习没做完就跑回管控 App → 立刻重新锁屏
            if (store.isLocked()) triggerLock()
        } else {
            store.exitSession()
        }
    }

    private fun triggerLock() {
        store.lock()
        // 先把管控 App 压到后台，再把复习锁屏拉到前台（避免被全屏视频遮挡）
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            val intent = Intent(this, GuardLockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
        }, 400)
    }
}