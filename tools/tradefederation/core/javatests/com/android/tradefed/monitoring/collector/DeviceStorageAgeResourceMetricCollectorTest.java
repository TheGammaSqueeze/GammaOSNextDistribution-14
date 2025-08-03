/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tradefed.monitoring.collector;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.Mockito.*;

import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;

import java.util.Calendar;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/** Tests for {@link DeviceStorageAgeResourceMetricCollector}. */
@RunWith(JUnit4.class)
public class DeviceStorageAgeResourceMetricCollectorTest {
    private static final String MOCK_CMD_RESPONSE =
            "11-20 17:54:09.226 16112 16113 I storaged_emmc_info: [ufs 210,1,1,1]";
    @Mock private IDeviceManager mDeviceManager;
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    private final DeviceStorageAgeResourceMetricCollector mCollector =
            new DeviceStorageAgeResourceMetricCollector();

    /** Tests building storage age command. */
    @Test
    public void testBuildStorageAgeCmd() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2020);
        cal.set(Calendar.MONTH, 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR, 8);
        cal.set(Calendar.MINUTE, 10);
        cal.set(Calendar.SECOND, 25);
        String cmd = mCollector.buildStorageAgeCommand(cal);
        Assert.assertEquals(
                "logcat -b events -d -t \"2020-01-01 07:10:00.000\" | grep "
                        + "storaged_emmc_info | tail -1",
                cmd);
    }

    /** Tests getting storage age metrics. */
    @Test
    public void testGetDeviceResource() {
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo",
                        mCollector.buildStorageAgeCommand(Calendar.getInstance()),
                        500,
                        TimeUnit.MILLISECONDS))
                .thenReturn(
                        new CommandResult() {
                            @Override
                            public CommandStatus getStatus() {
                                return CommandStatus.SUCCESS;
                            }

                            @Override
                            public String getStdout() {
                                return MOCK_CMD_RESPONSE;
                            }
                        });
        Collection<Resource> resources =
                mCollector.getDeviceResourceMetrics(
                        new DeviceDescriptor() {
                            @Override
                            public String getSerial() {
                                return "foo";
                            }
                        },
                        mDeviceManager);
        Assert.assertEquals(1, resources.size());
        Resource resource = resources.iterator().next();
        Assert.assertEquals("storage_age", resource.getResourceName());
        Assert.assertEquals(1, resource.getMetricCount());
        Assert.assertEquals("age", resource.getMetric(0).getTag());
        Assert.assertEquals(1.0f, resource.getMetric(0).getValue(), 0);
    }

    /** Tests adb command failed. */
    @Test
    public void testGetDeviceResource_failed() {
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo",
                        mCollector.buildStorageAgeCommand(Calendar.getInstance()),
                        500,
                        TimeUnit.MILLISECONDS))
                .thenReturn(
                        new CommandResult() {
                            @Override
                            public CommandStatus getStatus() {
                                return CommandStatus.FAILED;
                            }
                        });
        Collection<Resource> resources =
                mCollector.getDeviceResourceMetrics(
                        new DeviceDescriptor() {
                            @Override
                            public String getSerial() {
                                return "foo";
                            }
                        },
                        mDeviceManager);
        Assert.assertEquals(0, resources.size());
    }
}
