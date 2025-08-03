/*
 * Copyright 2021 The Android Open Source Project
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
package android.os.cts.process;

import static android.os.cts.process.common.Consts.HELPER1_RECEIVER0;
import static android.os.cts.process.common.Consts.HELPER1_RECEIVER1;
import static android.os.cts.process.common.Consts.HELPER1_RECEIVER2;
import static android.os.cts.process.common.Consts.HELPER2_RECEIVER0;
import static android.os.cts.process.common.Consts.HELPER2_RECEIVER1;
import static android.os.cts.process.common.Consts.HELPER2_RECEIVER2;
import static android.os.cts.process.common.Consts.HELPER3_RECEIVER0;
import static android.os.cts.process.common.Consts.HELPER3_RECEIVER1;
import static android.os.cts.process.common.Consts.HELPER3_RECEIVER2;
import static android.os.cts.process.common.Consts.HELPER3_RECEIVER3;
import static android.os.cts.process.common.Consts.HELPER4_RECEIVER0;
import static android.os.cts.process.common.Consts.HELPER4_RECEIVER1;
import static android.os.cts.process.common.Consts.HELPER4_RECEIVER2;
import static android.os.cts.process.common.Consts.HELPER4_RECEIVER3;
import static android.os.cts.process.common.Consts.HELPER_SHARED_PROCESS_NAME;
import static android.os.cts.process.common.Consts.PACKAGE_HELPER1;
import static android.os.cts.process.common.Consts.PACKAGE_HELPER2;
import static android.os.cts.process.common.Consts.PACKAGE_HELPER3;
import static android.os.cts.process.common.Consts.PACKAGE_HELPER4;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.os.SystemClock;
import android.os.cts.process.common.Consts;
import android.os.cts.process.common.Message;
import android.util.Log;
import android.util.LogPrinter;
import android.util.Printer;

import androidx.test.InstrumentationRegistry;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import com.android.compatibility.common.util.BroadcastMessenger.Receiver;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * CTS for {@link android.os.Process}.
 *
 * We have more test in cts/tests/tests/os too.
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class ProcessTest2 {
    protected static final Context sContext = InstrumentationRegistry.getTargetContext();

    /** Tell all the helper app processes to stop */
    private static void stopAllHelperApps() throws Exception {
        // Make sure all the broadcasts are delivered.
        ShellUtils.runShellCommand("am wait-for-broadcast-idle");
        Thread.sleep(500); // Just give the system a bit time to breathe.
        ShellUtils.runShellCommand("am force-stop " + PACKAGE_HELPER1);
        ShellUtils.runShellCommand("am force-stop " + PACKAGE_HELPER2);
        ShellUtils.runShellCommand("am force-stop " + PACKAGE_HELPER3);
        ShellUtils.runShellCommand("am force-stop " + PACKAGE_HELPER4);
        Thread.sleep(500); // Just give the system a bit time to breathe.
    }

    public void checkStartTime(ComponentName cn, String expectedProcessName) throws Exception {
        // Start the target process by sending a broadcast, and get back the results
        // from the target APIs.
        try (Receiver<Message> receiver = new Receiver<>(sContext, Consts.TAG)) {

            // Start the first process.
            Intent intent = new Intent(Consts.ACTION_SEND_BACK_START_TIME)
                    .setComponent(cn)
                    .setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

            final long beforeStartElapsedRealtime = SystemClock.elapsedRealtime();
            final long beforeStartUptimeMillis = SystemClock.uptimeMillis();

            sContext.sendBroadcast(intent);

            final Message m = receiver.waitForNextMessage();

            // Check the start times.
            assertThat(m.startRequestedElapsedRealtime).isAtLeast(beforeStartElapsedRealtime);
            assertThat(m.startElapsedRealtime).isAtLeast(m.startRequestedElapsedRealtime);

            assertThat(m.startRequestedUptimeMillis).isAtLeast(beforeStartUptimeMillis);
            assertThat(m.startUptimeMillis).isAtLeast(m.startRequestedUptimeMillis);

            // Check the process name.
            assertThat(m.processName).isEqualTo(expectedProcessName);

            // There may be more message, if the process has a custom app class, but ignore that.
        }
    }

    /**
     * Test for:
     * {@link Process#getStartElapsedRealtime()}
     * {@link Process#getStartUptimeMillis()}
     * {@link Process#getStartRequestedElapsedRealtime()}
     * {@link Process#getStartRequestedUptimeMillis()}
     */
    @Test
    public void testStartTime() throws Exception {
        stopAllHelperApps();

        // Main process.
        checkStartTime(HELPER1_RECEIVER0, PACKAGE_HELPER1);

        // Sub process.
        checkStartTime(HELPER1_RECEIVER1, PACKAGE_HELPER1 + ":sub1");
    }

    /**
     * Test for:
     * {@link Process#getStartElapsedRealtime()}
     * {@link Process#getStartUptimeMillis()}
     * {@link Process#getStartRequestedElapsedRealtime()}
     * {@link Process#getStartRequestedUptimeMillis()}
     *
     * but for a shared process.
     */
    @Test
    public void testStartTime_sharedProcess() throws Exception {
        stopAllHelperApps();

        try (Receiver<Message> receiver = new Receiver<>(sContext, Consts.TAG)) {

            // Bring up the first package on the same process.
            final Intent intent = new Intent(Consts.ACTION_SEND_BACK_START_TIME)
                    .setComponent(Consts.HELPER3_RECEIVER0)
                    .setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

            final long beforeStartElapsedRealtime = SystemClock.elapsedRealtime();
            final long beforeStartUptimeMillis = SystemClock.uptimeMillis();

            sContext.sendBroadcast(intent);

            final Message m = receiver.waitForNextMessage();

            // Check the start times.

            assertThat(m.startRequestedElapsedRealtime).isAtLeast(beforeStartElapsedRealtime);
            assertThat(m.startElapsedRealtime).isAtLeast(m.startRequestedElapsedRealtime);

            assertThat(m.startRequestedUptimeMillis).isAtLeast(beforeStartUptimeMillis);
            assertThat(m.startUptimeMillis).isAtLeast(m.startRequestedUptimeMillis);

            // Check the process name.
            assertThat(m.processName).isEqualTo(HELPER_SHARED_PROCESS_NAME);

            // Bring up the first package on the same process.
            // The start request time should still be the same as the above result.
            final Intent intent2 = new Intent(Consts.ACTION_SEND_BACK_START_TIME)
                    .setComponent(Consts.HELPER4_RECEIVER0)
                    .setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

            sContext.sendBroadcast(intent);

            final Message m2 = receiver.waitForNextMessage();

            assertThat(m2.startRequestedElapsedRealtime)
                    .isEqualTo(m.startRequestedElapsedRealtime);
            assertThat(m2.startRequestedUptimeMillis)
                    .isEqualTo(m.startRequestedUptimeMillis);

            assertThat(m2.processName).isEqualTo(HELPER_SHARED_PROCESS_NAME);

            receiver.ensureNoMoreMessages();
        }
    }

    private void checkApplicationClass(ComponentName receiverComponent,
            @NonNull String expectedPackageName, @NonNull String expectedProcessName,
            @Nullable String expectedApplicationClassName) throws Exception {

        // Start the target process by sending a receiver, and get back the results
        // from the target APIs.
        try (Receiver<Message> receiver = new Receiver<>(sContext, Consts.TAG)) {

            // Start the first process.
            Intent intent = new Intent(Consts.ACTION_SEND_BACK_START_TIME)
                    .setComponent(receiverComponent)
                    .setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

            sContext.sendBroadcast(intent);

            // If the process has a custom application class, then the first message should be from
            // the application class.
            if (expectedApplicationClassName != null) {
                final Message m = receiver.waitForNextMessage();
                Log.i(Consts.TAG, "Message (which is supposed to be from the application class): "
                        + m);

                assertThat(m.packageName).isEqualTo(expectedPackageName);
                assertThat(m.processName).isEqualTo(expectedProcessName);
                assertThat(m.applicationClassName).isEqualTo(expectedApplicationClassName);
            }

            // Then there should be a message from the receiver.
            final Message m = receiver.waitForNextMessage();
            Log.i(Consts.TAG, "Message (which is supposed to be from the receiver): " + m);

            assertThat(m.packageName).isEqualTo(expectedPackageName);
            assertThat(m.processName).isEqualTo(expectedProcessName);

            if (expectedApplicationClassName != null) {
                assertThat(m.applicationContextClassName).isEqualTo(expectedApplicationClassName);
            } else {
                // No custom app class, so the default app class should be used.
                assertThat(m.applicationContextClassName)
                        .isEqualTo(android.app.Application.class.getCanonicalName());
            }

            receiver.ensureNoMoreMessages();
        }
    }

    /**
     * Make sure the correct app class is instantiated in the app processes.
     */
    @Test
    public void testApplicationClass() throws Exception {
        stopAllHelperApps();

        // Each receiver in each helper package runs on different processes, which may or may
        // not have a custom application class.
        checkApplicationClass(HELPER1_RECEIVER0, PACKAGE_HELPER1, PACKAGE_HELPER1,
                null);
        checkApplicationClass(HELPER1_RECEIVER1, PACKAGE_HELPER1, PACKAGE_HELPER1 + ":sub1",
                "android.os.cts.process.helper.Application1");
        checkApplicationClass(HELPER1_RECEIVER2, PACKAGE_HELPER1, PACKAGE_HELPER1 + ":sub2",
                null);

        checkApplicationClass(HELPER2_RECEIVER0, PACKAGE_HELPER2, PACKAGE_HELPER2,
                "android.os.cts.process.helper.Application1");
        checkApplicationClass(HELPER2_RECEIVER1, PACKAGE_HELPER2, PACKAGE_HELPER2 + ":sub1",
                "android.os.cts.process.helper.Application1b");
        checkApplicationClass(HELPER2_RECEIVER2, PACKAGE_HELPER2, PACKAGE_HELPER2 + ":sub2",
                "android.os.cts.process.helper.Application2b");

        checkApplicationClass(HELPER3_RECEIVER0, PACKAGE_HELPER3,
                "android.os.cts.process.helper.shared_process",
                null);
        checkApplicationClass(HELPER3_RECEIVER1, PACKAGE_HELPER3, PACKAGE_HELPER3 + ":sub1",
                "android.os.cts.process.helper.Application1c");
        checkApplicationClass(HELPER3_RECEIVER2, PACKAGE_HELPER3, PACKAGE_HELPER3 + ":sub2",
                null);
        checkApplicationClass(HELPER3_RECEIVER3, PACKAGE_HELPER3,
                "android.os.cts.process.helper.shared.sub3",
                "android.os.cts.process.helper.Application3");

        checkApplicationClass(HELPER4_RECEIVER0, PACKAGE_HELPER4,
                "android.os.cts.process.helper.shared_process",
                "android.os.cts.process.helper.Application1");
        checkApplicationClass(HELPER4_RECEIVER1, PACKAGE_HELPER4, PACKAGE_HELPER4 + ":sub1",
                "android.os.cts.process.helper.Application1");
        checkApplicationClass(HELPER4_RECEIVER2, PACKAGE_HELPER4, PACKAGE_HELPER4 + ":sub2",
                "android.os.cts.process.helper.Application2");
        checkApplicationClass(HELPER4_RECEIVER3, PACKAGE_HELPER4,
                "android.os.cts.process.helper.shared.sub3",
                "android.os.cts.process.helper.Application3b");
    }

    /**
     * This doesn't do any assertions, but just dump the ApplicationInfo's for the helper APKs
     * on logcat, so if some of the tests fail, we can look at the log and verify the
     * ApplicationInfo is correct.
     *
     * (`dumpsys package` doesn't have a way to dump ApplicationInfo at the moment.)
     */
    @Test
    public void dumpApplicationInfo() throws Exception {
        LogPrinter pw = new LogPrinter(Log.VERBOSE, Consts.TAG);
        dumpApplicationInfo(pw, PACKAGE_HELPER1);
        dumpApplicationInfo(pw, Consts.PACKAGE_HELPER2);
        dumpApplicationInfo(pw, Consts.PACKAGE_HELPER3);
        dumpApplicationInfo(pw, Consts.PACKAGE_HELPER4);
    }

    private void dumpApplicationInfo(Printer pw, String packageName) throws Exception {
        ApplicationInfo ai = sContext.getPackageManager().getApplicationInfo(packageName, 0);
        pw.println("Dumping " + packageName);
        ai.dump(pw, "    ");
    }
}
