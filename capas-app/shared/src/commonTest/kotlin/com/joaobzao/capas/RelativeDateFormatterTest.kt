package com.joaobzao.capas

import com.joaobzao.capas.capas.RelativeDateFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelativeDateFormatterTest {

    @Test
    fun nullInputReturnsNull() {
        assertNull(RelativeDateFormatter.formatRelativeDate(null, "2026-03-27", "pt"))
    }

    @Test
    fun invalidDateReturnsNull() {
        assertNull(RelativeDateFormatter.formatRelativeDate("invalid", "2026-03-27", "pt"))
    }

    @Test
    fun todayPortuguese() {
        assertEquals("Hoje", RelativeDateFormatter.formatRelativeDate("2026-03-27", "2026-03-27", "pt"))
    }

    @Test
    fun todayEnglish() {
        assertEquals("Today", RelativeDateFormatter.formatRelativeDate("2026-03-27", "2026-03-27", "en"))
    }

    @Test
    fun todaySpanish() {
        assertEquals("Hoy", RelativeDateFormatter.formatRelativeDate("2026-03-27", "2026-03-27", "es"))
    }

    @Test
    fun yesterdayPortuguese() {
        assertEquals("Ontem", RelativeDateFormatter.formatRelativeDate("2026-03-26", "2026-03-27", "pt"))
    }

    @Test
    fun yesterdayEnglish() {
        assertEquals("Yesterday", RelativeDateFormatter.formatRelativeDate("2026-03-26", "2026-03-27", "en"))
    }

    @Test
    fun yesterdaySpanish() {
        assertEquals("Ayer", RelativeDateFormatter.formatRelativeDate("2026-03-26", "2026-03-27", "es"))
    }

    @Test
    fun threeDaysAgoPortuguese() {
        assertEquals("Há 3 dias", RelativeDateFormatter.formatRelativeDate("2026-03-24", "2026-03-27", "pt"))
    }

    @Test
    fun threeDaysAgoEnglish() {
        assertEquals("3 days ago", RelativeDateFormatter.formatRelativeDate("2026-03-24", "2026-03-27", "en"))
    }

    @Test
    fun threeDaysAgoSpanish() {
        assertEquals("Hace 3 días", RelativeDateFormatter.formatRelativeDate("2026-03-24", "2026-03-27", "es"))
    }

    @Test
    fun sevenDaysAgoIsRelative() {
        assertEquals("Há 7 dias", RelativeDateFormatter.formatRelativeDate("2026-03-20", "2026-03-27", "pt"))
    }

    @Test
    fun eightDaysAgoIsShortDate() {
        assertEquals("19 Mar", RelativeDateFormatter.formatRelativeDate("2026-03-19", "2026-03-27", "pt"))
    }

    @Test
    fun shortDateWithYear() {
        assertEquals(
            "19 Mar 2026",
            RelativeDateFormatter.formatRelativeDate("2026-03-19", "2026-03-27", "pt", includeYear = true)
        )
    }

    @Test
    fun yearBoundary() {
        assertEquals("Yesterday", RelativeDateFormatter.formatRelativeDate("2025-12-31", "2026-01-01", "en"))
    }

    @Test
    fun yearBoundaryMultipleDays() {
        assertEquals("3 days ago", RelativeDateFormatter.formatRelativeDate("2025-12-30", "2026-01-02", "en"))
    }

    @Test
    fun futureDateFallsBackToShortDate() {
        assertEquals("28 Mar", RelativeDateFormatter.formatRelativeDate("2026-03-28", "2026-03-27", "pt"))
    }

    @Test
    fun unknownLanguageFallsBackToEnglish() {
        assertEquals("Today", RelativeDateFormatter.formatRelativeDate("2026-03-27", "2026-03-27", "de"))
    }

    @Test
    fun monthAbbreviationsPortuguese() {
        assertEquals(
            "15 Fev 2026",
            RelativeDateFormatter.formatRelativeDate("2026-02-15", "2026-03-27", "pt", includeYear = true)
        )
    }

    @Test
    fun monthAbbreviationsSpanish() {
        assertEquals(
            "15 Ene 2026",
            RelativeDateFormatter.formatRelativeDate("2026-01-15", "2026-03-27", "es", includeYear = true)
        )
    }
}
