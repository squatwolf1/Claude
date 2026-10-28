package com.squatwolf.gagyebu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
    Color(0xFFFF8A80), Color(0xFFFFB74D), Color(0xFFFFD54F), Color(0xFFAED581), Color(0xFF4DD0E1),
    Color(0xFF64B5F6), Color(0xFF9575CD), Color(0xFFF06292), Color(0xFFA1887F), Color(0xFF90A4AE),
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
                    Text("${month.monthValue}월 지출", Modifier.weight(1f), fontSize = 20.sp, textAlign = TextAlign.Center)
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
            item { Donut(slices, total) }
            item {
                Card {
                    Text("📅 날짜별 지출", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    DailyBars(month, daily)
                }
            }
            item {
                Text(
                    "🏆 분류별 순위",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp),
                )
            }
            items(slices, key = { it.category }) { s -> SliceRow(slices.indexOf(s) + 1, s, total) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Donut(slices: List<Slice>, total: Long) {
    val measurer = rememberTextMeasurer()
    Card {
        Text("🍩 분류별 지출", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) {
                val stroke = 44.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val radius = (size.width - stroke) / 2
                var start = -90f
                slices.forEach { s ->
                    val sweep = 360f * s.amount / total
                    drawArc(
                        color = s.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    // 조각이 넉넉하면 조각 위에 그림 글자를 바로 얹는다.
                    if (sweep >= 25f) {
                        val mid = Math.toRadians((start + sweep / 2).toDouble())
                        val label = measurer.measure(emojiOf(s.category), TextStyle(fontSize = 18.sp))
                        val cx = center.x + radius * cos(mid).toFloat()
                        val cy = center.y + radius * sin(mid).toFloat()
                        drawText(label, topLeft = Offset(cx - label.size.width / 2, cy - label.size.height / 2))
                    }
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("총 지출", color = Muted, fontSize = 14.sp)
                Text("${won(total)}원", color = Red, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        // 색깔 칩: 그래프 색과 같은 바탕에 그림 글자, 분류, 비율
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slices.forEach { s ->
                Text(
                    "${emojiOf(s.category)} ${s.category} ${percent(s.amount, total)}%",
                    fontSize = 14.sp,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(s.color.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
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

@Composable
private fun SliceRow(rank: Int, s: Slice, total: Long) {
    val ratio = s.amount.toFloat() / total
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(s.color.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) { Text(emojiOf(s.category), fontSize = 20.sp) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("$rank. ${s.category}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${percent(s.amount, total)}%", color = Muted, fontSize = 13.sp)
            }
            Text("${won(s.amount)}원", fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Track)) {
            Box(Modifier.fillMaxWidth(ratio).height(10.dp).clip(CircleShape).background(s.color))
        }
    }
}
