/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;

import com.android.cts.verifier.ManifestTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

public class BleSecureClientTestListActivity extends PassFailButtons.TestListActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_secure_client_test_list_name, R.string.ble_secure_client_test_list_info, -1);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        List<String> disabledTest = new ArrayList<String>();

        // Temporarily disable this test (b/235763737).
        disabledTest.add(
                "com.android.cts.verifier.bluetooth.BleSecureConnectionPriorityClientTestActivity");
        disabledTest.add(
                "com.android.cts.verifier.bluetooth.BleSecureEncryptedClientTestActivity");

        if (adapter == null || !adapter.isOffloadedFilteringSupported()) {
            disabledTest.add(
                    "com.android.cts.verifier.bluetooth.BleAdvertiserHardwareScanFilterActivity.");
        }

        // RPA is optional on TVs and Cars already released before Android 11
        boolean isTv = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        boolean isCar = getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        int firstSdk = SystemProperties.getInt("ro.product.first_api_level", 0);
        if ((isTv || isCar) && (firstSdk <= Build.VERSION_CODES.Q)) {
            disabledTest.add(
                    "com.android.cts.verifier.bluetooth.BleSecureConnectionPriorityClientTestActivity");
            disabledTest.add(
                    "com.android.cts.verifier.bluetooth.BleSecureEncryptedClientTestActivity");
        }

        setTestListAdapter(new ManifestTestListAdapter(this, getClass().getName(),
                disabledTest.toArray(new String[disabledTest.size()])));
    }
}
