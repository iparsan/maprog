package com.iparsan.maprog.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Marcador dibujado con un emoji (evita usar iconos con derechos de terceros). */
class EmojiDrawable(context: Context, private val emoji: String) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val size = (30 * density).toInt()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; style = Paint.Style.STROKE; strokeWidth = 1.5f * density }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 17 * density; textAlign = Paint.Align.CENTER }

    override fun draw(canvas: Canvas) {
        val r = size / 2f
        canvas.drawCircle(r, r, r - density, bg)
        canvas.drawCircle(r, r, r - density, border)
        val y = r - (text.descent() + text.ascent()) / 2
        canvas.drawText(emoji, r, y, text)
    }
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Punto azul de "tu posición". */
class DotDrawable(private val color: Int) : Drawable() {
    private val size = 48
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (color and 0x00FFFFFF) or 0x40000000 }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    override fun draw(canvas: Canvas) {
        val r = size / 2f
        canvas.drawCircle(r, r, r, halo)
        canvas.drawCircle(r, r, r * 0.45f, white)
        canvas.drawCircle(r, r, r * 0.33f, dot)
    }
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
