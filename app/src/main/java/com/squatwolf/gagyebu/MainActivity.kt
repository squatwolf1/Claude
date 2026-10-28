package com.squatwolf.gagyebu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import com.squatwolf.gagyebu.ui.LedgerScreen
import com.squatwolf.gagyebu.ui.Ink
import com.squatwolf.gagyebu.ui.Paper

class MainActivity : ComponentActivity() {
    private val vm: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Ink,
                    background = Paper,
                    surface = Paper,
                )
            ) {
                LedgerScreen(vm)
            }
        }
    }
}
