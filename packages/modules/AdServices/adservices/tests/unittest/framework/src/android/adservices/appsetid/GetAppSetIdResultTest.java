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
package android.adservices.appsetid;

import static android.adservices.appsetid.GetAppSetIdResult.SCOPE_APP;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;

import org.junit.Test;

/** Unit tests for {@link GetAppSetIdResult} */
@RequiresSdkLevelAtLeastS()
@SmallTest
public final class GetAppSetIdResultTest extends AdServicesUnitTestCase {
    private static final String TEST_APP_SET_ID = "TEST_APP_SET_ID";

    @Test
    public void testWriteToParcel() {
        GetAppSetIdResult response =
                new GetAppSetIdResult.Builder()
                        .setAppSetId(TEST_APP_SET_ID)
                        .setAppSetIdScope(SCOPE_APP)
                        .build();
        Parcel p = Parcel.obtain();

        try {
            response.writeToParcel(p, 0);
            p.setDataPosition(0);

            GetAppSetIdResult fromParcel = GetAppSetIdResult.CREATOR.createFromParcel(p);
            expect.that(fromParcel.getAppSetId()).isEqualTo(TEST_APP_SET_ID);
            expect.that(fromParcel.getAppSetIdScope()).isEqualTo(SCOPE_APP);

            expect.that(fromParcel).isEqualTo(response);
            expect.that(fromParcel.hashCode()).isEqualTo(response.hashCode());

        } finally {
            p.recycle();
        }

        expect.that(response.getErrorMessage()).isNull();
        expect.that(response.describeContents()).isEqualTo(0);
        expect.that(response.toString()).contains("GetAppSetIdResult{");

        expect.that(GetAppSetIdResult.CREATOR.newArray(1).length).isEqualTo(1);
    }

    @Test
    public void testWriteToParcel_nullableThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetAppSetIdResult unusedResponse =
                            new GetAppSetIdResult.Builder().setAppSetId(null).build();
                });
    }
}
