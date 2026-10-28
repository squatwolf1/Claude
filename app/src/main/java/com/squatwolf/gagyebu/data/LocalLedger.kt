package com.squatwolf.gagyebu.data

import android.content.SharedPreferences
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** Firebase 설정 전에 쓰는 이 기기 전용 저장소. */
class LocalLedger(private val prefs: SharedPreferences) : LedgerRepo {
    override val status: StateFlow<String> = MutableStateFlow("혼자 쓰기 모드 (공유하려면 Firebase 설정 필요)")
    private val all = MutableStateFlow(load())

    override fun entries(month: YearMonth): Flow<List<Entry>> =
        all.map { list -> list.filter { it.date.startsWith(month.toString()) } }

    override fun add(entry: Entry) {
        all.value = all.value + entry.copy(id = UUID.randomUUID().toString())
        save()
    }

    override fun delete(id: String) {
        all.value = all.value.filterNot { it.id == id }
        save()
    }

    private fun load(): List<Entry> {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(
                id = o.getString("id"),
                date = o.getString("date"),
                income = o.getBoolean("income"),
                amount = o.getLong("amount"),
                category = o.optString("category"),
                memo = o.optString("memo"),
                method = o.optString("method"),
                author = o.optString("author"),
            )
        }
    }

    private fun save() {
        val arr = JSONArray()
        all.value.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id).put("date", e.date).put("income", e.income)
                    .put("amount", e.amount).put("category", e.category).put("memo", e.memo)
                    .put("method", e.method).put("author", e.author)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "local_entries"
    }
}
