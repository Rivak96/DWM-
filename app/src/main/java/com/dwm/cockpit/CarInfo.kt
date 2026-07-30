package com.dwm.cockpit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import com.tw.carinfoservice.CarServiceAidl
import com.tw.carinfoservice.CarServiceCallBack

/**
 * Real vehicle data, from the deck's own CAN service over its AIDL.
 *
 * This is the end of the road that started with v0.15's provider queries. The
 * chain, for anyone reading this later:
 *
 *  - v0.16 read the vendor manifests and found `com.tw.carinfoservice`.
 *  - Scan 3 measured `com.tw.carinfoservice.permission.USE_AIDL` as protection
 *    level `normal` and `CarService` as exported with no permission at all.
 *  - v0.18's probe bound it and read back `descriptor:
 *    com.tw.carinfoservice.CarServiceAidl`, connected in 63ms.
 *  - The APK export shipped in the same build turned out to make the decompile
 *    unnecessary: **the vendor ships the .aidl source files inside the APK**, at
 *    `com/tw/carinfoservice/CarServiceAidl.aidl` and `CarServiceCallBack.aidl`.
 *    Both are now in `src/main/aidl`, byte-for-byte as extracted, and the build
 *    generates the stubs.
 *
 * That last point is the one that matters for correctness. AIDL numbers its
 * transactions by declaration order, so a hand-written client has to get 64 codes
 * right and would break silently on a ROM that inserted a method. Generated-from-
 * source cannot disagree: 66 callback methods in the .aidl matched 66 in the
 * compiled interface, and every code in the service's own proxy lined up with the
 * declaration order, including the unmistakable ones (the 16-int `onRadar`, the
 * lone-String `onMaintenanceTime`).
 *
 * Two things learned the hard way, both from reading the service's `onTransact`:
 *
 *  - **`getCarReverse()` is a stub on this ROM.** Its case writes a literal -1 and
 *    never touches the CAN layer. Polling it will always say "unknown". Reverse
 *    arrives *pushed*, via `onCarReverse` and `onGear_Information`, which is why
 *    this class registers a callback rather than polling.
 *  - **Never call `extendedInterface(Bundle)`.** Its case pulls `data0`/`data1` out
 *    of the bundle and hands them to the CAN writer — it *sends bytes to the
 *    vehicle bus*. It is the one method here that isn't a read. Same for
 *    `updateApk()`. Neither is called from DWM, and neither should be.
 *
 * Everything else on the interface is a getter. This class reads only, and pushes
 * gear into [Vehicle] so the rest of the app keeps one source of truth.
 */
object CarInfo {

    const val PKG = "com.tw.carinfoservice"
    private const val CLS = "com.tw.carinfoservice.CarService"
    private const val ACTION = "com.tw.carinfoservice.CarService.Bind"

    /** Gear codes, straight from the vendor's comment: `0：D 1:N 2:R 3:P 4:S`. */
    fun gearName(g: Int?): String = when (g) {
        0 -> "D"; 1 -> "N"; 2 -> "R"; 3 -> "P"; 4 -> "S"
        null -> "—"
        else -> "?$g"
    }

    /** A wheel's worth of TPMS. Unit strings come from the service as-is. */
    class Tyre(val label: String) {
        @Volatile var pressure: Float? = null
        @Volatile var pressureUnit: String? = null
        @Volatile var temp: Float? = null
        @Volatile var tempUnit: String? = null
        /** 0 normal, 1 pressure, 2 temperature, 3 sensor — vendor's coding. */
        @Volatile var warnType: Int? = null
        @Volatile var warnValue: Int? = null

        fun line(): String {
            val p = pressure?.let { "%.1f%s".format(it, pressureUnit ?: "") } ?: "—"
            val t = temp?.let { "%.0f%s".format(it, tempUnit ?: "") } ?: "—"
            val w = warnType?.takeIf { it != 0 }?.let {
                "  WARN type=$it value=${warnValue ?: 0}"
            } ?: ""
            return "$label $p  $t$w"
        }
    }

    // ---- live state. null means "the service has never reported this", which is
    // ---- not the same as zero: plenty of these depend on the car, not the deck.

    @Volatile var gear: Int? = null; private set
    @Volatile var reverse: Boolean? = null; private set
    @Volatile var speedKmh: Int? = null; private set
    @Volatile var rpm: Int? = null; private set
    @Volatile var handbrake: Boolean? = null; private set
    @Volatile var headlight: Boolean? = null; private set
    /** 0 none, 1 right, 2 left, 3 hazard. */
    @Volatile var turnSignal: Int? = null; private set
    @Volatile var voltage: Float? = null; private set
    @Volatile var coolant: Float? = null; private set
    @Volatile var ambient: Float? = null; private set
    @Volatile var ambientUnit: String? = null; private set
    @Volatile var fuelLevel: Float? = null; private set
    @Volatile var beltDriver: Boolean? = null; private set
    @Volatile var beltPassenger: Boolean? = null; private set
    @Volatile var doorLF: Boolean? = null; private set
    @Volatile var doorRF: Boolean? = null; private set
    @Volatile var doorLR: Boolean? = null; private set
    @Volatile var doorRR: Boolean? = null; private set
    @Volatile var boot: Boolean? = null; private set
    /** 16 sensors, front-left then clockwise. 1..11 near→far, 0 = nothing. */
    @Volatile var radar: IntArray? = null; private set
    /** Steering trace, 0..480, centre 240. */
    @Volatile var track: Int? = null; private set

    val tyres = arrayOf(Tyre("LF"), Tyre("RF"), Tyre("LR"), Tyre("RR"))

    /** Callbacks received. The single most useful diagnostic: non-zero proves the
     *  service is genuinely pushing, not merely bound. */
    @Volatile var updates = 0; private set
    @Volatile var lastMs = 0L; private set
    @Volatile var status = "not started"; private set
    @Volatile private var registered = false

    private var svc: CarServiceAidl? = null
    private var conn: ServiceConnection? = null

    val bound: Boolean get() = conn != null
    val connected: Boolean get() = svc != null

    // ---- lifecycle ---------------------------------------------------------

    /**
     * Bind and register for pushes. Safe to call repeatedly.
     *
     * Started from the application context: the launcher process outlives every
     * activity, and gear state is only useful if we were already listening when
     * the car moved — the same reasoning [Vehicle] is started under.
     */
    fun start(c: Context) {
        if (conn != null) return
        val app = c.applicationContext
        val sc = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
                val s = runCatching { CarServiceAidl.Stub.asInterface(b) }.getOrNull()
                svc = s
                if (s == null) {
                    status = "bound, but the binder was not a CarServiceAidl"
                    return
                }
                status = "connected"
                // requestData() nudges the service to send the current values, and
                // getData() opens the speed/rpm/track stream (its onTransact just
                // flips the send flag on; closureData() is the same flag off).
                runCatching { s.registerCarServiceCallBack(callback) }
                    .onSuccess { registered = true }
                    .onFailure { status = "connected, register failed: ${it.javaClass.simpleName}" }
                runCatching { s.requestData() }
                runCatching { s.getData() }
                // Pushes alone leave most of the dashboard blank on this deck.
                startPolling()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                svc = null
                registered = false
                status = "service disconnected"
                stopPolling()
            }

            override fun onNullBinding(name: ComponentName?) {
                status = "onBind() returned null"
            }
        }
        val intent = Intent().apply {
            component = ComponentName(PKG, CLS)
            action = ACTION
        }
        val ok = runCatching { app.bindService(intent, sc, Context.BIND_AUTO_CREATE) }
            .onFailure { status = "bindService threw ${it.javaClass.simpleName}" }
            .getOrDefault(false)
        // A false return still leaves the connection registered, so it is tracked
        // either way and must still be unbound.
        conn = sc
        if (!ok && status == "not started") status = "bindService returned false"
    }

    fun stop(c: Context) {
        val app = c.applicationContext
        val s = svc
        stopPolling()
        if (s != null && registered) runCatching { s.unRegisterCarServiceCallBack(callback) }
        conn?.let { runCatching { app.unbindService(it) } }
        conn = null
        svc = null
        registered = false
        status = "stopped"
    }

    private fun touch() {
        updates++
        lastMs = System.currentTimeMillis()
    }

    private fun push(key: String) {
        touch()
        mark(key, "push")
    }

    /** Which signals have ever produced a real value, and how they arrived. The
     *  answer to "why is my dashboard all dashes" is exactly this set. */
    private val seen = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun mark(key: String, how: String) {
        seen[key] = how
    }

    /** Every key the poller asks for, so "asked and got nothing" can be told
     *  apart from "never asked". */
    private val probed = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Signals this car does not provide: polled repeatedly, always -1, never
     * pushed. Held back until a few cycles have run so a cold start doesn't
     * report the whole car as missing.
     *
     * This is the difference between a dashboard that looks broken and one that
     * is telling you something true about your vehicle.
     */
    fun absent(): Set<String> {
        if (pollCount < 8) return emptySet()
        return probed.toSet() - seen.keys
    }

    /** Raw values that are documented ambiguously and are therefore reported
     *  rather than decoded — see [pollOnce]. */
    @Volatile var rawDoor: Int? = null; private set

    // ---- polling ------------------------------------------------------------

    /**
     * Read the getters on a timer, as well as listening for pushes.
     *
     * v0.20.0 shipped push-only and that was wrong. On this deck the CAN service
     * volunteers almost nothing — a photo of the first real drive showed voltage
     * and headlights arriving and every other tile stuck on "—" — but the same
     * values sit behind 64 getters that nobody was calling. Pushes are still the
     * better channel where they exist (instant, no IPC cost), so this merges:
     * whichever source produces a real value wins, and [seen] records which one.
     *
     * On a background thread because a binder call into another process blocks,
     * and 1Hz because that is a dashboard, not a tachometer.
     *
     * Two methods are never called here, for the reasons in the class docs:
     * `extendedInterface` (writes to the vehicle bus) and `updateApk`.
     */
    private const val POLL_MS = 1000L
    private var pollThread: android.os.HandlerThread? = null
    private var pollHandler: android.os.Handler? = null

    private var pollCount = 0

    private val poll = object : Runnable {
        override fun run() {
            svc?.let { s ->
                runCatching { pollOnce(s) }
                // requestData() is the vendor's own "resend everything" — its
                // handler posts 0xFF03 to the CAN box. We called it once at
                // connect, which is no use if the box was still waking up or the
                // ignition came on later. Nudge it every 5s rather than every
                // second, so it stays a refresh and not a flood.
                if (pollCount % 5 == 0) runCatching { s.requestData() }
                pollCount++
            }
            pollHandler?.postDelayed(this, POLL_MS)
        }
    }

    /** The vendor documents -1 as "invalid" on every getter, so it is absence,
     *  not a reading. The cost is that a genuine -1°C ambient reads as unknown —
     *  accepted, because their own apps have the same blind spot. */
    private fun vInt(v: Int): Int? = if (v == -1) null else v
    private fun vFloat(v: Float): Float? = if (v == -1f || v.isNaN()) null else v
    private fun vBool(v: Int): Boolean? = if (v == -1) null else v != 0

    private fun pollOnce(s: CarServiceAidl) {
        // Each getter in its own runCatching: one unimplemented method on this
        // ROM must not stop the other sixty.
        fun <T> get(key: String, read: () -> T?): T? {
            probed += key
            return runCatching { read() }.getOrNull()?.also { mark(key, "poll") }
        }

        get("gear") { vInt(s.gear_Information) }?.let {
            if (gear != it) Vehicle.onCarInfoReverse(it == 2, "AIDL gear=${gearName(it)}")
            gear = it
        }
        get("speed") { s.instantaneous_Speed?.firstOrNull()?.let { v -> vInt(v) } }?.let { speedKmh = it }
        get("rpm") { vInt(s.engine_Speed) }?.let { rpm = it }
        get("handbrake") { vBool(s.handbrake) }?.let { handbrake = it }
        get("headlight") { vBool(s.headlight) }?.let { headlight = it }
        get("turn") { vInt(s.turn_Signal) }?.let { turnSignal = it }
        get("voltage") { vFloat(s.electricVoltage) }?.let { voltage = it }
        get("fuel") { vFloat(s.oil_Volume) }?.let { fuelLevel = it }
        get("belts") {
            beltDriver = vBool(s.main_Driving_Seat_Belt) ?: beltDriver
            beltPassenger = vBool(s.co_Pilot_Seat_Belt) ?: beltPassenger
            true
        }
        get("track") { vInt(s.track) }?.let { track = it }
        // Documented as a flag word, and guessing which bit is which door is
        // exactly the mistake this project keeps refusing to make. Reported raw.
        get("door_raw") { vInt(s.door) }?.let { rawDoor = it }

        // water temp: [0] value, [1] unit
        get("coolant") { s.water_Temp?.firstOrNull()?.let { v -> vFloat(v) } }?.let { coolant = it }
        // ambient: [0] is the validity flag, [1] the value
        get("ambient") {
            val a = s.ambient_Temp ?: return@get null
            if (a.isEmpty() || a[0] == -1f) null else (a.getOrNull(1) ?: a[0])
        }?.let { ambient = it }

        get("tpms") {
            val p = s.tirePressure ?: return@get null
            if (p.size < 4) return@get null
            val unit = when (p.getOrNull(4)) { 0f -> "psi"; 1f -> "kpa"; 2f -> "bar"; else -> null }
            for (i in 0 until 4) {
                vFloat(p[i])?.let { tyres[i].pressure = it; tyres[i].pressureUnit = unit }
            }
            true
        }
        get("radar") { s.radar?.takeIf { it.size >= 16 } }?.let { radar = it }
    }

    /**
     * Call every read-only getter once and print exactly what came back.
     *
     * Built after two releases of guessing. The dashboard shows what DWM decided a
     * value means; this shows the raw return, so "the car doesn't send it" and
     * "DWM read it wrong" stop being indistinguishable.
     *
     * The pattern this is meant to confirm: the service resolves a signal either
     * by name (`y0("Speed")`) or by profile index (`x0(4)`). Only seven are
     * name-based — Radar, SendElectricVoltage, SendHeadlight, SendHandbrake,
     * Speed, Track, Turn — and on this deck those are the only ones ever seen
     * working. The indexed ones look up a per-car profile list; if this vehicle's
     * profile doesn't define an item, the getter returns -1 forever and no amount
     * of DWM code changes that.
     *
     * `extendedInterface` and `updateApk` are not called. Everything else here is
     * a getter and cannot write to the bus.
     */
    fun dumpGetters(): String {
        val s = svc ?: return "Not connected to ${PKG} — nothing to dump."
        val sb = StringBuilder()
        sb.append("Raw return from every read-only getter, called once.\n")
        sb.append("-1 is the vendor's \"invalid\": the value is not in this car's profile.\n")
        sb.append("[name] = resolved by signal name · [idx N] = resolved by profile index\n\n")

        fun row(code: Int, how: String, name: String, read: () -> Any?) {
            val v = runCatching { read() }.fold(
                onSuccess = { r ->
                    when (r) {
                        null -> "null"
                        is IntArray -> r.joinToString(",")
                        is FloatArray -> r.joinToString(",")
                        else -> r.toString()
                    }
                },
                onFailure = { "THREW ${it.javaClass.simpleName} ${it.message}" }
            )
            sb.append(String.format("%-3d %-9s %-34s %s%n", code, how, name, v))
        }

        row(4, "[name]", "getElectricVoltage") { s.electricVoltage }
        row(5, "[name]", "getHeadlight") { s.headlight }
        row(6, "[idx]", "getDrivingTime") { s.drivingTime }
        row(7, "[idx 0]", "getTotal_Mileage") { s.total_Mileage }
        row(8, "[idx 1]", "getRecharge_Mileage") { s.recharge_Mileage }
        row(9, "[idx 2]", "getAverage_Fuel_Consumption") { s.average_Fuel_Consumption }
        row(10, "[idx 3]", "getInstant_Fuel_Consumption") { s.instant_Fuel_Consumption }
        row(11, "[idx 4]", "getGear_Information") { s.gear_Information }
        row(12, "[idx 5]", "getMain_Driving_Seat_Belt") { s.main_Driving_Seat_Belt }
        row(13, "[idx 6]", "getCo_Pilot_Seat_Belt") { s.co_Pilot_Seat_Belt }
        row(14, "[name]", "getHandbrake") { s.handbrake }
        row(15, "[idx 8]", "getLeft_Turn_Signal") { s.left_Turn_Signal }
        row(16, "[idx 9]", "getRight_Turn_Signal") { s.right_Turn_Signal }
        row(17, "[idx 15]", "getDouble_flash") { s.double_flash }
        row(18, "[name]", "getInstantaneous_Speed") { s.instantaneous_Speed }
        row(19, "[idx 11]", "getEngine_Speed") { s.engine_Speed }
        row(20, "[idx 12]", "getWater_Temp") { s.water_Temp }
        row(21, "[idx 13]", "getAmbient_Temp") { s.ambient_Temp }
        row(22, "[idx 14]", "getEngine_Oil_Temp") { s.engine_Oil_Temp }
        row(23, "[idx 18]", "getOil_Volume") { s.oil_Volume }
        row(24, "[idx 16]", "getElectricity") { s.electricity }
        row(25, "[idx 17]", "getCharging") { s.charging }
        row(26, "[idx 20]", "getVoltageWarning") { s.voltageWarning }
        row(27, "[stub]", "getCarReverse") { s.carReverse }
        row(28, "[idx 55]", "getDoor") { s.door }
        row(29, "[idx 59]", "getOriginalCarVoltage") { s.originalCarVoltage }
        row(30, "[idx 60]", "getTransTemp") { s.transTemp }
        row(31, "[idx 61]", "getIntkeTemp") { s.intkeTemp }
        row(32, "[idx 62]", "getIntkePressure") { s.intkePressure }
        row(33, "[idx 63]", "getTurbocharged") { s.turbocharged }
        row(34, "[idx 64]", "getDrivingMode") { s.drivingMode }
        row(35, "[idx 65]", "getOffSign") { s.offSign }
        row(36, "[idx 66]", "getOnSign") { s.onSign }
        row(37, "[idx 67+]", "getTirePressure") { s.tirePressure }
        row(38, "[idx 71]", "getThrottle") { s.throttle }
        row(39, "[idx 72]", "getSteeringWheelAngle") { s.steeringWheelAngle }
        row(40, "[idx 73]", "getEngineLoad") { s.engineLoad }
        row(41, "[idx 74]", "getFRAngle") { s.frAngle }
        row(42, "[idx 75]", "getLRAngle") { s.lrAngle }
        row(43, "[idx 76]", "getMaintenanceMileage") { s.maintenanceMileage }
        row(44, "[idx 77]", "getMaintenanceTime") { s.maintenanceTime }
        row(45, "[idx 78]", "getOilWarning") { s.oilWarning }
        row(48, "[idx 79]", "getDrivenDistance") { s.drivenDistance }
        row(49, "[idx 80]", "getLFTireWarning") { s.lfTireWarning }
        row(50, "[idx 81]", "getRFTireWarning") { s.rfTireWarning }
        row(51, "[idx 82]", "getLRTireWarning") { s.lrTireWarning }
        row(52, "[idx 83]", "getRRTireWarning") { s.rrTireWarning }
        row(53, "[idx 84]", "getLFTireTemp") { s.lfTireTemp }
        row(54, "[idx 85]", "getRFTireTemp") { s.rfTireTemp }
        row(55, "[idx 86]", "getLRTireTemp") { s.lrTireTemp }
        row(56, "[idx 87]", "getRRTireTemp") { s.rrTireTemp }
        row(57, "[idx 88]", "getWashingFluidWarning") { s.washingFluidWarning }
        row(58, "[name]", "getRadar") { s.radar }
        row(59, "[name]", "getTrack") { s.track }
        row(60, "[name]", "getTurn_Signal") { s.turn_Signal }
        row(61, "[idx 89]", "getAverage_Fuel_..._This_Drive") { s.average_Fuel_Consumption_For_This_Drive }

        sb.append("\nIf every [idx] row reads -1 and only [name] rows carry values, this car's\n")
        sb.append("CAN profile simply does not define those items. That is a limit of the\n")
        sb.append("vehicle/head-unit pairing, not of DWM, and the dashboard should be cut\n")
        sb.append("down to what is actually here.\n")
        sb.append("\nNOTE: air-conditioning is NOT part of this interface at all — there is no\n")
        sb.append("AC method among the 64. Climate lives in another vendor app entirely.\n")
        return sb.toString()
    }

    private fun startPolling() {
        if (pollThread != null) return
        val th = android.os.HandlerThread("dwm-caninfo").apply { start() }
        pollThread = th
        pollHandler = android.os.Handler(th.looper).also { it.post(poll) }
    }

    private fun stopPolling() {
        pollHandler?.removeCallbacks(poll)
        pollHandler = null
        pollThread?.quitSafely()
        pollThread = null
    }

    // ---- diagnostics -------------------------------------------------------

    /** One line for the Settings panel. */
    fun summary(): String {
        if (conn == null) return "CAN service: not bound"
        if (svc == null) return "CAN service: $status"
        if (updates == 0) return "CAN service: connected, no data pushed yet"
        val bits = mutableListOf("gear=${gearName(gear)}")
        reverse?.let { bits += "reverse=${if (it) "YES" else "no"}" }
        speedKmh?.let { bits += "${it}km/h" }
        rpm?.let { bits += "${it}rpm" }
        tyres[0].pressure?.let { bits += "tyres ok" }
        return "CAN service: ${bits.joinToString(" ")} ($updates updates)"
    }

    /** Section for the vehicle scan report. */
    fun report(): String {
        val sb = StringBuilder()
        sb.append("bound: ").append(conn != null)
        sb.append("  connected: ").append(svc != null)
        sb.append("  callback registered: ").append(registered).append('\n')
        sb.append("status: ").append(status).append('\n')
        sb.append("callbacks received: ").append(updates)
        if (lastMs != 0L) sb.append("  (last ${System.currentTimeMillis() - lastMs}ms ago)")
        sb.append("\n\n")
        if (updates == 0) {
            sb.append("Nothing was pushed. Either the car was not sending while the scan was\n")
            sb.append("open, or this deck's CAN box only reports on change — try again with the\n")
            sb.append("engine running and the gear moving.\n")
            return sb.toString()
        }
        fun line(k: String, v: Any?) {
            sb.append("  ").append(k.padEnd(22)).append(v ?: "— (never reported)").append('\n')
        }
        line("gear", gear?.let { "${gearName(it)}  (raw $it)" })
        line("reverse", reverse)
        line("speed", speedKmh?.let { "$it km/h" })
        line("engine rpm", rpm)
        line("handbrake", handbrake)
        line("headlight", headlight)
        line("turn signal", turnSignal?.let {
            when (it) { 0 -> "none"; 1 -> "right"; 2 -> "left"; 3 -> "hazard"; else -> "?$it" }
        })
        line("voltage", voltage)
        line("coolant", coolant)
        line("ambient", ambient?.let { "$it ${ambientUnit ?: ""}" })
        line("fuel level", fuelLevel)
        line("belts", "driver=$beltDriver passenger=$beltPassenger")
        line("doors", "LF=$doorLF RF=$doorRF LR=$doorLR RR=$doorRR boot=$boot")
        line("track", track)
        radar?.let { line("radar", it.joinToString(",")) }
        sb.append("\n  TPMS (pressure, temp):\n")
        for (t in tyres) sb.append("    ").append(t.line()).append('\n')
        line("door flags (raw)", rawDoor?.let { "$it  (0b${Integer.toBinaryString(it)})" })

        sb.append("\n  WHERE EACH SIGNAL CAME FROM\n")
        if (seen.isEmpty()) {
            sb.append("    nothing at all — neither a push nor a getter returned a real value\n")
        } else {
            for ((k, how) in seen.toSortedMap()) {
                sb.append("    ").append(k.padEnd(14)).append(how).append('\n')
            }
        }
        sb.append("\n  Signals absent from that list returned -1 (the vendor's \"invalid\") from\n")
        sb.append("  their getter AND were never pushed — meaning this car does not put them\n")
        sb.append("  on the bus, or the CAN box does not decode them. That is the honest\n")
        sb.append("  boundary of what DWM can show, and it is not a DWM failure.\n")
        sb.append("  Send this section: it is what decides which tiles are worth keeping.\n")
        return sb.toString()
    }

    // ---- the callback ------------------------------------------------------

    /**
     * All 66 methods, because the generated Stub is abstract and the service calls
     * whichever ones its CAN box decodes. The empty ones are deliberate: they are
     * signals DWM has no use for yet, and an unimplemented method would be a
     * crash on a binder thread rather than a no-op.
     */
    private val callback = object : CarServiceCallBack.Stub() {

        // -- the ones DWM actually uses

        override fun onGear_Information(gear_: Int) {
            push("gear")
            gear = gear_
            // Gear is the better reverse signal: it says which gear, not merely
            // that the deck ducked its audio. 2 = R.
            Vehicle.onCarInfoReverse(gear_ == 2, "AIDL gear=${gearName(gear_)}")
        }

        override fun onCarReverse(on: Boolean) {
            push("reverse")
            reverse = on
            Vehicle.onCarInfoReverse(on, "AIDL onCarReverse")
        }

        override fun onInstantaneous_Speed(v: Int) { push("speed"); speedKmh = v }
        override fun onEngine_Speed(v: Int) { push("rpm"); rpm = v }
        override fun onHandbrake(on: Boolean) { push("handbrake"); handbrake = on }
        override fun onHeadlight(on: Boolean) { push("headlight"); headlight = on }
        override fun onTurn_Signal(v: Int) { push("turn"); turnSignal = v }
        override fun onElectricVoltage(v: Float, unit: String?) { push("voltage"); voltage = v }
        override fun onWater_Temp(v: Int, unit: String?) { push("coolant"); coolant = v.toFloat() }
        override fun onAmbient_Temp(v: Float, unit: String?) {
            push("ambient"); ambient = v; ambientUnit = unit
        }
        override fun onOil_Volume(v: Float, unit: String?) { push("fuel"); fuelLevel = v }
        override fun onMain_Driving_Seat_Belt(on: Boolean) { push("belts"); beltDriver = on }
        override fun onCo_Pilot_Seat_Belt(on: Boolean) { push("belts"); beltPassenger = on }

        override fun onLeftFrontDoor(open: Boolean) { push("doors"); doorLF = open }
        override fun onRightFrontDoor(open: Boolean) { push("doors"); doorRF = open }
        override fun onLeftRearDoor(open: Boolean) { push("doors"); doorLR = open }
        override fun onRightRearDoor(open: Boolean) { push("doors"); doorRR = open }
        override fun onBackDoor(open: Boolean) { push("doors"); boot = open }

        override fun onLFTirePressure(v: Float, unit: String?) = tyre(0, v, unit)
        override fun onRFTirePressure(v: Float, unit: String?) = tyre(1, v, unit)
        override fun onLRTirePressure(v: Float, unit: String?) = tyre(2, v, unit)
        override fun onRRTirePressure(v: Float, unit: String?) = tyre(3, v, unit)

        override fun onLFTireTemp(v: Int, unit: String?) = tyreTemp(0, v, unit)
        override fun onRFTireTemp(v: Int, unit: String?) = tyreTemp(1, v, unit)
        override fun onLRTireTemp(v: Int, unit: String?) = tyreTemp(2, v, unit)
        override fun onRRTireTemp(v: Int, unit: String?) = tyreTemp(3, v, unit)

        override fun onLFTireWarning(type: Int, value: Int) = tyreWarn(0, type, value)
        override fun onRFTireWarning(type: Int, value: Int) = tyreWarn(1, type, value)
        override fun onLRTireWarning(type: Int, value: Int) = tyreWarn(2, type, value)
        override fun onRRTireWarning(type: Int, value: Int) = tyreWarn(3, type, value)

        override fun onTrack(v: Int) { push("track"); track = v }

        override fun onRadar(
            a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int,
            i: Int, j: Int, k: Int, l: Int, m: Int, n: Int, o: Int, p: Int
        ) {
            push("radar")
            radar = intArrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)
        }

        private fun tyre(i: Int, v: Float, unit: String?) {
            push("tpms"); tyres[i].pressure = v; tyres[i].pressureUnit = unit
        }

        private fun tyreTemp(i: Int, v: Int, unit: String?) {
            push("tpms_temp"); tyres[i].temp = v.toFloat(); tyres[i].tempUnit = unit
        }

        private fun tyreWarn(i: Int, type: Int, value: Int) {
            push("tpms_warn"); tyres[i].warnType = type; tyres[i].warnValue = value
        }

        // -- counted but unused: still evidence the bus is alive

        override fun onLeft_Turn_Signal(on: Boolean) { touch() }
        override fun onRight_Turn_Signal(on: Boolean) { touch() }
        override fun onDouble_flash(on: Boolean) { touch() }
        override fun onDrivingTime(v: Int) { touch() }
        override fun onTotal_Mileage(v: Float, unit: String?) { touch() }
        override fun onRecharge_Mileage(v: Float, unit: String?) { touch() }
        override fun onAverage_Fuel_Consumption(v: Float, unit: String?) { touch() }
        override fun onInstant_Fuel_Consumption(v: Float, unit: String?) { touch() }
        override fun onEngine_Oil_Temp(v: Float, unit: String?) { touch() }
        override fun onElectricity(v: Float) { touch() }
        override fun onCharging(v: Int) { touch() }
        override fun onVoltageWarning(on: Boolean) { touch() }
        override fun onOriginalCarVoltage(v: Float, unit: String?) { touch() }
        override fun onTransTemp(v: Float, unit: String?) { touch() }
        override fun onIntkeTemp(v: Float, unit: String?) { touch() }
        override fun onIntkePressure(v: Float, unit: String?) { touch() }
        override fun onTurbocharged(v: Float, unit: String?) { touch() }
        override fun onDrivingMode(v: Int) { touch() }
        override fun onOffSign(on: Boolean) { touch() }
        override fun onOnSign(on: Boolean) { touch() }
        override fun onThrottle(v: Float, unit: String?) { touch() }
        override fun onSteeringWheelAngle(v: Float, unit: String?) { touch() }
        override fun onEngineLoad(v: Float, unit: String?) { touch() }
        override fun onFRAngle(v: Float, unit: String?) { touch() }
        override fun onLRAngle(v: Float, unit: String?) { touch() }
        override fun onMaintenanceMileage(v: Float, unit: String?) { touch() }
        override fun onMaintenanceTime(v: String?) { touch() }
        override fun onOilWarning(on: Boolean) { touch() }
        override fun onDrivenDistance(v: Int, unit: String?) { touch() }
        override fun onWashingFluidWarning(on: Boolean) { touch() }
        override fun onAverage_Fuel_Consumption_For_This_Drive(v: Float, unit: String?) { touch() }
        override fun onMileage_For_This_Drive(v: Float, unit: String?) { touch() }
        override fun onExpand(b: Bundle?) { touch() }
        override fun onExtendedInterface(b: Bundle?) { touch() }
    }
}
