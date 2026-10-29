package com.squatwolf.gagyebu.data

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 부동저금: 적금·예금처럼 묶어 둔 돈. 유동저금: 언제든 꺼내 쓸 수 있는 돈. */
const val FIXED_SAVING = "부동저금"
const val LIQUID_SAVING = "유동저금"

/** 이 분류로 적은 지출은 저금통에 모인다. 예전 '저금'은 유동으로 친다. */
val SAVING_CATEGORIES = setOf(FIXED_SAVING, LIQUID_SAVING, "저금")

/** 저금통에 들어간 내역과 합계. 합계는 넣은 지출 - 꺼낸 수입. 내역은 최근 날짜부터. */
data class Savings(val fixedEntries: List<Entry> = emptyList(), val liquidEntries: List<Entry> = emptyList()) {
    val fixed get() = sum(fixedEntries)
    val liquid get() = sum(liquidEntries)
    val total get() = fixed + liquid

    private fun sum(list: List<Entry>) = list.sumOf { if (it.income) -it.amount else it.amount }

    companion object {
        /** 오늘까지 들어간 것만 센다. 미래 날짜(예: 미리 채워진 매달 고정)는 빼고. */
        fun of(entries: List<Entry>, today: LocalDate = LocalDate.now()): Savings {
            val until = today.toString()
            val saving = entries
                .filter { it.category in SAVING_CATEGORIES && it.date <= until }
                .sortedByDescending { it.date }
            val (fixed, liquid) = saving.partition { it.category == FIXED_SAVING }
            return Savings(fixed, liquid)
        }
    }
}

/** 내역 하나. date 는 "2026-10-27" 형식, amount 는 항상 양수. ruleId 는 매달 고정에서 만든 내역일 때만 있다. */
data class Entry(
    val id: String,
    val date: String,
    val income: Boolean,
    val amount: Long,
    val category: String,
    val memo: String,
    val method: String,
    val author: String,
    val ruleId: String = "",
)

/**
 * 매달 고정 내역. start 달부터 매달 day 일에 내역을 만든다(말일보다 크면 말일).
 * end 달부터는 멈추고, skips 에 든 달은 건너뛴다. 달은 "2026-10" 형식.
 */
data class Rule(
    val id: String,
    val day: Int,
    val income: Boolean,
    val amount: Long,
    val category: String,
    val memo: String,
    val method: String,
    val author: String,
    val start: String,
    val end: String = "",
    val skips: List<String> = emptyList(),
) {
    fun activeIn(month: YearMonth): Boolean {
        val m = month.toString()
        return m >= start && (end.isEmpty() || m < end) && m !in skips
    }

    // 달마다 ID 가 정해져 있어서 두 폰이 동시에 만들어도 하나로 합쳐진다.
    fun entryId(month: YearMonth) = "r_${id}_$month"

    fun entryFor(month: YearMonth) = Entry(
        id = entryId(month),
        date = month.atDay(minOf(day, month.lengthOfMonth())).toString(),
        income = income,
        amount = amount,
        category = category,
        memo = memo,
        method = method,
        author = author,
        ruleId = id,
    )
}

interface LedgerRepo {
    /** 화면 위에 보여 줄 연결 상태. */
    val status: StateFlow<String>
    val rules: Flow<List<Rule>>

    /** 지금까지 저금 분류로 넣은 돈(부동·유동). */
    val saved: Flow<Savings>
    fun entries(month: YearMonth): Flow<List<Entry>>

    /** id 가 비어 있으면 새로 만들고, 있으면 그 id 로 덮어쓴다. */
    fun add(entry: Entry)
    fun delete(id: String)
    fun addRule(rule: Rule)

    /** 그 달만 건너뛴다. */
    fun skip(ruleId: String, month: String)

    /** 그 달부터 멈추고, 이미 만들어 둔 그 달 이후 내역도 지운다. */
    fun stop(ruleId: String, month: String)
}
