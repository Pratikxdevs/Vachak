package com.vachak.content.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities for Santali FLN curriculum (Phase 4).
 * Mirrors curriculum/lessons/sat_lessons.json + curriculum/outcomes/nipun.json.
 * Translations are PRECOMPUTED — never generated on device. See AGENTS.md hard rules.
 * Budgets: content 10–30MB, flashcard images 20–50MB (AGENTS.md).
 */

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val id: String,
    val titleHi: String,
    val titleSatOlChiki: String,
    val textHi: String,
    val textSatOlChiki: String,
    val grade: Int,
    val domain: String, // oral | reading | writing
    val outcomeId: String,
    val estimatedMinutes: Int,
    val precomputed: Boolean = true
)

@Entity(
    tableName = "outcomes",
    indices = [Index("outcomeId", unique = true)]
)
data class OutcomeEntity(
    @PrimaryKey val outcomeId: String,
    val nipunCode: String,
    val descriptor: String,
    val descriptorHi: String,
    val descriptorSatOlChiki: String,
    val grade: Int,
    val domain: String
)

@Entity(
    tableName = "worksheets",
    foreignKeys = [ForeignKey(
        entity = LessonEntity::class,
        parentColumns = ["id"],
        childColumns = ["lessonId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("lessonId")]
)
data class WorksheetEntity(
    @PrimaryKey val id: String,
    val lessonId: String,
    val templateType: String, // trace | fill_blank | match | comprehension
    val assetPath: String, // e.g. worksheet/templates/trace_olchiki_G1.pdf
    val titleHi: String,
    val titleSatOlChiki: String
)

@Entity(
    tableName = "flashcards",
    foreignKeys = [ForeignKey(
        entity = LessonEntity::class,
        parentColumns = ["id"],
        childColumns = ["lessonId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("lessonId"), Index("cardId") ]
)
data class FlashcardEntity(
    @PrimaryKey val cardId: String,
    val lessonId: String,
    val conceptHi: String,
    val conceptSatOlChiki: String,
    val conceptEn: String,
    val imagePath: String, // e.g. flashcard/assets/number_1.png
    val sequence: Int
)
