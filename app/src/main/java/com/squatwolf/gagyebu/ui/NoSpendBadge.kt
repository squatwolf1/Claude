package com.squatwolf.gagyebu.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squatwolf.gagyebu.R
import com.squatwolf.gagyebu.data.Entry
import com.squatwolf.gagyebu.data.SAVING_CATEGORY
import java.time.LocalDate
import java.time.YearMonth

/** 순수하게 쓴 돈인지. 고정비(매달 고정·공과금·대출)와 저금은 빼고 센다. */
private fun Entry.isSpending() = !income && !isFixedCost() && category != SAVING_CATEGORY

/** 이 달에 순수 소비가 하나도 없는 날 수. 아직 오지 않은 날은 세지 않는다. */
fun noSpendDays(month: YearMonth, entries: List<Entry>, today: LocalDate = LocalDate.now()): Int {
    val last = when {
        month.isAfter(YearMonth.from(today)) -> return 0
        month == YearMonth.from(today) -> today.dayOfMonth
        else -> month.lengthOfMonth()
    }
    val spent = entries.filter { it.isSpending() }.map { it.date }.toSet()
    return (1..last).count { month.atDay(it).toString() !in spent }
}

/**
 * 무지출 일수 배지. 날이 늘수록 화려해진다.
 * 0일 회색 → 🌱 초록 → ✨ 반짝 → 🔥 두근두근 → 👑 무지개 + Fluent 반짝이(MIT).
 */
@Composable
fun NoSpendBadge(days: Int) {
    val tier = when {
        days <= 0 -> 0
        days <= 2 -> 1
        days <= 6 -> 2
        days <= 13 -> 3
        else -> 4
    }
    val emoji = listOf("🫥", "🌱", "✨", "🔥", "👑")[tier]
    val anim = rememberInfiniteTransition(label = "nospend")
    val pulse by anim.animateFloat(
        initialValue = 1f,
        targetValue = when (tier) { 2 -> 1.04f; 3 -> 1.09f; 4 -> 1.12f; else -> 1f },
        animationSpec = infiniteRepeatable(tween(if (tier >= 3) 550 else 900), RepeatMode.Reverse),
        label = "pulse",
    )
    val shift by anim.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    val spin by anim.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "spin",
    )

    val background = when (tier) {
        0 -> Brush.linearGradient(listOf(Color(0xFFEDEDED), Color(0xFFEDEDED)))
        1 -> Brush.linearGradient(listOf(Color(0xFFDDF5E3), Color(0xFFC8EFD3)))
        2 -> Brush.linearGradient(listOf(Color(0xFF7ED9A0), Color(0xFF4CC38A)))
        3 -> Brush.linearGradient(listOf(Color(0xFFFFB74D), Color(0xFFFF5E62)))
        else -> Brush.linearGradient(
            listOf(Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77), Color(0xFF4D96FF), Color(0xFFB980F0), Color(0xFFFF6B6B)),
            start = Offset(shift, 0f),
            end = Offset(shift + 300f, 120f),
            tileMode = TileMode.Repeated,
        )
    }
    val textColor = if (tier <= 1) Ink else Color.White

    Box {
        Text(
            "$emoji 무지출 ${days}일",
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (tier >= 2) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .padding(top = 6.dp, end = 10.dp)
                .scale(pulse)
                .shadow(if (tier >= 3) 8.dp else 0.dp, RoundedCornerShape(50), clip = false)
                .clip(RoundedCornerShape(50))
                .background(background)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (tier == 4) {
            Image(
                painterResource(R.drawable.fluent_sparkles),
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(22.dp).rotate(spin),
            )
        }
    }
}
