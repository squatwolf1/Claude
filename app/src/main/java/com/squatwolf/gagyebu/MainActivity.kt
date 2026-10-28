package com.squatwolf.gagyebu

import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.squatwolf.gagyebu.ui.GagyebuTheme
import com.squatwolf.gagyebu.ui.LedgerScreen

class MainActivity : ComponentActivity() {
    private val vm: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GagyebuTheme {
                LedgerScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppExit.leavingForExternal = false
    }

    // 홈으로 나가거나 뒤로 가기로 닫으면 백그라운드에 남기지 않고 최근 앱 목록에서도 지운다.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !AppExit.leavingForExternal) finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !isChangingConfigurations) Process.killProcess(Process.myPid())
    }
}
