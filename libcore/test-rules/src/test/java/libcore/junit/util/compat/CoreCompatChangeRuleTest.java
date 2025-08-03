/*
 * Copyright (C) 2018 The Android Open Source Project
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
package libcore.junit.util.compat;

import static org.junit.Assert.assertEquals;

import android.compat.testing.FakeApi;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for compatibility change gating.
 */
@RunWith(JUnit4.class)
public class CoreCompatChangeRuleTest {

    @Rule
    public TestRule compatChangeRule = new CoreCompatChangeRule();

    @Test
    @EnableCompatChanges({FakeApi.CHANGE_ID})
    public void testFakeGatingPositive() {
        assertEquals("A", FakeApi.fakeFunc());
    }

    @Test
    @DisableCompatChanges({FakeApi.CHANGE_ID})
    public void testFakeGatingNegative() {
        assertEquals("B", FakeApi.fakeFunc());
    }

    @Test
    @DisableCompatChanges({FakeApi.CHANGE_ID_1, FakeApi.CHANGE_ID_2})
    public void testFakeGatingCombined0() {
        assertEquals("0", FakeApi.fakeCombinedFunc());
    }

    @Test
    @DisableCompatChanges({FakeApi.CHANGE_ID_1})
    @EnableCompatChanges({FakeApi.CHANGE_ID_2})
    public void testFakeGatingCombined1() {
        assertEquals("1", FakeApi.fakeCombinedFunc());
    }

    @Test
    @EnableCompatChanges({FakeApi.CHANGE_ID_1})
    @DisableCompatChanges({FakeApi.CHANGE_ID_2})
    public void testFakeGatingCombined2() {
        assertEquals("2", FakeApi.fakeCombinedFunc());
    }

    @Test
    @EnableCompatChanges({FakeApi.CHANGE_ID_1, FakeApi.CHANGE_ID_2})
    public void testFakeGatingCombined3() {
        assertEquals("3", FakeApi.fakeCombinedFunc());
    }
}