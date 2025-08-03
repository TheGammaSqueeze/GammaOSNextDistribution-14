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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothStatusCodes.ERROR_REMOTE_OPERATION_NOT_SUPPORTED;
import static android.bluetooth.BluetoothStatusCodes.ERROR_TIMEOUT;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.DistanceMeasurementResult;
import android.bluetooth.le.DistanceMeasurementSession;
import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DistanceMeasurementSessionTest {
    private Context mContext;
    private BluetoothAdapter mAdapter;

    private DistanceMeasurementSession.Callback mTestcallback =
            new DistanceMeasurementSession.Callback() {
        public void onStarted(DistanceMeasurementSession session) {}

        public void onStartFail(int reason) {}

        public void onStopped(DistanceMeasurementSession session, int reason) {}

        public void onResult(BluetoothDevice device, DistanceMeasurementResult result) {}
    };

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        Assume.assumeTrue(mAdapter.isDistanceMeasurementSupported() == FEATURE_SUPPORTED);
    }

    @After
    public void tearDown() {
        TestUtils.dropPermissionAsShellUid();
        mAdapter = null;
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void callbackMethods() {
        mTestcallback.onStarted(null);
        mTestcallback.onStartFail(ERROR_REMOTE_OPERATION_NOT_SUPPORTED);
        mTestcallback.onStopped(null, ERROR_TIMEOUT);
        mTestcallback.onResult(null, null);
    }
}
