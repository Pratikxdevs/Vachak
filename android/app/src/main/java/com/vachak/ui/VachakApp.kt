package com.vachak.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineProvider
import com.vachak.ui.debug.DebugOverlay
import com.vachak.ui.debug.VachakLogger
import com.vachak.ui.navigation.AdaptiveScaffold
import com.vachak.ui.navigation.NavDest
import com.vachak.ui.screens.*
import com.vachak.ui.ManagePacksScreen
import com.vachak.ui.theme.VachakTheme

/** Type-safe destinations — lessonId arg for deep-link via NavBackStackEntry savedStateHandle */
sealed class NavRoute(val route: String) {
    data object Home : NavRoute("home")
    data object Live : NavRoute("live")
    data object Curriculum : NavRoute("curriculum")
    data class CurriculumDetail(val lessonId: String) : NavRoute("curriculum/{lessonId}")
    data object Tools : NavRoute("tools")
    data object Settings : NavRoute("settings")
    data object ManagePacks : NavRoute("packs")
    data object Diagnostics : NavRoute("diagnostics")
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun VachakApp(
    engine: EngineProvider,
    startDest: NavDest = NavDest.Home
) {
    val navController = rememberNavController()
    val activeLang by engine.activeLanguage.collectAsState()
    val debugEnabled by VachakLogger.enabled.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val isTablet = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded

    // Derive current NavDest from NavController for AdaptiveScaffold highlight
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("/")
    val current = NavDest.fromRoute(currentRoute)

    VachakTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                AdaptiveScaffold(
                    current = current,
                    onNavigate = { dest ->
                        navController.navigate(dest.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    windowSizeClass = windowSizeClass,
                    isTablet = isTablet
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = startDest.route
                    ) {
                        composable(NavRoute.Home.route) {
                            HomeScreen(
                                engine = engine,
                                onNavigateLive = { navController.navigate(NavDest.Live.route) },
                                onNavigateTools = { navController.navigate(NavDest.Tools.route) },
                                onContinueLesson = { lesson ->
                                    // Use SavedStateHandle for pending lesson (no global mutableState)
                                    navController.currentBackStackEntry?.savedStateHandle?.set("pendingLessonId", lesson.id)
                                    navController.navigate(NavDest.Curriculum.route)
                                }
                            )
                        }
                        composable(NavRoute.Live.route) {
                            LiveScreen(engine = engine)
                        }
                        composable(NavRoute.Curriculum.route) {
                            // pendingLesson via SavedStateHandle (lessonId string, not global mutableState)
                            val pendingLessonId = navController.previousBackStackEntry
                                ?.savedStateHandle?.get<String>("pendingLessonId")
                            CurriculumScreen(
                                engine = engine,
                                onOpenLesson = { lesson ->
                                    navController.currentBackStackEntry?.savedStateHandle?.set("pendingLessonId", lesson.id)
                                    navController.navigate(NavDest.Tools.route)
                                }
                            )
                        }
                        composable(
                            route = "curriculum/{lessonId}",
                            arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
                        ) { entry ->
                            val lessonId = entry.arguments?.getString("lessonId")
                            // lessonId deep-link via savedStateHandle
                            entry.savedStateHandle.set("lessonId", lessonId)
                            CurriculumScreen(
                                engine = engine,
                                onOpenLesson = { lesson ->
                                    navController.currentBackStackEntry?.savedStateHandle?.set("pendingLessonId", lesson.id)
                                    navController.navigate(NavDest.Tools.route)
                                }
                            )
                        }
                        composable(NavRoute.Tools.route) {
                            ToolsScreen(engine = engine)
                        }
                        composable(NavRoute.Settings.route) {
                            SettingsScreen(
                                engine = engine,
                                onManagePacks = { navController.navigate(NavDest.ManagePacks.route) },
                                onDiagnostics = { navController.navigate(NavDest.Diagnostics.route) }
                            )
                        }
                        composable(NavRoute.ManagePacks.route) {
                            ManagePacksScreen()
                        }
                        composable(NavRoute.Diagnostics.route) {
                            com.vachak.ui.screens.DiagnosticsScreen(engine = engine)
                        }
                    }
                }
                // Top-bar language switcher (overlay, minimal, global)
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp)
                ) {
                    com.vachak.ui.debug.LanguageSwitcher(
                        activeLang = activeLang,
                        onSelect = { lang ->
                            ActiveLanguage.set(lang)
                            (engine.translation as? com.vachak.ml.adapter.AdapterTranslationEngine)?.setActiveLanguage(lang)
                        }
                    )
                }
                // Debug overlay floating log viewer (200 line ring buffer Vachak-*)
                if (debugEnabled) {
                    DebugOverlay(
                        visible = true,
                        onClose = { VachakLogger.setEnabled(false) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}
