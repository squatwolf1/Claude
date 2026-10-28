package com.squatwolf.gagyebu

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.squatwolf.gagyebu.data.CloudLedger
import com.squatwolf.gagyebu.data.Entry
import com.squatwolf.gagyebu.data.LedgerRepo
import com.squatwolf.gagyebu.data.LocalLedger
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("gagyebu", Context.MODE_PRIVATE)

    /** google-services.json 을 넣고 빌드했을 때만 있다. */
    val cloud: CloudLedger? = if (FirebaseApp.getApps(app).isNotEmpty()) CloudLedger(prefs) else null
    private val repo: LedgerRepo = cloud ?: LocalLedger(prefs)

    var month by mutableStateOf(YearMonth.now())
        private set
    var selected by mutableStateOf(LocalDate.now())
    var name by mutableStateOf(prefs.getString("name", null) ?: "나")
        private set

    val status: StateFlow<String> = repo.status

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<Entry>> = snapshotFlow { month }
        .flatMapLatest { repo.entries(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        cloud?.let { viewModelScope.launch { it.start() } }
    }

    fun moveMonth(delta: Long) {
        month = month.plusMonths(delta)
        selected = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1)
    }

    fun changeName(value: String) {
        name = value.trim().ifEmpty { "나" }
        prefs.edit().putString("name", name).apply()
    }

    fun add(income: Boolean, amount: Long, category: String, memo: String, method: String) {
        repo.add(Entry("", selected.toString(), income, amount, category, memo, method, name))
    }

    fun delete(entry: Entry) = repo.delete(entry.id)
}
