package com.dwm.cockpit

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.InputStream
import java.util.UUID

/** Metric metadata shared by the OBD engine and panel labels. */
object Obd {
    // key -> PID, display name
    val METRICS = listOf(
        Triple("rpm", "010C", "RPM"),
        Triple("speed", "010D", "Speed"),
        Triple("coolant", "0105", "Coolant"),
        Triple("throttle", "0111", "Throttle"),
        Triple("map", "010B", "Boost/MAP"),
        Triple("intake", "010F", "Intake temp")
    )

    fun metricName(key: String?): String =
        METRICS.firstOrNull { it.first == key }?.third ?: (key ?: "")
}

/**
 * Minimal ELM327 (OBD-II) client over Bluetooth classic RFCOMM. Connects to a
 * paired dongle, initialises it, then polls the requested PIDs in a loop and
 * pushes parsed values back via [cb] as (metricKey, numericValue, displayText).
 * Fails softly when no dongle is connected — gauges just keep showing "--".
 */
class ObdManager(
    private val mac: String,
    private val metrics: List<String>,
    private val cb: (String, Float?, String) -> Unit
) {
    private val spp: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start(context: Context) {
        if (running) return
        running = true
        thread = Thread { loop(context) }.also { it.start() }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
    }

    @SuppressLint("MissingPermission")
    private fun loop(context: Context) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null) { cb("_status", null, "no bluetooth"); return }
        val dev = runCatching { adapter.getRemoteDevice(mac) }.getOrNull()
        if (dev == null) { cb("_status", null, "bad device"); return }

        // Keep retrying while active: the dongle may be out of range at startup
        // (engine off, car door open, etc.) and become reachable later.
        while (running) {
            try {
                cb("_status", null, "connecting…")
                adapter.cancelDiscovery()
                val s = dev.createRfcommSocketToServiceRecord(spp)
                socket = s
                s.connect()
                val out = s.outputStream
                val inp = s.inputStream

                fun cmd(c: String): String {
                    out.write((c + "\r").toByteArray()); out.flush()
                    return readUntilPrompt(inp)
                }
                cmd("ATZ"); cmd("ATE0"); cmd("ATL0"); cmd("ATSP0")
                cb("_status", null, "connected")

                val active = Obd.METRICS.filter { it.first in metrics }
                while (running) {
                    for ((key, pid, _) in active) {
                        if (!running) break
                        val resp = cmd(pid)
                        parse(pid, resp)?.let { (num, text) -> cb(key, num, text) }
                    }
                    Thread.sleep(300)
                }
            } catch (e: Exception) {
                cb("_status", null, "offline (${e.message})")
            } finally {
                runCatching { socket?.close() }
                socket = null
            }
            if (running) Thread.sleep(5000)
        }
    }

    private fun readUntilPrompt(inp: InputStream): String {
        val sb = StringBuilder()
        val buf = ByteArray(64)
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 2000) {
            if (inp.available() > 0) {
                val n = inp.read(buf)
                if (n > 0) {
                    sb.append(String(buf, 0, n))
                    if (sb.contains('>')) break
                }
            } else {
                Thread.sleep(10)
            }
        }
        return sb.toString()
    }

    /** Returns (numeric value, display text) for a PID response, or null. */
    private fun parse(pid: String, resp: String): Pair<Float, String>? {
        val clean = resp.replace("\r", " ").replace(">", " ").trim().uppercase()
        val bytes = clean.split(Regex("\\s+")).filter { it.matches(Regex("[0-9A-F]{2}")) }
        val i = bytes.indexOf("41")
        if (i < 0 || i + 2 >= bytes.size) return null
        val a = bytes[i + 2].toInt(16)
        val b = bytes.getOrNull(i + 3)?.toInt(16)
        return when (pid) {
            "010C" -> b?.let { val v = ((a * 256) + it) / 4f; v to "%.0f rpm".format(v) }
            "010D" -> a.toFloat().let { it to "%.0f km/h".format(it) }
            "0105" -> (a - 40f).let { it to "%.0f °C".format(it) }
            "0111" -> (a * 100 / 255f).let { it to "%.0f %%".format(it) }
            "010B" -> a.toFloat().let { it to "%.0f kPa".format(it) }
            "010F" -> (a - 40f).let { it to "%.0f °C".format(it) }
            else -> a.toFloat().let { it to "%.0f".format(it) }
        }
    }
}
