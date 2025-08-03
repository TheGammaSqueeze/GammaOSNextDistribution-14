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

import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.js.JSScriptRecordArgument;

import org.json.JSONObject;

/**
 * Interface for persisting the ad counter keys for an ad throughout the ad selection process and
 * into the ad selection table.
 */
public interface AdCounterKeyCopier {
    /**
     * Copies the ad counter keys from the source {@link DBAdData} into the given {@link
     * AdData.Builder} and returns it.
     *
     * <p>Note that the given {@code targetBuilder} will be modified.
     */
    @NonNull
    AdData.Builder copyAdCounterKeys(
            @NonNull AdData.Builder targetBuilder, @NonNull DBAdData sourceAdData);

    /**
     * Copies the ad counter keys from the source {@link AdData} into a copy of the given {@link
     * JSScriptRecordArgument} and returns the copy.
     */
    @NonNull
    JSScriptRecordArgument copyAdCounterKeys(
            @NonNull JSScriptRecordArgument originalRecordArgument, @NonNull AdData sourceAdData);

    /**
     * Copies the ad counter keys from the source {@link DBAdData} into a copy of the given {@link
     * JSScriptRecordArgument} and returns the copy.
     */
    @NonNull
    JSScriptRecordArgument copyAdCounterKeys(
            @NonNull JSScriptRecordArgument originalRecordArgument, @NonNull DBAdData sourceAdData);

    /**
     * Parses the ad counter keys from the JSON bidding or scoring result and copies any keys into
     * the given {@link AdData.Builder} and returns it.
     *
     * <p>Note that the given {@code targetBuilder} will be modified.
     */
    @NonNull
    AdData.Builder copyAdCounterKeys(
            @NonNull AdData.Builder targetBuilder, @NonNull JSONObject sourceObject);

    /**
     * Copies the ad counter keys from the winning ad's {@link AdScoringOutcome} to the given {@link
     * DBAdSelection.Builder} which will be persisted into the ad selection table and returns it.
     *
     * <p>Note that the given {@code targetBuilder} will be modified.
     */
    @NonNull
    DBAdSelection.Builder copyAdCounterKeys(
            @NonNull DBAdSelection.Builder targetBuilder, @NonNull AdScoringOutcome sourceOutcome);
}
