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

package android.car.navigation;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class CarNavigationInstrumentClusterTest {

    @Test
    public void testCopyConstructor_constructsAsExpected() {
        CarNavigationInstrumentCluster carNavigationInstrumentCluster =
                CarNavigationInstrumentCluster.createCustomImageCluster(/* minIntervalMs= */ 100,
                        /* imageWidth= */ 800, /* imageHeight= */ 480,
                        /* imageColorDepthBits= */ 32);

        CarNavigationInstrumentCluster copy = new CarNavigationInstrumentCluster(
                carNavigationInstrumentCluster);

        assertThat(copy.getExtra().keySet()).isEmpty();
        assertThat(copy.getImageColorDepthBits()).isEqualTo(32);
        assertThat(copy.getImageHeight()).isEqualTo(480);
        assertThat(copy.getImageWidth()).isEqualTo(800);
        assertThat(copy.getMinIntervalMillis()).isEqualTo(100);
    }

    @Test
    public void testNewArray() {
        CarNavigationInstrumentCluster[] carNavigationInstrumentClusters =
                CarNavigationInstrumentCluster.CREATOR.newArray(10);

        assertThat(carNavigationInstrumentClusters).hasLength(10);
    }

    @Test
    public void testCreateFromParcel() {
        CarNavigationInstrumentCluster carNavigationInstrumentCluster =
                CarNavigationInstrumentCluster.createCustomImageCluster(/* minIntervalMs= */ 100,
                        /* imageWidth= */ 800, /* imageHeight= */ 480,
                        /* imageColorDepthBits= */ 32);
        Parcel parcel = Parcel.obtain();
        carNavigationInstrumentCluster.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        CarNavigationInstrumentCluster navigationClusterInfoFromParcel =
                CarNavigationInstrumentCluster.CREATOR.createFromParcel(parcel);

        assertThat(navigationClusterInfoFromParcel.getExtra().keySet()).isEmpty();
        assertThat(navigationClusterInfoFromParcel.getImageColorDepthBits()).isEqualTo(32);
        assertThat(navigationClusterInfoFromParcel.getImageHeight()).isEqualTo(480);
        assertThat(navigationClusterInfoFromParcel.getImageWidth()).isEqualTo(800);
        assertThat(navigationClusterInfoFromParcel.getMinIntervalMillis()).isEqualTo(100);
    }
}
