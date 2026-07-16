package com.haptiq.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.haptiq.app.data.Song
import com.haptiq.app.ui.MiniPlayer
import com.haptiq.app.ui.theme.*

/**
 * Shared scaffold with:
 * - Custom bottom nav: dot-indicator active state (no M3 pill/rectangle)
 * - MiniPlayer sits ABOVE the nav bar inside content column
 */
@Composable
fun HaptiqScaffold(
    navController: NavHostController,
    currentRoute: String?,
    currentSong: Song?,
    isPlaying: Boolean,
    playbackProgress: Float,
    hapticActive: Boolean,
    onTogglePlayPause: () -> Unit,
    onNextClicked: () -> Unit,
    onPrevClicked: () -> Unit,
    onMiniPlayerExpanded: () -> Unit,
    onMiniPlayerDismissed: () -> Unit = {},
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ColorBackground,
        bottomBar = {
            HaptiqBottomNav(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Routes.LIBRARY) { inclusive = route == Routes.LIBRARY }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        // The app's three-part anatomy, owned in ONE place: top bar / main / bottom
        // chrome (mini player + nav). Screens supply only their main content.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            topBar()
            Box(modifier = Modifier.weight(1f)) {
                content(innerPadding)
            }
            MiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                progress = playbackProgress,
                hapticActive = hapticActive,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNextClicked,
                onPrev = onPrevClicked,
                onExpand = onMiniPlayerExpanded,
                onDismiss = onMiniPlayerDismissed
            )
        }
    }
}

// ─── Custom Bottom Nav ──────────────────────────────────────
private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val navItems = listOf(
    NavItem(Routes.LIBRARY,   "Library",   Icons.Default.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavItem(Routes.ALBUMS,    "Albums",    Icons.Default.Album,         Icons.Outlined.Album),
    NavItem(Routes.FAVORITES, "Favorites", Icons.Default.Favorite,      Icons.Outlined.Favorite),
    NavItem(Routes.PLAYLISTS, "Playlists", Icons.AutoMirrored.Filled.PlaylistPlay, Icons.AutoMirrored.Filled.PlaylistPlay),
    NavItem(Routes.SETTINGS,  "Settings",  Icons.Default.Settings,      Icons.Outlined.Settings),
)

@Composable
private fun HaptiqBottomNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Surface(
            // Clay-style chunky border: the nav reads as a soft physical slab,
            // matching the app's tactile premise (border, not glow).
            modifier = Modifier
                .fillMaxWidth()
                .height(ComponentSize.navBarHeight)
                .border(2.dp, ColorOutlineVariant, ExpressiveShapes.navPill),
            color = ColorSurfaceContainerHigh,
            shape = ExpressiveShapes.navPill,
            shadowElevation = Elevation.high,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { item ->
                    val isSelected = currentRoute == item.route
                    HaptiqNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = {
                            if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigate(item.route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HaptiqNavItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconAlpha by animateFloatAsState(
        // Unselected was 0.5 alpha over the already-muted OnSurface60 — ~30% effective
        // contrast, which reads as "disabled" and risks failing WCAG. Keep unselected
        // clearly legible (they're destinations, not disabled) and let colour, not
        // dimming, carry the selected state.
        targetValue = if (isSelected) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "icon_alpha"
    )
    val dotWidth by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "dot_width"
    )

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint = if (isSelected) ColorPrimary else ColorOnSurfaceVariant,
            modifier = Modifier
                .size(ComponentSize.iconLarge)
                .alpha(iconAlpha)
        )

        // Active dot indicator — animates in/out via width
        Box(
            modifier = Modifier
                .width(dotWidth)
                .height(3.dp)
                .clip(CircleShape)
                .background(ColorPrimary)
        )
    }
}
