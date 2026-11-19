package com.example.proguide

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
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
    private var animator: ValueAnimator? = null
    private var animationProgress = 0f

    // Arrows ki settings
    private val arrowCount = 4
    private val arrowSpacing = 45f
    private val arrowWidth = 35f
    private val arrowHeight = 25f

    init {
        // Shadow/Glow ke liye software layer zaroori hai
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        // Animation jo niche se upar glow leke jayegi
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                animationProgress = animation.animatedValue as Float
                invalidate() // Redraw for animation
            }
            start()
        }
    }

    // Direction set karne ke liye function (0 = straight, positive = right, negative = left)
    fun setDirection(angle: Float) {
        if (abs(currentAngle - angle) > 2f) {
            currentAngle = angle
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val startY = height * 0.8f // Niche se start
        val endY = height * 0.2f   // Upar tak

        canvas.save()
        // Center point ke around rotate karo (Map direction ke hisaab se)
        canvas.rotate(currentAngle, centerX, startY)

        for (i in 0 until arrowCount) {
            // i=0 sabse niche wala arrow, i=3 sabse upar wala
            val yPos = startY - (i * arrowSpacing)
            
            // Light niche se upar travel karegi
            val arrowProgress = i.toFloat() / (arrowCount - 1) 
            val distToAnim = abs(animationProgress - arrowProgress)
            
            // Glow intensity calculation
            val glowAlpha = when {
                distToAnim < 0.15f -> 255 // Full bright (Jahan light hai)
                distToAnim < 0.3f -> 120  // Medium glow
                else -> 40                // Dim (baaki arrows)
            }

            // Blue Color Glow
            arrowPaint.color = Color.argb(glowAlpha, 0, 160, 255) 
            
            // Shadow layer for glowing effect
            arrowPaint.setShadowLayer(
                if (glowAlpha == 255) 25f else 10f, 
                0f, 0f, 
                Color.argb(glowAlpha, 0, 160, 255)
            )

            // Arrow Path Draw karo (^ shape)
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
        animator?.cancel()
    }
}
