/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.location.cts.none;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.location.LocationRequest;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.collect.Range;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LocationRequestTest {

    @Test
    public void testBuild_Defaults() {
        LocationRequest request = new LocationRequest.Builder(0).build();
        assertThat(request.getIntervalMillis()).isEqualTo(0);
        assertThat(request.getQuality()).isEqualTo(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY);
        assertThat(request.getMinUpdateIntervalMillis()).isEqualTo(0);
        assertThat(request.getDurationMillis()).isEqualTo(Long.MAX_VALUE);
        assertThat(request.getMaxUpdates()).isEqualTo(Integer.MAX_VALUE);
        assertThat(request.getMinUpdateDistanceMeters()).isEqualTo(0f);
        assertThat(request.isHiddenFromAppOps()).isEqualTo(false);
        assertThat(request.isAdasGnssBypass()).isEqualTo(false);
        assertThat(request.isLocationSettingsIgnored()).isEqualTo(false);
        assertThat(request.isLowPower()).isEqualTo(false);
    }

    @Test
    public void testBuild_Explicit() {
        LocationRequest request = new LocationRequest.Builder(5000)
                .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(4000)
                .setDurationMillis(6000)
                .setMaxUpdates(7000)
                .setMinUpdateDistanceMeters(8000f)
                .setHiddenFromAppOps(true)
                .setAdasGnssBypass(true)
                .setLocationSettingsIgnored(true)
                .setLowPower(true)
                .build();
        assertThat(request.getIntervalMillis()).isEqualTo(5000);
        assertThat(request.getQuality()).isEqualTo(LocationRequest.QUALITY_HIGH_ACCURACY);
        assertThat(request.getMinUpdateIntervalMillis()).isEqualTo(4000);
        assertThat(request.getDurationMillis()).isEqualTo(6000);
        assertThat(request.getMaxUpdates()).isEqualTo(7000);
        assertThat(request.getMinUpdateDistanceMeters()).isEqualTo(8000f);
        assertThat(request.isHiddenFromAppOps()).isEqualTo(true);
        assertThat(request.isAdasGnssBypass()).isEqualTo(true);
        assertThat(request.isLocationSettingsIgnored()).isEqualTo(true);
        assertThat(request.isLowPower()).isEqualTo(true);
    }

    @Test
    public void testBuild_Copy() {
        LocationRequest original = new LocationRequest.Builder(5000)
                .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(4000)
                .setDurationMillis(6000)
                .setMaxUpdates(7000)
                .setMinUpdateDistanceMeters(8000f)
                .setHiddenFromAppOps(true)
                .setAdasGnssBypass(true)
                .setLocationSettingsIgnored(true)
                .setLowPower(true)
                .build();
        LocationRequest copy = new LocationRequest.Builder(original).build();
        assertThat(copy.getIntervalMillis()).isEqualTo(5000);
        assertThat(copy.getQuality()).isEqualTo(LocationRequest.QUALITY_HIGH_ACCURACY);
        assertThat(copy.getMinUpdateIntervalMillis()).isEqualTo(4000);
        assertThat(copy.getDurationMillis()).isEqualTo(6000);
        assertThat(copy.getMaxUpdates()).isEqualTo(7000);
        assertThat(copy.getMinUpdateDistanceMeters()).isEqualTo(8000f);
        assertThat(copy.isHiddenFromAppOps()).isEqualTo(true);
        assertThat(copy.isAdasGnssBypass()).isEqualTo(true);
        assertThat(copy.isLocationSettingsIgnored()).isEqualTo(true);
        assertThat(copy.isLowPower()).isEqualTo(true);
        assertThat(copy).isEqualTo(original);
    }

    @Test
    public void testBuild_ImplicitMinUpdateInterval() {
        LocationRequest.Builder builder = new LocationRequest.Builder(5000);
        assertThat(builder.build().getMinUpdateIntervalMillis()).isIn(Range.closed(0L, 5000L));

        builder.setIntervalMillis(6000);
        assertThat(builder.build().getMinUpdateIntervalMillis()).isIn(Range.closed(0L, 6000L));
    }

    @Test
    public void testBuild_ClearMinUpdateInterval() {
        LocationRequest request = new LocationRequest.Builder(5000)
                .setMinUpdateIntervalMillis(4000)
                .clearMinUpdateIntervalMillis()
                .build();
        assertThat(request.getMinUpdateIntervalMillis()).isIn(Range.closed(0L, 5000L));
    }

    @Test
    public void testBuild_BadMinUpdateInterval() {
        LocationRequest request = new LocationRequest.Builder(5000)
                .setMinUpdateIntervalMillis(6000)
                .build();
        assertThat(request.getMinUpdateIntervalMillis()).isEqualTo(5000);
    }

    @Test
    public void testBuild_IllegalInterval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocationRequest.Builder(-1));
    }

    @Test
    public void testBuild_IllegalQuality() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocationRequest.Builder(0).setQuality(-999));
    }

    @Test
    public void testBuild_IllegalMinUpdateInterval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocationRequest.Builder(0).setMinUpdateIntervalMillis(-1));
    }

    @Test
    public void testBuild_IllegalDuration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocationRequest.Builder(0).setDurationMillis(0));
    }

    @Test
    public void testBuild_IllegalMaxUpdates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocationRequest.Builder(0).setMaxUpdates(0));
    }

    @Test
    public void testBuild_IllegalMinUpdateDistance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocationRequest.Builder(0).setMinUpdateDistanceMeters(-1));
    }

    @Test
    public void testDescribeContents() {
        LocationRequest request = new LocationRequest.Builder(0).build();
        assertThat(request.describeContents()).isEqualTo(0);
    }

    @Test
    public void testParcelRoundtrip() {
        LocationRequest request = new LocationRequest.Builder(5000)
                .setQuality(LocationRequest.QUALITY_LOW_POWER)
                .setMinUpdateIntervalMillis(4000)
                .setDurationMillis(6000)
                .setMaxUpdates(7000)
                .setMinUpdateDistanceMeters(8000f)
                .setHiddenFromAppOps(true)
                .setAdasGnssBypass(true)
                .setLocationSettingsIgnored(true)
                .setLowPower(true)
                .build();

        Parcel parcel = Parcel.obtain();
        try {
            request.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            assertThat(LocationRequest.CREATOR.createFromParcel(parcel)).isEqualTo(request);
        } finally {
            parcel.recycle();
        }
    }
}
