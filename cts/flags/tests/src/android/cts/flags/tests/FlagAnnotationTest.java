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

package android.cts.flags.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.MethodSorters;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for test filtering base on flag values. Test case that will be executed MUST be ended with
 * '_execute'.
 */
@RunWith(JUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class FlagAnnotationTest {
    private static final List<String> EXPECTED_TESTS_EXECUTED =
            Arrays.stream(FlagAnnotationTest.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(methodName -> methodName.endsWith("_execute"))
                    .sorted()
                    .collect(Collectors.toList());

    private static final List<String> ACTUAL_TESTS_EXECUTED = new ArrayList<>();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READWRITE_DISABLED_FLAG)
    public void requiresReadonlyDisabledFlagEnabled_skip() {
        // Should not run.
        fail();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_READWRITE_DISABLED_FLAG)
    public void requiresReadonlyDisabledFlagDisabled_execute() {
        ACTUAL_TESTS_EXECUTED.add("requiresReadonlyDisabledFlagDisabled_execute");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READWRITE_ENABLED_FLAG)
    public void requiresReadonlyEnabledFlagEnabled_execute() {
        ACTUAL_TESTS_EXECUTED.add("requiresReadonlyEnabledFlagEnabled_execute");
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_READWRITE_ENABLED_FLAG)
    public void requiresReadonlyEnabledFlagDisabled_skip() {
        // Should not run.
        fail();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READWRITE_ENABLED_FLAG)
    @RequiresFlagsDisabled(Flags.FLAG_READWRITE_DISABLED_FLAG)
    public void requiresMultiStateFlags_execute() {
        ACTUAL_TESTS_EXECUTED.add("requiresMultiStateFlags_execute");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READWRITE_DISABLED_FLAG)
    @RequiresFlagsDisabled(Flags.FLAG_READWRITE_ENABLED_FLAG)
    public void requiresMultiStateFlags_skip() {
        // Should not run.
        fail();
    }

    @Test
    @RequiresFlagsDisabled({Flags.FLAG_READWRITE_DISABLED_FLAG,
            Flags.FLAG_READWRITE_DISABLED_FLAG_2})
    public void requiresMultiFlagsForTheSameState_execute() {
        ACTUAL_TESTS_EXECUTED.add("requiresMultiFlagsForTheSameState_execute");
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_READWRITE_ENABLED_FLAG, Flags.FLAG_READWRITE_DISABLED_FLAG})
    public void requiresMultiFlagsForTheSameState_skip() {
        // Should not run.
        fail();
    }

    @Test
    public void zLastTest_checkExecutedTests() { // Starts the method name with 'z' so that
        // it will be the last test to get executed.
        assertArrayEquals(EXPECTED_TESTS_EXECUTED.toArray(), ACTUAL_TESTS_EXECUTED.toArray());
    }
}
