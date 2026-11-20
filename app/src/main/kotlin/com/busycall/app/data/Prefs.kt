package com.busycall.app.data

import android.content.Context
import android.content.SharedPreferences

/** BusyCall's own small settings. Upstream keeps its settings elsewhere. */
object Prefs {
    private const val FILE = "busycall_prefs"
    private const val KEY_OPTIONS_ON_LEFT = "options_on_left"
    private const val KEY_MENU_KEY_SEEN = "menu_key_seen"

    private const val KEY_CONTACTS_ASKED = "contacts_asked"
    private const val KEY_END_KEY_ASKED = "end_key_asked"
    private const val KEY_PLUS_RULE = "plus_rule:"
    private const val KEY_PLUS_PREFIX = "plus_prefix:"
    private const val KEY_PARK = "park_number:"
    private const val KEY_BLOCK_MODE = "block_mode:"
    private const val KEY_BLOCK_REASON = "block_reason:"
    const val LIST_ALLOWED = "callers_allowed"
    const val LIST_BLOCKED = "callers_blocked"

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

    /** The END-key service was offered once at startup (keys/EndKeyService). */
    fun wasEndKeyAsked(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_END_KEY_ASKED, false)

    fun setEndKeyAsked(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_END_KEY_ASKED, true).apply()
    }

    /** "Numbers starting with +" of number [aor]: (rule key, prefix). Nulls before init or if never set. */
    fun plusRule(aor: String): Pair<String?, String?> {
        val p = app?.let { prefs(it) } ?: return null to null
        return p.getString(KEY_PLUS_RULE + aor, null) to p.getString(KEY_PLUS_PREFIX + aor, null)
    }

    /** Call blocking mode key of number [aor] (BlockMode.key); null = off. */
    fun blockMode(aor: String): String? = app?.let { prefs(it) }?.getString(KEY_BLOCK_MODE + aor, null)

    fun setBlockMode(aor: String, key: String?) {
        val p = app?.let { prefs(it) } ?: return
        p.edit().apply { if (key == null) remove(KEY_BLOCK_MODE + aor) else putString(KEY_BLOCK_MODE + aor, key) }.apply()
    }

    /** Your allowed or blocked callers ([LIST_ALLOWED] / [LIST_BLOCKED]), as CallFilter keys. */
    fun callers(list: String): Set<String> = app?.let { prefs(it) }?.getStringSet(list, null)?.toSet() ?: emptySet()

    fun setCaller(list: String, key: String, present: Boolean) {
        val p = app?.let { prefs(it) } ?: return
        val now = callers(list).toMutableSet()
        if (present) now.add(key) else now.remove(key)
        p.edit().putStringSet(list, now).apply()
    }

    /** Why calls from caller [key] were last blocked (BlockReason.key). */
    fun blockReason(key: String): String? = app?.let { prefs(it) }?.getString(KEY_BLOCK_REASON + key, null)

    fun setBlockReason(key: String, reason: String) {
        app?.let { prefs(it) }?.edit()?.putString(KEY_BLOCK_REASON + key, reason)?.apply()
    }

    /** The call-park number of number [aor]; null if never set (the default applies). */
    fun parkNumber(aor: String): String? = app?.let { prefs(it) }?.getString(KEY_PARK + aor, null)

    fun setParkNumber(aor: String, number: String?) {
        val p = app?.let { prefs(it) } ?: return
        p.edit().apply { if (number == null) remove(KEY_PARK + aor) else putString(KEY_PARK + aor, number) }.apply()
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
