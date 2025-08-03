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

package libcore.java.util.random;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGenerator.SplittableGenerator;
import java.util.random.RandomGeneratorFactory;

@RunWith(JUnit4.class)
public class SplittableGeneratorTest {

    @Test
    public void splits_produceTheSameResult_whenSourceIsIdentical() {
        var sourceSeed = 12345;
        var size = 10;

        SplittableGenerator random1 = new SplittableRandom(sourceSeed);
        SplittableGenerator random2 = new SplittableRandom(sourceSeed);

        var splitRandom1 = random1.splits().limit(size).toList();
        var splitRandom2 = random2.splits().limit(size).toList();

        assertEquals(size, splitRandom1.size());
        assertEquals(size, splitRandom2.size());

        for (int i = 0; i < size; ++i) {
            assertGeneratorsAreEquals(splitRandom1.get(i), splitRandom2.get(i));
        }
    }

    @Test
    public void of_acceptsAllSplittablesFromRandomGeneratorFactory() {
        RandomGeneratorFactory.all()
                .filter(RandomGeneratorFactory::isSplittable)
                .forEach(factory -> SplittableGenerator.of(factory.name()));
    }

    private static void assertGeneratorsAreEquals(SplittableGenerator random1,
            SplittableGenerator random2) {
        for (int i = 0; i < 1_000; ++i) {
            assertEquals(random1.nextLong(), random2.nextLong());
        }
    }

}
