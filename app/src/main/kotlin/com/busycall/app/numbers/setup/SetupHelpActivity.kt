package com.busycall.app.numbers.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.busycall.app.AppIdentity
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.tutpro.baresip.R
import java.util.concurrent.Executors

/**
 * Make a setup code (PLAN.md S7c), reached by "No, help me set it up". Step 1 saves the offline
 * setup page to Download; steps 2–4 say to move it to a computer over USB, Bluetooth or SD card,
 * fill it in there and come back to scan. Options: Save again · Save to SD card · Send by Bluetooth.
 */
class SetupHelpActivity : FlipBaseActivity() {

    private lateinit var result: TextView
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_setup_help)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_help_title)
        result = findViewById(R.id.bc_help_result)
        findViewById<TextView>(R.id.bc_help_step2).text = getString(R.string.bc_help_step2, AppIdentity.SETUP_PAGE_FILE)
        val save = findViewById<TextView>(R.id.bc_help_save)
        save.paint.isFakeBoldText = true
        save.setOnClickListener { saveToDownloads() }
        val scan = findViewById<TextView>(R.id.bc_help_scan)
        scan.paint.isFakeBoldText = true
        scan.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
            finish()
        }
        save.post { save.requestFocus() }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    override fun optionsItems(): List<MenuItem> = buildList {
        add(MenuItem(getString(R.string.bc_help_save_again)) { saveToDownloads() })
        // Only when a removable card is in.
        if (SetupPageSaver.sdCardDir(this@SetupHelpActivity) != null) {
            add(MenuItem(getString(R.string.bc_help_save_sd)) { saveToSdCard() })
        }
        add(MenuItem(getString(R.string.bc_help_bluetooth)) { sendByBluetooth() })
    }

    private fun saveToDownloads() {
        if (SetupPageSaver.needsStoragePermission() &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_REQUEST)
            return
        }
        save(downloads = true)
    }

    private fun saveToSdCard() = save(downloads = false)

    private fun save(downloads: Boolean) {
        showResult(getString(R.string.bc_help_saving), alert = false)
        worker.execute {
            val saved = if (downloads) SetupPageSaver.saveToDownloads(this) else SetupPageSaver.saveToSdCard(this)
            runOnUiThread {
                when (saved) {
                    is SetupPageSaver.Saved.Ok -> showResult(getString(
                        if (downloads) R.string.bc_help_saved else R.string.bc_help_saved_sd, saved.where,
                    ), alert = false)
                    SetupPageSaver.Saved.Failed -> showResult(getString(R.string.bc_help_save_failed), alert = true)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != STORAGE_REQUEST) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) save(downloads = true)
        else showResult(getString(R.string.bc_help_no_storage), alert = true)
    }

    private fun sendByBluetooth() {
        val bluetooth = SetupPageSaver.shareIntent(this, bluetoothOnly = true)
        if (bluetooth == null) {
            showResult(getString(R.string.bc_help_save_failed), alert = true)
            return
        }
        try {
            startActivity(bluetooth)
        } catch (e: ActivityNotFoundException) {
            // This phone's Bluetooth app has another name: let the user pick it.
            val any = SetupPageSaver.shareIntent(this, bluetoothOnly = false)
            try {
                startActivity(Intent.createChooser(any, getString(R.string.bc_help_bluetooth)))
            } catch (e2: ActivityNotFoundException) {
                showResult(getString(R.string.bc_help_no_bluetooth), alert = true)
            }
        }
    }

    private fun showResult(text: String, alert: Boolean) {
        result.text = text
        result.setTextColor(getColor(if (alert) R.color.bc_text_alert else R.color.bc_accent_soft))
        result.visibility = View.VISIBLE
    }

    companion object {
        private const val STORAGE_REQUEST = 4204
    }
}
