package com.voicedrop.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.voicedrop.android.audio.AudioItem
import com.voicedrop.android.audio.AudioPlayer
import com.voicedrop.android.audio.AudioRecorder
import com.voicedrop.android.audio.MicSource
import com.voicedrop.android.audio.SpeechTranscriber
import com.voicedrop.android.ui.theme.*
import com.voicedrop.android.util.LocaleManager
import com.voicedrop.android.util.LocalAppStrings
import com.voicedrop.android.audio.VoskTranscriber
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDropScreen(
    recorder: AudioRecorder,
    player: AudioPlayer,
    transcriber: SpeechTranscriber,
    voskTranscriber: VoskTranscriber,
    localeManager: LocaleManager,
    hasRecordPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val isRecording by recorder.isRecording.collectAsState()
    val recordingDurationMs by recorder.recordingDurationMs.collectAsState()
    val amplitude by recorder.amplitude.collectAsState()
    val latestRecording by recorder.latestRecording.collectAsState()
    val selectedMicSource by recorder.selectedMicSource.collectAsState()
    val strings = LocalAppStrings.current
    val currentLanguage by localeManager.currentLanguage.collectAsState()

    var showMicMenu by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var recentList by remember { mutableStateOf<List<AudioItem>>(emptyList()) }

    // Load recent recordings whenever history is shown or latest recording changes
    LaunchedEffect(latestRecording, showHistorySheet) {
        recentList = recorder.getRecentRecordings()
    }

    // Record button pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "record_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRecording) 1.15f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Scaffold(
        containerColor = StudioDarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDarkBackground)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎙️",
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "VoiceDrop",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(VoiceDropPurple.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "v1.2.3",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = VoiceDropPurpleLight,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                            Text(
                                text = strings.appSubtitle,
                                style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Language Switcher Button: [ 🌐 EN ] / [ 🌐 RU ]
                        OutlinedButton(
                            onClick = { localeManager.toggleLanguage() },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(StudioCardBorder)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = StudioCardBackground,
                                contentColor = TextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "${currentLanguage.flag} ${currentLanguage.name}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            )
                        }

                        // Recordings History Button
                        IconButton(
                            onClick = { showHistorySheet = true },
                            modifier = Modifier
                                .size(42.dp)
                                .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(StudioCardBackground)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (recentList.isNotEmpty()) {
                                        Badge(
                                            containerColor = VoiceDropPurple,
                                            contentColor = Color.White
                                        ) {
                                            Text("${recentList.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = strings.historyTooltip,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // App Minimization Button (Свернуть приложение)
                        val activity = LocalContext.current as? Activity
                        IconButton(
                            onClick = { activity?.moveTaskToBack(true) },
                            modifier = Modifier
                                .size(42.dp)
                                .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(StudioCardBackground)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = strings.minimizeTooltip,
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Permission Banner (if not granted)
            if (!hasRecordPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = RecordRed.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicOff,
                            contentDescription = strings.permissionTitle,
                            tint = RecordRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.permissionTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RecordRed
                                )
                            )
                            Text(
                                text = strings.permissionDesc,
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                            )
                        }
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = RecordRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(strings.permissionAllow, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Hardware Mic Selector Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable(enabled = !isRecording) { showMicMenu = true }
                    .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = StudioCardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = strings.audioInputSource,
                            tint = VoiceDropPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = strings.audioInputSource,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = selectedMicSource.getLocalizedLabel(strings),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = strings.audioInputSource,
                        tint = TextSecondary
                    )

                    DropdownMenu(
                        expanded = showMicMenu,
                        onDismissRequest = { showMicMenu = false },
                        modifier = Modifier.background(StudioCardBackground)
                    ) {
                        MicSource.values().forEach { source ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = source.getLocalizedLabel(strings),
                                        color = if (source == selectedMicSource) VoiceDropPurple else TextPrimary
                                    )
                                },
                                onClick = {
                                    recorder.setMicSource(source)
                                    showMicMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Recording Center Stage
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioCardBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = StudioCardBackground),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Recording Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isRecording) RecordRed.copy(alpha = 0.2f)
                                else StudioSurfaceVariant
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) RecordRed else TextMuted)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRecording) strings.statusRecording else strings.statusStudioReady,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isRecording) RecordRed else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Timer (matches VoiceDrop macOS 00:04.2 format)
                    val totalSec = recordingDurationMs / 1000
                    val minutes = totalSec / 60
                    val seconds = totalSec % 60
                    val tenths = (recordingDurationMs % 1000) / 100
                    val timerText = String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)

                    Text(
                        text = timerText,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 44.sp,
                            color = if (isRecording) RecordRed else TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Live Waveform Visualizer
                    WaveformVisualizer(
                        isRecording = isRecording,
                        amplitude = amplitude,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    // Big Record Button
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        // Glowing pulse ring when recording
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(RecordRedGlow)
                            )
                        }

                        // Main Button
                        val buttonBrush = if (isRecording) {
                            Brush.linearGradient(listOf(RecordRed, Color(0xFFB91C1C)))
                        } else {
                            Brush.linearGradient(listOf(VoiceDropPurple, VoiceDropPurpleDark))
                        }

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(buttonBrush)
                                .clickable {
                                    if (!hasRecordPermission) {
                                        onRequestPermission()
                                    } else {
                                        if (isRecording) {
                                            recorder.stopRecording()
                                            transcriber.stopLiveRecordingCapture { capturedText ->
                                                if (capturedText.isNotBlank()) {
                                                    recorder.latestRecording.value?.let { item ->
                                                        recorder.saveTranscription(item, capturedText)
                                                    }
                                                }
                                            }
                                        } else {
                                            recorder.startRecording()
                                            transcriber.startLiveRecordingCapture()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) strings.tapToStop else strings.tapToRecord,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isRecording) strings.tapToStop else strings.tapToRecord,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Saved Recording Card (Shows current / latest recording ready to copy and transcribe)
            latestRecording?.let { item ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.latestRecording,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    RecordingCard(
                        item = item,
                        player = player,
                        transcriber = transcriber,
                        voskTranscriber = voskTranscriber,
                        onSaveTranscription = { audioItem, text ->
                            recorder.saveTranscription(audioItem, text)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Recordings History Bottom Sheet
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = StudioDarkBackground,
            dragHandle = { BottomSheetDefaults.DragHandle(color = StudioCardBorder) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.historyTitle,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = strings.historyFiles(recentList.size),
                        style = MaterialTheme.typography.labelMedium.copy(color = TextMuted)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (recentList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.historyEmpty,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentList, key = { it.baseName }) { item ->
                            RecordingCard(
                                item = item,
                                player = player,
                                transcriber = transcriber,
                                voskTranscriber = voskTranscriber,
                                onSaveTranscription = { audioItem, text ->
                                    recorder.saveTranscription(audioItem, text)
                                    recentList = recorder.getRecentRecordings()
                                },
                                onDelete = {
                                    item.mp3File.delete()
                                    item.wavFile?.delete()
                                    java.io.File(item.mp3File.parentFile, "${item.baseName}.txt").delete()
                                    recentList = recorder.getRecentRecordings()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
