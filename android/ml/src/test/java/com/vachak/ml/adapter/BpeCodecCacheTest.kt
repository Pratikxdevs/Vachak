package com.vachak.ml.adapter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * BPE codec cache: first load parses 47MB JSON, second load must hit the
 * binary cache with IDENTICAL codec content (same sizes + same key sample).
 * Guards the MT warm-up speedup against silent cache corruption.
 */
@RunWith(RobolectricTestRunner::class)
class BpeCodecCacheTest {

    private fun assetFile(name: String): File {
        val candidates = listOf(
            "../app/src/main/assets/vachak_models/mt/$name",
            "app/src/main/assets/vachak_models/mt/$name",
            "android/app/src/main/assets/vachak_models/mt/$name"
        )
        // Work on COPIES in temp: the cache file is written next to inputs and
        // must never pollute the repo's asset tree from a test run.
        val src = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("tokenizer asset $name not found from ${System.getProperty("user.dir")}")
        val tmp = File.createTempFile("bpe-$name-", ".json")
        src.copyTo(tmp, overwrite = true)
        return tmp
    }

    @Test
    fun codecCache_roundTrip_identical() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "bpe-test-${System.currentTimeMillis()}")
        dir.mkdirs()
        try {
            val src = assetFile("tokenizer_src.json").copyTo(File(dir, "tokenizer_src.json"))
            val tgt = assetFile("tokenizer_tgt.json").copyTo(File(dir, "tokenizer_tgt.json"))
            val adapter = OnnxIndicTrans2Adapter(context)
            adapter.loadBpe(src, tgt) // JSON parse + cache write
            val first = adapter.bpeStats()
            assertTrue("vocab parsed, got $first", first.first > 100000)
            assertTrue("merges parsed, got $first", first.second > 200000)
            assertTrue("codec cache written", File(dir, "bpe_codec.bin").exists())
            val adapter2 = OnnxIndicTrans2Adapter(context)
            adapter2.loadBpe(src, tgt) // must be cache hit
            assertEquals("codec identical after cache round-trip", first, adapter2.bpeStats())
        } finally {
            dir.deleteRecursively()
        }
    }
}
