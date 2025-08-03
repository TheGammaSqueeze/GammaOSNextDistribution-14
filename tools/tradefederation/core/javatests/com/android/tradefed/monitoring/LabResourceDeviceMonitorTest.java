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

package com.android.tradefed.monitoring;

import com.android.tradefed.cluster.ClusterHostUtil;
import com.android.tradefed.cluster.ClusterOptions;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.monitoring.collector.IResourceMetricCollector;

import com.google.dualhomelab.monitoringagent.resourcemonitoring.Attribute;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResource;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResourceRequest;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResourceServiceGrpc;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.MonitoredEntity;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;

@RunWith(JUnit4.class)
public class LabResourceDeviceMonitorTest {
    @Mock private ClusterOptions mClusterOptions;

    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private static final DeviceDescriptor DEVICE_DESCRIPTOR =
            new DeviceDescriptor(
                    "fake-serial",
                    false,
                    DeviceAllocationState.Available,
                    "product",
                    "productVariant",
                    "sdkVersion",
                    "buildId",
                    "batteryLevel");

    private LabResourceDeviceMonitor mMonitor;

    @Before
    public void setUp() throws Exception {
        mMonitor = new LabResourceDeviceMonitor(3, mClusterOptions);
        mMonitor.setDeviceLister(
                new IDeviceMonitor.DeviceLister() {
                    @Override
                    public List<DeviceDescriptor> listDevices() {
                        return List.of(DEVICE_DESCRIPTOR);
                    }

                    @Override
                    public DeviceDescriptor getDeviceDescriptor(String serial) {
                        return null;
                    }
                });
    }

    /** Tests receiving empty LabResource message from test server. */
    @Test
    public void testServerStartAndShutdown() throws InterruptedException, IOException {
        Server server =
                grpcCleanup.register(
                        InProcessServerBuilder.forName("test")
                                .directExecutor()
                                .addService(mMonitor)
                                .build());
        ManagedChannel channel =
                grpcCleanup.register(
                        InProcessChannelBuilder.forName("test").directExecutor().build());
        mMonitor.setServer(server);
        server.start();
        LabResourceServiceGrpc.LabResourceServiceBlockingStub stub =
                LabResourceServiceGrpc.newBlockingStub(channel);
        LabResource labResource = stub.getLabResource(LabResourceRequest.newBuilder().build());
        Assert.assertEquals(LabResource.newBuilder().build(), labResource);
        mMonitor.stop();
        Assert.assertTrue("server should be shutdown after monitor stop", server.isShutdown());
        server.awaitTermination();
    }

    /** Tests building host entity. */
    @Test
    public void testBuildMonitoredHost() {
        when(mClusterOptions.getLabName()).thenReturn("foo-lab");
        when(mClusterOptions.getClusterId()).thenReturn("zoo");
        when(mClusterOptions.getNextClusterIds()).thenReturn(List.of("poolA", "poolB"));
        Assert.assertEquals(
                MonitoredEntity.newBuilder()
                        .putIdentifier(
                                LabResourceDeviceMonitor.HOST_NAME_KEY,
                                ClusterHostUtil.getHostName())
                        .putIdentifier(LabResourceDeviceMonitor.LAB_NAME_KEY, "foo-lab")
                        .putIdentifier(
                                LabResourceDeviceMonitor.TEST_HARNESS_KEY,
                                ClusterHostUtil.getTestHarness())
                        .addAttribute(
                                Attribute.newBuilder()
                                        .setName(LabResourceDeviceMonitor.HOST_GROUP_KEY)
                                        .setValue("zoo"))
                        .addAttribute(
                                Attribute.newBuilder()
                                        .setName(LabResourceDeviceMonitor.HARNESS_VERSION_KEY)
                                        .setValue(ClusterHostUtil.getTfVersion()))
                        .addAttribute(
                                Attribute.newBuilder()
                                        .setName(LabResourceDeviceMonitor.POOL_ATTRIBUTE_NAME)
                                        .setValue("poolA"))
                        .addAttribute(
                                Attribute.newBuilder()
                                        .setName(LabResourceDeviceMonitor.POOL_ATTRIBUTE_NAME)
                                        .setValue("poolB"))
                        .build(),
                mMonitor.buildMonitoredHost(List.of()));
    }

    /** Tests building device entity. */
    @Test
    public void testBuildMonitoredDeviceEntity() {
        when(mClusterOptions.getRunTargetFormat()).thenReturn(null);
        when(mClusterOptions.getDeviceTag()).thenReturn(null);
        MonitoredEntity device = mMonitor.buildMonitoredDevice(DEVICE_DESCRIPTOR, List.of());
        Assert.assertEquals(ClusterHostUtil.getHostName(), device.getAttribute(0).getValue());
        Assert.assertEquals("product:productVariant", device.getAttribute(1).getValue());
        Assert.assertEquals("status", device.getResource(0).getResourceName());
        Assert.assertEquals(
                DEVICE_DESCRIPTOR.getState().toString(),
                device.getResource(0).getMetric(0).getTag());
    }

    class MockCollector implements IResourceMetricCollector {

        boolean isFinished = false;

        @Override
        public Collection<Resource> getHostResourceMetrics() {
            while (!Thread.currentThread().isInterrupted()) {}

            isFinished = true;
            return List.of(Resource.newBuilder().build());
        }
    }

    /** Tests collector operation timeout and trigger next collector operation. */
    @Test
    public void testCollectorTimeout() {
        when(mClusterOptions.getLabName()).thenReturn("foo-lab");
        when(mClusterOptions.getClusterId()).thenReturn("zoo");
        MockCollector collector1 = new MockCollector();
        MockCollector collector2 = new MockCollector();
        mMonitor.startExecutors();
        mMonitor.buildMonitoredHost(List.of(collector1, collector2));
        mMonitor.stopExecutors();
        Assert.assertTrue(collector1.isFinished);
        Assert.assertTrue(collector2.isFinished);
    }

}
