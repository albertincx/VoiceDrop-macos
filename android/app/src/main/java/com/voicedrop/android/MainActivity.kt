package com.voicedrop.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.voicedrop.android.audio.AudioPlayer
import com.voicedrop.android.audio.AudioRecorder
import com.voicedrop.android.audio.SpeechTranscriber
import com.voicedrop.android.ui.VoiceDropScreen
import com.voicedrop.android.ui.theme.StudioDarkBackground
import com.voicedrop.android.ui.theme.VoiceDropTheme
import com.voicedrop.android.util.LocaleManager
import com.voicedrop.android.util.LocalAppStrings

import com.voicedrop.android.audio.VoskTranscriber

class MainActivity : ComponentActivity() {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var speechTranscriber: SpeechTranscriber
    private lateinit var voskTranscriber: VoskTranscriber
    private lateinit var localeManager: LocaleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        localeManager = LocaleManager(this)
        audioRecorder = AudioRecorder(this)
        audioPlayer = AudioPlayer(this)
        speechTranscriber = SpeechTranscriber(this)
        voskTranscriber = VoskTranscriber(this)

        setContent {
            val currentStrings by localeManager.strings.collectAsState()

            CompositionLocalProvider(LocalAppStrings provides currentStrings) {
                VoiceDropTheme {
                    var hasRecordPermission by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        hasRecordPermission = isGranted
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = StudioDarkBackground
                    ) {
                        VoiceDropScreen(
                            recorder = audioRecorder,
                            player = audioPlayer,
                            transcriber = speechTranscriber,
                            voskTranscriber = voskTranscriber,
                            localeManager = localeManager,
                            hasRecordPermission = hasRecordPermission,
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.stopRecording()
        audioPlayer.release()
        speechTranscriber.destroy()
        voskTranscriber.destroy()
    }
}
