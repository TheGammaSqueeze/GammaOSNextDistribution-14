/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.appdataisolation.appa;

import static com.android.cts.appdataisolation.common.FileUtils.APPA_PKG;
import static com.android.cts.appdataisolation.common.FileUtils.APPB_PKG;
import static com.android.cts.appdataisolation.common.FileUtils.CE_DATA_FILE_NAME;
import static com.android.cts.appdataisolation.common.FileUtils.DE_DATA_FILE_NAME;
import static com.android.cts.appdataisolation.common.FileUtils.EXTERNAL_DATA_FILE_NAME;
import static com.android.cts.appdataisolation.common.FileUtils.NOT_INSTALLED_PKG;
import static com.android.cts.appdataisolation.common.FileUtils.OBB_FILE_NAME;
import static com.android.cts.appdataisolation.common.FileUtils.assertDirDoesNotExist;
import static com.android.cts.appdataisolation.common.FileUtils.assertDirIsAccessible;
import static com.android.cts.appdataisolation.common.FileUtils.assertDirIsNotAccessible;
import static com.android.cts.appdataisolation.common.FileUtils.assertFileDoesNotExist;
import static com.android.cts.appdataisolation.common.FileUtils.assertFileExists;
import static com.android.cts.appdataisolation.common.FileUtils.deleteFile;
import static com.android.cts.appdataisolation.common.FileUtils.touchFile;
import static com.android.cts.appdataisolation.common.UserUtils.getCurrentUserId;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static android.view.Display.DEFAULT_DISPLAY;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.view.KeyEvent;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.PropertyUtil;
import com.android.cts.appdataisolation.common.FileUtils;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * This class is a helper class for AppDataIsolationTests to assert and check data stored in app.
 */
@SmallTest
public class AppATests {

    private static final long BIND_SERVICE_TIMEOUT_MS = 5000;
    private static final int MAX_VOLD_QUERY_RETRIES = 20;

    private Context mContext;
    private UiDevice mDevice;
    private String mCePath;
    private String mDePath;
    private String mExternalDataPath;
    private String mObbPath;

    // Sending the unlock keycode events to DEFAULT_DISPLAY
    private String testUnlockDeviceCommand_1 = "input -d "+ DEFAULT_DISPLAY +" keyevent "+KeyEvent.KEYCODE_1;
    private String testUnlockDeviceCommand_2 = "input -d "+ DEFAULT_DISPLAY +" keyevent "+KeyEvent.KEYCODE_2;
    private String testUnlockDeviceCommand_3 = "input -d "+ DEFAULT_DISPLAY +" keyevent "+KeyEvent.KEYCODE_3;
    private String testUnlockDeviceCommand_4 = "input -d "+ DEFAULT_DISPLAY +" keyevent "+KeyEvent.KEYCODE_4;
    private String testUnlockDeviceCommand_enter = "input -d "+ DEFAULT_DISPLAY +" keyevent "+KeyEvent.KEYCODE_ENTER;

    private volatile CountDownLatch mServiceConnectedLatch;
    private IIsolatedService mService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IIsolatedService.Stub.asInterface(service);
            mServiceConnectedLatch.countDown();
        }
    };

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mCePath = mContext.getApplicationInfo().dataDir;
        mDePath = mContext.getApplicationInfo().deviceProtectedDataDir;
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        setUpExternalStoragePaths();
    }

    private void setUpExternalStoragePaths() {
        File externalFilesDir = mContext.getExternalFilesDir("");
        if (externalFilesDir != null) {
            mExternalDataPath = externalFilesDir.getAbsolutePath();
        }
        File obbDir = mContext.getObbDir();
        if (obbDir != null) {
            mObbPath = obbDir.getAbsolutePath();
        }
    }

    @Test
    public void testCreateCeDeAppData() throws Exception {
        assertFileDoesNotExist(mCePath, CE_DATA_FILE_NAME);
        assertFileDoesNotExist(mCePath, DE_DATA_FILE_NAME);
        assertFileDoesNotExist(mDePath, CE_DATA_FILE_NAME);
        assertFileDoesNotExist(mDePath, DE_DATA_FILE_NAME);

        touchFile(mCePath, CE_DATA_FILE_NAME);
        touchFile(mDePath, DE_DATA_FILE_NAME);

        assertFileExists(mCePath, CE_DATA_FILE_NAME);
        assertFileDoesNotExist(mCePath, DE_DATA_FILE_NAME);
        assertFileDoesNotExist(mDePath, CE_DATA_FILE_NAME);
        assertFileExists(mDePath, DE_DATA_FILE_NAME);
    }

    @Test
    public void testCreateExternalDirs() throws Exception {
        assertFileDoesNotExist(mExternalDataPath, EXTERNAL_DATA_FILE_NAME);
        assertFileDoesNotExist(mObbPath, OBB_FILE_NAME);

        touchFile(mExternalDataPath, EXTERNAL_DATA_FILE_NAME);
        touchFile(mObbPath, OBB_FILE_NAME);

        assertFileExists(mExternalDataPath, EXTERNAL_DATA_FILE_NAME);
        assertFileExists(mObbPath, OBB_FILE_NAME);
    }

    @Test
    public void testDeleteExternalDirs() throws Exception {
        deleteFile(mExternalDataPath, EXTERNAL_DATA_FILE_NAME);
        deleteFile(mObbPath, OBB_FILE_NAME);

        assertFileDoesNotExist(mExternalDataPath, EXTERNAL_DATA_FILE_NAME);
        assertFileDoesNotExist(mObbPath, OBB_FILE_NAME);
    }

    @Test
    public void testAppACeDataExists() {
        assertFileExists(mCePath, CE_DATA_FILE_NAME);
    }

    @Test
    public void testAppACeDataDoesNotExist() {
        assertFileDoesNotExist(mCePath, CE_DATA_FILE_NAME);
    }

    @Test
    public void testAppADeDataExists() {
        assertFileExists(mDePath, DE_DATA_FILE_NAME);
    }

    @Test
    public void testAppADeDataDoesNotExist() {
        assertFileDoesNotExist(mDePath, DE_DATA_FILE_NAME);
    }

    @Test
    public void testAppAExternalDirsDoExist() {
        assertFileExists(mExternalDataPath, EXTERNAL_DATA_FILE_NAME);
        assertFileExists(mObbPath, OBB_FILE_NAME);
    }

    @Test
    public void testAppAExternalDirsDoNotExist() {
        assertFileDoesNotExist(mExternalDataPath, EXTERNAL_DATA_FILE_NAME);
        assertFileDoesNotExist(mObbPath, OBB_FILE_NAME);
    }

    @Test
    public void testAppAExternalDirsUnavailable() {
        assertNull(mContext.getExternalFilesDir(""));
        assertNull(mContext.getObbDir());
    }

    @Test
    public void testAppACurProfileDataAccessible() {
        assertDirIsAccessible("/data/misc/profiles/cur/"+ getCurrentUserId() + "/"
                + mContext.getPackageName());
    }

    @Test
    public void testAppARefProfileDataAccessible() {
        assertDirIsAccessible("/data/misc/profiles/ref/"
                + mContext.getPackageName());
    }

    @Test
    public void testCannotAccessAppBDataDir() throws Exception {
        ApplicationInfo applicationInfo = mContext.getPackageManager()
                .getApplicationInfo(APPB_PKG, 0);
        assertDirDoesNotExist(applicationInfo.dataDir);
        assertDirDoesNotExist(applicationInfo.deviceProtectedDataDir);
        assertDirDoesNotExist("/data/data/" + APPB_PKG);
        assertDirDoesNotExist("/data/misc/profiles/cur/" + getCurrentUserId() + "/" + APPB_PKG);
        assertDirDoesNotExist("/data/misc/profiles/ref/" + APPB_PKG);
    }

    @Test
    public void testUnlockDevice() throws Exception {
        mDevice.wakeUp();
        mDevice.waitForIdle();
        mDevice.pressMenu();
        mDevice.waitForIdle();
        mDevice.executeShellCommand(testUnlockDeviceCommand_1);
        mDevice.waitForIdle();
        mDevice.executeShellCommand(testUnlockDeviceCommand_2);
        mDevice.waitForIdle();
        mDevice.executeShellCommand(testUnlockDeviceCommand_3);
        mDevice.waitForIdle();
        mDevice.executeShellCommand(testUnlockDeviceCommand_4);
        mDevice.waitForIdle();
        mDevice.executeShellCommand(testUnlockDeviceCommand_enter);
        mDevice.waitForIdle();
        mDevice.pressHome();
        mDevice.waitForIdle();
    }

    private void testCanNotAccessAppBExternalDirs() {
        String appBExternalDir = FileUtils.replacePackageAWithPackageB(
                mContext.getExternalFilesDir("").getParentFile().getAbsolutePath());
        String appBObbDir = FileUtils.replacePackageAWithPackageB(
                mContext.getObbDir().getAbsolutePath());
        assertDirDoesNotExist(appBExternalDir);
        assertDirDoesNotExist(appBObbDir);
    }

    @Test
    public void testAppAUnlockDeviceAndVerifyCeDeExternalDataExist() throws Exception {

        final CountDownLatch unlocked = new CountDownLatch(1);
        final CountDownLatch bootCompleted = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case Intent.ACTION_USER_UNLOCKED:
                        unlocked.countDown();
                        break;
                    case Intent.ACTION_BOOT_COMPLETED:
                        bootCompleted.countDown();
                        break;
                }
            }
        };
        mContext.registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        mContext.registerReceiver(receiver, new IntentFilter(Intent.ACTION_BOOT_COMPLETED));

        testUnlockDevice();

        assertTrue("User not unlocked", unlocked.await(1, TimeUnit.MINUTES));
        assertTrue("No locked boot complete", bootCompleted.await(2, TimeUnit.MINUTES));

        // TODO(b/302586971): Remove this when the vold resets are resolved.
        // vold can reset on an HSUM configuration during the user unlock flow; wait until valid
        // values are returned for the external and obb directories before continuing with the test.
        int numRetries = 0;
        do {
            numRetries++;
            SystemClock.sleep(3000);
            setUpExternalStoragePaths();
        } while ((mExternalDataPath == null || mObbPath == null)
                && numRetries <= MAX_VOLD_QUERY_RETRIES);

        // The test app process should be still running, make sure CE DE now is available
        testAppACeDataExists();
        testAppADeDataExists();
        testAppAExternalDirsDoExist();
        testAppACurProfileDataAccessible();
        testAppARefProfileDataAccessible();

        // Verify after unlocking device, app a has still no access to app b dir.
        testCannotAccessAppBDataDir();
        testCanNotAccessAppBExternalDirs();
    }

    @Test
    public void testIsolatedProcess() throws Exception {
        mServiceConnectedLatch = new CountDownLatch(1);
        Intent serviceIntent = new Intent(mContext, IsolatedService.class);
        try {
            mContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
            assertTrue("Timed out while waiting to bind to isolated service",
                    mServiceConnectedLatch.await(BIND_SERVICE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            mService.assertDataIsolated();
        } finally {
            mContext.unbindService(mServiceConnection);
        }
    }

    @Test
    public void testAppZygoteIsolatedProcess() throws Exception {
        mServiceConnectedLatch = new CountDownLatch(1);
        Intent serviceIntent = new Intent(mContext, AppZygoteIsolatedService.class);
        try {
            mContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
            assertTrue("Timed out while waiting to bind to isolated service",
                    mServiceConnectedLatch.await(BIND_SERVICE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            mService.assertDataIsolated();
        } finally {
            mContext.unbindService(mServiceConnection);
        }
    }

    @Test
    public void testOtherUserDirsNotPresent() throws Exception {
        final Bundle arguments = InstrumentationRegistry.getArguments();
        final int otherUserId = Integer.parseInt(arguments.getString("other_user_id"));

        final String ceDataRoot = "/data/user/" + otherUserId;
        final String deDataRoot = "/data/user_de/" + otherUserId;
        final String profileRoot = "/data/misc/profiles/cur/" + otherUserId;

        assertDirDoesNotExist(ceDataRoot);
        assertDirDoesNotExist(deDataRoot);
        assertDirDoesNotExist(profileRoot);
    }

    @Test
    public void testOtherUserDirsNotAccessible() throws Exception {
        final Bundle arguments = InstrumentationRegistry.getArguments();
        final int otherUserId = Integer.parseInt(arguments.getString("other_user_id"));

        final String ceDataRoot = "/data/user/" + otherUserId;
        final String deDataRoot = "/data/user_de/" + otherUserId;
        final String profileRoot = "/data/misc/profiles/cur/" + otherUserId;

        // APPA (this app) is installed in this user but not the other one.
        // APPB is installed in this user and the other one.
        // NOT_INSTALLED_PKG isn't installed anywhere.
        // We must get the same answer for all of them, so we can't infer if any of them are or
        // are not installed in the other user.
        assertDirIsNotAccessible(ceDataRoot);
        assertDirIsNotAccessible(ceDataRoot + "/" + APPA_PKG);
        assertDirIsNotAccessible(ceDataRoot + "/" + APPB_PKG);
        assertDirIsNotAccessible(ceDataRoot + "/" + NOT_INSTALLED_PKG);

        assertDirIsNotAccessible(deDataRoot);
        assertDirIsNotAccessible(deDataRoot + "/" + APPA_PKG);
        assertDirIsNotAccessible(deDataRoot + "/" + APPB_PKG);
        assertDirIsNotAccessible(deDataRoot + "/" + NOT_INSTALLED_PKG);

        // If the vendor policy is pre-R then backward compatibility rules apply.
        if (isVendorPolicyNewerThanR()) {
            assertDirIsNotAccessible(profileRoot);
            assertDirIsNotAccessible(profileRoot + "/" + APPA_PKG);
            assertDirIsNotAccessible(profileRoot + "/" + APPB_PKG);
            assertDirIsNotAccessible(profileRoot + "/" + NOT_INSTALLED_PKG);
        }
    }

    private boolean isVendorPolicyNewerThanR() {
        if (SystemProperties.get("ro.vndk.version").equals("S")) {
            // Vendor build is S, but before the API level bump - good enough for us.
            return true;
        }
        return PropertyUtil.isVendorApiLevelNewerThan(Build.VERSION_CODES.R);
    }
}
