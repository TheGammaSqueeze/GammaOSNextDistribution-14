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

package com.android.adservices.shared.testing.junit;

import org.junit.Rule;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Data structure for ordering of {@link TestRule}/{@link MethodRule} instances. Copied from package
 * {@link org.junit.runners} which has RuleContainer class as this is package protected.
 */
public final class RuleContainer {
    private final IdentityHashMap<Object, Integer> mOrderValues =
            new IdentityHashMap<Object, Integer>();
    private final List<TestRule> testRules = new ArrayList<TestRule>();
    private final List<MethodRule> methodRules = new ArrayList<MethodRule>();

    /** Sets order value for the specified rule. */
    public void setOrder(Object rule, int order) {
        mOrderValues.put(rule, order);
    }

    public void add(MethodRule methodRule) {
        methodRules.add(methodRule);
    }

    public void add(TestRule testRule) {
        testRules.add(testRule);
    }

    static final Comparator<RuleEntry> ENTRY_COMPARATOR =
            new Comparator<RuleEntry>() {
                public int compare(RuleEntry o1, RuleEntry o2) {
                    int result = compareInt(o1.order, o2.order);
                    return result != 0 ? result : o1.type - o2.type;
                }

                private int compareInt(int a, int b) {
                    return (a < b) ? 1 : (a == b ? 0 : -1);
                }
            };

    /** Returns entries in the order how they should be applied, i.e. inner-to-outer. */
    private List<RuleEntry> getSortedEntries() {
        List<RuleEntry> ruleEntries =
                new ArrayList<RuleEntry>(methodRules.size() + testRules.size());
        for (MethodRule rule : methodRules) {
            ruleEntries.add(
                    new RuleEntry(rule, RuleEntry.TYPE_METHOD_RULE, mOrderValues.get(rule)));
        }
        for (TestRule rule : testRules) {
            ruleEntries.add(new RuleEntry(rule, RuleEntry.TYPE_TEST_RULE, mOrderValues.get(rule)));
        }
        Collections.sort(ruleEntries, ENTRY_COMPARATOR);
        return ruleEntries;
    }

    /** Applies all the rules ordered accordingly to the specified {@code statement}. */
    public Statement apply(
            FrameworkMethod method, Description description, Object target, Statement statement) {
        if (methodRules.isEmpty() && testRules.isEmpty()) {
            return statement;
        }
        Statement result = statement;
        for (RuleEntry ruleEntry : getSortedEntries()) {
            if (ruleEntry.type == RuleEntry.TYPE_TEST_RULE) {
                result = ((TestRule) ruleEntry.rule).apply(result, description);
            } else {
                result = ((MethodRule) ruleEntry.rule).apply(result, method, target);
            }
        }
        return result;
    }

    /**
     * Returns rule instances in the order how they should be applied, i.e. inner-to-outer.
     * VisibleForTesting
     */
    List<Object> getSortedRules() {
        List<Object> result = new ArrayList<Object>();
        for (RuleEntry entry : getSortedEntries()) {
            result.add(entry.rule);
        }
        return result;
    }

    static class RuleEntry {
        static final int TYPE_TEST_RULE = 1;
        static final int TYPE_METHOD_RULE = 0;

        final Object rule;
        final int type;
        final int order;

        RuleEntry(Object rule, int type, Integer order) {
            this.rule = rule;
            this.type = type;
            this.order = order != null ? order.intValue() : Rule.DEFAULT_ORDER;
        }
    }
}
