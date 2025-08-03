/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.os.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;

import android.os.Flags;
import android.os.HandlerThread;
import android.os.PerformanceHintManager;
import android.os.PerformanceHintManager.Session;
import android.os.Process;
import android.os.WorkDuration;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import com.google.common.base.Strings;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PerformanceHintManagerTest {
    private static final String TAG = "PerformanceHintManagerTest";

    private final long DEFAULT_TARGET_NS = 16666666L;
    private PerformanceHintManager mPerformanceHintManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    static {
        System.loadLibrary("ctsos_jni");
    }

    @Before
    public void setUp() {
        mPerformanceHintManager =
                InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                        PerformanceHintManager.class);
    }

    private Session createSession() {
        return mPerformanceHintManager.createHintSession(
                new int[]{Process.myPid()}, DEFAULT_TARGET_NS);
    }

    @Test
    public void testCreateHintSession() {
        Session a = createSession();
        Session b = createSession();
        if (a == null) {
            assertNull(b);
        } else if (b == null) {
            assertNull(a);
        } else {
            assertNotEquals(a, b);
        }
    }

    @Test
    public void testNativeCreateHintSession() {
        final String failureMessage = nativeTestCreateHintSession();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testGetPreferredUpdateRateNanos() {
        if (createSession() != null) {
            assertTrue(mPerformanceHintManager.getPreferredUpdateRateNanos() > 0);
        } else {
            assertEquals(-1, mPerformanceHintManager.getPreferredUpdateRateNanos());
        }
    }

    @Test
    public void testNativeGetPreferredUpdateRateNanos() {
        final String failureMessage = nativeTestGetPreferredUpdateRateNanos();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testUpdateTargetWorkDuration() {
        Session s = createSession();
        assumeNotNull(s);
        s.updateTargetWorkDuration(100);
    }

    @Test
    public void testNativeUpdateTargetWorkDuration() {
        final String failureMessage = nativeUpdateTargetWorkDuration();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testUpdateTargetWorkDurationWithNegativeDuration() {
        Session s = createSession();
        assumeNotNull(s);
        assertThrows(IllegalArgumentException.class, () -> {
            s.updateTargetWorkDuration(-1);
        });
    }

    @Test
    public void testNativeUpdateTargetWorkDurationWithNegativeDuration() {
        final String failureMessage = nativeUpdateTargetWorkDurationWithNegativeDuration();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testReportActualWorkDuration() {
        Session s = createSession();
        assumeNotNull(s);
        s.updateTargetWorkDuration(100);
        s.reportActualWorkDuration(1);
        s.reportActualWorkDuration(100);
        s.reportActualWorkDuration(1000);
    }

    @Test
    public void testNativeReportActualWorkDuration() {
        final String failureMessage = nativeReportActualWorkDuration();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testReportActualWorkDurationWithIllegalArgument() {
        Session s = createSession();
        assumeNotNull(s);
        s.updateTargetWorkDuration(100);
        assertThrows(IllegalArgumentException.class, () -> {
            s.reportActualWorkDuration(-1);
        });
    }

    @Test
    public void testNativeReportActualWorkDurationWithIllegalArgument() {
        final String failureMessage = nativeReportActualWorkDurationWithIllegalArgument();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    @ApiTest(apis = {"android.os.PerformanceHintManager.Session#sendHint"})
    public void testSendHint() {
        Session s = createSession();
        assumeNotNull(s);
        s.sendHint(Session.CPU_LOAD_UP);
        s.sendHint(Session.CPU_LOAD_RESET);
        s.sendHint(Session.GPU_LOAD_UP);
        s.sendHint(Session.GPU_LOAD_DOWN);
        s.sendHint(Session.GPU_LOAD_RESET);
    }

    @Test
    @ApiTest(apis = {"android.os.PerformanceHintManager.Session#sendHint"})
    public void testSendHintWithNegativeHint() {
        Session s = createSession();
        assumeNotNull(s);
        assertThrows(IllegalArgumentException.class, () -> {
            s.sendHint(-1);
        });
    }

    @Test
    public void testCloseHintSession() {
        Session s = createSession();
        assumeNotNull(s);
        s.close();
    }

    private static final class SyncRunnable implements Runnable {

        /** true if run is completed. */
        private boolean mHadCompleted;

        SyncRunnable() {}

        public void run() {
            synchronized (this) {
                mHadCompleted = true;
                notifyAll();
            }
        }

        public synchronized void waitForComplete() throws InterruptedException {
            if (!mHadCompleted) {
                wait(1000);
            }
        }
    }

    private static class TestHandlerThread extends HandlerThread {
        private Runnable mTarget;

        TestHandlerThread(Runnable target) {
            super("testSetThreadIdsHandlerThread");
            mTarget = target;
        }

        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            mTarget.run();
        }
    }

    @Test
    @ApiTest(apis = {"android.os.PerformanceHintManager.Session#setThreads"})
    public void testSetThreads() {
        Session s = createSession();
        assumeNotNull(s);
        int[] oldTids = new int[]{Process.myPid()};
        assertArrayEquals(oldTids, s.getThreadIds());

        final SyncRunnable sr = new SyncRunnable();
        HandlerThread thread = new TestHandlerThread(sr);
        thread.start();
        try {
            sr.waitForComplete();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error happens when waiting for handler thread: " + e);
        }
        int[] newTids = new int[]{thread.getThreadId()};
        s.setThreads(newTids);
        assertArrayEquals(newTids, s.getThreadIds());
    }

    @Test
    @ApiTest(apis = {"android.os.PerformanceHintManager.Session#setThreads"})
    public void testSetThreadsWithEmptyList() {
        Session s = createSession();
        assumeNotNull(s);
        assertThrows(IllegalArgumentException.class, () -> {
            s.setThreads(new int[]{});
        });
    }

    @Test
    @ApiTest(apis = {"android.os.PerformanceHintManager.Session#setThreads"})
    public void testSetThreadsWithInvalidTid() {
        final String failureMessage = nativeTestSetThreadsWithInvalidTid();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    @ApiTest(apis = {"android.os.PerformanceHintManager.Session#setPreferPowerEfficiency"})
    public void testSetPreferPowerEfficiency() {
        Session s = createSession();
        assumeNotNull(s);
        s.setPreferPowerEfficiency(false);
        s.setPreferPowerEfficiency(true);
        s.setPreferPowerEfficiency(true);
    }

    @Test
    @ApiTest(apis = {"android.os.PerformanceHintManager.Session#setPreferPowerEfficiency"})
    public void testNativeSetPreferPowerEfficiency() {
        final String failureMessage = nativeSetPreferPowerEfficiency();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ADPF_GPU_REPORT_ACTUAL_WORK_DURATION)
    @ApiTest(apis = {"android.os.PerformanceHintManager.Session#reportActualWorkDuration"})
    public void testReportActualWorkDurationWithWorkDurationClass() {
        Session s = createSession();
        assumeNotNull(s);
        s.updateTargetWorkDuration(16);
        WorkDuration workDuration = new WorkDuration();
        workDuration.setWorkPeriodStartTimestampNanos(1000);
        workDuration.setActualTotalDurationNanos(15);
        workDuration.setActualCpuDurationNanos(11);
        workDuration.setActualGpuDurationNanos(8);
        s.reportActualWorkDuration(workDuration);
    }

    @Test
    // TODO(b/304828176): Support NDK API annotation.
    @ApiTest(apis = {"APerformanceHint_reportActualWorkDuration2"})
    public void testNativeReportActualWorkDuration2() {
        final String resultMessage = nativeTestReportActualWorkDuration2();
        if (!Strings.isNullOrEmpty(resultMessage)) {
            fail(resultMessage);
        }
    }

    @Test
    // TODO(b/304828176): Support NDK API annotation.
    @ApiTest(apis = {"APerformanceHint_reportActualWorkDuration2"})
    public void testNativeReportActualWorkDuration2WithIllegalArgument() {
        final String resultMessage = nativeTestReportActualWorkDuration2WithIllegalArgument();
        if (!Strings.isNullOrEmpty(resultMessage)) {
            fail(resultMessage);
        }
    }

    private native String nativeTestCreateHintSession();
    private native String nativeTestGetPreferredUpdateRateNanos();
    private native String nativeUpdateTargetWorkDuration();
    private native String nativeUpdateTargetWorkDurationWithNegativeDuration();
    private native String nativeReportActualWorkDuration();
    private native String nativeReportActualWorkDurationWithIllegalArgument();
    private native String nativeTestSetThreadsWithInvalidTid();
    private native String nativeSetPreferPowerEfficiency();
    private native String nativeTestReportActualWorkDuration2();
    private native String nativeTestReportActualWorkDuration2WithIllegalArgument();
}
