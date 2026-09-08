package com.vachak.ui.mock

import com.vachak.engine.Flashcard
import com.vachak.engine.Lesson
import com.vachak.engine.Worksheet
import com.vachak.engine.WorksheetItem

object MockData {
    val lessons = listOf(
        Lesson("G1-L-01", "अभिवादन और अपना नाम बताना", 1, "नमस्ते। मेरा नाम रीता है। मैं कक्षा एक में पढ़ती हूँ।", "ᱡᱚᱦᱟᱨ ᱤᱧ ᱨᱤᱛᱟ ᱠᱟᱱᱟᱹᱧ", true, "oral"),
        Lesson("G1-L-02", "Letters & Sounds", 1, "Learn the first sounds and their corresponding Ol Chiki forms.", "ᱚᱞ ᱪᱤᱠᱤ ᱟᱠᱷᱚᱨ ᱠᱚ", true, "reading"),
        Lesson("G1-M-01", "Numbers and Counting", 1, "गिनना सीखें: एक से दस तक।", "ᱞᱮᱠᱷᱟ ᱢᱤᱫ ᱠᱷᱚᱱ ᱜᱮᱞ ᱦᱟᱹᱵᱤᱡ", true, "oral"),
        Lesson("G1-M-02", "Numbers 1 to 10", 1, "1, 2, 3, 4, 5, 6, 7, 8, 9, 10", "ᱢᱤᱫ, ᱵᱟᱨ, ᱯᱮ, ᱯᱩᱱ, ᱢᱚᱬᱮ, ᱛᱩᱨᱩᱭ, ᱮᱟᱭ, ᱤᱨᱟᱹᱞ, ᱟᱨᱮ, ᱜᱮᱞ", true, "writing"),
        Lesson("G1-L-03", "My Family", 1, "मेरा परिवार — माँ, पिता, भाई, बहन।", "ᱟᱞᱮᱭᱟᱜ ᱜᱷᱟᱨᱚᱸᱡ", true, "oral"),
        Lesson("G1-E-01", "Shapes Around Us", 1, "गोल, चौकोर, त्रिकोण — आकारों को पहचानें।", "ᱵᱟᱹᱭᱦᱟᱹᱨ ᱨᱮ ᱟᱠᱟᱨ", true, "reading"),
        Lesson("G2-M-01", "Addition Basics", 2, "जोड़ना सीखें: 2 + 3 = 5", "ᱡᱚᱲᱟᱣ ᱪᱮᱫ", true, "writing"),
        Lesson("G2-E-01", "Plants Around Us", 2, "पौधे — पत्ते, तना, जड़।", "ᱫᱟᱨᱮ ᱟᱨ ᱯᱟᱛᱟ", true, "reading")
    )

    // Worksheet templates per product spec §17
    val worksheetTemplates = listOf(
        "Count objects" to "count_objects_v1",
        "Match picture to word" to "match_picture_word",
        "Fill missing number" to "fill_missing_number",
        "Compare numbers" to "compare_numbers",
        "Circle correct answer" to "circle_correct",
        "Match word/picture" to "match_word_picture",
        "Oral assessment" to "oral_assessment",
        "Reading vocabulary" to "reading_vocab"
    )

    fun mockWorksheet(lessonId: String, template: String = "count_objects_v1"): Worksheet {
        return Worksheet(
            id = "ws-${lessonId}-${template}",
            lessonId = lessonId,
            template = template,
            items = listOf(
                WorksheetItem("हिन्दी: ${lessons.find { it.id == lessonId }?.title ?: lessonId} — प्रश्न 1", "Santali: ${lessons.find { it.id == lessonId }?.translatedText ?: "—"}"),
                WorksheetItem("Fill in the blanks: ___ आम", "ᱟᱢ"),
                WorksheetItem("Match: 1 → ᱑, 2 → ᱒", "1-᱑ matching")
            )
        )
    }

    val flashcardDecks = listOf(
        Deck("Ol Chiki Vowels", "ᱚ ᱟ ᱤ", 18, "ᱚ"),
        Deck("Hindi Varnamala", "अ आ इ", 20, "अ"),
        Deck("Number Names", "एक दो तीन", 15, "१"),
        Deck("Plants Around Us", "पौधे", 12, "🌿"),
        Deck("My Family Words", "परिवार", 16, "👨‍👩‍👧"),
        Deck("Shapes", "आकार", 14, "⬢"),
        Deck("Colours", "रंग", 12, "🎨"),
        Deck("Animals", "जानवर", 18, "🐘")
    )

    data class Deck(val title: String, val subtitle: String, val count: Int, val glyph: String)

    val flashcards = mapOf(
        "G1-L-01" to listOf(
            Flashcard("नमस्ते", "ᱡᱚᱦᱟᱨ", "flashcard/assets/johar_greeting.png"),
            Flashcard("मेरा नाम", "ᱤᱧ ᱧᱩᱛᱩᱢ", "flashcard/assets/l-sat-g1-oral-01_1.png"),
            Flashcard("आप कैसे हैं?", "ᱪᱮᱫ ᱞᱮᱠᱟᱢ ᱢᱮᱱᱟᱢ?", "flashcard/assets/l-sat-g1-oral-01_2.png")
        ),
        "G1-M-01" to listOf(
            Flashcard("एक", "ᱢᱤᱫ", "flashcard/assets/number_1.png"),
            Flashcard("दो", "ᱵᱟᱨ", "flashcard/assets/number_2.png"),
            Flashcard("तीन", "ᱯᱮ", "flashcard/assets/number_3.png"),
            Flashcard("चार", "ᱯᱩᱱ", "flashcard/assets/number_4.png")
        )
    )

    val recentWorksheets = listOf(
        RecentWs("Vowels Recognition", "Grade 1 • Language", "Generated today, 10:15 AM", "G1-L-02"),
        RecentWs("Numbers 1 to 10", "Grade 1 • Mathematics", "Generated yesterday, 4:30 PM", "G1-M-02"),
        RecentWs("Parts of Plants", "Grade 2 • EVS", "Generated 2 days ago, 9:20 AM", "G2-E-01")
    )
    data class RecentWs(val title: String, val meta: String, val time: String, val lessonId: String)
}
