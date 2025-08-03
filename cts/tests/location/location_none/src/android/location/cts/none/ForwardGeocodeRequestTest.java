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

import android.location.provider.ForwardGeocodeRequest;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class ForwardGeocodeRequestTest {

    @ApiTest(
            apis = {
                "android.location.provider.ForwardGeocodeRequest.Builder#Builder",
                "android.location.provider.ForwardGeocodeRequest.Builder#setCallingAttributionTag",
                "android.location.provider.ForwardGeocodeRequest.Builder#build",
                "android.location.Geocoder.ForwardGeocodeRequest#getLocationName",
                "android.location.Geocoder.ForwardGeocodeRequest#getLowerLeftLatitude",
                "android.location.Geocoder.ForwardGeocodeRequest#getLowerLeftLongitude",
                "android.location.Geocoder.ForwardGeocodeRequest#getUpperRightLatitude",
                "android.location.Geocoder.ForwardGeocodeRequest#getUpperRightLongitude",
                "android.location.Geocoder.ForwardGeocodeRequest#getMaxResults",
                "android.location.Geocoder.ForwardGeocodeRequest#getLocale",
                "android.location.Geocoder.ForwardGeocodeRequest#getCallingUid",
                "android.location.Geocoder.ForwardGeocodeRequest#getCallingPackage",
                "android.location.Geocoder.ForwardGeocodeRequest#getCallingAttributionTag",
            })
    @Test
    public void testConstructor() {
        ForwardGeocodeRequest p =
                new ForwardGeocodeRequest.Builder(
                                "location", 1, 2, 3, 4, 5, Locale.CANADA, 6, "package")
                        .setCallingAttributionTag("attribution")
                        .build();

        assertThat(p.getLocationName()).isEqualTo("location");
        assertThat(p.getLowerLeftLatitude()).isEqualTo(1);
        assertThat(p.getLowerLeftLongitude()).isEqualTo(2);
        assertThat(p.getUpperRightLatitude()).isEqualTo(3);
        assertThat(p.getUpperRightLongitude()).isEqualTo(4);
        assertThat(p.getMaxResults()).isEqualTo(5);
        assertThat(p.getLocale()).isEqualTo(Locale.CANADA);
        assertThat(p.getCallingUid()).isEqualTo(6);
        assertThat(p.getCallingPackage()).isEqualTo("package");
        assertThat(p.getCallingAttributionTag()).isEqualTo("attribution");
    }

    @ApiTest(apis = "android.location.Geocoder.ForwardGeocodeRequest#writeToParcel(CREATOR)")
    @Test
    public void testParcelRoundtrip() {
        ForwardGeocodeRequest p =
                new ForwardGeocodeRequest.Builder(
                                "location", 1, 2, 3, 4, 5, Locale.CANADA, 6, "package")
                        .setCallingAttributionTag("attribution")
                        .build();

        Parcel parcel = Parcel.obtain();
        try {
            p.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            assertThat(ForwardGeocodeRequest.CREATOR.createFromParcel(parcel)).isEqualTo(p);
        } finally {
            parcel.recycle();
        }
    }
}
