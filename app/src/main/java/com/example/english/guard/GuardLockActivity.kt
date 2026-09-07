package com.example.english.guard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.english.MainActivity
import com.example.english.data.DailyTaskStore
import com.example.english.data.WordRepository
import com.example.english.data.api.DeepSeekService
import com.example.english.data.entity.UnknownWord
import com.example.english.speech.SpeechService
import com.example.english.ui.screen.CelebrationBanner
import com.example.english.ui.screen.DictationLetterBoxes
import com.example.english.ui.screen.Word
import com.example.english.ui.screen.playErrorSound
import com.example.english.ui.screen.playPronunciation
import com.example.english.ui.screen.playSuccessSound
import com.example.english.ui.theme.EnglishTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 刷视频管控的复习锁屏。
 *
 * 规则：把今天找到的不认识单词（最多 `dailyUnknownLimit` 个，可在设置里改）依次过三关
 * —— 说意思 → 拼写 → 读一遍，全部完成才解锁；今天不足 5 个就按实际数量；
 * 一个都没有则引导去 App 里背单词找生词（此时仍保持锁定，回抖音会再次弹出）。
 */
class GuardLockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnglishTheme {
                GuardLockScreen(onFinish = { finish() })
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 锁屏期间禁止用返回键退出
    }
}

private enum class LockStage { MEANING, SPELL, READ, DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardLockScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { WordRepository(context) }
    val store = remember { GuardStateStore(context) }

    var words by remember { mutableStateOf<List<UnknownWord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        // 取今天学到的所有不认识单词并去重。
        // 注意：这里不再用 dailyUnknownLimit 截断——
        // 管控复习是「把今天学的不认识单词全部过一遍」，与「每日发现数量上限」（背单词时
        // 的新词配额）是两个独立维度，不能混用；否则今天找到 8 个生词但 limit=3，会被截掉
        // 后面 5 个，复习流程永远过不完。
        // 「不要记录进度，每次触发都从头开始」靠：
        //   1. reviewFlow 的 index/stage 用 remember（不持久化），Activity finish 即丢；
        //   2. 复习完一遍 unlockAndReset → finish()；下次 10 分钟阈值到了 EnGuardService
        //      重新启动 GuardLockActivity，LaunchedEffect(Unit) 重新加载并从 index=0 开始。
        // 「不限次数」靠：每次触发就是一次从头复习，复习完即解锁，无须计数也无须冷却。
        val todayWords = DailyTaskStore(context).getToday().unknownWords.distinct()
        words = repository.getUnknownWordsByText(todayWords)
        loading = false
    }

    // 锁屏期间屏蔽系统返回键
    BackHandler {}

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("娱乐管控") })
        }
    ) { innerPadding ->
        // 注意：外层 Column 不要 .verticalScroll —— 否则会把高度约束解绑成 Infinity，
        // 内层 ReviewFlow 里又有一个 verticalScroll Column，会被 Compose 1.5+ 检测到
        // 「嵌套垂直滚动」直接抛 IllegalStateException。
        // loading / NoWordsToday 本身不滚动；ReviewFlow 内部已经自带滚动 + 底部固定区。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                loading -> {
                    Spacer(modifier = Modifier.height(60.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在准备复习内容…", fontSize = 14.sp)
                }

                words.isEmpty() -> NoWordsToday(
                    onGoStudy = {
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        onFinish()
                    }
                )

                else -> ReviewFlow(
                    words = words,
                    hasMicPermission = hasMicPermission,
                    repository = repository,
                    onAllDone = {
                        store.unlockAndReset()
                        onFinish()
                    }
                )
            }
        }
    }
}

@Composable
private fun NoWordsToday(onGoStudy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "今天还没有找到不认识的单词",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "先去背单词，遇到不认识的标记为“不认识”，找到生词后就可以解锁继续刷视频了。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onGoStudy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("去背单词", fontSize = 16.sp) }
        }
    }
}

@Composable
private fun ReviewFlow(
    words: List<UnknownWord>,
    hasMicPermission: Boolean,
    repository: WordRepository,
    onAllDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var index by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf(LockStage.MEANING) }
    var spellingInput by remember { mutableStateOf("") }
    var manualInput by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    var meaningRevealed by remember { mutableStateOf(false) }

    // 最近一次录音识别的结果 + 录音文件（仅在 MEANING / READ stage 持有）。
    // advance() 切到下一 stage 时清空；切换单词由 advance() 兜底；stage 切换时
    // SpeechService 实例重建（remember(english) 触发），所以 wavFile 也需要清空。
    var lastRecognizedText by remember { mutableStateOf<String?>(null) }
    var lastRecordingFile by remember { mutableStateOf<File?>(null) }
    var recordingPlaybackJob by remember { mutableStateOf<Job?>(null) }

    fun startRecordingPlayback(file: File) {
        // 同一时刻只允许一段录音回放：开始新的回放前取消旧的，避免与新录的麦克风信号混音。
        recordingPlaybackJob?.cancel()
        recordingPlaybackJob = scope.launch {
            try {
                playRecordingFile(context, file)
            } finally {
                recordingPlaybackJob = null
            }
        }
    }

    val word = words.getOrNull(index)

    fun advance() {
        val current = words.getOrNull(index) ?: return
        // 三关都过 → 复用 App 内的复习调度（连续两天答对自动转认识）
        scope.launch { repository.onUnknownWordCorrect(current) }
        spellingInput = ""
        manualInput = ""
        hint = null
        meaningRevealed = false
        lastRecognizedText = null
        lastRecordingFile = null
        if (index + 1 >= words.size) {
            stage = LockStage.DONE
        } else {
            index++
            stage = LockStage.MEANING
        }
    }

    if (word == null || stage == LockStage.DONE) {
        // 全部完成视图：放在 scrollable Column 里，跟单词卡片同级
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "全部完成！${words.size} 个单词都复习过了",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "已经解锁，可以继续刷视频了。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAllDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("解锁", fontSize = 16.sp) }
        }
        return
    }

    val uiWord = remember(word) {
        Word(
            word = word.word,
            phonetic = word.phonetic,
            meaning = word.meaning,
            pronunciation = word.pronunciation
        )
    }

    val commitSpelling: () -> Unit = {
        val input = spellingInput.trim()
        if (input.isNotEmpty()) {
            if (input.equals(word.word.replace(" ", ""), ignoreCase = true)) {
                hint = null
                celebrating = true
            } else {
                hint = "拼写有误，再试试"
                playErrorSound(context)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 上半部：滚动区，展示进度 / 单词卡片 / 提示
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 进度
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "复习 ${index + 1} / ${words.size}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = when (stage) {
                        LockStage.MEANING -> "第 1 步 · 说意思"
                        LockStage.SPELL -> "第 2 步 · 拼写"
                        LockStage.READ -> "第 3 步 · 读一遍"
                        LockStage.DONE -> "完成"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 单词卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 拼写阶段不显示单词本身，让用户靠意思+音标回忆拼写
                        if (stage != LockStage.SPELL) {
                            Text(
                                text = word.word,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = {
                                if (word.pronunciation.isBlank()) {
                                    Toast.makeText(context, "该词暂无发音音频", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                scope.launch { playPronunciation(context, word.pronunciation) }
                            }) {
                                Icon(Icons.Rounded.VolumeUp, contentDescription = "播放发音")
                            }
                        }
                    }
                    // 拼写阶段也不显示音标（音标本身就是拼写提示）
                    if (word.phonetic.isNotBlank() && stage != LockStage.SPELL) {
                        Text(
                            text = word.phonetic,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (meaningRevealed) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = word.meaning,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }

                    // 录音识别结果 + 回放录音：仅在 MEANING / READ stage（用麦克风的关卡）显示
                    if (stage == LockStage.MEANING || stage == LockStage.READ) {
                        lastRecognizedText?.let { text ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { lastRecordingFile?.let { startRecordingPlayback(it) } },
                                    enabled = lastRecordingFile != null && recordingPlaybackJob == null
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "播放录音")
                                }
                                Text(
                                    text = if (recordingPlaybackJob != null) {
                                        "▶ ${if (stage == LockStage.MEANING) "播放中：$text" else "播放中：$text"}"
                                    } else {
                                        "识别：$text"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 拼写阶段：字母框（复用背单词的默写 UI）
                    if (stage == LockStage.SPELL) {
                        Spacer(modifier = Modifier.height(16.dp))
                        DictationLetterBoxes(
                            word = uiWord,
                            spellingInput = spellingInput,
                            onLetterClick = { focusRequester.requestFocus() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            hint?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 底部操作区占位（避免底部 fixed 操作区遮挡内容）
            Spacer(modifier = Modifier.height(180.dp))
        }

        // 底部固定操作区：Stage 1 说意思 + 手动输入 / Stage 2 拼写 / Stage 3 读一遍
        when (stage) {
            LockStage.MEANING -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "说出这个单词的中文意思",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 手动输入兜底：语音识别失败或没麦克风权限时也能过
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = { manualInput = it; hint = null },
                            modifier = Modifier.weight(1f).height(52.dp),
                            placeholder = { Text("或手动输入意思", fontSize = 13.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                val input = manualInput.trim()
                                if (input.isEmpty()) return@Button
                                scope.launch {
                                    checking = true
                                    val ok = checkMeaning(input, word.meaning.trim())
                                    checking = false
                                    if (ok) {
                                        hint = null
                                        meaningRevealed = true
                                        celebrating = true
                                    } else {
                                        hint = "意思不正确，再试试"
                                        playErrorSound(context)
                                    }
                                }
                            },
                            enabled = manualInput.isNotBlank() && !checking,
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("确认", fontSize = 14.sp) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    HoldToSpeakButton(
                        enabled = hasMicPermission && !checking && recordingPlaybackJob == null,
                        label = "说意思",
                        onResult = { text, wavFile ->
                            lastRecognizedText = text
                            lastRecordingFile = wavFile
                            scope.launch {
                                checking = true
                                val ok = checkMeaning(text, word.meaning.trim())
                                checking = false
                                if (ok) {
                                    hint = null
                                    meaningRevealed = true
                                    celebrating = true
                                } else {
                                    hint = "没听清或意思不对，再试一次"
                                    playErrorSound(context)
                                }
                            }
                        },
                        onError = { message -> hint = message }
                    )
                }
            }

            LockStage.SPELL -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "用键盘拼出这个单词",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 隐藏的输入框：接收键盘输入，字母显示在上面的字母框
                    // textStyle 必须设 1sp 透明，否则 BasicTextField 默认会渲染 16sp 行高 + 焦点光标下划线
                    val letterCount = word.word.count { it.isLetter() }
                    BasicTextField(
                        value = spellingInput,
                        onValueChange = { raw ->
                            spellingInput = raw.filter { it in 'a'..'z' || it in 'A'..'Z' }.take(letterCount)
                            hint = null
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { commitSpelling() }),
                        decorationBox = { inner -> inner() }
                    )

                    LaunchedEffect(stage, index) {
                        focusRequester.requestFocus()
                        delay(150)
                        keyboardController?.show()
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = commitSpelling,
                        enabled = spellingInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("确认", fontSize = 16.sp) }
                }
            }

            LockStage.READ -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "把这个单词读一遍",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HoldToSpeakButton(
                        enabled = hasMicPermission && !checking && recordingPlaybackJob == null,
                        label = "读一遍",
                        english = true,
                        onResult = { text, wavFile ->
                            lastRecognizedText = text
                            lastRecordingFile = wavFile
                            checking = true
                            if (isReadMatch(word.word, text)) {
                                checking = false
                                hint = null
                                celebrating = true
                            } else {
                                checking = false
                                hint = "读得不太准，再读一次"
                                playErrorSound(context)
                            }
                        },
                        onError = { message -> hint = message }
                    )
                }
            }

            LockStage.DONE -> Unit
        }

        // 识别 / AI 校验中的 loading 指示
        if (checking) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }

    // 答对提示动画：结束后进入下一步
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = celebrating,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CelebrationBanner(
                visible = celebrating,
                onFinished = {
                    celebrating = false
                    playSuccessSound(context)
                    when (stage) {
                        LockStage.MEANING -> stage = LockStage.SPELL
                        LockStage.SPELL -> stage = LockStage.READ
                        LockStage.READ -> advance()
                        LockStage.DONE -> Unit
                    }
                }
            )
        }
    }
}

@Composable
private fun HoldToSpeakButton(
    enabled: Boolean,
    label: String,
    /** 识别成功后回调：第二个参数是录音对应的 WAV 文件（用于回放），可能为 null */
    onResult: (String, File?) -> Unit,
    onError: (String) -> Unit,
    /** true = 识别英文（读单词）；false = 识别中文（说意思） */
    english: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speech = remember(english) {
        SpeechService(
            context,
            if (english) SpeechService.APP_KEY_ENGLISH else SpeechService.DEFAULT_APP_KEY
        )
    }
    var recording by remember { mutableStateOf(false) }

    // key 用 speech：中英文 key 切换时旧实例能被及时释放
    DisposableEffect(speech) { onDispose { speech.cleanup() } }

    Button(
        onClick = {},
        enabled = enabled && !recording,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(enabled, english) {
                // 用 pointerInput.awaitEachGesture 直接接管 press/release，
                // 避免 LaunchedEffect(isPressed) 在持续按住时因为 isPressed 反复变化
                // 触发的 cancel/restart race，导致录音被中途打断。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!enabled || recording) return@awaitEachGesture
                    recording = true
                    try {
                        speech.startRecording()
                    } catch (e: Exception) {
                        recording = false
                        onError(e.message ?: "录音启动失败")
                        return@awaitEachGesture
                    }
                    // 等待所有指针抬起
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                    }
                    // 用户松开：停止录音并识别
                    recording = false
                    scope.launch {
                        speech.stopAndRecognize()
                            .onSuccess { onResult(it, speech.lastWavFile) }
                            .onFailure { onError(it.message ?: "识别失败") }
                    }
                }
            },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50),
            contentColor = Color.White
        )
    ) {
        Text(if (recording) "松开识别" else label, fontSize = 16.sp)
    }
}

/** 说意思判定：先做包含匹配，不匹配再交给 AI 语义判定 */
/**
 * 判定用户的中文输入是否算"答对"了原意。
 *
 * 流程与 [WordScreen] / [TrainingScreen] 完全对齐：
 *   1. 快速本地命中（按 ; , 、 拆多义词 token，再做子串三向匹配）
 *   2. 快速命中失败才走 [DeepSeekService.compareMeaning]（每次 1~3s）
 *
 * 之所以把多义词按 token 切，是因为 DB 里 meaning 形如：
 *   "昂贵的；贵重的" / "苹果；苹果公司"
 * 用户常说"贵的" / "苹果"——整体 substring 命中没问题；但"value" / "不便宜"
 * 这种同义/反义表达 substring 直接挂，必须 AI 兜底。我们通过先按 token 切分，
 * 让本地命中覆盖更多的常见近义词片段（部分包含也算），减少不必要的 AI 调用。
 */
private suspend fun checkMeaning(input: String, meaning: String): Boolean {
    val ans = input.trim()
    val exp = meaning.trim()
    if (ans.isBlank() || exp.isBlank()) return false
    // 1. 整体三向 substring（背单词流程的快路径）
    if (exp.contains(ans) || ans.contains(exp)) return true
    // 2. 多义词 token 级命中：昂贵的；贵重的 → ["昂贵的", "贵重的"]
    val tokens = exp.split('；', ';', '、', ',', '，', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    for (t in tokens) {
        if (t == ans || t.contains(ans) || ans.contains(t)) return true
    }
    // 3. AI 兜底（不可避免的 1~3s 延迟）
    return DeepSeekService.compareMeaning(ans, exp)
}

/**
 * 朗读判定：与 [TrainingScreen.isPronunciationMatch] 同款（不再使用 LCS），
 * 避免 O(n×m) 计算带来的「按下后稍等一下」体感。
 *
 * 三向 contains 已能覆盖绝大部分 ASR 误差：
 *   - 末尾多字："apples" ⊇ "apple"
 *   - 末尾少字："appl" ⊂ "apple"
 *   - 完全一致
 * 极端 ASR 误差（如 "appul"）由 fail-fast + 用户重录解决，而不是用 LCS 蒙混过关。
 */
private fun isReadMatch(expected: String, actual: String): Boolean {
    val e = expected.lowercase().filter { it.isLetter() }.trim()
    val a = actual.lowercase().filter { it.isLetter() }.trim()
    if (e.isEmpty() || a.isEmpty()) return false
    return a == e || a.contains(e) || e.contains(a)
}

/**
 * 播放用户刚才录的 WAV 文件（单次播放）。
 *
 * 走和 [playPronunciation]（WordScreen.kt）一样的"单一 owner"模式：
 * - listener 只设置 `finished` 标志，不在这里 release mp
 * - 主循环是 MediaPlayer 的唯一所有者，主循环退出后单点 `mp.release()`
 * - 不调用 `mp.isPlaying()`，避开 IllegalStateException
 *
 * 调用方负责传入有效的本地 wav 文件（[SpeechService.lastWavFile]）。
 */
private suspend fun playRecordingFile(context: Context, file: File) =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            Log.d("EnglishApp", "playRecordingFile: ${file.absolutePath}")
            if (!file.exists() || file.length() == 0L) {
                Log.w("EnglishApp", "playRecordingFile: file missing or empty")
                return@withContext
            }
            val finished = java.util.concurrent.atomic.AtomicBoolean(false)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { finished.set(true) }
                setOnErrorListener { _, _, _ -> finished.set(true); true }
                prepare()
                start()
            }
            while (isActive && !finished.get()) {
                delay(200)
            }
            try { mp.release() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e("EnglishApp", "playRecordingFile failed: ${file.absolutePath}", e)
        }
    }
