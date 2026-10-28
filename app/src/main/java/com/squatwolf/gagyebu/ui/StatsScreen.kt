package com.squatwolf.gagyebu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squatwolf.gagyebu.data.Entry
import java.time.YearMonth

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
                    Text("${month.monthValue}월 지출", Modifier.weight(1f), fontSize = 24.sp, textAlign = TextAlign.Center)
                    IconButton(onClick = { onMove(1) }) { Icon(Icons.Default.KeyboardArrowRight, "다음 달") }
                    Spacer(Modifier.width(48.dp))
                }
            }
            if (total == 0L) {
                item {
                    Text(
                        "이번 달 지출이 아직 없어요.",
                        color = Muted,
                        fontSize = 18.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                return@LazyColumn
            }
            item { Donut(slices, total) }
            item {
                Card {
                    Text("날짜별 지출", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    DailyBars(daily)
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
            items(slices, key = { it.category }) { s -> SliceRow(s, total) }
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

@Composable
private fun Donut(slices: List<Slice>, total: Long) {
    Card {
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(210.dp)) {
                val stroke = 34.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
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
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("총 지출", color = Muted, fontSize = 16.sp)
                Text("${won(total)}원", color = Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DailyBars(daily: List<Long>) {
    val max = daily.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / daily.size
        val bar = slot * 0.6f
        daily.forEachIndexed { i, v ->
            val left = i * slot + (slot - bar) / 2
            drawRoundRect(Track, Offset(left, 0f), Size(bar, size.height), CornerRadius(bar / 2))
            if (v > 0) {
                val h = (size.height * v / max).coerceAtLeast(bar)
                drawRoundRect(Red, Offset(left, size.height - h), Size(bar, h), CornerRadius(bar / 2))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(1, 10, 20, daily.size).forEach { Text("${it}일", color = Muted, fontSize = 13.sp) }
    }
}

@Composable
private fun SliceRow(s: Slice, total: Long) {
    val ratio = s.amount.toFloat() / total
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(s.color))
            Text(s.category, fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Text("${(ratio * 100).toInt()}%", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
            Text("${won(s.amount)}원", fontSize = 18.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Track)) {
            Box(Modifier.fillMaxWidth(ratio).height(8.dp).clip(CircleShape).background(s.color))
        }
    }
}
