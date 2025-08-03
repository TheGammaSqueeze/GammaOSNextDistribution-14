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
package android.platform.test.rules;

import org.junit.Assume;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.lang.reflect.Modifier;
import java.util.function.Supplier;

/** A JUnit Rule for ignoring a test based on a condition. */
public class ConditionalIgnoreRule implements MethodRule {
    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        if (method.getAnnotation(ConditionalIgnore.class) != null) {
            ConditionalIgnore annotation = method.getAnnotation(ConditionalIgnore.class);
            Supplier<Boolean> condition;
            Class<? extends Supplier<Boolean>> conditionType = annotation.condition();
            boolean conditionTypeStandalone =
                    !conditionType.isMemberClass()
                            || Modifier.isStatic(conditionType.getModifiers());
            if (!conditionTypeStandalone
                    && !target.getClass().isAssignableFrom(conditionType.getDeclaringClass())) {
                String msg =
                        "Conditional class '%s' is a member class but was not declared inside the"
                                + " test case using it.\n"
                                + "Either make this class a static class, standalone class (by"
                                + " declaring it in its own file) or move it inside the test case"
                                + " using it";
                throw new IllegalArgumentException(String.format(msg, conditionType.getName()));
            }
            try {
                condition =
                        conditionTypeStandalone
                                ? conditionType.getDeclaredConstructor().newInstance()
                                : conditionType
                                        .getDeclaredConstructor(target.getClass())
                                        .newInstance(target);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (condition.get()) {
                return new IgnoreStatement(condition);
            }
        }
        return base;
    }

    private static class IgnoreStatement extends Statement {
        private final Supplier<Boolean> mCondition;

        IgnoreStatement(Supplier<Boolean> condition) {
            this.mCondition = condition;
        }

        @Override
        public void evaluate() {
            Assume.assumeTrue("Ignored by " + mCondition.getClass().getSimpleName(), false);
        }
    }
}
