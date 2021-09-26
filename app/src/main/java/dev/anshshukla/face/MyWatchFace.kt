package dev.anshshukla.face

import android.content.*
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.*
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.rendering.ComplicationDrawable
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.util.SparseArray
import android.view.SurfaceHolder
import dev.anshshukla.face.ComplicationConfigActivity.ComplicationLocation
import java.lang.ref.WeakReference
import java.util.*


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
    private val logTag = "MyWatchFace"

    companion object {
        private const val mTopComplicationId = 0
        private const val mLeftComplicationId = 1
        private const val mRightComplicationId = 2

        private val mComplicationIds =
            intArrayOf(mTopComplicationId, mLeftComplicationId, mRightComplicationId)
        private val mComplicationSupportedTypes = arrayOf(
            // top
            intArrayOf(
                ComplicationData.TYPE_RANGED_VALUE,
                ComplicationData.TYPE_EMPTY,
            ),
            // left
            intArrayOf(
                ComplicationData.TYPE_SHORT_TEXT,
                ComplicationData.TYPE_RANGED_VALUE,
                ComplicationData.TYPE_ICON,
                ComplicationData.TYPE_EMPTY,
            ),
            // right
            intArrayOf(
                ComplicationData.TYPE_SHORT_TEXT,
                ComplicationData.TYPE_RANGED_VALUE,
                ComplicationData.TYPE_ICON,
                ComplicationData.TYPE_EMPTY,
            )
        )

        /**
         * Used by [ComplicationConfigActivity] to retrieve id for complication locations
         * and to check if complication location is supported.
         */
        fun getComplicationId(
            complicationLocation: ComplicationLocation
        ): Int {
            return when (complicationLocation) {
                ComplicationLocation.TOP -> mTopComplicationId
                ComplicationLocation.LEFT -> mLeftComplicationId
                ComplicationLocation.RIGHT -> mRightComplicationId
            }
        }

        /**
         * Used by [ComplicationConfigActivity] to retrieve all complication ids
         */
        fun getComplicationIds(): IntArray {
            return mComplicationIds
        }

        /**
         * Used by [ComplicationConfigActivity] to retrieve complication types supported by location
         */
        fun getSupportedComplicationTypes(
            complicationLocation: ComplicationLocation
        ): IntArray {
            return when (complicationLocation) {
                ComplicationLocation.TOP -> mComplicationSupportedTypes[mTopComplicationId]
                ComplicationLocation.LEFT -> mComplicationSupportedTypes[mLeftComplicationId]
                ComplicationLocation.RIGHT -> mComplicationSupportedTypes[mRightComplicationId]
            }
        }
    }

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

        private lateinit var mWatchFaceDrawManager: WatchFaceDrawManager
        private lateinit var mAnimationDrawManager: AnimationDrawManager

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        override fun onComplicationDataUpdate(
            complicationId: Int, complicationData: ComplicationData?
        ) {
            Log.d(logTag, "onComplicationDataUpdate() id: $complicationId")

            // Adds/updates active complication data in the array.
            mActiveComplicationDataSparseArray.put(complicationId, complicationData)

            // Updates correct ComplicationDrawable with updated data.
            mComplicationDrawableSparseArray.get(complicationId).setComplicationData(complicationData)
            invalidate()
        }


        /* Maps active complication ids to the data for that complication. Note: Data will only be
         * present if the user has chosen a provider via the settings activity for the watch face.
         */
        private lateinit var mActiveComplicationDataSparseArray: SparseArray<ComplicationData>

        /* Maps complication ids to corresponding ComplicationDrawable that renders the
         * the complication data on the watch face.
         */
        private lateinit var mComplicationDrawableSparseArray: SparseArray<ComplicationDrawable>

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            Log.d(logTag, "onCreate()")
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            val dimensions = Dimensions((mCenterX * 2).toInt(), (mCenterX * 2).toInt())
            mAnimationDrawManager = AnimationDrawManager(resources, packageName, dimensions)
            mWatchFaceDrawManager = WatchFaceDrawManager(mCalendar, resources, dimensions)

            initializeComplications()
        }

        private fun initializeComplications() {
            Log.d(logTag, "initializeComplications()")
            mActiveComplicationDataSparseArray = SparseArray(mComplicationIds.size)
            val topComplicationDrawable =
                resources.getDrawable(
                    R.drawable.custom_complication_styles,
                    theme
                ) as ComplicationDrawable
            topComplicationDrawable.setContext(applicationContext)

            val leftComplicationDrawable =
                resources.getDrawable(
                    R.drawable.custom_complication_styles,
                    theme
                ) as ComplicationDrawable
            leftComplicationDrawable.setContext(applicationContext)

            val rightComplicationDrawable =
                resources.getDrawable(
                    R.drawable.custom_complication_styles,
                    theme
                ) as ComplicationDrawable
            rightComplicationDrawable.setContext(applicationContext)

            mComplicationDrawableSparseArray = SparseArray(mComplicationIds.size)
            mComplicationDrawableSparseArray.put(mTopComplicationId, topComplicationDrawable)
            mComplicationDrawableSparseArray.put(mLeftComplicationId, leftComplicationDrawable)
            mComplicationDrawableSparseArray.put(mRightComplicationId, rightComplicationDrawable)

            setActiveComplications(*mComplicationIds)
        }

        override fun onDestroy() {
            Log.d(logTag, "onDestroy()")
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
            // Updates complications to properly render in ambient mode based on the
            // screen's capabilities.
            // Updates complications to properly render in ambient mode based on the
            // screen's capabilities.
            var complicationDrawable: ComplicationDrawable?

            for (complicationId in mComplicationIds) {
                complicationDrawable = mComplicationDrawableSparseArray.get(complicationId)
                if (complicationDrawable != null) {
                    complicationDrawable.setLowBitAmbient(mLowBitAmbient)
                    complicationDrawable.setBurnInProtection(mBurnInProtection)
                }
            }
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            mWatchFaceDrawManager.updateWatchHandStyle(mAmbient, mMuteMode)

            for (complicationId in mComplicationIds) {
                mComplicationDrawableSparseArray.get(complicationId).setInAmbientMode(mAmbient)
            }

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mWatchFaceDrawManager.updateWatchHandStyle(mAmbient, mMuteMode)
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.v(logTag, "onSurfaceChanged()")
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            val sizeOfRoundComplications = width / 4
            val horizontalOffset: Float = (mCenterX - sizeOfRoundComplications) / 2
            val verticalOffset: Float = mCenterX - sizeOfRoundComplications / 2
            val leftBounds =  // Left, Top, Right, Bottom
                Rect(
                    horizontalOffset.toInt(),
                    verticalOffset.toInt(),
                    (horizontalOffset + sizeOfRoundComplications).toInt(),
                    (verticalOffset + sizeOfRoundComplications).toInt()
                )
            mComplicationDrawableSparseArray.get(mLeftComplicationId).bounds = leftBounds

            val rightBounds =  // Left, Top, Right, Bottom
                Rect(
                    (mCenterX + horizontalOffset).toInt(),
                    verticalOffset.toInt(),
                    (mCenterX + horizontalOffset + sizeOfRoundComplications).toInt(),
                    (verticalOffset + sizeOfRoundComplications).toInt()
                )

            mComplicationDrawableSparseArray.get(mRightComplicationId).bounds = rightBounds

            val dimensions = Dimensions(width, height)
            mWatchFaceDrawManager.onDimensionsChange(dimensions)
            mAnimationDrawManager.onDimensionsChange(dimensions)
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            Log.d(logTag, "onTapCommand(tapType: $tapType, x: $x, y: $y, eventTime: $eventTime")
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP -> mAnimationDrawManager.toggleAnimation()
            }
            invalidate()
        }

        /*
         * Determines if tap inside a complication area or returns -1.
         */
        private fun getTappedComplicationId(x: Int, y: Int): Int {
            var complicationData: ComplicationData?
            var complicationDrawable: ComplicationDrawable
            val currentTimeMillis = System.currentTimeMillis()
            for (complicationId in mComplicationIds) {
                complicationData = mActiveComplicationDataSparseArray.get(complicationId)
                if (complicationData != null
                    && complicationData.isActive(currentTimeMillis)
                    && complicationData.type != ComplicationData.TYPE_NOT_CONFIGURED
                    && complicationData.type != ComplicationData.TYPE_EMPTY
                ) {
                    complicationDrawable = mComplicationDrawableSparseArray.get(complicationId)
                    val complicationBoundingRect = complicationDrawable.bounds
                    if (complicationBoundingRect.width() > 0) {
                        if (complicationBoundingRect.contains(x, y)) {
                            return complicationId
                        }
                    } else {
                        Log.e(logTag, "Not a recognized complication id.")
                    }
                }
            }
            return -1
        }

        // Fires PendingIntent associated with complication (if it has one).
        private fun onComplicationTap(complicationId: Int) {
            // TODO: Step 5, onComplicationTap()
            Log.d(logTag, "onComplicationTap()")
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            canvas.drawColor(Color.BLACK)

            if (mAnimationDrawManager.draw(canvas, mAmbient)) {
                invalidate()
            }

            drawComplications(canvas, now)

            mWatchFaceDrawManager.draw(canvas, mAmbient)
        }

        private fun drawComplications(canvas: Canvas, currentTimeMillis: Long) {
            // TODO: Step 4, drawComplications()
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