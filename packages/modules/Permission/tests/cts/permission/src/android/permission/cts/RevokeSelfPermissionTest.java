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

package android.permission.cts;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.HIGH_SAMPLING_RATE_SENSORS;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_ONE_TIME;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.permission.cts.PermissionUtils.getPermissionFlags;
import static android.permission.cts.PermissionUtils.grantPermission;
import static android.permission.cts.PermissionUtils.setPermissionFlags;

import static com.android.compatibility.common.util.SystemUtil.eventually;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.Process;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@AppModeFull(reason = "Null permission info in instant mode")
public class RevokeSelfPermissionTest {
    private static final String APP_PKG_NAME =
            "android.permission.cts.apptotestrevokeselfpermission";
    private static final String APK =
            "/data/local/tmp/cts-permission/CtsAppToTestRevokeSelfPermission.apk";
    private static final long ONE_TIME_TIMEOUT_MILLIS = 500;
    private static final long ONE_TIME_TIMER_UPPER_GRACE_PERIOD = 5000;

    private final Instrumentation mInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getTargetContext();
    private final ActivityManager mActivityManager =
            mContext.getSystemService(ActivityManager.class);
    private final UiDevice mUiDevice = UiDevice.getInstance(mInstrumentation);
    private String mOldOneTimePermissionTimeoutValue;
    private String mOldScreenOffTimeoutValue;
    private String mOldSleepTimeoutValue;

    @Before
    public void wakeUpScreen() {
        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP");
        SystemUtil.runShellCommand("input keyevent 82");
        mOldScreenOffTimeoutValue = SystemUtil.runShellCommand(
                "settings get system screen_off_timeout");
        mOldSleepTimeoutValue = SystemUtil.runShellCommand("settings get secure sleep_timeout");
        SystemUtil.runShellCommand("settings put system screen_off_timeout -1");
        SystemUtil.runShellCommand("settings put secure sleep_timeout -1");
    }

    @Before
    public void prepareDeviceForOneTime() {
        runWithShellPermissionIdentity(() -> {
            mOldOneTimePermissionTimeoutValue = DeviceConfig.getProperty("permissions",
                    "one_time_permissions_timeout_millis");
            DeviceConfig.setProperty("permissions", "one_time_permissions_timeout_millis",
                    Long.toString(ONE_TIME_TIMEOUT_MILLIS), false);
        });
    }

    @After
    public void uninstallApp() {
        runShellCommand("pm uninstall " + APP_PKG_NAME);
    }

    @After
    public void restoreDeviceForOneTime() {
        runWithShellPermissionIdentity(() -> {
            DeviceConfig.setProperty("permissions", "one_time_permissions_timeout_millis",
                    mOldOneTimePermissionTimeoutValue, false);
        });
        SystemUtil.runShellCommand("settings put system screen_off_timeout "
                + mOldScreenOffTimeoutValue);
        SystemUtil.runShellCommand("settings put secure sleep_timeout " + mOldSleepTimeoutValue);
    }

    @Test
    public void testMultiplePermissions() throws Throwable {
        // Trying to revoke multiple permissions including some from the same permission group
        // should work.
        installApp();
        String[] permissions = new String[] {ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION,
                CAMERA};
        for (String permission : permissions) {
            grantPermission(APP_PKG_NAME, permission);
            assertGranted(ONE_TIME_TIMER_UPPER_GRACE_PERIOD, permission);
        }
        revokePermissions(permissions);
        placeAppInBackground();
        for (String permission : permissions) {
            assertDenied(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                    permission);
        }
        uninstallApp();
    }

    @Test
    public void testNormalPermission() throws Throwable {
        // Trying to revoke a normal (non-runtime) permission should not actually revoke it.
        installApp();
        revokePermission(HIGH_SAMPLING_RATE_SENSORS);
        placeAppInBackground();
        try {
            waitUntilPermissionRevoked(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                    HIGH_SAMPLING_RATE_SENSORS);
            fail("android.permission.HIGH_SAMPLING_RATE_SENSORS was revoked");
        } catch (Throwable expected) {
            assertEquals(HIGH_SAMPLING_RATE_SENSORS + " not revoked",
                    expected.getMessage());
        }
        uninstallApp();
    }

    @Test
    public void testKillTriggersRevocation() throws Throwable {
        // Killing the process should start the revocation right away
        installApp();
        grantPermission(APP_PKG_NAME, ACCESS_FINE_LOCATION);
        assertGranted(ONE_TIME_TIMER_UPPER_GRACE_PERIOD, ACCESS_FINE_LOCATION);
        revokePermission(ACCESS_FINE_LOCATION);
        killApp();
        assertDenied(ONE_TIME_TIMER_UPPER_GRACE_PERIOD, ACCESS_FINE_LOCATION);
        uninstallApp();
    }

    @Test
    public void testNoRevocationWhileForeground() throws Throwable {
        // Even after calling revokeSelfPermissionOnKill, the permission should stay granted while
        // the package is in the foreground.
        installApp();
        grantPermission(APP_PKG_NAME, ACCESS_FINE_LOCATION);
        assertGranted(ONE_TIME_TIMER_UPPER_GRACE_PERIOD, ACCESS_FINE_LOCATION);
        revokePermission(ACCESS_FINE_LOCATION);
        keepAppInForeground(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD);
        try {
            waitUntilPermissionRevoked(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                    ACCESS_FINE_LOCATION);
            fail("android.permission.ACCESS_FINE_LOCATION was revoked");
        } catch (Throwable expected) {
            assertEquals(ACCESS_FINE_LOCATION + " not revoked",
                    expected.getMessage());
        }
        uninstallApp();
    }

    @Test
    public void testRevokeLocationPermission() throws Throwable {
        // Test behavior specific to location group: revoking fine location should not revoke coarse
        // location, and background location should not be revoked as long as a foreground
        // permission is still granted
        installApp();
        grantPermission(APP_PKG_NAME, ACCESS_COARSE_LOCATION);
        assertGranted(ONE_TIME_TIMER_UPPER_GRACE_PERIOD, ACCESS_COARSE_LOCATION);
        grantPermission(APP_PKG_NAME, ACCESS_FINE_LOCATION);
        assertGranted(ONE_TIME_TIMER_UPPER_GRACE_PERIOD, ACCESS_FINE_LOCATION);
        grantPermission(APP_PKG_NAME, ACCESS_BACKGROUND_LOCATION);
        assertGranted(ONE_TIME_TIMER_UPPER_GRACE_PERIOD, ACCESS_BACKGROUND_LOCATION);
        revokePermission(ACCESS_BACKGROUND_LOCATION);
        killApp();
        assertDenied(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                ACCESS_BACKGROUND_LOCATION);
        assertGranted(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                ACCESS_COARSE_LOCATION);
        assertGranted(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                ACCESS_FINE_LOCATION);
        grantPermission(APP_PKG_NAME, ACCESS_BACKGROUND_LOCATION);
        setPermissionFlags(APP_PKG_NAME, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_ONE_TIME, 0);
        assertGranted(ONE_TIME_TIMER_UPPER_GRACE_PERIOD, ACCESS_BACKGROUND_LOCATION);
        revokePermission(ACCESS_FINE_LOCATION);
        killApp();
        assertDenied(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                ACCESS_FINE_LOCATION);
        assertGranted(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                ACCESS_COARSE_LOCATION);
        assertGranted(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                ACCESS_BACKGROUND_LOCATION);
        revokePermission(ACCESS_COARSE_LOCATION);
        killApp();
        assertDenied(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                ACCESS_COARSE_LOCATION);
        assertDenied(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                ACCESS_BACKGROUND_LOCATION);
        uninstallApp();
    }

    @Test
    public void testNoRepromptWhenUserFixed() throws Throwable {
        // If a permission has been USER_FIXED to not granted, then revoking the permission group
        // should leave the USER_FIXED flag.
        installApp();
        grantPermission(APP_PKG_NAME, ACCESS_FINE_LOCATION);
        setPermissionFlags(APP_PKG_NAME, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_FIXED,
                FLAG_PERMISSION_USER_FIXED);
        revokePermission(ACCESS_FINE_LOCATION);
        placeAppInBackground();
        assertDenied(ONE_TIME_TIMEOUT_MILLIS + ONE_TIME_TIMER_UPPER_GRACE_PERIOD,
                ACCESS_FINE_LOCATION);
        int flags = getPermissionFlags(APP_PKG_NAME, ACCESS_BACKGROUND_LOCATION);
        assertEquals(FLAG_PERMISSION_USER_FIXED, flags & FLAG_PERMISSION_USER_FIXED);
        uninstallApp();
    }


    private void installApp() {
        runShellCommandOrThrow("pm install -r " + APK);
    }

    private void keepAppInForeground(long timeoutMillis) {
        new Thread(() -> {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() < start + timeoutMillis) {
                runWithShellPermissionIdentity(() -> {
                    if (mActivityManager.getPackageImportance(APP_PKG_NAME)
                            > IMPORTANCE_FOREGROUND) {
                        runShellCommand("am start-activity -W -n " + APP_PKG_NAME
                                + "/.RevokePermission");
                    }
                });
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        }).start();
    }

    private void placeAppInBackground() {
        boolean[] hasExited = {false};
        try {
            new Thread(() -> {
                while (!hasExited[0]) {
                    mUiDevice.pressHome();
                    mUiDevice.pressBack();
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

    /**
     * Start the app. The app will revoke the permission.
     */
    private void revokePermission(String permName) {
        revokePermissions(new String[] { permName });
    }

    private void revokePermissions(String[] permissions) {
        runShellCommand("am start-activity -W -n " + APP_PKG_NAME  + "/.RevokePermission"
                + " --esa permissions " + String.join(",", permissions));
        PackageManager pkgMgr = mContext.getPackageManager();
        eventually(() -> runWithShellPermissionIdentity(() -> {
            for (int i = 0; i < permissions.length; i++) {
                if ((pkgMgr.getPermissionInfo(permissions[i], 0).getProtection()
                        & PermissionInfo.PROTECTION_DANGEROUS) != 0) {
                    int permissionFlags = pkgMgr.getPermissionFlags(permissions[i], APP_PKG_NAME,
                                    Process.myUserHandle());
                    Assert.assertTrue((permissionFlags & FLAG_PERMISSION_ONE_TIME) != 0);
                }
            }
        }));
    }

    private void killApp() {
        runShellCommand("am force-stop " + APP_PKG_NAME);
    }

    private void assertGrantedState(String s, String permissionName, int permissionGranted,
            long timeoutMillis) {
        eventually(() -> Assert.assertEquals(s, permissionGranted,
                mContext.getPackageManager().checkPermission(permissionName, APP_PKG_NAME)),
                timeoutMillis);
    }

    private void assertGranted(long timeoutMillis, String permissionName) {
        assertGrantedState("Permission was never granted", permissionName,
                PackageManager.PERMISSION_GRANTED, timeoutMillis);
    }

    private void assertDenied(long timeoutMillis, String permissionName) {
        assertGrantedState("Permission was never revoked", permissionName,
                PackageManager.PERMISSION_DENIED, timeoutMillis);
    }

    private void waitUntilPermissionRevoked(long timeoutMillis, String permName) throws Throwable {
        try {
            eventually(() -> {
                PackageInfo appInfo = mContext.getPackageManager().getPackageInfo(APP_PKG_NAME,
                        GET_PERMISSIONS);

                for (int i = 0; i < appInfo.requestedPermissions.length; i++) {
                    if (appInfo.requestedPermissions[i].equals(permName)
                            && (
                            (appInfo.requestedPermissionsFlags[i] & REQUESTED_PERMISSION_GRANTED)
                                    == 0)) {
                        return;
                    }
                }

                fail(permName + " not revoked");
            }, timeoutMillis);
        } catch (RuntimeException e) {
            throw e.getCause();
        }
    }
}
