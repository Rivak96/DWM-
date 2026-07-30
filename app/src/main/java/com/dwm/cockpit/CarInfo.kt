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
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                svc = null
                registered = false
                status = "service disconnected"
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
        sb.append("\nAnything marked \"never reported\" is a signal this car does not put on\n")
        sb.append("the bus, or that the CAN box does not decode — not a DWM failure.\n")
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
            touch()
            gear = gear_
            // Gear is the better reverse signal: it says which gear, not merely
            // that the deck ducked its audio. 2 = R.
            Vehicle.onCarInfoReverse(gear_ == 2, "AIDL gear=${gearName(gear_)}")
        }

        override fun onCarReverse(on: Boolean) {
            touch()
            reverse = on
            Vehicle.onCarInfoReverse(on, "AIDL onCarReverse")
        }

        override fun onInstantaneous_Speed(v: Int) { touch(); speedKmh = v }
        override fun onEngine_Speed(v: Int) { touch(); rpm = v }
        override fun onHandbrake(on: Boolean) { touch(); handbrake = on }
        override fun onHeadlight(on: Boolean) { touch(); headlight = on }
        override fun onTurn_Signal(v: Int) { touch(); turnSignal = v }
        override fun onElectricVoltage(v: Float, unit: String?) { touch(); voltage = v }
        override fun onWater_Temp(v: Int, unit: String?) { touch(); coolant = v.toFloat() }
        override fun onAmbient_Temp(v: Float, unit: String?) {
            touch(); ambient = v; ambientUnit = unit
        }
        override fun onOil_Volume(v: Float, unit: String?) { touch(); fuelLevel = v }
        override fun onMain_Driving_Seat_Belt(on: Boolean) { touch(); beltDriver = on }
        override fun onCo_Pilot_Seat_Belt(on: Boolean) { touch(); beltPassenger = on }

        override fun onLeftFrontDoor(open: Boolean) { touch(); doorLF = open }
        override fun onRightFrontDoor(open: Boolean) { touch(); doorRF = open }
        override fun onLeftRearDoor(open: Boolean) { touch(); doorLR = open }
        override fun onRightRearDoor(open: Boolean) { touch(); doorRR = open }
        override fun onBackDoor(open: Boolean) { touch(); boot = open }

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

        override fun onTrack(v: Int) { touch(); track = v }

        override fun onRadar(
            a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int,
            i: Int, j: Int, k: Int, l: Int, m: Int, n: Int, o: Int, p: Int
        ) {
            touch()
            radar = intArrayOf(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)
        }

        private fun tyre(i: Int, v: Float, unit: String?) {
            touch(); tyres[i].pressure = v; tyres[i].pressureUnit = unit
        }

        private fun tyreTemp(i: Int, v: Int, unit: String?) {
            touch(); tyres[i].temp = v.toFloat(); tyres[i].tempUnit = unit
        }

        private fun tyreWarn(i: Int, type: Int, value: Int) {
            touch(); tyres[i].warnType = type; tyres[i].warnValue = value
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
