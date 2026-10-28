package com.squatwolf.busalarm

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 도착 예정 하나. [vehicle] 은 같은 차량에 알림을 두 번 보내지 않으려고 쓴다. */
data class Arrival(
    val name: String,
    val etaSec: Int,
    val detail: String,
    val vehicle: String,
    val estimated: Boolean = false,
) {
    val etaText: String
        get() = when {
            etaSec <= 0 -> "도착"
            etaSec < 60 -> "곧 도착"
            else -> (if (estimated) "약 " else "") + "${etaSec / 60}분"
        }
}

data class Station(val id: String, val name: String, val number: String, val region: String)

class ApiException(message: String) : Exception(message)

object Api {
    private const val GG = "https://apis.data.go.kr/6410000"

    /** 알림 조건(노선·방향)에 맞는 도착 예정을 빠른 순으로. */
    suspend fun arrivals(c: Context, alarm: Alarm): List<Arrival> = when (alarm.kind) {
        Kind.BUS -> busArrivals(Store.busKey(c), alarm.stationId)
            .filter { alarm.routes.isEmpty() || it.name in alarm.routes }
        Kind.SUBWAY -> subwayArrivals(Store.subwayKey(c), alarm.stationName, alarm.lineId)
            .filter { it.first == alarm.direction }
            .map { it.second }
    }.sortedBy { it.etaSec }

    suspend fun searchStations(key: String, keyword: String): List<Station> {
        val body = gg(key, "busstationservice/v2/getBusStationListv2", "keyword" to keyword)
            ?: return emptyList()
        return body.opt("busStationList").objects().map {
            Station(it.str("stationId"), it.str("stationName"), it.str("mobileNo"), it.str("regionName"))
        }
    }

    /** 정류장을 지나는 노선 번호. */
    suspend fun stationRoutes(key: String, stationId: String): List<String> {
        val body = gg(key, "busstationservice/v2/getBusStationViaRouteListv2", "stationId" to stationId)
            ?: return emptyList()
        return body.opt("busRouteList").objects().map { it.str("routeName") }.distinct()
    }

    suspend fun busArrivals(key: String, stationId: String): List<Arrival> {
        val body = gg(key, "busarrivalservice/v2/getBusArrivalListv2", "stationId" to stationId)
            ?: return emptyList()
        return body.opt("busArrivalList").objects().flatMap { o ->
            val route = o.str("routeName")
            (1..2).mapNotNull { n ->
                val min = o.int("predictTime$n") ?: return@mapNotNull null
                val loc = o.int("locationNo$n")
                val seats = o.int("remainSeatCnt$n")?.takeIf { it >= 0 }
                val detail = listOfNotNull(
                    loc?.let { "${it}정거장 전" },
                    seats?.let { "${it}석" },
                ).joinToString(" · ")
                Arrival(
                    name = route,
                    etaSec = o.int("predictTimeSec$n") ?: (min * 60),
                    detail = detail,
                    vehicle = o.str("plateNo$n").ifEmpty { "$route/$n" },
                )
            }
        }
    }

    /** (방향, 도착) 목록. 수인분당선처럼 남은 초를 안 주는 노선은 메시지로 어림한다. */
    suspend fun subwayArrivals(key: String, station: String, lineId: String): List<Pair<String, Arrival>> {
        val name = station.trim().let { if (it.length >= 3 && it.endsWith("역")) it.dropLast(1) else it }
        val k = key.ifEmpty { "sample" }
        val count = if (k == "sample") 5 else 40
        val url = "http://swopenapi.seoul.go.kr/api/subway/${enc(k)}/json/realtimeStationArrival/0/$count/${enc(name)}"
        val root = json(get(url))
        if (!root.has("realtimeArrivalList")) {
            val err = root.optJSONObject("errorMessage") ?: root
            if (err.optString("code") == "INFO-200") return emptyList()
            throw ApiException(err.optString("message").ifEmpty { "지하철 정보를 불러오지 못했습니다" })
        }
        return root.opt("realtimeArrivalList").objects()
            .filter { it.str("subwayId") == lineId }
            .mapNotNull { o ->
                val eta = subwayEta(o) ?: return@mapNotNull null
                val dest = o.str("trainLineNm").substringBefore(" - ").trim()
                val express = o.str("btrainSttus").let { if (it == "급행" || it == "특급") " $it" else "" }
                val last = if (o.str("lstcarAt") == "1") " · 막차" else ""
                o.str("updnLine") to Arrival(
                    name = dest + express,
                    etaSec = eta.first,
                    detail = o.str("arvlMsg2") + last,
                    vehicle = o.str("btrainNo").ifEmpty { dest + o.str("arvlMsg2") },
                    estimated = eta.second,
                )
            }
    }

    /** (남은 초, 어림값인지). 이미 떠난 열차는 null. */
    private fun subwayEta(o: JSONObject): Pair<Int, Boolean>? {
        val age = runCatching {
            val at = LocalDateTime.parse(o.str("recptnDt").take(19), stamp)
            Duration.between(at, LocalDateTime.now()).seconds.toInt().coerceIn(0, 120)
        }.getOrDefault(0)
        val barvl = o.int("barvlDt") ?: 0
        if (barvl > 0) return (barvl - age).coerceAtLeast(0) to false

        val msg = o.str("arvlMsg2")
        Regex("""(\d+)분\s*(?:(\d+)초)?\s*후""").find(msg)?.let { m ->
            val sec = m.groupValues[1].toInt() * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
            return (sec - age).coerceAtLeast(0) to false
        }
        Regex("""(\d+)초\s*후""").find(msg)?.let { return (it.groupValues[1].toInt() - age).coerceAtLeast(0) to false }
        // 역 사이 평균 2분으로 어림한다.
        Regex("""\[(\d+)]번째 전역""").find(msg)?.let { return it.groupValues[1].toInt() * 120 to true }
        return when (o.int("arvlCd")) {
            0 -> 30 to true // 당역 진입
            1 -> 0 to true // 당역 도착
            3 -> 90 to true // 전역 출발
            4 -> 120 to true // 전역 진입
            5 -> 150 to true // 전역 도착
            else -> null // 2 당역 출발, 99 운행 중
        }
    }

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 경기도 API 를 부르고 msgBody 를 돌려준다. 결과 없음이면 null. */
    private suspend fun gg(key: String, path: String, vararg params: Pair<String, String>): JSONObject? {
        if (key.isEmpty()) throw ApiException("설정에서 공공데이터포털 인증키를 넣어 주세요")
        // 포털의 인코딩 키(% 포함)는 그대로, 디코딩 키는 인코딩해서 보낸다.
        val k = if ('%' in key) key else enc(key)
        val query = params.joinToString("") { "&${it.first}=${enc(it.second)}" }
        val root = json(get("$GG/$path?serviceKey=$k&format=json$query")).getJSONObject("response")
        val header = root.optJSONObject("msgHeader")
        when (header?.optInt("resultCode", 0) ?: 0) {
            0 -> {}
            4 -> return null
            else -> throw ApiException(header?.optString("resultMessage").orEmpty().ifEmpty { "경기도 버스 API 오류" })
        }
        return root.optJSONObject("msgBody")
    }

    private fun json(text: String): JSONObject {
        val t = text.trim()
        if (!t.startsWith("{")) {
            // 키가 틀리거나 승인 전이면 XML·평문 오류가 온다.
            val reason = Regex("<returnAuthMsg>(.*?)</returnAuthMsg>").find(t)?.groupValues?.get(1)
                ?: t.take(80)
            throw ApiException("인증키를 확인해 주세요 ($reason)")
        }
        return JSONObject(t)
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 && !text.trim().startsWith("{")) throw ApiException("서버 응답 $code ${text.take(60)}")
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** 결과가 하나면 배열 대신 객체로 오는 경우도 있다. */
private fun Any?.objects(): List<JSONObject> = when (this) {
    is JSONArray -> (0 until length()).mapNotNull { optJSONObject(it) }
    is JSONObject -> listOf(this)
    else -> emptyList()
}

private fun JSONObject.str(name: String): String =
    opt(name)?.takeIf { it != JSONObject.NULL }?.toString()?.trim().orEmpty()

private fun JSONObject.int(name: String): Int? = str(name).toDoubleOrNull()?.toInt()
