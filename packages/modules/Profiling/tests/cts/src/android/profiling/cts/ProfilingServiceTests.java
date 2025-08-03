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

package android.profiling.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.reset;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.os.IProfilingResultCallback;
import android.os.ParcelFileDescriptor;
import android.os.Binder;
import android.content.pm.PackageManager;
import android.app.UiAutomation;
import android.os.profiling.RateLimiter;
import android.os.profiling.TracingSession;
import android.os.ProfilingRequest;
import android.os.ProfilingResult;
import android.os.profiling.ProfilingService;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import java.lang.Process;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests in this class are for testing the ProfilingService directly without the need to get a
 * reference to the service via the call to getSystemService().
 */

@RunWith(AndroidJUnit4.class)
public final class ProfilingServiceTests {

    private static final String APP_FILE_PATH = "/data/user/0/com.profiling.test/files";
    private static final String APP_PACKAGE_NAME = "com.profiling.test";
    private static final String REQUEST_TAG = "some unique string";

    // Key most and least significant bits are used to generate a unique key specific to each
    // request. Key is used to pair request back to caller and callbacks so test to keep consistent.
    private static final long KEY_MOST_SIG_BITS = 456l;
    private static final long KEY_LEAST_SIG_BITS = 123l;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private PackageManager mPackageManager;
    @Mock private Process mActiveTrace;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private ProfilingService mProfilingService;
    private RateLimiter mRateLimiter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        mProfilingService = spy(new ProfilingService(mContext));
        mRateLimiter = spy(new RateLimiter(mContext));
        doReturn(mPackageManager).when(mContext).getPackageManager();
        mProfilingService.mRateLimiter = mRateLimiter;
        doReturn(APP_PACKAGE_NAME).when(mPackageManager).getNameForUid(anyInt());
    }

    /** Test that registering binder callbacks works as expected. */
    @Test
    public void testRegisterResultCallback() {
        ProfilingResultCallback callback = new ProfilingResultCallback();

        // Register callback.
        mProfilingService.registerResultsCallback(callback);

        // Confirm callback is registered.
        assertEquals(callback, mProfilingService.mResultCallbacks.get(Binder.getCallingUid()));
    }

    /**
     * Test that requesting profiling while another profiling is in progress fails with correct
     * error codes.
     */
    @Test
    public void testRequestProfiling_ProfilingRunning_Fails() {
        // Mock traces running check to simulate collection running.
        doReturn(true).when(mProfilingService).areAnyTracesRunning();

        // Register callback.
        ProfilingResultCallback callback = new ProfilingResultCallback();
        mProfilingService.registerResultsCallback(callback);

        // Kick off request.
        mProfilingService.requestProfiling(ProfilingTestUtils.getJavaHeapDumpProfilingRequest(),
                APP_FILE_PATH, REQUEST_TAG, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS);

        // Confirm result matches failure expectation.
        confirmResultCallback(callback, null, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS,
                ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS, REQUEST_TAG, false);
    }

    /**
     * Test that requesting profiling with an invalid request byte array fails with correct error
     * codes.
     */
    @Test
    public void testRequestProfiling_InvalidRequest_Fails() {
        // Bypass traces running check, we're not testing that here.
        doReturn(false).when(mProfilingService).areAnyTracesRunning();

        // Register callback.
        ProfilingResultCallback callback = new ProfilingResultCallback();
        mProfilingService.registerResultsCallback(callback);

        // Kick off request.
        mProfilingService.requestProfiling(new byte[4], APP_FILE_PATH, REQUEST_TAG,
                KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS);

        // Confirm result matches failure expectation.
        confirmResultCallback(callback, null, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS,
                ProfilingResult.ERROR_FAILED_INVALID_REQUEST, REQUEST_TAG, true);
    }

    /** Test that requesting where we cannot access the package name fails. */
    @Test
    public void testRequestProfiling_PackageNameNotFound_Fails() {
        // Mock getNameForUid to simulate failure case.
        doReturn(null).when(mPackageManager).getNameForUid(anyInt());

        // Register callback.
        ProfilingResultCallback callback = new ProfilingResultCallback();
        mProfilingService.registerResultsCallback(callback);

        // Kick off request.
        mProfilingService.requestProfiling(ProfilingTestUtils.getJavaHeapDumpProfilingRequest(),
                APP_FILE_PATH, REQUEST_TAG, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS);

        // Confirm result matches failure expectation.
        confirmResultCallback(callback, null, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS,
                ProfilingResult.ERROR_UNKNOWN, REQUEST_TAG, true);
    }

    /** Test that failing rate limiting blocks trace from running. */
    @Test
    public void testRequestProfiling_RateLimitBlocked_Fails() {
        // Bypass traces running check, we're not testing that here.
        doReturn(false).when(mProfilingService).areAnyTracesRunning();

        // Mock rate limiter result to simulate failure case.
        doReturn(RateLimiter.RATE_LIMIT_RESULT_BLOCKED_PROCESS).when(mRateLimiter)
              .isProfilingRequestAllowed(anyInt(), anyObject());

        // Register callback.
        ProfilingResultCallback callback = new ProfilingResultCallback();
        mProfilingService.registerResultsCallback(callback);

        // Kick off request.
        mProfilingService.requestProfiling(ProfilingTestUtils.getJavaHeapDumpProfilingRequest(),
                APP_FILE_PATH, REQUEST_TAG, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS);

        // Confirm result matches failure expectation.
        confirmResultCallback(callback, null, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS,
                ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS, REQUEST_TAG, false);
    }

    /**
     * Test profiling request with no issues makes it to perfetto kick off and fails because we're
     * using the wrong context in these tests.
     */
    @Test
    public void testRequestProfiling_Allowed_PerfettoPermissions_Fails() {
        // Bypass traces running check, we're not testing that here.
        doReturn(false).when(mProfilingService).areAnyTracesRunning();

        // Register callback.
        ProfilingResultCallback callback = new ProfilingResultCallback();
        mProfilingService.registerResultsCallback(callback);

        // Kick off request.
        mProfilingService.requestProfiling(ProfilingTestUtils.getJavaHeapDumpProfilingRequest(),
                APP_FILE_PATH, REQUEST_TAG, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS);

        // Perfetto cannot be run from this context, ensure it was attempted and failed permissions.
        confirmResultCallback(callback, null, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS,
                ProfilingResult.ERROR_UNKNOWN, REQUEST_TAG, true);
        assertEquals("Perfetto error", callback.mError);
    }

    /** Test that checking if any traces are running works when trace is running. */
    @Test
    public void testAreAnyTracesRunning_True() {
        // Ensure no active tracing sessions tracked.
        mProfilingService.mTracingSessions.clear();
        assertFalse(mProfilingService.areAnyTracesRunning());

        // Create a tracing session.
        TracingSession tracingSession = new TracingSession(null, APP_FILE_PATH, 123,
                APP_PACKAGE_NAME, REQUEST_TAG, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS);

        // Mock tracing session to be running.
        doReturn(true).when(mActiveTrace).isAlive();

        // Add trace to session and session to ProfilingService tracked sessions.
        tracingSession.setActiveTrace(mActiveTrace);
        mProfilingService.mTracingSessions.put(
                (new UUID(KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS)).toString(), tracingSession);

        // Confirm check returns that a trace is running.
        assertTrue(mProfilingService.areAnyTracesRunning());
    }

    /** Test that checking if any traces are running works when trace is not running. */
    @Test
    public void testAreAnyTracesRunning_False() {
        mProfilingService.mTracingSessions.clear();
        assertFalse(mProfilingService.areAnyTracesRunning());

        TracingSession tracingSession = new TracingSession(null, APP_FILE_PATH, 123,
                APP_PACKAGE_NAME, REQUEST_TAG, KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS);
        mProfilingService.mTracingSessions.put(
                (new UUID(KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS)).toString(), tracingSession);
        assertFalse(mProfilingService.areAnyTracesRunning());
    }

    /** Test that request cancel trace does nothing if no trace is running. */
    @Test
    public void testRequestCancel_NotRunning() {
        // Ensure no active tracing sessions tracked.
        mProfilingService.mTracingSessions.clear();
        assertFalse(mProfilingService.areAnyTracesRunning());

        // Register callback.
        ProfilingResultCallback callback = new ProfilingResultCallback();
        mProfilingService.registerResultsCallback(callback);

        // Request cancellation.
        mProfilingService.requestCancel(KEY_MOST_SIG_BITS, KEY_LEAST_SIG_BITS);

        // Confirm callback was not triggerd with a result because there was no trace to stop.
        assertFalse(callback.mResultSent);
    }

    /** Confirm that all fields returned by callback match expectation. */
    private void confirmResultCallback(ProfilingResultCallback callback, String resultFile,
            long keyMostSigBits, long keyLeastSigBits, int status, String tag,
            boolean errorExpected) {
        assertEquals(resultFile, callback.mResultFile);
        assertEquals(keyMostSigBits, callback.mKeyMostSigBits);
        assertEquals(keyLeastSigBits, callback.mKeyLeastSigBits);
        assertEquals(status, callback.mStatus);
        assertEquals(tag, callback.mTag);
        if (errorExpected) {
            assertNotNull(callback.mError);
        } else {
            assertNull(callback.mError);
        }
    }

    public static class ProfilingResultCallback extends IProfilingResultCallback.Stub {
        boolean mResultSent = false;
        boolean mFileRequested = false;
        public String mResultFile;
        public long mKeyMostSigBits;
        public long mKeyLeastSigBits;
        public int mStatus;
        public String mTag;
        public String mError;
        @Override
        public void sendResult(String resultFile, long keyMostSigBits,
                long keyLeastSigBits, int status, String tag, String error) {
            mResultSent = true;
            mResultFile = resultFile;
            mKeyMostSigBits = keyMostSigBits;
            mKeyLeastSigBits = keyLeastSigBits;
            mStatus = status;
            mTag = tag;
            mError = error;
        }
        @Override
        public ParcelFileDescriptor generateFile(String filePathAbsolute, String fileName) {
            mFileRequested = true;
            return null;
        }
    }
}


