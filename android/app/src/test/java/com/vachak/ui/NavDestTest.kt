package com.vachak.ui

import com.vachak.ui.navigation.NavDest
import org.junit.Assert.*
import org.junit.Test

/**
 * Bottom-nav highlight lock: nested Learn (learn/grade/…/chapter/…/worksheet,
 * learn/lesson/…) highlights Learn — never Home — and nested Tools
 * (tools/worksheets|…) highlights Tools. Guards the "continue jumps home and
 * spams" regression at the route-table level (pure JVM, no framework).
 */
class NavDestTest {

    @Test
    fun `learn nested routes map to Curriculum`() {
        assertEquals(NavDest.Curriculum, NavDest.fromRoute("curriculum"))
        assertEquals(NavDest.Curriculum, NavDest.fromRoute("learn/grade/2"))
        assertEquals(NavDest.Curriculum, NavDest.fromRoute("learn/grade/2/chapter/fun-with-numbers"))
        assertEquals(
            NavDest.Curriculum,
            NavDest.fromRoute("learn/grade/4/chapter/hide-and-seek/worksheet")
        )
        assertEquals(NavDest.Curriculum, NavDest.fromRoute("learn/lesson/L-SAT-G1-ORAL-01"))
        assertEquals(NavDest.Curriculum, NavDest.fromRoute("curriculum/L-SAT-G1-ORAL-01"))
    }

    @Test
    fun `tools nested routes map to Tools`() {
        assertEquals(NavDest.Tools, NavDest.fromRoute("tools"))
        assertEquals(NavDest.Tools, NavDest.fromRoute("tools/worksheets"))
        assertEquals(NavDest.Tools, NavDest.fromRoute("tools/flashcards"))
        assertEquals(NavDest.Tools, NavDest.fromRoute("tools/saved"))
    }

    @Test
    fun `top-level roots still map`() {
        assertEquals(NavDest.Home, NavDest.fromRoute("home"))
        assertEquals(NavDest.Live, NavDest.fromRoute("live"))
        assertEquals(NavDest.Settings, NavDest.fromRoute("settings"))
        assertEquals(NavDest.Diagnostics, NavDest.fromRoute("diagnostics"))
        assertEquals(NavDest.ManagePacks, NavDest.fromRoute("packs"))
        assertEquals(NavDest.Home, NavDest.fromRoute(null))
    }
}
