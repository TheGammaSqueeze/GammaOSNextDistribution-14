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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.res.Resources;
import android.telephony.PhoneNumberUtils;
import android.util.ArraySet;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

public class ContactsIndexerPhoneNumberUtilsTest {
    Resources mResources;

    @Before
    public void setUp() {
        // Make sure the default locale in this configuration is English.
        Context context = ApplicationProvider.getApplicationContext();
        mResources = context.getResources();
    }

    @Test
    public void testFormatNational_originalFormat1() {
        String phoneNumberOriginal = "(202) 555-0111";
        String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);

        String phoneNumberInNationalFormat = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, "US");

        assertThat(phoneNumberInNationalFormat).isEqualTo("(202) 555-0111");
    }

    @Test
    public void testFormatNational_originalFormat2() {
        String phoneNumberOriginal = "202 555 0111";
        String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);

        String phoneNumberInNationalFormat = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, "US");

        assertThat(phoneNumberInNationalFormat).isEqualTo("(202) 555-0111");
    }

    @Test
    public void testFormatNational_originalFormat3() {
        String phoneNumberOriginal = "202-555-0111";
        String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);

        String phoneNumberInNationalFormat = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, "US");

        assertThat(phoneNumberInNationalFormat).isEqualTo("(202) 555-0111");
    }

    @Test
    public void testFormatNational_originalFormat4() {
        String phoneNumberOriginal = "2025550111";
        String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);

        String phoneNumberInNationalFormat = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, "US");

        assertThat(phoneNumberInNationalFormat).isEqualTo("(202) 555-0111");
    }

    @Test
    public void testFormatNational_originalFormat5() {
        String phoneNumberOriginal = "202 5550111";
        String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);

        String phoneNumberInNationalFormat = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, "US");

        assertThat(phoneNumberInNationalFormat).isEqualTo("(202) 555-0111");
    }

    @Test
    public void testFormatNational_withCountryCode_us() {
        String phoneNumberOriginal = "12025550111";
        String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);

        String phoneNumberInNationalFormat = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, "US");

        assertThat(phoneNumberInNationalFormat).isEqualTo("1 202-555-0111");
    }

    @Test
    public void testFormatNational_withCountryCode_cn() {
        String phoneNumberOriginal = "8613488833333";
        String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);

        String phoneNumberInNationalFormat = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, "CN");

        assertThat(phoneNumberInNationalFormat).isEqualTo("86 134 8883 3333");
    }

    @Test
    public void testFormatNational_withoutCountryCode() {
        String phoneNumberOriginal = "202-555-0111";
        String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);

        String phoneNumberInNationalFormat = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, "US");

        assertThat(phoneNumberInNationalFormat).isEqualTo("(202) 555-0111");
    }

    @Test
    public void testCreateFormatNational_withRightCountryCode() {
        String phoneNumberOriginal = "134 888 33333";
        String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);

        String phoneNumberInNationalFormat = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, "CN");

        assertThat(phoneNumberInNationalFormat).isEqualTo("134 8883 3333");
    }

    @Test
    public void testCreateFormatNational_withoutCountryCodePassedIn() {
        String phoneNumberOriginal = "13488833333";
        String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);

        String phoneNumberInNationalFormat = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, /*countryCode=*/ null);

        assertThat(phoneNumberInNationalFormat).isNull();
    }

    @Test
    public void testAddVariantsFromFormatNational() {
        String phoneNumberNormalized = "2025550111";
        String phoneNumberNational = ContactsIndexerPhoneNumberUtils.createFormatNational(
                phoneNumberNormalized, "US");
        Set<String> results = new ArraySet<>();

        ContactsIndexerPhoneNumberUtils.addVariantsFromFormatNational(phoneNumberNational, results);

        assertThat(results).containsExactly("(202) 555-0111", "(202) 5550111");
    }

    @Test
    public void testAddVariantsFromFormatNational_validNumber() {
        Set<String> results = new ArraySet<>();

        ContactsIndexerPhoneNumberUtils.addVariantsFromFormatNational("555 0111", results);

        assertThat(results).containsExactly("555 0111", "5550111");
    }

    @Test
    public void testAddVariantsFromFormatNational_invalidPhoneNumber() {
        Set<String> results = new ArraySet<>();

        ContactsIndexerPhoneNumberUtils.addVariantsFromFormatNational("202", results);

        assertThat(results).containsExactly("202");
    }

    // This shouldn't happen in production since we will always use the normalized number. But it
    // is good to test the function itself.
    @Test
    public void testAddVariantsFromFormatNational_invalidPhoneNumberWithLeadingSpace() {
        Set<String> results = new ArraySet<>();

        ContactsIndexerPhoneNumberUtils.addVariantsFromFormatNational(" 202", results);

        assertThat(results).containsExactly(" 202", "202");
    }

    @Test
    public void testParsePhoneNumberInE164_US() {
        String dialingCode = "+1";
        String nationalNumber = "2025550111";

        Pair<String, String> result = ContactsIndexerPhoneNumberUtils.parsePhoneNumberInE164(
                dialingCode + nationalNumber);

        assertThat(result.first).isEqualTo(dialingCode);
        assertThat(result.second).isEqualTo(nationalNumber);
    }

    @Test
    public void testParsePhoneNumberInE164_ES() {
        String dialingCode = "+34";
        String nationalNumber = "913302800";

        Pair<String, String> result = ContactsIndexerPhoneNumberUtils.parsePhoneNumberInE164(
                dialingCode + nationalNumber);

        assertThat(result.first).isEqualTo(dialingCode);
        assertThat(result.second).isEqualTo(nationalNumber);
    }

    @Test
    public void testParsePhoneNumberInE164_AR() {
        String dialingCode = "+54";
        String nationalNumber = "1148910000";

        Pair<String, String> result = ContactsIndexerPhoneNumberUtils.parsePhoneNumberInE164(
                dialingCode + nationalNumber);

        assertThat(result.first).isEqualTo(dialingCode);
        assertThat(result.second).isEqualTo(nationalNumber);
    }

    @Test
    public void testParsePhoneNumberInE164_BG() {
        String dialingCode = "+359";
        String nationalNumber = "29335000";

        Pair<String, String> result = ContactsIndexerPhoneNumberUtils.parsePhoneNumberInE164(
                dialingCode + nationalNumber);

        assertThat(result.first).isEqualTo(dialingCode);
        assertThat(result.second).isEqualTo(nationalNumber);
    }

    @Test
    public void testParsePhoneNumberInE164_invalidDialingCode() {
        String dialingCode = "+000";
        String nationalNumber = "11235";

        Pair<String, String> result = ContactsIndexerPhoneNumberUtils.parsePhoneNumberInE164(
                dialingCode + nationalNumber);

        assertThat(result).isNull();
    }

    @Test
    public void testParsePhoneNumberInE164_invalidDialingCode_noPlusSign() {
        String dialingCode = "1";
        String nationalNumber = "2025550111";

        Pair<String, String> result = ContactsIndexerPhoneNumberUtils.parsePhoneNumberInE164(
                dialingCode + nationalNumber);

        assertThat(result).isNull();
    }

    @Test
    public void testParsePhoneNumberInE164_invalidE164_noDigits() {
        Pair<String, String> result = ContactsIndexerPhoneNumberUtils.parsePhoneNumberInE164(
                "+");

        assertThat(result).isNull();
    }

    @Test
    public void testParsePhoneNumberInE164_shortNumber_oneDigit() {
        Pair<String, String> result = ContactsIndexerPhoneNumberUtils.parsePhoneNumberInE164(
                "+12");

        assertThat(result.first).isEqualTo("+1");
        assertThat(result.second).isEqualTo("2");
    }

    @Test
    public void testParsePhoneNumberInE164_shortNumber_twoDigit() {
        Pair<String, String> result = ContactsIndexerPhoneNumberUtils.parsePhoneNumberInE164(
                "+123");

        assertThat(result.first).isEqualTo("+1");
        assertThat(result.second).isEqualTo("23");
    }
}
