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
import androidx.compose.foundation.layout.width
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
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.english.data.WordRepository
import com.example.english.guard.EnGuardService
import com.example.english.guard.GuardAppPickerActivity
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
            var guardThreshold by remember { mutableIntStateOf(guardStore.thresholdMinutes) }
            var guardPackages by remember { mutableStateOf(guardStore.packages) }
            var serviceEnabled by remember { mutableStateOf(EnGuardService.isServiceEnabled(context)) }

            // 从系统无障碍设置页返回时刷新服务状态
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                serviceEnabled = EnGuardService.isServiceEnabled(context)
            }

            fun updateThreshold(delta: Int) {
                val next = (guardThreshold + delta)
                    .coerceIn(
                        GuardStateStore.MIN_THRESHOLD_MINUTES,
                        GuardStateStore.MAX_THRESHOLD_MINUTES
                    )
                guardThreshold = next
                guardStore.thresholdMinutes = next
            }

            fun addApp(pkg: String) {
                guardPackages = guardPackages + pkg
                guardStore.addPackage(pkg)
            }

            fun removeApp(pkg: String) {
                guardPackages = guardPackages - pkg
                guardStore.removePackage(pkg)
            }

            val pickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val pkg = result.data?.getStringExtra(
                        GuardAppPickerActivity.RESULT_EXTRA_PACKAGE
                    )
                    if (!pkg.isNullOrBlank()) addApp(pkg)
                }
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
                        text = "娱乐管控",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "开启无障碍服务后，在被管控的应用中累计使用达到触发时长，就会自动弹出今天找到的生词，依次「说意思 → 拼写 → 读一遍」，全部完成才解锁继续使用。复习数量按上方「每日发现不认识单词数量」取值。",
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

                    Text(
                        text = "触发时长",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedIconButton(
                            onClick = { updateThreshold(-1) },
                            enabled = guardThreshold > GuardStateStore.MIN_THRESHOLD_MINUTES
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "减少",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(20.dp))
                        Text(
                            text = "$guardThreshold 分钟",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.size(20.dp))
                        OutlinedIconButton(
                            onClick = { updateThreshold(1) },
                            enabled = guardThreshold < GuardStateStore.MAX_THRESHOLD_MINUTES
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "增加",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "已管控应用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (guardPackages.isEmpty()) {
                        Text(
                            text = "暂未添加任何应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        guardPackages.sorted().forEach { pkg ->
                            GuardPackageRow(
                                pkg = pkg,
                                onRemove = { removeApp(pkg) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val intent = Intent(context, GuardAppPickerActivity::class.java).apply {
                                putStringArrayListExtra(
                                    GuardAppPickerActivity.EXTRA_SELECTED_PACKAGES,
                                    ArrayList(guardPackages)
                                )
                            }
                            pickerLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("添加管控应用")
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
                            onClick = { EnGuardService.openAccessibilitySettings(context) }
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

private data class AppItemInfo(val label: String, val icon: Drawable)

/**
 * 设置页「已管控应用」中的一行：应用图标 + 名称 + 包名 + 删除按钮。
 * 包名查不到时（应用已卸载）展示包名原文 + 占位符，不报错。
 */
@Composable
private fun GuardPackageRow(pkg: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    val info = remember(pkg) {
        try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            AppItemInfo(
                label = pm.getApplicationLabel(ai).toString(),
                icon = pm.getApplicationIcon(ai)
            )
        } catch (_: Exception) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (info != null) {
            Image(
                bitmap = remember(pkg) { info.icon.toBitmap(80, 80).asImageBitmap() },
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info?.label ?: pkg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "移除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
