package com.dwm.cockpit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Live camera panel via Camera2. On this deck the wired analog input may (or may
 * not) surface as a Camera2 device — if it does, we render it here. If it can't
 * be opened, tapping the panel launches the fallback app (the deck's camera app).
 */
class CameraPanel(
    context: Context,
    private val camIdPref: String?,
    private val fallbackPkg: String?
) : FrameLayout(context) {

    private val texture = TextureView(context)
    private val status = TextView(context)
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    init {
        setBackgroundColor(Color.BLACK)
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        status.setTextColor(Color.WHITE)
        status.textSize = 12f
        status.gravity = Gravity.CENTER
        addView(
            status,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
            }
        )
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: android.graphics.SurfaceTexture, w: Int, h: Int) = openCamera()
            override fun onSurfaceTextureSizeChanged(s: android.graphics.SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(s: android.graphics.SurfaceTexture): Boolean {
                closeCamera(); return true
            }
            override fun onSurfaceTextureUpdated(s: android.graphics.SurfaceTexture) {}
        }
        setOnClickListener {
            if (device == null && fallbackPkg != null) LaunchEngine.launchFullscreen(context, fallbackPkg)
        }
    }

    private fun hasPerm() =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun openCamera() {
        if (!hasPerm()) { status.text = "Grant camera permission"; return }
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = runCatching { mgr.cameraIdList }.getOrDefault(emptyArray())
        if (ids.isEmpty()) { status.text = "No camera devices exposed"; return }
        val id = camIdPref?.takeIf { it in ids } ?: ids.first()
        status.text = "Opening camera $id…"
        startBg()
        try {
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    device = cam; post { status.text = "" }; startPreview(cam)
                }
                override fun onDisconnected(cam: CameraDevice) { cam.close(); device = null }
                override fun onError(cam: CameraDevice, err: Int) {
                    cam.close(); device = null; post { status.text = "Camera error $err (tap to open app)" }
                }
            }, bgHandler)
        } catch (e: SecurityException) {
            status.text = "No camera permission"
        } catch (e: Exception) {
            status.text = "Open failed: ${e.message}"
        }
    }

    private fun startPreview(cam: CameraDevice) {
        val st = texture.surfaceTexture ?: return
        st.setDefaultBufferSize(texture.width.coerceAtLeast(320), texture.height.coerceAtLeast(240))
        val surface = Surface(st)
        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
        @Suppress("DEPRECATION")
        cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                runCatching { s.setRepeatingRequest(req.build(), null, bgHandler) }
            }
            override fun onConfigureFailed(s: CameraCaptureSession) {
                post { status.text = "Preview failed" }
            }
        }, bgHandler)
    }

    private fun closeCamera() {
        runCatching { session?.close() }; session = null
        runCatching { device?.close() }; device = null
        stopBg()
    }

    private fun startBg() {
        bgThread = HandlerThread("dwm-cam").also { it.start() }
        bgHandler = Handler(bgThread!!.looper)
    }

    private fun stopBg() {
        bgThread?.quitSafely(); bgThread = null; bgHandler = null
    }
}
