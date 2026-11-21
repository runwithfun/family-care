package com.runwithfun.familycare.ui.home

import com.runwithfun.familycare.domain.CareDate

private val WeekdayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val MonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

internal fun CareDate.weekdayName(): String = WeekdayNames[ordinalDay() % 7]

internal fun CareDate.monthAndDay(): String = "${MonthNames[month - 1]} $day"

internal fun weekContaining(date: CareDate): List<CareDate?> {
    val sundayOffset = date.ordinalDay() % 7
    return (0..6).map { date.shiftDays(it - sundayOffset) }
}

internal fun childAge(birthDate: CareDate, asOf: CareDate): String? {
    val days = asOf.ordinalDay() - birthDate.ordinalDay()
    if (days < 0) return null
    if (days < 7) return "Newborn"
    val months = (asOf.year - birthDate.year) * 12 + asOf.month - birthDate.month -
        if (asOf.day < birthDate.day) 1 else 0
    return when {
        months < 3 -> ageLabel(days / 7, "week")
        months < 24 -> ageLabel(months, "month")
        else -> ageLabel(months / 12, "year")
    }
}

private fun ageLabel(count: Int, unit: String): String = "$count ${if (count == 1) unit else unit + "s"} old"

private fun CareDate.ordinalDay(): Int {
    val previousYear = year - 1
    val completedYears = previousYear * 365 + previousYear / 4 - previousYear / 100 + previousYear / 400
    return completedYears + (1 until month).sumOf { monthLength(year, it) } + day
}

private fun CareDate.shiftDays(days: Int): CareDate? {
    var newYear = year
    var newMonth = month
    var newDay = day + days
    if (newDay < 1) {
        if (--newMonth == 0) {
            newMonth = 12
            if (--newYear == 0) return null
        }
        newDay += monthLength(newYear, newMonth)
    } else {
        val length = monthLength(newYear, newMonth)
        if (newDay > length) {
            newDay -= length
            if (++newMonth == 13) {
                newMonth = 1
                if (++newYear == 10000) return null
            }
        }
    }
    return CareDate(newYear, newMonth, newDay)
}

private fun monthLength(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}
