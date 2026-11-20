package com.busycall.app.ui

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** AGENTS §9: text at least 7:1 on the black background and the bars (guide §4). Reads bc_colors.xml. */
class PaletteTest {

    private fun colors(): Map<String, Int>? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (path in listOf("src/main/res/values/bc_colors.xml", "app/src/main/res/values/bc_colors.xml")) {
                val f = File(dir, path)
                if (f.isFile) {
                    return Regex("<color name=\"(\\w+)\">#([0-9A-Fa-f]{8})</color>").findAll(f.readText())
                        .associate { it.groupValues[1] to it.groupValues[2].toLong(16).toInt() }
                }
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun luminance(argb: Int): Double {
        fun ch(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(argb shr 16 and 0xFF) + 0.7152 * ch(argb shr 8 and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @Test fun `text keeps 7 to 1 on black and on the bars`() {
        val c = colors()
        assumeTrue("bc_colors.xml not found from the working directory", c != null)
        val map = c!!
        for (bg in listOf("bc_background", "bc_bar")) {
            for (fg in listOf("bc_text", "bc_text_muted", "bc_accent", "bc_accent_soft", "bc_footer_hint", "bc_text_alert")) {
                val ratio = contrast(map.getValue(fg), map.getValue(bg))
                assertTrue("$fg on $bg is %.1f:1".format(ratio), ratio >= 7.0)
            }
        }
        // White labels on the green and red buttons.
        assertTrue(contrast(map.getValue("bc_text_on_bar"), map.getValue("bc_call")) >= 4.5)
        assertTrue(contrast(map.getValue("bc_text_on_bar"), map.getValue("bc_end")) >= 4.5)
    }
}
