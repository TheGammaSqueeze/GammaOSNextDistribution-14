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
package android.cts.statsd.subscriber;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.compatibility.common.util.CpuFeatures;
import com.android.internal.os.StatsdConfigProto;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.SystemUptime;
import com.android.os.ShellConfig;
import com.android.os.statsd.ShellDataProto;
import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceTestCase;
import com.google.common.io.Files;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import android.cts.statsd.atom.AtomTestCase;

/**
 * Statsd shell data subscription test.
 */
public class ShellSubscriberTest extends AtomTestCase {
    private int sizetBytes;

    public class ShellSubscriptionThread extends Thread {
        String cmd;
        CollectingByteOutputReceiver receiver;
        int maxTimeoutForCommandSec;
        public ShellSubscriptionThread(
                String cmd,
                CollectingByteOutputReceiver receiver,
                int maxTimeoutForCommandSec) {
            this.cmd = cmd;
            this.receiver = receiver;
            this.maxTimeoutForCommandSec = maxTimeoutForCommandSec;
        }
        public void run () {
            try {
                getDevice().executeShellCommand(cmd, receiver, maxTimeoutForCommandSec,
                        /*maxTimeToOutputShellResponse=*/maxTimeoutForCommandSec, TimeUnit.SECONDS,
                        /*retryAttempts=*/0);
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        sizetBytes = getSizetBytes();
    }

    public void testShellSubscription() {
        if (sizetBytes < 0) {
            return;
        }

        CollectingByteOutputReceiver receiver = startSubscription();
        checkOutput(receiver);
    }

    // This is testShellSubscription but 5x
    public void testShellSubscriptionReconnect() {
        int numOfSubs = 5;
        if (sizetBytes < 0) {
            return;
        }

        for (int i = 0; i < numOfSubs; i++) {
            CollectingByteOutputReceiver receiver = startSubscription();
            checkOutput(receiver);
        }
    }

    // Tests that multiple clients can run at once:
    // -Runs maximum number of active subscriptions (20) at once.
    // -Maximum number of subscriptions minus 1 return:
    // --Leave 1 subscription alive to ensure the subscriber helper thread stays alive.
    // -Run maximum number of subscriptions minus 1 to reach the maximum running again.
    // -Attempt to run one more subscription, which will fail.
    public void testShellMaxSubscriptions() {
        // Maximum number of active subscriptions, set in ShellSubscriber.h
        int maxSubs = 20;
        if (sizetBytes < 0) {
            return;
        }
        CollectingByteOutputReceiver[] receivers = new CollectingByteOutputReceiver[maxSubs + 1];
        ShellSubscriptionThread[] shellThreads = new ShellSubscriptionThread[maxSubs + 1];
        ShellConfig.ShellSubscription config = createConfig();
        byte[] validConfig = makeValidConfig(config);

        // timeout of 5 sec for all subscriptions except for the first
        int timeout = 5;
        // timeout of 25 sec to ensure that the first subscription stays active for two sessions
        // of creating the maximum number of subscriptions
        int firstSubTimeout = 25;
        try {
            // Push the shell config file to the device
            String remotePath = pushShellConfigToDevice(validConfig);

            String cmd = "cat " + remotePath + " |  cmd stats data-subscribe " + timeout;
            String firstSubCmd =
                        "cat " + remotePath + " |  cmd stats data-subscribe " + firstSubTimeout;

            for (int i = 0; i < maxSubs; i++) {
                // Run data-subscribe on a thread
                receivers[i] = new CollectingByteOutputReceiver();
                if (i == 0) {
                    shellThreads[i] =
                        new ShellSubscriptionThread(firstSubCmd, receivers[i], firstSubTimeout);
                } else {
                    shellThreads[i] =
                        new ShellSubscriptionThread(cmd, receivers[i], timeout);
                }
                shellThreads[i].start();
                LogUtil.CLog.d("Starting new shell subscription.");
            }
            // Sleep 2 seconds to make sure all subscription clients are initialized before
            // first pushed event
            Thread.sleep(2000);

            // Pushed event. arbitrary label = 1
            doAppBreadcrumbReported(1);

            // Make sure the last 19 threads die before moving to the next step.
            // First subscription is still active due to its longer timeout that is used keep
            // the subscriber helper thread alive
            for (int i = 1; i < maxSubs; i++) {
                shellThreads[i].join();
            }

            // Validate the outputs of the last 19 subscriptions since they are finished
            for (int i = 1; i < maxSubs; i++) {
                checkOutput(receivers[i]);
            }

            // Run 19 more subscriptions to hit the maximum active subscriptions again
            for (int i = 1; i < maxSubs; i++) {
                // Run data-subscribe on a thread
                receivers[i] = new CollectingByteOutputReceiver();
                shellThreads[i] =
                    new ShellSubscriptionThread(cmd, receivers[i], timeout);
                shellThreads[i].start();
                LogUtil.CLog.d("Starting new shell subscription.");
            }
            // Sleep 2 seconds to make sure all subscription clients are initialized before
            // pushed event
            Thread.sleep(2000);

            // ShellSubscriber only allows 20 subscriptions at a time. This is the 21st which will
            // be ignored
            receivers[maxSubs] = new CollectingByteOutputReceiver();
            shellThreads[maxSubs] =
                new ShellSubscriptionThread(cmd, receivers[maxSubs], timeout);
            shellThreads[maxSubs].start();

            // Sleep 1 seconds to ensure that the 21st subscription is rejected
            Thread.sleep(1000);

            // Pushed event. arbitrary label = 1
            doAppBreadcrumbReported(1);

            // Make sure all the threads die before moving to the next step
            for (int i = 0; i <= maxSubs; i++) {
                shellThreads[i].join();
            }
            // Remove config from device if not already deleted
            getDevice().executeShellCommand("rm " + remotePath);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        for (int i = 0; i < maxSubs; i++) {
            checkOutput(receivers[i]);
        }
        // Ensure that the 21st subscription got rejected and has an empty output
        byte[] output = receivers[maxSubs].getOutput();
        assertThat(output.length).isEqualTo(0);
    }

    private int getSizetBytes() {
        try {
            ITestDevice device = getDevice();
            if (CpuFeatures.isArm64(device)) {
                return 8;
            }
            if (CpuFeatures.isArm32(device)) {
                return 4;
            }
            return -1;
        } catch (DeviceNotAvailableException e) {
            return -1;
        }
    }

    private ShellConfig.ShellSubscription createConfig() {
        return ShellConfig.ShellSubscription.newBuilder()
                .addPushed((StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                .setAtomId(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER))
                        .build()).build();
    }

    private byte[] makeValidConfig(ShellConfig.ShellSubscription config) {
        int length = config.toByteArray().length;
        byte[] validConfig = new byte[sizetBytes + length];
        System.arraycopy(IntToByteArrayLittleEndian(length), 0, validConfig, 0, sizetBytes);
        System.arraycopy(config.toByteArray(), 0, validConfig, sizetBytes, length);
        return validConfig;
    }

    private String pushShellConfigToDevice(byte[] validConfig) {
        try {
            File configFile = File.createTempFile("shellconfig", ".config");
            configFile.deleteOnExit();
            Files.write(validConfig, configFile);
            String remotePath = "/data/local/tmp/" + configFile.getName();
            getDevice().pushFile(configFile, remotePath);
            return remotePath;

        } catch (Exception e) {
            fail(e.getMessage());
        }
        return "";
    }

    private CollectingByteOutputReceiver startSubscription() {
        ShellConfig.ShellSubscription config = createConfig();
        CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
        LogUtil.CLog.d("Uploading the following config:\n" + config.toString());
        byte[] validConfig = makeValidConfig(config);
        // timeout of 2 sec for both data-subscribe command and executeShellCommand in thread
        int timeout = 2;
        try {
            // Push the shell config file to the device
            String remotePath = pushShellConfigToDevice(validConfig);

            String cmd = "cat " + remotePath + " |  cmd stats data-subscribe " + timeout;
            // Run data-subscribe on a thread
            ShellSubscriptionThread shellThread =
                                    new ShellSubscriptionThread(cmd, receiver, timeout);
            shellThread.start();
            LogUtil.CLog.d("Starting new shell subscription.");

            // Sleep a second to make sure subscription is initiated
            Thread.sleep(1000);

            // Pushed event. arbitrary label = 1
            doAppBreadcrumbReported(1);
            // Wait for thread to die before returning
            shellThread.join();
            // Remove config from device if not already deleted
            getDevice().executeShellCommand("rm " + remotePath);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return receiver;
    }

    private byte[] IntToByteArrayLittleEndian(int length) {
        ByteBuffer b = ByteBuffer.allocate(sizetBytes);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(length);
        return b.array();
    }

    // We do not know how much data will be returned, but we can check the data format.
    private void checkOutput(CollectingByteOutputReceiver receiver) {
        int atomCount = 0;
        int startIndex = 0;

        byte[] output = receiver.getOutput();
        LogUtil.CLog.d("output length in checkOutput: " + output.length);
        assertThat(output.length).isGreaterThan(0);
        while (output.length > startIndex) {
            assertThat(output.length).isAtLeast(startIndex + sizetBytes);
            int dataLength = readSizetFromByteArray(output, startIndex);
            if (dataLength == 0) {
                // We have received a heartbeat from statsd. This heartbeat isn't accompanied by any
                // atoms so return to top of while loop.
                startIndex += sizetBytes;
                continue;
            }
            assertThat(output.length).isAtLeast(startIndex + sizetBytes + dataLength);

            ShellDataProto.ShellData data = null;
            try {
                int dataStart = startIndex + sizetBytes;
                int dataEnd = dataStart + dataLength;
                data = ShellDataProto.ShellData.parseFrom(
                        Arrays.copyOfRange(output, dataStart, dataEnd));
            } catch (InvalidProtocolBufferException e) {
                fail("Failed to parse proto");
            }

            assertThat(data.getAtomCount()).isEqualTo(1);
            assertThat(data.getAtom(0).hasAppBreadcrumbReported()).isTrue();
            assertThat(data.getAtom(0).getAppBreadcrumbReported().getLabel()).isEqualTo(1);
            assertThat(data.getAtom(0).getAppBreadcrumbReported().getState().getNumber())
                       .isEqualTo(1);
            atomCount++;
            startIndex += sizetBytes + dataLength;
        }
        assertThat(atomCount).isGreaterThan(0);
    }

    // Converts the bytes in range [startIndex, startIndex + sizetBytes) from a little-endian array
    // into an integer. Even though sizetBytes could be greater than 4, we assume that the result
    // will fit within an int.
    private int readSizetFromByteArray(byte[] arr, int startIndex) {
        int value = 0;
        for (int j = 0; j < sizetBytes; j++) {
            value += ((int) arr[j + startIndex] & 0xffL) << (8 * j);
        }
        return value;
    }
}
