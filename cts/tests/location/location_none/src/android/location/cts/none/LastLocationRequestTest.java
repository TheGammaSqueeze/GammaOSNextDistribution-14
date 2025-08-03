/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.location.LastLocationRequest;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LastLocationRequestTest {

    @Test
    public void testBuild_Defaults() {
        LastLocationRequest request = new LastLocationRequest.Builder().build();
        assertThat(request.isHiddenFromAppOps()).isEqualTo(false);
        assertThat(request.isAdasGnssBypass()).isEqualTo(false);
        assertThat(request.isLocationSettingsIgnored()).isEqualTo(false);
    }

    @Test
    public void testBuild_Explicit() {
        LastLocationRequest request = new LastLocationRequest.Builder()
                .setHiddenFromAppOps(true)
                .setAdasGnssBypass(true)
                .setLocationSettingsIgnored(true)
                .build();
        assertThat(request.isHiddenFromAppOps()).isEqualTo(true);
        assertThat(request.isAdasGnssBypass()).isEqualTo(true);
        assertThat(request.isLocationSettingsIgnored()).isEqualTo(true);
    }

    @Test
    public void testBuild_Copy() {
        LastLocationRequest original = new LastLocationRequest.Builder()
                .setHiddenFromAppOps(true)
                .setAdasGnssBypass(true)
                .setLocationSettingsIgnored(true)
                .build();
        LastLocationRequest copy = new LastLocationRequest.Builder(original).build();
        assertThat(copy.isHiddenFromAppOps()).isEqualTo(true);
        assertThat(copy.isAdasGnssBypass()).isEqualTo(true);
        assertThat(copy.isLocationSettingsIgnored()).isEqualTo(true);
        assertThat(copy).isEqualTo(original);
    }

    @Test
    public void testDescribeContents() {
        LastLocationRequest request = new LastLocationRequest.Builder().build();
        assertThat(request.describeContents()).isEqualTo(0);
    }

    @Test
    public void testParcelRoundtrip() {
        LastLocationRequest request = new LastLocationRequest.Builder()
                .setHiddenFromAppOps(true)
                .setAdasGnssBypass(true)
                .setLocationSettingsIgnored(true)
                .build();

        Parcel parcel = Parcel.obtain();
        try {
            request.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            assertThat(LastLocationRequest.CREATOR.createFromParcel(parcel)).isEqualTo(request);
        } finally {
            parcel.recycle();
        }
    }
}
