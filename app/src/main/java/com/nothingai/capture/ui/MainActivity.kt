package com.nothingai.capture.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nothingai.capture.ui.detail.DetailScreen
import com.nothingai.capture.ui.gallery.GalleryScreen
import com.nothingai.capture.ui.theme.NothingTheme
import com.nothingai.capture.ui.theme.rememberThemeChoice
import com.nothingai.capture.ui.wizard.SetupChecks
import com.nothingai.capture.ui.wizard.SetupWizardScreen
import com.nothingai.capture.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeChoice by rememberThemeChoice()
            NothingTheme(themeChoice) {
                Surface {
                    val nav = rememberNavController()
                    val start = if (SetupChecks.read(this).ready) "gallery" else "wizard"
                    NavHost(nav, startDestination = start) {
                        composable("wizard") {
                            SetupWizardScreen(onDone = {
                                nav.navigate("gallery") { popUpTo("wizard") { inclusive = true } }
                            })
                        }
                        composable("gallery") {
                            GalleryScreen(onOpen = { id -> nav.navigate("detail/$id") }, onSettings = { nav.navigate("settings") })
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable("detail/{id}") { backStackEntry ->
                            DetailScreen(
                                id = backStackEntry.arguments!!.getString("id")!!,
                                onBack = { nav.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
