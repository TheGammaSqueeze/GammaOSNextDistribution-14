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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.SelectAdsFromOutcomesArgumentUtil.BID_FIELD_NAME;
import static com.android.adservices.service.adselection.SelectAdsFromOutcomesArgumentUtil.ID_FIELD_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.service.js.JSScriptArgument;

import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class SelectAdsFromOutcomesArgumentUtilTest {
    private static final String SCRIPT_ARGUMENT_NAME_IGNORED = "ignored";
    private static final long AD_SELECTION_ID = 123456789101112L;
    private static final double BID = 10.0;
    private static final Uri URI = Uri.parse("www.test.com");
    private static final AdSelectionResultBidAndUri ID_WITH_BID_AND_RENDER_URI =
            AdSelectionResultBidAndUri.builder()
                    .setAdSelectionId(AD_SELECTION_ID)
                    .setWinningAdBid(BID)
                    .setWinningAdRenderUri(URI)
                    .build();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testConvertsToScriptArgument() {
        JSScriptArgument argument =
                SelectAdsFromOutcomesArgumentUtil.asScriptArgument(
                        SCRIPT_ARGUMENT_NAME_IGNORED, ID_WITH_BID_AND_RENDER_URI);
        JSScriptArgument expected =
                JSScriptArgument.recordArg(
                        SCRIPT_ARGUMENT_NAME_IGNORED,
                        JSScriptArgument.stringArg(ID_FIELD_NAME, Long.toString(AD_SELECTION_ID)),
                        JSScriptArgument.numericArg(BID_FIELD_NAME, BID));
        assertThat(argument).isEqualTo(expected);
    }
}
