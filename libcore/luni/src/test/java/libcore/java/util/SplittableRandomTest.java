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

package libcore.java.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator.SplittableGenerator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SplittableRandomTest {

    @Test
    public void testNextBoolean() {
        long seed = 0x1234567890L;
        SplittableRandom random1 = new SplittableRandom(seed);
        SplittableRandom random2 = new SplittableRandom(seed);

        assertGeneratorsAreEquals(random1, random2);
    }

    @Test
    public void split_throwsNPE_whenSourceIsNull() {
        SplittableRandom random = new SplittableRandom(42);

        assertThrows(NullPointerException.class, () -> random.split(/* source= */ null));
        assertThrows(NullPointerException.class, () -> random.splits(1, /* source= */ null));
        assertThrows(NullPointerException.class, () -> random.splits(/* source= */ null));
    }

    @Test
    public void split_throwsIAE_whenSizeIsNonPositive() {
        SplittableRandom random = new SplittableRandom(42);

        assertThrows(IllegalArgumentException.class, () -> random.splits(-1, random));
        assertThrows(IllegalArgumentException.class, () -> random.splits(-1));
    }

    @Test
    public void splits_streamOfSizeZero_areEmpty() {
        var random = new SplittableRandom();

        assertEquals(0L, random.splits(0).count());
        assertEquals(0L, random.splits(0, random).count());
    }

    @Test
    public void splitInstances_areTheSame_whenTheyAreSplitWithIdenticalSource() {
        var seed = 1001;
        var random1 = new SplittableRandom(seed);
        var random2 = new SplittableRandom(seed);

        var sourceSeed = 9999;

        var splitRandom1 = random1.split(new SplittableRandom(sourceSeed));
        var splitRandom2 = random2.split(new SplittableRandom(sourceSeed));

        assertGeneratorsAreEquals(splitRandom1, splitRandom2);
    }

    @Test
    public void splitsInstances_areTheSame_whenTheyAreSplitWithIdenticalSource_boundedStream() {
        var seed = 1001;

        var random1 = new SplittableRandom(seed);
        var random2 = new SplittableRandom(seed);

        var sourceSeed = 9999;
        var size = 10;

        var splitRandom1 = random1.splits(size, new SplittableRandom(sourceSeed)).toList();
        var splitRandom2 = random2.splits(size, new SplittableRandom(sourceSeed)).toList();

        assertEquals(size, splitRandom1.size());
        assertEquals(size, splitRandom2.size());

        for (int i = 0; i < size; ++i) {
            assertGeneratorsAreEquals(splitRandom1.get(i), splitRandom2.get(i));
        }
    }


    @Test
    public void splitsInstances_areTheSame_whenTheyAreSplitWithIdenticalSource_unboundedStream() {
        var seed = 1001;

        var random1 = new SplittableRandom(seed);
        var random2 = new SplittableRandom(seed);

        var sourceSeed = 9999;
        var size = 10;

        var splitRandom1 = random1.splits(new SplittableRandom(sourceSeed)).limit(size).toList();
        var splitRandom2 = random2.splits(new SplittableRandom(sourceSeed)).limit(size).toList();

        assertEquals(size, splitRandom1.size());
        assertEquals(size, splitRandom2.size());

        for (int i = 0; i < size; ++i) {
            assertGeneratorsAreEquals(splitRandom1.get(i), splitRandom2.get(i));
        }
    }

    @Test
    public void splitsInstances_areTheSame_whenSourceIsIdentical() {
        var seed = 1001;

        var random1 = new SplittableRandom(seed);
        var random2 = new SplittableRandom(seed);

        var size = 10;

        var splitRandom1 = random1.splits(size).toList();
        var splitRandom2 = random2.splits(size).toList();

        assertEquals(size, splitRandom1.size());
        assertEquals(size, splitRandom2.size());

        for (int i = 0; i < size; ++i) {
            assertGeneratorsAreEquals(splitRandom1.get(i), splitRandom2.get(i));
        }
    }

    private static void assertGeneratorsAreEquals(SplittableGenerator random1,
                                                  SplittableGenerator random2) {
        for (int i = 0; i < 1_000; ++i) {
            assertEquals(random1.nextLong(), random2.nextLong());
        }
    }

}
