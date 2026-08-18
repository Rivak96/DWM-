package com.dwm.cockpit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Live camera panel via Camera2. On this deck the wired analog input may (or may
 * not) surface as a Camera2 device — if it does, we render it here. If it can't
 * be opened, tapping the panel launches the fallback app (the deck's camera app).
 *
 * The picture is aspect-correct: we pick a real supported preview size and letter-
 * box or centre-crop it into the panel, instead of stretching whatever the panel
 * shape happens to be (which is what made the front camera look squashed).
 *
 * Exposure is tuned per day/night so the feed stays readable without blinding the
 * cabin at night — see [applyTone].
 */
class CameraPanel(
    context: Context,
    private val camIdPref: String?,
    private val fallbackPkg: String?,
    rotationDeg: Int = 0,
    /** Which consumer this is, for [CameraHost]'s priority rule. */
    private val owner: CameraHost.Owner = CameraHost.Owner.OVERLAY
) : FrameLayout(context), CameraHost.Client {

    private val texture = TextureView(context)
    private val status = TextView(context)
    /** Only used below API 31, where RenderEffect isn't available. */
    private val scrim = View(context)

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewReq: CaptureRequest.Builder? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private val ui = Handler(Looper.getMainLooper())

    /**
     * True while [CameraHost] has this panel stood down.
     *
     * Distinct from "not open": a suppressed panel must not re-open on the next surface
     * event, which is the whole reason this is a flag rather than just a closed device.
     */
    private var suppressed = false

    private var rotation = ((rotationDeg % 360) + 360) % 360
    /** Buffer size actually delivered by the camera; 0 until the camera opens. */
    private var bufW = 0
    private var bufH = 0

    private var night = false
    private var lux = Float.NaN
    private var sensorMgr: SensorManager? = null
    private var lightListener: SensorEventListener? = null

    /** Re-evaluates day/night and picks up Settings changes without a restart. */
    private val retune = object : Runnable {
        override fun run() {
            refreshTuning()
            ui.postDelayed(this, 30_000)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        scrim.setBackgroundColor(Color.BLACK)
        scrim.alpha = 0f
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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

    /**
     * Registration happens here and **never in the constructor**.
     *
     * `CameraBox` calls `drawnView(panel)` from inside a composable body, and
     * `CockpitHome` recomposes on every vehicle-state tick, so a fresh `CameraPanel` is
     * built constantly while only the first is ever attached. Registering at construction
     * would hand [CameraHost] a growing pile of panels that never draw and never detach.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLightSensor()
        refreshTuning()
        ui.postDelayed(retune, 30_000)
        CameraHost.register(this, CameraIds.resolve(context, camIdPref), owner)
    }

    /**
     * Release the hardware here rather than trusting `onSurfaceTextureDestroyed` to fire.
     *
     * This used to leave the close to the surface callback, which is *usually* true for an
     * `AndroidView`-hosted panel and was an implicit dependency rather than a release. The
     * v0.48.0 dump showed the cost: `CameraCaptureSessionImpl.finalize` reaching a dead
     * `dwm-cam` looper — "sending message to a Handler on a dead thread", twice preceded by
     * "A resource failed to call release". The finalizer was closing the session because
     * nothing else had. [closeCamera] is idempotent, so a later surface callback is
     * harmless.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ui.removeCallbacks(retune)
        stopLightSensor()
        CameraHost.forget(this)
        closeCamera()
    }

    // ---- CameraHost.Client -------------------------------------------------

    /**
     * Stand down for whoever is showing this camera already.
     *
     * The card keeps its place, its size and its grips, and says where the feed went —
     * a panel that simply went black would read as the very fault this fixes.
     */
    override fun yieldCamera() {
        ui.post {
            suppressed = true
            closeCamera()
            status.text = "Live on the dashboard"
        }
    }

    override fun reclaimCamera() {
        ui.post {
            suppressed = false
            if (device == null && texture.isAvailable) openCamera()
        }
    }

    /** Rotate the live preview by 0/90/180/270°. Applied via a texture matrix so
     *  the panel rectangle itself stays put. */
    fun setRotationDeg(deg: Int) {
        rotation = ((deg % 360) + 360) % 360
        applyTransform()
    }

    // ---- geometry --------------------------------------------------------

    /**
     * Map the camera buffer into the panel without distorting it.
     *
     * TextureView's default behaviour stretches the buffer onto the whole view, so
     * the matrix first undoes that stretch (back to the buffer's natural size,
     * centred), then rotates, then applies the fit:
     *   fill    — uniform scale, crops the overflow (default; no black bars)
     *   fit     — uniform scale, letterboxes
     *   stretch — non-uniform, fills exactly (the old, distorting behaviour)
     */
    private fun applyTransform() {
        val vw = texture.width.toFloat()
        val vh = texture.height.toFloat()
        if (vw <= 0f || vh <= 0f) return

        val bw = (if (bufW > 0) bufW else texture.width).toFloat()
        val bh = (if (bufH > 0) bufH else texture.height).toFloat()
        if (bw <= 0f || bh <= 0f) return

        // Natural source size once rotated into view orientation.
        val swapped = rotation == 90 || rotation == 270
        val sw = if (swapped) bh else bw
        val sh = if (swapped) bw else bh

        val kx: Float
        val ky: Float
        when (Prefs.camFit(context)) {
            FIT -> { val s = min(vw / sw, vh / sh); kx = s; ky = s }
            STRETCH -> { kx = vw / sw; ky = vh / sh }
            else -> { val s = max(vw / sw, vh / sh); kx = s; ky = s }
        }

        val cx = vw / 2f
        val cy = vh / 2f
        val m = Matrix()
        m.setScale(bw / vw, bh / vh, cx, cy)   // undo TextureView's stretch
        m.postRotate(rotation.toFloat(), cx, cy)
        m.postScale(kx, ky, cx, cy)
        texture.setTransform(m)
    }

    // ---- day / night tone -------------------------------------------------

    /** Re-read Settings, decide day vs night, and push the result to the camera
     *  (exposure compensation) and to the view (tone curve). */
    fun refreshTuning() {
        night = when (Prefs.camDayNight(context)) {
            FORCE_DAY -> false
            FORCE_NIGHT -> true
            else -> autoIsNight()
        }
        applyTone()
        applyExposure()
    }

    /** Ambient light if the deck exposes a sensor, otherwise the clock. */
    private fun autoIsNight(): Boolean {
        if (!lux.isNaN()) return lux < NIGHT_LUX
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < 6 || hour >= 19
    }

    /** Brightness/contrast profile. Day pulls the exposure back (the analog feed
     *  blows out its highlights); night dims hard so the panel doesn't wash out
     *  the cabin, with a little extra contrast to keep detail readable. */
    private fun toneFor(): Pair<Float, Float> {
        val trim = 1f + 0.08f * Prefs.camTrim(context)
        return if (night) (0.60f * trim) to 1.15f else (0.90f * trim) to 1.06f
    }

    /**
     * Dimming is done with a plain overlay view, not a colour filter: a View's
     * alpha is guaranteed to composite over a TextureView, whereas RenderEffect on
     * a TextureView is not something we can verify without the deck. RenderEffect
     * then only *adds* contrast back (brightness factor left at 1.0), so if it
     * silently does nothing the picture is still correctly dimmed — never doubly.
     */
    private fun applyTone() {
        val (brightness, contrast) = toneFor()
        scrim.alpha = (1f - brightness).coerceIn(0f, 0.75f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val t = (0.5f - 0.5f * contrast) * 255f
            val cm = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            runCatching {
                texture.setRenderEffect(
                    RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm))
                )
            }
        }
    }

    /** Nudge the camera's own auto-exposure. Analog-to-USB bridges often ignore
     *  this, which is why [applyTone] does the visible work regardless. */
    private fun applyExposure() {
        val req = previewReq ?: return
        val cam = device ?: return
        val chars = runCatching {
            (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
                .getCameraCharacteristics(cam.id)
        }.getOrNull() ?: return

        val range = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: return
        val step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: return
        val stepEv = step.numerator.toFloat() / step.denominator.toFloat()
        if (stepEv <= 0f) return

        // Target EV: pull day exposure down (highlights clip), leave night close to
        // neutral so the sensor still gathers light; the panel dim handles glare.
        val targetEv = (if (night) -0.3f else -0.7f) + 0.3f * Prefs.camTrim(context)
        val steps = (targetEv / stepEv).roundToInt().coerceIn(range.lower, range.upper)

        req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, steps)
        runCatching { session?.setRepeatingRequest(req.build(), null, bgHandler) }
    }

    private fun startLightSensor() {
        val mgr = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = mgr.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values.firstOrNull() ?: return
                val wasNight = !lux.isNaN() && lux < NIGHT_LUX
                lux = v
                if (Prefs.camDayNight(context) == AUTO && (v < NIGHT_LUX) != wasNight) refreshTuning()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        mgr.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorMgr = mgr
        lightListener = l
    }

    private fun stopLightSensor() {
        lightListener?.let { runCatching { sensorMgr?.unregisterListener(it) } }
        lightListener = null
        sensorMgr = null
    }

    // ---- camera -----------------------------------------------------------

    private fun hasPerm() =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun openCamera() {
        // Stood down by CameraHost. Without this the next surface event would re-open
        // the device and take it straight back off whoever we just yielded it to.
        if (suppressed) return
        if (!hasPerm()) { status.text = "Grant camera permission"; return }
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Resolved by CameraIds, not here, so that CameraHost could compare this panel
        // against the others *before* anything opened. See that file for why the two
        // stored preferences can look different and still mean one device.
        val id = CameraIds.resolve(context, camIdPref)
        if (id == null) {
            status.text = if (fallbackPkg != null)
                "Camera not exposed to apps\nTap to open camera app"
            else
                "No camera input exposed"
            return
        }
        status.text = "Opening camera $id…"
        startBg()
        try {
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    device = cam; post { status.text = "" }; startPreview(cam)
                }

                /**
                 * Something else took the device.
                 *
                 * This used to be `cam.close(); device = null` and nothing more: no
                 * status, no log, no retry. A panel that lost its camera went black and
                 * stayed black, which is most of why this bug presented as "neither
                 * one works" rather than "one wins". Tell the host — it may be able to
                 * hand the device straight back — and say so on the card either way.
                 */
                override fun onDisconnected(cam: CameraDevice) {
                    cam.close(); device = null
                    post { status.text = "Camera taken by another app" }
                    CameraHost.lost(this@CameraPanel)
                }

                override fun onError(cam: CameraDevice, err: Int) {
                    cam.close(); device = null
                    post { status.text = "Camera error $err (tap to open app)" }
                    CameraHost.lost(this@CameraPanel)
                }
            }, bgHandler)
        } catch (e: SecurityException) {
            // startBg() has already run at this point, and only closeCamera() stops it,
            // so an open that throws used to leak the dwm-cam HandlerThread every time.
            stopBg()
            status.text = "No camera permission"
        } catch (e: Exception) {
            stopBg()
            status.text = "Open failed: ${e.message}"
        }
    }

    /**
     * Pick a preview size the camera actually supports, closest in aspect to the
     * panel. Requesting an arbitrary size (the panel's own pixels) is what made
     * the feed stretch: the driver silently substitutes its nearest mode and the
     * frame gets squashed into our buffer.
     */
    private fun choosePreviewSize(camId: String): Size? {
        val sizes = CameraIds.sizes(context, camId)
            .filter { it.width * it.height <= MAX_PREVIEW_PX }
            .takeIf { it.isNotEmpty() } ?: return null

        val swapped = rotation == 90 || rotation == 270
        val vw = if (swapped) texture.height else texture.width
        val vh = if (swapped) texture.width else texture.height
        val want = if (vw > 0 && vh > 0) vw.toFloat() / vh else 16f / 9f

        return sizes.minWithOrNull(
            compareBy(
                { abs(it.width.toFloat() / it.height - want) },
                { -(it.width.toLong() * it.height) }
            )
        )
    }

    private fun startPreview(cam: CameraDevice) {
        val st = texture.surfaceTexture ?: return
        val size = choosePreviewSize(cam.id)
        if (size != null) {
            bufW = size.width; bufH = size.height
        } else {
            bufW = texture.width.coerceAtLeast(320); bufH = texture.height.coerceAtLeast(240)
        }
        st.setDefaultBufferSize(bufW, bufH)
        post { applyTransform() }

        val surface = Surface(st)
        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
        previewReq = req
        @Suppress("DEPRECATION")
        cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                runCatching { s.setRepeatingRequest(req.build(), null, bgHandler) }
                post { applyTransform(); refreshTuning() }
            }
            override fun onConfigureFailed(s: CameraCaptureSession) {
                post { status.text = "Preview failed" }
            }
        }, bgHandler)
    }

    private fun closeCamera() {
        runCatching { session?.close() }; session = null
        runCatching { device?.close() }; device = null
        previewReq = null
        stopBg()
    }

    /** Stops any existing thread first: after a disconnect the device is null while the
     *  thread is still alive, and a reclaim would otherwise strand it. */
    private fun startBg() {
        stopBg()
        bgThread = HandlerThread("dwm-cam").also { it.start() }
        bgHandler = Handler(bgThread!!.looper)
    }

    private fun stopBg() {
        bgThread?.quitSafely(); bgThread = null; bgHandler = null
    }

    companion object {
        // Prefs.camFit
        const val FILL = 0
        const val FIT = 1
        const val STRETCH = 2
        // Prefs.camDayNight
        const val AUTO = 0
        const val FORCE_DAY = 1
        const val FORCE_NIGHT = 2

        private const val NIGHT_LUX = 15f
        private const val MAX_PREVIEW_PX = 1920 * 1080
    }
}
