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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Unit tests for {@link SetAppInstallAdvertisersInput} */
@SmallTest
public final class SetAppInstallAdvertisersInputTest {
    private static final Set<AdTechIdentifier> ADVERTISERS =
            new HashSet<>(
                    Arrays.asList(
                            AdTechIdentifier.fromString("example1.com"),
                            AdTechIdentifier.fromString("example2.com")));
    private static final String CALLER_PACKAGE_NAME = "callerPackageName";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testWriteToParcel() {
        SetAppInstallAdvertisersInput input =
                new SetAppInstallAdvertisersInput.Builder()
                        .setAdvertisers(ADVERTISERS)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        Parcel p = Parcel.obtain();
        input.writeToParcel(p, 0);
        p.setDataPosition(0);

        SetAppInstallAdvertisersInput fromParcel =
                SetAppInstallAdvertisersInput.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getAdvertisers()).isEqualTo(ADVERTISERS);
        assertThat(fromParcel.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
    }

    @Test
    public void testFailsToBuildWithNullCallerPackageName() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new SetAppInstallAdvertisersInput.Builder()
                            .setAdvertisers(ADVERTISERS)
                            // Not setting CallerPackageName making it null.
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithNullAdvertisers() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new SetAppInstallAdvertisersInput.Builder()
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            // Not setting Advertisers making it null.
                            .build();
                });
    }

    @Test
    public void testSetAppInstallAdvertisersInputDescribeContents() {
        SetAppInstallAdvertisersInput obj =
                new SetAppInstallAdvertisersInput.Builder()
                        .setAdvertisers(ADVERTISERS)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertEquals(obj.describeContents(), 0);
    }
}
