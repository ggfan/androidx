/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.painting.borders

import androidx.ui.lerpDouble
import androidx.ui.painting.Color
import androidx.ui.painting.Paint
import androidx.ui.painting.PaintingStyle
import androidx.ui.runtimeType
import androidx.ui.toStringAsFixed

/** The style of line to draw for a [BorderSide] in a [Border]. */
enum class BorderStyle {
    /** Skip the border. */
    NONE,

    /** Draw the border as a SOLID line. */
    SOLID

    // if you add more, think about how they will lerp
}

/**
 * A side of a border of a box.
 *
 * A [Border] consists of four [BorderSide] objects: [Border.top],
 * [Border.left], [Border.right], and [Border.bottom].
 *
 * ## Sample code
 *
 * This sample shows how [BorderSide] objects can be used in a [Container], via
 * a [BoxDecoration] and a [Border], to decorate some [Text]. In this example,
 * the text has a thick bar above it that is light blue, and a thick bar below
 * it that is a darker shade of blue.
 *
 * ```dart
 * new Container(
 *   padding: new EdgeInsets.all(8.0),
 *   decoration: new BoxDecoration(
 *     border: new Border(
 *       top: new BorderSide(width: 16.0, color: Colors.lightBlue.shade50),
 *       bottom: new BorderSide(width: 16.0, color: Colors.lightBlue.shade900),
 *     ),
 *   ),
 *   child: new Text('Flutter in the sky', textAlign: TextAlign.center),
 * )
 * ```
 *
 * See also:
 *
 *  * [Border], which uses [BorderSide] objects to represent its sides.
 *  * [BoxDecoration], which optionally takes a [Border] object.
 *  * [TableBorder], which is similar to [Border] but has two more sides
 *    ([TableBorder.horizontalInside] and [TableBorder.verticalInside]), both
 *    of which are also [BorderSide] objects.
 *
 * By default, the border is 1.0 logical pixels wide and SOLID black.
 */
data class BorderSide(
    /** The color of this side of the border. */
    val color: Color = Color(0xFF000000.toInt()),
    /**
     * The width of this side of the border, in logical pixels. A
     * zero-width border is a hairline border. To omit the border
     * entirely, set the [style] to [BorderStyle.NONE].
     */
    val width: Double = 1.0,
    /**
     * The style of this side of the border.
     *
     * To omit a side, set [style] to [BorderStyle.NONE]. This skips
     * painting the border, but the border still has a [width].
     */
    val style: BorderStyle = BorderStyle.SOLID

) {

    init {
        assert(width >= 0.0)
    }

    /**
     * Creates a copy of this border side description but with the width scaled
     * by the factor `t`.
     *
     * The `t` argument represents the multiplicand, or the position on the
     * timeline for an interpolation from nothing to `this`, with 0.0 meaning
     * that the object returned should be the nil variant of this object, 1.0
     * meaning that no change should be applied, returning `this` (or something
     * equivalent to `this`), and other values meaning that the object should be
     * multiplied by `t`. Negative values are treated like zero.
     *
     * Since a zero width is normally painted as a hairline width rather than no
     * border at all, the zero factor is special-cased to instead change the
     * style no [BorderStyle.NONE].
     *
     * Values for `t` are usually obtained from an [Animation<double>], such as
     * an [AnimationController].
     */
    fun scale(t: Double): BorderSide {
        assert(t != null)
        return BorderSide(
            color = color,
            width = Math.max(0.0, width * t),
            style = if (t <= 0.0) BorderStyle.NONE else style
        )
    }

    /**
     * Create a [Paint] object that, if used to stroke a line, will draw the line
     * in this border's style.
     *
     * Not all borders use this method to paint their border sides. For example,
     * non-uniform rectangular [Border]s have beveled edges and so paint their
     * border sides as filled shapes rather than using a stroke.
     */
    fun toPaint(): Paint {
        return when (style) {
            BorderStyle.SOLID -> Paint().apply {
                color = color
                strokeWidth = width
                style = PaintingStyle.stroke
            }
            BorderStyle.NONE -> Paint().apply {
                color = Color(0x00000000)
                strokeWidth = 0.0
                style = PaintingStyle.stroke
            }
        }
    }

    override fun toString() = "$runtimeType($color, ${width.toStringAsFixed(1)}, $style)"

    companion object {

        /** A hairline black border that is not rendered. */
        @JvmStatic
        val None = BorderSide(width = 0.0, style = BorderStyle.NONE)
    }
}

/**
 * Creates a [BorderSide] that represents the addition of the two given
 * [BorderSide]s.
 *
 * It is only valid to call this if [canMerge] returns true for the two
 * sides.
 *
 * If one of the sides is zero-width with [BorderStyle.NONE], then the other
 * side is return as-is. If both of the sides are zero-width with
 * [BorderStyle.NONE], then [BorderSide.zero] is returned.
 *
 * The arguments must not be null.
 */
fun merge(a: BorderSide, b: BorderSide): BorderSide {
    assert(canMerge(a, b))
    val aIsNone = a.style == BorderStyle.NONE && a.width == 0.0
    val bIsNone = b.style == BorderStyle.NONE && b.width == 0.0
    if (aIsNone && bIsNone)
        return BorderSide.None
    if (aIsNone)
        return b
    if (bIsNone)
        return a
    assert(a.color == b.color)
    assert(a.style == b.style)
    return BorderSide(
        color = a.color, // == b.color
        width = a.width + b.width,
        style = a.style // == b.style
    )
}

/**
 * Whether the two given [BorderSide]s can be merged using [new
 * BorderSide.merge].
 *
 * Two sides can be merged if one or both are zero-width with
 * [BorderStyle.NONE], or if they both have the same color and style.
 */
fun canMerge(a: BorderSide, b: BorderSide): Boolean {
    if ((a.style == BorderStyle.NONE && a.width == 0.0) ||
        (b.style == BorderStyle.NONE && b.width == 0.0)
    )
        return true
    return a.style == b.style &&
            a.color == b.color
}

/**
 * Linearly interpolate between two border sides.
 *
 * The arguments must not be null.
 *
 * The `t` argument represents position on the timeline, with 0.0 meaning
 * that the interpolation has not started, returning `a` (or something
 * equivalent to `a`), 1.0 meaning that the interpolation has finished,
 * returning `b` (or something equivalent to `b`), and values in between
 * meaning that the interpolation is at the relevant point on the timeline
 * between `a` and `b`. The interpolation can be extrapolated beyond 0.0 and
 * 1.0, so negative values and values greater than 1.0 are valid (and can
 * easily be generated by curves such as [Curves.elasticInOut]).
 *
 * Values for `t` are usually obtained from an [Animation<double>], such as
 * an [AnimationController].
 */
fun lerp(a: BorderSide, b: BorderSide, t: Double): BorderSide {
    if (t == 0.0)
        return a
    if (t == 1.0)
        return b
    val width = lerpDouble(a.width, b.width, t)
    if (width < 0.0)
        return BorderSide.None
    if (a.style == b.style) {
        return BorderSide(
            color = Color.lerp(a.color, b.color, t)!!,
            width = width,
            style = a.style // == b.style
        )
    }
    val colorA: Color = when (a.style) {
        BorderStyle.SOLID ->
            a.color
        BorderStyle.NONE ->
            a.color.withAlpha(0x00)
    }
    val colorB: Color = when (b.style) {
        BorderStyle.SOLID ->
            b.color
        BorderStyle.NONE ->
            b.color.withAlpha(0x00)
    }
    return BorderSide(
        color = Color.lerp(colorA, colorB, t)!!,
        width = width,
        style = BorderStyle.SOLID
    )
}