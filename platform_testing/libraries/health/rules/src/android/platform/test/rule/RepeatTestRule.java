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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;

import androidx.annotation.NonNull;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Repeat Test annotation is a helper class for running test multiple times.
 * Only to be used while running tests on local devices.
 */
public class RepeatTestRule implements TestRule {
    private static class RepeatTestStatement extends Statement {
        private final Statement mStatement;
        private final int mRepeatCount;

        RepeatTestStatement(Statement statement, int repeatCount) {
            this.mStatement = statement;
            this.mRepeatCount = repeatCount;
        }

        @Override
        public void evaluate() throws Throwable {
            for (int i = 0; i < mRepeatCount; i++) {
                mStatement.evaluate();
            }
        }
    }

    // Annotation for tests that need to be run multiple times.
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ANNOTATION_TYPE})
    public @interface RepeatTest {
        int value() default 1;
    }

    @NonNull
    @Override
    public Statement apply(@NonNull Statement base, @NonNull Description description) {
        RepeatTest repeat = description.getAnnotation(RepeatTest.class);
        if (repeat == null) {
            return base;
        }
        return new RepeatTestStatement(base, repeat.value());
    }
}
