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

        /** 模拟"在管控 App 中达到阈值"时触发的跳转。 */
        private const val TRIGGER_DELAY_MS = 400L

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

        /**
         * 模拟一次完整的「管控触发」链路，从设置页按钮调用，用于验证完整流程：
         * 1. 设置锁定状态（store.lock）—— 复习没做完回到被管控 App 时会立即再次触发
         * 2. 退到桌面（等价于 service 内的 performGlobalAction(GLOBAL_ACTION_HOME)，
         *    避免被全屏视频遮挡复习锁屏）
         * 3. 400ms 后启动 GuardLockActivity，带 NEW_TASK + CLEAR_TOP + SINGLE_TOP
         *
         * 和 [triggerLock] 行为一致，区别仅在退桌面这一步用普通 Intent 走系统 launcher
         * （外部 Context 拿不到 AccessibilityService 实例）。
         */
        fun simulateTrigger(context: Context) {
            GuardStateStore(context).lock()
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(home)
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(context, GuardLockActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                context.startActivity(intent)
            }, TRIGGER_DELAY_MS)
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