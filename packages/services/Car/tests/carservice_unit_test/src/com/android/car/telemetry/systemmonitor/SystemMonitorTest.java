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

package com.android.car.telemetry.systemmonitor;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.os.Handler;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@RunWith(MockitoJUnitRunner.class)
public class SystemMonitorTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String TEST_LOADAVG = "0.2 3.4 2.2 123/1452 21348";
    private static final String TEST_LOADAVG_BAD_FORMAT = "1.2 3.4";
    private static final String TEST_LOADAVG_NOT_FLOAT = "1.2 abc 2.1 12/231 2";

    @Mock private Handler mMockHandler; // it promptly executes the runnable in the same thread
    @Mock private ActivityManager mMockActivityManager;
    @Mock private SystemMonitor.SystemMonitorCallback mMockCallback;

    @Captor ArgumentCaptor<Runnable> mRunnableCaptor;
    @Captor ArgumentCaptor<SystemMonitorEvent> mEventCaptor;

    @Before
    public void setup() {
        when(mMockHandler.post(any(Runnable.class))).thenAnswer(i -> {
            Runnable runnable = i.getArgument(0);
            runnable.run();
            return true;
        });
    }

    @Test
    public void testSetEventCpuUsageLevel_setsCorrectUsageLevelForHighUsage() {
        SystemMonitor systemMonitor = SystemMonitor.create(mMockActivityManager, mMockHandler);
        SystemMonitorEvent event = new SystemMonitorEvent();

        systemMonitor.setEventCpuUsageLevel(event, /* cpuLoadPerCore= */ 1.5);

        assertThat(event.getCpuUsageLevel())
            .isEqualTo(SystemMonitorEvent.USAGE_LEVEL_HI);
    }

    @Test
    public void testSetEventCpuUsageLevel_setsCorrectUsageLevelForMedUsage() {
        SystemMonitor systemMonitor = SystemMonitor.create(mMockActivityManager, mMockHandler);
        SystemMonitorEvent event = new SystemMonitorEvent();

        systemMonitor.setEventCpuUsageLevel(event, /* cpuLoadPerCore= */ 0.6);

        assertThat(event.getCpuUsageLevel())
            .isEqualTo(SystemMonitorEvent.USAGE_LEVEL_MED);
    }

    @Test
    public void testSetEventCpuUsageLevel_setsCorrectUsageLevelForLowUsage() {
        SystemMonitor systemMonitor = SystemMonitor.create(mMockActivityManager, mMockHandler);
        SystemMonitorEvent event = new SystemMonitorEvent();

        systemMonitor.setEventCpuUsageLevel(event, /* cpuLoadPerCore= */ 0.5);

        assertThat(event.getCpuUsageLevel())
            .isEqualTo(SystemMonitorEvent.USAGE_LEVEL_LOW);
    }

    @Test
    public void testSetEventMemUsageLevel_setsCorrectUsageLevelForHighUsage() {
        SystemMonitor systemMonitor = SystemMonitor.create(mMockActivityManager, mMockHandler);
        SystemMonitorEvent event = new SystemMonitorEvent();

        systemMonitor.setEventMemUsageLevel(event, /* memLoadRatio= */ 0.98);

        assertThat(event.getMemoryUsageLevel())
            .isEqualTo(SystemMonitorEvent.USAGE_LEVEL_HI);
    }

    @Test
    public void testSetEventMemUsageLevel_setsCorrectUsageLevelForMedUsage() {
        SystemMonitor systemMonitor = SystemMonitor.create(mMockActivityManager, mMockHandler);
        SystemMonitorEvent event = new SystemMonitorEvent();

        systemMonitor.setEventMemUsageLevel(event, /* memLoadRatio= */ 0.85);

        assertThat(event.getMemoryUsageLevel())
            .isEqualTo(SystemMonitorEvent.USAGE_LEVEL_MED);
    }

    @Test
    public void testSetEventMemUsageLevel_setsCorrectUsageLevelForLowUsage() {
        SystemMonitor systemMonitor = SystemMonitor.create(mMockActivityManager, mMockHandler);
        SystemMonitorEvent event = new SystemMonitorEvent();

        systemMonitor.setEventMemUsageLevel(event, /* memLoadRatio= */ 0.80);

        assertThat(event.getMemoryUsageLevel())
            .isEqualTo(SystemMonitorEvent.USAGE_LEVEL_LOW);
    }

    @Test
    public void testSetCallback_whenMemUsageLow_shouldInvokeCallback() throws IOException {
        doAnswer(i -> {
            MemoryInfo mi = i.getArgument(0); // memory usage is at 50%
            mi.availMem = 5_000_000L;
            mi.totalMem = 10_000_000;
            return null;
        }).when(mMockActivityManager).getMemoryInfo(any(MemoryInfo.class));
        SystemMonitor systemMonitor = new SystemMonitor(
                mMockActivityManager, mMockHandler, writeTempFile(TEST_LOADAVG));

        systemMonitor.setSystemMonitorCallback(mMockCallback);

        verify(mMockCallback, atLeastOnce()).onSystemMonitorEvent(mEventCaptor.capture());
        SystemMonitorEvent event = mEventCaptor.getValue();
        // from TEST_LOADAVG, cpu load = 0.2 / numProcessors, CPU usage should be low
        assertThat(event.getCpuUsageLevel()).isEqualTo(SystemMonitorEvent.USAGE_LEVEL_LOW);
        // 1 - 5_000_000 / 10_000_000 = 0.5, memory usage should be low
        assertThat(event.getMemoryUsageLevel()).isEqualTo(SystemMonitorEvent.USAGE_LEVEL_LOW);
    }

    @Test
    public void testSetCallback_whenMemUsageHigh_shouldInvokeCallback() throws IOException {
        doAnswer(i -> {
            MemoryInfo mi = i.getArgument(0); // memory usage is at 95%
            mi.availMem = 500_000L;
            mi.totalMem = 10_000_000L;
            return null;
        }).when(mMockActivityManager).getMemoryInfo(any(MemoryInfo.class));
        SystemMonitor systemMonitor = new SystemMonitor(
                mMockActivityManager, mMockHandler, writeTempFile(TEST_LOADAVG));

        systemMonitor.setSystemMonitorCallback(mMockCallback);

        verify(mMockCallback, atLeastOnce()).onSystemMonitorEvent(mEventCaptor.capture());
        SystemMonitorEvent event = mEventCaptor.getValue();
        // 1 - 500_000 / 10_000_000 = 0.95, memory usage should be high
        assertThat(event.getMemoryUsageLevel()).isEqualTo(SystemMonitorEvent.USAGE_LEVEL_HI);
    }

    @Test
    public void testWhenLoadavgIsBadFormat_getCpuLoadReturnsNull() throws IOException {
        SystemMonitor systemMonitor = new SystemMonitor(
                mMockActivityManager, mMockHandler, writeTempFile(TEST_LOADAVG_BAD_FORMAT));

        assertThat(systemMonitor.getCpuLoad()).isNull();
    }

    @Test
    public void testWhenLoadavgIsNotFloatParsable_getCpuLoadReturnsNull() throws IOException {
        SystemMonitor systemMonitor = new SystemMonitor(
                mMockActivityManager, mMockHandler, writeTempFile(TEST_LOADAVG_NOT_FLOAT));

        assertThat(systemMonitor.getCpuLoad()).isNull();
    }

    @Test
    public void testWhenUnsetCallback_sameCallbackFromSetCallbackIsRemoved() throws IOException {
        SystemMonitor systemMonitor = new SystemMonitor(
                mMockActivityManager, mMockHandler, writeTempFile(TEST_LOADAVG));

        systemMonitor.setSystemMonitorCallback(mMockCallback);
        systemMonitor.unsetSystemMonitorCallback();

        verify(mMockHandler, times(1)).post(mRunnableCaptor.capture());
        Runnable setRunnable = mRunnableCaptor.getValue();
        verify(mMockHandler, times(1)).removeCallbacks(mRunnableCaptor.capture());
        Runnable unsetRunnable = mRunnableCaptor.getValue();
        assertThat(setRunnable).isEqualTo(unsetRunnable);
    }

    /**
     * Creates and writes to the temp file, returns its path.
     */
    private String writeTempFile(String content) throws IOException {
        File tempFile = temporaryFolder.newFile();
        try (FileWriter fw = new FileWriter(tempFile)) {
            fw.write(content);
        }
        return tempFile.getAbsolutePath();
    }
}
