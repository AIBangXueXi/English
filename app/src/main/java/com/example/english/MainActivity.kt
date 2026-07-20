package com.example.english

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.english.data.WordViewModel
import com.example.english.speech.SpeechService
import com.example.english.ui.screen.HomeScreen
import com.example.english.ui.screen.WordScreen
import com.example.english.ui.theme.EnglishTheme

class MainActivity : ComponentActivity() {
    private lateinit var speechService: SpeechService

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "需要录音权限才能使用语音识别功能", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechService = SpeechService(this)
        enableEdgeToEdge()

        setContent {
            EnglishTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                when (val screen = currentScreen) {
                    is Screen.Home -> HomeScreen(
                        onWordClick = {
                            ensureAudioPermission {
                                currentScreen = Screen.WordPage
                            }
                        }
                    )
                    is Screen.WordPage -> {
                        val wordViewModel: WordViewModel = viewModel()
                        WordScreen(
                            viewModel = wordViewModel,
                            speechService = speechService,
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                }
            }
        }
    }

    private fun ensureAudioPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

sealed class Screen {
    data object Home : Screen()
    data object WordPage : Screen()
}
