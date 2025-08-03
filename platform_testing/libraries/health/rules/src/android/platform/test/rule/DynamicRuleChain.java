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
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;
import org.junit.runner.Description;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A rule that loads other ({@link TestRule}s) at runtime.
 *
 * <p>Rules are supplied as a comma-separated list. If the rule is in the {@code
 * android.platform.test.rule} package (and not in its sub-packages), it can be passed via their
 * simple class name; otherwise, its fully-qualified class name must be used.
 *
 * <p>Each passed-in rule wraps around the subsequent rule; this fits the mental model of JUnit's
 * {@code RuleChain}. For example, if {@code "rule1,rule2,rule3"} are passed in, {@code rule1} will
 * wrap around {@code rule2} and {@code rule2} will wrap around {@code rule3}; {@code rule3} will be
 * applied closest to the test method.
 */
public class DynamicRuleChain implements TestRule {
    private static final String LOG_TAG = DynamicRuleChain.class.getSimpleName();

    @VisibleForTesting static final String DEFAULT_RULES_OPTION = "dynamic-rules";

    @VisibleForTesting static final String RULES_PACKAGE = "android.platform.test.rule";

    private String mRulesOptionName = DEFAULT_RULES_OPTION;
    private Bundle mArgs;

    public DynamicRuleChain() {
        this(InstrumentationRegistry.getArguments());
    }

    public DynamicRuleChain(Bundle args) {
        mArgs = args;
    }

    public DynamicRuleChain(String rulesOptionName, Bundle args) {
        this(args);
        if (rulesOptionName == null || rulesOptionName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Rules option name override must not be null or empty.");
        }
        mRulesOptionName = rulesOptionName;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        List<String> ruleNames = Arrays.asList(mArgs.getString(mRulesOptionName, "").split(","));
        // The inner rules need to be applied first, so reverse the class names first.
        Collections.reverse(ruleNames);
        // Instantiate rules and apply them one-by-one.
        // JUnit's RunRules is not used here because its ordering of rules is not clearly defined.
        Statement statement = base;
        for (String ruleName : ruleNames) {
            if (ruleName.isEmpty()) {
                continue;
            }
            TestRule rule = null;
            // We could use a regex here, but this is simpler and should work just as well.
            if (ruleName.contains(".")) {
                Log.i(
                        LOG_TAG,
                        String.format(
                                "Attempting to dynamically load rule with fully qualified name %s.",
                                ruleName));
                try {
                    rule = loadRuleByFullyQualifiedName(ruleName);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Failed to dynamically load rule with fully qualified name %s.",
                                    ruleName),
                            e);
                }
            } else {
                String fullName = String.format("%s.%s", RULES_PACKAGE, ruleName);
                Log.i(
                        LOG_TAG,
                        String.format(
                                "Attempting to dynamically load rule with simple class name %s"
                                        + " (fully qualified name: %s).",
                                ruleName, fullName));
                try {
                    rule = loadRuleByFullyQualifiedName(fullName);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Failed to dynamically load rule with simple class name %s.",
                                    ruleName),
                            e);
                }
            }
            statement = rule.apply(statement, description);
        }
        return statement;
    }

    private TestRule loadRuleByFullyQualifiedName(String name) throws Exception {
        // Load the rule class using reflection.
        Class<?> loadedClass = null;
        try {
            loadedClass = DynamicRuleChain.class.getClassLoader().loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    String.format("Could not find class with fully qualified name %s.", name));
        }
        // Ensure that the class found is a TestRule.
        if (loadedClass == null || (!TestRule.class.isAssignableFrom(loadedClass))) {
            throw new IllegalArgumentException(
                    String.format("Class %s is not a TestRule.", loadedClass));
        }

        // Use the default constructor to create a rule instance.
        try {
            Constructor<?> constructor = loadedClass.getConstructor();
            // Cast is safe as we have vetted that loadedClass is a TestRule.
            return (TestRule) constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Rule %s cannot be instantiated with an empty constructor",
                            loadedClass),
                    e);
        }
    }
}
