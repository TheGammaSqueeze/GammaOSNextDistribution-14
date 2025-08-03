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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;

import org.junit.runner.Runner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import java.lang.reflect.Constructor;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Combines parameterization functionality of the JUnit {@code Parameterized} runner with the
 * multi-platform dispatch functionality of the {@code AndroidJUnit4} runner.
 *
 * <p>Tests which run on Robolectric only don't need to use this runner; they can use {@link
 * org.robolectric.ParameterizedRobolectricTestRunner} or {@link
 * com.google.android.testing.rsl.robolectric.junit.ParametrizedRslTestRunner} instead.
 *
 * @see org.junit.runners.Parameterized
 * @see androidx.test.runners.AndroidJunit4
 */
public final class ParameterizedAndroidJunit4 extends Suite {

    private static final String JUNIT_RUNNER_SYSTEM_PROPERTY = "android.junit.runner";
    private static final String JAVA_RUNTIME_SYSTEM_PROPERTY = "java.runtime.name";
    private static final String JAVA_RUNTIME_ANDROID = "android";

    private static final String ROBOLECTRIC_RUNNER_CLASS_NAME =
            "org.robolectric.RobolectricTestRunner";
    private static final String ROBOLECTRIC_PARAMETERIZED_RUNNER_CLASS_NAME =
            "platform.test.runner.parameterized.RobolectricParameterizedRunner";

    private static final String ANDROID_RUNNER_CLASS_NAME =
            "androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner";
    private static final String ANDROID_PARAMETERIZED_RUNNER_CLASS_NAME =
            "platform.test.runner.parameterized.AndroidParameterizedRunner";

    private static final String RSL_RUNNER_CLASS_NAME =
            "com.google.android.testing.rsl.robolectric.junit.RslTestRunner";
    private static final String RSL_PARAMETERIZED_RUNNER_CLASS_NAME =
            "platform.test.runner.parameterized.RslParameterizedRunner";

    private final ArrayList<Runner> mRunners = new ArrayList<>();

    public ParameterizedAndroidJunit4(Class<?> klass) throws Throwable {
        super(klass, ImmutableList.of());
        TestClass testClass = getTestClass();
        ClassLoader classLoader = getClass().getClassLoader();
        Parameters parameters =
                ParameterizedRunnerDelegate.getParametersMethod(testClass, classLoader)
                        .getAnnotation(Parameters.class);
        List<Object> parametersList =
                ParameterizedRunnerDelegate.getParametersList(testClass, classLoader);
        for (int i = 0; i < parametersList.size(); i++) {
            Object parametersObj = parametersList.get(i);
            Object[] parameterArray =
                    (parametersObj instanceof Object[])
                            ? (Object[]) parametersObj
                            : new Object[] {parametersObj};
            mRunners.add(makeTestRunner(testClass, parameters, i, parameterArray));
        }
    }

    @SuppressWarnings("unchecked")
    private static Runner makeTestRunner(
            TestClass testClass, Parameters parameters, int i, Object[] parameterArray)
            throws InitializationError {
        String parameterizedRunnerClassName;
        String runnerClassName = System.getProperty(JUNIT_RUNNER_SYSTEM_PROPERTY, null);
        if (runnerClassName == null) {
            if (isRunningOnAndroid()) {
                parameterizedRunnerClassName = ANDROID_PARAMETERIZED_RUNNER_CLASS_NAME;
            } else {
                parameterizedRunnerClassName = ROBOLECTRIC_PARAMETERIZED_RUNNER_CLASS_NAME;
            }
        } else {
            if (ROBOLECTRIC_RUNNER_CLASS_NAME.equals(runnerClassName)) {
                parameterizedRunnerClassName = ROBOLECTRIC_PARAMETERIZED_RUNNER_CLASS_NAME;
            } else if (RSL_RUNNER_CLASS_NAME.equals(runnerClassName)) {
                parameterizedRunnerClassName = RSL_PARAMETERIZED_RUNNER_CLASS_NAME;
            } else if (ANDROID_RUNNER_CLASS_NAME.equals(runnerClassName)) {
                parameterizedRunnerClassName = ANDROID_PARAMETERIZED_RUNNER_CLASS_NAME;
            } else {
                throw new IllegalStateException(
                        "Don't know how to parameterized test runner class " + runnerClassName);
            }
        }
        try {
            Class<? extends Runner> parameterizedRunnerClass =
                    (Class<? extends Runner>)
                            testClass
                                    .getJavaClass()
                                    .getClassLoader()
                                    .loadClass(parameterizedRunnerClassName);
            Constructor<? extends Runner> ctor =
                    parameterizedRunnerClass.getConstructor(Class.class, int.class, String.class);
            return ctor.newInstance(
                    testClass.getJavaClass(), i, nameFor(parameters.name(), i, parameterArray));
        } catch (Exception e) {
            throw new InitializationError(e);
        }
    }

    @Override
    protected List<Runner> getChildren() {
        return mRunners;
    }

    /**
     * Returns true if the test is running as an Android instrumentation test, on a real Android
     * device or emulator.
     */
    private static boolean isRunningOnAndroid() {
        return Ascii.toLowerCase(System.getProperty(JAVA_RUNTIME_SYSTEM_PROPERTY))
                .contains(JAVA_RUNTIME_ANDROID);
    }

    private static String nameFor(String namePattern, int index, Object[] parameters) {
        String finalPattern = namePattern.replace("{index}", Integer.toString(index));
        String name = MessageFormat.format(finalPattern, parameters);
        return "[" + name + "]";
    }
}
