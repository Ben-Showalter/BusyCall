package com.busycall.app.numbers.setup

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTest {

    /** A QR code drawn the way a camera's luminance plane would see it (dark = 0). */
    private fun frame(text: String, size: Int = 320, inverted: Boolean = false): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val out = ByteArray(size * size * 3 / 2) // NV21: luminance plane, then chroma
        for (y in 0 until size) for (x in 0 until size) {
            val dark = matrix.get(x, y) != inverted
            out[y * size + x] = if (dark) 0 else 0xFF.toByte()
        }
        return out
    }

    private val code = "busycall:add?v=1&name=Office&server=pbx.example.org&user=104&pass=not-a-real-one"

    @Test fun `decodes a QR code in a camera frame, and its inverse`() {
        assertEquals(code, QrDecoder.decode(frame(code), 320, 320))
        assertEquals(code, QrDecoder.decode(frame(code, inverted = true), 320, 320))
    }

    @Test fun `no code, or a bad frame, gives null`() {
        assertNull(QrDecoder.decode(ByteArray(320 * 320), 320, 320))
        assertNull(QrDecoder.decode(ByteArray(10), 320, 320))
        assertNull(QrDecoder.decode(ByteArray(0), 0, 0))
    }

    @Test fun `scan outcomes`() {
        val vm = ScanViewModel()
        val found = vm.onDecoded(code)
        assertTrue(found is ScanOutcome.Found)
        assertEquals("104", (found as ScanOutcome.Found).draft.user)
        assertEquals("pbx.example.org", found.draft.server)

        assertEquals(ScanOutcome.NotACode, vm.onDecoded("https://example.org/menu"))
        // The same rejected code again, frame after frame: no new message.
        assertEquals(ScanOutcome.Repeat, vm.onDecoded("https://example.org/menu"))
        vm.reset()
        assertEquals(ScanOutcome.NotACode, vm.onDecoded("https://example.org/menu"))

        assertEquals(ScanOutcome.TooNew, vm.onDecoded("busycall:add?v=9&server=pbx&user=1"))
        assertEquals(ScanOutcome.Incomplete(SetupCodeParser.Field.Server), vm.onDecoded("busycall:add?v=1&user=104"))
    }
}

class ScanLayoutTest {

    @Test fun `portrait phone, 640x480 camera turned upright, fits the width`() {
        // 240×180 area (above the button) vs a 480×640 upright picture: height limits it.
        assertEquals(135 to 180, ScanLayout.fit(240, 180, 640, 480, rotated = true))
        // A tall area: width limits it.
        assertEquals(240 to 320, ScanLayout.fit(240, 400, 640, 480, rotated = true))
    }

    @Test fun `landscape picture not rotated`() {
        assertEquals(320 to 240, ScanLayout.fit(320, 300, 640, 480, rotated = false))
        assertEquals(200 to 150, ScanLayout.fit(400, 150, 640, 480, rotated = false))
    }

    @Test fun `unknown sizes use the whole area`() {
        assertEquals(240 to 180, ScanLayout.fit(240, 180, 0, 0, rotated = true))
        assertEquals(0 to 0, ScanLayout.fit(0, 0, 640, 480, rotated = false))
    }
}
