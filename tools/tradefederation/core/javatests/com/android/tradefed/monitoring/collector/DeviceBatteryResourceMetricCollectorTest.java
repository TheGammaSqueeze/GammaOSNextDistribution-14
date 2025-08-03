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

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class DeviceBatteryResourceMetricCollectorTest {
    private static final String MOCK_CMD_RESPONSE =
            String.join(
                    "\n",
                    "Current Battery Service state:",
                    "    AC powered: true",
                    "    USB powered: false",
                    "    Wireless powered: false",
                    "    Max charging current: 3000000",
                    "    Max charging voltage: 5000000",
                    "    Charge counter: 2797000",
                    "    status: 5",
                    "    health: 2",
                    "    present: true",
                    "    level: 100",
                    "    scale: 100",
                    "    voltage: 4356",
                    "    temperature: 331",
                    "    technology: Li-ion");
    @Mock private IDeviceManager mDeviceManager;
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    private final DeviceBatteryResourceMetricCollector mCollector =
            new DeviceBatteryResourceMetricCollector();

    /** Tests issuing adb shell command to get battery metrics. */
    @Test
    public void testGetDeviceResource() {
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo", "dumpsys battery", 500, TimeUnit.MILLISECONDS))
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
        Assert.assertEquals(
                DeviceBatteryResourceMetricCollector.BATTERY_RESOURCE_NAME,
                resource.getResourceName());
        Assert.assertEquals(5, resource.getMetricCount());
        Assert.assertEquals("status", resource.getMetric(0).getTag());
        Assert.assertEquals(5.0f, resource.getMetric(0).getValue(), 0);
        Assert.assertEquals("health", resource.getMetric(1).getTag());
        Assert.assertEquals(2.0f, resource.getMetric(1).getValue(), 0);
        Assert.assertEquals("level", resource.getMetric(2).getTag());
        Assert.assertEquals("scale", resource.getMetric(3).getTag());
        Assert.assertEquals("temperature", resource.getMetric(4).getTag());
        Assert.assertEquals(33.1f, resource.getMetric(4).getValue(), 0);
    }

    /** Tests adb shell command failed. */
    @Test
    public void testGetDeviceResource_failed() {
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo", "dumpsys battery", 500, TimeUnit.MILLISECONDS))
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
