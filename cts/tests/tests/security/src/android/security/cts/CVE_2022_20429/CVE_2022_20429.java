/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.cts.CVE_2022_20429;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevicePicker;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.security.cts.R;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20429 extends StsExtraBusinessLogicTestCase {
    private BroadcastReceiver mBroadcastReceiver;
    private Context mContext;
    private Semaphore mBroadcastReceived;
    private String mErrorMessage;
    private UiAutomation mUiAutomation;
    private boolean mBtState;
    private int mStatusCode;

    @AsbSecurityTest(cveBugId = 220741473)
    @Test
    public void testPocCVE_2022_20429() {
        try {
            Instrumentation instrumentation = getInstrumentation();
            mContext = instrumentation.getContext();
            PackageManager pm = mContext.getPackageManager();

            // Skip test for non-automotive builds
            assumeTrue(
                    mContext.getString(
                            R.string.cve_2022_20429_featureMissing,
                            PackageManager.FEATURE_AUTOMOTIVE),
                    pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

            // Skip test if bluetooth feature not available
            assumeTrue(
                    mContext.getString(
                            R.string.cve_2022_20429_featureMissing,
                            PackageManager.FEATURE_BLUETOOTH),
                    pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
            mBroadcastReceived = new Semaphore(0);
            mBtState = false;
            mErrorMessage = "";
            mStatusCode = getInteger(R.integer.cve_2022_20429_failure);
            mUiAutomation = instrumentation.getUiAutomation();
            mUiAutomation.adoptShellPermissionIdentity();

            // Register BroadcastReceiver to receive status from PocActivity
            mBroadcastReceiver =
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context mContext, Intent intent) {
                            try {
                                if (intent.getAction()
                                        .equals(
                                                mContext.getString(
                                                        R.string.cve_2022_20429_broadcastAction))) {
                                    mStatusCode =
                                            intent.getIntExtra(
                                                    mContext.getString(
                                                            R.string.cve_2022_20429_resultKey),
                                                    getInteger(R.integer.cve_2022_20429_failure));
                                    mErrorMessage =
                                            intent.getStringExtra(
                                                    mContext.getString(
                                                            R.string.cve_2022_20429_messageKey));
                                    mBroadcastReceived.release();
                                } else if (intent.getAction()
                                                .equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
                                        && intent.getIntExtra(
                                                        BluetoothAdapter.EXTRA_SCAN_MODE,
                                                        BluetoothAdapter.ERROR)
                                                == BluetoothAdapter
                                                        .SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                                    mBroadcastReceived.release();
                                }
                            } catch (Exception ignored) {
                                // Ignore exceptions here
                            }
                        }
                    };
            IntentFilter filter = new IntentFilter();
            filter.addAction(mContext.getString(R.string.cve_2022_20429_broadcastAction));
            filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
            mContext.registerReceiver(mBroadcastReceiver, filter);
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

            // Save the state of bluetooth adapter to reset after the test
            mBtState = btAdapter.isEnabled();

            // Disable bluetooth if already enabled in 'SCAN_MODE_CONNECTABLE_DISCOVERABLE' mode
            if (btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                switchBluetoothMode(BluetoothAdapter.ACTION_REQUEST_DISABLE);
            }

            // Enable bluetooth if in disabled state
            if (!btAdapter.isEnabled()) {
                switchBluetoothMode(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            }
            mContext.startActivity(
                    new Intent(BluetoothDevicePicker.ACTION_LAUNCH)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            mBroadcastReceived.tryAcquire(
                    getInteger(R.integer.cve_2022_20429_timeoutMs), TimeUnit.MILLISECONDS);

            // Test fails if Bluetooth Scan mode changes to SCAN_MODE_CONNECTABLE_DISCOVERABLE
            assertFalse(
                    mContext.getString(R.string.cve_2022_20429_failMsg),
                    btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // Disable bluetooth if it was OFF before the test
                if (!mBtState) {
                    switchBluetoothMode(BluetoothAdapter.ACTION_REQUEST_DISABLE);
                }
                mUiAutomation.dropShellPermissionIdentity();
            } catch (Exception e) {
                // Ignore exceptions here
            }
        }
    }

    int getInteger(int resId) {
        return mContext.getResources().getInteger(resId);
    }

    private void switchBluetoothMode(String action) throws Exception {
        // Start PocActivity to switch bluetooth mode
        Intent intent = new Intent(mContext, PocActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(mContext.getString(R.string.cve_2022_20429_btAction), action);
        mContext.startActivity(intent);

        // Wait until bluetooth mode switch is completed successfully
        assumeTrue(
                mBroadcastReceived.tryAcquire(
                        getInteger(R.integer.cve_2022_20429_timeoutMs), TimeUnit.MILLISECONDS));
        assumeTrue(mErrorMessage, mStatusCode != getInteger(R.integer.cve_2022_20429_failure));
    }
}
