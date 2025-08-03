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

package android.platform.test.flag.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class FlagsParameterizationTest {
    @Test
    public void toStringIsOrderIndependent() {
        FlagsParameterization params1 = new FlagsParameterization(Map.of("a", true, "b", false));
        FlagsParameterization params2 = new FlagsParameterization(Map.of("b", false, "a", true));
        assertEquals(params1.toString(), params2.toString());
        assertEquals(params1, params2);
    }

    @Test
    public void toStringIsDifferent() {
        FlagsParameterization params1 = new FlagsParameterization(Map.of("a", true, "b", false));
        FlagsParameterization params2 = new FlagsParameterization(Map.of("a", true, "b", true));
        assertNotEquals(params1.toString(), params2.toString());
        assertNotEquals(params1, params2);
    }

    @Test
    public void toStringIsNotEmpty() {
        FlagsParameterization params = new FlagsParameterization(Map.of());
        assertNotEquals("", params.toString());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void overridesCannotBeChanged() {
        Map<String, Boolean> original = new HashMap<>();
        original.put("foo", true);
        FlagsParameterization params = new FlagsParameterization(original);
        params.mOverrides.put("foo", false);
    }

    @Test
    public void overridesIsACopy() {
        Map<String, Boolean> original = new HashMap<>();
        original.put("foo", true);
        FlagsParameterization params = new FlagsParameterization(original);
        original.put("foo", false);
        assertTrue(params.mOverrides.get("foo"));
    }

    @Test
    public void allCombinationsWith0Flags() {
        List<FlagsParameterization> actual = FlagsParameterization.allCombinationsOf();
        List<FlagsParameterization> expected = List.of(new FlagsParameterization(Map.of()));
        assertEquals(expected, actual);
    }

    @Test
    public void allCombinationsWith1Flags() {
        List<FlagsParameterization> actual = FlagsParameterization.allCombinationsOf("a");
        List<FlagsParameterization> expected =
                List.of(
                        new FlagsParameterization(Map.of("a", false)),
                        new FlagsParameterization(Map.of("a", true)));
        assertEquals(expected, actual);
    }

    @Test
    public void allCombinationsWith2Flags() {
        List<FlagsParameterization> actual = FlagsParameterization.allCombinationsOf("a", "b");
        List<FlagsParameterization> expected =
                List.of(
                        new FlagsParameterization(Map.of("a", false, "b", false)),
                        new FlagsParameterization(Map.of("a", false, "b", true)),
                        new FlagsParameterization(Map.of("a", true, "b", false)),
                        new FlagsParameterization(Map.of("a", true, "b", true)));
        assertEquals(expected, actual);
    }

    @Test
    public void allCombinationsWith3Flags() {
        List<FlagsParameterization> actual = FlagsParameterization.allCombinationsOf("a", "b", "c");
        List<FlagsParameterization> expected =
                List.of(
                        new FlagsParameterization(Map.of("a", false, "b", false, "c", false)),
                        new FlagsParameterization(Map.of("a", false, "b", false, "c", true)),
                        new FlagsParameterization(Map.of("a", false, "b", true, "c", false)),
                        new FlagsParameterization(Map.of("a", false, "b", true, "c", true)),
                        new FlagsParameterization(Map.of("a", true, "b", false, "c", false)),
                        new FlagsParameterization(Map.of("a", true, "b", false, "c", true)),
                        new FlagsParameterization(Map.of("a", true, "b", true, "c", false)),
                        new FlagsParameterization(Map.of("a", true, "b", true, "c", true)));
        assertEquals(expected, actual);
    }

    @Test
    public void progressionWith0Flags() {
        List<FlagsParameterization> actual = FlagsParameterization.progressionOf();
        List<FlagsParameterization> expected = List.of(new FlagsParameterization(Map.of()));
        assertEquals(expected, actual);
    }

    @Test
    public void progressionWith1Flags() {
        List<FlagsParameterization> actual = FlagsParameterization.progressionOf("a");
        List<FlagsParameterization> expected =
                List.of(
                        new FlagsParameterization(Map.of("a", false)),
                        new FlagsParameterization(Map.of("a", true)));
        assertEquals(expected, actual);
    }

    @Test
    public void progressionWith2Flags() {
        List<FlagsParameterization> actual = FlagsParameterization.progressionOf("a", "b");
        List<FlagsParameterization> expected =
                List.of(
                        new FlagsParameterization(Map.of("a", false, "b", false)),
                        new FlagsParameterization(Map.of("a", true, "b", false)),
                        new FlagsParameterization(Map.of("a", true, "b", true)));
        assertEquals(expected, actual);
    }

    @Test
    public void progressionWith3Flags() {
        List<FlagsParameterization> actual = FlagsParameterization.progressionOf("a", "b", "c");
        List<FlagsParameterization> expected =
                List.of(
                        new FlagsParameterization(Map.of("a", false, "b", false, "c", false)),
                        new FlagsParameterization(Map.of("a", true, "b", false, "c", false)),
                        new FlagsParameterization(Map.of("a", true, "b", true, "c", false)),
                        new FlagsParameterization(Map.of("a", true, "b", true, "c", true)));
        assertEquals(expected, actual);
    }
}
