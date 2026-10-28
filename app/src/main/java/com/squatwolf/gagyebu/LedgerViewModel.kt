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
import com.squatwolf.gagyebu.data.Updater
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

    val build = Updater.currentBuild(app)

    /** 새 버전이 있으면 들어온다. */
    var update by mutableStateOf<Updater.Release?>(null)
        private set

    /** 다운로드 진행률(0~1). 받는 중이 아니면 null. */
    var downloading by mutableStateOf<Float?>(null)
        private set

    init {
        cloud?.let { viewModelScope.launch { it.start() } }
        viewModelScope.launch { update = Updater.check(app) }
    }

    fun skipUpdate() {
        update = null
    }

    fun installUpdate() {
        val release = update ?: return
        if (downloading != null) return
        val app = getApplication<Application>()
        downloading = 0f
        viewModelScope.launch {
            try {
                val file = Updater.download(app, release) { p -> downloading = p }
                // 설치 허용 화면으로 갔다면 다시 눌러 설치할 수 있게 update 는 남겨 둔다.
                if (Updater.install(app, file)) update = null
            } catch (e: Exception) {
            } finally {
                downloading = null
            }
        }
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
