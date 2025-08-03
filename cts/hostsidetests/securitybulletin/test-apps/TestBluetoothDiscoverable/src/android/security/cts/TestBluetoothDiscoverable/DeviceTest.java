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

package android.security.cts.TestBluetoothDiscoverable;

import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.provider.SettingsSlicesContract.AUTHORITY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    private static Context sContext;
    private BluetoothAdapter mBtAdapter;
    private BroadcastReceiver mBroadcastReceiver;
    private Instrumentation mInstrumentation;
    private Resources mResources;
    private Semaphore mBroadcastReceived;
    private String mErrorMessage;
    private UiAutomation mUiAutomation;
    private UiDevice mDevice;
    private boolean mBtState;
    private int mStatusCode;

    @Before
    public void setUp() {
        try {
            mInstrumentation = getInstrumentation();
            sContext = mInstrumentation.getTargetContext();
            mBroadcastReceived = new Semaphore(0);
            mBtState = false;
            mResources = sContext.getResources();
            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
            mStatusCode = mResources.getInteger(R.integer.assumptionFailure);
            mErrorMessage = "";

            // Register BroadcastReceiver to receive status from PocActivity
            mBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        if (intent.getAction()
                                .equals(mResources.getString(R.string.broadcastAction))) {
                            mStatusCode =
                                    intent.getIntExtra(mResources.getString(R.string.resultKey),
                                            mResources.getInteger(R.integer.assumptionFailure));
                            mErrorMessage = intent
                                    .getStringExtra(mResources.getString(R.string.messageKey));
                            mBroadcastReceived.release();
                        }
                    } catch (Exception ignored) {
                        // Ignore exceptions here
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(sContext.getString(R.string.broadcastAction));
            sContext.registerReceiver(mBroadcastReceiver, filter);

            // Save the state of bluetooth adapter to reset after the test
            mBtState = mBtAdapter.isEnabled();

            // Disable bluetooth if already enabled in 'SCAN_MODE_CONNECTABLE_DISCOVERABLE' mode
            if (mBtAdapter.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                switchBluetoothMode(BluetoothAdapter.ACTION_REQUEST_DISABLE);
            }

            // Enable bluetooth if in disabled state
            switchBluetoothMode(BluetoothAdapter.ACTION_REQUEST_ENABLE);

            // 'MODIFY_PHONE_STATE' permission is required to launch target Settings app activity
            mUiAutomation = mInstrumentation.getUiAutomation();
            mUiAutomation
                    .adoptShellPermissionIdentity(android.Manifest.permission.MODIFY_PHONE_STATE);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    @After
    public void tearDown() {
        try {
            mUiAutomation.dropShellPermissionIdentity();
            // Disable bluetooth if it was OFF before the test
            if (!mBtState) {
                switchBluetoothMode(BluetoothAdapter.ACTION_REQUEST_DISABLE);
            }
            sContext.unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            // Ignore exceptions here
        }
    }

    @Test
    public void testConnectedDeviceDashboardFragment() {
        try {
            // Check if device is unlocked
            PowerManager powerManager = sContext.getSystemService(PowerManager.class);
            KeyguardManager keyguardManager = sContext.getSystemService(KeyguardManager.class);
            assumeTrue(sContext.getString(R.string.msgDeviceLocked),
                    powerManager.isInteractive() && !keyguardManager.isKeyguardLocked());

            // Check if bluetooth is enabled. The test requires bluetooth to be enabled
            assumeTrue(mBtAdapter.isEnabled());

            // Check if bluetooth mode is not set to SCAN_MODE_CONNECTABLE_DISCOVERABLE
            assumeTrue(mBtAdapter.getScanMode() != SCAN_MODE_CONNECTABLE_DISCOVERABLE);

            // Launch bluetooth settings which is supposed to set scan mode to
            // SCAN_MODE_CONNECTABLE_DISCOVERABLE if vulnerability is present
            String settingsPkg = getSettingsPkgName();
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse(
                    sContext.getString(R.string.sliceConnectedDevicesDashboardUri, settingsPkg)));
            intent.setClassName(settingsPkg,
                    sContext.getString(R.string.sliceDeepLinkSpringBoardClassName, settingsPkg));
            sContext.startActivity(intent);

            // Wait until target activity from settings package is launched
            mDevice = UiDevice.getInstance(mInstrumentation);
            assumeTrue(mDevice.wait(Until.hasObject(By.pkg(settingsPkg)),
                    mResources.getInteger(R.integer.timeoutMs)));

            // Test fails if bluetooth is made discoverable through PoC
            boolean isBtDiscoverable =
                    (mBtAdapter.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE);
            assertFalse(sContext.getString(R.string.msgFailConnectedDeviceDashboardFragment),
                    isBtDiscoverable);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    @Test
    public void testBluetoothDashboardFragment() {
        try {
            // Check if device is unlocked
            PowerManager powerManager = sContext.getSystemService(PowerManager.class);
            KeyguardManager keyguardManager = sContext.getSystemService(KeyguardManager.class);
            assumeTrue(sContext.getString(R.string.msgDeviceLocked),
                    powerManager.isInteractive() && !keyguardManager.isKeyguardLocked());

            // Check if bluetooth is enabled. The test requires bluetooth to be enabled
            assumeTrue(mBtAdapter.isEnabled());

            // Check if bluetooth mode is not set to SCAN_MODE_CONNECTABLE_DISCOVERABLE
            assumeTrue(mBtAdapter.getScanMode() != SCAN_MODE_CONNECTABLE_DISCOVERABLE);

            // Launch bluetooth settings which is supposed to set scan mode to
            // SCAN_MODE_CONNECTABLE_DISCOVERABLE if vulnerability is present
            String settingsPkg = getSettingsPkgName();
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse(sContext.getString(R.string.sliceBluetoothDashboardUri,
                    settingsPkg, AUTHORITY)));
            sContext.startActivity(intent);

            // Wait until target activity from settings package is launched
            mDevice = UiDevice.getInstance(mInstrumentation);
            assumeTrue(mDevice.wait(Until.hasObject(By.pkg(settingsPkg)),
                    mResources.getInteger(R.integer.timeoutMs)));

            // Test fails if bluetooth is made discoverable through PoC
            boolean isBtDiscoverable =
                    (mBtAdapter.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE);
            assertFalse(sContext.getString(R.string.msgFailBluetoothDashboardFragment),
                    isBtDiscoverable);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }


    public static String getSettingsPkgName() {
        // Retrieve settings package name dynamically
        Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
        ComponentName settingsComponent =
                settingsIntent.resolveActivity(sContext.getPackageManager());
        String pkgName = settingsComponent != null ? settingsComponent.getPackageName()
                : sContext.getString(R.string.defaultSettingsPkg);
        return pkgName;
    }

    private void switchBluetoothMode(String action) throws Exception {
        // Start PocActivity to switch bluetooth mode
        Intent intent = new Intent(sContext, PocActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(sContext.getString(R.string.btAction), action);
        sContext.startActivity(intent);

        // Wait until bluetooth mode switch is completed successfully
        assumeTrue(mBroadcastReceived.tryAcquire(mResources.getInteger(R.integer.timeoutMs),
                TimeUnit.MILLISECONDS));
        assumeTrue(mErrorMessage,
                mStatusCode != mResources.getInteger(R.integer.assumptionFailure));
    }
}
