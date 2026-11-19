package com.example.proguide

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs

class NavigationArrowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val path = Path()
    private var currentAngle = 0f
    private var glowAnimator: ValueAnimator? = null
    private var rotateAnimator: ValueAnimator? = null
    private var animationProgress = 0f

    // Arrows ki settings (6 arrows)
    private val arrowCount = 6
    private val arrowSpacing = 40f
    private val arrowWidth = 35f
    private val arrowHeight = 25f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        // Glow Animation (Niche se upar light jayegi)
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                animationProgress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // Smooth Rotation Function
    fun setDirection(angle: Float) {
        var targetAngle = angle
        var diff = targetAngle - currentAngle
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        targetAngle = currentAngle + diff

        if (abs(currentAngle - targetAngle) < 2f) return

        rotateAnimator?.cancel()
        rotateAnimator = ValueAnimator.ofFloat(currentAngle, targetAngle).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        
        // Calculate total height of the arrows to center them vertically
        val totalHeight = (arrowCount - 1) * arrowSpacing
        val startY = centerY + (totalHeight / 2f)

        canvas.save()
        // Rotate exactly around the center of the view to prevent clipping
        canvas.rotate(currentAngle, centerX, centerY)

        for (i in 0 until arrowCount) {
            val yPos = startY - (i * arrowSpacing)
            
            val arrowProgress = i.toFloat() / (arrowCount - 1) 
            val distToAnim = abs(animationProgress - arrowProgress)
            
            val glowAlpha = when {
                distToAnim < 0.15f -> 255 // Bright
                distToAnim < 0.3f -> 120  // Medium
                else -> 40                // Dim
            }

            arrowPaint.color = Color.argb(glowAlpha, 0, 160, 255) 
            
            arrowPaint.setShadowLayer(
                if (glowAlpha == 255) 25f else 10f, 
                0f, 0f, 
                Color.argb(glowAlpha, 0, 160, 255)
            )

            path.reset()
            path.moveTo(centerX - arrowWidth, yPos + arrowHeight)
            path.lineTo(centerX, yPos)
            path.lineTo(centerX + arrowWidth, yPos + arrowHeight)
            
            canvas.drawPath(path, arrowPaint)
        }

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator?.cancel()
        rotateAnimator?.cancel()
    }
}
