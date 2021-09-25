package dev.anshshukla.face

import android.graphics.Canvas

data class Dimensions(val width: Int, val height: Int)

interface IDrawableManager {
    var mDeviceDimensions: Dimensions
    var mInitialzed: Boolean
    fun onDimensionsChange(dimensions: Dimensions)
    fun draw(canvas: Canvas, ambientMode: Boolean): Boolean
}