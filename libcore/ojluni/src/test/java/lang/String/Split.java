/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package test.java.lang.String;

/**
 * @test
 * @bug 6840246 6559590
 * @summary test String.split()
 * @key randomness
 */
import java.util.Arrays;
import java.util.Random;
import java.util.regex.*;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

// Android-changed: migrate to testng
public class Split {

    private static final String SOURCE = "0123456789";
    private static final String FAST_PATH_SOURCE = "0123456789abcdefgABCDEFG";
    private static Random R;

    @BeforeClass
    public static void setUp() {
        R = new Random();
    }

    @Test
    public void testSplit_byDigit() {
        for (int limit=-2; limit<3; limit++) {
            for (int x=0; x<10; x++) {
                String[] result = SOURCE.split(Integer.toString(x), limit);
                int expectedLength = limit < 1 ? 2 : limit;

                if ((limit == 0) && (x == 9)) {
                    // expected dropping of ""
                    if (result.length != 1)
                        throw new RuntimeException("String.split failure 1");
                    if (!result[0].equals("012345678")) {
                        throw new RuntimeException("String.split failure 2");
                    }
                } else {
                    if (result.length != expectedLength) {
                        throw new RuntimeException("String.split failure 3");
                    }
                    if (!result[0].equals(SOURCE.substring(0,x))) {
                        if (limit != 1) {
                            throw new RuntimeException(
                                "String.split failure 4");
                        } else {
                            if (!result[0].equals(SOURCE.substring(0,10))) {
                                throw new RuntimeException(
                                    "String.split failure 10");
                            }
                        }
                    }
                    if (expectedLength > 1) { // Check segment 2
                        if (!result[1].equals(SOURCE.substring(x+1,10)))
                            throw new RuntimeException("String.split failure 5");
                    }
                }
            }
        }
    }

    @Test
    public void testSplit_noMatch() {
        for (int limit=-2; limit<3; limit++) {
            String[] result = SOURCE.split("e", limit);
            if (result.length != 1)
                throw new RuntimeException("String.split failure 6");
            if (!result[0].equals(SOURCE))
                throw new RuntimeException("String.split failure 7");
        }
    }

    // Check the case for limit == 0, source = "";
    // split() now returns 0-length for empty source "" see #6559590
    @Test
    public void testSplit_zeroLimit_emptySource() {
        String source = "";
        String[] result = source.split("e", 0);
        if (result.length != 1)
            throw new RuntimeException("String.split failure 8");
        if (!result[0].equals(source))
            throw new RuntimeException("String.split failure 9");
    }

    /**
     * Tests fastpath of {@link java.lang.String#split(String, int)}.
     *
     * This test is disabled and just provides explanation of testSplitFastPath_shard*
     * test functions family. Originally, this test was running around 50 seconds and
     * was doing the following:
     *
     * <pre>
     * for (boolean doEscape: new boolean[] {false, true}) {
     *     for (int cp = 0; cp < 0x11000; cp++) {
     *         ...
     *     }
     * }
     * </pre>
     *
     * To make it faster it was sharded into five shards:
     *
     * 1) First shard tests {@code doEscape = false} and {@code cp = [0, 0x11000)}, i.e. full range.
     *    It was accounted for ~10% of all time (~5s), so the whole range goes in single shard.
     * 2-5) These four shards test {@code doEscape = true} and {@code cp} split in four equal
     *      intervals.
     *
     * There is also {@link #getShardRange(int)} helper function which returns shard range.
     */
    @Test(enabled = false)
    public void testSplit_fastPath() {
    }

    @Test
    public void testSplit_fastPath_shard1_noEscape() {
        testSplit_fastPath_(FAST_PATH_SOURCE, R, false, 0, 0x11000);
    }

    @Test
    public void testSplit_fastPath_shard2() {
        int[] shardRange = getShardRange(0);
        testSplit_fastPath_(FAST_PATH_SOURCE, R, true, shardRange[0], shardRange[1]);
    }

    @Test
    public void testSplit_fastPath_shard3() {
        int[] shardRange = getShardRange(1);
        testSplit_fastPath_(FAST_PATH_SOURCE, R, true, shardRange[0], shardRange[1]);
    }

    @Test
    public void testSplit_fastPath_shard4() {
        int[] shardRange = getShardRange(2);
        testSplit_fastPath_(FAST_PATH_SOURCE, R, true, shardRange[0], shardRange[1]);
    }

    @Test
    public void testSplit_fastPath_shard5() {
        int[] shardRange = getShardRange(3);
        testSplit_fastPath_(FAST_PATH_SOURCE, R, true, shardRange[0], shardRange[1]);
    }

    /**
     * Calculates shard range, i.e. [start, end) by shard index,
     * assuming there are 4 shards in total; and the whole range spans from 0 to 0x11000.
     *
     * @param shardIndex index of the
     * @return int[] of size 2: [start, end).
     */
    private static int[] getShardRange(int shardIndex) {
        final int SHARD_LENGTH = 0x11000;
        final int SHARD_COUNT = 4;
        int shardLength = SHARD_LENGTH / SHARD_COUNT;
        int shardStart = shardIndex * shardLength;
        int shardEnd = (shardIndex + 1) * shardLength - 1;
        return new int[]{ shardStart, shardEnd };
    }

    private void testSplit_fastPath_(String source, Random r, boolean doEscape, int cpFrom, int cpTo) {
        for (int cp = cpFrom; cp < cpTo; cp++) {
            Pattern p = null;
            String regex = new String(Character.toChars(cp));
            if (doEscape)
                regex = "\\" + regex;
            try {
                p = Pattern.compile(regex);
            } catch (PatternSyntaxException pse) {
                // illegal syntax
                try {
                    "abc".split(regex);
                } catch (PatternSyntaxException pse0) {
                    continue;
                }
                throw new RuntimeException("String.split failure 11");
            }
            int off = r.nextInt(source.length());
            String[] srcStrs = new String[] {
                "",
                source,
                regex + source,
                source + regex,
                source.substring(0, 3)
                    + regex + source.substring(3, 9)
                    + regex + source.substring(9, 15)
                    + regex + source.substring(15),
                source.substring(0, off) + regex + source.substring(off)
            };
            for (String src: srcStrs) {
                for (int limit=-2; limit<3; limit++) {
                    if (!Arrays.equals(src.split(regex, limit),
                        p.split(src, limit)))
                        throw new RuntimeException("String.split failure 12");
                }
            }
        }
    }
}
