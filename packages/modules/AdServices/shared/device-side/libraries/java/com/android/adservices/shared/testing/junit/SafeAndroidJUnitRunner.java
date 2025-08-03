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

import android.util.Log;

import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// TODO(b/323197304): add unit tests for the runner itself
/**
 * Runner that ignores (some) errors when scanning a test class.
 *
 * <p>In particular, it won't crash the test process when methods in the test class somehow
 * references Android classes that are not available in the device SDK, like {@link
 * android.os.OutcomeReceiver} (which was introduced on Android RVC.
 *
 * <p>Notice that the it doesn't prevent crashes if such classes are exposed outside methods (like
 * as instance variables) - it's responsibility of the test author to "hide" those references. The
 * test authors should also make sure that test methods that use unsupported classes are skipped
 * (for example, using {@link com.android.adservices.common.SdkLevelSupportRule})
 */
public class SafeAndroidJUnitRunner extends EasilyExtensibleBlockJUnit4ClassRunner {

    private static final String TAG = SafeAndroidJUnitRunner.class.getSimpleName();

    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    public SafeAndroidJUnitRunner(Class<?> testClass) throws InitializationError {
        super(new SafeTestClass(testClass));
    }

    public SafeAndroidJUnitRunner(TestClass testClass) throws InitializationError {
        super(new SafeTestClass(testClass));
    }

    @Override
    protected TestClass createTestClass(Class<?> testClass) {
        return new SafeTestClass(testClass);
    }

    private static final class SafeTestClass extends TestClass {

        public SafeTestClass(Class<?> clazz) {
            super(clazz);
        }

        public SafeTestClass(TestClass testClass) {
            this(testClass.getJavaClass());
        }

        @Override
        protected void scanAnnotatedMembers(
                Map<Class<? extends Annotation>, List<FrameworkMethod>> methodsForAnnotations,
                Map<Class<? extends Annotation>, List<FrameworkField>> fieldsForAnnotations) {
            Class<?> clazz = getJavaClass();
            for (Class<?> eachClass : getSuperClasses(clazz)) {
                // JUnit calls Class.getDeclaredMethods() instead, which would throw a
                // NoClassDefFoundError when the test class somehow references an Android class
                // that's not available in the device (like android.os.OutcomeReceiver, which was
                // introduced on SC).
                // The "workaround" is to call Class.getMethods() instead, whose implementation
                // calls getDeclaredMethodsUnchecked() (which in turn wouldn't throw), then iterate
                // throw the methods and try/catch the exception
                for (Method eachMethod : getSafeDeclaredMethods(eachClass)) {
                    addToAnnotationLists(new FrameworkMethod(eachMethod), methodsForAnnotations);
                }
                // NOTE: we cannot use the same technique here, the NoSuchFieldException would be
                // thrown in other places
                for (Field eachField : getSortedDeclaredFields(eachClass)) {
                    addToAnnotationLists(new FrameworkField(eachField), fieldsForAnnotations);
                }
            }
        }

        private static Method[] getSafeDeclaredMethods(Class<?> clazz) {
            Log.d(TAG, "getSafeDeclaredMethods() for " + clazz);
            Method[] allMethods = clazz.getMethods();
            List<Method> safeMethodsList = new ArrayList<>();
            for (Method method : allMethods) {
                String methodName = "N/A";
                try {
                    methodName = method.getName();
                    if (!method.getDeclaringClass().equals(clazz)) {
                        // Ignore methods from superclass
                        continue;
                    }
                    // Try to "access" the method to force an exception it uses classes not
                    // available on this device SDK version.
                    String unused = method.toString();
                    safeMethodsList.add(method);
                } catch (Throwable e) {
                    Log.w(TAG, "ignoring method " + methodName + " due to exception: " + e);
                }
            }
            Method[] safeMethods = safeMethodsList.stream().toArray(Method[]::new);
            if (VERBOSE && !clazz.equals(Object.class)) {
                int numberMethods = safeMethods.length;
                Log.v(
                        TAG,
                        "Return following "
                                + numberMethods
                                + " methods for "
                                + clazz
                                + ": "
                                + Arrays.stream(safeMethods)
                                        .map(m -> m.getName())
                                        .collect(Collectors.toList()));
            }
            Method[] sortedMethods = MethodSorter.getSortedMethods(clazz, safeMethods);
            return sortedMethods;
        }
    }
}
