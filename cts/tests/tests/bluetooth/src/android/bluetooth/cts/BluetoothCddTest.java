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
import static android.Manifest.permission.BLUETOOTH_SCAN;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothCddTest {
    private static final String TAG = BluetoothCddTest.class.getSimpleName();
    private static final int PROFILE_MCP_SERVER = 24;
    private static final int PROFILE_LE_CALL_CONTROL = 27;
    // Some devices need some extra time after entering STATE_OFF
    private static final int BLUETOOTH_TOGGLE_DELAY_MS = 2000;
    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mHasBluetooth = TestUtils.hasBluetooth();
        Assume.assumeTrue(mHasBluetooth);
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED,
                BLUETOOTH_SCAN);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();

        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();
    }

    @After
    public void tearDown() {
        if (!mHasBluetooth) {
            return;
        }
        if (mAdapter != null && mAdapter.getState() != BluetoothAdapter.STATE_OFF) {
            if (mAdapter.getState() == BluetoothAdapter.STATE_ON) {
                assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
            }
            try {
                Thread.sleep(BLUETOOTH_TOGGLE_DELAY_MS);
            } catch (InterruptedException ignored) { }
        }
        mAdapter = null;
        mContext = null;
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-3-1", "7.4.3/C-3-2"})
    @Test
    public void C_3_BleRequirements() {
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));
        assertThat(mAdapter.getSupportedProfiles()).contains(BluetoothProfile.GATT);
    }

    @CddTest(requirements = {"7.4.3/C-7-3", "7.4.3/C-7-5"})
    @Test
    public void C_7_LeAudioUnicastRequirements() {
        // Assert that BluetoothAdapter#isLeAudioSupported() and
        // BluetoothAdapter#getSupportedProfiles() return the same information
        if (mAdapter.isLeAudioSupported() != BluetoothStatusCodes.FEATURE_SUPPORTED) {
            assertThat(mAdapter.getSupportedProfiles()).doesNotContain(BluetoothProfile.LE_AUDIO);
            return;
        }
        assertThat(mAdapter.getSupportedProfiles()).containsAtLeast(
                BluetoothProfile.LE_AUDIO,
                BluetoothProfile.CSIP_SET_COORDINATOR,
                PROFILE_MCP_SERVER,
                BluetoothProfile.VOLUME_CONTROL,
                PROFILE_LE_CALL_CONTROL);
        assertThat(mAdapter.isLe2MPhySupported()).isTrue();
        assertThat(mAdapter.isLeExtendedAdvertisingSupported()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-8-2", "7.4.3/C-8-3"})
    @Test
    public void C_8_LeAudioBroadcastSourceRequirements() {
        // Assert that BluetoothAdapter#isLeAudioBroadcastSourceSupported() and
        // BluetoothAdapter#getSupportedProfiles() return the same information
        if (mAdapter.isLeAudioBroadcastSourceSupported()
                != BluetoothStatusCodes.FEATURE_SUPPORTED) {
            assertThat(mAdapter.getSupportedProfiles()).doesNotContain(
                    BluetoothProfile.LE_AUDIO_BROADCAST);
        } else {
            assertThat(mAdapter.getSupportedProfiles()).containsAtLeast(
                    BluetoothProfile.LE_AUDIO_BROADCAST,
                    BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
            assertThat(mAdapter.isLePeriodicAdvertisingSupported()).isTrue();
            // TODO: Enforce Periodic Advertising support
        }

    }

    @CddTest(requirements = {"7.4.3/C-9-2"})
    @Test
    public void C_9_LeAudioBroadcastAssistantRequirements() {
        // Assert that BluetoothAdapter#isLeAudioBroadcastAssistantSupported() and
        // BluetoothAdapter#getSupportedProfiles() return the same information
        if (mAdapter.isLeAudioBroadcastAssistantSupported()
                != BluetoothStatusCodes.FEATURE_SUPPORTED) {
            assertThat(mAdapter.getSupportedProfiles()).doesNotContain(
                    BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
        } else {
            assertThat(mAdapter.getSupportedProfiles()).contains(
                    BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
            assertThat(mAdapter.isLePeriodicAdvertisingSupported()).isTrue();
            // TODO: Enforce Periodic Advertising support
        }
    }
}
