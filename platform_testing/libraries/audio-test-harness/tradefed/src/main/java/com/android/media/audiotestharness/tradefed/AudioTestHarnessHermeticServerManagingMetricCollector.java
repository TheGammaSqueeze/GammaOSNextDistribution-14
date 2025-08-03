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

package com.android.media.audiotestharness.tradefed;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.media.audiotestharness.common.Defaults;
import com.android.media.audiotestharness.proto.AudioDeviceOuterClass;
import com.android.media.audiotestharness.server.AudioTestHarnessGrpcServer;
import com.android.media.audiotestharness.server.AudioTestHarnessGrpcServerFactory;
import com.android.media.audiotestharness.server.config.SharedHostConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.metric.BaseDeviceMetricCollector;
import com.android.tradefed.device.metric.DeviceMetricData;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.metrics.proto.MetricMeasurement;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link BaseDeviceMetricCollector} that manages the spin-up and tear-down of hermetic instances of
 * the Audio Test Harness Server.
 */
public class AudioTestHarnessHermeticServerManagingMetricCollector
        extends BaseDeviceMetricCollector {
    /**
     * Command used for executing port reversals with adb.
     *
     * <p>In general, a call to adb looks like the following:
     *
     * <pre>adb reverse tcp:55555 tcp:51000</pre>
     *
     * <p>Which forwards all requests that the device makes to localhost:55555 to the adb host
     * machine port 51000.
     */
    private static final String REVERSE_COMMAND = "reverse";

    /**
     * Argument specifying the source port to forward requests from on device.
     *
     * <p>This value is a constant as it is based on the {@link Defaults#DEVICE_PORT} value.
     */
    private static final String SOURCE_PORT_ARGUMENT =
            String.format("tcp:%d", Defaults.DEVICE_PORT);

    /**
     * Argument used with a call to
     *
     * <pre>adb reverse</pre>
     *
     * that undos all currently registered port reversals.
     */
    private static final String UNDO_REVERSALS_ARGUMENT = "--remove-all";

    private final AudioTestHarnessGrpcServerFactory mAudioTestHarnessGrpcServerFactory;

    private AudioTestHarnessGrpcServer mAudioTestHarnessGrpcServer;

    @Option(
            name = "capture-device",
            description = "The capture device(s) to use for test " + "execution.")
    private final List<String> mCaptureDevices;

    public AudioTestHarnessHermeticServerManagingMetricCollector() {
        this(AudioTestHarnessGrpcServerFactory.createFactory(), new ArrayList<>());
    }

    @VisibleForTesting
    AudioTestHarnessHermeticServerManagingMetricCollector(
            AudioTestHarnessGrpcServerFactory audioTestHarnessGrpcServerFactory,
            List<String> captureDevices) {
        Preconditions.checkNotNull(
                audioTestHarnessGrpcServerFactory,
                "audioTestHarnessGrpcServerFactory cannot be null");
        mAudioTestHarnessGrpcServerFactory = audioTestHarnessGrpcServerFactory;
        mCaptureDevices = captureDevices;
    }

    @Override
    public void onTestRunStart(DeviceMetricData runData) {
        LogUtil.CLog.i("Starting Audio Test Harness...");

        // Use the default configuration if no devices are specified, otherwise, create a
        // configuration containing the specified devices.
        SharedHostConfiguration sharedHostConfiguration =
                mCaptureDevices.isEmpty()
                        ? null
                        : SharedHostConfiguration.create(
                                mCaptureDevices.stream()
                                        .map(
                                                (name) ->
                                                        AudioDeviceOuterClass.AudioDevice
                                                                .newBuilder()
                                                                .setName(name)
                                                                .addCapabilities(
                                                                        AudioDeviceOuterClass
                                                                                .AudioDevice
                                                                                .Capability.CAPTURE)
                                                                .build())
                                        .collect(toImmutableList()));

        mAudioTestHarnessGrpcServer =
                mAudioTestHarnessGrpcServerFactory.createOnNextAvailablePort(
                        sharedHostConfiguration);

        // Ensure that the server's logs are output through the TradeFed logging system.
        // This needs to be called after the server is instantiated to ensure
        // that the static logger on the class has been loaded.
        AudioTestHarnessServerLogForwardingHandler.configureServerLoggerWithHandler(true);

        try {
            mAudioTestHarnessGrpcServer.open();
        } catch (IOException ioe) {
            throw new RuntimeException(
                    "Unable to start the Audio Test Harness Server, test cannot continue.", ioe);
        }

        for (ITestDevice device : getDevices()) {
            reversePort(device, mAudioTestHarnessGrpcServer.getPort());
        }
    }

    @Override
    public void onTestRunEnd(
            DeviceMetricData runData, Map<String, MetricMeasurement.Metric> currentRunMetrics) {
        LogUtil.CLog.i("Stopping Audio Test Harness...");

        mAudioTestHarnessGrpcServer.close();
        mAudioTestHarnessGrpcServerFactory.close();

        for (ITestDevice device : getDevices()) {
            undoPortReversals(device);
        }
    }

    /**
     * Reverse port-forwards requests from the provided device on the default communication port to
     * the host at the specified destination port.
     *
     * <p>This process allows the device to communicate with the Audio Test Harness.
     */
    private void reversePort(ITestDevice testDevice, int destinationPort) {
        String destinationPortArgument = String.format("tcp:%d", destinationPort);
        try {
            LogUtil.CLog.i(
                    String.format(
                            "Reversing forwarding connections from device (serial=%s) to host "
                                    + "(device:%d => host:%d)",
                            testDevice.getSerialNumber(), Defaults.DEVICE_PORT, destinationPort));

            // Executes the 'adb reverse tcp:<source-port> tcp:<destination-port>' command.
            testDevice.executeAdbCommand(
                    REVERSE_COMMAND, SOURCE_PORT_ARGUMENT, destinationPortArgument);
        } catch (DeviceNotAvailableException dnae) {
            throw new RuntimeException(
                    "Unable to forward requests from device to host, test cannot continue since "
                            + "the device cannot communicate with the Audio Test Harness",
                    dnae);
        }
    }

    /** Undoes all of the reverse port-forwarding on the provided device. */
    private void undoPortReversals(ITestDevice testDevice) {
        try {
            LogUtil.CLog.i(
                    String.format(
                            "Undoing port reversals for device (serial=%s)",
                            testDevice.getSerialNumber()));

            // Executes the 'adb reverse --remove-all' command.
            testDevice.executeAdbCommand(REVERSE_COMMAND, UNDO_REVERSALS_ARGUMENT);
        } catch (DeviceNotAvailableException dnae) {
            LogUtil.CLog.w(
                    String.format(
                            "Unable to undo port reversals for device (%s)",
                            testDevice.getSerialNumber()));
        }
    }
}
