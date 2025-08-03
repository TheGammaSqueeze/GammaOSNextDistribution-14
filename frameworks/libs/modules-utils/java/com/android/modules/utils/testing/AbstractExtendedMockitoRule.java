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
package com.android.modules.utils.testing;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import android.annotation.Nullable;
import android.util.Log;

import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.testing.AbstractExtendedMockitoRule.AbstractBuilder;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStaticClasses;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStaticClasses;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;
import org.mockito.MockitoFramework;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Base class for {@link ExtendedMockitoRule} and other rules - it contains the logic so those
 * classes can be final.
 */
public abstract class AbstractExtendedMockitoRule<R extends AbstractExtendedMockitoRule<R, B>,
        B extends AbstractBuilder<R, B>> implements TestRule {

    private static final String TAG = AbstractExtendedMockitoRule.class.getSimpleName();

    private static final AnnotationFetcher<SpyStatic, SpyStaticClasses>
        sSpyStaticAnnotationFetcher = new AnnotationFetcher<>(SpyStatic.class,
                SpyStaticClasses.class, r -> r.value());
    private static final AnnotationFetcher<MockStatic, MockStaticClasses>
        sMockStaticAnnotationFetcher = new AnnotationFetcher<>(MockStatic.class,
                MockStaticClasses.class, r -> r.value());

    private final Object mTestClassInstance;
    private final Strictness mStrictness;
    private @Nullable final MockitoFramework mMockitoFramework;
    private @Nullable final Runnable mAfterSessionFinishedCallback;
    private final Set<Class<?>> mMockedStaticClasses;
    private final Set<Class<?>> mSpiedStaticClasses;
    private final List<StaticMockFixture> mStaticMockFixtures;
    private final boolean mClearInlineMocks;

    private MockitoSession mMockitoSession;

    protected AbstractExtendedMockitoRule(B builder) {
        mTestClassInstance = builder.mTestClassInstance;
        mStrictness = builder.mStrictness;
        mMockitoFramework = builder.mMockitoFramework;
        mMockitoSession = builder.mMockitoSession;
        mAfterSessionFinishedCallback = builder.mAfterSessionFinishedCallback;
        mMockedStaticClasses = builder.mMockedStaticClasses;
        mSpiedStaticClasses = builder.mSpiedStaticClasses;
        mStaticMockFixtures = builder.mStaticMockFixtures == null ? Collections.emptyList()
                : builder.mStaticMockFixtures;
        mClearInlineMocks = builder.mClearInlineMocks;
        Log.v(TAG, "strictness=" + mStrictness + ", testClassInstance" + mTestClassInstance
                + ", mockedStaticClasses=" + mMockedStaticClasses
                + ", spiedStaticClasses=" + mSpiedStaticClasses
                + ", staticMockFixtures=" + mStaticMockFixtures
                + ", afterSessionFinishedCallback=" + mAfterSessionFinishedCallback
                + ", mockitoFramework=" + mMockitoFramework
                + ", mockitoSession=" + mMockitoSession
                + ", clearInlineMocks=" + mClearInlineMocks);
    }

    /**
     * Gets the mocked static classes present in the given test.
     *
     * <p>By default, it returns the classes defined by {@link AbstractBuilder#mockStatic(Class)}
     * plus the classes present in the {@link MockStatic} and {@link MockStaticClasses}
     * annotations (presents in the test method, its class, or its superclasses).
     */
    protected Set<Class<?>> getMockedStaticClasses(Description description) {
        Set<Class<?>> staticClasses = new HashSet<>(mMockedStaticClasses);
        sMockStaticAnnotationFetcher.getAnnotations(description)
                .forEach(a -> staticClasses.add(a.value()));
        return Collections.unmodifiableSet(staticClasses);
    }

    /**
     * Gets the spied static classes present in the given test.
     *
     * <p>By default, it returns the classes defined by {@link AbstractBuilder#spyStatic(Class)}
     * plus the classes present in the {@link SpyStatic} and {@link SpyStaticClasses}
     * annotations (presents in the test method, its class, or its superclasses).
     */
    protected Set<Class<?>> getSpiedStaticClasses(Description description) {
        Set<Class<?>> staticClasses = new HashSet<>(mSpiedStaticClasses);
        sSpyStaticAnnotationFetcher.getAnnotations(description)
                .forEach(a -> staticClasses.add(a.value()));
        return Collections.unmodifiableSet(staticClasses);
    }

    /**
     * Gets whether the rule should clear the inline mocks after the given test.
     *
     * <p>By default, it returns {@code} (unless the rule was built with
     * {@link AbstractBuilder#dontClearInlineMocks()}, but subclasses can override to change the
     * behavior (for example, to decide based on custom annotations).
     */
    protected boolean getClearInlineMethodsAtTheEnd(Description description) {
        return mClearInlineMocks;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                createMockitoSession(base, description);
                Throwable error = null;
                try {
                    // TODO(b/296937563): need to add unit tests that make sure the session is
                    // always closed
                    base.evaluate();
                } catch (Throwable t) {
                    error = t;
                }
                try {
                    tearDown(description, error);
                } catch (Throwable t) {
                    if (error != null) {
                        Log.e(TAG, "Teardown failed for " + description.getDisplayName()
                            + ", but not throwing it because test also threw (" + error + ")", t);
                    } else {
                        error = t;
                    }
                }
                if (error != null) {
                    // TODO(b/296937563): ideally should also add unit tests to make sure the
                    // test error is thrown (in case tearDown() above fails)
                    throw error;
                }
            }
        };
    }

    private void createMockitoSession(Statement base, Description description) {
        // TODO(b/296937563): might be prudent to save the session statically so it's explicitly
        // closed in case it fails to be created again if for some reason it was not closed by us
        // (although that should not happen)
        Log.v(TAG, "Creating session builder with strictness " + mStrictness);
        StaticMockitoSessionBuilder mSessionBuilder = mockitoSession().strictness(mStrictness);

        setUpMockedClasses(description, mSessionBuilder);

        if (mTestClassInstance != null) {
            Log.v(TAG, "Initializing mocks on " + description + " using " + mSessionBuilder);
            mSessionBuilder.initMocks(mTestClassInstance);
        } else {
            Log.v(TAG, "NOT Initializing mocks on " + description + " as requested by builder");
        }

        if (mMockitoSession != null) {
            Log.d(TAG, "NOT creating session as set on builder: " + mMockitoSession);
        } else {
            Log.d(TAG, "Creating mockito session using " + mSessionBuilder);
            mMockitoSession = mSessionBuilder.startMocking();
        }

        setUpMockBehaviors();
    }

    private void setUpMockedClasses(Description description,
            StaticMockitoSessionBuilder sessionBuilder) {
        if (!mStaticMockFixtures.isEmpty()) {
            for (StaticMockFixture fixture : mStaticMockFixtures) {
                Log.v(TAG, "Calling setUpMockedClasses(" + sessionBuilder + ") on " + fixture);
                fixture.setUpMockedClasses(sessionBuilder);
            }
        }
        for (Class<?> clazz: getMockedStaticClasses(description)) {
            Log.v(TAG, "Calling mockStatic() on " + clazz);
            sessionBuilder.mockStatic(clazz);
        }
        for (Class<?> clazz: getSpiedStaticClasses(description)) {
            Log.v(TAG, "Calling spyStatic() on " + clazz);
            sessionBuilder.spyStatic(clazz);
        }
    }

    private void setUpMockBehaviors() {
        if (mStaticMockFixtures.isEmpty()) {
            Log.v(TAG, "setUpMockBehaviors(): not needed, mStaticMockFixtures is empty");
            return;
        }
        for (StaticMockFixture fixture : mStaticMockFixtures) {
            Log.v(TAG, "Calling setUpMockBehaviors() on " + fixture);
            fixture.setUpMockBehaviors();
        }
    }

    private void tearDown(Description description, Throwable e) {
        Log.d(TAG, "Finishing mockito session " + mMockitoSession + " on " + description
                + (e == null ? "" : " (which failed with " + e + ")"));
        try {
            try {
                mMockitoSession.finishMocking(e);
                mMockitoSession = null;
            } finally {
                // Must iterate in reverse order
                for (int i = mStaticMockFixtures.size() - 1; i >= 0; i--) {
                    StaticMockFixture fixture = mStaticMockFixtures.get(i);
                    Log.v(TAG, "Calling tearDown() on " + fixture);
                    fixture.tearDown();
                }
                if (mAfterSessionFinishedCallback != null) {
                    mAfterSessionFinishedCallback.run();
                }
            }
        } finally {
            clearInlineMocks(description);
        }
    }

    private void clearInlineMocks(Description description) {
        boolean clearIt = getClearInlineMethodsAtTheEnd(description);
        if (!clearIt) {
            Log.d(TAG, "NOT calling clearInlineMocks() as set on builder");
            return;
        }
        if (mMockitoFramework != null) {
            Log.v(TAG, "Calling clearInlineMocks() on custom mockito framework: "
                    + mMockitoFramework);
            mMockitoFramework.clearInlineMocks();
            return;
        }
        Log.v(TAG, "Calling Mockito.framework().clearInlineMocks()");
        Mockito.framework().clearInlineMocks();
    }

    /**
     * Builder for the rule.
     */
    public static abstract class AbstractBuilder<R extends
            AbstractExtendedMockitoRule<R, B>, B extends AbstractBuilder<R, B>> {
        final Object mTestClassInstance;
        final Set<Class<?>> mMockedStaticClasses = new HashSet<>();
        final Set<Class<?>> mSpiedStaticClasses = new HashSet<>();
        @Nullable List<StaticMockFixture> mStaticMockFixtures;
        Strictness mStrictness = Strictness.LENIENT;
        @Nullable MockitoFramework mMockitoFramework;
        @Nullable MockitoSession mMockitoSession;
        @Nullable Runnable mAfterSessionFinishedCallback;
        boolean mClearInlineMocks = true;

        /**
         * Constructs a builder for the giving test instance (typically {@code this}) and initialize
         * mocks on it.
         */
        protected AbstractBuilder(Object testClassInstance) {
            mTestClassInstance = Objects.requireNonNull(testClassInstance);
        }

        /**
         * Constructs a builder that doesn't initialize mocks.
         *
         * <p>Typically used on test classes that already initialize mocks somewhere else.
         */
        protected AbstractBuilder() {
            mTestClassInstance = null;
        }

        /**
         * Sets the mock strictness.
         */
        public final B setStrictness(Strictness strictness) {
            mStrictness = Objects.requireNonNull(strictness);
            return thisBuilder();
        }

        /**
         * Same as {@link
         * com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder#mockStatic(Class)}.
         *
         * @throws IllegalStateException if the same class was already passed to
         *   {@link #mockStatic(Class)} or {@link #spyStatic(Class)}.
         */
        public final B mockStatic(Class<?> clazz) {
            mMockedStaticClasses.add(checkClassNotMockedOrSpied(clazz));
            return thisBuilder();
        }

        /**
         * Same as {@link
         * com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder#spyStatic(Class)}.
         *
         * @throws IllegalStateException if the same class was already passed to
         *   {@link #mockStatic(Class)} or {@link #spyStatic(Class)}.
         */
        public final B spyStatic(Class<?> clazz) {
            mSpiedStaticClasses.add(checkClassNotMockedOrSpied(clazz));
            return thisBuilder();
        }

        /**
         * Uses the supplied {@link StaticMockFixture} as well.
         */
        @SafeVarargs
        public final B addStaticMockFixtures(
                Supplier<? extends StaticMockFixture>... suppliers) {
            List<StaticMockFixture> fixtures = Arrays
                    .stream(Objects.requireNonNull(suppliers)).map(s -> s.get())
                    .collect(Collectors.toList());
            if (mStaticMockFixtures == null) {
                mStaticMockFixtures = fixtures;
            } else {
                mStaticMockFixtures.addAll(fixtures);
            }
            return thisBuilder();
        }

        /**
         * Runs the given {@code runnable} after the session finished.
         *
         * <p>Typically used for clean-up code that cannot be executed on {@code &#064;After}, as
         * those methods are called before the session is finished.
         */
        public final B afterSessionFinished(Runnable runnable) {
            mAfterSessionFinishedCallback = Objects.requireNonNull(runnable);
            return thisBuilder();
        }

        /**
         * By default, it cleans up inline mocks after the session is closed to prevent OutOfMemory
         * errors (see <a href="https://github.com/mockito/mockito/issues/1614">external bug</a>
         * and/or <a href="http://b/259280359">internal bug</a>); use this method to not do so.
         */
        public final B dontClearInlineMocks() {
            mClearInlineMocks  = false;
            return thisBuilder();
        }

        // Used by ExtendedMockitoRuleTest itself
        @VisibleForTesting
        final B setMockitoFrameworkForTesting(MockitoFramework mockitoFramework) {
            mMockitoFramework = Objects.requireNonNull(mockitoFramework);
            return thisBuilder();
        }

        // Used by ExtendedMockitoRuleTest itself
        @VisibleForTesting
        final B setMockitoSessionForTesting(MockitoSession mockitoSession) {
            mMockitoSession = Objects.requireNonNull(mockitoSession);
            return thisBuilder();
        }

        /**
         * Builds the rule.
         */
        public abstract R build();

        @SuppressWarnings("unchecked")
        private B thisBuilder() {
            return (B) this;
        }

        private Class<?> checkClassNotMockedOrSpied(Class<?> clazz) {
            Objects.requireNonNull(clazz);
            checkState(!mMockedStaticClasses.contains(clazz), "class %s already mocked", clazz);
            checkState(!mSpiedStaticClasses.contains(clazz), "class %s already spied", clazz);
            return clazz;
        }
    }

    // Copied from com.android.internal.util.Preconditions, as that method is not available on RVC
    private static void checkState(boolean expression, String messageTemplate,
            Object... messageArgs) {
        if (!expression) {
            throw new IllegalStateException(String.format(messageTemplate, messageArgs));
        }
    }

    // TODO: make it public so it can be used by other modules
    private static final class AnnotationFetcher<A extends Annotation, R extends Annotation> {

        private final Class<A> mAnnotationType;
        private final Class<R> mRepeatableType;
        private final Function<R, A[]> mConverter;

        AnnotationFetcher(Class<A> annotationType, Class<R> repeatableType,
                Function<R, A[]> converter) {
            mAnnotationType = annotationType;
            mRepeatableType = repeatableType;
            mConverter = converter;
        }

        private void add(Set<A> allAnnotations, R repeatableAnnotation) {
            A[] repeatedAnnotations = mConverter.apply(repeatableAnnotation);
            for (A repeatedAnnotation : repeatedAnnotations) {
                allAnnotations.add(repeatedAnnotation);
            }
        }

        Set<A> getAnnotations(Description description) {
            Set<A> allAnnotations = new HashSet<>();

            // Gets the annotations from the method first
            Collection<Annotation> annotations = description.getAnnotations();
            if (annotations != null) {
                for (Annotation annotation : annotations) {
                    if (mAnnotationType.isInstance(annotation)) {
                        allAnnotations.add(mAnnotationType.cast(annotation));
                    }
                    if (mRepeatableType.isInstance(annotation)) {
                        add(allAnnotations, mRepeatableType.cast(annotation));
                    }
                }
            }

            // Then superclasses
            Class<?> clazz = description.getTestClass();
            do {
                A[] repeatedAnnotations = clazz.getAnnotationsByType(mAnnotationType);
                if (repeatedAnnotations != null) {
                    for (A repeatedAnnotation : repeatedAnnotations) {
                        allAnnotations.add(repeatedAnnotation);
                    }
                }
                R[] repeatableAnnotations = clazz.getAnnotationsByType(mRepeatableType);
                if (repeatableAnnotations != null) {
                    for (R repeatableAnnotation : repeatableAnnotations) {
                        add(allAnnotations, mRepeatableType.cast(repeatableAnnotation));
                    }
                }
                clazz = clazz.getSuperclass();
            } while (clazz != null);

            return allAnnotations;
        }
    }
}
