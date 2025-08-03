/*
 * Copyright 2022 The Android Open Source Project
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

package android.app.cts

import android.media.NearbyDevice
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [android.media.NearbyDevice]. */
@RunWith(AndroidJUnit4::class)
class NearbyDeviceTest {

    @Test
    fun getMediaRoute2Id_returnsIdPassedIn() {
        val id = "testIdHere"
        val nearbyDevice = NearbyDevice(id, NearbyDevice.RANGE_UNKNOWN)

        assertThat(nearbyDevice.mediaRoute2Id).isEqualTo(id)
    }

    @Test
    fun getRangeZone_returnsRangeZonePassedIn() {
        val rangeZone = NearbyDevice.RANGE_WITHIN_REACH
        val nearbyDevice = NearbyDevice("id", rangeZone)

        assertThat(nearbyDevice.rangeZone).isEqualTo(rangeZone)
    }

    @Test
    fun writeToAndCreateFromParcel_resultHasCorrectIdAndRangeZone() {
        val id = "testIdHere"
        val rangeZone = NearbyDevice.RANGE_WITHIN_REACH
        val nearbyDevice = NearbyDevice(id, rangeZone)

        val parcel = Parcel.obtain()
        nearbyDevice.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val resultFromParcel = NearbyDevice.CREATOR.createFromParcel(parcel)

        assertThat(resultFromParcel.mediaRoute2Id).isEqualTo(id)
        assertThat(resultFromParcel.rangeZone).isEqualTo(rangeZone)
    }
}
