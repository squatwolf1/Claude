package com.squatwolf.busalarm

import android.content.Context
import java.time.LocalDateTime
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class Kind { BUS, SUBWAY }

/** 서울시 실시간 지하철 API 의 호선 ID. */
val subwayLines = linkedMapOf(
    "1075" to "수인분당선",
    "1077" to "신분당선",
    "1001" to "1호선",
    "1002" to "2호선",
    "1003" to "3호선",
    "1004" to "4호선",
    "1005" to "5호선",
    "1006" to "6호선",
    "1007" to "7호선",
    "1008" to "8호선",
    "1009" to "9호선",
    "1063" to "경의중앙선",
    "1065" to "공항철도",
    "1067" to "경춘선",
    "1081" to "경강선",
    "1093" to "서해선",
    "1032" to "GTX-A",
)

/** 2호선만 내선/외선, 나머지는 상행/하행. */
fun directionsOf(lineId: String) = if (lineId == "1002") listOf("내선", "외선") else listOf("상행", "하행")

/**
 * 알림 하나. [days] 는 DayOfWeek 값(1=월 … 7=일), [start]·[end] 는 하루 중 분.
 * end 가 start 보다 이르면 자정을 넘기는 시간대로 본다.
 */
data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val kind: Kind = Kind.BUS,
    val stationId: String = "",
    val stationName: String = "",
    val routes: List<String> = emptyList(),
    val lineId: String = "1075",
    val direction: String = "상행",
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val start: Int = 7 * 60 + 30,
    val end: Int = 8 * 60 + 30,
    val leadMin: Int = 3,
) {
    val title: String
        get() = when (kind) {
            Kind.BUS -> "$stationName · " + routes.ifEmpty { listOf("모든 버스") }.joinToString(", ")
            Kind.SUBWAY -> "${subwayLines[lineId] ?: lineId} $stationName $direction"
        }

    val scheduleText: String
        get() {
            val d = when (days) {
                setOf(1, 2, 3, 4, 5) -> "평일"
                setOf(6, 7) -> "주말"
                (1..7).toSet() -> "매일"
                else -> days.sorted().joinToString("") { dayNames[it - 1] }
            }
            return "$d ${hm(start)}~${hm(end)} · ${leadMin}분 전 알림"
        }

    fun isActive(now: LocalDateTime): Boolean {
        val m = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value in days
        return if (start < end) {
            today && m >= start && m < end
        } else {
            (today && m >= start) || (now.minusDays(1).dayOfWeek.value in days && m < end)
        }
    }

    fun nextStart(now: LocalDateTime): LocalDateTime? {
        for (d in 0L..7L) {
            val date = now.toLocalDate().plusDays(d)
            if (date.dayOfWeek.value !in days) continue
            val at = date.atTime(start / 60, start % 60)
            if (at.isAfter(now)) return at
        }
        return null
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("enabled", enabled)
        .put("kind", kind.name)
        .put("stationId", stationId)
        .put("stationName", stationName)
        .put("routes", JSONArray(routes))
        .put("lineId", lineId)
        .put("direction", direction)
        .put("days", JSONArray(days.sorted()))
        .put("start", start)
        .put("end", end)
        .put("leadMin", leadMin)

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val routes = o.optJSONArray("routes") ?: JSONArray()
            val days = o.optJSONArray("days") ?: JSONArray()
            return Alarm(
                id = o.getString("id"),
                enabled = o.optBoolean("enabled", true),
                kind = Kind.valueOf(o.optString("kind", "BUS")),
                stationId = o.optString("stationId"),
                stationName = o.optString("stationName"),
                routes = (0 until routes.length()).map { routes.getString(it) },
                lineId = o.optString("lineId", "1075"),
                direction = o.optString("direction", "상행"),
                days = (0 until days.length()).map { days.getInt(it) }.toSet(),
                start = o.optInt("start"),
                end = o.optInt("end"),
                leadMin = o.optInt("leadMin", 3),
            )
        }
    }
}

val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")

fun hm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

/** 알림 목록과 API 키를 이 기기에 저장한다. */
object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("busalarm", Context.MODE_PRIVATE)

    fun alarms(c: Context): List<Alarm> {
        val arr = JSONArray(prefs(c).getString("alarms", "[]"))
        return (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
    }

    fun saveAlarms(c: Context, list: List<Alarm>) {
        prefs(c).edit().putString("alarms", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    /** 공공데이터포털(data.go.kr) 일반 인증키. 경기도 버스에 쓴다. */
    fun busKey(c: Context) = prefs(c).getString("busKey", "")!!.trim()

    /** 서울 열린데이터광장 실시간 지하철 인증키. 비어 있으면 sample 키(5건 제한)를 쓴다. */
    fun subwayKey(c: Context) = prefs(c).getString("subwayKey", "")!!.trim()

    fun saveKeys(c: Context, bus: String, subway: String) {
        prefs(c).edit().putString("busKey", bus.trim()).putString("subwayKey", subway.trim()).apply()
    }
}
