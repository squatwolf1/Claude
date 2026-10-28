package com.squatwolf.busalarm

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.squatwolf.busalarm.ui.MainScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WatchService.createChannels(this)
        Scheduler.reschedule(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1B6E3C))) {
                MainScreen(vm)
            }
        }
    }
}

/** 화면에 보이는 실시간 도착 정보. */
data class Live(val arrivals: List<Arrival>, val error: String? = null)

class AlarmViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()

    var alarms by mutableStateOf(Store.alarms(app))
        private set
    var live by mutableStateOf<Map<String, Live>>(emptyMap())
        private set
    var busKey by mutableStateOf(Store.busKey(app))
        private set
    var subwayKey by mutableStateOf(Store.subwayKey(app))
        private set

    fun save(alarm: Alarm) {
        alarms = if (alarms.any { it.id == alarm.id }) {
            alarms.map { if (it.id == alarm.id) alarm else it }
        } else {
            alarms + alarm
        }
        persist()
        refresh(alarm)
    }

    fun toggle(alarm: Alarm) = save(alarm.copy(enabled = !alarm.enabled))

    fun delete(alarm: Alarm) {
        alarms = alarms.filterNot { it.id == alarm.id }
        live = live - alarm.id
        persist()
    }

    fun saveKeys(bus: String, subway: String) {
        Store.saveKeys(ctx, bus, subway)
        busKey = Store.busKey(ctx)
        subwayKey = Store.subwayKey(ctx)
        refreshAll()
    }

    fun refreshAll() = alarms.forEach { refresh(it) }

    private fun refresh(alarm: Alarm) {
        viewModelScope.launch {
            val result = try {
                Live(Api.arrivals(ctx, alarm))
            } catch (e: Exception) {
                Live(emptyList(), e.message ?: "불러오기 실패")
            }
            live = live + (alarm.id to result)
        }
    }

    private fun persist() {
        Store.saveAlarms(ctx, alarms)
        Scheduler.reschedule(ctx)
    }
}
