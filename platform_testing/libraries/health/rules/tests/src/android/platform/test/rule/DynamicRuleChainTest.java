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

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link DynamicRuleChain}. */
@RunWith(JUnit4.class)
public class DynamicRuleChainTest {
    private static final Description DESCRIPTION =
            Description.createTestDescription("class", "method");

    // A static variable is used so that the rules in this test can modify its state while being
    // static (and thus able to be created via an empty constructor). This route is taken as the
    // rules themselves are not directly observable due to being created via reflection from
    // DynamicRuleChain.
    private static final List<String> sLogs = new ArrayList<String>();

    private static final Statement mStatement =
            new Statement() {
                @Override
                public void evaluate() {
                    sLogs.add("Test execution");
                }
            };

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        // Clear the logs between tests.
        sLogs.clear();
    }

    @Test
    public void testAppliesRuleBySimpleClassNameForRulesInKnownPackage() throws Throwable {
        DynamicRuleChain chain = createWithRuleNames("DynamicRuleChainTest$Rule1");
        Statement applied = chain.apply(mStatement, DESCRIPTION);
        applied.evaluate();
        assertThat(sLogs).containsExactly("Rule1 starting", "Test execution", "Rule1 finished");
    }

    @Test
    public void testAppliesRuleByFullyQualifiedClassName() throws Throwable {
        DynamicRuleChain chain =
                createWithRuleNames("android.platform.test.rule.DynamicRuleChainTest$Rule1");
        Statement applied = chain.apply(mStatement, DESCRIPTION);
        applied.evaluate();
        assertThat(sLogs).containsExactly("Rule1 starting", "Test execution", "Rule1 finished");
    }

    @Test
    public void testThrowsOnNonexistentSimpleClassName() throws Throwable {
        String badName = "NotARuleRule";
        expectedException.expectMessage(
                String.format(
                        "Failed to dynamically load rule with simple class name %s.", badName));
        DynamicRuleChain chain = createWithRuleNames(badName);
        chain.apply(mStatement, DESCRIPTION);
    }

    @Test
    public void testThrowsOnNonexistentFullyQualifiedClassName() throws Throwable {
        String badName = "not.a.rule.Rule";
        expectedException.expectMessage(
                String.format(
                        "Failed to dynamically load rule with fully qualified name %s.", badName));
        DynamicRuleChain chain = createWithRuleNames(badName);
        chain.apply(mStatement, DESCRIPTION);
    }

    @Test
    public void testMultipleRulesAreAppliedInCorrectOrder() throws Throwable {
        DynamicRuleChain chain =
                createWithRuleNames(
                        "DynamicRuleChainTest$Rule2",
                        "android.platform.test.rule.DynamicRuleChainTest$Rule1");
        Statement applied = chain.apply(mStatement, DESCRIPTION);
        applied.evaluate();
        // Rule2's logs should be outside, and Rule1's logs should be inside.
        assertThat(sLogs)
                .containsExactly(
                        "Rule2 starting",
                        "Rule1 starting",
                        "Test execution",
                        "Rule1 finished",
                        "Rule2 finished");
    }

    @Test
    public void testInvalidRuleNameInMultipleRulesAlsoThrows() throws Throwable {
        String badName = "not.a.rule.Rule";
        expectedException.expectMessage(
                String.format(
                        "Failed to dynamically load rule with fully qualified name %s.", badName));
        DynamicRuleChain chain =
                createWithRuleNames(
                        badName, "android.platform.test.rule.DynamicRuleChainTest$Rule1");
        chain.apply(mStatement, DESCRIPTION);
    }

    @Test
    public void testSupportsDuplicateSimpleRuleNames() throws Throwable {
        DynamicRuleChain chain =
                createWithRuleNames(
                        "DynamicRuleChainTest$Rule2",
                        "android.platform.test.rule.DynamicRuleChainTest$Rule1",
                        "DynamicRuleChainTest$Rule2");
        Statement applied = chain.apply(mStatement, DESCRIPTION);
        applied.evaluate();
        // Rule2's logs should be outside, and Rule1's logs should be inside.
        assertThat(sLogs)
                .containsExactly(
                        "Rule2 starting",
                        "Rule1 starting",
                        "Rule2 starting",
                        "Test execution",
                        "Rule2 finished",
                        "Rule1 finished",
                        "Rule2 finished");
    }

    @Test
    public void testSupportsDuplicateFullyQualifiedRuleNames() throws Throwable {
        DynamicRuleChain chain =
                createWithRuleNames(
                        "android.platform.test.rule.DynamicRuleChainTest$Rule1",
                        "DynamicRuleChainTest$Rule2",
                        "android.platform.test.rule.DynamicRuleChainTest$Rule1");
        Statement applied = chain.apply(mStatement, DESCRIPTION);
        applied.evaluate();
        // Rule2's logs should be outside, and Rule1's logs should be inside.
        assertThat(sLogs)
                .containsExactly(
                        "Rule1 starting",
                        "Rule2 starting",
                        "Rule1 starting",
                        "Test execution",
                        "Rule1 finished",
                        "Rule2 finished",
                        "Rule1 finished");
    }

    @Test
    public void testRulesOptionNameOverride() throws Throwable {
        DynamicRuleChain chain =
                createWithRulesOptionNameAndRuleNames(
                        "override-rules",
                        "DynamicRuleChainTest$Rule2",
                        "android.platform.test.rule.DynamicRuleChainTest$Rule1");
        Statement applied = chain.apply(mStatement, DESCRIPTION);
        applied.evaluate();
        assertThat(sLogs)
                .containsExactly(
                        "Rule2 starting",
                        "Rule1 starting",
                        "Test execution",
                        "Rule1 finished",
                        "Rule2 finished");
    }

    @Test
    public void testRulesOptionNameOverrideWithNullThrows() throws Throwable {
        expectedException.expectMessage(String.format("null or empty"));
        DynamicRuleChain chain =
                createWithRulesOptionNameAndRuleNames(
                        null,
                        "DynamicRuleChainTest$Rule2",
                        "android.platform.test.rule.DynamicRuleChainTest$Rule1");
    }

    @Test
    public void testRulesOptionNameOverrideWithEmptyThrows() throws Throwable {
        expectedException.expectMessage(String.format("null or empty"));
        DynamicRuleChain chain =
                createWithRulesOptionNameAndRuleNames(
                        "",
                        "DynamicRuleChainTest$Rule2",
                        "android.platform.test.rule.DynamicRuleChainTest$Rule1");
    }

    private DynamicRuleChain createWithRuleNames(String... ruleNames) {
        Bundle args = new Bundle();
        args.putString(DynamicRuleChain.DEFAULT_RULES_OPTION, String.join(",", ruleNames));
        return new DynamicRuleChain(args);
    }

    private DynamicRuleChain createWithRulesOptionNameAndRuleNames(
            String rulesOptionName, String... ruleNames) {
        Bundle args = new Bundle();
        args.putString(rulesOptionName, String.join(",", ruleNames));
        return new DynamicRuleChain(rulesOptionName, args);
    }

    public static class Rule1 extends TestWatcher {
        @Override
        public void starting(Description description) {
            sLogs.add("Rule1 starting");
        }

        @Override
        public void finished(Description description) {
            sLogs.add("Rule1 finished");
        }
    }

    public static class Rule2 extends TestWatcher {
        @Override
        public void starting(Description description) {
            sLogs.add("Rule2 starting");
        }

        @Override
        public void finished(Description description) {
            sLogs.add("Rule2 finished");
        }
    }
}
