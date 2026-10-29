package com.squatwolf.gagyebu.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

private val ItemHeight = 44.dp
private const val VISIBLE = 5 // 가운데 줄이 고른 값
private const val PAD = VISIBLE / 2

/** 위아래로 굴려서 고르는 휠. 가운데 칸에 멈춘 값이 선택된다. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(items: List<String>, initial: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    // 위아래에 빈 칸을 PAD 개씩 둬서 첫·끝 값도 가운데로 올 수 있게 한다.
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val itemPx = with(LocalDensity.current) { ItemHeight.toPx() }
    val selected by remember {
        derivedStateOf {
            val i = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            i.coerceIn(items.indices)
        }
    }
    LaunchedEffect(selected) { onSelect(selected) }

    Box(modifier.height(ItemHeight * VISIBLE)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(ItemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEFEFEF))
        )
        LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(state)) {
            items(items.size + PAD * 2) { i ->
                val index = i - PAD
                Box(Modifier.fillMaxWidth().height(ItemHeight), contentAlignment = Alignment.Center) {
                    if (index in items.indices) {
                        Text(
                            items[index],
                            fontSize = if (index == selected) 20.sp else 16.sp,
                            fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == selected) Ink else Muted,
                        )
                    }
                }
            }
        }
    }
}

/** 연·월을 휠로 굴려서 고르는 창. */
@Composable
fun MonthPickerDialog(current: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    val now = YearMonth.now()
    val years = (2020..now.year + 1).toList()
    var year by remember { mutableIntStateOf(current.year) }
    var month by remember { mutableIntStateOf(current.monthValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("연·월 선택") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Wheel(
                    items = years.map { "${it}년" },
                    initial = years.indexOf(current.year).coerceAtLeast(0),
                    onSelect = { year = years[it] },
                    modifier = Modifier.weight(1f),
                )
                Wheel(
                    items = (1..12).map { "${it}월" },
                    initial = current.monthValue - 1,
                    onSelect = { month = it + 1 },
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(YearMonth.of(year, month)) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
