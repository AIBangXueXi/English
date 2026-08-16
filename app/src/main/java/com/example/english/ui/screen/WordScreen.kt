package com.example.english.ui.screen

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.english.data.QuizState
import com.example.english.data.WordViewModel
import com.example.english.data.api.DeepSeekService
import com.example.english.speech.SpeechService
import com.example.english.data.resolveRawResId
import com.example.english.data.resolveStaticUrl
import com.example.english.ui.theme.EnglishTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Syllable(
    val text: String,
    val phonetic: String
)

data class Word(
    val word: String,
    val phonetic: String,
    val meaning: String,
    val pronunciation: String = "",
    val etymology: List<String> = emptyList(),
    val etymologyPhonetic: List<String> = emptyList(),
    val etymologyPronunciation: List<String> = emptyList(),
    val plural: String = "",
    val thirdPersonSingular: String = "",
    val presentParticiple: String = "",
    val pastTense: String = "",
    val categoryName: String = "",
    val remark: String = "",
    val repeatVoice: String = ""
) {
    val syllables: List<Syllable>
        get() = if (etymology.isNotEmpty() && etymologyPhonetic.isNotEmpty())
            etymology.zip(etymologyPhonetic).map { (text, ph) -> Syllable(text, ph) }
        else emptyList()
}

private val syllableColors = listOf(
    Color(0xFF4A90D9),
    Color(0xFFE8913A),
    Color(0xFF50B86C)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordScreen(
    viewModel: WordViewModel,
    speechService: SpeechService,
    onBack: () -> Unit
) {
    val quizState by viewModel.state.collectAsStateWithLifecycle()
    var revealed by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasActiveRecording by remember { mutableStateOf(false) }
    var hasPcmData by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    var showRetryHint by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }
    var manualResult by remember { mutableStateOf<String?>(null) }
    var meaningPassed by remember { mutableStateOf(false) }
    var spellingPassed by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    var gaveUp by remember { mutableStateOf(false) }
    var spellingInput by remember { mutableStateOf("") }
    var spellingResult by remember { mutableStateOf<String?>(null) }
    var waitingForDictation by remember { mutableStateOf(false) }
    var isAiChecking by remember { mutableStateOf(false) }
    var isPlayingPronunciation by remember { mutableStateOf(false) }
    var pronunciationJob by remember { mutableStateOf<Job?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var playJob by remember { mutableStateOf<Job?>(null) }
    var studySeconds by remember { mutableStateOf(0) }
    var showRestReminder by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            studySeconds++
            if (studySeconds == 15 * 60) {
                showRestReminder = true
            }
        }
    }

    val currentWord = (quizState as? QuizState.Active)?.word
    val isDictation = meaningPassed && !spellingPassed && !gaveUp && !waitingForDictation
    LaunchedEffect(currentWord) {
        revealed = false
        isRecording = false
        isProcessing = false
        recognizedText = null
        errorMessage = null
        hasActiveRecording = false
        hasPcmData = false
        isCorrect = false
        showCelebration = false
        showRetryHint = false
        manualInput = ""
        manualResult = null
        meaningPassed = false
        spellingPassed = false
        committed = false
        gaveUp = false
        spellingInput = ""
        spellingResult = null
        waitingForDictation = false
        isPlayingPronunciation = false
        isAiChecking = false
    }

    // 默写完成自动确认：拼写完全正确即自动标记通过（无需点“确认”）
    LaunchedEffect(spellingInput, isDictation) {
        if (isDictation && spellingInput.isNotBlank()) {
            val target = currentWord?.word ?: return@LaunchedEffect
            if (spellingInput.trim().equals(target, ignoreCase = true)) {
                spellingPassed = true
                showCelebration = true
                spellingResult = null
                if (!committed) {
                    committed = true
                    viewModel.onCorrectAnswer()
                }
            }
        }
    }

    // 默写成功后播放 res/raw/success.mp3 反馈音
    LaunchedEffect(showCelebration) {
        if (showCelebration) {
            playSuccessSound(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playJob?.cancel()
            pronunciationJob?.cancel()
        }
    }

    LaunchedEffect(isPressed) {
        if (meaningPassed || gaveUp) return@LaunchedEffect
        val word = currentWord ?: return@LaunchedEffect
        if (isPressed && !revealed) {
            hasActiveRecording = true
            isRecording = true
            recognizedText = null
            errorMessage = null
            hasPcmData = false
            isCorrect = false
            showCelebration = false
            showRetryHint = false
            isAiChecking = false
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
                    if (word.meaning.trim().contains(text.trim())) {
                        meaningPassed = true
                        revealed = true
                        showCelebration = true
                        waitingForDictation = true
                    } else {
                        isAiChecking = true
                        val aiMatch = DeepSeekService.compareMeaning(
                            text.trim(), word.meaning.trim()
                        )
                        isAiChecking = false
                        if (aiMatch) {
                            meaningPassed = true
                            revealed = true
                            showCelebration = true
                            waitingForDictation = true
                        } else {
                            showRetryHint = true
                            playErrorSound(context)
                        }
                    }
                }.onFailure { e -> errorMessage = e.message }
                hasPcmData = speechService.lastPcmData != null
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("背单词") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val minutes = studySeconds / 60
                    val seconds = studySeconds % 60
                    Text(
                        text = "${minutes}:${seconds.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (studySeconds >= 15 * 60)
                            Color(0xFFE53935)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        when (quizState) {
            is QuizState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("加载单词中...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            is QuizState.Complete -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "全部单词已学完！",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "休息一下，明天再来",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                            Text("返回首页")
                        }
                    }
                }
            }

            is QuizState.DailyComplete -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎯", style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "今日学习任务已完成！",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "今天发现的不认识单词已达目标，明天再来吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                            Text("返回首页")
                        }
                    }
                }
            }

            is QuizState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = (quizState as QuizState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadNextWord() }, shape = RoundedCornerShape(14.dp)) {
                            Text("重试")
                        }
                    }
                }
            }

            is QuizState.Active -> {
                val active = quizState as QuizState.Active
                val word = active.word
                val context = LocalContext.current

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                ) {
                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Category badge
                        if (word.categoryName.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text(
                                    text = word.categoryName,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Review badge
                        if (active.isReview) {
                            Text(
                                text = "复习 · 第 ${active.stage + 1} 阶段 · 待巩固 ${active.unknownCount} 词",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9800),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // The full word (hidden during dictation so the user must recall the spelling)
                        if (isDictation) {
                            Text(
                                text = "第 2 步 · 默写",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "＿＿＿",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 36.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "拼写该单词",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = word.word,
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 36.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Etymology breakdown
                        if (word.syllables.isNotEmpty() && !isDictation) {
                            Spacer(modifier = Modifier.height(24.dp))
                            EtymologyBreakdown(word.syllables, word.etymologyPronunciation, revealed)
                        }

                        if (revealed) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Phonetic + pronunciation audio
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = word.phonetic,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!isDictation) {
                                    IconButton(
                                        onClick = {
                                            if (word.pronunciation.isBlank()) {
                                                Toast.makeText(
                                                    context,
                                                    "该词暂无发音音频",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@IconButton
                                            }
                                            if (isPlayingPronunciation) {
                                                pronunciationJob?.cancel()
                                                isPlayingPronunciation = false
                                            } else {
                                                isPlayingPronunciation = true
                                                pronunciationJob = scope.launch {
                                                    playPronunciation(context, word.pronunciation)
                                                    isPlayingPronunciation = false
                                                }
                                            }
                                        },
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

                            Spacer(modifier = Modifier.height(16.dp))

                            // Translation
                            Text(
                                text = word.meaning,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )

                            // Word forms
                            val forms = buildList {
                                if (word.plural.isNotEmpty()) add("复数: ${word.plural}")
                                if (word.thirdPersonSingular.isNotEmpty()) add("三单: ${word.thirdPersonSingular}")
                                if (word.presentParticiple.isNotEmpty()) add("现在分词: ${word.presentParticiple}")
                                if (word.pastTense.isNotEmpty()) add("过去式: ${word.pastTense}")
                            }
                            if (forms.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "词形变化",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            forms.forEach { form ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = form,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        textAlign = TextAlign.Center,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Remark
                            if (word.remark.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = word.remark,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp)
                                )
                            }
                        }

                        // Status indicators
                        AnimatedVisibility(
                            visible = manualResult != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                text = manualResult ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = spellingResult != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                text = spellingResult ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isRecording,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                text = "正在录音...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFE53935),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        AnimatedVisibility(
                            visible = isProcessing,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                if (hasPcmData) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    PlayButton(
                                        hasData = true,
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
                        }

                        // Bottom spacer to avoid overlap with fixed buttons
                        Spacer(modifier = Modifier.height(140.dp))
                    }

                    // Fixed bottom action area — staged: 说意思 → (答对提示) → 默写 → 下一个
                    when {
                        meaningPassed && waitingForDictation -> {
                            // 答对提示播放中：等庆祝动画/提示音结束后再进入默写
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "即将进入默写…",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        !meaningPassed && !gaveUp -> {
                            // Stage 1: 说意思
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Manual input
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = manualInput,
                                        onValueChange = { manualInput = it; manualResult = null },
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        placeholder = { Text("手动输入单词意思", fontSize = 13.sp) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium
                                    )
                                    Button(
                                        onClick = {
                                            val input = manualInput.trim()
                                            if (input.isNotEmpty()) {
                                                if (word.meaning.trim().contains(input)) {
                                                    meaningPassed = true
                                                    revealed = true
                                                    manualResult = null
                                                    showCelebration = true
                                                    waitingForDictation = true
                                                } else {
                                                    scope.launch {
                                                        isAiChecking = true
                                                        val aiMatch = DeepSeekService.compareMeaning(
                                                            input, word.meaning.trim()
                                                        )
                                                        isAiChecking = false
                                                        if (aiMatch) {
                                                            meaningPassed = true
                                                            revealed = true
                                                            manualResult = null
                                                            showCelebration = true
                                                            waitingForDictation = true
                                                        } else {
                                                            manualResult = "意思不正确，再试试"
                                                        playErrorSound(context)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        enabled = manualInput.isNotBlank(),
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Text("确认", fontSize = 14.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Main action buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            gaveUp = true
                                            revealed = true
                                            if (!committed) {
                                                committed = true
                                                viewModel.onWrongAnswer()
                                            }
                                        },
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

                        isDictation -> {
                            // Stage 2: 默写 —— 一字母一框，实时绿/红反馈
                            val targetChars = word.word.map { it }
                            val letterIndexBySlot = run {
                                var cursor = 0
                                targetChars.map { ch ->
                                    if (ch.isLetter()) {
                                        val idx = cursor
                                        cursor++
                                        idx
                                    } else -1
                                }
                            }
                            val typedLetters = spellingInput.filter { it.isLetter() }
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(isDictation, currentWord) {
                                focusRequester.requestFocus()
                            }
                            val commitSpelling: () -> Unit = {
                                val input = spellingInput.trim()
                                if (input.isNotEmpty()) {
                                    if (input.equals(word.word, ignoreCase = true)) {
                                        spellingPassed = true
                                        showCelebration = true
                                        spellingResult = null
                                        if (!committed) {
                                            committed = true
                                            viewModel.onCorrectAnswer()
                                        }
                                    } else {
                                        spellingResult = "拼写有误，再试试"
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 字母框 + 非字母分隔符；点击任意处聚焦键盘
                                BasicTextField(
                                    value = spellingInput,
                                    onValueChange = { spellingInput = it; spellingResult = null },
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
                                    keyboardActions = KeyboardActions(onDone = { commitSpelling() }),
                                    decorationBox = { innerTextField ->
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                                            maxItemsInEachRow = Int.MAX_VALUE
                                        ) {
                                            targetChars.forEachIndexed { pos, ch ->
                                                if (ch.isLetter()) {
                                                    val typed = typedLetters.getOrNull(letterIndexBySlot[pos])
                                                    val correct =
                                                        typed != null && typed.equals(ch, ignoreCase = true)
                                                    val bg = when {
                                                        typed == null -> Color(0xFFECEFF1) // 未填：浅灰
                                                        correct -> Color(0xFFE0F2E1)       // 拼对：淡绿
                                                        else -> Color(0xFFFFEBEE)           // 拼错：淡红
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(56.dp)
                                                            .background(bg, RoundedCornerShape(10.dp))
                                                            .border(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.outline
                                                                    .copy(alpha = 0.4f),
                                                                RoundedCornerShape(8.dp)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = (typed ?: ' ').toString(),
                                                            style = MaterialTheme.typography.headlineSmall
                                                                .copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = ch.toString(),
                                                        style = MaterialTheme.typography.headlineSmall
                                                            .copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                            innerTextField()
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (spellingInput.isEmpty()) {
                                    Text(
                                        text = "用键盘在字母框中拼写，全部拼对会自动进入下一步",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            gaveUp = true
                                            revealed = true
                                            if (!committed) {
                                                committed = true
                                                viewModel.onWrongAnswer()
                                            }
                                        },
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
                                        onClick = { commitSpelling() },
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        enabled = spellingInput.isNotBlank()
                                    ) {
                                        Text("确认", fontSize = 16.sp)
                                    }
                                }
                            }
                        }

                        else -> {
                            // Both passed (or gave up) → commit and go next
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.loadNextWord()
                                    },
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
                        }
                    }

                    // 答对/重试提示：全屏居中覆盖层（不再内嵌于滚动内容）
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
                                    waitingForDictation = false
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

    if (showRestReminder) {
        AlertDialog(
            onDismissRequest = { showRestReminder = false },
            title = { Text("休息一下") },
            text = { Text("你已经连续学习15分钟了，起来活动一下，看看远处，让眼睛休息一下吧。") },
            confirmButton = {
                TextButton(onClick = { showRestReminder = false }) {
                    Text("继续学习")
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text("返回首页")
                }
            }
        )
    }
}

@Composable
private fun EtymologyBreakdown(
    syllables: List<Syllable>,
    etymologyPronunciation: List<String>,
    revealed: Boolean
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var playingIndex by remember { mutableStateOf<Int?>(null) }
    var syllableJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose { syllableJob?.cancel() }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        syllables.forEachIndexed { index, syllable ->
            val color = syllableColors[index % syllableColors.size]
            val pronUrl = etymologyPronunciation.getOrNull(index)
                ?.takeIf { it.isNotEmpty() }
                ?.let { resolveStaticUrl(it) } ?: ""
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (revealed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = syllable.phonetic,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = color.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        if (pronUrl.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    if (playingIndex == index) {
                                        syllableJob?.cancel()
                                        playingIndex = null
                                    } else {
                                        syllableJob?.cancel()
                                        playingIndex = index
                                        syllableJob = scope.launch(Dispatchers.IO) {
                                            try {
                                                Log.d("EnglishApp", "Syllable play: index=$index text=${syllable.text} url=$pronUrl")
                                                val mp = if (pronUrl.startsWith("raw:", ignoreCase = true)) {
                                                    val resId = resolveRawResId(context, pronUrl)
                                                    if (resId != 0) MediaPlayer.create(context, resId) else null
                                                } else {
                                                    val tempFile = java.io.File(context.cacheDir, "syl_${System.currentTimeMillis()}.wav")
                                                    try {
                                                        val conn = java.net.URL(pronUrl).openConnection() as java.net.HttpURLConnection
                                                        conn.setRequestProperty("User-Agent", "EnglishApp")
                                                        conn.connectTimeout = 10000
                                                        conn.readTimeout = 10000
                                                        conn.connect()
                                                        if (conn.responseCode == 200) {
                                                            conn.inputStream.use { input ->
                                                                tempFile.outputStream().use { output -> input.copyTo(output) }
                                                            }
                                                        } else { conn.disconnect(); null }
                                                        conn.disconnect()
                                                    } catch (e: Exception) { null }
                                                    if (tempFile.exists() && tempFile.length() > 44) {
                                                        MediaPlayer().apply {
                                                            setAudioAttributes(
                                                                AudioAttributes.Builder()
                                                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                                    .build()
                                                            )
                                                            setDataSource(tempFile.absolutePath)
                                                            setOnCompletionListener {
                                                                it.release()
                                                                tempFile.delete()
                                                            }
                                                            setOnErrorListener { m, _, _ ->
                                                                m.release()
                                                                tempFile.delete()
                                                                true
                                                            }
                                                            prepare()
                                                            start()
                                                        }
                                                    } else null
                                                }
                                                if (mp != null) {
                                                    if (pronUrl.startsWith("raw:", ignoreCase = true)) mp.start()
                                                    while (isActive && mp.isPlaying) { delay(300) }
                                                    try { mp.release() } catch (_: Exception) {}
                                                }
                                            } catch (e: Exception) {
                                                Log.e("EnglishApp", "Syllable play failed: url=$pronUrl", e)
                                            }
                                            playingIndex = null
                                        }
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (playingIndex == index) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
                                    contentDescription = if (playingIndex == index) "停止" else "发音",
                                    modifier = Modifier.size(16.dp),
                                    tint = color
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = syllable.text,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = color,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun CelebrationBanner(visible: Boolean, onFinished: () -> Unit) {
    val starColors = listOf(
        Color(0xFFFFD700), Color(0xFFFF6B6B), Color(0xFF4FC3F7),
        Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFBA68C8)
    )

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = tween(500)
    )

    // 小球从左/右两侧慢慢靠拢到中心
    val converge by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(durationMillis = 1600, easing = LinearOutSlowInEasing)
    )

    if (visible) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2500)
            onFinished()
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(vertical = 22.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                starColors.forEachIndexed { i, color ->
                    val baseOffset = (i.toFloat() - 2.5f) * 60
                    val xOffset = (baseOffset * converge).dp
                    val delay = i * 100L
                    val animScale by animateFloatAsState(
                        targetValue = if (visible) 1f else 0f,
                        animationSpec = tween(600, delayMillis = delay.toInt())
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = xOffset)
                            .size((12 * animScale).dp)
                            .background(color, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size((48 * scale).dp),
                tint = Color(0xFF4CAF50)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "说对了!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (28 * scale).sp
                ),
                color = Color(0xFF4CAF50),
                textAlign = TextAlign.Center
            )
            }
        }
    }
}

@Composable
internal fun RetryBanner(visible: Boolean, onFinished: () -> Unit) {
    val shakeOffset = remember { Animatable(0f) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400)
    )

    if (visible) {
        LaunchedEffect(Unit) {
            shakeOffset.animateTo(-12f, spring(dampingRatio = 0.3f, stiffness = 800f))
            shakeOffset.animateTo(12f, spring(dampingRatio = 0.3f, stiffness = 800f))
            shakeOffset.animateTo(-8f, spring(dampingRatio = 0.3f, stiffness = 800f))
            shakeOffset.animateTo(8f, spring(dampingRatio = 0.3f, stiffness = 800f))
            shakeOffset.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 800f))
            kotlinx.coroutines.delay(1200)
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .offset(x = shakeOffset.value.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🤔",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "再想一想...",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp
                ),
                color = Color(0xFFFF9800),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "长按按钮再试一次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun PlayButton(hasData: Boolean, isPlaying: Boolean, onPlay: () -> Unit, onStop: () -> Unit) {
    TextButton(
        onClick = { if (isPlaying) onStop() else onPlay() },
        enabled = hasData
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "停止" else "播放",
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isPlaying) "停止播放" else "播放录音",
            fontSize = 14.sp
        )
    }
}

/** Play PCM 16-bit 16kHz mono audio via AudioTrack. Blocking — call from IO dispatcher. */
private suspend fun playPcm(pcmData: ByteArray?) = withContext(Dispatchers.IO) {
    val data = pcmData ?: return@withContext
    if (data.isEmpty()) return@withContext

    val sampleRate = 16000
    val bufferSize = maxOf(data.size, AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ))

    val track = AudioTrack(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build(),
        AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build(),
        bufferSize,
        AudioTrack.MODE_STATIC,
        AudioManager.AUDIO_SESSION_ID_GENERATE
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

/**
 * Play the bundled success feedback sound (res/raw/success.mp3) once.
 * One-shot: the MediaPlayer releases itself when playback finishes.
 */
internal fun playSuccessSound(context: Context) {
    try {
        val resId = resolveRawResId(context, "raw:success")
        if (resId == 0) return
        val mp = MediaPlayer.create(context, resId) ?: return
        mp.setOnCompletionListener { it.release() }
        mp.setOnErrorListener { m, _, _ -> m.release(); true }
        mp.start()
    } catch (_: Exception) {
        // ignore — feedback sound is non-critical
    }
}

/**
 * Play the bundled error feedback sound (res/raw/error.mp3) once.
 * One-shot: the MediaPlayer releases itself when playback finishes.
 */
internal fun playErrorSound(context: Context) {
    try {
        val resId = resolveRawResId(context, "raw:error")
        if (resId == 0) return
        val mp = MediaPlayer.create(context, resId) ?: return
        mp.setOnCompletionListener { it.release() }
        mp.setOnErrorListener { m, _, _ -> m.release(); true }
        mp.start()
    } catch (_: Exception) {
        // ignore — feedback sound is non-critical
    }
}

/**
 * Play audio via MediaPlayer. Supports two source forms:
 * - "raw:<resName>" -> a file bundled in res/raw/ (offline playback).
 * - anything else    -> treated as a URL (online playback).
 * Returns true if playback started successfully.
 */
internal suspend fun playPronunciation(context: Context, source: String): Boolean = withContext(Dispatchers.IO) {
    try {
        Log.d("EnglishApp", "playPronunciation: source=$source")
        val mp = if (source.startsWith("raw:", ignoreCase = true)) {
            val resId = resolveRawResId(context, source)
            if (resId == 0) return@withContext false
            MediaPlayer.create(context, resId) ?: return@withContext false
        } else {
            // Download to temp file first to avoid HTTPS issues on some devices (e.g. OPPO)
            val tempFile = java.io.File(context.cacheDir, "pron_${System.currentTimeMillis()}.wav")
            try {
                val conn = java.net.URL(source).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "EnglishApp")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.connect()
                if (conn.responseCode == 200) {
                    conn.inputStream.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } else {
                    Log.e("EnglishApp", "Download failed: HTTP ${conn.responseCode}")
                    return@withContext false
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("EnglishApp", "Download failed", e)
                return@withContext false
            }
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    it.release()
                    tempFile.delete()
                }
                setOnErrorListener { m, _, _ ->
                    m.release()
                    tempFile.delete()
                    true
                }
                prepare()
                start()
            }
        }
        if (source.startsWith("raw:", ignoreCase = true)) {
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { m, _, _ -> m.release(); true }
            mp.start()
        }
        while (isActive && mp.isPlaying) { delay(300) }
        try { mp.release() } catch (_: Exception) {}
        true
    } catch (e: Exception) {
        Log.e("EnglishApp", "playPronunciation failed: source=$source", e)
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun WordScreenPreview() {
    EnglishTheme {
        var revealed by remember { mutableStateOf(false) }
        var recognizedText by remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("背单词") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Category badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = "CET-4",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.weight(0.15f))

                Text(
                    text = "expensive",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                EtymologyBreakdown(
                    listOf(
                        Syllable("ex", "/ɪk/"),
                        Syllable("pen", "/ˈspen/"),
                        Syllable("sive", "/sɪv/")
                    ),
                    emptyList(),
                    revealed
                )

                if (revealed) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "/ɪkˈspensɪv/",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "昂贵的",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }

                if (recognizedText != null) {
                    Text(
                        text = "你说的是：$recognizedText",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.weight(0.75f))

                if (!revealed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("说意思", fontSize = 16.sp)
                        }
                        Button(
                            onClick = { revealed = true },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("不认识", fontSize = 16.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("下一个", fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
