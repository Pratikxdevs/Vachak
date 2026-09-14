package com.vachak.engine.orchestrator

import com.vachak.engine.LanguagePair

/**
 * Live classroom session state (PHASE 9B). Mirrors shared/orchestrator/classroom_state.py.
 * Persist the snapshot to Room; the Compose UI reads [presentText]/[presentAudio].
 */
data class ClassroomState(
    var currentLessonId: String? = null,
    var lessonTitle: String? = null,
    var activity: String? = null,
    var grade: Int? = null,
    var subject: String? = null,
    var sourceLanguage: String = "hi",
    var targetLanguage: String = "sat_Olck",
    var transcript: String? = null,        // recognized Hindi
    var translation: String? = null,       // translated Mundari / Ol Chiki
    var studentResponse: String? = null,
    var lastStage: String? = null,
    var lastStatus: String? = null,
    var lastError: String? = null,
    var lastAction: String? = null,
) {
    fun snapshot(): Map<String, Any?> = mapOf(
        "lesson" to currentLessonId,
        "transcript" to transcript,
        "translation" to translation,
        "studentResponse" to studentResponse,
        "lastStage" to lastStage,
        "lastStatus" to lastStatus,
        "lastError" to lastError,
    )
}
