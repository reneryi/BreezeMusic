package com.concept.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.concept.music.ui.theme.ConceptAnimatedOverlaySheet
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.softCardClickable

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirmWithCallback: (name: String, onComplete: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    ConceptAnimatedOverlaySheet(
        onDismiss = { if (!isSubmitting) onDismiss() },
        isLandscape = true,
        scrimAlpha = 0.56f,
        zIndex = 95f
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = conceptSheetContainerColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 24.dp,
            modifier = modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 400.dp)
                .wrapContentHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ConceptPrimary.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = ConceptPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "新建歌单",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "输入歌单名称，创建专属音乐集",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    placeholder = { Text("例如：我最爱的白噪音、学习歌单") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        if (name.isNotEmpty()) {
                            IconButton(onClick = { name = "" }, enabled = !isSubmitting) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "清空",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 无边框色阶取消按钮，告别生硬边框线
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = conceptElevatedSurfaceColor(),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .softCardClickable(
                                enabled = !isSubmitting,
                                shape = RoundedCornerShape(14.dp),
                                onClick = requestDismiss
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "取消",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank() && !isSubmitting) {
                                isSubmitting = true
                                onConfirmWithCallback(name.trim()) { success ->
                                    isSubmitting = false
                                    if (success) {
                                        requestDismiss()
                                    }
                                }
                            }
                        },
                        enabled = name.isNotBlank() && !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ConceptPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text("创建", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
