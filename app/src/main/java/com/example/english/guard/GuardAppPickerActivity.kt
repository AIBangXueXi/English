package com.example.english.guard

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.english.ui.theme.EnglishTheme

/**
 * 让用户从已安装的可启动应用中选一个加入管控列表。
 *
 * 启动：[GuardAppPickerActivity.start] → 启动本 Activity
 * 结果：被选中的应用包名通过 Intent extras (`"package"`) 返回。
 */
class GuardAppPickerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SELECTED_PACKAGES = "selected"
        const val RESULT_EXTRA_PACKAGE = "package"
    }

    private var selected: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = intent.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES)?.toSet() ?: emptySet()
        setContent {
            EnglishTheme {
                GuardAppPickerScreen(
                    selectedPackages = selected,
                    onPick = { pkg ->
                        val data = Intent().putExtra(RESULT_EXTRA_PACKAGE, pkg)
                        setResult(Activity.RESULT_OK, data)
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

private data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardAppPickerScreen(
    selectedPackages: Set<String>,
    onPick: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val selfPkg = context.packageName

    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri: ResolveInfo ->
                val pkg = ri.activityInfo.packageName
                // 过滤掉自己（自己管控自己没意义）和系统权限不足的应用
                if (pkg == selfPkg) return@mapNotNull null
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon = ri.loadIcon(pm)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加管控应用") },
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
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索应用名称或包名") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有匹配的应用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppPickerItem(
                            app = app,
                            isSelected = app.packageName in selectedPackages,
                            onClick = { onPick(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerItem(app: AppInfo, isSelected: Boolean, onClick: () -> Unit) {
    val iconBitmap = remember(app.packageName) {
        app.icon.toBitmap(width = 96, height = 96).asImageBitmap()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSelected, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "已添加",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}