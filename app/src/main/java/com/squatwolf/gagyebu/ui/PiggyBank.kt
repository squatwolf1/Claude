package com.squatwolf.gagyebu.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squatwolf.gagyebu.R
import com.squatwolf.gagyebu.data.Savings
import kotlin.math.roundToInt

private val PigPink = Color(0xFFF8A5C2)
private val PigDark = Color(0xFFE77FA5)
private val PigLight = Color(0xFFFFC9DC)
private val Gold = Color(0xFFFFCF4A)

private val FixedGold = Color(0xFFE0A21B)
private val LiquidBlue = Color(0xFF5BB8C6)

@Composable
private fun SavingCell(label: String, amount: Long, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text("${won(amount.coerceAtLeast(0))}원", color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private enum class Mood { Hungry, Meh, Happy, Full }

private fun moodOf(ratio: Float) = when {
    ratio < 0.1f -> Mood.Hungry
    ratio < 0.4f -> Mood.Meh
    ratio < 1f -> Mood.Happy
    else -> Mood.Full
}

/**
 * 돼지 저금통. 모은 돈이 목표에 가까울수록 몸이 커지고 금색으로 차오르며 표정이 밝아진다.
 * 동전·반짝이·돈주머니 그림: Microsoft Fluent Emoji (MIT, licenses/FluentEmoji-MIT.txt).
 */
@Composable
fun PiggyCard(savings: Savings, goal: Long, onGoal: (Long) -> Unit) {
    val saved = savings.total
    val ratio = if (goal > 0) saved.coerceAtLeast(0).toFloat() / goal else 0f
    val fill by animateFloatAsState(ratio.coerceIn(0f, 1.2f), tween(900), label = "piggy")
    val mood = moodOf(ratio)
    var editing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFF4F8))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🐷 우리 저금통", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = true }) { Text("목표 바꾸기", color = PigDark, fontSize = 13.sp) }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp)) {
            val w = maxWidth
            Canvas(Modifier.fillMaxSize()) { drawPig(fill, mood) }

            // 동전 투입구 위로 동전이 통통 떨어진다.
            val bounce = rememberInfiniteTransition(label = "coin")
            val drop by bounce.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
                label = "drop",
            )
            Image(
                painterResource(R.drawable.fluent_coin),
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .offset(x = w * 0.45f - 15.dp, y = (drop * 40f).dp - 4.dp),
            )

            // 바닥에 쌓인 동전: 많이 모을수록 높이 쌓인다.
            val coins = (fill * 6).toInt().coerceIn(0, 7)
            repeat(coins) { i ->
                Image(
                    painterResource(R.drawable.fluent_coin),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-(i % 2) * 14).dp, y = (-(i / 2) * 16).dp)
                        .size(30.dp),
                )
            }
            if (mood == Mood.Happy || mood == Mood.Full) {
                Image(
                    painterResource(R.drawable.fluent_sparkles),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopStart).offset(x = 6.dp, y = 30.dp).size(34.dp),
                )
            }
            if (mood == Mood.Full) {
                Image(
                    painterResource(R.drawable.fluent_money_bag),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomStart).size(48.dp),
                )
            }
        }

        Text(
            "${won(saved.coerceAtLeast(0))}원",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = PigDark,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            when (mood) {
                Mood.Hungry -> "텅텅… 배고파요 🥺"
                Mood.Meh -> "조금씩 모이고 있어요"
                Mood.Happy -> "꿀꿀! 든든해요 😊"
                Mood.Full -> "배불러요! 목표 달성 🎉"
            },
            fontSize = 15.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
        // 부동 · 유동 나눠 보기
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SavingCell("🔒 부동 저금", savings.fixed, FixedGold, Modifier.weight(1f))
            SavingCell("💧 유동 저금", savings.liquid, LiquidBlue, Modifier.weight(1f))
        }
        if (saved > 0) {
            val fixedShare = (savings.fixed.coerceAtLeast(0).toFloat() / saved).coerceIn(0f, 1f)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(CircleShape)) {
                if (fixedShare > 0f) Box(Modifier.weight(fixedShare).fillMaxHeight().background(FixedGold))
                if (fixedShare < 1f) Box(Modifier.weight(1f - fixedShare).fillMaxHeight().background(LiquidBlue))
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(PigLight)) {
            Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(14.dp).clip(CircleShape).background(Gold))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("목표 ${won(goal)}원", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${(ratio * 100).roundToInt()}%", color = PigDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text("지출에서 🔒 부동저금 · 💧 유동저금 분류로 넣으면 여기에 모여요", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }

    if (editing) {
        var text by remember { mutableStateOf(goal.toString()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("저금 목표") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(12) },
                    label = { Text("목표 금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandsTransformation,
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toLongOrNull()?.takeIf { it > 0 }?.let(onGoal)
                    editing = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("취소") } },
        )
    }
}

/** fill: 목표 대비 비율(0~1.2). 몸 크기와 금색 차오름이 여기에 따라 바뀐다. */
private fun DrawScope.drawPig(fill: Float, mood: Mood) {
    val f = fill.coerceIn(0f, 1.2f)
    val cx = size.width * 0.45f
    val bw = (size.width * 0.46f) * (0.82f + 0.25f * f) // 몸 너비: 모을수록 두둑
    val bh = (bw * 0.66f) * (0.92f + 0.14f * f)
    val cy = size.height * 0.58f
    val body = Rect(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)
    val outline = 3.dp.toPx()

    // 다리
    val legW = bw * 0.13f
    val legH = bh * 0.22f
    listOf(-0.3f, 0.18f).forEach { x ->
        drawRoundRect(PigDark, Offset(cx + bw * x - legW / 2 + bw * 0.06f, body.bottom - legH * 0.6f), Size(legW, legH),
            androidx.compose.ui.geometry.CornerRadius(legW / 2))
    }
    listOf(-0.22f, 0.27f).forEach { x ->
        drawRoundRect(PigPink, Offset(cx + bw * x - legW / 2, body.bottom - legH * 0.45f), Size(legW, legH),
            androidx.compose.ui.geometry.CornerRadius(legW / 2))
    }

    // 꼬리
    val tail = Path().apply {
        moveTo(body.left + bw * 0.02f, cy - bh * 0.05f)
        cubicTo(body.left - bw * 0.12f, cy - bh * 0.25f, body.left - bw * 0.02f, cy - bh * 0.38f, body.left - bw * 0.1f, cy - bh * 0.42f)
    }
    drawPath(tail, PigDark, style = Stroke(width = outline * 1.4f))

    // 몸
    drawOval(PigPink, body.topLeft, body.size)
    // 모은 만큼 금색으로 차오름
    val bodyPath = Path().apply { addOval(body) }
    clipPath(bodyPath) {
        val level = body.bottom - bh * f.coerceAtMost(1f)
        drawRect(Gold.copy(alpha = 0.75f), Offset(body.left, level), Size(bw, body.bottom - level))
        if (f > 0.05f) drawLine(Color.White.copy(alpha = 0.6f), Offset(body.left, level), Offset(body.right, level), outline)
    }
    // 반짝 하이라이트
    drawOval(Color.White.copy(alpha = 0.35f), Offset(cx - bw * 0.3f, cy - bh * 0.38f), Size(bw * 0.28f, bh * 0.16f))
    drawOval(PigDark, body.topLeft, body.size, style = Stroke(outline))

    // 귀
    val ear = Path().apply {
        moveTo(cx + bw * 0.05f, body.top + bh * 0.08f)
        lineTo(cx + bw * 0.14f, body.top - bh * 0.2f)
        lineTo(cx + bw * 0.28f, body.top + bh * 0.1f)
        close()
    }
    drawPath(ear, PigPink)
    drawPath(ear, PigDark, style = Stroke(outline))

    // 동전 투입구
    drawRoundRect(Color(0xFF7A3B55), Offset(cx - bw * 0.12f, body.top + bh * 0.06f), Size(bw * 0.24f, outline * 1.6f),
        androidx.compose.ui.geometry.CornerRadius(outline))

    // 코
    val snoutW = bw * 0.2f
    val snoutH = bh * 0.3f
    val snout = Offset(body.right - snoutW * 0.55f, cy - snoutH * 0.35f)
    drawOval(PigLight, snout, Size(snoutW, snoutH))
    drawOval(PigDark, snout, Size(snoutW, snoutH), style = Stroke(outline))
    drawCircle(PigDark, snoutW * 0.1f, Offset(snout.x + snoutW * 0.35f, snout.y + snoutH * 0.5f))
    drawCircle(PigDark, snoutW * 0.1f, Offset(snout.x + snoutW * 0.68f, snout.y + snoutH * 0.5f))

    // 눈
    val eye = Offset(cx + bw * 0.2f, cy - bh * 0.15f)
    val eyeR = bw * 0.035f
    val ink = Color(0xFF3A2430)
    when (mood) {
        Mood.Full -> {
            // 웃는 눈 ^
            val p = Path().apply {
                moveTo(eye.x - eyeR * 1.4f, eye.y + eyeR * 0.6f)
                quadraticTo(eye.x, eye.y - eyeR * 1.6f, eye.x + eyeR * 1.4f, eye.y + eyeR * 0.6f)
            }
            drawPath(p, ink, style = Stroke(outline))
        }
        else -> {
            drawCircle(ink, eyeR, eye)
            drawCircle(Color.White, eyeR * 0.35f, Offset(eye.x + eyeR * 0.3f, eye.y - eyeR * 0.3f))
        }
    }
    if (mood == Mood.Hungry) {
        // 눈물
        drawOval(Color(0xFF7EC8F2), Offset(eye.x - eyeR * 0.5f, eye.y + eyeR * 1.4f), Size(eyeR * 1.1f, eyeR * 1.8f))
    }
    if (mood == Mood.Happy || mood == Mood.Full) {
        // 볼터치
        drawOval(Color(0xFFFF7FA8).copy(alpha = 0.55f), Offset(eye.x - eyeR * 2.5f, eye.y + eyeR * 1.6f), Size(eyeR * 3f, eyeR * 1.6f))
    }

    // 입
    val mx = snout.x - bw * 0.02f
    val my = snout.y + snoutH * 1.12f
    val mouthW = bw * 0.1f
    val mouth = Path().apply {
        moveTo(mx - mouthW, my)
        when (mood) {
            Mood.Hungry -> quadraticTo(mx, my - mouthW * 0.7f, mx + mouthW * 0.6f, my) // 시무룩
            Mood.Meh -> lineTo(mx + mouthW * 0.6f, my)
            Mood.Happy -> quadraticTo(mx, my + mouthW * 0.7f, mx + mouthW * 0.6f, my)
            Mood.Full -> quadraticTo(mx, my + mouthW * 1.3f, mx + mouthW * 0.6f, my)
        }
    }
    drawPath(mouth, ink, style = Stroke(outline * 0.8f))
}
