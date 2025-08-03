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

import android.safetycenter.SafetyCenterErrorDetails
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterErrorDetails]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterErrorDetailsTest {

    private val errorDetails1 = SafetyCenterErrorDetails("an error message")
    private val errorDetails2 = SafetyCenterErrorDetails("another error message")

    @Test
    fun getErrorMessage_returnsErrorMessage() {
        assertThat(errorDetails1.errorMessage).isEqualTo("an error message")
        assertThat(errorDetails2.errorMessage).isEqualTo("another error message")
    }

    @Test
    fun describeContents_returns0() {
        assertThat(errorDetails1.describeContents()).isEqualTo(0)
        assertThat(errorDetails2.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(errorDetails1).recreatesEqual(SafetyCenterErrorDetails.CREATOR)
        assertThat(errorDetails2).recreatesEqual(SafetyCenterErrorDetails.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterErrorDetails.CREATOR
            )
            .addEqualityGroup(errorDetails1, SafetyCenterErrorDetails("an error message"))
            .addEqualityGroup(errorDetails2, SafetyCenterErrorDetails("another error message"))
            .addEqualityGroup(
                SafetyCenterErrorDetails("a different error message"),
                SafetyCenterErrorDetails("a different error message")
            )
            .test()
    }
}
