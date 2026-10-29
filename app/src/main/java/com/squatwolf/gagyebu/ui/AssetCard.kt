package com.squatwolf.gagyebu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squatwolf.gagyebu.data.Assets
import java.time.YearMonth

private fun signed(v: Long) = (if (v < 0) "-" else "+") + won(kotlin.math.abs(v)) + "원"

/**
 * 총자산 카드. 끝난 달의 합계가 매월 1일에 쌓인다.
 * 이번 달 합계는 '반영 예정'으로 따로 보여 주고, 월별 기록을 펼쳐 볼 수 있다.
 */
@Composable
fun AssetCard(assets: Assets, onStartAsset: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var editingStart by remember { mutableStateOf(false) }
    val lastMonth = YearMonth.now().minusMonths(1)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF2F7FF))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💰 총자산", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${lastMonth.monthValue}월 말 기준", color = Muted, fontSize = 12.sp)
        }
        Text(
            "${if (assets.total < 0) "-" else ""}${won(kotlin.math.abs(assets.total))}원",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (assets.total < 0) Red else Ink,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "이번 달 지금까지 ${signed(assets.pending)} · 다음 달 1일에 더해져요",
            color = if (assets.pending < 0) Red else Blue,
            fontSize = 13.sp,
        )
        Text("저금은 지출이 아니라 자산을 옮긴 것이라 총자산에서 빠지지 않아요", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))

        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Row(Modifier.fillMaxWidth().clickable { editingStart = true }, verticalAlignment = Alignment.CenterVertically) {
            Text("시작 자산 (앱 쓰기 전)", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${won(assets.start)}원 ›", fontSize = 13.sp)
        }
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("월별 기록", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(if (expanded) "접기 ▴" else "${assets.months.size}개월 ▾", fontSize = 13.sp)
        }
        if (expanded) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (assets.months.isEmpty()) Text("아직 끝난 달이 없어요", color = Muted, fontSize = 13.sp)
                assets.months.forEach { (m, v) ->
                    Row {
                        Text("${m.year}년 ${m.monthValue}월", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(signed(v), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (v < 0) Red else Blue)
                    }
                }
            }
        }
    }

    if (editingStart) {
        var text by remember { mutableStateOf(assets.start.coerceAtLeast(0).toString()) }
        AlertDialog(
            onDismissRequest = { editingStart = false },
            title = { Text("시작 자산") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter(Char::isDigit).take(13) },
                        label = { Text("앱을 쓰기 전 가지고 있던 돈 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ThousandsTransformation,
                        singleLine = true,
                    )
                    Text("처음 한 번만 넣으면 돼요. 그 뒤로는 매달 합계가 자동으로 쌓여요.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onStartAsset(text.toLongOrNull() ?: 0L)
                    editingStart = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editingStart = false }) { Text("취소") } },
        )
    }
}
