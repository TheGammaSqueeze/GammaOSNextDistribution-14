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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.SetAppInstallAdvertisersRequest;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Ignore;
import org.junit.Rule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SetAppInstallAdvertisersRequestTest {
    private static final Set<AdTechIdentifier> ADVERTISERS =
            new HashSet<>(
                    Arrays.asList(
                            AdTechIdentifier.fromString("example1.com"),
                            AdTechIdentifier.fromString("example2.com")));

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Ignore
    public void testBuildsSetAppInstallAdvertisersRequest() {
        SetAppInstallAdvertisersRequest request =
                new SetAppInstallAdvertisersRequest.Builder().setAdvertisers(ADVERTISERS).build();

        assertThat(request.getAdvertisers()).isEqualTo(ADVERTISERS);
    }
}
