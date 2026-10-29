package com.squatwolf.gagyebu.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squatwolf.gagyebu.LedgerViewModel
import com.squatwolf.gagyebu.data.Entry
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val expenseCategories = listOf("식비", "교통", "쇼핑", "생활", "공과금", "대출", "보험", "유류", "병원", "저금(사용불가)", "저금(사용가능)", "기타")
private val incomeCategories = listOf("월급", "용돈", "부수입", "기타")
private val methods = listOf("현금", "카드", "계좌이체")

/** 숫자만 저장하고 화면에는 1,234,000 처럼 세 자리마다 쉼표를 찍어 보여 준다. */
internal object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val n = digits.length
        fun commaBefore(i: Int) = i > 0 && (n - i) % 3 == 0
        val out = buildString {
            digits.forEachIndexed { i, c ->
                if (commaBefore(i)) append(',')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + (1 until offset).count { commaBefore(it) }

            override fun transformedToOriginal(offset: Int) =
                (0..n).last { originalToTransformed(it) <= offset }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEntryDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (income: Boolean, amount: Long, category: String, memo: String, method: String, monthly: Boolean) -> Unit,
    editing: Entry? = null,
    onDelete: (() -> Unit)? = null,
) {
    // editing 이 있으면 그 내역 값으로 채운 수정 창이 된다.
    var income by remember { mutableStateOf(editing?.income ?: false) }
    var amount by remember { mutableStateOf(editing?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(editing?.category ?: expenseCategories[0]) }
    var memo by remember { mutableStateOf(editing?.memo ?: "") }
    var method by remember { mutableStateOf(editing?.method?.ifEmpty { null } ?: methods[0]) }
    var monthly by remember { mutableStateOf(false) }
    val value = amount.toLongOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue}월 ${date.dayOfMonth}일 내역" + if (editing != null) " 수정" else "") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !income, onClick = {
                        income = false; category = expenseCategories[0]
                    }, label = { Text("지출") })
                    FilterChip(selected = income, onClick = {
                        income = true; category = incomeCategories[0]
                    }, label = { Text("수입") })
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(12) },
                    label = { Text("금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandsTransformation,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("분류", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (income) incomeCategories else expenseCategories).forEach {
                        FilterChip(selected = category == it, onClick = { category = it }, label = { Text("${emojiOf(it)} $it") })
                    }
                }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(40) },
                    label = { Text("메모 (예: 가스비)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("결제수단", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    methods.forEach {
                        FilterChip(selected = method == it, onClick = { method = it }, label = { Text(it) })
                    }
                }
                // 새 내역이나 일반 내역 수정에서는 매달 고정으로 바꿀 수 있다.
                if (editing == null || editing.ruleId.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { monthly = !monthly },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = monthly, onCheckedChange = { monthly = it })
                        Column {
                            Text("🔁 매달 고정", fontWeight = FontWeight.Bold)
                            Text("매월 ${date.dayOfMonth}일에 자동으로 들어가요", fontSize = 12.sp, color = Muted)
                        }
                    }
                } else {
                    Text("🔁 매달 고정 내역이에요. 이번 달 것만 바뀌어요.", fontSize = 12.sp, color = Muted)
                }
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("이 내역 삭제", color = Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value > 0, onClick = { onSave(income, value, category, memo.trim(), method, monthly) }) {
                Text("저장")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun ShareDialog(vm: LedgerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cloud = vm.cloud
    val code = cloud?.code?.collectAsStateWithLifecycle()?.value
    var name by remember { mutableStateOf(vm.name) }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정 · 함께 쓰기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(10) },
                    label = { Text("내 이름 (내역에 표시)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("화면 크기 ${(vm.uiScale * 100).roundToInt()}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Slider(
                    value = vm.uiScale,
                    onValueChange = { vm.changeUiScale((it * 20).roundToInt() / 20f) },
                    valueRange = 0.7f..1.3f,
                    steps = 11,
                )
                Text("앱 버전: 빌드 ${vm.build}", color = Muted, fontSize = 12.sp)
                HorizontalDivider()
                when {
                    cloud == null -> Text(
                        "아직 Firebase가 연결되지 않아 이 폰에만 저장돼요.\n" +
                            "저장소의 app 폴더에 google-services.json 을 올리면 다음 빌드부터 공유가 켜져요.",
                        fontSize = 14.sp,
                    )
                    code == null -> Text("연결 중이에요. 인터넷을 확인해 주세요.", fontSize = 14.sp)
                    else -> {
                        Text("내 초대코드", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(code, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "꼬빵 초대코드: $code\n앱의 공유 버튼 → 코드 입력에 넣어 줘!")
                            context.startActivity(Intent.createChooser(send, "초대코드 보내기"))
                        }) { Text("코드 보내기") }
                        HorizontalDivider()
                        Text("상대 가계부에 들어가기", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("상대가 보낸 코드를 넣으면 같은 가계부를 함께 써요. (지금 내 가계부 내역은 이 폰에서 안 보이게 돼요)", fontSize = 12.sp)
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.uppercase().take(8) },
                            label = { Text("초대코드 8자리") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(enabled = !busy && input.length == 8, onClick = {
                            busy = true
                            scope.launch {
                                val c = cloud ?: return@launch
                                message = c.join(input) ?: "참여했어요!"
                                busy = false
                            }
                        }) { Text(if (busy) "참여 중…" else "참여하기") }
                        message?.let { Text(it, fontSize = 13.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.changeName(name); onDismiss() }) { Text("닫기") }
        },
    )
}
