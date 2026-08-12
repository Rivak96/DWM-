package com.dwm.cockpit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dwm.cockpit.ui.theme.DwmSpace
import java.util.Locale

/**
 * Every vehicle reading, on one dense line.
 *
 * This replaces four large cards that between them showed two numbers. Small and
 * dense beats big and empty: a strip of eight readings reads as an instrument cluster
 * even when every one of them is an em dash, where four half-empty cards read as a
 * feature that failed to load. That swap is most of the density fix.
 *
 * Everything goes through [Reading], so a missing value is an em dash in the mono
 * face at full size and tabular width — and the strip will not move by a pixel when
 * the CAN bus finally starts talking.
 *
 * ### What is here, and what is deliberately not
 *
 * Speed, gear, coolant, voltage, fuel, rpm and ambient all come from `CarInfo` and
 * populate the moment the vehicle service sends anything. Boost is OBD PID `010B`
 * (`Obd.kt`) and needs an ELM327 dongle.
 *
 * **Transmission temperature is absent on purpose.** It is not a standard OBD PID and
 * is not on this vehicle's CAN profile — it would need a Ford-proprietary request. A
 * slot that can never populate is worse than no slot, because it teaches the eye to
 * ignore an em dash that would otherwise mean "not yet".
 */
@Composable
fun VehicleBar(
    drive: DriveState,
    vitals: VitalsState,
    ambient: Float?,
    boost: Float?,
    modifier: Modifier = Modifier
) {
    DwmCard(modifier = modifier, padding = DwmSpace.cardPadding) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Reading("Speed", drive.speedKmh?.toString(), "km/h")
            Reading("Gear", gearLabel(drive.gear), null)
            Reading("Coolant", vitals.coolant?.let { fmt0(it) }, "°C")
            Reading("Battery", vitals.voltage?.let { fmt1(it) }, "V")
            Reading("Fuel", vitals.fuel?.let { fmt0(it) }, "%")
            Reading("Engine", drive.rpm?.toString(), "rpm")
            Reading("Boost", boost?.let { fmt1(it) }, "bar")
            Reading("Outside", ambient?.let { fmt0(it) }, "°C")
        }
    }
}

/**
 * P / R / N / D / S, or an em dash.
 *
 * Goes through the existing [gearIndex], which carries this vendor's mapping — and
 * that mapping is inverted from the obvious guess: 3 is Park and 0 is Drive, not the
 * other way round. Reimplementing it here would have displayed "P" while the truck
 * was in Drive.
 *
 * Returns `null` rather than a placeholder so [Reading] applies the same no-signal
 * treatment it gives every number. A gear reading "P" on a silent bus is the same
 * lie as a speed reading 0.
 */
private fun gearLabel(gear: Int?): String? = GEARS.getOrNull(gearIndex(gear))

private fun fmt0(v: Float) = String.format(Locale.US, "%.0f", v)
private fun fmt1(v: Float) = String.format(Locale.US, "%.1f", v)
