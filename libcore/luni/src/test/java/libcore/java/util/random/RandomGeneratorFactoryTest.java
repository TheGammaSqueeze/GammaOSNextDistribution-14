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

package libcore.java.util.random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static java.util.stream.Collectors.toSet;

import libcore.test.annotation.NonCts;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import jdk.internal.util.random.RandomSupport;


@RunWith(JUnit4.class)
public class RandomGeneratorFactoryTest {

    @Test
    public void defaultFactoryInstance() {
        RandomGeneratorFactory<RandomGenerator> defaultFactory = RandomGeneratorFactory.getDefault();

        assertNotNull(defaultFactory);

        int stateBits = defaultFactory.stateBits();
        assertTrue("stateBits() for default was " + stateBits, stateBits >= 64);
    }

    @Test
    @NonCts(reason = "Algorithm might not be available in the future")
    public void createsL64X256MixRandomInstance() {
        RandomGeneratorFactory<RandomGenerator> specificAlgoFactory =
                RandomGeneratorFactory.of("L64X256MixRandom");

        assertNotNull(specificAlgoFactory);
        // These are coming from package-summary page.
        assertEquals( "L64X256MixRandom", specificAlgoFactory.name());
        assertEquals("LXM", specificAlgoFactory.group());
        assertEquals(4, specificAlgoFactory.equidistribution());
        BigInteger expectedPeriod =
                BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE).shiftLeft(64);
        assertEquals(expectedPeriod, specificAlgoFactory.period());
        assertTrue(specificAlgoFactory.isSplittable());
        assertTrue(specificAlgoFactory.isStreamable());
    }

    @Test
    public void throwsOnUnsupported() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RandomGeneratorFactory.of("something something"));
        assertThrows(NullPointerException.class, () -> RandomGeneratorFactory.of(null));
    }

    /**
     * Goal is to ensure that META-INF/services/java.util.random.RandomGenerator is present and
     * is read.
     */
    @Test
    @NonCts(reason = "No guarantees are provided around available implementations")
    public void checkCurrentlyAvailableImplementations() {
        Set<String> actual = RandomGeneratorFactory.all()
                .map(RandomGeneratorFactory::name)
                .collect(toSet());

        Set<String> expected = Set.of(
                "L32X64MixRandom",
                "L64X128MixRandom",
                "L64X128StarStarRandom",
                "L64X256MixRandom",
                "L64X1024MixRandom",
                "L128X128MixRandom",
                "L128X256MixRandom",
                "L128X1024MixRandom",
                "Xoroshiro128PlusPlus",
                "Xoshiro256PlusPlus");

        assertEquals(expected, actual);
    }

    @Test
    public void checkConsistency_initializeWithLongSeed() {
        RandomGeneratorFactory.all()
            .forEach(RandomGeneratorFactoryTest::checkConsistencyWithLongSeed);
    }

    @Test
    public void checkConsistency_initializeWithBytesSeed() {
        RandomGeneratorFactory.all()
            .forEach(RandomGeneratorFactoryTest::checkConsistencyWithBytesSeed);
    }

    @Test
    public void isDeprecated_consistency() {
        RandomGeneratorFactory.all().forEach(factory -> {
            var rng = factory.create();
            var msg = factory.name() + " created deprecated RandomGenerator";
            assertFalse(msg, rng.isDeprecated());
        });
    }

    private static void checkConsistencyWithLongSeed(RandomGeneratorFactory rngFactory) {
        long seed = RandomSupport.initialSeed();

        System.out.println("Testing with seed=" + seed);

        checkNextBoolean(rngFactory.create(seed), rngFactory.create(seed));
        checkNextInt(rngFactory.create(seed), rngFactory.create(seed));
        checkInts(rngFactory.create(seed), rngFactory.create(seed));
        checkNextLong(rngFactory.create(seed), rngFactory.create(seed));
        checkLongs(rngFactory.create(seed), rngFactory.create(seed));
        checkNextDouble(rngFactory.create(seed), rngFactory.create(seed));
        checkDoubles(rngFactory.create(seed), rngFactory.create(seed));
    }

    private static void checkConsistencyWithBytesSeed(RandomGeneratorFactory rngFactory) {
        byte[] seedBytes = new SecureRandom().generateSeed(256);

        System.out.println("Testing with seed=" + Arrays.toString(seedBytes));

        checkNextBoolean(rngFactory.create(seedBytes),  rngFactory.create(seedBytes));
        checkNextInt(rngFactory.create(seedBytes), rngFactory.create(seedBytes));
        checkInts(rngFactory.create(seedBytes), rngFactory.create(seedBytes));
        checkNextLong(rngFactory.create(seedBytes), rngFactory.create(seedBytes));
        checkLongs(rngFactory.create(seedBytes), rngFactory.create(seedBytes));
        checkNextDouble(rngFactory.create(seedBytes), rngFactory.create(seedBytes));
        checkDoubles(rngFactory.create(seedBytes), rngFactory.create(seedBytes));
    }

     private static void checkNextBoolean(RandomGenerator first, RandomGenerator second) {
        for (int i = 0; i < 1_000; ++i) {
            boolean firstResult = first.nextBoolean();
            boolean secondResult = second.nextBoolean();
            String errorMsg = String.format("At iteration %d %s returned %b, but %s returned %b",
                                            i, first, firstResult, second, secondResult);
            assertEquals(errorMsg, firstResult, secondResult);
        }
    }

    private static void checkNextInt(RandomGenerator first, RandomGenerator second) {
        for (int i = 0; i < 1_000; ++i) {
            int firstResult = first.nextInt();
            int secondResult = second.nextInt();
            String errorMsg = String.format("At iteration %d %s returned %d, but %s returned %d",
                                            i, first, firstResult, second, secondResult);
            assertEquals(errorMsg, firstResult, secondResult);
        }
    }

    private static void checkNextLong(RandomGenerator first, RandomGenerator second) {
        for (int i = 0; i < 1_000; ++i) {
            long firstResult = first.nextLong();
            long secondResult = second.nextLong();
            String errorMsg = String.format("At iteration %d %s returned %d, but %s returned %d",
                                            i, first, firstResult, second, secondResult);
            assertEquals(errorMsg, firstResult, secondResult);
        }
    }

    private static void checkNextDouble(RandomGenerator first, RandomGenerator second) {
        for (int i = 0; i < 1_000; ++i) {
            double firstResult = first.nextDouble();
            double secondResult = second.nextDouble();
            String errorMsg = String.format("At iteration %d %s returned %f, but %s returned %f",
                                            i, first, firstResult, second, secondResult);
            assertEquals(errorMsg, firstResult, secondResult, 0.0d);
        }
    }

    private static void checkInts(RandomGenerator first, RandomGenerator second) {
        int[] firstResult = first.ints().limit(1_000).toArray();
        int[] secondResult = second.ints().limit(1_000).toArray();

        String errorMsg = String.format("%s.ints() and %s.ints() generated different sequences",
                                        first, second);
        assertArrayEquals(errorMsg, firstResult, secondResult);
    }

    private static void checkLongs(RandomGenerator first, RandomGenerator second) {
        long[] firstResult = first.longs().limit(1_000).toArray();
        long[] secondResult = second.longs().limit(1_000).toArray();

        String errorMsg = String.format("%s.longs() and %s.longs() generated different sequences",
                                        first, second);
        assertArrayEquals(errorMsg, firstResult, secondResult);
    }

    private static void checkDoubles(RandomGenerator first, RandomGenerator second) {
        double[] firstResult = first.doubles().limit(1_000).toArray();
        double[] secondResult = second.doubles().limit(1_000).toArray();

        String errorMsg = String.format("%s.doubles() and %s.doubles() generated different sequences",
                                        first, second);
        assertArrayEquals(errorMsg, firstResult, secondResult, 0.0d);
    }
}
