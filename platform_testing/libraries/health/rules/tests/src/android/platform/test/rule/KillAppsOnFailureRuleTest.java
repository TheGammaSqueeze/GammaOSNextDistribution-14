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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Bundle;

import androidx.test.uiautomator.UiDevice;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** Tests for {@link KillAppsOnFailureRule}. */
public class KillAppsOnFailureRuleTest {

    private static final String EXCEPTION_MESSAGE = "Expected test failure.";
    private static final Statement FAILING_TEST_STATEMENT =
            new Statement() {
                @Override
                public void evaluate() {
                    throw new RuntimeException(EXCEPTION_MESSAGE);
                }
            };
    private static final Description TEST_DESCRIPTION =
            Description.createTestDescription("class", "method");

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testDefaultDoesNothing() throws Throwable {
        TestableKillAppsOnFailureRule rule = new TestableKillAppsOnFailureRule(null);
        expectedException.expectMessage(EXCEPTION_MESSAGE);
        rule.apply(FAILING_TEST_STATEMENT, TEST_DESCRIPTION).evaluate();

        verify(rule.getUiDevice(), never()).executeShellCommand(any());
    }

    @Test
    public void testKillsSpecifiedApp() throws Throwable {
        String app = "com.test.package";
        TestableKillAppsOnFailureRule rule = new TestableKillAppsOnFailureRule(app);
        expectedException.expectMessage(EXCEPTION_MESSAGE);
        rule.apply(FAILING_TEST_STATEMENT, TEST_DESCRIPTION).evaluate();
        verify(rule.getUiDevice(), times(1))
                .executeShellCommand(String.format(KillAppsOnFailureRule.KILL_CMD, app));
    }

    @Test
    public void testKillsSpecifiedApps() throws Throwable {
        String apps = "com.test.package.first,com.test.package.second,com.test.package.third";
        TestableKillAppsOnFailureRule rule = new TestableKillAppsOnFailureRule(apps);
        expectedException.expectMessage(EXCEPTION_MESSAGE);
        rule.apply(FAILING_TEST_STATEMENT, TEST_DESCRIPTION).evaluate();

        InOrder order = inOrder(rule.getUiDevice());
        for (String app : apps.split(",")) {
            order.verify(rule.getUiDevice())
                    .executeShellCommand(String.format(KillAppsOnFailureRule.KILL_CMD, app));
        }
    }

    private static class TestableKillAppsOnFailureRule extends KillAppsOnFailureRule {
        private UiDevice mDevice;
        private Bundle mArgs;

        TestableKillAppsOnFailureRule(String commaSeparatedApps) {
            mDevice = Mockito.mock(UiDevice.class);
            mArgs = new Bundle();
            if (commaSeparatedApps != null) {
                mArgs.putString(KillAppsOnFailureRule.APPS_OPTION, commaSeparatedApps);
            }
        }

        @Override
        protected Bundle getArguments() {
            return mArgs;
        }

        @Override
        protected UiDevice getUiDevice() {
            return mDevice;
        }
    }
}
