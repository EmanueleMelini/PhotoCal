package it.emanuelemelini.photocal.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.emanuelemelini.photocal.AppIcon
import it.emanuelemelini.photocal.R

/** Launcher icon choice, with a preview of each icon. */
@Composable
fun AppIconPicker() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppIcon.current(context)) }

    Text(stringResource(R.string.app_icon_title), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        AppIcon.entries.forEach { icon ->
            val isSelected = icon == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = {
                        if (!isSelected) {
                            AppIcon.select(context, icon)
                            selected = icon
                        }
                    },
                ),
            ) {
                IconPreview(
                    icon = icon,
                    modifier = Modifier.border(
                        width = 3.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                    ),
                )
                Text(
                    stringResource(icon.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    Text(
        stringResource(R.string.app_icon_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Adaptive icons can't be drawn by painterResource: background and foreground vectors are
 * stacked instead, enlarged 1.5x like the launcher mask (72dp visible out of 108dp).
 */
@Composable
private fun IconPreview(icon: AppIcon, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(56.dp)
            .clip(CircleShape),
    ) {
        val enlarged = Modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = 1.5f, scaleY = 1.5f)
        Image(painterResource(icon.backgroundRes), contentDescription = null, contentScale = ContentScale.Crop, modifier = enlarged)
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = enlarged)
    }
}
