package com.busycall.app.data

import android.content.Context
import android.content.SharedPreferences

/** BusyCall's own small settings. Upstream keeps its settings elsewhere. */
object Prefs {
    private const val FILE = "busycall_prefs"
    private const val KEY_OPTIONS_ON_LEFT = "options_on_left"
    private const val KEY_MENU_KEY_SEEN = "menu_key_seen"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Options label and key on the left softkey (true after the first MENU press, AGENTS §4). */
    fun isOptionsOnLeft(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_OPTIONS_ON_LEFT, false)

    fun setOptionsOnLeft(ctx: Context, left: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_OPTIONS_ON_LEFT, left).apply()
    }

    fun hasSeenMenuKey(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_MENU_KEY_SEEN, false)

    fun setSeenMenuKey(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_MENU_KEY_SEEN, true).apply()
    }
}
