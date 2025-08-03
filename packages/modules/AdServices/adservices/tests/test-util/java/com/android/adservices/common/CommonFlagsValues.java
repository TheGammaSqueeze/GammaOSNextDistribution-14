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
package com.android.adservices.common;

import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.Flags.SDK_REQUEST_PERMITS_PER_SECOND;

/** Provides common values for flags used in multiple CTS / unit tests. */
public final class CommonFlagsValues {

    public static final long DEFAULT_API_RATE_LIMIT_SLEEP_MS =
            (long) (1500 / SDK_REQUEST_PERMITS_PER_SECOND) + 100L;

    // TODO(b/273656890): Investigate dynamic timeouts for device types
    public static final long ADDITIONAL_TIMEOUT = 3_000L;
    public static final long EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS =
            FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS + ADDITIONAL_TIMEOUT;
    public static final long EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS =
            FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS + ADDITIONAL_TIMEOUT;
    public static final long EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS =
            FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS + ADDITIONAL_TIMEOUT;
    public static final long EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS =
            FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS + ADDITIONAL_TIMEOUT * 4;
    public static final long EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS =
            FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS + ADDITIONAL_TIMEOUT;
    public static final long EXTENDED_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS =
            FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS + ADDITIONAL_TIMEOUT;
    public static final int EXTENDED_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS =
            FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS + (int) ADDITIONAL_TIMEOUT;
    public static final int EXTENDED_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS =
            FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS + (int) ADDITIONAL_TIMEOUT;

    public static final int
            EXTENDED_AD_SELECTION_DATA_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS =
                    FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS
                            + (int) ADDITIONAL_TIMEOUT;
    public static final int
            EXTENDED_AD_SELECTION_DATA_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS =
                    FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS
                            + (int) ADDITIONAL_TIMEOUT;

    private CommonFlagsValues() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
