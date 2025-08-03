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

package android.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import pandora.HostProto.DiscoverabilityMode;
import pandora.HostProto.SetDiscoverabilityModeRequest;

import java.util.ArrayList;

/** Test cases for {@link DeviceDiscoveryManager}. */
@RunWith(AndroidJUnit4.class)
public class DeviceDiscoveryTest {
    private static final String TAG = "DeviceDiscoveryTest";

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final BluetoothManager mManager = mContext.getSystemService(BluetoothManager.class);
    private final BluetoothAdapter mAdapter = mManager.getAdapter();

    private SettableFuture<String> mFutureDiscoveryStartedIntent;
    private SettableFuture<String> mFutureDiscoveryFinishedIntent;

    @Rule public final AdoptShellPermissionsRule mPermissionRule = new AdoptShellPermissionsRule();

    @Rule public final PandoraDevice mBumble = new PandoraDevice();

    private ArrayList<Intent> mDeviceFoundData;

    private BroadcastReceiver mConnectionStateReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(intent.getAction())) {
                        mFutureDiscoveryStartedIntent.set(
                                BluetoothAdapter.ACTION_DISCOVERY_STARTED);
                    } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(
                            intent.getAction())) {
                        mFutureDiscoveryFinishedIntent.set(
                                BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                    } else if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                        mDeviceFoundData.add(intent);
                    }
                }
            };

    @Test
    public void startDeviceDiscoveryTest() throws Exception {
        mFutureDiscoveryStartedIntent = SettableFuture.create();
        mFutureDiscoveryFinishedIntent = SettableFuture.create();

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        mContext.registerReceiver(mConnectionStateReceiver, filter);

        assertThat(mAdapter.startDiscovery()).isTrue();
        assertThat(mFutureDiscoveryStartedIntent.get())
                .isEqualTo(BluetoothAdapter.ACTION_DISCOVERY_STARTED);

        // Wait for device discovery to complete
        assertThat(mFutureDiscoveryFinishedIntent.get())
                .isEqualTo(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        mContext.unregisterReceiver(mConnectionStateReceiver);
    }

    @Test
    public void cancelDeviceDiscoveryTest() throws Exception {
        mFutureDiscoveryStartedIntent = SettableFuture.create();
        mFutureDiscoveryFinishedIntent = SettableFuture.create();

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        mContext.registerReceiver(mConnectionStateReceiver, filter);

        assertThat(mAdapter.startDiscovery()).isTrue();
        assertThat(mFutureDiscoveryStartedIntent.get())
                .isEqualTo(BluetoothAdapter.ACTION_DISCOVERY_STARTED);

        // Issue a cancel discovery and wait for device discovery finished
        assertThat(mAdapter.cancelDiscovery()).isTrue();
        assertThat(mFutureDiscoveryFinishedIntent.get())
                .isEqualTo(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        mContext.unregisterReceiver(mConnectionStateReceiver);
    }

    @Test
    public void checkDeviceIsDiscoveredTest() throws Exception {
        mFutureDiscoveryStartedIntent = SettableFuture.create();
        mFutureDiscoveryFinishedIntent = SettableFuture.create();
        mDeviceFoundData = new ArrayList<Intent>();

        // Ensure remote device is discoverable
        mBumble.hostBlocking()
                .setDiscoverabilityMode(
                        SetDiscoverabilityModeRequest.newBuilder()
                                .setMode(DiscoverabilityMode.DISCOVERABLE_GENERAL)
                                .build());

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        mContext.registerReceiver(mConnectionStateReceiver, filter);

        assertThat(mAdapter.startDiscovery()).isTrue();
        assertThat(mFutureDiscoveryStartedIntent.get())
                .isEqualTo(BluetoothAdapter.ACTION_DISCOVERY_STARTED);

        // Wait for device discovery to complete
        assertThat(mFutureDiscoveryFinishedIntent.get())
                .isEqualTo(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        mContext.unregisterReceiver(mConnectionStateReceiver);

        // Ensure we received at least one inquiry response
        assertThat(!mDeviceFoundData.isEmpty()).isTrue();
        Log.i(TAG, "Found inquiry results count:" + mDeviceFoundData.size());
    }
}
