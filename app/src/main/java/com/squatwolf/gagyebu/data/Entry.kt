package com.squatwolf.gagyebu.data

import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 내역 하나. date 는 "2026-10-27" 형식, amount 는 항상 양수. */
data class Entry(
    val id: String,
    val date: String,
    val income: Boolean,
    val amount: Long,
    val category: String,
    val memo: String,
    val method: String,
    val author: String,
)

interface LedgerRepo {
    /** 화면 위에 보여 줄 연결 상태. */
    val status: StateFlow<String>
    fun entries(month: YearMonth): Flow<List<Entry>>
    fun add(entry: Entry)
    fun delete(id: String)
}
