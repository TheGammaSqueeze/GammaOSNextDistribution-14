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

import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.internal.annotations.VisibleForTesting;

/**
 * A wrapper class for {@code AdSelectionId} and {@code Bid} pair to support the conversion to JS
 * Script parameter and from JS result string.
 */
public class SelectAdsFromOutcomesArgumentUtil {
    @VisibleForTesting static final String ID_FIELD_NAME = "id";

    @VisibleForTesting static final String BID_FIELD_NAME = "bid";

    // No instance of this class is supposed to be created
    private SelectAdsFromOutcomesArgumentUtil() {}

    /** Converts {@link AdSelectionResultBidAndUri} object to Json object */
    public static JSScriptArgument asScriptArgument(
            String name, AdSelectionResultBidAndUri adSelectionIdWithBidAndRenderUri) {
        return recordArg(
                name,
                // Parse as a string, so we won't lose precision in JS
                stringArg(
                        ID_FIELD_NAME,
                        Long.toString(adSelectionIdWithBidAndRenderUri.getAdSelectionId())),
                numericArg(BID_FIELD_NAME, adSelectionIdWithBidAndRenderUri.getWinningAdBid()));
    }
}
