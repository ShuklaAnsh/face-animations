package dev.anshshukla.face

import android.content.res.Resources
import android.graphics.*
import androidx.core.graphics.scale

open class AnimationManager(resources: Resources, packageName: String, override var mDeviceDimensions: Dimensions) :
    IDrawableManager {
    init {
        initializeAnimationManager(resources, packageName)
    }

    private var mRunning: Boolean = false
    private var mCurrAnimationIdx: Int = 0
    private lateinit var mAnimationPaint: Paint
    private var mAnimationBitmaps: MutableList<Bitmap> = ArrayList()
    override var mInitialzed: Boolean = false

    private fun initializeAnimationManager(resources: Resources, packageName: String) {
        Thread {
            var imageNum = 0
            while (true) {
                val imageNumStr = imageNum.toString().padStart(4, '0')
                val resId =
                    resources.getIdentifier("cloud_$imageNumStr", "drawable", packageName)
                if (resId == 0) break

                val bitmap = BitmapFactory.decodeResource(resources, resId)
                if(mDeviceDimensions.width != 0) {
                    val scale = mDeviceDimensions.width / bitmap.width.toFloat()
                    mAnimationBitmaps.add(
                        bitmap.scale(
                            (bitmap.width * scale).toInt(),
                            (bitmap.height * scale).toInt(), true
                        )
                    )
                }
                imageNum++
            }
            mInitialzed = true
        }.start()

        mAnimationPaint = Paint().apply {
            color = Color.BLACK
        }
    }

    override fun draw(canvas: Canvas, ambientMode: Boolean): Boolean {
        if(mInitialzed) {
            canvas.save()
            if (mRunning && !ambientMode) {
                canvas.drawBitmap(mAnimationBitmaps[mCurrAnimationIdx], 0f, 0f, mAnimationPaint)
                mCurrAnimationIdx = (mCurrAnimationIdx + 1) % mAnimationBitmaps.size // loop forever
            }
            else {
                canvas.drawBitmap(mAnimationBitmaps[mCurrAnimationIdx], 0f, 0f, mAnimationPaint)
            }
            canvas.restore()
        }
        return mRunning && mInitialzed
    }

    override fun onDimensionsChange(dimensions: Dimensions) {
        if(dimensions == mDeviceDimensions) return
        mDeviceDimensions = dimensions

        if (mAnimationBitmaps.size > 0) {
            Thread {
                val animScale = mDeviceDimensions.width / mAnimationBitmaps[0].width.toFloat()
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
        mRunning = !mRunning
    }
}