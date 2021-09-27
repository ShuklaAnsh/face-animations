package dev.anshshukla.face

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.wearable.complications.ComplicationProviderInfo
import android.support.wearable.complications.ProviderInfoRetriever
import android.support.wearable.complications.ProviderInfoRetriever.OnProviderInfoReceivedCallback
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import dev.anshshukla.face.databinding.ActivityComplicationConfigBinding
import java.util.concurrent.Executors
import android.support.wearable.complications.ComplicationHelperActivity
import android.support.wearable.complications.ProviderChooserIntent
import androidx.core.content.res.ResourcesCompat

class ComplicationConfigActivity : Activity(), View.OnClickListener {
    private val logTag = "ComplicationConfigActivity"

    companion object {
        const val mComplicationConfigRequestCode = 1001
    }

    /**
     * Used by the watch face ([MyWatchFace]) to let this configuration Activity know which
     * complication locations are supported, their ids, and supported complication data types.
     */
    enum class ComplicationLocation {
        TOP,
        LEFT,
        RIGHT
    }

    private val mTopComplicationId = -1
    private var mLeftComplicationId = -1
    private var mRightComplicationId = -1

    // Selected complication id by user.
    private var mSelectedComplicationId = -1

    // ComponentName used to identify a specific service that renders the watch face.
    private lateinit var mWatchFaceComponentName: ComponentName

    // Required to retrieve complication data from watch face for preview.
    private lateinit var mProviderInfoRetriever: ProviderInfoRetriever

    private lateinit var mLeftComplicationBackground: ImageView
    private lateinit var mRightComplicationBackground: ImageView

    private lateinit var mLeftComplication: ImageButton
    private lateinit var mRightComplication: ImageButton

    private lateinit var mDefaultAddComplicationDrawable: Drawable

    private lateinit var binding: ActivityComplicationConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(logTag, "onCreate()")
        super.onCreate(savedInstanceState)

        binding = ActivityComplicationConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mDefaultAddComplicationDrawable = ResourcesCompat.getDrawable(resources, R.drawable.add_complication, theme)!!

        mLeftComplicationId =
            MyWatchFace.getComplicationId(ComplicationLocation.LEFT)
        mRightComplicationId =
            MyWatchFace.getComplicationId(ComplicationLocation.RIGHT)

        mWatchFaceComponentName = ComponentName(
            applicationContext,
            MyWatchFace::class.java
        )

        // Sets up left complication preview.
        mLeftComplicationBackground = findViewById<ImageButton>(R.id.left_complication_background)
        mLeftComplication = findViewById<ImageButton>(R.id.left_complication)
        mLeftComplication.setOnClickListener(this)

        // Sets default as "Add Complication" icon.
        mLeftComplication.setImageDrawable(mDefaultAddComplicationDrawable)
        mLeftComplicationBackground.visibility = View.INVISIBLE

        // Sets up right complication preview.
        mRightComplicationBackground = findViewById<ImageView>(R.id.right_complication_background)
        mRightComplication = findViewById<ImageButton>(R.id.right_complication)
        mRightComplication.setOnClickListener(this)

        // Sets default as "Add Complication" icon.
        mRightComplication.setImageDrawable(mDefaultAddComplicationDrawable)
        mRightComplicationBackground.visibility = View.INVISIBLE

        mProviderInfoRetriever =
            ProviderInfoRetriever(applicationContext, Executors.newCachedThreadPool())
        mProviderInfoRetriever.init()

        retrieveInitialComplicationsData()

    }

    override fun onDestroy() {
        Log.d(logTag, "onDestroy()")
        super.onDestroy()
        mProviderInfoRetriever.release()
    }

    private fun retrieveInitialComplicationsData() {
        Log.d(logTag, "retrieveInitialComplicationsData()")
        val complicationIds: IntArray = MyWatchFace.getComplicationIds()

        mProviderInfoRetriever.retrieveProviderInfo(
            object : OnProviderInfoReceivedCallback() {
                override fun onProviderInfoReceived(
                    watchFaceComplicationId: Int,
                    complicationProviderInfo: ComplicationProviderInfo?
                ) {
                    Log.d(logTag, "\n\nonProviderInfoReceived: $complicationProviderInfo")
                    updateComplicationViews(watchFaceComplicationId, complicationProviderInfo)
                }
            },
            mWatchFaceComponentName,
            *complicationIds
        )

    }

    override fun onClick(view: View) {
        Log.d(logTag, "onClick(view: $view.id)")
        if (view == mLeftComplication) {
            Log.d(logTag, "Left Complication click()")
            launchComplicationHelperActivity(ComplicationLocation.LEFT)
        } else if (view == mRightComplication) {
            Log.d(logTag, "Right Complication click()")
            launchComplicationHelperActivity(ComplicationLocation.RIGHT)
        }
    }

    // Verifies the watch face supports the complication location, then launches the helper
    // class, so user can choose their complication data provider.
    private fun launchComplicationHelperActivity(complicationLocation: ComplicationLocation) {
        Log.d(logTag, "launchComplicationHelperActivity($complicationLocation)")
        mSelectedComplicationId =
            MyWatchFace.getComplicationId(complicationLocation)

        if (mSelectedComplicationId >= 0) {
            val supportedTypes: IntArray =
                MyWatchFace.getSupportedComplicationTypes(
                    complicationLocation
                )
            startActivityForResult(
                ComplicationHelperActivity.createProviderChooserHelperIntent(
                    applicationContext,
                    mWatchFaceComponentName,
                    mSelectedComplicationId,
                    *supportedTypes
                ),
                mComplicationConfigRequestCode
            )
        } else {
            Log.d(logTag, "Complication not supported by watch face.")
        }

    }
    fun updateComplicationViews(
        watchFaceComplicationId: Int, complicationProviderInfo: ComplicationProviderInfo?
    ) {
        Log.d(logTag, "updateComplicationViews(): id: $watchFaceComplicationId")
        Log.d(logTag, "\tinfo: $complicationProviderInfo")
        if (watchFaceComplicationId == mLeftComplicationId) {
            if (complicationProviderInfo != null) {
                mLeftComplication.setImageIcon(complicationProviderInfo.providerIcon)
                mLeftComplicationBackground.visibility = View.VISIBLE
            } else {
                mLeftComplication.setImageDrawable(mDefaultAddComplicationDrawable)
                mLeftComplicationBackground.visibility = View.INVISIBLE
            }
        } else if (watchFaceComplicationId == mRightComplicationId) {
            if (complicationProviderInfo != null) {
                mRightComplication.setImageIcon(complicationProviderInfo.providerIcon)
                mRightComplicationBackground.visibility = View.VISIBLE
            } else {
                mRightComplication.setImageDrawable(mDefaultAddComplicationDrawable)
                mRightComplicationBackground.visibility = View.INVISIBLE
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(logTag, "onActivityResult(requestCode: $requestCode, resultCode: $requestCode, data: $data)")
        if (requestCode == mComplicationConfigRequestCode && resultCode == RESULT_OK) {

            // Retrieves information for selected Complication provider.
            val complicationProviderInfo: ComplicationProviderInfo? =
                data?.getParcelableExtra(ProviderChooserIntent.EXTRA_PROVIDER_INFO)
            Log.d(logTag, "Provider: $complicationProviderInfo")
            if (mSelectedComplicationId >= 0) {
                updateComplicationViews(mSelectedComplicationId, complicationProviderInfo)
            }
        }

    }
}