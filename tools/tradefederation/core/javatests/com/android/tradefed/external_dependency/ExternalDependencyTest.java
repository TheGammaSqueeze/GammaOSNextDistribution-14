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

package com.android.tradefed.external_dependency;

import static org.junit.Assert.assertEquals;
import com.android.tradefed.dependencies.ExternalDependency;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link ExternalDependency}. */
@RunWith(JUnit4.class)
public class ExternalDependencyTest {

    public static class TestExternalDependencyOne extends ExternalDependency {}

    public static class TestExternalDependencyTwo extends ExternalDependency {}

    public static class TestExternalDependencyThree extends TestExternalDependencyTwo {}

    @Test
    public void testEquals() {
        Set<ExternalDependency> dependencySet = new LinkedHashSet<>();
        dependencySet.add(new TestExternalDependencyOne());
        assertEquals(1, dependencySet.size());
        dependencySet.add(new TestExternalDependencyOne());
        // Objects of the same dependency class should be treated as equal.
        assertEquals(1, dependencySet.size());

        dependencySet.add(new TestExternalDependencyTwo());
        // Objects of different dependency class should be treated as different.
        assertEquals(2, dependencySet.size());
        dependencySet.add(new TestExternalDependencyTwo());
        assertEquals(2, dependencySet.size());

        dependencySet.add(new TestExternalDependencyThree());
        assertEquals(3, dependencySet.size());
    }

    @Test
    public void testHashCode() {
        // Map representing frequency of each dependency class.
        Map<ExternalDependency, Integer> dependencyMap = new LinkedHashMap<>();
        TestExternalDependencyOne testDep1 = new TestExternalDependencyOne();
        TestExternalDependencyOne testDep2 = new TestExternalDependencyOne();
        TestExternalDependencyTwo testDep3 = new TestExternalDependencyTwo();

        dependencyMap.put(testDep1, dependencyMap.getOrDefault(testDep1, 0) + 1);
        assertEquals(1, dependencyMap.size());
        assertEquals(1, (int) dependencyMap.get(testDep1));

        // Objects of the same dependency class should be treated as equal.
        dependencyMap.put(testDep2, dependencyMap.getOrDefault(testDep1, 0) + 1);
        assertEquals(1, dependencyMap.size());
        assertEquals(2, (int) dependencyMap.get(testDep2));

        // Objects of different dependency class should be treated as different.
        dependencyMap.put(testDep3, dependencyMap.getOrDefault(testDep3, 0) + 1);
        assertEquals(2, dependencyMap.size());
        assertEquals(1, (int) dependencyMap.get(testDep3));

        dependencyMap.put(testDep3, dependencyMap.getOrDefault(testDep3, 0) + 1);
        assertEquals(2, dependencyMap.size());
        assertEquals(2, (int) dependencyMap.get(testDep3));
    }
}
