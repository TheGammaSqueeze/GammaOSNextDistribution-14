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

package android.platform.test.flag.junit.example;

import static org.junit.Assert.fail;

import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.IFlagsValueProvider;
import android.platform.test.flag.util.FlagReadException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Example for how to write a test using {@code CheckFlagsRule}. */
@RunWith(JUnit4.class)
public class ExampleCheckFlagsRuleTest {

    /**
     * NOTE: A real test would use the following: @Rule public final CheckFlagsRule mCheckFlagsRule
     * = DeviceFlagsValueProvider.createCheckFlagsRule();
     */
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            new CheckFlagsRule(
                    new IFlagsValueProvider() {
                        @Override
                        public boolean getBoolean(String flag) throws FlagReadException {
                            switch (flag) {
                                case "flag0":
                                    return false;
                                case "flag1":
                                    return true;
                                default:
                                    throw new FlagReadException(flag, "unknown flag");
                            }
                        }
                    });

    @Test
    public void noAnnotation_execute() {
        // Test passes
    }

    @Test
    @RequiresFlagsEnabled("flag0")
    public void requiredDisabledFlagEnabled_skip() {
        fail("Test should be skipped");
    }

    @Test
    @RequiresFlagsEnabled("flag1")
    @RequiresFlagsDisabled("flag0")
    public void requireBothEnabledAndDisabledFlags_execute() {
        // Test passes
    }

    @Test
    @RequiresFlagsDisabled("flag1")
    public void requiredEnabledFlagDisabled_skip() {
        fail("Test should be skipped");
    }

    @Test
    @RequiresFlagsEnabled({"flag0", "flag1"})
    public void requiredDisabledFlagEnabledWithOthers_skip() {
        fail("Test should be skipped");
    }
}
