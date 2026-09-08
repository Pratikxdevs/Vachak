package com.vachak.ml

import java.text.Normalizer
import java.util.ArrayDeque

object IndicProcessorPort {
    private val placeholderQueue: ArrayDeque<Map<String, String>> = ArrayDeque()
    private val digitMap: Map<Int, String> by lazy {
        val m = mutableMapOf<Int, String>()
        val groups = listOf(0x09E6 to 0x09EF, 0x0AE6 to 0x0AEF, 0x0CE6 to 0x0CEF, 0x0966 to 0x096F, 0x0660 to 0x0669, 0xABF0 to 0xABF9, 0x0B66 to 0x0B6F, 0x0A66 to 0x0A6F, 0x1C50 to 0x1C59, 0x06F0 to 0x06F9, 0x0C66 to 0x0C6F, 0x0BE6 to 0x0BEF, 0x0D66 to 0x0D6F)
        for ((s, _) in groups) for (i in 0..9) m[s + i] = ('0'.code + i).toChar().toString()
        for (c in '0'..'9') m[c.code] = c.toString()
        m
    }
    private val emailRegex = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val urlRegex = Regex("""\b(?<![\w/.])(?:(?:https?|ftp)://)?(?:(?:[\w\-]+\.)+(?!\.))(?:[\w/\-?#&=%.]+)+(?!\.\w+)\b""")
    private val numeralRegex = Regex("""(~?\d+\.?\d*\s?%?\s?-?\s?~?\d+\.?\d*\s?%|~?\d+%|\d+[-\/.,:']\d+[-\/.,:'+]\d+(?:\.\d+)?|\d+[-\/.:'+]\d+(?:\.\d+)?)""")
    private val otherRegex = Regex("""[A-Za-z0-9]*[#|@]\w+""")
    private val multispaceRegex = Regex("[ ]{2,}")
    private val digitSpacePercent = Regex("""(\d) %""")
    private val doubleQuotPunc = Regex(""""([,\.]+)""")
    private val endBracketSpacePunc = Regex("""\) ([\.!:?;,])""")
    private val digitNbspDigit = Regex("""(\d)\u00A0(\d)""")
    private val punctSet = setOf('!','"','#','$','%','&','\'','(',')','*','+',',','-','.','/',';',':','<','=','>','?','@','[','\\',']','^','_','`','{','|','}','~','\u0964','\u0965','\uAAF1','\uAAF0','\uABEB','\uABEC','\uABED','\uABEE','\uABEF','\u1C7E','\u1C7F')
    private val leftSet = setOf('!', '%', ')', ']', '}', ',', '.', ':', ';', '>', '?', '\u0964', '\u0965')
    private val rightSet = setOf('#', '$', '(', '[', '{', '<', '@')
    private val lrSet = setOf('-', '/', '\\')
    fun preprocessBatch(texts: List<String>, srcLang: String, tgtLang: String): List<String> {
        placeholderQueue.clear()
        return texts.map { preprocessSingle(it, srcLang, tgtLang) }
    }
    private fun preprocessSingle(sent: String, srcLang: String, tgtLang: String): String {
        var t = Normalizer.normalize(sent, Normalizer.Form.NFKC)
        t = puncNorm(t); t = translateDigits(t); t = wrapPlaceholders(t); t = trivialTokenize(t).trim()
        return if (t.isEmpty()) "$srcLang $tgtLang" else "$srcLang $tgtLang $t"
    }
    private fun puncNorm(text: String): String {
        var t = text.replace("\r","")
        t = t.replace(Regex("""\(\s*"""), "("); t = t.replace(Regex("""\s*\)"""), ")")
        t = t.replace(Regex("""\s:\s?"""), ":"); t = t.replace(Regex("""\s;\s?"""), ";")
        t = t.replace(Regex("""[`´‘‚’]"""), "'"); t = t.replace(Regex("""[„“”«»]"""), "\""); t = t.replace(Regex("""[–—]"""), "-")
        t = t.replace("\u00A0%", "%"); t = t.replace("nº\u00A0", "nº "); t = t.replace("\u00A0ºC", " ºC")
        t = Regex("""\u00A0([?!;])""").replace(t){it.groupValues[1]}; t = t.replace(", \u00A0", ", ")
        t = multispaceRegex.replace(t," "); t = endBracketSpacePunc.replace(t,")$1"); t = digitSpacePercent.replace(t,"$1%")
        t = doubleQuotPunc.replace(t,"$1\""); t = digitNbspDigit.replace(t,"$1.$2")
        return t.trim()
    }
    private fun translateDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) { val m = digitMap[ch.code]; if(m!=null) sb.append(m) else sb.append(ch) }
        return sb.toString()
    }
    private fun wrapPlaceholders(text: String): String {
        var t = text; val map = mutableMapOf<String,String>(); var serial=1
        val pats = listOf(emailRegex, urlRegex, numeralRegex, otherRegex)
        for (pat in pats) {
            val matches = pat.findAll(t).map{it.value}.toSet()
            for (mch in matches) {
                if(pat==urlRegex && mch.replace(".","").length<4) continue
                if(pat==numeralRegex && mch.replace(" ","").replace(".","").replace(":","").length<4) continue
                if(!t.contains(mch)) continue
                addVariants(map, serial, mch); t = t.replace(mch, "<ID$serial>"); serial++
            }
        }
        t = Regex("""\s+""").replace(t," ").replace(">/", ">").replace("]/", "]").trim()
        placeholderQueue.addLast(map); return t
    }
    private fun addVariants(map: MutableMap<String,String>, serial:Int, v:String){
        for (k in listOf("<ID$serial>","< ID$serial >","[ID$serial]","[ ID$serial ]","[ID $serial]","<ID$serial]","< ID$serial]","<ID$serial ]","<id$serial>","< id$serial >","[id$serial]","[ id$serial ]","[id $serial]","<id$serial]","< id$serial]","<id$serial ]")) map[k]=v
    }
    private fun trivialTokenize(text: String): String {
        val sb = StringBuilder()
        for(c in text.replace('\t',' ')) if(c in punctSet) sb.append(' ').append(c).append(' ') else sb.append(c)
        return Regex("[ ]+").replace(sb.toString()," ").trim()
    }
    fun postprocessBatch(texts: List<String>, lang: String): List<String> {
        val maps = mutableListOf<Map<String,String>>()
        repeat(texts.size){ if(placeholderQueue.isNotEmpty()) maps.add(placeholderQueue.removeFirst()) else maps.add(emptyMap()) }
        placeholderQueue.clear()
        return texts.mapIndexed{ i, s -> postprocessSingle(s, lang, if(i<maps.size) maps[i] else emptyMap()) }
    }
    private fun postprocessSingle(sent:String, lang:String, ph:Map<String,String>): String {
        var s=sent; for((k,v) in ph) s=s.replace(k,v); return trivialDetokenize(s)
    }
    private fun trivialDetokenize(text:String): String {
        var s=text; for(c in lrSet) s=s.replace(" $c ", c.toString()); for(c in leftSet) s=s.replace(" $c", c.toString()); for(c in rightSet) s=s.replace("$c ", c.toString())
        for(p in listOf('\'','"','`')){ var cnt=0; val out=StringBuilder(); for(ch in s){ if(ch==p){ out.append(if(cnt%2==0)"@RA" else "@LA"); cnt++ } else out.append(ch) }; s=out.toString().replace("@RA ",p.toString()).replace(" @LA",p.toString()).replace("@RA",p.toString()).replace("@LA",p.toString()) }
        return s
    }
    fun clearQueue()=placeholderQueue.clear()
    fun queueSize()=placeholderQueue.size
}
