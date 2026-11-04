package com.busycall.app.keys

import android.view.KeyEvent

/** A physical key, as BusyCall understands it. Screens react to these, never to keycodes. */
sealed class LogicalKey {
    /** 0-9, `*` or `#`. */
    data class Digit(val char: Char) : LogicalKey()
    object Up : LogicalKey()
    object Down : LogicalKey()
    object Left : LogicalKey()
    object Right : LogicalKey()
    /** OK / D-pad centre. Focused views handle it themselves (click). */
    object Ok : LogicalKey()
    /** BACK, and Clear (Kyocera sends Clear as BACK). */
    object Back : LogicalKey()
    /** DEL, where a phone sends it: delete one character. */
    object Delete : LogicalKey()
    object Call : LogicalKey()
    object End : LogicalKey()
    object SoftLeft : LogicalKey()
    object SoftRight : LogicalKey()
    /** Dedicated Options/MENU key (Sonim). Always means Options. */
    object Menu : LogicalKey()
    /** Kyocera outer buttons, identified by scan code (guide docs/keys.md). */
    object OuterEnd : LogicalKey()
    object OuterSpeaker : LogicalKey()
    /** Bluetooth headset button. */
    object Headset : LogicalKey()
    /** Keys the system or other apps own (volume, power, camera, mic, SOS, PTT…). Never toast these. */
    object System : LogicalKey()
    /** Anything else: shown on screen so new phones can be learned (guide §7). */
    data class Unknown(val keyCode: Int, val scanCode: Int) : LogicalKey()
}

/**
 * The only place that reads raw keycodes and scan codes (AGENTS.md §2, §4).
 * When a phone sends something new, add it here with a comment naming the phone,
 * and add it to the guide's docs/keys.md.
 */
object KeyRouter {

    // Kyocera outer buttons: they arrive with no usable keycode (guide docs/keys.md).
    private const val SCAN_E4810_SOS = 763
    private const val SCAN_E4810_OUTER_END = 764
    private const val SCAN_E4810_OUTER_SPEAKER = 765
    private const val SCAN_E4810_PTT = 766
    private const val SCAN_E4610_OUTER_END = 172
    private const val SCAN_E4610_OUTER_SPEAKER = 213
    private const val SCAN_E4610_PTT = 231

    // Mic/Assistant key: KEYCODE_F4, or raw 287 (E4610 even when an app has focus).
    private const val KEYCODE_MIC_RAW = 287

    fun route(keyCode: Int, scanCode: Int): LogicalKey = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> LogicalKey.Digit('0' + (keyCode - KeyEvent.KEYCODE_0))
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
            LogicalKey.Digit('0' + (keyCode - KeyEvent.KEYCODE_NUMPAD_0))
        // Sonim sends `*` as NUMPAD_MULTIPLY.
        KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> LogicalKey.Digit('*')
        KeyEvent.KEYCODE_POUND -> LogicalKey.Digit('#')

        KeyEvent.KEYCODE_DPAD_UP -> LogicalKey.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> LogicalKey.Down
        KeyEvent.KEYCODE_DPAD_LEFT -> LogicalKey.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> LogicalKey.Right
        // Devices send either CENTER or ENTER for OK.
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> LogicalKey.Ok

        KeyEvent.KEYCODE_BACK -> LogicalKey.Back
        KeyEvent.KEYCODE_DEL -> LogicalKey.Delete
        KeyEvent.KEYCODE_CALL -> LogicalKey.Call
        KeyEvent.KEYCODE_ENDCALL -> LogicalKey.End
        KeyEvent.KEYCODE_SOFT_LEFT -> LogicalKey.SoftLeft
        KeyEvent.KEYCODE_SOFT_RIGHT -> LogicalKey.SoftRight
        KeyEvent.KEYCODE_MENU -> LogicalKey.Menu

        KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> LogicalKey.Headset

        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_FOCUS,
        KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_F4, KEYCODE_MIC_RAW -> LogicalKey.System

        else -> routeByScanCode(keyCode, scanCode)
    }

    private fun routeByScanCode(keyCode: Int, scanCode: Int): LogicalKey = when (scanCode) {
        SCAN_E4810_OUTER_END, SCAN_E4610_OUTER_END -> LogicalKey.OuterEnd
        SCAN_E4810_OUTER_SPEAKER, SCAN_E4610_OUTER_SPEAKER -> LogicalKey.OuterSpeaker
        SCAN_E4810_SOS, SCAN_E4810_PTT, SCAN_E4610_PTT -> LogicalKey.System
        else -> LogicalKey.Unknown(keyCode, scanCode)
    }
}
