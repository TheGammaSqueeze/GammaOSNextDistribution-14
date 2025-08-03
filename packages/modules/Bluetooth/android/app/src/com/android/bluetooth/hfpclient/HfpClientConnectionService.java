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
package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelUuid;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.pbapclient.PbapClientService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HfpClientConnectionService extends ConnectionService {
    private static final String TAG = "HfpClientConnService";
    private static final boolean DBG = true;

    public static final String HFP_SCHEME = "hfpc";

    private TelecomManager mTelecomManager;

    private HeadsetClientServiceInterface mServiceInterface = new HeadsetClientServiceInterface();

    private final Map<BluetoothDevice, HfpClientDeviceBlock> mDeviceBlocks = new HashMap<>();

    //--------------------------------------------------------------------------------------------//
    // SINGLETON MANAGEMENT                                                                       //
    //--------------------------------------------------------------------------------------------//

    private static final Object INSTANCE_LOCK = new Object();
    private static HfpClientConnectionService sHfpClientConnectionService;

    private void setInstance(HfpClientConnectionService instance) {
        synchronized (INSTANCE_LOCK) {
            sHfpClientConnectionService = instance;
        }
    }

    private static HfpClientConnectionService getInstance() {
        synchronized (INSTANCE_LOCK) {
            return sHfpClientConnectionService;
        }
    }

    private void clearInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sHfpClientConnectionService == this) {
                setInstance(null);
            }
        }
    }

    //--------------------------------------------------------------------------------------------//
    // MESSAGES FROM HEADSET CLIENT SERVICE                                                       //
    //--------------------------------------------------------------------------------------------//

    /**
     * Send a device connection state changed event to this service
     */
    public static void onConnectionStateChanged(BluetoothDevice device, int newState,
            int oldState) {
        HfpClientConnectionService service = getInstance();
        if (service == null) {
            Log.e(TAG, "onConnectionStateChanged: HFP Client Connection Service not started");
            return;
        }
        service.onConnectionStateChangedInternal(device, newState, oldState);
    }

    /**
     * Send a device call state changed event to this service
     */
    public static void onCallChanged(BluetoothDevice device, HfpClientCall call) {
        HfpClientConnectionService service = getInstance();
        if (service == null) {
            Log.e(TAG, "onCallChanged: HFP Client Connection Service not started");
            return;
        }
        service.onCallChangedInternal(device, call);
    }

    /**
     * Send a device audio state changed event to this service
     */
    public static void onAudioStateChanged(BluetoothDevice device, int newState, int oldState) {
        HfpClientConnectionService service = getInstance();
        if (service == null) {
            Log.e(TAG, "onAudioStateChanged: HFP Client Connection Service not started");
            return;
        }
        service.onAudioStateChangedInternal(device, newState, oldState);
    }

    //--------------------------------------------------------------------------------------------//
    // HANDLE MESSAGES FROM HEADSET CLIENT SERVICE                                                //
    //--------------------------------------------------------------------------------------------//

    private void onConnectionStateChangedInternal(BluetoothDevice device, int newState,
            int oldState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (DBG) {
                Log.d(TAG, "Established connection with " + device);
            }

            HfpClientDeviceBlock block = createBlockForDevice(device);
            if (block == null) {
                Log.w(TAG, "Block already exists for device= " + device + ", ignoring.");
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            if (DBG) {
                Log.d(TAG, "Disconnecting from " + device);
            }

            // Disconnect any inflight calls from the connection service.
            synchronized (HfpClientConnectionService.this) {
                HfpClientDeviceBlock block = mDeviceBlocks.remove(device);
                if (block == null) {
                    Log.w(TAG, "Disconnect for device but no block, device=" + device);
                    return;
                }
                block.cleanup();
            }
        }
        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService != null && adapterService.getRemoteDevices() != null) {
            adapterService
                    .getRemoteDevices()
                    .handleHeadsetClientConnectionStateChanged(device, oldState, newState);
        }
        adapterService.notifyProfileConnectionStateChangeToGatt(
                BluetoothProfile.HEADSET_CLIENT, oldState, newState);
        if (PbapClientService.getPbapClientService() != null) {
            PbapClientService.getPbapClientService()
                    .handleHeadsetClientConnectionStateChanged(device, oldState, newState);
        }
        if (adapterService != null) {
            adapterService.updateProfileConnectionAdapterProperties(
                    device, BluetoothProfile.HEADSET_CLIENT, newState, oldState);
        }
    }

    private void onCallChangedInternal(BluetoothDevice device, HfpClientCall call) {
        HfpClientDeviceBlock block = findBlockForDevice(device);
        if (block == null) {
            Log.w(TAG, "Call changed but no block for device=" + device);
            return;
        }

        // If we are not connected, then when we actually do get connected the calls should be added
        // (see ACTION_CONNECTION_STATE_CHANGED intent above).
        block.handleCall(call);
    }

    private void onAudioStateChangedInternal(BluetoothDevice device, int newState, int oldState) {
        HfpClientDeviceBlock block = findBlockForDevice(device);
        if (block == null) {
            Log.w(TAG, "Device audio state changed but no block for device=" + device);
            return;
        }
        block.onAudioStateChange(newState, oldState);
    }

    //--------------------------------------------------------------------------------------------//
    // SERVICE SETUP AND TEAR DOWN                                                                //
    //--------------------------------------------------------------------------------------------//

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) {
            Log.d(TAG, "onCreate");
        }
        mTelecomManager = getSystemService(TelecomManager.class);
        if (mTelecomManager != null) mTelecomManager.clearPhoneAccounts();

        List<BluetoothDevice> devices = mServiceInterface.getConnectedDevices();
        if (devices != null) {
            for (BluetoothDevice device : devices) {
                createBlockForDevice(device);
            }
        }

        setInstance(this);
    }

    @Override
    public void onDestroy() {
        if (DBG) {
            Log.d(TAG, "onDestroy called");
        }

        // Unregister the phone account. This should ideally happen when disconnection ensues but in
        // case the service crashes we may need to force clean.
        disconnectAll();

        clearInstance();
    }

    private synchronized void disconnectAll() {
        for (Iterator<Map.Entry<BluetoothDevice, HfpClientDeviceBlock>> it =
                mDeviceBlocks.entrySet().iterator(); it.hasNext(); ) {
            it.next().getValue().cleanup();
            it.remove();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DBG) {
            Log.d(TAG, "onStartCommand " + intent);
        }
        // In order to make sure that the service is sticky (recovers from errors when HFP
        // connection is still active) and to stop it we need a special intent since stopService
        // only recreates it.
        if (intent != null && intent.getBooleanExtra(HeadsetClientService.HFP_CLIENT_STOP_TAG,
                false)) {
            // Stop the service.
            stopSelf();
            return 0;
        }
        return START_STICKY;
    }

    //--------------------------------------------------------------------------------------------//
    // TELECOM CONNECTION SERVICE FUNCTIONS                                                       //
    //--------------------------------------------------------------------------------------------//

    // This method is called whenever there is a new incoming call (or right after BT connection).
    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        if (DBG) {
            Log.d(TAG,
                    "onCreateIncomingConnection " + connectionManagerAccount + " req: " + request);
        }

        HfpClientDeviceBlock block = findBlockForHandle(connectionManagerAccount);
        if (block == null) {
            Log.w(TAG, "HfpClient does not support having a connection manager");
            return null;
        }

        // We should already have a connection by this time.
        ParcelUuid callUuid =
                request.getExtras().getParcelable(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
        HfpClientConnection connection =
                block.onCreateIncomingConnection((callUuid != null ? callUuid.getUuid() : null));
        return connection;
    }

    // This method is called *only if* Dialer UI is used to place an outgoing call.
    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        if (DBG) {
            Log.d(TAG, "onCreateOutgoingConnection " + connectionManagerAccount);
        }
        HfpClientDeviceBlock block = findBlockForHandle(connectionManagerAccount);
        if (block == null) {
            Log.w(TAG, "HfpClient does not support having a connection manager");
            return null;
        }
        HfpClientConnection connection = block.onCreateOutgoingConnection(request.getAddress());
        return connection;
    }

    // This method is called when:
    // 1. Outgoing call created from the AG.
    // 2. Call transfer from AG -> HF (on connection when existed call present).
    @Override
    public Connection onCreateUnknownConnection(PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        if (DBG) {
            Log.d(TAG, "onCreateUnknownConnection " + connectionManagerAccount);
        }
        HfpClientDeviceBlock block = findBlockForHandle(connectionManagerAccount);
        if (block == null) {
            Log.w(TAG, "HfpClient does not support having a connection manager");
            return null;
        }

        // We should already have a connection by this time.
        ParcelUuid callUuid =
                request.getExtras().getParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
        HfpClientConnection connection =
                block.onCreateUnknownConnection((callUuid != null ? callUuid.getUuid() : null));
        return connection;
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        if (DBG) {
            Log.d(TAG, "onConference " + connection1 + " " + connection2);
        }

        BluetoothDevice bd1 = ((HfpClientConnection) connection1).getDevice();
        BluetoothDevice bd2 = ((HfpClientConnection) connection2).getDevice();
        // We can only conference two connections on same device
        if (!Objects.equals(bd1, bd2)) {
            Log.e(TAG,
                    "Cannot conference calls from two different devices " + "bd1 " + bd1 + " bd2 "
                            + bd2 + " conn1 " + connection1 + "connection2 " + connection2);
            return;
        }

        HfpClientDeviceBlock block = findBlockForDevice(bd1);
        block.onConference(connection1, connection2);
    }

    //--------------------------------------------------------------------------------------------//
    // DEVICE MANAGEMENT                                                                          //
    //--------------------------------------------------------------------------------------------//

    private BluetoothDevice getDevice(PhoneAccountHandle handle) {
        BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
        PhoneAccount account = mTelecomManager.getPhoneAccount(handle);
        if (account == null) {
            return null;
        }
        String btAddr = account.getAddress().getSchemeSpecificPart();
        return adapter.getRemoteDevice(btAddr);
    }

    // Block management functions
    synchronized HfpClientDeviceBlock createBlockForDevice(BluetoothDevice device) {
        Log.d(TAG, "Creating block for device " + device);
        if (mDeviceBlocks.containsKey(device)) {
            Log.e(TAG, "Device already exists " + device + " blocks " + mDeviceBlocks);
            return null;
        }

        HfpClientDeviceBlock block =
                HfpClientDeviceBlock.Factory.build(device, this, mServiceInterface);
        mDeviceBlocks.put(device, block);
        return block;
    }

    synchronized HfpClientDeviceBlock findBlockForDevice(BluetoothDevice device) {
        Log.d(TAG, "Finding block for device " + device + " blocks " + mDeviceBlocks);
        return mDeviceBlocks.get(device);
    }

    synchronized HfpClientDeviceBlock findBlockForHandle(PhoneAccountHandle handle) {
        BluetoothDevice device = getDevice(handle);
        Log.d(TAG, "Finding block for handle " + handle + " device " + device);
        return mDeviceBlocks.get(device);
    }

    PhoneAccount createAccount(BluetoothDevice device) {
        Uri addr = Uri.fromParts(HfpClientConnectionService.HFP_SCHEME, device.getAddress(), null);
        PhoneAccountHandle handle =
                new PhoneAccountHandle(new ComponentName(this, HfpClientConnectionService.class),
                        device.getAddress());

        int capabilities = PhoneAccount.CAPABILITY_CALL_PROVIDER;
        if (getApplicationContext().getResources().getBoolean(
                com.android.bluetooth.R.bool
                .hfp_client_connection_service_support_emergency_call)) {
            // Need to have an emergency call capability to place emergency call
            capabilities |= PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS;
        }

        PhoneAccount account =
                new PhoneAccount.Builder(handle, "HFP " + device.toString()).setAddress(addr)
                        .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_TEL))
                        .setCapabilities(capabilities)
                        .build();
        if (DBG) {
            Log.d(TAG, "phoneaccount: " + account);
        }
        return account;
    }

    private Map<BluetoothDevice, HfpClientDeviceBlock> getDeviceBlocks() {
        return mDeviceBlocks;
    }

    /**
     * Dump the state of the HfpClientConnectionService and internal objects
     */
    public static void dump(StringBuilder sb) {
        HfpClientConnectionService instance = getInstance();
        sb.append("  HfpClientConnectionService:\n");

        if (instance == null) {
            sb.append("    null");
        } else {
            sb.append("    Devices:\n");
            for (HfpClientDeviceBlock block : instance.getDeviceBlocks().values()) {
                sb.append("      " + block.toString() + "\n");
            }
        }
    }
}
