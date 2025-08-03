/*
 * Copyright (C) 2024 The Android Open Source Project
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
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMember;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.MemberValueConsumer;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * This class extends {@link BlockJUnit4ClassRunner} and re-implement some methods that are private
 * as protected (by copying them "as-is", unless indicated otherwise) so they can be used by custom
 * runners.
 */
public abstract class EasilyExtensibleBlockJUnit4ClassRunner extends BlockJUnit4ClassRunner {

    private static final ThreadLocal<RuleContainer> sCurrentRuleContainer = new ThreadLocal<>();
    private static final FieldComparator FIELD_COMPARATOR = new FieldComparator();

    protected EasilyExtensibleBlockJUnit4ClassRunner(Class<?> testClass)
            throws InitializationError {
        super(testClass);
    }

    protected EasilyExtensibleBlockJUnit4ClassRunner(TestClass testClass)
            throws InitializationError {
        super(testClass);
    }

    // NOTE: this is not the same as EasilyExtensibleBlockJUnit4ClassRunner's method, as that one
    // takes an Object as testClass. But the body itself is copied.
    protected List<TestRule> getTestRules(TestClass testClass, Object target) {
        RuleCollector<TestRule> collector = new RuleCollector<>();
        testClass.collectAnnotatedMethodValues(target, Rule.class, TestRule.class, collector);
        testClass.collectAnnotatedFieldValues(target, Rule.class, TestRule.class, collector);
        return collector.mResult;
    }

    protected List<MethodRule> rules(TestClass testClass, Object target) {
        RuleCollector<MethodRule> collector = new RuleCollector<MethodRule>();
        testClass.collectAnnotatedMethodValues(target, Rule.class, MethodRule.class, collector);
        testClass.collectAnnotatedFieldValues(target, Rule.class, MethodRule.class, collector);
        return collector.mResult;
    }

    protected Statement withRules(
            FrameworkMethod method, TestClass testClass, Object target, Statement statement) {
        RuleContainer ruleContainer = new RuleContainer();
        sCurrentRuleContainer.set(ruleContainer);
        try {
            List<TestRule> testRules = getTestRules(testClass, target);
            for (MethodRule each : rules(testClass, target)) {
                if (!(each instanceof TestRule && testRules.contains(each))) {
                    ruleContainer.add(each);
                }
            }
            for (TestRule rule : testRules) {
                ruleContainer.add(rule);
            }
        } finally {
            sCurrentRuleContainer.remove();
        }
        return ruleContainer.apply(
                method,
                Description.createTestDescription(testClass.getJavaClass(), method.getName()),
                target,
                statement);
    }

    protected static final class RuleCollector<T> implements MemberValueConsumer<T> {
        final List<T> mResult = new ArrayList<T>();

        public void accept(FrameworkMember<?> member, T value) {
            Rule rule = member.getAnnotation(Rule.class);
            if (rule != null) {
                RuleContainer container = sCurrentRuleContainer.get();
                if (container != null) {
                    container.setOrder(value, rule.order());
                }
            }
            mResult.add(value);
        }
    }

    protected static Field[] getSortedDeclaredFields(Class<?> clazz) {
        Field[] declaredFields = clazz.getDeclaredFields();
        Arrays.sort(declaredFields, FIELD_COMPARATOR);
        return declaredFields;
    }

    protected static List<Class<?>> getSuperClasses(Class<?> testClass) {
        List<Class<?>> results = new ArrayList<Class<?>>();
        Class<?> current = testClass;
        while (current != null) {
            results.add(current);
            current = current.getSuperclass();
        }
        return results;
    }

    private static class FieldComparator implements Comparator<Field> {
        public int compare(Field left, Field right) {
            return left.getName().compareTo(right.getName());
        }
    }
}
