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
import com.android.tradefed.cluster.IClusterOptions;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.monitoring.collector.IResourceMetricCollector;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Attribute;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResource;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResourceRequest;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.LabResourceServiceGrpc;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Metric;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.MonitoredEntity;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;
import com.google.protobuf.util.Timestamps;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * The lab resource monitor which initializes/manages the gRPC server for LabResourceService. To add
 * resource metric collectors, please add resource_metric_collector tags in global configuration to
 * load the collectors.
 */
@OptionClass(alias = "lab-resource-monitor")
public class LabResourceDeviceMonitor extends LabResourceServiceGrpc.LabResourceServiceImplBase
        implements IDeviceMonitor {
    public static final String DEVICE_SERIAL_KEY = "device_serial";
    public static final String HOST_NAME_KEY = "hostname";
    public static final String LAB_NAME_KEY = "lab_name";
    public static final String TEST_HARNESS_KEY = "test_harness";
    public static final String HARNESS_VERSION_KEY = "harness_version";
    public static final String HOST_GROUP_KEY = "host_group";
    public static final int DEFAULT_PORT = 8887;
    public static final int DEFAULT_THREAD_COUNT = 1;
    public static final String POOL_ATTRIBUTE_NAME = "pool";
    public static final String RUN_TARGET_ATTRIBUTE_NAME = "run_target";
    public static final String STATUS_RESOURCE_NAME = "status";
    public static final String HARDWARE_REVISION_RESOURCE_NAME = "hardware_revision";
    public static final float FIXED_METRIC_VALUE = 1.0f;
    private static final long EXECUTOR_TERMINATE_TIMEOUT_SEC = 10;
    private Server mServer;
    private IClusterOptions mClusterOptions;
    private DeviceLister mDeviceLister;
    private final Collection<IResourceMetricCollector> mMetricCollectors = new ArrayList<>();
    private final ReadWriteLock mLabResourceLock = new ReentrantReadWriteLock();
    private LabResource mLabResource = LabResource.newBuilder().build();
    // The ScheduledExecutorService triggers the metricize operations base on mMetricizeCycleSec.
    private ScheduledExecutorService mMetricizeExecutor;
    // The ExecutorService executes the collector functions in every metricize operation.
    private ExecutorService mCollectionExecutor;

    @Option(
            name = "metricize-cycle-sec",
            description = "The time in seconds between for each metricize cycle.")
    private long mMetricizeCycleSec = 300;

    public LabResourceDeviceMonitor() {
        super();
    }

    @VisibleForTesting
    LabResourceDeviceMonitor(long metricizeCycleSec, IClusterOptions clusterOptions) {
        super();
        mMetricizeCycleSec = metricizeCycleSec;
        mClusterOptions = clusterOptions;
    }

    private IClusterOptions getClusterOptions() {
        return mClusterOptions == null ? ClusterHostUtil.getClusterOptions() : mClusterOptions;
    }

    private void loadMetricCollectors() {
        List<IResourceMetricCollector> collectors =
                GlobalConfiguration.getInstance().getResourceMetricCollectors();
        if (collectors != null) {
            mMetricCollectors.addAll(collectors);
        }
    }

    @VisibleForTesting
    void setServer(Server server) {
        mServer = server;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        if (getClusterOptions().isDeviceMonitorDisabled()) {
            CLog.i("LabResourceDeviceMonitor is disabled.");
            return;
        }
        if (mServer == null) {
            mServer =
                    // Because dockerized TF use bridge network driver now, so we remove the
                    // hostname restriction for the grpc server.
                    // The restriction should be established by setting docker port mapping.
                    // For example: adding docker run argument "-p 127.0.0.1:8887:8887".
                    ServerBuilder.forPort(DEFAULT_PORT)
                            .addService(this)
                            .executor(
                                    Executors.newFixedThreadPool(
                                            DEFAULT_THREAD_COUNT,
                                            new ThreadFactory() {
                                                @Override
                                                public Thread newThread(Runnable r) {
                                                    Thread t =
                                                            Executors.defaultThreadFactory()
                                                                    .newThread(r);
                                                    t.setDaemon(true);
                                                    t.setName("lab-resource-server");
                                                    return t;
                                                }
                                            }))
                            .build();
        }
        try {
            mServer.start();
            loadMetricCollectors();
            startExecutors();
            scheduleMetricizeTask();
        } catch (IOException | IllegalStateException e) {
            CLog.e(e);
            stopExecutors();
        }
    }

    @VisibleForTesting
    void startExecutors() {
        mMetricizeExecutor =
                MoreExecutors.getExitingScheduledExecutorService(
                        new ScheduledThreadPoolExecutor(
                                1,
                                new ThreadFactory() {
                                    @Override
                                    public Thread newThread(Runnable r) {
                                        Thread t = Executors.defaultThreadFactory().newThread(r);
                                        t.setDaemon(true);
                                        t.setName("lab-resource-metricize-executor");
                                        return t;
                                    }
                                }));
        mCollectionExecutor =
                Executors.newSingleThreadExecutor(
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(Runnable r) {
                                Thread t = Executors.defaultThreadFactory().newThread(r);
                                t.setDaemon(true);
                                t.setName("lab-resource-collection-executor");
                                return t;
                            }
                        });
    }

    @VisibleForTesting
    void stopExecutors() {
        awaitTerminateExecutor(mMetricizeExecutor);
        awaitTerminateExecutor(mCollectionExecutor);
    }

    private void awaitTerminateExecutor(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(EXECUTOR_TERMINATE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            CLog.w("Interrupted when waiting executor terminated.");
        }
    }

    private void setCachedLabResource(LabResource resource) {
        mLabResourceLock.writeLock().lock();
        try {
            mLabResource = resource;
        } finally {
            mLabResourceLock.writeLock().unlock();
        }
    }

    protected LabResource getCachedLabResource() {
        mLabResourceLock.readLock().lock();
        try {
            return mLabResource;
        } finally {
            mLabResourceLock.readLock().unlock();
        }
    }

    private void scheduleMetricizeTask() {
        if (mMetricizeExecutor == null) {
            CLog.d("schedule metricize task before the mMetricizeExecutor initialized");
            return;
        }
        mMetricizeExecutor.scheduleAtFixedRate(
                () -> {
                    LabResource.Builder builder =
                            LabResource.newBuilder().setHost(buildMonitoredHost(mMetricCollectors));
                    List<DeviceDescriptor> descriptors =
                            mDeviceLister
                                    .listDevices()
                                    .stream()
                                    .filter(descriptor -> !descriptor.isTemporary())
                                    .collect(Collectors.toList());
                    for (DeviceDescriptor descriptor : descriptors) {
                        if (mMetricizeExecutor.isShutdown()) {
                            break;
                        }
                        if (ClusterHostUtil.isLocalhostIpPort(descriptor.getSerial())) {
                            continue;
                        }
                        builder.addDevice(buildMonitoredDevice(descriptor, mMetricCollectors));
                    }
                    setCachedLabResource(builder.build());
                },
                0,
                mMetricizeCycleSec,
                TimeUnit.SECONDS);
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        if (mServer != null && !mServer.isShutdown()) {
            mServer.shutdownNow();
        }
        stopExecutors();
    }

    /** {@inheritDoc} */
    @Override
    public void setDeviceLister(DeviceLister lister) {
        mDeviceLister = lister;
    }

    /** {@inheritDoc} */
    @Override
    public void notifyDeviceStateChange(
            String serial, DeviceAllocationState oldState, DeviceAllocationState newState) {
        // ignored
    }

    /** The gRPC request handler. */
    @Override
    public void getLabResource(
            LabResourceRequest request, StreamObserver<LabResource> responseObserver) {
        responseObserver.onNext(getCachedLabResource());
        responseObserver.onCompleted();
    }

    /** Build host {@link MonitoredEntity}. */
    @VisibleForTesting
    MonitoredEntity buildMonitoredHost(Collection<IResourceMetricCollector> collectors) {
        MonitoredEntity.Builder builder =
                MonitoredEntity.newBuilder()
                        .putIdentifier(HOST_NAME_KEY, ClusterHostUtil.getHostName())
                        .putIdentifier(LAB_NAME_KEY, getClusterOptions().getLabName())
                        .putIdentifier(TEST_HARNESS_KEY, ClusterHostUtil.getTestHarness())
                        .addAttribute(
                                Attribute.newBuilder()
                                        .setName(HOST_GROUP_KEY)
                                        .setValue(getClusterOptions().getClusterId()))
                        .addAttribute(
                                Attribute.newBuilder()
                                        .setName(HARNESS_VERSION_KEY)
                                        .setValue(ClusterHostUtil.getTfVersion()))
                        .addAllAttribute(
                                getClusterOptions().getNextClusterIds().stream()
                                        .map(
                                                pool ->
                                                        Attribute.newBuilder()
                                                                .setName(POOL_ATTRIBUTE_NAME)
                                                                .setValue(pool)
                                                                .build())
                                        .collect(Collectors.toList()));
        for (IResourceMetricCollector collector : collectors) {
            Future<Collection<Resource>> future = null;
            try {
                future = mCollectionExecutor.submit(collector::getHostResourceMetrics);
                builder.addAllResource(
                        future.get(collector.getHostMetricizeTimeoutMs(), TimeUnit.MILLISECONDS));
            } catch (InterruptedException
                    | ExecutionException
                    | TimeoutException
                    | RejectedExecutionException
                    | NullPointerException e) {
                CLog.w(
                        "%s got %s when collecting host metrics.",
                        collector.getClass().getSimpleName(), e.toString());
                if (future != null) {
                    future.cancel(true);
                }
            }
        }
        return builder.build();
    }

    /** Builds device {@link MonitoredEntity}. */
    @VisibleForTesting
    MonitoredEntity buildMonitoredDevice(
            DeviceDescriptor descriptor, Collection<IResourceMetricCollector> collectors) {
        MonitoredEntity.Builder builder =
                MonitoredEntity.newBuilder()
                        .putIdentifier(DEVICE_SERIAL_KEY, descriptor.getSerial())
                        .addAttribute(
                                Attribute.newBuilder()
                                        .setName(HOST_NAME_KEY)
                                        .setValue(ClusterHostUtil.getHostName()))
                        .addAttribute(
                                Attribute.newBuilder()
                                        .setName(RUN_TARGET_ATTRIBUTE_NAME)
                                        .setValue(
                                                ClusterHostUtil.getRunTarget(
                                                        descriptor,
                                                        getClusterOptions().getRunTargetFormat(),
                                                        getClusterOptions().getDeviceTag())))
                        .addResource(
                                Resource.newBuilder()
                                        .setResourceName(STATUS_RESOURCE_NAME)
                                        .setTimestamp(
                                                Timestamps.fromMillis(Instant.now().toEpochMilli()))
                                        .addMetric(
                                                Metric.newBuilder()
                                                        .setTag(descriptor.getState().name())
                                                        .setValue(FIXED_METRIC_VALUE)));
        // Continue to collect resource metrics if the device is a full stack android device.
        if (!descriptor.getDeviceClass().equals(TestDevice.class.getSimpleName())) {
            return builder.build();
        }
        for (IResourceMetricCollector collector : collectors) {
            Future<Collection<Resource>> future = null;
            try {
                future =
                        mCollectionExecutor.submit(
                                () ->
                                        collector.getDeviceResourceMetrics(
                                                descriptor,
                                                GlobalConfiguration.getDeviceManagerInstance()));
                builder.addAllResource(
                        future.get(collector.getDeviceMetricizeTimeoutMs(), TimeUnit.MILLISECONDS));
            } catch (InterruptedException
                    | ExecutionException
                    | TimeoutException
                    | RejectedExecutionException
                    | NullPointerException e) {
                CLog.w(
                        "%s got %s when collecting device metrics.",
                        collector.getClass().getSimpleName(), e.toString());
                if (future != null) {
                    future.cancel(true);
                }
            }
        }
        return builder.build();
    }
}
