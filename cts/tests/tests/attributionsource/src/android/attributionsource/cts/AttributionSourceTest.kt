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
import android.app.Instrumentation.ActivityResult
import android.content.AttributionSource
import android.content.Context
import android.content.Intent
import android.os.Process
import android.permission.flags.Flags
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.android.compatibility.common.util.ApiTest
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AttributionSourceTest {
    @get:Rule
    public val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    @Throws(Exception::class)
    fun testRemoteProcessActivityPidCheck() {
        val context: Context = ApplicationProvider.getApplicationContext()

        val activityIntent = Intent(context, AttributionSourceActivity::class.java)
        activityIntent.putExtra(ATTRIBUTION_SOURCE_KEY, context.getAttributionSource())

        // Launch activity from adjacent thread (cannot be launched from main thread)
        val thread = LaunchActivityThread(activityIntent)

        thread.start()
        thread.join()

        assertEquals("Test activity did not return RESULT_SECURITY_EXCEPTION",
                AttributionSourceActivity.RESULT_SECURITY_EXCEPTION, thread.getResultCode())
    }

    @Test
    @ApiTest(apis = ["android.content.AttributionSource.Builder#setNextAttributionSource"])
    @Throws(Exception::class)
    @RequiresFlagsEnabled(Flags.FLAG_SET_NEXT_ATTRIBUTION_SOURCE)
    fun testSetNextAttributionSourceNonNull() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val thisAttributionSource = context.getAttributionSource()
        val builder = AttributionSource.Builder(Process.myUid())
        builder.setNextAttributionSource(thisAttributionSource)
        builder.build()
    }

    @Test
    @ApiTest(apis = ["android.content.AttributionSource.Builder#setNextAttributionSource"])
    @Throws(Exception::class)
    @RequiresFlagsEnabled(Flags.FLAG_ATTRIBUTION_SOURCE_CONSTRUCTOR)
    fun testSetNextAttributionSourceWithNull() {
        assertFailsWith(Exception::class, "setNextAttributionSource should throw on null") {
            val nullBuilder = AttributionSource.Builder(Process.myUid())
            AttributionSourceJavaWrapper.setNullNextAttributionSource(nullBuilder)
        }
    }

    @Test
    @ApiTest(apis = ["android.content.AttributionSource#getDeviceId"])
    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED)
    fun testDefaultDeviceId() {
        val attributionSource = AttributionSource.Builder(Process.myUid()).build()
        assertEquals(Context.DEVICE_ID_DEFAULT, attributionSource.deviceId)
    }

    @Test
    @ApiTest(apis = ["android.content.AttributionSource#getDeviceId"])
    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED)
    fun testVirtualDeviceId() {
        // random integer
        val deviceId = 100
        val attributionSource = AttributionSource.Builder(Process.myUid())
            .setDeviceId(deviceId)
            .build()
        assertEquals(deviceId, attributionSource.deviceId)
    }

    companion object {
        const val ATTRIBUTION_SOURCE_KEY = "attributionSource"

        private class LaunchActivityThread(activityIntent: Intent) : Thread() {
            private val mActivityIntent = activityIntent
            private var mResultCode: Int = Activity.RESULT_OK

            override fun run() {
                val scenario: ActivityScenario<AttributionSourceActivity> =
                        ActivityScenario.launchActivityForResult(mActivityIntent)
                val result: ActivityResult = scenario.getResult()
                mResultCode = result.getResultCode()
            }

            fun getResultCode(): Int {
                return mResultCode
            }
        }
    }
}
