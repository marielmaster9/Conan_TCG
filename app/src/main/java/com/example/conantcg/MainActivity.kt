package com.example.conantcg
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import com.example.conantcg.ui.screens.MainAppScreen
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.ConanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[CardViewModel::class.java]
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = ConanTheme.BlueSuit,
                secondary = ConanTheme.BowTieRed,
                background = ConanTheme.BrickBg,
                surface = Color.White
            )) {
                MainAppScreen(vm)
            }
        }
    }
}