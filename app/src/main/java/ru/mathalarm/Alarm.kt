package ru.mathalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Будильник.
 * days — битовая маска дней: бит 0 = понедельник ... бит 6 = воскресенье. 0 = сработает один раз.
 * soundUri — null означает стандартный звук будильника.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Int,
    val soundUri: String?,
    val enabled: Boolean
) {
    fun nextTriggerMillis(now: LocalDateTime = LocalDateTime.now()): Long {
        val base = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        for (offset in 0..7) {
            val candidate = base.plusDays(offset.toLong())
            if (!candidate.isAfter(now)) continue
            val dayBit = 1 shl (candidate.dayOfWeek.value - 1)
            if (days == 0 || (days and dayBit) != 0) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return base.plusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

val DAY_NAMES = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

fun daysText(mask: Int): String = when (mask) {
    0 -> "ОДИН РАЗ"
    127 -> "КАЖДЫЙ ДЕНЬ"
    31 -> "ПН–ПТ"
    96 -> "СБ–ВС"
    else -> DAY_NAMES.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.joinToString(" ")
}

fun two(n: Int): String = n.toString().padStart(2, '0')

fun untilText(triggerMillis: Long): String {
    val diff = (triggerMillis - System.currentTimeMillis()).coerceAtLeast(0)
    val totalMin = (diff + 59_999) / 60_000
    val d = totalMin / 1440
    val h = (totalMin % 1440) / 60
    val m = totalMin % 60
    return buildString {
        if (d > 0) append("$d д ")
        if (h > 0 || d > 0) append("$h ч ")
        append("$m мин")
    }
}

object AlarmStore {
    private const val PREFS = "mathalarm"
    private const val KEY_LIST = "alarms"
    private const val KEY_RINGING = "ringing_id"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context): List<Alarm> {
        return try {
            val arr = JSONArray(prefs(c).getString(KEY_LIST, "[]"))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Alarm(
                    id = o.getInt("id"),
                    hour = o.getInt("h"),
                    minute = o.getInt("m"),
                    days = o.getInt("d"),
                    soundUri = o.optString("s", "").ifEmpty { null },
                    enabled = o.getBoolean("e")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(c: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("h", a.hour)
                    .put("m", a.minute)
                    .put("d", a.days)
                    .put("s", a.soundUri ?: "")
                    .put("e", a.enabled)
            )
        }
        prefs(c).edit().putString(KEY_LIST, arr.toString()).commit()
    }

    fun nextId(list: List<Alarm>): Int = (list.maxOfOrNull { it.id } ?: 0) + 1

    /** id звонящего сейчас будильника, -1 — ничего не звонит. */
    fun ringingId(c: Context): Int = prefs(c).getInt(KEY_RINGING, -1)

    fun setRinging(c: Context, id: Int) {
        prefs(c).edit().putInt(KEY_RINGING, id).commit()
    }
}
