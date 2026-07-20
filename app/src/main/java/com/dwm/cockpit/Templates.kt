package com.dwm.cockpit

import android.graphics.RectF

/** Preset cockpit layouts. Each slot is a fraction-rect of the screen. Picking a
 *  template lays out empty "+" slots the user fills with apps/panels. */
object Templates {

    data class Template(val name: String, val slots: List<RectF>)

    private fun r(l: Float, t: Float, rr: Float, b: Float) = RectF(l, t, rr, b)

    val ALL: List<Template> = listOf(
        Template("Single", listOf(r(0f, 0f, 1f, 1f))),
        Template("Split (2)", listOf(r(0f, 0f, .5f, 1f), r(.5f, 0f, 1f, 1f))),
        Template(
            "Big + 2 right",
            listOf(r(0f, 0f, .62f, 1f), r(.62f, 0f, 1f, .5f), r(.62f, .5f, 1f, 1f))
        ),
        Template(
            "2 x 2 grid",
            listOf(r(0f, 0f, .5f, .5f), r(.5f, 0f, 1f, .5f), r(0f, .5f, .5f, 1f), r(.5f, .5f, 1f, 1f))
        ),
        Template(
            "Big + 4 right",
            listOf(
                r(0f, 0f, .6f, 1f),
                r(.6f, 0f, .8f, .5f), r(.8f, 0f, 1f, .5f),
                r(.6f, .5f, .8f, 1f), r(.8f, .5f, 1f, 1f)
            )
        ),
        Template(
            "Main + bottom strip",
            listOf(
                r(0f, 0f, 1f, .7f),
                r(0f, .7f, .34f, 1f), r(.34f, .7f, .67f, 1f), r(.67f, .7f, 1f, 1f)
            )
        ),
        Template(
            "Main + bottom camera",
            listOf(r(0f, 0f, 1f, .72f), r(0f, .72f, .45f, 1f), r(.45f, .72f, 1f, 1f))
        )
    )
}
