package com.voicedrop.android.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicedrop.android.audio.AudioItem
import com.voicedrop.android.audio.AudioPlayer
import com.voicedrop.android.audio.SpeechTranscriber
import com.voicedrop.android.ui.theme.*
import com.voicedrop.android.util.ClipboardHelper
import com.voicedrop.android.util.LocalAppStrings
import kotlinx.coroutines.delay
import java.util.Locale

import com.voicedrop.android.audio.VoskTranscriber
import kotlinx.coroutines.launch

@Composable
fun RecordingCard(
    item: AudioItem,
    player: AudioPlayer,
    transcriber: SpeechTranscriber,
    voskTranscriber: VoskTranscriber,
    modifier: Modifier = Modifier,
    onSaveTranscription: ((AudioItem, String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()

    var isMp3Copied by remember { mutableStateOf(false) }
    var isWavCopied by remember { mutableStateOf(false) }
    var isTextCopied by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }

    var transcriptionText by remember(item.transcription) { mutableStateOf(item.transcription) }
    val isTranscribing by transcriber.isTranscribing.collectAsState()
    val transcribingBaseName by transcriber.transcribingBaseName.collectAsState()
    val isThisItemTranscribing = isTranscribing && transcribingBaseName == item.baseName
    val useLocalModel by transcriber.useLocalModel.collectAsState()

    var isLocalTranscribing by remember { mutableStateOf(false) }
    val isExtractingModel by voskTranscriber.isExtracting.collectAsState()
    val isBusyTranscribing = isThisItemTranscribing || isLocalTranscribing || isExtractingModel

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull()?.trim() ?: ""
            if (text.isNotEmpty()) {
                transcriptionText = text
                onSaveTranscription?.invoke(item, text)
                Toast.makeText(context, strings.toastTextRecognized, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, strings.sttNoSpeechDetected, Toast.LENGTH_SHORT).show()
            }
        } else if (result.resultCode != Activity.RESULT_CANCELED) {
            Toast.makeText(context, strings.sttRecognitionError, Toast.LENGTH_SHORT).show()
        }
    }

    val launchRecognition = {
        if (useLocalModel) {
            // 🧠 100% ON-DEVICE VOSK RECOGNITION DIRECTLY FROM RECORDED WAV FILE
            // NO GOOGLE DIALOG, NO GOOGLE CRASH, NO NETWORK NEEDED!
            val targetWav = item.wavFile ?: item.mp3File
            if (targetWav != null && targetWav.exists()) {
                isLocalTranscribing = true
                coroutineScope.launch {
                    try {
                        val isRu = strings.recognizeAudio == "Распознать аудио"
                        val recognized = voskTranscriber.transcribeWav(targetWav, isRussian = isRu)
                        if (recognized.isNotBlank()) {
                            transcriptionText = recognized
                            onSaveTranscription?.invoke(item, recognized)
                            Toast.makeText(context, strings.toastTextRecognized, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, strings.sttNoSpeechDetected, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Vosk error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isLocalTranscribing = false
                    }
                }
            } else {
                Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Google Cloud Recognition via System Activity Dialog
            val langTag = if (strings.recognizeAudio == "Распознать аудио") "ru-RU" else "en-US"
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, strings.sttPrompt)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, strings.sttRecognitionError, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val isPlaying by player.isPlaying.collectAsState()
    val currentPlayingItem by player.currentPlayingItem.collectAsState()
    val currentPositionMs by player.currentPositionMs.collectAsState()
    val durationMs by player.durationMs.collectAsState()

    val isThisItemPlaying = isPlaying && currentPlayingItem?.baseName == item.baseName

    LaunchedEffect(isMp3Copied) {
        if (isMp3Copied) {
            delay(2000)
            isMp3Copied = false
        }
    }

    LaunchedEffect(isWavCopied) {
        if (isWavCopied) {
            delay(2000)
            isWavCopied = false
        }
    }

    LaunchedEffect(isTextCopied) {
        if (isTextCopied) {
            delay(2000)
            isTextCopied = false
        }
    }

    val copyMp3Bg by animateColorAsState(
        targetValue = if (isMp3Copied) SuccessGreenBackground else Mp3OrangeBackground,
        label = "copy_mp3_bg"
    )
    val copyMp3Fg by animateColorAsState(
        targetValue = if (isMp3Copied) SuccessGreen else Mp3Orange,
        label = "copy_mp3_fg"
    )

    val copyWavBg by animateColorAsState(
        targetValue = if (isWavCopied) SuccessGreenBackground else PlayBlueBackground,
        label = "copy_wav_bg"
    )
    val copyWavFg by animateColorAsState(
        targetValue = if (isWavCopied) SuccessGreen else PlayBlue,
        label = "copy_wav_fg"
    )

    val copyTextBg by animateColorAsState(
        targetValue = if (isTextCopied) SuccessGreenBackground else VoiceDropPurple.copy(alpha = 0.2f),
        label = "copy_text_bg"
    )
    val copyTextFg by animateColorAsState(
        targetValue = if (isTextCopied) SuccessGreen else VoiceDropPurpleLight,
        label = "copy_text_fg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, StudioCardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = StudioCardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Icon + File name + badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoiceDropPurple.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = "Audio File",
                        tint = VoiceDropPurple,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.baseName}.mp3",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // MP3 Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Mp3Orange.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "MP3",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Mp3Orange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        if (item.wavFile != null) {
                            Spacer(modifier = Modifier.width(5.dp))
                            // WAV Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PlayBlue.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "WAV",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = PlayBlue,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = item.formattedMp3Size,
                            style = MaterialTheme.typography.labelMedium.copy(color = TextMuted)
                        )

                        if (item.formattedDuration.isNotEmpty()) {
                            Text(
                                text = " • ${item.formattedDuration}",
                                style = MaterialTheme.typography.labelMedium.copy(color = TextMuted)
                            )
                        }
                    }
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = strings.delete,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Player Bar: Play/Pause Button + Scrubber Slider + Times
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { player.togglePlayPause(item) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PlayBlue)
                ) {
                    Icon(
                        imageVector = if (isThisItemPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isThisItemPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                val currentProg = if (isThisItemPlaying && durationMs > 0) {
                    currentPositionMs.toFloat() / durationMs.toFloat()
                } else 0f

                Slider(
                    value = currentProg.coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        if (durationMs > 0) {
                            player.seekTo((frac * durationMs).toInt())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = PlayBlue,
                        activeTrackColor = PlayBlue,
                        inactiveTrackColor = StudioCardBorder
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                val displayTime = if (isThisItemPlaying) {
                    formatMs(currentPositionMs.toLong())
                } else if (item.durationMs > 0) {
                    formatMs(item.durationMs)
                } else {
                    "--:--"
                }

                Text(
                    text = displayTime,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Audio Action Buttons: [Copy MP3] + [Copy WAV] + [Share] + [Download]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Copy MP3 Button
                Button(
                    onClick = {
                        val success = ClipboardHelper.copyMp3ToClipboard(context, item)
                        if (success) isMp3Copied = true
                    },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = copyMp3Bg,
                        contentColor = copyMp3Fg
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isMp3Copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = strings.copyMp3,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMp3Copied) strings.copied else strings.copyMp3,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }

                // 2. Copy WAV Button
                Button(
                    onClick = {
                        val success = ClipboardHelper.copyWavToClipboard(context, item)
                        if (success) isWavCopied = true
                    },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = copyWavBg,
                        contentColor = copyWavFg
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isWavCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = strings.copyWav,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isWavCopied) strings.copied else strings.copyWav,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }

                // 3. Share Button
                Box {
                    OutlinedButton(
                        onClick = { showShareMenu = true },
                        modifier = Modifier.height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(StudioCardBorder)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = strings.share,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showShareMenu,
                        onDismissRequest = { showShareMenu = false },
                        modifier = Modifier.background(StudioCardBackground)
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.shareMp3, color = TextPrimary) },
                            onClick = {
                                showShareMenu = false
                                ClipboardHelper.shareAudio(context, item, preferWav = false)
                            }
                        )
                        if (item.wavFile != null) {
                            DropdownMenuItem(
                                text = { Text(strings.shareLosslessWav, color = TextPrimary) },
                                onClick = {
                                    showShareMenu = false
                                    ClipboardHelper.shareAudio(context, item, preferWav = true)
                                }
                            )
                        }
                    }
                }

                // 4. Save to Downloads Button
                IconButton(
                    onClick = { ClipboardHelper.saveToDownloads(context, item) },
                    modifier = Modifier
                        .size(42.dp)
                        .border(1.dp, StudioCardBorder, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = strings.saveToDownloads,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = StudioCardBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // ⚙️ LOCAL MODEL / ON-DEVICE TOGGLE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { transcriber.setUseLocalModel(!useLocalModel) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = useLocalModel,
                        onCheckedChange = { transcriber.setUseLocalModel(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = VoiceDropPurpleLight,
                            uncheckedColor = TextMuted,
                            checkmarkColor = StudioDarkBackground
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.useLocalModel,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (useLocalModel) SuccessGreen.copy(alpha = 0.2f)
                                        else StudioSurfaceVariant
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (useLocalModel) "Vosk AI (Local)" else "Google Cloud",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (useLocalModel) SuccessGreen else TextMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                        Text(
                            text = strings.useLocalModelDesc,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Quick Settings Shortcut Button to download offline models
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent("android.settings.VOICE_INPUT_SETTINGS").apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, strings.openVoiceSettings, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = strings.openVoiceSettings,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 🧠 ON-DEVICE SPEECH RECOGNITION SECTION (RECOGNIZE THIS AUDIO FILE)
            if (transcriptionText.isNotBlank()) {
                // Transcription already exists or recognized: display editable text & Copy Text button
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = strings.sttTitle,
                                tint = VoiceDropPurpleLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.sttTitle,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        // Re-recognize Action
                        TextButton(
                            onClick = { launchRecognition() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            enabled = !isBusyTranscribing
                        ) {
                            if (isBusyTranscribing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = VoiceDropPurpleLight,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.recognizing,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = VoiceDropPurpleLight,
                                        fontSize = 11.sp
                                    )
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = strings.reRecognize,
                                    modifier = Modifier.size(12.dp),
                                    tint = TextMuted
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.reRecognize,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Editable Recognized Text Field
                    OutlinedTextField(
                        value = transcriptionText,
                        onValueChange = { newText ->
                            transcriptionText = newText
                            onSaveTranscription?.invoke(item, newText)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 130.dp),
                        placeholder = {
                            Text(
                                text = strings.sttPlaceholder,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VoiceDropPurple,
                            unfocusedBorderColor = StudioCardBorder,
                            focusedContainerColor = StudioSurfaceVariant,
                            unfocusedContainerColor = StudioSurfaceVariant,
                            cursorColor = VoiceDropPurpleLight
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Buttons: [ 📋 Copy Text ] + [ Share Text ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val success = ClipboardHelper.copyTextToClipboard(context, transcriptionText)
                                if (success) isTextCopied = true
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = copyTextBg,
                                contentColor = copyTextFg
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isTextCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = strings.sttCopyText,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTextCopied) strings.copied else strings.sttCopyText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = { ClipboardHelper.shareText(context, transcriptionText) },
                            modifier = Modifier
                                .weight(0.9f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(StudioCardBorder)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = strings.sttShareText,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.sttShareText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            } else {
                // Not yet recognized: Button to recognize THIS audio file!
                Button(
                    onClick = { launchRecognition() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoiceDropPurple.copy(alpha = 0.2f),
                        contentColor = VoiceDropPurpleLight
                    ),
                    enabled = !isBusyTranscribing
                ) {
                    if (isBusyTranscribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = VoiceDropPurpleLight,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.recognizing,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = strings.recognizeAudio,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.recognizeAudio,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            // Guidance hint & Copy & Minimize action
            Spacer(modifier = Modifier.height(10.dp))
            val activity = context as? Activity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strings.pasteHint,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextMuted,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (activity != null) {
                    TextButton(
                        onClick = {
                            val success = ClipboardHelper.copyMp3ToClipboard(context, item)
                            if (success) {
                                isMp3Copied = true
                                activity.moveTaskToBack(true)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Launch,
                            contentDescription = strings.copyAndMinimize,
                            modifier = Modifier.size(13.dp),
                            tint = VoiceDropPurpleLight
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.copyAndMinimize,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = VoiceDropPurpleLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatMs(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
