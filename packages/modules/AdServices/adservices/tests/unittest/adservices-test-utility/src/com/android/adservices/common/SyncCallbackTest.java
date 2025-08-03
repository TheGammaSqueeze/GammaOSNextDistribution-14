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

package com.android.adservices.common;

import static com.android.adservices.common.ConcurrencyHelper.runAsync;
import static com.android.adservices.common.ConcurrencyHelper.runOnMainThread;
import static com.android.adservices.common.SyncCallback.MSG_WRONG_ERROR_RECEIVED;

import static org.junit.Assert.assertThrows;

import android.util.Log;

import com.android.adservices.common.LogEntry.Level;
import com.android.adservices.mockito.LogInterceptor;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

import java.util.NoSuchElementException;

public final class SyncCallbackTest extends AdServicesExtendedMockitoTestCase {

    private static final int TIMEOUT_MS = 200;

    private static final String RESULT = "There is a person in a smiling bag.";
    private static final String ANOTHER_RESULT = "The owls are not what they seem.";
    private static final Exception ERROR = new Exception("Without chemicals, they point.");
    private static final Exception ANOTHER_ERROR = new Exception("D'OH!");

    @Test
    public void testDefaultConstructor() {
        SyncCallback<String, Exception> callback = new SyncCallback<>();

        expect.withMessage("getMaxTimeoutMs()").that(callback.getMaxTimeoutMs()).isGreaterThan(0);
        expect.withMessage("isFailIfCalledOnMainThread()")
                .that(callback.isFailIfCalledOnMainThread())
                .isTrue();
    }

    @Test
    public void testGetId() {
        SyncCallback<String, Exception> callback1 = new SyncCallback<>();
        String id1 = callback1.getId();
        expect.withMessage("id of 1st callback (%s)", callback1).that(id1).isNotNull();
        expect.withMessage("id of 1st callback (%s)", callback1).that(id1).isNotEmpty();

        SyncCallback<String, Exception> callback2 = new SyncCallback<>();
        String id2 = callback2.getId();
        expect.withMessage("id of 2nd callback (%s)", callback2).that(id2).isNotNull();
        expect.withMessage("id of 2nd callback (%s)", callback2).that(id2).isNotEmpty();

        expect.withMessage("id of 2nd callback (%s)", callback2).that(id2).isNotEqualTo(id1);
    }

    @Test
    public void testGetMaxTimeoutMs() {
        SyncCallback<String, Exception> callback = new SyncCallback<>(42);

        expect.withMessage("getMaxTimeoutMs()").that(callback.getMaxTimeoutMs()).isEqualTo(42);
    }

    @Test
    public void testInjectResult() throws Exception {
        SyncCallback<String, Exception> callback = new SyncCallback<>(TIMEOUT_MS * 3);

        runAsync(TIMEOUT_MS, () -> callback.injectResult(RESULT));

        assertResultReceived(callback, RESULT);
    }

    @Test
    public void testInjectResult_null() throws Exception {
        SyncCallback<String, Exception> callback = new SyncCallback<>(TIMEOUT_MS * 3);

        runAsync(TIMEOUT_MS, () -> callback.injectResult(null));

        assertResultReceived(callback, null);
    }

    @Test
    public void testInjectResult_calledTwice() {
        SyncCallback<String, Exception> callback = new SyncCallback<>(TIMEOUT_MS * 3);

        callback.injectResult(RESULT);
        callback.injectResult(ANOTHER_RESULT);
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> callback.assertReceived());

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains(
                        "injectResult("
                                + ANOTHER_RESULT
                                + ") called after injectResult("
                                + RESULT
                                + ")");
    }

    @Test
    public void testInjectResult_afterinjectError() {
        SyncCallback<String, Exception> callback = new SyncCallback<>();
        callback.injectError(ERROR);
        callback.injectResult(RESULT);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> callback.assertReceived());

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("injectResult(" + RESULT + ") called after injectError(" + ERROR + ")");
    }

    @Test
    public void testInjectResult_calledOnMainThread_fails() throws Exception {
        SyncCallback<String, Exception> callback =
                new SyncCallback<>(TIMEOUT_MS, /* failIfCalledOnMainThread= */ true);

        runOnMainThread(() -> callback.injectResult(RESULT));
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> callback.assertReceived());

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("injectResult(" + RESULT + ") called on main thread");
    }

    @Test
    public void testInjectResult_calledOnMainThread_pass() throws Exception {
        SyncCallback<String, Exception> callback =
                new SyncCallback<>(TIMEOUT_MS, /* failIfCalledOnMainThread= */ false);

        runOnMainThread(() -> callback.injectResult(RESULT));

        assertResultReceived(callback, RESULT);
    }

    @Test
    public void testAssertErrorReceived_nullArg() {
        SyncCallback<String, Exception> callback = new SyncCallback<>();
        callback.injectError(ERROR);

        assertThrows(IllegalArgumentException.class, () -> callback.assertErrorReceived(null));
    }

    @Test
    public void testInjectError() throws Exception {
        SyncCallback<String, Exception> callback = new SyncCallback<>(TIMEOUT_MS * 3);

        runAsync(TIMEOUT_MS, () -> callback.injectError(ERROR));

        assertErrorReceived(callback, ERROR);
    }

    @Test
    public void testInjectError_wrongExceptionClass() throws Exception {
        SyncCallback<String, Exception> callback = new SyncCallback<>(TIMEOUT_MS * 3);

        runAsync(TIMEOUT_MS, () -> callback.injectError(ERROR));
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> callback.assertErrorReceived(NoSuchElementException.class));

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                MSG_WRONG_ERROR_RECEIVED, NoSuchElementException.class, ERROR));
    }

    @Test
    public void testInjectError_calledTwice() {
        SyncCallback<String, Exception> callback = new SyncCallback<>();
        callback.injectError(ERROR);
        callback.injectError(ANOTHER_ERROR);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> callback.assertReceived());

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains(
                        "injectError("
                                + ANOTHER_ERROR
                                + ") called after injectError("
                                + ERROR
                                + ")");
    }

    @Test
    public void testInjectError_afterOnResult() {
        SyncCallback<String, Exception> callback = new SyncCallback<>();
        callback.injectResult(RESULT);
        callback.injectError(ERROR);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> callback.assertReceived());

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("injectError(" + ERROR + ") called after injectResult(" + RESULT + ")");
    }

    @Test
    public void testInjectError_calledOnMainThread_fails() throws Exception {
        SyncCallback<String, Exception> callback =
                new SyncCallback<>(TIMEOUT_MS, /* failIfCalledOnMainThread= */ true);

        runOnMainThread(() -> callback.injectError(ERROR));
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> callback.assertReceived());

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("injectError(" + ERROR + ") called on main thread");
    }

    @Test
    public void testInjectError_calledOnMainThread_pass() throws Exception {
        SyncCallback<String, Exception> callback =
                new SyncCallback<>(TIMEOUT_MS, /* failIfCalledOnMainThread= */ false);

        runOnMainThread(() -> callback.injectError(ERROR));
        Exception error = callback.assertErrorReceived(ERROR.getClass());

        assertErrorReceived(callback, error);
    }

    @Test
    public void testToString_beforeOutcome() {
        SyncCallback<String, Exception> callback =
                new SyncCallback<>(TIMEOUT_MS, /* failIfCalledOnMainThread= */ false);

        String string = callback.toString();

        expect.withMessage("toString()").that(string).startsWith("SyncCallback");
        expect.withMessage("toString()")
                .that(string)
                .containsMatch(".*timeoutMs=" + TIMEOUT_MS + ".*");
        expect.withMessage("toString()")
                .that(string)
                .containsMatch(".*failIfCalledOnMainThread=false.*");
        expect.withMessage("toString()").that(string).containsMatch(".*result=null.*");
        expect.withMessage("toString()").that(string).containsMatch(".*error=null.*");
    }

    @Test
    @SpyStatic(Log.class)
    public void testLogV() {
        SyncCallback<String, Exception> callback = new SyncCallback<>();
        String tag = SyncCallback.TAG;
        LogInterceptor logInterceptor = extendedMockito.interceptLogV(tag);

        callback.logV("Answer=%d", 42);

        expect.withMessage("Log.*() calls to tag %s", tag)
                .that(logInterceptor.getAllEntries(tag))
                .hasSize(1);
        expect.withMessage("Log.v() calls to tag %s", tag)
                .that(logInterceptor.getPlainMessages(tag, Level.VERBOSE))
                .containsExactly("[" + callback.getId() + "] Answer=42");
    }

    @Test
    @SpyStatic(Log.class)
    public void testLogE() {
        SyncCallback<String, Exception> callback = new SyncCallback<>();
        String tag = SyncCallback.TAG;
        LogInterceptor logInterceptor = extendedMockito.interceptLogE(tag);

        callback.logE("Answer=%d", 42);

        expect.withMessage("Log.*() calls to tag %s", tag)
                .that(logInterceptor.getAllEntries(tag))
                .hasSize(1);
        expect.withMessage("Log.e() calls to tag %s", tag)
                .that(logInterceptor.getPlainMessages(tag, Level.ERROR))
                .containsExactly("[" + callback.getId() + "] Answer=42");
    }

    private void assertResultReceived(
            SyncCallback<String, Exception> callback, @Nullable String expectedResult)
            throws InterruptedException {
        String actualResult = callback.assertResultReceived();
        if (expectedResult == null) {
            expect.withMessage("assertResultReceived()").that(actualResult).isNull();
            expect.withMessage("getResultReceived()").that(callback.getResultReceived()).isNull();

        } else {
            expect.withMessage("assertResultReceived()")
                    .that(actualResult)
                    .isSameInstanceAs(expectedResult);
            expect.withMessage("getResultReceived()")
                    .that(callback.getResultReceived())
                    .isSameInstanceAs(expectedResult);
        }
        expect.withMessage("getErrorReceived()").that(callback.getErrorReceived()).isNull();
        String toString = callback.toString();
        expect.withMessage("toString()").that(toString).contains("result=" + expectedResult);
        expect.withMessage("toString()").that(toString).contains("error=null");
    }

    private void assertErrorReceived(
            SyncCallback<String, Exception> callback, Exception expectedError)
            throws InterruptedException {
        Exception actualError = callback.assertErrorReceived();
        expect.withMessage("assertErrorReceived()")
                .that(actualError)
                .isSameInstanceAs(expectedError);
        expect.withMessage("getErrorReceived()")
                .that(callback.getErrorReceived())
                .isSameInstanceAs(expectedError);
        expect.withMessage("getResultReceived()").that(callback.getResultReceived()).isNull();
        String toString = callback.toString();
        expect.withMessage("toString()").that(toString).contains("result=null");
        expect.withMessage("toString()").that(toString).contains("error=" + expectedError);
    }
}
