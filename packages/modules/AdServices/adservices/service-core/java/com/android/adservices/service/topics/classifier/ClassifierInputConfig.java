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

package com.android.adservices.service.topics.classifier;

import android.annotation.NonNull;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;

/** POJO Represents a ClassifierInputConfig. */
public class ClassifierInputConfig {
    private final String mInputFormat;

    private final List<ClassifierInputField> mInputFields;

    ClassifierInputConfig(
            @NonNull String inputFormat, @NonNull List<ClassifierInputField> inputFields) {
        Objects.requireNonNull(inputFormat);
        Objects.requireNonNull(inputFields);
        mInputFormat = inputFormat;
        mInputFields = inputFields;
    }

    /** Enum representing the possible input fields for the classifier. */
    public enum ClassifierInputField {
        PACKAGE_NAME,
        SPLIT_PACKAGE_NAME,
        APP_NAME,
        APP_DESCRIPTION;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ClassifierInputConfig)) {
            return false;
        }
        ClassifierInputConfig classifierInputConfig = (ClassifierInputConfig) object;
        return mInputFormat.equals(classifierInputConfig.mInputFormat)
                && mInputFields.equals(classifierInputConfig.mInputFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mInputFormat, mInputFields);
    }

    /** @return The input format. */
    @NonNull
    public String getInputFormat() {
        return mInputFormat;
    }

    /** @return The input fields. */
    @NonNull
    public List<ClassifierInputField> getInputFields() {
        return mInputFields;
    }

    /**
     * Util method to get an empty config.
     *
     * @return A config with empty input format and input fields.
     */
    public static ClassifierInputConfig getEmptyConfig() {
        return new ClassifierInputConfig("", ImmutableList.of());
    }
}
