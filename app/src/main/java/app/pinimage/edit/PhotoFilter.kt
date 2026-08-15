package app.pinimage.edit

import android.graphics.Bitmap
import androidx.annotation.StringRes
import app.pinimage.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class FilterAdjustments(
    val lightness: Int = 0,
    val brightness: Int = 0,
    val highlights: Int = 0,
    val shadows: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val vibrance: Int = 0,
    val warmth: Int = 0,
    val tint: Int = 0,
    val sharpness: Int = 0,
    val clarity: Int = 0,
    val glow: Int = 0,
    val loFi: Int = 0,
)

data class FilterPreset(@StringRes val name: Int, val adjustments: FilterAdjustments)

object PhotoFilters {
    val presets = listOf(
        FilterPreset(R.string.filter_original, FilterAdjustments()),
        FilterPreset(R.string.filter_dream_glow, FilterAdjustments(lightness = 25, highlights = 10, shadows = -20, contrast = 25, vibrance = 25, warmth = 32, sharpness = 45, tint = 25)),
        FilterPreset(R.string.filter_floating_gold, FilterAdjustments(lightness = 20, highlights = 20, contrast = 18, vibrance = 30, warmth = -15, tint = 35, clarity = 23)),
        FilterPreset(R.string.filter_clear_green, FilterAdjustments(lightness = 25, highlights = 10, contrast = 25, vibrance = 13, sharpness = 30, warmth = 10, tint = 31, clarity = 34)),
        FilterPreset(R.string.filter_shimmering, FilterAdjustments(lightness = 10, highlights = 30, contrast = 28, brightness = -24, saturation = -30, vibrance = 15, warmth = 22, tint = 36, clarity = 38)),
        FilterPreset(R.string.filter_midsummer, FilterAdjustments(lightness = 15, highlights = 30, contrast = 28, brightness = -24, saturation = -30, vibrance = 35, warmth = 20, tint = 36, clarity = 15)),
        FilterPreset(R.string.filter_ghibli_summer, FilterAdjustments(lightness = 22, highlights = 30, contrast = 20, brightness = -24, saturation = -30, vibrance = 35, warmth = 20, tint = 36, clarity = 13)),
        FilterPreset(R.string.filter_mozhizao_glow, FilterAdjustments(tint = -45, warmth = -7, shadows = -52, highlights = 15, saturation = 50, glow = 75, loFi = 50)),
    )
}

/** Deterministic, offline bitmap renderer. Values use the familiar -100..100 scale. */
object PhotoFilterRenderer {
    fun apply(source: Bitmap, a: FilterAdjustments): Bitmap {
        if (a == FilterAdjustments()) return source.copy(Bitmap.Config.ARGB_8888, false)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val contrastFactor = 1f + a.contrast / 100f * .9f
        val saturationFactor = 1f + a.saturation / 100f
        val exposureFactor = 2f.pow(a.lightness / 100f * .65f)
        val brightnessAdd = a.brightness / 100f * 72f
        val warmth = a.warmth / 100f * 28f
        val tint = a.tint / 100f * 24f
        val lofi = a.loFi.coerceIn(0, 100) / 100f

        for (i in pixels.indices) {
            val color = pixels[i]
            val alpha = color ushr 24
            var r = ((color shr 16) and 255).toFloat()
            var g = ((color shr 8) and 255).toFloat()
            var b = (color and 255).toFloat()
            var lum = (.2126f * r + .7152f * g + .0722f * b) / 255f
            val shadowWeight = (1f - lum).let { it * it }
            val highlightWeight = lum * lum
            val tonal = (a.shadows / 100f * shadowWeight + a.highlights / 100f * highlightWeight) * 82f
            r = r * exposureFactor + brightnessAdd + tonal
            g = g * exposureFactor + brightnessAdd + tonal
            b = b * exposureFactor + brightnessAdd + tonal
            r = (r - 127.5f) * contrastFactor + 127.5f
            g = (g - 127.5f) * contrastFactor + 127.5f
            b = (b - 127.5f) * contrastFactor + 127.5f
            lum = .2126f * r + .7152f * g + .0722f * b
            val chroma = (max(r, max(g, b)) - min(r, min(g, b))) / 255f
            val vibrant = 1f + a.vibrance / 100f * (1f - chroma) * .85f
            val sat = saturationFactor * vibrant
            r = lum + (r - lum) * sat
            g = lum + (g - lum) * sat
            b = lum + (b - lum) * sat
            r += warmth
            b -= warmth
            if (tint >= 0f) {
                r -= tint * .45f; g += tint * .75f; b -= tint * .1f
            } else {
                val cool = -tint
                r -= cool * .45f; g += cool * .25f; b += cool * .75f
            }
            if (lofi > 0f) {
                val levels = (48 - lofi * 32).coerceAtLeast(12f)
                r = (r / 255f * levels).toInt() / levels * 255f
                g = (g / 255f * levels).toInt() / levels * 255f
                b = (b / 255f * levels).toInt() / levels * 255f
                val x = (i % width) / width.toFloat() * 2f - 1f
                val y = (i / width) / height.toFloat() * 2f - 1f
                val vignette = (1f - (x * x + y * y) * .16f * lofi).coerceAtLeast(.68f)
                r *= vignette; g *= vignette; b *= vignette
            }
            pixels[i] = (alpha shl 24) or (r.clamp() shl 16) or (g.clamp() shl 8) or b.clamp()
        }

        if (a.clarity != 0 || a.sharpness != 0) sharpen(pixels, width, height, a.clarity, a.sharpness)
        if (a.glow > 0) addGlow(pixels, width, height, a.glow)
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun sharpen(pixels: IntArray, w: Int, h: Int, clarity: Int, sharpness: Int) {
        if (w < 3 || h < 3) return
        val original = pixels.copyOf()
        val amount = (sharpness / 100f * .7f + clarity / 100f * .45f).coerceIn(-.8f, 1.15f)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val c = original[i]
            val n = intArrayOf(original[i - 1], original[i + 1], original[i - w], original[i + w])
            fun channel(shift: Int): Int {
                val center = (c shr shift) and 255
                val blur = n.sumOf { (it shr shift) and 255 } / 4f
                return (center + (center - blur) * amount).clamp()
            }
            pixels[i] = (c and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }

    private fun addGlow(pixels: IntArray, w: Int, h: Int, value: Int) {
        if (w < 3 || h < 3) return
        val source = pixels.copyOf()
        val strength = value.coerceIn(0, 100) / 100f * .62f
        val radius = (min(w, h) / 180).coerceIn(2, 9)
        val step = radius
        for (y in radius until h - radius) for (x in radius until w - radius) {
            var rr = 0; var gg = 0; var bb = 0; var count = 0
            var yy = y - radius
            while (yy <= y + radius) {
                var xx = x - radius
                while (xx <= x + radius) {
                    val c = source[yy * w + xx]
                    val lum = (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)) / 3
                    if (lum > 128) { rr += (c shr 16) and 255; gg += (c shr 8) and 255; bb += c and 255; count++ }
                    xx += step
                }
                yy += step
            }
            if (count == 0) continue
            val i = y * w + x
            val c = pixels[i]
            fun screen(base: Int, glow: Int): Int {
                val screened = 255 - (255 - base) * (255 - glow) / 255
                return (base + (screened - base) * strength).clamp()
            }
            pixels[i] = (c and -0x1000000) or
                (screen((c shr 16) and 255, rr / count) shl 16) or
                (screen((c shr 8) and 255, gg / count) shl 8) or screen(c and 255, bb / count)
        }
    }

    private fun Float.clamp() = toInt().coerceIn(0, 255)
    private fun Int.clamp() = coerceIn(0, 255)
}
