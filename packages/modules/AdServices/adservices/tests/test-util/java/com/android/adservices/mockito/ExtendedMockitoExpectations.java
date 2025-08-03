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

import static com.android.adservices.mockito.MockitoExpectations.getSpiedAdServicesJobServiceLogger;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

import android.content.Context;
import android.os.SystemProperties;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.adservices.common.SyncCallback;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.truth.Expect;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.mockito.verification.VerificationMode;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Provides Mockito expectation for common calls.
 *
 * <p><b>NOTE: </b> most expectations require {@code spyStatic()} or {@code mockStatic()} in the
 * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession session} ahead of time - this
 * helper doesn't check that such calls were made, it's up to the caller to do so.
 */
public final class ExtendedMockitoExpectations {

    private static final String TAG = ExtendedMockitoExpectations.class.getSimpleName();

    // TODO(b/314969513): remove once there is no more usage
    /**
     * Mocks a call to {@link SdkLevel#isAtLeastS()}, returning {@code isIt}.
     *
     * @deprecated - use {@link AdServicesExtendedMockitoRule#mockIsAtLeastS(boolean)} instead
     */
    @Deprecated
    public static void mockIsAtLeastS(boolean isIt) {
        Log.v(TAG, "mockIsAtLeastS(" + isIt + ")");
        doReturn(isIt).when(SdkLevel::isAtLeastS);
    }

    // TODO(b/314969513): remove once there is no more usage
    /**
     * Mocks a call to {@link SdkLevel#isAtLeastT()}, returning {@code isIt}.
     *
     * @deprecated - use {@link AdServicesExtendedMockitoRule#mockIsAtLeastT(boolean)} instead
     */
    @Deprecated
    public static void mockIsAtLeastT(boolean isIt) {
        Log.v(TAG, "mockIsAtLeastT(" + isIt + ")");
        doReturn(isIt).when(SdkLevel::isAtLeastT);
    }

    /** Mocks a call to {@link SdkLevel#isAtLeastU()}, returning {@code isIt}. */
    public static void mockIsAtLeastU(boolean isIt) {
        Log.v(TAG, "mockIsAtLeastU(" + isIt + ")");
        doReturn(isIt).when(SdkLevel::isAtLeastU);
    }

    /**
     * Mocks a call to {@link ErrorLogUtil#e()}, does nothing.
     *
     * <p>Mocks behavior for both variants of the method.
     */
    public static void doNothingOnErrorLogUtilError() {
        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
    }

    /**
     * Mocks a call to {@link ErrorLogUtil#e(Throwable, int, int)} and returns a callback object
     * that blocks until that call is made.
     *
     * <p>Useful in cases where {@link ErrorLogUtil} is expected to be called asynchronously.
     */
    public static ErrorLogUtilCallback mockErrorLogUtilWithThrowable() {
        ErrorLogUtilCallback callback = new ErrorLogUtilCallback();
        doAnswer(
                        inv -> {
                            Log.d(TAG, "mockErrorLogUtilError(): inv= " + inv);
                            callback.injectResult(
                                    new ErrorLogUtilInvocation(
                                            inv.getArgument(0),
                                            inv.getArgument(1),
                                            inv.getArgument(2)));
                            return null;
                        })
                .when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        return callback;
    }

    /**
     * Mocks a call to {@link ErrorLogUtil#e(int, int)} and returns a callback object that blocks
     * until that call is made.
     *
     * <p>Useful in cases where {@link ErrorLogUtil} is expected to be called asynchronously.
     */
    public static ErrorLogUtilCallback mockErrorLogUtilWithoutThrowable() {
        ErrorLogUtilCallback callback = new ErrorLogUtilCallback();
        doAnswer(
                        inv -> {
                            Log.d(TAG, "mockErrorLogUtilError(): inv= " + inv);
                            callback.injectResult(
                                    new ErrorLogUtilInvocation(
                                            /* throwable= */ null,
                                            inv.getArgument(0),
                                            inv.getArgument(1)));
                            return null;
                        })
                .when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        return callback;
    }

    // TODO(b/314969513): remove once there is no more usage
    /**
     * Mocks a call to {@link FlagsFactory#getFlags()}, returning {@link
     * FlagsFactory#getFlagsForTest()}
     *
     * @deprecated - use {@link AdServicesExtendedMockitoRule#mockGetFlagsForTesting(Flags)} instead
     */
    public static void mockGetFlagsForTest() {
        mockGetFlags(FlagsFactory.getFlagsForTest());
    }

    // TODO(b/314969513): remove once there is no more usage
    /**
     * Mocks a call of {@link FlagsFactory#getFlags()} to return the passed-in mocking {@link Flags}
     * object.
     *
     * @deprecated - use {@link AdServicesExtendedMockitoRule#mockGetFlags(Flags)} instead
     */
    @Deprecated
    public static void mockGetFlags(Flags mockedFlags) {
        doReturn(mockedFlags).when(FlagsFactory::getFlags);
    }

    /**
     * Mocks a call to a method that dumps something into a {@link PrintWriter}.
     *
     * @param invocation invocation that will call dump passing a {@link PrintWriter}. Typically a
     *     static method, using {@code any()} to represent the {@link PrintWriter} reference.
     * @param pwArgIndex index of the {@link PrintWriter}
     * @param dump value to be {@code println}'ed into the {@link PrintWriter}.
     */
    public static void mockDump(Runnable invocation, int pwArgIndex, String dump) {
        doAnswer(
                        inv -> {
                            PrintWriter pw = (PrintWriter) inv.getArgument(1);
                            pw.println(dump);
                            return null;
                        })
                .when(() -> invocation.run());
    }

    /** Mocks {@link AdServicesJobServiceLogger} to not actually log the stats to server. */
    public static AdServicesJobServiceLogger mockAdServicesJobServiceLogger(
            Context context, Flags flags) {
        AdServicesJobServiceLogger logger = getSpiedAdServicesJobServiceLogger(context, flags);

        mockGetAdServicesJobServiceLogger(logger);
        doNothing().when(logger).recordOnStartJob(anyInt());
        doNothing().when(logger).recordOnStopJob(any(), anyInt(), anyBoolean());
        doNothing().when(logger).recordJobSkipped(anyInt(), anyInt());
        doNothing().when(logger).recordJobFinished(anyInt(), anyBoolean(), anyBoolean());

        return logger;
    }

    /** Mocks {@link AdServicesJobServiceLogger#getInstance(Context)} to return a mocked logger. */
    public static void mockGetAdServicesJobServiceLogger(AdServicesJobServiceLogger logger) {
        doReturn(logger).when(() -> AdServicesJobServiceLogger.getInstance(any(Context.class)));
    }

    /** Mocks a call to {@link SystemProperties#getLong(String, long)}, returning {@code value}. */
    public static void mockGetSystemProperty(String key, long value) {
        logV("mockGetSystemProperty(key=%s, value=%s)", key, value);
        doReturn(value).when(() -> SystemProperties.getLong(eq(key), anyLong()));
    }

    /**
     * Mocks a call to {@link SystemProperties#getBoolean(String, boolean)}, returning {@code
     * value}.
     */
    public static void mockGetSystemProperty(String key, boolean value) {
        logV("mockGetSystemProperty(key=%s, value=%s)", key, value);
        doReturn(value).when(() -> SystemProperties.getBoolean(eq(key), anyBoolean()));
    }

    /**
     * Mocks a call to {@code DeviceConfig.getBoolean()} using the AdServices namespace and
     * returning {@code value}.
     */
    public static void mockGetAdServicesFlag(String name, boolean value) {
        logV("mockGetAdServicesFlag(name=%s, value=%s)", name, value);
        doReturn(value)
                .when(
                        () ->
                                DeviceConfig.getBoolean(
                                        eq(DeviceConfig.NAMESPACE_ADSERVICES),
                                        eq(name),
                                        /* defaultValue= */ anyBoolean()));
    }

    /**
     * Mocks a call to {@code DeviceConfig.getString()} using the AdServices namespace and returning
     * {@code value}.
     */
    public static void mockGetAdServicesFlag(String name, String value) {
        logV("mockGetAdServicesFlag(name=%s, value=%s)", name, value);
        doReturn(value)
                .when(
                        () ->
                                DeviceConfig.getString(
                                        eq(DeviceConfig.NAMESPACE_ADSERVICES),
                                        eq(name),
                                        /* defaultValue= */ any()));
    }

    /**
     * Mocks a call to {@code DeviceConfig.getInt()} using the AdServices namespace and returning
     * {@code value}.
     */
    public static void mockGetAdServicesFlag(String name, int value) {
        logV("mockGetAdServicesFlag(name=%s, value=%s)", name, value);
        doReturn(value)
                .when(
                        () ->
                                DeviceConfig.getInt(
                                        eq(DeviceConfig.NAMESPACE_ADSERVICES),
                                        eq(name),
                                        /* defaultValue= */ anyInt()));
    }

    /**
     * Mocks a call to {@code DeviceConfig.getLong()} using the AdServices namespace and returning
     * {@code value}.
     */
    public static void mockGetAdServicesFlag(String name, long value) {
        logV("mockGetAdServicesFlag(name=%s, value=%s)", name, value);
        doReturn(value)
                .when(
                        () ->
                                DeviceConfig.getLong(
                                        eq(DeviceConfig.NAMESPACE_ADSERVICES),
                                        eq(name),
                                        /* defaultValue= */ anyLong()));
    }

    /**
     * Mocks a call to {@code DeviceConfig.getFloat()} using the AdServices namespace and returning
     * {@code value}.
     */
    public static void mockGetAdServicesFlag(String name, float value) {
        logV("mockGetAdServicesFlag(name=%s, value=%s)", name, value);
        doReturn(value)
                .when(
                        () ->
                                DeviceConfig.getFloat(
                                        eq(DeviceConfig.NAMESPACE_ADSERVICES),
                                        eq(name),
                                        /* defaultValue= */ anyFloat()));
    }

    /**
     * Verifies no call to {@link SystemProperties#getLong(String, boolean)} with the given {@code
     * key} was made.
     */
    public static void verifyGetBooleanSystemPropertyNotCalled(String key) {
        logV("verifyGetBooleanSystemPropertyNotCalled(key=%s)", key);
        verify(() -> SystemProperties.getBoolean(eq(key), anyBoolean()), never());
    }

    /**
     * Verifies no call to {@link DeviceConfig#getBoolean(String, String, boolean)} with the given
     * {@code namespace} and {@code name} was made.
     */
    public static void verifyGetBooleanDeviceConfigFlagNotCalled(String namespace, String name) {
        logV("verifyGetBooleanDeviceConfigFlagNotCalled(namespace=%s, name=%s)", namespace, name);
        verify(() -> DeviceConfig.getBoolean(eq(namespace), eq(name), anyBoolean()), never());
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values.
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithThrowable()} before the test calls {@link ErrorLogUtil#e(Throwable, int,
     * int)}.
     */
    public static void verifyErrorLogUtilErrorWithAnyException(int errorCode, int ppapiName) {
        verifyErrorLogUtilErrorWithAnyException(errorCode, ppapiName, times(1));
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values, using Mockito's {@link
     * VerificationMode} to set the number of times (like {@code times(2)} or {@code never}).
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithThrowable()} before the test calls {@link ErrorLogUtil#e(Throwable, int,
     * int)}.
     */
    public static void verifyErrorLogUtilErrorWithAnyException(
            int errorCode, int ppapiName, VerificationMode mode) {
        verify(() -> ErrorLogUtil.e(any(), eq(errorCode), eq(ppapiName)), mode);
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values.
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithThrowable()} before the test calls {@link ErrorLogUtil#e(Throwable, int,
     * int)}.
     */
    public static void verifyErrorLogUtilError(Throwable throwable, int errorCode, int ppapiName) {
        verifyErrorLogUtilError(throwable, errorCode, ppapiName, times(1));
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values, using Mockito's {@link
     * VerificationMode} to set the number of times (like {@code times(2)} or {@code never}).
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithThrowable()} before the test calls {@link ErrorLogUtil#e(Throwable, int,
     * int)}.
     */
    public static void verifyErrorLogUtilError(
            Throwable throwable, int errorCode, int ppapiName, VerificationMode mode) {
        verify(() -> ErrorLogUtil.e(throwable, errorCode, ppapiName), mode);
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values.
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithoutThrowable()} before the test calls {@link ErrorLogUtil#e(int, int)}.
     */
    public static void verifyErrorLogUtilError(int errorCode, int ppapiName) {
        verify(() -> ErrorLogUtil.e(errorCode, ppapiName));
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values, using Mockito's {@link
     * VerificationMode} to set the number of times (like {@code times(2)} or {@code never}).
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithoutThrowable()} before the test calls {@link ErrorLogUtil#e(int, int)}.
     */
    public static void verifyErrorLogUtilError(
            int errorCode, int ppapiName, VerificationMode mode) {
        verify(() -> ErrorLogUtil.e(errorCode, ppapiName), mode);
    }

    /**
     * {@link SyncCallback} used in conjunction with {@link #mockErrorLogUtilWithoutThrowable()} /
     * {@link #mockErrorLogUtilWithThrowable()}.
     */
    public static final class ErrorLogUtilCallback
            extends SyncCallback<ErrorLogUtilInvocation, Exception> {

        /**
         * Asserts {@link ErrorLogUtil#e(Throwable, int, int)}) was called with the given values.
         */
        public void assertReceived(Expect expect, Throwable throwable, int errorCode, int ppapiName)
                throws InterruptedException {
            ErrorLogUtilInvocation result = assertResultReceived();
            expect.withMessage("throwable on %s", result)
                    .that(result.throwable)
                    .isSameInstanceAs(throwable);
            expect.withMessage("errorCode on %s", result)
                    .that(result.errorCode)
                    .isSameInstanceAs(errorCode);
            expect.withMessage("ppapiName on %s", result)
                    .that(result.ppapiName)
                    .isSameInstanceAs(ppapiName);
        }

        /** Asserts {@link ErrorLogUtil#e(int, int)}) was called with the given values. */
        public void assertReceived(Expect expect, int errorCode, int ppapiName)
                throws InterruptedException {
            ErrorLogUtilInvocation result = assertResultReceived();
            expect.withMessage("throwable on %s", result).that(result.throwable).isNull();
            expect.withMessage("errorCode on %s", result)
                    .that(result.errorCode)
                    .isSameInstanceAs(errorCode);
            expect.withMessage("ppapiName on %s", result)
                    .that(result.ppapiName)
                    .isSameInstanceAs(ppapiName);
        }
    }

    private static final class ErrorLogUtilInvocation {
        @Nullable public final Throwable throwable;
        public final int errorCode;
        public final int ppapiName;

        ErrorLogUtilInvocation(Throwable throwable, int errorCode, int ppapiName) {
            this.throwable = throwable;
            this.errorCode = errorCode;
            this.ppapiName = ppapiName;
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, ppapiName, throwable);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ErrorLogUtilInvocation other = (ErrorLogUtilInvocation) obj;
            return errorCode == other.errorCode
                    && ppapiName == other.ppapiName
                    && Objects.equals(throwable, other.throwable);
        }

        @Override
        public String toString() {
            return "ErrorLogUtilInvocation [exception="
                    + throwable
                    + ", errorCode="
                    + errorCode
                    + ", ppapiName="
                    + ppapiName
                    + "]";
        }
    }

    @FormatMethod
    private static void logV(@FormatString String fmt, Object... args) {
        Log.v(TAG, String.format(fmt, args));
    }

    private ExtendedMockitoExpectations() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
