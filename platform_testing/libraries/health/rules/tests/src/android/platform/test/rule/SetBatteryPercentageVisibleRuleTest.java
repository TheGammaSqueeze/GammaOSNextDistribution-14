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

package android.platform.test.rule;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Bundle;

import androidx.test.uiautomator.UiDevice;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;

/** Unit test the logic for {@link SetBatteryPercentageVisibleRule} */
@RunWith(JUnit4.class)
public class SetBatteryPercentageVisibleRuleTest {

    private static final Statement TEST_STATEMENT =
            new Statement() {
                @Override
                public void evaluate() {}
            };
    private static final Description TEST_DESCRIPTION =
            Description.createTestDescription("class", "method");

    /** Tests that this rule will call shell command to set the Battery percentage to be visible */
    @Test
    public void testSetBatteryPercentageVisibleTrue() throws Throwable {
        batteryVisibleTest(true);
    }

    /**
     * Tests that this rule will call shell command to set the Battery percentage to be invisible
     */
    @Test
    public void testSetBatteryPercentageVisibleFalse() throws Throwable {
        batteryVisibleTest(false);
    }

    void batteryVisibleTest(Boolean visible) throws Throwable {
        TestableSetBatteryPercentageVisibleRule rule =
                new TestableSetBatteryPercentageVisibleRule(visible);
        rule.apply(TEST_STATEMENT, TEST_DESCRIPTION).evaluate();
        String shellCommand =
                "settings put system "
                        + SetBatteryPercentageVisibleRule.SHOW_BATTERY_PERCENT_SETTING
                        + " "
                        + (visible ? 1 : 0);
        verify(rule.getUiDevice(), times(1)).executeShellCommand(shellCommand);
    }

    private static class TestableSetBatteryPercentageVisibleRule
            extends SetBatteryPercentageVisibleRule {
        private UiDevice mUiDevice;
        private Bundle mArgs;

        TestableSetBatteryPercentageVisibleRule(Boolean visible) {
            super(visible);
            mUiDevice = Mockito.mock(UiDevice.class);
            mArgs = new Bundle();
            if (visible != null) {
                mArgs.putBoolean(SetBatteryPercentageVisibleRule.VISIBLE_OPTION, visible);
            }
        }

        @Override
        protected Bundle getArguments() {
            return mArgs;
        }

        @Override
        protected UiDevice getUiDevice() {
            return mUiDevice;
        }
    }
}
