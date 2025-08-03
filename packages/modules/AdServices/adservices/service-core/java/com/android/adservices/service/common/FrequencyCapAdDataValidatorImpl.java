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

package com.android.adservices.service.common;

import android.adservices.common.AdData;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCap;
import android.annotation.NonNull;
import android.annotation.Nullable;

import com.google.common.collect.ImmutableCollection;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Validator for the frequency cap-related fields in an {@link AdData} object, used when ad
 * filtering is enabled.
 */
public class FrequencyCapAdDataValidatorImpl implements FrequencyCapAdDataValidator {
    public FrequencyCapAdDataValidatorImpl() {}

    @Override
    public void addValidation(
            @NonNull AdData adData, @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(adData);
        Objects.requireNonNull(violations);

        final Set<Integer> adCounterKeys = adData.getAdCounterKeys();
        if (adCounterKeys != null) {
            if (adCounterKeys.size() > AdData.MAX_NUM_AD_COUNTER_KEYS) {
                violations.add(
                        String.format(
                                Locale.ENGLISH,
                                AdData.NUM_AD_COUNTER_KEYS_EXCEEDED_FORMAT,
                                AdData.MAX_NUM_AD_COUNTER_KEYS));
            }
        }

        if (adData.getAdFilters() != null
                && adData.getAdFilters().getFrequencyCapFilters() != null) {
            int numFrequencyCapFilters = 0;
            FrequencyCapFilters filters = adData.getAdFilters().getFrequencyCapFilters();

            addKeyedFrequencyCapListValidation(
                    filters.getKeyedFrequencyCapsForWinEvents(), violations);
            addKeyedFrequencyCapListValidation(
                    filters.getKeyedFrequencyCapsForImpressionEvents(), violations);
            addKeyedFrequencyCapListValidation(
                    filters.getKeyedFrequencyCapsForViewEvents(), violations);
            addKeyedFrequencyCapListValidation(
                    filters.getKeyedFrequencyCapsForClickEvents(), violations);

            if (filters.getKeyedFrequencyCapsForWinEvents() != null) {
                numFrequencyCapFilters += filters.getKeyedFrequencyCapsForWinEvents().size();
            }
            if (filters.getKeyedFrequencyCapsForImpressionEvents() != null) {
                numFrequencyCapFilters += filters.getKeyedFrequencyCapsForImpressionEvents().size();
            }
            if (filters.getKeyedFrequencyCapsForViewEvents() != null) {
                numFrequencyCapFilters += filters.getKeyedFrequencyCapsForViewEvents().size();
            }
            if (filters.getKeyedFrequencyCapsForClickEvents() != null) {
                numFrequencyCapFilters += filters.getKeyedFrequencyCapsForClickEvents().size();
            }

            if (numFrequencyCapFilters > FrequencyCapFilters.MAX_NUM_FREQUENCY_CAP_FILTERS) {
                violations.add(
                        String.format(
                                Locale.ENGLISH,
                                FrequencyCapFilters.NUM_FREQUENCY_CAP_FILTERS_EXCEEDED_FORMAT,
                                FrequencyCapFilters.MAX_NUM_FREQUENCY_CAP_FILTERS));
            }
        }
    }

    private void addKeyedFrequencyCapListValidation(
            @Nullable List<KeyedFrequencyCap> keyedFrequencyCaps,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(violations);

        if (keyedFrequencyCaps == null) {
            violations.add(FrequencyCapFilters.FREQUENCY_CAP_FILTERS_NULL_LIST_ERROR_MESSAGE);
            return;
        }

        for (KeyedFrequencyCap keyedFrequencyCap : keyedFrequencyCaps) {
            if (keyedFrequencyCap == null) {
                violations.add(
                        FrequencyCapFilters.FREQUENCY_CAP_FILTERS_NULL_ELEMENT_ERROR_MESSAGE);
            } else {
                if (keyedFrequencyCap.getMaxCount() <= 0) {
                    violations.add(
                            String.format(
                                    Locale.ENGLISH,
                                    KeyedFrequencyCap.MAX_COUNT_NOT_POSITIVE_ERROR_MESSAGE,
                                    keyedFrequencyCap.getMaxCount()));
                }

                if (keyedFrequencyCap.getInterval() == null) {
                    violations.add(KeyedFrequencyCap.INTERVAL_NULL_ERROR_MESSAGE);
                } else {
                    long intervalSeconds = keyedFrequencyCap.getInterval().getSeconds();
                    if (intervalSeconds <= 0) {
                        violations.add(
                                String.format(
                                        Locale.ENGLISH,
                                        KeyedFrequencyCap.INTERVAL_NOT_POSITIVE_FORMAT,
                                        keyedFrequencyCap.getInterval()));
                    } else if (intervalSeconds > KeyedFrequencyCap.MAX_INTERVAL.getSeconds()) {
                        violations.add(
                                String.format(
                                        Locale.ENGLISH,
                                        KeyedFrequencyCap.MAX_INTERVAL_EXCEEDED_FORMAT,
                                        keyedFrequencyCap.getInterval(),
                                        KeyedFrequencyCap.MAX_INTERVAL));
                    }
                }
            }
        }
    }
}
