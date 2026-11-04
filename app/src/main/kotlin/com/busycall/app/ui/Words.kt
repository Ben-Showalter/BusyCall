package com.busycall.app.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.widget.TextView
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.bridge.Transport
import com.tutpro.baresip.R
import java.util.Date

/** Turns states into the words and colours screens show. Strings live in bc_strings.xml. */
object Words {

    fun status(ctx: Context, status: NumberStatus): String = ctx.getString(
        when (status) {
            NumberStatus.Registered -> R.string.bc_status_registered
            NumberStatus.Connecting -> R.string.bc_status_connecting
            NumberStatus.NoConnection -> R.string.bc_status_no_connection
            NumberStatus.NotRegistering -> R.string.bc_status_off
        }
    )

    fun statusTone(status: NumberStatus): RowTone = when (status) {
        NumberStatus.NoConnection -> RowTone.Alert
        else -> RowTone.Muted
    }

    fun transport(ctx: Context, transport: Transport): String = ctx.getString(
        when (transport) {
            Transport.UDP -> R.string.bc_transport_udp
            Transport.TCP -> R.string.bc_transport_tcp
            Transport.TLS -> R.string.bc_transport_tls
        }
    )

    fun time(ctx: Context, label: TimeLabel): String = when (label) {
        TimeLabel.Unknown -> ""
        TimeLabel.JustNow -> ctx.getString(R.string.bc_time_just_now)
        is TimeLabel.MinutesAgo -> ctx.getString(R.string.bc_time_minutes_ago, label.minutes)
        is TimeLabel.Today -> DateFormat.getTimeFormat(ctx).format(Date(label.millis))
        TimeLabel.Yesterday -> ctx.getString(R.string.bc_time_yesterday)
        is TimeLabel.OnDate -> DateFormat.getDateFormat(ctx).format(Date(label.millis))
    }

    /** Puts a coloured status dot at the start of [view]. */
    fun dot(view: TextView, status: NumberStatus?) {
        if (status == null) {
            view.setCompoundDrawablesRelative(null, null, null, null)
            return
        }
        val color = when (status) {
            NumberStatus.Registered -> R.color.bc_status_ok
            NumberStatus.Connecting -> R.color.bc_status_wait
            NumberStatus.NoConnection, NumberStatus.NotRegistering -> R.color.bc_status_off
        }
        val dot: Drawable? = view.context.getDrawable(R.drawable.bc_dot)?.mutate()
        dot?.setTint(view.context.getColor(color))
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
    }
}
