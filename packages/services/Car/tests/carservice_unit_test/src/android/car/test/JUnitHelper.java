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
package android.car.test;

import static com.google.common.truth.Truth.assertWithMessage;

import android.util.Log;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;

/**
 * Provides common {@code JUnit} artifacts for the tests.
 *
 */
final class JUnitHelper {

    private static final String TAG = JUnitHelper.class.getSimpleName();

    // Not a real test (i.e., it doesn't exist on this class), but it's passed to Description
    private static final String TEST_METHOD_BEING_EXECUTED = "testAmI..OrNot";

    public static Description newTestMethod(Annotation... annotations) {
        return newTestMethod(TEST_METHOD_BEING_EXECUTED, annotations);
    }

    public static Description newTestMethod(String methodName, Annotation... annotations) {
        return Description.createTestDescription(PermissionsCheckerRuleTest.class,
                methodName, annotations);
    }

    public static final class SimpleStatement<T extends Exception> extends Statement {

        private boolean mEvaluated;
        private Throwable mThrowable;

        @Override
        public void evaluate() throws Throwable {
            Log.d(TAG, "evaluate() called");
            mEvaluated = true;
            if (mThrowable != null) {
                Log.d(TAG, "Throwing " + mThrowable);
                throw mThrowable;
            }
        }

        public void failWith(Throwable t) {
            mThrowable = t;
        }

        public void assertEvaluated() {
            assertWithMessage("test method called").that(mEvaluated).isTrue();
        }
    }

    private JUnitHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }
}
