package com.example.english

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.english.data.WordRepository
import com.example.english.data.WordViewModel
import com.example.english.data.update.UpdateInfo
import com.example.english.data.update.UpdateManager
import com.example.english.speech.SpeechService
import com.example.english.ui.screen.HomeScreen
import com.example.english.ui.screen.LibraryManagementScreen
import com.example.english.ui.screen.WordScreen
import com.example.english.ui.theme.EnglishTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var speechService: SpeechService
    private lateinit var updateManager: UpdateManager

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "需要录音权限才能使用语音识别功能", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechService = SpeechService(this)
        updateManager = UpdateManager(this)
        enableEdgeToEdge()

        setContent {
            EnglishTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                var isCheckingUpdate by remember { mutableStateOf(false) }
                var isSyncing by remember { mutableStateOf(false) }
                var isMoErPlaying by remember { mutableStateOf(false) }
                var moErCurrentWord by remember { mutableStateOf("") }
                var knownCount by remember { mutableStateOf(0) }
                var unknownCount by remember { mutableStateOf(0) }
                val scope = rememberCoroutineScope()
                val syncViewModel: WordViewModel = viewModel()

                LaunchedEffect(Unit) {
                    val repo = WordRepository(this@MainActivity)
                    knownCount = repo.getKnownCount()
                    unknownCount = repo.getUnknownCount()
                }

                val onSync: () -> Unit = {
                    scope.launch {
                        isSyncing = true
                        syncViewModel.syncFromServer { count ->
                            isSyncing = false
                            Toast.makeText(
                                this@MainActivity,
                                "同步完成，更新了 $count 个单词",
                                Toast.LENGTH_SHORT
                            ).show()
                            scope.launch {
                                knownCount = WordRepository(this@MainActivity).getKnownCount()
                                unknownCount = WordRepository(this@MainActivity).getUnknownCount()
                            }
                        }
                    }
                }

                val onMoEr: () -> Unit = {
                    scope.launch {
                        if (isMoErPlaying) {
                            isMoErPlaying = false
                            return@launch
                        }
                        isMoErPlaying = true
                        val urls = syncViewModel.getMoErWords()
                        if (urls.isEmpty()) {
                            isMoErPlaying = false
                            Toast.makeText(
                                this@MainActivity,
                                "没有可播放的磨耳音频，请先同步数据",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }
                        withContext(Dispatchers.IO) {
                            var i = 0
                            while (isMoErPlaying && i < urls.size) {
                                moErCurrentWord = "播放中 (${i + 1}/${urls.size})"
                                try {
                                    val mp = MediaPlayer().apply {
                                        setAudioAttributes(
                                            AudioAttributes.Builder()
                                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                .build()
                                        )
                                        setDataSource(urls[i])
                                        setOnCompletionListener { it.release() }
                                        setOnErrorListener { m, _, _ -> m.release(); true }
                                        prepare()
                                        start()
                                    }
                                    while (isMoErPlaying && mp.isPlaying) {
                                        delay(500)
                                    }
                                    try { mp.release() } catch (_: Exception) {}
                                } catch (_: Exception) { }
                                i++
                                // Gap between words
                                if (isMoErPlaying && i < urls.size) delay(2000)
                            }
                        }
                        isMoErPlaying = false
                        moErCurrentWord = ""
                        if (urls.isNotEmpty()) {
                            Toast.makeText(this@MainActivity, "磨耳播放完成", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val checkForUpdates: () -> Unit = {
                    scope.launch {
                        isCheckingUpdate = true
                        try {
                            val info = updateManager.checkUpdate()
                            isCheckingUpdate = false
                            when {
                                info == null ->
                                    Toast.makeText(
                                        this@MainActivity,
                                        "检查更新失败，请稍后重试",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                info.hasUpdate -> updateInfo = info
                                else ->
                                    Toast.makeText(
                                        this@MainActivity,
                                        "已是最新版本 v${BuildConfig.VERSION_NAME}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                            }
                        } catch (e: Exception) {
                            isCheckingUpdate = false
                            Toast.makeText(
                                this@MainActivity,
                                "检查更新失败，请稍后重试",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    updateManager.checkUpdate()?.let { info ->
                        if (info.hasUpdate) {
                            updateInfo = info
                        }
                    }
                }

                if (updateInfo != null) {
                    AlertDialog(
                        onDismissRequest = { updateInfo = null },
                        title = { Text("发现新版本") },
                        text = { Text("最新版本：${updateInfo!!.latestVersion}\n是否立即更新？") },
                        confirmButton = {
                            TextButton(onClick = {
                                val info = updateInfo!!
                                updateInfo = null
                                updateManager.downloadAndInstall(
                                    info.downloadUrl,
                                    "english_v${info.latestVersion}.apk"
                                )
                            }) {
                                Text("立即更新")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateInfo = null }) {
                                Text("稍后再说")
                            }
                        }
                    )
                }

                when (val screen = currentScreen) {
                    is Screen.Home -> HomeScreen(
                        onWordClick = {
                            ensureAudioPermission {
                                currentScreen = Screen.WordPage
                            }
                        },
                        onDictionaryClick = {
                            currentScreen = Screen.LibraryManagement
                        },
                        onMoErClick = onMoEr,
                        onCheckUpdate = checkForUpdates,
                        isCheckingUpdate = isCheckingUpdate,
                        knownCount = knownCount,
                        unknownCount = unknownCount
                    )
                    is Screen.WordPage -> {
                        val wordViewModel: WordViewModel = viewModel()
                        WordScreen(
                            viewModel = wordViewModel,
                            speechService = speechService,
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                    is Screen.LibraryManagement -> {
                        val libraryViewModel: WordViewModel = viewModel()
                        LibraryManagementScreen(
                            viewModel = libraryViewModel,
                            onBack = { currentScreen = Screen.Home },
                            onSync = onSync,
                            isSyncing = isSyncing
                        )
                    }
                }
            }
        }
    }

    private fun ensureAudioPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

sealed class Screen {
    data object Home : Screen()
    data object WordPage : Screen()
    data object LibraryManagement : Screen()
}
