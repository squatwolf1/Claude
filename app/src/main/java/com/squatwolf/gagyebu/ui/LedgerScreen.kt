package com.squatwolf.gagyebu.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squatwolf.gagyebu.LedgerViewModel
import com.squatwolf.gagyebu.data.Assets
import com.squatwolf.gagyebu.data.Entry
import java.time.LocalDate
import java.time.YearMonth

val Paper = Color(0xFFF7F7F7)
val Ink = Color(0xFF222222)
val Muted = Color(0xFFAAAAAA)
val Blue = Color(0xFF5B8DEF)
val Red = Color(0xFFE5655B)
val Fixed = Color(0xFFF08C84) // 고정비: 연한 빨강
val FixedIn = Color(0xFF8DB4F2) // 고정 수입: 연한 파랑
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
    var editing by remember { mutableStateOf<Entry?>(null) }
    var stats by remember { mutableStateOf(false) }

    val byDate = remember(entries) { entries.groupBy { it.date } }
    val income = entries.filter { it.income }.sumOf { it.amount }
    val expense = entries.filter { !it.income }.sumOf { it.amount }
    val dayEntries = byDate[vm.selected.toString()].orEmpty()

    // 첫 화면에서 뒤로 가기 → 종료 확인
    var exiting by remember { mutableStateOf(false) }
    BackHandler(enabled = !stats) { exiting = true }
    if (exiting) {
        val activity = LocalContext.current as? Activity
        AlertDialog(
            onDismissRequest = { exiting = false },
            title = { Text("종료하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { activity?.finishAndRemoveTask() }) { Text("종료", color = Red) }
            },
            dismissButton = { TextButton(onClick = { exiting = false }) { Text("취소") } },
        )
    }

    if (stats) {
        BackHandler { stats = false }
        val saved by vm.saved.collectAsStateWithLifecycle()
        val all by vm.allEntries.collectAsStateWithLifecycle()
        val assets = remember(all, vm.startAsset) { Assets.of(all, vm.startAsset) }
        StatsScreen(
            vm.month,
            entries,
            saved = saved,
            piggyScale = vm.goal,
            assets = assets,
            onStartAsset = vm::changeStartAsset,
            onPick = vm::pickMonth,
            onBack = { stats = false },
        )
        return
    }

    Scaffold(
        containerColor = Paper,
        // 연결 상태(혼자 쓰기 모드 등)는 화면 맨 아래 오른쪽에 작게
        bottomBar = {
            Text(
                status,
                color = Muted,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        },
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
                NoSpendBadge(remember(entries, vm.month) { noSpendDays(vm.month, entries) })
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.moveMonth(-1) }) { Icon(Icons.Default.KeyboardArrowLeft, "이전 달") }
                    Text(
                        monthTitle(vm.month),
                        Modifier.weight(1f),
                        fontSize = 22.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { vm.moveMonth(1) }) { Icon(Icons.Default.KeyboardArrowRight, "다음 달") }
                    TextButton(onClick = { stats = true }) { Text("통계", fontSize = 16.sp, color = Ink) }
                    IconButton(onClick = { sharing = true }) { Icon(Icons.Default.Share, "공유") }
                }
                Spacer(Modifier.height(8.dp))
            } }
            item { Column { Calendar(vm.month, vm.selected, byDate) { vm.selected = it } } }
            item { Summary(income, expense) }
            if (dayEntries.isEmpty()) {
                item {
                    Text(
                        "${vm.selected.monthValue}월 ${vm.selected.dayOfMonth}일 내역이 없어요. + 를 눌러 추가하세요.",
                        color = Muted,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(dayEntries, key = { it.id }) { e ->
                EntryCard(e, Modifier.clickable { editing = e })
            }
        }
    }

    if (adding) {
        AddEntryDialog(
            date = vm.selected,
            onDismiss = { adding = false },
            onSave = { inc, amount, cat, memo, method, monthly ->
                vm.add(inc, amount, cat, memo, method, monthly)
                adding = false
            },
        )
    }
    editing?.let { e ->
        AddEntryDialog(
            date = LocalDate.parse(e.date),
            editing = e,
            onDismiss = { editing = null },
            onSave = { inc, amount, cat, memo, method, monthly ->
                if (monthly) vm.makeMonthly(e, inc, amount, cat, memo, method)
                else vm.update(e, inc, amount, cat, memo, method)
                editing = null
            },
            onDelete = {
                editing = null
                deleting = e
            },
        )
    }
    if (sharing) ShareDialog(vm, onDismiss = { sharing = false })
    vm.update?.let { release ->
        val progress = vm.downloading
        AlertDialog(
            onDismissRequest = { if (progress == null) vm.skipUpdate() },
            title = { Text("새 버전이 나왔어요 🎉") },
            text = {
                Column {
                    Text("빌드 ${vm.build} → 빌드 ${release.build}")
                    if (progress != null) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = progress == null, onClick = { vm.installUpdate() }) {
                    Text(if (progress == null) "업데이트" else "받는 중…")
                }
            },
            dismissButton = {
                TextButton(enabled = progress == null, onClick = { vm.skipUpdate() }) { Text("나중에") }
            },
        )
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("삭제하시겠습니까?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${emojiOf(e.category)} ${e.category} ${e.memo} ${won(e.amount)}원")
                    if (e.ruleId.isNotEmpty()) {
                        Text("🔁 매달 고정 내역이에요", color = Muted, fontSize = 13.sp)
                        OutlinedButton(onClick = { vm.delete(e); deleting = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("이번 달만 삭제")
                        }
                        OutlinedButton(onClick = { vm.stopMonthly(e); deleting = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("이번 달부터 고정 그만", color = Red)
                        }
                    }
                }
            },
            confirmButton = {
                if (e.ruleId.isEmpty()) {
                    TextButton(onClick = { vm.delete(e); deleting = null }) { Text("삭제", color = Red) }
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

private fun monthTitle(m: YearMonth) =
    if (m.year == YearMonth.now().year) "${m.monthValue}월" else "${m.year}년 ${m.monthValue}월"

@Composable
private fun Summary(income: Long, expense: Long) {
    val total = income - expense
    // 수입 · 지출 · 합계를 각각 칸으로 나눠 보여 준다.
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryCell("수입", "+${won(income)}원", Blue, Modifier.weight(1f))
        SummaryCell("지출", "-${won(expense)}원", Red, Modifier.weight(1f))
        SummaryCell(
            "합계",
            (if (total >= 0) "+" else "-") + won(kotlin.math.abs(total)) + "원",
            if (total >= 0) Blue else Red,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCell(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            amount,
            color = color,
            fontSize = if (amount.length > 11) 13.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
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
                    Spacer(Modifier.weight(1f).height(96.dp))
                    continue
                }
                val list = byDate[day.toString()].orEmpty()
                val inc = list.filter { it.income && !it.isFixedIncome() }.sumOf { it.amount }
                val fixedIn = list.filter { it.isFixedIncome() }.sumOf { it.amount }
                val fixed = list.filter { it.isFixedCost() }.sumOf { it.amount }
                val exp = list.filter { !it.income && !it.isFixedCost() }.sumOf { it.amount }
                Column(
                    Modifier
                        .weight(1f)
                        .height(96.dp)
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
                        fontWeight = if (day == today) FontWeight.ExtraBold else FontWeight.Bold,
                        color = Ink,
                    )
                    // 그날 수입 합계(파랑), 지출 합계(빨강), 고정비(보라 띠)
                    if (inc > 0) DayAmount("+${won(inc)}", Blue)
                    if (exp > 0) DayAmount("-${won(exp)}", Red)
                    if (fixedIn > 0) FixedAmount("+${won(fixedIn)}", FixedIn)
                    if (fixed > 0) FixedAmount("-${won(fixed)}", Fixed)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DayAmount(text: String, color: Color) {
    FitText(text, color, 11f, Modifier.padding(top = 2.dp))
}

/** 칸 너비에 다 들어갈 때까지 글자를 줄여서 한 줄로 보여 준다. 잘리지 않게 하려는 것. */
@Composable
private fun FitText(text: String, color: Color, maxSp: Float, modifier: Modifier = Modifier) {
    var size by remember(text) { mutableStateOf(maxSp) }
    var fitted by remember(text) { mutableStateOf(false) }
    Text(
        text,
        color = color,
        fontSize = size.sp,
        lineHeight = (size + 1).sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.drawWithContent { if (fitted) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth && size > 6f) size *= 0.9f else fitted = true
        },
    )
}

/** 고정비(연한 빨강)·고정 수입(연한 파랑)은 상자에 흰 글씨로, 위아래 여백 없이 얇게 표시한다. */
@Composable
private fun FixedAmount(text: String, color: Color) {
    FitText(
        text,
        Color.White,
        10f,
        Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .padding(horizontal = 3.dp),
    )
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
        Text("${emojiOf(e.category)} ${e.category}", fontSize = 16.sp, modifier = Modifier.weight(0.9f))
        Column(Modifier.weight(1.4f)) {
            Text(e.memo.ifEmpty { "-" }, fontSize = 15.sp, maxLines = 1)
            Text(listOf(if (e.isFixedIncome()) "🔁 고정 수입" else if (e.ruleId.isNotEmpty()) "🔁 매달" else if (e.isFixedCost()) "고정비" else "", e.method, e.author).filter { it.isNotEmpty() }.joinToString(" · "), color = Muted, fontSize = 12.sp)
        }
        Text(
            (if (e.income) "+" else "-") + won(e.amount) + "원",
            color = if (e.income) Blue else Red,
            fontSize = 17.sp,
        )
    }
}
