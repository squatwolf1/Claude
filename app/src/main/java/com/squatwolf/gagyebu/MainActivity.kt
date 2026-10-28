package com.squatwolf.gagyebu

import android.os.Bundle
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
}
