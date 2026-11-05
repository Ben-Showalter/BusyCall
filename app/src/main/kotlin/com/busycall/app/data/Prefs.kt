package com.busycall.app.data

import android.content.Context
import android.content.SharedPreferences

/** BusyCall's own small settings. Upstream keeps its settings elsewhere. */
object Prefs {
    private const val FILE = "busycall_prefs"
    private const val KEY_OPTIONS_ON_LEFT = "options_on_left"
    private const val KEY_MENU_KEY_SEEN = "menu_key_seen"

    private const val KEY_CONTACTS_ASKED = "contacts_asked"
    private const val KEY_PLUS_RULE = "plus_rule:"
    private const val KEY_PLUS_PREFIX = "plus_prefix:"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The app's context, for code with no Activity at hand (the bridge). Set by FlipBaseActivity. */
    @Volatile
    private var app: Context? = null

    fun init(ctx: Context) {
        if (app == null) app = ctx.applicationContext
    }

    /** True once the contacts prompt has been shown at startup (asked once, AGENTS §7). */
    fun wereContactsAsked(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CONTACTS_ASKED, false)

    fun setContactsAsked(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_CONTACTS_ASKED, true).apply()
    }

    /** "Numbers starting with +" of number [aor]: (rule key, prefix). Nulls before init or if never set. */
    fun plusRule(aor: String): Pair<String?, String?> {
        val p = app?.let { prefs(it) } ?: return null to null
        return p.getString(KEY_PLUS_RULE + aor, null) to p.getString(KEY_PLUS_PREFIX + aor, null)
    }

    fun setPlusRule(aor: String, key: String?, prefix: String?) {
        val p = app?.let { prefs(it) } ?: return
        p.edit().apply {
            if (key == null) remove(KEY_PLUS_RULE + aor) else putString(KEY_PLUS_RULE + aor, key)
            if (prefix == null) remove(KEY_PLUS_PREFIX + aor) else putString(KEY_PLUS_PREFIX + aor, prefix)
        }.apply()
    }

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
