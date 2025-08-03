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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdFilters;
import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.FrequencyCapFiltersFixture;
import android.adservices.common.KeyedFrequencyCap;
import android.adservices.common.KeyedFrequencyCapFixture;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

public class FrequencyCapAdDataValidatorImplTest {
    private FrequencyCapAdDataValidatorImpl mFrequencyCapAdDataValidator;

    @Before
    public void setup() {
        mFrequencyCapAdDataValidator = new FrequencyCapAdDataValidatorImpl();
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

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .containsExactly(
                        "AdData should have no more than 10 ad counter keys",
                        "FrequencyCapFilters should have no more than 20 filters");
    }

    @Test
    public void testAddValidation_nullKeyedFrequencyCapReturnsViolations() {
        ImmutableList.Builder<String> violationsBuilder = ImmutableList.builder();

        mFrequencyCapAdDataValidator.addValidation(
                AdDataFixture.getValidFilterAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                FrequencyCapFiltersFixture
                                                        .getFrequencyCapFiltersWithNullCaps())
                                        .build())
                        .build(),
                violationsBuilder);

        String expectedErrorMessagePerNull =
                "FrequencyCapFilters should not contain null KeyedFrequencyCaps";
        assertWithMessage("List of violations")
                .that(violationsBuilder.build())
                .containsExactly(
                        expectedErrorMessagePerNull,
                        expectedErrorMessagePerNull,
                        expectedErrorMessagePerNull,
                        expectedErrorMessagePerNull);
    }

    @Test
    public void testAddValidation_KeyedFrequencyCapsWithNegativeFieldsReturnsViolations() {
        ImmutableList.Builder<String> violationsBuilder = ImmutableList.builder();

        Duration negativeDuration = Duration.ofSeconds(-1);

        KeyedFrequencyCap capWithNegativeFields =
                KeyedFrequencyCapFixture.getKeyedFrequencyCapWithFields(
                        /* adCounterKey= */ -1, /* maxCount= */ -1, negativeDuration);

        mFrequencyCapAdDataValidator.addValidation(
                AdDataFixture.getValidFilterAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                new FrequencyCapFilters.Builder()
                                                        .setKeyedFrequencyCapsForWinEvents(
                                                                Arrays.asList(
                                                                        capWithNegativeFields))
                                                        .build())
                                        .build())
                        .build(),
                violationsBuilder);

        assertWithMessage("List of violations")
                .that(violationsBuilder.build())
                .containsExactly(
                        String.format(
                                Locale.ENGLISH,
                                "KeyedFrequencyCap max count %d must be strictly positive",
                                -1),
                        String.format(
                                Locale.ENGLISH,
                                "KeyedFrequencyCap interval %s must be strictly positive",
                                negativeDuration));
    }

    @Test
    public void testAddValidation_KeyedFrequencyCapsWithZeroFieldsReturnsViolations() {
        ImmutableList.Builder<String> violationsBuilder = ImmutableList.builder();

        Duration zeroDuration = Duration.ofSeconds(0);

        KeyedFrequencyCap capWithNegativeFields =
                KeyedFrequencyCapFixture.getKeyedFrequencyCapWithFields(
                        /* adCounterKey= */ 0, /* maxCount= */ 0, zeroDuration);

        mFrequencyCapAdDataValidator.addValidation(
                AdDataFixture.getValidFilterAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                new FrequencyCapFilters.Builder()
                                                        .setKeyedFrequencyCapsForImpressionEvents(
                                                                Arrays.asList(
                                                                        capWithNegativeFields))
                                                        .build())
                                        .build())
                        .build(),
                violationsBuilder);

        assertWithMessage("List of violations")
                .that(violationsBuilder.build())
                .containsExactly(
                        String.format(
                                Locale.ENGLISH,
                                "KeyedFrequencyCap max count %d must be strictly positive",
                                0),
                        String.format(
                                Locale.ENGLISH,
                                "KeyedFrequencyCap interval %s must be strictly positive",
                                zeroDuration));
    }

    @Test
    public void testAddValidation_KeyedFrequencyCapsWithExcessiveIntervalReturnsViolations() {
        ImmutableList.Builder<String> violationsBuilder = ImmutableList.builder();

        Duration excessiveDuration = Duration.ofDays(100000);

        KeyedFrequencyCap capWithExcessiveInterval =
                KeyedFrequencyCapFixture.getKeyedFrequencyCapWithFields(
                        /* adCounterKey= */ 10, /* maxCount= */ 10, excessiveDuration);

        mFrequencyCapAdDataValidator.addValidation(
                AdDataFixture.getValidFilterAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                new FrequencyCapFilters.Builder()
                                                        .setKeyedFrequencyCapsForViewEvents(
                                                                Arrays.asList(
                                                                        capWithExcessiveInterval))
                                                        .build())
                                        .build())
                        .build(),
                violationsBuilder);

        assertWithMessage("List of violations")
                .that(violationsBuilder.build())
                .containsExactly(
                        String.format(
                                Locale.ENGLISH,
                                "KeyedFrequencyCap interval %s must be no greater than %s",
                                excessiveDuration,
                                KeyedFrequencyCap.MAX_INTERVAL));
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
