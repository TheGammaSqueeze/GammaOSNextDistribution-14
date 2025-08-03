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
package android.platform.test.rule;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tests for {@link WaitForIdleTimeoutRule}. */
public class WaitForIdleTimeoutRuleTest {
    private static List<String> sLogs = new ArrayList<>();

    private static final String TEST_STATEMENT_LOG = "Running test statement.";
    private static final Statement TEST_STATEMENT =
            new Statement() {
                @Override
                public void evaluate() {
                    sLogs.add(TEST_STATEMENT_LOG);
                }
            };
    private static final Description DESCRIPTION =
            Description.createTestDescription("class", "method");

    private static class TestableTimeoutSetter extends WaitForIdleTimeoutRule.TimeoutSetter {
        public static final long INITIAL_TIMEOUT = 7L;
        public static final String SET_TIMEOUT_LOG_TEMPLATE = "Setting timeout to %d ms.";

        private long mTimeout = INITIAL_TIMEOUT;

        @Override
        protected String getDescription() {
            return "TestableTimeoutSetter";
        }

        @Override
        protected void setTimeout(long ms) {
            sLogs.add(String.format(SET_TIMEOUT_LOG_TEMPLATE, ms));
            mTimeout = ms;
        }

        @Override
        protected long getTimeout() {
            return mTimeout;
        }
    }

    @Before
    public void setUp() {
        sLogs.clear();
    }

    @Test
    public void testSetsAndRestoresTimeout() throws Throwable {
        long timeout = 777L;
        WaitForIdleTimeoutRule rule = createRule(timeout, false);

        rule.apply(TEST_STATEMENT, DESCRIPTION).evaluate();

        assertThat(sLogs)
                .containsExactly(
                        String.format(TestableTimeoutSetter.SET_TIMEOUT_LOG_TEMPLATE, timeout),
                        TEST_STATEMENT_LOG,
                        String.format(
                                TestableTimeoutSetter.SET_TIMEOUT_LOG_TEMPLATE,
                                TestableTimeoutSetter.INITIAL_TIMEOUT));
    }

    @Test
    public void testDisable() throws Throwable {
        // The rule is disabled by default.
        WaitForIdleTimeoutRule rule = createRule(777L, null);

        rule.apply(TEST_STATEMENT, DESCRIPTION).evaluate();

        assertThat(sLogs).containsExactly(TEST_STATEMENT_LOG);
    }

    private WaitForIdleTimeoutRule createRule(long timeout, Boolean disabled) {
        Bundle args = new Bundle();
        args.putString(WaitForIdleTimeoutRule.TIMEOUT_OPTION, String.valueOf(timeout));
        if (disabled != null) {
            args.putString(WaitForIdleTimeoutRule.DISABLE_OPTION, String.valueOf(disabled));
        }

        WaitForIdleTimeoutRule rule = spy(new WaitForIdleTimeoutRule());
        doReturn(args).when(rule).getArguments();
        doReturn(Arrays.asList(new TestableTimeoutSetter())).when(rule).getTimeoutSetters();

        return rule;
    }
}
