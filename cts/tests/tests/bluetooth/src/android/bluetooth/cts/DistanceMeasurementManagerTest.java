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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ChannelSoundingParams;
import android.bluetooth.le.DistanceMeasurementManager;
import android.bluetooth.le.DistanceMeasurementMethod;
import android.bluetooth.le.DistanceMeasurementParams;
import android.bluetooth.le.DistanceMeasurementResult;
import android.bluetooth.le.DistanceMeasurementSession;
import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DistanceMeasurementManagerTest {
    private Context mContext;
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mDevice;
    private DistanceMeasurementManager mDistanceMeasurementManager;

    private DistanceMeasurementSession.Callback mTestcallback =
            new DistanceMeasurementSession.Callback() {
        public void onStarted(DistanceMeasurementSession session) {}

        public void onStartFail(int reason) {}

        public void onStopped(DistanceMeasurementSession session, int reason) {}

        public void onResult(BluetoothDevice device, DistanceMeasurementResult result) {}
    };

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        Assume.assumeTrue(mAdapter.isDistanceMeasurementSupported() == FEATURE_SUPPORTED);
        mDistanceMeasurementManager = mAdapter.getDistanceMeasurementManager();

        mDevice = mAdapter.getRemoteDevice("11:22:33:44:55:66");
    }

    @After
    public void tearDown() {
        TestUtils.dropPermissionAsShellUid();
        mAdapter = null;
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void startMeasurementSession() {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice)
                .setDurationSeconds(15)
                .setFrequency(DistanceMeasurementParams.REPORT_FREQUENCY_LOW)
                .build();
        CancellationSignal signal = mDistanceMeasurementManager.startMeasurementSession(params,
                mContext.getMainExecutor(), mTestcallback);
        assertNotNull(signal);
        signal.cancel();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getSupportedMethods() {
        List<DistanceMeasurementMethod> list = mDistanceMeasurementManager.getSupportedMethods();
        assertNotNull(list);
    }

    @RequiresFlagsEnabled(Flags.FLAG_CHANNEL_SOUNDING)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getChannelSoundingMaxSupportedSecurityLevel() {
        int securityLevel =
                mDistanceMeasurementManager.getChannelSoundingMaxSupportedSecurityLevel(mDevice);
        assertTrue(isValidSecurityLevel(securityLevel));
    }

    @RequiresFlagsEnabled(Flags.FLAG_CHANNEL_SOUNDING)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getLocalChannelSoundingMaxSupportedSecurityLevel() {
        int securityLevel =
                mDistanceMeasurementManager.getLocalChannelSoundingMaxSupportedSecurityLevel();
        assertTrue(isValidSecurityLevel(securityLevel));
    }

    private boolean isValidSecurityLevel(int securityLevel) {
        return (securityLevel >= ChannelSoundingParams.CS_SECURITY_LEVEL_UNKNOWN
                && securityLevel <= ChannelSoundingParams.CS_SECURITY_LEVEL_FOUR);
    }
}
