package com.vachak.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Heroicons v2 — Primary icon system for Vachak (per product spec)
 *
 * Default: Heroicons Outline (24dp, stroke 1.5) → Maps to Material Outlined (20-24dp)
 * Active/selected: Heroicons Solid → Maps to Material Filled
 * Compact UI: Heroicons Mini (20dp) → Maps to Material Outlined 20dp with 44dp touch target
 *
 * Do NOT mix Material Icons, Font Awesome, emoji as UI icons.
 * All functional icons are 20–24dp, icon-only touch targets min 44dp.
 * This file provides a semantic mapping so future migration to true Heroicons Compose
 * library is a one-line swap. Currently uses Material icons as 1:1 visual proxy
 * (outline stroke, weight, and optical size match Heroicons Outline).
 */
object HeroiconsOutline {
    val MagnifyingGlass: ImageVector get() = Icons.Outlined.Search
    val Bell: ImageVector get() = Icons.Outlined.Notifications
    val User: ImageVector get() = Icons.Outlined.Person
    val Home: ImageVector get() = Icons.Outlined.Home
    val Microphone: ImageVector get() = Icons.Outlined.Mic
    val BookOpen: ImageVector get() = Icons.AutoMirrored.Outlined.MenuBook
    val WrenchScrewdriver: ImageVector get() = Icons.Outlined.Build
    val Cog6Tooth: ImageVector get() = Icons.Outlined.Settings
    val DocumentText: ImageVector get() = Icons.Outlined.Description
    val InformationCircle: ImageVector get() = Icons.Outlined.Info
    val AdjustmentsHorizontal: ImageVector get() = Icons.Outlined.Tune
    val ChevronRight: ImageVector get() = Icons.Outlined.ChevronRight
    val ArrowRight: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowForward
    val Play: ImageVector get() = Icons.Outlined.PlayArrow
    val Book: ImageVector get() = Icons.Outlined.MenuBook
    val Clock: ImageVector get() = Icons.Outlined.Schedule
    val CheckCircle: ImageVector get() = Icons.Outlined.CheckCircle
    val Squares2x2: ImageVector get() = Icons.Outlined.GridView
    val Calculator: ImageVector get() = Icons.Outlined.Calculate
    val SpeakerWave: ImageVector get() = Icons.AutoMirrored.Outlined.VolumeUp
    val DocumentDuplicate: ImageVector get() = Icons.Outlined.ContentCopy
    val Trash: ImageVector get() = Icons.Outlined.Delete
    val Folder: ImageVector get() = Icons.Outlined.Folder
    val Language: ImageVector get() = Icons.Outlined.Language
    val AcademicCap: ImageVector get() = Icons.Outlined.School
}

object HeroiconsSolid {
    val Home: ImageVector get() = Icons.Filled.Home
    val Microphone: ImageVector get() = Icons.Filled.Mic
    val BookOpen: ImageVector get() = Icons.AutoMirrored.Filled.MenuBook
    val WrenchScrewdriver: ImageVector get() = Icons.Filled.Build
    val Cog6Tooth: ImageVector get() = Icons.Filled.Settings
    val CheckCircle: ImageVector get() = Icons.Filled.CheckCircle
    val InformationCircle: ImageVector get() = Icons.Filled.Info
}

object HeroiconsMini {
    // 20dp compact — same icons at 20dp with 44dp touch target
    val MagnifyingGlass: ImageVector get() = Icons.Outlined.Search // rendered at 20dp
    val Bell: ImageVector get() = Icons.Outlined.Notifications
}
