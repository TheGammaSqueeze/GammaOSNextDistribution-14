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

package android.adservices.adselection;

import static android.adservices.adselection.AdSelectionConfigFixture.anAdSelectionConfig;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link AdSelectionInput} */
@SmallTest
public final class AdSelectionInputTest {
    private static final String CALLER_PACKAGE_NAME = "callerPackageName";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testWriteToParcel() throws Exception {

        AdSelectionConfig testAdSelectionConfig = anAdSelectionConfig();

        AdSelectionInput input =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(testAdSelectionConfig)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        Parcel p = Parcel.obtain();
        input.writeToParcel(p, 0);
        p.setDataPosition(0);

        AdSelectionInput fromParcel = AdSelectionInput.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getAdSelectionConfig()).isEqualTo(testAdSelectionConfig);
        assertThat(fromParcel.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
    }

    @Test
    public void testFailsToBuildWithNullCallerPackageName() {
        AdSelectionConfig testAdSelectionConfig = anAdSelectionConfig();

        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionInput.Builder()
                            .setAdSelectionConfig(testAdSelectionConfig)
                            // Not setting CallerPackageName making it null.
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithNullAdSelectionConfig() {

        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionInput.Builder()
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            // Not setting AdSelectionConfig making it null.
                            .build();
                });
    }

    @Test
    public void testAdSelectionInputDescribeContents() {
        AdSelectionConfig testAdSelectionConfig = anAdSelectionConfig();

        AdSelectionInput obj =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(testAdSelectionConfig)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertEquals(obj.describeContents(), 0);
    }
}
