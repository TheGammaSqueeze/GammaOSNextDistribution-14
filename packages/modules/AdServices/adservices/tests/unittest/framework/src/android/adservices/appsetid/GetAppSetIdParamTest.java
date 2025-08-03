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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;

import org.junit.Test;

/** Unit tests for {@link GetAppSetIdParam} */
@RequiresSdkLevelAtLeastS()
@SmallTest
public final class GetAppSetIdParamTest extends AdServicesUnitTestCase {
    private static final String SOME_PACKAGE_NAME = "SomePackageName";
    private static final String SOME_SDK_PACKAGE_NAME = "SomeSdkPackageName";

    @Test
    public void test_nonNull() {
        GetAppSetIdParam param =
                new GetAppSetIdParam.Builder()
                        .setAppPackageName(SOME_PACKAGE_NAME)
                        .setSdkPackageName(SOME_SDK_PACKAGE_NAME)
                        .build();

        expect.that(param.getSdkPackageName()).isEqualTo(SOME_SDK_PACKAGE_NAME);
        expect.that(param.getAppPackageName()).isEqualTo(SOME_PACKAGE_NAME);

        // no file descriptor marshalling.
        expect.that(param.describeContents()).isEqualTo(0);

        Parcel parcel = Parcel.obtain();

        try {
            param.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            GetAppSetIdParam createdParam = GetAppSetIdParam.CREATOR.createFromParcel(parcel);
            expect.that(createdParam).isNotSameInstanceAs(param);

            // equals() is not overridden.
            expect.that(createdParam).isNotEqualTo(param);
        } finally {
            parcel.recycle();
        }

        assertThat(GetAppSetIdParam.CREATOR.newArray(1).length).isEqualTo(1);
    }

    @Test
    public void test_nullAppPackageName_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetAppSetIdParam unusedRequest =
                            new GetAppSetIdParam.Builder()
                                    // Not setting AppPackageName making it null.
                                    .build();
                });

        // Null AppPackageName.
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetAppSetIdParam unusedRequest =
                            new GetAppSetIdParam.Builder().setAppPackageName(null).build();
                });
    }

    @Test
    public void test_notSettingAppPackageName_throwsIllegalArgumentException() {
        // Empty AppPackageName.
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetAppSetIdParam unusedRequest =
                            new GetAppSetIdParam.Builder().setAppPackageName("").build();
                });
    }
}
