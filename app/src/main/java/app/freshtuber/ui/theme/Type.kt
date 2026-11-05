package app.freshtuber.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.freshtuber.R

/** Source Serif 4 (OFL), one variable file with every weight the app uses. */
private val SourceSerif = FontFamily(
    listOf(300, 400, 450, 500, 600, 700).map { weight ->
        Font(R.font.source_serif_4, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

/** Lining, tabular numerals for durations, times and counters. */
const val TabularNumbers = "lnum, tnum"

/** Extra text roles of the liquid-light design, in the current family. */
object AppType {
    /** Screen titles such as «Главная». */
    val screenTitle: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineLarge
        .merge(TextStyle(fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.02).em))
    val playerTitle: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleLarge
        .merge(TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight(450)))
    val cardTitle: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleMedium
        .merge(TextStyle(fontSize = 15.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp))
    val meta: TextStyle @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodySmall
        .merge(TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontFeatureSettings = TabularNumbers))
}

fun appFontFamily(serif: Boolean): FontFamily = if (serif) SourceSerif else FontFamily.Default

/** Material's type scale in the chosen family. */
fun appTypography(serif: Boolean): Typography {
    val family = appFontFamily(serif)
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(), titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
}
