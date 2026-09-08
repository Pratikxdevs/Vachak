// ============================================================================
// Vachak Curriculum — Room @Entity definitions (Phase 3A)
// ----------------------------------------------------------------------------
// These mirror schemas/schema.sql. They live under curriculum/schemas (NOT the
// android/ module) as the canonical schema contract. The android/ app copies
// these into its Room database module. Room annotations are standard; no
// runtime dependency beyond androidx.room.
//
// Language fields store three parallel strings per concept:
//   *_Hi      -> Hindi (Devanagari)
//   *_Target  -> Mundari/Santali (see localization layer; default Santali Ol Chiki)
//   *_En      -> English (dev/audit only)
// ============================================================================

package org.sih26042.vachak.curriculum

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "grades")
data class Grade(
    @PrimaryKey val gradeId: String,
    val gradeNumber: Int,
    val nameHi: String,
    val nameTarget: String,
    val nameEn: String
)

@Entity(
    tableName = "subjects",
    foreignKeys = [ForeignKey(
        entity = Grade::class,
        parentColumns = ["gradeId"],
        childColumns = ["gradeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("gradeId")]
)
data class Subject(
    @PrimaryKey val subjectId: String,
    val gradeId: String,
    val nameHi: String,
    val nameTarget: String,
    val nameEn: String
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = Subject::class,
        parentColumns = ["subjectId"],
        childColumns = ["subjectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("subjectId")]
)
data class Chapter(
    @PrimaryKey val chapterId: String,
    val subjectId: String,
    val nameHi: String,
    val nameTarget: String,
    val nameEn: String,
    val sequence: Int
)

@Entity(
    tableName = "lessons",
    foreignKeys = [ForeignKey(
        entity = Chapter::class,
        parentColumns = ["chapterId"],
        childColumns = ["chapterId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("chapterId")]
)
data class Lesson(
    @PrimaryKey val lessonId: String,
    val chapterId: String,
    val titleHi: String,
    val titleTarget: String,
    val titleEn: String,
    val sequence: Int,
    val estimatedMinutes: Int
)

@Entity(
    tableName = "learning_outcomes",
    foreignKeys = [ForeignKey(
        entity = Lesson::class,
        parentColumns = ["lessonId"],
        childColumns = ["lessonId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("lessonId")]
)
data class LearningOutcome(
    @PrimaryKey val outcomeId: String,
    val lessonId: String,
    val code: String,
    val descriptionHi: String,
    val descriptionTarget: String,
    val descriptionEn: String,
    val sequence: Int
)

@Entity(
    tableName = "teacher_instructions",
    foreignKeys = [ForeignKey(
        entity = Lesson::class,
        parentColumns = ["lessonId"],
        childColumns = ["lessonId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("lessonId")]
)
data class TeacherInstruction(
    @PrimaryKey val instructionId: String,
    val lessonId: String,
    val textHi: String,
    val textTarget: String,
    val textEn: String,
    val sequence: Int
)

@Entity(
    tableName = "activities",
    foreignKeys = [ForeignKey(
        entity = LearningOutcome::class,
        parentColumns = ["outcomeId"],
        childColumns = ["outcomeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("outcomeId")]
)
data class Activity(
    @PrimaryKey val activityId: String,
    val outcomeId: String,
    val titleHi: String,
    val titleTarget: String,
    val titleEn: String,
    val activityType: String,
    val instructionsHi: String,
    val instructionsTarget: String,
    val instructionsEn: String,
    val sequence: Int
)

@Entity(
    tableName = "assessments",
    foreignKeys = [ForeignKey(
        entity = LearningOutcome::class,
        parentColumns = ["outcomeId"],
        childColumns = ["outcomeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("outcomeId")]
)
data class Assessment(
    @PrimaryKey val assessmentId: String,
    val outcomeId: String,
    val type: String,
    val promptHi: String,
    val promptTarget: String,
    val promptEn: String,
    val answerKey: String,
    val difficulty: String,
    val sequence: Int
)

@Entity(
    tableName = "vocabulary_terms",
    foreignKeys = [ForeignKey(
        entity = Lesson::class,
        parentColumns = ["lessonId"],
        childColumns = ["lessonId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("lessonId")]
)
data class VocabularyTerm(
    @PrimaryKey val termId: String,
    val lessonId: String,
    val termHi: String,
    val termTarget: String,
    val termEn: String,
    val definitionHi: String?,
    val definitionTarget: String?,
    val definitionEn: String?
)

@Entity(
    tableName = "flashcard_concepts",
    foreignKeys = [ForeignKey(
        entity = Lesson::class,
        parentColumns = ["lessonId"],
        childColumns = ["lessonId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("lessonId")]
)
data class FlashcardConcept(
    @PrimaryKey val cardId: String,
    val lessonId: String,
    val conceptHi: String,
    val conceptTarget: String,
    val conceptEn: String,
    val imageRef: String?,
    val audioRef: String?,
    val sequence: Int
)
