package com.busycall.app.ui

import android.app.Activity
import com.busycall.app.bridge.BlockMode
import com.tutpro.baresip.R

/** The "Block calls" choice (owner, 2026-11-20): Off · Known spam · Not in contacts. */
object BlockMenu {
    fun show(activity: Activity, onPick: (BlockMode) -> Unit) {
        Menus.show(activity, activity.getString(R.string.bc_field_block), listOf(
            MenuItem(activity.getString(R.string.bc_block_off), activity.getString(R.string.bc_block_off_sub)) { onPick(BlockMode.Off) },
            MenuItem(activity.getString(R.string.bc_block_spam), activity.getString(R.string.bc_block_spam_sub)) { onPick(BlockMode.KnownSpam) },
            MenuItem(activity.getString(R.string.bc_block_contacts), activity.getString(R.string.bc_block_contacts_sub)) { onPick(BlockMode.NotInContacts) },
        ), message = activity.getString(R.string.bc_block_voicemail_note))
    }
}
