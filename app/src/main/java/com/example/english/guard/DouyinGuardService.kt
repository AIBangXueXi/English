package com.example.english.guard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 刷视频管控：在抖音里统计刷过的视频数，达到阈值就强制回桌面并弹出复习锁屏。
 *
 * 计数有三层，互相兜底（抖音 Feed 是自定义竖向 ViewPager，事件不稳定）：
 * 1. TYPE_VIEW_SCROLLED —— 上滑切视频的主信号
 * 2. 视频文案变化 —— 部分版本不抛滚动事件，改为检测屏幕文本签名变化
 * 3. 时间兜底 —— 长时间检测不到切换，按平均单个视频时长补记，保证一定会触发
 *
 * 锁定期间复习没做完就跑回抖音：[onAccessibilityEvent] 检测到抖音回到前台会立刻重新锁屏。
 */
class DouyinGuardService : AccessibilityService() {

    companion object {
        /** 抖音 / 抖音极速版 */
        private val TARGET_PACKAGES = setOf(
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite"
        )

        /** 两次计数的最小间隔，防止一次滑动连续触发多次 */
        private const val MIN_SWIPE_INTERVAL_MS = 1_500L
        /** 时间兜底：超过这个时长没检测到切换就补记一个视频（按平均视频时长估算） */
        private const val FALLBACK_TICK_MS = 30_000L
        /** 文本签名检测的最小间隔，避免高频遍历窗口 */
        private const val SIGNATURE_CHECK_INTERVAL_MS = 800L

        /** 系统无障碍服务是否已授予本服务 */
        fun isServiceEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${DouyinGuardService::class.java.name}"
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

    private var lastCountAt = 0L
    private var inTarget = false
    private var lastSignature = ""
    private var lastSignatureCheckAt = 0L

    private val fallbackTicker = object : Runnable {
        override fun run() {
            if (::store.isInitialized && store.enabled && inTarget) {
                val since = System.currentTimeMillis() - lastCountAt
                if (since >= FALLBACK_TICK_MS) countVideoOnce()
            }
            handler.postDelayed(this, FALLBACK_TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = GuardStateStore(this)
        handler.postDelayed(fallbackTicker, FALLBACK_TICK_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(fallbackTicker)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::store.isInitialized || !store.enabled) return
        val pkg = event.packageName?.toString() ?: return

        // 前台应用切换：进入 / 离开抖音
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val nowInTarget = pkg in TARGET_PACKAGES
            if (nowInTarget != inTarget) {
                inTarget = nowInTarget
                if (nowInTarget) {
                    lastCountAt = System.currentTimeMillis()
                    // 复习还没做完就跑回抖音 → 立刻重新锁定
                    if (store.isLocked()) triggerLock()
                }
            }
        }

        if (pkg !in TARGET_PACKAGES) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> countVideoOnce()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkTextChanged()
        }
    }

    /** 兜底：检测视频文案（标题/话题）变化来判定切换了视频 */
    private fun checkTextChanged() {
        val now = System.currentTimeMillis()
        if (now - lastSignatureCheckAt < SIGNATURE_CHECK_INTERVAL_MS) return
        lastSignatureCheckAt = now

        val root = rootInActiveWindow ?: return
        val signature = collectTextSignature(root)
        if (signature.isBlank()) return
        if (lastSignature.isEmpty()) {
            lastSignature = signature
            return
        }
        if (signature != lastSignature) {
            lastSignature = signature
            countVideoOnce()
        }
    }

    private fun collectTextSignature(root: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12 || texts.size >= 12) return
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.length >= 6) texts.add(text)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return texts.take(6).joinToString("|")
    }

    private fun countVideoOnce() {
        val now = System.currentTimeMillis()
        if (now - lastCountAt < MIN_SWIPE_INTERVAL_MS) return
        lastCountAt = now
        if (store.countVideo() >= store.threshold) triggerLock()
    }

    private fun triggerLock() {
        store.lock()
        // 先把抖音压到后台，再把复习锁屏拉到前台（避免被全屏视频遮挡）
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

    override fun onInterrupt() {}
}
