package com.example.english.ui.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import com.example.english.data.WordRepository
import com.example.english.guard.DouyinGuardService
import com.example.english.guard.GuardLockActivity
import com.example.english.guard.GuardStateStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember { WordRepository(context) }
    var dailyLimit by remember { mutableIntStateOf(repository.getDailyUnknownLimit()) }

    fun updateLimit(delta: Int) {
        val next = (dailyLimit + delta).coerceIn(
            WordRepository.DAILY_LIMIT_MIN,
            WordRepository.DAILY_LIMIT_MAX
        )
        dailyLimit = next
        repository.setDailyUnknownLimit(next)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

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
                        text = "每日发现不认识单词数量",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "在背单词过程中，把不认识的单词标记为“不认识”。每天标记的数量达到该值后，会提示今日学习任务完成。可在 ${WordRepository.DAILY_LIMIT_MIN} ~ ${WordRepository.DAILY_LIMIT_MAX} 个之间调整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedIconButton(
                            onClick = { updateLimit(-1) },
                            enabled = dailyLimit > WordRepository.DAILY_LIMIT_MIN
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "减少",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(24.dp))
                        Text(
                            text = "$dailyLimit 个",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.size(24.dp))
                        OutlinedIconButton(
                            onClick = { updateLimit(1) },
                            enabled = dailyLimit < WordRepository.DAILY_LIMIT_MAX
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "增加",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val guardStore = remember { GuardStateStore(context) }
            var guardEnabled by remember { mutableStateOf(guardStore.enabled) }
            var videoThreshold by remember { mutableIntStateOf(guardStore.threshold) }
            var serviceEnabled by remember { mutableStateOf(DouyinGuardService.isServiceEnabled(context)) }

            // 从系统无障碍设置页返回时刷新状态
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                serviceEnabled = DouyinGuardService.isServiceEnabled(context)
            }

            fun updateThreshold(delta: Int) {
                val next = (videoThreshold + delta)
                    .coerceIn(GuardStateStore.MIN_THRESHOLD, GuardStateStore.MAX_THRESHOLD)
                videoThreshold = next
                guardStore.threshold = next
            }

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
                        text = "刷视频管控",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "开启无障碍服务后，在抖音里每刷满设定个数的视频，就会自动弹出今天找到的生词，依次「说意思 → 拼写 → 读一遍」，全部完成才解锁继续刷。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "开启管控",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = guardEnabled,
                            onCheckedChange = {
                                guardEnabled = it
                                guardStore.enabled = it
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedIconButton(
                            onClick = { updateThreshold(-1) },
                            enabled = videoThreshold > GuardStateStore.MIN_THRESHOLD
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "减少",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(20.dp))
                        Text(
                            text = "$videoThreshold 个视频",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.size(20.dp))
                        OutlinedIconButton(
                            onClick = { updateThreshold(1) },
                            enabled = videoThreshold < GuardStateStore.MAX_THRESHOLD
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "增加",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (serviceEnabled) {
                        Text(
                            text = "无障碍服务已开启，管控生效中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "无障碍服务未开启，管控不会生效（OPPO 上请在电池优化中把本应用设为无限制，并允许自启动/后台运行）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = { DouyinGuardService.openAccessibilitySettings(context) }
                        ) {
                            Text("去开启无障碍服务")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(context, GuardLockActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("预览复习锁屏", fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
