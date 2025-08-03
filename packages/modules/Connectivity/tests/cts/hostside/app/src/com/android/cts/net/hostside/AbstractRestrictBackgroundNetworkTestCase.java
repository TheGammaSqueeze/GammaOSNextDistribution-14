/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.net.hostside;

import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.app.job.JobScheduler.RESULT_SUCCESS;
import static android.net.ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED;
import static android.os.BatteryManager.BATTERY_PLUGGED_ANY;

import static com.android.cts.net.arguments.InstrumentationArguments.ARG_WAIVE_BIND_PRIORITY;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.executeShellCommand;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.forceRunJob;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.getConnectivityManager;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.getContext;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.getInstrumentation;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.isAppStandbySupported;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.isBatterySaverSupported;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.isDozeModeSupported;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.restrictBackgroundValueToString;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.setRestrictBackgroundInternal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.provider.DeviceConfig;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.ThrowingRunnable;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Superclass for tests related to background network restrictions.
 */
@RunWith(NetworkPolicyTestRunner.class)
public abstract class AbstractRestrictBackgroundNetworkTestCase {
    public static final String TAG = "RestrictBackgroundNetworkTests";

    protected static final String TEST_PKG = "com.android.cts.net.hostside";
    protected static final String TEST_APP2_PKG = "com.android.cts.net.hostside.app2";
    // TODO(b/321797685): Configure it via device-config once it is available.
    protected static final long PROCESS_STATE_TRANSITION_DELAY_MS = TimeUnit.SECONDS.toMillis(5);

    private static final String TEST_APP2_ACTIVITY_CLASS = TEST_APP2_PKG + ".MyActivity";
    private static final String TEST_APP2_SERVICE_CLASS = TEST_APP2_PKG + ".MyForegroundService";
    private static final String TEST_APP2_JOB_SERVICE_CLASS = TEST_APP2_PKG + ".MyJobService";

    private static final ComponentName TEST_JOB_COMPONENT = new ComponentName(
            TEST_APP2_PKG, TEST_APP2_JOB_SERVICE_CLASS);
    private static final int TEST_JOB_ID = 7357437;

    private static final int SLEEP_TIME_SEC = 1;

    // Constants below must match values defined on app2's Common.java
    private static final String MANIFEST_RECEIVER = "ManifestReceiver";
    private static final String DYNAMIC_RECEIVER = "DynamicReceiver";
    private static final String ACTION_FINISH_ACTIVITY =
            "com.android.cts.net.hostside.app2.action.FINISH_ACTIVITY";
    private static final String ACTION_FINISH_JOB =
            "com.android.cts.net.hostside.app2.action.FINISH_JOB";
    // Copied from com.android.server.net.NetworkPolicyManagerService class
    private static final String ACTION_SNOOZE_WARNING =
            "com.android.server.net.action.SNOOZE_WARNING";

    private static final String ACTION_RECEIVER_READY =
            "com.android.cts.net.hostside.app2.action.RECEIVER_READY";
    static final String ACTION_SHOW_TOAST =
            "com.android.cts.net.hostside.app2.action.SHOW_TOAST";

    protected static final String NOTIFICATION_TYPE_CONTENT = "CONTENT";
    protected static final String NOTIFICATION_TYPE_DELETE = "DELETE";
    protected static final String NOTIFICATION_TYPE_FULL_SCREEN = "FULL_SCREEN";
    protected static final String NOTIFICATION_TYPE_BUNDLE = "BUNDLE";
    protected static final String NOTIFICATION_TYPE_ACTION = "ACTION";
    protected static final String NOTIFICATION_TYPE_ACTION_BUNDLE = "ACTION_BUNDLE";
    protected static final String NOTIFICATION_TYPE_ACTION_REMOTE_INPUT = "ACTION_REMOTE_INPUT";

    private static final String NETWORK_STATUS_SEPARATOR = "\\|";
    private static final int SECOND_IN_MS = 1000;
    static final int NETWORK_TIMEOUT_MS = 15 * SECOND_IN_MS;

    private static final String KEY_NETWORK_STATE_OBSERVER = TEST_PKG + ".observer";
    private static final String KEY_SKIP_VALIDATION_CHECKS = TEST_PKG + ".skip_validation_checks";

    private static final String EMPTY_STRING = "";

    protected static final int TYPE_COMPONENT_ACTIVTIY = 0;
    protected static final int TYPE_COMPONENT_FOREGROUND_SERVICE = 1;
    protected static final int TYPE_EXPEDITED_JOB = 2;

    private static final int BATTERY_STATE_TIMEOUT_MS = 5000;
    private static final int BATTERY_STATE_CHECK_INTERVAL_MS = 500;

    private static final int ACTIVITY_NETWORK_STATE_TIMEOUT_MS = 10_000;
    private static final int JOB_NETWORK_STATE_TIMEOUT_MS = 10_000;
    private static final int LAUNCH_ACTIVITY_TIMEOUT_MS = 10_000;

    // Must be higher than NETWORK_TIMEOUT_MS
    private static final int ORDERED_BROADCAST_TIMEOUT_MS = NETWORK_TIMEOUT_MS * 4;

    private static final IntentFilter BATTERY_CHANGED_FILTER =
            new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

    protected static final long TEMP_POWERSAVE_WHITELIST_DURATION_MS = 20_000; // 20 sec

    private static final long BROADCAST_TIMEOUT_MS = 5_000;

    protected Context mContext;
    protected Instrumentation mInstrumentation;
    protected ConnectivityManager mCm;
    protected int mUid;
    private int mMyUid;
    private MyServiceClient mServiceClient;
    private DeviceConfigStateHelper mDeviceIdleDeviceConfigStateHelper;
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mLock;

    @Rule
    public final RuleChain mRuleChain = RuleChain.outerRule(new RequiredPropertiesRule())
            .around(new MeterednessConfigurationRule());

    protected void setUp() throws Exception {
        mInstrumentation = getInstrumentation();
        mContext = getContext();
        mCm = getConnectivityManager();
        mDeviceIdleDeviceConfigStateHelper =
                new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_DEVICE_IDLE);
        mUid = getUid(TEST_APP2_PKG);
        mMyUid = getUid(mContext.getPackageName());
        mServiceClient = new MyServiceClient(mContext);

        final Bundle args = InstrumentationRegistry.getArguments();
        final int bindPriorityFlags;
        if (Boolean.valueOf(args.getString(ARG_WAIVE_BIND_PRIORITY, "false"))) {
            bindPriorityFlags = Context.BIND_WAIVE_PRIORITY;
        } else {
            bindPriorityFlags = Context.BIND_NOT_FOREGROUND;
        }
        mServiceClient.bind(bindPriorityFlags);

        mPowerManager = mContext.getSystemService(PowerManager.class);
        executeShellCommand("cmd netpolicy start-watching " + mUid);
        // Some of the test cases assume that Data saver mode is initially disabled, which might not
        // always be the case. Therefore, explicitly disable it before running the tests.
        // Invoke setRestrictBackgroundInternal() directly instead of going through
        // setRestrictBackground(), as some devices do not fully support the Data saver mode but
        // still have certain parts of it enabled by default.
        setRestrictBackgroundInternal(false);
        setAppIdle(false);
        mLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        Log.i(TAG, "Apps status:\n"
                + "\ttest app: uid=" + mMyUid + ", state=" + getProcessStateByUid(mMyUid) + "\n"
                + "\tapp2: uid=" + mUid + ", state=" + getProcessStateByUid(mUid));
    }

    protected void tearDown() throws Exception {
        executeShellCommand("cmd netpolicy stop-watching");
        mServiceClient.unbind();
        final PowerManager.WakeLock lock = mLock;
        if (null != lock && lock.isHeld()) lock.release();
    }

    /**
     * Check if the feature blocking network for top_sleeping and lower priority proc-states is
     * enabled. This is a manual check because the feature flag infrastructure may not be available
     * in all the branches that will get this code.
     * TODO: b/322115994 - Use @RequiresFlagsEnabled with
     * Flags.FLAG_NETWORK_BLOCKED_FOR_TOP_SLEEPING_AND_ABOVE once the tests are moved to cts.
     */
    protected boolean isNetworkBlockedForTopSleepingAndAbove() {
        if (!SdkLevel.isAtLeastV()) {
            return false;
        }
        final String output = executeShellCommand("device_config get backstage_power"
                + " com.android.server.net.network_blocked_for_top_sleeping_and_above");
        return Boolean.parseBoolean(output);
    }

    protected int getUid(String packageName) throws Exception {
        return mContext.getPackageManager().getPackageUid(packageName, 0);
    }

    protected void assertRestrictBackgroundChangedReceived(int expectedCount) throws Exception {
        assertRestrictBackgroundChangedReceived(DYNAMIC_RECEIVER, expectedCount);
        assertRestrictBackgroundChangedReceived(MANIFEST_RECEIVER, 0);
    }

    protected void assertRestrictBackgroundChangedReceived(String receiverName, int expectedCount)
            throws Exception {
        int attempts = 0;
        int count = 0;
        final int maxAttempts = 5;
        do {
            attempts++;
            count = getNumberBroadcastsReceived(receiverName, ACTION_RESTRICT_BACKGROUND_CHANGED);
            assertFalse("Expected count " + expectedCount + " but actual is " + count,
                    count > expectedCount);
            if (count == expectedCount) {
                break;
            }
            Log.d(TAG, "Expecting count " + expectedCount + " but actual is " + count + " after "
                    + attempts + " attempts; sleeping "
                    + SLEEP_TIME_SEC + " seconds before trying again");
            // No sleep after the last turn
            if (attempts <= maxAttempts) {
                SystemClock.sleep(SLEEP_TIME_SEC * SECOND_IN_MS);
            }
        } while (attempts <= maxAttempts);
        assertEquals("Number of expected broadcasts for " + receiverName + " not reached after "
                + maxAttempts * SLEEP_TIME_SEC + " seconds", expectedCount, count);
    }

    protected void assertSnoozeWarningNotReceived() throws Exception {
        // Wait for a while to take broadcast queue delays into account
        SystemClock.sleep(BROADCAST_TIMEOUT_MS);
        assertEquals(0, getNumberBroadcastsReceived(DYNAMIC_RECEIVER, ACTION_SNOOZE_WARNING));
    }

    protected String sendOrderedBroadcast(Intent intent) throws Exception {
        return sendOrderedBroadcast(intent, ORDERED_BROADCAST_TIMEOUT_MS);
    }

    protected String sendOrderedBroadcast(Intent intent, int timeoutMs) throws Exception {
        final LinkedBlockingQueue<String> result = new LinkedBlockingQueue<>(1);
        Log.d(TAG, "Sending ordered broadcast: " + intent);
        mContext.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final String resultData = getResultData();
                if (resultData == null) {
                    Log.e(TAG, "Received null data from ordered intent");
                    // Offer an empty string so that the code waiting for the result can return.
                    result.offer(EMPTY_STRING);
                    return;
                }
                result.offer(resultData);
            }
        }, null, 0, null, null);

        final String resultData = result.poll(timeoutMs, TimeUnit.MILLISECONDS);
        Log.d(TAG, "Ordered broadcast response after " + timeoutMs + "ms: " + resultData );
        return resultData;
    }

    protected int getNumberBroadcastsReceived(String receiverName, String action) throws Exception {
        return mServiceClient.getCounters(receiverName, action);
    }

    protected void assertRestrictBackgroundStatus(int expectedStatus) throws Exception {
        final String status = mServiceClient.getRestrictBackgroundStatus();
        assertNotNull("didn't get API status from app2", status);
        assertEquals(restrictBackgroundValueToString(expectedStatus),
                restrictBackgroundValueToString(Integer.parseInt(status)));
    }

    /**
     * @deprecated The definition of "background" can be ambiguous. Use separate calls to
     * {@link #assertProcessStateBelow(int)} with
     * {@link #assertNetworkAccess(boolean, boolean, String)} to be explicit, instead.
     */
    @Deprecated
    protected void assertBackgroundNetworkAccess(boolean expectAllowed) throws Exception {
        assertProcessStateBelow(PROCESS_STATE_BOUND_FOREGROUND_SERVICE);
        assertNetworkAccess(expectAllowed, false, null);
    }

    protected void assertTopNetworkAccess(boolean expectAllowed) throws Exception {
        assertTopState();
        assertNetworkAccess(expectAllowed, true /* needScreenOn */);
    }

    protected void assertForegroundServiceNetworkAccess() throws Exception {
        assertForegroundServiceState();
        assertNetworkAccess(true /* expectAvailable */, false /* needScreenOn */);
    }

    /**
     * Asserts that an app always have access while on foreground or running a foreground service.
     *
     * <p>This method will launch an activity, a foreground service to make
     * the assertion, but will finish the activity / stop the service afterwards.
     */
    protected void assertsForegroundAlwaysHasNetworkAccess() throws Exception{
        // Checks foreground first.
        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_ACTIVTIY);
        finishActivity();

        // Then foreground service
        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_FOREGROUND_SERVICE);
        stopForegroundService();
    }

    protected void assertExpeditedJobHasNetworkAccess() throws Exception {
        launchComponentAndAssertNetworkAccess(TYPE_EXPEDITED_JOB);
        finishExpeditedJob();
    }

    protected void assertExpeditedJobHasNoNetworkAccess() throws Exception {
        launchComponentAndAssertNetworkAccess(TYPE_EXPEDITED_JOB, false);
        finishExpeditedJob();
    }

    /**
     * Asserts that the process state of the test app is below, in priority, to the given
     * {@link android.app.ActivityManager.ProcessState}.
     */
    protected final void assertProcessStateBelow(int processState) throws Exception {
        assertProcessState(ps -> ps.state > processState, null);
    }

    protected final void assertTopState() throws Exception {
        assertProcessState(ps -> ps.state == PROCESS_STATE_TOP, () -> turnScreenOn());
    }

    protected final void assertForegroundServiceState() throws Exception {
        assertProcessState(ps -> ps.state == PROCESS_STATE_FOREGROUND_SERVICE, null);
    }

    private void assertProcessState(Predicate<ProcessState> statePredicate,
            ThrowingRunnable onRetry) throws Exception {
        final int maxTries = 30;
        ProcessState state = null;
        for (int i = 1; i <= maxTries; i++) {
            if (onRetry != null) {
                onRetry.run();
            }
            state = getProcessStateByUid(mUid);
            Log.v(TAG, "assertProcessState(): status for app2 (" + mUid + ") on attempt #" + i
                    + ": " + state);
            if (statePredicate.test(state)) {
                return;
            }
            Log.i(TAG, "App not in desired process state on attempt #" + i
                    + "; sleeping 1s before trying again");
            if (i < maxTries) {
                SystemClock.sleep(SECOND_IN_MS);
            }
        }
        fail("App2 (" + mUid + ") is not in the desired process state after " + maxTries
                + " attempts: " + state);
    }

    /**
     * Asserts whether the active network is available or not. If the network is unavailable, also
     * checks whether it is blocked by the expected error.
     *
     * @param expectAllowed expect background network access to be allowed or not.
     * @param expectedUnavailableError the expected error when {@code expectAllowed} is false. It's
     *                                 meaningful only when the {@code expectAllowed} is 'false'.
     *                                 Throws an IllegalArgumentException when {@code expectAllowed}
     *                                 is true and this parameter is not null. When the
     *                                 {@code expectAllowed} is 'false' and this parameter is null,
     *                                 this function does not compare error type of the networking
     *                                 access failure.
     */
    protected void assertNetworkAccess(boolean expectAllowed, String expectedUnavailableError)
            throws Exception {
        if (expectAllowed && expectedUnavailableError != null) {
            throw new IllegalArgumentException("expectedUnavailableError is not null");
        }
        assertNetworkAccess(expectAllowed, false, expectedUnavailableError);
    }

    /**
     * Asserts whether the active network is available or not.
     */
    private void assertNetworkAccess(boolean expectAvailable, boolean needScreenOn)
            throws Exception {
        assertNetworkAccess(expectAvailable, needScreenOn, null);
    }

    private void assertNetworkAccess(boolean expectAvailable, boolean needScreenOn,
            @Nullable final String expectedUnavailableError) throws Exception {
        final int maxTries = 5;
        String error = null;
        int timeoutMs = 500;

        for (int i = 1; i <= maxTries; i++) {
            error = checkNetworkAccess(expectAvailable, expectedUnavailableError);

            if (error == null) return;

            // TODO: ideally, it should retry only when it cannot connect to an external site,
            // or no retry at all! But, currently, the initial change fails almost always on
            // battery saver tests because the netd changes are made asynchronously.
            // Once b/27803922 is fixed, this retry mechanism should be revisited.

            Log.w(TAG, "Network status didn't match for expectAvailable=" + expectAvailable
                    + " on attempt #" + i + ": " + error + "\n"
                    + "Sleeping " + timeoutMs + "ms before trying again");
            if (needScreenOn) {
                turnScreenOn();
            }
            // No sleep after the last turn
            if (i < maxTries) {
                SystemClock.sleep(timeoutMs);
            }
            // Exponential back-off.
            timeoutMs = Math.min(timeoutMs*2, NETWORK_TIMEOUT_MS);
        }
        fail("Invalid state for " + mUid + "; expectAvailable=" + expectAvailable + " after "
                + maxTries + " attempts.\nLast error: " + error);
    }

    /**
     * Asserts whether the network is blocked by accessing bpf maps if command-line tool supports.
     */
    void assertNetworkAccessBlockedByBpf(boolean expectBlocked, int uid, boolean metered) {
        final String result;
        try {
            result = executeShellCommand(
                    "cmd network_stack is-uid-networking-blocked " + uid + " " + metered);
        } catch (AssertionError e) {
            // If NetworkStack is too old to support this command, ignore and continue
            // this test to verify other parts.
            if (e.getMessage().contains("No shell command implementation.")) {
                return;
            }
            throw e;
        }

        // Tethering module is too old.
        if (result.contains("API is unsupported")) {
            return;
        }

        assertEquals(expectBlocked, parseBooleanOrThrow(result.trim()));
    }

    /**
     * Similar to {@link Boolean#parseBoolean} but throws when the input
     * is unexpected instead of returning false.
     */
    private static boolean parseBooleanOrThrow(@NonNull String s) {
        // Don't use Boolean.parseBoolean
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        throw new IllegalArgumentException("Unexpected: " + s);
    }

    /**
     * Checks whether the network is available as expected.
     *
     * @return error message with the mismatch (or empty if assertion passed).
     */
    private String checkNetworkAccess(boolean expectAvailable,
            @Nullable final String expectedUnavailableError) throws Exception {
        final String resultData = mServiceClient.checkNetworkStatus();
        return checkForAvailabilityInResultData(resultData, expectAvailable,
                expectedUnavailableError);
    }

    private String checkForAvailabilityInResultData(String resultData, boolean expectAvailable,
            @Nullable final String expectedUnavailableError) {
        if (resultData == null) {
            assertNotNull("Network status from app2 is null", resultData);
        }
        // Network status format is described on MyBroadcastReceiver.checkNetworkStatus()
        final String[] parts = resultData.split(NETWORK_STATUS_SEPARATOR);
        assertEquals("Wrong network status: " + resultData, 5, parts.length);
        final State state = parts[0].equals("null") ? null : State.valueOf(parts[0]);
        final DetailedState detailedState = parts[1].equals("null")
                ? null : DetailedState.valueOf(parts[1]);
        final boolean connected = Boolean.valueOf(parts[2]);
        final String connectionCheckDetails = parts[3];
        final String networkInfo = parts[4];

        final StringBuilder errors = new StringBuilder();
        final State expectedState;
        final DetailedState expectedDetailedState;
        if (expectAvailable) {
            expectedState = State.CONNECTED;
            expectedDetailedState = DetailedState.CONNECTED;
        } else {
            expectedState = State.DISCONNECTED;
            expectedDetailedState = DetailedState.BLOCKED;
        }

        if (expectAvailable != connected) {
            errors.append(String.format("External site connection failed: expected %s, got %s\n",
                    expectAvailable, connected));
        }
        if (expectedState != state || expectedDetailedState != detailedState) {
            errors.append(String.format("Connection state mismatch: expected %s/%s, got %s/%s\n",
                    expectedState, expectedDetailedState, state, detailedState));
        } else if (!expectAvailable && (expectedUnavailableError != null)
                 && !connectionCheckDetails.contains(expectedUnavailableError)) {
            errors.append("Connection unavailable reason mismatch: expected "
                     + expectedUnavailableError + "\n");
        }

        if (errors.length() > 0) {
            errors.append("\tnetworkInfo: " + networkInfo + "\n");
            errors.append("\tconnectionCheckDetails: " + connectionCheckDetails + "\n");
        }
        return errors.length() == 0 ? null : errors.toString();
    }

    /**
     * Runs a Shell command which is not expected to generate output.
     */
    protected void executeSilentShellCommand(String command) {
        final String result = executeShellCommand(command);
        assertTrue("Command '" + command + "' failed: " + result, result.trim().isEmpty());
    }

    /**
     * Asserts the result of a command, wait and re-running it a couple times if necessary.
     */
    protected void assertDelayedShellCommand(String command, final String expectedResult)
            throws Exception {
        assertDelayedShellCommand(command, 5, 1, expectedResult);
    }

    protected void assertDelayedShellCommand(String command, int maxTries, int napTimeSeconds,
            final String expectedResult) throws Exception {
        assertDelayedShellCommand(command, maxTries, napTimeSeconds, new ExpectResultChecker() {

            @Override
            public boolean isExpected(String result) {
                return expectedResult.equals(result);
            }

            @Override
            public String getExpected() {
                return expectedResult;
            }
        });
    }

    protected void assertDelayedShellCommand(String command, int maxTries, int napTimeSeconds,
            ExpectResultChecker checker) throws Exception {
        String result = "";
        for (int i = 1; i <= maxTries; i++) {
            result = executeShellCommand(command).trim();
            if (checker.isExpected(result)) return;
            Log.v(TAG, "Command '" + command + "' returned '" + result + " instead of '"
                    + checker.getExpected() + "' on attempt #" + i
                    + "; sleeping " + napTimeSeconds + "s before trying again");
            // No sleep after the last turn
            if (i < maxTries) {
                SystemClock.sleep(napTimeSeconds * SECOND_IN_MS);
            }
        }
        fail("Command '" + command + "' did not return '" + checker.getExpected() + "' after "
                + maxTries
                + " attempts. Last result: '" + result + "'");
    }

    protected void addRestrictBackgroundWhitelist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy add restrict-background-whitelist " + uid);
        assertRestrictBackgroundWhitelist(uid, true);
        // UID policies live by the Highlander rule: "There can be only one".
        // Hence, if app is whitelisted, it should not be blacklisted.
        assertRestrictBackgroundBlacklist(uid, false);
    }

    protected void removeRestrictBackgroundWhitelist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy remove restrict-background-whitelist " + uid);
        assertRestrictBackgroundWhitelist(uid, false);
    }

    protected void assertRestrictBackgroundWhitelist(int uid, boolean expected) throws Exception {
        assertRestrictBackground("restrict-background-whitelist", uid, expected);
    }

    protected void addRestrictBackgroundBlacklist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy add restrict-background-blacklist " + uid);
        assertRestrictBackgroundBlacklist(uid, true);
        // UID policies live by the Highlander rule: "There can be only one".
        // Hence, if app is blacklisted, it should not be whitelisted.
        assertRestrictBackgroundWhitelist(uid, false);
    }

    protected void removeRestrictBackgroundBlacklist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy remove restrict-background-blacklist " + uid);
        assertRestrictBackgroundBlacklist(uid, false);
    }

    protected void assertRestrictBackgroundBlacklist(int uid, boolean expected) throws Exception {
        assertRestrictBackground("restrict-background-blacklist", uid, expected);
    }

    protected void addAppIdleWhitelist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy add app-idle-whitelist " + uid);
        assertAppIdleWhitelist(uid, true);
    }

    protected void removeAppIdleWhitelist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy remove app-idle-whitelist " + uid);
        assertAppIdleWhitelist(uid, false);
    }

    protected void assertAppIdleWhitelist(int uid, boolean expected) throws Exception {
        assertRestrictBackground("app-idle-whitelist", uid, expected);
    }

    private void assertRestrictBackground(String list, int uid, boolean expected) throws Exception {
        final int maxTries = 5;
        boolean actual = false;
        final String expectedUid = Integer.toString(uid);
        String uids = "";
        for (int i = 1; i <= maxTries; i++) {
            final String output =
                    executeShellCommand("cmd netpolicy list " + list);
            uids = output.split(":")[1];
            for (String candidate : uids.split(" ")) {
                actual = candidate.trim().equals(expectedUid);
                if (expected == actual) {
                    return;
                }
            }
            Log.v(TAG, list + " check for uid " + uid + " doesn't match yet (expected "
                    + expected + ", got " + actual + "); sleeping 1s before polling again");
            // No sleep after the last turn
            if (i < maxTries) {
                SystemClock.sleep(SECOND_IN_MS);
            }
        }
        fail(list + " check for uid " + uid + " failed: expected " + expected + ", got " + actual
                + ". Full list: " + uids);
    }

    protected void addTempPowerSaveModeWhitelist(String packageName, long duration)
            throws Exception {
        Log.i(TAG, "Adding pkg " + packageName + " to temp-power-save-mode whitelist");
        executeShellCommand("dumpsys deviceidle tempwhitelist -d " + duration + " " + packageName);
    }

    protected void assertPowerSaveModeWhitelist(String packageName, boolean expected)
            throws Exception {
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        assertDelayedShellCommand("dumpsys deviceidle whitelist =" + packageName,
                Boolean.toString(expected));
    }

    protected void addPowerSaveModeWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Adding package " + packageName + " to power-save-mode whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        executeShellCommand("dumpsys deviceidle whitelist +" + packageName);
        assertPowerSaveModeWhitelist(packageName, true);
    }

    protected void removePowerSaveModeWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Removing package " + packageName + " from power-save-mode whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        executeShellCommand("dumpsys deviceidle whitelist -" + packageName);
        assertPowerSaveModeWhitelist(packageName, false);
    }

    protected void assertPowerSaveModeExceptIdleWhitelist(String packageName, boolean expected)
            throws Exception {
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        assertDelayedShellCommand("dumpsys deviceidle except-idle-whitelist =" + packageName,
                Boolean.toString(expected));
    }

    protected void addPowerSaveModeExceptIdleWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Adding package " + packageName + " to power-save-mode-except-idle whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        executeShellCommand("dumpsys deviceidle except-idle-whitelist +" + packageName);
        assertPowerSaveModeExceptIdleWhitelist(packageName, true);
    }

    protected void removePowerSaveModeExceptIdleWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Removing package " + packageName
                + " from power-save-mode-except-idle whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        executeShellCommand("dumpsys deviceidle except-idle-whitelist reset");
        assertPowerSaveModeExceptIdleWhitelist(packageName, false);
    }

    protected void turnBatteryOn() throws Exception {
        executeSilentShellCommand("cmd battery unplug");
        executeSilentShellCommand("cmd battery set status "
                + BatteryManager.BATTERY_STATUS_DISCHARGING);
        assertBatteryState(false);
    }

    protected void turnBatteryOff() throws Exception {
        executeSilentShellCommand("cmd battery set ac " + BATTERY_PLUGGED_ANY);
        executeSilentShellCommand("cmd battery set level 100");
        executeSilentShellCommand("cmd battery set status "
                + BatteryManager.BATTERY_STATUS_CHARGING);
        assertBatteryState(true);
    }

    protected void resetBatteryState() {
        BatteryUtils.runDumpsysBatteryReset();
    }

    private void assertBatteryState(boolean pluggedIn) throws Exception {
        final long endTime = SystemClock.elapsedRealtime() + BATTERY_STATE_TIMEOUT_MS;
        while (isDevicePluggedIn() != pluggedIn && SystemClock.elapsedRealtime() <= endTime) {
            Thread.sleep(BATTERY_STATE_CHECK_INTERVAL_MS);
        }
        if (isDevicePluggedIn() != pluggedIn) {
            fail("Timed out waiting for the plugged-in state to change,"
                    + " expected pluggedIn: " + pluggedIn);
        }
    }

    private boolean isDevicePluggedIn() {
        final Intent batteryIntent = mContext.registerReceiver(null, BATTERY_CHANGED_FILTER);
        return batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) > 0;
    }

    protected void turnScreenOff() throws Exception {
        if (!mLock.isHeld()) mLock.acquire();
        executeSilentShellCommand("input keyevent KEYCODE_SLEEP");
    }

    protected void turnScreenOn() throws Exception {
        executeSilentShellCommand("input keyevent KEYCODE_WAKEUP");
        if (mLock.isHeld()) mLock.release();
        executeSilentShellCommand("wm dismiss-keyguard");
    }

    protected void setBatterySaverMode(boolean enabled) throws Exception {
        if (!isBatterySaverSupported()) {
            return;
        }
        Log.i(TAG, "Setting Battery Saver Mode to " + enabled);
        if (enabled) {
            turnBatteryOn();
            AmUtils.waitForBroadcastBarrier();
            executeSilentShellCommand("cmd power set-mode 1");
        } else {
            executeSilentShellCommand("cmd power set-mode 0");
            turnBatteryOff();
            AmUtils.waitForBroadcastBarrier();
        }
    }

    protected void setDozeMode(boolean enabled) throws Exception {
        if (!isDozeModeSupported()) {
            return;
        }

        Log.i(TAG, "Setting Doze Mode to " + enabled);
        if (enabled) {
            turnBatteryOn();
            turnScreenOff();
            executeShellCommand("dumpsys deviceidle force-idle deep");
        } else {
            turnScreenOn();
            turnBatteryOff();
            executeShellCommand("dumpsys deviceidle unforce");
        }
        assertDozeMode(enabled);
    }

    protected void assertDozeMode(boolean enabled) throws Exception {
        assertDelayedShellCommand("dumpsys deviceidle get deep", enabled ? "IDLE" : "ACTIVE");
    }

    protected void setAppIdle(boolean isIdle) throws Exception {
        setAppIdleNoAssert(isIdle);
        assertAppIdle(isIdle);
    }

    protected void setAppIdleNoAssert(boolean isIdle) throws Exception {
        if (!isAppStandbySupported()) {
            return;
        }
        Log.i(TAG, "Setting app idle to " + isIdle);
        final String bucketName = isIdle ? "rare" : "active";
        executeSilentShellCommand("am set-standby-bucket " + TEST_APP2_PKG + " " + bucketName);
    }

    protected void assertAppIdle(boolean isIdle) throws Exception {
        try {
            assertDelayedShellCommand("am get-inactive " + TEST_APP2_PKG,
                    30 /* maxTries */, 1 /* napTimeSeconds */, "Idle=" + isIdle);
        } catch (Throwable e) {
            throw e;
        }
    }

    /**
     * Starts a service that will register a broadcast receiver to receive
     * {@code RESTRICT_BACKGROUND_CHANGE} intents.
     * <p>
     * The service must run in a separate app because otherwise it would be killed every time
     * {@link #runDeviceTests(String, String)} is executed.
     */
    protected void registerBroadcastReceiver() throws Exception {
        mServiceClient.registerBroadcastReceiver();

        final Intent intent = new Intent(ACTION_RECEIVER_READY)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        // Wait until receiver is ready.
        final int maxTries = 10;
        for (int i = 1; i <= maxTries; i++) {
            final String message = sendOrderedBroadcast(intent, SECOND_IN_MS * 4);
            Log.d(TAG, "app2 receiver acked: " + message);
            if (message != null) {
                return;
            }
            Log.v(TAG, "app2 receiver is not ready yet; sleeping 1s before polling again");
            // No sleep after the last turn
            if (i < maxTries) {
                SystemClock.sleep(SECOND_IN_MS);
            }
        }
        fail("app2 receiver is not ready in " + mUid);
    }

    protected void registerNetworkCallback(final NetworkRequest request, INetworkCallback cb)
            throws Exception {
        Log.i(TAG, "Registering network callback for request: " + request);
        mServiceClient.registerNetworkCallback(request, cb);
    }

    protected void unregisterNetworkCallback() throws Exception {
        mServiceClient.unregisterNetworkCallback();
    }

    /**
     * Registers a {@link NotificationListenerService} implementation that will execute the
     * notification actions right after the notification is sent.
     */
    protected void registerNotificationListenerService() throws Exception {
        executeShellCommand("cmd notification allow_listener "
                + MyNotificationListenerService.getId());
        final NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        final ComponentName listenerComponent = MyNotificationListenerService.getComponentName();
        assertTrue(listenerComponent + " has not been granted access",
                nm.isNotificationListenerAccessGranted(listenerComponent));
    }

    protected void setPendingIntentAllowlistDuration(long durationMs) {
        mDeviceIdleDeviceConfigStateHelper.set("notification_allowlist_duration_ms",
                String.valueOf(durationMs));
    }

    protected void resetDeviceIdleSettings() {
        mDeviceIdleDeviceConfigStateHelper.restoreOriginalValues();
    }

    protected void launchActivity() throws Exception {
        turnScreenOn();
        final CountDownLatch latch = new CountDownLatch(1);
        final Intent launchIntent = getIntentForComponent(TYPE_COMPONENT_ACTIVTIY);
        final RemoteCallback callback = new RemoteCallback(result -> latch.countDown());
        launchIntent.putExtra(Intent.EXTRA_REMOTE_CALLBACK, callback);
        mContext.startActivity(launchIntent);
        // There might be a race when app2 is launched but ACTION_FINISH_ACTIVITY has not registered
        // before test calls finishActivity(). When the issue is happened, there is no way to fix
        // it, so have a callback design to make sure that the app is launched completely and
        // ACTION_FINISH_ACTIVITY will be registered before leaving this method.
        if (!latch.await(LAUNCH_ACTIVITY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Timed out waiting for launching activity");
        }
    }

    protected void launchComponentAndAssertNetworkAccess(int type) throws Exception {
        launchComponentAndAssertNetworkAccess(type, true);
    }

    protected void launchComponentAndAssertNetworkAccess(int type, boolean expectAvailable)
            throws Exception {
        if (type == TYPE_COMPONENT_FOREGROUND_SERVICE) {
            startForegroundService();
            assertForegroundServiceNetworkAccess();
            return;
        } else if (type == TYPE_COMPONENT_ACTIVTIY) {
            turnScreenOn();
            final CountDownLatch latch = new CountDownLatch(1);
            final Intent launchIntent = getIntentForComponent(type);
            final Bundle extras = new Bundle();
            final ArrayList<Pair<Integer, String>> result = new ArrayList<>(1);
            extras.putBinder(KEY_NETWORK_STATE_OBSERVER, getNewNetworkStateObserver(latch, result));
            extras.putBoolean(KEY_SKIP_VALIDATION_CHECKS, !expectAvailable);
            launchIntent.putExtras(extras);
            mContext.startActivity(launchIntent);
            if (latch.await(ACTIVITY_NETWORK_STATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                final int resultCode = result.get(0).first;
                final String resultData = result.get(0).second;
                if (resultCode == INetworkStateObserver.RESULT_SUCCESS_NETWORK_STATE_CHECKED) {
                    final String error = checkForAvailabilityInResultData(
                            resultData, expectAvailable, null /* expectedUnavailableError */);
                    if (error != null) {
                        fail("Network is not available for activity in app2 (" + mUid + "): "
                                + error);
                    }
                } else if (resultCode == INetworkStateObserver.RESULT_ERROR_UNEXPECTED_PROC_STATE) {
                    Log.d(TAG, resultData);
                    // App didn't come to foreground when the activity is started, so try again.
                    assertTopNetworkAccess(true);
                } else {
                    fail("Unexpected resultCode=" + resultCode + "; received=[" + resultData + "]");
                }
            } else {
                fail("Timed out waiting for network availability status from app2's activity ("
                        + mUid + ")");
            }
        } else if (type == TYPE_EXPEDITED_JOB) {
            final Bundle extras = new Bundle();
            final ArrayList<Pair<Integer, String>> result = new ArrayList<>(1);
            final CountDownLatch latch = new CountDownLatch(1);
            extras.putBinder(KEY_NETWORK_STATE_OBSERVER, getNewNetworkStateObserver(latch, result));
            extras.putBoolean(KEY_SKIP_VALIDATION_CHECKS, !expectAvailable);
            final JobInfo jobInfo = new JobInfo.Builder(TEST_JOB_ID, TEST_JOB_COMPONENT)
                    .setExpedited(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setTransientExtras(extras)
                    .build();
            assertEquals("Error scheduling " + jobInfo,
                    RESULT_SUCCESS, mServiceClient.scheduleJob(jobInfo));
            forceRunJob(TEST_APP2_PKG, TEST_JOB_ID);
            if (latch.await(JOB_NETWORK_STATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                final int resultCode = result.get(0).first;
                final String resultData = result.get(0).second;
                if (resultCode == INetworkStateObserver.RESULT_SUCCESS_NETWORK_STATE_CHECKED) {
                    final String error = checkForAvailabilityInResultData(
                            resultData, expectAvailable, null /* expectedUnavailableError */);
                    if (error != null) {
                        Log.d(TAG, "Network state is unexpected, checking again. " + error);
                        // Right now we could end up in an unexpected state if expedited job
                        // doesn't have network access immediately after starting, so check again.
                        assertNetworkAccess(expectAvailable, false /* needScreenOn */);
                    }
                } else {
                    fail("Unexpected resultCode=" + resultCode + "; received=[" + resultData + "]");
                }
            } else {
                fail("Timed out waiting for network availability status from app2's expedited job ("
                        + mUid + ")");
            }
        } else {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    protected void startActivity() throws Exception {
        final Intent launchIntent = getIntentForComponent(TYPE_COMPONENT_ACTIVTIY);
        mContext.startActivity(launchIntent);
    }

    private void startForegroundService() throws Exception {
        final Intent launchIntent = getIntentForComponent(TYPE_COMPONENT_FOREGROUND_SERVICE);
        mContext.startForegroundService(launchIntent);
        assertForegroundServiceState();
    }

    private Intent getIntentForComponent(int type) {
        final Intent intent = new Intent();
        if (type == TYPE_COMPONENT_ACTIVTIY) {
            intent.setComponent(new ComponentName(TEST_APP2_PKG, TEST_APP2_ACTIVITY_CLASS))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else if (type == TYPE_COMPONENT_FOREGROUND_SERVICE) {
            intent.setComponent(new ComponentName(TEST_APP2_PKG, TEST_APP2_SERVICE_CLASS))
                    .setFlags(1);
        } else {
            fail("Unknown type: " + type);
        }
        return intent;
    }

    protected void stopForegroundService() throws Exception {
        executeShellCommand(String.format("am startservice -f 2 %s/%s",
                TEST_APP2_PKG, TEST_APP2_SERVICE_CLASS));
        // NOTE: cannot assert state because it depends on whether activity was on top before.
    }

    private Binder getNewNetworkStateObserver(final CountDownLatch latch,
            final ArrayList<Pair<Integer, String>> result) {
        return new INetworkStateObserver.Stub() {
            @Override
            public void onNetworkStateChecked(int resultCode, String resultData) {
                result.add(Pair.create(resultCode, resultData));
                latch.countDown();
            }
        };
    }

    /**
     * Finishes an activity on app2 so its process is demoted from foreground status.
     */
    protected void finishActivity() throws Exception {
        final Intent intent = new Intent(ACTION_FINISH_ACTIVITY)
                .setPackage(TEST_APP2_PKG)
                .setFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        sendOrderedBroadcast(intent);
    }

    /**
     * Finishes the expedited job on app2 so its process is demoted from foreground status.
     */
    private void finishExpeditedJob() throws Exception {
        final Intent intent = new Intent(ACTION_FINISH_JOB)
                .setPackage(TEST_APP2_PKG)
                .setFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        sendOrderedBroadcast(intent);
    }

    protected void sendNotification(int notificationId, String notificationType) throws Exception {
        Log.d(TAG, "Sending notification broadcast (id=" + notificationId
                + ", type=" + notificationType);
        mServiceClient.sendNotification(notificationId, notificationType);
    }

    protected String showToast() {
        final Intent intent = new Intent(ACTION_SHOW_TOAST);
        intent.setPackage(TEST_APP2_PKG);
        Log.d(TAG, "Sending request to show toast");
        try {
            return sendOrderedBroadcast(intent, 3 * SECOND_IN_MS);
        } catch (Exception e) {
            return "";
        }
    }

    private ProcessState getProcessStateByUid(int uid) throws Exception {
        return new ProcessState(executeShellCommand("cmd activity get-uid-state " + uid));
    }

    private static class ProcessState {
        private final String fullState;
        final int state;

        ProcessState(String fullState) {
            this.fullState = fullState;
            try {
                this.state = Integer.parseInt(fullState.split(" ")[0]);
            } catch (Exception e) {
                throw new IllegalArgumentException("Could not parse " + fullState);
            }
        }

        @Override
        public String toString() {
            return fullState;
        }
    }

    /**
     * Helper class used to assert the result of a Shell command.
     */
    protected static interface ExpectResultChecker {
        /**
         * Checkes whether the result of the command matched the expectation.
         */
        boolean isExpected(String result);
        /**
         * Gets the expected result so it's displayed on log and failure messages.
         */
        String getExpected();
    }

    protected void setRestrictedNetworkingMode(boolean enabled) throws Exception {
        executeSilentShellCommand(
                "settings put global restricted_networking_mode " + (enabled ? 1 : 0));
        assertRestrictedNetworkingModeState(enabled);
    }

    protected void assertRestrictedNetworkingModeState(boolean enabled) throws Exception {
        assertDelayedShellCommand("cmd netpolicy get restricted-mode",
                "Restricted mode status: " + (enabled ? "enabled" : "disabled"));
    }
}
