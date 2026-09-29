package it.emanuelemelini.photocal.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Round profile photo, or the initial of [name] when there is none. Decorative: no description. */
@Composable
fun Avatar(image: ImageBitmap?, name: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val shape = CircleShape
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase().orEmpty(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}
