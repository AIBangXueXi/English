package com.example.english.ui.screen

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.english.data.WordViewModel
import com.example.english.data.entity.KnownWord
import com.example.english.data.entity.UnknownWord

private enum class LibraryTable { KNOWN, UNKNOWN }

private data class PendingDelete(
    val table: LibraryTable,
    val id: Long,
    val word: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryManagementScreen(
    viewModel: WordViewModel,
    onBack: () -> Unit,
    onSync: () -> Unit = {},
    isSyncing: Boolean = false
) {
    val knownWords by viewModel.libraryKnownWords.collectAsStateWithLifecycle()
    val unknownWords by viewModel.libraryUnknownWords.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var knownExpanded by remember { mutableStateOf(false) }
    var unknownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredKnown = remember(knownWords, searchQuery) {
        if (searchQuery.isBlank()) knownWords
        else knownWords.filter { matchWord(it.word, it.phonetic, it.meaning, searchQuery) }
    }
    val filteredUnknown = remember(unknownWords, searchQuery) {
        if (searchQuery.isBlank()) unknownWords
        else unknownWords.filter { matchWord(it.word, it.phonetic, it.meaning, searchQuery) }
    }
    // 搜索时自动展开两个分组，方便直接看到结果
    val effectiveKnownExpanded = knownExpanded || searchQuery.isNotBlank()
    val effectiveUnknownExpanded = unknownExpanded || searchQuery.isNotBlank()

    LaunchedEffect(Unit) {
        viewModel.refreshLibrary()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本地词库管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSync, enabled = !isSyncing) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "同步",
                            tint = if (isSyncing)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "已掌握",
                        count = knownWords.size,
                        icon = Icons.Rounded.CheckCircle,
                        tint = Color(0xFF50B86C)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "待复习",
                        count = unknownWords.size,
                        icon = Icons.Rounded.Refresh,
                        tint = Color(0xFFE8913A)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索单词或释义") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "清空")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                SectionHeader(
                    title = "已掌握单词",
                    count = filteredKnown.size,
                    expanded = effectiveKnownExpanded,
                    onToggle = { knownExpanded = !knownExpanded }
                )
            }
            if (effectiveKnownExpanded) {
                if (filteredKnown.isEmpty()) {
                    item {
                        EmptyHint(
                            if (searchQuery.isBlank()) "暂无已掌握单词" else "未找到匹配的已掌握单词"
                        )
                    }
                } else {
                    items(filteredKnown, key = { "known_${it.id}" }) { word ->
                        WordRow(
                            word = word.word,
                            phonetic = word.phonetic,
                            meaning = word.meaning,
                            onDelete = {
                                pendingDelete = PendingDelete(
                                    LibraryTable.KNOWN, word.id, word.word
                                )
                            }
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "待复习单词",
                    count = filteredUnknown.size,
                    expanded = effectiveUnknownExpanded,
                    onToggle = { unknownExpanded = !unknownExpanded }
                )
            }
            if (effectiveUnknownExpanded) {
                if (filteredUnknown.isEmpty()) {
                    item {
                        EmptyHint(
                            if (searchQuery.isBlank()) "暂无待复习单词" else "未找到匹配的待复习单词"
                        )
                    }
                } else {
                    items(filteredUnknown, key = { "unknown_${it.id}" }) { word ->
                        WordRow(
                            word = word.word,
                            phonetic = word.phonetic,
                            meaning = word.meaning,
                            stage = word.stage,
                            nextReviewTime = word.nextReviewTime,
                            onDelete = {
                                pendingDelete = PendingDelete(
                                    LibraryTable.UNKNOWN, word.id, word.word
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (pendingDelete != null) {
        val target = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除单词 \"${target.word}\" 吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (target.table) {
                            LibraryTable.KNOWN -> viewModel.deleteKnownWord(target.id)
                            LibraryTable.UNKNOWN -> viewModel.deleteUnknownWord(target.id)
                        }
                        pendingDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
}

/** 按单词 / 音标 / 释义做不区分大小写的子串匹配。 */
private fun matchWord(word: String, phonetic: String, meaning: String, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return word.lowercase().contains(q) ||
        phonetic.lowercase().contains(q) ||
        meaning.lowercase().contains(q)
}

/** 把下次复习时间戳格式化为分级的人类可读文本。 */
private fun formatNextReviewTime(nextReviewTime: Long): String {
    if (nextReviewTime <= 0) return "现在可复习"
    val delta = nextReviewTime - System.currentTimeMillis()
    if (delta <= 0) return "现在可复习"
    val minutes = delta / (60 * 1000)
    val hours = delta / (60 * 60 * 1000)
    val days = delta / (24 * 60 * 60 * 1000)
    return when {
        days >= 1 -> "${days}天后"
        hours >= 1 -> "${hours}小时后"
        else -> "${if (minutes < 1) 1 else minutes}分钟后"
    }
}

@Composable
private fun WordRow(
    word: String,
    phonetic: String,
    meaning: String,
    onDelete: () -> Unit,
    stage: Int? = null,
    nextReviewTime: Long? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (stage != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "第 ${stage + 1} 阶段",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (nextReviewTime != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = formatNextReviewTime(nextReviewTime),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (phonetic.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = phonetic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                if (meaning.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = meaning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    icon: ImageVector,
    tint: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
