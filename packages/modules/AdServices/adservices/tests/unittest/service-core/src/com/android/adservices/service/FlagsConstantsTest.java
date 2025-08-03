/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.service;

import static com.android.adservices.flags.Flags.FLAG_ADSERVICES_OUTCOMERECEIVER_R_API_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_ID_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_FLEDGE_CUSTOM_AUDIENCE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_FLEDGE_SERVER_AUCTION_MULTI_CLOUD_ENABLED;

import android.util.Log;
import android.util.Pair;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.internal.util.Preconditions;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class FlagsConstantsTest extends AdServicesUnitTestCase {

    private static final String ACONFIG_PREFIX = "com.android.adservices.flags.";

    private static final List<String> MISSING_FLAGS_ALLOWLIST =
            List.of(
                    // FLAG_ADSERVICES_OUTCOMERECEIVER_R_API_ENABLED guards APIs that are overloaded
                    // to take android.adservices.common.OutcomeReceiver (instead of
                    // android.os.OutcomeReceiver) and hence don't need to be checked at runtime.
                    FLAG_ADSERVICES_OUTCOMERECEIVER_R_API_ENABLED,

                    // TODO(b/323888604): fix it
                    FLAG_FLEDGE_SERVER_AUCTION_MULTI_CLOUD_ENABLED,

                    // TODO(b/323297322): fix it
                    FLAG_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED,

                    // TODO(b/323397060);
                    FLAG_FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_ID_ENABLED,

                    // TODO(b/320786372): fix it
                    FLAG_FLEDGE_CUSTOM_AUDIENCE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED

                    // Add more flags below, either explaining to reason or using a TODO(b/BUG) that
                    // will
                    // add the PhFlags / FlagsConstant counterpart
                    );

    private static final Map<String, String> NON_CANONICAL_FLAGS =
            // Flag names on FlagsConstants are expect to have the same name (minus prefix) as the
            // aconfig counterpart, but there are a few exceptions like:
            // - DeviceConfig flag already pushed to production
            // - Same DeviceConfig flag is guarding multiple APIs using different @FlaggedApi
            Map.of(
                    // Add more flags below, either explaining to reason or using a TODO(b/BUG) that
                    // will add the PhFlags / FlagsConstant counterpart
                    );

    @Test
    public void testNoFlagHasTheAConfigPrefix() throws Exception {
        for (Pair<String, String> constant : getAllFlagNameConstants(FlagsConstants.class)) {
            String name = constant.first;
            String value = constant.second;
            expect.withMessage(
                            "Value (%s) of constants %s starts with prefix %s",
                            value, name, ACONFIG_PREFIX)
                    .that(value.startsWith(ACONFIG_PREFIX))
                    .isFalse();
        }
    }

    @Test
    public void testAllAconfigFlagsAreMapped() throws Exception {
        Map<String, String> reverseServiceFlags =
                getAllFlagNameConstants(FlagsConstants.class).stream()
                        .collect(Collectors.toMap(p -> p.second, p -> p.first));
        List<String> missingFlags = new ArrayList<>();
        for (Pair<String, String> constant :
                getAllFlagNameConstants(com.android.adservices.flags.Flags.class)) {
            String constantName = constant.first;
            String aconfigFlag = constant.second;
            String expectedDeviceConfigFlag = getExpectedDeviceConfigFlag(aconfigFlag);
            String serviceConstant = reverseServiceFlags.get(expectedDeviceConfigFlag);
            if (serviceConstant == null) {
                if (MISSING_FLAGS_ALLOWLIST.contains(aconfigFlag)) {
                    Log.i(
                            mTag,
                            "Missing mapping for allowlisted flag ("
                                    + constantName
                                    + "="
                                    + aconfigFlag
                                    + ")");
                } else {
                    Log.e(mTag, "Missing mapping for " + constantName + "=" + aconfigFlag);
                    missingFlags.add(expectedDeviceConfigFlag);
                }
            } else {
                Log.d(mTag, "Found mapping: " + constantName + "->" + serviceConstant);
            }
        }
        expect.withMessage("aconfig flags missing counterpart on FlagsConstants")
                .that(missingFlags)
                .isEmpty();
    }

    private static String aconfigToDeviceConfig(String flag) {
        Preconditions.checkArgument(
                Objects.requireNonNull(flag).startsWith(ACONFIG_PREFIX),
                "Flag doesn't start with %s: %s",
                ACONFIG_PREFIX,
                flag);
        return flag.substring(ACONFIG_PREFIX.length());
    }

    private String getExpectedDeviceConfigFlag(String aconfigFlag) {
        String nonCanonical = NON_CANONICAL_FLAGS.get(aconfigFlag);
        if (nonCanonical != null) {
            Log.i(mTag, "Returning non-canonical flag for " + aconfigFlag + ": " + nonCanonical);
            return nonCanonical;
        }
        return aconfigToDeviceConfig(aconfigFlag);
    }

    private static List<Pair<String, String>> getAllFlagNameConstants(Class<?> clazz)
            throws IllegalAccessException {
        List<Pair<String, String>> constants = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && (field.getType().equals(String.class))) {
                String name = field.getName();
                if (name.startsWith("KEY_") || name.startsWith("FLAG_")) {
                    String value = (String) field.get(null);
                    constants.add(new Pair<>(name, value));
                }
            }
        }
        return constants;
    }
}
