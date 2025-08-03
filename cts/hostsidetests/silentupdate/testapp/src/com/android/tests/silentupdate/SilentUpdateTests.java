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

package com.android.tests.silentupdate;

import static android.Manifest.permission.INSTALL_DPC_PACKAGES;
import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.content.Context.MODE_PRIVATE;
import static android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED;
import static android.content.pm.PackageInstaller.SessionParams.USER_ACTION_REQUIRED;
import static android.content.pm.PackageInstaller.SessionParams.USER_ACTION_UNSPECIFIED;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.junit.Assert.fail;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.ArraySet;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Tests for multi-package (a.k.a. atomic) installs.
 */
@RunWith(JUnit4.class)
public class SilentUpdateTests {
    private static final String CURRENT_APK = "SilentInstallCurrent.apk";
    private static final String R_APK = "SilentInstallR.apk";
    private static final String S_APK = "SilentInstallS.apk";
    private static final String INSTALLER_PACKAGE_NAME = "com.android.tests.silentupdate";
    static final long SILENT_UPDATE_THROTTLE_TIME_SECOND = 10;

    private static final TestAppProvider sTestAppProvider = new TestAppProvider();
    private static final TestApp sDpcApp = sTestAppProvider.query()
                    .whereIsDeviceAdmin().isTrue()
                    .whereTestOnly().isFalse()
                    .get();

    private final ArraySet<Integer> mPendingAbandonSessionIds = new ArraySet<>();

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    @After
    public void tearDown() {
        if (mPendingAbandonSessionIds.size() > 0) {
            for (int i = 0; i < mPendingAbandonSessionIds.size(); i++) {
                int sessionId = mPendingAbandonSessionIds.valueAt(i);
                getContext().getPackageManager().getPackageInstaller().abandonSession(sessionId);
            }
            mPendingAbandonSessionIds.clear();
        }
        sDpcApp.uninstall();
        resetSilentUpdatesPolicy();
    }

    @Test
    public void newInstall_RequiresUserAction() throws Exception {
        Assert.assertEquals("New install should require user action",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                silentInstallResource(CURRENT_APK));
    }

    @Test
    public void updateWithUnknownSourcesDisabled_RequiresUserAction() throws Exception {
        Assert.assertEquals("Update with unknown sources disabled should require user action",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                silentInstallResource(CURRENT_APK));
    }

    @Test
    public void updateAsNonInstallerOfRecord_RequiresUserAction() throws Exception {
        Assert.assertEquals("Update when not installer of record should require user action",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                silentInstallResource(CURRENT_APK));
    }

    @Test
    public void updatedInstall_RequiresNoUserAction() throws Exception {
        Assert.assertEquals("Nominal silent update should not require user action",
                PackageInstaller.STATUS_SUCCESS,
                silentInstallResource(CURRENT_APK));
    }

    @Test
    public void updatedInstallWithoutCallSetUserAction_RequiresUserAction() throws Exception {
        Assert.assertEquals("Update should require action when setRequireUserAction not called",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                installResource(CURRENT_APK, null /*setRequireUserAction*/));
    }

    @Test
    public void updatedInstallForceUserAction_RequiresUserAction() throws Exception {
        Assert.assertEquals("Update should require action when setRequireUserAction true",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                installResource(CURRENT_APK, true /*setRequireUserAction*/));
    }

    @Test
    public void updatePreSApp_RequiresUserAction() throws Exception {
        Assert.assertEquals("Updating to a pre-S app should require user action",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                silentInstallResource(R_APK));
    }

    @Test
    public void updateSApp_RequiresNoUserAction() throws Exception {
        Assert.assertEquals("Updating to an S app should not require user action",
                PackageInstaller.STATUS_SUCCESS,
                silentInstallResource(S_APK));
    }

    @Test
    public void selfUpdate_RequiresNoUserAction() throws Exception {
        // persist the last update time to disk
        final long lastUpdateTime = getLastUpdateTime();
        getContext().getSharedPreferences("default", MODE_PRIVATE)
                .edit()
                .putLong("lastUpdateTime", lastUpdateTime)
                .commit();
        InstallStatusListener isl = commit(fileSupplier(
                "/data/local/tmp/silentupdatetest/CtsSilentUpdateTestCases_mdpi-v4.apk"),
                false /* requireUserAction */, INSTALLER_PACKAGE_NAME);
        final Intent statusUpdate = isl.getResult();
        final int result =
                statusUpdate.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
        Assert.assertEquals(PackageInstaller.STATUS_SUCCESS, result);
    }

    @Test
    public void selfUpdate_updateApplied() throws Exception {
        // verify persisted last update time is no longer current
        Assert.assertTrue("lastUpdateTime should be increased if install correctly applied",
                getLastUpdateTime() > getContext().getSharedPreferences("default", MODE_PRIVATE)
                .getLong("lastUpdateTime", 0));

    }

    @Test
    public void setRequireUserAction_throwsOnIllegalArgument() {
        SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
        params.setRequireUserAction(USER_ACTION_UNSPECIFIED);
        params.setRequireUserAction(USER_ACTION_REQUIRED);
        params.setRequireUserAction(USER_ACTION_NOT_REQUIRED);
        try {
            params.setRequireUserAction(-1);
            fail("Should not be able to setRequireUserAction to -1");
        } catch (IllegalArgumentException e) {
            // pass!
        }
        try {
            params.setRequireUserAction(3);
            fail("Should not be able to setRequireUserAction to 3");
        } catch (IllegalArgumentException e) {
            // pass!
        }
    }

    @Test
    public void silentInstallRepeatedly_RequiresUserAction() throws Exception {
        Assert.assertEquals("The first silent update should succeed",
                PackageInstaller.STATUS_SUCCESS,
                silentInstallResource(CURRENT_APK));
        Assert.assertEquals("The repeated silent install invoked within the throttle time "
                        + "should require user action",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                silentInstallResource(CURRENT_APK));
    }

    @Test
    public void silentInstallRepeatedly_withUnlimitedSilentUpdates_succeed() throws Exception {
        setUnlimitedSilentUpdates(INSTALLER_PACKAGE_NAME);
        Assert.assertEquals("The first silent update should succeed",
                PackageInstaller.STATUS_SUCCESS,
                silentInstallResource(CURRENT_APK));
        Assert.assertEquals("The repeated silent update should succeed",
                PackageInstaller.STATUS_SUCCESS,
                silentInstallResource(CURRENT_APK));
    }

    @Test
    public void silentInstallRepeatedly_waitForThrottleTime_succeed() throws Exception {
        setSilentUpdatesThrottleTime(SILENT_UPDATE_THROTTLE_TIME_SECOND);
        Assert.assertEquals("The first silent update should succeed",
                PackageInstaller.STATUS_SUCCESS,
                silentInstallResource(CURRENT_APK));
        SystemClock.sleep(TimeUnit.SECONDS.toMillis(SILENT_UPDATE_THROTTLE_TIME_SECOND));
        Assert.assertEquals("The repeated silent update should succeed",
                PackageInstaller.STATUS_SUCCESS,
                silentInstallResource(CURRENT_APK));
    }

    @Test
    public void newInstall_withInstallDpcPermission_requiresUserAction() throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(
                INSTALL_DPC_PACKAGES)) {
            Assert.assertEquals("Installing a non DPC package with INSTALL_DPC_PACKAGES "
                    + "permission should require user action.",
                    PackageInstaller.STATUS_PENDING_USER_ACTION,
                    silentInstallResource(CURRENT_APK));
        }
    }

    @Test
    public void newInstallDpc_withInstallDpcPermission_requiresNoUserAction() throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(
                INSTALL_DPC_PACKAGES)) {
            Assert.assertEquals("Installing a DPC package with INSTALL_DPC_PACKAGES "
                            + "permission should not require user action.",
                    PackageInstaller.STATUS_SUCCESS,
                    install(sDpcApp::apkStream, /* requireUserAction= */ false));
        }
    }

    @Test
    public void newInstallDpc_withoutInstallDpcPermission_requiresUserAction() throws Exception {
        try (PermissionContext p = TestApis.permissions().withoutPermission(
                INSTALL_DPC_PACKAGES)) {
            Assert.assertEquals("Installing a DPC package without INSTALL_DPC_PACKAGES "
                            + "permission should require user action.",
                    PackageInstaller.STATUS_PENDING_USER_ACTION,
                    install(sDpcApp::apkStream, /* requireUserAction= */ false));
        }
    }

    private int silentInstallResource(String resourceName) throws Exception {
        return install(resourceSupplier(resourceName), false);
    }

    private int installResource(String resourceName, Boolean requireUserAction) throws Exception {
        return install(resourceSupplier(resourceName), requireUserAction);
    }

    private int install(Supplier<InputStream> apkStreamSupplier, Boolean requireUserAction)
            throws Exception {
        InstallStatusListener isl = commit(apkStreamSupplier, requireUserAction,
                null /* dontKillPackageName */);
        final Intent statusUpdate = isl.getResult();
        final int result =
                statusUpdate.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
        if (result != PackageInstaller.STATUS_SUCCESS) {
            final int sessionId =
                    statusUpdate.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, Integer.MIN_VALUE);
            mPendingAbandonSessionIds.add(sessionId);
        }
        return result;
    }

    private InstallStatusListener commit(Supplier<InputStream> apkStreamSupplier,
            Boolean requireUserAction, String dontKillPackageName) throws IOException {
        final Context context = getContext();
        final PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        SessionParams params = new SessionParams(
                dontKillPackageName == null ? SessionParams.MODE_FULL_INSTALL
                        : SessionParams.MODE_INHERIT_EXISTING);
        if (requireUserAction != null) {
            params.setRequireUserAction(requireUserAction
                    ? USER_ACTION_REQUIRED
                    : USER_ACTION_NOT_REQUIRED);
        }
        if (dontKillPackageName != null) {
            params.setAppPackageName(dontKillPackageName);
            params.setDontKillApp(true);
        }
        int sessionId = installer.createSession(params);
        Assert.assertEquals("SessionInfo.getRequireUserAction and "
                        + "SessionParams.setRequireUserAction are not equal",
                installer.getSessionInfo(sessionId).getRequireUserAction(),
                requireUserAction == null
                        ? USER_ACTION_UNSPECIFIED
                        : requireUserAction == Boolean.TRUE
                                ? USER_ACTION_REQUIRED
                                : SessionParams.USER_ACTION_NOT_REQUIRED);
        final PackageInstaller.Session session = installer.openSession(sessionId);
        try(OutputStream os = session.openWrite("apk", 0, -1)) {
            try (InputStream is = apkStreamSupplier.get()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = is.read(buffer)) >= 0) {
                    os.write(buffer, 0, n);
                }
            }
        }
        InstallStatusListener isl = new InstallStatusListener();
        session.commit(isl.getIntentSender());
        return isl;
    }

    private Supplier<InputStream> resourceSupplier(String resourceName) {
        return () -> {
            final InputStream resourceAsStream =
                    getClass().getClassLoader().getResourceAsStream(resourceName);
            if (resourceAsStream == null) {
                throw new RuntimeException("Resource " + resourceName + " could not be found.");
            }
            return resourceAsStream;
        };
    }

    private Supplier<InputStream> fileSupplier(String fileName) {
        return () -> {
            try {
                return new FileInputStream(fileName);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private long getLastUpdateTime() throws PackageManager.NameNotFoundException {
        final PackageInfo packageInfo = getContext().getPackageManager().getPackageInfo(
                getContext().getPackageName(), 0);
        final long lastUpdateTime = packageInfo.lastUpdateTime;
        return lastUpdateTime;
    }

    private void setUnlimitedSilentUpdates(String installerPackageName) {
        final StringBuilder cmd = new StringBuilder(
                "pm set-silent-updates-policy --allow-unlimited-silent-updates ");
        cmd.append(installerPackageName);
        runShellCommand(cmd.toString());
    }

    private void setSilentUpdatesThrottleTime(long throttleTimeInSeconds) {
        final StringBuilder cmd = new StringBuilder(
                "pm set-silent-updates-policy --throttle-time ");
        cmd.append(throttleTimeInSeconds);
        runShellCommand(cmd.toString());
    }

    private void resetSilentUpdatesPolicy() {
        final StringBuilder cmd = new StringBuilder("pm set-silent-updates-policy --reset");
        runShellCommand(cmd.toString());
    }

    public static class InstallStatusListener extends BroadcastReceiver {

        private final BlockingQueue<Intent> mResults = new LinkedBlockingQueue<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            mResults.add(intent);
        }

        public IntentSender getIntentSender() {
            final Context context = getContext();
            final String action = UUID.randomUUID().toString();
            context.registerReceiver(this, new IntentFilter(action),
                    Context.RECEIVER_EXPORTED_UNAUDITED);
            Intent intent = new Intent(action).setPackage(context.getPackageName());
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent, FLAG_MUTABLE);
            return pending.getIntentSender();
        }

        public Intent getResult() throws InterruptedException {
            return mResults.take();
        }
    }
}
