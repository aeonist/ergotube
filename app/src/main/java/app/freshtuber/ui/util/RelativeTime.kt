package app.freshtuber.ui.util

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

enum class AgeUnit { Seconds, Minutes, Hours, Days, Weeks, Months, Years }

data class Age(val amount: Long, val unit: AgeUnit)

/** Picks the unit YouTube would show for something [ageMillis] old ("5 hours", "2 weeks"). */
fun ageOf(ageMillis: Long): Age {
    val s = (ageMillis / 1000).coerceAtLeast(0)
    return when {
        s < MINUTE -> Age(s, AgeUnit.Seconds)
        s < HOUR -> Age(s / MINUTE, AgeUnit.Minutes)
        s < DAY -> Age(s / HOUR, AgeUnit.Hours)
        s < WEEK -> Age(s / DAY, AgeUnit.Days)
        s < MONTH -> Age(s / WEEK, AgeUnit.Weeks)
        s < YEAR -> Age(s / MONTH, AgeUnit.Months)
        else -> Age(s / YEAR, AgeUnit.Years)
    }
}

/** "8 лет назад" / "8 years ago" in the language the UI is shown in. */
@Composable
fun relativeTimeText(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val age = ageOf(System.currentTimeMillis() - epochMillis)
    return remember(age, locale) {
        RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale)).format(
            age.amount.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            age.unit.toIcu(),
        )
    }
}

private fun AgeUnit.toIcu(): RelativeDateTimeFormatter.RelativeUnit = when (this) {
    AgeUnit.Seconds -> RelativeDateTimeFormatter.RelativeUnit.SECONDS
    AgeUnit.Minutes -> RelativeDateTimeFormatter.RelativeUnit.MINUTES
    AgeUnit.Hours -> RelativeDateTimeFormatter.RelativeUnit.HOURS
    AgeUnit.Days -> RelativeDateTimeFormatter.RelativeUnit.DAYS
    AgeUnit.Weeks -> RelativeDateTimeFormatter.RelativeUnit.WEEKS
    AgeUnit.Months -> RelativeDateTimeFormatter.RelativeUnit.MONTHS
    AgeUnit.Years -> RelativeDateTimeFormatter.RelativeUnit.YEARS
}

private const val MINUTE = 60L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY
private const val MONTH = 30 * DAY
private const val YEAR = 365 * DAY
