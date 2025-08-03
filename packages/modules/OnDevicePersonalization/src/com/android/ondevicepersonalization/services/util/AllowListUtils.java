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

package com.android.ondevicepersonalization.services.util;

import android.annotation.NonNull;

import java.util.Arrays;

/** A utility class to check a single entity against an allow list */
public class AllowListUtils {
    private static final String ALLOW_ALL = "*";
    private static final String SPLITTER = ",";
    private static final String PAIR_SPLITTER = ";";
    private static final String CERT_SPLITTER = ":";

    /** check if an entity is in the allow list */
    public static boolean isAllowListed(final String entityName,
                                        @NonNull final String allowList) {
        if (ALLOW_ALL.equals(allowList)) {
            return true;
        }

        if (entityName == null || entityName.trim().isEmpty()) {
            return false;
        }

        return Arrays.stream(allowList.split(SPLITTER))
                .map(String::trim)
                .anyMatch(entityInAllowList -> entityInAllowList.equals(entityName));
    }

    /** check if a pair of entities are in an allow list */
    public static boolean isPairAllowListed(
            final String first, final String firstCertDigest,
            final String second, final String secondCertDigest,
            @NonNull final String pairAllowList) {
        if (first == null || first.isBlank()
                || second == null || second.isBlank()
                || firstCertDigest == null || firstCertDigest.isBlank()
                || secondCertDigest == null || secondCertDigest.isBlank()
                || pairAllowList == null || pairAllowList.isBlank()) {
            return false;
        }

        return Arrays.stream(pairAllowList.split(SPLITTER))
                .map(String::trim)
                .anyMatch(entityInAllowList -> isPairMatch(
                        entityInAllowList, first, firstCertDigest, second, secondCertDigest));
    }

    private static boolean isPairMatch(
            String entityPairInAllowList,
            String first, String firstCertDigest,
            String second, String secondCertDigest) {
        String[] pair = entityPairInAllowList.split(PAIR_SPLITTER);
        if (pair == null || pair.length != 2 || pair[0] == null || pair[1] == null
                || pair[0].isBlank() || pair[1].isBlank()) {
            return false;
        }
        return isMatch(pair[0], first, firstCertDigest)
                && isMatch(pair[1], second, secondCertDigest);
    }

    private static boolean isMatch(
            String entityInAllowList, String entityName, String certDigest) {
        String[] entityAndCert = entityInAllowList.split(CERT_SPLITTER);
        if (entityAndCert == null) {
            return false;
        } else if (entityAndCert.length == 1 && entityAndCert[0] != null
                && !entityAndCert[0].isBlank()) {
            return entityAndCert[0].equals(entityName);
        } else if (entityAndCert.length == 2 && entityAndCert[0] != null
                && !entityAndCert[0].isBlank() && entityAndCert[1] != null
                && !entityAndCert[1].isBlank()) {
            return entityAndCert[0].equals(entityName) && entityAndCert[1].equals(certDigest);
        }
        return false;
    }
}
