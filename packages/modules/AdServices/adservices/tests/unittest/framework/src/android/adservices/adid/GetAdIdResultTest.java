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
package android.adservices.adid;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit tests for {@link GetAdIdResult} */
@SmallTest
public final class GetAdIdResultTest extends AdServicesUnitTestCase {
    private static final String TEST_AD_ID = "TEST_AD_ID";
    private static final boolean TEST_LIMIT_AD_TRACKING_ENABLED = true;

    @Test
    public void testWriteToParcel() {
        GetAdIdResult response =
                new GetAdIdResult.Builder()
                        .setAdId(TEST_AD_ID)
                        .setLatEnabled(TEST_LIMIT_AD_TRACKING_ENABLED)
                        .build();
        Parcel p = Parcel.obtain();

        try {
            response.writeToParcel(p, 0);
            p.setDataPosition(0);

            GetAdIdResult fromParcel = GetAdIdResult.CREATOR.createFromParcel(p);
            expect.that(fromParcel.getAdId()).isEqualTo(TEST_AD_ID);
            expect.that(fromParcel.isLatEnabled()).isEqualTo(TEST_LIMIT_AD_TRACKING_ENABLED);

            expect.that(fromParcel).isEqualTo(response);
            expect.that(fromParcel.hashCode()).isEqualTo(response.hashCode());

        } finally {
            p.recycle();
        }

        expect.that(response.getErrorMessage()).isNull();
        expect.that(response.describeContents()).isEqualTo(0);
        expect.that(response.toString()).contains("GetAdIdResult{");

        expect.that(GetAdIdResult.CREATOR.newArray(1).length).isEqualTo(1);
    }

    @Test
    public void testWriteToParcel_nullableThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetAdIdResult unusedResponse =
                            new GetAdIdResult.Builder().setAdId(null).build();
                });
    }
}
