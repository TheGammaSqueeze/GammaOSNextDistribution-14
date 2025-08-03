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

package android.permission.cts;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.compatibility.common.util.SystemUtil.eventually;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assume.assumeFalse;

import android.app.ActivityManager;
import android.app.DreamManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.rule.ScreenRecordRule;
import android.provider.DeviceConfig;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UiAutomatorUtils2;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ScreenRecordRule.ScreenRecord
public class OneTimePermissionTest {

    private static final String APP_PKG_NAME = "android.permission.cts.appthatrequestpermission";
    private static final String CUSTOM_CAMERA_PERM_APP_PKG_NAME =
            "android.permission.cts.appthatrequestcustomcamerapermission";
    private static final String APK =
            "/data/local/tmp/cts-permission/CtsAppThatRequestsOneTimePermission.apk";
    private static final String CUSTOM_CAMERA_PERM_APK =
            "/data/local/tmp/cts-permission/CtsAppThatRequestCustomCameraPermission.apk";
    private static final String EXTRA_FOREGROUND_SERVICE_LIFESPAN =
            "android.permission.cts.OneTimePermissionTest.EXTRA_FOREGROUND_SERVICE_LIFESPAN";
    private static final String EXTRA_FOREGROUND_SERVICE_STICKY =
            "android.permission.cts.OneTimePermissionTest.EXTRA_FOREGROUND_SERVICE_STICKY";

    public static final String CUSTOM_PERMISSION = "appthatrequestcustomcamerapermission.CUSTOM";

    private static final long ONE_TIME_TIMEOUT_MILLIS = 5000;
    private static final long ONE_TIME_KILLED_DELAY_MILLIS = 5000;
    private static final long ONE_TIME_TIMER_LOWER_GRACE_PERIOD = 1000;
    private static final long ONE_TIME_TIMER_UPPER_GRACE_PERIOD = 10000;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final PackageManager mPackageManager = mContext.getPackageManager();
    private final UiDevice mUiDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final ActivityManager mActivityManager =
            mContext.getSystemService(ActivityManager.class);
    private String mOldOneTimePermissionTimeoutValue;
    private String mOldOneTimePermissionKilledDelayValue;

    @Rule
    public final ScreenRecordRule sScreenRecordRule = new ScreenRecordRule(false, false);

    @Rule
    public IgnoreAllTestsRule mIgnoreAutomotive = new IgnoreAllTestsRule(
            mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

    @Before
    public void wakeUpScreen() {
        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP");

        SystemUtil.runShellCommand("input keyevent 82");
    }

    @Before
    public void installApp() {
        runShellCommandOrThrow("pm install -r " + APK);
        runShellCommandOrThrow("pm install -r " + CUSTOM_CAMERA_PERM_APK);
    }

    @Before
    public void prepareDeviceForOneTime() {
        runWithShellPermissionIdentity(() -> {
            mOldOneTimePermissionTimeoutValue = DeviceConfig.getProperty("permissions",
                    "one_time_permissions_timeout_millis");
            mOldOneTimePermissionKilledDelayValue = DeviceConfig.getProperty("permissions",
                    "one_time_permissions_killed_delay_millis");
            DeviceConfig.setProperty("permissions", "one_time_permissions_timeout_millis",
                    Long.toString(ONE_TIME_TIMEOUT_MILLIS), false);
            DeviceConfig.setProperty("permissions",
                    "one_time_permissions_killed_delay_millis",
                    Long.toString(ONE_TIME_KILLED_DELAY_MILLIS), false);
        });
    }

    @After
    public void uninstallApp() {
        runShellCommand("pm uninstall " + APP_PKG_NAME);
        runShellCommand("pm uninstall " + CUSTOM_CAMERA_PERM_APP_PKG_NAME);
    }

    @After
    public void restoreDeviceForOneTime() {
        runWithShellPermissionIdentity(
                () -> {
                    DeviceConfig.setProperty("permissions", "one_time_permissions_timeout_millis",
                            mOldOneTimePermissionTimeoutValue, false);
                    DeviceConfig.setProperty("permissions",
                            "one_time_permissions_killed_delay_millis",
                            mOldOneTimePermissionKilledDelayValue, false);
                });
    }

    @Test
    public void testOneTimePermission() throws Throwable {
        startApp();

        CompletableFuture<Long> exitTime = registerAppExitListener();

        clickOneTimeButton();

        exitApp();

        assertGranted(5000);

        assertDenied(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD);

        assertExpectedLifespan(exitTime, ONE_TIME_TIMEOUT_MILLIS);
    }

    @Ignore
    @Test
    public void testForegroundServiceMaintainsPermission() throws Throwable {
        startApp();

        CompletableFuture<Long> exitTime = registerAppExitListener();

        clickOneTimeButton();

        long expectedLifespanMillis = 2 * ONE_TIME_TIMEOUT_MILLIS;
        startAppForegroundService(expectedLifespanMillis, false);

        exitApp();

        assertGranted(5000);

        assertDenied(expectedLifespanMillis + ONE_TIME_TIMER_UPPER_GRACE_PERIOD);

        assertExpectedLifespan(exitTime, expectedLifespanMillis);

    }

    @Test
    public void testPermissionRevokedOnKill() throws Throwable {
        startApp();

        clickOneTimeButton();

        exitApp();

        assertGranted(5000);

        mUiDevice.waitForIdle();
        SystemUtil.runWithShellPermissionIdentity(() ->
                mActivityManager.killBackgroundProcesses(APP_PKG_NAME));

        runWithShellPermissionIdentity(
                () -> Thread.sleep(DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
                "one_time_permissions_killed_delay_millis", 5000L)));
        assertDenied(500);
    }

    @Test
    @FlakyTest
    public void testStickyServiceMaintainsPermissionOnRestart() throws Throwable {
        startApp();

        clickOneTimeButton();

        startAppForegroundService(2 * ONE_TIME_TIMEOUT_MILLIS, true);

        exitApp();

        assertGranted(5000);
        mUiDevice.waitForIdle();
        Thread.sleep(ONE_TIME_TIMEOUT_MILLIS);

        runShellCommand("am crash " + APP_PKG_NAME);

        eventually(() -> runWithShellPermissionIdentity(() -> {
            if (mActivityManager.getPackageImportance(APP_PKG_NAME) <= IMPORTANCE_CACHED) {
                throw new AssertionError("App was never killed");
            }
        }));

        eventually(() -> runWithShellPermissionIdentity(() -> {
            if (mActivityManager.getPackageImportance(APP_PKG_NAME)
                    > IMPORTANCE_FOREGROUND_SERVICE) {
                throw new AssertionError("Foreground service never resumed");
            }
            Assert.assertEquals("Service resumed without permission",
                    PackageManager.PERMISSION_GRANTED, mContext.getPackageManager()
                            .checkPermission(ACCESS_FINE_LOCATION, APP_PKG_NAME));
        }));
    }

    @Test
    @AsbSecurityTest(cveBugId = 237405974L)
    public void testCustomPermissionIsGrantedOneTime() throws Throwable {
        startApp(new ComponentName(CUSTOM_CAMERA_PERM_APP_PKG_NAME,
                CUSTOM_CAMERA_PERM_APP_PKG_NAME + ".RequestCameraPermission"));

        // We're only manually granting CAMERA, but the app will later request CUSTOM and get it
        // granted silently. This is intentional since it's in the same group but both should
        // eventually be revoked
        clickOneTimeButton();

        // Just waiting for the revocation
        eventually(() -> Assert.assertEquals(PackageManager.PERMISSION_DENIED,
                mContext.getPackageManager()
                        .checkPermission(CAMERA, CUSTOM_CAMERA_PERM_APP_PKG_NAME)), 30000);

        // This checks the vulnerability
        eventually(() -> Assert.assertEquals(PackageManager.PERMISSION_DENIED,
                mContext.getPackageManager()
                        .checkPermission(CUSTOM_PERMISSION, CUSTOM_CAMERA_PERM_APP_PKG_NAME)),
                30000);

    }

    private void assertGrantedState(String s, int permissionGranted, long timeoutMillis) {
        eventually(() -> Assert.assertEquals(s,
                permissionGranted, mPackageManager
                        .checkPermission(ACCESS_FINE_LOCATION, APP_PKG_NAME)), timeoutMillis);
    }

    private void assertGranted(long timeoutMillis) {
        assertGrantedState("Permission was never granted", PackageManager.PERMISSION_GRANTED,
                timeoutMillis);
    }

    private void assertDenied(long timeoutMillis) {
        assertGrantedState("Permission was never revoked", PackageManager.PERMISSION_DENIED,
                timeoutMillis);
    }

    private void assertExpectedLifespan(CompletableFuture<Long> exitTime, long expectedLifespan)
            throws InterruptedException, java.util.concurrent.ExecutionException,
            java.util.concurrent.TimeoutException {
        long grantedLength = System.currentTimeMillis() - exitTime.get(0, TimeUnit.MILLISECONDS);
        if (grantedLength + ONE_TIME_TIMER_LOWER_GRACE_PERIOD < expectedLifespan) {
            throw new AssertionError(
                    "The one time permission lived shorter than expected. expected: "
                            + expectedLifespan + "ms but was: " + grantedLength + "ms");
        }
    }

    private void exitApp() {
        boolean[] hasExited = {false};
        try {
            new Thread(() -> {
                while (!hasExited[0]) {
                    DreamManager mDreamManager = mContext.getSystemService(DreamManager.class);
                    mUiDevice.pressHome();
                    mUiDevice.pressBack();
                    runWithShellPermissionIdentity(() -> {
                        if (mDreamManager.isDreaming()) {
                            mDreamManager.stopDream();
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
            }).start();
            eventually(() -> {
                runWithShellPermissionIdentity(() -> {
                    if (mActivityManager.getPackageImportance(APP_PKG_NAME)
                            <= IMPORTANCE_FOREGROUND) {
                        throw new AssertionError("Unable to exit application");
                    }
                });
            });
        } finally {
            hasExited[0] = true;
        }
    }

    private void clickOneTimeButton() throws Throwable {
        final UiObject2 uiObject = UiAutomatorUtils2.waitFindObject(By.res(
                "com.android.permissioncontroller:id/permission_allow_one_time_button"), 10000);
        Thread.sleep(500);
        uiObject.click();
    }

    /**
     * Start the app. The app will request the permissions.
     */
    private void startApp(ComponentName componentName) {
        // One time permission is not applicable for Wear OS.
        // The only permissions available are Allow or Deny
        assumeFalse(
                "Skipping test: One time permission is not supported in Wear OS",
                FeatureUtil.isWatch());
        Intent startApp = new Intent();
        startApp.setComponent(componentName);
        startApp.setFlags(FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(startApp);
    }

    /**
     * Start the default app for these tests. The app will request the permissions.
     */
    private void startApp() {
        startApp(new ComponentName(APP_PKG_NAME, APP_PKG_NAME + ".RequestPermission"));
    }

    private void startAppForegroundService(long lifespanMillis, boolean sticky) {
        Intent intent = new Intent()
                .setComponent(new ComponentName(
                APP_PKG_NAME, APP_PKG_NAME + ".KeepAliveForegroundService"))
                .putExtra(EXTRA_FOREGROUND_SERVICE_LIFESPAN, lifespanMillis)
                .putExtra(EXTRA_FOREGROUND_SERVICE_STICKY, sticky);
        mContext.startService(intent);
    }

    private CompletableFuture<Long> registerAppExitListener() {
        CompletableFuture<Long> exitTimeCallback = new CompletableFuture<>();
        try {
            int uid = mContext.getPackageManager().getPackageUid(APP_PKG_NAME, 0);
            runWithShellPermissionIdentity(() ->
                    mActivityManager.addOnUidImportanceListener(new SingleAppExitListener(
                            uid, IMPORTANCE_FOREGROUND, exitTimeCallback), IMPORTANCE_FOREGROUND));
        } catch (PackageManager.NameNotFoundException e) {
            throw new AssertionError("Package not found.", e);
        }
        return exitTimeCallback;
    }

    private class SingleAppExitListener implements ActivityManager.OnUidImportanceListener {

        private final int mUid;
        private final int mImportance;
        private final CompletableFuture<Long> mCallback;

        SingleAppExitListener(int uid, int importance, CompletableFuture<Long> callback) {
            mUid = uid;
            mImportance = importance;
            mCallback = callback;
        }

        @Override
        public void onUidImportance(int uid, int importance) {
            if (uid == mUid && importance > mImportance) {
                mCallback.complete(System.currentTimeMillis());
                mActivityManager.removeOnUidImportanceListener(this);
            }
        }
    }
}
