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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static java.lang.Boolean.FALSE;

import android.content.pm.PackageManager;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;

/** Unit test the logic for {@link SuspendUnsuspendAppRule} */
@RunWith(JUnit4.class)
public class SuspendUnsuspendAppRuleTest {
    private static final Statement TEST_STATEMENT =
            new Statement() {
                @Override
                public void evaluate() {}
            };
    private static final Description DESCRIPTION =
            Description.createTestDescription("class", "method");
    private final String mPackageToSuspend = "example.package.name";

    /**
     * Tests that this rule will call setPackagesSuspended method on the passed package to suspend
     * and then to unsuspend the package.
     */
    @Test
    public void testSuspendUnsuspendAppRule() throws Throwable {
        TestableSuspendUnsuspendAppRule rule =
                new TestableSuspendUnsuspendAppRule(new Bundle(), mPackageToSuspend);
        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
        rule.setPackageManager(mockPackageManager);
        rule.apply(TEST_STATEMENT, DESCRIPTION).evaluate();

        // Validate if the setPackagesSuspended is called once to suspend the package
        verify(mockPackageManager, times(1))
                .setPackagesSuspended(
                        new String[] {mPackageToSuspend},
                        true, // suspended
                        null, // appExtras
                        null, // launcherExtras
                        SuspendUnsuspendAppRule.SUSPEND_DIALOG_INFO);

        // Validate if the setPackagesSuspended is called once to unsuspend the package
        verify(mockPackageManager, times(1))
                .setPackagesSuspended(
                        new String[] {mPackageToSuspend},
                        false, // suspended
                        null, // appExtras
                        null, // launcherExtras
                        SuspendUnsuspendAppRule.UNSUSPEND_DIALOG_INFO);
    }

    /**
     * Tests that the App is suspended and unsuspended when passing the enable-suspend-unsuspend-app
     * argument as true
     */
    @Test
    public void testEnableSuspendUnsuspendAppRuleTrue() throws Throwable {
        Bundle suspendUnsuspendAppBundle = new Bundle();
        suspendUnsuspendAppBundle.putString(
                SuspendUnsuspendAppRule.ENABLE_SUSPEND_UNSUSPEND_APP, "true");
        TestableSuspendUnsuspendAppRule rule =
                new TestableSuspendUnsuspendAppRule(
                        suspendUnsuspendAppBundle, mPackageToSuspend, FALSE);
        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
        rule.setPackageManager(mockPackageManager);
        rule.apply(TEST_STATEMENT, DESCRIPTION).evaluate();

        // Validate if the setPackagesSuspended is called once to suspend the package
        verify(mockPackageManager, times(1))
                .setPackagesSuspended(
                        new String[] {mPackageToSuspend},
                        true, // suspended
                        null, // appExtras
                        null, // launcherExtras
                        SuspendUnsuspendAppRule.SUSPEND_DIALOG_INFO);

        // Validate if the setPackagesSuspended is called once to unsuspend the package
        verify(mockPackageManager, times(1))
                .setPackagesSuspended(
                        new String[] {mPackageToSuspend},
                        false, // suspended
                        null, // appExtras
                        null, // launcherExtras
                        SuspendUnsuspendAppRule.UNSUSPEND_DIALOG_INFO);
    }

    /**
     * Tests that the App is not suspended and unsuspended when passing the
     * enable-suspend-unsuspend-app argument as false
     */
    @Test
    public void testEnableSuspendUnsuspendAppRuleFalse() throws Throwable {
        Bundle suspendUnsuspendAppBundle = new Bundle();
        suspendUnsuspendAppBundle.putString(
                SuspendUnsuspendAppRule.ENABLE_SUSPEND_UNSUSPEND_APP, "false");
        TestableSuspendUnsuspendAppRule rule =
                new TestableSuspendUnsuspendAppRule(
                        suspendUnsuspendAppBundle, mPackageToSuspend, FALSE);
        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
        rule.setPackageManager(mockPackageManager);
        rule.apply(TEST_STATEMENT, DESCRIPTION).evaluate();

        // Validate if the setPackagesSuspended is called once to suspend the package
        verify(mockPackageManager, never())
                .setPackagesSuspended(
                        new String[] {mPackageToSuspend},
                        true, // suspended
                        null, // appExtras
                        null, // launcherExtras
                        SuspendUnsuspendAppRule.SUSPEND_DIALOG_INFO);

        // Validate if the setPackagesSuspended is called once to unsuspend the package
        verify(mockPackageManager, never())
                .setPackagesSuspended(
                        new String[] {mPackageToSuspend},
                        false, // suspended
                        null, // appExtras
                        null, // launcherExtras
                        SuspendUnsuspendAppRule.UNSUSPEND_DIALOG_INFO);
    }

    private static class TestableSuspendUnsuspendAppRule extends SuspendUnsuspendAppRule {
        private final Bundle mBundle;

        TestableSuspendUnsuspendAppRule(Bundle bundle, String app) {
            super(app);
            mBundle = bundle;
        }

        TestableSuspendUnsuspendAppRule(Bundle bundle, String app, boolean enableRule) {
            super(app, enableRule);
            mBundle = bundle;
        }

        @Override
        protected Bundle getArguments() {
            return mBundle;
        }
    }
}
