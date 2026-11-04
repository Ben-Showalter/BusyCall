package com.busycall.app.keys

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyRouterTest {

    private fun route(keyCode: Int, scanCode: Int = 0) = KeyRouter.route(keyCode, scanCode)

    @Test fun `digits star and pound`() {
        assertEquals(LogicalKey.Digit('0'), route(KeyEvent.KEYCODE_0))
        assertEquals(LogicalKey.Digit('9'), route(KeyEvent.KEYCODE_9))
        assertEquals(LogicalKey.Digit('*'), route(KeyEvent.KEYCODE_STAR))
        assertEquals(LogicalKey.Digit('#'), route(KeyEvent.KEYCODE_POUND))
    }

    @Test fun `sonim star arrives as numpad multiply`() {
        assertEquals(LogicalKey.Digit('*'), route(KeyEvent.KEYCODE_NUMPAD_MULTIPLY))
    }

    @Test fun `ok is center or enter`() {
        assertEquals(LogicalKey.Ok, route(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(LogicalKey.Ok, route(KeyEvent.KEYCODE_ENTER))
    }

    @Test fun `kyocera clear is back`() {
        assertEquals(LogicalKey.Back, route(KeyEvent.KEYCODE_BACK))
    }

    @Test fun `softkeys menu call end`() {
        assertEquals(LogicalKey.SoftLeft, route(KeyEvent.KEYCODE_SOFT_LEFT))
        assertEquals(LogicalKey.SoftRight, route(KeyEvent.KEYCODE_SOFT_RIGHT))
        assertEquals(LogicalKey.Menu, route(KeyEvent.KEYCODE_MENU))
        assertEquals(LogicalKey.Call, route(KeyEvent.KEYCODE_CALL))
        assertEquals(LogicalKey.End, route(KeyEvent.KEYCODE_ENDCALL))
    }

    @Test fun `kyocera e4810 outer buttons by scan code`() {
        assertEquals(LogicalKey.OuterEnd, route(KeyEvent.KEYCODE_UNKNOWN, 764))
        assertEquals(LogicalKey.OuterSpeaker, route(KeyEvent.KEYCODE_UNKNOWN, 765))
        assertEquals(LogicalKey.System, route(KeyEvent.KEYCODE_UNKNOWN, 763)) // SOS
    }

    @Test fun `kyocera e4610 outer buttons by scan code`() {
        assertEquals(LogicalKey.OuterEnd, route(KeyEvent.KEYCODE_UNKNOWN, 172))
        assertEquals(LogicalKey.OuterSpeaker, route(KeyEvent.KEYCODE_UNKNOWN, 213))
    }

    @Test fun `system keys never show as unknown`() {
        assertEquals(LogicalKey.System, route(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(LogicalKey.System, route(KeyEvent.KEYCODE_F4)) // mic key
        assertEquals(LogicalKey.System, route(287)) // E4610 mic key raw code
    }

    @Test fun `anything else is unknown, with its codes`() {
        assertEquals(LogicalKey.Unknown(KeyEvent.KEYCODE_F12, 88), route(KeyEvent.KEYCODE_F12, 88))
    }
}
