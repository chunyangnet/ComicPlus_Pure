@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.comicplus.app.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.comicplus.app.ui.icons.ComicPlusIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import com.comicplus.pure.JmGateway
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.LocalComicPlusReduceMotion
import com.comicplus.app.ui.LocalFavoritePendingKeys
import com.comicplus.app.ui.key
import com.comicplus.app.ui.rememberDelayedBusyIndicator
import com.comicplus.app.ui.theme.Bronze
import com.comicplus.app.ui.theme.Gold
import com.comicplus.app.ui.theme.Ink
import com.comicplus.app.ui.theme.InkSoft
import com.comicplus.app.ui.theme.Line
import com.comicplus.app.ui.theme.Muted
import com.comicplus.app.ui.theme.Silver
import com.comicplus.app.ui.theme.SurfaceSoft
import com.comicplus.app.ui.theme.White
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CpDimens {
    val screenPadding = 18.dp
    val cardRadius = 16.dp
    val heroRadius = 22.dp
    val controlRadius = 14.dp
    val pillRadius = 50.dp
    val sectionGap = 30.dp
}

data class AppBarAction(
    val icon: ImageVector,
    val label: String,
    val prominent: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ComicPlusTopBar(
    title: String,
    actions: List<AppBarAction>,
    modifier: Modifier = Modifier,
    showLogo: Boolean = false,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(66.dp)
            .padding(horizontal = CpDimens.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        } else if (showLogo) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "C+",
                        color = White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = if (subtitle == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = if (subtitle == null) FontWeight.Normal else FontWeight.Bold,
            )
            subtitle?.let {
                Text(it, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.weight(1f))
        actions.forEach { action ->
            if (action.prominent) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CpDimens.pillRadius))
                        .clickable(onClick = action.onClick),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            action.label,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                IconButton(onClick = action.onClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = InkSoft,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SearchCapsule(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索漫画或输入 JM ID",
    hint: String? = null,
    focusRequester: FocusRequester? = null,
    showSearchAction: Boolean = true,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitSearch = {
        if (query.isNotBlank()) keyboardController?.hide()
        onSearch()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CpDimens.pillRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                    ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.merge(
                    TextStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(placeholder, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        inner()
                    }
                },
            )
            if (!hint.isNullOrBlank()) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        hint,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "清空搜索", tint = Muted, modifier = Modifier.size(17.dp))
                }
                if (showSearchAction) {
                    IconButton(onClick = submitSearch, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PillRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = CpDimens.screenPadding),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(labels.size, key = { index -> "pill-$index-${labels[index]}" }) { index ->
            val label = labels[index]
            Pill(label = label, selected = index == selectedIndex, onClick = { onSelected(index) })
        }
    }
}

@Composable
fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        tween(if (LocalComicPlusReduceMotion.current) 0 else 180),
        label = "pill-color",
    )
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(if (LocalComicPlusReduceMotion.current) 0 else 180),
        label = "pill-content",
    )
    Surface(
        modifier = modifier
            .pressFeedback(interactionSource, pressedScale = .95f)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(CpDimens.pillRadius),
        color = color,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun SegmentedControl(
    labels: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalComicPlusReduceMotion.current
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(CpDimens.controlRadius),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            if (labels.isEmpty()) return@BoxWithConstraints
            val selectedIndex = labels.indexOf(selected).coerceAtLeast(0)
            val segmentWidth = maxWidth / labels.size
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = .84f, stiffness = 620f),
                label = "segment-indicator-offset",
            )
            Surface(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(segmentWidth)
                    .height(40.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(11.dp),
                shadowElevation = 1.dp,
            ) {}
            Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                labels.forEach { label ->
                    val isSelected = label == selected
                    val interactionSource = remember(label) { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val contentColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(if (reduceMotion) 0 else 240),
                        label = "segment-content",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .pressFeedback(interactionSource, pressedScale = .965f)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                            ) { onSelected(label) },
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
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(9.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.weight(1f))
        if (onMore != null) {
            Row(
                modifier = Modifier.clickable(onClick = onMore).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("更多", color = Muted, style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
fun ComicPosterCard(
    comic: ComicUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(CpDimens.cardRadius)),
        ) {
            ComicCover(
                coverUrl = comic.coverUrl,
                title = comic.title,
                accentIndex = comic.accentIndex,
                modifier = Modifier.fillMaxSize(),
            )
            if (onToggleFavorite != null) {
                FavoriteButton(
                    isFavorite = isFavorite,
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    favoriteKey = comic.key,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            comic.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            color = Ink,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            comic.supportingLabel(preferMetric = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

@Composable
fun RankingRow(
    rank: Int,
    comic: ComicUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = rank <= 3,
	supportingText: String? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rankColor = when (rank) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> Muted
    }
    val background = if (prominent) rankColor.copy(alpha = .055f) else Color.Transparent
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        color = background,
        shape = RoundedCornerShape(CpDimens.cardRadius),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (prominent) 12.dp else 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rank.toString().padStart(2, '0'),
                color = rankColor,
                style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp),
            )
            Box(
                modifier = Modifier
                    .width(if (prominent) 70.dp else 52.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                ComicCover(comic.coverUrl, comic.title, comic.accentIndex, Modifier.fillMaxSize())
                if (onToggleFavorite != null) {
                    FavoriteButton(
                        isFavorite = isFavorite,
                        onClick = onToggleFavorite,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        compact = true,
                        favoriteKey = comic.key,
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    comic.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Ink,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(5.dp))
                Text(
					supportingText?.takeIf(String::isNotBlank) ?: comic.supportingLabel(preferMetric = true),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Line, modifier = Modifier.size(19.dp))
        }
    }
}

/** High-contrast cover action that remains legible over both light and dark artwork. */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    favoriteKey: String? = null,
) {
    val reduceMotion = LocalComicPlusReduceMotion.current
    val loading = favoriteKey?.let { it in LocalFavoritePendingKeys.current } == true
    val showLoading = rememberDelayedBusyIndicator(loading)
    val containerColor by animateColorAsState(
        targetValue = if (isFavorite) {
            MaterialTheme.colorScheme.primary.copy(alpha = .94f)
        } else {
            Color(0xD91B2029)
        },
        animationSpec = tween(if (reduceMotion) 0 else 180),
        label = "favorite-button-container",
    )
    val size = if (compact) 28.dp else 36.dp
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = containerColor,
        shadowElevation = if (isFavorite) 3.dp else 1.dp,
    ) {
        IconButton(onClick = onClick, enabled = !loading, modifier = Modifier.fillMaxSize()) {
            if (showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 14.dp else 18.dp),
                    color = White,
                    strokeWidth = if (compact) 1.7.dp else 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = when {
                        loading -> "收藏同步中"
                        isFavorite -> "取消收藏"
                        else -> "加入收藏"
                    },
                    tint = White,
                    modifier = Modifier.size(if (compact) 15.dp else 19.dp),
                )
            }
        }
    }
}

@Composable
fun ComicCover(
    coverUrl: String?,
    title: String,
    accentIndex: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var measuredSize by remember(coverUrl) { mutableStateOf(IntSize.Zero) }
    if (coverUrl.isNullOrBlank()) {
        CoverFallback(title, accentIndex, modifier)
        return
    }
    val headers = remember {
        NetworkHeaders.Builder().apply {
            JmGateway.imageRequestHeaders().forEach { (name, value) -> set(name, value) }
        }.build()
    }
    val decodeSize = remember(measuredSize) { optimizedCoverDecodeSize(measuredSize) }
    val cacheIdentity = remember(coverUrl) { canonicalCoverCacheIdentity(coverUrl) }
    val request = remember(context, coverUrl, cacheIdentity, headers, decodeSize) {
        if (decodeSize == IntSize.Zero) null else {
            ImageRequest.Builder(context)
                .data(coverUrl)
                .httpHeaders(headers)
                // Keep one stable memory entry per URL/size pair even when the
                // surrounding list item is recomposed or moved off-screen.
                .memoryCacheKey("cover:$cacheIdentity:${decodeSize.width}x${decodeSize.height}")
                // Disk stores the fetched source and can be reused for cards,
                // rankings, and the larger detail header.
                .diskCacheKey("cover:$cacheIdentity")
                .size(decodeSize.width, decodeSize.height)
                .scale(Scale.FILL)
                .precision(Precision.INEXACT)
                .crossfade(120)
                .build()
        }
    }
    Box(modifier.onSizeChanged { measuredSize = it }) {
        CoverFallback(title, accentIndex, Modifier.fillMaxSize())
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val JM_COVER_CACHE_PATH = Regex(
    "^/media/albums/[A-Za-z0-9_-]{1,128}\\.(?:jpg|jpeg|png|webp)$",
    RegexOption.IGNORE_CASE,
)

internal fun canonicalCoverCacheIdentity(url: String): String {
    val parsed = url.toHttpUrlOrNull() ?: return url
    if (!JM_COVER_CACHE_PATH.matches(parsed.encodedPath)) return url
    return buildString {
        append(parsed.encodedPath)
        parsed.encodedQuery?.let { query -> append('?').append(query) }
    }
}

internal fun optimizedCoverDecodeSize(size: IntSize): IntSize {
    if (size.width <= 0 || size.height <= 0) return IntSize.Zero
    val longEdge = maxOf(size.width, size.height)
    val scale = minOf(1f, MAX_COVER_DECODE_EDGE_PX / longEdge.toFloat())
    fun bucket(value: Int): Int = ((value * scale).toInt().coerceAtLeast(1) + COVER_SIZE_BUCKET_PX - 1) /
        COVER_SIZE_BUCKET_PX * COVER_SIZE_BUCKET_PX
    return IntSize(
        width = bucket(size.width).coerceAtMost(MAX_COVER_DECODE_EDGE_PX),
        height = bucket(size.height).coerceAtMost(MAX_COVER_DECODE_EDGE_PX),
    )
}

@Composable
private fun CoverFallback(
    title: String,
    accentIndex: Int,
    modifier: Modifier,
) {
    val colors = coverGradient(accentIndex)
    Box(modifier.background(Brush.linearGradient(colors))) {
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text("JM", color = White.copy(alpha = .72f), style = MaterialTheme.typography.labelMedium)
            Text(
                title.take(12),
                color = White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
fun rememberMotionAllowed(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        } catch (_: Exception) {
            true
        }
    }
}

@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = .98f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motionAllowed = !LocalComicPlusReduceMotion.current && rememberMotionAllowed()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motionAllowed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = .78f, stiffness = 820f),
        label = "press-feedback-scale",
    )
    return scale(scale)
}

internal fun ComicUiItem.supportingLabel(preferMetric: Boolean): String {
    val primary = if (preferMetric) metric else subtitle
    val secondary = if (preferMetric) subtitle else metric
    return primary.takeIf(String::isNotBlank)
        ?: secondary.takeIf(String::isNotBlank)
        ?: "查看详情"
}

private fun coverGradient(index: Int): List<Color> = when (index.mod(6)) {
    0 -> listOf(Color(0xFF183C64), Color(0xFF4A7FA8))
    1 -> listOf(Color(0xFF6E394A), Color(0xFFC97D83))
    2 -> listOf(Color(0xFF31584C), Color(0xFF7AA88E))
    3 -> listOf(Color(0xFF5B4932), Color(0xFFD0A867))
    4 -> listOf(Color(0xFF493F69), Color(0xFF9284C5))
    else -> listOf(Color(0xFF30495A), Color(0xFF7D99A7))
}

private const val COVER_SIZE_BUCKET_PX = 64
private const val MAX_COVER_DECODE_EDGE_PX = 1088
