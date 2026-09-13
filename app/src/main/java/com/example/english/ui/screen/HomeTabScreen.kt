package com.example.english.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.english.data.DailyTask
import com.example.english.data.DailyTaskStore
import com.example.english.data.MO_ER_TARGET_SECONDS
import com.example.english.data.TrainingProgressStore
import com.example.english.data.WordRepository
import com.example.english.guard.GuardLockActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页 Tab：今日任务列表 + 历史完成情况 + 统计。
 * 每次切回本 Tab 会重新组合并刷新数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabScreen(
    onOpenStudy: () -> Unit,
    onWordTaskClick: () -> Unit = {},
    onKnownWordsClick: () -> Unit = {},
    onUnknownWordsClick: () -> Unit = {},
    onMoErTaskClick: () -> Unit = {},
    onDictationTaskClick: () -> Unit = {},
    onPronunciationTaskClick: () -> Unit = {},
    onMeaningTaskClick: () -> Unit = {},
    onMatchingTaskClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { WordRepository(context) }
    val taskStore = remember { DailyTaskStore(context) }
    val progressStore = remember { TrainingProgressStore(context) }

    var unknownLimit by remember { mutableIntStateOf(repository.getDailyUnknownLimit()) }
    var knownCount by remember { mutableIntStateOf(0) }
    var unknownCount by remember { mutableIntStateOf(0) }
    var todayTask by remember { mutableStateOf(DailyTask()) }
    var weekData by remember { mutableStateOf<List<List<Pair<String, DailyTask>>>>(emptyList()) }
    var meaningPos by remember { mutableIntStateOf(0) }
    var pronunciationPos by remember { mutableIntStateOf(0) }
    var dictationPos by remember { mutableIntStateOf(0) }
    var matchingPos by remember { mutableIntStateOf(0) }

    // 每次回到前台都 +1（含 App 长期后台后跨天回到前台），驱动下方数据重新加载。
    // 否则停在首页过夜，第二天看到的仍是昨天的任务进度。
    var reloadToken by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { WEEK_COUNT })
    LifecycleStartEffect(Unit) {
        reloadToken++
        onStopOrDispose { }
    }

    LaunchedEffect(reloadToken) {
        unknownLimit = repository.getDailyUnknownLimit()
        knownCount = repository.getKnownCount()
        unknownCount = repository.getUnknownCount()
        todayTask = taskStore.getToday()
        weekData = List(WEEK_COUNT) { taskStore.getWeekDays(it) }
        meaningPos = trainingPosition(progressStore, "Meaning", todayTask.meaningDone, unknownCount)
        pronunciationPos = trainingPosition(progressStore, "Pronunciation", todayTask.pronunciationDone, unknownCount)
        dictationPos = trainingPosition(progressStore, "Dictation", todayTask.dictationDone, unknownCount)
        matchingPos = trainingPosition(progressStore, "Matching", todayTask.matchingDone, unknownCount)
    }

    val done = todayTask.completedCount(unknownLimit)
    val total = todayTask.totalTasks
    // 其他任务需先完成「背单词发现不认识」后才能解锁
    val wordsFound = todayTask.unknownFound >= unknownLimit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("首页") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, GuardLockActivity::class.java))
                    }) {
                        Icon(Icons.Rounded.Lock, contentDescription = "娱乐管控")
                    }
                }
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
                        StatChip(
                            label = "认识",
                            value = knownCount,
                            color = Color(0xFF1387C0),
                            onClick = onKnownWordsClick
                        )
                        StatChip(
                            label = "不认识",
                            value = unknownCount,
                            color = Color(0xFF9E9E9E),
                            onClick = onUnknownWordsClick
                        )
                        StatChip(label = "今日完成", value = done, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "今日任务",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    // 例如：9月7日 周一
                                    text = remember { formatToday() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                                    color = Color(0xFF1387C0)
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
                        color = Color(0xFF1387C0),
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
                        detail = "$meaningPos / $unknownCount",
                        done = todayTask.meaningDone,
                        progress = if (unknownCount == 0) 1f else (meaningPos.toFloat() / unknownCount).coerceIn(0f, 1f),
                        locked = !wordsFound,
                        onClick = onMeaningTaskClick
                    )
                    TaskItem(
                        title = "发音训练一遍",
                        detail = "$pronunciationPos / $unknownCount",
                        done = todayTask.pronunciationDone,
                        progress = if (unknownCount == 0) 1f else (pronunciationPos.toFloat() / unknownCount).coerceIn(0f, 1f),
                        locked = !wordsFound,
                        onClick = onPronunciationTaskClick
                    )
                    TaskItem(
                        title = "默写单词训练一遍",
                        detail = "$dictationPos / $unknownCount",
                        done = todayTask.dictationDone,
                        progress = if (unknownCount == 0) 1f else (dictationPos.toFloat() / unknownCount).coerceIn(0f, 1f),
                        locked = !wordsFound,
                        onClick = onDictationTaskClick
                    )
                    TaskItem(
                        title = "单词对对碰训练一遍",
                        detail = "$matchingPos / $unknownCount",
                        done = todayTask.matchingDone,
                        progress = if (unknownCount == 0) 1f else (matchingPos.toFloat() / unknownCount).coerceIn(0f, 1f),
                        locked = !wordsFound,
                        onClick = onMatchingTaskClick
                    )
                    TaskItem(
                        title = "磨耳训练 15 分钟",
                        detail = "${todayTask.moErSeconds / 60} / 15 分钟",
                        done = todayTask.moErSeconds >= MO_ER_TARGET_SECONDS,
                        progress = (todayTask.moErSeconds.toFloat() / MO_ER_TARGET_SECONDS).coerceIn(0f, 1f),
                        locked = !wordsFound,
                        onClick = onMoErTaskClick
                    )
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
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) { page ->
                        val days = weekData.getOrNull(page).orEmpty()
                        Column {
                            Text(
                                text = weekLabel(page),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            days.forEachIndexed { d, (date, task) ->
                                val c = task.completedCount(unknownLimit)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (date == taskStore.todayKey()) "今天" else WEEKDAY_LABELS[d],
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.width(44.dp)
                                    )
                                    LinearProgressIndicator(
                                        progress = { if (task.totalTasks == 0) 0f else c.toFloat() / task.totalTasks },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp),
                                        color = if (c >= task.totalTasks) Color(0xFF1387C0)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "$c/${task.totalTasks}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (c >= task.totalTasks) Color(0xFF1387C0)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(44.dp)
                                    )
                                }
                            }
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
    locked: Boolean = false,
    onClick: () -> Unit = {}
) {
    val accent = when {
        done -> Color(0xFF1387C0)
        locked -> Color(0xFFBDBDBD)
        else -> Color(0xFFF44336)
    }
    Card(
        onClick = { if (!done && !locked) onClick() },
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
                    } else if (locked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
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
                    color = when {
                        done -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        locked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (done || locked) FontWeight.Normal else FontWeight.Medium
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
                text = if (locked) "先背单词" else detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (done) accent.copy(alpha = 0.9f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: Int,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) {
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    } else {
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    }
    Column(
        modifier = clickModifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

/**
 * 计算某个训练模式“当前练习到的位置”。
 * 已完成 → 等于总数；已开始 → 保存的下标 + 1（第几个词）；未开始 → 0。
 */
private fun trainingPosition(
    store: TrainingProgressStore,
    modeKey: String,
    done: Boolean,
    total: Int
): Int {
    if (total == 0) return 0
    if (done) return total
    if (!store.isStarted(modeKey)) return 0
    return (store.getIndex(modeKey) + 1).coerceIn(1, total)
}

/** 展示的周数：本周 + 前四周 */
private const val WEEK_COUNT = 5

private val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun weekLabel(index: Int): String = when (index) {
    0 -> "本周"
    1 -> "上周"
    else -> "前${index}周"
}

/** 「9月7日 周一」格式的今日日期（中文 locale） */
private fun formatToday(): String =
    SimpleDateFormat("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE).format(Date())
