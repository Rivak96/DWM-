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
    private val fallbackPkg: String?,
    rotationDeg: Int = 0
) : FrameLayout(context) {

    private val texture = TextureView(context)
    private val status = TextView(context)
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private var rotation = ((rotationDeg % 360) + 360) % 360
    private var previewSize: android.util.Size? = null

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
            override fun onSurfaceTextureAvailable(s: android.graphics.SurfaceTexture, w: Int, h: Int) {
                openCamera(); applyTransform()
            }
            override fun onSurfaceTextureSizeChanged(s: android.graphics.SurfaceTexture, w: Int, h: Int) = applyTransform()
            override fun onSurfaceTextureDestroyed(s: android.graphics.SurfaceTexture): Boolean {
                closeCamera(); return true
            }
            override fun onSurfaceTextureUpdated(s: android.graphics.SurfaceTexture) {}
        }
        setOnClickListener {
            if (device == null && fallbackPkg != null) LaunchEngine.launchFullscreen(context, fallbackPkg)
        }
    }

    /** Rotate the live preview by 0/90/180/270°. Applied via a texture matrix so
     *  the panel rectangle itself stays put. */
    fun setRotationDeg(deg: Int) {
        rotation = ((deg % 360) + 360) % 360
        applyTransform()
    }

    /** Centre the camera frame in the panel at its true aspect ratio (contain:
     *  the whole frame is always visible, no distortion, no shift), then apply
     *  the user's rotation. Recomputed whenever the panel is resized. */
    private fun applyTransform() {
        val vw = texture.width.toFloat()
        val vh = texture.height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val ps = previewSize
        val pw = ps?.width?.toFloat() ?: vw
        val ph = ps?.height?.toFloat() ?: vh

        val m = android.graphics.Matrix()
        val viewRect = android.graphics.RectF(0f, 0f, vw, vh)
        val cx = viewRect.centerX()
        val cy = viewRect.centerY()

        // Undo TextureView's default stretch: map the view back onto the buffer's
        // native rect, centred, so the content shows at its true aspect ratio.
        val bufferRect = android.graphics.RectF(0f, 0f, pw, ph)
        bufferRect.offset(cx - bufferRect.centerX(), cy - bufferRect.centerY())
        m.setRectToRect(viewRect, bufferRect, android.graphics.Matrix.ScaleToFit.FILL)

        // Contain-fit the whole frame (accounting for 90/270 swapping dimensions).
        val rotated = rotation == 90 || rotation == 270
        val fitScale = if (rotated) minOf(vw / ph, vh / pw) else minOf(vw / pw, vh / ph)
        m.postScale(fitScale, fitScale, cx, cy)
        m.postRotate(rotation.toFloat(), cx, cy)
        texture.setTransform(m)
    }

    private fun hasPerm() =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun openCamera() {
        if (!hasPerm()) { status.text = "Grant camera permission"; return }
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = runCatching { mgr.cameraIdList }.getOrDefault(emptyArray())
        if (ids.isEmpty()) {
            status.text = if (fallbackPkg != null)
                "Camera not exposed to apps\nTap to open camera app"
            else
                "No camera input exposed"
            return
        }
        // Prefer the configured id; else auto-pick: a wired analog input usually
        // reports as EXTERNAL, then a BACK cam, else the first device.
        val id = camIdPref?.takeIf { it in ids } ?: run {
            fun facing(cid: String) = runCatching {
                mgr.getCameraCharacteristics(cid)
                    .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
            }.getOrNull()
            ids.firstOrNull { facing(it) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL }
                ?: ids.firstOrNull { facing(it) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK }
                ?: ids.first()
        }
        // Pick the camera's real output size (not the view size) so the frame
        // isn't sampled/stretched wrongly.
        previewSize = runCatching {
            val chars = mgr.getCameraCharacteristics(id)
            val map = chars.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)?.toList()
            sizes?.filter { it.width <= 1920 && it.height <= 1080 }?.maxByOrNull { it.width.toLong() * it.height }
                ?: sizes?.maxByOrNull { it.width.toLong() * it.height }
        }.getOrNull() ?: android.util.Size(1280, 720)

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
        val ps = previewSize ?: android.util.Size(1280, 720)
        st.setDefaultBufferSize(ps.width, ps.height)
        val surface = Surface(st)
        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
        @Suppress("DEPRECATION")
        cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                runCatching { s.setRepeatingRequest(req.build(), null, bgHandler) }
                post { applyTransform() }
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
