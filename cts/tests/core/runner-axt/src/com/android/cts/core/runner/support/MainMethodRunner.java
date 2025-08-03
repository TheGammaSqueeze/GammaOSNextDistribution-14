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

package com.android.cts.core.runner.support;

import junit.framework.TestCase;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public class MainMethodRunner extends BlockJUnit4ClassRunner {

    private MainMethodRunner(TestClass clazz) throws InitializationError {
        super(clazz);
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        Method m = getMainMethod();
        if (m != null) {
            return List.of(new FrameworkMethod(m));
        }
        return List.of();
    }

    @Override
    protected Statement methodInvoker(FrameworkMethod method, Object test) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // Invoke the static main method like calling Main.main(new String[0]);
                method.invokeExplosively(null, new Object[] {new String[0]});
            }
        };
    }

    /**
     * @return null if no public static main method is found.
     */
    public static MainMethodRunner createRunner(Class<?> clazz) {
        Method m = getMainMethod(clazz);
        // Do not try to initialize MainMethodRunner if the class has no main method.
        if (m == null) {
            return null;
        }

        TestClass testClass;
        try {
            testClass = new TestClass(clazz);
        } catch (IllegalArgumentException e) {
            // Ignore and this class isn't a valid for running the main method.
            // Usually, It means that the class doesn't have one exact constructor.
            return null;
        }
        // Do not try to run test using other test frameworks.
        if (useOtherTestFrameworks(testClass)) {
            return null;
        }
        try {
            return new MainMethodRunner(testClass);
        } catch (InitializationError e) {
            throw new IllegalArgumentException("Class: " + clazz.getName(), e);
        }
    }

    private static boolean useOtherTestFrameworks(TestClass testClass) {
        if (TestCase.class.isAssignableFrom(testClass.getJavaClass())) {
            return true;
        }

        if (testClass.getAnnotation(org.testng.annotations.Test.class) != null
                || testClass.getAnnotation(org.junit.Test.class) != null) {
            return true;
        }

        if (!testClass.getAnnotatedMethods(org.testng.annotations.Test.class).isEmpty()
                || !testClass.getAnnotatedMethods(org.junit.Test.class).isEmpty()) {
            return true;
        }

        return false;
    }

    public Method getMainMethod() {
        return getMainMethod(getTestClass().getJavaClass());
    }

    public static Method getMainMethod(Class<?> target) {
        try {
            Method method = target.getMethod("main", String[].class);
            int modifiers = method.getModifiers();
            if (!Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
                return null;
            }
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

}
