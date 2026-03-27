package com.joaobzao.capas.capas

object RelativeDateFormatter {

    private val monthNames = mapOf(
        "pt" to listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"),
        "en" to listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
        "es" to listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    )

    private val todayStrings = mapOf("pt" to "Hoje", "en" to "Today", "es" to "Hoy")
    private val yesterdayStrings = mapOf("pt" to "Ontem", "en" to "Yesterday", "es" to "Ayer")

    private fun daysAgoString(days: Int, language: String): String = when (language) {
        "pt" -> "Há $days dias"
        "es" -> "Hace $days días"
        else -> "$days days ago"
    }

    /**
     * Formats a date string as a relative date.
     *
     * @param dateString ISO date "YYYY-MM-DD", or null
     * @param todayString ISO date "YYYY-MM-DD" representing today
     * @param language "pt", "en", or "es"
     * @param includeYear if true, appends the year for dates older than 7 days
     * @return formatted string, or null if dateString is null or unparseable
     */
    fun formatRelativeDate(
        dateString: String?,
        todayString: String,
        language: String,
        includeYear: Boolean = false
    ): String? {
        if (dateString == null) return null

        val dateParts = dateString.split("-")
        val todayParts = todayString.split("-")
        if (dateParts.size != 3 || todayParts.size != 3) return null

        val dateYear = dateParts[0].toIntOrNull() ?: return null
        val dateMonth = dateParts[1].toIntOrNull() ?: return null
        val dateDay = dateParts[2].toIntOrNull() ?: return null

        val todayYear = todayParts[0].toIntOrNull() ?: return null
        val todayMonth = todayParts[1].toIntOrNull() ?: return null
        val todayDay = todayParts[2].toIntOrNull() ?: return null

        val dateDays = toEpochDay(dateYear, dateMonth, dateDay)
        val todayDays = toEpochDay(todayYear, todayMonth, todayDay)
        val diff = todayDays - dateDays

        val lang = if (language in monthNames) language else "en"

        return when {
            diff < 0 -> formatShortDate(dateDay, dateMonth, dateYear, lang, includeYear)
            diff == 0L -> todayStrings[lang] ?: todayStrings["en"]!!
            diff == 1L -> yesterdayStrings[lang] ?: yesterdayStrings["en"]!!
            diff in 2..7 -> daysAgoString(diff.toInt(), lang)
            else -> formatShortDate(dateDay, dateMonth, dateYear, lang, includeYear)
        }
    }

    private fun formatShortDate(day: Int, month: Int, year: Int, language: String, includeYear: Boolean): String {
        val months = monthNames[language] ?: monthNames["en"]!!
        val monthName = months[month - 1]
        return if (includeYear) "$day $monthName $year" else "$day $monthName"
    }

    private fun toEpochDay(year: Int, month: Int, day: Int): Long {
        val y = year.toLong()
        val m = month.toLong()
        val d = day.toLong()

        // Days from years
        var total = 365 * y + (y / 4) - (y / 100) + (y / 400)

        // Days from months (using March-based trick)
        val daysInMonths = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        total += daysInMonths[(m - 1).toInt()]

        // Add leap day if after February in a leap year
        if (m > 2 && isLeapYear(year)) total += 1

        total += d

        return total
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
