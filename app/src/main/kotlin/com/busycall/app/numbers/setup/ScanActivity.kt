package com.busycall.app.numbers.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.busycall.app.numbers.DraftExtras
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.tutpro.baresip.R

/**
 * Scan QR setup code (PLAN.md §5.1): BusyCall's own camera screen, not a scanner app and not
 * ML Kit (AGENTS §6a). The classic camera API (works on every Android 9 keypad phone) feeds
 * preview frames to [QrDecoder] on a background thread. Frames are never saved.
 *
 * OK or a tap on **Scan again** refocuses and clears the message; BACK cancels. A setup code
 * opens "Setup code found"; anything else gets a plain message and scanning goes on.
 */
@Suppress("DEPRECATION") // android.hardware.Camera: the one camera API every target phone has working.
class ScanActivity : FlipBaseActivity(), SurfaceHolder.Callback, Camera.PreviewCallback {

    private val viewModel = ScanViewModel()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var preview: SurfaceView
    private lateinit var area: View
    private var rotated = false
    private lateinit var status: TextView
    private lateinit var again: TextView

    private var camera: Camera? = null
    private var previewWidth = 0
    private var previewHeight = 0
    private var surfaceReady = false
    private var resumed = false
    private var decodeThread: HandlerThread? = null
    private var decodeHandler: Handler? = null

    @Volatile private var busy = false
    @Volatile private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_scan)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_scan_title)
        setFooterHint(getString(R.string.bc_scan_footer))
        preview = findViewById(R.id.bc_scan_preview)
        area = findViewById(R.id.bc_scan_area)
        // The flip or a rotation changes the area: keep the picture's shape.
        area.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) area.post { sizePreview() }
        }
        status = findViewById(R.id.bc_scan_status)
        again = findViewById(R.id.bc_scan_again)
        again.paint.isFakeBoldText = true
        again.setOnClickListener { scanAgain() }
        preview.setOnClickListener { scanAgain() }
        preview.holder.addCallback(this)
        again.post { again.requestFocus() }
    }

    override fun optionsItems(): List<MenuItem> = listOf(
        MenuItem(getString(R.string.bc_scan_again)) { scanAgain() },
        MenuItem(getString(R.string.bc_scan_help)) {
            startActivity(Intent(this, SetupHelpActivity::class.java))
            finish()
        },
    )

    override fun onResume() {
        super.onResume()
        resumed = true
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        } else {
            startCamera()
        }
    }

    override fun onPause() {
        resumed = false
        stopCamera()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA_REQUEST) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera()
        else showProblem(getString(R.string.bc_scan_no_permission))
    }

    // ---- Surface ----

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startCamera()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopCamera()
    }

    // ---- Camera ----

    private fun startCamera() {
        if (camera != null || !surfaceReady || !resumed || finished) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val cam = try {
            openBackCamera()
        } catch (e: RuntimeException) {
            null
        }
        if (cam == null) {
            showProblem(getString(R.string.bc_scan_no_camera))
            return
        }
        try {
            val params = cam.parameters
            val size = params.supportedPreviewSizes
                .minByOrNull { Math.abs(it.width * it.height - TARGET_PIXELS) }
            if (size != null) params.setPreviewSize(size.width, size.height)
            val modes = params.supportedFocusModes.orEmpty()
            when {
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in modes ->
                    params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                Camera.Parameters.FOCUS_MODE_AUTO in modes -> params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            }
            cam.parameters = params
            val used = cam.parameters.previewSize
            previewWidth = used.width
            previewHeight = used.height
            val orientation = displayOrientation(cameraId)
            rotated = orientation == 90 || orientation == 270
            cam.setDisplayOrientation(orientation)
            cam.setPreviewDisplay(preview.holder)
            val thread = HandlerThread("BusyCallQr").also { it.start() }
            decodeThread = thread
            decodeHandler = Handler(thread.looper)
            cam.setPreviewCallback(this)
            cam.startPreview()
            camera = cam
            sizePreview()
            if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO) &&
                params.focusMode == Camera.Parameters.FOCUS_MODE_AUTO
            ) autoFocus()
            showStatus(getString(R.string.bc_scan_point))
        } catch (e: Exception) {
            cam.release()
            stopDecodeThread()
            showProblem(getString(R.string.bc_scan_no_camera))
        }
    }

    private var cameraId = 0

    /** Fits the SurfaceView to the camera picture's ratio inside the preview area (no squash). */
    private fun sizePreview() {
        if (camera == null) return
        val (w, h) = ScanLayout.fit(area.width, area.height, previewWidth, previewHeight, rotated)
        val params = preview.layoutParams as FrameLayout.LayoutParams
        if (params.width != w || params.height != h) {
            params.width = w
            params.height = h
            preview.layoutParams = params
        }
    }

    private fun openBackCamera(): Camera? {
        val info = Camera.CameraInfo()
        val count = Camera.getNumberOfCameras()
        if (count == 0) return null
        val back = (0 until count).firstOrNull {
            Camera.getCameraInfo(it, info)
            info.facing == Camera.CameraInfo.CAMERA_FACING_BACK
        } ?: 0
        cameraId = back
        return Camera.open(back)
    }

    /** The standard rotation so the preview is upright on screen. */
    private fun displayOrientation(id: Int): Int {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(id, info)
        @Suppress("DEPRECATION")
        val degrees = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - (info.orientation + degrees) % 360) % 360
        } else {
            (info.orientation - degrees + 360) % 360
        }
    }

    private fun autoFocus() {
        try {
            camera?.autoFocus { _, _ -> }
        } catch (e: RuntimeException) {
            // Fixed-focus cameras and some OEM builds refuse; scanning still works.
        }
    }

    private fun stopCamera() {
        camera?.let {
            try {
                it.setPreviewCallback(null)
                it.stopPreview()
            } catch (e: RuntimeException) {
                // Already stopped.
            }
            it.release()
        }
        camera = null
        stopDecodeThread()
    }

    private fun stopDecodeThread() {
        decodeThread?.quitSafely()
        decodeThread = null
        decodeHandler = null
        busy = false
    }

    /** One frame at a time; frames arriving while one is decoding are dropped, never stored. */
    override fun onPreviewFrame(data: ByteArray?, cam: Camera?) {
        if (data == null || busy || finished) return
        val handler = decodeHandler ?: return
        val w = previewWidth
        val h = previewHeight
        busy = true
        handler.post {
            val text = QrDecoder.decode(data, w, h)
            if (text == null) {
                busy = false
            } else {
                main.post {
                    handleDecoded(text)
                    busy = false
                }
            }
        }
    }

    private fun handleDecoded(text: String) {
        if (finished) return
        when (val outcome = viewModel.onDecoded(text)) {
            is ScanOutcome.Found -> {
                finished = true
                stopCamera()
                startActivity(DraftExtras.put(Intent(this, SetupFoundActivity::class.java), outcome.draft))
                finish()
            }
            ScanOutcome.NotACode -> showProblem(getString(R.string.bc_scan_not_a_code))
            ScanOutcome.TooNew -> showProblem(getString(R.string.bc_paste_too_new))
            is ScanOutcome.Incomplete -> showProblem(getString(
                if (outcome.missing == SetupCodeParser.Field.Server) R.string.bc_paste_no_server else R.string.bc_paste_no_user,
            ))
            ScanOutcome.Repeat -> {}
        }
    }

    private fun scanAgain() {
        viewModel.reset()
        if (camera == null) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
            } else {
                startCamera()
            }
            return
        }
        showStatus(getString(R.string.bc_scan_point))
        autoFocus()
    }

    private fun showStatus(text: String) {
        status.text = text
        status.setTextColor(getColor(R.color.bc_text))
        again.setText(R.string.bc_scan_again)
    }

    private fun showProblem(text: String) {
        status.text = text
        status.setTextColor(getColor(R.color.bc_text_alert))
        again.setText(if (camera == null) R.string.bc_retry else R.string.bc_scan_again)
    }

    companion object {
        private const val CAMERA_REQUEST = 4203
        /** About 640×480: enough for a QR code on a screen, light enough for a Snapdragon 215. */
        private const val TARGET_PIXELS = 640 * 480
    }
}
