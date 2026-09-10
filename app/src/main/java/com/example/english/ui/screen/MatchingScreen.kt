package com.example.english.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 每轮配对的单词数量 */
private const val ROUND_SIZE = 6

/**
 * 单词对对碰：左列单词、右列中文意思，逐对匹配。
 * 每轮最多 [ROUND_SIZE] 对，全部配对后进入下一轮，直到所有不认识单词配对完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchingScreen(
    words: List<Word>,
    initialMatchedCount: Int = 0,
    onBack: () -> Unit,
    onProgressChange: (Int) -> Unit = {},
    onCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val total = words.size
    var completedCount by remember { mutableIntStateOf(initialMatchedCount.coerceIn(0, total)) }

    // 当前轮的词与打乱后的两列顺序
    val roundEnd = minOf(total, completedCount + ROUND_SIZE)
    val roundWords = if (completedCount >= total) emptyList() else words.subList(completedCount, roundEnd)
    val wordOrder = remember(completedCount, total) { roundWords.indices.shuffled() }
    val meaningOrder = remember(completedCount, total) { roundWords.indices.shuffled() }
    var selectedWord by remember(completedCount, total) { mutableStateOf<Int?>(null) } // 选中的 round 下标
    var matched by remember(completedCount, total) { mutableStateOf(setOf<Int>()) }    // 已配对的 round 下标
    var wrongFlash by remember(completedCount, total) { mutableStateOf<Int?>(null) }   // 答错闪烁的 round 下标

    // 答错红框短暂显示后复位
    LaunchedEffect(wrongFlash) {
        if (wrongFlash != null) {
            delay(700)
            wrongFlash = null
        }
    }

    // 当前轮全部配对 → 进入下一轮
    LaunchedEffect(matched, roundWords.size) {
        if (roundWords.isNotEmpty() && matched.size == roundWords.size) {
            delay(500)
            completedCount += roundWords.size
            onProgressChange(completedCount)
        }
    }

    // 全部完成
    LaunchedEffect(completedCount, total) {
        if (total > 0 && completedCount >= total) {
            onCompleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("单词对对碰") },
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

            completedCount >= total -> {
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
                            text = "共配对 ${words.size} 个单词",
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "配对单词与意思",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "已配对 ${completedCount + matched.size} / $total",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            wordOrder.forEach { roundIdx ->
                                MatchingCard(
                                    text = roundWords[roundIdx].word,
                                    matched = roundIdx in matched,
                                    selected = selectedWord == roundIdx,
                                    wrong = false,
                                    onClick = {
                                        if (roundIdx !in matched) {
                                            selectedWord = roundIdx
                                            wrongFlash = null
                                            val w = roundWords[roundIdx]
                                            if (w.pronunciation.isNotBlank()) {
                                                scope.launch { playPronunciation(context, w.pronunciation) }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            meaningOrder.forEach { roundIdx ->
                                MatchingCard(
                                    text = roundWords[roundIdx].meaning,
                                    matched = roundIdx in matched,
                                    selected = false,
                                    wrong = wrongFlash == roundIdx,
                                    onClick = {
                                        if (roundIdx !in matched) {
                                            val sel = selectedWord
                                            when {
                                                sel == null -> {}
                                                sel == roundIdx -> {
                                                    matched = matched + roundIdx
                                                    selectedWord = null
                                                    playSuccessSound(context)
                                                }
                                                else -> {
                                                    wrongFlash = roundIdx
                                                    playErrorSound(context)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchingCard(
    text: String,
    matched: Boolean,
    selected: Boolean,
    wrong: Boolean,
    onClick: () -> Unit
) {
    val container = when {
        matched -> Color(0xFF1387C0).copy(alpha = 0.18f)
        wrong -> MaterialTheme.colorScheme.errorContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val border = when {
        selected -> MaterialTheme.colorScheme.primary
        matched -> Color(0xFF1387C0)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val scale = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }

    // 配对成功：放大回弹
    LaunchedEffect(matched) {
        if (matched) {
            scale.animateTo(1.15f, spring(dampingRatio = 0.35f, stiffness = 600f))
            scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
        }
    }

    // 配对失败：左右抖动
    LaunchedEffect(wrong) {
        if (wrong) {
            try {
                shake.animateTo(-10f, spring(dampingRatio = 0.3f, stiffness = 800f))
                shake.animateTo(10f, spring(dampingRatio = 0.3f, stiffness = 800f))
                shake.animateTo(-7f, spring(dampingRatio = 0.3f, stiffness = 800f))
                shake.animateTo(7f, spring(dampingRatio = 0.3f, stiffness = 800f))
                shake.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 800f))
            } finally {
                shake.snapTo(0f)
            }
        }
    }

    Card(
        onClick = onClick,
        enabled = !matched,
        modifier = Modifier
            .scale(scale.value)
            .offset(x = shake.value.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 18.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (matched) FontWeight.Normal else FontWeight.Medium,
            color = if (matched) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
