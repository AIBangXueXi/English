package com.example.english.guard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 刷视频管控的复习锁屏。
 *
 * 规则：把今天找到的不认识单词（最多 [GuardStateStore.REVIEW_WORD_COUNT] 个）依次过三关
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
        val todayWords = DailyTaskStore(context).getToday().unknownWords.distinct()
        words = repository.getUnknownWordsByText(todayWords)
            .take(GuardStateStore.REVIEW_WORD_COUNT)
        loading = false
    }

    // 锁屏期间屏蔽系统返回键
    BackHandler {}

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("刷视频管控") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(24.dp))
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

    val word = words.getOrNull(index)

    fun advance() {
        val current = words.getOrNull(index) ?: return
        // 三关都过 → 复用 App 内的复习调度（连续两天答对自动转认识）
        scope.launch { repository.onUnknownWordCorrect(current) }
        spellingInput = ""
        manualInput = ""
        hint = null
        meaningRevealed = false
        if (index + 1 >= words.size) {
            stage = LockStage.DONE
        } else {
            index++
            stage = LockStage.MEANING
        }
    }

    if (word == null || stage == LockStage.DONE) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = {
                        scope.launch { playPronunciation(context, word.pronunciation) }
                    }) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = "播放发音")
                    }
                }
                if (word.phonetic.isNotBlank()) {
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

        when (stage) {
            LockStage.MEANING -> {
                Text(
                    text = "说出这个单词的中文意思",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.height(12.dp))

                HoldToSpeakButton(
                    enabled = hasMicPermission && !checking,
                    label = "说意思",
                    onResult = { text ->
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

            LockStage.SPELL -> {
                Text(
                    text = "用键盘拼出这个单词",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 隐藏输入框：接收键盘输入，字母显示在上面的字母框
                val letterCount = word.word.count { it.isLetter() }
                BasicTextField(
                    value = spellingInput,
                    onValueChange = { raw ->
                        spellingInput = raw.filter { it in 'a'..'z' || it in 'A'..'Z' }.take(letterCount)
                        hint = null
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
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

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = commitSpelling,
                    enabled = spellingInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("确认", fontSize = 16.sp) }
            }

            LockStage.READ -> {
                Text(
                    text = "把这个单词读一遍",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                HoldToSpeakButton(
                    enabled = hasMicPermission && !checking,
                    label = "读一遍",
                    onResult = { text ->
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

            LockStage.DONE -> Unit
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (checking) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speech = remember { SpeechService(context) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var busy by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed && enabled && !busy) {
            active = true
            busy = true
            try {
                speech.startRecording()
            } catch (e: Exception) {
                active = false
                busy = false
                onError(e.message ?: "录音启动失败")
            }
        } else if (!isPressed && active) {
            active = false
            scope.launch {
                speech.stopAndRecognize()
                    .onSuccess { onResult(it) }
                    .onFailure { onError(it.message ?: "识别失败") }
                busy = false
            }
        }
    }

    DisposableEffect(Unit) { onDispose { speech.cleanup() } }

    Button(
        onClick = {},
        interactionSource = interactionSource,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50),
            contentColor = Color.White
        )
    ) {
        Text(if (busy) "松开识别" else label, fontSize = 16.sp)
    }
}

/** 说意思判定：先做包含匹配，不匹配再交给 AI 语义判定 */
private suspend fun checkMeaning(input: String, meaning: String): Boolean {
    if (input.isBlank() || meaning.isBlank()) return false
    if (meaning.contains(input.trim())) return true
    return DeepSeekService.compareMeaning(input.trim(), meaning)
}

/** 朗读判定：归一化后完全相等 / 包含，或用最长公共子序列比例容错 ASR 识别误差 */
private fun isReadMatch(expected: String, actual: String): Boolean {
    val e = normalizeSpoken(expected)
    val a = normalizeSpoken(actual)
    if (e.isEmpty() || a.isEmpty()) return false
    if (e == a || a.contains(e) || e.contains(a)) return true
    val lcs = lcsLength(e, a)
    return lcs.toFloat() / maxOf(e.length, a.length) >= 0.75f
}

private fun normalizeSpoken(text: String): String =
    text.lowercase().filter { it in 'a'..'z' }

private fun lcsLength(a: String, b: String): Int {
    val dp = IntArray(b.length + 1)
    for (i in 1..a.length) {
        var prev = 0
        for (j in 1..b.length) {
            val tmp = dp[j]
            dp[j] = if (a[i - 1] == b[j - 1]) prev + 1 else maxOf(dp[j], dp[j - 1])
            prev = tmp
        }
    }
    return dp[b.length]
}
