package com.dwm.cockpit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition

/** Toolchain probe: forces the Compose compiler, Material 3, Material Icons
 *  (extended) and Lottie to resolve/compile. Removed once the real UI lands. */
@Composable
internal fun ComposeProbe() {
    val comp by rememberLottieComposition(LottieCompositionSpec.Asset("dwm_boot.json"))
    Surface {
        Column {
            Text("DWM ${if (comp != null) "ready" else "..."}")
            Icon(Icons.Rounded.Speed, contentDescription = null)
            Icon(Icons.Filled.Bluetooth, contentDescription = null)
        }
    }
}
