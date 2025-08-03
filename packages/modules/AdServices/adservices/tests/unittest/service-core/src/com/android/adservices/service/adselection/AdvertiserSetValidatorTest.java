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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.ValidatorTestUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;

@RunWith(MockitoJUnitRunner.class)
public class AdvertiserSetValidatorTest {
    @Mock private AdTechIdentifierValidator mAdTechIdentifierValidator;

    private AdvertiserSetValidator mValidator;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mValidator = new AdvertiserSetValidator(mAdTechIdentifierValidator);
    }

    @Test
    public void testFailureVerifyNullAdvertiserSet() {
        assertThrows(NullPointerException.class, () -> mValidator.validate(null));
        verifyZeroInteractions(mAdTechIdentifierValidator);
    }

    @Test
    public void testFailureVerifyTooBigAdvertiserSet() {
        HashSet<AdTechIdentifier> advertisers = new HashSet<>();
        for (int host = 10, bytes = 0;
                bytes <= AdvertiserSetValidator.MAX_TOTAL_SIZE_BYTES;
                host++) {
            AdTechIdentifier toAdd = AdTechIdentifier.fromString(host + ".com");
            bytes += toAdd.toString().getBytes(StandardCharsets.UTF_8).length;
            advertisers.add(toAdd);
        }
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(advertisers));
        ValidatorTestUtil.assertValidationFailuresMatch(
                thrown,
                "",
                Collections.singletonList(
                        String.format(
                                Locale.US,
                                AdvertiserSetValidator.SET_SHOULD_NOT_EXCEED_MAX_SIZE,
                                AdvertiserSetValidator.MAX_TOTAL_SIZE_BYTES)));
    }

    @Test
    public void testSuccessValidSet() {
        HashSet<AdTechIdentifier> advertisers = new HashSet<>();
        advertisers.add(CommonFixture.VALID_BUYER_1);
        advertisers.add(CommonFixture.VALID_BUYER_2);
        mValidator.validate(advertisers);
        verify(mAdTechIdentifierValidator)
                .addValidation(eq(CommonFixture.VALID_BUYER_1.toString()), any());
        verify(mAdTechIdentifierValidator)
                .addValidation(eq(CommonFixture.VALID_BUYER_2.toString()), any());
    }

    @Test
    public void testSuccessVerifyEmptyAdvertiserSet() {
        mValidator.validate(Collections.emptySet());
        verifyZeroInteractions(mAdTechIdentifierValidator);
    }
}
