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

package com.android.adservices.service.adselection;

import android.adservices.common.AdData;
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptRecordArgument;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Real implementation of the {@link AdCounterKeyCopier} for copying ad counter keys to a {@link
 * DBAdSelection} for caching in the ad selection database.
 */
public class AdCounterKeyCopierImpl implements AdCounterKeyCopier {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting public static final String AD_COUNTER_KEYS_FIELD_NAME = "ad_counter_keys";

    public AdCounterKeyCopierImpl() {}

    @Override
    @NonNull
    public AdData.Builder copyAdCounterKeys(
            @NonNull AdData.Builder targetBuilder, @NonNull DBAdData sourceAdData) {
        Objects.requireNonNull(targetBuilder);
        Objects.requireNonNull(sourceAdData);
        return targetBuilder.setAdCounterKeys(sourceAdData.getAdCounterKeys());
    }

    @Override
    @NonNull
    public JSScriptRecordArgument copyAdCounterKeys(
            @NonNull JSScriptRecordArgument originalRecordArgument, @NonNull AdData sourceAdData) {
        Objects.requireNonNull(originalRecordArgument);
        Objects.requireNonNull(sourceAdData);

        if (sourceAdData.getAdCounterKeys().isEmpty()) {
            return originalRecordArgument;
        }

        return originalRecordArgument.getCopyWithFields(
                Collections.singletonList(
                        JSScriptArgument.numericArrayArg(
                                AD_COUNTER_KEYS_FIELD_NAME,
                                new ArrayList<>(sourceAdData.getAdCounterKeys()))));
    }

    @Override
    @NonNull
    public JSScriptRecordArgument copyAdCounterKeys(
            @NonNull JSScriptRecordArgument originalRecordArgument,
            @NonNull DBAdData sourceAdData) {
        Objects.requireNonNull(originalRecordArgument);
        Objects.requireNonNull(sourceAdData);

        if (sourceAdData.getAdCounterKeys().isEmpty()) {
            return originalRecordArgument;
        }

        return originalRecordArgument.getCopyWithFields(
                Collections.singletonList(
                        JSScriptArgument.numericArrayArg(
                                AD_COUNTER_KEYS_FIELD_NAME,
                                new ArrayList<>(sourceAdData.getAdCounterKeys()))));
    }

    @Override
    @NonNull
    public AdData.Builder copyAdCounterKeys(
            @NonNull AdData.Builder targetBuilder, @NonNull JSONObject sourceObject) {
        Objects.requireNonNull(targetBuilder);
        Objects.requireNonNull(sourceObject);

        JSONArray keysArray = sourceObject.optJSONArray(AD_COUNTER_KEYS_FIELD_NAME);
        if (keysArray == null || keysArray.length() == 0) {
            return targetBuilder;
        }

        HashSet<Integer> keysSet = new HashSet<>();
        for (int index = 0; index < keysArray.length(); index++) {
            try {
                int key = keysArray.getInt(index);
                keysSet.add(key);
            } catch (JSONException exception) {
                sLogger.d(
                        exception, "Error parsing ad counter keys from logic result; skipping key");
            }
        }

        return targetBuilder.setAdCounterKeys(keysSet);
    }

    @Override
    @NonNull
    public DBAdSelection.Builder copyAdCounterKeys(
            @NonNull DBAdSelection.Builder targetBuilder, @NonNull AdScoringOutcome sourceOutcome) {
        Objects.requireNonNull(targetBuilder);
        Objects.requireNonNull(sourceOutcome);
        Set<Integer> keys =
                sourceOutcome.getAdWithScore().getAdWithBid().getAdData().getAdCounterKeys();
        sLogger.v("Copying %d ad counter keys to ad selection entry builder", keys.size());
        return targetBuilder.setAdCounterIntKeys(keys);
    }
}
