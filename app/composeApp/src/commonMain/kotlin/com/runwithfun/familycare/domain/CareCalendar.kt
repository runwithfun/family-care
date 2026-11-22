package com.runwithfun.familycare.domain

object CareCalendar {
    const val MinYear = 1
    const val MaxYear = 9999

    private val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    fun monthLabel(year: Int, month: Int): String = "${monthNames[month - 1]} $year"

    fun lengthOfMonth(year: Int, month: Int): Int {
        val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        return when (month) {
            2 -> if (leapYear) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }

    fun shiftMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val absolute = year * 12 + (month - 1) + delta
        val nextYear = absolute.floorDiv(12)
        val nextMonth = absolute.mod(12) + 1
        return nextYear to nextMonth
    }

    fun canShiftMonth(year: Int, month: Int, delta: Int): Boolean {
        val (nextYear, _) = shiftMonth(year, month, delta)
        return nextYear in MinYear..MaxYear
    }

    fun shiftMonthBounded(year: Int, month: Int, delta: Int): Pair<Int, Int>? {
        if (!canShiftMonth(year, month, delta)) return null
        return shiftMonth(year, month, delta)
    }

    fun clampDay(year: Int, month: Int, day: Int): CareDate =
        CareDate(year, month, day.coerceIn(1, lengthOfMonth(year, month)))

    // Monday = 0 … Sunday = 6
    fun dayOfWeekMondayBased(year: Int, month: Int, day: Int): Int {
        var y = year
        var m = month
        if (m < 3) {
            m += 12
            y -= 1
        }
        val k = y % 100
        val j = y / 100
        val h = (day + (13 * (m + 1)) / 5 + k + k / 4 + j / 4 + 5 * j) % 7
        return (h + 5) % 7
    }

    fun monthCells(year: Int, month: Int): List<CareDate?> {
        val leading = dayOfWeekMondayBased(year, month, 1)
        val days = lengthOfMonth(year, month)
        val cells = MutableList<CareDate?>(leading) { null }
        for (day in 1..days) {
            cells += CareDate(year, month, day)
        }
        while (cells.size % 7 != 0) {
            cells += null
        }
        return cells
    }
}
