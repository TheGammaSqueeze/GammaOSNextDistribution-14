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

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.Mockito.*;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/** Tests for {@link DeviceInternetAccessibilityResourceMetricCollector}. */
@RunWith(JUnit4.class)
public class DeviceInternetAccessibilityResourceMetricCollectorTest {
    private static final String MOCK_SUCCESS_RESPONSE =
            String.join(
                    "\n",
                    "PING google.com (172.217.27.142) 56(84) bytes of data.",
                    "64 bytes from tsa03s02-in-f14.1e100.net (172.217.27.142): "
                            + "icmp_seq=1 ttl=57 time=3.09 ms",
                    "64 bytes from tsa03s02-in-f14.1e100.net (172.217.27.142): "
                            + "icmp_seq=2 ttl=57 time=17.7 ms",
                    "",
                    "--- google.com ping statistics ---",
                    "2 packets transmitted, 2 received, 0% packet loss, time 1003ms",
                    "rtt min/avg/max/mdev = 3.092/10.412/17.732/7.320 ms");
    private static final String MOCK_SUCCESS_RESPONSE6 =
            String.join(
                    "\n",
                    "PING google.com(tsa01s08-in-x0e.1e100.net) 56 data bytes",
                    "64 bytes from tsa01s08-in-x0e.1e100.net: icmp_seq=1 ttl=115 time=4.22 ms",
                    "64 bytes from tsa01s08-in-x0e.1e100.net: icmp_seq=2 ttl=115 time=7.31 ms",
                    "",
                    "--- google.com ping statistics ---",
                    "2 packets transmitted, 2 received, 0% packet loss, time 1002ms",
                    "rtt min/avg/max/mdev = 4.226/5.770/7.314/1.544 ms");
    @Mock private IDeviceManager mDeviceManager;
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    private final DeviceDescriptor mDescriptor =
            new DeviceDescriptor() {
                @Override
                public String getSerial() {
                    return "foo";
                }
            };
    private final DeviceInternetAccessibilityResourceMetricCollector mCollector =
            new DeviceInternetAccessibilityResourceMetricCollector();

    private CommandResult createCommandResult(CommandStatus status, String output) {
        return new CommandResult() {
            @Override
            public CommandStatus getStatus() {
                return status;
            }

            @Override
            public String getStdout() {
                return output;
            }
        };
    }

    /** Tests no internet accessibility. */
    @Test
    public void testgetAveragePing_not_accessible() {
        final CommandResult mockResult =
                createCommandResult(CommandStatus.SUCCESS, "ping: unknown host facebook.com");
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo",
                        DeviceInternetAccessibilityResourceMetricCollector.PING_CMD,
                        mCollector.getCmdTimeoutMs(),
                        TimeUnit.MILLISECONDS))
                .thenReturn(mockResult);
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo",
                        DeviceInternetAccessibilityResourceMetricCollector.PING6_CMD,
                        mCollector.getCmdTimeoutMs(),
                        TimeUnit.MILLISECONDS))
                .thenReturn(mockResult);
        Assert.assertEquals(
                DeviceInternetAccessibilityResourceMetricCollector.FAILED_VAL,
                mCollector.getAveragePing(
                        mDescriptor,
                        mDeviceManager,
                        DeviceInternetAccessibilityResourceMetricCollector.PING_CMD),
                0.f);
        Assert.assertEquals(
                DeviceInternetAccessibilityResourceMetricCollector.FAILED_VAL,
                mCollector.getAveragePing(
                        mDescriptor,
                        mDeviceManager,
                        DeviceInternetAccessibilityResourceMetricCollector.PING6_CMD),
                0.f);
    }

    /** Tests successful internet access. */
    @Test
    public void testgetAveragePing_success() {
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo",
                        DeviceInternetAccessibilityResourceMetricCollector.PING_CMD,
                        mCollector.getCmdTimeoutMs(),
                        TimeUnit.MILLISECONDS))
                .thenReturn(createCommandResult(CommandStatus.SUCCESS, MOCK_SUCCESS_RESPONSE));
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo",
                        DeviceInternetAccessibilityResourceMetricCollector.PING6_CMD,
                        mCollector.getCmdTimeoutMs(),
                        TimeUnit.MILLISECONDS))
                .thenReturn(createCommandResult(CommandStatus.SUCCESS, MOCK_SUCCESS_RESPONSE6));
        Assert.assertEquals(
                10.412f,
                mCollector.getAveragePing(
                        mDescriptor,
                        mDeviceManager,
                        DeviceInternetAccessibilityResourceMetricCollector.PING_CMD),
                0.01f);
        Assert.assertEquals(
                5.770f,
                mCollector.getAveragePing(
                        mDescriptor,
                        mDeviceManager,
                        DeviceInternetAccessibilityResourceMetricCollector.PING6_CMD),
                0.01f);
    }

    /** Tests composing resource protobuf. */
    @Test
    public void testGetDeviceResourceMetrics() {
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo",
                        DeviceInternetAccessibilityResourceMetricCollector.PING_CMD,
                        mCollector.getCmdTimeoutMs(),
                        TimeUnit.MILLISECONDS))
                .thenReturn(createCommandResult(CommandStatus.SUCCESS, MOCK_SUCCESS_RESPONSE));
        when(mDeviceManager.executeCmdOnAvailableDevice(
                        "foo",
                        DeviceInternetAccessibilityResourceMetricCollector.PING6_CMD,
                        mCollector.getCmdTimeoutMs(),
                        TimeUnit.MILLISECONDS))
                .thenReturn(createCommandResult(CommandStatus.SUCCESS, MOCK_SUCCESS_RESPONSE6));
        Collection<Resource> resources =
                mCollector.getDeviceResourceMetrics(mDescriptor, mDeviceManager);
        Assert.assertEquals(1, resources.size());
        Resource resource = resources.iterator().next();
        Assert.assertEquals(
                DeviceInternetAccessibilityResourceMetricCollector
                        .INTERNET_ACCESSIBILITY_METRIC_NAME,
                resource.getResourceName());
        Assert.assertEquals(2, resource.getMetricCount());
        Assert.assertEquals(
                DeviceInternetAccessibilityResourceMetricCollector.AVG_PING_TAG,
                resource.getMetric(0).getTag());
        Assert.assertEquals(10.412f, resource.getMetric(0).getValue(), 0.01f);
        Assert.assertEquals(
                DeviceInternetAccessibilityResourceMetricCollector.AVG_PING6_TAG,
                resource.getMetric(1).getTag());
        Assert.assertEquals(5.770f, resource.getMetric(1).getValue(), 0.01f);
    }
}
