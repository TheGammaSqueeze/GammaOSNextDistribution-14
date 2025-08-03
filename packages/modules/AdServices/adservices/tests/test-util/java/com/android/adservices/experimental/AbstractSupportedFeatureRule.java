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

package com.android.adservices.experimental;

import com.android.adservices.common.Logger;
import com.android.adservices.common.Logger.RealLogger;
import com.android.adservices.common.Nullable;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

// TODO(b/284971005): move to module-utils
// TODO(b/284971005): add examples
// TODO(b/284971005): add unit tests
/**
 * Rule used to properly check a test behavior depending on whether the device supports a given
 * feature.
 *
 * <p>This rule is abstract so subclass can define what a "feature" means. It also doesn't have any
 * dependency on Android code, so it can be used both on device-side and host-side tests.
 */
public abstract class AbstractSupportedFeatureRule implements TestRule {

    private static final String TAG = "SupportedFeatureRule";

    /** Defines the rule behavior. */
    public enum Mode {
        /**
         * All tests are assumed to be running in a device that supports the feature, unless they
         * are annotated otherwise (by annotations defined by {@link
         * AbstractSupportedFeatureRule#isFeatureNotSupportedAnnotation(Annotation)} or {@link
         * AbstractSupportedFeatureRule#isFeatureSupportedOrNotAnnotation(Annotation)}).
         */
        SUPPORTED_BY_DEFAULT,

        /**
         * All tests are assumed to be running in a device that does not support the platform,
         * unless they are annotated otherwise (by annotations defined by {@link
         * AbstractSupportedFeatureRule#isFeatureSupportedAnnotation(Annotation)} or {@link
         * AbstractSupportedFeatureRule#isFeatureSupportedOrNotAnnotation(Annotation)}).
         */
        NOT_SUPPORTED_BY_DEFAULT,

        /**
         * All tests are assumed to be running on any device (i.e., whether the device supports the
         * feature or not, unless they are annotated otherwise (by annotations defined by {@link
         * AbstractSupportedFeatureRule#isFeatureSupportedAnnotation(Annotation)} or {@link
         * AbstractSupportedFeatureRule#isFeatureNotSupportedAnnotation(Annotation)}).
         */
        SUPPORTED_OR_NOT_BY_DEFAULT,

        /**
         * The behavior of each test is defined by the annotations (defined by {@link
         * AbstractSupportedFeatureRule#isFeatureSupportedAnnotation(Annotation)}, {@link
         * AbstractSupportedFeatureRule#isFeatureNotSupportedAnnotation(Annotation)}, and {@link
         * AbstractSupportedFeatureRule#isFeatureSupportedOrNotAnnotation(Annotation)}).
         *
         * <p>The annotations could be defined in the test method itself, its class, or its
         * superclasses - the method annotations have higher priority, then the class, and so on...
         */
        ANNOTATION_ONLY
    }

    protected final Logger mLog;
    private final Mode mMode;

    /** Default constructor. */
    public AbstractSupportedFeatureRule(RealLogger logger, Mode mode) {
        mMode = Objects.requireNonNull(mode);
        mLog = new Logger(logger, TAG);
        mLog.d("Constructor: logger=%s, mode=%s", logger, mode);
    }

    // NOTE: ideally should be final and provide proper hooks for subclasses, but we might make it
    // non-final in the feature if needed
    @Override
    public final Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                boolean isFeatureSupported = isFeatureSupported();
                ExpectedTestBehavior expectedTestBehavior = getExpectedTestBehavior(description);
                mLog.d(
                        "Evaluating %s when feature supported is %b and expected test behavior is"
                                + " %s",
                        description, isFeatureSupported, expectedTestBehavior);
                boolean testShouldRunAsSupported = true; // will be used after the test
                switch (expectedTestBehavior) {
                    case SUPPORTED:
                        if (!isFeatureSupported) {
                            throwFeatureNotSupportedAssumptionViolatedException();
                        }
                        break;
                    case NOT_SUPPORTED:
                        if (isFeatureSupported) {
                            throwFeatureSupportedAssumptionViolatedException();
                        }
                        testShouldRunAsSupported = false;
                        break;
                    case SUPPORTED_OR_NOT:
                        testShouldRunAsSupported = isFeatureSupported;
                        mLog.d(
                                "not throwing AssumptionViolatedException because test should run"
                                        + " when feature is supported or not, but setting"
                                        + " testShouldRunAsSupported as %b",
                                testShouldRunAsSupported);
                        break;
                }
                Throwable thrown = null;
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    thrown = t;
                }
                mLog.d("Base evaluated: thrown=%s", thrown);
                afterTest(testShouldRunAsSupported, thrown);
            }
        };
    }

    /**
     * Called after a test is finished.
     *
     * <p>By default, it assumes that on unsupported devices the test should throw an exception
     * (which is defined by {@link #assertUnsupportedTestThrewRightException(Throwable)}), but
     * subclasses can override it to change the behavior (for example, by always throwing the
     * exception).
     */
    protected void afterTest(boolean testShouldRunAsSupported, @Nullable Throwable thrown)
            throws Throwable {
        if (testShouldRunAsSupported) {
            if (thrown != null) {
                throw thrown;
            }
        } else {
            if (thrown == null) {
                throwUnsupporteTestDidntThrowExpectedExceptionError();
            }
            assertUnsupportedTestThrewRightException(thrown);
        }
    }

    private ExpectedTestBehavior getExpectedTestBehavior(Description description) {
        // First, check the annotations in the test itself
        ExpectedTestBehavior expectedTestBehavior =
                getExpectedTestBehaviorByAnnotations(
                        description.getMethodName(), description.getAnnotations());
        if (expectedTestBehavior != null) {
            return expectedTestBehavior;
        }

        // Then in the test class...
        Class<?> clazz = description.getTestClass();
        do {
            expectedTestBehavior =
                    getExpectedTestBehaviorByAnnotations(
                            clazz.getName(), Arrays.asList(clazz.getAnnotations()));
            if (expectedTestBehavior != null) {
                return expectedTestBehavior;
            }
            // ...and its superclasses
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        // Finally, check the mode
        switch (mMode) {
            case SUPPORTED_BY_DEFAULT:
                mLog.v(
                        "getExpectedTestBehavior(): no annotation found, returning SUPPORTED by"
                                + " default");
                return ExpectedTestBehavior.SUPPORTED;
            case NOT_SUPPORTED_BY_DEFAULT:
                mLog.v(
                        "getExpectedTestBehavior(): no annotation found, returning NOT_SUPPORTED by"
                                + " default");
                return ExpectedTestBehavior.NOT_SUPPORTED;
            case SUPPORTED_OR_NOT_BY_DEFAULT:
                mLog.v(
                        "getExpectedTestBehavior(): no annotation found, returning SUPPORTED_OR_NOT"
                                + " by default");
                return ExpectedTestBehavior.SUPPORTED_OR_NOT;
            case ANNOTATION_ONLY:
                throw new IllegalStateException(
                        "No annotation found on "
                                + description
                                + ", its class, or its superclasses");
        }

        return ExpectedTestBehavior.SUPPORTED;
    }

    @Nullable
    private ExpectedTestBehavior getExpectedTestBehaviorByAnnotations(
            String where, Collection<Annotation> annotations) {
        // TODO(b/284971005): should scan all annotations (instead of returning when one is found)
        // to make sure it doesn't have both supported and unsupported (but would unit unit tests
        // to do so)
        for (Annotation annotation : annotations) {
            if (isFeatureSupportedAnnotation(annotation)) {
                mLog.v(
                        "getExpectedTestBehaviorByAnnotations(%s, %s): found 'supported' annotation"
                                + " %s, returning SUPPORTED",
                        where, annotations, annotation);
                return ExpectedTestBehavior.SUPPORTED;
            }
            if (isFeatureNotSupportedAnnotation(annotation)) {
                mLog.v(
                        "getExpectedTestBehaviorByAnnotations(%s, %s): found 'unsupported'"
                                + " annotation %s, returning NOT_SUPPORTED",
                        where, annotations, annotation);
                return ExpectedTestBehavior.NOT_SUPPORTED;
            }
            if (isFeatureSupportedOrNotAnnotation(annotation)) {
                mLog.v(
                        "getExpectedTestBehaviorByAnnotations(%s, %s): found 'supportedOrNot'"
                                + " annotation %s, returning SUPPORTED_OR_NOT",
                        where, annotations, annotation);
                return ExpectedTestBehavior.SUPPORTED_OR_NOT;
            }
        }
        mLog.v(
                "getExpectedTestBehaviorByAnnotations(%s, %s): found no annotation returning null",
                where, annotations);
        return null;
    }

    /**
     * Returns whether the given annotation indicates that the test should run in a device that
     * supports the feature.
     */
    protected boolean isFeatureSupportedAnnotation(Annotation annotation) {
        mLog.w("%s didn't override isFeatureSupportedAnnotation(); returning false", getClass());
        return false;
    }

    /**
     * Returns whether the given annotation indicates that the test should run in a device that does
     * not support the feature.
     */
    protected boolean isFeatureNotSupportedAnnotation(Annotation annotation) {
        mLog.w("%s didn't override isFeatureNotSupportedAnnotation(); returning false", getClass());
        return false;
    }

    /**
     * Returns whether the given annotation indicates that the test should run on each case (i.e.,
     * in a device that supports or does not support the feature.
     */
    protected boolean isFeatureSupportedOrNotAnnotation(Annotation annotation) {
        mLog.w(
                "%s didn't override isFeatureSupportedOrNotAnnotation(); returning false",
                getClass());
        return false;
    }

    /**
     * Called before the test is run, when the device doesn't support the feature and the test
     * requires it.
     *
     * <p>By the default throws a {@link AssumptionViolatedException} with a generic message.
     */
    protected abstract void throwFeatureNotSupportedAssumptionViolatedException();

    /**
     * Called before the test is run, when the device supports the feature and the test requires it
     * to not be supported.
     */
    protected abstract void throwFeatureSupportedAssumptionViolatedException();

    /**
     * Called after the test is run, when the code under test was expected to throw an exception
     * because the device doesn't support the feature, but the test didn't thrown any exception.
     */
    protected abstract void throwUnsupporteTestDidntThrowExpectedExceptionError();

    /**
     * Called after the test threw an exception when running in a device that doesn't support the
     * feature - it must verify that the exception is the expected one (for example, right type) and
     * throw an {@link AssertionError} if it's not.
     *
     * <p>By the default it checks that the exception is a {@link UnsupportedOperationException}.
     */
    protected void assertUnsupportedTestThrewRightException(Throwable thrown) {
        if (thrown instanceof UnsupportedOperationException) {
            mLog.d("test threw UnsupportedOperationException as expected: %s", thrown);
            return;
        }
        throw new AssertionError(
                "test should have thrown an UnsupportedOperationException, but instead threw "
                        + thrown,
                thrown);
    }

    /** Checks if the device supports the feature. */
    public abstract boolean isFeatureSupported() throws Exception;

    /** Defines the expected behavior of each test. */
    private enum ExpectedTestBehavior {
        SUPPORTED,
        NOT_SUPPORTED,
        SUPPORTED_OR_NOT
    }
}
