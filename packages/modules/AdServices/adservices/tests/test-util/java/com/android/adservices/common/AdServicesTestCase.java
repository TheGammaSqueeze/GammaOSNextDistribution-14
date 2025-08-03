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
package com.android.adservices.common;

import android.content.Context;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import com.google.common.truth.Expect;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.util.Objects;

// TODO(b/285014040): need to add unit tests for this class itself, as it's now providing logic.

// Superclass for all other "base classes"
abstract class AdServicesTestCase {

    private static final String TAG = AdServicesTestCase.class.getSimpleName();

    // NOTE: properties below are meant to be used to debug / reproduce test failures, they should
    // NOT be used programmatically in the tests themselves.

    /** When set, defines duration (in milliseconds) to sleep after each test. */
    private static final String PROP_DELAY_AFTER_TEST = "debug.adservices.test.postTestDelay";

    /**
     * When set to a (non-zero) number, test will throw a RuntimeException in a background time at
     * the value defined by it, either once (if the number is positive) or repeatedly.
     *
     * <p>For example, if the value is {@code 3}, the 3rd test - and only the 3rd test - will throw
     * the exception after it's done. But if it's {@code -3}, then the exception will be thrown
     * after the 3rd test, 6th test, etc... .
     */
    private static final String PROP_EXCEPTION_THROWN_FREQUENCY =
            "debug.adservices.test.postTestThrownFrequency";

    /** Reference to the context of package being instrumented (target context). */
    protected static final Context sContext = InstrumentationRegistry.getTargetContext();

    /** Package name of the app being instrumented. */
    protected static final String sPackageName = sContext.getPackageName();

    private static int sTestCount;

    private int mTestNumber;

    protected final String mTag = getClass().getSimpleName();

    /** Reference to the context of package being instrumented (target context). */
    protected final Context mContext = sContext;

    /** Package name of the app being instrumented. */
    protected final String mPackageName = sPackageName;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAnyLevel();

    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 2)
    public final ProcessLifeguardRule processLifeguard =
            new ProcessLifeguardRule(ProcessLifeguardRule.Mode.IGNORE);

    @Rule(order = 3)
    public final Expect expect = Expect.create();

    @Before
    public final void setTestNumber() {
        mTestNumber = ++sTestCount;
        Log.d(TAG, "setTestNumber(): " + getTestName() + " is test #" + mTestNumber);
    }

    @After
    public final void postTestOptionalActions() {
        throwExceptionInBgAfterTest();
        sleepAfterTest();
    }

    /** Gets the name of the test being executed. */
    protected final String getTestName() {
        return processLifeguard.getTestName();
    }

    /** Sleeps for the given amount of time. */
    @FormatMethod
    protected final void sleep(
            int timeMs, @FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        String reason = String.format(reasonFmt, reasonArgs);
        Log.i(
                TAG,
                getTestName()
                        + ": napping "
                        + timeMs
                        + "ms on thread "
                        + Thread.currentThread()
                        + ". Reason: "
                        + reason);
        SystemClock.sleep(timeMs);
        Log.i(TAG, "Little Suzie woke up!");
    }

    /**
     * Throws a runtime exception (with the given {@code message}) in the background.
     *
     * <p>By default, it starts a thread (with the given {@code threadName}) that throws right away,
     * but subclasses could override it to change the behavior (for example, to use an existing
     * executor).
     */
    protected void throwExceptionInBg(String threadName, String message) {
        RuntimeException exception = new RuntimeException(message);
        Log.i(TAG, "Starting thread " + threadName + " (which will throw " + exception + ")");
        new Thread(
                        () -> {
                            throw exception;
                        },
                        threadName)
                .start();
    }

    // TODO(b/285014040): refactor to take Object... instead (once it's unit tested)
    /**
     * Helper method that uses {@code expect} to assert the class properly implement {@code
     * equals()} and {@code hashCode()}.
     *
     * @param obj1 object that is equals to {@code obj2}
     * @param obj2 object that is equals to {@code obj1}
     */
    protected final void expectObjectsAreEqual(Object obj1, Object obj2) {
        Objects.requireNonNull(obj1, "1st arg cannot be null");
        Objects.requireNonNull(obj2, "2nd arg cannot be null");

        expect.withMessage("1st obj (%s)", obj1).that(obj1).isEqualTo(obj2);
        expect.withMessage("2nd obj (%s)", obj2).that(obj2).isEqualTo(obj1);
        expect.withMessage("hashCode of %s", obj1).that(obj1.hashCode()).isEqualTo(obj2.hashCode());
    }

    // TODO(b/285014040): refactor to take Object... instead (once it's unit tested)
    /**
     * Helper method that uses {@code expect} to assert the class properly implement {@code
     * equals()} and {@code hashCode()}.
     *
     * @param obj1 object that is not equal to {@code obj2}
     * @param obj2 object that is not equal to {@code obj1}
     */
    protected final void expectObjectsAreNotEqual(Object obj1, @Nullable Object obj2) {
        Objects.requireNonNull(obj1, "1st arg cannot be null");

        expect.withMessage("1st obj (%s)", obj1).that(obj1).isNotEqualTo(obj2);
        expect.withMessage("2nd obj (%s)", obj2).that(obj2).isNotEqualTo(obj1);
        if (obj2 != null) {
            expect.withMessage("hashCode of %s", obj1)
                    .that(obj1.hashCode())
                    .isNotEqualTo(obj2.hashCode());
        }
    }

    private void sleepAfterTest() {
        int napTimeMs = SystemProperties.getInt(PROP_DELAY_AFTER_TEST, 0);
        if (napTimeMs <= 0) {
            return;
        }
        sleep(
                napTimeMs,
                "forcing sleep after test as requested by system property %s",
                PROP_DELAY_AFTER_TEST);
    }

    private void throwExceptionInBgAfterTest() {
        int frequency = SystemProperties.getInt(PROP_EXCEPTION_THROWN_FREQUENCY, 0);
        if (frequency == 0) {
            return;
        }

        boolean throwException =
                (frequency < 0 && (mTestNumber % frequency != 0))
                        || (frequency > 0 && mTestNumber == frequency);

        if (!throwException) {
            Log.i(
                    TAG,
                    "Not throwing exception after test #"
                            + mTestNumber
                            + " (frequency="
                            + frequency
                            + ")");
            return;
        }

        Log.e(
                TAG,
                "Throwing exception after test #" + mTestNumber + " (frequency=" + frequency + ")");

        String threadName = getTestName() + "-postTest-#" + mTestNumber;
        String message =
                getTestName()
                        + " failing @After "
                        + mTestNumber
                        + " invocation(s) of "
                        + "test methods from classes that extend "
                        + AdServicesTestCase.class.getSimpleName()
                        + " (as requested by property "
                        + PROP_EXCEPTION_THROWN_FREQUENCY
                        + ")";
        throwExceptionInBg(threadName, message);
    }
}
