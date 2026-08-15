package com.comicplus.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicplus.app.ui.AppSettings
import com.comicplus.app.ui.AppUpdateUiState
import com.comicplus.app.ui.LocalComicPlusReduceMotion
import com.comicplus.app.ui.ReaderDirection
import com.comicplus.app.ui.ReaderImageQuality
import com.comicplus.app.ui.ReaderMode
import com.comicplus.app.ui.ReaderPrefetchMode
import com.comicplus.app.ui.components.ComicPlusTopBar
import com.comicplus.app.ui.components.CpDimens
import com.comicplus.app.ui.components.SegmentedControl
import com.comicplus.app.ui.theme.Canvas
import com.comicplus.app.ui.theme.ComicPlusPalette
import com.comicplus.app.ui.theme.Ink
import com.comicplus.app.ui.theme.InkSoft
import com.comicplus.app.ui.theme.Muted
import com.comicplus.app.ui.theme.SurfaceSoft
import com.comicplus.app.ui.theme.White
import com.comicplus.pure.DownloadedChapter
import com.comicplus.app.ui.JmSourceUiState
import com.comicplus.app.ui.JmSourceUiItem
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    downloads: List<DownloadedChapter>,
    onSettingsChange: (AppSettings) -> Unit,
    onClearReaderCache: () -> Unit,
    sourceStatus: JmSourceUiState,
    updateStatus: AppUpdateUiState,
    onRefreshSources: () -> Unit,
    onCheckUpdates: (Boolean) -> Unit,
    onOpenUpdate: (String) -> Unit,
    onOpenDownload: (DownloadedChapter) -> Unit,
    onDeleteDownload: (DownloadedChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<DownloadedChapter?>(null) }
    var showUpdateDetails by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onCheckUpdates(false) }
    Column(modifier.fillMaxSize().background(Canvas)) {
        ComicPlusTopBar(title = "设置", subtitle = "纯本地设置与下载管理", actions = emptyList())
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                SettingsSectionTitle("外观")
                PaletteSelector(settings.paletteKey) { onSettingsChange(settings.copy(paletteKey = it)) }
                SettingsSwitchRow(
                    Icons.Outlined.DarkMode,
                    "黑夜模式",
                    "将首页、详情、搜索和设置切换为低亮度配色",
                    settings.darkMode,
                ) { onSettingsChange(settings.copy(darkMode = it)) }
                SettingsSwitchRow(
                    Icons.Outlined.Speed,
                    "减少动态效果",
                    "关闭部分切换和装饰动效",
                    settings.reduceMotion,
                ) { onSettingsChange(settings.copy(reduceMotion = it)) }
            }
            item {
                SettingsSectionTitle("阅读")
                ReaderModeSettings(settings, onSettingsChange)
                Text(
                    "页面画质",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 8.dp),
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
                SegmentedControl(
                    labels = listOf("流畅", "标准", "高清"),
                    selected = when {
                        settings.readerTurboMode -> "流畅"
                        settings.readerImageQuality == ReaderImageQuality.Low -> "流畅"
                        settings.readerImageQuality == ReaderImageQuality.High -> "高清"
                        else -> "标准"
                    },
                    onSelected = { label ->
                        onSettingsChange(
                            settings.copy(
                                readerImageQuality = when (label) {
                                    "流畅" -> ReaderImageQuality.Low
                                    "高清" -> ReaderImageQuality.High
                                    else -> ReaderImageQuality.Medium
                                },
                                readerTurboMode = false,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding),
                )
                Text(
                    "流畅 720px · 标准 1080px · 高清 1440px",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 5.dp),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "预加载策略",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 8.dp),
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
                SegmentedControl(
                    labels = listOf("省内存", "智能", "积极", "自定义"),
                    selected = when (settings.readerPrefetchMode) {
                        ReaderPrefetchMode.Conservative -> "省内存"
                        ReaderPrefetchMode.Aggressive -> "积极"
                        ReaderPrefetchMode.Custom -> "自定义"
                        ReaderPrefetchMode.Smart -> "智能"
                    },
                    onSelected = { label ->
                        val mode = when (label) {
                            "省内存" -> ReaderPrefetchMode.Conservative
                            "积极" -> ReaderPrefetchMode.Aggressive
                            "自定义" -> ReaderPrefetchMode.Custom
                            else -> ReaderPrefetchMode.Smart
                        }
                        val pages = when (mode) {
                            ReaderPrefetchMode.Conservative -> 1
                            ReaderPrefetchMode.Aggressive -> 5
                            ReaderPrefetchMode.Smart -> 3
                            ReaderPrefetchMode.Custom -> settings.readerPrefetchPages
                        }
                        onSettingsChange(settings.copy(readerPrefetchMode = mode, readerPrefetchPages = pages))
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding),
                )
                Text(
                    when (settings.readerPrefetchMode) {
                        ReaderPrefetchMode.Conservative -> "只保温相邻 1 页，优先降低内存和流量占用"
                        ReaderPrefetchMode.Aggressive -> "前后页并行保温，适合网速快且连续阅读"
                        ReaderPrefetchMode.Custom -> "按下方页数保温，并自动避让前台页面"
                        ReaderPrefetchMode.Smart -> "根据翻页速度、方向和设备内存自动调整"
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 5.dp),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                AnimatedVisibility(
                    visible = settings.readerPrefetchMode == ReaderPrefetchMode.Custom,
                    enter = if (LocalComicPlusReduceMotion.current) fadeIn() else fadeIn() + expandVertically(),
                    exit = if (LocalComicPlusReduceMotion.current) fadeOut() else fadeOut() + shrinkVertically(),
                ) {
                    SettingSliderRow("预加载页数", "${settings.readerPrefetchPages} 页", settings.readerPrefetchPages.toFloat(), 0f..6f, 5) {
                        onSettingsChange(settings.copy(readerPrefetchPages = it.roundToInt(), readerPrefetchMode = ReaderPrefetchMode.Custom))
                    }
                }
                SettingSliderRow("页面间距", "${settings.readerPageSpacingDp} dp", settings.readerPageSpacingDp.toFloat(), 0f..16f, 7) {
                    onSettingsChange(settings.copy(readerPageSpacingDp = it.roundToInt()))
                }
                SettingSliderRow(
                    "阅读亮度",
                    if (settings.readerBrightnessPercent == 0) "跟随系统" else "${settings.readerBrightnessPercent}%",
                    settings.readerBrightnessPercent.toFloat(),
                    0f..100f,
                    9,
                ) { onSettingsChange(settings.copy(readerBrightnessPercent = (it / 10f).roundToInt() * 10)) }
                SettingsSwitchRow(Icons.Outlined.TouchApp, "点击切换阅读菜单", "轻触图片区域显示或隐藏工具栏", settings.tapToToggleReaderMenu) {
                    onSettingsChange(settings.copy(tapToToggleReaderMenu = it))
                }
                SettingsSwitchRow(Icons.Outlined.Cached, "自动续读", "仅在本机保存章节与页码", settings.autoResumeReading) {
                    onSettingsChange(settings.copy(autoResumeReading = it))
                }
                SettingsSwitchRow(Icons.Outlined.Cached, "保持屏幕常亮", "仅在阅读器打开期间生效", settings.keepScreenOn) {
                    onSettingsChange(settings.copy(keepScreenOn = it))
                }
                SettingsSwitchRow(Icons.Outlined.Speed, "节省流量", "阅读器最多后台预加载 1 页", settings.dataSaver) {
                    onSettingsChange(settings.copy(dataSaver = it, readerTurboMode = if (it) false else settings.readerTurboMode))
                }
                SettingsSwitchRow(Icons.Outlined.Speed, "极速模式", "480px 解码、当前页抢占、低延迟线路与提前预取", settings.readerTurboMode) {
                    onSettingsChange(
                        settings.copy(
                            readerTurboMode = it,
                            dataSaver = if (it) false else settings.dataSaver,
                        ),
                    )
                }
            }
            item {
                SettingsSectionTitle("本地存储")
                SettingsActionRow(Icons.Outlined.AutoDelete, "清理阅读缓存", "删除已解码页面，不影响已下载章节", onClearReaderCache)
            }
            item {
                SettingsSectionTitle("官方源站")
                SourceSettings(
                    settings = settings,
                    status = sourceStatus,
                    onSettingsChange = onSettingsChange,
                    onRefresh = onRefreshSources,
                )
            }
            item {
                SettingsSectionTitle("应用更新")
                AppUpdateSettings(
                    status = updateStatus,
                    onCheck = { onCheckUpdates(true) },
                    onShowDetails = { showUpdateDetails = true },
                )
            }
            item {
                SettingsSectionTitle("下载管理")
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(CpDimens.cardRadius),
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Download, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            val available = downloads.count(DownloadedChapter::complete)
                            val unavailable = downloads.size - available
                            Text("可离线 $available 个章节", color = Ink, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (unavailable > 0) "$unavailable 个旧章节需重新下载" else "文件仅保存在 App 私有目录",
                                color = if (unavailable > 0) MaterialTheme.colorScheme.error else Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (downloads.isEmpty()) {
                item {
                    Text(
                        "还没有下载内容，可在漫画详情的章节列表中点击下载。",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 24.dp),
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(downloads, key = { "${it.comicId}:${it.chapterId}" }) { item ->
                    DownloadRow(item, onOpen = { onOpenDownload(item) }, onDelete = { pendingDelete = item })
                }
            }
            item {
                SettingsSectionTitle("关于")
                SettingsActionRow(
                    Icons.Outlined.Info,
                    "Comic Plus",
                    "当前版本 ${updateStatus.currentVersion.ifBlank { "未知" }}",
                    onClick = { if (updateStatus.releaseUrl != null) showUpdateDetails = true },
                )
                Text(
                    "Comic Plus · 数据与下载仅保存在本机",
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp).navigationBarsPadding(),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Outlined.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除下载章节？") },
            text = { Text("将删除《${item.comicTitle}》${item.chapterTitle} 的全部本地图片。") },
            confirmButton = {
                TextButton(onClick = { onDeleteDownload(item); pendingDelete = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    val updateUrl = if (updateStatus.updateAvailable) {
        updateStatus.downloadUrl ?: updateStatus.releaseUrl
    } else {
        updateStatus.releaseUrl
    }
    if (showUpdateDetails && updateUrl != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDetails = false },
            icon = { Icon(Icons.Outlined.SystemUpdateAlt, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(updateStatus.releaseName ?: "Comic Plus ${updateStatus.latestVersion.orEmpty()}") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "当前 ${updateStatus.currentVersion} · 最新 ${updateStatus.latestVersion.orEmpty()}",
                        color = InkSoft,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    updateStatus.publishedAt?.let { publishedAt ->
                        Text(
                            "发布于 ${formatReleaseDate(publishedAt)}" + updateStatus.assetSize?.let { " · ${formatBytes(it)}" }.orEmpty(),
                            modifier = Modifier.padding(top = 4.dp),
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        updateStatus.notes.ifBlank { "本次发布未提供更新说明。" },
                        modifier = Modifier.padding(top = 16.dp),
                        color = Ink,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onOpenUpdate(updateUrl)
                        showUpdateDetails = false
                    },
                ) {
                    Text(if (updateStatus.updateAvailable && updateStatus.downloadUrl != null) "下载 APK" else "打开发布页")
                }
            },
            dismissButton = { TextButton(onClick = { showUpdateDetails = false }) { Text("关闭") } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun AppUpdateSettings(
    status: AppUpdateUiState,
    onCheck: () -> Unit,
    onShowDetails: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 4.dp),
        shape = RoundedCornerShape(CpDimens.cardRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Outlined.SystemUpdateAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("GitHub Release", color = Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        updateStatusSummary(status),
                        color = if (status.error == null) Muted else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    .clickable(enabled = !status.checking, onClick = onCheck),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (status.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (status.checking) "正在检查 GitHub Release" else "检查更新",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (status.releaseUrl != null) {
                TextButton(
                    onClick = onShowDetails,
                    modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                ) {
                    Text("查看 ${status.latestVersion.orEmpty()} 更新详情")
                }
            }
        }
    }
}

@Composable
private fun SourceSettings(
    settings: AppSettings,
    status: JmSourceUiState,
    onSettingsChange: (AppSettings) -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 4.dp),
        shape = RoundedCornerShape(CpDimens.cardRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Outlined.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("源站调度", color = Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(sourceStatusSummary(status), color = if (status.error == null) Muted else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.size(8.dp))
            SourceToggleRow(
                title = "自动选择最快源",
                description = "按接口和图片线路的实测延迟自动排序",
                checked = settings.autoSelectSource,
            ) { onSettingsChange(settings.copy(autoSelectSource = it)) }
            SourceToggleRow(
                title = "自动更新源站列表",
                description = "启动时更新，运行期间每 6 小时检查",
                checked = settings.autoUpdateSourceList,
            ) { onSettingsChange(settings.copy(autoUpdateSourceList = it)) }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    .clickable(enabled = !status.checking, onClick = onRefresh),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (status.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (status.checking) "正在检测官方源站" else "立即更新并检测延迟",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            SourceEndpointGroup(
                title = "接口线路",
                endpoints = status.items,
                selectedHost = if (settings.autoSelectSource) status.selectedHost else settings.preferredSourceHost ?: status.selectedHost,
                onSelect = { host ->
                    onSettingsChange(
                        settings.copy(
                            autoSelectSource = false,
                            preferredSourceHost = host,
                        ),
                    )
                },
            )
            SourceEndpointGroup(
                title = "漫画图片线路",
                endpoints = status.imageItems,
                selectedHost = if (settings.autoSelectSource) status.selectedImageHost else settings.preferredImageHost ?: status.selectedImageHost,
                onSelect = { host ->
                    onSettingsChange(
                        settings.copy(
                            autoSelectSource = false,
                            preferredImageHost = host,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SourceEndpointGroup(
    title: String,
    endpoints: List<JmSourceUiItem>,
    selectedHost: String?,
    onSelect: (String) -> Unit,
) {
    if (endpoints.isEmpty()) return
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, color = InkSoft, style = MaterialTheme.typography.labelMedium)
        endpoints.forEach { endpoint ->
            val selected = endpoint.host == selectedHost
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelect(endpoint.host) }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(7.dp).clip(CircleShape).background(
                        when {
                            endpoint.latencyMs == null -> MaterialTheme.colorScheme.error
                            selected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = endpoint.host,
                    modifier = Modifier.weight(1f),
                    color = if (selected) MaterialTheme.colorScheme.primary else InkSoft,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    endpoint.latencyMs?.let { if (selected) "优先 · ${it} ms" else "${it} ms" } ?: "不可用",
                    color = endpoint.latencyMs?.let { Muted } ?: MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SourceToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.bodyMedium)
            Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun sourceStatusSummary(status: JmSourceUiState): String {
    status.error?.let { return "检测失败，可点击重试" }
    if (status.checking) return "正在测试官方源站延迟"
    val updated = status.updatedAt.takeIf { it > 0L }?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    }
    val available = status.items.count { it.latencyMs != null }
    val imageAvailable = status.imageItems.count { it.latencyMs != null }
    return when {
        status.selectedHost == null -> "尚未检测官方源站"
        updated == null -> "当前源：${status.selectedHost}"
        else -> "接口 $available/${status.items.size} · 图片 $imageAvailable/${status.imageItems.size} · 最近检测 $updated"
    }
}

private fun updateStatusSummary(status: AppUpdateUiState): String = when {
    status.checking -> "正在读取 GitHub 最新版本"
    status.error != null -> "检查失败：${status.error}"
    status.updateAvailable -> "发现新版本 ${status.latestVersion.orEmpty()}"
    status.checked && status.latestVersion != null -> "已是最新版 · ${status.latestVersion}"
    else -> "当前版本 ${status.currentVersion.ifBlank { "未知" }}"
}

private fun formatReleaseDate(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(RELEASE_DATE_FORMAT)
}.getOrDefault(value)

private val RELEASE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
private fun ReaderModeSettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 4.dp),
        shape = RoundedCornerShape(CpDimens.cardRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(15.dp)) {
            Text("阅读模式", color = Ink, style = MaterialTheme.typography.bodyLarge)
            SegmentedControl(
                labels = listOf("纵向滚动", "左右分页"),
                selected = if (settings.readerMode == ReaderMode.Vertical) "纵向滚动" else "左右分页",
                onSelected = { onSettingsChange(settings.copy(readerMode = if (it == "纵向滚动") ReaderMode.Vertical else ReaderMode.Paged)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            AnimatedVisibility(
                visible = settings.readerMode == ReaderMode.Paged,
                enter = if (LocalComicPlusReduceMotion.current) fadeIn() else fadeIn() + expandVertically(),
                exit = if (LocalComicPlusReduceMotion.current) fadeOut() else fadeOut() + shrinkVertically(),
            ) {
                SegmentedControl(
                    labels = listOf("从左向右", "从右向左"),
                    selected = if (settings.readerDirection == ReaderDirection.LeftToRight) "从左向右" else "从右向左",
                    onSelected = {
                        onSettingsChange(settings.copy(readerDirection = if (it == "从左向右") ReaderDirection.LeftToRight else ReaderDirection.RightToLeft))
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PaletteSelector(selectedKey: String, onSelect: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding),
        shape = RoundedCornerShape(CpDimens.cardRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ColorLens, null, tint = InkSoft)
                Spacer(Modifier.width(12.dp))
                Text("全局主色", color = Ink, style = MaterialTheme.typography.bodyLarge)
            }
            Row(Modifier.padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComicPlusPalette.entries.forEach { palette ->
                    val selected = palette.key == selectedKey
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).clickable { onSelect(palette.key) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(31.dp).clip(CircleShape).background(palette.primary), contentAlignment = Alignment.Center) {
                            if (selected) Icon(Icons.Outlined.Check, null, tint = White, modifier = Modifier.size(18.dp))
                        }
                        Text(palette.displayName, color = if (selected) palette.primaryDark else Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = CpDimens.screenPadding, end = CpDimens.screenPadding, top = 27.dp, bottom = 9.dp),
        color = Ink,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SettingsSwitchRow(icon: ImageVector, title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsRowContainer {
        Icon(icon, null, tint = InkSoft, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.bodyLarge)
            Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    SettingsRowContainer(onClick) {
        Icon(icon, null, tint = InkSoft, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.bodyLarge)
            Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    var draftValue by remember(title) { mutableFloatStateOf(value) }
    var dragging by remember(title) { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!dragging) draftValue = value
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 4.dp),
        shape = RoundedCornerShape(CpDimens.cardRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.bodyLarge)
                Surface(shape = CircleShape, color = SurfaceSoft) {
                    Text(valueLabel, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = InkSoft, style = MaterialTheme.typography.labelMedium)
                }
            }
            Slider(
                value = draftValue.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = {
                    dragging = true
                    draftValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    onValueChange(draftValue)
                },
                valueRange = valueRange,
                steps = steps,
            )
        }
    }
}

@Composable
private fun SettingsRowContainer(
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalComicPlusReduceMotion.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 4.dp)
            .graphicsLayer {
                val scale = if (pressed && !reduceMotion) .985f else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick) else Modifier),
        shape = RoundedCornerShape(CpDimens.cardRadius),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (onClick != null && !pressed) 1.dp else 0.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun DownloadRow(item: DownloadedChapter, onOpen: () -> Unit, onDelete: () -> Unit) {
    SettingsRowContainer(onOpen) {
        Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Outlined.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.comicTitle, color = Ink, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${item.chapterTitle} · ${item.pageCount} 页 · ${formatBytes(item.bytes)}" +
                    if (item.complete) "" else " · 需重新下载",
                color = if (item.complete) Muted else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
