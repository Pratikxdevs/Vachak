package com.vachak.ui

import com.vachak.ui.screens.toolsPanelFromRoute
import com.vachak.ui.screens.ToolsPanel
import org.junit.Assert.*
import org.junit.Test

/**
 * Navigation route-table lock: every hierarchical destination resolves to a
 * unique path, grade/chapter args compose correctly, and tools sub-routes map
 * to the right studio panel. No mocks, no Android framework — pure JVM.
 */
class NavRouteTest {

    @Test
    fun `grade paths compose per class`() {
        assertEquals("learn/grade/1", NavRoute.Grade.path(1))
        assertEquals("learn/grade/5", NavRoute.Grade.path(5))
    }

    @Test
    fun `chapter paths nest under their grade`() {
        val p = NavRoute.Chapter.path(2, "fun-with-numbers")
        assertEquals("learn/grade/2/chapter/fun-with-numbers", p)
        assertTrue(p.startsWith(NavRoute.Grade.path(2) + "/"))
    }

    @Test
    fun `chapter study pages nest under their chapter`() {
        val ws = NavRoute.ChapterWorksheet.path(4, "hide-and-seek")
        val deck = NavRoute.ChapterDeck.path(4, "hide-and-seek")
        assertEquals("learn/grade/4/chapter/hide-and-seek/worksheet", ws)
        assertEquals("learn/grade/4/chapter/hide-and-seek/flashcards", deck)
        assertTrue(ws.startsWith(NavRoute.Chapter.path(4, "hide-and-seek") + "/"))
        assertTrue(deck.startsWith(NavRoute.Chapter.path(4, "hide-and-seek") + "/"))
    }

    @Test
    fun `all routes are unique`() {
        val routes = listOf(
            NavRoute.Home.route, NavRoute.Live.route, NavRoute.Curriculum.route,
            "curriculum/x", NavRoute.Grade.path(3), NavRoute.Chapter.path(3, "y"),
            "learn/lesson/z", NavRoute.Tools.route, NavRoute.ToolsWorksheets.route,
            NavRoute.ToolsFlashcards.route, NavRoute.ToolsSaved.route,
            NavRoute.ChapterWorksheet.path(1, "a"), NavRoute.ChapterDeck.path(1, "a"),
            NavRoute.Settings.route, NavRoute.ManagePacks.route, NavRoute.Diagnostics.route
        )
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `tools sub-route segments map to panels`() {
        assertEquals(ToolsPanel.Worksheets, toolsPanelFromRoute("worksheets"))
        assertEquals(ToolsPanel.Flashcards, toolsPanelFromRoute("flashcards"))
        assertEquals(ToolsPanel.Saved, toolsPanelFromRoute("saved"))
        assertEquals(ToolsPanel.Hub, toolsPanelFromRoute("nonsense"))
    }

    @Test
    fun `bottom-nav highlight roots are stable`() {
        // AdaptiveScaffold derives highlight via substringBefore("/").
        assertEquals("learn", "learn/grade/2/chapter/x".substringBefore("/"))
        assertEquals("tools", "tools/worksheets".substringBefore("/"))
    }
}
