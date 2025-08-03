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

package android.location.cts.none;

import static com.google.common.truth.Truth.assertThat;

import android.location.provider.ReverseGeocodeRequest;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class ReverseGeocodeRequestTest {

    @ApiTest(
            apis = {
                "android.location.provider.ReverseGeocodeRequest.Builder#Builder",
                "android.location.provider.ReverseGeocodeRequest.Builder#setCallingAttributionTag",
                "android.location.provider.ReverseGeocodeRequest.Builder#build",
                "android.location.Geocoder.ReverseGeocodeRequest#getLatitude",
                "android.location.Geocoder.ReverseGeocodeRequest#getLongitude",
                "android.location.Geocoder.ReverseGeocodeRequest#getMaxResults",
                "android.location.Geocoder.ReverseGeocodeRequest#getLocale",
                "android.location.Geocoder.ReverseGeocodeRequest#getCallingUid",
                "android.location.Geocoder.ReverseGeocodeRequest#getCallingPackage",
                "android.location.Geocoder.ReverseGeocodeRequest#getCallingAttributionTag",
            })
    @Test
    public void testConstructor() {
        ReverseGeocodeRequest p =
                new ReverseGeocodeRequest.Builder(1, 2, 3, Locale.CANADA, 4, "package")
                        .setCallingAttributionTag("attribution")
                        .build();

        assertThat(p.getLatitude()).isEqualTo(1);
        assertThat(p.getLongitude()).isEqualTo(2);
        assertThat(p.getMaxResults()).isEqualTo(3);
        assertThat(p.getLocale()).isEqualTo(Locale.CANADA);
        assertThat(p.getCallingUid()).isEqualTo(4);
        assertThat(p.getCallingPackage()).isEqualTo("package");
        assertThat(p.getCallingAttributionTag()).isEqualTo("attribution");
    }

    @ApiTest(apis = "android.location.Geocoder.ReverseGeocodeRequest#writeToParcel(CREATOR)")
    @Test
    public void testParcelRoundtrip() {
        ReverseGeocodeRequest p =
                new ReverseGeocodeRequest.Builder(1, 2, 3, Locale.CANADA, 4, "package")
                        .setCallingAttributionTag("attribution")
                        .build();

        Parcel parcel = Parcel.obtain();
        try {
            p.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            assertThat(ReverseGeocodeRequest.CREATOR.createFromParcel(parcel)).isEqualTo(p);
        } finally {
            parcel.recycle();
        }
    }
}
