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

package com.android.server.connectivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.UidRange;
import android.os.Build;
import android.util.ArraySet;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tests for UidRangeUtils.
 *
 * Build, install and run with:
 *  runtest frameworks-net -c com.android.server.connectivity.UidRangeUtilsTest
 */
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
public class UidRangeUtilsTest {
    private static void assertInSameRange(@NonNull final String msg,
            @Nullable final UidRange r1,
            @Nullable final Set<UidRange> s2) {
        assertTrue(msg + " : " + s2 + " unexpectedly is not in range of " + r1,
                UidRangeUtils.isRangeSetInUidRange(r1, s2));
    }

    private static void assertNotInSameRange(@NonNull final String msg,
            @Nullable final UidRange r1, @Nullable final Set<UidRange> s2) {
        assertFalse(msg + " : " + s2 + " unexpectedly is in range of " + r1,
                UidRangeUtils.isRangeSetInUidRange(r1, s2));
    }

    @Test @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
    public void testRangeSetInUidRange() {
        final UidRange uids1 = new UidRange(1, 100);
        final UidRange uids2 = new UidRange(3, 300);
        final UidRange uids3 = new UidRange(1, 1000);
        final UidRange uids4 = new UidRange(1, 100);
        final UidRange uids5 = new UidRange(2, 20);
        final UidRange uids6 = new UidRange(3, 30);

        assertThrows(NullPointerException.class,
                () -> UidRangeUtils.isRangeSetInUidRange(null, null));
        assertThrows(NullPointerException.class,
                () -> UidRangeUtils.isRangeSetInUidRange(uids1, null));

        final ArraySet<UidRange> set1 = new ArraySet<>();
        final ArraySet<UidRange> set2 = new ArraySet<>();

        assertThrows(NullPointerException.class,
                () -> UidRangeUtils.isRangeSetInUidRange(null, set1));
        assertInSameRange("uids1 <=> empty", uids1, set2);

        set2.add(uids1);
        assertInSameRange("uids1 <=> uids1", uids1, set2);

        set2.clear();
        set2.add(uids2);
        assertNotInSameRange("uids1 <=> uids2", uids1, set2);
        set2.clear();
        set2.add(uids3);
        assertNotInSameRange("uids1 <=> uids3", uids1, set2);
        set2.clear();
        set2.add(uids4);
        assertInSameRange("uids1 <=> uids4", uids1, set2);

        set2.clear();
        set2.add(uids5);
        set2.add(uids6);
        assertInSameRange("uids1 <=> uids5, 6", uids1, set2);

        set2.clear();
        set2.add(uids2);
        set2.add(uids6);
        assertNotInSameRange("uids1 <=> uids2, 6", uids1, set2);
    }

    @Test @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
    public void testRemoveRangeSetFromUidRange() {
        final UidRange uids1 = new UidRange(1, 100);
        final UidRange uids2 = new UidRange(3, 300);
        final UidRange uids3 = new UidRange(1, 1000);
        final UidRange uids4 = new UidRange(1, 100);
        final UidRange uids5 = new UidRange(2, 20);
        final UidRange uids6 = new UidRange(3, 30);
        final UidRange uids7 = new UidRange(30, 39);

        final UidRange uids8 = new UidRange(1, 1);
        final UidRange uids9 = new UidRange(21, 100);
        final UidRange uids10 = new UidRange(1, 2);
        final UidRange uids11 = new UidRange(31, 100);

        final UidRange uids12 = new UidRange(1, 1);
        final UidRange uids13 = new UidRange(21, 29);
        final UidRange uids14 = new UidRange(40, 100);

        final UidRange uids15 = new UidRange(3, 30);
        final UidRange uids16 = new UidRange(31, 39);

        assertThrows(NullPointerException.class,
                () -> UidRangeUtils.removeRangeSetFromUidRange(null, null));
        Set<UidRange> expected = new ArraySet<>();
        expected.add(uids1);
        assertThrows(NullPointerException.class,
                () -> UidRangeUtils.removeRangeSetFromUidRange(uids1, null));
        assertEquals(expected, UidRangeUtils.removeRangeSetFromUidRange(uids1, new ArraySet<>()));

        expected.clear();
        final ArraySet<UidRange> set2 = new ArraySet<>();
        set2.add(uids1);
        assertEquals(expected, UidRangeUtils.removeRangeSetFromUidRange(uids1, set2));
        set2.clear();
        set2.add(uids4);
        assertEquals(expected, UidRangeUtils.removeRangeSetFromUidRange(uids1, set2));

        expected.add(uids10);
        set2.clear();
        set2.add(uids2);
        assertEquals(expected, UidRangeUtils.removeRangeSetFromUidRange(uids1, set2));

        expected.clear();
        set2.clear();
        set2.add(uids3);
        assertEquals(expected, UidRangeUtils.removeRangeSetFromUidRange(uids1, set2));

        set2.clear();
        set2.add(uids3);
        set2.add(uids6);
        assertThrows(IllegalArgumentException.class,
                () -> UidRangeUtils.removeRangeSetFromUidRange(uids1, set2));

        expected.clear();
        expected.add(uids8);
        expected.add(uids9);
        set2.clear();
        set2.add(uids5);
        assertEquals(expected, UidRangeUtils.removeRangeSetFromUidRange(uids1, set2));

        expected.clear();
        expected.add(uids10);
        expected.add(uids11);
        set2.clear();
        set2.add(uids6);
        assertEquals(expected, UidRangeUtils.removeRangeSetFromUidRange(uids1, set2));

        expected.clear();
        expected.add(uids12);
        expected.add(uids13);
        expected.add(uids14);
        set2.clear();
        set2.add(uids5);
        set2.add(uids7);
        assertEquals(expected, UidRangeUtils.removeRangeSetFromUidRange(uids1, set2));

        expected.clear();
        expected.add(uids10);
        expected.add(uids14);
        set2.clear();
        set2.add(uids15);
        set2.add(uids16);
        assertEquals(expected, UidRangeUtils.removeRangeSetFromUidRange(uids1, set2));
    }

    private static void assertRangeOverlaps(@NonNull final String msg,
            @Nullable final Set<UidRange> s1,
            @Nullable final Set<UidRange> s2) {
        assertTrue(msg + " : " + s2 + " unexpectedly does not overlap with " + s1,
                UidRangeUtils.doesRangeSetOverlap(s1, s2));
    }

    private static void assertRangeDoesNotOverlap(@NonNull final String msg,
            @Nullable final Set<UidRange> s1, @Nullable final Set<UidRange> s2) {
        assertFalse(msg + " : " + s2 + " unexpectedly ovelaps with " + s1,
                UidRangeUtils.doesRangeSetOverlap(s1, s2));
    }

    @Test @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
    public void testRangeSetOverlap() {
        final UidRange uids1 = new UidRange(1, 100);
        final UidRange uids2 = new UidRange(3, 300);
        final UidRange uids3 = new UidRange(1, 1000);
        final UidRange uids4 = new UidRange(1, 100);
        final UidRange uids5 = new UidRange(2, 20);
        final UidRange uids6 = new UidRange(3, 30);
        final UidRange uids7 = new UidRange(0, 0);
        final UidRange uids8 = new UidRange(1, 500);
        final UidRange uids9 = new UidRange(101, 200);

        assertThrows(NullPointerException.class,
                () -> UidRangeUtils.doesRangeSetOverlap(null, null));

        final ArraySet<UidRange> set1 = new ArraySet<>();
        final ArraySet<UidRange> set2 = new ArraySet<>();
        assertThrows(NullPointerException.class,
                () -> UidRangeUtils.doesRangeSetOverlap(set1, null));
        assertThrows(NullPointerException.class,
                () -> UidRangeUtils.doesRangeSetOverlap(null, set2));
        assertRangeDoesNotOverlap("empty <=> null", set1, set2);

        set2.add(uids1);
        set1.add(uids1);
        assertRangeOverlaps("uids1 <=> uids1", set1, set2);

        set1.clear();
        set1.add(uids1);
        set2.clear();
        set2.add(uids2);
        assertRangeOverlaps("uids1 <=> uids2", set1, set2);

        set1.clear();
        set1.add(uids1);
        set2.clear();
        set2.add(uids3);
        assertRangeOverlaps("uids1 <=> uids3", set1, set2);

        set1.clear();
        set1.add(uids1);
        set2.clear();
        set2.add(uids4);
        assertRangeOverlaps("uids1 <=> uids4", set1, set2);

        set1.clear();
        set1.add(uids1);
        set2.clear();
        set2.add(uids5);
        set2.add(uids6);
        assertRangeOverlaps("uids1 <=> uids5,6", set1, set2);

        set1.clear();
        set1.add(uids1);
        set2.clear();
        set2.add(uids7);
        assertRangeDoesNotOverlap("uids1 <=> uids7", set1, set2);

        set1.clear();
        set1.add(uids1);
        set2.clear();
        set2.add(uids9);
        assertRangeDoesNotOverlap("uids1 <=> uids9", set1, set2);

        set1.clear();
        set1.add(uids1);
        set2.clear();
        set2.add(uids8);
        assertRangeOverlaps("uids1 <=> uids8", set1, set2);


        set1.clear();
        set1.add(uids1);
        set2.clear();
        set2.add(uids8);
        set2.add(uids7);
        assertRangeOverlaps("uids1 <=> uids7, 8", set1, set2);
    }

    @Test @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
    public void testConvertListToUidRange() {
        final UidRange uids1 = new UidRange(1, 1);
        final UidRange uids2 = new UidRange(1, 2);
        final UidRange uids3 = new UidRange(100, 100);
        final UidRange uids4 = new UidRange(10, 10);

        final UidRange uids5 = new UidRange(10, 14);
        final UidRange uids6 = new UidRange(20, 24);

        final Set<UidRange> expected = new ArraySet<>();
        final List<Integer> input = new ArrayList<Integer>();

        assertThrows(NullPointerException.class, () -> UidRangeUtils.convertListToUidRange(null));
        assertEquals(expected, UidRangeUtils.convertListToUidRange(input));

        input.add(1);
        expected.add(uids1);
        assertEquals(expected, UidRangeUtils.convertListToUidRange(input));

        input.add(2);
        expected.clear();
        expected.add(uids2);
        assertEquals(expected, UidRangeUtils.convertListToUidRange(input));

        input.clear();
        input.add(1);
        input.add(100);
        expected.clear();
        expected.add(uids1);
        expected.add(uids3);
        assertEquals(expected, UidRangeUtils.convertListToUidRange(input));

        input.clear();
        input.add(100);
        input.add(1);
        expected.clear();
        expected.add(uids1);
        expected.add(uids3);
        assertEquals(expected, UidRangeUtils.convertListToUidRange(input));

        input.clear();
        input.add(100);
        input.add(1);
        input.add(2);
        input.add(1);
        input.add(10);
        expected.clear();
        expected.add(uids2);
        expected.add(uids4);
        expected.add(uids3);
        assertEquals(expected, UidRangeUtils.convertListToUidRange(input));

        input.clear();
        input.add(10);
        input.add(11);
        input.add(12);
        input.add(13);
        input.add(14);
        input.add(20);
        input.add(21);
        input.add(22);
        input.add(23);
        input.add(24);
        expected.clear();
        expected.add(uids5);
        expected.add(uids6);
        assertEquals(expected, UidRangeUtils.convertListToUidRange(input));
    }

    @Test @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
    public void testConvertArrayToUidRange() {
        final UidRange uids1_1 = new UidRange(1, 1);
        final UidRange uids1_2 = new UidRange(1, 2);
        final UidRange uids100_100 = new UidRange(100, 100);
        final UidRange uids10_10 = new UidRange(10, 10);

        final UidRange uids10_14 = new UidRange(10, 14);
        final UidRange uids20_24 = new UidRange(20, 24);

        final Set<UidRange> expected = new ArraySet<>();
        int[] input = new int[0];

        assertThrows(NullPointerException.class, () -> UidRangeUtils.convertArrayToUidRange(null));
        assertEquals(expected, UidRangeUtils.convertArrayToUidRange(input));

        input = new int[] {1};
        expected.add(uids1_1);
        assertEquals(expected, UidRangeUtils.convertArrayToUidRange(input));

        input = new int[]{1, 2};
        expected.clear();
        expected.add(uids1_2);
        assertEquals(expected, UidRangeUtils.convertArrayToUidRange(input));

        input = new int[]{1, 100};
        expected.clear();
        expected.add(uids1_1);
        expected.add(uids100_100);
        assertEquals(expected, UidRangeUtils.convertArrayToUidRange(input));

        input = new int[]{100, 1};
        expected.clear();
        expected.add(uids1_1);
        expected.add(uids100_100);
        assertEquals(expected, UidRangeUtils.convertArrayToUidRange(input));

        input = new int[]{100, 1, 2, 1, 10};
        expected.clear();
        expected.add(uids1_2);
        expected.add(uids10_10);
        expected.add(uids100_100);
        assertEquals(expected, UidRangeUtils.convertArrayToUidRange(input));

        input = new int[]{10, 11, 12, 13, 14, 20, 21, 22, 23, 24};
        expected.clear();
        expected.add(uids10_14);
        expected.add(uids20_24);
        assertEquals(expected, UidRangeUtils.convertArrayToUidRange(input));
    }

    @Test
    public void testSortRangesByStartUid() throws Exception {
        final UidRange uid1 = new UidRange(100, 110);
        final UidRange uid2 = new UidRange(120, 130);
        final UidRange[] unsortedRanges = new UidRange[] {uid2, uid1};
        final UidRange[] sortedRanges = UidRangeUtils.sortRangesByStartUid(unsortedRanges);
        assertEquals(uid1, sortedRanges[0]);
        assertEquals(uid2, sortedRanges[1]);
    }

    @Test
    public void testSortedRangesContainOverlap() throws Exception {
        final UidRange uid1 = new UidRange(100, 110);
        final UidRange uid2 = new UidRange(109, 120);
        final UidRange uid3 = new UidRange(120, 130);
        final UidRange[] overlapRanges1 = new UidRange[] {uid1, uid2};
        final UidRange[] overlapRanges2 = new UidRange[] {uid2, uid3};
        final UidRange[] notOverlapRanges = new UidRange[] {uid1, uid3};
        assertTrue(UidRangeUtils.sortedRangesContainOverlap(overlapRanges1));
        assertTrue(UidRangeUtils.sortedRangesContainOverlap(overlapRanges2));
        assertFalse(UidRangeUtils.sortedRangesContainOverlap(notOverlapRanges));
    }
}
