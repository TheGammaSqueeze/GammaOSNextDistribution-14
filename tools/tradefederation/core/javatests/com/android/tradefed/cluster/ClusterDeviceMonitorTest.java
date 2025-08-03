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
package com.android.tradefed.cluster;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.monitoring.LabResourceDeviceMonitor;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResource;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Metric;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.MonitoredEntity;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;
import com.google.protobuf.util.Timestamps;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link ClusterDeviceMonitor}. */
@RunWith(JUnit4.class)
public class ClusterDeviceMonitorTest {

    private static final String PRODCERTSTATUS_KEY = "LOAS status";
    private static final String KRBSTATUS_KEY = "Kerberos status";
    private static final String PRODCERTSTATUS_CMD = "prodcertstatus";
    private static final String KRBSTATUS_CMD = "krbstatus";
    @Mock IRunUtil mRunUtil;
    private ClusterDeviceMonitor mClusterDeviceMonitor = null;
    private OptionSetter mClusterDeviceMonitorSetter = null;
    private ClusterDeviceMonitor.EventDispatcher mEventDispatcher = null;
    private IClusterOptions mClusterOptions = null;
    @Mock IClusterEventUploader<ClusterHostEvent> mHostEventUploader;
    private OptionSetter mClusterOptionSetter = null;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mClusterOptions = new ClusterOptions();

        mClusterDeviceMonitor =
                new ClusterDeviceMonitor() {
                    @Override
                    public IRunUtil getRunUtil() {
                        return mRunUtil;
                    }

                    @Override
                    List<DeviceDescriptor> listDevices() {
                        List<DeviceDescriptor> devices = new ArrayList<DeviceDescriptor>();
                        devices.add(
                                new DeviceDescriptor(
                                        "device1",
                                        false,
                                        DeviceAllocationState.Available,
                                        "product1",
                                        "variant1",
                                        "sdkVersion",
                                        "buildId",
                                        "batteryLevel"));
                        return devices;
                    }

                    @Override
                    protected LabResource getCachedLabResource() {
                        return LabResource.newBuilder()
                                .addDevice(
                                        MonitoredEntity.newBuilder()
                                                .putIdentifier(
                                                        LabResourceDeviceMonitor.DEVICE_SERIAL_KEY,
                                                        "device1")
                                                .addResource(
                                                        Resource.newBuilder()
                                                                .setResourceName("resource1")
                                                                .setTimestamp(
                                                                        Timestamps.fromMillis(
                                                                                Instant.now()
                                                                                        .toEpochMilli()))
                                                                .addMetric(
                                                                        Metric.newBuilder()
                                                                                .setTag("tag1")
                                                                                .setValue(10.0f))))
                                .build();
                    }
                };
        mClusterDeviceMonitorSetter = new OptionSetter(mClusterDeviceMonitor);
        mEventDispatcher =
                mClusterDeviceMonitor.new EventDispatcher() {
                    @Override
                    public IClusterOptions getClusterOptions() {
                        return mClusterOptions;
                    }

                    @Override
                    public IClusterEventUploader<ClusterHostEvent> getEventUploader() {
                        return mHostEventUploader;
                    }
                };
        mClusterOptionSetter = new OptionSetter(mClusterOptions);
        mClusterOptionSetter.setOptionValue("cluster:cluster", "cluster1");
        mClusterOptionSetter.setOptionValue("cluster:next-cluster", "cluster2");
        mClusterOptionSetter.setOptionValue("cluster:next-cluster", "cluster3");
        mClusterOptionSetter.setOptionValue("cluster:lab-name", "lab1");
    }

    @Test
    public void testDispatch() throws Exception {
        ArgumentCaptor<ClusterHostEvent> capture = ArgumentCaptor.forClass(ClusterHostEvent.class);

        mEventDispatcher.dispatch();

        verify(mHostEventUploader).postEvent(capture.capture());
        verify(mHostEventUploader).flush();
        ClusterHostEvent hostEvent = capture.getValue();
        Assert.assertNotNull(hostEvent.getHostName());
        Assert.assertNotNull(hostEvent.getData().get(ClusterHostEvent.TEST_HARNESS_START_TIME_KEY));
        Assert.assertNotNull(hostEvent.getTimestamp());
        Assert.assertEquals("cluster1", hostEvent.getClusterId());
        Assert.assertEquals(Arrays.asList("cluster2", "cluster3"), hostEvent.getNextClusterIds());
        Assert.assertEquals("lab1", hostEvent.getLabName());
        Assert.assertEquals("", hostEvent.getData().get("label"));

        Assert.assertEquals(1, hostEvent.getDeviceInfos().size());
        ClusterDeviceInfo device = hostEvent.getDeviceInfos().get(0);
        Assert.assertEquals("device1", device.getDeviceDescriptor().getSerial());
    }

    @Test
    public void testLabel() throws Exception {
        mClusterOptionSetter.setOptionValue("cluster:label", "label1");
        mClusterOptionSetter.setOptionValue("cluster:label", "label2");
        ArgumentCaptor<ClusterHostEvent> capture = ArgumentCaptor.forClass(ClusterHostEvent.class);

        mEventDispatcher.dispatch();

        verify(mHostEventUploader).postEvent(capture.capture());
        verify(mHostEventUploader).flush();
        ClusterHostEvent hostEvent = capture.getValue();
        Assert.assertNotNull(hostEvent.getHostName());
        Assert.assertNotNull(hostEvent.getTimestamp());
        Assert.assertEquals("cluster1", hostEvent.getClusterId());
        Assert.assertEquals(Arrays.asList("cluster2", "cluster3"), hostEvent.getNextClusterIds());
        Assert.assertEquals("lab1", hostEvent.getLabName());
        Assert.assertEquals("label1,label2", hostEvent.getData().get("label"));
    }

    void setOptions() throws Exception {
        mClusterDeviceMonitorSetter.setOptionValue(
                "host-info-cmd", PRODCERTSTATUS_KEY, PRODCERTSTATUS_CMD);
        mClusterDeviceMonitorSetter.setOptionValue("host-info-cmd", KRBSTATUS_KEY, KRBSTATUS_CMD);
    }

    // Test getting additional host information
    @Test
    public void testGetAdditionalHostInfo() throws Exception {
        setOptions();
        String prodcertstatusOutput = "LOAS cert expires in 13h 5m";
        CommandResult prodcertstatusMockResult = new CommandResult();
        prodcertstatusMockResult.setStdout(prodcertstatusOutput);
        prodcertstatusMockResult.setStatus(CommandStatus.SUCCESS);
        when(mRunUtil.runTimedCmdSilently(Mockito.anyLong(), Mockito.eq(PRODCERTSTATUS_CMD)))
                .thenReturn(prodcertstatusMockResult);

        String krbstatusOutput = "android-test ticket expires in 65d 19h";
        CommandResult krbstatusMockResult = new CommandResult();
        krbstatusMockResult.setStdout(krbstatusOutput);
        krbstatusMockResult.setStatus(CommandStatus.SUCCESS);
        when(mRunUtil.runTimedCmdSilently(Mockito.anyLong(), Mockito.eq(KRBSTATUS_CMD)))
                .thenReturn(krbstatusMockResult);

        Map<String, String> expected = new HashMap<>();
        expected.put(PRODCERTSTATUS_KEY, prodcertstatusOutput);
        expected.put(KRBSTATUS_KEY, krbstatusOutput);

        Assert.assertEquals(expected, mClusterDeviceMonitor.getAdditionalHostInfo());
        verify(mRunUtil, times(1))
                .runTimedCmdSilently(Mockito.anyLong(), Mockito.eq(PRODCERTSTATUS_CMD));
        verify(mRunUtil, times(1))
                .runTimedCmdSilently(Mockito.anyLong(), Mockito.eq(KRBSTATUS_CMD));
    }

    // Test getting additional host information with no commands to run
    @Test
    public void testGetAdditionalHostInfo_noCommands() {
        Map<String, String> expected = new HashMap<>();
        Assert.assertEquals(expected, mClusterDeviceMonitor.getAdditionalHostInfo());
    }

    // Test getting additional host information with failures
    @Test
    public void testGetAdditionalHostInfo_commandFailed() throws Exception {
        setOptions();
        String prodcertstatusOutput = "LOAS cert expires in 13h 5m";
        CommandResult prodcertstatusMockResult = new CommandResult();
        prodcertstatusMockResult.setStdout(prodcertstatusOutput);
        prodcertstatusMockResult.setStatus(CommandStatus.SUCCESS);
        when(mRunUtil.runTimedCmdSilently(Mockito.anyLong(), Mockito.eq(PRODCERTSTATUS_CMD)))
                .thenReturn(prodcertstatusMockResult);

        String krbstatusOutput = "android-test ticket expires in 65d 19h";
        String krbstatusError = "Some terrible failure";
        CommandResult krbstatusMockResult = new CommandResult();
        krbstatusMockResult.setStdout(krbstatusOutput);
        krbstatusMockResult.setStderr(krbstatusError);
        krbstatusMockResult.setStatus(CommandStatus.FAILED);
        when(mRunUtil.runTimedCmdSilently(Mockito.anyLong(), Mockito.eq(KRBSTATUS_CMD)))
                .thenReturn(krbstatusMockResult);

        Map<String, String> expected = new HashMap<>();
        expected.put(PRODCERTSTATUS_KEY, prodcertstatusOutput);
        expected.put(KRBSTATUS_KEY, krbstatusError);

        Assert.assertEquals(expected, mClusterDeviceMonitor.getAdditionalHostInfo());
        verify(mRunUtil, times(1))
                .runTimedCmdSilently(Mockito.anyLong(), Mockito.eq(PRODCERTSTATUS_CMD));
        verify(mRunUtil, times(1))
                .runTimedCmdSilently(Mockito.anyLong(), Mockito.eq(KRBSTATUS_CMD));
    }

    @Test
    public void testDeviceExtraInfo() throws Exception {
        ArgumentCaptor<ClusterHostEvent> capture = ArgumentCaptor.forClass(ClusterHostEvent.class);

        mEventDispatcher.dispatch();

        verify(mHostEventUploader).postEvent(capture.capture());
        verify(mHostEventUploader).flush();
        ClusterHostEvent hostEvent = capture.getValue();
        Assert.assertNotNull(hostEvent.getHostName());
        Assert.assertNotNull(hostEvent.getTimestamp());
        Assert.assertEquals("cluster1", hostEvent.getClusterId());
        Assert.assertEquals(Arrays.asList("cluster2", "cluster3"), hostEvent.getNextClusterIds());
        Assert.assertEquals(1, hostEvent.getDeviceInfos().size());
        ClusterDeviceInfo device = hostEvent.getDeviceInfos().get(0);
        Assert.assertEquals("device1", device.getDeviceDescriptor().getSerial());
        Assert.assertEquals(2, device.getExtraInfo().size());
        Assert.assertEquals("10.0", device.getExtraInfo().get("resource1-tag1"));
    }
}
