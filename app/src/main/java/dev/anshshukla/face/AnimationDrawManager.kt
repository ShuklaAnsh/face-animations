package dev.anshshukla.face

import android.content.res.Resources
import android.graphics.*
import android.util.Log
import androidx.core.graphics.scale

open class AnimationDrawManager(
    resources: Resources,
    packageName: String,
    override var deviceDimensions: Dimensions
) :
    IDrawManager {
    private val logTag = "MyWatchFace"
    init {
        initializeAnimationManager(resources, packageName)
    }

    private var mRunning: Boolean = false
    private var mCurrAnimationIdx: Int = 0
    private lateinit var mAnimationPaint: Paint
    private var mAnimationBitmaps: MutableList<Bitmap> = ArrayList()
    override var isInitialized: Boolean = false

    private fun initializeAnimationManager(resources: Resources, packageName: String) {
        Log.d(logTag, "initializeAnimationManager()")
        Thread {
            var imageNum = 0
            while (true) {
                val imageNumStr = imageNum.toString().padStart(4, '0')
                val resId =
                    resources.getIdentifier("cloud_$imageNumStr", "drawable", packageName)
                if (resId == 0) break

                val bitmap = BitmapFactory.decodeResource(resources, resId)
                if (deviceDimensions.width != 0) {
                    val scale = deviceDimensions.width / bitmap.width.toFloat()
                    mAnimationBitmaps.add(
                        bitmap.scale(
                            (bitmap.width * scale).toInt(),
                            (bitmap.height * scale).toInt(), true
                        )
                    )
                }
                imageNum++
            }
            isInitialized = true
        }.start()

        mAnimationPaint = Paint().apply {
            color = Color.BLACK
        }
    }

    override fun draw(canvas: Canvas, ambientMode: Boolean): Boolean {
        if (isInitialized) {
            canvas.save()
            if (mRunning && !ambientMode) {
                canvas.drawBitmap(mAnimationBitmaps[mCurrAnimationIdx], 0f, 0f, mAnimationPaint)
                mCurrAnimationIdx = (mCurrAnimationIdx + 1) % mAnimationBitmaps.size // loop forever
            } else {
                canvas.drawBitmap(mAnimationBitmaps[mCurrAnimationIdx], 0f, 0f, mAnimationPaint)
            }
            canvas.restore()
        }
        return mRunning && isInitialized
    }

    override fun onDimensionsChange(dimensions: Dimensions) {
        Log.d(logTag, "onDimensionsChange()")
        if (dimensions == deviceDimensions) return
        deviceDimensions = dimensions

        if (mAnimationBitmaps.size > 0) {
            Thread {
                val animScale = deviceDimensions.width / mAnimationBitmaps[0].width.toFloat()
                for (i in mAnimationBitmaps.indices) {
                    mAnimationBitmaps[i] = mAnimationBitmaps[i].scale(
                        (mAnimationBitmaps[i].width * animScale).toInt(),
                        (mAnimationBitmaps[i].height * animScale).toInt(), true
                    )
                }
            }.start()
        }
    }

    fun toggleAnimation() {
        Log.d(logTag, "toggleAnimation()")
        mRunning = !mRunning
    }
}