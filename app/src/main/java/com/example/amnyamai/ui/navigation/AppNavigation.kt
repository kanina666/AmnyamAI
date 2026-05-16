package com.example.amnyamai.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.amnyamai.ui.screens.HistoryScreen
import com.example.amnyamai.ui.screens.HomeScreen
import com.example.amnyamai.ui.screens.JoinMeetingScreen
import com.example.amnyamai.ui.screens.RecordingScreen
import com.example.amnyamai.ui.screens.RegisterScreen
import com.example.amnyamai.ui.screens.ResultScreen
import com.example.amnyamai.ui.viewmodel.RegisterViewModel

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val registerVm: RegisterViewModel = viewModel()
    val registered by registerVm.registered.collectAsState()

    val start = if (registered) "home" else "register"

    NavHost(navController = nav, startDestination = start) {

        composable("register") {
            RegisterScreen(
                onRegistered = { nav.navigate("home") { popUpTo("register") { inclusive = true } } }
            )
        }

        composable("home") {
            HomeScreen(
                onMeetingCreated = { meetingId, code ->
                    nav.navigate("recording/${Uri.encode(meetingId)}/${Uri.encode(code)}")
                },
                onJoinMeeting = { nav.navigate("join") },
                onHistory = { nav.navigate("history") }
            )
        }

        composable(
            "recording/{meetingId}/{code}",
            arguments = listOf(
                navArgument("meetingId") { type = NavType.StringType },
                navArgument("code") { type = NavType.StringType }
            )
        ) { entry ->
            RecordingScreen(
                meetingId = Uri.decode(entry.arguments?.getString("meetingId") ?: ""),
                code = Uri.decode(entry.arguments?.getString("code") ?: ""),
                onDone = { meetingId ->
                    nav.navigate("result/${Uri.encode(meetingId)}") {
                        popUpTo("home")
                    }
                }
            )
        }

        composable("join") {
            JoinMeetingScreen(
                onMeetingEnded = { meetingId ->
                    nav.navigate("result/${Uri.encode(meetingId)}") {
                        popUpTo("home")
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            "result/{meetingId}",
            arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
        ) { entry ->
            ResultScreen(
                meetingId = Uri.decode(entry.arguments?.getString("meetingId") ?: ""),
                onDone = { nav.navigate("home") { popUpTo("home") { inclusive = true } } }
            )
        }

        composable("history") {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
    }
}
