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

import android.adservices.common.AdData;
import android.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.Objects;
import java.util.stream.Collectors;

/** Validator for a ad data instance. */
public class AdDataValidator implements Validator<AdData> {
    @VisibleForTesting public static final String VIOLATION_FORMAT = "For %s, %s";

    @VisibleForTesting public static final String AD_DATA_CLASS_NAME = AdData.class.getName();
    @VisibleForTesting public static final String RENDER_URI_FIELD_NAME = "render uri";
    @VisibleForTesting public static final String METADATA_FIELD_NAME = "metadata";

    @NonNull private final AdTechUriValidator mUriValidator;
    @NonNull private final JsonValidator mMetadataValidator;
    @NonNull private final FrequencyCapAdDataValidator mFrequencyCapAdDataValidator;
    @NonNull private final AdRenderIdValidator mAdRenderIdValidator;

    public AdDataValidator(
            @NonNull String adTechRole,
            @NonNull String adTechIdentifier,
            @NonNull FrequencyCapAdDataValidator frequencyCapAdDataValidator,
            @NonNull AdRenderIdValidator adRenderIdValidator) {
        Objects.requireNonNull(adTechRole);
        Objects.requireNonNull(adTechIdentifier);
        Objects.requireNonNull(frequencyCapAdDataValidator);
        Objects.requireNonNull(adRenderIdValidator);

        mUriValidator =
                new AdTechUriValidator(
                        adTechRole, adTechIdentifier, AD_DATA_CLASS_NAME, RENDER_URI_FIELD_NAME);
        mMetadataValidator = new JsonValidator(AD_DATA_CLASS_NAME, METADATA_FIELD_NAME);
        mFrequencyCapAdDataValidator = frequencyCapAdDataValidator;
        mAdRenderIdValidator = adRenderIdValidator;
    }

    /**
     * Validate an ad data is valid.
     *
     * @param adData the ad data to be validated.
     */
    @Override
    public void addValidation(
            @NonNull AdData adData, @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(adData);
        Objects.requireNonNull(violations);

        ImmutableCollection.Builder<String> adDataViolations = new ImmutableList.Builder<>();
        mUriValidator.addValidation(adData.getRenderUri(), adDataViolations);
        mMetadataValidator.addValidation(adData.getMetadata(), adDataViolations);
        mFrequencyCapAdDataValidator.addValidation(adData, adDataViolations);
        mAdRenderIdValidator.addValidation(adData.getAdRenderId(), adDataViolations);

        violations.addAll(
                adDataViolations.build().stream()
                        .map(violation -> String.format(VIOLATION_FORMAT, adData, violation))
                        .collect(Collectors.toList()));
    }
}
