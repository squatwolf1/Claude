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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squatwolf.gagyebu.LedgerViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

private val expenseCategories = listOf("식비", "교통", "쇼핑", "생활", "공과금", "대출", "유류", "병원", "저금", "기타")
private val incomeCategories = listOf("월급", "용돈", "부수입", "기타")
private val methods = listOf("현금", "카드", "계좌이체")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEntryDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (income: Boolean, amount: Long, category: String, memo: String, method: String, monthly: Boolean) -> Unit,
) {
    var income by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(expenseCategories[0]) }
    var memo by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(methods[0]) }
    var monthly by remember { mutableStateOf(false) }
    val value = amount.toLongOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue}월 ${date.dayOfMonth}일 내역") },
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
        title = { Text("함께 쓰기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(10) },
                    label = { Text("내 이름 (내역에 표시)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
