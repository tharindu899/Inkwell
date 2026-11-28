package com.inkwell.notes.util

import com.inkwell.notes.model.Note
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun Long.toDay(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** 98 levels (0..4), oldest first = 14 weeks x 7 days. Same buckets as the web activity grid. */
fun activityLevels(notes: List<Note>, today: LocalDate = LocalDate.now()): List<Int> {
    val counts = notes.groupingBy { (if (it.updatedAt > 0) it.updatedAt else it.createdAt).toDay() }.eachCount()
    val max = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    return List(98) { i ->
        val v = counts[today.minusDays((97 - i).toLong())] ?: 0
        val r = v.toFloat() / max
        when {
            v == 0 -> 0
            r < 0.25f -> 1
            r < 0.5f -> 2
            r < 0.75f -> 3
            else -> 4
        }
    }
}

/** (current streak, longest streak). Same rules as the web calcStreak(): the streak needs a note today. */
fun writingStreaks(notes: List<Note>, today: LocalDate = LocalDate.now()): Pair<Int, Int> {
    val days = notes.map { it.updatedAt.toDay() }.toSet()
    var streak = 0
    var best = 0
    var cur = 0
    for (i in 0 until 365) {
        if (today.minusDays(i.toLong()) in days) {
            cur++
            best = maxOf(best, cur)
            if (i == 0 || streak > 0) streak = cur
        } else if (i > 0) {
            cur = 0
        }
    }
    return streak to best
}
