package com.vachak.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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

/** Type-safe destinations — lessonId arg for deep-link via NavBackStackEntry savedStateHandle.
 *  Hierarchy: learn → learn/grade/{g} → learn/grade/{g}/chapter/{slug};
 *  tools → tools/{worksheets|flashcards|saved}. No cross-funneling. */
sealed class NavRoute(val route: String) {
    data object Home : NavRoute("home")
    data object Live : NavRoute("live")
    data object Curriculum : NavRoute("curriculum")
    data class CurriculumDetail(val lessonId: String) : NavRoute("curriculum/{lessonId}")
    data class LearnLesson(val lessonId: String) : NavRoute("learn/lesson/{lessonId}")
    data class Grade(val grade: Int) : NavRoute("learn/grade/{grade}") {
        companion object {
            fun path(grade: Int) = "learn/grade/$grade"
        }
    }
    data class Chapter(val grade: Int, val slug: String) : NavRoute("learn/grade/{grade}/chapter/{slug}") {
        companion object {
            fun path(grade: Int, slug: String) = "learn/grade/$grade/chapter/$slug"
        }
    }
    data class ChapterWorksheet(val grade: Int, val slug: String) : NavRoute("learn/grade/{grade}/chapter/{slug}/worksheet") {
        companion object {
            fun path(grade: Int, slug: String) = "learn/grade/$grade/chapter/$slug/worksheet"
        }
    }
    data class ChapterDeck(val grade: Int, val slug: String) : NavRoute("learn/grade/{grade}/chapter/{slug}/flashcards") {
        companion object {
            fun path(grade: Int, slug: String) = "learn/grade/$grade/chapter/$slug/flashcards"
        }
    }
    data object Tools : NavRoute("tools")
    data object ToolsWorksheets : NavRoute("tools/worksheets")
    data object ToolsFlashcards : NavRoute("tools/flashcards")
    data object ToolsSaved : NavRoute("tools/saved")
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
    // Single-flight forward navigation: rapid double-taps never stack duplicates.
    fun navOnce(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }
    val activeLang by engine.activeLanguage.collectAsState()
    val debugEnabled by VachakLogger.enabled.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val isTablet = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded

    // Derive current NavDest from the FULL NavController route so nested Learn
    // (learn/grade/…/chapter/…/worksheet, learn/lesson/…) highlights Learn and
    // nested Tools (tools/worksheets|…) highlights Tools — never Home.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = NavDest.fromRoute(backStackEntry?.destination?.route)

    VachakTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                AdaptiveScaffold(
                    current = current,
                    onNavigate = { dest ->
                        // Bottom-nav taps never stack duplicates: pop to the tab root,
                        // keep per-tab state, and reuse the existing instance.
                        navController.navigate(dest.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
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
                                onNavigateLive = { navOnce(NavDest.Live.route) },
                                onNavigateTools = { navOnce(NavDest.Tools.route) },
                                onNavigateCurriculum = { navOnce(NavDest.Curriculum.route) },
                                onNavigateSettings = { navOnce(NavDest.Settings.route) },
                                onContinueLesson = { lesson ->
                                    // Single canonical lesson route — the Learn tree owns
                                    // lesson detail (never a second curriculum/* alias that
                                    // flips the bottom-nav highlight to Home).
                                    navOnce("learn/lesson/${lesson.id}")
                                }
                            )
                        }
                        composable(NavRoute.Live.route) {
                            LiveScreen(engine = engine)
                        }
                        composable(NavRoute.Curriculum.route) {
                            CurriculumScreen(
                                engine = engine,
                                onOpenLesson = { lesson ->
                                    navOnce("learn/lesson/${lesson.id}")
                                },
                                onOpenGrade = { grade ->
                                    navOnce(NavRoute.Grade.path(grade))
                                }
                            )
                        }
                        composable(
                            route = "learn/grade/{grade}",
                            arguments = listOf(navArgument("grade") { type = NavType.IntType })
                        ) { entry ->
                            val grade = entry.arguments?.getInt("grade") ?: 1
                            GradeScreen(
                                engine = engine,
                                grade = grade,
                                onOpenChapter = { slug ->
                                    navOnce(NavRoute.Chapter.path(grade, slug))
                                },
                                onOpenLesson = { lesson ->
                                    navOnce("learn/lesson/${lesson.id}")
                                },
                                onOpenPacks = { navOnce(NavDest.ManagePacks.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "learn/grade/{grade}/chapter/{slug}",
                            arguments = listOf(
                                navArgument("grade") { type = NavType.IntType },
                                navArgument("slug") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val grade = entry.arguments?.getInt("grade") ?: 1
                            val slug = entry.arguments?.getString("slug").orEmpty()
                            ChapterScreen(
                                engine = engine,
                                grade = grade,
                                slug = slug,
                                onOpenWorksheets = { navOnce(NavRoute.ChapterWorksheet.path(grade, slug)) },
                                onOpenFlashcards = { navOnce(NavRoute.ChapterDeck.path(grade, slug)) },
                                onOpenPacks = { navOnce(NavDest.ManagePacks.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "learn/grade/{grade}/chapter/{slug}/worksheet",
                            arguments = listOf(
                                navArgument("grade") { type = NavType.IntType },
                                navArgument("slug") { type = NavType.StringType }
                            )
                        ) { entry ->
                            ChapterWorksheetScreen(
                                engine = engine,
                                grade = entry.arguments?.getInt("grade") ?: 1,
                                slug = entry.arguments?.getString("slug").orEmpty(),
                                onOpenPacks = { navOnce(NavDest.ManagePacks.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "learn/grade/{grade}/chapter/{slug}/flashcards",
                            arguments = listOf(
                                navArgument("grade") { type = NavType.IntType },
                                navArgument("slug") { type = NavType.StringType }
                            )
                        ) { entry ->
                            ChapterDeckScreen(
                                engine = engine,
                                grade = entry.arguments?.getInt("grade") ?: 1,
                                slug = entry.arguments?.getString("slug").orEmpty(),
                                onOpenPacks = { navOnce(NavDest.ManagePacks.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "learn/lesson/{lessonId}",
                            arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
                        ) { entry ->
                            val lessonId = entry.arguments?.getString("lessonId").orEmpty()
                            LessonDetailScreen(
                                engine = engine,
                                lessonId = lessonId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "curriculum/{lessonId}",
                            arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
                        ) { entry ->
                            val lessonId = entry.arguments?.getString("lessonId").orEmpty()
                            LessonDetailScreen(
                                engine = engine,
                                lessonId = lessonId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(NavRoute.Tools.route) {
                            ToolsScreen(
                                engine = engine,
                                onOpenPanel = { panel -> navOnce("tools/${panel.name.lowercase()}") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(NavRoute.ToolsWorksheets.route) {
                            ToolsScreen(
                                engine = engine,
                                startPanel = ToolsPanel.Worksheets,
                                onOpenPanel = { panel -> navOnce("tools/${panel.name.lowercase()}") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(NavRoute.ToolsFlashcards.route) {
                            ToolsScreen(
                                engine = engine,
                                startPanel = ToolsPanel.Flashcards,
                                onOpenPanel = { panel -> navOnce("tools/${panel.name.lowercase()}") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(NavRoute.ToolsSaved.route) {
                            ToolsScreen(
                                engine = engine,
                                startPanel = ToolsPanel.Saved,
                                onOpenPanel = { panel -> navOnce("tools/${panel.name.lowercase()}") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(NavRoute.Settings.route) {
                            SettingsScreen(
                                engine = engine,
                                onManagePacks = { navOnce(NavDest.ManagePacks.route) },
                                onDiagnostics = { navOnce(NavDest.Diagnostics.route) }
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
                // Top-bar language switcher (overlay, minimal, global).
                // P4: statusBarsPadding — under edge-to-edge transparent bars
                // the chip sat behind the status bar, untappable.
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 4.dp, end = 8.dp)
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
