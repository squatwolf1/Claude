package com.squatwolf.gagyebu.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squatwolf.gagyebu.LedgerViewModel
import com.squatwolf.gagyebu.data.Entry
import java.time.LocalDate
import java.time.YearMonth

val Paper = Color(0xFFF7F7F7)
val Ink = Color(0xFF222222)
val Muted = Color(0xFFAAAAAA)
val Blue = Color(0xFF5B8DEF)
val Red = Color(0xFFE5655B)
private val Selected = Color(0xFFE4E4E4)

fun won(v: Long) = "%,d".format(v)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(vm: LedgerViewModel) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Entry?>(null) }

    val byDate = remember(entries) { entries.groupBy { it.date } }
    val income = entries.filter { it.income }.sumOf { it.amount }
    val expense = entries.filter { !it.income }.sumOf { it.amount }
    val dayEntries = byDate[vm.selected.toString()].orEmpty()

    Scaffold(
        containerColor = Paper,
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = Ink, contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = "내역 추가")
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            item { Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.moveMonth(-1) }) { Icon(Icons.Default.KeyboardArrowLeft, "이전 달") }
                    Text(
                        monthTitle(vm.month),
                        Modifier.weight(1f),
                        fontSize = 22.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { vm.moveMonth(1) }) { Icon(Icons.Default.KeyboardArrowRight, "다음 달") }
                    IconButton(onClick = { sharing = true }) { Icon(Icons.Default.Share, "공유") }
                }
                Text(status, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
            } }
            item { Summary(income, expense) }
            item { Column { Calendar(vm.month, vm.selected, byDate) { vm.selected = it } } }
            if (dayEntries.isEmpty()) {
                item {
                    Text(
                        "${vm.selected.monthValue}월 ${vm.selected.dayOfMonth}일 내역이 없어요. + 를 눌러 추가하세요.",
                        color = Muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(dayEntries, key = { it.id }) { e ->
                EntryCard(e, Modifier.combinedClickable(onClick = {}, onLongClick = { deleting = e }))
            }
        }
    }

    if (adding) {
        AddEntryDialog(
            date = vm.selected,
            onDismiss = { adding = false },
            onSave = { inc, amount, cat, memo, method ->
                vm.add(inc, amount, cat, memo, method)
                adding = false
            },
        )
    }
    if (sharing) ShareDialog(vm, onDismiss = { sharing = false })
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("삭제할까요?") },
            text = { Text("${e.category} ${e.memo} ${won(e.amount)}원") },
            confirmButton = { TextButton(onClick = { vm.delete(e); deleting = null }) { Text("삭제", color = Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

private fun monthTitle(m: YearMonth) =
    if (m.year == YearMonth.now().year) "${m.monthValue}월" else "${m.year}년 ${m.monthValue}월"

@Composable
private fun Summary(income: Long, expense: Long) {
    val total = income - expense
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.weight(1f)) {
            Row {
                Text("수입 ", fontSize = 16.sp)
                Text("+${won(income)}원", color = Blue, fontSize = 16.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text("지출 ", fontSize = 16.sp)
                Text("-${won(expense)}원", color = Red, fontSize = 16.sp)
            }
        }
        Row {
            Text("합계 ", fontSize = 16.sp)
            Text(
                (if (total >= 0) "+" else "-") + won(kotlin.math.abs(total)) + "원",
                color = if (total >= 0) Blue else Red,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun Calendar(month: YearMonth, selected: LocalDate, byDate: Map<String, List<Entry>>, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        listOf("일", "월", "화", "수", "목", "금", "토").forEach {
            Text(it, Modifier.weight(1f), color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    // 일요일부터 시작. 앞쪽 빈칸 수 = 1일의 요일(월=1 … 일=7)을 7로 나눈 나머지.
    val lead = month.atDay(1).dayOfWeek.value % 7
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val day = week.getOrNull(i)
                if (day == null) {
                    Spacer(Modifier.weight(1f).height(72.dp))
                    continue
                }
                val list = byDate[day.toString()].orEmpty()
                val inc = list.filter { it.income }.sumOf { it.amount }
                val exp = list.filter { !it.income }.sumOf { it.amount }
                Column(
                    Modifier
                        .weight(1f)
                        .height(72.dp)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (day == selected) Selected else Color.Transparent)
                        .clickable { onPick(day) }
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${day.dayOfMonth}",
                        fontSize = 16.sp,
                        fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                        color = Ink,
                    )
                    if (inc > 0) Text("+${won(inc)}", color = Blue, fontSize = 10.sp, maxLines = 1)
                    if (exp > 0) Text("-${won(exp)}", color = Red, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun EntryCard(e: Entry, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(e.category, fontSize = 16.sp, modifier = Modifier.weight(0.8f))
        Column(Modifier.weight(1.4f)) {
            Text(e.memo.ifEmpty { "-" }, fontSize = 15.sp, maxLines = 1)
            Text(listOf(e.method, e.author).filter { it.isNotEmpty() }.joinToString(" · "), color = Muted, fontSize = 12.sp)
        }
        Text(
            (if (e.income) "+" else "-") + won(e.amount) + "원",
            color = if (e.income) Blue else Red,
            fontSize = 17.sp,
        )
    }
}
