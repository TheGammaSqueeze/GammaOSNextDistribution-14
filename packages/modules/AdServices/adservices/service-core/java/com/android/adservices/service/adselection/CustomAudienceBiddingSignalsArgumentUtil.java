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

import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import androidx.annotation.NonNull;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.js.JSScriptArgument;


import org.json.JSONException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A utility class to convert instances of {@link CustomAudienceSignals} into {@link
 * JSScriptArgument}
 */
public class CustomAudienceBiddingSignalsArgumentUtil {
    // TODO: (b/228094391): Put these common constants in a separate class
    public static final String OWNER_FIELD_NAME = "owner";
    public static final String BUYER_FIELD_NAME = "buyer";
    public static final String NAME_FIELD_NAME = "name";
    public static final String ACTIVATION_TIME_FIELD_NAME = "activation_time";
    public static final String EXPIRATION_TIME_FIELD_NAME = "expiration_time";
    public static final String USER_BIDDING_SIGNALS_FIELD_NAME = "user_bidding_signals";

    // No instance of this class is supposed to be created
    private CustomAudienceBiddingSignalsArgumentUtil() {}

    /**
     * @return A {@link JSScriptArgument} with the given {@code name} to represent this instance of
     *     {@link CustomAudienceBiddingSignalsArgumentUtil}
     * @throws JSONException if any of the signals in this class is not valid JSON.
     */
    public static JSScriptArgument asScriptArgument(
            String name, CustomAudienceSignals customAudienceSignals) throws JSONException {
        return recordArg(
                name,
                stringArg(OWNER_FIELD_NAME, customAudienceSignals.getOwner()),
                stringArg(BUYER_FIELD_NAME, customAudienceSignals.getBuyer().toString()),
                stringArg(NAME_FIELD_NAME, customAudienceSignals.getName()),
                numericArg(
                        ACTIVATION_TIME_FIELD_NAME,
                        instantToEpochMilli(customAudienceSignals.getActivationTime())),
                numericArg(
                        EXPIRATION_TIME_FIELD_NAME,
                        instantToEpochMilli(customAudienceSignals.getExpirationTime())),
                jsonArg(
                        USER_BIDDING_SIGNALS_FIELD_NAME,
                        customAudienceSignals.getUserBiddingSignals()));
    }

    /**
     * Maps an Instant to a long representation of its number of milliseconds
     *
     * @param instant an Instant to be converted
     * @return a Long representation of the Instant's number of milliseconds
     */
    public static Long instantToEpochMilli(@NonNull Instant instant) {
        Objects.requireNonNull(instant);

        return Optional.ofNullable(instant).map(Instant::toEpochMilli).orElse(null);
    }
}
