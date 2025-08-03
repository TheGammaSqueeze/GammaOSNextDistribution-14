/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.performance.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

/** Unit test for {@link BootTimeTest}. Focus on testing boot time metrics */
@RunWith(JUnit4.class)
public class BootTimeTestTest {

    private BootTimeTest mBootTimeTest;
    private TestInformation mTestInfo;
    @Mock private ITestInvocationListener mMockListener;
    @Mock private ITestDevice mMockDevice;
    @Mock private IDevice mMockIDevice;
    @Mock private IRunUtil mMockRunUtil;
    @Mock private LogcatReceiver mMockLogcatReceiver;
    @Mock private IBuildInfo mMockBuildInfo;

    private static final String BOOT_COMPLETED_PROP = "getprop sys.boot_completed";
    private static final String SUCCESSIVE_BOOT_PREPARE_CMD = "command";

    @Before
    public void setUp() {
        initMocks(this);
        mBootTimeTest =
                new BootTimeTest() {
                    @Override
                    public IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
        mBootTimeTest.setDevice(mMockDevice);
        mBootTimeTest.setRebootLogcatReceiver(mMockLogcatReceiver);
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        mTestInfo = TestInformation.newBuilder().build();
        mBootTimeTest.setBuild(mMockBuildInfo);
    }

    @Test
    public void testAnalyzingCustomBootMetrics_normalLogcat()
            throws ConfigurationException, DeviceNotAvailableException {
        // Setup boot time test with custom boot metrics enabled
        mBootTimeTest.setDmesgBootCompleteTime(Arrays.asList(22083.0));
        mBootTimeTest.setDmesgBootIterationTime(22083.0);

        OptionSetter setter = new OptionSetter(mBootTimeTest);
        setOptionsForCustomBootMetricsTest(setter);
        setter.setOptionValue("boot-count", "1");
        setter.setOptionValue("boot-delay", "10000");
        setter.setOptionValue("boot-time-pattern", "metric_1", "hello\\s*metric 1");
        setter.setOptionValue("boot-time-pattern", "metric_2", "hello\\s*metric 2");

        // run user supplied command
        CommandResult prepareCmdResult = new CommandResult();
        prepareCmdResult.setStatus(CommandStatus.SUCCESS);
        when(mMockDevice.executeShellV2Command(SUCCESSIVE_BOOT_PREPARE_CMD))
                .thenReturn(prepareCmdResult);
        // Successive boots
        when(mMockDevice.executeShellCommand(BOOT_COMPLETED_PROP)).thenReturn("1");

        String log =
                String.join(
                        "\n",
                        "01-03 00:56:33.173     0     0 I         : Linux version 4.4.177 (Kernel"
                                + " Boot Started)",
                        "01-03 00:56:43.882   935   935 I metric 108 finished",
                        "07-08 21:05:48.290   935   935 I hello  metric 1",
                        "07-08 21:05:49.290   935  1031 I SystemServiceManager: Starting phase"
                                + " 1000",
                        "07-08 21:05:51.320   935  1037 I hello metric 2",
                        "01-03 01:06:33.173     0     0 I         : Linux version 4.4.177 (Kernel"
                                + " Boot Started)",
                        "01-03 01:06:43.882   935   935 I metric 108 finished");

        // Mock logcat info. Need to stub with two separate logcat data for system service analyzing
        // and custom boot metric analyzing, each will clean up log data after analyzing.
        when(mMockLogcatReceiver.getLogcatData())
                .thenReturn(createInputStreamSource(log))
                .thenReturn(createInputStreamSource(log));
        mBootTimeTest.run(mTestInfo, mMockListener);

        verify(mMockListener, times(2)).testEnded(any(TestDescription.class), anyMap());
        // verify successive boot metrics are correct
        assertEquals(Arrays.asList(21083.0), mBootTimeTest.getBootMetricValues("metric_1"));
        assertEquals(Arrays.asList(24113.0), mBootTimeTest.getBootMetricValues("metric_2"));
    }

    @Test
    public void testAnalyzingCustomBootMetrics_missingBootCompleteSignals()
            throws ConfigurationException, DeviceNotAvailableException {
        // Setup boot time test with custom boot metrics enabled
        mBootTimeTest.setDmesgBootCompleteTime(Arrays.asList(22083.0));
        mBootTimeTest.setDmesgBootIterationTime(22083.0);

        OptionSetter setter = new OptionSetter(mBootTimeTest);
        setOptionsForCustomBootMetricsTest(setter);
        setter.setOptionValue("boot-count", "1");
        setter.setOptionValue("boot-delay", "10000");
        setter.setOptionValue("boot-time-pattern", "metric_1", "hello\\s*metric 1");

        // run user supplied command
        CommandResult prepareCmdResult = new CommandResult();
        prepareCmdResult.setStatus(CommandStatus.SUCCESS);
        when(mMockDevice.executeShellV2Command(SUCCESSIVE_BOOT_PREPARE_CMD))
                .thenReturn(prepareCmdResult);
        // Successive boots
        when(mMockDevice.executeShellCommand(BOOT_COMPLETED_PROP)).thenReturn("1");

        String log =
                String.join(
                        "\n",
                        "01-03 00:56:33.173     0     0 I         : Linux version 4.4.177 (Kernel"
                                + " Boot Started)",
                        "07-08 21:05:48.290   935   935 I hello  metric 1",
                        // Missing boot complete signal
                        "07-08 21:05:49.290   935  1031 I hello metric 2");

        // Mock logcat info. Need to stub with two separate logcat data for system service analyzing
        // and custom boot metric analyzing, each will clean up log data after analyzing.
        when(mMockLogcatReceiver.getLogcatData())
                .thenReturn(createInputStreamSource(log))
                .thenReturn(createInputStreamSource(log));
        mBootTimeTest.run(mTestInfo, mMockListener);
        verify(mMockListener, times(2)).testEnded(any(TestDescription.class), anyMap());
        // verify successive boot metrics are correct
        assertTrue(mBootTimeTest.getBootMetricValues("metric_1").isEmpty());
    }

    @Test
    public void testAnalyzingCustomBootMetrics_missingCustomMetrics()
            throws ConfigurationException, DeviceNotAvailableException {
        // Setup boot time test with custom boot metrics enabled
        mBootTimeTest.setDmesgBootCompleteTime(Arrays.asList(24083.0));
        mBootTimeTest.setDmesgBootIterationTime(24083.0);

        OptionSetter setter = new OptionSetter(mBootTimeTest);
        setOptionsForCustomBootMetricsTest(setter);
        setter.setOptionValue("boot-count", "1");
        setter.setOptionValue("boot-delay", "10000");
        setter.setOptionValue("boot-time-pattern", "metric_1", "hello\\s*metric 1");
        setter.setOptionValue("boot-time-pattern", "metric_2", "hello\\s*metric 2");

        // run user supplied command
        CommandResult prepareCmdResult = new CommandResult();
        prepareCmdResult.setStatus(CommandStatus.SUCCESS);
        when(mMockDevice.executeShellV2Command(SUCCESSIVE_BOOT_PREPARE_CMD))
                .thenReturn(prepareCmdResult);
        // Successive boots
        when(mMockDevice.executeShellCommand(BOOT_COMPLETED_PROP)).thenReturn("1");

        String log =
                String.join(
                        "\n",
                        "01-03 00:56:33.173     0     0 I         : Linux version 4.4.177 (Kernel"
                                + " Boot Started)",
                        // Missing custom metric signal "hello metric 1"
                        "07-08 21:05:49.290   935  1031 I SystemServiceManager: Starting phase"
                                + " 1000",
                        "07-08 21:05:53.290   935   935 I hello  metric 2");

        // Mock logcat info. Need to stub with two separate logcat data for system service analyzing
        // and custom boot metric analyzing, each will clean up log data after analyzing.
        when(mMockLogcatReceiver.getLogcatData())
                .thenReturn(createInputStreamSource(log))
                .thenReturn(createInputStreamSource(log));
        mBootTimeTest.run(mTestInfo, mMockListener);
        verify(mMockListener, times(2)).testEnded(any(TestDescription.class), anyMap());
        // verify successive boot metrics are correct
        assertTrue(mBootTimeTest.getBootMetricValues("metric_1").isEmpty());
        assertEquals(Arrays.asList(28083.0), mBootTimeTest.getBootMetricValues("metric_2"));
    }

    @Test
    public void testLogcatArtifactPerIteration_twoBootIterations()
            throws ConfigurationException, DeviceNotAvailableException {
        OptionSetter setter = new OptionSetter(mBootTimeTest);
        setter.setOptionValue("boot-count", "2");
        setter.setOptionValue("granular-boot-info", "true");

        String log = "Some important logs";
        when(mMockLogcatReceiver.getLogcatData()).thenReturn(createInputStreamSource(log));

        // Successive boots
        when(mMockDevice.executeShellCommand(BOOT_COMPLETED_PROP)).thenReturn("1");

        mBootTimeTest.run(mTestInfo, mMockListener);
        verify(mMockListener, times(2))
                .testLog(
                        matches("Successive_reboots_logcat_.*"),
                        eq(LogDataType.TEXT),
                        any(InputStreamSource.class));
    }

    private InputStreamSource createInputStreamSource(String log) {
        return new ByteArrayInputStreamSource(log.getBytes());
    }

    private void setOptionsForCustomBootMetricsTest(OptionSetter setter)
            throws ConfigurationException {
        setter.setOptionValue("first-boot", "false");
        setter.setOptionValue("successive-boot", "true");
        setter.setOptionValue("granular-boot-info", "true");
        setter.setOptionValue("successive-boot-prepare-cmd", SUCCESSIVE_BOOT_PREPARE_CMD);
    }
}
