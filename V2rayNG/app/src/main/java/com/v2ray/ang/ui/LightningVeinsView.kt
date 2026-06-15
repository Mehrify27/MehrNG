package com.v2ray.ang.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.util.Random

class LightningVeinsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#34C759") // Apple green
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        setShadowLayer(10f, 0f, 0f, Color.parseColor("#34C759")) // Glow effect
    }

    private var active = false
    private val random = Random()
    private val paths = mutableListOf<Path>()

    fun setActive(isActive: Boolean) {
        active = isActive
        if (active) {
            generatePaths()
            invalidate()
        } else {
            paths.clear()
            invalidate()
        }
    }

    private fun generatePaths() {
        paths.clear()
        if (!active) return

        val cx = width / 2f
        val cy = height / 2f
        val density = resources.displayMetrics.density
        // Central circle radius is 60dp (120dp/2), start slightly outside it (62dp)
        val startRadius = 62f * density
        val maxRadius = width / 2f

        if (maxRadius <= startRadius) return

        // Generate 3 to 6 random lightning branches
        val numBranches = 3 + random.nextInt(4)
        for (i in 0 until numBranches) {
            val path = Path()
            val angle = random.nextFloat() * 2 * Math.PI.toFloat()
            var currX = cx + Math.cos(angle.toDouble()).toFloat() * startRadius
            var currY = cy + Math.sin(angle.toDouble()).toFloat() * startRadius
            path.moveTo(currX, currY)

            var currRadius = startRadius
            val steps = 3 + random.nextInt(4)
            val stepSize = (maxRadius - startRadius) / steps

            for (step in 0 until steps) {
                currRadius += stepSize
                val jitterAngle = angle + (random.nextFloat() - 0.5f) * 0.45f
                val nextX = cx + Math.cos(jitterAngle.toDouble()).toFloat() * currRadius
                val nextY = cy + Math.sin(jitterAngle.toDouble()).toFloat() * currRadius
                
                path.lineTo(nextX, nextY)
                currX = nextX
                currY = nextY

                // Branch out sometimes
                if (random.nextFloat() < 0.35f && step < steps - 1) {
                    val branchPath = Path()
                    branchPath.moveTo(currX, currY)
                    val branchAngle = jitterAngle + (if (random.nextBoolean()) 0.45f else -0.45f)
                    val branchNextX = currX + Math.cos(branchAngle.toDouble()).toFloat() * stepSize * 0.75f
                    val branchNextY = currY + Math.sin(branchAngle.toDouble()).toFloat() * stepSize * 0.75f
                    branchPath.lineTo(branchNextX, branchNextY)
                    paths.add(branchPath)
                }
            }
            paths.add(path)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return

        // Enable software layer for shadow glow support
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        for (path in paths) {
            canvas.drawPath(path, paint)
        }

        // Regenerate and redraw for flicker animation
        if (active) {
            postDelayed({
                if (active) {
                    generatePaths()
                    invalidate()
                }
            }, 120 + random.nextInt(120).toLong())
        }
    }
}
