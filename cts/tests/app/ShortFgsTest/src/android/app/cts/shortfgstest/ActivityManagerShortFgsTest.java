/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.app.cts.shortfgstest;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.ACTIVITY;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.BOUND_SERVICE_B;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.FGS0;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.FGS1;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.FGS2;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.FGS_B_0;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.HELPER_PACKAGE;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.HELPER_PACKAGE2;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.flattenComponentName;
import static android.app.nano.AppProtoEnums.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.nano.AppProtoEnums.PROCESS_STATE_LAST_ACTIVITY;
import static android.app.nano.AppProtoEnums.PROCESS_STATE_TOP;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static com.google.common.truth.Truth.assertThat;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Service;
import android.app.StartForegroundCalledOnStoppedServiceException;
import android.app.cts.shortfgstest.DumpProtoUtils.ProcStateInfo;
import android.app.cts.shortfgstesthelper.ShortFgsHelper;
import android.app.cts.shortfgstesthelper.ShortFgsMessage;
import android.app.cts.shortfgstesthelper.ShortFgsMessageReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerExemptionManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.Suppress;

import com.android.compatibility.common.util.AnrMonitor;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.server.am.nano.ServiceRecordProto;
import com.android.server.am.nano.ServiceRecordProto.ShortFgsInfo;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for "SHORT SERVICE" foreground services.
 */
@Presubmit
public class ActivityManagerShortFgsTest {
    protected static final Context sContext = ShortFgsHelper.sContext;

    public static DeviceConfigStateHelper sDeviceConfig;

    /**
     * The timeout duration documented on the
     * {@link android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_SHORT_SERVICE} javadoc.
     */
    public static final long SHORT_FGS_PUBLIC_TIMEOUT = 3 * 60 * 1000;

    public static final long SHORT_FGS_MINIMUM_PROCSTATE_DEMOTION_GRACE_PERIOD = 5 * 1000;
    public static final long SHORT_FGS_MINIMUM_ANR_GRACE_PERIOD = 10 * 1000;

    public static final long WAIT_TIMEOUT = 10_000;

    /**
     * Timeout for "short" FGS used throughout this test. It's shorter than the default value to
     * speed up the test.
     */
    public static final long SHORTENED_TIMEOUT = 5_000;

    /**
     * After {@link #SHORTENED_TIMEOUT} + {@link #SHORTENED_PROCSTATE_GRACE_PERIOD} ms, the
     * procstate and oom-adjustment of the short-fgs will drop.
     */
    public static final long SHORTENED_PROCSTATE_GRACE_PERIOD = 5_000;

    /**
     * After {@link #SHORTENED_TIMEOUT} + {@link #SHORTENED_PROCSTATE_GRACE_PERIOD} + {@link
     * #EXTENDED_ANR_GRACE_PERIOD} ms, the app will be declared an ANR.
     * <p>
     * We don't want to trigger an ANR accidentally on a slow device, so we use a large value here.
     * <p>
     * (We set a smaller value when we test the ANR behavior)
     */
    public static final long EXTENDED_ANR_GRACE_PERIOD = 24 * 60 * 60 * 1000;

    /**
     * This is the timeout between Context.startForegroundService() and Service.startForeground().
     * Within this duration, the app is temp-allowlisted, so any FGS could be started.
     * This will affect some of the tests, so we shorten this too.
     *
     * Here, we use the same value as SHORTENED_TIMEOUT.
     */
    public static final long SHORTENED_START_SERVICE_TIMEOUT = 5_000;

    /***
     * TOP-started FGS will get oom-adjustment boosted within this period.
     */
    public static final long TOP_TO_FGS_GRACE_PERIOD = 5_000;

    /**
     * We keep track of the test start time and finish time with them. Helper will use them,
     * via {@link ShortFgsHelper#getCurrentTestInfo()}, to detect stale messages.
     */
    private static volatile long sLastTestStartUptime;
    private static volatile long sLastTestEndUptime;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Log.d(TAG, "setUpClass() started");

        sDeviceConfig = new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER);
        ShellUtils.runShellCommand("cmd device_config set_sync_disabled_for_tests until_reboot");

        Log.d(TAG, "setUpClass() done");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        Log.d(TAG, "tearDownClass() started");
        sDeviceConfig.close();
        Log.d(TAG, "tearDownClass() done");
    }

    @Before
    public void setUp() throws Exception {
        sLastTestStartUptime = 0;
        sLastTestEndUptime = 0;

        Log.d(TAG, "setUp() started");

        updateDeviceConfig("top_to_fgs_grace_duration", TOP_TO_FGS_GRACE_PERIOD,
                /* verify= */ true);

        updateDeviceConfig("short_fgs_timeout_duration", SHORTENED_TIMEOUT, /* verify= */ false);
        updateDeviceConfig("short_fgs_proc_state_extra_wait_duration",
                SHORTENED_PROCSTATE_GRACE_PERIOD, /* verify= */ false);
        updateDeviceConfig("short_fgs_anr_extra_wait_duration",
                SHORTENED_PROCSTATE_GRACE_PERIOD + EXTENDED_ANR_GRACE_PERIOD, /* verify= */ false);

        // Only verify the last change. (Skip for the other ones to speed up the test)
        updateDeviceConfig("service_start_foreground_timeout_ms",
                SHORTENED_START_SERVICE_TIMEOUT, /* verify= */ true);

        forceStopHelperApps();

        // Drop any pending messages
        CallProvider.clearMessageQueue();

        sLastTestStartUptime = SystemClock.uptimeMillis();

        Log.d(TAG, "setUp() done");
    }

    @After
    public void tearDown() throws Exception {
        Log.d(TAG, "tearDown() started");

        forceStopHelperApps();

        Log.d(TAG, "tearDown() done");
    }

    private static void updateDeviceConfig(String key, long value) throws Exception {
        updateDeviceConfig(key, value, /* verify= */ true);
    }

    private static void updateDeviceConfig(String key, long value, boolean verify)
            throws Exception {
        Log.d(TAG, "updateDeviceConfig: setting " + key + " to " + value);
        sDeviceConfig.set(key, "" + value);

        if (verify) {
            waitUntil("`dumpsys activity settings` didn't update", () -> {
                final String dumpsys = ShellUtils.runShellCommand(
                        "dumpsys activity settings");

                // Look each line, rather than just doing a contains() check, so we can print
                // the current value.
                for (String line : dumpsys.split("\\n", -1)) {
                    if (!line.contains(" " + key + "=")) {
                        continue;
                    }
                    Log.d(TAG, "Current config: " + line);
                    if (line.endsWith("=" + value)) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private static void ensureHelperAppNotRunning() throws Exception {
        // Wait until the process is actually gone.
        // We need it because 1) kill is async and 2) the ack is sent before the kill anyway
        waitUntil("Process still running",
                () -> !DumpProtoUtils.processExists(FGS0.getPackageName()));

    }

    /**
     * Force-stop the helper app. It'll also remove the test app from temp-allowlist.
     */
    private static void forceStopHelperApps() throws Exception {
        SystemUtil.runShellCommand("am force-stop " + HELPER_PACKAGE);
        untempAllowlistPackage(HELPER_PACKAGE);

        SystemUtil.runShellCommand("am force-stop " + HELPER_PACKAGE2);
        untempAllowlistPackage(HELPER_PACKAGE2);

        ensureHelperAppNotRunning();
    }

    /**
     * Send a "kill self" command to the helper, and wait for the process to go away.
     *
     * This is needed when "force-stop"'s side effects are not ideal. (e.g. force-stop will
     * prevent STICKY FGS from restarting)
     */
    private static void killHelperApp() throws Exception {
        // Tell the helper to kill itself.
        ShortFgsMessageReceiver.sendMessage(newMessage().setDoKillProcess(true));

        waitForAckMessage();

        ensureHelperAppNotRunning();
    }

    /**
     * Called by the helper app, via {@link CallProvider}.
     */
    public static ShortFgsMessage getTestInfo() {
        return newMessage()
                .setLastTestStartUptime(sLastTestStartUptime)
                .setLastTestEndUptime(sLastTestEndUptime);
    }

    private static ShortFgsMessage newMessage() {
        return new ShortFgsMessage();
    }

    private static ShortFgsMessage waitForNextMessage() {
        return CallProvider.waitForNextMessage(WAIT_TIMEOUT);
    }

    public static void waitForAckMessage() {
        CallProvider.waitForAckMessage(WAIT_TIMEOUT);
    }

    public static ShortFgsMessage waitForException() {
        ShortFgsMessage m = waitForNextMessage();
        if (m.getActualExceptionClasss() != null) {
            return m;
        }
        Assert.fail("Expected an exception message, but received: " + m);
        return m;
    }

    public static void startForegroundService(ComponentName cn, int fgsTypes) {
        startForegroundService(cn, fgsTypes, Service.START_NOT_STICKY);
    }

    public static void startForegroundService(ComponentName cn, int fgsTypes,
            int startCommandResult) {
        Log.i(TAG, "startForegroundService: Starting " + cn
                + " types=0x" + Integer.toHexString(fgsTypes));
        ShortFgsMessage startMessage = newMessage()
                .setComponentName(cn)
                .setDoCallStartForeground(true)
                .setFgsType(fgsTypes)
                .setStartCommandResult(startCommandResult);

        // Actual intent to start the FGS.
        Intent i = new Intent().setComponent(cn);
        ShortFgsHelper.setMessage(i, startMessage);

        sContext.startForegroundService(i);
    }

    public static ShortFgsMessage waitForMethodCall(ComponentName cn, String methodName) {
        Log.i(TAG, "waitForMethodCall: waiting for " + methodName + " from " + cn);
        ShortFgsMessage m = waitForNextMessage();

        String expected = flattenComponentName(cn) + "." + methodName;
        if (m.getMethodName() == null) {
            Assert.fail("Waited for " + expected + " but received: " + m);
        }
        assertThat(
                flattenComponentName(m.getComponentName()) + "." + m.getMethodName())
                .isEqualTo(expected);
        return m;
    }

    public static void assertHelperPackageProcState(int procState, int oomAdjustment) {
        final ProcStateInfo expected = new ProcStateInfo();
        expected.mProcState = procState;
        expected.mOomAdjustment = oomAdjustment;

        final ProcStateInfo actual = DumpProtoUtils.getProcessProcState(HELPER_PACKAGE);

        assertThat(actual.toString()).isEqualTo(expected.toString());
    }

    public static void assertHelperPackageIsCached() {
        final ProcStateInfo actual = DumpProtoUtils.getProcessProcState(HELPER_PACKAGE);

        assertThat(actual.mProcState).isAtMost(PROCESS_STATE_LAST_ACTIVITY);
    }

    /**
     * Make sure a specified FGS is running.
     */
    public static ServiceRecordProto assertFgsRunning(ComponentName cn) {
        final ServiceRecordProto srp = DumpProtoUtils.findServiceRecord(cn);
        if (srp == null) {
            Assert.fail("Service " + cn + " is not running");
        }
        if (srp.foreground == null) {
            Assert.fail("Service " + cn + " is running, but is not an FGS");
        }

        return srp;
    }

    /**
     * Make sure a service is running. (it doesn't have to be an FGS.)
     */
    public static ServiceRecordProto assertServiceRunning(ComponentName cn) {
        final ServiceRecordProto srp = DumpProtoUtils.findServiceRecord(cn);
        if (srp == null) {
            Assert.fail("Service " + cn + " is not running");
        }

        return srp;
    }

    public static void assertServiceNotRunning(ComponentName cn) {
        final ServiceRecordProto srp = DumpProtoUtils.findServiceRecord(cn);
        assertThat(srp).isNull();
    }

    public static void tempAllowlistPackage(String packageName, int durationMillis) {
        final PowerExemptionManager pem = sContext.getSystemService(PowerExemptionManager.class);

        SystemUtil.runWithShellPermissionIdentity(
                () -> pem.addToTemporaryAllowList(packageName, PowerExemptionManager.REASON_OTHER,
                        TAG, durationMillis));
    }

    public static void untempAllowlistPackage(String packageName) {
        SystemUtil.runShellCommand("cmd deviceidle tempwhitelist -r "
                + " -u " + UserHandle.getUserId(android.os.Process.myUid())
                + " " + packageName);
    }

    /**
     * Basic test: Start SHORT_SERVICE FGS and stop it, and make sure the nothing throws, and
     * the service's onDestroy() gets called.
     * - Start FGS0 (in the helper app) with the SHORT_TYPE FGS
     * - Stop it with Context.stopService().
     * - Wait until the timeout time and make sure the timeout callback won't be called.
     */
    @Test
    public void testStartStop_stopService() throws Exception {
        // Start FGS0.
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

        // Wait for the method name message.
        waitForMethodCall(FGS0, "onStartCommand");

        assertFgsRunning(FGS0);

        // Short service's procstat is IMP-FG, and when it starts. It's not started from TOP,
        // so the oom-adj will be 225 + 1.
        // If it was started from TOP, it'd be 50 + 1.
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 225 + 1);

        // Stop the service.
        sContext.stopService(new Intent().setComponent(FGS0));

        waitForMethodCall(FGS0, "onDestroy");

        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        Thread.sleep(SHORTENED_TIMEOUT + 5000);

        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Start SHORT_SERVICE FGS and stop it, and make sure the nothing throws, and
     * the service's onDestroy() gets called.
     * - Start FGS0 (in the helper app) with the SHORT_TYPE FGS
     * - Stop it with Service.stopSelf().
     * - Wait until the timeout time and make sure the timeout callback won't be called.
     */
    @Test
    public void testStartStop_stopSelf() throws Exception {
        // Start FGS0.
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

        // Wait for the method name message.
        waitForMethodCall(FGS0, "onStartCommand");

        assertFgsRunning(FGS0);

        // Short service's procstat is IMP-FG, and when it starts. It's not started from TOP,
        // so the oom-adj will be 225 + 1.
        // If it was started from TOP, it'd be 50 + 1.
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 225 + 1);

        // Stop the service, using Service.stopSelf().
        ShortFgsMessageReceiver.sendMessage(
                newMessage().setDoCallStopSelf(true).setComponentName(FGS0));

        // Wait for "ACK".
        waitForAckMessage();

        // Service should be destroyed.
        waitForMethodCall(FGS0, "onDestroy");

        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        Thread.sleep(SHORTENED_TIMEOUT + 5000);

        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Start SHORT_SERVICE FGS, and make sure the timeout callback is called.
     */
    @Test
    @Suppress
    public void testTimeout() throws Exception {
        final long serviceStartTime = SystemClock.uptimeMillis();

        // Start FGS0.
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

        // Wait for the method name message.
        final int startId =
                waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();

        assertFgsRunning(FGS0);

        // Short service's procstat is IMP-FG, and when it starts. It's not started from TOP,
        // so the oom-adj will be 225 + 1.
        // If it was started from TOP, it'd be 50 + 1.
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 225 + 1);

        // Wait for onTimeout()
        Thread.sleep(SHORTENED_TIMEOUT);

        {
            ShortFgsMessage m = waitForMethodCall(FGS0, "onTimeout");

            assertThat(m.getServiceStartId()).isEqualTo(startId);

            // Timeout should happen after SHORTENED_TIMEOUT.
            assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        }

        // At this point, the procstate should still be the same.
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 225 + 1);

        assertFgsRunning(FGS0);

        // Stop the service.
        sContext.stopService(new Intent().setComponent(FGS0));

        waitForMethodCall(FGS0, "onDestroy");

        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        Thread.sleep(SHORTENED_TIMEOUT + 5000);

        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Same as {@link #testTimeout}, but with another "normal" FGS running. The result should
     * be the same.
     */
    @Test
    public void testTimeout_withAnotherFgs() throws Exception {
        final long serviceStartTime = SystemClock.uptimeMillis();

        // Start FGS0.
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

        // Wait for the method name message.
        final int startId =
                waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();
        assertFgsRunning(FGS0);

        // Start FGS1. (which is not a SHORT_SERVICE.)
        startForegroundService(FGS1, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        waitForMethodCall(FGS1, "onStartCommand");
        assertFgsRunning(FGS1);

        // The procstat should be FGS. (not IMP_FG, which is for SHORT_SERVICE.)
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 200);

        // Wait for the timeout, and also wait until the procstate grace period expires.
        Thread.sleep(SHORTENED_TIMEOUT + SHORTENED_PROCSTATE_GRACE_PERIOD);

        {
            ShortFgsMessage m = waitForMethodCall(FGS0, "onTimeout");

            assertThat(m.getServiceStartId()).isEqualTo(startId);

            // Timeout should happen after SHORTENED_TIMEOUT.
            assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        }

        // Because the app has a "normal" FGS, the procstate should still be the same.
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 200);

        assertFgsRunning(FGS1);

        // Stop both services.
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        sContext.stopService(new Intent().setComponent(FGS1));
        waitForMethodCall(FGS1, "onDestroy");
        assertServiceNotRunning(FGS0);

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Start SHORT_SERVICE FGS:
     * - Make sure the timeout callback is called.
     * - If the service still doesn't stop after the grace period, the process should now be cached.
     */
    @Test
    public void testTimeout_procStateDemotion() throws Exception {
        final long serviceStartTime = SystemClock.uptimeMillis();

        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

        // Wait for the method name message.
        final int startId =
                waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();

        assertFgsRunning(FGS0);

        // Wait until the procstate is demoted
        Thread.sleep(SHORTENED_TIMEOUT + SHORTENED_PROCSTATE_GRACE_PERIOD);

        // Timeout callback should still have been made.
        {
            ShortFgsMessage m = waitForMethodCall(FGS0, "onTimeout");
            assertThat(m.getServiceStartId()).isEqualTo(startId);
            assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        }

        // The process should now be CACHED
        assertHelperPackageIsCached();

        // Service should still exist.
        assertFgsRunning(FGS0);

        // Stop the service.
        sContext.stopService(new Intent().setComponent(FGS0));

        waitForMethodCall(FGS0, "onDestroy");

        assertServiceNotRunning(FGS0);

        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)

        CallProvider.ensureNoMoreMessages();
    }

    @Test
    public void testTimeout_cannotBeSticky_startSticky() throws Exception {
        testServiceIsNotSticky(Service.START_STICKY);
    }

    @Test
    public void testTimeout_cannotBeSticky_startStickyCompatibility() throws Exception {
        testServiceIsNotSticky(Service.START_STICKY_COMPATIBILITY);
    }

    @Test
    public void testTimeout_cannotBeSticky_startNotSticky() throws Exception {
        testServiceIsNotSticky(Service.START_NOT_STICKY);
    }

    @Test
    public void testTimeout_cannotBeSticky_startRedeliveryIntent() throws Exception {
        testServiceIsNotSticky(Service.START_REDELIVER_INTENT);
    }

    /**
     * Make sure a SHORT FGS is not sticky. That is, if the process dies, the ServiceRecord
     * should be deleted.
     */
    private void testServiceIsNotSticky(int startCommand) throws Exception {
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, startCommand);

        // Wait for the method name message.
        waitForMethodCall(FGS0, "onStartCommand");

        killHelperApp();

        // The service record should now be deleted.
        assertServiceNotRunning(FGS0);

        // Make sure onTimeout() won't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)

        // Wait for the timeout + extra duration
        Thread.sleep(SHORTENED_TIMEOUT + 5000);

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * This is a test to ensure the assumption of {@link #testServiceIsNotSticky} is correct --
     * that is, even when a process dies, its sticky services should still have ServiceRecords.
     */
    @Test
    public void testRegularFgsCanBeSticky() throws Exception {
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                Service.START_STICKY);

        // Wait for the method name message.
        waitForMethodCall(FGS0, "onStartCommand");

        killHelperApp();

        // Because the service _is_ sticky, the ServiceRecord should still exist.
        assertFgsRunning(FGS0);

        // Stop the service.
        sContext.stopService(new Intent().setComponent(FGS0));

        waitUntil("Service still running",
                () -> DumpProtoUtils.findServiceRecord(FGS0) == null);

        // We ignore any remaining messages.
    }

    /**
     * Calling Service.startForeground(SHORT_SERVICE) should fail, if the service isn't started
     * but just being bound.
     */
    @Test
    public void testCannotMakeShortFgsIfBoundButNotStarted() throws Exception {
        try (ServiceBinder b = ServiceBinder.bind(sContext, FGS0, Context.BIND_AUTO_CREATE)) {

            waitForMethodCall(FGS0, "onBind");


            // Calling Service.startForeground() without starting it.
            // This should fail.
            ShortFgsMessageReceiver.sendMessage(
                    newMessage().setDoCallStartForeground(true).setComponentName(FGS0)
                            .setFgsType(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                            .setExpectedExceptionClass(IllegalStateException.class));

            ShortFgsMessage m = waitForException();

            assertThat(m.getActualExceptionClasss())
                    .isEqualTo(StartForegroundCalledOnStoppedServiceException.class.getName());

            assertThat(m.getActualExceptionMessage())
                    .contains("called on a service that's not started.");
        }

        waitForMethodCall(FGS0, "onDestroy");
        CallProvider.ensureNoMoreMessages();
    }

    /**
     * If the service is bound and also started, this can be a short FGS.
     */
    @Test
    public void testCanMakeShortFgsIfBoundAndStarted() throws Exception {
        try (ServiceBinder b = ServiceBinder.bind(sContext, FGS0,
                Context.BIND_AUTO_CREATE)) {

            waitForMethodCall(FGS0, "onBind");

            // Start FGS0.
            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            // Wait for the method name message.
            waitForMethodCall(FGS0, "onStartCommand");

            assertFgsRunning(FGS0);
        }

        // After unbinding, the service should still running.
        assertFgsRunning(FGS0);

        // The procstate should be of the SHORT_FGS.
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 225 + 1);

        // Stop the service.
        sContext.stopService(new Intent().setComponent(FGS0));

        waitForMethodCall(FGS0, "onDestroy");

        assertServiceNotRunning(FGS0);

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Make sure another FGS cannot be started from a SHORT_SERVICE.
     */
    @Test
    public void testCannotStartAnotherFgsFromShortService() throws Exception {

        // Here, we want the SHORT_SERVICE timeout to be significantly larger than the
        // startForeground() timeout, because we want to check the state between them.
        updateDeviceConfig("short_fgs_timeout_duration", SHORTENED_START_SERVICE_TIMEOUT + 30_000);

        // Start FGS0.
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 225 + 1);

        // Because of the first Context.startForegroundService() for FGS0, the helper
        // app is temp-allowlisted for this duration, so another
        // Context.startForegroundService() would automatically succeed.
        // We wait until the temp-allowlist expires, so startForegroundService() would
        // fail.
        Thread.sleep(SHORTENED_START_SERVICE_TIMEOUT);

        // Let the helper app call Context.startForegroundService, which should fail.
        ShortFgsMessageReceiver.sendMessage(
                newMessage().setDoCallStartForegroundService(true)
                        .setComponentName(FGS2)
                        .setExpectedExceptionClass(
                                ForegroundServiceStartNotAllowedException.class));

        // It should have failed.
        ShortFgsMessage m = waitForException();
        assertThat(m.getActualExceptionClasss())
                .isEqualTo(ForegroundServiceStartNotAllowedException.class.getName());

        // Stop the service.
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Make sure another FGS *can* be started, if an app has a SHORT_SERVICE FGS and a
     * other kinds of FGS.
     */
    @Test
    public void testCantStartAnotherFgsFromShortServiceAndAnotherFgs() throws Exception {

        // Here, we want the SHORT_SERVICE timeout to be significantly larger than the
        // startForeground() timeout, because we want to check the state between them.
        updateDeviceConfig("short_fgs_timeout_duration", SHORTENED_START_SERVICE_TIMEOUT + 60_000);

        // Start FGS0.
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 225 + 1);

        // Start another FGS, that's not a SHORT_SERVICE.
        startForegroundService(FGS1, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        waitForMethodCall(FGS1, "onStartCommand");
        assertFgsRunning(FGS1);

        // Now at the FGS procstate.
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 200);

        // See testCannotStartAnotherFgsFromShortService() for why we need it.
        Thread.sleep(SHORTENED_START_SERVICE_TIMEOUT);

        // Let the helper app call Context.startForegroundService, which should succeed.
        ShortFgsMessageReceiver.sendMessage(
                newMessage().setDoCallStartForegroundService(true)
                        .setComponentName(FGS2)
                        .setDoCallStartForeground(true)
                        .setFgsType(FOREGROUND_SERVICE_TYPE_SPECIAL_USE));

        waitForAckMessage();

        // FGS2 should now be running too.
        waitForMethodCall(FGS2, "onStartCommand");
        assertFgsRunning(FGS2);
    }

    /**
     * Make sure, when a short service is started from UI, the OOM-adjustment is boosted.
     */
    @Test
    public void testTopStartedShortService() throws Exception {

        // Here, we want the SHORT_SERVICE timeout to be significantly larger than
        // the TOP-TO-FGS grace period.
        updateDeviceConfig("short_fgs_timeout_duration", (TOP_TO_FGS_GRACE_PERIOD + 60_000));

        // Start an activity. Procstate should be TOP.
        sContext.startActivity(
                new Intent()
                        .setComponent(ACTIVITY)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForMethodCall(ACTIVITY, "onCreate");

        // Wait until the procstate becomes TOP.
        waitUntil("Procstate is not TOP",
                () -> DumpProtoUtils.getProcessProcState(HELPER_PACKAGE).mProcState
                        == PROCESS_STATE_TOP);
        assertHelperPackageProcState(PROCESS_STATE_TOP, 0);

        // Start a short-fgs. Procstate should still be TOP.
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        assertHelperPackageProcState(PROCESS_STATE_TOP, 0);

        // Close the activity.
        ShortFgsMessageReceiver.sendMessage(newMessage().setDoFinishActivity(true));
        waitForAckMessage();

        waitForMethodCall(ACTIVITY, "onDestroy");

        // The service is still running, and the procstate should now be FGS, but
        // the OOM-adjustment should be boosted.
        assertFgsRunning(FGS0);
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 51);

        // Wait until the grace period finishes. Now, the oom-adjustment should be lower.
        Thread.sleep(TOP_TO_FGS_GRACE_PERIOD + 1000 /* extra time, just in case*/);

        // The system doesn't actually run a timer for this grace period expiration.
        // So, instead, we let helper call startForeground(SHORT_SERVICE), which should succeed
        // and causes an oom-adjuster run.
        ShortFgsMessageReceiver.sendMessage(newMessage()
                .setComponentName(FGS0)
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE));
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 225 + 1);
    }

    /**
     * Make sure, if a short service doesn't stop, the app gets ANRed.
     */
    @Test
    public void testAnr() throws Exception {
        final int anrExtraTimeout = 10_000;

        updateDeviceConfig("short_fgs_proc_state_extra_wait_duration", 0, /* verify= */ false);
        updateDeviceConfig("short_fgs_anr_extra_wait_duration",
                anrExtraTimeout, /* verify= */ true);

        try (AnrMonitor monitor = AnrMonitor.start(InstrumentationRegistry.getInstrumentation(),
                HELPER_PACKAGE)) {
            final long startTime = SystemClock.uptimeMillis();

            // Start FGS0.
            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            // Wait for the method name message.
            waitForMethodCall(FGS0, "onStartCommand");

            assertFgsRunning(FGS0);

            // Wait for the timeout + extra duration
            Thread.sleep(SHORTENED_TIMEOUT + anrExtraTimeout + 2000);

            // Wait for the ANR.
            final long anrTime = monitor.waitForAnrAndReturnUptime(60_000);

            // The ANR time should be after the timeout + the ANR grace period.
            assertThat(anrTime).isAtLeast(startTime + SHORTENED_TIMEOUT + anrExtraTimeout);
        }
    }


    /**
     * Start a SHORT_SERVICE, using Context.startService, instead of Context.startForegroundService.
     *
     * Then make sure onTimeout() is called.
     */
    @Test
    public void testStartFromStartService() throws Exception {

        // The helper app is in the background and can't start a BG service, so we need to put
        // it in the temp-allowlsit first.
        tempAllowlistPackage(HELPER_PACKAGE, 5000);

        final long serviceStartTime = SystemClock.uptimeMillis();

        ShortFgsMessageReceiver.sendMessage(newMessage()
                .setComponentName(FGS0)
                .setDoCallStartService(true)
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                .setStartCommandResult(Service.START_NOT_STICKY));

        waitForAckMessage();

        // Wait for the method name message.
        final int startId =
                waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();

        assertFgsRunning(FGS0);

        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 225 + 1);


        // Wait for onTimeout()
        Thread.sleep(SHORTENED_TIMEOUT);

        {
            ShortFgsMessage m = waitForMethodCall(FGS0, "onTimeout");

            assertThat(m.getServiceStartId()).isEqualTo(startId);

            // Timeout should happen after SHORTENED_TIMEOUT.
            assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        }

        // At this point, the procstate should still be the same.
        assertHelperPackageProcState(PROCESS_STATE_FOREGROUND_SERVICE, 225 + 1);

        assertFgsRunning(FGS0);

        // Stop the service.
        sContext.stopService(new Intent().setComponent(FGS0));

        waitForMethodCall(FGS0, "onDestroy");

        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        Thread.sleep(SHORTENED_TIMEOUT + 5000);

        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)

        CallProvider.ensureNoMoreMessages();
    }

    private static void checkServiceTimeoutValues(ServiceRecordProto sr,
            long preServiceStartTime, // A time before the service starts.
            long postServiceStartTime, // A time after the service starts.
            long timeoutTime, // Expected timeout duration
            long demoteTime, // Expected "demotion" extra time (in addition to the timeout duration)
            long anrTime // Expected ANR extra time (in addition to the timeout duration)
    ) throws Exception {
        ShortFgsInfo sfi = sr.shortFgsInfo;
        assertThat(sfi).isNotNull();
        assertThat(sfi.startTime).isAtLeast(preServiceStartTime);
        assertThat(sfi.startTime).isAtMost(postServiceStartTime);

        assertThat(sfi.timeoutTime).isEqualTo(sfi.startTime + timeoutTime);
        assertThat(sfi.procStateDemoteTime).isEqualTo(sfi.startTime + timeoutTime + demoteTime);
        assertThat(sfi.anrTime).isEqualTo(sfi.startTime + timeoutTime + anrTime);
    }

    /**
     * Check the various timestamps (obtained from the ServiceRecord) related to SHORT_FGS
     */
    @Test
    public void testTimeoutDetails() throws Exception {

        final long serviceStartTime = SystemClock.uptimeMillis();

        // Start FGS0.
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

        // Wait for the method name message.
        final int startId =
                waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();

        final long serviceStartDoneTime = SystemClock.uptimeMillis();

        {
            ServiceRecordProto sr = assertFgsRunning(FGS0);

            checkServiceTimeoutValues(sr,
                    serviceStartTime,
                    serviceStartDoneTime,
                    SHORTENED_TIMEOUT,
                    SHORTENED_PROCSTATE_GRACE_PERIOD,
                    SHORTENED_PROCSTATE_GRACE_PERIOD + EXTENDED_ANR_GRACE_PERIOD);
        }
    }

    /**
     * Change the FGS type from SHORT_SERVICE to another type.
     */
    @Test
    public void testTypeChange_fromShort_toAnother() throws Exception {
        final long serviceStartTime = SystemClock.uptimeMillis();

        // Temp-allowlist the helper app for the entire test, so the app can call startForeground()
        // any time.
        tempAllowlistPackage(HELPER_PACKAGE, 10 * 60 * 1000);

        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

        waitForMethodCall(FGS0, "onStartCommand");

        final long serviceStartDoneTime = SystemClock.uptimeMillis();

        // Check the start time and the FGS type.
        {
            ServiceRecordProto sr = assertFgsRunning(FGS0);

            assertThat(sr.foreground.foregroundServiceType)
                    .isEqualTo(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            checkServiceTimeoutValues(sr,
                    serviceStartTime,
                    serviceStartDoneTime,
                    SHORTENED_TIMEOUT,
                    SHORTENED_PROCSTATE_GRACE_PERIOD,
                    SHORTENED_PROCSTATE_GRACE_PERIOD + EXTENDED_ANR_GRACE_PERIOD);
        }

        // Change the FGS type to SPECIAL_USE.
        Thread.sleep(1000);

        ShortFgsMessageReceiver.sendMessage(newMessage()
                .setComponentName(FGS0)
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_SPECIAL_USE));

        waitForAckMessage();

        // The FGS type should be different now.
        {
            ServiceRecordProto sr = assertFgsRunning(FGS0);

            assertThat(sr.foreground.foregroundServiceType)
                    .isEqualTo(FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        }

        // Change the FGS type back to SHORT_SERVICE again.
        Thread.sleep(1000);

        long typeChangeStartTime = SystemClock.uptimeMillis();
        // Change the FGS type.
        ShortFgsMessageReceiver.sendMessage(newMessage()
                .setComponentName(FGS0)
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE));

        waitForAckMessage();
        long typeChangeDoneTime = SystemClock.uptimeMillis();

        // Check the start time and the FGS type.
        {
            ServiceRecordProto sr = assertFgsRunning(FGS0);
            assertThat(sr.foreground.foregroundServiceType)
                    .isEqualTo(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            checkServiceTimeoutValues(sr,
                    typeChangeStartTime,
                    typeChangeDoneTime,
                    SHORTENED_TIMEOUT,
                    SHORTENED_PROCSTATE_GRACE_PERIOD,
                    SHORTENED_PROCSTATE_GRACE_PERIOD + EXTENDED_ANR_GRACE_PERIOD);
        }
    }

    /**
     * Make sure Service.startForeground()s calls extend the timeout.
     */
    @Test
    public void testTimeoutExtension() throws Exception {
        final long serviceStartTime = SystemClock.uptimeMillis();

        // Temp-allowlist the helper app for the entire test, so the app can call startForeground()
        // any time.
        tempAllowlistPackage(HELPER_PACKAGE, 10 * 60 * 1000);

        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

        waitForMethodCall(FGS0, "onStartCommand");

        final long serviceStartDoneTime = SystemClock.uptimeMillis();

        // Check the start time and the FGS type.
        {
            ServiceRecordProto sr = assertFgsRunning(FGS0);

            assertThat(sr.foreground.foregroundServiceType)
                    .isEqualTo(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            checkServiceTimeoutValues(sr,
                    serviceStartTime,
                    serviceStartDoneTime,
                    SHORTENED_TIMEOUT,
                    SHORTENED_PROCSTATE_GRACE_PERIOD,
                    SHORTENED_PROCSTATE_GRACE_PERIOD + EXTENDED_ANR_GRACE_PERIOD);
        }

        int notificationId = 10;

        // Call startForeground() and make sure the start time is updated.
        for (int i = 0; i < 3; i++) {
            Thread.sleep(1000);

            long startForegroundStartTime = SystemClock.uptimeMillis();
            // Change the FGS type.
            ShortFgsMessageReceiver.sendMessage(newMessage()
                    .setComponentName(FGS0)
                    .setDoCallStartForeground(true)
                    .setFgsType(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                    .setNotificationId(++notificationId));

            waitForAckMessage();
            long startForegroundDoneTime = SystemClock.uptimeMillis();

            // Check the start time and the FGS type.
            {
                ServiceRecordProto sr = assertFgsRunning(FGS0);

                assertThat(sr.foreground.foregroundServiceType)
                        .isEqualTo(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

                checkServiceTimeoutValues(sr,
                        startForegroundStartTime,
                        startForegroundDoneTime,
                        SHORTENED_TIMEOUT,
                        SHORTENED_PROCSTATE_GRACE_PERIOD,
                        SHORTENED_PROCSTATE_GRACE_PERIOD + EXTENDED_ANR_GRACE_PERIOD);

                // Make sure the notification ID has changed as well, even if the timeout didn't
                // extend.
                assertThat(sr.foreground.id).isEqualTo(notificationId);
            }
        }
    }

    /**
     * Test to make sure:
     * - calling startForeground([other types]) on an already running short-FGS *will* throw.
     * - calling startForeground(SHORT_SERVICE) on an already running short-FGS will _not_ throw.
     */
    @Test
    public void testStartForeground_onTimedOutShortService() throws Exception {

        // Here, we want the SHORT_SERVICE timeout to be significantly larger than the
        // startForeground() timeout, because we want to check the state between them.
        updateDeviceConfig("short_fgs_timeout_duration", SHORTENED_START_SERVICE_TIMEOUT + 30_000);

        // Start FGS0.
        final long serviceStartTime = SystemClock.uptimeMillis();
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        waitForMethodCall(FGS0, "onStartCommand");
        final long serviceStartDoneTime = SystemClock.uptimeMillis();

        assertFgsRunning(FGS0);

        // Check the timeout values.
        {
            ServiceRecordProto sr = assertFgsRunning(FGS0);

            assertThat(sr.foreground.foregroundServiceType)
                    .isEqualTo(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            checkServiceTimeoutValues(sr,
                    serviceStartTime,
                    serviceStartDoneTime,
                    SHORTENED_TIMEOUT + 30_000,
                    SHORTENED_PROCSTATE_GRACE_PERIOD,
                    SHORTENED_PROCSTATE_GRACE_PERIOD + EXTENDED_ANR_GRACE_PERIOD);
        }

        // First, call startForeground(SPECIAL_USE) and make sure it _fails_.

        // We use the same logic astestCannotStartAnotherFgsFromShortService() does here:

        // Because of the first Context.startForegroundService() for FGS0, the helper
        // app is temp-allowlisted for this duration, so another
        // Context.startForegroundService() would automatically succeed.
        // We wait until the temp-allowlist expires, so startForegroundService() would
        // fail.
        Thread.sleep(SHORTENED_START_SERVICE_TIMEOUT);

        // Let the helper app call Context.startForegroundService, which should fail.
        ShortFgsMessageReceiver.sendMessage(
                newMessage()
                        .setDoCallStartForeground(true)
                        .setFgsType(FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        .setComponentName(FGS0)
                        .setExpectedExceptionClass(
                                ForegroundServiceStartNotAllowedException.class));

        // It should have failed.
        ShortFgsMessage m = waitForException();
        assertThat(m.getActualExceptionClasss())
                .isEqualTo(ForegroundServiceStartNotAllowedException.class.getName());

        // Now, do the same thing but with FOREGROUND_SERVICE_TYPE_SHORT_SERVICE.
        // This _should_ succeed.
        ShortFgsMessageReceiver.sendMessage(
                newMessage()
                        .setDoCallStartForeground(true)
                        .setFgsType(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                        .setComponentName(FGS0));

        waitForAckMessage();

        // But, it shouldn't have changed the timeout.
        {
            ServiceRecordProto sr = assertFgsRunning(FGS0);

            assertThat(sr.foreground.foregroundServiceType)
                    .isEqualTo(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            checkServiceTimeoutValues(sr,
                    serviceStartTime,
                    serviceStartDoneTime,
                    SHORTENED_TIMEOUT + 30_000,
                    SHORTENED_PROCSTATE_GRACE_PERIOD,
                    SHORTENED_PROCSTATE_GRACE_PERIOD + EXTENDED_ANR_GRACE_PERIOD);
        }
    }

    /**
     * Common part for tests that involve bindign from helper to helper2.
     * This will:
     * - Start FGS0 as a foreground service in helper, using fgsType.
     * - Let helper bind to helper 2.
     */
    private void setupForBindingTest(int fgsType) throws Exception {
        // For this test, timeout is not needed.
        updateDeviceConfig("short_fgs_timeout_duration", 60 * 60 * 1000);

        // In this test, we want the helper app to call Context.startForegroundService(). *1
        // To do so, we need to temp-allowlist it.
        // *1 this is because, if this main test app calls Context.startForegroundService(),
        // then it'll put the helper app in a grace period where it could start an FGS,
        // and that grace period would propagate to the second helper app.
        tempAllowlistPackage(HELPER_PACKAGE, 60_0000);

        // Start a SHORT_SERVICE in the helper app, and let it bound to helper2.
        ShortFgsMessageReceiver.sendMessage(
                newMessage().setDoCallStartForegroundService(true)
                        .setComponentName(FGS0)
                        .setDoCallStartForeground(true)
                        .setFgsType(fgsType));
        waitForAckMessage();
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        // Let helper bind to helper2.
        ShortFgsMessageReceiver.sendMessage(
                newMessage()
                        .setDoCallBindService(true)
                        .setComponentName(BOUND_SERVICE_B));
        waitForAckMessage();
        waitForMethodCall(BOUND_SERVICE_B, "onBind");

        // Now, the bound service (in helper2) should be running.
        assertServiceRunning(BOUND_SERVICE_B);

        // Un-tempallowlist the helper app. (so it's no longer BFSL-allowed.)
        untempAllowlistPackage(HELPER_PACKAGE);

        // Adding a small sleep here, which will make it a little easy to debug when the following
        // checks fail...
        Thread.sleep(1000);
    }

    /**
     * Make sure, when an app bound (in helper2)  by a SHORT_SERVICE (in main helper) is
     * BFSL-denied.
     *
     * (i.e. ensure negative PROCESS_CAPABILITY_BFSL is propagated.)
     */
    @Test
    public void testBoundByShortService() throws Exception {
        setupForBindingTest(FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

        // Now, verify that helper2 can't call Context.startForegroundService().
        ShortFgsMessageReceiver.sendMessage(HELPER_PACKAGE2,
                newMessage().setDoCallStartForegroundService(true)
                        .setComponentName(FGS_B_0)
                        .setExpectedExceptionClass(
                                ForegroundServiceStartNotAllowedException.class));

        // It should have failed.
        ShortFgsMessage m = waitForException();
        assertThat(m.getActualExceptionClasss())
                .isEqualTo(ForegroundServiceStartNotAllowedException.class.getName());
    }

    /**
     * Same as {@ink #testBoundByShortService}, except it uses a non-short-FGS type,
     * so helper2 should be allowed BFSL.
     *
     * (i.e. ensure positive PROCESS_CAPABILITY_BFSL is propagated.)
     */
    @Test
    public void testBoundByNonShortService() throws Exception {
        setupForBindingTest(FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        // Make sure helper2 can start an FGS.
        ShortFgsMessageReceiver.sendMessage(HELPER_PACKAGE2,
                newMessage().setDoCallStartForegroundService(true)
                        .setComponentName(FGS_B_0)
                        .setDoCallStartForeground(true)
                        .setFgsType(FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        .setExpectedExceptionClass(
                                ForegroundServiceStartNotAllowedException.class));

        waitForAckMessage();
        waitForMethodCall(FGS_B_0, "onStartCommand");
        assertFgsRunning(FGS_B_0);
    }
}
