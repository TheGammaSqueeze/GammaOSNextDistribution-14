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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.Set;

/**
 * Class to provide utilities to handle phone numbers.
 *
 * @hide
 */
public class ContactsIndexerPhoneNumberUtils {
    // 3 digits international calling code and the leading "+". E.g. "+354" for Iceland.
    // So maximum 4 characters total.
    @VisibleForTesting
    static final int DIALING_CODE_WITH_PLUS_SIGN_MAX_DIGITS = 4;
    private static final String TAG = "ContactsIndexerPhoneNumberUtils";

    /**
     * Creates different phone number variants for the given phone number.
     *
     * <p>The different formats we will try are the normalized format, national format and its
     * variants, and the E164 representation.
     *
     * <p>The locales on the current system configurations will be used to determine the e164
     * representation and the country code used for national format.
     *
     * <p>This method is doing best effort to generate those variants, which are nice to have.
     * Depending on the format original phone number is using, and the locales on the system, it may
     * not be able to produce all the variants.
     *
     * @param resources           the application's resource
     * @param phoneNumberOriginal the phone number in the original form from CP2.
     * @param phoneNumberFromCP2InE164 the phone number in e164 from {@link Phone#NORMALIZED_NUMBER}
     * @return a set containing different phone variants created.
     */
    @NonNull
    public static Set<String> createPhoneNumberVariants(@NonNull Resources resources,
            @NonNull String phoneNumberOriginal, @Nullable String phoneNumberFromCP2InE164) {
        Objects.requireNonNull(resources);
        Objects.requireNonNull(phoneNumberOriginal);

        Set<String> phoneNumberVariants = new ArraySet<>();
        try {
            // Normalize the phone number. It may or may not include country code, depending on
            // the original phone number.
            // With country code: "1 (202) 555-0111" -> "12025550111"
            //                     "+1 (202) 555-0111" -> "+12025550111"
            // Without country code: "(202) 555-0111" -> "2025550111"
            String phoneNumberNormalized = PhoneNumberUtils.normalizeNumber(phoneNumberOriginal);
            if (TextUtils.isEmpty(phoneNumberNormalized)) {
                return phoneNumberVariants;
            }
            phoneNumberVariants.add(phoneNumberNormalized);

            String phoneNumberInE164 = phoneNumberFromCP2InE164;
            if (TextUtils.isEmpty(phoneNumberInE164)) {
                if (!phoneNumberNormalized.startsWith("+")) {
                    // e164 format is not provided by CP2 and the normalized phone number isn't
                    // in e164 either. Nothing more can be done. Just return.
                    return phoneNumberVariants;
                }
                // e164 form is not provided by CP2, but the original phone number is likely
                // to be in e164.
                phoneNumberInE164 = phoneNumberNormalized;
            }
            phoneNumberInE164 = PhoneNumberUtils.normalizeNumber(phoneNumberInE164);
            phoneNumberVariants.add(phoneNumberInE164);

            // E.g. "+12025550111" will be split into dialingCode "+1" and phoneNumberNormalized
            // without country code: "2025550111".
            Pair<String, String> result = parsePhoneNumberInE164(phoneNumberInE164);
            if (result == null) {
                return phoneNumberVariants;
            }
            String dialingCode = result.first;
            // "+1" -> "US"
            String isoCountryCode = CountryCodeUtils.COUNTRY_TO_REGIONAL_CODE.get(dialingCode);
            if (TextUtils.isEmpty(isoCountryCode)) {
                return phoneNumberVariants;
            }
            String phoneNumberNormalizedWithoutCountryCode = result.second;
            phoneNumberVariants.add(phoneNumberNormalizedWithoutCountryCode);
            // create phone number in national format, and generate variants based on it.
            String nationalFormat = createFormatNational(phoneNumberNormalizedWithoutCountryCode,
                    isoCountryCode);
            // lastly, we want to index a national format with a country dialing code:
            // E.g. for (202) 555-0111, we also want to index "1 (202) 555-0111". So when the query
            // is "1 202" or "1 (202)", a match can still be returned.
            if (TextUtils.isEmpty(nationalFormat)) {
                return phoneNumberVariants;
            }
            addVariantsFromFormatNational(nationalFormat, phoneNumberVariants);

            // Put dialing code without "+" at the front of the national format(e.g. (202)
            // 555-0111) so we can index something like "1 (202) 555-0111". With this, we can
            // support more search queries starting with the international dialing code.
            phoneNumberVariants.add(dialingCode.substring(1) + " " + nationalFormat);
        } catch (Throwable t) {
            Log.w(TAG, "Exception thrown while creating phone variants.", t);
        }
        return phoneNumberVariants;
    }

    /**
     * Parses a phone number in e164 format.
     *
     * @return a pair of dialing code and a normalized phone number without the dialing code. E.g.
     * for +12025550111, this function returns "+1" and "2025550111". {@code null} if phone number
     * is not in a valid e164 form.
     */
    @Nullable
    static Pair<String, String> parsePhoneNumberInE164(@NonNull String phoneNumberInE164) {
        Objects.requireNonNull(phoneNumberInE164);

        if (!phoneNumberInE164.startsWith("+")) {
            return null;
        }
        // For e164, the calling code has maximum 3 digits, and it should start with '+' like
        // "+12025550111".
        int len = Math.min(DIALING_CODE_WITH_PLUS_SIGN_MAX_DIGITS, phoneNumberInE164.length());
        for (int i = 2; i <= len; ++i) {
            String possibleCodeWithPlusSign = phoneNumberInE164.substring(0, i);
            if (CountryCodeUtils.COUNTRY_DIALING_CODE.contains(possibleCodeWithPlusSign)) {
                return new Pair<>(possibleCodeWithPlusSign, phoneNumberInE164.substring(i));
            }
        }

        return null;
    }

    /**
     * Creates a national phone format based on a normalized phone number.
     *
     * <p>For a normalized phone number 2025550111, the national format will be (202) 555-0111 with
     * country code "US".
     *
     * @param phoneNumberNormalized normalized number. E.g. for phone number 202-555-0111, its
     *                              normalized form would be 2025550111.
     * @param countryCode           the country code to be used to format the phone number. If it is
     *                              {@code null}, it will try the country codes from the locales in
     *                              the configuration and return the first match.
     * @return the national format of the phone number. {@code null} if {@code countryCode} is
     * {@code null}.
     */
    @Nullable
    static String createFormatNational(@NonNull String phoneNumberNormalized,
            @Nullable String countryCode) {
        Objects.requireNonNull(phoneNumberNormalized);

        if (TextUtils.isEmpty(countryCode)) {
            return null;
        }
        return PhoneNumberUtils.formatNumber(phoneNumberNormalized, countryCode);
    }

    /**
     * Adds the variants generated from the phone number in national format into the given
     * set.
     *
     * <p>E.g. for national format (202) 555-0111, we will add itself as a variant, as well as (202)
     * 5550111 by removing the hyphen(last non-digit character).
     *
     * @param phoneNumberNational phone number in national format. E.g. (202)-555-0111
     * @param phoneNumberVariants set to hold the generated variants.
     */
    static void addVariantsFromFormatNational(@Nullable String phoneNumberNational,
            @NonNull Set<String> phoneNumberVariants) {
        Objects.requireNonNull(phoneNumberVariants);

        if (TextUtils.isEmpty(phoneNumberNational)) {
            return;
        }
        phoneNumberVariants.add(phoneNumberNational);
        // Remove the last non-digit character from the national format. So "(202) 555-0111"
        // becomes "(202) 5550111". And query "5550" can return the expected result.
        int i;
        for (i = phoneNumberNational.length() - 1; i >= 0; --i) {
            char c = phoneNumberNational.charAt(i);
            // last non-digit character in the national format.
            if (c < '0' || c > '9') {
                break;
            }
        }
        if (i >= 0) {
            phoneNumberVariants.add(
                    phoneNumberNational.substring(0, i) + phoneNumberNational.substring(i + 1));
        }
    }
}
