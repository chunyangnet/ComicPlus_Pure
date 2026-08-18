package com.comicplus.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.comicplus.app.ui.icons.ComicPlusIcons as Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.comicplus.app.ui.AppSettings
import com.comicplus.app.ui.JmSourceUiItem
import com.comicplus.app.ui.JmSourceUiState
import com.comicplus.app.ui.LocalComicPlusReduceMotion
import com.comicplus.app.ui.ReaderDirection
import com.comicplus.app.ui.ReaderImageQuality
import com.comicplus.app.ui.ReaderMode
import com.comicplus.app.ui.ReaderPrefetchMode
import com.comicplus.app.ui.theme.White
import kotlin.math.roundToInt

@Composable
internal fun ReaderSettingsDialog(
    settings: AppSettings,
    sourceStatus: JmSourceUiState,
    onSettingsChange: (AppSettings) -> Unit,
    onRefreshSources: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(settings) }
    fun commit(updated: AppSettings) {
        draft = updated
        onSettingsChange(updated)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF171719),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("阅读设置", color = White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Text("阅读模式", color = White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ReaderSegmentedControl(
                    labels = listOf("纵向滚动", "左右分页"),
                    selected = if (draft.readerMode == ReaderMode.Vertical) "纵向滚动" else "左右分页",
                    onSelected = { label ->
                        commit(
                            draft.copy(readerMode = if (label == "纵向滚动") ReaderMode.Vertical else ReaderMode.Paged),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(
                    visible = draft.readerMode == ReaderMode.Paged,
                    enter = fadeIn() + slideInVertically { -it / 3 },
                    exit = fadeOut() + slideOutVertically { -it / 3 },
                ) {
                    Spacer(Modifier.height(10.dp))
                    ReaderSegmentedControl(
                        labels = listOf("从左向右", "从右向左"),
                        selected = if (draft.readerDirection == ReaderDirection.LeftToRight) "从左向右" else "从右向左",
                        onSelected = { label ->
                            commit(
                                draft.copy(
                                    readerDirection = if (label == "从左向右") {
                                        ReaderDirection.LeftToRight
                                    } else {
                                        ReaderDirection.RightToLeft
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("页面画质", color = White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ReaderSegmentedControl(
                    labels = listOf("流畅", "标准", "高清"),
                    selected = when {
                        draft.readerTurboMode -> "流畅"
                        draft.readerImageQuality == ReaderImageQuality.Low -> "流畅"
                        draft.readerImageQuality == ReaderImageQuality.High -> "高清"
                        else -> "标准"
                    },
                    onSelected = { label ->
                        commit(
                            draft.copy(
                                readerImageQuality = when (label) {
                                    "流畅" -> ReaderImageQuality.Low
                                    "高清" -> ReaderImageQuality.High
                                    else -> ReaderImageQuality.Medium
                                },
                                readerTurboMode = false,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "流畅 720px · 标准 1080px · 高清 1440px",
                    color = White.copy(alpha = .38f),
                    style = MaterialTheme.typography.labelSmall,
                )
                SettingSwitchRow("极速模式", draft.readerTurboMode) {
                    commit(
                        draft.copy(
                            readerTurboMode = it,
                            dataSaver = if (it) false else draft.dataSaver,
                        ),
                    )
                }
                AnimatedVisibility(
                    visible = draft.readerTurboMode,
                    enter = fadeIn() + slideInVertically { -it / 4 },
                    exit = fadeOut() + slideOutVertically { -it / 4 },
                ) {
                    Text(
                        "极速模式使用 480px 解码、当前页抢占和低延迟图片线路",
                        color = White.copy(alpha = .42f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("线路与延迟", color = White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            sourceStatus.selectedImageHost?.let { host ->
                                val latency = sourceStatus.imageItems.firstOrNull { it.host == host }?.latencyMs
                                if (latency == null) host else "$host · $latency ms"
                            } ?: sourceStatus.selectedHost ?: "尚未检测",
                            color = White.copy(alpha = .42f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onRefreshSources, enabled = !sourceStatus.checking) {
                        if (sourceStatus.checking) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "检测线路延迟", tint = White.copy(alpha = .8f))
                        }
                    }
                }
                SettingSwitchRow("自动选择最快线路", draft.autoSelectSource) {
                    commit(draft.copy(autoSelectSource = it))
                }
                ReaderSourceEndpointGroup(
                    title = "漫画图片线路",
                    endpoints = sourceStatus.imageItems,
                    selectedHost = if (draft.autoSelectSource) {
                        sourceStatus.selectedImageHost
                    } else {
                        draft.preferredImageHost ?: sourceStatus.selectedImageHost
                    },
                    onSelect = { host ->
                        commit(draft.copy(autoSelectSource = false, preferredImageHost = host))
                    },
                )
                ReaderSourceEndpointGroup(
                    title = "章节接口线路",
                    endpoints = sourceStatus.items,
                    selectedHost = if (draft.autoSelectSource) {
                        sourceStatus.selectedHost
                    } else {
                        draft.preferredSourceHost ?: sourceStatus.selectedHost
                    },
                    onSelect = { host ->
                        commit(draft.copy(autoSelectSource = false, preferredSourceHost = host))
                    },
                )
                Spacer(Modifier.height(12.dp))
                SettingSwitchRow("保持屏幕常亮", draft.keepScreenOn) {
                    commit(draft.copy(keepScreenOn = it))
                }
                SettingSwitchRow("点击切换菜单", draft.tapToToggleReaderMenu) {
                    commit(draft.copy(tapToToggleReaderMenu = it))
                }
                SettingSwitchRow("跟随系统亮度", draft.readerBrightnessPercent == 0) { followSystem ->
                    commit(
                        draft.copy(
                            readerBrightnessPercent = if (followSystem) 0 else 60,
                        ),
                    )
                }
                AnimatedVisibility(
                    visible = draft.readerBrightnessPercent > 0,
                    enter = fadeIn() + slideInVertically { it / 4 },
                    exit = fadeOut() + slideOutVertically { it / 4 },
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "阅读亮度 ${draft.readerBrightnessPercent}%",
                            color = White.copy(alpha = .72f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = draft.readerBrightnessPercent.toFloat(),
                            onValueChange = {
                                val percent = (it / 10f).roundToInt().times(10).coerceIn(10, 100)
                                draft = draft.copy(readerBrightnessPercent = percent)
                            },
                            onValueChangeFinished = { onSettingsChange(draft) },
                            valueRange = 10f..100f,
                            steps = 8,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "预加载策略",
                    color = White.copy(alpha = .72f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                ReaderSegmentedControl(
                    labels = listOf("省内存", "智能", "积极", "超激进", "自定义"),
                    selected = readerPrefetchModeLabel(draft.readerPrefetchMode),
                    onSelected = { label ->
                        val mode = readerPrefetchModeForLabel(label)
                        draft = draft.copy(
                            readerPrefetchMode = mode,
                            readerPrefetchPages = when (mode) {
                                ReaderPrefetchMode.Conservative -> 1
                                ReaderPrefetchMode.Smart -> 3
                                ReaderPrefetchMode.Aggressive -> 5
                                ReaderPrefetchMode.UltraAggressive -> 6
                                ReaderPrefetchMode.Custom -> draft.readerPrefetchPages.coerceIn(0, 6)
                            },
                            dataSaver = if (mode == ReaderPrefetchMode.UltraAggressive) false else draft.dataSaver,
                        )
                        onSettingsChange(draft)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    readerPrefetchDescription(draft.readerPrefetchMode, draft.readerPrefetchPages),
                    color = White.copy(alpha = .42f),
                    style = MaterialTheme.typography.labelSmall,
                )
                AnimatedVisibility(
                    visible = draft.readerPrefetchMode == ReaderPrefetchMode.Custom,
                    enter = fadeIn() + slideInVertically { it / 4 },
                    exit = fadeOut() + slideOutVertically { it / 4 },
                ) {
                    Column {
                        Text("精确页数 ${draft.readerPrefetchPages}", color = White.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = draft.readerPrefetchPages.toFloat(),
                            onValueChange = { draft = draft.copy(readerPrefetchPages = it.roundToInt().coerceIn(0, 6)) },
                            onValueChangeFinished = { onSettingsChange(draft) },
                            valueRange = 0f..6f,
                            steps = 5,
                        )
                    }
                }
                Text("页面间距 ${draft.readerPageSpacingDp} dp", color = White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = draft.readerPageSpacingDp.toFloat(),
                    onValueChange = { draft = draft.copy(readerPageSpacingDp = it.roundToInt().coerceIn(0, 16)) },
                    onValueChangeFinished = { onSettingsChange(draft) },
                    valueRange = 0f..16f,
                    steps = 7,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Color.Black),
                ) {
                    Text("完成")
                }
            }
        }
    }
}
@Composable
private fun ReaderSourceEndpointGroup(
    title: String,
    endpoints: List<JmSourceUiItem>,
    selectedHost: String?,
    onSelect: (String) -> Unit,
) {
    if (endpoints.isEmpty()) return
    Text(
        title,
        modifier = Modifier.padding(top = 8.dp, bottom = 3.dp),
        color = White.copy(alpha = .55f),
        style = MaterialTheme.typography.labelMedium,
    )
    endpoints.forEach { endpoint ->
        val selected = endpoint.host == selectedHost
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .clickable { onSelect(endpoint.host) }
                .background(if (selected) White.copy(alpha = .1f) else Color.Transparent)
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(7.dp).clip(CircleShape).background(
                    when {
                        endpoint.latencyMs == null -> Color(0xFFE57373)
                        selected -> White
                        else -> White.copy(alpha = .25f)
                    },
                ),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                endpoint.host,
                modifier = Modifier.weight(1f),
                color = White.copy(alpha = if (selected) .92f else .58f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                endpoint.latencyMs?.let { if (selected) "优先 · $it ms" else "$it ms" } ?: "不可用",
                color = if (endpoint.latencyMs == null) Color(0xFFE57373) else White.copy(alpha = .46f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = White.copy(alpha = .84f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun readerPrefetchModeLabel(mode: ReaderPrefetchMode): String = when (mode) {
    ReaderPrefetchMode.Conservative -> "省内存"
    ReaderPrefetchMode.Smart -> "智能"
    ReaderPrefetchMode.Aggressive -> "积极"
    ReaderPrefetchMode.UltraAggressive -> "超激进"
    ReaderPrefetchMode.Custom -> "自定义"
}

private fun readerPrefetchModeForLabel(label: String): ReaderPrefetchMode = when (label) {
    "省内存" -> ReaderPrefetchMode.Conservative
    "积极" -> ReaderPrefetchMode.Aggressive
    "超激进" -> ReaderPrefetchMode.UltraAggressive
    "自定义" -> ReaderPrefetchMode.Custom
    else -> ReaderPrefetchMode.Smart
}

private fun readerPrefetchDescription(mode: ReaderPrefetchMode, pages: Int): String = when (mode) {
    ReaderPrefetchMode.Conservative -> "只保温相邻 1 页，优先降低内存和流量占用"
    ReaderPrefetchMode.Aggressive -> "前后页并行保温，并提前准备下一话"
    ReaderPrefetchMode.UltraAggressive -> "首屏就绪后进入，沿滑动方向保持解码缓冲并提前准备下一话；流量、内存与存储占用最高"
    ReaderPrefetchMode.Custom -> "按 $pages 页保温，并自动避让正在阅读的页面"
    ReaderPrefetchMode.Smart -> "根据翻页速度、方向和设备内存动态调整"
}

@Composable
private fun ReaderSegmentedControl(
    labels: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalComicPlusReduceMotion.current
    BoxWithConstraints(modifier.background(Color(0xFF242426), RoundedCornerShape(6.dp)).padding(3.dp)) {
        if (labels.isEmpty()) return@BoxWithConstraints
        val segmentWidth = maxWidth / labels.size
        val selectedIndex = labels.indexOf(selected).coerceAtLeast(0)
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = .84f, stiffness = 620f),
            label = "reader-segment-indicator-offset",
        )
        Surface(
            modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(segmentWidth)
                .height(40.dp),
            color = White.copy(alpha = .14f),
            shape = RoundedCornerShape(4.dp),
        ) {}
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            labels.forEach { label ->
                val active = label == selected
                val contentColor by animateColorAsState(
                    White.copy(alpha = if (active) 1f else .58f),
                    tween(if (reduceMotion) 0 else 240),
                    label = "reader-segment-content",
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(label) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
