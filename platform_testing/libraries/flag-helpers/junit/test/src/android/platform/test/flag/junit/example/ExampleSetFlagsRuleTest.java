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

import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.Flags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Example for how to write a test using {@link SetFlagsRule} and the annotations {@link
 * EnableFlags}, {@link DisableFlags}.
 */
@RunWith(JUnit4.class)
public class ExampleSetFlagsRuleTest {

    // NOTE: Flags.flagName3() defaults to false
    // NOTE: Flags.flagName4() defaults to true

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);

    @Test
    public void runTestWithDefaults() {
        assertFalse(Flags.flagName3());
        assertTrue(Flags.flagName4());
    }

    @Test
    @EnableFlags(Flags.FLAG_FLAG_NAME3)
    public void runTestWithFlag3Enabled() {
        assertTrue(Flags.flagName3());
        assertTrue(Flags.flagName4());
    }

    @Test
    @DisableFlags(Flags.FLAG_FLAG_NAME4)
    public void runTestWithFlag4Disabled() {
        assertFalse(Flags.flagName3());
        assertFalse(Flags.flagName4());
    }

    @Test
    @EnableFlags(Flags.FLAG_FLAG_NAME3)
    @DisableFlags(Flags.FLAG_FLAG_NAME4)
    public void runTestWithFlag3EnabledAndFlag4Disabled() {
        assertTrue(Flags.flagName3());
        assertFalse(Flags.flagName4());
    }

    @Test
    @EnableFlags({Flags.FLAG_FLAG_NAME3, Flags.FLAG_FLAG_NAME4})
    public void runATestWithTwoFlagsEnabled() {
        assertTrue(Flags.flagName3());
        assertTrue(Flags.flagName4());
    }
}
