/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package android.app.notification.extenders.cts.current

import android.app.Notification
import android.app.Notification.WearableExtender
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearableExtenderApiCurrentTest {

    private lateinit var mContext: Context

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun wearableBackgroundBlockDisabled_wearableBackgroundSet_valueIsNull() {
        // The API change in this test is not present in API levels beforehand.
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 35)

        val extender = WearableExtender()
        val bitmap = Bitmap.createBitmap(200, 200, Config.ARGB_8888)
        extender.setBackground(bitmap)
        val notif: Notification =
                Notification.Builder(mContext, "test id")
                    .setSmallIcon(1)
                    .setContentTitle("test_title")
                    .extend(extender)
                    .build()

        val result = WearableExtender(notif)
        val background = result.getBackground()
        assertThat(background).isNull()
    }
}
