/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class GetAdSelectionDataRequestTest {

    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final Uri COORDINATOR_URI = Uri.parse("http://foo.bar/gcp");

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void getAdSelectionDataRequest_withNonNullInputs_success() {
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(SELLER)
                        .setCoordinatorOriginUri(COORDINATOR_URI)
                        .build();

        assertThat(request.getSeller()).isEqualTo(SELLER);
        assertThat(request.getCoordinatorOriginUri()).isEqualTo(COORDINATOR_URI);
    }

    @Test
    public void getAdSelectionDataRequest_nullCoordinator_success() {
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder().setSeller(SELLER).build();

        assertThat(request.getSeller()).isEqualTo(SELLER);
        assertThat(request.getCoordinatorOriginUri()).isNull();
    }

    @Test
    public void getAdSelectionDataRequest_nullSeller_success() {
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setCoordinatorOriginUri(COORDINATOR_URI)
                        .build();

        assertThat(request.getSeller()).isNull();
        assertThat(request.getCoordinatorOriginUri()).isEqualTo(COORDINATOR_URI);
    }
}
