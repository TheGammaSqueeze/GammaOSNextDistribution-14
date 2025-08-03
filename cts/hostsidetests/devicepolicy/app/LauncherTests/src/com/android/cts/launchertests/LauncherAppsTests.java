/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.cts.launchertests;

import static android.os.Process.myUserHandle;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tests for LauncherApps service
 */
@RunWith(AndroidJUnit4.class)
public class LauncherAppsTests {

    private static final String TAG = LauncherAppsTests.class.getSimpleName();

    public static final String SIMPLE_APP_PACKAGE = "com.android.cts.launcherapps.simpleapp";
    private static final String HAS_LAUNCHER_ACTIVITY_APP_PACKAGE =
            "com.android.cts.haslauncheractivityapp";
    private static final String NO_LAUNCHER_ACTIVITY_APP_PACKAGE =
            "com.android.cts.nolauncheractivityapp";
    private static final String NO_PERMISSION_APP_PACKAGE =
            "com.android.cts.nopermissionapp";
    private static final String LAUNCHER_ACTIVITY_COMPONENT =
            "com.android.cts.haslauncheractivityapp/.MainActivity";

    private static final String SYNTHETIC_APP_DETAILS_ACTIVITY = "android.app.AppDetailsActivity";

    public static final String USER_EXTRA = "user_extra";
    public static final String PACKAGE_EXTRA = "package_extra";
    public static final String REPLY_EXTRA = "reply_extra";

    private static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";

    public static final int MSG_RESULT = 0;
    public static final int MSG_CHECK_PACKAGE_ADDED = 1;
    public static final int MSG_CHECK_PACKAGE_REMOVED = 2;
    public static final int MSG_CHECK_PACKAGE_CHANGED = 3;
    public static final int MSG_CHECK_NO_PACKAGE_ADDED = 4;

    public static final int RESULT_PASS = 1;
    public static final int RESULT_FAIL = 2;
    public static final int RESULT_TIMEOUT = 3;

    private Context mContext;
    private UserManager mUserManager;
    private LauncherApps mLauncherApps;
    private UserHandle mUser;
    private Instrumentation mInstrumentation;
    private Messenger mService;
    private Connection mConnection;
    private Result mResult;
    private Messenger mResultMessenger;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Bundle arguments = InstrumentationRegistry.getArguments();
        mContext = mInstrumentation.getContext();
        mUserManager = mContext.getSystemService(UserManager.class);
        mUser = getUserHandleArgument("testUser", arguments);

        Log.d(TAG, "Running as user " + myUserHandle() + " and checking for launcher on "
                + "user " + mUser);
        mLauncherApps = mContext.getSystemService(LauncherApps.class);

        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.android.cts.launchertests.support",
                        "com.android.cts.launchertests.support.LauncherCallbackTestsService"));

        mConnection = new Connection();
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mConnection.waitForService();
        mResult = new Result(Looper.getMainLooper());
        mResultMessenger = new Messenger(mResult);
    }

    @Test
    public void testGetActivitiesForUserFails() throws Exception {
        expectSecurityException(() -> mLauncherApps.getActivityList(null, mUser),
                "getActivities for non-profile user failed to throw exception");
    }

    @Test
    public void testSimpleAppInstalledForUser() throws Exception {
        List<LauncherActivityInfo> activities =
                mLauncherApps.getActivityList(null, mUser);
        // Check simple app is there.
        boolean foundSimpleApp = false;
        for (LauncherActivityInfo activity : activities) {
            if (activity.getComponentName().getPackageName().equals(
                    SIMPLE_APP_PACKAGE)) {
                foundSimpleApp = true;
                assertThat(activity.getLoadingProgress()).isWithin(1.0e-10f).of(1.0f);
            }
            assertThat(activity.getUser()).isEqualTo(mUser);
        }
        assertThat(foundSimpleApp).isTrue();

        // Also make sure getApplicationInfo works too.
        ApplicationInfo ai =
                mLauncherApps.getApplicationInfo(SIMPLE_APP_PACKAGE, /* flags= */ 0, mUser);
        assertThat(ai.packageName).isEqualTo(SIMPLE_APP_PACKAGE);
        assertThat(UserHandle.getUserHandleForUid(ai.uid)).isEqualTo(mUser);
    }

    @Test
    public void testAccessPrimaryProfileFromManagedProfile() throws Exception {
        assertThat(mLauncherApps.getActivityList(null, mUser)).isEmpty();

        expectNameNotFoundException(
                () -> mLauncherApps.getApplicationInfo(SIMPLE_APP_PACKAGE, /* flags= */ 0, mUser),
                "get applicationInfo failed to throw name not found exception");
        assertThat(mLauncherApps.isPackageEnabled(SIMPLE_APP_PACKAGE, mUser)).isFalse();

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.android.com/"));
        assertThat(mLauncherApps.resolveActivity(intent, mUser)).isNull();
    }

    @Test
    public void testGetProfiles_fromMainProfile() {
        List<UserHandle> profiles = mLauncherApps.getProfiles();
        assertThat(profiles).hasSize(2);
        assertThat(profiles).contains(myUserHandle());
        assertThat(profiles).containsExactlyElementsIn(mUserManager.getUserProfiles());
    }

    @Test
    public void testGetProfiles_fromManagedProfile() {
        final List<UserHandle> profiles = mLauncherApps.getProfiles();
        assertThat(profiles).containsExactly(myUserHandle());
    }

    @Test
    public void testPackageAddedCallbackForUser() throws Throwable {
        int result = sendMessageToCallbacksService(MSG_CHECK_PACKAGE_ADDED,
                mUser, SIMPLE_APP_PACKAGE);
        assertThat(result).isEqualTo(RESULT_PASS);
    }

    @Test
    public void testPackageRemovedCallbackForUser() throws Throwable {
        int result = sendMessageToCallbacksService(MSG_CHECK_PACKAGE_REMOVED,
                mUser, SIMPLE_APP_PACKAGE);
        assertThat(result).isEqualTo(RESULT_PASS);
    }

    @Test
    public void testPackageChangedCallbackForUser() throws Throwable {
        int result = sendMessageToCallbacksService(MSG_CHECK_PACKAGE_CHANGED,
                mUser, SIMPLE_APP_PACKAGE);
        assertThat(result).isEqualTo(RESULT_PASS);
    }

    @Test
    public void testNoPackageAddedCallbackForUser() throws Throwable {
        int result = sendMessageToCallbacksService(MSG_CHECK_NO_PACKAGE_ADDED,
                mUser, SIMPLE_APP_PACKAGE);
        assertThat(result).isEqualTo(RESULT_PASS);
    }

    @Test
    public void testLaunchNonExportActivityFails() throws Exception {
        expectSecurityException(() -> mLauncherApps.startMainActivity(new ComponentName(
                SIMPLE_APP_PACKAGE, SIMPLE_APP_PACKAGE + ".NonExportedActivity"),
                mUser, null, null),
                "starting non-exported activity failed to throw exception");
    }

    @Test
    public void testLaunchNonExportLauncherFails() throws Exception {
        expectSecurityException(() -> mLauncherApps.startMainActivity(new ComponentName(
                SIMPLE_APP_PACKAGE, SIMPLE_APP_PACKAGE + ".NonLauncherActivity"),
                mUser, null, null),
                "starting non-launcher activity failed to throw exception");
    }

    @Test
    public void testLaunchMainActivity() throws Exception {
        ActivityLaunchedReceiver receiver = new ActivityLaunchedReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ActivityLaunchedReceiver.ACTIVITY_LAUNCHED_ACTION);
        mContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED_UNAUDITED);
        ComponentName compName = new ComponentName(SIMPLE_APP_PACKAGE, SIMPLE_APP_PACKAGE
                + ".SimpleActivity");
        Log.i(TAG, "Launching " + compName.flattenToShortString() + " on user " + mUser);
        mLauncherApps.startMainActivity(compName, mUser, null, null);
        assertWithMessage("Activity %s launched for user %s", compName.flattenToShortString(),
                mUser).that(receiver.waitForActivity()).isEqualTo(RESULT_PASS);
        mContext.unregisterReceiver(receiver);
    }

    @Test
    public void testReverseAccessNoThrow() throws Exception {
        // Trying to access the main profile from a managed profile -> shouldn't throw but
        // should just return false.
        assertThat(mLauncherApps.isPackageEnabled("android", mUser)).isFalse();
    }

    @Test
    public void testProfileOwnerLauncherActivityInjected() throws Exception {
        assertActivityInjected(MANAGED_PROFILE_PKG);
    }

    @Test
    public void testNoLauncherActivityAppNotInjected() throws Exception {
        // NoLauncherActivityApp is installed for duration of this test - make sure
        // it's NOT present on the activity list
        assertInjectedActivityNotFound(NO_LAUNCHER_ACTIVITY_APP_PACKAGE);
    }

    @Test
    public void testNoPermissionAppNotInjected() throws Exception {
        // NoPermissionApp is installed for duration of this test - make sure
        // it's NOT present on the activity list
        assertInjectedActivityNotFound(NO_PERMISSION_APP_PACKAGE);
    }



    @Test
    public void testProfileOwnerInjectedActivityNotFound() throws Exception {
        assertInjectedActivityNotFound(MANAGED_PROFILE_PKG);
    }

    @Test
    public void testNoSystemAppHasSyntheticAppDetailsActivityInjected() throws Exception {
        Log.d(TAG, "testNoSystemAppHasSyntheticAppDetailsActivityInjected() for user " + mUser);
        List<LauncherActivityInfo> activities = mLauncherApps.getActivityList(null, mUser);
        logActivities(activities);
        for (LauncherActivityInfo activity : activities) {
            if (!activity.getUser().equals(mUser)) {
                continue;
            }
            ApplicationInfo appInfo = activity.getApplicationInfo();
            boolean isSystemApp = ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    || ((appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
            if (isSystemApp) {
                // make sure we haven't generated a synthetic app details activity for it
                assertWithMessage("Found a system app that had a synthetic activity generated,"
                        + " package name: %s; activity name: %s",
                        activity.getComponentName().getPackageName(), activity.getName())
                                .that(activity.getName())
                                .isNotEqualTo(SYNTHETIC_APP_DETAILS_ACTIVITY);
            }
        }
    }

    private void disableLauncherActivity() throws IOException {
        runShellCommand("pm disable --user %d %s", mUser.getIdentifier(),
                LAUNCHER_ACTIVITY_COMPONENT);
    }

    private void expectSecurityException(ExceptionRunnable action, String failMessage)
            throws Exception {
        try {
            action.run();
            fail(failMessage);
        } catch (SecurityException e) {
            // expected
        }
    }

    private void expectNameNotFoundException(ExceptionRunnable action, String failMessage)
            throws Exception {
        try {
            action.run();
            fail(failMessage);
        } catch (PackageManager.NameNotFoundException e) {
            // expected
        }
    }

    private void assertActivityInjected(String targetPackage) {
        Log.d(TAG, "Getting activities for package " + targetPackage + " on user " + mUser);
        List<LauncherActivityInfo> activities = mLauncherApps.getActivityList(null, mUser);
        logActivities(activities);

        boolean noLaunchableActivityAppFound = false;
        for (LauncherActivityInfo activity : activities) {
            UserHandle user = activity.getUser();
            if (!user.equals(mUser)) {
                Log.w(TAG, "Skipping activity " + toString(activity) + " from user " + user);
                continue;
            }
            ComponentName compName = activity.getComponentName();
            if (compName.getPackageName().equals(targetPackage)) {
                noLaunchableActivityAppFound = true;
                // make sure it points to the synthetic app details activity
                assertWithMessage("name of synthetic app").that(activity.getName())
                        .isEqualTo(SYNTHETIC_APP_DETAILS_ACTIVITY);
                // make sure it's both exported and enabled
                try {
                    PackageManager pm = mContext.getPackageManager();
                    ActivityInfo ai = pm.getActivityInfo(compName, /* flags= */ 0);
                    assertWithMessage("component %s enabled", compName.flattenToShortString())
                            .that(ai.enabled).isTrue();
                    assertWithMessage("component %s exported", compName.flattenToShortString())
                            .that(ai.exported).isTrue();
                } catch (NameNotFoundException e) {
                    fail("Package " + compName.getPackageName() + " not found: " + e);
                }
            }
        }
        assertWithMessage("user %s has no launchable activity for app %s", mUser, targetPackage)
                .that(noLaunchableActivityAppFound).isTrue();
    }

    @FunctionalInterface
    public interface ExceptionRunnable {
        void run() throws Exception;
    }

    private UserHandle getUserHandleArgument(String key, Bundle arguments) throws Exception {
        String serial = arguments.getString(key);
        if (serial == null) {
            return null;
        }
        int serialNo = Integer.parseInt(serial);
        return mUserManager.getUserForSerialNumber(serialNo);
    }

    private final class Connection implements ServiceConnection {
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            mSemaphore.release();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }

        public void waitForService() {
            try {
                if (mSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
            }
            fail("failed to connec to service");
        }
    };

    private static final class Result extends Handler {

        private final Semaphore mSemaphore = new Semaphore(0);
        public int result = 0;

        public Result(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_RESULT) {
                result = msg.arg1;
                mSemaphore.release();
            } else {
                super.handleMessage(msg);
            }
        }

        public int waitForResult() {
            try {
                if (mSemaphore.tryAcquire(120, TimeUnit.SECONDS)) {
                     return result;
                }
            } catch (InterruptedException e) {
            }
            return RESULT_TIMEOUT;
        }
    }

    public final class ActivityLaunchedReceiver extends BroadcastReceiver {
        public static final String ACTIVITY_LAUNCHED_ACTION =
                "com.android.cts.launchertests.LauncherAppsTests.LAUNCHED_ACTION";

        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTIVITY_LAUNCHED_ACTION)) {
                mSemaphore.release();
            }
        }

        public int waitForActivity() {
            try {
                if (mSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    return RESULT_PASS;
                }
            } catch (InterruptedException e) {
            }
            return RESULT_TIMEOUT;
        }
    }

    private int sendMessageToCallbacksService(int msg, UserHandle user, String packageName)
            throws Throwable {
        Bundle params = new Bundle();
        params.putParcelable(USER_EXTRA, user);
        params.putString(PACKAGE_EXTRA, packageName);

        Message message = Message.obtain(null, msg, params);
        message.replyTo = mResultMessenger;

        mService.send(message);

        return mResult.waitForResult();
    }

    private void assertInjectedActivityNotFound(String targetPackage) {
        Log.d(TAG, "Searching for package " + targetPackage + " on user " + mUser);
        List<LauncherActivityInfo> activities = mLauncherApps.getActivityList(null, mUser);
        logActivities(activities);
        for (LauncherActivityInfo activity : activities) {
            if (!activity.getUser().equals(mUser)) {
                continue;
            }
            ComponentName compName = activity.getComponentName();
            if (compName.getPackageName().equals(targetPackage)) {
                fail("Injected activity found: " + compName.flattenToString());
            }
        }
    }

    private void logActivities(List<LauncherActivityInfo> activities) {
        Log.d(TAG, "Got " + activities.size() + " activities: " + activities.stream()
                .map((info) -> toString(info))
                .collect(Collectors.toList()));
    }

    private void runShellCommand(String format, Object...args) throws IOException {
        String command = String.format(format, args);
        Log.i(TAG, "Running command: " + command);
        String output = SystemUtil.runShellCommand(mInstrumentation, command);
        Log.d(TAG, "Output: " + output);
    }

    private String toString(LauncherActivityInfo info) {
        return info == null ? null : info.getComponentName().flattenToShortString();
    }

    private void assumeNotHeadlessSystemUserMode() {
        // On headless system user mode, the current user is a profile owner, and hence
        // the synthetic activity is not listed by LauncherApps.getActivityList()
        assumeFalse("test skipped on headless system user mode",
                UserManager.isHeadlessSystemUserMode());
    }
}
