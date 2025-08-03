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

package android.app.cts.fgstest;

import static android.app.fgstesthelper.LocalForegroundServiceBase.RESULT_INVALID_TYPE_EXCEPTION;
import static android.app.fgstesthelper.LocalForegroundServiceBase.RESULT_MISSING_TYPE_EXCEPTION;
import static android.app.fgstesthelper.LocalForegroundServiceBase.RESULT_OK;
import static android.app.fgstesthelper.LocalForegroundServiceBase.RESULT_SECURITY_EXCEPTION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.ForegroundServiceTypePolicy;
import android.app.ForegroundServiceTypePolicy.ForegroundServiceTypePolicyInfo;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.app.fgstesthelper.LocalForegroundServiceBase;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.location.LocationManager;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.util.ArrayMap;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.SystemUtil;
import com.android.internal.util.ArrayUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@Presubmit
public final class ActivityManagerForegroundServiceTypeTest {
    private static final String TAG = ActivityManagerForegroundServiceTypeTest.class.getName();

    private static final String TEST_PKG_NAME_TARGET = "android.app.fgstesthelper";
    private static final String TEST_PKG_NAME_CURRENT = "android.app.fgstesthelpercurrent";
    private static final String TEST_PKG_NAME_API33 = "android.app.fgstesthelper33";
    private static final String SHELL_PKG_NAME = "com.android.shell";

    private static final String TEST_CLS_NAME_NO_TYPE =
            "android.app.fgstesthelper.LocalForegroundServiceNoType";
    private static final String TEST_CLS_NAME_ALL_TYPE =
            "android.app.fgstesthelper.LocalForegroundServiceAllTypes";
    private static final String FGS_TYPE_PERMISSION_CHANGE_ID = "FGS_TYPE_PERMISSION_CHANGE_ID";

    private static final long WAITFOR_MSEC = 5000;

    private static final ComponentName TEST_COMP_TARGET_FGS_NO_TYPE = new ComponentName(
            TEST_PKG_NAME_TARGET, TEST_CLS_NAME_NO_TYPE);
    private static final ComponentName TEST_COMP_TARGET_FGS_ALL_TYPE = new ComponentName(
            TEST_PKG_NAME_TARGET, TEST_CLS_NAME_ALL_TYPE);
    private static final ComponentName TEST_COMP_CURRENT_FGS_NO_TYPE = new ComponentName(
            TEST_PKG_NAME_CURRENT, TEST_CLS_NAME_NO_TYPE);
    private static final ComponentName TEST_COMP_CURRENT_FGS_ALL_TYPE = new ComponentName(
            TEST_PKG_NAME_CURRENT, TEST_CLS_NAME_ALL_TYPE);
    private static final ComponentName TEST_COMP_API33_FGS_NO_TYPE = new ComponentName(
            TEST_PKG_NAME_API33, TEST_CLS_NAME_NO_TYPE);
    private static final ComponentName TEST_COMP_API33_FGS_ALL_TYPE = new ComponentName(
            TEST_PKG_NAME_API33, TEST_CLS_NAME_ALL_TYPE);

    private static final String SPECIAL_PERMISSION_OP_ALLOWLISTED = "SPECIAL_PERM_ALLOWLISTED";
    private static final ArrayMap<String, SpecialPermissionOp> sSpecialPermissionOps =
            new ArrayMap<>();

    private Context mContext;
    private Context mTargetContext;
    private Instrumentation mInstrumentation;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mTargetContext = mInstrumentation.getTargetContext();
        mActivityManager = mInstrumentation.getContext().getSystemService(ActivityManager.class);
        mPackageManager = mInstrumentation.getContext().getPackageManager();
        if (sSpecialPermissionOps.isEmpty()) {
            sSpecialPermissionOps.put(SPECIAL_PERMISSION_OP_ALLOWLISTED,
                    new DeviceAllowlistPermissionOp());
        }
    }

    @After
    public void tearDown() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mActivityManager.forceStopPackage(TEST_PKG_NAME_CURRENT);
            mActivityManager.forceStopPackage(TEST_PKG_NAME_API33);
        });
    }

    @ApiTest(apis = {"android.app.Service#startForeground"})
    @Test
    public void testForegroundServiceTypeMissing() throws Exception {
        try {
            enablePermissionEnforcement(false, TEST_PKG_NAME_CURRENT);
            enablePermissionEnforcement(false, TEST_PKG_NAME_API33);
            testForegroundServiceTypeDisabledCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
                    RESULT_MISSING_TYPE_EXCEPTION,
                    TEST_COMP_API33_FGS_NO_TYPE, TEST_COMP_CURRENT_FGS_NO_TYPE);
        } finally {
            clearPermissionEnforcement(TEST_PKG_NAME_CURRENT);
            clearPermissionEnforcement(TEST_PKG_NAME_API33);
        }
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_NONE"})
    @Test
    public void testForegroundServiceTypeNone() throws Exception {
        try {
            enablePermissionEnforcement(false, TEST_PKG_NAME_CURRENT);
            enablePermissionEnforcement(false, TEST_PKG_NAME_API33);
            testForegroundServiceTypeDisabledCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE,
                    TEST_COMP_API33_FGS_NO_TYPE, TEST_COMP_CURRENT_FGS_NO_TYPE);
        } finally {
            clearPermissionEnforcement(TEST_PKG_NAME_CURRENT);
            clearPermissionEnforcement(TEST_PKG_NAME_API33);
        }
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_DATA_SYNC"})
    @Test
    public void testForegroundServiceTypeDataSync() throws Exception {
        try {
            enablePermissionEnforcement(false, TEST_PKG_NAME_CURRENT);
            enablePermissionEnforcement(false, TEST_PKG_NAME_API33);
            testForegroundServiceTypeDisabledCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    TEST_COMP_API33_FGS_ALL_TYPE, TEST_COMP_CURRENT_FGS_ALL_TYPE);
        } finally {
            clearPermissionEnforcement(TEST_PKG_NAME_CURRENT);
            clearPermissionEnforcement(TEST_PKG_NAME_API33);
        }
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_DATA_SYNC"})
    @Test
    public void testForegroundServiceTypeDataSyncPermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK"})
    @Test
    public void testForegroundServiceTypeMediaPlaybackPermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_PHONE_CALL"})
    @Test
    public void testForegroundServiceTypePhoneCallPermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_LOCATION"})
    @Test
    public void testForegroundServiceTypeLocationPermission() throws Exception {
        final LocationManager lm = mContext.getSystemService(LocationManager.class);
        final UserHandle user = Process.myUserHandle();
        final boolean wasEnabled = lm.isLocationEnabledForUser(user);
        try {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                lm.setLocationEnabledForUser(true, user);
            });
            testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } finally {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                lm.setLocationEnabledForUser(wasEnabled, user);
            });
        }
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE"})
    @Test
    public void testForegroundServiceTypeConnectedDevicePermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION"})
    @Test
    public void testForegroundServiceTypeMediaProjectionPermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_CAMERA"})
    @Test
    public void testForegroundServiceTypeCameraPermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_MICROPHONE"})
    @Test
    public void testForegroundServiceTypeMicrophonePermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_HEALTH"})
    @Test
    public void testForegroundServiceTypeHealthPermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING"})
    @Test
    public void testForegroundServiceTypeRemoteMessagingPermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED"})
    @Test
    public void testForegroundServiceTypeSystemExemptedPermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                new String[] {SPECIAL_PERMISSION_OP_ALLOWLISTED});
    }

    @Ignore("b/265347862")
    @Test
    public void testForegroundServiceTypeFileManagementPermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_FILE_MANAGEMENT);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING"})
    @Test
    public void testForegroundServiceTypeMediaProcessingPermission() throws Exception {
        assumeTrue(android.content.pm.Flags.introduceMediaProcessingType());
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_SPECIAL_USE"})
    @Test
    public void testForegroundServiceTypeSpecialUsePermission() throws Exception {
        testPermissionEnforcementCommon(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    @ApiTest(apis = {"android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_SPECIAL_USE"})
    @Test
    public void testForegroundServiceTypeSpecialUseProperty() throws Exception {
        final String expectedPropertyValue = "foo";
        try {
            final PackageManager.Property prop = mTargetContext.getPackageManager()
                    .getProperty(PackageManager.PROPERTY_SPECIAL_USE_FGS_SUBTYPE,
                            TEST_COMP_TARGET_FGS_NO_TYPE);
            fail("Property " + PackageManager.PROPERTY_SPECIAL_USE_FGS_SUBTYPE + " not expected.");
        } catch (PackageManager.NameNotFoundException e) {
            // expected.
        }
        final PackageManager.Property prop = mTargetContext.getPackageManager()
                .getProperty(PackageManager.PROPERTY_SPECIAL_USE_FGS_SUBTYPE,
                        TEST_COMP_TARGET_FGS_ALL_TYPE);
        assertEquals(expectedPropertyValue, prop.getString());
    }

    private void testForegroundServiceTypeDisabledCommon(int type,
            ComponentName api33Comp, ComponentName apiCurComp) throws Exception {
        testForegroundServiceTypeDisabledCommon(type, RESULT_INVALID_TYPE_EXCEPTION,
                api33Comp, apiCurComp);
    }

    private void testForegroundServiceTypeDisabledCommon(int type, int exceptionType,
            ComponentName api33Comp, ComponentName apiCurComp) throws Exception {
        final ApplicationInfo appCurInfo = mTargetContext.getPackageManager().getApplicationInfo(
                TEST_PKG_NAME_CURRENT, 0);
        final ApplicationInfo app33Info = mTargetContext.getPackageManager().getApplicationInfo(
                TEST_PKG_NAME_API33, 0);
        final WatchUidRunner uidCurWatcher = new WatchUidRunner(mInstrumentation, appCurInfo.uid,
                WAITFOR_MSEC);
        final WatchUidRunner uid33Watcher = new WatchUidRunner(mInstrumentation, app33Info.uid,
                WAITFOR_MSEC);

        final ForegroundServiceTypePolicy policy = ForegroundServiceTypePolicy.getDefaultPolicy();
        final ForegroundServiceTypePolicyInfo info = policy.getForegroundServiceTypePolicyInfo(
                type, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE);
        try {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                info.setTypeDisabledForTest(false, TEST_PKG_NAME_CURRENT);
                info.setTypeDisabledForTest(false, TEST_PKG_NAME_API33);
            });
            startAndStopFgsType(api33Comp, type, uid33Watcher);
            startAndStopFgsType(apiCurComp, type, uidCurWatcher);

            SystemUtil.runWithShellPermissionIdentity(() -> {
                info.setTypeDisabledForTest(true, TEST_PKG_NAME_CURRENT);
            });

            assertEquals(exceptionType, startForegroundServiceWithType(apiCurComp, type));

            stopService(apiCurComp, null);
            startAndStopFgsType(api33Comp, type, uid33Watcher);
        } finally {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                info.clearTypeDisabledForTest(TEST_PKG_NAME_CURRENT);
                info.clearTypeDisabledForTest(TEST_PKG_NAME_API33);
            });
        }
    }

    private void startAndStopFgsType(ComponentName compName, int type, WatchUidRunner uidWatcher)
            throws Exception {
        assertEquals(RESULT_OK, startForegroundServiceWithType(compName, type));
        if (uidWatcher != null) {
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE, null);
        }
        stopService(compName, uidWatcher);
    }

    private int startForegroundServiceWithType(ComponentName compName, int type) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final int[] result = new int[1];
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                result[0] = intent.getIntExtra(LocalForegroundServiceBase.EXTRA_RESULT_CODE,
                        RESULT_OK);
                latch.countDown();
            }
        };
        final Intent intent = new Intent();
        intent.setComponent(compName);
        intent.putExtra(LocalForegroundServiceBase.EXTRA_COMMAND,
                LocalForegroundServiceBase.COMMAND_START_FOREGROUND);
        intent.putExtra(LocalForegroundServiceBase.EXTRA_FGS_TYPE, type);
        final IntentFilter filter =
                new IntentFilter(LocalForegroundServiceBase.ACTION_START_FGS_RESULT);

        try {
            mTargetContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            mTargetContext.startForegroundService(intent);
            latch.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS);
            return result[0];
        } finally {
            mTargetContext.unregisterReceiver(receiver);
        }
    }

    private void stopService(ComponentName compName, WatchUidRunner uidWatcher) throws Exception {
        final Intent intent = new Intent();
        intent.setComponent(compName);
        intent.putExtra(LocalForegroundServiceBase.EXTRA_COMMAND,
                LocalForegroundServiceBase.COMMAND_STOP_SELF);

        mTargetContext.startService(intent);

        if (uidWatcher != null) {
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                    null);
        }
    }

    private void testPermissionEnforcementCommon(int type) throws Exception {
        testPermissionEnforcementCommon(type, null);
    }

    private void testPermissionEnforcementCommon(int type, String[] specialOps) throws Exception {
        final String testPackageName = TEST_PKG_NAME_TARGET;
        TestPermissionInfo[] allOfPermissions = null;
        TestPermissionInfo[] anyOfPermissions = null;
        final ForegroundServiceTypePolicy policy =
                ForegroundServiceTypePolicy.getDefaultPolicy();
        final ForegroundServiceTypePolicyInfo info = policy.getForegroundServiceTypePolicyInfo(
                type, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE);
        final String permFlag = info.getPermissionEnforcementFlagForTest();
        try (DeviceConfigStateHelper helper = new DeviceConfigStateHelper("activity_manager")) {
            // Enable the permission check.
            enablePermissionEnforcement(true, testPackageName);
            if (permFlag != null) {
                helper.set(permFlag, "true");
            }

            assertEquals(type, info.getForegroundServiceType());
            allOfPermissions = triagePermissions(
                    info.getRequiredAllOfPermissionsForTest(mTargetContext).orElse(null));
            anyOfPermissions = ArrayUtils.concat(TestPermissionInfo.class,
                    triagePermissions(info.getRequiredAnyOfPermissionsForTest(
                            mTargetContext).orElse(null)),
                    triagePermissions(specialOps));

            // If we grant all of the permissions, the foreground service start will succeed.
            grantPermissions(ArrayUtils.concat(TestPermissionInfo.class,
                    allOfPermissions, anyOfPermissions), testPackageName);

            startAndStopFgsType(TEST_COMP_TARGET_FGS_ALL_TYPE, type, null);

            resetPermissions(anyOfPermissions, testPackageName);

            // If we grant all of the "allOf" permission, but none of the "anyOf" permission, it
            // should fail to start a foreground service.
            if (!ArrayUtils.isEmpty(anyOfPermissions)) {
                grantPermissions(allOfPermissions, testPackageName);
                assertEquals(RESULT_SECURITY_EXCEPTION,
                        startForegroundServiceWithType(TEST_COMP_TARGET_FGS_ALL_TYPE, type));
                stopService(TEST_COMP_TARGET_FGS_ALL_TYPE, null);
                resetPermissions(anyOfPermissions, testPackageName);

                // If there is a feature flag to turn the permission check off, it should succeed.
                if (permFlag != null) {
                    helper.set(permFlag, "false");
                    grantPermissions(allOfPermissions, testPackageName);
                    startAndStopFgsType(TEST_COMP_TARGET_FGS_ALL_TYPE, type, null);
                    resetPermissions(anyOfPermissions, testPackageName);
                    helper.set(permFlag, "true");
                }

                // If we grant any of them, it should succeed.
                for (TestPermissionInfo perm: anyOfPermissions) {
                    grantPermissions(ArrayUtils.concat(TestPermissionInfo.class,
                            allOfPermissions, new TestPermissionInfo[] {perm}),
                            testPackageName);
                    startAndStopFgsType(TEST_COMP_TARGET_FGS_ALL_TYPE, type, null);
                    resetPermissions(anyOfPermissions, testPackageName);
                }
            }

            // If we skip one of the "allOf" permissions, it should fail.
            if (!ArrayUtils.isEmpty(allOfPermissions)) {
                for (int i = 0; i < allOfPermissions.length; i++) {
                    final TestPermissionInfo[] perms = getListExceptIndex(allOfPermissions, i);
                    grantPermissions(ArrayUtils.concat(TestPermissionInfo.class,
                                perms, anyOfPermissions), testPackageName);
                    assertEquals(RESULT_SECURITY_EXCEPTION,
                            startForegroundServiceWithType(TEST_COMP_TARGET_FGS_ALL_TYPE, type));
                    stopService(TEST_COMP_TARGET_FGS_ALL_TYPE, null);
                    resetPermissions(anyOfPermissions, testPackageName);
                }
            }
        } finally {
            resetPermissions(anyOfPermissions, testPackageName);
            enablePermissionEnforcement(false, testPackageName);
        }
    }

    private static int regularPermissionToAppOpIfPossible(TestPermissionInfo perm) {
        return !perm.mIsAppOps && perm.mSpecialOp == null
                ? AppOpsManager.permissionToOpCode(perm.mName)
                : AppOpsManager.OP_NONE;
    }

    private TestPermissionInfo[] getListExceptIndex(TestPermissionInfo[] list, int exceptIndex) {
        final ArrayList<TestPermissionInfo> ret = new ArrayList<>();
        for (int i = 0; i < list.length; i++) {
            if (i == exceptIndex) {
                continue;
            }
            ret.add(list[i]);
        }
        if (ret.size() > 0) {
            return ret.toArray(new TestPermissionInfo[ret.size()]);
        } else {
            return null;
        }
    }

    private void enablePermissionEnforcement(boolean enable, String packageName) throws Exception {
        if (enable) {
            executeShellCommand("am compat enable --no-kill FGS_TYPE_PERMISSION_CHANGE_ID "
                    + packageName);
        } else {
            executeShellCommand("am compat disable --no-kill FGS_TYPE_PERMISSION_CHANGE_ID "
                    + packageName);
        }
    }

    private void clearPermissionEnforcement(String packageName) throws Exception {
        executeShellCommand("am compat reset --no-kill FGS_TYPE_PERMISSION_CHANGE_ID "
                + packageName);
    }

    private String executeShellCommand(String cmd) throws Exception {
        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        return uiDevice.executeShellCommand(cmd).trim();
    }

    private class TestPermissionInfo {
        final String mName;
        final boolean mIsAppOps;
        final SpecialPermissionOp mSpecialOp;
        final boolean mIsRole;

        TestPermissionInfo(String name, boolean isAppOps, SpecialPermissionOp specialOp,
                boolean isRole) {
            mName = name;
            mIsAppOps = isAppOps;
            mSpecialOp = specialOp;
            mIsRole = isRole;
        }
    }

    private interface SpecialPermissionOp {
        void grantPermission(String packageName) throws Exception;
        void revokePermission(String packageName) throws Exception;
    }

    private class DeviceAllowlistPermissionOp implements SpecialPermissionOp {
        @Override
        public void grantPermission(String packageName) throws Exception {
            executeShellCommand("cmd deviceidle whitelist +" + packageName);
        }

        @Override
        public void revokePermission(String packageName) throws Exception {
            executeShellCommand("cmd deviceidle whitelist -" + packageName);
        }
    }

    private TestPermissionInfo[] triagePermissions(String[] permissions) {
        final ArrayList<TestPermissionInfo> perms = new ArrayList<>();
        if (permissions != null) {
            final RoleManager rm = mTargetContext.getSystemService(RoleManager.class);
            for (String perm : permissions) {
                PermissionInfo pi = null;
                try {
                    pi = mPackageManager.getPermissionInfo(perm, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    // It could be an appop.
                }
                if (pi != null) {
                    perms.add(new TestPermissionInfo(perm, false, null, false));
                } else if (sSpecialPermissionOps.containsKey(perm)) {
                    perms.add(new TestPermissionInfo(perm, false, sSpecialPermissionOps.get(perm),
                            true));
                } else if (rm.isRoleAvailable(perm)) {
                    perms.add(new TestPermissionInfo(perm, false, null, true));
                } else {
                    try {
                        AppOpsManager.strOpToOp(perm);
                        perms.add(new TestPermissionInfo(perm, true, null, false));
                    } catch (IllegalArgumentException e) {
                        // We don't support other type of permissions in CTS tests here.
                    }
                }
            }
        }
        return perms.toArray(new TestPermissionInfo[perms.size()]);
    }

    private void grantPermissions(TestPermissionInfo[] permissions, String packageName)
            throws Exception {
        if (ArrayUtils.isEmpty(permissions)) {
            return;
        }
        final String[] regularPermissions = Arrays.stream(permissions)
                .filter(p -> !p.mIsAppOps && p.mSpecialOp == null && !p.mIsRole)
                .map(p -> p.mName)
                .toArray(String[]::new);
        final String[] appops = ArrayUtils.concat(String.class, Arrays.stream(permissions)
                .filter(p -> p.mIsAppOps && p.mSpecialOp == null && !p.mIsRole)
                .map(p -> p.mName)
                .toArray(String[]::new),
                Arrays.stream(permissions)
                .filter(p -> regularPermissionToAppOpIfPossible(p) != AppOpsManager.OP_NONE)
                .map(p-> AppOpsManager.opToPublicName(regularPermissionToAppOpIfPossible(p)))
                .toArray(String[]::new));
        final SpecialPermissionOp[] specialOps = Arrays.stream(permissions)
                .filter(p-> p.mSpecialOp != null)
                .map(p -> p.mSpecialOp)
                .toArray(SpecialPermissionOp[]::new);
        final String[] roles = Arrays.stream(permissions)
                .filter(p-> p.mIsRole && p.mSpecialOp == null && !p.mIsAppOps)
                .map(p -> p.mName)
                .toArray(String[]::new);
        if (!ArrayUtils.isEmpty(regularPermissions)) {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(regularPermissions);
        }
        if (!ArrayUtils.isEmpty(appops)) {
            for (String appop : appops) {
                // Because we're adopting the shell identity, we have to set the appop to shell here
                executeShellCommand("appops set --user " + UserHandle.myUserId()
                        + " --uid " + SHELL_PKG_NAME + " " + appop + " allow");
            }
        }
        if (!ArrayUtils.isEmpty(specialOps)) {
            for (SpecialPermissionOp op : specialOps) {
                op.grantPermission(packageName);
            }
        }
        if (!ArrayUtils.isEmpty(roles)) {
            for (String role: roles) {
                executeShellCommand("cmd role add-role-holder --user " + UserHandle.myUserId()
                        + " " + role + " " + packageName);
            }
        }
    }

    private void resetPermissions(TestPermissionInfo[] permissions, String packageName)
            throws Exception {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        executeShellCommand("appops reset --user " + UserHandle.myUserId()
                + " " + SHELL_PKG_NAME);
        if (permissions != null) {
            final SpecialPermissionOp[] specialOps = Arrays.stream(permissions)
                    .filter(p-> p.mSpecialOp != null)
                    .map(p -> p.mSpecialOp)
                    .toArray(SpecialPermissionOp[]::new);
            final String[] roles = Arrays.stream(permissions)
                    .filter(p-> p.mIsRole && p.mSpecialOp == null && !p.mIsAppOps)
                    .map(p -> p.mName)
                    .toArray(String[]::new);
            if (!ArrayUtils.isEmpty(specialOps)) {
                for (SpecialPermissionOp op : specialOps) {
                    op.revokePermission(packageName);
                }
            }
            if (!ArrayUtils.isEmpty(roles)) {
                for (String role: roles) {
                    executeShellCommand("cmd role remove-role-holder --user "
                            + UserHandle.myUserId() + " " + role + " " + packageName);
                }
            }
        }
    }
}
