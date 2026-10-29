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
import com.squatwolf.gagyebu.data.Rule
import com.squatwolf.gagyebu.data.Savings
import com.squatwolf.gagyebu.data.Updater
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    val saved: StateFlow<Savings> = repo.saved.stateIn(viewModelScope, SharingStarted.Eagerly, Savings())

    /** 저금 목표. 이 폰에만 저장한다. */
    var goal by mutableStateOf(prefs.getLong("goal", 1_000_000L))
        private set

    /** 설정에서 고른 화면 크기 배율. */
    var uiScale by mutableStateOf(prefs.getFloat("uiScale", 1f))
        private set

    fun changeUiScale(value: Float) {
        uiScale = value
        prefs.edit().putFloat("uiScale", value).commit()
    }

    /** 총자산을 직접 고친 만큼의 금액. 저금(사용가능)에 더해서 보여 준다. 이 폰에만 저장. */
    var assetBase by mutableStateOf(prefs.getLong("assetBase", 0L))
        private set

    fun changeAssetBase(value: Long) {
        assetBase = value
        prefs.edit().putLong("assetBase", value).commit()
    }

    fun changeGoal(value: Long) {
        goal = value
        prefs.edit().putLong("goal", value).commit()
    }

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

    /** 이번에 지운 고정 내역. 건너뛰기 표시가 늦게 와도 다시 만들지 않게 기억해 둔다. */
    private val removed = mutableSetOf<String>()

    init {
        cloud?.let { viewModelScope.launch { it.start() } }
        // 보고 있는 달에 매달 고정 내역이 빠져 있으면 채운다.
        viewModelScope.launch {
            combine(snapshotFlow { month }, repo.rules, entries) { m, rules, list -> Triple(m, rules, list) }
                .collect { (m, rules, list) ->
                    val have = list.map { it.id }.toSet()
                    rules.filter { it.activeIn(m) }
                        .map { it.entryFor(m) }
                        .filter { it.id !in have && it.id !in removed }
                        // add 가 아니라 ensure: 시작 직후 목록이 아직 비어 있어도 고친 내역을 덮어쓰지 않는다.
                        .forEach { repo.ensure(it) }
                }
        }
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

    fun pickMonth(value: YearMonth) {
        month = value
        selected = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1)
    }

    fun moveMonth(delta: Long) {
        month = month.plusMonths(delta)
        selected = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1)
    }

    fun changeName(value: String) {
        name = value.trim().ifEmpty { "나" }
        prefs.edit().putString("name", name).commit()
    }

    fun add(income: Boolean, amount: Long, category: String, memo: String, method: String, monthly: Boolean) {
        if (monthly) {
            // 이번 달 내역은 위의 채우기가 만든다.
            repo.addRule(
                Rule(
                    id = UUID.randomUUID().toString().take(12),
                    day = selected.dayOfMonth,
                    income = income,
                    amount = amount,
                    category = category,
                    memo = memo,
                    method = method,
                    author = name,
                    start = YearMonth.from(selected).toString(),
                )
            )
        } else {
            repo.add(Entry("", selected.toString(), income, amount, category, memo, method, name))
        }
    }

    /** 적어 둔 내역을 고친다. 같은 id 로 덮어쓴다. */
    fun update(entry: Entry, income: Boolean, amount: Long, category: String, memo: String, method: String) {
        repo.add(entry.copy(income = income, amount = amount, category = category, memo = memo, method = method))
    }

    /** 일반 내역을 매달 고정으로 바꾼다. 그 날짜로 규칙을 만들고, 원래 내역은 규칙이 만든 내역으로 바뀐다. */
    fun makeMonthly(entry: Entry, income: Boolean, amount: Long, category: String, memo: String, method: String) {
        repo.addRule(
            Rule(
                id = UUID.randomUUID().toString().take(12),
                day = LocalDate.parse(entry.date).dayOfMonth,
                income = income,
                amount = amount,
                category = category,
                memo = memo,
                method = method,
                author = entry.author.ifEmpty { name },
                start = entry.date.take(7),
            )
        )
        repo.delete(entry.id)
    }

    /** 내역 하나를 지운다. 고정 내역이면 그 달만 건너뛴다. */
    fun delete(entry: Entry) {
        if (entry.ruleId.isNotEmpty()) {
            removed += entry.id
            repo.skip(entry.ruleId, entry.date.take(7))
        }
        repo.delete(entry.id)
    }

    /** 고정 내역을 이 달부터 그만둔다. */
    fun stopMonthly(entry: Entry) {
        removed += entry.id
        repo.stop(entry.ruleId, entry.date.take(7))
        repo.delete(entry.id)
    }
}
