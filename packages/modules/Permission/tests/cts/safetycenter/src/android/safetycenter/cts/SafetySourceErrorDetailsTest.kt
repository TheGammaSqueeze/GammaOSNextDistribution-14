/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.safetycenter.cts

import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceErrorDetails
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceErrorDetails]. */
@RunWith(AndroidJUnit4::class)
class SafetySourceErrorDetailsTest {
    @Test
    fun getSafetyEvent_returnsSafetyEvent() {
        val errorDetails = SafetySourceErrorDetails(SAFETY_EVENT)

        assertThat(errorDetails.safetyEvent).isEqualTo(SAFETY_EVENT)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        val errorDetails = SafetySourceErrorDetails(SAFETY_EVENT)

        assertThat(errorDetails).recreatesEqual(SafetySourceErrorDetails.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetySourceErrorDetails.CREATOR
            )
            .addEqualityGroup(
                SafetySourceErrorDetails(SAFETY_EVENT),
                SafetySourceErrorDetails(
                    SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
                )
            )
            .addEqualityGroup(
                SafetySourceErrorDetails(
                    SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build()
                )
            )
            .test()
    }

    companion object {
        private val SAFETY_EVENT =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
    }
}
