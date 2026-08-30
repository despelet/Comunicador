package com.comunic

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.VideoView
import kotlin.math.min

class CropVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {

    private var videoAspectRatio: Float? = null

    fun setVideoAspectRatio(ratio: Float) {
        if (ratio > 0f) {
            videoAspectRatio = ratio
            requestLayout()
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)

        val ratio = videoAspectRatio

        if (ratio == null || ratio <= 0f) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        /*
         * Queremos ocupar el máximo espacio posible
         * SIN deformar el video.
         */

        val widthFromHeight = (availableHeight * ratio).toInt()

        val finalWidth: Int
        val finalHeight: Int

        if (widthFromHeight <= availableWidth) {
            // El video puede ocupar toda la altura
            finalWidth = widthFromHeight
            finalHeight = availableHeight
        } else {
            // El video no entra usando toda la altura,
            // entonces usamos todo el ancho.
            finalWidth = availableWidth
            finalHeight = (availableWidth / ratio).toInt()
        }

        setMeasuredDimension(
            finalWidth,
            finalHeight
        )
    }
}