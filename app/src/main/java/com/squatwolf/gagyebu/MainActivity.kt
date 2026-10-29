package com.squatwolf.gagyebu

import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.squatwolf.gagyebu.ui.AutoScale
import com.squatwolf.gagyebu.ui.GagyebuTheme
import com.squatwolf.gagyebu.ui.LedgerScreen

class MainActivity : ComponentActivity() {
    private val vm: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GagyebuTheme {
                AutoScale(vm.uiScale) {
                    LedgerScreen(vm)
                }
            }
        }
    }

    // 홈으로 나가면 백그라운드에 남고, 뒤로 가기 → '종료'를 고르면 여기서 프로세스까지 끝낸다.
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !isChangingConfigurations) Process.killProcess(Process.myPid())
    }
}
