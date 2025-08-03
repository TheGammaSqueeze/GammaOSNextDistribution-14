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
import com.android.adservices.service.js.JSScriptRecordArgument;

import org.json.JSONObject;

import java.util.Objects;

/**
 * No-op implementation of the {@link AdCounterKeyCopier}, to be used if frequency cap filtering is
 * disabled.
 */
public class AdCounterKeyCopierNoOpImpl implements AdCounterKeyCopier {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public AdCounterKeyCopierNoOpImpl() {}

    @Override
    @NonNull
    public AdData.Builder copyAdCounterKeys(
            @NonNull AdData.Builder targetBuilder, @NonNull DBAdData sourceAdData) {
        Objects.requireNonNull(targetBuilder);
        Objects.requireNonNull(sourceAdData);
        return targetBuilder;
    }

    @Override
    @NonNull
    public JSScriptRecordArgument copyAdCounterKeys(
            @NonNull JSScriptRecordArgument originalRecordArgument, @NonNull AdData sourceAdData) {
        Objects.requireNonNull(originalRecordArgument);
        Objects.requireNonNull(sourceAdData);
        return originalRecordArgument;
    }

    @Override
    @NonNull
    public JSScriptRecordArgument copyAdCounterKeys(
            @NonNull JSScriptRecordArgument originalRecordArgument,
            @NonNull DBAdData sourceAdData) {
        Objects.requireNonNull(originalRecordArgument);
        Objects.requireNonNull(sourceAdData);
        return originalRecordArgument;
    }

    @Override
    @NonNull
    public AdData.Builder copyAdCounterKeys(
            @NonNull AdData.Builder targetBuilder, @NonNull JSONObject sourceObject) {
        Objects.requireNonNull(targetBuilder);
        Objects.requireNonNull(sourceObject);
        return targetBuilder;
    }

    @Override
    @NonNull
    public DBAdSelection.Builder copyAdCounterKeys(
            @NonNull DBAdSelection.Builder targetBuilder, @NonNull AdScoringOutcome sourceOutcome) {
        Objects.requireNonNull(targetBuilder);
        Objects.requireNonNull(sourceOutcome);
        sLogger.v("Ad selection filtering disabled, skipping ad counter key copying");
        return targetBuilder;
    }
}
