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

package libcore.java.util;

import static org.junit.Assert.assertEquals;

import static java.util.stream.Collectors.toSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class ArbitrarilyJumpableGeneratorTest {

    private static final class StubGenerator
            implements RandomGenerator.ArbitrarilyJumpableGenerator {

        private double state;

        StubGenerator(double state) {
            this.state = state;
        }

        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public double jumpDistance() {
            return 1.0d;
        }

        @Override
        public double leapDistance() {
            return 2.0d;
        }

        @Override
        public ArbitrarilyJumpableGenerator copy() {
            return new StubGenerator(state);
        }

        @Override
        public void jumpPowerOfTwo(int logDistance) {
            state += Math.pow(2, logDistance);
        }

        @Override
        public void jump(double distance) {
            state += distance;
        }
    }

    @Test
    public void jump_jumpsToJumpDistance() {
        double initState = 10.0d;
        StubGenerator rng = new StubGenerator(initState);

        rng.jump();

        assertEquals(initState + rng.jumpDistance(), rng.state, 0.001d);
    }

    @Test
    public void leap_jumpsToLeapDistance() {
        double initState = 10.0d;
        StubGenerator rng = new StubGenerator(initState);

        rng.leap();

        assertEquals(initState + rng.leapDistance(), rng.state, 0.001d);
    }

    @Test
    public void jump_advancesEachElementInStream() {
        double initState = 1d;
        StubGenerator rng = new StubGenerator(initState);

        double jumpDistance = 10;
        int streamSize = 5;

        var generators = rng.jumps(streamSize, jumpDistance).toList();

        assertEquals(initState + jumpDistance * streamSize, rng.state, 0.001d);
        assertEquals(streamSize, generators.size());

        Set<Double> actualStates = generators.stream()
                .map(generator -> (StubGenerator) generator)
                .map(stubGenerator -> stubGenerator.state)
                .collect(toSet());

        Set<Double> expectedStates = Set.of(
                initState,
                initState + jumpDistance,
                initState + 2 * jumpDistance,
                initState + 3 * jumpDistance,
                initState + 4 * jumpDistance);

        assertEquals(expectedStates, actualStates);
    }
}
