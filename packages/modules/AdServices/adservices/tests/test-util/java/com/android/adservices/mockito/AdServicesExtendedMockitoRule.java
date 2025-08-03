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

package com.android.adservices.mockito;

import static com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule.shouldClearInlineMocksAfterTest;
import static com.android.adservices.shared.testing.common.TestHelper.getAnnotation;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import android.app.ActivityManager;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.adservices.common.LogEntry.Level;
import com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule.ClearInlineMocksMode;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.testing.common.TestHelper;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.AbstractExtendedMockitoRule;
import com.android.modules.utils.testing.StaticMockFixture;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

// NOTE: javadoc below copied mostly as-is from ExtendedMockitoRule
/**
 * Rule to make it easier to use Extended Mockito:
 *
 * <ul>
 *   <li>Automatically creates and finishes the mock session.
 *   <li>Provides multiple ways to set which classes must be statically mocked or spied
 *   <li>Automatically starts mocking (so tests don't need a mockito runner or rule)
 *   <li>Automatically clears the inlined mocks at the end (to avoid OOM)
 *   <li>Allows other customization like strictness
 * </ul>
 *
 * <p>Typical usage:
 *
 * <pre class="prettyprint">
 * &#064;Rule
 * public final AdServicesExtendedMockitoRule extendedMockito =
 *   new AdServicesExtendedMockitoRule.Builder(this)
 *     .spyStatic(SomeClassWithStaticMethodsToBeMocked)
 *     .build();
 * </pre>
 */
public class AdServicesExtendedMockitoRule
        extends AbstractExtendedMockitoRule<
                AdServicesExtendedMockitoRule, AdServicesExtendedMockitoRule.Builder> {

    private static final String TAG = AdServicesExtendedMockitoRule.class.getSimpleName();

    private final Set<Class<?>> mSpiedOrMockedStaticClasses = new HashSet<>();

    @Nullable private String mTestName;

    public AdServicesExtendedMockitoRule(Builder builder) {
        super(builder);
    }

    @SafeVarargs
    public AdServicesExtendedMockitoRule(Supplier<? extends StaticMockFixture>... suppliers) {
        super(new Builder().addStaticMockFixtures(suppliers));
    }

    // TODO(b/312802824): add unit tests (for rule itself)
    /**
     * Gets the name of the test being executed.
     *
     * @throws IllegalStateException if not running a test.
     */
    public final String getTestName() {
        if (mTestName == null) {
            throw new IllegalStateException("not running a test");
        }
        return mTestName;
    }

    /**
     * Mocks a call of {@link FlagsFactory#getFlags()} to return the passed-in mocking {@link Flags}
     * object.
     *
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link FlagsFactory}.
     */
    public final void mockGetFlags(Flags mockedFlags) {
        logV("mockGetFlags(%s)", mockedFlags);
        assertSpiedOrMocked(FlagsFactory.class);
        doReturn(mockedFlags).when(FlagsFactory::getFlags);
    }

    /**
     * Mocks a call to {@link FlagsFactory#getFlags()}, returning {@link
     * FlagsFactory#getFlagsForTest()}
     *
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link FlagsFactory}.
     */
    public final void mockGetFlagsForTesting(Flags mockedFlags) {
        mockGetFlags(FlagsFactory.getFlagsForTest());
    }

    /**
     * Mocks a call to {@link Binder#getCallingUidOrThrow()}, returning {@code uid}.
     *
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link Binder}.
     */
    public final void mockGetCallingUidOrThrow(int uid) {
        logV("mockGetCallingUidOrThrow(%d)", uid);
        mockBinderGetCallingUidOrThrow(uid);
    }

    /**
     * Same as {@link #mockGetCallingUidOrThrow(int)}, but using the {@code uid} of the calling
     * process.
     *
     * <p>Typically used when code under test calls {@link Binder#getCallingUidOrThrow()} and the
     * test doesn't care about the result, but it needs to be mocked otherwise the real call would
     * fail (as the test is not running inside a binder transaction).
     */
    public final void mockGetCallingUidOrThrow() {
        int uid = Process.myUid();
        logV("mockGetCallingUidOrThrow(Process.myUid=%d)", uid);
        mockBinderGetCallingUidOrThrow(uid);
    }

    /** Mocks a call to {@link SdkLevel#isAtLeastR()}, returning {@code isIt}. */
    public final void mockIsAtLeastR(boolean isIt) {
        logV("mockIsAtLeastR(%b)", isIt);
        assertSpiedOrMocked(SdkLevel.class);
        doReturn(isIt).when(SdkLevel::isAtLeastR);
    }

    /** Mocks a call to {@link SdkLevel#isAtLeastS()}, returning {@code isIt}. */
    public final void mockIsAtLeastS(boolean isIt) {
        logV("mockIsAtLeastS(%b)", isIt);
        assertSpiedOrMocked(SdkLevel.class);
        doReturn(isIt).when(SdkLevel::isAtLeastS);
    }

    /** Mocks a call to {@link SdkLevel#isAtLeastT()}, returning {@code isIt}. */
    public final void mockIsAtLeastT(boolean isIt) {
        logV("mockIsAtLeastT(%b)", isIt);
        assertSpiedOrMocked(SdkLevel.class);
        doReturn(isIt).when(SdkLevel::isAtLeastT);
    }

    /**
     * Mocks a call to {@link ActivityManager#getCurrentUser()}, returning {@code user}.
     *
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link ActivityManager}.
     */
    public final void mockGetCurrentUser(int user) {
        logV("mockGetCurrentUser(user=%d)", user);
        assertSpiedOrMocked(ActivityManager.class);
        doReturn(user).when(ActivityManager::getCurrentUser);
    }

    /**
     * Statically spy on {@code Log.v} for that {@code tag}.
     *
     * @return object that can be used to assert the {@code Log.v} calls.
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link Log}.
     */
    public final LogInterceptor interceptLogV(String tag) {
        logV("interceptLogV(%s)", tag);
        assertSpiedOrMocked(Log.class);

        return LogInterceptor.forTagAndLevels(tag, Level.VERBOSE);
    }

    /**
     * Statically spy on {@code Log.e} for that {@code tag}.
     *
     * @return object that can be used to assert the {@code Log.e} calls.
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link Log}.
     */
    public final LogInterceptor interceptLogE(String tag) {
        logV("interceptLogE(%s)", tag);
        assertSpiedOrMocked(Log.class);

        return LogInterceptor.forTagAndLevels(tag, Level.ERROR);
    }

    // NOTE: current tests are only intercepting v and e, but we could add more methods on demand
    // (even one that takes Level...levels)

    // mock only, don't log
    private void mockBinderGetCallingUidOrThrow(int uid) {
        assertSpiedOrMocked(Binder.class);
        doReturn(uid).when(Binder::getCallingUidOrThrow);
    }

    // Overridden to get test name
    @Override
    public final Statement apply(Statement base, Description description) {
        Statement realStatement = super.apply(base, description);
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                mTestName = TestHelper.getTestName(description);
                try {
                    realStatement.evaluate();
                } finally {
                    mTestName = null;
                }
            }
        };
    }

    @Override
    protected final Set<Class<?>> getSpiedStaticClasses(Description description) {
        Set<Class<?>> spiedStaticClasses = super.getSpiedStaticClasses(description);
        mSpiedOrMockedStaticClasses.addAll(spiedStaticClasses);
        return spiedStaticClasses;
    }

    @Override
    protected final Set<Class<?>> getMockedStaticClasses(Description description) {
        Set<Class<?>> mockedStaticClasses = super.getMockedStaticClasses(description);
        mSpiedOrMockedStaticClasses.addAll(mockedStaticClasses);
        return mockedStaticClasses;
    }

    @Override
    protected final boolean getClearInlineMethodsAtTheEnd(Description description) {
        ClearInlineMocksMode annotation = getAnnotation(description, ClearInlineMocksMode.class);
        if (annotation != null) {
            boolean shouldClear = shouldClearInlineMocksAfterTest(description, annotation.value());
            Log.d(
                    TAG,
                    "getClearInlineMethodsAtTheEnd(): returning value based on annotation ("
                            + shouldClear
                            + ") for "
                            + TestHelper.getTestName(description));
            return shouldClear;
        }
        return super.getClearInlineMethodsAtTheEnd(description);
    }

    // TODO(b/312802824): add unit tests (for rule itself)
    private void assertSpiedOrMocked(Class<?> clazz) {
        if (!mSpiedOrMockedStaticClasses.contains(clazz)) {
            throw new IllegalStateException(
                    "Test doesn't static spy or mock "
                            + clazz
                            + ", only: "
                            + mSpiedOrMockedStaticClasses);
        }
    }

    @FormatMethod
    private void logV(@FormatString String fmt, Object... args) {
        Log.v(TAG, "on " + getTestName() + ": " + String.format(fmt, args));
    }

    public static final class Builder
            extends AbstractBuilder<AdServicesExtendedMockitoRule, Builder> {

        public Builder() {
            super();
        }

        public Builder(Object testClassInstance) {
            super(testClassInstance);
        }

        @Override
        public AdServicesExtendedMockitoRule build() {
            return new AdServicesExtendedMockitoRule(this);
        }
    }
}
