package com.runwithfun.familycare.domain

data class CareDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(year in 1..9999) { "Year must be between 1 and 9999" }
        require(month in 1..12) { "Month must be between 1 and 12" }
        val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val lastDay = when (month) {
            2 -> if (leapYear) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        require(day in 1..lastDay) { "Day must be valid for its month and year" }
    }

    override fun toString(): String =
        "${year.toString().padStart(4, '0')}-${month.twoDigits()}-${day.twoDigits()}"
}

data class CareTime(val hour: Int, val minute: Int) : Comparable<CareTime> {
    init {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
    }

    override fun compareTo(other: CareTime): Int =
        compareValuesBy(this, other, CareTime::hour, CareTime::minute)

    override fun toString(): String = "${hour.twoDigits()}:${minute.twoDigits()}"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
