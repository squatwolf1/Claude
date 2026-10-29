package com.squatwolf.gagyebu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** 글꼴은 따로 넣지 않는다. 폰에서 고른 시스템 글꼴을 그대로 따라간다. */
@Composable
fun GagyebuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Ink, background = Paper, surface = Paper),
        content = content,
    )
}

/**
 * 창 너비에 맞춰 화면 전체 배율을 바꾼다. 분할 화면처럼 창이 좁으면 작게, 태블릿처럼 넓으면 크게.
 * userScale 은 설정에서 고른 화면 크기(0.7~1.3)이고 여기에 곱한다.
 */
@Composable
fun AutoScale(userScale: Float, content: @Composable () -> Unit) {
    val width = LocalConfiguration.current.screenWidthDp
    val auto = (width / 400f).coerceIn(0.7f, 1.25f)
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * auto * userScale, base.fontScale),
        content = content,
    )
}
