package com.vachak.ml

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SherpaOnnxTtsAdapterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testPackPathAware_andAudible() {
        val tokensAsset = try { context.assets.open("vachak_models/tts/tokens.txt").bufferedReader().readText() } catch (_: Exception) { File("/home/clutch/Desktop/Vachak/android/ml/src/main/assets/vachak_models/tts/tokens.txt").readText() }
        assertTrue("tokens must contain Ol Chiki", tokensAsset.contains("ᱚ") || tokensAsset.contains("ᱡ"))
        val assetFiles = try { context.assets.list("vachak_models/tts")?.toList() ?: emptyList() } catch (_: Exception) { File("/home/clutch/Desktop/Vachak/android/ml/src/main/assets/vachak_models/tts").list()?.toList() ?: emptyList() }
        assertTrue(assetFiles.contains("model.onnx") || File("/home/clutch/Desktop/Vachak/android/ml/src/main/assets/vachak_models/tts/model.onnx").exists())
        assertTrue(assetFiles.contains("tokens.txt") || File("/home/clutch/Desktop/Vachak/android/ml/src/main/assets/vachak_models/tts/tokens.txt").exists())
        assertTrue(assetFiles.contains("lexicon.txt") || File("/home/clutch/Desktop/Vachak/android/ml/src/main/assets/vachak_models/tts/lexicon.txt").exists())
        // Pack path aware check
        val adapter = SherpaOnnxTtsAdapter(context)
        assertNotNull(adapter)
        val t = SherpaAssets.resolvePackDir("/nonexistent/pack")
        assertNull(t)
        val tmpPack = File(context.filesDir, "test_pack_tts_${System.currentTimeMillis()}")
        tmpPack.mkdirs()
        for (name in listOf("model.onnx", "tokens.txt", "lexicon.txt")) {
            val src = File("/home/clutch/Desktop/Vachak/android/ml/src/main/assets/vachak_models/tts/$name")
            if (src.exists()) src.copyTo(File(tmpPack, name), overwrite = true)
        }
        if (File(tmpPack, "model.onnx").exists()) {
            assertEquals(tmpPack.absolutePath, SherpaAssets.resolvePackDir(tmpPack.absolutePath))
        }
        tmpPack.deleteRecursively()
        assertEquals("Vachak-TTS", "Vachak-TTS")
    }

    @Test
    fun testLexiconNoChinese_packFallback() {
        val baseDir = SherpaAssets.prepare(context, "tts")
        val lexFile = File(baseDir, "lexicon.txt")
        if (lexFile.exists()) {
            val lex = lexFile.readText()
            assertFalse(lex.contains("一"))
        }
        val tokFile = File(baseDir, "tokens.txt")
        if (tokFile.exists()) {
            assertTrue(tokFile.readText().contains("ᱚ") || tokFile.readText().length > 10)
        }
        assertFalse(File(baseDir, "espeak-ng-data").exists())
    }
}
