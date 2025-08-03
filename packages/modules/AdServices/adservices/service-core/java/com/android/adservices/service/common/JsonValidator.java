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

package com.android.adservices.service.common;

import android.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import com.google.common.collect.ImmutableCollection;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/** Validation utility class for JSON fields. */
public class JsonValidator implements Validator<String> {
    @VisibleForTesting
    public static final String SHOULD_BE_A_VALID_JSON = "%s's %s should be a valid json.";

    @NonNull private final String mClassName;
    @NonNull private final String mFieldName;

    /**
     * Constructs a validator which validates an ad tech identifier.
     *
     * @param className the class the field belong to
     * @param fieldName the field name of the json
     */
    public JsonValidator(@NonNull String className, @NonNull String fieldName) {
        Preconditions.checkStringNotEmpty(className);
        Preconditions.checkStringNotEmpty(fieldName);

        mClassName = className;
        mFieldName = fieldName;
    }

    /**
     * Validate a string field is a valid json.
     *
     * @param json the field to be validated
     */
    @Override
    public void addValidation(
            @NonNull String json, @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(json);
        Objects.requireNonNull(violations);

        try {
            new JSONObject(json);
        } catch (JSONException jsonException) {
            violations.add(String.format(SHOULD_BE_A_VALID_JSON, mClassName, mFieldName));
        }
    }
}
