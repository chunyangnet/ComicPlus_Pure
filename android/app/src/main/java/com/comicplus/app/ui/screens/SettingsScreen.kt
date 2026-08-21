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
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.KeyboardOptions
import com.comicplus.app.ui.icons.ComicPlusIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicplus.app.ui.AppSettings
import com.comicplus.app.ui.AppUpdateUiState
import com.comicplus.app.ui.JmAccountStatus
import com.comicplus.app.ui.JmAccountUiState
import com.comicplus.app.ui.JmDailyStatus
import com.comicplus.app.ui.JmDailyUiState
import com.comicplus.app.ui.markdownToAnnotatedString
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
import com.comicplus.pure.isSignedToday
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
fun ProfileScreen(
    account: JmAccountUiState,
    daily: JmDailyUiState,
    favoriteCount: Int,
    historyCount: Int,
    downloads: List<DownloadedChapter>,
    sourceStatus: JmSourceUiState,
    updateStatus: AppUpdateUiState,
    onOpenSettings: () -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onSyncFavorites: () -> Unit,
    onLoadDaily: () -> Unit,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var jmUsername by remember { mutableStateOf(account.username) }
    var jmPassword by remember { mutableStateOf("") }
    LaunchedEffect(account.username, account.status) {
        if (account.username.isNotBlank()) jmUsername = account.username
        if (account.status == JmAccountStatus.SignedIn) jmPassword = ""
    }

    Column(modifier.fillMaxSize().background(Canvas)) {
        ComicPlusTopBar(
            title = "个人",
            subtitle = if (account.signedIn) "${account.username.ifBlank { "JM 官方账号" }} · 账号与本机数据" else "账号与本机数据",
            actions = listOf(
                com.comicplus.app.ui.components.AppBarAction(
                    icon = Icons.Outlined.Settings,
                    label = "设置",
                    onClick = onOpenSettings,
                ),
            ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SettingsSectionTitle("JM 官方账号")
                JmAccountSettings(
                    account = account,
                    username = jmUsername,
                    password = jmPassword,
                    onUsernameChange = { jmUsername = it.take(128) },
                    onPasswordChange = { jmPassword = it.take(512) },
                    onLogin = {
                        onLogin(jmUsername.trim(), jmPassword)
                        jmPassword = ""
                    },
                    onLogout = onLogout,
                    onSyncFavorites = onSyncFavorites,
                )
            }
            item {
                ProfileMetrics(
                    favoriteCount = account.favoriteCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: favoriteCount,
                    historyCount = historyCount,
                    downloadCount = downloads.count(DownloadedChapter::complete),
                )
            }
            if (account.signedIn) {
                item {
                    DailyCheckInCard(
                        daily = daily,
                        onLoad = onLoadDaily,
                        onCheckIn = onCheckIn,
                        modifier = Modifier.padding(horizontal = CpDimens.screenPadding),
                    )
                }
            }
            item {
                SettingsSectionTitle("服务状态")
                ProfileStatusPanel(sourceStatus, updateStatus)
            }
            item {
                Text(
                    "Comic Plus · 阅读记录、设置与下载均保存在本机",
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp).navigationBarsPadding(),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ProfileMetrics(favoriteCount: Int, historyCount: Int, downloadCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "收藏" to favoriteCount,
            "历史" to historyCount,
            "离线" to downloadCount,
        ).forEachIndexed { index, (label, value) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(if (index == 0) 16.dp else 8.dp),
                color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else SurfaceSoft,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 13.dp)) {
                    Text(
                        value.toString(),
                        color = if (index == 0) MaterialTheme.colorScheme.onPrimaryContainer else Ink,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        label,
                        color = if (index == 0) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .68f) else Muted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStatusPanel(sourceStatus: JmSourceUiState, updateStatus: AppUpdateUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding),
        shape = RoundedCornerShape(CpDimens.cardRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 5.dp)) {
            ProfileStatusRow(
                icon = Icons.Outlined.NetworkCheck,
                title = "JM 官方源",
                description = sourceStatusSummary(sourceStatus),
                warning = sourceStatus.error != null,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            ProfileStatusRow(
                icon = Icons.Outlined.SystemUpdateAlt,
                title = "应用版本 ${updateStatus.currentVersion.ifBlank { "未知" }}",
                description = updateStatusSummary(updateStatus),
                warning = updateStatus.error != null,
            )
        }
    }
}

@Composable
private fun ProfileStatusRow(icon: ImageVector, title: String, description: String, warning: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(description, color = if (warning) MaterialTheme.colorScheme.error else Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<DownloadedChapter?>(null) }
    var showUpdateDetails by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onCheckUpdates(false) }
    Column(modifier.fillMaxSize().background(Canvas)) {
        ComicPlusTopBar(
            title = "设置",
            subtitle = "阅读、外观、连接与存储",
            actions = emptyList(),
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Ink)
                }
            },
        )
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
                    labels = listOf("省内存", "智能", "积极", "超激进", "自定义"),
                    selected = when (settings.readerPrefetchMode) {
                        ReaderPrefetchMode.Conservative -> "省内存"
                        ReaderPrefetchMode.Aggressive -> "积极"
                        ReaderPrefetchMode.UltraAggressive -> "超激进"
                        ReaderPrefetchMode.Custom -> "自定义"
                        ReaderPrefetchMode.Smart -> "智能"
                    },
                    onSelected = { label ->
                        val mode = when (label) {
                            "省内存" -> ReaderPrefetchMode.Conservative
                            "积极" -> ReaderPrefetchMode.Aggressive
                            "超激进" -> ReaderPrefetchMode.UltraAggressive
                            "自定义" -> ReaderPrefetchMode.Custom
                            else -> ReaderPrefetchMode.Smart
                        }
                        val pages = when (mode) {
                            ReaderPrefetchMode.Conservative -> 1
                            ReaderPrefetchMode.Aggressive -> 5
                            ReaderPrefetchMode.UltraAggressive -> 6
                            ReaderPrefetchMode.Smart -> 3
                            ReaderPrefetchMode.Custom -> settings.readerPrefetchPages
                        }
                        onSettingsChange(
                            settings.copy(
                                readerPrefetchMode = mode,
                                readerPrefetchPages = pages,
                                dataSaver = if (mode == ReaderPrefetchMode.UltraAggressive) false else settings.dataSaver,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding),
                )
                Text(
                    when (settings.readerPrefetchMode) {
                        ReaderPrefetchMode.Conservative -> "只保温相邻 1 页，优先降低内存和流量占用"
                        ReaderPrefetchMode.Aggressive -> "前后页并行保温，适合网速快且连续阅读"
                        ReaderPrefetchMode.UltraAggressive -> "首屏就绪后进入，沿滑动方向保持解码缓冲并提前准备下一话；流量、内存和存储占用最高"
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
                SettingsSwitchRow(
                    Icons.Outlined.FormatListNumbered,
                    "按顺序加载漫画页",
                    "开启后必须完成上一页，才会继续请求下一页；默认关闭",
                    settings.sequentialPageLoading,
                ) { onSettingsChange(settings.copy(sequentialPageLoading = it)) }
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
                    "Comic Plus · 阅读记录与下载保存在本机，JM 收藏通过官方接口同步",
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
                    val releaseNotes = remember(updateStatus.notes) {
                        markdownToAnnotatedString(updateStatus.notes.ifBlank { "本次发布未提供更新说明。" })
                    }
                    Text(
                        text = releaseNotes,
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
private fun JmAccountSettings(
    account: JmAccountUiState,
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSyncFavorites: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 4.dp),
        shape = RoundedCornerShape(CpDimens.cardRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(15.dp)) {
            when (account.status) {
                JmAccountStatus.SignedIn -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp).size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account.username.ifBlank { "JM 官方账号" }, color = Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                buildString {
                                    append("UID ")
                                    append(account.uid.ifBlank { "未知" })
                                    account.favoriteCount?.let { append(" · 收藏 $it 部") }
                                },
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "收藏操作将通过 JM 官方接口同步到账号",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    account.error?.takeIf(String::isNotBlank)?.let { error ->
                        Text(
                            error,
                            modifier = Modifier.padding(top = 7.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onSyncFavorites, enabled = !account.syncing) {
                            if (account.syncing) {
                                CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(if (account.syncing) "同步中" else "同步收藏")
                        }
                        TextButton(onClick = onLogout, enabled = !account.syncing) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("退出登录")
                        }
                    }
                }

                JmAccountStatus.Restoring -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text("正在恢复 JM 登录状态", color = Ink, style = MaterialTheme.typography.bodyLarge)
                            Text("正在读取官方收藏夹", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                else -> {
                    Text(
                        "密码会以加密形式保存在本机，仅用于登录状态失效时自动重新登录。",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(11.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = account.status != JmAccountStatus.SigningIn,
                        label = { Text("JM 用户名") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = account.status != JmAccountStatus.SigningIn,
                        label = { Text("JM 密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    account.error?.takeIf(String::isNotBlank)?.let { error ->
                        Text(
                            error,
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                    Button(
                        onClick = onLogin,
                        enabled = account.status != JmAccountStatus.SigningIn && username.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (account.status == JmAccountStatus.SigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("正在登录")
                        } else {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("登录 JM 官方账号")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCheckInCard(
    daily: JmDailyUiState,
    onLoad: () -> Unit,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(daily.status) {
        if (daily.status == JmDailyStatus.Idle) onLoad()
    }
    val info = daily.info
    val todaySigned = daily.confirmedToday || info?.isSignedToday() == true
    Surface(
        modifier = modifier.fillMaxWidth().padding(top = 13.dp),
        color = SurfaceSoft,
        shape = RoundedCornerShape(CpDimens.controlRadius),
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("每日签到", color = Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            daily.loading -> "正在读取官方签到状态…"
                            daily.checking -> "正在提交签到…"
                            todaySigned -> "今日已签到"
                            info?.dailyId.isNullOrBlank() -> "当前没有可用的签到活动"
                            else -> "签到可领取官方活动奖励"
                        },
                        color = if (daily.error != null) MaterialTheme.colorScheme.error else Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onLoad, enabled = !daily.loading && !daily.checking) {
                    Text("刷新")
                }
            }
            info?.let { current ->
                val signedCount = current.records.count { it.signed }
                Text(
                    "本月已签到 $signedCount 天${current.eventName.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
                    modifier = Modifier.padding(top = 8.dp),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onCheckIn,
                    enabled = !todaySigned && !daily.loading && !daily.checking && current.dailyId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    if (daily.checking) CircularProgressIndicator(Modifier.size(17.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (todaySigned) "今日已签到" else "立即签到")
                }
            }
            daily.message?.takeIf(String::isNotBlank)?.let { message ->
                Text(message, modifier = Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            daily.error?.takeIf(String::isNotBlank)?.let { error ->
                Text(error, modifier = Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
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

private fun formatReleaseDate(value: String): String = com.comicplus.pure.runCatchingNonFatal {
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
