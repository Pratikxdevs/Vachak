package com.vachak.ml

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IndicProcessorPortTest {

    @Before
    fun setup() {
        IndicProcessorPort.clearQueue()
    }

    @Test
    fun `preprocessBatch matches Python IndicProcessor on 20 fixtures`() {
        val fixtures = listOf(
            "नमस्ते।",
            "बच्चों, पाँच आम गिनो।",
            "मैं स्कूल जा रहा हूँ।",
            "यह किताब बहुत अच्छी है।",
            "नमस्ते दुनिया!",
            "पानी (साफ) है।",
            "बहुत   सारा   स्पेस",
            "\"हैलो,\" उसने कहा।",
            "https://example.com पर जाओ",
            "मेरा ईमेल test@example.com है",
            "आज 12/05/2023 को मिलते हैं",
            "समय 12:30 बजे है",
            "१२३४५",
            "आज 123% बढ़ा",
            "नमस्ते दुनिया। आज मौसम अच्छा है।",
            "क्या हाल है?",
            "मैंने 100 रुपये दिए।",
            "हैश #tag टेस्ट",
            "मेरा लिंक http://test.org है",
            "संख्या 42 और 100"
        )
        val expected = listOf(
            "hin_Deva sat_Olck नमस्ते ।",
            "hin_Deva sat_Olck बच्चों , पाँच आम गिनो ।",
            "hin_Deva sat_Olck मैं स्कूल जा रहा हूँ ।",
            "hin_Deva sat_Olck यह किताब बहुत अच्छी है ।",
            "hin_Deva sat_Olck नमस्ते दुनिया !",
            "hin_Deva sat_Olck पानी ( साफ ) है ।",
            "hin_Deva sat_Olck बहुत सारा स्पेस",
            "hin_Deva sat_Olck \" हैलो , \" उसने कहा ।",
            "hin_Deva sat_Olck < ID1 > पर जाओ",
            "hin_Deva sat_Olck मेरा ईमेल < ID1 > है",
            "hin_Deva sat_Olck आज < ID1 > को मिलते हैं",
            "hin_Deva sat_Olck समय < ID1 > बजे है",
            "hin_Deva sat_Olck 12345",
            "hin_Deva sat_Olck आज < ID1 > बढ़ा",
            "hin_Deva sat_Olck नमस्ते दुनिया । आज मौसम अच्छा है ।",
            "hin_Deva sat_Olck क्या हाल है ?",
            "hin_Deva sat_Olck मैंने 100 रुपये दिए ।",
            "hin_Deva sat_Olck हैश < ID1 > टेस्ट",
            "hin_Deva sat_Olck मेरा लिंक < ID1 > है",
            "hin_Deva sat_Olck संख्या 42 और 100"
        )
        val result = IndicProcessorPort.preprocessBatch(fixtures, "hin_Deva", "sat_Olck")
        assertEquals("preprocess size mismatch", expected.size, result.size)
        for (i in expected.indices) {
            assertEquals("preprocess mismatch at $i: input=${fixtures[i]}", expected[i], result[i])
        }
    }

    @Test
    fun `postprocessBatch restores placeholders and detokenizes`() {
        // Need to prime queue with preprocess
        val fixtures = listOf(
            "नमस्ते।",
            "बच्चों, पाँच आम गिनो।",
            "मैं स्कूल जा रहा हूँ।",
            "यह किताब बहुत अच्छी है।",
            "नमस्ते दुनिया!",
            "पानी (साफ) है।",
            "बहुत   सारा   स्पेस",
            "\"हैलो,\" उसने कहा।",
            "https://example.com पर जाओ",
            "मेरा ईमेल test@example.com है",
            "आज 12/05/2023 को मिलते हैं",
            "समय 12:30 बजे है",
            "१२३४५",
            "आज 123% बढ़ा",
            "नमस्ते दुनिया। आज मौसम अच्छा है।",
            "क्या हाल है?",
            "मैंने 100 रुपये दिए।",
            "हैश #tag टेस्ट",
            "मेरा लिंक http://test.org है",
            "संख्या 42 और 100"
        )
        IndicProcessorPort.preprocessBatch(fixtures, "hin_Deva", "sat_Olck")
        val decoded = listOf(
            "ᱡᱚᱦᱟᱨ ᱾",
            "ᱜᱤᱫᱽᱨᱟᱹ ᱠᱚ, ᱢᱚᱬᱮ ᱩᱞ ᱞᱮᱠᱷᱟ ᱢᱮ ᱾",
            "ᱤᱧ ᱟᱥᱲᱟ ᱥᱮᱱᱚᱜ ᱠᱟᱱᱟᱹᱧ ᱾",
            "ᱱᱚᱣᱟ ᱯᱩᱛᱷᱤ ᱟᱹᱰᱤ ᱱᱟᱯᱟᱭ ᱜᱮᱭᱟ ᱾",
            "ᱡᱚᱦᱟᱨ ᱫᱷᱟᱹᱨᱛᱤ !",
            "ᱫᱟᱜ (ᱥᱟᱯᱷᱟ) ᱢᱮᱱᱟᱜᱼᱟ ᱾",
            "ᱟᱹᱰᱤ ᱥᱟᱝᱜᱮ ᱡᱟᱭᱜᱟ",
            "\"ᱦᱟᱞᱚ,\" ᱟᱹᱭ ᱢᱮᱱ ᱠᱮᱫᱟ ᱾",
            "ᱡᱚᱦᱟᱨ <ID1>",
            "ᱤᱧᱟᱜ ᱤᱢᱮᱞ <ID1> ᱠᱟᱱᱟ",
            "ᱛᱮᱦᱮᱧ <ID1> ᱨᱮ ᱧᱟᱯᱟᱢᱟ",
            "ᱚᱠᱛᱚ <ID1> ᱴᱟᱲᱟᱝ ᱠᱟᱱᱟ",
            "᱑᱒᱓᱔᱕",
            "ᱛᱮᱦᱮᱧ <ID1> ᱵᱟᱹᱲᱛᱤ",
            "ᱡᱚᱦᱟᱨ ᱫᱷᱟᱹᱨᱛᱤ ᱾ ᱛᱮᱦᱮᱧ ᱢᱚᱣᱥᱚᱢ ᱱᱟᱯᱟᱭ ᱜᱮᱭᱟ ᱾",
            "ᱪᱮᱫ ᱦᱟᱞ ᱢᱮᱱᱟᱜᱼᱟ ?",
            "ᱤᱧ 100 ᱴᱟᱠᱟ ᱮᱢ ᱠᱮᱫᱟ ᱾",
            "ᱟᱹᱰᱤ <ID1> ᱪᱤᱱᱦᱟᱹ",
            "ᱧᱮᱞ <ID1> ᱱᱤᱛᱚᱜ",
            "ᱮᱞ 42 ᱟᱨ 100"
        )
        val expected = listOf(
            "ᱡᱚᱦᱟᱨ ᱾",
            "ᱜᱤᱫᱽᱨᱟᱹ ᱠᱚ, ᱢᱚᱬᱮ ᱩᱞ ᱞᱮᱠᱷᱟ ᱢᱮ ᱾",
            "ᱤᱧ ᱟᱥᱲᱟ ᱥᱮᱱᱚᱜ ᱠᱟᱱᱟᱹᱧ ᱾",
            "ᱱᱚᱣᱟ ᱯᱩᱛᱷᱤ ᱟᱹᱰᱤ ᱱᱟᱯᱟᱭ ᱜᱮᱭᱟ ᱾",
            "ᱡᱚᱦᱟᱨ ᱫᱷᱟᱹᱨᱛᱤ!",
            "ᱫᱟᱜ (ᱥᱟᱯᱷᱟ) ᱢᱮᱱᱟᱜᱼᱟ ᱾",
            "ᱟᱹᱰᱤ ᱥᱟᱝᱜᱮ ᱡᱟᱭᱜᱟ",
            "\"ᱦᱟᱞᱚ,\" ᱟᱹᱭ ᱢᱮᱱ ᱠᱮᱫᱟ ᱾",
            "ᱡᱚᱦᱟᱨ https://example.com",
            "ᱤᱧᱟᱜ ᱤᱢᱮᱞ test@example.com ᱠᱟᱱᱟ",
            "ᱛᱮᱦᱮᱧ 12/05/2023 ᱨᱮ ᱧᱟᱯᱟᱢᱟ",
            "ᱚᱠᱛᱚ 12:30 ᱴᱟᱲᱟᱝ ᱠᱟᱱᱟ",
            "᱑᱒᱓᱔᱕",
            "ᱛᱮᱦᱮᱧ 123% ᱵᱟᱹᱲᱛᱤ",
            "ᱡᱚᱦᱟᱨ ᱫᱷᱟᱹᱨᱛᱤ ᱾ ᱛᱮᱦᱮᱧ ᱢᱚᱣᱥᱚᱢ ᱱᱟᱯᱟᱭ ᱜᱮᱭᱟ ᱾",
            "ᱪᱮᱫ ᱦᱟᱞ ᱢᱮᱱᱟᱜᱼᱟ?",
            "ᱤᱧ 100 ᱴᱟᱠᱟ ᱮᱢ ᱠᱮᱫᱟ ᱾",
            "ᱟᱹᱰᱤ #tag ᱪᱤᱱᱦᱟᱹ",
            "ᱧᱮᱞ http://test.org ᱱᱤᱛᱚᱜ",
            "ᱮᱞ 42 ᱟᱨ 100"
        )
        val result = IndicProcessorPort.postprocessBatch(decoded, "sat_Olck")
        assertEquals(expected.size, result.size)
        for (i in expected.indices) {
            assertEquals("postprocess mismatch at $i", expected[i], result[i])
        }
        // Queue should be cleared after postprocess
        assertEquals(0, IndicProcessorPort.queueSize())
    }

    @Test
    fun `queue cleared between calls`() {
        IndicProcessorPort.preprocessBatch(listOf("नमस्ते।"), "hin_Deva", "sat_Olck")
        assertEquals(1, IndicProcessorPort.queueSize())
        IndicProcessorPort.postprocessBatch(listOf("ᱡᱚᱦᱟᱨ ᱾"), "sat_Olck")
        assertEquals(0, IndicProcessorPort.queueSize())
        // Second call should also work
        val second = IndicProcessorPort.preprocessBatch(listOf("https://example.com पर जाओ"), "hin_Deva", "sat_Olck")
        assertEquals("hin_Deva sat_Olck < ID1 > पर जाओ", second[0])
        assertEquals(1, IndicProcessorPort.queueSize())
        val post2 = IndicProcessorPort.postprocessBatch(listOf("ᱡᱚᱦᱟᱨ <ID1>"), "sat_Olck")
        assertEquals("ᱡᱚᱦᱟᱨ https://example.com", post2[0])
        assertEquals(0, IndicProcessorPort.queueSize())
    }

    @Test
    fun `NFKC and digit translation`() {
        val out = IndicProcessorPort.preprocessBatch(listOf("१२३४५"), "hin_Deva", "sat_Olck")
        assertEquals("hin_Deva sat_Olck 12345", out[0])
    }
}
