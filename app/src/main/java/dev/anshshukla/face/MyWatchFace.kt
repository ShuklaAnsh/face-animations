package dev.anshshukla.face

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.widget.Toast

import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone

/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 20

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn"t
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class MyWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: Engine) : Handler(Looper.getMainLooper()) {
        private val mWeakReference: WeakReference<Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F

        private lateinit var mPinPaint: Paint
        private lateinit var mPinBitmap: Bitmap

        private lateinit var mSecondHandPaint: Paint
        private lateinit var mSecondHandBitmap: Bitmap

        private lateinit var mMinuteHandPaint: Paint
        private lateinit var mMinuteHandBitmap: Bitmap

        private lateinit var mHourHandPaint: Paint
        private lateinit var mHourHandBitmap: Bitmap

        private var mLoopAnimationForever: Boolean = false
        private var mCurrAnimationIdx: Int = 0
        private lateinit var mAnimationPaint: Paint
        private lateinit var mAnimationBitmaps: Array<Bitmap>

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mBackgroundBitmap: Bitmap
        private lateinit var mAODBackgroundBitmap: Bitmap

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            initializeBackground()
            initializeAnimation()
            initializeWatchFace()
        }

        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.watchface)

            mAODBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.watchface_aod)
        }

        private fun initializeAnimation() {
            var imageNum = 0
            val animationBitmaps: MutableList<Bitmap> = ArrayList()
            while(true) {
                val resId = resources.getIdentifier("tongie_$imageNum", "drawable", packageName)
                if (resId == 0) break
                animationBitmaps.add(BitmapFactory.decodeResource(resources, resId))
                imageNum++
            }

            mAnimationPaint = Paint().apply {
                color = Color.BLACK
            }
            mAnimationBitmaps = animationBitmaps.toTypedArray()
        }

        private fun initializeWatchFace() {
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

            mPinBitmap = BitmapFactory.decodeResource(resources, R.drawable.pin)
            mSecondHandBitmap = BitmapFactory.decodeResource(resources, R.drawable.secondhand)
            mHourHandBitmap = BitmapFactory.decodeResource(resources, R.drawable.hourhand)
            mMinuteHandBitmap = BitmapFactory.decodeResource(resources, R.drawable.minutehand)
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        private fun updateWatchHandStyle() {
            if (mAmbient) {
                mSecondHandPaint.color = Color.TRANSPARENT
            } else {
                mSecondHandPaint.color = Color.BLACK
            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mHourHandPaint.alpha = if (inMuteMode) 100 else 255
                mMinuteHandPaint.alpha = if (inMuteMode) 100 else 255
                mSecondHandPaint.alpha = if (inMuteMode) 80 else 255
                mPinPaint.alpha = if (inMuteMode) 100 else 255
                mSecondHandPaint.alpha = if (inMuteMode) 80 else 255
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            val bgScale = width.toFloat() / mBackgroundBitmap.width.toFloat()
            mBackgroundBitmap = Bitmap.createScaledBitmap(
                mBackgroundBitmap,
                (mBackgroundBitmap.width * bgScale).toInt(),
                (mBackgroundBitmap.height * bgScale).toInt(), true
            )

            val aodScale = width.toFloat() / mAODBackgroundBitmap.width.toFloat()
            mAODBackgroundBitmap = Bitmap.createScaledBitmap(
                mAODBackgroundBitmap,
                (mAODBackgroundBitmap.width * aodScale).toInt(),
                (mAODBackgroundBitmap.height * aodScale).toInt(), true
            )

            val hourHandScale = width.toFloat() / mHourHandBitmap.width.toFloat()
            mHourHandBitmap = Bitmap.createScaledBitmap(
                mHourHandBitmap,
                (mHourHandBitmap.width * hourHandScale).toInt(),
                (mHourHandBitmap.height * hourHandScale).toInt(), true
            )

            val minuteHandScale = width.toFloat() / mMinuteHandBitmap.width.toFloat()
            mMinuteHandBitmap = Bitmap.createScaledBitmap(
                mMinuteHandBitmap,
                (mMinuteHandBitmap.width * minuteHandScale).toInt(),
                (mMinuteHandBitmap.height * minuteHandScale).toInt(), true
            )

            val secondHandScale = width.toFloat() / mSecondHandBitmap.width.toFloat()
            mSecondHandBitmap = Bitmap.createScaledBitmap(
                mSecondHandBitmap,
                (mSecondHandBitmap.width * secondHandScale).toInt(),
                (mSecondHandBitmap.height * secondHandScale).toInt(), true
            )

            val pinScale = width.toFloat() / mPinBitmap.width.toFloat()
            mPinBitmap = Bitmap.createScaledBitmap(
                mPinBitmap,
                (mPinBitmap.width * pinScale).toInt(),
                (mPinBitmap.height * pinScale).toInt(), true
            )

            val animScale = width.toFloat() / mAnimationBitmaps[0].width.toFloat()
            for(i in mAnimationBitmaps.indices) {
                mAnimationBitmaps[i] = Bitmap.createScaledBitmap(
                    mAnimationBitmaps[i],
                    (mAnimationBitmaps[i].width * animScale).toInt(),
                    (mAnimationBitmaps[i].height * animScale).toInt(), true
                )
            }
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP ->
                    mCurrAnimationIdx = 0
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            drawBackground(canvas)
            drawAnimation(canvas)
            drawWatchFace(canvas)
        }

        private fun drawBackground(canvas: Canvas) {

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK)
            } else if (mAmbient) {
                canvas.drawColor(Color.BLACK) // remove after aod transparency fixed
                canvas.drawBitmap(mAODBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            }
        }

        private fun drawAnimation(canvas: Canvas) {
            canvas.save()
            if(mAmbient) {
                canvas.drawBitmap(mAnimationBitmaps[0], 0f, 0f, mAnimationPaint)
            } else if(mLoopAnimationForever) {
                canvas.drawBitmap(mAnimationBitmaps[mCurrAnimationIdx], 0f, 0f, mAnimationPaint)
                mCurrAnimationIdx = (mCurrAnimationIdx + 1) % mAnimationBitmaps.size // loop forever
            } else {
                if(mCurrAnimationIdx < mAnimationBitmaps.size) {
                    canvas.drawBitmap(mAnimationBitmaps[mCurrAnimationIdx], 0f, 0f, mAnimationPaint)
                    mCurrAnimationIdx ++
                }
            }
            canvas.restore()
            invalidate()
        }

        private fun drawWatchFace(canvas: Canvas) {

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            // Constantly moving seconds
//            val seconds =
//                mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f
            val seconds = mCalendar.get(Calendar.SECOND)
            val secondsRotation = seconds * 6f

            val minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f

            val hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f
            val hoursRotation = mCalendar.get(Calendar.HOUR) * 30 + hourHandOffset

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save()

            canvas.rotate(hoursRotation, mCenterX, mCenterY)
            canvas.drawBitmap(mHourHandBitmap, 0f, 0f, mMinuteHandPaint)

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY)
            canvas.drawBitmap(mMinuteHandBitmap, 0f, 0f, mMinuteHandPaint)

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY)
                canvas.drawBitmap(mSecondHandBitmap, 0f, 0f, mSecondHandPaint)
            }

            /* Restore the canvas" original orientation. */
            canvas.restore()

            canvas.drawBitmap(mPinBitmap, 0f, 0f, mPinPaint)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren"t visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}