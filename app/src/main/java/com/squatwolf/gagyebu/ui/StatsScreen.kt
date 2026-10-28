package com.squatwolf.gagyebu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squatwolf.gagyebu.data.Entry
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// 분류별 파스텔 색. 분류가 더 많으면 돌아가며 쓴다.
private val palette = listOf(
    Color(0xFFE35D5D), Color(0xFF9C82A8), Color(0xFF5BB8C6), Color(0xFFF0915A), Color(0xFFBDBDBD),
    Color(0xFFEBCF4E), Color(0xFFE8709A), Color(0xFF8B7FD1), Color(0xFF6CC08B), Color(0xFF8D6E63),
)
private val Track = Color(0xFFEDEDED)

private data class Slice(val category: String, val amount: Long, val color: Color)

@Composable
fun StatsScreen(
    month: YearMonth,
    entries: List<Entry>,
    onMove: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val expenses = entries.filter { !it.income }
    val total = expenses.sumOf { it.amount }
    val slices = remember(expenses) {
        expenses.groupBy { it.category }
            .map { (cat, list) -> cat to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .mapIndexed { i, (cat, sum) -> Slice(cat, sum, palette[i % palette.size]) }
    }
    val daily = remember(expenses, month) {
        val byDay = expenses.groupBy { it.date.takeLast(2).toInt() }
        (1..month.lengthOfMonth()).map { d -> byDay[d].orEmpty().sumOf { it.amount } }
    }

    Scaffold(containerColor = Paper) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") }
                    IconButton(onClick = { onMove(-1) }) { Icon(Icons.Default.KeyboardArrowLeft, "이전 달") }
                    Text("지출 통계", Modifier.weight(1f), fontSize = 20.sp, textAlign = TextAlign.Center)
                    IconButton(onClick = { onMove(1) }) { Icon(Icons.Default.KeyboardArrowRight, "다음 달") }
                    Spacer(Modifier.width(48.dp))
                }
            }
            if (total == 0L) {
                item {
                    Text(
                        "이번 달 지출이 아직 없어요.",
                        color = Muted,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                return@LazyColumn
            }
            item { Header(month, expenses, total) }
            item { Donut(slices, total) }
            items(slices, key = { it.category }) { s -> SliceRow(s, total) }
            item {
                Card {
                    Text("📅 날짜별 지출", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    DailyBars(month, daily)
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(18.dp)
    ) { content() }
}

private fun percent(part: Long, total: Long) = (part * 100.0 / total).roundToInt()

@Composable
private fun Header(month: YearMonth, expenses: List<Entry>, total: Long) {
    val byMethod = expenses.groupBy { it.method.ifEmpty { "기타" } }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${month.monthValue}월 지출", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "  (${month.monthValue}.1 - ${month.monthValue}.${month.lengthOfMonth()})",
                color = Muted,
                fontSize = 13.sp,
            )
        }
        Text("${won(total)}원", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        byMethod.forEach { (method, sum) ->
            Row {
                Text(method, color = Muted, fontSize = 14.sp, modifier = Modifier.width(72.dp))
                Text(won(sum), color = Muted, fontSize = 14.sp)
            }
        }
    }
}

/** 도넛 조각 안에 %를, 조각 바깥에는 선으로 이은 동그란 그림 글자 아이콘을 둔다. */
@Composable
private fun Donut(slices: List<Slice>, total: Long) {
    val measurer = rememberTextMeasurer()
    val pctStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val iconStyle = TextStyle(fontSize = 17.sp)
    Canvas(Modifier.fillMaxWidth().height(340.dp)) {
        val outer = size.height * 0.30f
        val inner = outer * 0.42f
        val ring = outer - inner
        val mid = (outer + inner) / 2
        val bubble = 21.dp.toPx()
        var start = -90f
        slices.forEachIndexed { i, s ->
            val sweep = 360f * s.amount / total
            drawArc(
                color = s.color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - mid, center.y - mid),
                size = Size(mid * 2, mid * 2),
                style = Stroke(width = ring),
            )
            val a = Math.toRadians((start + sweep / 2).toDouble())
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()

            // 조각 안 비율
            if (sweep >= 14f) {
                val t = measurer.measure("${percent(s.amount, total)}%", pctStyle)
                drawText(t, topLeft = Offset(center.x + dx * mid - t.size.width / 2, center.y + dy * mid - t.size.height / 2))
            }

            // 바깥 아이콘. 작은 조각끼리 겹치지 않게 번갈아 거리를 다르게 한다.
            val reach = outer + 38.dp.toPx() + if (sweep < 20f && i % 2 == 1) 30.dp.toPx() else 0f
            val bx = (center.x + dx * reach).coerceIn(bubble, size.width - bubble)
            val by = (center.y + dy * reach).coerceIn(bubble, size.height - bubble)
            drawLine(s.color, Offset(center.x + dx * outer, center.y + dy * outer), Offset(bx, by), strokeWidth = 1.5.dp.toPx())
            drawCircle(s.color, bubble, Offset(bx, by))
            val icon = measurer.measure(emojiOf(s.category), iconStyle)
            drawText(icon, topLeft = Offset(bx - icon.size.width / 2, by - icon.size.height / 2))

            start += sweep
        }
    }
}

@Composable
private fun DailyBars(month: YearMonth, daily: List<Long>) {
    val max = daily.maxOrNull()?.takeIf { it > 0 } ?: 1L
    // 처음에는 가장 많이 쓴 날에 말풍선을 띄운다. 막대를 누르면 그날로 옮겨 간다.
    var picked by remember(daily) { mutableIntStateOf(daily.indexOf(daily.max())) }
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = Muted, fontSize = 11.sp)
    val bubbleStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(daily) {
                detectTapGestures { pos ->
                    picked = (pos.x / (size.width.toFloat() / daily.size)).toInt().coerceIn(0, daily.lastIndex)
                }
            }
    ) {
        val axis = 18.dp.toPx() // 아래 날짜 글자 자리
        val top = 40.dp.toPx() // 위 말풍선 자리
        val chart = size.height - axis - top
        val slot = size.width / daily.size
        val bar = slot * 0.6f

        daily.forEachIndexed { i, v ->
            val left = i * slot + (slot - bar) / 2
            drawRoundRect(Track, Offset(left, top), Size(bar, chart), CornerRadius(bar / 2))
            if (v > 0) {
                val h = (chart * v / max).coerceAtLeast(bar)
                val color = if (i == picked) Red else Red.copy(alpha = 0.4f)
                drawRoundRect(color, Offset(left, top + chart - h), Size(bar, h), CornerRadius(bar / 2))
            }
        }

        // 말풍선: "16일 247,380원"
        val v = daily[picked]
        val text = measurer.measure("${picked + 1}일 ${if (v > 0) won(v) + "원" else "0원"}", bubbleStyle)
        val padX = 8.dp.toPx()
        val padY = 4.dp.toPx()
        val w = text.size.width + padX * 2
        val h = text.size.height + padY * 2
        val barCenter = picked * slot + slot / 2
        val barTop = if (v > 0) top + chart - (chart * v / max).coerceAtLeast(bar) else top + chart
        val bx = (barCenter - w / 2).coerceIn(0f, size.width - w)
        val by = (barTop - h - 8.dp.toPx()).coerceAtLeast(0f)
        val bubble = if (v > 0) Ink else Muted
        drawRoundRect(bubble, Offset(bx, by), Size(w, h), CornerRadius(h / 2))
        val tail = Path().apply {
            moveTo(barCenter - 5.dp.toPx(), by + h - 1f)
            lineTo(barCenter + 5.dp.toPx(), by + h - 1f)
            lineTo(barCenter, by + h + 6.dp.toPx())
            close()
        }
        drawPath(tail, bubble)
        drawText(text, topLeft = Offset(bx + padX, by + padY))

        // 날짜 눈금: 1, 5, 10, 15, 20, 25일과 고른 날
        (listOf(1, 5, 10, 15, 20, 25).filter { it <= daily.size } + (picked + 1)).distinct().forEach { d ->
            val t = measurer.measure("$d", tickStyle.copy(color = if (d == picked + 1) Ink else Muted))
            val x = ((d - 1) * slot + slot / 2 - t.size.width / 2).coerceIn(0f, size.width - t.size.width)
            drawText(t, topLeft = Offset(x, size.height - t.size.height))
        }
    }
}

/** 비율 · 아이콘 · 분류 · 막대 · 금액 한 줄. */
@Composable
private fun SliceRow(s: Slice, total: Long) {
    val ratio = s.amount.toFloat() / total
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${percent(s.amount, total)}%",
            color = s.color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp),
        )
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(s.color),
            contentAlignment = Alignment.Center,
        ) { Text(emojiOf(s.category), fontSize = 18.sp) }
        Text(
            s.category,
            fontSize = 15.sp,
            maxLines = 2,
            modifier = Modifier.padding(start = 8.dp).width(56.dp),
        )
        Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(12.dp).clip(CircleShape).background(Track)) {
            Box(Modifier.fillMaxWidth(ratio).height(12.dp).clip(CircleShape).background(s.color))
        }
        Text("${won(s.amount)}원", fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
