package com.squatwolf.gagyebu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.squatwolf.gagyebu.R

/** 개구(Gaegu) 손글씨체, SIL OFL 1.1 (licenses/Gaegu-OFL.txt). */
val Gaegu = FontFamily(
    Font(R.font.gaegu_regular, FontWeight.Normal),
    Font(R.font.gaegu_bold, FontWeight.Bold),
)

private val base = Typography()

private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Gaegu),
    displayMedium = base.displayMedium.copy(fontFamily = Gaegu),
    displaySmall = base.displaySmall.copy(fontFamily = Gaegu),
    headlineLarge = base.headlineLarge.copy(fontFamily = Gaegu),
    headlineMedium = base.headlineMedium.copy(fontFamily = Gaegu),
    headlineSmall = base.headlineSmall.copy(fontFamily = Gaegu),
    titleLarge = base.titleLarge.copy(fontFamily = Gaegu),
    titleMedium = base.titleMedium.copy(fontFamily = Gaegu),
    titleSmall = base.titleSmall.copy(fontFamily = Gaegu),
    bodyLarge = base.bodyLarge.copy(fontFamily = Gaegu),
    bodyMedium = base.bodyMedium.copy(fontFamily = Gaegu),
    bodySmall = base.bodySmall.copy(fontFamily = Gaegu),
    labelLarge = base.labelLarge.copy(fontFamily = Gaegu),
    labelMedium = base.labelMedium.copy(fontFamily = Gaegu),
    labelSmall = base.labelSmall.copy(fontFamily = Gaegu),
)

@Composable
fun GagyebuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Ink, background = Paper, surface = Paper),
        typography = typography,
        content = content,
    )
}
