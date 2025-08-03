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

package com.android.adservices.service.stats;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DestinationRegisteredBeaconsReportedStatsTest {
    @Test
    public void testGetInteractionKeySizeRangeTypeList_SuccessCorrectInput() {
        // Tests getInteractionKeySizeRangeTypeList return correct list.
        Set<String> testKeySet = new HashSet<>(Arrays.asList(
                "abcde", "ab", null, "abcdeabcde", "abcdeabcdeabcde"));
        long maxInteractionKeySize = 10;
        List<DestinationRegisteredBeaconsReportedStats.InteractionKeySizeRangeType>
                testInteractionKeySizeRangeTypeList =
                DestinationRegisteredBeaconsReportedStats
                        .getInteractionKeySizeRangeTypeList(testKeySet, maxInteractionKeySize);
        // The maximum interaction key size is 10, so:
        // "abcde" should be "SMALLER_THAN_MAXIMUM_KEY_SIZE"
        // "ab" should be "MUCH_SMALLER_THAN_MAXIMUM_KEY_SIZE"
        // null should be "UNSET_TYPE"
        // "abcdeabcde" should be "EQUAL_TO_MAXIMUM_KEY_SIZE"
        // "abcdeabcdeabcde" should be "LARGER_THAN_MAXIMUM_KEY_SIZE"
        assertThat(testInteractionKeySizeRangeTypeList).containsExactly(
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .SMALLER_THAN_MAXIMUM_KEY_SIZE,
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .MUCH_SMALLER_THAN_MAXIMUM_KEY_SIZE,
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .UNSET_TYPE,
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .EQUAL_TO_MAXIMUM_KEY_SIZE,
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .LARGER_THAN_MAXIMUM_KEY_SIZE
        );
    }

    @Test
    public void testGetInteractionKeySizeRangeTypeList_nullKeySet() {
        // Tests getInteractionKeySizeRangeTypeList returns empty list by given null key set.
        long maxInteractionKeySize = 5;
        assertThat(
                DestinationRegisteredBeaconsReportedStats.getInteractionKeySizeRangeTypeList(
                        null, maxInteractionKeySize)).isEmpty();
    }

    @Test
    public void testGetInteractionKeySizeRangeTypeList_negativeMaxKeySize() {
        // Tests getInteractionKeySizeRangeTypeList returns empty list
        // by given negative maxInteractionKeySize.
        Set<String> testKeySet = new HashSet<>(Arrays.asList("abcde", "ab"));
        long maxInteractionKeySize = -1;
        assertThat(
                DestinationRegisteredBeaconsReportedStats.getInteractionKeySizeRangeTypeList(
                        testKeySet, maxInteractionKeySize)).isEmpty();
    }
}
