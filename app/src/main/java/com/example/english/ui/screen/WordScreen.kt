package com.example.english.ui.screen

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.example.english.speech.SpeechService
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
    val partOfSpeech: String,
    val syllables: List<Syllable> = emptyList()
)

private val syllableColors = listOf(
    Color(0xFF4A90D9),
    Color(0xFFE8913A),
    Color(0xFF50B86C)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordScreen(
    word: Word,
    speechService: SpeechService,
    onBack: () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasActiveRecording by remember { mutableStateOf(false) }
    var hasPcmData by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var playJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose { playJob?.cancel() }
    }

    LaunchedEffect(isPressed) {
        if (isPressed && !revealed) {
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
                speechService.stopAndRecognize()
                    .onSuccess { text -> recognizedText = text }
                    .onFailure { e -> errorMessage = e.message }
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
            Spacer(modifier = Modifier.weight(0.2f))

            SyllableWord(word.syllables, revealed)

            if (revealed) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = word.phonetic,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "${word.partOfSpeech}  ${word.meaning}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Recording indicator
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

            // Processing indicator
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

            // Recognition result + play button
            AnimatedVisibility(
                visible = recognizedText != null && !isProcessing,
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

            // Error message
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

            Spacer(modifier = Modifier.weight(0.75f))

            if (!revealed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(52.dp),
                        interactionSource = interactionSource,
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isProcessing
                    ) {
                        Text(if (isRecording) "松开识别" else "说意思", fontSize = 16.sp)
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
                    onClick = { /* TODO: next word */ },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlayButton(hasData: Boolean, isPlaying: Boolean, onPlay: () -> Unit, onStop: () -> Unit) {
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

        // Wait for playback to finish, polling for cancellation
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

@Composable
private fun SyllableWord(syllables: List<Syllable>, revealed: Boolean) {
    if (syllables.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        syllables.forEachIndexed { index, syllable ->
            val color = syllableColors[index % syllableColors.size]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (revealed) {
                    Text(
                        text = syllable.phonetic,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = color.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = syllable.text,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp
                    ),
                    color = color,
                    textAlign = TextAlign.Center
                )
            }
        }
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
                Spacer(modifier = Modifier.weight(0.2f))
                SyllableWord(
                    listOf(
                        Syllable("ex", "/ɪk/"),
                        Syllable("pen", "/ˈspen/"),
                        Syllable("sive", "/sɪv/")
                    ),
                    revealed
                )

                if (revealed) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "/ɪkˈspensɪv/",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "adj.  昂贵的",
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
