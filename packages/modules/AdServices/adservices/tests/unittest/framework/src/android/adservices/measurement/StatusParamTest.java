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

package android.adservices.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.os.Parcel;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link StatusParam} */
@SmallTest
public final class StatusParamTest {
    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";

    private StatusParam createExample() {
        return new StatusParam.Builder(sContext.getPackageName(), SDK_PACKAGE_NAME).build();
    }

    void verifyExample(StatusParam param) {
        assertEquals(sContext.getPackageName(), param.getAppPackageName());
        assertEquals(SDK_PACKAGE_NAME, param.getSdkPackageName());
    }

    @Test
    public void testMissingAppPackageName_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new StatusParam.Builder(/* appPackageName = */ null, SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingSdkPackageName_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new StatusParam.Builder(
                                        sContext.getPackageName(), /* sdkPackageName = */ null)
                                .build());
    }

    @Test
    public void testCreation() {
        verifyExample(createExample());
    }

    @Test
    public void testParceling() {
        Parcel p = Parcel.obtain();
        createExample().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExample(StatusParam.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, createExample().describeContents());
    }
}
