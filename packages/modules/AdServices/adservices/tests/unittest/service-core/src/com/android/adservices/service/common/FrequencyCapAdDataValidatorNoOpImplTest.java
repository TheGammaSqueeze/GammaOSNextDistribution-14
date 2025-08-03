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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

public class FrequencyCapAdDataValidatorNoOpImplTest {
    private FrequencyCapAdDataValidatorNoOpImpl mFrequencyCapAdDataValidator;

    @Before
    public void setup() {
        mFrequencyCapAdDataValidator = new FrequencyCapAdDataValidatorNoOpImpl();
    }

    @Test
    public void testAddValidation_nullAdDataThrows() {
        assertThrows(
                NullPointerException.class,
                () -> mFrequencyCapAdDataValidator.addValidation(null, ImmutableList.builder()));
    }

    @Test
    public void testAddValidation_nullViolationsThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mFrequencyCapAdDataValidator.addValidation(
                                AdDataFixture.getValidFilterAdDataByBuyer(
                                        CommonFixture.VALID_BUYER_1, 0),
                                null));
    }

    @Test
    public void testAddValidation_validKeysAndFiltersNoViolationsFound() {
        ImmutableList.Builder<String> violationsBuilder = ImmutableList.builder();

        mFrequencyCapAdDataValidator.addValidation(
                AdDataFixture.getValidFilterAdDataByBuyer(CommonFixture.VALID_BUYER_1, 0),
                violationsBuilder);

        assertThat(violationsBuilder.build()).isEmpty();
    }

    @Test
    public void testAddValidation_excessiveNumKeysAndFiltersReturnsViolations() {
        ImmutableList.Builder<String> violationsBuilder = ImmutableList.builder();
        AdData adDataWithExceededFrequencyCapLimits =
                AdDataFixture.getAdDataWithExceededFrequencyCapLimits(
                        CommonFixture.VALID_BUYER_1, 0);

        assertThat(adDataWithExceededFrequencyCapLimits.getAdCounterKeys().size())
                .isGreaterThan(AdData.MAX_NUM_AD_COUNTER_KEYS);
        assertThat(
                        adDataWithExceededFrequencyCapLimits
                                        .getAdFilters()
                                        .getFrequencyCapFilters()
                                        .getKeyedFrequencyCapsForWinEvents()
                                        .size()
                                + adDataWithExceededFrequencyCapLimits
                                        .getAdFilters()
                                        .getFrequencyCapFilters()
                                        .getKeyedFrequencyCapsForImpressionEvents()
                                        .size()
                                + adDataWithExceededFrequencyCapLimits
                                        .getAdFilters()
                                        .getFrequencyCapFilters()
                                        .getKeyedFrequencyCapsForViewEvents()
                                        .size()
                                + adDataWithExceededFrequencyCapLimits
                                        .getAdFilters()
                                        .getFrequencyCapFilters()
                                        .getKeyedFrequencyCapsForClickEvents()
                                        .size())
                .isGreaterThan(FrequencyCapFilters.MAX_NUM_FREQUENCY_CAP_FILTERS);

        mFrequencyCapAdDataValidator.addValidation(
                adDataWithExceededFrequencyCapLimits, violationsBuilder);

        ImmutableList<String> violations = violationsBuilder.build();

        assertThat(violations).isEmpty();
    }

    @Test
    public void testAddValidation_noKeysOrFiltersNoViolationsFound() {
        ImmutableList.Builder<String> violationsBuilder = ImmutableList.builder();

        mFrequencyCapAdDataValidator.addValidation(
                AdDataFixture.getValidAdDataByBuyer(CommonFixture.VALID_BUYER_1, 0),
                violationsBuilder);

        assertThat(violationsBuilder.build()).isEmpty();
    }
}
