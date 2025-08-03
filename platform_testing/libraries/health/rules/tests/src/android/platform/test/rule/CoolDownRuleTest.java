/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.Bundle;
import android.os.RemoteException;
import androidx.test.uiautomator.UiDevice;

import com.android.helpers.ThermalHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit test the logic for {@link CoolDownRule} */
@RunWith(JUnit4.class)
public class CoolDownRuleTest {
    private static final int TARGET_TEMPERATURE = 35;
    private static final int POLL_INTERVAL = 987;
    private static final int IDLE_WAIT = 123;
    private static final String TEMPERATURE_NAME = "test temperature sensor";
    private static final String TEMPERATURE_NAME_ESCAPED = "test#temperature#sensor";
    private static final int OVERHEAT_COUNT = 3;

    private static final String OPS_SCREEN_ON = "screen On";
    private static final String OPS_SCREEN_OFF = "screen Off";
    private static final String OPS_TEST = "test";
    private static final String OPS_SLEEP_INTERVAL = "sleep 987000 milli seconds";
    private static final String OPS_SLEEP_IDLE = "sleep 123000 milli seconds";

    private final ThermalHelper mThermalHelper = mock(ThermalHelper.class);

    /** Tests that this rule will complete cool down as expected steps. */
    @Test
    public void testCoolDownNormal() throws Throwable {
        boolean screenOn = true;
        boolean abortOnTimeout = false;
        int maxWait = (OVERHEAT_COUNT * POLL_INTERVAL) + 5;
        TestableRule rule = getDefaultRule(screenOn, maxWait, abortOnTimeout);

        doAnswer(new TemperatureAnswer(TARGET_TEMPERATURE, OVERHEAT_COUNT))
                .when(mThermalHelper)
                .getTemperature(TEMPERATURE_NAME);
        rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                .evaluate();
        assertThat(rule.getOperations())
                .containsExactly(
                        OPS_SCREEN_OFF,
                        "get device temperature degree: 46",
                        OPS_SLEEP_INTERVAL,
                        "get device temperature degree: 41",
                        OPS_SLEEP_INTERVAL,
                        "get device temperature degree: 37",
                        OPS_SLEEP_INTERVAL,
                        "get device temperature degree: 33",
                        OPS_SLEEP_IDLE,
                        OPS_SCREEN_ON,
                        OPS_TEST)
                .inOrder();
    }

    /** Tests that this rule will be skipped if not all required parameters are available. */
    @Test
    public void testCoolDownFallback() throws Throwable {
        boolean screenOn = true;
        TestableRule rule = new TestableRule(screenOn, mThermalHelper);
        rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                .evaluate();
        assertThat(rule.getOperations()).containsExactly(OPS_TEST).inOrder();
    }

    /** Tests that this rule will fail to cool down due to timeout as expected steps. */
    @Test
    public void testCoolDownTimeout() throws Throwable {
        boolean screenOn = true;
        boolean abortOnTimeout = false;
        int maxWait = (OVERHEAT_COUNT * POLL_INTERVAL) - 5;
        TestableRule rule = getDefaultRule(screenOn, maxWait, abortOnTimeout);

        double cooldownOffset = -1.0; // heat up instead of cool down
        doAnswer(new TemperatureAnswer(TARGET_TEMPERATURE, OVERHEAT_COUNT, cooldownOffset))
                .when(mThermalHelper)
                .getTemperature(TEMPERATURE_NAME);
        rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                .evaluate();
        assertThat(rule.getOperations())
                .containsExactly(
                        OPS_SCREEN_OFF,
                        "get device temperature degree: 37",
                        OPS_SLEEP_INTERVAL,
                        "get device temperature degree: 38",
                        OPS_SLEEP_INTERVAL,
                        "get device temperature degree: 39",
                        OPS_SLEEP_INTERVAL,
                        "get device temperature degree: 40",
                        OPS_SLEEP_IDLE,
                        OPS_SCREEN_ON,
                        OPS_TEST)
                .inOrder();
    }

    /**
     * Tests that this rule will fail to cool down due to timeout and throw exception to abort the
     * test as expected steps.
     */
    @Test
    public void testCoolDownTimeoutAbort() throws Throwable {
        boolean screenOn = true;
        boolean abortOnTimeout = true;
        int maxWait = (OVERHEAT_COUNT * POLL_INTERVAL) - 5;
        TestableRule rule = getDefaultRule(screenOn, maxWait, abortOnTimeout);

        double cooldownOffset = -1.0; // heat up instead of cool down
        doAnswer(new TemperatureAnswer(TARGET_TEMPERATURE, OVERHEAT_COUNT, cooldownOffset))
                .when(mThermalHelper)
                .getTemperature(TEMPERATURE_NAME);
        try {
            rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                    .evaluate();
            fail("An exception should have been thrown.");
        } catch (IllegalStateException e) {
            assertThat(rule.getOperations())
                    .containsExactly(
                            OPS_SCREEN_OFF,
                            "get device temperature degree: 37",
                            OPS_SLEEP_INTERVAL,
                            "get device temperature degree: 38",
                            OPS_SLEEP_INTERVAL,
                            "get device temperature degree: 39",
                            OPS_SLEEP_INTERVAL,
                            "get device temperature degree: 40")
                    .inOrder();
        }
    }

    /**
     * Tests that this rule will complete cool down without turning off screen as expected steps.
     */
    @Test
    public void testCoolDownScreenOff() throws Throwable {
        boolean screenOn = false;
        boolean abortOnTimeout = false;
        int maxWait = (OVERHEAT_COUNT * POLL_INTERVAL) + 5;
        TestableRule rule = getDefaultRule(screenOn, maxWait, abortOnTimeout);

        doAnswer(new TemperatureAnswer(TARGET_TEMPERATURE, OVERHEAT_COUNT))
                .when(mThermalHelper)
                .getTemperature(TEMPERATURE_NAME);
        rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                .evaluate();
        assertThat(rule.getOperations())
                .containsExactly(
                        "get device temperature degree: 46",
                        OPS_SLEEP_INTERVAL,
                        "get device temperature degree: 41",
                        OPS_SLEEP_INTERVAL,
                        "get device temperature degree: 37",
                        OPS_SLEEP_INTERVAL,
                        "get device temperature degree: 33",
                        OPS_SLEEP_IDLE,
                        OPS_TEST)
                .inOrder();
    }
    /** Tests to confirm option value strings will be escaped correctly. */
    @Test
    public void testEscapedOptionStrs() throws Throwable {
        Map<String, String> escape_test_cases = new HashMap<String, String>();
        escape_test_cases.put("", "");
        escape_test_cases.put("#", " ");
        escape_test_cases.put("##", "#");
        escape_test_cases.put("prefix#", "prefix ");
        escape_test_cases.put("prefix##", "prefix#");
        escape_test_cases.put("#postfix", " postfix");
        escape_test_cases.put("##postfix", "#postfix");
        escape_test_cases.put("prefix#postfix", "prefix postfix");
        escape_test_cases.put("prefix##postfix", "prefix#postfix");
        escape_test_cases.put("###", "# ");
        escape_test_cases.put("####", "##");
        escape_test_cases.put("prefix###", "prefix# ");
        escape_test_cases.put("prefix####", "prefix##");
        escape_test_cases.put("###postfix", "# postfix");
        escape_test_cases.put("####postfix", "##postfix");
        escape_test_cases.put("prefix###postfix", "prefix# postfix");
        escape_test_cases.put("prefix####postfix", "prefix##postfix");
        escape_test_cases.put("#####", "## ");
        escape_test_cases.put("######", "###");
        for (String input : escape_test_cases.keySet()) {
            String expected_output = escape_test_cases.get(input);
            String actual_output = CoolDownRule.unescapeOptionStr(input);
            assertThat(expected_output.equals(actual_output)).isTrue();
        }
    }
    /** Tests to detect unescaped option value strings. */
    @Test
    public void testNonEscapeOptionStrs() throws Throwable {
        String inputs[] =
                new String[] {
                    " ",
                    "prefix postfix",
                    " #",
                    "# ",
                    "prefix #",
                    "# postfix",
                    "prefix #postfix",
                    "prefix# postfix",
                    " ##",
                    "## ",
                    "# #",
                    " prefix##",
                    "##postfix ",
                    "prefix ##postfix",
                    "prefix# #postfix",
                    "prefix## postfix",
                    " ###",
                    "# ##",
                    "## #",
                    "### ",
                };

        for (String input : inputs) {
            assertThrows(
                    IllegalArgumentException.class, () -> CoolDownRule.unescapeOptionStr(input));
        }
    }

    private TestableRule getDefaultRule(boolean screenOn, int maxWait, boolean abortOnTimeout) {
        TestableRule rule = new TestableRule(screenOn, mThermalHelper);
        rule.addArg(CoolDownRule.POLL_INTERVAL_OPTION, String.valueOf(POLL_INTERVAL));
        rule.addArg(CoolDownRule.MAX_WAIT_OPTION, String.valueOf(maxWait));
        rule.addArg(CoolDownRule.ABORT_ON_TIMEOUT_OPTION, String.valueOf(abortOnTimeout));
        rule.addArg(CoolDownRule.POST_IDLE_WAIT_OPTION, String.valueOf(IDLE_WAIT));
        rule.addArg(CoolDownRule.DEVICE_TEMPERATURE_NAME_OPTION, TEMPERATURE_NAME_ESCAPED);
        rule.addArg(CoolDownRule.TARGET_TEMPERATURE_OPTION, String.valueOf(TARGET_TEMPERATURE));
        return rule;
    }

    private static class TestableRule extends CoolDownRule {
        private final UiDevice mUiDevice;

        private List<String> mOperations = new ArrayList<>();
        private Bundle mBundle = new Bundle();
        private boolean mIsScreenOn = true;
        private long mTotalSleepMs = 0;
        private final ThermalHelper mThermalHelper;

        private TestableRule() {
            this(true, null);
        }

        public TestableRule(boolean screenOn, ThermalHelper thermalHelper) {
            mUiDevice = mock(UiDevice.class);
            mIsScreenOn = screenOn;
            mThermalHelper = thermalHelper;
        }

        @Override
        protected UiDevice getUiDevice() {
            try {
                when(mUiDevice.isScreenOn()).thenReturn(mIsScreenOn);
                doAnswer(
                                invocation -> {
                                    return setScreen(false);
                                })
                        .when(mUiDevice)
                        .sleep();
                doAnswer(
                                invocation -> {
                                    return setScreen(true);
                                })
                        .when(mUiDevice)
                        .wakeUp();
            } catch (RemoteException e) {
                throw new RuntimeException("Could not unlock device.", e);
            }
            return mUiDevice;
        }

        @Override
        ThermalHelper initThermalHelper() {
            return mThermalHelper;
        }

        @Override
        protected Bundle getArguments() {
            return mBundle;
        }

        @Override
        int getDeviceTemperature(String name) {
            int value = super.getDeviceTemperature(name);
            mOperations.add(String.format("get device temperature degree: %d", value));
            return value;
        }

        @Override
        protected void sleepMs(long milliSeconds) {
            mOperations.add(String.format("sleep %d milli seconds", milliSeconds));
            mTotalSleepMs += milliSeconds;
        }

        @Override
        protected long elapsedMs(long start) {
            long ms = super.elapsedMs(start);
            return (mTotalSleepMs + ms);
        }

        public List<String> getOperations() {
            return mOperations;
        }

        public void addArg(String key, String value) {
            mBundle.putString(key, value);
        }

        public Object setScreen(boolean screenOn) {
            mIsScreenOn = screenOn;
            mOperations.add(mIsScreenOn ? OPS_SCREEN_ON : OPS_SCREEN_OFF);
            return null;
        }

        public Statement getTestStatement() {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    mOperations.add(OPS_TEST);
                }
            };
        }
    }

    private static class TemperatureAnswer implements Answer {
        private static final double INIT_OFFSET = 2.0;
        private static final double DEFAULT_COOLDOWN_OFFSET = 4.4;

        private final double targetTemperature;
        private final double cooldownOffset;
        private final int overHeatCount;

        private double temperature = 0.0;

        private TemperatureAnswer() {
            this(36.0, 3, DEFAULT_COOLDOWN_OFFSET);
        }

        public TemperatureAnswer(double targetTemperatureIn, int overHeatCount) {
            this(targetTemperatureIn, overHeatCount, DEFAULT_COOLDOWN_OFFSET);
        }

        public TemperatureAnswer(
                double targetTemperatureIn, int overHeatCountIn, double cooldownOffsetIn) {
            targetTemperature = targetTemperatureIn;
            overHeatCount = overHeatCountIn;
            cooldownOffset = cooldownOffsetIn;

            if (cooldownOffset > 0) { // cool down by turn
                temperature = targetTemperature - INIT_OFFSET;
                temperature += cooldownOffset * overHeatCount;
            } else { // from warm to warmer
                temperature = targetTemperature + INIT_OFFSET;
            }
        }

        @Override
        public Double answer(InvocationOnMock invocation) {
            double result = temperature;
            temperature -= cooldownOffset;
            return Double.valueOf(result);
        }
    }
}
