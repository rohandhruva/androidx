/*
 * Copyright 2020 The Android Open Source Project
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

package android.graphics

open class Paint {
    enum class Style(val skija: org.jetbrains.skija.PaintMode) {
        FILL(org.jetbrains.skija.PaintMode.FILL),
        FILL_AND_STROKE(org.jetbrains.skija.PaintMode.STROKE_AND_FILL),
        STROKE(org.jetbrains.skija.PaintMode.STROKE)
    }

    enum class Cap(val skija: org.jetbrains.skija.PaintStrokeCap) {
        BUTT(org.jetbrains.skija.PaintStrokeCap.BUTT),
        ROUND(org.jetbrains.skija.PaintStrokeCap.ROUND),
        SQUARE(org.jetbrains.skija.PaintStrokeCap.SQUARE)
    }

    enum class Join(val skija: org.jetbrains.skija.PaintStrokeJoin) {
        BEVEL(org.jetbrains.skija.PaintStrokeJoin.BEVEL),
        MITER(org.jetbrains.skija.PaintStrokeJoin.MITER),
        ROUND((org.jetbrains.skija.PaintStrokeJoin.ROUND))
    }

    open class FontMetricsInt {
        @JvmField
        var top: Int = 0
        @JvmField
        var ascent: Int = 0
        @JvmField
        var descent: Int = 0
        @JvmField
        var bottom: Int = 0
        @JvmField
        var leading: Int = 0

        override fun toString(): String {
            return "FontMetricsInt: top=" + top + " ascent=" + ascent +
                    " descent=" + descent + " bottom=" + bottom +
                    " leading=" + leading
        }
    }

    val skijaPaint = org.jetbrains.skija.Paint()

    constructor(flags: Int) {
        if (flags and 1 == 1) {
            skijaPaint.setAntiAlias(true)
        }
    }

    var color: Int
        get() = skijaPaint.getColor()
        set(value) {
            skijaPaint.setColor(value)
        }
    var strokeWidth: Float
        get() = skijaPaint.getStrokeWidth()
        set(value) {
            skijaPaint.setStrokeWidth(value)
        }
    var style: Style
        get() = Style.values().first { it.skija == skijaPaint.getMode() }
        set(value) {
            skijaPaint.setMode(value.skija)
        }
    var strokeCap: Cap
        get() = Cap.values().first { it.skija == skijaPaint.getStrokeCap() }
        set(value) {
            skijaPaint.setStrokeCap(value.skija)
        }
    var strokeMiter: Float
        get() = skijaPaint.getStrokeMiter().toFloat()
        set(value) {
            skijaPaint.setStrokeMiter(value)
        }
    var strokeJoin: Join
        get() = Join.values().first { it.skija == skijaPaint.getStrokeJoin() }
        set(value) {
            skijaPaint.setStrokeJoin(value.skija)
        }
    var antiAlias: Boolean
        get() = skijaPaint.isAntiAlias()
        set(value) {
            skijaPaint.setAntiAlias(value)
        }

    var alpha: Int
        get() = (color shr 24) and 0xff
        set(value) {
            color = (value shl 24) or (color and 0x00ffffff)
        }

    var textSize: Float = 20f

    private var typeface: Typeface? = null
    fun getTypeface(): Typeface? = typeface
    fun setTypeface(newTypeface: Typeface?): Typeface? {
        val oldTypeface = typeface
        typeface = newTypeface
        return oldTypeface
    }
    var textLocale: java.util.Locale = java.util.Locale.getDefault()
    var fontFeatureSettings: String? = null
    var textScaleX: Float = 1f
    var textSkewX: Float = 1f

    fun setShadowLayer(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        println("Paint.setShadowLayer")
    }

    fun setPathEffect(effect: PathEffect?): PathEffect? {
        if (effect != null)
            println("setPathEffect not implemented yet")
        return effect
    }
}
