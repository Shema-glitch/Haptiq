package com.haptiq.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.haptiq.app.ui.theme.ColorOnSurface60
import com.haptiq.app.ui.theme.ColorPrimary
import com.haptiq.app.ui.theme.Spacing

/**
 * The single canonical "HAPTIQ" wordmark treatment, used by every screen's top bar.
 * Before this existed, four screens each hand-rolled their own size/weight/tracking —
 * this is the one place that decides what the app's own name looks like.
 *
 * @param centered use inside a [androidx.compose.material3.CenterAlignedTopAppBar];
 *   leave false inside a left-aligned Row-based header (e.g. Library's).
 */
@Composable
fun HaptiqWordmark(subtitle: String, centered: Boolean = false) {
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            text = "HAPTIQ",
            style = MaterialTheme.typography.headlineMedium,
            color = ColorPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = ColorOnSurface60
        )
    }
}

/**
 * The one screen header for every tab: wordmark left, optional action right.
 * Library, Albums, Favorites and Settings all render this — one screen using a
 * centered app bar while another left-aligns was exactly the "another world"
 * inconsistency between tabs.
 */
@Composable
fun HaptiqScreenHeader(
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xl, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HaptiqWordmark(subtitle = subtitle)
        trailing?.invoke()
    }
}
