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

package android.app.cts;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ApplicationExitInfo;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.app.stubs.IHeartbeat;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.externalservice.common.RunningServiceInfo;
import android.externalservice.common.ServiceMessages;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.os.Flags;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.server.wm.settings.SettingsSession;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.DebugUtils;
import android.util.Log;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AmMonitor;
import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.MemInfoReader;
import com.android.server.os.TombstoneProtos.Tombstone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public final class ActivityManagerAppExitInfoTest {
    private static final String TAG = ActivityManagerAppExitInfoTest.class.getSimpleName();

    public static final boolean FIRST_SDK_IS_AT_LEAST_U =
            ApiLevelUtil.isFirstApiAfter(Build.VERSION_CODES.TIRAMISU);

    private static final String STUB_PACKAGE_NAME =
            "com.android.cts.launcherapps.simpleapp";
    private static final String STUB_SERVICE_NAME =
            "com.android.cts.launcherapps.simpleapp.SimpleService4";
    private static final String STUB_SERVICE_REMOTE_NAME =
            "com.android.cts.launcherapps.simpleapp.SimpleService5";
    private static final String STUB_SERVICE_ISOLATED_NAME =
            "com.android.cts.launcherapps.simpleapp.SimpleService6";
    private static final String STUB_RECEIVER_NAME =
            "com.android.cts.launcherapps.simpleapp.SimpleReceiver";
    private static final String STUB_PROCESS_NAME = STUB_PACKAGE_NAME;
    private static final String STUB_REMOTE_PROCESS_NAME = STUB_PROCESS_NAME + ":remote";
    private static final String SIMPLE_ACTIVITY = ".SimpleActivity";

    private static final String HEARTBEAT_PACKAGE = "android.app.stubs";
    private static final String HEARTBEAT_PROCESS = HEARTBEAT_PACKAGE + ":hbact";
    private static final String HEARTBEAT_ACTIVITY = HEARTBEAT_PACKAGE + ".HeartbeatActivity";
    private static final String HEARTBEAT_SERVICE = HEARTBEAT_PACKAGE + ".HeartbeatService";
    private static final String HEARTBEAT_PROCESS_DEAD = "dead";
    private static final String HEARTBEAT_COUNTDOWN_NAME = "countdown";
    private static final String HEARTBEAT_INTERVAL_NAME = "interval";
    private static final int HEARTBEAT_COUNTDOWN = 15;
    private static final long HEARTBEAT_INTERVAL = 1000;
    private static final long HEARTBEAT_FREEZER_LONG = 30000;
    private static final long HEARTBEAT_FREEZER_SHORT = 5000;
    private static final long FREEZER_TIMEOUT_FLOOR = 10000;

    private static final String EXIT_ACTION =
            "com.android.cts.launchertests.simpleapp.EXIT_ACTION";
    private static final String EXTRA_ACTION = "action";
    private static final String EXTRA_MESSENGER = "messenger";
    private static final String EXTRA_PROCESS_NAME = "process";
    private static final String EXTRA_COOKIE = "cookie";

    private static final int ACTION_NONE = 0;
    private static final int ACTION_FINISH = 1;
    private static final int ACTION_EXIT = 2;
    private static final int ACTION_ANR = 3;
    private static final int ACTION_NATIVE_CRASH = 4;
    private static final int ACTION_KILL = 5;
    private static final int ACTION_ACQUIRE_STABLE_PROVIDER = 6;
    private static final int ACTION_KILL_PROVIDER = 7;
    private static final int EXIT_CODE = 123;
    private static final int CRASH_SIGNAL = OsConstants.SIGSEGV;

    private static final long TOMBSTONE_FETCH_TIMEOUT_MS = 10_000;

    private static final long WAITFOR_MSEC = 10000;
    private static final long WAITFOR_SETTLE_DOWN = 2000;

    private static final int CMD_PID = 1;

    private Context mContext;
    private Instrumentation mInstrumentation;
    private int mStubPackageUid;
    private int mStubPackagePid;
    private int mStubPackageRemotePid;
    private int mStubPackageOtherUid;
    private int mStubPackageOtherUserPid;
    private int mStubPackageRemoteOtherUserPid;
    private int mStubPackageIsolatedUid;
    private int mStubPackageIsolatedPid;
    private String mStubPackageIsolatedProcessName;
    private WatchUidRunner mWatcher;
    private WatchUidRunner mOtherUidWatcher;
    private ActivityManager mActivityManager;
    private CountDownLatch mLatch;
    private UserManager mUserManager;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Messenger mMessenger;
    private boolean mSupportMultipleUsers;
    private int mCurrentUserId;
    private UserHandle mCurrentUserHandle;
    private int mOtherUserId;
    private UserHandle mOtherUserHandle;
    private DropBoxManager.Entry mAnrEntry;
    private SettingsSession<String> mDataAnrSettings;
    private SettingsSession<String> mHiddenApiSettings;
    private int mProcSeqNum;
    private String mFreezerTimeout;
    private boolean mHeartbeatDead;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mStubPackageUid = mContext.getPackageManager().getPackageUid(STUB_PACKAGE_NAME, 0);
        mWatcher = new WatchUidRunner(mInstrumentation, mStubPackageUid, WAITFOR_MSEC);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mUserManager = UserManager.get(mContext);
        mCurrentUserId = UserHandle.getUserId(Process.myUid());
        mCurrentUserHandle = Process.myUserHandle();
        mSupportMultipleUsers = mUserManager.supportsMultipleUsers();
        mHandlerThread = new HandlerThread("receiver");
        mHandlerThread.start();
        mHandler = new H(mHandlerThread.getLooper());
        mMessenger = new Messenger(mHandler);
        executeShellCmd("cmd deviceidle whitelist +" + STUB_PACKAGE_NAME);
        executeShellCmd("cmd deviceidle whitelist +" + HEARTBEAT_PACKAGE);
        mDataAnrSettings = new SettingsSession<>(
                Settings.Global.getUriFor(
                        Settings.Global.DROPBOX_TAG_PREFIX + "data_app_anr"),
                Settings.Global::getString, Settings.Global::putString);
        mDataAnrSettings.set("enabled");
        mHiddenApiSettings = new SettingsSession<>(
                Settings.Global.getUriFor(
                        Settings.Global.HIDDEN_API_BLACKLIST_EXEMPTIONS),
                Settings.Global::getString, Settings.Global::putString);
        mHiddenApiSettings.set("*");
        mFreezerTimeout = executeShellCmd(
                "device_config get activity_manager_native_boot freeze_debounce_timeout");

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
        mContext.getPackageManager().setApplicationEnabledSetting(
                STUB_PACKAGE_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                0);
    }

    private void handleMessagePid(Message msg) {
        boolean didSomething = false;
        Bundle b = (Bundle) msg.obj;
        String processName = b.getString(EXTRA_PROCESS_NAME);

        if (STUB_PROCESS_NAME.equals(processName)) {
            if (mOtherUserId != 0 && UserHandle.getUserId(msg.arg2) == mOtherUserId) {
                mStubPackageOtherUserPid = msg.arg1;
                assertTrue(mStubPackageOtherUserPid > 0);
            } else {
                mStubPackagePid = msg.arg1;
                assertTrue(mStubPackagePid > 0);
            }
        } else if (STUB_REMOTE_PROCESS_NAME.equals(processName)) {
            if (mOtherUserId != 0 && UserHandle.getUserId(msg.arg2) == mOtherUserId) {
                mStubPackageRemoteOtherUserPid = msg.arg1;
                assertTrue(mStubPackageRemoteOtherUserPid > 0);
            } else {
                mStubPackageRemotePid = msg.arg1;
                assertTrue(mStubPackageRemotePid > 0);
            }
        } else if (HEARTBEAT_PROCESS.equals(processName)) {
            mStubPackagePid = msg.arg1;
            mStubPackageUid = msg.arg2;
            mHeartbeatDead = b.getBoolean(HEARTBEAT_PROCESS_DEAD);
            assertTrue(mStubPackagePid > 0);
            assertTrue(mStubPackageUid > 0);
        } else { // must be isolated process
            mStubPackageIsolatedPid = msg.arg1;
            mStubPackageIsolatedUid = msg.arg2;
            mStubPackageIsolatedProcessName = processName;
            assertTrue(mStubPackageIsolatedPid > 0);
            assertTrue(mStubPackageIsolatedUid > 0);
            assertNotNull(processName);
        }

        if (mLatch != null) {
            Log.d(TAG, "Counting down latch on message " + msg + " for process " + processName);
            mLatch.countDown();
        }
    }

    private class H extends Handler {
        H(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CMD_PID:
                    handleMessagePid(msg);
                    break;
                default:
                    break;
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        mWatcher.finish();
        executeShellCmd(
                "device_config put activity_manager_native_boot freeze_debounce_timeout "
                        + mFreezerTimeout);
        executeShellCmd("cmd deviceidle whitelist -" + STUB_PACKAGE_NAME);
        executeShellCmd("cmd deviceidle whitelist -" + HEARTBEAT_PACKAGE);
        executeShellCmd("am force-stop " + STUB_PACKAGE_NAME);
        executeShellCmd("am force-stop " + HEARTBEAT_PACKAGE);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();

        removeTestUserIfNecessary();
        mHandlerThread.quitSafely();
        if (mDataAnrSettings != null) {
            mDataAnrSettings.close();
        }
        if (mHiddenApiSettings != null) {
            mHiddenApiSettings.close();
        }
    }

    private int createUser(String name, boolean guest) throws Exception {
        final String output = executeShellCmd(
                "pm create-user " + (guest ? "--guest " : "") + name);
        if (output.startsWith("Success")) {
            int userId = Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
            Log.i(TAG, "Created user with id " + userId);
            return userId;
        }
        throw new IllegalStateException(String.format("Failed to create user: %s", output));
    }

    private boolean removeUser(final int userId) throws Exception {
        final String output = executeShellCmd("pm remove-user %s", userId);
        if (output.startsWith("Error")) {
            Log.w(TAG, "Failed to remove user: " + output);
            return false;
        }
        return true;
    }

    private boolean startUser(int userId, boolean waitFlag) throws Exception {
        String cmd = "am start-user " + (waitFlag ? "-w " : "") + userId;

        final String output = executeShellCmd(cmd);
        if (output.startsWith("Error")) {
            Log.w(TAG, "Failed to start user: " + output);
            return false;
        }
        if (waitFlag) {
            String state = executeShellCmd("am get-started-user-state " + userId);
            if (!state.contains("RUNNING_UNLOCKED")) {
                return false;
            }
        }
        return true;
    }

    private boolean stopUser(int userId, boolean waitFlag, boolean forceFlag)
            throws Exception {
        StringBuilder cmd = new StringBuilder("am stop-user ");
        if (waitFlag) {
            cmd.append("-w ");
        }
        if (forceFlag) {
            cmd.append("-f ");
        }
        cmd.append(userId);

        final String output = executeShellCmd(cmd.toString());
        if (output.contains("Error: Can't stop system user")) {
            return false;
        }
        return true;
    }

    private void installExistingPackageAsUser(String packageName, int userId)
            throws Exception {

        // Makes sure package doesn't exist yet, otherwise pm install will hang
        assertWithMessage("package %s for user %s exists", packageName, userId)
                .that(isPackageInstalledAsUser(packageName, userId)).isFalse();

        Log.i(TAG, "installing existing " + packageName + " on user" + userId);
        executeShellCmd("pm install-existing --user %d --wait %s", userId, packageName);
    }

    private boolean isPackageInstalledAsUser(String packageName, int userId) throws Exception {
        String output = executeShellCmd("pm list packages --user %d %s", userId, packageName);
        return output.contains("package:" + packageName + "\n");
    }

    private String executeShellCmd(String cmdFormat, Object... args) throws Exception {
        String cmd = String.format(cmdFormat, args);
        String result = SystemUtil.runShellCommand(mInstrumentation, cmd);
        Log.d(TAG, String.format("Output for '%s': %s", cmd, result));
        return result;
    }

    private void awaitForLatch(CountDownLatch latch, String reasonFormat,
            Object... reasonArgs) {
        awaitForLatch(latch, WAITFOR_MSEC, reasonFormat, reasonArgs);
    }

    private void awaitForLatch(CountDownLatch latch, long timeout, String reasonFormat,
            Object... reasonArgs) {
        String reason = String.format(reasonFormat, reasonArgs);
        Log.d(TAG, "waiting " + WAITFOR_MSEC + " for " + reason);
        try {
            assertTrue("Timeout for waiting", latch.await(timeout, TimeUnit.MILLISECONDS));
            Log.d(TAG, "latch counted down");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted");
        }
    }

    // Start the target package
    private void startService(int commandCode, String serviceName, boolean waitForGone,
            boolean other) {
        startService(commandCode, serviceName, waitForGone, true, other, false, null);
    }

    private void startService(int commandCode, String serviceName, boolean waitForGone,
            boolean waitForIdle, boolean other, boolean includeCookie, byte[] cookie) {
        Intent intent = new Intent(EXIT_ACTION);
        intent.setClassName(STUB_PACKAGE_NAME, serviceName);
        intent.putExtra(EXTRA_ACTION, commandCode);
        intent.putExtra(EXTRA_MESSENGER, mMessenger);
        if (includeCookie) {
            intent.putExtra(EXTRA_COOKIE, cookie);
        }
        mLatch = new CountDownLatch(1);
        UserHandle user = other ? mOtherUserHandle : mCurrentUserHandle;
        WatchUidRunner watcher = other ? mOtherUidWatcher : mWatcher;
        Log.i(TAG, "Starting service " + serviceName + ": waitForGone=" + waitForGone
                + ", waitForIdle=" + waitForIdle + ",intent=" + intent + ", user=" + user);
        mContext.startServiceAsUser(intent, user);
        if (waitForIdle) {
            watcher.waitFor(WatchUidRunner.CMD_IDLE, null);
        }
        if (waitForGone) {
            waitForGone(watcher);
        }
        awaitForLatch(mLatch, "service %s to start on user %s", serviceName, user);
    }

    private void startIsolatedService(int commandCode, String serviceName) {
        Intent intent = new Intent(EXIT_ACTION);
        intent.setClassName(STUB_PACKAGE_NAME, serviceName);
        intent.putExtra(EXTRA_ACTION, commandCode);
        intent.putExtra(EXTRA_MESSENGER, mMessenger);
        mLatch = new CountDownLatch(1);
        mContext.startServiceAsUser(intent, mCurrentUserHandle);
        awaitForLatch(mLatch, "service %s to start", serviceName);
    }

    private void waitForGone(WatchUidRunner watcher) {
        watcher.waitFor(WatchUidRunner.CMD_GONE, null);
        // Give a few seconds to generate the exit report.
        sleep(WAITFOR_SETTLE_DOWN);
    }

    private void clearHistoricalExitInfo() throws Exception {
        executeShellCmd("am clear-exit-info --user all " + STUB_PACKAGE_NAME);
    }

    private void sleep(long timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
        }
    }

    private List<ApplicationExitInfo> getHistoricalProcessExitReasonsAsUser(
            final String packageName, final int pid, final int max, final int userId) {
        Context context = mContext.createContextAsUser(UserHandle.of(userId), 0);
        ActivityManager am = context.getSystemService(ActivityManager.class);
        return am.getHistoricalProcessExitReasons(packageName, pid, max);
    }

    @Test
    public void testExitCode() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();
        // Start a process and let it call System.exit() right away.
        startService(ACTION_EXIT, STUB_SERVICE_NAME, true, false);

        long now2 = System.currentTimeMillis();
        // Query with the current package name, but the mStubPackagePid belongs to the
        // target package, so the below call should return an empty result.
        List<ApplicationExitInfo> list = null;
        try {
            list = mActivityManager.getHistoricalProcessExitReasons(
                    STUB_PACKAGE_NAME, mStubPackagePid, 1);
            fail("Shouldn't be able to query other package");
        } catch (SecurityException e) {
            // expected
        }

        // Now query with the advanced version
        try {
            list = getHistoricalProcessExitReasonsAsUser(STUB_PACKAGE_NAME,
                    mStubPackagePid, 1, mCurrentUserId);
            fail("Shouldn't be able to query other package");
        } catch (SecurityException e) {
            // expected
        }

        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1, mCurrentUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE, null, now, now2);
    }

    private List<ServiceConnection> fillUpMemoryAndCheck(
            final MemoryConsumerService.TestFuncInterface testFunc,
            final List<ApplicationExitInfo> list) throws Exception {
        final String procNamePrefix = "memconsumer_";
        final ArrayList<ServiceConnection> memConsumers = new ArrayList<>();
        Pair<IBinder, ServiceConnection> p = MemoryConsumerService.bindToService(
                mContext, testFunc, procNamePrefix + mProcSeqNum++);
        IBinder consumer = p.first;
        memConsumers.add(p.second);

        while (list.size() == 0) {
            // Get the meminfo firstly
            MemInfoReader reader = new MemInfoReader();
            reader.readMemInfo();

            long totalMb = (reader.getFreeSizeKb() + reader.getCachedSizeKb()) >> 10;
            if (!MemoryConsumerService.runOnce(consumer, totalMb) && list.size() == 0) {
                // Need to create a new consumer (the present one might be running out of space)
                p = MemoryConsumerService.bindToService(mContext, testFunc,
                        procNamePrefix + mProcSeqNum++);
                consumer = p.first;
                memConsumers.add(p.second);
            }
            // make sure we have cached process killed
            String output = executeShellCmd("dumpsys activity lru");
            if (output == null && output.indexOf(" cch+") == -1) {
                break;
            }
        }

        return memConsumers;
    }

    @Test
    public void testLmkdKill() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();
        boolean lmkdReportSupported = ActivityManager.isLowMemoryKillReportSupported();

        // Start a process and do nothing
        startService(ACTION_FINISH, STUB_SERVICE_NAME, false, false);

        final ArrayList<IBinder> memConsumers = new ArrayList<>();
        List<ApplicationExitInfo> list = new ArrayList<>();
        final MemoryConsumerService.TestFuncInterface testFunc =
                new MemoryConsumerService.TestFuncInterface(() -> {
                    final long token = Binder.clearCallingIdentity();
                    try {
                        List<ApplicationExitInfo> result =
                                ShellIdentityUtils.invokeMethodWithShellPermissions(
                                        STUB_PACKAGE_NAME, mStubPackagePid, 1,
                                        mActivityManager::getHistoricalProcessExitReasons,
                                        android.Manifest.permission.DUMP);
                        if (result != null && result.size() == 1) {
                            list.add(result.get(0));
                            return true;
                        }
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                    return false;
                });

        List<ServiceConnection> services = fillUpMemoryAndCheck(testFunc, list);

        // Unbind all the service connections firstly
        for (int i = services.size() - 1; i >= 0; i--) {
            mContext.unbindService(services.get(i));
        }

        long now2 = System.currentTimeMillis();
        assertTrue(list != null && list.size() == 1);
        ApplicationExitInfo info = list.get(0);
        assertNotNull(info);
        if (lmkdReportSupported) {
            verify(info, mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                    ApplicationExitInfo.REASON_LOW_MEMORY, null, null, now, now2);
        } else {
            verify(info, mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                    ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now, now2);
        }
    }

    @Test
    public void testKillBySignal() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();

        // Start a process and kill itself
        startService(ACTION_KILL, STUB_SERVICE_NAME, true, false);

        long now2 = System.currentTimeMillis();
        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now, now2);
    }

    @Test
    public void testAnr() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        final DropBoxManager dbox = mContext.getSystemService(DropBoxManager.class);
        final CountDownLatch dboxLatch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String tag_anr = "data_app_anr";
                if (tag_anr.equals(intent.getStringExtra(DropBoxManager.EXTRA_TAG))) {
                    mAnrEntry = dbox.getNextEntry(tag_anr, intent.getLongExtra(
                            DropBoxManager.EXTRA_TIME, 0) - 1);
                    Log.d(TAG, "Counting down latch onReceive(" + intent + ")");
                    dboxLatch.countDown();
                }
            }
        };
        mContext.registerReceiver(receiver,
                new IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED));
        final long timeout = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.BROADCAST_FG_CONSTANTS, 10 * 1000) * 3;

        long now = System.currentTimeMillis();

        // Start a process and block its main thread
        startService(ACTION_ANR, STUB_SERVICE_NAME, false, false);

        // Sleep for a while to make sure it's already blocking its main thread.
        sleep(WAITFOR_MSEC);

        AmMonitor monitor = new AmMonitor(mInstrumentation,
                new String[]{AmMonitor.WAIT_FOR_CRASHED});

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(STUB_PACKAGE_NAME, STUB_RECEIVER_NAME));
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        // This will result an ANR
        mContext.sendOrderedBroadcast(intent, null);

        // Wait for the early ANR
        monitor.waitFor(AmMonitor.WAIT_FOR_EARLY_ANR, timeout);
        // Continue, so we could collect ANR traces
        monitor.sendCommand(AmMonitor.CMD_CONTINUE);
        // Wait for the ANR
        monitor.waitFor(AmMonitor.WAIT_FOR_ANR, timeout);
        // Kill it
        monitor.sendCommand(AmMonitor.CMD_KILL);
        // Wait the process gone
        waitForGone(mWatcher);
        long now2 = System.currentTimeMillis();

        awaitForLatch(dboxLatch, "broadcast for %s be received",
                DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED);
        assertTrue(mAnrEntry != null);

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        ApplicationExitInfo info = list.get(0);
        verify(info, mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_ANR, null, null, now, now2);
        assertEquals(mStubPackageUid, info.getPackageUid());
        assertEquals(mStubPackageUid, info.getDefiningUid());

        // Verify the traces

        // Read from dropbox
        final String dboxTrace = mAnrEntry.getText(0x100000 /* 1M */);
        assertFalse(TextUtils.isEmpty(dboxTrace));

        // Read the input stream from the ApplicationExitInfo
        String trace = ShellIdentityUtils.invokeMethodWithShellPermissions(info, (i) -> {
            try (BufferedInputStream input = new BufferedInputStream(i.getTraceInputStream())) {
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[8192];
                while (true) {
                    final int len = input.read(buf, 0, buf.length);
                    if (len <= 0) {
                        break;
                    }
                    sb.append(new String(buf, 0, len));
                }
                return sb.toString();
            } catch (IOException e) {
                return null;
            }
        }, android.Manifest.permission.DUMP);
        assertFalse(TextUtils.isEmpty(trace));
        assertTrue(trace.indexOf(Integer.toString(info.getPid())) >= 0);
        assertTrue(trace.indexOf("Cmd line: " + STUB_PACKAGE_NAME) >= 0);
        assertTrue(dboxTrace.indexOf(trace) >= 0);

        monitor.finish();
        mContext.unregisterReceiver(receiver);
    }

    @Test
    public void testOther() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        final String servicePackage = "android.externalservice.service";
        final String keyIBinder = "ibinder";
        final CountDownLatch latch = new CountDownLatch(1);
        final Bundle holder = new Bundle();
        final ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                holder.putBinder(keyIBinder, service);
                Log.d(TAG, "Counting down latch onServiceConnected(" + name + ")");
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        final Intent intent = new Intent();
        ComponentName serviceComponent = new ComponentName(servicePackage,
                servicePackage + ".ExternalServiceWithZygote");
        intent.setComponent(serviceComponent);

        // Bind to that external service, which will become an isolated process
        // running in the current package user id.
        assertTrue(mContext.bindService(intent,
                Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE,
                AsyncTask.THREAD_POOL_EXECUTOR, connection));

        awaitForLatch(latch, "service %s to bind", serviceComponent.flattenToShortString());

        final IBinder service = holder.getBinder(keyIBinder);
        assertNotNull(service);

        // Retrieve its uid/pd/package name info.
        Messenger remote = new Messenger(service);
        RunningServiceInfo id = identifyService(remote);
        assertNotNull(id);

        assertFalse(id.uid == 0 || id.pid == 0);
        assertFalse(Process.myUid() == id.uid);
        assertFalse(Process.myPid() == id.pid);
        assertEquals(mContext.getPackageName(), id.packageName);

        final WatchUidRunner watcher = new WatchUidRunner(mInstrumentation,
                id.uid, WAITFOR_MSEC);

        long now = System.currentTimeMillis();

        // Remove the service connection
        mContext.unbindService(connection);

        try {
            // Isolated process should have been killed as long as its service is done.
            waitForGone(watcher);
        } finally {
            watcher.finish();
        }

        long now2 = System.currentTimeMillis();
        final ActivityManager am = mContext.getSystemService(ActivityManager.class);
        final List<ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(null, id.pid, 1);
        assertTrue(list != null && list.size() == 1);

        ApplicationExitInfo info = list.get(0);
        verify(info, id.pid, id.uid, null, ApplicationExitInfo.REASON_OTHER, null,
                "isolated not needed", now, now2);
        assertEquals(Process.myUid(), info.getPackageUid());
        assertEquals(mContext.getPackageManager().getPackageUid(servicePackage, 0),
                info.getDefiningUid());
        assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE,
                info.getImportance());
    }

    private String extractMemString(String dump, String prefix, char nextSep) {
        int start = dump.indexOf(prefix);
        assertTrue(start >= 0);
        start += prefix.length();
        int end = dump.indexOf(nextSep, start);
        assertTrue(end > start);
        return dump.substring(start, end);
    }

    @Test
    public void testPermissionChange() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        // Grant the read calendar permission
        mInstrumentation.getUiAutomation().grantRuntimePermission(
                STUB_PACKAGE_NAME, android.Manifest.permission.READ_CALENDAR);
        long now = System.currentTimeMillis();

        // Start a process and do nothing
        startService(ACTION_FINISH, STUB_SERVICE_NAME, false, false);

        // Enable high frequency memory sampling
        executeShellCmd("dumpsys procstats --start-testing");
        // Sleep for a while to wait for the sampling of memory info
        sleep(10000);
        // Stop the high frequency memory sampling
        executeShellCmd("dumpsys procstats --stop-testing");
        // Get the memory info from it.
        String dump = executeShellCmd("dumpsys activity processes " + STUB_PACKAGE_NAME);
        assertNotNull(dump);
        String lastPss = null;
        String lastRss = null;
        if (!Flags.removeAppProfilerPssCollection()) {
            lastPss = extractMemString(dump, " lastPss=", ' ');
            lastRss = extractMemString(dump, " lastRss=", '\n');
        } else {
            // lastRss is not the final field in the dump, so the next separator is not a newline.
            lastRss = extractMemString(dump, " lastRss=", ' ');
        }

        // Revoke the read calendar permission
        mInstrumentation.getUiAutomation().revokeRuntimePermission(
                STUB_PACKAGE_NAME, android.Manifest.permission.READ_CALENDAR);
        waitForGone(mWatcher);
        long now2 = System.currentTimeMillis();

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);

        ApplicationExitInfo info = list.get(0);
        verify(info, mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_PERMISSION_CHANGE, null, null, now, now2);

        // Also verify that we get the expected meminfo
        if (!Flags.removeAppProfilerPssCollection()) {
            assertEquals(lastPss, DebugUtils.sizeValueToString(
                    info.getPss() * 1024, new StringBuilder()));
        }
        assertEquals(lastRss, DebugUtils.sizeValueToString(
                info.getRss() * 1024, new StringBuilder()));
    }

    // A clone of testPermissionChange using a different revoke api
    @Test
    public void testPermissionChangeWithReason() throws Exception {
        String revokeReason = "test reason";
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        // Grant the read calendar permission
        mInstrumentation.getUiAutomation().grantRuntimePermission(
                STUB_PACKAGE_NAME, android.Manifest.permission.READ_CALENDAR);
        long now = System.currentTimeMillis();

        // Start a process and do nothing
        startService(ACTION_FINISH, STUB_SERVICE_NAME, false, false);

        // Enable high frequency memory sampling
        executeShellCmd("dumpsys procstats --start-testing");
        // Sleep for a while to wait for the sampling of memory info
        sleep(10000);
        // Stop the high frequency memory sampling
        executeShellCmd("dumpsys procstats --stop-testing");
        // Get the memory info from it.
        String dump = executeShellCmd("dumpsys activity processes " + STUB_PACKAGE_NAME);
        assertNotNull(dump);
        String lastPss = null;
        String lastRss = null;
        if (!Flags.removeAppProfilerPssCollection()) {
            lastPss = extractMemString(dump, " lastPss=", ' ');
            lastRss = extractMemString(dump, " lastRss=", '\n');
        } else {
            // lastRss is not the final field in the dump, so the next separator is not a newline.
            lastRss = extractMemString(dump, " lastRss=", ' ');
        }

        // Revoke the read calendar permission
        runWithShellPermissionIdentity(() -> {
            mContext.getPackageManager().revokeRuntimePermission(STUB_PACKAGE_NAME,
                    android.Manifest.permission.READ_CALENDAR, Process.myUserHandle(),
                    revokeReason);
        });
        waitForGone(mWatcher);
        long now2 = System.currentTimeMillis();

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);

        ApplicationExitInfo info = list.get(0);
        verify(info, mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_PERMISSION_CHANGE, null, null, now, now2);
        assertEquals(revokeReason, info.getDescription());

        // Also verify that we get the expected meminfo
        if (!Flags.removeAppProfilerPssCollection()) {
            assertEquals(lastPss, DebugUtils.sizeValueToString(
                    info.getPss() * 1024, new StringBuilder()));
        }
        assertEquals(lastRss, DebugUtils.sizeValueToString(
                info.getRss() * 1024, new StringBuilder()));
    }

    @Test
    public void testCrash() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();

        // Start a process and do nothing
        startService(ACTION_NONE, STUB_SERVICE_NAME, false, false);

        // Induce a crash
        executeShellCmd("am crash " + STUB_PACKAGE_NAME);
        waitForGone(mWatcher);
        long now2 = System.currentTimeMillis();

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_CRASH, null, null, now, now2);
    }

    @Test
    public void testNativeCrash() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();

        // Start a process and crash it
        startService(ACTION_NATIVE_CRASH, STUB_SERVICE_NAME, true, false);

        // Native crashes are handled asynchronously from the actual crash, so
        // it's possible for us to notice that the process crashed before an
        // actual tombstone exists.
        Thread.sleep(1000);

        long now2 = System.currentTimeMillis();
        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_CRASH_NATIVE, null, null, now, now2);

        TombstoneFetcher tombstoneFetcher = new TombstoneFetcher(list.get(0));
        PollingCheck.check("not able to get tombstone", TOMBSTONE_FETCH_TIMEOUT_MS,
                () -> tombstoneFetcher.fetchTrace());

        InputStream trace = tombstoneFetcher.getTrace();
        assertNotNull(trace);
        Tombstone tombstone = Tombstone.parseFrom(trace);
        assertEquals(tombstone.getPid(), mStubPackagePid);
    }

    @Test
    public void testUserRequested() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();

        // Start a process and do nothing
        startService(ACTION_NONE, STUB_SERVICE_NAME, false, false);

        // Force stop the test package
        executeShellCmd("am force-stop " + STUB_PACKAGE_NAME);

        // Wait the process gone
        waitForGone(mWatcher);

        long now2 = System.currentTimeMillis();
        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_USER_REQUESTED, null, null, now, now2);
    }

    @Test
    public void testPackageDisabled() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        // Start a process and do nothing
        startService(ACTION_NONE, STUB_SERVICE_NAME, false, false);

        //disable the app and kill it
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
        PackageManager packageManager = mContext.getPackageManager();
        packageManager.setApplicationEnabledSetting(
                STUB_PACKAGE_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                0);

        waitForGone(mWatcher);

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        assertEquals(ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE, list.get(0).getReason());
        assertEquals(ApplicationExitInfo.SUBREASON_UNKNOWN, list.get(0).getSubReason());
    }

    @Test
    public void testPackageUpdated() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        // Start a process and do nothing
        startService(ACTION_NONE, STUB_SERVICE_NAME, false, false);

        // Update the package
        executeShellCmd("pm install -r /data/local/tmp/cts/content/CtsSimpleApp.apk");

        waitForGone(mWatcher);

        List<ApplicationExitInfo> list =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        STUB_PACKAGE_NAME, mStubPackagePid, 1,
                        mActivityManager::getHistoricalProcessExitReasons,
                        Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        assertEquals(ApplicationExitInfo.REASON_PACKAGE_UPDATED, list.get(0).getReason());
        assertEquals(ApplicationExitInfo.SUBREASON_UNKNOWN, list.get(0).getSubReason());
    }

    @Test
    public void testDependencyDied() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        // Start a process and acquire the provider
        startService(ACTION_ACQUIRE_STABLE_PROVIDER, STUB_SERVICE_NAME, false, false);

        final ActivityManager am = mContext.getSystemService(ActivityManager.class);
        long now = System.currentTimeMillis();
        final long timeout = now + WAITFOR_MSEC;
        int providerPid = -1;
        while (now < timeout && providerPid < 0) {
            sleep(1000);
            List<RunningAppProcessInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    am, ActivityManager::getRunningAppProcesses,
                    android.Manifest.permission.REAL_GET_TASKS);
            for (RunningAppProcessInfo info: list) {
                if (info.processName.equals(STUB_REMOTE_PROCESS_NAME)) {
                    providerPid = info.pid;
                    break;
                }
            }
            now = System.currentTimeMillis();
        }
        assertTrue(providerPid > 0);

        now = System.currentTimeMillis();
        // Now let the provider exit itself
        startService(ACTION_KILL_PROVIDER, STUB_SERVICE_NAME, false, false, false, false, null);

        // Wait for both of the processes gone
        waitForGone(mWatcher);
        final long now2 = System.currentTimeMillis();

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_DEPENDENCY_DIED, null, null, now, now2);
    }

    @Test
    public void testMultipleProcess() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();

        // Start a process and kill itself
        startService(ACTION_KILL, STUB_SERVICE_NAME, true, false);

        long now2 = System.currentTimeMillis();

        // Start a remote process and exit
        startService(ACTION_EXIT, STUB_SERVICE_REMOTE_NAME, true, false);

        long now3 = System.currentTimeMillis();
        // Now to get the two reports
        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 2,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 2);
        verify(list.get(0), mStubPackageRemotePid, mStubPackageUid, STUB_REMOTE_PROCESS_NAME,
                ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE, null, now2, now3);
        verify(list.get(1), mStubPackagePid, mStubPackageUid, STUB_PROCESS_NAME,
                ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now, now2);

        // If we only retrieve one report
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackageRemotePid, mStubPackageUid, STUB_REMOTE_PROCESS_NAME,
                ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE, null, now2, now3);
    }

    private RunningServiceInfo identifyService(Messenger service) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        class IdentifyHandler extends Handler {
            IdentifyHandler() {
                super(Looper.getMainLooper());
            }

            RunningServiceInfo mInfo;

            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "Received message: " + msg);
                switch (msg.what) {
                    case ServiceMessages.MSG_IDENTIFY_RESPONSE:
                        msg.getData().setClassLoader(RunningServiceInfo.class.getClassLoader());
                        mInfo = msg.getData().getParcelable(ServiceMessages.IDENTIFY_INFO);
                        Log.d(TAG, "Counting down latch on IdentifyHandler msg: " + msg);
                        latch.countDown();
                        break;
                }
                super.handleMessage(msg);
            }
        }

        IdentifyHandler handler = new IdentifyHandler();
        Messenger local = new Messenger(handler);

        Message msg = Message.obtain(null, ServiceMessages.MSG_IDENTIFY);
        msg.replyTo = local;
        service.send(msg);
        awaitForLatch(latch, "service to receive message");

        return handler.mInfo;
    }

    private void prepareTestUser() throws Exception {
        Log.d(TAG, "prepareTestUser()");
        // Create the test user
        mOtherUserId = createUser("TestUser_" + SystemClock.uptimeMillis(), false);
        Log.d(TAG, "user created: " + mOtherUserId);
        mOtherUserHandle = UserHandle.of(mOtherUserId);
        // Start the other user
        assertTrue(startUser(mOtherUserId, true));
        Log.d(TAG, "user started");
        // Install the test helper APK into the other user
        installExistingPackageAsUser(STUB_PACKAGE_NAME, mOtherUserId);
        installExistingPackageAsUser(mContext.getPackageName(), mOtherUserId);
        mStubPackageOtherUid = mContext.getPackageManager().getPackageUidAsUser(
                STUB_PACKAGE_NAME, 0, mOtherUserId);
        Log.d(TAG, "UID of " + STUB_PACKAGE_NAME + ": " + mStubPackageOtherUid);
        mOtherUidWatcher = new WatchUidRunner(mInstrumentation, mStubPackageOtherUid,
                WAITFOR_MSEC);
    }

    private void removeTestUserIfNecessary() throws Exception {
        if (mSupportMultipleUsers && mOtherUserId > 0) {
            // Stop the test user
            assertTrue(stopUser(mOtherUserId, true, true));
            // Remove the test user
            removeUser(mOtherUserId);
            if (mOtherUidWatcher != null) {
                mOtherUidWatcher.finish();
            }
        }
    }

    @Test
    public void testSecondaryUser() throws Exception {
        if (!mSupportMultipleUsers) {
            return;
        }

        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        // Get the full user permission in order to start service as other user
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.INTERACT_ACROSS_USERS,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        // Create the test user, we'll remove it during tearDown
        prepareTestUser();

        final byte[] cookie0 = {(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
                (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07};
        final byte[] cookie1 = {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08};
        final byte[] cookie2 = {(byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x01};
        final byte[] cookie3 = {(byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06,
                (byte) 0x07, (byte) 0x08, (byte) 0x01, (byte) 0x02};
        final byte[] cookie4 = {(byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                (byte) 0x08, (byte) 0x01, (byte) 0x02, (byte) 0x03};
        final byte[] cookie5 = null;

        long now = System.currentTimeMillis();

        // Start a process and do nothing
        startService(ACTION_NONE, STUB_SERVICE_NAME, false, true, false, true, cookie0);
        // request to exit by itself with a different cookie
        startService(ACTION_EXIT, STUB_SERVICE_NAME, true, false, false, true, cookie1);

        long now2 = System.currentTimeMillis();

        // Start the process in a secondary user and kill itself
        startService(ACTION_KILL, STUB_SERVICE_NAME, true, true, true, true, cookie2);

        long now3 = System.currentTimeMillis();

        // Start a remote process in a secondary user and exit
        startService(ACTION_EXIT, STUB_SERVICE_REMOTE_NAME, true, true, true, true, cookie3);

        long now4 = System.currentTimeMillis();

        // Start a remote process and kill itself
        startService(ACTION_KILL, STUB_SERVICE_REMOTE_NAME, true, true, false, true, cookie4);

        long now5 = System.currentTimeMillis();
        // drop the permissions
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();

        List<ApplicationExitInfo> list = null;

        // Now try to query for all users
        try {
            list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 0, 0, UserHandle.USER_ALL,
                    this::getHistoricalProcessExitReasonsAsUser,
                    android.Manifest.permission.DUMP);
            fail("Shouldn't be able to query all users");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Now try to query for "current" user
        try {
            list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 0, 0, UserHandle.USER_CURRENT,
                    this::getHistoricalProcessExitReasonsAsUser,
                    android.Manifest.permission.DUMP);
            fail("Shouldn't be able to query current user, explicit user-Id is expected");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Now only try the current user
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 0, mCurrentUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 2);
        verify(list.get(0), mStubPackageRemotePid, mStubPackageUid, STUB_REMOTE_PROCESS_NAME,
                ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now4, now5,
                cookie4);
        verify(list.get(1), mStubPackagePid, mStubPackageUid, STUB_PROCESS_NAME,
                ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE, null, now, now2, cookie1);

        // Now try the other user
        try {
            list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 0, 0, mOtherUserId,
                    this::getHistoricalProcessExitReasonsAsUser,
                    android.Manifest.permission.DUMP);
            fail("Shouldn't be able to query other users");
        } catch (SecurityException e) {
            // expected
        }

        // Now try the other user with proper permissions
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 0, mOtherUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP,
                android.Manifest.permission.INTERACT_ACROSS_USERS);

        assertTrue(list != null && list.size() == 2);
        verify(list.get(0), mStubPackageRemoteOtherUserPid, mStubPackageOtherUid,
                STUB_REMOTE_PROCESS_NAME, ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE,
                null, now3, now4, cookie3);
        verify(list.get(1), mStubPackageOtherUserPid, mStubPackageOtherUid, STUB_PROCESS_NAME,
                ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null,
                now2, now3, cookie2);

        // Get the full user permission in order to start service as other user
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.INTERACT_ACROSS_USERS,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        // Start the process in a secondary user and do nothing
        startService(ACTION_NONE, STUB_SERVICE_NAME, false, true, true, true, cookie5);
        // drop the permissions
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();

        long now6 = System.currentTimeMillis();
        // Stop the test user
        assertTrue(stopUser(mOtherUserId, true, true));
        // Wait for being killed
        waitForGone(mOtherUidWatcher);

        long now7 = System.currentTimeMillis();
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 1, mOtherUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP,
                android.Manifest.permission.INTERACT_ACROSS_USERS);
        verify(list.get(0), mStubPackageOtherUserPid, mStubPackageOtherUid, STUB_PROCESS_NAME,
                ApplicationExitInfo.REASON_USER_STOPPED, null, null, now6, now7, cookie5);

        int otherUserId = mOtherUserId;
        // Now remove the other user
        removeUser(mOtherUserId);
        mOtherUidWatcher.finish();
        mOtherUserId = 0;

        // Poll userInfo to check if the user has been removed, wait up to 10 mins
        for (int i = 0; i < 600; i++) {
            if (ShellIdentityUtils.invokeMethodWithShellPermissions(otherUserId,
                    mUserManager::getUserInfo,
                    android.Manifest.permission.CREATE_USERS) != null) {
                // We can still get the userInfo, sleep 1 second and try again
                sleep(1000);
            } else {
                Log.d(TAG, "User " + otherUserId + " has been removed");
                break;
            }
        }
        // For now the ACTION_USER_REMOVED should have been sent to all receives,
        // we take an extra nap to make sure we've had the broadcast handling settled.
        sleep(15 * 1000);

        // Now query the other userId, and it should return nothing.
        final Context context = mContext.createPackageContextAsUser("android", 0,
                UserHandle.of(otherUserId));
        final ActivityManager am = context.getSystemService(ActivityManager.class);
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 0,
                am::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP,
                android.Manifest.permission.INTERACT_ACROSS_USERS);
        assertTrue(list == null || list.size() == 0);

        // The current user shouldn't be impacted.
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 0, mCurrentUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP,
                android.Manifest.permission.INTERACT_ACROSS_USERS);

        assertTrue(list != null && list.size() == 2);
        verify(list.get(0), mStubPackageRemotePid, mStubPackageUid, STUB_REMOTE_PROCESS_NAME,
                ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now4, now5,
                cookie4);
        verify(list.get(1), mStubPackagePid, mStubPackageUid, STUB_PROCESS_NAME,
                ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE, null, now, now2, cookie1);
    }

    /**
     * By design, an app's process in cached state is subject to being killed due
     * to system memory pressure. Any work in this state, e.g. an {@link Activity}
     * trying to execute extra code after the {@link Activity#onStop()} method has
     * been called and returned, is unreliable and strongly discouraged. For more
     * details see <a
     * href="https://developer.android.com/guide/components/activities/process-lifecycle">
     * Processes and app lifecycle</a>.
     * <p>
     * Starting in {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE}, OS
     * enforces cached-app resource usage. This test checks whether the Freezer
     * has been correctly enabled to be consistent with the documented developer
     * expectations.
     */
    @CddTest(requirements = {"3.5/C-0-2"})
    @Test
    public void testFreezerEnabled() throws Exception {
        if (FIRST_SDK_IS_AT_LEAST_U
                && SystemProperties.get("ro.kernel.version").compareTo("5") >= 0) {
            // We expect all devices with kernel 5.x that first shipped with U to support Freezer
            assertTrue(ActivityManager.getService().isAppFreezerSupported());
        } else {
            // For old devices OTA'ed to U, check if Linux kernel and vendor partition is too old
            assumeTrue(ActivityManager.getService().isAppFreezerSupported());
        }

        // Freezer must be enabled as long as it's supported
        assertTrue(ActivityManager.getService().isAppFreezerEnabled());

        // Check dumpsys to verify the Freezer configurations in use
        final String output = executeShellCmd("dumpsys activity");
        Pattern pattern = Pattern.compile("freeze_debounce_timeout=(\\d+)");
        Matcher matcher = pattern.matcher(output);
        assertTrue(matcher.find());
        final long timeout = Long.parseLong(matcher.group(1));
        assertTrue(timeout >= FREEZER_TIMEOUT_FLOOR);
    }

    @Test
    public void testFreezerNormalExitCode() throws Exception {
        // The app should NOT be frozen with 30s freeze timeout configuration
        runFreezerTest(HEARTBEAT_FREEZER_LONG, false, ApplicationExitInfo.REASON_SIGNALED);
    }

    @Test
    public void testFreezerKillExitCode() throws Exception {
        // The app should be frozen and killed with 5s freeze timeout configuration
        assumeTrue(ActivityManager.getService().isAppFreezerEnabled());
        runFreezerTest(HEARTBEAT_FREEZER_SHORT, true, ApplicationExitInfo.REASON_FREEZER);
    }

    public void runFreezerTest(long freezerTimeout, boolean dead, int reason) throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        executeShellCmd(
                "device_config put activity_manager_native_boot freeze_debounce_timeout "
                        + freezerTimeout);

        long now = System.currentTimeMillis();

        mLatch = new CountDownLatch(1);

        // Start the HeartbeatService to wait for HeartbeatActivity
        Intent serviceIntent = new Intent(HEARTBEAT_SERVICE);
        serviceIntent.setPackage(HEARTBEAT_PACKAGE);
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected(" + name + "): " + service);
                IHeartbeat heartbeat = IHeartbeat.Stub.asInterface(service);
                try {
                    heartbeat.monitor(mMessenger);
                } catch (RemoteException e) {
                    fail("Failed to monitor Heartbeat service");
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        mContext.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        // Launch the HeartbeatActivity to talk to the HeartbeatService
        Intent clientIntent = new Intent(Intent.ACTION_MAIN);
        clientIntent.setClassName(HEARTBEAT_PACKAGE, HEARTBEAT_ACTIVITY);
        clientIntent.putExtra(HEARTBEAT_COUNTDOWN_NAME, HEARTBEAT_COUNTDOWN);
        clientIntent.putExtra(HEARTBEAT_INTERVAL_NAME, HEARTBEAT_INTERVAL);
        clientIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(clientIntent);
        sleep(1000);

        // Launch another app to bring the HeartbeatActivity to background
        Intent intent1 = new Intent(Intent.ACTION_MAIN);
        intent1.setClassName(STUB_PACKAGE_NAME, STUB_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent1);
        sleep(1000);

        // Launch another activity to make sure the HeartbeatActivity is in cached mode
        Intent intent2 = new Intent(Intent.ACTION_MAIN);
        intent2.setClassName(STUB_PACKAGE_NAME, STUB_PACKAGE_NAME + ".NonLauncherActivity");
        intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent2);

        // Wait until the HeartbeatService finishes
        awaitForLatch(mLatch, HEARTBEAT_COUNTDOWN * HEARTBEAT_INTERVAL, "heartbeat");
        mContext.unbindService(connection);
        sleep(1000);

        // Check if the frozen app is killed
        assertEquals(dead, mHeartbeatDead);
        int uid = mContext.getPackageManager().getPackageUid(HEARTBEAT_PACKAGE,
                PackageManager.PackageInfoFlags.of(0));
        assertEquals(uid, mStubPackageUid);

        long now2 = System.currentTimeMillis();

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                HEARTBEAT_PACKAGE, mStubPackagePid, 1, mCurrentUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP);

        assertNotNull(list);
        assertEquals(1, list.size());
        verify(list.get(0), mStubPackagePid, uid, HEARTBEAT_PROCESS,
                reason, null, null, now, now2);
    }

    private void verify(ApplicationExitInfo info, int pid, int uid, String processName,
            int reason, Integer status, String description, long before, long after) {
        verify(info, pid, uid, processName, reason, status, description, before, after, null);
    }

    private void verify(ApplicationExitInfo info, int pid, int uid, String processName,
            int reason, Integer status, String description, long before, long after,
            byte[] cookie) {
        assertNotNull(info);
        assertEquals(pid, info.getPid());
        assertEquals(uid, info.getRealUid());
        assertEquals(UserHandle.of(UserHandle.getUserId(uid)), info.getUserHandle());
        if (processName != null) {
            assertEquals(processName, info.getProcessName());
        }
        assertEquals(reason, info.getReason());
        if (status != null) {
            assertEquals(status.intValue(), info.getStatus());
        }

        if (description != null) {
            assertTrue(info.getDescription().contains(description));
        }

        assertTrue(before <= info.getTimestamp());
        assertTrue(after >= info.getTimestamp());
        assertTrue(ArrayUtils.equals(info.getProcessStateSummary(), cookie,
                cookie == null ? 0 : cookie.length));
    }

    private static class TombstoneFetcher {
        private InputStream mTrace = null;
        private final ApplicationExitInfo mExitInfo;

        TombstoneFetcher(ApplicationExitInfo exitInfo) {
            mExitInfo = exitInfo;
        }

        public InputStream getTrace() {
            return mTrace;
        }

        public boolean fetchTrace() throws Exception {
            mTrace = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mExitInfo,
                    (i) -> {
                        try {
                            return i.getTraceInputStream();
                        } catch (IOException ex) {
                            return null;
                        }
                    },
                    android.Manifest.permission.DUMP);
            return (mTrace != null);
        }
    }
}
