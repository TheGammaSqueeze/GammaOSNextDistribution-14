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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

@RunWith(JUnit4.class)
public class RandomGeneratorTest {

    @Test
    public void defaultExists() {
        assertNotNull(RandomGenerator.getDefault());
    }

    @Test
    public void constructorInvalidArgs() {
        assertThrows(NullPointerException.class, () -> RandomGenerator.of(null));
        assertThrows(IllegalArgumentException.class, () -> RandomGenerator.of("invalid-arg"));
    }

    @Test
    public void randomGeneratorFactory_and_randomGenerator_consistency() {
        RandomGeneratorFactory.all()
            .map(rngFactory -> rngFactory.name())
            .forEach(RandomGenerator::of);;
    }

    @Test
    public void isDeprecated_doesNotThrow() {
        RandomGeneratorFactory.all()
            .map(RandomGeneratorFactory::create)
            .forEach(RandomGenerator::isDeprecated);
    }

    @Test
    public void of_factoryMethod_throwsNPE() {
        assertThrows(NullPointerException.class,
            () -> RandomGenerator.ArbitrarilyJumpableGenerator.of(null));

        assertThrows(NullPointerException.class,
            () -> RandomGenerator.JumpableGenerator.of(null));

        assertThrows(NullPointerException.class,
            () -> RandomGenerator.StreamableGenerator.of(null));
    }

}
