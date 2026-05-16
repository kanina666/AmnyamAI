package com.example.amnyamai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun AmNyamGif(
    asset: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx)
            .data("file:///android_asset/$asset")
            .crossfade(false)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale
    )
}
