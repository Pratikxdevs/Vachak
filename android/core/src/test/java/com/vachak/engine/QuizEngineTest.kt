package com.vachak.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEngineTest {
    private fun deck() = FlashcardDeck(
        "L1", listOf(
            Flashcard("अ", "ᱚ", "a.png"),
            Flashcard("आ", "ᱟ", "b.png"),
            Flashcard("इ", "ᱤ", "c.png"),
            Flashcard("ई", "ᱤᱤ", "d.png"),
            Flashcard("उ", "ᱩ", "e.png")
        )
    )

    @Test
    fun `build yields answerable questions`() {
        val quiz = QuizEngine.build(deck(), count = 3, seed = 1L)
        assertEquals(3, quiz.questions.size)
        quiz.questions.forEach { q ->
            assertTrue(q.options.size in 2..4)
            assertEquals(q.prompt.let { p -> deck().cards.first { it.front == p }.back }, q.options[q.answerIndex])
        }
    }

    @Test
    fun `build is deterministic per seed`() {
        val a = QuizEngine.build(deck(), seed = 7L)
        val b = QuizEngine.build(deck(), seed = 7L)
        assertEquals(a.questions.map { it.options }, b.questions.map { it.options })
    }

    @Test
    fun `small deck still yields options`() {
        val tiny = FlashcardDeck("L2", listOf(Flashcard("अ", "ᱚ", "a.png")))
        val quiz = QuizEngine.build(tiny, count = 2, seed = 0L)
        assertEquals(1, quiz.questions.size)
        assertTrue(quiz.questions[0].options.contains("ᱚ"))
    }

    @Test
    fun `blank cards skipped`() {
        val mixed = FlashcardDeck("L3", listOf(Flashcard("अ", "", "a.png"), Flashcard("आ", "ᱟ", "b.png")))
        val quiz = QuizEngine.build(mixed, seed = 0L)
        assertEquals(1, quiz.questions.size)
    }

    @Test
    fun `grade counts correct answers`() {
        val quiz = QuizEngine.build(deck(), count = 2, seed = 1L)
        val perfect = quiz.questions.mapIndexed { i, q -> i to q.answerIndex }.toMap()
        assertEquals(2 to 2, QuizEngine.grade(quiz, perfect))
        assertEquals(0 to 2, QuizEngine.grade(quiz, emptyMap()))
        val wrong = quiz.questions.mapIndexed { i, q -> i to ((q.answerIndex + 1) % q.options.size) }.toMap()
        val (c, _) = QuizEngine.grade(quiz, wrong)
        assertFalse(c == 2)
    }
}
