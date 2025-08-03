/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK;
import static android.app.ActivityManager.PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;
import static android.app.Flags.FLAG_APP_RESTRICTIONS_API;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN;
import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.OnUidImportanceListener;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.app.Flags;
import android.app.HomeVisibilityListener;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.Instrumentation.ActivityResult;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.app.stubs.ActivityManagerRecentOneActivity;
import android.app.stubs.ActivityManagerRecentTwoActivity;
import android.app.stubs.CommandReceiver;
import android.app.stubs.LocalForegroundService;
import android.app.stubs.MockApplicationActivity;
import android.app.stubs.MockService;
import android.app.stubs.RemoteActivity;
import android.app.stubs.ScreenOnActivity;
import android.app.stubs.TestHomeActivity;
import android.app.stubs.TrimMemService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.NewUserRequest;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.cts.PermissionUtils;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.annotations.RestrictedBuildTest;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.server.wm.WindowManagerStateHelper;
import android.server.wm.settings.SettingsSession;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AmMonitor;
import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.UserHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
@Presubmit
public final class ActivityManagerTest {
    private static final String TAG = ActivityManagerTest.class.getSimpleName();
    private static final String STUB_PACKAGE_NAME = "android.app.stubs";
    private static final long WAITFOR_MSEC = 5000;
    // Long enough to cover devices with doubled hw multipliers. On most devices
    // this should be 10s as defined in ActivityManagerService#PROC_START_TIMEOUT
    private static final long WAITFOR_PROCSTAT_TIMEOUT_MSEC = 30000;
    private static final String SERVICE_NAME = "android.app.stubs.MockService";
    private static final long WAIT_TIME = 2000;
    private static final long WAITFOR_ORDERED_BROADCAST_DRAINED = 60000;
    // A secondary test activity from another APK.
    static final String SIMPLE_PACKAGE_NAME = "com.android.cts.launcherapps.simpleapp";
    static final String SIMPLE_ACTIVITY = ".SimpleActivity";
    static final String SIMPLE_ACTIVITY_IMMEDIATE_EXIT = ".SimpleActivityImmediateExit";
    static final String SIMPLE_ACTIVITY_CHAIN_EXIT = ".SimpleActivityChainExit";
    static final String SIMPLE_RECEIVER = ".SimpleReceiver";
    static final String SIMPLE_REMOTE_RECEIVER = ".SimpleRemoteReceiver";
    // The action sent back by the SIMPLE_APP after a restart.
    private static final String ACTIVITY_LAUNCHED_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.LAUNCHED_ACTION";
    // The action sent back by the SIMPLE_APP_IMMEDIATE_EXIT when it terminates.
    private static final String ACTIVITY_EXIT_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.EXIT_ACTION";
    // The action sent back by the SIMPLE_APP_CHAIN_EXIT when the task chain ends.
    private static final String ACTIVITY_CHAIN_EXIT_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.CHAIN_EXIT_ACTION";
    // The action sent to identify the time track info.
    private static final String ACTIVITY_TIME_TRACK_INFO = "com.android.cts.TIME_TRACK_INFO";

    private static final String PACKAGE_NAME_APP1 = "com.android.app1";
    private static final String PACKAGE_NAME_APP2 = "com.android.app2";
    private static final String PACKAGE_NAME_APP3 = "com.android.app3";
    private static final String PACKAGE_NAME_WEDGED_STARTUP = "com.android.wedged_start";

    private static final String CANT_SAVE_STATE_1_PACKAGE_NAME = "com.android.test.cantsavestate1";
    private static final String ACTION_FINISH = "com.android.test.action.FINISH";

    private static final String MCC_TO_UPDATE = "987";
    private static final String MNC_TO_UPDATE = "654";

    // Return states of the ActivityReceiverFilter.
    public static final int RESULT_PASS = 1;
    public static final int RESULT_FAIL = 2;
    public static final int RESULT_TIMEOUT = 3;

    private static final int PROCESS_CAPABILITY_ALL = PROCESS_CAPABILITY_FOREGROUND_LOCATION
            | PROCESS_CAPABILITY_FOREGROUND_CAMERA
            | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE
            | PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK
            | PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;

    private static final String PACKAGE_SCHEME = "package";

    private Context mTargetContext;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;
    private Intent mIntent;
    private List<Activity> mStartedActivityList;
    private int mErrorProcessID;
    private Instrumentation mInstrumentation;
    private HomeActivitySession mTestHomeSession;
    private boolean mAppStandbyEnabled;
    private boolean mAutomotiveDevice;
    private boolean mLeanbackOnly;
    private boolean mWatchDevice;

    private final UserHelper mUserHelper = new UserHelper();

    private String mPreviousModernTrim;

    private boolean mIsWaitForFinishAttachApplicationEnabled;

    private static final String WRITE_DEVICE_CONFIG_PERMISSION =
            "android.permission.WRITE_DEVICE_CONFIG";

    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    private static final String MONITOR_DEVICE_CONFIG_ACCESS =
            "android.permission.MONITOR_DEVICE_CONFIG_ACCESS";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();
        mActivityManager = mInstrumentation.getContext().getSystemService(ActivityManager.class);
        mPackageManager = mInstrumentation.getContext().getPackageManager();

        mStartedActivityList = new ArrayList<Activity>();
        mErrorProcessID = -1;
        mAppStandbyEnabled = AppStandbyUtils.isAppStandbyEnabled();
        mAutomotiveDevice = mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        mLeanbackOnly = mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY);
        mWatchDevice = mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH);

        startSubActivity(ScreenOnActivity.class);
        AmUtils.waitForBroadcastBarrier();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestHomeSession != null) {
            mTestHomeSession.close();
        }
        if (mIntent != null) {
            mInstrumentation.getContext().stopService(mIntent);
        }
        for (int i = 0; i < mStartedActivityList.size(); i++) {
            mStartedActivityList.get(i).finish();
        }
        if (mErrorProcessID != -1) {
            android.os.Process.killProcess(mErrorProcessID);
        }
    }

    @Test
    public void testGetRecentTasks() throws Exception {
        int maxNum = 0;
        int flags = 0;

        List<RecentTaskInfo> recentTaskList;
        // Test parameter: maxNum is set to 0
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        assertNotNull(recentTaskList);
        assertTrue(recentTaskList.size() == 0);
        // Test parameter: maxNum is set to 50
        maxNum = 50;
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        assertNotNull(recentTaskList);
        // start recent1_activity.
        startSubActivity(ActivityManagerRecentOneActivity.class);
        Thread.sleep(WAIT_TIME);
        // start recent2_activity
        startSubActivity(ActivityManagerRecentTwoActivity.class);
        Thread.sleep(WAIT_TIME);
        /*
         * assert both recent1_activity and recent2_activity exist in the recent
         * tasks list. Moreover,the index of the recent2_activity is smaller
         * than the index of recent1_activity
         */
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int indexRecentOne = getTaskInfoIndex(recentTaskList,
                ActivityManagerRecentOneActivity.class);
        int indexRecentTwo = getTaskInfoIndex(recentTaskList,
                ActivityManagerRecentTwoActivity.class);
        assertTrue(indexRecentOne != -1 && indexRecentTwo != -1);
        assertTrue(indexRecentTwo < indexRecentOne);

        try {
            mActivityManager.getRecentTasks(-1, 0);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception
        }
    }

    @Test
    public void testGetRecentTasksLimitedToCurrentAPK() throws Exception {
        int maxNum = 0;
        int flags = 0;

        // Check the number of tasks at this time.
        List<RecentTaskInfo>  recentTaskList;
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int numberOfEntriesFirstRun = recentTaskList.size();

        // Start another activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityReceiverFilter receiver = new ActivityReceiverFilter(ACTIVITY_LAUNCHED_ACTION);
        mTargetContext.startActivity(intent);

        // Make sure the activity has really started.
        assertEquals(RESULT_PASS, receiver.waitForActivity());
        receiver.close();

        // There shouldn't be any more tasks in this list at this time.
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int numberOfEntriesSecondRun = recentTaskList.size();
        assertTrue(numberOfEntriesSecondRun == numberOfEntriesFirstRun);

        // Tell the activity to finalize.
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("finish", true);
        mTargetContext.startActivity(intent);
    }

    // The receiver filter needs to be instantiated with the command to filter for before calling
    // startActivity.
    private class ActivityReceiverFilter extends BroadcastReceiver {
        // The activity we want to filter for.
        private String mActivityToFilter;
        private int result = RESULT_TIMEOUT;
        public long mTimeUsed = 0;
        private static final int TIMEOUT_IN_MS = 5000;

        // Create the filter with the intent to look for.
        public ActivityReceiverFilter(String activityToFilter) {
            mActivityToFilter = activityToFilter;
            IntentFilter filter = new IntentFilter();
            filter.addAction(mActivityToFilter);
            mInstrumentation.getTargetContext().registerReceiver(this, filter,
                    Context.RECEIVER_EXPORTED);
        }

        // Turn off the filter.
        public void close() {
            mInstrumentation.getTargetContext().unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(mActivityToFilter)) {
                synchronized(this) {
                   result = RESULT_PASS;
                   if (mActivityToFilter.equals(ACTIVITY_TIME_TRACK_INFO)) {
                       mTimeUsed = intent.getExtras().getLong(
                               ActivityOptions.EXTRA_USAGE_TIME_REPORT);
                   }
                   notifyAll();
                }
            }
        }

        public int waitForActivity() throws Exception {
            AmUtils.waitForBroadcastBarrier();
            synchronized(this) {
                try {
                    wait(TIMEOUT_IN_MS);
                } catch (InterruptedException e) {
                }
            }
            return result;
        }
    }

    private final <T extends Activity> void startSubActivity(Class<T> activityClass) {
        startSubActivity(activityClass, null);
    }

    private <T extends Activity> void startSubActivity(
            Class<T> activityClass,
            ActivityOptions activityOptions) {
        final Instrumentation.ActivityResult result = new ActivityResult(0, new Intent());
        final ActivityMonitor monitor = new ActivityMonitor(activityClass.getName(), result, false);
        mInstrumentation.addMonitor(monitor);
        launchActivity(STUB_PACKAGE_NAME, activityClass, activityOptions);
        mStartedActivityList.add(monitor.waitForActivity());
    }

    private <T extends Activity> T launchActivity(
            String pkg,
            Class<T> activityCls,
            ActivityOptions activityOptions) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        return launchActivityWithIntent(pkg, activityCls, intent, activityOptions);
    }

    private <T extends Activity> T launchActivityWithIntent(
            String pkg,
            Class<T> activityCls,
            Intent intent,
            ActivityOptions activityOptions) {
        intent.setClassName(pkg, activityCls.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        T activity = (T) mInstrumentation.startActivitySync(
                intent, activityOptions == null ? null : activityOptions.toBundle());
        mInstrumentation.waitForIdleSync();
        return activity;
    }

    private <T extends TaskInfo, S extends Activity> int getTaskInfoIndex(List<T> taskList,
            Class<S> activityClass) {
        int i = 0;
        for (TaskInfo ti : taskList) {
            if (ti.baseActivity.getClassName().equals(activityClass.getName())) {
                return i;
            }
            i++;
        }
        return -1;
    }

    @Test
    public void testGetRunningTasks() {
        // Test illegal parameter
        List<RunningTaskInfo> runningTaskList;
        runningTaskList = mActivityManager.getRunningTasks(-1);
        assertTrue(runningTaskList.size() == 0);

        runningTaskList = mActivityManager.getRunningTasks(0);
        assertTrue(runningTaskList.size() == 0);

        runningTaskList = mActivityManager.getRunningTasks(20);
        int taskSize = runningTaskList.size();
        assertTrue(taskSize >= 0 && taskSize <= 20);

        // start recent1_activity.
        startSubActivity(ActivityManagerRecentOneActivity.class);

        runningTaskList = mActivityManager.getRunningTasks(20);

        // assert only recent1_activity exists and is visible.
        int indexRecentOne = getTaskInfoIndex(runningTaskList,
                ActivityManagerRecentOneActivity.class);
        int indexRecentTwo = getTaskInfoIndex(runningTaskList,
                ActivityManagerRecentTwoActivity.class);
        assertTrue(indexRecentOne != -1 && indexRecentTwo == -1);
        assertTrue(runningTaskList.get(indexRecentOne).isVisible());

        // start recent2_activity in fullscreen to hide the recent1_activity.
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
        startSubActivity(ActivityManagerRecentTwoActivity.class, options);

        /*
         * assert both recent1_activity and recent2_activity exist in the
         * running tasks list. Moreover,the index of the recent2_activity is
         * smaller than the index of recent1_activity.
         */
        runningTaskList = mActivityManager.getRunningTasks(20);
        indexRecentOne = getTaskInfoIndex(runningTaskList,
                ActivityManagerRecentOneActivity.class);
        indexRecentTwo = getTaskInfoIndex(runningTaskList,
                ActivityManagerRecentTwoActivity.class);
        assertTrue(indexRecentOne != -1 && indexRecentTwo != -1);
        assertTrue(indexRecentTwo < indexRecentOne);

        // assert only recent2_activity is visible.
        assertFalse(runningTaskList.get(indexRecentOne).isVisible());
        assertTrue(runningTaskList.get(indexRecentTwo).isVisible());
    }

    @Test
    public void testGetRunningServices() throws Exception {
        // Test illegal parameter
        List<RunningServiceInfo> runningServiceInfo;
        runningServiceInfo = mActivityManager.getRunningServices(-1);
        assertTrue(runningServiceInfo.isEmpty());

        runningServiceInfo = mActivityManager.getRunningServices(0);
        assertTrue(runningServiceInfo.isEmpty());

        runningServiceInfo = mActivityManager.getRunningServices(5);
        assertTrue(runningServiceInfo.size() <= 5);

        Intent intent = new Intent();
        intent.setClass(mInstrumentation.getTargetContext(), MockService.class);
        intent.putExtra(MockService.EXTRA_NO_STOP, true);
        mInstrumentation.getTargetContext().startService(intent);
        MockService.waitForStart(WAIT_TIME);

        runningServiceInfo = mActivityManager.getRunningServices(Integer.MAX_VALUE);
        boolean foundService = false;
        for (RunningServiceInfo rs : runningServiceInfo) {
            if (rs.service.getClassName().equals(SERVICE_NAME)) {
                foundService = true;
                break;
            }
        }
        assertTrue(foundService);
        MockService.prepareDestroy();
        mTargetContext.stopService(intent);
        boolean destroyed = MockService.waitForDestroy(WAIT_TIME);
        assertTrue(destroyed);
    }

    private void executeAndLogShellCommand(String cmd) throws IOException {
        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        final String output = uiDevice.executeShellCommand(cmd);
        Log.d(TAG, "executed[" + cmd + "]; output[" + output.trim() + "]");
    }

    private String executeShellCommand(String cmd) throws IOException {
        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        return uiDevice.executeShellCommand(cmd).trim();
    }

    private void setForcedAppStandby(String packageName, boolean enabled)
            throws IOException {
        final StringBuilder cmdBuilder = new StringBuilder("appops set --user ")
                .append(mUserHelper.getUserId()).append(' ')
                .append(packageName)
                .append(" RUN_ANY_IN_BACKGROUND ")
                .append(enabled ? "ignore" : "allow");
        executeAndLogShellCommand(cmdBuilder.toString());
    }

    @Test
    public void testIsBackgroundRestricted() throws IOException {
        // This instrumentation runs in the target package's uid.
        final String targetPackage = mTargetContext.getPackageName();
        final ActivityManager am = mTargetContext.getSystemService(ActivityManager.class);
        setForcedAppStandby(targetPackage, true);
        assertTrue(am.isBackgroundRestricted());
        setForcedAppStandby(targetPackage, false);
        assertFalse(am.isBackgroundRestricted());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_RESTRICTIONS_API)
    public void testBackgroundRestrictionsIntent() throws IOException {
        assumeFalse("This intent is not required for leanback devices", mLeanbackOnly);
        assumeFalse("This intent is not required for watch devices", mWatchDevice);

        // This instrumentation runs in the target package's uid.
        final String targetPackage = mTargetContext.getPackageName();
        final Intent intent = new Intent(Settings.ACTION_BACKGROUND_RESTRICTIONS_SETTINGS)
                .setData(Uri.fromParts(PACKAGE_SCHEME, targetPackage, null));
        final ResolveInfo resolveInfo = mTargetContext.getPackageManager().resolveActivity(
                intent, 0);
        assertNotNull(resolveInfo);
    }

    @FlakyTest(detail = "Known fail on cuttleshish b/275888802 and other devices b/255817314.")
    @Test
    public void testGetMemoryInfo() {
        // Advertised memory is required when VSR API is V.
        if (PropertyUtil.getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityManager.MemoryInfo outInfo = new ActivityManager.MemoryInfo();
            mActivityManager.getMemoryInfo(outInfo);
            assertTrue(
                String.format("lowmemory (%s) == (%d <= %d)",
                outInfo.lowMemory, outInfo.availMem, outInfo.threshold),
                outInfo.lowMemory == (outInfo.availMem <= outInfo.threshold));
            assertTrue(
                String.format("totalMem (%d) <= advertisedMem (%d)",
                outInfo.totalMem, outInfo.advertisedMem),
                outInfo.totalMem <= outInfo.advertisedMem);
        }
    }

    @Test
    public void testGetRunningAppProcesses() throws Exception {
        List<RunningAppProcessInfo> list = mActivityManager.getRunningAppProcesses();
        assertNotNull(list);
        final String SYSTEM_PROCESS = "system";
        boolean hasSystemProcess = false;
        // The package name is also the default name for the application process
        final String TEST_PROCESS = STUB_PACKAGE_NAME;
        boolean hasTestProcess = false;
        for (RunningAppProcessInfo ra : list) {
            if (ra.processName.equals(SYSTEM_PROCESS)) {
                hasSystemProcess = true;

                // Make sure the importance is a sane value.
                assertTrue(ra.importance >= RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
                assertTrue(ra.importance < RunningAppProcessInfo.IMPORTANCE_GONE);
            } else if (ra.processName.equals(TEST_PROCESS)) {
                hasTestProcess = true;
            }
        }

        // For security reasons the system process is not exposed.
        assertFalse(hasSystemProcess);
        assertTrue(hasTestProcess);

        for (RunningAppProcessInfo ra : list) {
            if (ra.processName.equals("android.app.stubs:remote")) {
                fail("should be no process named android.app.stubs:remote");
            }
        }
        // start a new process
        // XXX would be a lot cleaner to bind instead of start.
        mIntent = new Intent("android.app.REMOTESERVICE");
        mIntent.setPackage("android.app.stubs");
        mInstrumentation.getTargetContext().startService(mIntent);
        Thread.sleep(WAITFOR_MSEC);

        List<RunningAppProcessInfo> listNew = mActivityManager.getRunningAppProcesses();
        mInstrumentation.getTargetContext().stopService(mIntent);

        for (RunningAppProcessInfo ra : listNew) {
            if (ra.processName.equals("android.app.stubs:remote")) {
                return;
            }
        }
        fail("android.app.stubs:remote process should be available");
    }

    @Test
    public void testGetMyMemoryState() {
        final RunningAppProcessInfo ra = new RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(ra);

        assertEquals(android.os.Process.myUid(), ra.uid);

        // When an instrumentation test is running, the importance is high.
        assertEquals(RunningAppProcessInfo.IMPORTANCE_FOREGROUND, ra.importance);
    }

    @Test
    public void testGetProcessInErrorState() throws Exception {
        List<ActivityManager.ProcessErrorStateInfo> errList = null;
        errList = mActivityManager.getProcessesInErrorState();
        assertNull(errList);

        // Setup the ANR monitor.
        final AmMonitor monitor = new AmMonitor(mInstrumentation, null);
        final ApplicationInfo app1Info = mTargetContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        final ApplicationInfo stubInfo = mTargetContext.getPackageManager().getApplicationInfo(
                STUB_PACKAGE_NAME, 0);
        final WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC, PROCESS_CAPABILITY_ALL);
        final String crashActivityName = "ActivityManagerStubCrashActivity";

        final SettingsSession<Integer> showOnFirstCrash = new SettingsSession<>(
                Settings.Global.getUriFor(Settings.Global.SHOW_FIRST_CRASH_DIALOG),
                Settings.Global::getInt, Settings.Global::putInt);
        final SettingsSession<Integer> showBackground = new SettingsSession<>(
                Settings.Secure.getUriFor(Settings.Secure.ANR_SHOW_BACKGROUND),
                Settings.Secure::getInt, Settings.Secure::putInt);
        try {
            runWithShellPermissionIdentity(() -> {
                showOnFirstCrash.set(1);
                showBackground.set(1);
            });

            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_TOP,
                    new Integer(PROCESS_CAPABILITY_ALL));

            // Sleep a while to let things go through.
            Thread.sleep(WAIT_TIME);

            // Now tell it goto ANR.
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_SELF_INDUCED_ANR,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            // Verify we got the ANR.
            assertTrue(monitor.waitFor(AmMonitor.WAIT_FOR_EARLY_ANR, WAITFOR_MSEC));

            // Let it continue.
            monitor.sendCommand(AmMonitor.CMD_CONTINUE);

            // Now it should've reached the normal ANR process.
            assertTrue(monitor.waitFor(AmMonitor.WAIT_FOR_ANR, WAITFOR_MSEC * 3));

            // Continue again, we need to see the ANR dialog in order to get the error report.
            monitor.sendCommand(AmMonitor.CMD_CONTINUE);

            // Sleep a while to let things go through.
            Thread.sleep(WAIT_TIME);

            // We shouldn't be able to read the error state info of that.
            errList = mActivityManager.getProcessesInErrorState();
            assertNull(errList);

            // Shell should have the access.
            final List<ActivityManager.ProcessErrorStateInfo>[] holder = new List[1];
            runWithShellPermissionIdentity(() -> {
                holder[0] = mActivityManager.getProcessesInErrorState();
            });
            assertNotNull(holder[0]);
            assertEquals(1, holder[0].size());
            verifyProcessErrorStateInfo(holder[0].get(0),
                    ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING,
                    app1Info.uid,
                    PACKAGE_NAME_APP1);

            // Start a crashing activity in remote process with the same UID.
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(STUB_PACKAGE_NAME, STUB_PACKAGE_NAME + "." + crashActivityName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mTargetContext.startActivity(intent);

            // Wait for the crash.
            assertTrue(monitor.waitFor(AmMonitor.WAIT_FOR_CRASHED, WAITFOR_MSEC));

            // Let it continue, we need to see the crash dialog in order to get the error report.
            monitor.sendCommand(AmMonitor.CMD_CONTINUE);

            // Sleep a while to let things go through.
            Thread.sleep(WAIT_TIME);

            // We should be able to see this crash info.
            errList = mActivityManager.getProcessesInErrorState();
            assertNotNull(errList);
            assertEquals(1, errList.size());

            verifyProcessErrorStateInfo(errList.get(0),
                    ActivityManager.ProcessErrorStateInfo.CRASHED,
                    stubInfo.uid,
                    STUB_PACKAGE_NAME + ":" + crashActivityName);

            // Shell should have the access to all of the crash info here.
            runWithShellPermissionIdentity(() -> {
                holder[0] = mActivityManager.getProcessesInErrorState();
            });
            assertNotNull(holder[0]);
            assertEquals(2, holder[0].size());
            // The return result is not sorted.
            final ActivityManager.ProcessErrorStateInfo t0 = holder[0].get(0);
            final ActivityManager.ProcessErrorStateInfo t1 = holder[0].get(1);
            final ActivityManager.ProcessErrorStateInfo info0 = t0.uid == stubInfo.uid ? t0 : t1;
            final ActivityManager.ProcessErrorStateInfo info1 = t1.uid == app1Info.uid ? t1 : t0;

            verifyProcessErrorStateInfo(info0,
                    ActivityManager.ProcessErrorStateInfo.CRASHED,
                    stubInfo.uid,
                    STUB_PACKAGE_NAME + ":" + crashActivityName);
            verifyProcessErrorStateInfo(info1,
                    ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING,
                    app1Info.uid,
                    PACKAGE_NAME_APP1);
        } finally {
            runWithShellPermissionIdentity(() -> {
                showOnFirstCrash.close();
                showBackground.close();
            });
            monitor.finish();
            uid1Watcher.finish();
            runWithShellPermissionIdentity(() -> {
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
            });
        }
    }

    private void verifyProcessErrorStateInfo(ActivityManager.ProcessErrorStateInfo info,
            int condition, int uid, String processName) throws Exception {
        assertEquals(condition, info.condition);
        assertEquals(uid, info.uid);
        assertEquals(processName, info.processName);
    }

    @Test
    public void testGetDeviceConfigurationInfo() {
        ConfigurationInfo conInf = mActivityManager.getDeviceConfigurationInfo();
        assertNotNull(conInf);
    }

    @Test
    public void testUpdateMccMncConfiguration() throws Exception {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.i(TAG, "testUpdateMccMncConfiguration skipped: no telephony available");
            return;
        }

        Configuration originalConfig = mTargetContext.getResources().getConfiguration();
        String[] mccMncConfigToUpdate = new String[] {MCC_TO_UPDATE, MNC_TO_UPDATE};
        boolean success = ShellIdentityUtils.invokeMethodWithShellPermissions(mActivityManager,
                (am) -> am.updateMccMncConfiguration(mccMncConfigToUpdate[0],
                        mccMncConfigToUpdate[1]));

        if (success) {
            Configuration changedConfig = mTargetContext.getResources().getConfiguration();
            assertEquals(MNC_TO_UPDATE, Integer.toString(changedConfig.mnc));
            assertEquals(MCC_TO_UPDATE, Integer.toString(changedConfig.mcc));
        }

        // Set mcc mnc configs back in the end of the test if they were set to something else.
        ShellIdentityUtils.invokeMethodWithShellPermissions(mActivityManager,
                (am) -> am.updateMccMncConfiguration(Integer.toString(originalConfig.mcc),
                        Integer.toString(originalConfig.mnc)));
    }

    /**
     * Simple test for {@link ActivityManager#isUserAMonkey()} - verifies its false.
     *
     * TODO: test positive case
     */
    @Test
    public void testIsUserAMonkey() {
        assertFalse(ActivityManager.isUserAMonkey());
    }

    /**
     * Verify that {@link ActivityManager#isRunningInTestHarness()} is false.
     */
    @RestrictedBuildTest
    @Test
    public void testIsRunningInTestHarness() {
        assertFalse("isRunningInTestHarness must be false in production builds",
                ActivityManager.isRunningInTestHarness());
    }

    /**
     * Go back to the home screen since running applications can interfere with application
     * lifetime tests.
     */
    private void launchHome() throws Exception {
        if (noHomeScreen()) {
            Log.d(TAG, "launcHome(): no home screen");
            return;
        }
        launchHomeScreenUsingIntent();
        Thread.sleep(WAIT_TIME);
    }

    private void launchHomeScreenUsingIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (mUserHelper.isVisibleBackgroundUser()) {
            ActivityOptions options = mUserHelper.getActivityOptions();
            mTargetContext.startActivity(intent, options.toBundle());
        } else {
            mTargetContext.startActivity(intent);
        }
    }

    private void launchHomeScreenUsingKeyCode() throws IOException {
        if (mUserHelper.isVisibleBackgroundUser()) {
            // TODO(b/270634492): should call "input -d + mDisplayId + keyevent KEYCODE_HOME", but
            // it's not working
            launchHomeScreenUsingIntent();
            return;
        }
        executeAndLogShellCommand("input keyevent KEYCODE_HOME");
    }

    /**
     * Gets the value of com.android.internal.R.bool.config_noHomeScreen.
     * @return true if no home screen is supported, false otherwise.
     */
    private boolean noHomeScreen() {
        try {
            return mTargetContext.getResources().getBoolean(
                    Resources.getSystem().getIdentifier("config_noHomeScreen", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume there's a home screen.
            return false;
        }
    }

    /**
     * Verify that the TimeTrackingAPI works properly when starting and ending an activity.
     */
    @Test
    public void testTimeTrackingAPI_SimpleStartExit() throws Exception {
        createManagedHomeActivitySession();
        launchHome();
        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY_IMMEDIATE_EXIT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO)
                .setPackage(context.getPackageName());
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context, 0, receiveIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE));

        // The application finished tracker.
        ActivityReceiverFilter appEndReceiver = new ActivityReceiverFilter(ACTIVITY_EXIT_ACTION);

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mTargetContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, appEndReceiver.waitForActivity());
        appEndReceiver.close();

        if (!noHomeScreen()) {
            // At this time the timerReceiver should not fire, even though the activity has shut
            // down, because we are back to the home screen. Going to the home screen does not
            // qualify as the user leaving the activity's flow. The time tracking is considered
            // complete only when the user switches to another activity that is not part of the
            // tracked flow.
            assertEquals(RESULT_TIMEOUT, timeReceiver.waitForActivity());
            assertTrue(timeReceiver.mTimeUsed == 0);
        } else {
            // With platforms that have no home screen, focus is returned to something else that is
            // considered a completion of the tracked activity flow, and hence time tracking is
            // triggered.
            assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        }

        // Issuing now another activity will trigger the timing information release.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        timeReceiver.close();
        assertTrue(timeReceiver.mTimeUsed != 0);
    }

    /**
     * Checks whether the device is automotive
     */
    private static boolean isAutomotive(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    @Test
    public void testHomeVisibilityListener() throws Exception {
        assumeFalse("With platforms that have no home screen, no need to test", noHomeScreen());
        assumeFalse("Skip test on TV devices", isAtvDevice());

        LinkedBlockingQueue<Boolean> currentHomeScreenVisibility = new LinkedBlockingQueue<>(2);
        HomeVisibilityListener homeVisibilityListener = new HomeVisibilityListener() {
            @Override
            public void onHomeVisibilityChanged(boolean isHomeActivityVisible) {
                currentHomeScreenVisibility.offer(isHomeActivityVisible);
            }
        };
        launchHome();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mActivityManager,
                (am) -> am.addHomeVisibilityListener(Runnable::run, homeVisibilityListener));

        try {
            PackageManager pm = mTargetContext.getPackageManager();
            // In multi-task mode with split screen there can be more than one application that is
            // visible and to user. An activity with category HOME might not be visible when HOME
            // intent is fired.
            // Hence, when in PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING mode
            // do not check that HOME is visible.
            if (!pm.hasSystemFeature(/* PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING */
                    "android.software.car.splitscreen_multitasking")
                    || !isAutomotive(mTargetContext)) {
                // Make sure we got the first notification that the home screen is visible.
                assertTrue(currentHomeScreenVisibility.poll(WAIT_TIME, TimeUnit.MILLISECONDS));
            }

            // Launch a basic activity to obscure the home screen.
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mTargetContext.startActivity(intent);

            // Make sure the observer reports the home screen as no longer visible
            assertFalse(currentHomeScreenVisibility.poll(WAIT_TIME, TimeUnit.MILLISECONDS));
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mActivityManager,
                    (am) -> am.removeHomeVisibilityListener(homeVisibilityListener));
        }
    }

    /**
     * Verify that the TimeTrackingAPI works properly when switching away from the monitored task.
     */
    @Test
    public void testTimeTrackingAPI_SwitchAwayTriggers() throws Exception {
        createManagedHomeActivitySession();
        launchHome();

        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO)
                .setPackage(context.getPackageName());
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context, 0, receiveIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE));

        // The application started tracker.
        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                ACTIVITY_LAUNCHED_ACTION);

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mTargetContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, appStartedReceiver.waitForActivity());
        appStartedReceiver.close();

        // At this time the timerReceiver should not fire since our app is running.
        assertEquals(RESULT_TIMEOUT, timeReceiver.waitForActivity());
        assertTrue(timeReceiver.mTimeUsed == 0);

        // Starting now another activity will put ours into the back hence releasing the timing.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        timeReceiver.close();
        assertTrue(timeReceiver.mTimeUsed != 0);
    }

    /**
     * Verify that the TimeTrackingAPI works properly when handling an activity chain gets started
     * and ended.
     */
    @Test
    public void testTimeTrackingAPI_ChainedActivityExit() throws Exception {
        createManagedHomeActivitySession();
        launchHome();
        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY_CHAIN_EXIT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO)
                .setPackage(context.getPackageName());
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context, 0, receiveIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE));

        // The application finished tracker.
        ActivityReceiverFilter appEndReceiver = new ActivityReceiverFilter(
                ACTIVITY_CHAIN_EXIT_ACTION);

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mTargetContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, appEndReceiver.waitForActivity());
        appEndReceiver.close();

        if (!noHomeScreen()) {
            // At this time the timerReceiver should not fire, even though the activity has shut
            // down, because we are back to the home screen. Going to the home screen does not
            // qualify as the user leaving the activity's flow. The time tracking is considered
            // complete only when the user switches to another activity that is not part of the
            // tracked flow.
            assertEquals(RESULT_TIMEOUT, timeReceiver.waitForActivity());
            assertTrue(timeReceiver.mTimeUsed == 0);
        } else {
            // With platforms that have no home screen, focus is returned to something else that is
            // considered a completion of the tracked activity flow, and hence time tracking is
            // triggered.
            assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        }

        // Issue another activity so that the timing information gets released.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        timeReceiver.close();
        assertTrue(timeReceiver.mTimeUsed != 0);
    }

    /**
     * Verify that after force-stopping a package which has a foreground task contains multiple
     * activities, the process of the package should not be alive (restarted).
     */
    @Test
    public void testForceStopPackageWontRestartProcess() throws Exception {
        // Ensure that there are no remaining component records of the test app package.
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(SIMPLE_PACKAGE_NAME));
        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                ACTIVITY_LAUNCHED_ACTION);
        // Start an activity of another APK.
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mTargetContext.startActivity(intent);
        assertEquals(RESULT_PASS, appStartedReceiver.waitForActivity());

        // Start a new activity in the same task. Here adds an action to make a different to intent
        // filter comparison so another same activity will be created.
        intent.setAction(Intent.ACTION_VIEW);
        mTargetContext.startActivity(intent);
        assertEquals(RESULT_PASS, appStartedReceiver.waitForActivity());
        appStartedReceiver.close();

        // Wait for the first activity to stop so its ActivityRecord.haveState will be true. The
        // condition is required to keep the activity record when its process is died.
        Thread.sleep(WAIT_TIME);

        // The package name is also the default name for the activity process.
        final String testProcess = SIMPLE_PACKAGE_NAME;
        Predicate<RunningAppProcessInfo> processNamePredicate =
                runningApp -> testProcess.equals(runningApp.processName);

        List<RunningAppProcessInfo> runningApps = callWithShellPermissionIdentity(
                () -> mActivityManager.getRunningAppProcesses());
        assertTrue("Process " + testProcess + " should be found in running process list",
                runningApps.stream().anyMatch(processNamePredicate));

        runningApps = callWithShellPermissionIdentity(() -> {
            mActivityManager.forceStopPackage(SIMPLE_PACKAGE_NAME);
            // Wait awhile (process starting may be asynchronous) to verify if the process is
            // started again unexpectedly.
            Thread.sleep(WAIT_TIME);
            return mActivityManager.getRunningAppProcesses();
        });

        assertFalse("Process " + testProcess + " should not be alive after force-stop",
                runningApps.stream().anyMatch(processNamePredicate));
    }

    /**
     * This test is to verify that devices are patched with the fix in b/119327603 for b/115384617.
     */
    @Test
    public void testIsAppForegroundRemoved() throws ClassNotFoundException {
       try {
           Class.forName("android.app.IActivityManager").getDeclaredMethod(
                   "isAppForeground", int.class);
           fail("IActivityManager.isAppForeground() API should not be available.");
       } catch (NoSuchMethodException e) {
           // Patched devices should throw this exception since isAppForeground is removed.
       }
    }

    /**
     * This test verifies the self-induced ANR by ActivityManager.appNotResponding().
     */
    @Test
    public void testAppNotResponding() throws Exception {
        // Setup the ANR monitor
        AmMonitor monitor = new AmMonitor(mInstrumentation,
                new String[]{AmMonitor.WAIT_FOR_CRASHED});

        // Now tell it goto ANR
        CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_SELF_INDUCED_ANR,
                PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

        try {

            // Verify we got the ANR
            assertTrue(monitor.waitFor(AmMonitor.WAIT_FOR_EARLY_ANR, WAITFOR_MSEC));

            // Just kill the test app
            monitor.sendCommand(AmMonitor.CMD_KILL);
        } finally {
            // clean up
            monitor.finish();
            runWithShellPermissionIdentity(() -> {
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
            });
        }
    }

    /**
     * This test verifies that the PROC_START_TIMEOUT triggers an ANR.
     */
    @Test
    public void testAppNotRespondingOnStartup() throws Exception {
        runWithShellPermissionIdentity(() -> {
            mIsWaitForFinishAttachApplicationEnabled = DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    "enable_wait_for_finish_attach_application",
                    false);
        });

        assumeTrue("App startup ANRs disabled", mIsWaitForFinishAttachApplicationEnabled);

        // Setup the ANR monitor
        AmMonitor monitor = new AmMonitor(mInstrumentation,
                new String[]{AmMonitor.WAIT_FOR_CRASHED});

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(PACKAGE_NAME_WEDGED_STARTUP, MockApplicationActivity.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mTargetContext.startActivity(intent);

        try {
            // Verify we got the ANR
            assertTrue(monitor.waitFor(AmMonitor.WAIT_FOR_EARLY_ANR,
                            WAITFOR_PROCSTAT_TIMEOUT_MSEC));

            // Just kill the test app
            monitor.sendCommand(AmMonitor.CMD_KILL);
        } finally {
            // clean up
            monitor.finish();
            runWithShellPermissionIdentity(() -> {
                mActivityManager.forceStopPackage(PACKAGE_NAME_WEDGED_STARTUP);
            });
        }
    }

    /*
     * Verifies the {@link android.app.ActivityManager#killProcessesWhenImperceptible}.
     */
    @Test
    public void testKillingPidsOnImperceptible() throws Exception {
        // Start remote service process
        final String remoteProcessName = STUB_PACKAGE_NAME + ":remote";
        Intent remoteIntent = new Intent("android.app.REMOTESERVICE");
        remoteIntent.setPackage(STUB_PACKAGE_NAME);
        mTargetContext.startService(remoteIntent);
        Thread.sleep(WAITFOR_MSEC);

        RunningAppProcessInfo remote = getRunningAppProcessInfo(remoteProcessName);
        assertNotNull(remote);

        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                ACTIVITY_LAUNCHED_ACTION);
        boolean disabled = "0".equals(executeShellCommand("cmd deviceidle enabled light"));
        try {
            if (disabled) {
                executeAndLogShellCommand("cmd deviceidle enable light");
            }
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mTargetContext.startActivity(intent);
            assertEquals(RESULT_PASS, appStartedReceiver.waitForActivity());

            RunningAppProcessInfo proc = getRunningAppProcessInfo(SIMPLE_PACKAGE_NAME);
            assertNotNull(proc);

            final String reason = "cts";

            try {
                mActivityManager.killProcessesWhenImperceptible(new int[]{proc.pid}, reason);
                fail("Shouldn't have the permission");
            } catch (SecurityException e) {
                // expected
            }

            final long defaultWaitForKillTimeout = 5_000;

            // Keep the device awake
            toggleScreenOn(true);

            // Kill the remote process
            runWithShellPermissionIdentity(() -> {
                mActivityManager.killProcessesWhenImperceptible(new int[]{remote.pid}, reason);
            });

            // Kill the activity process
            runWithShellPermissionIdentity(() -> {
                mActivityManager.killProcessesWhenImperceptible(new int[]{proc.pid}, reason);
            });

            // The processes should be still alive because device isn't in idle
            assertFalse(waitUntilTrue(defaultWaitForKillTimeout, () -> isProcessGone(
                    remote.pid, remoteProcessName)));
            assertFalse(waitUntilTrue(defaultWaitForKillTimeout, () -> isProcessGone(
                    proc.pid, SIMPLE_PACKAGE_NAME)));

            if (isAtvDevice()) {
                // On operator tier devices of AndroidTv, Activity is put behind TvLauncher
                // after turnScreenOff by android.intent.category.HOME intent from
                // TvRecommendation.
                return;
            }

            // force device idle
            toggleScreenOn(false);
            triggerIdle(true);

            // Now the remote process should have been killed.
            assertTrue(waitUntilTrue(defaultWaitForKillTimeout, () -> isProcessGone(
                    remote.pid, remoteProcessName)));

            // The activity process should be still alive because it's is on the top (perceptible)
            assertFalse(waitUntilTrue(defaultWaitForKillTimeout, () -> isProcessGone(
                    proc.pid, SIMPLE_PACKAGE_NAME)));

            triggerIdle(false);
            // Toogle screen ON
            toggleScreenOn(true);

            // Now launch home
            launchHomeScreenUsingKeyCode();

            // force device idle again
            toggleScreenOn(false);
            triggerIdle(true);

            // Now the activity process should be gone.
            assertTrue(waitUntilTrue(defaultWaitForKillTimeout, () -> isProcessGone(
                    proc.pid, SIMPLE_PACKAGE_NAME)));

        } finally {
            // Clean up code
            triggerIdle(false);
            toggleScreenOn(true);
            appStartedReceiver.close();
            mTargetContext.stopService(remoteIntent);

            if (disabled) {
                executeAndLogShellCommand("cmd deviceidle disable light");
            }
            runWithShellPermissionIdentity(() -> {
                mActivityManager.forceStopPackage(SIMPLE_PACKAGE_NAME);
            });
            executeAndLogShellCommand("am kill " + STUB_PACKAGE_NAME);
        }
    }

    /**
     * Verifies the system will kill app's child processes if they are using excessive cpu
     */
    @LargeTest
    @Test
    public void testKillingAppChildProcess() throws Exception {
        final long powerCheckInterval = 5 * 1000;
        final long processGoneTimeout = powerCheckInterval * 4;
        final int waitForSec = 5 * 1000;
        final String activityManagerConstants = "activity_manager_constants";

        final SettingsSession<String> amSettings = new SettingsSession<>(
                Settings.Global.getUriFor(activityManagerConstants),
                Settings.Global::getString, Settings.Global::putString);

        final ApplicationInfo ai = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP1, 0);
        final WatchUidRunner watcher = new WatchUidRunner(mInstrumentation, ai.uid, waitForSec);

        try {
            // Shorten the power check intervals
            amSettings.set("power_check_interval=" + powerCheckInterval);

            // Make sure we could start activity from background
            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist +" + PACKAGE_NAME_APP1);

            // Keep the device awake
            toggleScreenOn(true);

            // Start an activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Spawn a light weight child process
            CountDownLatch startLatch = startChildProcessInPackage(PACKAGE_NAME_APP1,
                    new String[] {"/system/bin/sh", "-c",  "sleep 1000"});

            // Wait for the start of the child process
            assertTrue("Failed to spawn child process",
                    startLatch.await(waitForSec, TimeUnit.MILLISECONDS));

            // Stop the activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher.waitFor(WatchUidRunner.CMD_CACHED, null);

            // Wait for the system to kill that light weight child (it won't happen actually)
            CountDownLatch stopLatch = initWaitingForChildProcessGone(
                    PACKAGE_NAME_APP1, processGoneTimeout);

            assertFalse("App's light weight child process shouldn't be gone",
                    stopLatch.await(processGoneTimeout, TimeUnit.MILLISECONDS));

            // Now kill the light weight child
            stopLatch = stopChildProcess(PACKAGE_NAME_APP1, waitForSec);

            assertTrue("Failed to kill app's light weight child process",
                    stopLatch.await(waitForSec, TimeUnit.MILLISECONDS));

            // Start an activity again
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Spawn the cpu intensive child process
            startLatch = startChildProcessInPackage(PACKAGE_NAME_APP1,
                    new String[] {"/system/bin/sh", "-c",  "while true; do :; done"});

            // Wait for the start of the child process
            assertTrue("Failed to spawn child process",
                    startLatch.await(waitForSec, TimeUnit.MILLISECONDS));

            // Stop the activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher.waitFor(WatchUidRunner.CMD_CACHED, null);

            // Wait for the system to kill that heavy child due to excessive cpu usage,
            // as well as the parent process.
            watcher.waitFor(WatchUidRunner.CMD_GONE, processGoneTimeout);

        } finally {
            amSettings.close();

            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist -" + PACKAGE_NAME_APP1);

            runWithShellPermissionIdentity(() -> {
                // force stop test package, where the whole test process group will be killed.
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
            });

            watcher.finish();
        }
    }


    /**
     * Verifies the system will trim app's child processes if there are too many
     */
    @LargeTest
    @Test
    public void testTrimAppChildProcess() throws Exception {
        final long powerCheckInterval = 5 * 1000;
        final long processGoneTimeout = powerCheckInterval * 4;
        final int waitForSec = 5 * 1000;
        final int maxPhantomProcessesNum = 2;
        final String namespaceActivityManager = "activity_manager";
        final String activityManagerConstants = "activity_manager_constants";
        final String maxPhantomProcesses = "max_phantom_processes";

        final SettingsSession<String> amSettings = new SettingsSession<>(
                Settings.Global.getUriFor(activityManagerConstants),
                Settings.Global::getString, Settings.Global::putString);
        final Bundle currentMax = new Bundle();
        final String keyCurrent = "current";

        ApplicationInfo ai = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP1, 0);
        final WatchUidRunner watcher1 = new WatchUidRunner(mInstrumentation, ai.uid, waitForSec);
        ai = mTargetContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP2, 0);
        final WatchUidRunner watcher2 = new WatchUidRunner(mInstrumentation, ai.uid, waitForSec);
        ai = mTargetContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP3, 0);
        final WatchUidRunner watcher3 = new WatchUidRunner(mInstrumentation, ai.uid, waitForSec);

        try {
            // Shorten the power check intervals
            amSettings.set("power_check_interval=" + powerCheckInterval);

            // Reduce the maximum phantom processes allowance
            runWithShellPermissionIdentity(() -> {
                int current = DeviceConfig.getInt(namespaceActivityManager,
                        maxPhantomProcesses, -1);
                currentMax.putInt(keyCurrent, current);
                DeviceConfig.setProperty(namespaceActivityManager,
                        maxPhantomProcesses,
                        Integer.toString(maxPhantomProcessesNum), false);
            });

            // Make sure we could start activity from background
            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist +" + PACKAGE_NAME_APP1);
            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist +" + PACKAGE_NAME_APP2);
            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist +" + PACKAGE_NAME_APP3);

            // Keep the device awake
            toggleScreenOn(true);

            // Start an activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher1.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Spawn a light weight child process
            CountDownLatch startLatch = startChildProcessInPackage(PACKAGE_NAME_APP1,
                    new String[] {"/system/bin/sh", "-c",  "sleep 1000"});

            // Wait for the start of the child process
            assertTrue("Failed to spawn child process",
                    startLatch.await(waitForSec, TimeUnit.MILLISECONDS));

            // Start an activity in another package
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP2, PACKAGE_NAME_APP2, 0, null);

            watcher2.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Spawn a light weight child process
            startLatch = startChildProcessInPackage(PACKAGE_NAME_APP2,
                    new String[] {"/system/bin/sh", "-c",  "sleep 1000"});

            // Wait for the start of the child process
            assertTrue("Failed to spawn child process",
                    startLatch.await(waitForSec, TimeUnit.MILLISECONDS));

            // Finish the 1st activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            watcher1.waitFor(WatchUidRunner.CMD_CACHED, null);

            // Wait for the system to kill that light weight child (it won't happen actually)
            CountDownLatch stopLatch = initWaitingForChildProcessGone(
                    PACKAGE_NAME_APP1, processGoneTimeout);

            assertFalse("App's light weight child process shouldn't be gone",
                    stopLatch.await(processGoneTimeout, TimeUnit.MILLISECONDS));

            // Sleep a while
            SystemClock.sleep(powerCheckInterval);

            // Now start an activity in the 3rd party
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP3, PACKAGE_NAME_APP3, 0, null);

            watcher3.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Spawn a light weight child process
            startLatch = startChildProcessInPackage(PACKAGE_NAME_APP3,
                    new String[] {"/system/bin/sh", "-c",  "sleep 1000"});

            // Wait for the start of the child process
            assertTrue("Failed to spawn child process",
                    startLatch.await(waitForSec, TimeUnit.MILLISECONDS));

            // Now the 1st child process should have been gone.
            stopLatch = initWaitingForChildProcessGone(
                    PACKAGE_NAME_APP1, processGoneTimeout);

            assertTrue("1st App's child process should have been gone",
                    stopLatch.await(processGoneTimeout, TimeUnit.MILLISECONDS));

        } finally {
            amSettings.close();

            runWithShellPermissionIdentity(() -> {
                final int current = currentMax.getInt(keyCurrent);
                if (current < 0) {
                    // Hm, DeviceConfig doesn't have an API to delete a property,
                    // let's set it empty so the code will use the built-in default value.
                    DeviceConfig.setProperty(namespaceActivityManager,
                            maxPhantomProcesses, "", false);
                } else {
                    DeviceConfig.setProperty(namespaceActivityManager,
                            maxPhantomProcesses, Integer.toString(current), false);
                }
            });

            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist -" + PACKAGE_NAME_APP1);
            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist -" + PACKAGE_NAME_APP2);
            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist -" + PACKAGE_NAME_APP3);

            runWithShellPermissionIdentity(() -> {
                // force stop test package, where the whole test process group will be killed.
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP2);
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP3);
            });

            watcher1.finish();
            watcher2.finish();
            watcher3.finish();
        }
    }

    private CountDownLatch startChildProcessInPackage(String pkgName, String[] cmdline) {
        final CountDownLatch startLatch = new CountDownLatch(1);

        final IBinder binder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                switch (code) {
                    case CommandReceiver.RESULT_CHILD_PROCESS_STARTED:
                        startLatch.countDown();
                        return true;
                    default:
                        return false;
                }
            }
        };
        final Bundle extras = new Bundle();
        extras.putBinder(CommandReceiver.EXTRA_CALLBACK, binder);
        extras.putStringArray(CommandReceiver.EXTRA_CHILD_CMDLINE, cmdline);

        CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_CHILD_PROCESS,
                pkgName, pkgName, 0, extras);

        return startLatch;
    }

    final CountDownLatch stopChildProcess(String pkgName, long timeout) {
        final CountDownLatch stopLatch = new CountDownLatch(1);

        final IBinder binder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                switch (code) {
                    case CommandReceiver.RESULT_CHILD_PROCESS_STOPPED:
                        stopLatch.countDown();
                        return true;
                    default:
                        return false;
                }
            }
        };
        final Bundle extras = new Bundle();
        extras.putBinder(CommandReceiver.EXTRA_CALLBACK, binder);
        extras.putLong(CommandReceiver.EXTRA_TIMEOUT, timeout);

        CommandReceiver.sendCommand(mTargetContext,
                CommandReceiver.COMMAND_STOP_CHILD_PROCESS, pkgName, pkgName, 0, extras);

        return stopLatch;
    }

    final CountDownLatch initWaitingForChildProcessGone(String pkgName, long timeout) {
        final CountDownLatch stopLatch = new CountDownLatch(1);

        final IBinder binder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                switch (code) {
                    case CommandReceiver.RESULT_CHILD_PROCESS_GONE:
                        stopLatch.countDown();
                        return true;
                    default:
                        return false;
                }
            }
        };
        final Bundle extras = new Bundle();
        extras.putBinder(CommandReceiver.EXTRA_CALLBACK, binder);
        extras.putLong(CommandReceiver.EXTRA_TIMEOUT, timeout);

        CommandReceiver.sendCommand(mTargetContext,
                CommandReceiver.COMMAND_WAIT_FOR_CHILD_PROCESS_GONE, pkgName, pkgName, 0, extras);

        return stopLatch;
    }

    @Test
    public void testTrimMemActivityFg() throws Exception {

        runWithShellPermissionIdentity(() -> {
            mPreviousModernTrim = DeviceConfig.getString(
                DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                "use_modern_trim",
                null);

            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER, "use_modern_trim",
                    "false", false);
        });

        final int waitForSec = 5 * 1000;
        final ApplicationInfo ai1 = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP1, 0);
        final WatchUidRunner watcher1 = new WatchUidRunner(mInstrumentation, ai1.uid, waitForSec);

        final ApplicationInfo ai2 = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP2, 0);
        final WatchUidRunner watcher2 = new WatchUidRunner(mInstrumentation, ai2.uid, waitForSec);

        final ApplicationInfo ai3 = mTargetContext.getPackageManager()
                .getApplicationInfo(CANT_SAVE_STATE_1_PACKAGE_NAME, 0);
        final WatchUidRunner watcher3 = new WatchUidRunner(mInstrumentation, ai3.uid, waitForSec);

        final CountDownLatch[] latchHolder = new CountDownLatch[1];
        final int[] expectedLevel = new int[1];
        final Bundle extras = initWaitingForTrimLevel(level -> {
            if (level == expectedLevel[0]) {
                latchHolder[0].countDown();
            }
        });
        try {
            // Make sure we could start activity from background
            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist +" + PACKAGE_NAME_APP1);

            // Override the memory pressure level, force it staying at normal.
            runShellCommand(mInstrumentation, "am memory-factor set NORMAL");

            // Keep the device awake
            toggleScreenOn(true);

            latchHolder[0] = new CountDownLatch(1);
            expectedLevel[0] = TRIM_MEMORY_RUNNING_MODERATE;

            // Start an activity
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);

            watcher1.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);

            // Force the memory pressure to moderate
            runShellCommand(mInstrumentation, "am memory-factor set MODERATE");
            assertTrue("Failed to wait for the trim memory event",
                    latchHolder[0].await(waitForSec, TimeUnit.MILLISECONDS));

            latchHolder[0] = new CountDownLatch(1);
            expectedLevel[0] = TRIM_MEMORY_RUNNING_LOW;
            // Force the memory pressure to low
            runShellCommand(mInstrumentation, "am memory-factor set LOW");
            assertTrue("Failed to wait for the trim memory event",
                    latchHolder[0].await(waitForSec, TimeUnit.MILLISECONDS));

            latchHolder[0] = new CountDownLatch(1);
            expectedLevel[0] = TRIM_MEMORY_RUNNING_CRITICAL;
            // Force the memory pressure to critical
            runShellCommand(mInstrumentation, "am memory-factor set CRITICAL");
            assertTrue("Failed to wait for the trim memory event",
                    latchHolder[0].await(waitForSec, TimeUnit.MILLISECONDS));

            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, LocalForegroundService.newCommand(
                    LocalForegroundService.COMMAND_START_NO_FOREGROUND));

            // Reset the memory pressure override
            runShellCommand(mInstrumentation, "am memory-factor reset");

            latchHolder[0] = new CountDownLatch(1);
            expectedLevel[0] = TRIM_MEMORY_UI_HIDDEN;
            // Start another activity in package2
            CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            watcher2.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);
            watcher1.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE, null);
            assertTrue("Failed to wait for the trim memory event",
                    latchHolder[0].await(waitForSec, TimeUnit.MILLISECONDS));

            // Start the heavy weight activity
            final Intent intent = new Intent();
            final CountDownLatch[] heavyLatchHolder = new CountDownLatch[1];
            final Predicate[] testFunc = new Predicate[1];

            intent.setPackage(CANT_SAVE_STATE_1_PACKAGE_NAME);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtras(initWaitingForTrimLevel(level -> {
                if (testFunc[0].test(level)) {
                    heavyLatchHolder[0].countDown();
                }
            }));

            final WindowManagerStateHelper wms = new WindowManagerStateHelper();
            mTargetContext.startActivity(intent);
            watcher3.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);
            wms.waitForValidState(new ComponentName(CANT_SAVE_STATE_1_PACKAGE_NAME,
                      "CantSave1Activity"));

            heavyLatchHolder[0] = new CountDownLatch(1);
            testFunc[0] = level -> TRIM_MEMORY_RUNNING_MODERATE <= (int) level
                    && TRIM_MEMORY_RUNNING_CRITICAL >= (int) level;
            // Force the memory pressure to moderate
            runShellCommand(mInstrumentation, "am memory-factor set MODERATE");
            assertTrue("Failed to wait for the trim memory event",
                    heavyLatchHolder[0].await(waitForSec, TimeUnit.MILLISECONDS));

            // Now go home
            final Intent homeIntent = new Intent();
            homeIntent.setAction(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            heavyLatchHolder[0] = new CountDownLatch(1);
            testFunc[0] = level -> TRIM_MEMORY_BACKGROUND == (int) level;
            mTargetContext.startActivity(homeIntent);
            wms.waitForHomeActivityVisible();
            assertTrue("Failed to wait for the trim memory event",
                    heavyLatchHolder[0].await(waitForSec, TimeUnit.MILLISECONDS));

        } finally {
            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist -" + PACKAGE_NAME_APP1);

            runShellCommand(mInstrumentation, "am memory-factor reset");

            runWithShellPermissionIdentity(() -> {
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP2);
                mActivityManager.forceStopPackage(CANT_SAVE_STATE_1_PACKAGE_NAME);
                if (mPreviousModernTrim == null) {
                    DeviceConfig.deleteProperty(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                            "use_modern_trim");
                } else {
                    DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                            "use_modern_trim", mPreviousModernTrim, false);
                }

            });

            watcher1.finish();
            watcher2.finish();
            watcher3.finish();
        }
    }

    @Test
    public void testTrimMemActivityBg() throws Exception {
        final int minLru = 8;
        final int waitForSec = 30 * 1000;
        final String prefix = "trimmem_";
        final CountDownLatch[] latchHolder = new CountDownLatch[1];
        final String pkgName = PACKAGE_NAME_APP1;
        final ArrayMap<String, Pair<int[], ServiceConnection>> procName2Level = new ArrayMap<>();
        int startSeq = 0;

        try {

            runWithShellPermissionIdentity(() -> {
                mPreviousModernTrim = DeviceConfig.getString(
                    DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                    "use_modern_trim",
                    null);

                DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                        "use_modern_trim", "false", false);
            });

            // Kill all background processes
            runShellCommand(mInstrumentation, "am kill-all");

            // Override the memory pressure level, force it staying at normal.
            runShellCommand(mInstrumentation, "am memory-factor set NORMAL");

            List<String> lru;
            // Start a new isolated service once a time, and then check the lru list
            do {
                final String instanceName = prefix + startSeq++;
                final int[] levelHolder = new int[1];

                // Spawn the new isolated service
                final ServiceConnection conn = TrimMemService.bindToTrimMemService(
                        pkgName, instanceName, latchHolder, levelHolder, mTargetContext);

                // Get the list of all cached apps
                lru = getCachedAppsLru();
                assertTrue(lru.size() > 0);

                for (int i = lru.size() - 1; i >= 0; i--) {
                    String p = lru.get(i);
                    if (p.indexOf(instanceName) != -1) {
                        // This is the new one we just created
                        procName2Level.put(p, new Pair<>(levelHolder, conn));
                        break;
                    }
                }
                if (lru.size() < minLru) {
                    continue;
                }
                if (lru.get(0).indexOf(pkgName) != -1) {
                    // Okay now the very least recent used cached process is one of ours
                    break;
                } else {
                    // Hm, someone dropped below us in the between, let's kill it
                    ArraySet<String> others = new ArraySet<>();
                    for (int i = 0, size = lru.size(); i < size; i++) {
                        final String name = lru.get(i);
                        if (name.indexOf(pkgName) != -1) {
                            break;
                        }
                        others.add(name);
                    }
                    killBackgroundProcesses(others::contains);
                }
            } while (true);

            // Remove all other processes
            for (int i = lru.size() - 1; i >= 0; i--) {
                if (lru.get(i).indexOf(prefix) == -1) {
                    lru.remove(i);
                }
            }
            {
                final List<String> localLru = lru;
                killBackgroundProcesses(process -> !localLru.contains(process));
            }

            latchHolder[0] = new CountDownLatch(lru.size());
            // Force the memory pressure to moderate
            runShellCommand(mInstrumentation, "am memory-factor set MODERATE");
            assertTrue("Failed to wait for the trim memory event",
                    latchHolder[0].await(waitForSec, TimeUnit.MILLISECONDS));

            // Verify the trim levels among the LRU
            int level = TRIM_MEMORY_COMPLETE;
            assertEquals(level, procName2Level.get(lru.get(0)).first[0]);
            for (int i = 1, size = lru.size(); i < size; i++) {
                int curLevel = procName2Level.get(lru.get(i)).first[0];
                assertTrue(level >= curLevel);
                level = curLevel;
            }

            // Cleanup: Unbind from them
            for (int i = procName2Level.size() - 1; i >= 0; i--) {
                mTargetContext.unbindService(procName2Level.valueAt(i).second);
            }
        } finally {
            runShellCommand(mInstrumentation, "am memory-factor reset");

            runWithShellPermissionIdentity(() -> {
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
                if (mPreviousModernTrim == null) {
                    DeviceConfig.deleteProperty(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                            "use_modern_trim");
                } else {
                    DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                            "use_modern_trim", mPreviousModernTrim, false);
                }

            });
        }
    }

    private void killBackgroundProcesses(Predicate<String> predicate) {
        runWithShellPermissionIdentity(() -> {
            final List<ActivityManager.RunningAppProcessInfo> procs = mActivityManager
                    .getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo info: procs) {
                if (info.importance
                        >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                    if (predicate.test(info.processName)) {
                        mActivityManager.killBackgroundProcesses(info.pkgList[0]);
                    }
                }
            }
        });
    }

    @Test
    public void testServiceDoneLRUPosition() throws Exception {
        final String[] packageNames = {PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, PACKAGE_NAME_APP3};
        final WatchUidRunner[] watchers = initWatchUidRunners(packageNames, WAITFOR_MSEC);
        final HandlerThread handlerThread = new HandlerThread("worker");
        final Messenger[] controllerHolder = new Messenger[1];
        final CountDownLatch[] countDownLatchHolder = new CountDownLatch[1];
        handlerThread.start();
        final Messenger messenger = new Messenger(new Handler(handlerThread.getLooper(), msg -> {
            final Bundle bundle = (Bundle) msg.obj;
            final IBinder binder = bundle.getBinder(CommandReceiver.EXTRA_MESSENGER);
            if (binder != null) {
                controllerHolder[0] = new Messenger(binder);
                countDownLatchHolder[0].countDown();
            }
            return true;
        }));

        try {
            // Make sure we could start activity from background
            forEach(packageNames, packageName -> runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist +" + packageName));

            // Keep the device awake
            toggleScreenOn(true);

            // Start a FGS in app1
            final Bundle extras = new Bundle();
            countDownLatchHolder[0] = new CountDownLatch(1);
            extras.putBinder(CommandReceiver.EXTRA_MESSENGER, messenger.getBinder());
            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);

            watchers[0].waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE,
                    null);

            assertTrue("Failed to get the controller interface",
                    countDownLatchHolder[0].await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));

            final String[] otherPackages = {PACKAGE_NAME_APP2, PACKAGE_NAME_APP3};
            final WatchUidRunner[] otherWatchers = {watchers[1], watchers[2]};
            // Start an activity in another package
            forBiEach(otherPackages, otherWatchers, (packageName, watcher) -> {
                CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                        packageName, packageName, 0, null);
                watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);
            });

            // Stop both of these activities
            forBiEach(otherPackages, otherWatchers, (packageName, watcher) -> {
                CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                        packageName, packageName, 0, null);
                watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                        null);
                // Sleep a while before proceeding to next one to make sure the activity lifecycle
                // transitions have completed.
                SystemClock.sleep(1000);
            });

            // Launch home so we'd have cleared these the above test activities from recents.
            launchHome();

            // Now stop the foreground service, we'd have to do via the controller interface
            final Message msg = Message.obtain();
            try {
                msg.what = LocalForegroundService.COMMAND_STOP_SELF;
                controllerHolder[0].send(msg);
            } catch (RemoteException e) {
                fail("Unable to stop test package");
            }
            msg.recycle();
            watchers[0].waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                    null);

            verifyLruOrders(packageNames, 0, true, (a, b) -> a > b, "%s should be newer than %s");
        } finally {
            handlerThread.quitSafely();

            forEach(packageNames, packageName -> runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist -" + packageName));

            // force stop test package, where the whole test process group will be killed.
            forEach(packageNames, packageName -> runWithShellPermissionIdentity(
                    () -> mActivityManager.forceStopPackage(packageName)));

            forEach(watchers, watcher -> watcher.finish());
        }
    }

    @Test
    public void testBroadcastReceiverLRUPosition() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeFalse("not testable in automotive device", mAutomotiveDevice);
        assumeFalse("not testable in leanback device", mLeanbackOnly);

        final String[] packageNames = {PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, PACKAGE_NAME_APP3};
        final WatchUidRunner[] watchers = initWatchUidRunners(packageNames, WAITFOR_MSEC * 2);

        try {
            // Set the PACKAGE_NAME_APP1 into rare bucket
            runShellCommand(mInstrumentation, "am set-standby-bucket "
                    + PACKAGE_NAME_APP1 + " rare");

            // Make sure we could start activity from background
            forEach(packageNames, packageName -> runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist +" + packageName));

            // Keep the device awake
            toggleScreenOn(true);

            // Start activities in these packages.
            forBiEach(packageNames, watchers, (packageName, watcher) -> {
                CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_START_ACTIVITY,
                        packageName, packageName, 0, null);
                watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);
            });

            // Stop all of these activities
            forBiEach(packageNames, watchers, (packageName, watcher) -> {
                CommandReceiver.sendCommand(mTargetContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                        packageName, packageName, 0, null);
                watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                        null);
                // Sleep a while before proceeding to next one to make sure the activity lifecycle
                // transitions have completed.
                SystemClock.sleep(1000);
            });

            // Launch home so we'd have cleared these the above test activities from recents.
            launchHome();

            // Verify the LRU position.
            verifyLruOrders(packageNames, 0, false, (a, b) -> a < b, "%s should be older than %s");

            forEach(packageNames, packageName -> runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist -" + packageName));
            // Restrict the PACKAGE_NAME_APP1
            runShellCommand(mInstrumentation, "am set-standby-bucket "
                    + PACKAGE_NAME_APP1 + " restricted");
            // Sleep a while to let it take effect.
            Thread.sleep(WAITFOR_MSEC);

            final Intent intent = new Intent();
            final CountDownLatch[] latch = new CountDownLatch[] {new CountDownLatch(1)};
            final BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    latch[0].countDown();
                }
            };
            // Send a broadcast to PACKAGE_NAME_APP1
            CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                    CommandReceiver.COMMAND_EMPTY, PACKAGE_NAME_APP1, PACKAGE_NAME_APP1,
                    0, null, receiver);

            assertTrue("Failed to get the broadcast",
                    latch[0].await(WAITFOR_MSEC * 2, TimeUnit.MILLISECONDS));

            // Now check the LRU position again, it should remain the same because it's restricted.
            verifyLruOrders(packageNames, 0, false, (a, b) -> a < b, "%s should be older than %s");

            // Set the PACKAGE_NAME_APP1 into rare bucket again.
            runShellCommand(mInstrumentation, "am set-standby-bucket "
                    + PACKAGE_NAME_APP1 + " rare");

            latch[0] = new CountDownLatch(1);
            // Send a broadcast to PACKAGE_NAME_APP1 again.
            CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                    CommandReceiver.COMMAND_EMPTY, PACKAGE_NAME_APP1, PACKAGE_NAME_APP1,
                    0, null, receiver);

            // Now its LRU posistion should have been bumped.
            verifyLruOrders(packageNames, 0, true, (a, b) -> a > b, "%s should be newer than %s");
        } finally {
            forEach(packageNames, packageName -> runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist -" + packageName));

            runShellCommand(mInstrumentation, "am set-standby-bucket "
                    + PACKAGE_NAME_APP1 + " rare");

            // force stop test package, where the whole test process group will be killed.
            forEach(packageNames, packageName -> runWithShellPermissionIdentity(
                    () -> mActivityManager.forceStopPackage(packageName)));

            forEach(watchers, watcher -> watcher.finish());
        }
    }

    @Test
    public void testGetUidProcessState_checkAccess() throws Exception {
        boolean hasPermissionGrantChanged = false;
        if (!PermissionUtils.isPermissionGranted(STUB_PACKAGE_NAME,
                android.Manifest.permission.PACKAGE_USAGE_STATS)) {
            PermissionUtils.grantPermission(
                    STUB_PACKAGE_NAME, android.Manifest.permission.PACKAGE_USAGE_STATS);
            hasPermissionGrantChanged = true;
        }
        int newUserId = UserHandle.USER_NULL;
        try {
            // Verify that calling the API doesn't trigger any exceptions.
            mActivityManager.getUidProcessState(Process.myUid());

            assumeTrue(UserManager.supportsMultipleUsers());
            newUserId = createNewUser();
            assertNotEquals(UserHandle.USER_NULL, newUserId);
            startUser(newUserId);
            installExistingPackageAsUser(STUB_PACKAGE_NAME, newUserId);
            final int uidFromNewUser = UserHandle.getUid(newUserId, Process.myUid());
            // Verify that calling the API for a uid on a different user results in an exception.
            assertThrows(SecurityException.class, () -> mActivityManager.getUidProcessState(
                    uidFromNewUser));

            // Verify that calling the API with shell identity (which has
            // INTERACT_ACROSS_USERS_FULL permission) for a uid on a different user works.
            runWithShellPermissionIdentity(() -> mActivityManager.getUidProcessState(
                    uidFromNewUser));
        } finally {
            if (newUserId != UserHandle.USER_NULL) {
                removeUser(newUserId);
            }
            if (hasPermissionGrantChanged) {
                PermissionUtils.revokePermission(
                        STUB_PACKAGE_NAME, android.Manifest.permission.PACKAGE_USAGE_STATS);
            }
        }
    }

    @Test
    public void testGetUidProcessCapabilities_checkAccess() throws Exception {
        boolean hasPermissionGrantChanged = false;
        if (!PermissionUtils.isPermissionGranted(STUB_PACKAGE_NAME,
                android.Manifest.permission.PACKAGE_USAGE_STATS)) {
            PermissionUtils.grantPermission(
                    STUB_PACKAGE_NAME, android.Manifest.permission.PACKAGE_USAGE_STATS);
            hasPermissionGrantChanged = true;
        }
        int newUserId = UserHandle.USER_NULL;
        try {
            // Verify that calling the API doesn't trigger any exceptions.
            mActivityManager.getUidProcessCapabilities(Process.myUid());

            assumeTrue(UserManager.supportsMultipleUsers());
            newUserId = createNewUser();
            assertNotEquals(UserHandle.USER_NULL, newUserId);
            startUser(newUserId);
            installExistingPackageAsUser(STUB_PACKAGE_NAME, newUserId);
            final int uidFromNewUser = UserHandle.getUid(newUserId, Process.myUid());
            // Verify that calling the API for a uid on a different user results in an exception.
            assertThrows(SecurityException.class, () -> mActivityManager.getUidProcessState(
                    uidFromNewUser));

            // Verify that calling the API with shell identity (which has
            // INTERACT_ACROSS_USERS_FULL permission) for a uid on a different user works.
            runWithShellPermissionIdentity(() -> mActivityManager.getUidProcessState(
                    uidFromNewUser));
        } finally {
            if (newUserId != UserHandle.USER_NULL) {
                removeUser(newUserId);
            }
            if (hasPermissionGrantChanged) {
                PermissionUtils.revokePermission(
                        STUB_PACKAGE_NAME, android.Manifest.permission.PACKAGE_USAGE_STATS);
            }
        }
    }

    @Test
    public void testObserveForegroundProcess() throws Exception {
        final ParcelFileDescriptor[] pfds = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommandRw("am observe-foreground-process");
        final ParcelFileDescriptor stdOut = pfds[0];
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(stdOut)) {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mTargetContext.startActivity(intent);
            final String result = reader.readLine();
            final int topPid = getRunningAppProcessInfo(SIMPLE_PACKAGE_NAME).pid;
            assertEquals(result, "New foreground process: " + topPid);
        }
    }

    @Test
    public void testKillBackgroundProcess() throws Exception {
        final String otherPackage = PACKAGE_NAME_APP1;
        final ApplicationInfo ai1 = mTargetContext.getPackageManager()
                .getApplicationInfo(otherPackage, 0);
        final WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, Process.myUid(),
                WAITFOR_MSEC);
        final WatchUidRunner uid2Watcher = new WatchUidRunner(mInstrumentation, ai1.uid,
                WAITFOR_MSEC);
        try {
            launchHome();

            // Since we're running instrumentation, our proc state will stay above FGS.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE, null);

            // Start an activity in another process in our package, our proc state will goto TOP.
            final CountDownLatch remoteBinderDeathLatch1 = startRemoteActivityAndLinkToDeath(
                    new ComponentName(mTargetContext, RemoteActivity.class),
                    uid1Watcher);

            final CountDownLatch remoteBinderDeathLatch2 = startRemoteActivityAndLinkToDeath(
                    new ComponentName(otherPackage, STUB_PACKAGE_NAME + ".RemoteActivity"),
                    uid2Watcher);

            // Launch home again so our activity will be backgrounded.
            launchHome();

            // The uid goes back to FGS state,
            // but the process with the remote activity should have been in the background.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE, null);

            // And the test package should be in background too.
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY, null);

            // Now, try to kill the background process of our own, it should succeed.
            mActivityManager.killBackgroundProcesses(mTargetContext.getPackageName());

            assertTrue("We should be able to kill our own process",
                    remoteBinderDeathLatch1.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));

            // Try to kill the background process of other app, it should fail.
            mActivityManager.killBackgroundProcesses(otherPackage);

            assertFalse("We should be able to kill the processes of other package",
                    remoteBinderDeathLatch2.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));

            // Adopt the permission, we should be able to kill it now.
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    android.Manifest.permission.KILL_ALL_BACKGROUND_PROCESSES);
            mActivityManager.killBackgroundProcesses(otherPackage);

            assertTrue("We should be able to kill the processes of other package",
                    remoteBinderDeathLatch2.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));
        } finally {
            uid1Watcher.finish();
            uid2Watcher.finish();
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSwitchToSystemUserIsRestrictedWhenItsNotAFullUser() {
        assumeHeadlessSystemUserMode();
        assumeFalse("Switch to Non-full headless SYSTEM user is only restricted when "
                        + "config_canSwitchToHeadlessSystemUser is disabled.",
                canSwitchToHeadlessSystemUser());

        runWithShellPermissionIdentity(() ->
                assertFalse(mActivityManager.switchUser(UserHandle.SYSTEM)));
    }

    @Test
    public void testSwitchToSystemUserIsAllowedWhenItsAFullUser() {
        assumeNonHeadlessSystemUserMode();

        runWithShellPermissionIdentity(() -> {
            int currentUser = ActivityManager.getCurrentUser();
            assertTrue(mActivityManager.switchUser(UserHandle.SYSTEM));
            assertTrue(switchUser(currentUser, /* waitForSwitchToComplete= */ true));
        });
    }

    @Test
    public void testSwitchToHeadlessSystemUser_whenCanSwitchToHeadlessSystemUserEnabled() {
        assumeHeadlessSystemUserMode();
        assumeTrue("Switch to Non-full headless SYSTEM user is only allowed when "
                        + "config_canSwitchToHeadlessSystemUser is enabled.",
                canSwitchToHeadlessSystemUser());

        runWithShellPermissionIdentity(() -> {
            int currentUser = ActivityManager.getCurrentUser();
            assumeTrue(mActivityManager.switchUser(UserHandle.SYSTEM));
            assertTrue(switchUser(currentUser, /* waitForSwitchToComplete= */ true));
        });
    }

    @Test
    @Ignore("b/279787820: This is an internal API "
            + "that must be one way and thus cannot be verified.")
    public void testNoteForegroundResourceUse() {
        // Testing the method without permissions
        try {
            mActivityManager.noteForegroundResourceUseBegin(1, 1, 1);
            fail("Should not be able to call noteForegroundResourceUseBegin without permission");
        } catch (SecurityException expected) {
        }
        try {
            mActivityManager.noteForegroundResourceUseEnd(1, 1, 1);
            fail("Should not be able to call noteForegroundResourceUseEnd without permission");
        } catch (SecurityException expected) {
        }

        try {
            mInstrumentation.getUiAutomation()
                    .adoptShellPermissionIdentity(
                            android.Manifest.permission.LOG_FOREGROUND_RESOURCE_USE);
        } catch (Exception e) {
            fail("Couldn't grant permission: " + e.getMessage());
        }

        // Testing invocation with permission granted
        try {
            mActivityManager.noteForegroundResourceUseBegin(1, 1, 1);
        } catch (SecurityException e) {
            fail("Could not call noteForegroundResourceUseBegin with permission" + e.getMessage());
        }
        try {
            mActivityManager.noteForegroundResourceUseEnd(1, 1, 1);
        } catch (SecurityException e) {
            fail("Could not call noteForegroundResourceUseBegin with permission" + e.getMessage());
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_UID_IMPORTANCE_LISTENER_FOR_UIDS)
    @Test
    public void testAddOnUidImportanceListener() throws Exception {
        final ApplicationInfo ai1 = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP1, 0);
        final ApplicationInfo ai2 = mTargetContext.getPackageManager()
                .getApplicationInfo(PACKAGE_NAME_APP2, 0);
        final CountDownLatch[] latchHolder = new CountDownLatch[1];
        final int[] expectedUidHolder = new int[1];
        final OnUidImportanceListener listener = new OnUidImportanceListener() {
            @Override
            public void onUidImportance(int uid, int importance) {
                if (uid == expectedUidHolder[0]) {
                    latchHolder[0].countDown();
                }
            }
        };
        try {
            // Make sure we could start activity from background
            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist +" + PACKAGE_NAME_APP1);

            // If we didn't specify the target UID, we should be able to listen on all UID events.
            mActivityManager.addOnUidImportanceListener(listener,
                    RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

            latchHolder[0] = new CountDownLatch(1);
            expectedUidHolder[0] = ai1.uid;
            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            assertTrue("Failed to receive the UID importance changes",
                    latchHolder[0].await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));

            latchHolder[0] = new CountDownLatch(1);
            expectedUidHolder[0] = ai2.uid;
            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            assertTrue("Failed to receive the UID importance changes",
                    latchHolder[0].await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));

            launchHome();
            mActivityManager.removeOnUidImportanceListener(listener);

            // Listen on the APP1's UID importance changes only.
            mActivityManager.addOnUidImportanceListener(listener,
                    RunningAppProcessInfo.IMPORTANCE_FOREGROUND, new int[] {ai1.uid});

            latchHolder[0] = new CountDownLatch(1);
            expectedUidHolder[0] = ai1.uid;
            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            assertTrue("Failed to receive the UID importance changes",
                    latchHolder[0].await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));

            latchHolder[0] = new CountDownLatch(1);
            expectedUidHolder[0] = ai2.uid;
            CommandReceiver.sendCommand(mTargetContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            assertFalse("It should not receive the UID importance changes",
                    latchHolder[0].await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));
        } finally {
            runShellCommand(mInstrumentation,
                    "cmd deviceidle whitelist -" + PACKAGE_NAME_APP1);

            mActivityManager.removeOnUidImportanceListener(listener);

            runWithShellPermissionIdentity(() -> {
                // force stop test package, where the whole test process group will be killed.
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP2);
            });
        }
    }

    private boolean switchUser(int userId, boolean waitForSwitchToComplete) throws IOException {
        StringBuilder userSwitchCommand = new StringBuilder("am switch-user ");
        if (waitForSwitchToComplete) {
            userSwitchCommand.append("-w ");
        }

        userSwitchCommand.append(userId);
        return runShellCommand(mInstrumentation, userSwitchCommand.toString()).isEmpty();
    }

    private CountDownLatch startRemoteActivityAndLinkToDeath(ComponentName activity,
            WatchUidRunner uidWatcher) throws Exception {
        final IBinder[] remoteBinderHolder = new IBinder[1];
        final CountDownLatch remoteBinderLatch = new CountDownLatch(1);
        final IBinder binder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                switch (code) {
                    case IBinder.FIRST_CALL_TRANSACTION:
                        remoteBinderHolder[0] = data.readStrongBinder();
                        remoteBinderLatch.countDown();
                        return true;
                    default:
                        return false;
                }
            }
        };
        final CountDownLatch remoteBinderDeathLatch = new CountDownLatch(1);
        final IBinder.DeathRecipient recipient = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                remoteBinderDeathLatch.countDown();
            }
        };
        final Intent intent = new Intent();
        intent.setComponent(activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Bundle extras = new Bundle();
        extras.putBinder(RemoteActivity.EXTRA_CALLBACK, binder);
        intent.putExtras(extras);
        mTargetContext.startActivity(intent);

        uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP, null);
        assertTrue("Failed to receive the callback from remote activity",
                remoteBinderLatch.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));
        assertNotNull(remoteBinderHolder[0]);
        remoteBinderHolder[0].linkToDeath(recipient, 0);

        // Finish the activity.
        final Parcel data = Parcel.obtain();
        try {
            remoteBinderHolder[0].transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
        } catch (RemoteException e) {
        } finally {
            data.recycle();
        }

        // Sleep a while to let things go through.
        Thread.sleep(WAIT_TIME);
        return remoteBinderDeathLatch;
    }

    private int createNewUser() throws Exception {
        final UserManager userManager = mTargetContext.getSystemService(UserManager.class);
        return runWithShellPermissionIdentity(() -> {
            final NewUserRequest newUserRequest = new NewUserRequest.Builder()
                    .setName("test_user")
                    .setUserType(UserManager.USER_TYPE_FULL_SECONDARY)
                    .build();
            final UserHandle newUser = userManager.createUser(newUserRequest)
                    .getUser();
            return newUser == null ? UserHandle.USER_NULL : newUser.getIdentifier();
        });
    }

    private void startUser(int userId) throws Exception {
        final String cmd = "cmd activity start-user -w " + userId;
        final String output = executeShellCommand(cmd);
        if (output.startsWith("Error")) {
            fail("Error starting the new user u" + userId + ": " + output);
        }
        final String state = executeShellCommand("am get-started-user-state " + userId);
        if (!state.contains("RUNNING_UNLOCKED")) {
            fail("Unexpected state for the new user u" + userId + ": " + state);
        }
    }

    private void removeUser(int userId) throws Exception {
        final String cmd = "cmd package remove-user " + userId;
        final String output = executeShellCommand(cmd);
        if (output.startsWith("Error")) {
            fail("Error removing the user u" + userId + ": " + output);
        }
    }

    private void installExistingPackageAsUser(String packageName, int userId)
            throws Exception {
        final String cmd = String.format("cmd package install-existing --user %d --wait %s",
                userId, packageName);
        executeShellCommand(cmd);
    }

    private int[] getLruPositions(String[] packageNames) throws Exception {
        final List<String> lru = getCachedAppsLru();
        assertTrue("Failed to get cached app list", lru.size() > 0);
        final int[] pos = new int[packageNames.length];
        for (int i = 0; i < packageNames.length; i++) {
            pos[i] = lru.indexOf(packageNames[i]);
        }
        return pos;
    }

    private void verifyLruOrders(String[] packageNames, int testIndex, boolean newest,
            BiPredicate<Integer, Integer> predicate, String msg) throws Exception {
        final List<String> lru = getCachedAppsLru();

        assertTrue("Failed to get cached app list", lru.size() > 0);
        final int[] pos = getLruPositions(packageNames);
        if (pos[testIndex] != -1) {
            for (int i = 0; i < pos.length; i++) {
                if (i == testIndex || pos[i] == -1) {
                    continue;
                }
                assertTrue(String.format(msg, packageNames[testIndex], packageNames[i]),
                        predicate.test(pos[testIndex], pos[i]));
            }
        } else if (newest) {
            for (int i = 0; i < pos.length; i++) {
                assertEquals(packageNames[i] + " should have gone", -1, pos[i]);
            }
        }
    }

    private WatchUidRunner[] initWatchUidRunners(String[] packageNames, long waitFormMs)
            throws Exception {
        final WatchUidRunner[] watchers = new WatchUidRunner[packageNames.length];
        for (int i = 0; i < packageNames.length; i++) {
            final ApplicationInfo ai = mTargetContext.getPackageManager()
                    .getApplicationInfo(packageNames[i], 0);
            watchers[i] = new WatchUidRunner(mInstrumentation, ai.uid, waitFormMs);
        }
        return watchers;
    }

    private interface ConsumerWithException<T> {
        void accept(T t) throws Exception;
    }

    private interface BiConsumerWithException<T, U> {
        void accept(T t, U u) throws Exception;
    }

    private <T> void forEach(T[] items, ConsumerWithException<T> consumer) throws Exception {
        for (T item: items) {
            consumer.accept(item);
        }
    }

    private <T, U> void forBiEach(T[] itemsA, U[] itemsB, BiConsumerWithException<T, U> consumer)
            throws Exception {
        for (int i = 0; i < itemsA.length; i++) {
            consumer.accept(itemsA[i], itemsB[i]);
        }
    }

    private List<String> getCachedAppsLru() throws Exception {
        final List<String> lru = new ArrayList<>();
        final String output = runShellCommand(mInstrumentation, "dumpsys activity lru");
        final String[] lines = output.split("\n");
        for (String line: lines) {
            if (line == null || line.indexOf(" cch") == -1) {
                continue;
            }
            final int slash = line.lastIndexOf('/');
            if (slash == -1) {
                continue;
            }
            line = line.substring(0, slash);
            final int space = line.lastIndexOf(' ');
            if (space == -1) {
                continue;
            }
            line = line.substring(space + 1);
            final int colon = line.indexOf(':');
            if (colon == -1) {
                continue;
            }
            lru.add(0, line.substring(colon + 1));
        }
        return lru;
    }

    private Bundle initWaitingForTrimLevel(final Consumer<Integer> checker) {
        final IBinder binder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                switch (code) {
                    case IBinder.FIRST_CALL_TRANSACTION:
                        final int level = data.readInt();
                        checker.accept(level);
                        return true;
                    default:
                        return false;
                }
            }
        };
        final Bundle extras = new Bundle();
        extras.putBinder(CommandReceiver.EXTRA_CALLBACK, binder);
        return extras;
    }

    private RunningAppProcessInfo getRunningAppProcessInfo(String processName) {
        try {
            return callWithShellPermissionIdentity(()-> {
                return mActivityManager.getRunningAppProcesses().stream().filter(
                        (ra) -> processName.equals(ra.processName)).findFirst().orElse(null);
            });
        } catch (Exception e) {
        }
        return null;
    }

    private boolean isProcessGone(int pid, String processName) {
        RunningAppProcessInfo info = getRunningAppProcessInfo(processName);
        return info == null || info.pid != pid;
    }

    // Copied from DeviceStatesTest
    /**
     * Make sure the screen state.
     */
    private void toggleScreenOn(final boolean screenon) throws Exception {
        if (screenon) {
            executeAndLogShellCommand("input keyevent KEYCODE_WAKEUP");
            executeAndLogShellCommand("wm dismiss-keyguard");
        } else {
            executeAndLogShellCommand("input keyevent KEYCODE_SLEEP");
        }
        // Since the screen on/off intent is ordered, they will not be sent right now.
        SystemClock.sleep(2_000);
    }

    /**
     * Simulated for idle, and then perform idle maintenance now.
     */
    private void triggerIdle(boolean idle) throws Exception {
        if (idle) {
            executeAndLogShellCommand("cmd deviceidle force-idle light");
        } else {
            executeAndLogShellCommand("cmd deviceidle unforce");
        }
        // Wait a moment to let that happen before proceeding.
        SystemClock.sleep(2_000);
    }

    /**
     * Return true if the given supplier says it's true
     */
    private boolean waitUntilTrue(long maxWait, Supplier<Boolean> supplier) throws Exception {
        final long deadLine = SystemClock.uptimeMillis() + maxWait;
        boolean result = false;
        do {
            Thread.sleep(500);
        } while (!(result = supplier.get()) && SystemClock.uptimeMillis() < deadLine);
        return result;
    }

    private void createManagedHomeActivitySession()
            throws Exception {
        if (noHomeScreen()) return;
        ComponentName homeActivity = new ComponentName(
                STUB_PACKAGE_NAME, TestHomeActivity.class.getName());
        mTestHomeSession = new HomeActivitySession(homeActivity);
    }

    /**
     * HomeActivitySession is used to replace the default home component, so that you can use
     * your preferred home for testing within the session. The original default home will be
     * restored automatically afterward.
     */
    private class HomeActivitySession {
        private PackageManager mPackageManager;
        private ComponentName mOrigHome;
        private ComponentName mSessionHome;

        HomeActivitySession(ComponentName sessionHome) throws Exception {
            mSessionHome = sessionHome;
            mPackageManager = mInstrumentation.getContext().getPackageManager();
            mOrigHome = getDefaultHomeComponent();

            runWithShellPermissionIdentity(
                    () -> mPackageManager.setComponentEnabledSetting(mSessionHome,
                            COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));
            setDefaultHome(mSessionHome);
        }

        public void close() throws Exception {
            runWithShellPermissionIdentity(
                    () -> mPackageManager.setComponentEnabledSetting(mSessionHome,
                            COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP));
            if (mOrigHome != null) {
                setDefaultHome(mOrigHome);
                mOrigHome = null;
            }
        }

        private void setDefaultHome(ComponentName componentName) throws Exception {
            executeShellCommand("cmd package set-home-activity --user "
                    + android.os.Process.myUserHandle().getIdentifier() + " "
                    + componentName.flattenToString());
        }

        private ComponentName getDefaultHomeComponent() {
            final Intent intent = new Intent(ACTION_MAIN);
            intent.addCategory(CATEGORY_HOME);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            final ResolveInfo resolveInfo = mInstrumentation.getContext()
                    .getPackageManager().resolveActivity(intent, MATCH_DEFAULT_ONLY);
            if (resolveInfo == null) {
                throw new AssertionError("Home activity not found");
            }
            return new ComponentName(resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name);
        }
    }

    private boolean isAtvDevice() {
        final Context context = mInstrumentation.getTargetContext();
        return context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEVISION);
    }

    /**
     * Gets the value of {@link com.android.internal.R.bool#config_canSwitchToHeadlessSystemUser}.
     * @return {@code true} If headless system user is allowed to run in the foreground
     * even though it is not a full user.
     */
    private boolean canSwitchToHeadlessSystemUser() {
        try {
            return mTargetContext.getResources().getBoolean(Resources.getSystem()
                    .getIdentifier("config_canSwitchToHeadlessSystemUser", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume headless system user switch is disabled.
            Log.w(TAG, "Unable to read system property " + e.getMessage());
            return false;
        }
    }

    private void assumeHeadlessSystemUserMode() {
        assumeTrue("System user is a FULL user in non-headless system user mode.",
                UserManager.isHeadlessSystemUserMode());
    }

    private void assumeNonHeadlessSystemUserMode() {
        assumeFalse("System user is not a FULL user in headless system user mode.",
                UserManager.isHeadlessSystemUserMode());
    }
}
