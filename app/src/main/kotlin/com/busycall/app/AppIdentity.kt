package com.busycall.app

/**
 * Everything that would change if the app were renamed (AGENTS.md §0a).
 * The visible name is `@string/app_name`; never hard-code it elsewhere.
 * The applicationId (com.busycall.app) never changes.
 */
object AppIdentity {
    /** Setup links look like `busycall:add?v=1&...`. */
    const val LINK_SCHEME = "busycall"

    /** Older schemes the setup-code parser must keep accepting forever. */
    val OLD_LINK_SCHEMES: List<String> = emptyList()

    /** File name of the offline setup-code page (tools/setup-code/). */
    const val SETUP_PAGE_FILE = "busycall-setup.html"

    /** GitHub repo checked for app updates (owner/name). */
    const val UPDATE_REPO = "Ben-Showalter/BusyCall"
}
