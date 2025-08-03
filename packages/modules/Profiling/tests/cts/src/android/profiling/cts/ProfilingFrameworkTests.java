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

import android.app.UiAutomation;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.ProfilingManager;
import android.os.ProfilingResult;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.os.profiling.Flags;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testng.TestException;

import java.util.function.Consumer;

/**
 *
 * Tests defined in this class are expected to test the API implementation.  All tests below require
 * the android.os.profiling.telemetry_apis flag to be enabled, otherwise you will receive an
 * assumed failure for any tests has the @RequiresFlagsEnabled annotation.
 *
 */

@RunWith(AndroidJUnit4.class)
public final class ProfilingFrameworkTests {
    private static String TAG = "ProfilingFrameworkTests";
    private static ProfilingManager mProfilingManager = null;
    private static CancellationSignal mCancellationSignal = new CancellationSignal();
    private UiAutomation uiAutomator =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void initTestClass() {
        Context ctx = ApplicationProvider.getApplicationContext();
        mProfilingManager = ctx.getSystemService(ProfilingManager.class);
    }

    // Check and see if we can get a reference to the ProfilingManager service.
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TELEMETRY_APIS)
    public void createServiceTest() {
        assertThat(mProfilingManager).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TELEMETRY_APIS)
    public void createProfilingRequestTest() {
        if(mProfilingManager == null) throw new TestException("mProfilingManager can not be null");

        Consumer<ProfilingResult> invalidRequestResultConsumer = profilingResult -> {
                assertThat(profilingResult.getErrorCode())
                        .isEqualTo(ProfilingResult.ERROR_FAILED_INVALID_REQUEST);
        };

        // This call is passing an invalid profiling request config and should result in a parsing
        // error and a ERROR_FAILED_INVALID_REQUEST as the error delivered to resultConsumer.
        mProfilingManager.requestProfiling(
                new byte[16],
                TAG,
                mCancellationSignal,
                new ProfilingTestUtils.ImmediateExecutor(),
                invalidRequestResultConsumer);

        // Ensure enough time as elapsed to have the error propagated to the resultConsumer.
        try {
            uiAutomator.wait(500L);
        }catch (Exception exc) {
            // Do Nothing
        }
    }

}

