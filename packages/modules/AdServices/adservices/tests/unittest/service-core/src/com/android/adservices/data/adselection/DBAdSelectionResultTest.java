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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class DBAdSelectionResultTest {

    private static final long AD_SELECTION_ID_1 = 1L;
    private static final double AD_WINNING_BID_1 = 2.23d;

    private static final Uri VALID_AD_RENDER_URI_1 =
            AdDataFixture.getValidRenderUriByBuyer(CommonFixture.VALID_BUYER_1, 1);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuild_unsetAdSelectionId_throwsISE() {
        assertThrows(IllegalStateException.class, () -> DBAdSelectionResult.builder().build());
    }

    @Test
    public void testBuild_withoutWinningCustomAudience_success() {
        DBAdSelectionResult result =
                DBAdSelectionResult.builder()
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setWinningAdBid(AD_WINNING_BID_1)
                        .setWinningAdRenderUri(VALID_AD_RENDER_URI_1)
                        .setWinningBuyer(CommonFixture.VALID_BUYER_1)
                        .build();

        assertEquals(AD_SELECTION_ID_1, result.getAdSelectionId());
        assertEquals(AD_WINNING_BID_1, result.getWinningAdBid(), 0.0);
        assertEquals(VALID_AD_RENDER_URI_1, result.getWinningAdRenderUri());
        assertEquals(CommonFixture.VALID_BUYER_1, result.getWinningBuyer());
    }

    @Test
    public void testBuild_withWinningCustomAudience_success() {
        DBWinningCustomAudience winningCustomAudience =
                DBWinningCustomAudience.builder()
                        .setOwner(CustomAudienceFixture.VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setAdCounterIntKeys(AdDataFixture.getAdCounterKeys())
                        .build();

        DBAdSelectionResult result =
                DBAdSelectionResult.builder()
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setWinningAdBid(AD_WINNING_BID_1)
                        .setWinningAdRenderUri(VALID_AD_RENDER_URI_1)
                        .setWinningBuyer(CommonFixture.VALID_BUYER_1)
                        .setWinningCustomAudience(winningCustomAudience)
                        .build();

        assertEquals(AD_SELECTION_ID_1, result.getAdSelectionId());
        assertEquals(AD_WINNING_BID_1, result.getWinningAdBid(), 0.0);
        assertEquals(VALID_AD_RENDER_URI_1, result.getWinningAdRenderUri());
        assertEquals(CommonFixture.VALID_BUYER_1, result.getWinningBuyer());
        assertEquals(winningCustomAudience, result.getWinningCustomAudience());
    }
}
