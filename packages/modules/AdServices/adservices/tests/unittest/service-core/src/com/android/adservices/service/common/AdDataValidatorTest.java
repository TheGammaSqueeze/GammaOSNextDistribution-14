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

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

public class AdDataValidatorTest {
    private static final FrequencyCapAdDataValidator FREQUENCY_CAP_AD_DATA_VALIDATOR =
            new FrequencyCapAdDataValidatorImpl();

    private static final AdRenderIdValidator AD_RENDER_ID_VALIDATOR =
            AdRenderIdValidator.createEnabledInstance(100);

    AdDataValidator mValidator =
            new AdDataValidator(
                    ValidatorUtil.AD_TECH_ROLE_BUYER,
                    CommonFixture.VALID_BUYER_1.toString(),
                    FREQUENCY_CAP_AD_DATA_VALIDATOR,
                    AD_RENDER_ID_VALIDATOR);

    @Test
    public void testValidAdData() {
        Assert.assertTrue(
                mValidator
                        .getValidationViolations(
                                AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1)
                                        .get(0))
                        .isEmpty());
    }

    @Test
    public void testInvalidUri() {
        String uriHost = "b.com";
        AdData adData =
                new AdData.Builder()
                        .setRenderUri(Uri.parse("https://" + uriHost + "/aaa"))
                        .setMetadata("{\"a\":1}")
                        .build();
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(adData),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        adData,
                        String.format(
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                CommonFixture.VALID_BUYER_1,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                AdDataValidator.RENDER_URI_FIELD_NAME,
                                uriHost)));
    }

    @Test
    public void testInvalidMetadata() {
        AdData adData =
                new AdData.Builder()
                        .setRenderUri(Uri.parse("https://" + CommonFixture.VALID_BUYER_1 + "/aaa"))
                        .setMetadata("invalid[json]field")
                        .build();
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(adData),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        adData,
                        String.format(
                                JsonValidator.SHOULD_BE_A_VALID_JSON,
                                AdDataValidator.AD_DATA_CLASS_NAME,
                                AdDataValidator.METADATA_FIELD_NAME)));
    }

    @Test
    public void testExceededFrequencyCapLimits() {
        AdData adDataWithExceededFrequencyCapLimits =
                AdDataFixture.getAdDataWithExceededFrequencyCapLimits(
                        CommonFixture.VALID_BUYER_1, 0);

        List<String> expectedViolations =
                List.of(
                        String.format(
                                Locale.ENGLISH,
                                "For %s, AdData should have no more than 10 ad counter keys",
                                adDataWithExceededFrequencyCapLimits),
                        String.format(
                                Locale.ENGLISH,
                                "For %s, FrequencyCapFilters should have no more than 20 filters",
                                adDataWithExceededFrequencyCapLimits));

        assertThat(mValidator.getValidationViolations(adDataWithExceededFrequencyCapLimits))
                .containsExactlyElementsIn(expectedViolations);
    }

    @Test
    public void testInvalidRenderId() {
        AdData adDataWithInvalidRenderId =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdRenderId("0123456789")
                        .build();

        AdDataValidator validator =
                new AdDataValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        CommonFixture.VALID_BUYER_1.toString(),
                        FREQUENCY_CAP_AD_DATA_VALIDATOR,
                        AdRenderIdValidator.createEnabledInstance(5));

        assertThat(validator.getValidationViolations(adDataWithInvalidRenderId)).isNotEmpty();
    }
}
