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

package com.android.server.appsearch.contactsindexer;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

public class CountryCodeUtilsTest {
    @Test
    public void testCountryToRegionalCode_generatedCorrectly() {
        Map<Integer, List<String>> original =
                CountryCodeToRegionCodeMap.getCountryCodeToRegionCodeMap();

        assertThat(CountryCodeUtils.COUNTRY_TO_REGIONAL_CODE).hasSize(original.size());
        assertThat(CountryCodeUtils.COUNTRY_DIALING_CODE).hasSize(original.size());

        for (String dialingCode : CountryCodeUtils.COUNTRY_DIALING_CODE) {
            int code = Integer.parseInt(dialingCode.substring(1));
            assertThat(CountryCodeUtils.COUNTRY_TO_REGIONAL_CODE.get(dialingCode)).isEqualTo(
                    original.get(code).get(0));
        }
    }

    @Test
    public void testCountryToRegionalCode_dialingCode_hasMaximum_threeDigits() {
        for (String dialingCode : CountryCodeUtils.COUNTRY_DIALING_CODE) {
            assertThat(dialingCode.length()).isAtMost(
                    ContactsIndexerPhoneNumberUtils.DIALING_CODE_WITH_PLUS_SIGN_MAX_DIGITS);
        }
    }

    // There is no such case that, one dialing code is the prefix of the other.
    @Test
    public void testCountryToRegionalCode_dialingCode_noPrefixes() {
        for (String dialingCode : CountryCodeUtils.COUNTRY_DIALING_CODE) {
            for (int i = 1; i < dialingCode.length(); ++i) {
                assertThat(CountryCodeUtils.COUNTRY_DIALING_CODE).doesNotContain(
                        dialingCode.substring(0, i));
            }
        }
    }
}
