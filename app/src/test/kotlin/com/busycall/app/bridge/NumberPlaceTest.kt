package com.busycall.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File

/** Where a number is from (owner, 2026-11-20), against the real generated assets. */
class NumberPlaceTest {

    private fun asset(name: String): File? =
        listOf("src/main/assets/$name", "app/src/main/assets/$name").map(::File).firstOrNull { it.exists() }

    private val places: NumberPlaces? by lazy {
        val codes = asset("bc_area_codes.csv") ?: return@lazy null
        val regions = asset("bc_regions.csv") ?: return@lazy null
        NumberPlaces.parse(codes.readText(), regions.readText())
    }

    private fun places(): NumberPlaces {
        assumeTrue("assets not found from the working directory", places != null)
        return places!!
    }

    @Test fun `us and canadian area codes in every form`() {
        val p = places()
        assertEquals(Place("Ohio", listOf("OH")), p.of("sip:2165550142@10.1.10.247"))
        assertEquals(Place("Ohio", listOf("OH")), p.of("tel:+12165550142"))
        assertEquals(Place("Ohio", listOf("OH")), p.of("sip:12165550142@pbx"))
        assertEquals(Place("California", listOf("CA")), p.of("sip:(310) 555-0100@pbx"))
        assertEquals("Ontario", p.of("tel:+14165550100")?.name)
    }

    @Test fun `shared area codes highlight both regions`() {
        val place = places().of("tel:+19025550100")!!
        assertEquals(listOf("NS", "PE"), place.regions)
        assertEquals("Nova Scotia / Prince Edward Island", place.name)
    }

    @Test fun `caribbean numbers name the country with no map`() {
        assertEquals(Place("Jamaica"), places().of("tel:+18765550100"))
    }

    @Test fun `toll-free, extensions, sip names and hidden numbers have no place`() {
        val p = places()
        assertNull(p.of("tel:+18005550100"))
        assertNull(p.of("sip:104@pbx"))
        assertNull(p.of("sip:anna@example.com"))
        assertNull(p.of("sip:anonymous@anonymous.invalid"))
    }

    @Test fun `other countries by calling code`() {
        val p = NumberPlaces.EMPTY
        assertEquals(Place("United Kingdom"), p.of("tel:+442071234567"))
        assertEquals(Place("Mexico"), p.of("sip:011525512345678@pbx"))
        assertEquals(Place("Ireland"), p.of("tel:+35312345678"))
    }

    @Test fun `every region in the area codes has a name and a shape on the map`() {
        val codes = asset("bc_area_codes.csv")
        val names = asset("bc_regions.csv")
        val map = asset("bc_map.bin")
        assumeTrue(codes != null && names != null && map != null)
        val regionNames = names!!.readLines().filter { !it.startsWith("#") && ',' in it }.map { it.substringBefore(',') }.toSet()
        val shapes = mutableSetOf<String>()
        DataInputStream(map!!.inputStream().buffered()).use { input ->
            repeat(input.readShort().toInt()) {
                shapes.add(String(byteArrayOf(input.readByte(), input.readByte())))
                input.readShort(); input.readShort()
                repeat(input.readShort().toInt()) { repeat(input.readShort().toInt()) { input.readShort(); input.readShort() } }
            }
        }
        assertEquals(regionNames, shapes)
        val used = codes!!.readLines().filter { !it.startsWith("#") && ',' in it }
            .map { it.substringAfter(',') }.filter { r -> r.split('/').all { it.length == 2 && it.all(Char::isUpperCase) } }
            .flatMap { it.split('/') }.toSet()
        assertTrue(used.isNotEmpty())
        assertTrue("missing: ${used - shapes}", shapes.containsAll(used))
        assertNotNull(places().of("tel:+19075550100")) // Alaska
    }
}
