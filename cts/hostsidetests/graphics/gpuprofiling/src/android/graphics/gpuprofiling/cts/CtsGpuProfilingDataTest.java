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

package android.graphics.gpuprofiling.cts;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.CodedInputStream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import perfetto.protos.PerfettoConfig.DataSourceDescriptor;
import perfetto.protos.PerfettoConfig.TraceConfig;
import perfetto.protos.PerfettoConfig.TracingServiceState;
import perfetto.protos.PerfettoConfig.TracingServiceState.DataSource;
import perfetto.protos.PerfettoTrace.GpuCounterEvent;
import perfetto.protos.PerfettoTrace.Trace;
import perfetto.protos.PerfettoTrace.TracePacket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests that ensure Perfetto producers exist for GPU profiling when the device claims to support
 * profilng.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsGpuProfilingDataTest extends BaseHostJUnit4Test {
    public static final String TAG = "GpuProfilingDataDeviceActivity";

    // This test ensures that if a device reports ro.hardware.gpu.profiler.support if reports the
    // correct perfetto producers
    //
    // Positive tests
    // - Ensure the perfetto producers for render stages, counters, and ftrace gpu frequency are
    // available
    // - Ensure the validity of GPU counter values

    private static final String BIN_NAME = "ctsgraphicsgpucountersinit";
    private static final String DEVICE_BIN_PATH = "/data/local/tmp/" + BIN_NAME;
    private static final String APP = "android.graphics.gpuprofiling.app";
    private static final String APK = "CtsGraphicsProfilingDataApp.apk";
    private static final String ACTIVITY = "GpuRenderStagesDeviceActivity";
    private static final String COUNTERS_SOURCE_NAME = "gpu.counters";
    private static final String STAGES_SOURCE_NAME = "gpu.renderstages";
    private static final String PROFILING_PROPERTY = "graphics.gpu.profiler.support";
    private static final String LAYER_PACKAGE_PROPERTY = "graphics.gpu.profiler.vulkan_layer_apk";
    private static final String LAYER_NAME = "VkRenderStagesProducer";
    private static final String DEBUG_PROPERTY = "debug.graphics.gpu.profiler.perfetto";
    private static final int MAX_QUERY_RETRIES = 5;

    private static final int TRACE_BUFFER_SIZE_KB = 131072; // 1024 * 128
    private static final Duration TRACE_COUNTER_PERIOD = Duration.ofMillis(5);
    private static final Duration TRACE_DURATION = Duration.ofSeconds(1);
    private static final String TRACE_FILE_PATH = "/data/misc/perfetto-traces/cts-trace";
    private static final int MAX_TRACE_RETRIES = 3;

    private class ShellThread extends Thread {

        private String mCmd;

        public ShellThread(String cmd) throws Exception {
            super("ShellThread");
            mCmd = cmd;
        }

        @Override
        public void run() {
            try {
                getDevice().executeShellV2Command(mCmd);
            } catch (Exception e) {
                CLog.e("Failed to start counters producer" + e.getMessage());
            }
        }
    }

    /** Kill the native process and remove the layer related settings after each test */
    @After
    public void cleanup() throws Exception {
        getDevice().executeShellV2Command("killall " + BIN_NAME);
        getDevice().executeShellV2Command("am force-stop " + APP);
        getDevice().executeShellV2Command("settings delete global gpu_debug_layers");
        getDevice().executeShellV2Command("settings delete global enable_gpu_debug_layers");
        getDevice().executeShellV2Command("settings delete global gpu_debug_app");
        getDevice().executeShellV2Command("settings delete global gpu_debug_layer_app");
    }

    /** Clean up before starting any tests. Apply the necessary layer settings if we need them */
    @Before
    public void init() throws Exception {
        cleanup();
        String layerApp = getDevice().getProperty(LAYER_PACKAGE_PROPERTY);
        if (layerApp != null && !layerApp.isEmpty()) {
            getDevice().executeShellV2Command("settings put global enable_gpu_debug_layers 1");
            getDevice().executeShellV2Command("settings put global gpu_debug_app " + APP);
            getDevice()
                    .executeShellV2Command("settings put global gpu_debug_layer_app " + layerApp);
            getDevice().executeShellV2Command("settings put global gpu_debug_layers " + LAYER_NAME);
        }
        installPackage(APK);
    }

    /**
     * This is the primary test of the feature. We check that gpu.counters and gpu.renderstages
     * sources are available.
     */
    @Test
    public void testProfilingDataProducersAvailable() throws Exception {
        getDevice().setProperty(DEBUG_PROPERTY, "1");
        String profilingSupport = getDevice().getProperty(PROFILING_PROPERTY);
        if (profilingSupport == null || !profilingSupport.equals("true")) {
            return;
        }

        // Spin up a new thread to avoid blocking the main thread while the native process waits to
        // be
        // killed.
        ShellThread shellThread = new ShellThread(DEVICE_BIN_PATH);
        shellThread.start();
        CommandResult activityStatus =
                getDevice().executeShellV2Command("am start -n " + APP + "/." + ACTIVITY);
        boolean countersSourceFound = false;
        boolean stagesSourceFound = false;
        Set<Integer> counterIds = null;

        for (int i = 0; i < MAX_QUERY_RETRIES; i++) {
            CommandResult queryStatus =
                    getDevice().executeShellV2Command("perfetto --query-raw | base64");
            Assert.assertEquals(CommandStatus.SUCCESS, queryStatus.getStatus());
            byte[] decodedBytes = Base64.getMimeDecoder().decode(queryStatus.getStdout());
            TracingServiceState state = TracingServiceState.parseFrom(decodedBytes);
            int count = state.getDataSourcesCount();
            Assert.assertTrue("No sources found", count > 0);
            for (int j = 0; j < count; j++) {
                DataSource source = state.getDataSources(j);
                DataSourceDescriptor descriptor = source.getDsDescriptor();
                if (descriptor != null) {
                    if (descriptor.getName().equals(COUNTERS_SOURCE_NAME)) {
                        countersSourceFound = true;
                        Assert.assertTrue(
                                "GpuCounterDescriptor field not found in data source descriptor ("
                                        + COUNTERS_SOURCE_NAME
                                        + ")",
                                descriptor.hasGpuCounterDescriptor());

                        counterIds =
                                descriptor.getGpuCounterDescriptor().getSpecsList().stream()
                                        .map(spec -> spec.getCounterId())
                                        .collect(Collectors.toSet());
                    }
                    if (descriptor.getName().equals(STAGES_SOURCE_NAME)) {
                        stagesSourceFound = true;
                    }
                    if (countersSourceFound && stagesSourceFound) {
                        break;
                    }
                }
            }
            if (countersSourceFound && stagesSourceFound) {
                break;
            }
            RunUtil.getDefault().sleep(500);
        }

        Assert.assertTrue("Producer " + STAGES_SOURCE_NAME + " not found", stagesSourceFound);
        Assert.assertTrue("Producer " + COUNTERS_SOURCE_NAME + " not found", countersSourceFound);

        // Create trace config based on queried data sources.
        TraceConfig.Builder config =
                TraceConfig.newBuilder().setDurationMs((int) TRACE_DURATION.toMillis());
        config.addBuffersBuilder().setSizeKb(TRACE_BUFFER_SIZE_KB);
        config.addDataSourcesBuilder()
                .getConfigBuilder()
                .setName(COUNTERS_SOURCE_NAME)
                .getGpuCounterConfigBuilder()
                .setCounterPeriodNs((int) TRACE_COUNTER_PERIOD.toNanos())
                .addAllCounterIds(counterIds);
        File configFile = File.createTempFile("perfetto", ".cfg");
        try (OutputStream out = new FileOutputStream(configFile)) {
            config.build().writeTo(out);
        }

        boolean foundValidGpuCounterEvent = false;
        for (int i = 0; i < MAX_TRACE_RETRIES; i++) {
            if (foundValidGpuCounterEvent) break;

            CommandResult queryStatus =
                    getDevice()
                            .executeShellV2Command(
                                    "perfetto -c - -o " + TRACE_FILE_PATH, configFile);
            Assert.assertEquals(CommandStatus.SUCCESS, queryStatus.getStatus());

            File traceResult = getDevice().pullFile(TRACE_FILE_PATH);
            Trace trace = null;
            try (InputStream in = new FileInputStream(traceResult)) {
                trace = Trace.parseFrom(CodedInputStream.newInstance(in));
            }

            for (TracePacket packet : trace.getPacketList()) {
                if (foundValidGpuCounterEvent) break;
                if (!packet.hasGpuCounterEvent()) continue;

                GpuCounterEvent gpuCounterEvent = packet.getGpuCounterEvent();
                if (gpuCounterEvent.getCountersCount() == 0) continue;

                for (GpuCounterEvent.GpuCounter counter : gpuCounterEvent.getCountersList()) {
                    // Currently, "valid counters" are defined by having at least one, non-zero
                    // value.
                    if ((counter.hasIntValue() && counter.getIntValue() > 0)
                            || (counter.hasDoubleValue() && counter.getDoubleValue() > 0.0)) {
                        foundValidGpuCounterEvent = true;
                        break;
                    }
                }
            }

            traceResult.delete();
            CommandResult deleteTraceStatus = getDevice()
                    .executeShellV2Command("rm -f " + TRACE_FILE_PATH);
            Assert.assertEquals(CommandStatus.SUCCESS, deleteTraceStatus.getStatus());
        }

        configFile.delete();
        Assert.assertTrue(
                "Trace does not contain valid GPU counter values.", foundValidGpuCounterEvent);
    }
}
