package com.example.pdrprototype

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class TrajectoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val path = Path()
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val pointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val points = mutableListOf<Pair<Double, Double>>()
    private var currentX = 0.0
    private var currentY = 0.0

    fun updatePosition(x: Double, y: Double) {
        currentX = x
        currentY = y
        points.add(Pair(x, y))
        invalidate()
    }

    fun reset() {
        points.clear()
        currentX = 0.0
        currentY = 0.0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        drawGrid(canvas, centerX, centerY)

        if (points.isEmpty()) {
            canvas.drawCircle(centerX, centerY, 12f, pointPaint)
            return
        }

        val scale = 100f
        path.reset()

        for (i in points.indices) {
            val p = points[i]
            val screenX = centerX + (p.first * scale).toFloat()
            val screenY = centerY - (p.second * scale).toFloat()
            if (i == 0) path.moveTo(screenX, screenY)
            else path.lineTo(screenX, screenY)
        }

        canvas.drawPath(path, paint)

        val currentScreenX = centerX + (currentX * scale).toFloat()
        val currentScreenY = centerY - (currentY * scale).toFloat()
        canvas.drawCircle(currentScreenX, currentScreenY, 14f, pointPaint)
    }

    private fun drawGrid(canvas: Canvas, centerX: Float, centerY: Float) {
        val gridSize = 50f

        var x = centerX
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += gridSize
        }
        x = centerX - gridSize
        while (x > 0) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x -= gridSize
        }

        var y = centerY
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += gridSize
        }
        y = centerY - gridSize
        while (y > 0) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y -= gridSize
        }

        canvas.drawLine(0f, centerY, width.toFloat(), centerY, gridPaint)
        canvas.drawLine(centerX, 0f, centerX, height.toFloat(), gridPaint)
    }
}
