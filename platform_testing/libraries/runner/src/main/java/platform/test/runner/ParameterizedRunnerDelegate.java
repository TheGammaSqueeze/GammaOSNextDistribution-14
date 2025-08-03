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

import org.junit.Assert;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Encapsulates reflection operations needed to instantiate and parameterize a test instance for
 * tests run with {@link ParameterizedAndroidJunit4}. This logic is independent of the platform/
 * environment on which the test is running, so it's shared by all of the runners that {@link
 * ParameterizedAndroidJunit4} delegates to.
 *
 * @see org.junit.runners.Parameterized
 * @see org.robolectric.ParameterizedRobolectricTestRunner
 * @see com.google.android.testing.rsl.robolectric.junit.ParametrizedRslTestRunner
 */
class ParameterizedRunnerDelegate {

    private final int mParametersIndex;
    private final String mName;

    ParameterizedRunnerDelegate(int parametersIndex, String name) {
        this.mParametersIndex = parametersIndex;
        this.mName = name;
    }

    public String getName() {
        return mName;
    }

    public Object createTestInstance(Class<?> bootstrappedClass, final TestClass testClass)
            throws Exception {
        Constructor<?>[] constructors = bootstrappedClass.getConstructors();
        Assert.assertEquals(1, constructors.length);
        ClassLoader classLoader = bootstrappedClass.getClassLoader();
        if (!fieldsAreAnnotated(testClass)) {
            return constructors[0].newInstance(computeParams(classLoader, testClass));
        } else {
            Object instance = constructors[0].newInstance();
            injectParametersIntoFields(instance, classLoader, testClass);
            return instance;
        }
    }

    private Object[] computeParams(ClassLoader classLoader, final TestClass testClass)
            throws Exception {
        // Robolectric uses a different class loader when running the tests, so the parameters
        // objects
        // created by the test runner are not compatible with the parameters required by the test.
        // Instead, we compute the parameters within the test's class loader.
        try {
            List<Object> parametersList = getParametersList(testClass, classLoader);

            if (mParametersIndex >= parametersList.size()) {
                throw new Exception(
                        "Re-computing the parameter list returned a different number of parameters"
                                + " values. Is the data() method of your test non-deterministic?");
            }
            Object parametersObj = parametersList.get(mParametersIndex);
            return (parametersObj instanceof Object[])
                    ? (Object[]) parametersObj
                    : new Object[] {parametersObj};
        } catch (ClassCastException e) {
            throw new Exception(
                    String.format(
                            "%s.%s() must return a Collection of arrays.",
                            testClass.getName(), mName),
                    e);
        } catch (Exception exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new Exception(throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private void injectParametersIntoFields(
            Object testClassInstance, ClassLoader classLoader, final TestClass testClass)
            throws Exception {
        // Robolectric uses a different class loader when running the tests, so referencing
        // Parameter
        // directly causes type mismatches. Instead, we find its class within the test's class
        // loader.
        Class<?> parameterClass = getClassInClassLoader(Parameter.class, classLoader);
        Object[] parameters = computeParams(classLoader, testClass);
        HashSet<Integer> parameterFieldsFound = new HashSet<>();
        for (Field field : testClassInstance.getClass().getFields()) {
            Annotation parameter = field.getAnnotation((Class<Annotation>) parameterClass);
            if (parameter != null) {
                int index = (int) parameter.annotationType().getMethod("value").invoke(parameter);
                parameterFieldsFound.add(index);
                try {
                    field.set(testClassInstance, parameters[index]);
                } catch (IllegalArgumentException e) {
                    throw new Exception(
                            String.format(
                                    "%s: Trying to set %s with the value %s that is not the right"
                                            + " type (%s instead of %s).",
                                    testClass.getName(),
                                    field.getName(),
                                    parameters[index],
                                    parameters[index].getClass().getSimpleName(),
                                    field.getType().getSimpleName()),
                            e);
                }
            }
        }
        if (parameterFieldsFound.size() != parameters.length) {
            throw new IllegalStateException(
                    String.format(
                            "Provided %d parameters, but only found fields for parameters: %s",
                            parameters.length, parameterFieldsFound));
        }
    }

    static void validateFields(List<Throwable> errors, TestClass testClass) {
        // Ensure that indexes for parameters are correctly defined
        if (fieldsAreAnnotated(testClass)) {
            List<FrameworkField> annotatedFieldsByParameter =
                    getAnnotatedFieldsByParameter(testClass);
            int[] usedIndices = new int[annotatedFieldsByParameter.size()];
            for (FrameworkField each : annotatedFieldsByParameter) {
                int index = each.getField().getAnnotation(Parameter.class).value();
                if (index < 0 || index > annotatedFieldsByParameter.size() - 1) {
                    errors.add(
                            new Exception(
                                    String.format(
                                            Locale.US,
                                            "Invalid @Parameter value: %d. @Parameter fields"
                                                + " counted: %d. Please use an index between 0 and"
                                                + " %d.",
                                            index,
                                            annotatedFieldsByParameter.size(),
                                            annotatedFieldsByParameter.size() - 1)));
                } else {
                    usedIndices[index]++;
                }
            }
            for (int index = 0; index < usedIndices.length; index++) {
                int numberOfUses = usedIndices[index];
                if (numberOfUses == 0) {
                    errors.add(
                            new Exception(
                                    String.format(
                                            Locale.US, "@Parameter(%d) is never used.", index)));
                } else if (numberOfUses > 1) {
                    errors.add(
                            new Exception(
                                    String.format(
                                            Locale.US,
                                            "@Parameter(%d) is used more than once (%d).",
                                            index,
                                            numberOfUses)));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<FrameworkField> getAnnotatedFieldsByParameter(TestClass testClass) {
        try {
            return testClass.getAnnotatedFields(
                    (Class<Parameter>)
                            testClass
                                    .getJavaClass()
                                    .getClassLoader()
                                    .loadClass(Parameter.class.getName()));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean fieldsAreAnnotated(TestClass testClass) {
        return !getAnnotatedFieldsByParameter(testClass).isEmpty();
    }

    @SuppressWarnings("unchecked")
    static List<Object> getParametersList(TestClass testClass, ClassLoader classLoader)
            throws Throwable {
        return (List<Object>) getParametersMethod(testClass, classLoader).invokeExplosively(null);
    }

    @SuppressWarnings("unchecked")
    static FrameworkMethod getParametersMethod(TestClass testClass, ClassLoader classLoader)
            throws Exception {
        List<FrameworkMethod> methods =
                testClass.getAnnotatedMethods(
                        (Class<Parameters>) classLoader.loadClass(Parameters.class.getName()));
        for (FrameworkMethod each : methods) {
            int modifiers = each.getMethod().getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)) {
                return getFrameworkMethodInClassLoader(each, classLoader);
            }
        }

        throw new Exception(
                String.format(
                        "No public static parameters method on class %s", testClass.getName()));
    }

    /**
     * Returns the {@link FrameworkMethod} object for the given method in the provided class loader.
     */
    private static FrameworkMethod getFrameworkMethodInClassLoader(
            FrameworkMethod method, ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException {
        Method methodInClassLoader = getMethodInClassLoader(method.getMethod(), classLoader);
        if (methodInClassLoader.equals(method.getMethod())) {
            // The method was already loaded in the right class loader, return it as is.
            return method;
        }
        return new FrameworkMethod(methodInClassLoader);
    }

    /** Returns the {@link Method} object for the given method in the provided class loader. */
    private static Method getMethodInClassLoader(Method method, ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException {
        Class<?> declaringClass = method.getDeclaringClass();

        if (declaringClass.getClassLoader() == classLoader) {
            // The method was already loaded in the right class loader, return it as is.
            return method;
        }

        // Find the class in the class loader corresponding to the declaring class of the method.
        Class<?> declaringClassInClassLoader = getClassInClassLoader(declaringClass, classLoader);

        // Find the method with the same signature in the class loader.
        return declaringClassInClassLoader.getMethod(method.getName(), method.getParameterTypes());
    }

    /** Returns the {@link Class} object for the given class in the provided class loader. */
    private static Class<?> getClassInClassLoader(Class<?> klass, ClassLoader classLoader)
            throws ClassNotFoundException {
        if (klass.getClassLoader() == classLoader) {
            // The method was already loaded in the right class loader, return it as is.
            return klass;
        }

        // Find the class in the class loader corresponding to the declaring class of the method.
        return classLoader.loadClass(klass.getName());
    }
}
