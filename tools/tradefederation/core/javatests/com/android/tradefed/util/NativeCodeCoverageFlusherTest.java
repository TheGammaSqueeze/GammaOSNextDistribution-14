/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tradefed.util;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@RunWith(JUnit4.class)
public final class NativeCodeCoverageFlusherTest {

    private static final String PS_OUTPUT =
            "USER       PID   PPID  VSZ   RSS   WCHAN       PC  S NAME\n"
                    + "shell       123  1366  123    456   SyS_epoll+   0  S adbd\n"
                    + "root        234     1 7890   123   binder_io+   0  S logcat\n"
                    + "root        456  1234  567   890   binder_io+   0  S media.swcodec\n";
    @Mock ITestDevice mMockDevice;
    @Mock IRunUtil mMockRunUtil;

    // Object under test
    NativeCodeCoverageFlusher mFlusher;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(PS_OUTPUT).when(mMockDevice).executeShellCommand("ps -e");
    }

    @Test
    public void testClearCoverageMeasurements_rmCommandCalled() throws DeviceNotAvailableException {
        doReturn(true).when(mMockDevice).isAdbRoot();

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("1000000000\n");
        result.setExitCode(0);

        when(mMockDevice.executeShellV2Command(anyString())).thenReturn(result);

        mFlusher = new NativeCodeCoverageFlusher(mMockDevice, ImmutableList.of());
        mFlusher.setRunUtil(mMockRunUtil);
        mFlusher.resetCoverage();

        // Verify that the coverage clear commands were executed.
        verify(mMockDevice).executeShellCommand("find /data/misc/trace -name '*.profraw' -delete");
        verify(mMockDevice).executeShellCommand("find /data/misc/trace -name '*.gcda' -delete");
    }

    @Test
    public void testNoAdbRootClearCoverageMeasurements_noOp() throws DeviceNotAvailableException {
        doReturn(false).when(mMockDevice).isAdbRoot();

        try {
            mFlusher = new NativeCodeCoverageFlusher(mMockDevice, ImmutableList.of());
            mFlusher.setRunUtil(mMockRunUtil);
            mFlusher.resetCoverage();
            fail("Should have thrown an exception");
        } catch (IllegalStateException e) {
            // Expected
        }

        // Verify that no shell command was executed.
        verify(mMockDevice, never()).executeShellCommand(anyString());
    }

    @Test
    public void testFlushCoverageAllProcesses_flushAllCommandCalled()
            throws DeviceNotAvailableException {
        doReturn(true).when(mMockDevice).isAdbRoot();

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("1000000000\n");
        result.setExitCode(0);

        when(mMockDevice.executeShellV2Command(anyString())).thenReturn(result);

        mFlusher = new NativeCodeCoverageFlusher(mMockDevice, ImmutableList.of());
        mFlusher.setRunUtil(mMockRunUtil);
        mFlusher.forceCoverageFlush();

        // Verify that the flush command for all individual processes was called.
        verify(mMockDevice).executeShellCommand("kill -37 123 234 456");
    }

    @Test
    public void testFlushCoverageSpecificProcesses_flushSpecificCommandCalled()
            throws DeviceNotAvailableException {
        List<String> processes = ImmutableList.of("adbd", "logcat");

        doReturn(true).when(mMockDevice).isAdbRoot();

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("1000000000\n");
        result.setExitCode(0);

        when(mMockDevice.executeShellV2Command(anyString())).thenReturn(result);

        mFlusher = new NativeCodeCoverageFlusher(mMockDevice, processes);
        mFlusher.setRunUtil(mMockRunUtil);
        mFlusher.forceCoverageFlush();

        // Verify that the flush command for the specific processes was called.
        verify(mMockDevice).executeShellCommand("kill -37 123 234");
    }

    @Test
    public void testFlushNotHandled_flushNotCalled() throws DeviceNotAvailableException {
        List<String> processes = ImmutableList.of("adbd");

        doReturn(true).when(mMockDevice).isAdbRoot();

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("0000000000\n");
        result.setExitCode(0);

        when(mMockDevice.executeShellV2Command(anyString())).thenReturn(result);

        mFlusher = new NativeCodeCoverageFlusher(mMockDevice, processes);
        mFlusher.setRunUtil(mMockRunUtil);
        mFlusher.forceCoverageFlush();

        // Verify that the flush command was not called.
        verify(mMockDevice, never()).executeShellCommand("kill -37 123");
    }

    @Test
    public void testFlushStatusReadFailed_flushNotCalled() throws DeviceNotAvailableException {
        List<String> processes = ImmutableList.of("adbd");

        doReturn(true).when(mMockDevice).isAdbRoot();

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setExitCode(-1);

        when(mMockDevice.executeShellV2Command(anyString())).thenReturn(result);

        mFlusher = new NativeCodeCoverageFlusher(mMockDevice, processes);
        mFlusher.setRunUtil(mMockRunUtil);
        mFlusher.forceCoverageFlush();

        // Verify that the flush command was not called.
        verify(mMockDevice, never()).executeShellCommand("kill -37 123");
    }

    @Test
    public void testFlushStatusReadEmpty_flushNotCalled() throws DeviceNotAvailableException {
        List<String> processes = ImmutableList.of("adbd");

        doReturn(true).when(mMockDevice).isAdbRoot();

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("");
        result.setExitCode(0);

        when(mMockDevice.executeShellV2Command(anyString())).thenReturn(result);

        mFlusher = new NativeCodeCoverageFlusher(mMockDevice, processes);
        mFlusher.setRunUtil(mMockRunUtil);
        mFlusher.forceCoverageFlush();

        // Verify that the flush command was not called.
        verify(mMockDevice, never()).executeShellCommand("kill -37 123");
    }

    @Test
    public void testFlushOnlySigCgt_flushSpecificProcesses() throws DeviceNotAvailableException {
        doReturn(true).when(mMockDevice).isAdbRoot();

        CommandResult resultNotHandled = new CommandResult(CommandStatus.SUCCESS);
        resultNotHandled.setStdout("8000000000000000\n");
        resultNotHandled.setExitCode(0);

        CommandResult resultHandled = new CommandResult(CommandStatus.SUCCESS);
        resultHandled.setStdout("1000000000\n");
        resultHandled.setExitCode(0);

        CommandResult resultEmpty = new CommandResult(CommandStatus.SUCCESS);
        resultEmpty.setStdout("\n");
        resultEmpty.setExitCode(0);

        when(mMockDevice.executeShellV2Command(contains("123"))).thenReturn(resultNotHandled);
        when(mMockDevice.executeShellV2Command(contains("234"))).thenReturn(resultHandled);
        when(mMockDevice.executeShellV2Command(contains("456"))).thenReturn(resultEmpty);

        mFlusher = new NativeCodeCoverageFlusher(mMockDevice, ImmutableList.of());
        mFlusher.setRunUtil(mMockRunUtil);
        mFlusher.forceCoverageFlush();

        // Verify that the flush command was only called for pid 234.
        verify(mMockDevice).executeShellCommand("kill -37 234");
    }

    @Test
    public void testNoAdbRootFlush_noOp() throws DeviceNotAvailableException {
        doReturn(false).when(mMockDevice).isAdbRoot();

        try {
            mFlusher = new NativeCodeCoverageFlusher(mMockDevice, ImmutableList.of("mediaserver"));
            mFlusher.setRunUtil(mMockRunUtil);
            mFlusher.forceCoverageFlush();
            fail("Should have thrown an exception");
        } catch (IllegalStateException e) {
            // Expected
        }

        // Verify no shell commands or pid lookups were executed.
        verify(mMockDevice, never()).executeShellCommand(anyString());
        verify(mMockDevice, never()).getProcessPid(anyString());
    }
}
