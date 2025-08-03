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

package android.adservices.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adid.AdId;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit tests for {@link UpdateAdIdRequest} */
public final class UpdateAdIdRequestTest extends AdServicesUnitTestCase {

    @Test
    public void testWriteToParcel() {
        UpdateAdIdRequest request =
                new UpdateAdIdRequest.Builder(AdId.ZERO_OUT)
                        .setLimitAdTrackingEnabled(false)
                        .build();
        Parcel parcel = Parcel.obtain();

        try {
            request.writeToParcel(parcel, /* flags= */ 0);
            parcel.setDataPosition(/* pos= */ 0);
            UpdateAdIdRequest requestFromParcel =
                    UpdateAdIdRequest.CREATOR.createFromParcel(parcel);

            assertThat(request).isEqualTo(requestFromParcel);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testBuildAndGetUpdateAdIdRequest() {
        String adIdString = AdId.ZERO_OUT;
        boolean isLatEnabled = true;

        UpdateAdIdRequest request =
                new UpdateAdIdRequest.Builder(adIdString)
                        .setLimitAdTrackingEnabled(isLatEnabled)
                        .build();

        expect.that(request.getAdId()).isEqualTo(adIdString);
        expect.that(request.isLimitAdTrackingEnabled()).isEqualTo(isLatEnabled);
    }

    @Test
    public void testBuildAndGetUpdateAdIdRequest_nullAdId() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    UpdateAdIdRequest unusedRequest =
                            new UpdateAdIdRequest.Builder(/* adId= */ null).build();
                });
    }
}
