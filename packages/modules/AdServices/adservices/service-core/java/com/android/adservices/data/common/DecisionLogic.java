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

package com.android.adservices.data.common;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.adservices.service.adselection.JsVersionHelper;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

/** A class represents a certain logic with its signature version. */
@AutoValue
public abstract class DecisionLogic {
    /** The script. */
    @NonNull
    public abstract String getPayload();

    /** Returns the versions of the content in the script. */
    @NonNull
    public abstract ImmutableMap<Integer, Long> getVersions();

    /** Returns {@link DecisionLogic} built with certain parameters. */
    public static DecisionLogic create(
            @NonNull String payload, @NonNull ImmutableMap<Integer, Long> versions) {
        return new AutoValue_DecisionLogic(payload, versions);
    }

    /**
     * Returns the version according to the {@link
     * com.android.adservices.service.adselection.JsVersionHelper.JsPayloadType}.
     */
    @Nullable
    public Long getVersion(@JsVersionHelper.JsPayloadType Integer jsPayloadType) {
        return getVersions()
                .getOrDefault(jsPayloadType, JsVersionHelper.DEFAULT_JS_VERSION_IF_ABSENT);
    }
}
