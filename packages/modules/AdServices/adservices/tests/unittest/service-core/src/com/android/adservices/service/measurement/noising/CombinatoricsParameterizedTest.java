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

package com.android.adservices.service.measurement.noising;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RunWith(Parameterized.class)
public class CombinatoricsParameterizedTest {
    private final int mNumBucketIncrements;
    private final int[] mPerTypeNumWindows;
    private final int[] mPerTypeCap;

    public CombinatoricsParameterizedTest(int numBucketIncrements, int[] perTypeNumWindows,
            int[] perTypeCap) {
        mNumBucketIncrements = numBucketIncrements;
        mPerTypeNumWindows = perTypeNumWindows;
        mPerTypeCap = perTypeCap;
    }

    @Parameterized.Parameters()
    public static Collection<Object[]> getTestCaseForRandomState() {
        // Test Case: {numBucketIncrements, perTypeNumWindows, perTypeCap}}
        return Arrays.asList(new Object[][] {
                {2, new int[] {2, 2}, new int[] {1, 1}},
                {3, new int[] {2, 2}, new int[] {3, 3}},
                {7, new int[] {2, 2}, new int[] {3, 3}},
                // Default parameters for existing event reporting API. Since no local limited is
                // set, just put max integer value here.
                {
                    3,
                    new int[] {3, 3, 3, 3, 3, 3, 3, 3},
                    new int[] {
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE
                    }
                },
                {4, new int[] {3, 3, 3, 3, 3, 3, 3, 3}, new int[] {3, 3, 3, 3, 3, 3, 3, 3}}
        });
    }

    @Test
    public void runTest() {
        getSingleRandomSelectReportSet_checkNoDuplication_success(
                mNumBucketIncrements, mPerTypeNumWindows, mPerTypeCap);
        getSingleRandomSelectReportSet_checkReportSetsMeetRequirement_success(
                mNumBucketIncrements, mPerTypeNumWindows, mPerTypeCap);
    }

    private static void getSingleRandomSelectReportSet_checkNoDuplication_success(
            int numBucketIncrements, int[] perTypeNumWindows, int[] perTypeCap) {
        Map<List<Integer>, Long> dp = new HashMap<>();
        ArrayList<List<Combinatorics.AtomReportState>> allReportSets =
                new ArrayList<>();
        long numberStates =
                Combinatorics.getNumStatesFlexApi(
                        numBucketIncrements, perTypeNumWindows, perTypeCap);
        for (long i = 0; i < numberStates; i++) {
            List<Combinatorics.AtomReportState> ithSet =
                    Combinatorics.getReportSetBasedOnRank(
                            numBucketIncrements, perTypeNumWindows, perTypeCap, i, dp);
            Collections.sort(ithSet, new AtomReportStateComparator());
            allReportSets.add(ithSet);
        }
        HashSet<List<Combinatorics.AtomReportState>> set =
                new HashSet<>(allReportSets);
        assertEquals(allReportSets.size(), set.size());
    }

    private static void getSingleRandomSelectReportSet_checkReportSetsMeetRequirement_success(
            int numBucketIncrements, int[] perTypeNumWindows, int[] perTypeCap) {
        Map<List<Integer>, Long> dp = new HashMap<>();
        long numberStates =
                Combinatorics.getNumStatesFlexApi(
                        numBucketIncrements, perTypeNumWindows, perTypeCap);
        for (long i = 0; i < numberStates; i++) {
            List<Combinatorics.AtomReportState> ithSet =
                    Combinatorics.getReportSetBasedOnRank(
                            numBucketIncrements, perTypeNumWindows, perTypeCap, i, dp);
            assertTrue(
                    atomReportStateSetMeetRequirement(
                            numBucketIncrements, perTypeNumWindows, perTypeCap, ithSet));
        }
    }

    private static boolean atomReportStateSetMeetRequirement(
            int totalCap,
            int[] perTypeNumWindowList,
            int[] perTypeCapList,
            List<Combinatorics.AtomReportState> reportSet) {
        // if number of report over max reports
        if (reportSet.size() > totalCap) {
            return false;
        }
        int[] perTypeReportList = new int[perTypeCapList.length];
        // Initialize all elements to zero
        for (int i = 0; i < perTypeCapList.length; i++) {
            perTypeReportList[i] = 0;
        }
        for (Combinatorics.AtomReportState report : reportSet) {
            int triggerDataIndex = report.getTriggerDataType();
            // if the report window larger than total report window of this trigger data
            // input perTypeNumWindowList is [3,3,3], and report windows index is 4, return false
            if (report.getWindowIndex() + 1 > perTypeNumWindowList[triggerDataIndex]) {
                return false;
            }
            perTypeReportList[triggerDataIndex]++;
            // number of report for this trigger data over the per data limit
            if (perTypeCapList[triggerDataIndex] < perTypeReportList[triggerDataIndex]) {
                return false;
            }
        }
        return true;
    }

    private static class AtomReportStateComparator
            implements Comparator<Combinatorics.AtomReportState> {
        @Override
        public int compare(Combinatorics.AtomReportState o1, Combinatorics.AtomReportState o2) {
            if (o1.getTriggerDataType() != o2.getTriggerDataType()) {
                return Integer.compare(o1.getTriggerDataType(), o2.getTriggerDataType());
            }
            return Integer.compare(o1.getWindowIndex(), o2.getWindowIndex());
        }
    }
}
