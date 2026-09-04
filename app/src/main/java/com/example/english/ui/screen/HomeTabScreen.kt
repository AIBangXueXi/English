package com.example.english.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.english.data.DailyTask
import com.example.english.data.DailyTaskStore
import com.example.english.data.MO_ER_TARGET_SECONDS
import com.example.english.data.WordRepository

/**
 * 首页 Tab：今日任务列表 + 历史完成情况 + 统计。
 * 每次切回本 Tab 会重新组合并刷新数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabScreen(
    onOpenStudy: () -> Unit,
    onWordTaskClick: () -> Unit = {},
    onMoErTaskClick: () -> Unit = {},
    onDictationTaskClick: () -> Unit = {},
    onPronunciationTaskClick: () -> Unit = {},
    onMeaningTaskClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { WordRepository(context) }
    val taskStore = remember { DailyTaskStore(context) }

    var unknownLimit by remember { mutableIntStateOf(repository.getDailyUnknownLimit()) }
    var knownCount by remember { mutableIntStateOf(0) }
    var unknownCount by remember { mutableIntStateOf(0) }
    var todayTask by remember { mutableStateOf(DailyTask()) }
    var history by remember { mutableStateOf<List<Pair<String, DailyTask>>>(emptyList()) }

    LaunchedEffect(Unit) {
        unknownLimit = repository.getDailyUnknownLimit()
        knownCount = repository.getKnownCount()
        unknownCount = repository.getUnknownCount()
        todayTask = taskStore.getToday()
        history = taskStore.getHistory(7)
    }

    val done = todayTask.completedCount(unknownLimit)
    val total = todayTask.totalTasks

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("首页") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ===== 今日任务 =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "今",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "今日任务",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "完成 $done / $total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (done >= total) {
                            Text(
                                text = "🎉 全部完成",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = Color(0xFF4CAF50),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TaskItem(
                        title = "背单词 · 发现 $unknownLimit 个不认识",
                        detail = "${todayTask.unknownFound} / $unknownLimit",
                        done = todayTask.unknownFound >= unknownLimit,
                        progress = if (unknownLimit == 0) 1f
                        else (todayTask.unknownFound.toFloat() / unknownLimit).coerceIn(0f, 1f),
                        onClick = onWordTaskClick
                    )
                    TaskItem(
                        title = "单词意思训练一遍",
                        detail = if (todayTask.meaningDone) "已完成" else "未完成",
                        done = todayTask.meaningDone,
                        progress = if (todayTask.meaningDone) 1f else 0f,
                        onClick = onMeaningTaskClick
                    )
                    TaskItem(
                        title = "发音训练一遍",
                        detail = if (todayTask.pronunciationDone) "已完成" else "未完成",
                        done = todayTask.pronunciationDone,
                        progress = if (todayTask.pronunciationDone) 1f else 0f,
                        onClick = onPronunciationTaskClick
                    )
                    TaskItem(
                        title = "默写单词训练一遍",
                        detail = if (todayTask.dictationDone) "已完成" else "未完成",
                        done = todayTask.dictationDone,
                        progress = if (todayTask.dictationDone) 1f else 0f,
                        onClick = onDictationTaskClick
                    )
                    TaskItem(
                        title = "磨耳训练 30 分钟",
                        detail = formatSeconds(todayTask.moErSeconds) + " / 30 分钟",
                        done = todayTask.moErSeconds >= MO_ER_TARGET_SECONDS,
                        progress = (todayTask.moErSeconds.toFloat() / MO_ER_TARGET_SECONDS).coerceIn(0f, 1f),
                        onClick = onMoErTaskClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 统计 =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "学习统计",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip(label = "认识", value = knownCount, color = Color(0xFF4CAF50))
                        StatChip(label = "不认识", value = unknownCount, color = Color(0xFF9E9E9E))
                        StatChip(label = "今日完成", value = done, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 以前完成的情况（近 7 天） =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "最近完成情况",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    history.forEachIndexed { i, (date, task) ->
                        val c = task.completedCount(unknownLimit)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDate(date, i == 0),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            LinearProgressIndicator(
                                progress = { if (task.totalTasks == 0) 0f else c.toFloat() / task.totalTasks },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp),
                                color = if (c >= task.totalTasks) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "$c/${task.totalTasks}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (c >= task.totalTasks) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(40.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onOpenStudy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.School,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("去学习", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskItem(
    title: String,
    detail: String,
    done: Boolean,
    progress: Float,
    onClick: () -> Unit = {}
) {
    val accent = if (done) Color(0xFF4CAF50) else Color(0xFFF44336)
    Card(
        onClick = { if (!done) onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 完成标记：完成=绿，未完成=红
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = accent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (done) FontWeight.Normal else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (done) accent.copy(alpha = 0.9f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatSeconds(total: Int): String {
    val m = total / 60
    val s = total % 60
    return if (m > 0) "${m}分${s}秒" else "${s}秒"
}

private fun formatDate(key: String, isToday: Boolean): String {
    if (isToday) return "今天"
    val parts = key.split("-")
    return if (parts.size == 3) "${parts[1]}/${parts[2]}" else key
}
