package com.example.english.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * 离线音频缓存：把远程磨耳/单词读音缓存到手机持久存储，
 * 断网时也能用之前下载过的音频播放。
 *
 * 缓存目录使用 [Context.filesDir] 下的 audio_cache（内部存储，不需要权限）。
 * 文件名是 URL 的 SHA-256 前缀 + 原扩展名，写前先落到临时文件再 rename，
 * 避免并发或进程被杀导致半截文件。
 */
object AudioCache {

    private const val DIR_NAME = "audio_cache"

    fun cacheDir(context: Context): File = File(context.filesDir, DIR_NAME)

    /** 根据 URL 计算稳定文件名（不含目录）。 */
    fun fileNameForUrl(url: String): String {
        val shortHash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        val ext = url.substringBefore('?').substringAfterLast('.', "").let {
            if (it.length in 1..5 && it.all(Char::isLetterOrDigit)) ".$it" else ".wav"
        }
        return "$shortHash$ext"
    }

    /** 命中缓存的本地文件，否则返回 null。 */
    fun get(context: Context, url: String): File? {
        val dir = cacheDir(context)
        if (!dir.isDirectory) return null
        val file = File(dir, fileNameForUrl(url))
        return if (file.exists() && file.length() >= 44L) file else null
    }

    /**
     * 把 [source]（已完整下载的本地临时文件）写入缓存。
     * 返回缓存后的文件；失败返回 null，不影响主流程。
     */
    fun put(context: Context, url: String, source: File): File? = try {
        val dir = cacheDir(context)
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, fileNameForUrl(url))
        // 先写临时文件再原子替换，避免半截文件被当成有效缓存
        val tmp = File(dir, "${target.name}.tmp")
        source.copyTo(tmp, overwrite = true)
        if (tmp.renameTo(target)) target else null
    } catch (_: Exception) {
        null
    }
}
