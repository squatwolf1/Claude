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
    private val ruleList = MutableStateFlow(loadRules())
    override val rules: Flow<List<Rule>> = ruleList
    override val saved: Flow<Savings> = all.map { Savings.of(it) }

    override fun entries(month: YearMonth): Flow<List<Entry>> =
        all.map { list -> list.filter { it.date.startsWith(month.toString()) } }

    override fun add(entry: Entry) {
        val e = if (entry.id.isEmpty()) entry.copy(id = UUID.randomUUID().toString()) else entry
        all.value = all.value.filterNot { it.id == e.id } + e
        save()
    }

    override fun addRule(rule: Rule) {
        ruleList.value = ruleList.value + rule
        saveRules()
    }

    override fun skip(ruleId: String, month: String) {
        ruleList.value = ruleList.value.map { if (it.id == ruleId) it.copy(skips = it.skips + month) else it }
        saveRules()
    }

    override fun stop(ruleId: String, month: String) {
        ruleList.value = ruleList.value.map { if (it.id == ruleId) it.copy(end = month) else it }
        saveRules()
        all.value = all.value.filterNot { it.ruleId == ruleId && it.date >= month }
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
                ruleId = o.optString("ruleId"),
            )
        }
    }

    // commit 으로 바로 디스크에 쓴다. 종료할 때 프로세스를 끝내므로 apply(나중에 쓰기)는 저장 전에 사라질 수 있다.
    private fun save() {
        val arr = JSONArray()
        all.value.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id).put("date", e.date).put("income", e.income)
                    .put("amount", e.amount).put("category", e.category).put("memo", e.memo)
                    .put("method", e.method).put("author", e.author).put("ruleId", e.ruleId)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).commit()
    }

    private fun loadRules(): List<Rule> {
        val arr = JSONArray(prefs.getString(RULES, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val skips = o.optJSONArray("skips") ?: JSONArray()
            Rule(
                id = o.getString("id"),
                day = o.getInt("day"),
                income = o.getBoolean("income"),
                amount = o.getLong("amount"),
                category = o.optString("category"),
                memo = o.optString("memo"),
                method = o.optString("method"),
                author = o.optString("author"),
                start = o.getString("start"),
                end = o.optString("end"),
                skips = (0 until skips.length()).map { skips.getString(it) },
            )
        }
    }

    private fun saveRules() {
        val arr = JSONArray()
        ruleList.value.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id).put("day", r.day).put("income", r.income).put("amount", r.amount)
                    .put("category", r.category).put("memo", r.memo).put("method", r.method)
                    .put("author", r.author).put("start", r.start).put("end", r.end)
                    .put("skips", JSONArray(r.skips))
            )
        }
        prefs.edit().putString(RULES, arr.toString()).commit()
    }

    private companion object {
        const val KEY = "local_entries"
        const val RULES = "local_rules"
    }
}
