package com.vachak.engine

/**
 * Deterministic quiz builder: turns a [FlashcardDeck] into multiple-choice
 * questions (front → pick the matching back from 4 options). Pure Kotlin,
 * seeded shuffle for stable tests. No AI generation — options come from the
 * prebuilt deck itself.
 */
object QuizEngine {
    data class Question(
        val prompt: String,
        val options: List<String>,
        val answerIndex: Int
    )

    data class Quiz(val lessonId: String, val questions: List<Question>)

    /**
     * Build up to [count] questions. Distractors are backs from other cards
     * (deterministic rotation when the deck is small). Cards with blank backs
     * are skipped. Options are shuffled with [seed].
     */
    fun build(deck: FlashcardDeck, count: Int = 10, seed: Long = 0L): Quiz {
        val usable = deck.cards.filter { it.front.isNotBlank() && it.back.isNotBlank() }
        val rnd = kotlin.random.Random(seed)
        val questions = usable.shuffled(rnd).take(count.coerceAtLeast(1)).mapIndexed { qi, card ->
            val others = usable.filter { it.back != card.back }.map { it.back }.distinct()
            val distractors = if (others.isEmpty()) {
                listOf(card.back)
            } else {
                // Deterministic rotation so small decks still yield 3 distractors.
                List(3) { k -> others[(qi + k) % others.size] }
            }
            val options = (distractors + card.back).distinct().shuffled(rnd).take(4).toMutableList()
            if (card.back !in options) options[0] = card.back
            val opts = options.shuffled(rnd)
            Question(card.front, opts, opts.indexOf(card.back))
        }
        return Quiz(deck.lessonId, questions)
    }

    fun grade(quiz: Quiz, answers: Map<Int, Int>): Pair<Int, Int> {
        var correct = 0
        quiz.questions.forEachIndexed { i, q ->
            if (answers[i] == q.answerIndex) correct++
        }
        return correct to quiz.questions.size
    }
}
