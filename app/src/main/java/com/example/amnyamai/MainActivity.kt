package com.example.amnyamai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.amnyamai.ui.navigation.AppNavigation
import com.example.amnyamai.ui.theme.AmnyamAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmnyamAiTheme {
                AppNavigation()
            }
        }
    }
}

