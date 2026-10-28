package com.squatwolf.busalarm.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.squatwolf.busalarm.Alarm
import com.squatwolf.busalarm.AlarmViewModel
import com.squatwolf.busalarm.Kind
import com.squatwolf.busalarm.Live
import kotlinx.coroutines.delay

val Muted = Color(0xFF6B6B6B)
private val Soon = Color(0xFFD03A2B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AlarmViewModel) {
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var deleting by remember { mutableStateOf<Alarm?>(null) }
    var settings by remember { mutableStateOf(false) }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // 화면이 켜져 있는 동안 30초마다 새로 불러온다.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refreshAll()
                delay(30_000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("도착알림", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Default.Refresh, "새로고침") }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "설정") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Alarm() }) { Icon(Icons.Default.Add, "알림 추가") }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.busKey.isEmpty()) {
                item {
                    Card(onClick = { settings = true }, Modifier.fillMaxWidth()) {
                        Text(
                            "버스 알림을 쓰려면 설정(톱니바퀴)에서 공공데이터포털 인증키를 넣어 주세요.",
                            Modifier.padding(16.dp),
                        )
                    }
                }
            }
            if (vm.alarms.isEmpty()) {
                item {
                    Text(
                        "오른쪽 아래 + 로 알림을 추가하세요.\n\n" +
                            "예) 평일 07:30~08:10 동안 우리 집 앞 정류장의 1550번이 3분 남으면 알림",
                        color = Muted,
                    )
                }
            }
            items(vm.alarms, key = { it.id }) { a ->
                AlarmCard(
                    alarm = a,
                    live = vm.live[a.id],
                    onClick = { editing = a },
                    onLongClick = { deleting = a },
                    onToggle = { vm.toggle(a) },
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    editing?.let { a ->
        EditDialog(
            initial = a,
            busKey = vm.busKey,
            subwayKey = vm.subwayKey,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
        )
    }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("알림 삭제") },
            text = { Text("'${a.title}' 알림을 지울까요?") },
            confirmButton = { TextButton(onClick = { vm.delete(a); deleting = null }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
    if (settings) SettingsDialog(vm, onDismiss = { settings = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    live: Live?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.enabled) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF0F0F0),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (alarm.kind == Kind.BUS) "버스" else "지하철", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text(alarm.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = alarm.enabled, onCheckedChange = { onToggle() })
            }
            Text(alarm.scheduleText, color = Muted, fontSize = 13.sp)
            when {
                live == null -> Text("불러오는 중…", color = Muted)
                live.error != null -> Text(live.error, color = Soon, fontSize = 13.sp)
                live.arrivals.isEmpty() -> Text("지금은 도착 정보가 없어요", color = Muted)
                else -> live.arrivals.take(3).forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.name, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
                        Text(
                            a.etaText,
                            color = if (a.etaSec <= alarm.leadMin * 60) Soon else Color.Unspecified,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(a.detail, color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
