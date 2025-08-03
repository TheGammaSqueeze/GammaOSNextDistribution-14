/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static org.junit.Assert.fail;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test the logic for {@link FinishActivitiesWithoutProcessKillRule}
 */
@RunWith(JUnit4.class)
public class FinishActivitiesWithoutProcessKillRuleTest {

    private final static String DUMPSYS_NO_ACTIVITY_OUTPUT = "";

    /**
     * Tests that this rule will fail to register if no package name is supplied
     * to finish the activities.
     */
    @Test
    public void testNoAppToFinishActivitiesFails() {
        try {
            FinishActivitiesWithoutProcessKillRule rule =
                    new FinishActivitiesWithoutProcessKillRule();
            fail("An initialization error should have been thrown, but wasn't.");
        } catch (InitializationError e) {
            return;
        }
    }

    /**
     * Tests that this rule will finish the activities associated with the package.
     */
    @Test
    public void testFinishActivities() throws Throwable {
        TestableRule rule = new TestableRule(new Bundle(), "example.package.name");
        rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                .evaluate();
        assertThat(rule.getOperations()).containsExactly(
                "dumpsys activity activities", "test")
                .inOrder();
    }

    /**
     * Tests that this rule will finish the activities associated with the package, if
     * finish all activity without process kill flag is enabled.
     */
    @Test
    public void testDisableFinishActivities() throws Throwable {
        Bundle bundle = new Bundle();
        bundle.putString(
                FinishActivitiesWithoutProcessKillRule.FINISH_ACTIVITY_WITHOUT_PROCESS_KILL,
                "false");

        TestableRule rule = new TestableRule(bundle, "example.package.name");
        rule.apply(rule.getTestStatement(), Description.createTestDescription("clzz", "mthd"))
                .evaluate();
        assertThat(rule.getOperations()).containsExactly("test").inOrder();
    }

    private static class TestableRule extends FinishActivitiesWithoutProcessKillRule {
        private List<String> mOperations = new ArrayList<>();
        private Bundle mBundle;

        public TestableRule(Bundle bundle, String app) {
            super(app);
            mBundle = bundle;
        }

        @Override
        protected String executeShellCommand(String cmd) {
            mOperations.add(cmd);
            if (cmd.equalsIgnoreCase("dumpsys activity activities")) {
                return DUMPSYS_NO_ACTIVITY_OUTPUT;
            }
            return "";
        }

        @Override
        protected Bundle getArguments() {
            return mBundle;
        }

        public List<String> getOperations() {
            return mOperations;
        }

        public Statement getTestStatement() {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    mOperations.add("test");
                }
            };
        }
    }
}
