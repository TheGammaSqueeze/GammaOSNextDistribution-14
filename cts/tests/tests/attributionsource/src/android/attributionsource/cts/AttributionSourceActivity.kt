
/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.attributionsource.cts

import android.app.Activity
import android.content.AttributionSource
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/**
 * Activity which runs in the same process as the test and receives an AttributionSource.
 */
public class AttributionSourceActivity : Activity() {
    private var mHandlerThread: HandlerThread? = null

    protected override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate called.")
        super.onCreate(savedInstanceState)
        val intent = getIntent()
        Log.i(TAG, "Read AttributionSource from PID " + Binder.getCallingPid())
        mHandlerThread = HandlerThread("AttributionSourceActivityHandlerThread")
        mHandlerThread!!.start()
        val handler = Handler(mHandlerThread!!.getLooper())
        readAttributionSource(this, intent, handler)
    }

    protected override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        Log.i(TAG, "onActivityResult called.")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            Log.i(TAG, "Received result " + resultCode + ", finishing activity")
            setResult(resultCode)
            finish()
        }
    }

    protected override fun onDestroy() {
        Log.i(TAG, "onDestroy called.")
        super.onDestroy()
        if (mHandlerThread != null) {
            mHandlerThread!!.quitSafely()
            mHandlerThread = null
        }
    }

    companion object {
        public final val RESULT_SECURITY_EXCEPTION: Int = -2
        private final val TAG: String = "AttributionSourceActivity"
        private final val REQUEST_CODE: Int = 0

        public fun readAttributionSource(activity: Activity, intent: Intent, handler: Handler) {
            handler.postDelayed(object : Runnable {
                override fun run() {
                    try {
                        val attributionSource = intent.getParcelableExtra(
                                AttributionSourceTest.ATTRIBUTION_SOURCE_KEY,
                                AttributionSource::class.java)
                        Log.i(TAG, "Received AttributionSource with PID " +
                                attributionSource!!.getPid() + ", current process PID " +
                                Binder.getCallingPid())
                        activity.setResult(Activity.RESULT_OK)
                    } catch (e: SecurityException) {
                        Log.i(TAG, "Received SecurityException from AttributionSource")
                        activity.setResult(AttributionSourceActivity.RESULT_SECURITY_EXCEPTION)
                    }
                    activity.finish()
                }
            }, 1000)
        }
    }
}
