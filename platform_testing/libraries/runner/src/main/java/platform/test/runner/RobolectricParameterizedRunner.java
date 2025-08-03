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

package platform.test.runner.parameterized;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.internal.SandboxTestRunner;

import java.util.List;

/**
 * Parameterized runner for vanilla (non-RSL) Robolectric tests.
 *
 * @see org.robolectric.ParameterizedRobolectricTestRunner
 */
public class RobolectricParameterizedRunner extends RobolectricTestRunner {

    private final ParameterizedRunnerDelegate mDelegate;

    public RobolectricParameterizedRunner(Class<?> type, int parametersIndex, String name)
            throws InitializationError {
        super(type);
        mDelegate = new ParameterizedRunnerDelegate(parametersIndex, name);
    }

    @Override
    protected String getName() {
        return mDelegate.getName();
    }

    @Override
    protected String testName(final FrameworkMethod method) {
        return method.getName() + getName();
    }

    @Override
    protected void validateConstructor(List<Throwable> errors) {
        validateOnlyOneConstructor(errors);
        if (ParameterizedRunnerDelegate.fieldsAreAnnotated(getTestClass())) {
            validateZeroArgConstructor(errors);
        }
    }

    @Override
    public String toString() {
        return "RobolectricParameterizedRunner " + mDelegate.getName();
    }

    @Override
    protected void validateFields(List<Throwable> errors) {
        super.validateFields(errors);
        ParameterizedRunnerDelegate.validateFields(errors, getTestClass());
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected SandboxTestRunner.HelperTestRunner getHelperTestRunner(Class bootstrappedTestClass) {
        try {
            return new HelperTestRunner(bootstrappedTestClass) {
                @Override
                protected void validateConstructor(List<Throwable> errors) {
                    RobolectricParameterizedRunner.this.validateOnlyOneConstructor(errors);
                }

                @Override
                protected Object createTest() throws Exception {
                    return mDelegate.createTestInstance(
                            getTestClass().getJavaClass(), getTestClass());
                }

                @Override
                public String toString() {
                    return "HelperTestRunner for " + RobolectricParameterizedRunner.this;
                }
            };
        } catch (InitializationError initializationError) {
            throw new RuntimeException(initializationError);
        }
    }
}
