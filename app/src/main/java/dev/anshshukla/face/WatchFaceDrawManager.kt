package dev.anshshukla.face

import android.content.res.Resources
import android.graphics.*
import android.util.Log
import androidx.core.graphics.scale
import java.util.*

open class WatchFaceDrawManager(
    calendar: Calendar,
    resources: Resources,
    override var deviceDimensions: Dimensions
) : IDrawManager {
    private val logTag = "WatchFaceDrawManager"

    init {
        initializeWatchFaceManager(resources)
    }

    private var mCalendar = calendar
    private lateinit var mPinPaint: Paint
    private lateinit var mPinBitmap: Bitmap

    private lateinit var mSecondHandPaint: Paint
    private lateinit var mSecondHandBitmap: Bitmap

    private lateinit var mMinuteHandPaint: Paint
    private lateinit var mMinuteHandBitmap: Bitmap
    private lateinit var mMinuteHandAODBitmap: Bitmap

    private lateinit var mHourHandPaint: Paint
    private lateinit var mHourHandBitmap: Bitmap
    private lateinit var mHourHandAODBitmap: Bitmap

    private lateinit var mIndexPaint: Paint
    private lateinit var mIndexBitmap: Bitmap
    private lateinit var mAODForegroundPaint: Paint

    override var isInitialized: Boolean = false

    override fun onDimensionsChange(dimensions: Dimensions) {
        Log.d(logTag, "onDimensionsChange()")
        if (dimensions == deviceDimensions || !isInitialized) return
        deviceDimensions = dimensions

        scaleBitmaps()
    }

    private fun scaleBitmaps() {
        Log.d(logTag, "scaleBitmaps()")
        val bgScale = deviceDimensions.width / mIndexBitmap.width.toFloat()
        mIndexBitmap = mIndexBitmap.scale(
            (mIndexBitmap.width * bgScale).toInt(),
            (mIndexBitmap.height * bgScale).toInt(), true
        )

        val hourHandScale = deviceDimensions.width / mHourHandBitmap.width.toFloat()
        mHourHandBitmap = mHourHandBitmap.scale(
            (mHourHandBitmap.width * hourHandScale).toInt(),
            (mHourHandBitmap.height * hourHandScale).toInt(), true
        )

        val minuteHandScale = deviceDimensions.width / mMinuteHandBitmap.width.toFloat()
        mMinuteHandBitmap = mMinuteHandBitmap.scale(
            (mMinuteHandBitmap.width * minuteHandScale).toInt(),
            (mMinuteHandBitmap.height * minuteHandScale).toInt(), true
        )

        val hourHandAODScale = deviceDimensions.width / mHourHandAODBitmap.width.toFloat()
        mHourHandAODBitmap = mHourHandAODBitmap.scale(
            (mHourHandAODBitmap.width * hourHandAODScale).toInt(),
            (mHourHandAODBitmap.height * hourHandAODScale).toInt(), true
        )

        val minuteHandAODScale = deviceDimensions.width / mMinuteHandAODBitmap.width.toFloat()
        mMinuteHandAODBitmap = mMinuteHandAODBitmap.scale(
            (mMinuteHandAODBitmap.width * minuteHandAODScale).toInt(),
            (mMinuteHandAODBitmap.height * minuteHandAODScale).toInt(), true
        )

        val secondHandScale = deviceDimensions.width / mSecondHandBitmap.width.toFloat()
        mSecondHandBitmap = mSecondHandBitmap.scale(
            (mSecondHandBitmap.width * secondHandScale).toInt(),
            (mSecondHandBitmap.height * secondHandScale).toInt(), true
        )

        val pinScale = deviceDimensions.width / mPinBitmap.width.toFloat()
        mPinBitmap = mPinBitmap.scale(
            (mPinBitmap.width * pinScale).toInt(),
            (mPinBitmap.height * pinScale).toInt(), true
        )
    }

    override fun draw(canvas: Canvas, ambientMode: Boolean): Boolean {
        if (isInitialized) {
            // draw Index(canvas)
            canvas.drawBitmap(mIndexBitmap, 0f, 0f, mIndexPaint)
            // draw AOD foreground
            if (ambientMode) {
                canvas.drawPaint(mAODForegroundPaint)
            }
            drawWatchFace(canvas, ambientMode)
        }
        return isInitialized
    }

    private fun drawWatchFace(canvas: Canvas, ambientMode: Boolean) {
        val centerX = (deviceDimensions.width / 2).toFloat()
        val centerY = (deviceDimensions.height / 2).toFloat()
        /*
         * These calculations reflect the rotation in degrees per unit of time, e.g.,
         * 360 / 60 = 6 and 360 / 12 = 30.
         */
        // Constantly moving seconds
        val seconds =
            mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f
        val secondsRotation = seconds * 6f

        val minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f

        val hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f
        val hoursRotation = mCalendar.get(Calendar.HOUR) * 30 + hourHandOffset

        /*
         * Save the canvas state before we can begin to rotate it.
         */
        canvas.save()

        canvas.rotate(hoursRotation, centerX, centerY)
        if (ambientMode) {
            canvas.drawBitmap(mHourHandAODBitmap, 0f, 0f, mHourHandPaint)
        } else {
            canvas.drawBitmap(mHourHandBitmap, 0f, 0f, mHourHandPaint)
        }

        canvas.rotate(minutesRotation - hoursRotation, centerX, centerY)
        if (ambientMode) {
            canvas.drawBitmap(mMinuteHandAODBitmap, 0f, 0f, mMinuteHandPaint)
        } else {
            canvas.drawBitmap(mMinuteHandBitmap, 0f, 0f, mMinuteHandPaint)
        }

        /*
         * Ensure the "seconds" hand is drawn only when we are in interactive mode.
         * Otherwise, we only update the watch face once a minute.
         */
        if (!ambientMode) {
            canvas.rotate(secondsRotation - minutesRotation, centerX, centerY)
            canvas.drawBitmap(mSecondHandBitmap, 0f, 0f, mSecondHandPaint)
        }

        /* Restore the canvas" original orientation. */
        canvas.restore()

        if (ambientMode) {
            canvas.drawBitmap(mPinBitmap, 0f, 0f, mPinPaint)
        }
    }

    private fun initializeWatchFaceManager(resources: Resources) {
        Log.d(logTag, "initializeWatchFaceManager()")
        mIndexPaint = Paint().apply {
            color = Color.BLACK
        }
        mHourHandPaint = Paint().apply {
            color = Color.BLACK
        }
        mMinuteHandPaint = Paint().apply {
            color = Color.BLACK
        }
        mSecondHandPaint = Paint().apply {
            color = Color.BLACK
        }
        mPinPaint = Paint().apply {
            color = Color.BLACK
        }
        mAODForegroundPaint = Paint().apply {
            color = Color.BLACK
            alpha = 75
        }

        mIndexBitmap = BitmapFactory.decodeResource(resources, R.drawable.index)
        mPinBitmap = BitmapFactory.decodeResource(resources, R.drawable.pin_aod)
        mSecondHandBitmap = BitmapFactory.decodeResource(resources, R.drawable.second)
        mHourHandBitmap = BitmapFactory.decodeResource(resources, R.drawable.hour)
        mMinuteHandBitmap = BitmapFactory.decodeResource(resources, R.drawable.minute)
        mHourHandAODBitmap = BitmapFactory.decodeResource(resources, R.drawable.hour_aod)
        mMinuteHandAODBitmap = BitmapFactory.decodeResource(resources, R.drawable.minute_aod)

        if (deviceDimensions.width != 0) {
            scaleBitmaps()
        }
        isInitialized = true
    }

    fun updateWatchHandStyle(ambientMode: Boolean, muteMode: Boolean) {
        Log.d(logTag, "updateWatchHandStyle()")
        if (ambientMode) {
            mSecondHandPaint.color = Color.TRANSPARENT
        } else {
            mSecondHandPaint.color = Color.BLACK
        }

        mHourHandPaint.alpha = if (muteMode) 100 else 255
        mMinuteHandPaint.alpha = if (muteMode) 100 else 255
        mSecondHandPaint.alpha = if (muteMode) 80 else 255
        mPinPaint.alpha = if (muteMode) 100 else 255
        mSecondHandPaint.alpha = if (muteMode) 80 else 255
    }
}