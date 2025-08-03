/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.packageinstaller.admin.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class tests {@link PackageInstaller#ACTION_SESSION_COMMITTED} is properly sent to the
 * launcher app.
 */
@RunWith(BedsteadJUnit4.class)
public class SessionCommitBroadcastTest extends BasePackageInstallTest {

    private static final long BROADCAST_TIMEOUT_SECS = 20;
    private ComponentName mDefaultLauncher;
    private ComponentName mThisAppLauncher;
    private SessionCommitReceiver mReceiver;

    @Before
    public void setUpTest() throws Exception {
        mDefaultLauncher = ComponentName.unflattenFromString(getDefaultLauncher());
        mThisAppLauncher = new ComponentName(mContext, LauncherActivity.class);
        mReceiver = new SessionCommitReceiver();
    }

    @After
    public void tearDownTest() throws Exception {
        mContext.unregisterReceiver(mReceiver);
        Uninstall.packages(TestApp.A);
    }

    @Test
    public void testBroadcastNotReceivedForDifferentLauncher() throws Exception {
        if (mDefaultLauncher.equals(mThisAppLauncher)) {
            // Find a different launcher
            Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addCategory(Intent.CATEGORY_DEFAULT);
            for (ResolveInfo info : mPackageManager.queryIntentActivities(homeIntent, 0)) {
                mDefaultLauncher =
                        new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                if (!mDefaultLauncher.equals(mThisAppLauncher)) {
                    setLauncher(mDefaultLauncher.flattenToString());
                    break;
                }
            }
        }

        assertFalse("No default launcher found", mDefaultLauncher.equals(mThisAppLauncher));
        // install the app
        assertInstallPackage();
        // Broadcast not received
        assertNull(mReceiver.blockingGetIntent());

        tryUninstallPackage();
    }

    private void verifySessionIntent(Intent intent) {
        assertNotNull(intent);
        PackageInstaller.SessionInfo info = intent
                .getParcelableExtra(PackageInstaller.EXTRA_SESSION);
        assertEquals(TEST_APP_PKG, info.getAppPackageName());
    }

    @Test
    public void testBroadcastNotReceivedForUpdateInstall() throws Exception {
        try {
            setLauncher(mThisAppLauncher.flattenToString());

            int sessionId = Install.single(TestApp.A1).commit();
            assertEquals(1, InstallUtils.getInstalledVersion(TestApp.A));
            // Check the broadcast is received for a new install and session id matches
            Intent intent = mReceiver.blockingGetIntent();
            PackageInstaller.SessionInfo info =
                    intent.getParcelableExtra(PackageInstaller.EXTRA_SESSION);
            assertEquals(sessionId, info.getSessionId());

            mContext.unregisterReceiver(mReceiver);
            mReceiver = new SessionCommitReceiver();
            Install.single(TestApp.A2).commit();
            assertEquals(2, InstallUtils.getInstalledVersion(TestApp.A));

            // Check no broadcast is received for an update install
            intent = mReceiver.blockingGetIntent();
            assertNull(intent);
        } finally {
            // Revert to default launcher
            setLauncher(mDefaultLauncher.flattenToString());
        }
    }

    @Test
    public void testBroadcastReceivedForNewInstall() throws Exception {
        setLauncher(mThisAppLauncher.flattenToString());

        // install the app
        assertInstallPackage();

        verifySessionIntent(mReceiver.blockingGetIntent());
        mContext.unregisterReceiver(mReceiver);
        forceUninstall();
        mReceiver = new SessionCommitReceiver();
        assertInstallPackage();
        verifySessionIntent(mReceiver.blockingGetIntent());

        tryUninstallPackage();
        // Revert to default launcher
        setLauncher(mDefaultLauncher.flattenToString());
    }

    @Test
    public void testBroadcastReceivedForEnablingApp() throws Exception {
        assumeTrue("Cannot add multiple users", UserManager.supportsMultipleUsers());

        setLauncher(mThisAppLauncher.flattenToString());

        ComponentName cn = new ComponentName(mContext, BasicAdminReceiver.class);
        UserHandle user = mDevicePolicyManager.createAndManageUser(cn,
                "Test User " + System.currentTimeMillis(), cn,
                null, DevicePolicyManager.SKIP_SETUP_WIZARD);
        int userId = user.getIdentifier();
        assertTrue(TextUtils.join(" ", runShellCommand("am start-user " + userId))
                .toLowerCase().contains("success"));

        // Install app for the second user
        assertTrue(TextUtils.join(" ", runShellCommand(
                "pm install -r --user " + userId + "  " + TEST_APP_LOCATION))
                .toLowerCase().contains("success"));

        // Enable the app for this user
        runShellCommand("cmd package install-existing --user " +
                Process.myUserHandle().getIdentifier() + "  " + TEST_APP_PKG);
        verifySessionIntent(mReceiver.blockingGetIntent());

        // Cleanup
        setLauncher(mDefaultLauncher.flattenToString());
        mDevicePolicyManager.removeUser(cn, user);
        forceUninstall();
    }

    private String getDefaultLauncher() throws Exception {
        final String PREFIX = "Launcher: ComponentInfo{";
        final String POSTFIX = "}";
        for (String s : runShellCommand("cmd shortcut get-default-launcher")) {
            if (s.startsWith(PREFIX) && s.endsWith(POSTFIX)) {
                return s.substring(PREFIX.length(), s.length() - POSTFIX.length());
            }
        }
        throw new Exception("Default launcher not found");
    }

    private void setLauncher(String component) throws Exception {
        runShellCommand("cmd package set-home-activity --user "
                + mInstrumentation.getContext().getUserId() + " " + component);
    }

    private class SessionCommitReceiver extends BroadcastReceiver {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private Intent mIntent;

        SessionCommitReceiver() {
            mContext.registerReceiver(this,
                    new IntentFilter(PackageInstaller.ACTION_SESSION_COMMITTED),
                    Context.RECEIVER_EXPORTED);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            assertNull(mIntent);
            mIntent = intent;
            mLatch.countDown();
        }

        public Intent blockingGetIntent() throws Exception {
            mLatch.await(BROADCAST_TIMEOUT_SECS, TimeUnit.SECONDS);
            return mIntent;
        }
    }
}
