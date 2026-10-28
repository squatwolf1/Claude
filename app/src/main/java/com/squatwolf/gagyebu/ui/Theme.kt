package com.squatwolf.gagyebu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** 글꼴은 따로 넣지 않는다. 폰에서 고른 시스템 글꼴을 그대로 따라간다. */
@Composable
fun GagyebuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Ink, background = Paper, surface = Paper),
        content = content,
    )
}
