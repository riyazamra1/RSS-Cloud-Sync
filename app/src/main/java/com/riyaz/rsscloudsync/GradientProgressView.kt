package com.riyaz.rsscloudsync

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class GradientProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 72f

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(235, 226, 245)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
    }

    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 23, 45)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.BOLD
        )
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 124, 140)
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    fun setProgress(value: Float, animate: Boolean = true) {

        val newValue = value.coerceIn(0f, 100f)

        if (!animate) {
            progress = newValue
            invalidate()
            return
        }

        val animator = ValueAnimator.ofFloat(progress, newValue)

        animator.duration = 700L

        animator.interpolator = DecelerateInterpolator()

        animator.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }

        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()

        val centerX = width / 2f
        val centerY = height / 2f

        val radius = size / 2f - 9f

        rect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // Background ring
        canvas.drawArc(
            rect,
            -90f,
            360f,
            false,
            backgroundPaint
        )

        // Gradient ring
        val gradient = SweepGradient(
            centerX,
            centerY,
            intArrayOf(
                Color.rgb(232, 76, 176),
                Color.rgb(125, 74, 240),
                Color.rgb(60, 130, 245),
                Color.rgb(232, 76, 176)
            ),
            null
        )

        progressPaint.shader = gradient

        canvas.drawArc(
            rect,
            -90f,
            360f * (progress / 100f),
            false,
            progressPaint
        )

        // Percentage
        percentPaint.textSize = size * 0.27f

        val percentText = "${progress.toInt()}%"

        val percentY =
            centerY - (percentPaint.ascent() + percentPaint.descent()) / 2f - 4f

        canvas.drawText(
            percentText,
            centerX,
            percentY,
            percentPaint
        )

        // "Synced"
        labelPaint.textSize = size * 0.15f

        canvas.drawText(
            "Synced",
            centerX,
            centerY + size * 0.20f,
            labelPaint
        )
    }
}