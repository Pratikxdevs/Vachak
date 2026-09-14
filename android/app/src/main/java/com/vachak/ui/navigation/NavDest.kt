package com.vachak.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavDest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    data object Home : NavDest("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object Live : NavDest("live", "Live", Icons.Outlined.Mic, Icons.Filled.Mic)
    data object Curriculum : NavDest("curriculum", "Learn", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook)
    data object Tools : NavDest("tools", "Tools", Icons.Outlined.GridView, Icons.Filled.GridView)
    data object Settings : NavDest("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
    data object ManagePacks : NavDest("packs", "Packs", Icons.Outlined.Description, Icons.Filled.Description)
    data object Diagnostics : NavDest("diagnostics", "Diagnostics", Icons.Outlined.Info, Icons.Filled.Info)

    companion object {
        val all: List<NavDest> get() = listOf(Home, Live, Curriculum, Tools, Settings, Diagnostics)
        /** Bottom-nav highlight root for any destination route, including nested
         *  Learn (learn/grade/…/chapter/…/worksheet, learn/lesson/…) and Tools
         *  (tools/worksheets|flashcards|saved) sub-routes. Unknown routes fall
         *  back to Home only when they match nothing — never for a learn route. */
        fun fromRoute(route: String?): NavDest {
            if (route == null) return Home
            val root = route.substringBefore("/")
            return when {
                route == Home.route || root == "home" -> Home
                route == Live.route || root == "live" -> Live
                route == Curriculum.route || root == "curriculum" || root == "learn" -> Curriculum
                route == Tools.route || root == "tools" -> Tools
                route == Settings.route || root == "settings" -> Settings
                route == ManagePacks.route || root == "packs" -> ManagePacks
                route == Diagnostics.route || root == "diagnostics" -> Diagnostics
                else -> all.find { it.route == route } ?: Home
            }
        }
    }
}
