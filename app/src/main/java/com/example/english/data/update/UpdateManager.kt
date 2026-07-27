package com.example.english.data.update

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import kotlin.system.exitProcess
import com.example.english.BuildConfig
import com.example.english.data.api.WordApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String
)

class UpdateManager(private val context: Context) {
    private val appContext = context.applicationContext

    private val api: WordApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://bxxapi.aibangxuexi.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WordApiService::class.java)
    }

    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val response = api.getVersion()
            if (response.code != 0 || response.data.isBlank()) return@withContext null

            val latestVersion = extractVersion(response.data)
            val localVersion = BuildConfig.VERSION_NAME
            if (compareVersion(latestVersion, localVersion) > 0) {
                UpdateInfo(
                    hasUpdate = true,
                    latestVersion = latestVersion,
                    downloadUrl = "https://www.aibangxuexi.com/apk/english/${response.data}"
                )
            } else {
                UpdateInfo(hasUpdate = false, latestVersion = localVersion, downloadUrl = "")
            }
        } catch (e: Exception) {
            null
        }
    }

    fun downloadAndInstall(url: String, filename: String) {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.getFilesDir()
        val file = File(dir, filename)

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        Thread {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@Thread

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }

                installApk(file)
                // Give the system a moment to bring up the installer, then exit
                // this app so the package can be replaced cleanly.
                try { Thread.sleep(500) } catch (_: Exception) { }
                exitProcess(0)
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(intent)
    }

    private fun extractVersion(filename: String): String {
        // "app.english.v1.0.0.apk" -> "1.0.0"
        val regex = Regex("""v(\d+\.\d+\.\d+)""")
        return regex.find(filename)?.groupValues?.get(1) ?: "0.0.0"
    }

    private fun compareVersion(a: String, b: String): Int {
        val partsA = a.split(".").map { it.toIntOrNull() ?: 0 }
        val partsB = b.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}
