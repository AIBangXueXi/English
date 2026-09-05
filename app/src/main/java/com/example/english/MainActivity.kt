package com.example.english

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.english.data.DailyTaskStore
import com.example.english.data.WordRepository
import com.example.english.data.WordViewModel
import com.example.english.data.update.UpdateInfo
import com.example.english.data.update.UpdateManager
import com.example.english.speech.SpeechService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.english.ui.screen.HomeScreen
import com.example.english.ui.screen.HomeTabScreen
import com.example.english.ui.screen.LibraryManagementScreen
import com.example.english.ui.screen.LibrarySection
import com.example.english.ui.screen.MoErScreen
import com.example.english.ui.screen.MoErWord
import com.example.english.ui.screen.SettingsScreen
import com.example.english.ui.screen.TrainingMode
import com.example.english.ui.screen.TrainingScreen
import com.example.english.ui.screen.Word
import com.example.english.ui.screen.WordScreen
import com.example.english.ui.theme.EnglishTheme
import kotlinx.coroutines.launch

/** 底部 Tab：首页 / 学习 / 设置 */
enum class MainTab(val title: String, val icon: ImageVector) {
    Home("首页", Icons.Rounded.Home),
    Study("学习", Icons.Rounded.School),
    Settings("设置", Icons.Rounded.Settings)
}

sealed class Screen {
    data class Main(val tab: MainTab) : Screen()
    data object WordPage : Screen()
    data class LibraryManagement(val expandedSection: LibrarySection? = null) : Screen()
    data object MoErPage : Screen()
    data object DictationPage : Screen()
    data object PronunciationPage : Screen()
    data object MeaningPage : Screen()
}

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
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Main(MainTab.Home)) }
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                var isCheckingUpdate by remember { mutableStateOf(false) }
                var isSyncing by remember { mutableStateOf(false) }
                var knownCount by remember { mutableStateOf(0) }
                var unknownCount by remember { mutableStateOf(0) }
                val scope = rememberCoroutineScope()
                val syncViewModel: WordViewModel = viewModel()
                val taskStore = remember { DailyTaskStore(this@MainActivity) }

                val backToStudy: () -> Unit = { currentScreen = Screen.Main(MainTab.Study) }

                LaunchedEffect(currentScreen) {
                    if (currentScreen is Screen.Main) {
                        val repo = WordRepository(this@MainActivity)
                        knownCount = repo.getKnownCount()
                        unknownCount = repo.getUnknownCount()
                    }
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
                    is Screen.Main -> {
                        // 底部三 Tab 容器
                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    MainTab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = tab == screen.tab,
                                            onClick = { currentScreen = Screen.Main(tab) },
                                            icon = {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.title
                                                )
                                            },
                                            label = { Text(tab.title) }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                when (screen.tab) {
                                    MainTab.Home -> HomeTabScreen(
                                        onOpenStudy = {
                                            currentScreen = Screen.Main(MainTab.Study)
                                        },
                                        onWordTaskClick = {
                                            ensureAudioPermission {
                                                currentScreen = Screen.WordPage
                                            }
                                        },
                                        onKnownWordsClick = {
                                            currentScreen = Screen.LibraryManagement(LibrarySection.KNOWN)
                                        },
                                        onUnknownWordsClick = {
                                            currentScreen = Screen.LibraryManagement(LibrarySection.UNKNOWN)
                                        },
                                        onMoErTaskClick = {
                                            currentScreen = Screen.MoErPage
                                        },
                                        onDictationTaskClick = {
                                            currentScreen = Screen.DictationPage
                                        },
                                        onPronunciationTaskClick = {
                                            ensureAudioPermission {
                                                currentScreen = Screen.PronunciationPage
                                            }
                                        },
                                        onMeaningTaskClick = {
                                            ensureAudioPermission {
                                                currentScreen = Screen.MeaningPage
                                            }
                                        }
                                    )
                                    MainTab.Study -> HomeScreen(
                                        onWordClick = {
                                            ensureAudioPermission {
                                                currentScreen = Screen.WordPage
                                            }
                                        },
                                        onDictionaryClick = {
                                            currentScreen = Screen.LibraryManagement()
                                        },
                                        onMoErClick = {
                                            currentScreen = Screen.MoErPage
                                        },
                                        onDictationClick = {
                                            currentScreen = Screen.DictationPage
                                        },
                                        onPronunciationClick = {
                                            ensureAudioPermission {
                                                currentScreen = Screen.PronunciationPage
                                            }
                                        },
                                        onMeaningClick = {
                                            ensureAudioPermission {
                                                currentScreen = Screen.MeaningPage
                                            }
                                        },
                                        onSettingsClick = {
                                            currentScreen = Screen.Main(MainTab.Settings)
                                        },
                                        onCheckUpdate = checkForUpdates,
                                        isCheckingUpdate = isCheckingUpdate,
                                        knownCount = knownCount,
                                        unknownCount = unknownCount
                                    )
                                    MainTab.Settings -> SettingsScreen(onBack = null)
                                }
                            }
                        }
                    }
                    is Screen.WordPage -> {
                        val wordViewModel: WordViewModel = viewModel()
                        WordScreen(
                            viewModel = wordViewModel,
                            speechService = speechService,
                            onBack = backToStudy
                        )
                    }
                    is Screen.LibraryManagement -> {
                        val libraryViewModel: WordViewModel = viewModel()
                        LibraryManagementScreen(
                            viewModel = libraryViewModel,
                            onBack = backToStudy,
                            onSync = onSync,
                            isSyncing = isSyncing,
                            initialExpandedSection = screen.expandedSection,
                            onReset = {
                                libraryViewModel.resetAll {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "已重置所有学习记录",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    scope.launch {
                                        knownCount = 0
                                        unknownCount = 0
                                    }
                                }
                            }
                        )
                    }
                    is Screen.MoErPage -> {
                        val moerViewModel: WordViewModel = viewModel()
                        LaunchedEffect(Unit) { moerViewModel.refreshLibrary() }
                        val moerWords by moerViewModel.libraryUnknownWords.collectAsStateWithLifecycle()
                        val words = moerWords.map {
                            MoErWord(it.word, it.meaning, it.repeatVoice)
                        }
                        MoErScreen(
                            words = words,
                            onBack = backToStudy,
                            onMoErSecond = { taskStore.recordMoErSecond() }
                        )
                    }
                    is Screen.DictationPage -> TrainingPage(
                        mode = TrainingMode.Dictation,
                        speechService = speechService,
                        onBack = backToStudy,
                        onTrainingCompleted = { mode -> markTrainingDone(taskStore, mode) }
                    )
                    is Screen.PronunciationPage -> TrainingPage(
                        mode = TrainingMode.Pronunciation,
                        speechService = speechService,
                        onBack = backToStudy,
                        onTrainingCompleted = { mode -> markTrainingDone(taskStore, mode) }
                    )
                    is Screen.MeaningPage -> TrainingPage(
                        mode = TrainingMode.Meaning,
                        speechService = speechService,
                        onBack = backToStudy,
                        onTrainingCompleted = { mode -> markTrainingDone(taskStore, mode) }
                    )
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

private fun markTrainingDone(store: DailyTaskStore, mode: TrainingMode) {
    when (mode) {
        TrainingMode.Dictation -> store.markDictationDone()
        TrainingMode.Pronunciation -> store.markPronunciationDone()
        TrainingMode.Meaning -> store.markMeaningDone()
    }
}

@Composable
private fun TrainingPage(
    mode: TrainingMode,
    speechService: SpeechService,
    onBack: () -> Unit,
    onTrainingCompleted: (TrainingMode) -> Unit = {}
) {
    val trainingViewModel: WordViewModel = viewModel()
    var trainingWords by remember { mutableStateOf<List<Word>>(emptyList()) }
    LaunchedEffect(Unit) { trainingWords = trainingViewModel.getTrainingWords() }
    TrainingScreen(
        mode = mode,
        words = trainingWords,
        speechService = speechService,
        onBack = onBack,
        onTrainingCompleted = onTrainingCompleted
    )
}
