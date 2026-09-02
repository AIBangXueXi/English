package com.example.english.ui.screen

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.english.data.api.DeepSeekService
import com.example.english.speech.SpeechService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TrainingMode(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    Dictation("默写单词", "看意思，拼写单词", Icons.Rounded.Edit),
    Pronunciation("发音训练", "看单词，跟读发音", Icons.Rounded.Mic),
    Meaning("单词意思", "看单词，说出意思", Icons.Rounded.Translate)
}

/**
 * 训练模式：默写单词 / 发音训练 / 单词意思。
 * 共用一套词表（本地词库 + 空库时拉取新词兜底），逐个训练，
 * 答对后播放庆祝提示，提示播完自动进入下一个单词。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrainingScreen(
    mode: TrainingMode,
    words: List<Word>,
    speechService: SpeechService,
    onBack: () -> Unit,
    onTrainingCompleted: (TrainingMode) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }
    var completed by remember { mutableStateOf(false) }
    var passed by remember { mutableStateOf(false) }
    var gaveUp by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    var showRetryHint by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isAiChecking by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasPcmData by remember { mutableStateOf(false) }
    var hasActiveRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var isPlayingPronunciation by remember { mutableStateOf(false) }
    var pronunciationJob by remember { mutableStateOf<Job?>(null) }
    var spellingInput by remember { mutableStateOf("") }
    var spellingError by remember { mutableStateOf<String?>(null) }
    var manualInput by remember { mutableStateOf("") }
    var manualResult by remember { mutableStateOf<String?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentWord = words.getOrNull(index)

    // 换词时重置当前词的状态
    LaunchedEffect(index) {
        passed = false
        gaveUp = false
        showCelebration = false
        showRetryHint = false
        isRecording = false
        isProcessing = false
        isAiChecking = false
        recognizedText = null
        errorMessage = null
        hasPcmData = false
        hasActiveRecording = false
        isPlaying = false
        spellingInput = ""
        spellingError = null
        manualInput = ""
        manualResult = null
    }

    fun nextWord() {
        if (index < words.size - 1) index++ else {
            completed = true
            onTrainingCompleted(mode)
        }
    }

    fun markPassed() {
        passed = true
        showCelebration = true
    }

    // 答对提示音
    LaunchedEffect(showCelebration) {
        if (showCelebration) playSuccessSound(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            playJob?.cancel()
            pronunciationJob?.cancel()
        }
    }

    // 按住录音 → 松开识别（发音训练 / 单词意思）
    LaunchedEffect(isPressed) {
        if (mode == TrainingMode.Dictation) return@LaunchedEffect
        if (passed || gaveUp) return@LaunchedEffect
        val word = currentWord ?: return@LaunchedEffect
        if (isPressed && !hasActiveRecording) {
            hasActiveRecording = true
            isRecording = true
            recognizedText = null
            errorMessage = null
            hasPcmData = false
            try {
                speechService.startRecording()
            } catch (e: Exception) {
                hasActiveRecording = false
                isRecording = false
                errorMessage = "录音启动失败: ${e.message}"
            }
        } else if (!isPressed && hasActiveRecording) {
            hasActiveRecording = false
            isRecording = false
            isProcessing = true
            scope.launch {
                val result = speechService.stopAndRecognize()
                result.onSuccess { text ->
                    recognizedText = text
                    val direct = when (mode) {
                        TrainingMode.Pronunciation -> isPronunciationMatch(text, word.word)
                        TrainingMode.Meaning -> word.meaning.trim().contains(text.trim())
                        TrainingMode.Dictation -> false
                    }
                    if (direct) {
                        markPassed()
                    } else if (mode == TrainingMode.Meaning) {
                        isAiChecking = true
                        val aiMatch = DeepSeekService.compareMeaning(
                            text.trim(), word.meaning.trim()
                        )
                        isAiChecking = false
                        if (aiMatch) {
                            markPassed()
                        } else {
                            showRetryHint = true
                            playErrorSound(context)
                        }
                    } else {
                        showRetryHint = true
                        playErrorSound(context)
                    }
                }.onFailure { e -> errorMessage = e.message }
                hasPcmData = speechService.lastPcmData != null
                isProcessing = false
            }
        }
    }

    // 默写自动判对：拼写完全正确即通过
    LaunchedEffect(spellingInput, mode) {
        if (mode == TrainingMode.Dictation && !passed && !gaveUp) {
            val target = currentWord?.word ?: return@LaunchedEffect
            if (spellingInput.isNotBlank() &&
                spellingInput.trim().equals(target, ignoreCase = true)
            ) {
                markPassed()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mode.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        when {
            words.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "没有不认识单词",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "先去背单词，标记不认识的词后再来训练吧",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                            Text("返回首页")
                        }
                    }
                }
            }

            completed -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "训练完成！",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "共完成 ${words.size} 个单词",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                            Text("返回首页")
                        }
                    }
                }
            }

            else -> {
                val word = currentWord
                if (word == null) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("出错了，请返回重试", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .imePadding()
                    ) {
                        // 默写字母框的键盘焦点（展示区点击字母框时聚焦到底部隐藏输入框）
                        val dictationFocusRequester = remember { FocusRequester() }
                        val keyboardController = LocalSoftwareKeyboardController.current
                        // 进入默写或换词时自动聚焦并唤起键盘，方便直接拼写
                        LaunchedEffect(index, mode) {
                            if (mode == TrainingMode.Dictation) {
                                dictationFocusRequester.requestFocus()
                                // 等焦点随下一帧应用后再唤起软键盘，否则 show 会失效
                                delay(150)
                                keyboardController?.show()
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 进度
                            Text(
                                text = "第 ${index + 1} / ${words.size} 个",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            when (mode) {
                                TrainingMode.Dictation -> DictationBody(
                                    word = word,
                                    gaveUp = gaveUp,
                                    spellingInput = spellingInput,
                                    onLetterClick = { dictationFocusRequester.requestFocus() },
                                    isPlayingPronunciation = isPlayingPronunciation,
                                    onTogglePronunciation = {
                                        togglePronunciation(
                                            context = context,
                                            word = word,
                                            scope = scope,
                                            isPlayingPronunciation = isPlayingPronunciation,
                                            setPlaying = { isPlayingPronunciation = it },
                                            job = pronunciationJob,
                                            setJob = { pronunciationJob = it }
                                        )
                                    }
                                )

                                TrainingMode.Pronunciation -> PronunciationBody(
                                    word = word,
                                    isPlayingPronunciation = isPlayingPronunciation,
                                    onTogglePronunciation = {
                                        togglePronunciation(
                                            context = context,
                                            word = word,
                                            scope = scope,
                                            isPlayingPronunciation = isPlayingPronunciation,
                                            setPlaying = { isPlayingPronunciation = it },
                                            job = pronunciationJob,
                                            setJob = { pronunciationJob = it }
                                        )
                                    }
                                )

                                TrainingMode.Meaning -> MeaningBody(
                                    word = word,
                                    isPlayingPronunciation = isPlayingPronunciation,
                                    onTogglePronunciation = {
                                        togglePronunciation(
                                            context = context,
                                            word = word,
                                            scope = scope,
                                            isPlayingPronunciation = isPlayingPronunciation,
                                            setPlaying = { isPlayingPronunciation = it },
                                            job = pronunciationJob,
                                            setJob = { pronunciationJob = it }
                                        )
                                    }
                                )
                            }

                            // 语音识别状态区（发音训练 / 单词意思）
                            if (mode != TrainingMode.Dictation) {
                                AnimatedVisibility(
                                    visible = isRecording,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Text(
                                        text = "正在录音...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color(0xFFE53935),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isProcessing,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "识别中...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isAiChecking,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "AI 验证中...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = recognizedText != null && !isProcessing && !isAiChecking,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "你说的是：",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = recognizedText ?: "",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 20.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        PlayButton(
                                            hasData = hasPcmData,
                                            isPlaying = isPlaying,
                                            onPlay = {
                                                isPlaying = true
                                                playJob = scope.launch {
                                                    playPcm(speechService.lastPcmData)
                                                    isPlaying = false
                                                }
                                            },
                                            onStop = {
                                                playJob?.cancel()
                                                isPlaying = false
                                            }
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = errorMessage != null && !isProcessing,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Text(
                                        text = errorMessage ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(150.dp))
                        }

                        // 底部操作区
                        BottomActionArea(
                            mode = mode,
                            word = word,
                            passed = passed,
                            gaveUp = gaveUp,
                            spellingInput = spellingInput,
                            spellingError = spellingError,
                            onSpellingChange = {
                                spellingInput = it
                                spellingError = null
                            },
                            onSpellingDone = {
                                val input = spellingInput.trim()
                                if (input.isNotEmpty() &&
                                    !input.equals(word.word, ignoreCase = true)
                                ) {
                                    spellingError = "拼写有误，再试试"
                                }
                            },
                            manualInput = manualInput,
                            manualResult = manualResult,
                            onManualChange = { manualInput = it; manualResult = null },
                            onManualConfirm = {
                                val input = manualInput.trim()
                                if (input.isNotEmpty()) {
                                    if (word.meaning.trim().contains(input)) {
                                        manualResult = null
                                        markPassed()
                                    } else {
                                        scope.launch {
                                            isAiChecking = true
                                            val aiMatch = DeepSeekService.compareMeaning(
                                                input, word.meaning.trim()
                                            )
                                            isAiChecking = false
                                            if (aiMatch) {
                                                manualResult = null
                                                markPassed()
                                            } else {
                                                manualResult = "意思不正确，再试试"
                                                playErrorSound(context)
                                            }
                                        }
                                    }
                                }
                            },
                            interactionSource = interactionSource,
                            isRecording = isRecording,
                            isProcessing = isProcessing,
                            dictationFocusRequester = dictationFocusRequester,
                            onGiveUp = { gaveUp = true },
                            onNext = { nextWord() }
                        )

                        // 答对 / 重试提示覆盖层
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedVisibility(
                                visible = showCelebration,
                                enter = scaleIn(animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)),
                                exit = fadeOut(animationSpec = tween(300))
                            ) {
                                CelebrationBanner(
                                    visible = showCelebration,
                                    onFinished = {
                                        showCelebration = false
                                        nextWord()
                                    }
                                )
                            }

                            AnimatedVisibility(
                                visible = showRetryHint,
                                enter = fadeIn(animationSpec = tween(300)),
                                exit = fadeOut(animationSpec = tween(300))
                            ) {
                                RetryBanner(
                                    visible = showRetryHint,
                                    onFinished = { showRetryHint = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 语音识别文本与目标单词是否匹配（忽略大小写、非字母字符） */
private fun isPronunciationMatch(spoken: String, target: String): Boolean {
    val norm = spoken.lowercase().filter { it.isLetter() }.trim()
    val t = target.lowercase().filter { it.isLetter() }.trim()
    if (norm.isEmpty() || t.isEmpty()) return false
    return norm == t || norm.contains(t) || t.contains(norm)
}

/** 播放单词发音音频（切换 播放/停止） */
private fun togglePronunciation(
    context: Context,
    word: Word,
    scope: CoroutineScope,
    isPlayingPronunciation: Boolean,
    setPlaying: (Boolean) -> Unit,
    job: Job?,
    setJob: (Job?) -> Unit
) {
    if (word.pronunciation.isBlank()) {
        Toast.makeText(context, "该词暂无发音音频", Toast.LENGTH_SHORT).show()
        return
    }
    if (isPlayingPronunciation) {
        job?.cancel()
        setPlaying(false)
    } else {
        setPlaying(true)
        setJob(scope.launch {
            playPronunciation(context, word.pronunciation)
            setPlaying(false)
        })
    }
}

@Composable
private fun PhoneticRow(
    word: Word,
    isPlayingPronunciation: Boolean,
    onTogglePronunciation: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = word.phonetic,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(
            onClick = onTogglePronunciation,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isPlayingPronunciation) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
                contentDescription = if (isPlayingPronunciation) "停止" else "发音",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DictationBody(
    word: Word,
    gaveUp: Boolean,
    spellingInput: String,
    onLetterClick: () -> Unit,
    isPlayingPronunciation: Boolean,
    onTogglePronunciation: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "默写",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (gaveUp) {
            Text(
                text = word.word,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        } else {
            // 在单词展示位置直接显示输入的字母（一字母一框）
            DictationLetterBoxes(
                word = word,
                spellingInput = spellingInput,
                onLetterClick = onLetterClick
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = word.meaning,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        PhoneticRow(word, isPlayingPronunciation, onTogglePronunciation)
    }
}

@Composable
private fun PronunciationBody(
    word: Word,
    isPlayingPronunciation: Boolean,
    onTogglePronunciation: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = word.word,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        PhoneticRow(word, isPlayingPronunciation, onTogglePronunciation)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = word.meaning,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "按住下方按钮，大声说出这个单词",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MeaningBody(
    word: Word,
    isPlayingPronunciation: Boolean,
    onTogglePronunciation: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = word.word,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        PhoneticRow(word, isPlayingPronunciation, onTogglePronunciation)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "说出或输入这个单词的意思",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.BottomActionArea(
    mode: TrainingMode,
    word: Word,
    passed: Boolean,
    gaveUp: Boolean,
    spellingInput: String,
    spellingError: String?,
    onSpellingChange: (String) -> Unit,
    onSpellingDone: () -> Unit,
    manualInput: String,
    manualResult: String?,
    onManualChange: (String) -> Unit,
    onManualConfirm: () -> Unit,
    interactionSource: MutableInteractionSource,
    isRecording: Boolean,
    isProcessing: Boolean,
    dictationFocusRequester: FocusRequester,
    onGiveUp: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            passed -> {
                // 庆祝播放中：等提示结束后自动进入下一个
                Text(
                    text = "即将进入下一个…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            gaveUp -> {
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("下一个", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            mode == TrainingMode.Dictation -> {
                DictationInput(
                    spellingInput = spellingInput,
                    spellingError = spellingError,
                    onSpellingChange = onSpellingChange,
                    onSpellingDone = onSpellingDone,
                    dictationFocusRequester = dictationFocusRequester,
                    onGiveUp = onGiveUp
                )
            }

            mode == TrainingMode.Pronunciation -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onGiveUp,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9E9E9E),
                            contentColor = Color.White
                        )
                    ) {
                        Text("跳过", fontSize = 16.sp)
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(52.dp),
                        interactionSource = interactionSource,
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                            disabledContentColor = Color.White
                        )
                    ) {
                        Text(if (isRecording) "松开识别" else "按住说单词", fontSize = 16.sp)
                    }
                }
            }

            else -> {
                // Meaning
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = onManualChange,
                        modifier = Modifier.weight(1f).height(52.dp),
                        placeholder = { Text("手动输入单词意思", fontSize = 13.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = onManualConfirm,
                        shape = RoundedCornerShape(14.dp),
                        enabled = manualInput.isNotBlank(),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("确认", fontSize = 14.sp)
                    }
                }

                if (manualResult != null) {
                    Text(
                        text = manualResult ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onGiveUp,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9E9E9E),
                            contentColor = Color.White
                        )
                    ) {
                        Text("不认识", fontSize = 16.sp)
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(52.dp),
                        interactionSource = interactionSource,
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                            disabledContentColor = Color.White
                        )
                    ) {
                        Text(if (isRecording) "松开识别" else "说意思", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

/** 默写输入：底部隐藏输入框（接收键盘），字母框显示在单词展示位置 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DictationInput(
    spellingInput: String,
    spellingError: String?,
    onSpellingChange: (String) -> Unit,
    onSpellingDone: () -> Unit,
    dictationFocusRequester: FocusRequester,
    onGiveUp: () -> Unit
) {
    val focusRequester = dictationFocusRequester

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = spellingInput,
            onValueChange = onSpellingChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .clickable { focusRequester.requestFocus() },
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSpellingDone() }),
            decorationBox = { innerTextField -> innerTextField() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (spellingInput.isEmpty()) {
            Text(
                text = "在字母框中拼写，全部拼对会自动进入下一个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (spellingError != null) {
            Text(
                text = spellingError ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onGiveUp,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF9E9E9E),
                contentColor = Color.White
            )
        ) {
            Text("不认识", fontSize = 16.sp)
        }
    }
}

/** Play PCM 16-bit 16kHz mono audio via AudioTrack. Blocking — call from IO dispatcher. */
private suspend fun playPcm(pcmData: ByteArray?) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val data = pcmData ?: return@withContext
    if (data.isEmpty()) return@withContext

    val sampleRate = 16000
    val bufferSize = maxOf(data.size, android.media.AudioTrack.getMinBufferSize(
        sampleRate,
        android.media.AudioFormat.CHANNEL_OUT_MONO,
        android.media.AudioFormat.ENCODING_PCM_16BIT
    ))

    val track = android.media.AudioTrack(
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build(),
        android.media.AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
            .build(),
        bufferSize,
        android.media.AudioTrack.MODE_STATIC,
        android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
    )

    try {
        track.write(data, 0, data.size)
        track.play()

        val durationMs = (data.size * 1000L) / (sampleRate * 2)
        val start = System.currentTimeMillis()
        while (isActive && System.currentTimeMillis() - start < durationMs + 200) {
            delay(100)
        }
    } finally {
        try { track.stop() } catch (_: Exception) {}
        track.release()
    }
}
