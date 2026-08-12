package com.dwm.cockpit.ui

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import com.dwm.cockpit.Panel
import com.dwm.cockpit.ui.theme.DwmShapes
import com.dwm.cockpit.ui.theme.DwmSpace

/**
 * A live camera feed in the right-hand column.
 *
 * The picture is the point, so the card is padded only above the label and the feed
 * runs to the card's inner edges — a camera inset by 16dp on all four sides reads as
 * a thumbnail of a camera rather than a camera.
 *
 * Give this an `aspectRatio(16f/9f)` from the caller rather than a height. A camera
 * box that does not match the feed's shape either letterboxes (wasted panel on a
 * screen with none to spare) or centre-crops away the sides, and on a reversing camera
 * the sides are where the bollard is. [com.dwm.cockpit.CameraPanel] can do fill, fit
 * or stretch, but none of them can invent a correct shape from a wrong box.
 *
 * @param drawnView the activity's `Panel -> View?` bridge. Null means the view could
 *   not be built — no hardware, no permission, or a golden being rendered off-device —
 *   and produces [Unavailable] naming what it tried to show, never a blank rectangle.
 */
@Composable
fun CameraBox(
    panel: Panel,
    label: String,
    drawnView: (Panel) -> View?,
    modifier: Modifier = Modifier
) {
    DwmCard(modifier = modifier, padding = DwmSpace.cardPadding) {
        Column(Modifier.fillMaxSize()) {
            CardLabel(label)
            Spacer(Modifier.height(DwmSpace.s))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(DwmShapes.small)
            ) {
                // Rebuild the View subtree when the feed's identity changes, so
                // switching camera id or rotation tears the old CameraPanel down
                // rather than leaving it holding the hardware.
                key(panel.camId, panel.rotation) {
                    val view = drawnView(panel)
                    if (view != null) {
                        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
                    } else {
                        Unavailable(panel.displayLabel())
                    }
                }
            }
        }
    }
}
