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

package android.nearby.cts;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.OffloadCapability;
import android.os.Build;
import android.os.Parcel;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class OffloadCapabilityTest {
    private static final long VERSION = 123456;

    @Test
    public void testDefault() {
        OffloadCapability offloadCapability = new OffloadCapability.Builder().build();

        assertThat(offloadCapability.isFastPairSupported()).isFalse();
        assertThat(offloadCapability.isNearbyShareSupported()).isFalse();
        assertThat(offloadCapability.getVersion()).isEqualTo(0);
    }

    @Test
    public void testBuilder() {
        OffloadCapability offloadCapability = new OffloadCapability.Builder()
                .setFastPairSupported(true)
                .setNearbyShareSupported(true)
                .setVersion(VERSION)
                .build();

        assertThat(offloadCapability.isFastPairSupported()).isTrue();
        assertThat(offloadCapability.isNearbyShareSupported()).isTrue();
        assertThat(offloadCapability.getVersion()).isEqualTo(VERSION);
    }

    @Test
    public void testWriteParcel() {
        OffloadCapability offloadCapability = new OffloadCapability.Builder()
                .setFastPairSupported(true)
                .setNearbyShareSupported(false)
                .setVersion(VERSION)
                .build();

        Parcel parcel = Parcel.obtain();
        offloadCapability.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        OffloadCapability capability = OffloadCapability.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(capability.isFastPairSupported()).isTrue();
        assertThat(capability.isNearbyShareSupported()).isFalse();
        assertThat(capability.getVersion()).isEqualTo(VERSION);
    }
}
