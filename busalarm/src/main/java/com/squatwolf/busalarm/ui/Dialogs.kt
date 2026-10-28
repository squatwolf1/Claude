package com.squatwolf.busalarm.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.squatwolf.busalarm.Alarm
import com.squatwolf.busalarm.AlarmViewModel
import com.squatwolf.busalarm.Api
import com.squatwolf.busalarm.Arrival
import com.squatwolf.busalarm.Kind
import com.squatwolf.busalarm.Station
import com.squatwolf.busalarm.WatchService
import com.squatwolf.busalarm.dayNames
import com.squatwolf.busalarm.directionsOf
import com.squatwolf.busalarm.hm
import com.squatwolf.busalarm.notifyIfAllowed
import com.squatwolf.busalarm.subwayLines
import kotlinx.coroutines.launch

private val ErrorRed = Color(0xFFD03A2B)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditDialog(
    initial: Alarm,
    busKey: String,
    subwayKey: String,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
) {
    var a by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var routeChoices by remember { mutableStateOf(initial.routes) }
    var preview by remember { mutableStateOf<List<Pair<String, Arrival>>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 고른 정류장을 지나는 노선을 불러와 고를 수 있게 한다.
    LaunchedEffect(a.stationId) {
        if (a.kind != Kind.BUS || a.stationId.isEmpty()) return@LaunchedEffect
        try {
            routeChoices = (Api.stationRoutes(busKey, a.stationId) + a.routes).distinct()
        } catch (e: Exception) {
            message = e.message
        }
    }

    fun search() = scope.launch {
        message = null
        try {
            stations = Api.searchStations(busKey, query.trim())
            if (stations.isEmpty()) message = "검색 결과가 없어요"
        } catch (e: Exception) {
            message = e.message
        }
    }

    val valid = a.days.isNotEmpty() &&
        (if (a.kind == Kind.BUS) a.stationId.isNotEmpty() else a.stationName.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.stationName.isEmpty()) "알림 추가" else "알림 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kind.entries.forEach { k ->
                        FilterChip(
                            selected = a.kind == k,
                            onClick = {
                                if (a.kind != k) {
                                    a = a.copy(kind = k, stationId = "", stationName = "", routes = emptyList())
                                    stations = emptyList(); preview = emptyList(); message = null
                                }
                            },
                            label = { Text(if (k == Kind.BUS) "경기 버스" else "지하철") },
                        )
                    }
                }

                if (a.kind == Kind.BUS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("정류장 이름 또는 번호") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search() }),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { search() }, enabled = query.isNotBlank()) { Text("검색") }
                    }
                    message?.let { Text(it, color = ErrorRed, fontSize = 13.sp) }
                    stations.take(20).forEach { s ->
                        Text(
                            "${s.name}  (${s.number}) · ${s.region}",
                            Modifier.fillMaxWidth().clickable {
                                a = a.copy(stationId = s.id, stationName = s.name, routes = emptyList())
                                routeChoices = emptyList()
                                stations = emptyList()
                            }.padding(vertical = 8.dp),
                        )
                    }
                    if (a.stationId.isNotEmpty()) {
                        Text("정류장: ${a.stationName}", fontWeight = FontWeight.Bold)
                        Text("알림 받을 버스 (안 고르면 모든 버스)", fontSize = 13.sp, color = Muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            routeChoices.forEach { r ->
                                FilterChip(
                                    selected = r in a.routes,
                                    onClick = { a = a.copy(routes = if (r in a.routes) a.routes - r else a.routes + r) },
                                    label = { Text(r) },
                                )
                            }
                        }
                    }
                } else {
                    Text("노선", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        subwayLines.forEach { (id, name) ->
                            FilterChip(
                                selected = a.lineId == id,
                                onClick = { a = a.copy(lineId = id, direction = directionsOf(id).first()); preview = emptyList() },
                                label = { Text(name) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = a.stationName,
                        onValueChange = { a = a.copy(stationName = it) },
                        label = { Text("역 이름 (예: 정자)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("방향", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        directionsOf(a.lineId).forEach { d ->
                            FilterChip(selected = a.direction == d, onClick = { a = a.copy(direction = d) }, label = { Text(d) })
                        }
                    }
                    if (a.lineId == "1075") {
                        Text("상행: 왕십리·청량리 방면 / 하행: 수원·인천 방면", fontSize = 13.sp, color = Muted)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                message = null
                                try {
                                    preview = Api.subwayArrivals(subwayKey, a.stationName, a.lineId)
                                    if (preview.isEmpty()) message = "지금은 이 노선의 도착 정보가 없어요"
                                } catch (e: Exception) {
                                    message = e.message
                                }
                            }
                        },
                        enabled = a.stationName.isNotBlank(),
                    ) { Text("지금 도착 정보로 확인") }
                    message?.let { Text(it, color = ErrorRed, fontSize = 13.sp) }
                    preview.forEach { (dir, ar) ->
                        Text("$dir · ${ar.name} · ${ar.etaText}  ${ar.detail}", fontSize = 13.sp)
                    }
                }

                HorizontalDivider()
                Text("요일", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayNames.forEachIndexed { i, d ->
                        val day = i + 1
                        FilterChip(
                            selected = day in a.days,
                            onClick = { a = a.copy(days = if (day in a.days) a.days - day else a.days + day) },
                            label = { Text(d) },
                        )
                    }
                }
                Text("이 시간에만 확인해요", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime(context, a.start) { a = a.copy(start = it) } }) { Text(hm(a.start)) }
                    Text("~")
                    OutlinedButton(onClick = { pickTime(context, a.end) { a = a.copy(end = it) } }) { Text(hm(a.end)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("도착 ${a.leadMin}분 전에 알림", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { a = a.copy(leadMin = (a.leadMin - 1).coerceAtLeast(1)) }) { Text("−", fontSize = 20.sp) }
                    TextButton(onClick = { a = a.copy(leadMin = (a.leadMin + 1).coerceAtMost(15)) }) { Text("+", fontSize = 20.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(a.copy(stationName = a.stationName.trim())) }, enabled = valid) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun pickTime(context: Context, minutes: Int, onPick: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, true).show()
}

@Composable
fun SettingsDialog(vm: AlarmViewModel, onDismiss: () -> Unit) {
    var bus by remember { mutableStateOf(vm.busKey) }
    var subway by remember { mutableStateOf(vm.subwayKey) }
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val ignoringBattery = remember {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("경기 버스", fontWeight = FontWeight.Bold)
                Text(
                    "공공데이터포털에서 '경기도_정류소 조회'와 '경기도_버스도착정보 조회'를 활용신청한 뒤 " +
                        "마이페이지의 일반 인증키를 붙여넣으세요.",
                    fontSize = 13.sp,
                )
                TextButton(onClick = { uri.openUri("https://www.data.go.kr") }) { Text("data.go.kr 열기") }
                OutlinedTextField(bus, { bus = it }, Modifier.fillMaxWidth(), label = { Text("공공데이터포털 인증키") }, singleLine = true)

                Text("지하철", fontWeight = FontWeight.Bold)
                Text(
                    "서울 열린데이터광장에서 '실시간 지하철 인증키'를 받아 넣으세요. " +
                        "비워 두면 체험용 키로 역마다 5건만 받아요.",
                    fontSize = 13.sp,
                )
                TextButton(onClick = { uri.openUri("https://data.seoul.go.kr/together/mypage/actkeyMain.do") }) {
                    Text("data.seoul.go.kr 열기")
                }
                OutlinedTextField(subway, { subway = it }, Modifier.fillMaxWidth(), label = { Text("실시간 지하철 인증키") }, singleLine = true)

                HorizontalDivider()
                Text(
                    "화면이 꺼져 있어도 제때 알림이 오려면 배터리 최적화에서 이 앱을 빼 주세요.",
                    fontSize = 13.sp,
                )
                if (!ignoringBattery) {
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                        )
                    }) { Text("배터리 최적화 제외") }
                } else {
                    Text("배터리 최적화 제외됨 ✓", fontSize = 13.sp, color = Muted)
                }
                OutlinedButton(onClick = {
                    val n = NotificationCompat.Builder(context, WatchService.ARRIVAL_CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("1550번 3분 후 도착 (테스트)")
                        .setContentText("이렇게 알려 드려요")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setAutoCancel(true)
                        .build()
                    context.notifyIfAllowed(99, n)
                }) { Text("알림 테스트") }
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveKeys(bus, subway); onDismiss() }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
