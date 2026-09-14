package com.vachak.content.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons ORDER BY grade ASC, id ASC")
    suspend fun getAll(): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE grade = :grade ORDER BY id ASC")
    suspend fun getByGrade(grade: Int): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LessonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lessons: List<LessonEntity>)

    @Query("SELECT COUNT(*) FROM lessons")
    suspend fun count(): Int
}

@Dao
interface OutcomeDao {
    @Query("SELECT * FROM outcomes WHERE outcomeId = :outcomeId LIMIT 1")
    suspend fun getById(outcomeId: String): OutcomeEntity?

    @Query("SELECT * FROM outcomes WHERE grade = :grade ORDER BY nipunCode ASC")
    suspend fun getByGrade(grade: Int): List<OutcomeEntity>

    @Query("SELECT * FROM outcomes ORDER BY grade ASC, nipunCode ASC")
    suspend fun getAll(): List<OutcomeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(outcomes: List<OutcomeEntity>)

    @Query("SELECT COUNT(*) FROM outcomes")
    suspend fun count(): Int
}

@Dao
interface WorksheetDao {
    @Query("SELECT * FROM worksheets WHERE lessonId = :lessonId ORDER BY templateType ASC")
    suspend fun getForLesson(lessonId: String): List<WorksheetEntity>

    @Query("SELECT * FROM worksheets ORDER BY lessonId ASC, templateType ASC")
    suspend fun getAll(): List<WorksheetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(worksheets: List<WorksheetEntity>)

    @Query("SELECT COUNT(*) FROM worksheets")
    suspend fun count(): Int
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE lessonId = :lessonId ORDER BY sequence ASC")
    suspend fun getForLesson(lessonId: String): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards ORDER BY lessonId ASC, sequence ASC")
    suspend fun getAll(): List<FlashcardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<FlashcardEntity>)

    @Query("SELECT COUNT(*) FROM flashcards")
    suspend fun count(): Int
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE lessonId = :lessonId ORDER BY sequence ASC")
    suspend fun getForLesson(lessonId: String): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ActivityEntity>)

    @Query("SELECT COUNT(*) FROM activities")
    suspend fun count(): Int
}

@Dao
interface AssessmentPromptDao {
    @Query("SELECT * FROM assessment_prompts WHERE lessonId = :lessonId ORDER BY id ASC")
    suspend fun getForLesson(lessonId: String): List<AssessmentPromptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AssessmentPromptEntity>)

    @Query("SELECT COUNT(*) FROM assessment_prompts")
    suspend fun count(): Int
}
