package com.busycall.app.numbers.setup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.busycall.app.AppIdentity
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Puts the offline setup page (assets/busycall-setup.html, copied from tools/setup-code/) where
 * the user can take it to a computer (PLAN.md S7c, AGENTS §6a). The page holds no secrets.
 */
object SetupPageSaver {

    sealed class Saved {
        /** [where] is the path to show, e.g. "Download › busycall-setup.html". */
        data class Ok(val where: String) : Saved()
        object Failed : Saved()
    }

    private const val MIME = "text/html"

    /** API 28 needs WRITE_EXTERNAL_STORAGE for Download; 29+ uses MediaStore and needs nothing. */
    fun needsStoragePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private fun copyPage(ctx: Context, out: OutputStream) {
        ctx.assets.open(AppIdentity.SETUP_PAGE_FILE).use { input -> out.use { input.copyTo(it) } }
    }

    /** Saves to the phone's Download folder, replacing an earlier copy. */
    fun saveToDownloads(ctx: Context): Saved = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveWithMediaStore(ctx) else saveLegacy(ctx)
    } catch (e: IOException) {
        Saved.Failed
    } catch (e: SecurityException) {
        Saved.Failed
    } catch (e: IllegalStateException) {
        Saved.Failed
    }

    @Suppress("NewApi") // Only called on API 29+.
    private fun saveWithMediaStore(ctx: Context): Saved {
        val resolver = ctx.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val name = AppIdentity.SETUP_PAGE_FILE
        // Our own earlier copy (MediaStore only shows an app the files it made): replace it.
        val existing: Uri? = resolver.query(
            collection, arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(name), null,
        )?.use { c -> if (c.moveToFirst()) Uri.withAppendedPath(collection, c.getLong(0).toString()) else null }
        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }) ?: return Saved.Failed
        val out = resolver.openOutputStream(uri, "wt") ?: return Saved.Failed
        copyPage(ctx, out)
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        // If another app's file already had the name, MediaStore picked another: show the real one.
        val actual = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: name
        return Saved.Ok("${Environment.DIRECTORY_DOWNLOADS} › $actual")
    }

    @Suppress("DEPRECATION") // The public Download folder on API 28.
    private fun saveLegacy(ctx: Context): Saved {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) return Saved.Failed
        val file = File(dir, AppIdentity.SETUP_PAGE_FILE)
        copyPage(ctx, file.outputStream())
        // So it shows up straight away when the phone is plugged into a computer (USB/MTP).
        MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf(MIME), null)
        return Saved.Ok("${Environment.DIRECTORY_DOWNLOADS} › ${file.name}")
    }

    /** This app's folder on a removable SD card, or null if there's no card. */
    fun sdCardDir(ctx: Context): File? = ctx.getExternalFilesDirs(null)
        .drop(1)
        .firstOrNull { it != null && Environment.isExternalStorageRemovable(it) && Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }

    /** Saves to the SD card (in BusyCall's own folder there: no permission needed). */
    fun saveToSdCard(ctx: Context): Saved {
        val dir = sdCardDir(ctx) ?: return Saved.Failed
        return try {
            val file = File(dir, AppIdentity.SETUP_PAGE_FILE)
            copyPage(ctx, file.outputStream())
            // Shown relative to the card: "SD card › Android/data/<app>/files/busycall-setup.html".
            val onCard = file.absolutePath.substringAfter("/Android/", "").let { if (it.isEmpty()) file.name else "Android/$it" }
            Saved.Ok(onCard)
        } catch (e: IOException) {
            Saved.Failed
        }
    }

    /**
     * An ACTION_SEND intent for the page (through the app's FileProvider), aimed at Bluetooth
     * when [bluetoothOnly]. Null if the page couldn't be prepared.
     */
    fun shareIntent(ctx: Context, bluetoothOnly: Boolean): Intent? = try {
        val dir = File(ctx.filesDir, "share").apply { mkdirs() }
        val file = File(dir, AppIdentity.SETUP_PAGE_FILE)
        copyPage(ctx, file.outputStream())
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (bluetoothOnly) setPackage(BLUETOOTH_PACKAGE)
        }
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** AOSP's Bluetooth share target. If a phone names it differently, the share sheet is used. */
    const val BLUETOOTH_PACKAGE = "com.android.bluetooth"
}
