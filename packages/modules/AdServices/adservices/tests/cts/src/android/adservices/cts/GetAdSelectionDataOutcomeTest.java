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

import android.adservices.adselection.GetAdSelectionDataOutcome;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class GetAdSelectionDataOutcomeTest {
    private static final long AD_SELECTION_ID = 123456789L;
    private static final byte[] AD_SELECTION_RESULT = new byte[] {1, 2, 3, 4};

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testGetAdSelectionDataRequest_validInput_success() {
        GetAdSelectionDataOutcome request =
                new GetAdSelectionDataOutcome.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        assertThat(request.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        assertThat(request.getAdSelectionDataId()).isEqualTo(AD_SELECTION_ID);
        assertThat(request.getAdSelectionData()).isEqualTo(AD_SELECTION_RESULT);
    }

    @Test
    public void testMutabilityForAdSelectionData() {
        byte originalValue = 1;
        byte[] adSelectionData = new byte[] {originalValue};
        GetAdSelectionDataOutcome getAdSelectionDataOutcome =
                new GetAdSelectionDataOutcome.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionData(adSelectionData)
                        .build();
        assertThat(getAdSelectionDataOutcome.getAdSelectionData()).isEqualTo(adSelectionData);

        byte newValue = 5;
        adSelectionData[0] = newValue;
        assertThat(getAdSelectionDataOutcome.getAdSelectionData()).isNotNull();
        assertThat(getAdSelectionDataOutcome.getAdSelectionData()[0]).isEqualTo(originalValue);
    }
}
