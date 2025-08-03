/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth.pan;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.TETHER_PRIVILEGED;

import android.annotation.RequiresPermission;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothPan.LocalPanRole;
import android.bluetooth.BluetoothPan.RemotePanRole;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothPan;
import android.bluetooth.IBluetoothPanCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.net.TetheringInterface;
import android.net.TetheringManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserManager;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import com.android.bluetooth.BluetoothMetricsProto;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.flags.Flags;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.HandlerExecutor;
import com.android.modules.utils.SynchronousResultReceiver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provides Bluetooth Pan Device profile, as a service in
 * the Bluetooth application.
 * @hide
 */
public class PanService extends ProfileService {
    private static final String TAG = "PanService";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static PanService sPanService;

    private static final String BLUETOOTH_IFACE_ADDR_START = "192.168.44.1";
    private static final int BLUETOOTH_MAX_PAN_CONNECTIONS = 5;
    private static final int BLUETOOTH_PREFIX_LENGTH = 24;

    @VisibleForTesting
    HashMap<BluetoothDevice, BluetoothPanDevice> mPanDevices;
    private int mMaxPanDevices;
    private String mPanIfName;
    @VisibleForTesting
    boolean mIsTethering = false;
    private boolean mNativeAvailable;
    private HashMap<String, IBluetoothPanCallback> mBluetoothTetheringCallbacks;

    private TetheringManager mTetheringManager;
    private DatabaseManager mDatabaseManager;
    @VisibleForTesting
    UserManager mUserManager;

    private static final int MESSAGE_CONNECT = 1;
    private static final int MESSAGE_DISCONNECT = 2;
    private static final int MESSAGE_CONNECT_STATE_CHANGED = 11;
    private boolean mTetherOn = false;

    private BluetoothTetheringNetworkFactory mNetworkFactory;
    private boolean mStarted = false;

    private AdapterService mAdapterService;

    @VisibleForTesting PanNativeInterface mNativeInterface;

    TetheringManager.TetheringEventCallback mTetheringCallback =
            new TetheringManager.TetheringEventCallback() {
                @Override
                public void onError(TetheringInterface iface, int error) {
                    if (mIsTethering && iface.getType() == TetheringManager.TETHERING_BLUETOOTH) {
                        // tethering is fail because of @TetheringIfaceError error.
                        Log.e(TAG, "Error setting up tether interface: " + error);
                        for (Map.Entry device : mPanDevices.entrySet()) {
                            mNativeInterface.disconnect(
                                    Utils.getByteAddress((BluetoothDevice) device.getKey()));
                        }
                        mPanDevices.clear();
                        mIsTethering = false;
                    }
                }
            };

    public PanService(Context ctx) {
        super(ctx);
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfilePanNapEnabled().orElse(false)
                || BluetoothProperties.isProfilePanPanuEnabled().orElse(false);
    }

    @Override
    public IProfileServiceBinder initBinder() {
        return new BluetoothPanBinder(this);
    }

    public static synchronized PanService getPanService() {
        if (sPanService == null) {
            Log.w(TAG, "getPanService(): service is null");
            return null;
        }
        if (!sPanService.isAvailable()) {
            Log.w(TAG, "getPanService(): service is not available ");
            return null;
        }
        return sPanService;
    }

    private static synchronized void setPanService(PanService instance) {
        if (DBG) {
            Log.d(TAG, "setPanService(): set to: " + instance);
        }
        sPanService = instance;
    }

    @Override
    public void start() {
        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when PanService starts");
        mDatabaseManager = Objects.requireNonNull(AdapterService.getAdapterService().getDatabase(),
                "DatabaseManager cannot be null when PanService starts");
        mNativeInterface =
                Objects.requireNonNull(
                        PanNativeInterface.getInstance(),
                        "PanNativeInterface cannot be null when PanService starts");

        mBluetoothTetheringCallbacks = new HashMap<>();
        mPanDevices = new HashMap<BluetoothDevice, BluetoothPanDevice>();
        try {
            mMaxPanDevices = getResources().getInteger(
                    com.android.bluetooth.R.integer.config_max_pan_devices);
        } catch (NotFoundException e) {
            mMaxPanDevices = BLUETOOTH_MAX_PAN_CONNECTIONS;
        }
        mNativeInterface.init(this);

        mUserManager = getSystemService(UserManager.class);

        mTetheringManager = getSystemService(TetheringManager.class);
        mTetheringManager.registerTetheringEventCallback(
                new HandlerExecutor(new Handler(Looper.getMainLooper())), mTetheringCallback);
        setPanService(this);
        mStarted = true;
    }

    @Override
    public void stop() {
        if (!mStarted) {
            Log.w(TAG, "stop() called before start()");
            return;
        }
        if (mTetheringManager != null) {
            mTetheringManager.unregisterTetheringEventCallback(mTetheringCallback);
            mTetheringManager = null;
        }
        mNativeInterface.cleanup();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void cleanup() {
        // TODO(b/72948646): this should be moved to stop()
        setPanService(null);

        mUserManager = null;

        if (mPanDevices != null) {
           int[] desiredStates = {BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED,
                                  BluetoothProfile.STATE_DISCONNECTING};
           List<BluetoothDevice> devList =
                   getDevicesMatchingConnectionStates(desiredStates);
           for (BluetoothDevice device : devList) {
                BluetoothPanDevice panDevice = mPanDevices.get(device);
                Log.d(TAG, "panDevice: " + panDevice + " device address: " + device);
                if (panDevice != null) {
                    handlePanDeviceStateChange(device, mPanIfName,
                        BluetoothProfile.STATE_DISCONNECTED,
                        panDevice.mLocalRole, panDevice.mRemoteRole);
                }
            }
            mPanDevices.clear();
        }
    }

    private final Handler mHandler =
            new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MESSAGE_CONNECT:
                            BluetoothDevice connectDevice = (BluetoothDevice) msg.obj;
                            if (!mNativeInterface.connect(
                                    Flags.identityAddressNullIfUnknown()
                                            ? Utils.getByteBrEdrAddress(connectDevice)
                                            : mAdapterService.getByteIdentityAddress(
                                                    connectDevice))) {
                                handlePanDeviceStateChange(
                                        connectDevice,
                                        null,
                                        BluetoothProfile.STATE_CONNECTING,
                                        BluetoothPan.LOCAL_PANU_ROLE,
                                        BluetoothPan.REMOTE_NAP_ROLE);
                                handlePanDeviceStateChange(
                                        connectDevice,
                                        null,
                                        BluetoothProfile.STATE_DISCONNECTED,
                                        BluetoothPan.LOCAL_PANU_ROLE,
                                        BluetoothPan.REMOTE_NAP_ROLE);
                            }
                            break;
                        case MESSAGE_DISCONNECT:
                            BluetoothDevice disconnectDevice = (BluetoothDevice) msg.obj;
                            if (!mNativeInterface.disconnect(
                                    Flags.identityAddressNullIfUnknown()
                                            ? Utils.getByteBrEdrAddress(disconnectDevice)
                                            : mAdapterService.getByteIdentityAddress(
                                                    disconnectDevice))) {
                                handlePanDeviceStateChange(
                                        disconnectDevice,
                                        mPanIfName,
                                        BluetoothProfile.STATE_DISCONNECTING,
                                        BluetoothPan.LOCAL_PANU_ROLE,
                                        BluetoothPan.REMOTE_NAP_ROLE);
                                handlePanDeviceStateChange(
                                        disconnectDevice,
                                        mPanIfName,
                                        BluetoothProfile.STATE_DISCONNECTED,
                                        BluetoothPan.LOCAL_PANU_ROLE,
                                        BluetoothPan.REMOTE_NAP_ROLE);
                            }
                            break;
                        case MESSAGE_CONNECT_STATE_CHANGED:
                            ConnectState cs = (ConnectState) msg.obj;
                            final BluetoothDevice device =
                                    mAdapterService.getDeviceFromByte(cs.addr);
                            // TBD get iface from the msg
                            if (DBG) {
                                Log.d(
                                        TAG,
                                        "MESSAGE_CONNECT_STATE_CHANGED: "
                                                + device
                                                + " state: "
                                                + cs.state);
                            }
                            // It could be null if the connection up is coming when the
                            // Bluetooth is turning off.
                            if (device == null) {
                                break;
                            }
                            handlePanDeviceStateChange(
                                    device,
                                    mPanIfName /* iface */,
                                    cs.state,
                                    cs.local_role,
                                    cs.remote_role);
                            break;
                    }
                }
            };

    /**
     * Handlers for incoming service calls
     */
    @VisibleForTesting
    static class BluetoothPanBinder extends IBluetoothPan.Stub
            implements IProfileServiceBinder {
        private PanService mService;

        BluetoothPanBinder(PanService svc) {
            mService = svc;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private PanService getService(AttributionSource source) {
            if (Utils.isInstrumentationTestMode()) {
                return mService;
            }
            if (!Utils.checkServiceAvailable(mService, TAG)
                    || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                    || !Utils.checkConnectPermissionForDataDelivery(mService, source, TAG)) {
                return null;
            }
            return mService;
        }

        @Override
        public void connect(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                PanService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.connect(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void disconnect(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                PanService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.disconnect(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setConnectionPolicy(BluetoothDevice device, int connectionPolicy,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                PanService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.setConnectionPolicy(device, connectionPolicy);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionState(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                PanService service = getService(source);
                int defaultValue = BluetoothPan.STATE_DISCONNECTED;
                if (service != null) {
                    defaultValue = service.getConnectionState(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isTetheringOn(AttributionSource source, SynchronousResultReceiver receiver) {
            // TODO(BT) have a variable marking the on/off state
            try {
                PanService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.isTetheringOn();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setBluetoothTethering(IBluetoothPanCallback callback, int id,
                boolean value, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                PanService service = getService(source);
                if (service != null) {
                    Log.d(TAG, "setBluetoothTethering: " + value
                            + ", pkgName: " + source.getPackageName()
                            + ", mTetherOn: " + service.mTetherOn);
                    service.setBluetoothTethering(callback, id, source.getUid(), value);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectedDevices(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                PanService service = getService(source);
                List<BluetoothDevice> defaultValue = new ArrayList<BluetoothDevice>(0);
                if (service != null) {
                    defaultValue = service.getConnectedDevices();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getDevicesMatchingConnectionStates(int[] states,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                PanService service = getService(source);
                List<BluetoothDevice> defaultValue = new ArrayList<BluetoothDevice>(0);
                if (service != null) {
                    defaultValue = service.getDevicesMatchingConnectionStates(states);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean connect(BluetoothDevice device) {
        if (mUserManager.isGuestUser()) {
            Log.w(TAG, "Guest user does not have the permission to change the WiFi network");
            return false;
        }
        if (getConnectionState(device) != BluetoothProfile.STATE_DISCONNECTED) {
            Log.e(TAG, "Pan Device not disconnected: " + device);
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT, device);
        mHandler.sendMessage(msg);
        return true;
    }

    public boolean disconnect(BluetoothDevice device) {
        Message msg = mHandler.obtainMessage(MESSAGE_DISCONNECT, device);
        mHandler.sendMessage(msg);
        return true;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(
                BLUETOOTH_PRIVILEGED, "Need BLUETOOTH_PRIVILEGED permission");
        BluetoothPanDevice panDevice = mPanDevices.get(device);
        if (panDevice == null) {
            return BluetoothPan.STATE_DISCONNECTED;
        }
        return panDevice.mState;
    }

    public boolean isTetheringOn() {
        // TODO(BT) have a variable marking the on/off state
        return mTetherOn;
    }

    @RequiresPermission(allOf = {
            android.Manifest.permission.BLUETOOTH_PRIVILEGED,
            android.Manifest.permission.TETHER_PRIVILEGED,
    })
    void setBluetoothTethering(IBluetoothPanCallback callback, int id, int callerUid,
            boolean value) {
        if (DBG) {
            Log.d(TAG, "setBluetoothTethering: " + value + ", mTetherOn: " + mTetherOn);
        }
        enforceCallingOrSelfPermission(
                BLUETOOTH_PRIVILEGED, "Need BLUETOOTH_PRIVILEGED permission");
        enforceCallingOrSelfPermission(
                TETHER_PRIVILEGED, "Need TETHER_PRIVILEGED permission");

        UserManager um = getSystemService(UserManager.class);
        if (um.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING) && value) {
            throw new SecurityException("DISALLOW_CONFIG_TETHERING is enabled for this user.");
        }
        final String key = id + "/" + callerUid;
        if (callback != null) {
            boolean keyExists = mBluetoothTetheringCallbacks.containsKey(key);
            if (value) {
                if (!keyExists) {
                    mBluetoothTetheringCallbacks.put(key, callback);
                } else {
                    Log.e(TAG, "setBluetoothTethering Error: Callback already registered.");
                    return;
                }
            } else {
                if (keyExists) {
                    mBluetoothTetheringCallbacks.remove(key);
                } else {
                    Log.e(TAG, "setBluetoothTethering Error: Callback not registered.");
                    return;
                }
            }
        }
        if (mTetherOn != value) {
            //drop any existing panu or pan-nap connection when changing the tethering state
            mTetherOn = value;
            List<BluetoothDevice> devList = getConnectedDevices();
            for (BluetoothDevice dev : devList) {
                disconnect(dev);
            }
            Intent intent = new Intent(BluetoothPan.ACTION_TETHERING_STATE_CHANGED);
            intent.putExtra(BluetoothPan.EXTRA_TETHERING_STATE,
                    mTetherOn ? BluetoothPan.TETHERING_STATE_ON : BluetoothPan.TETHERING_STATE_OFF);
            Utils.sendBroadcast(this, intent, null, Utils.getTempAllowlistBroadcastOptions());
        }
    }

    /**
     * Set connection policy of the profile and connects it if connectionPolicy is
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED} or disconnects if connectionPolicy is
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN}
     *
     * <p> The device should already be paired.
     * Connection policy can be one of:
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED},
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN},
     * {@link BluetoothProfile#CONNECTION_POLICY_UNKNOWN}
     *
     * @param device Paired bluetooth device
     * @param connectionPolicy is the connection policy to set to for this profile
     * @return true if connectionPolicy is set, false on error
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean setConnectionPolicy(BluetoothDevice device, int connectionPolicy) {
        enforceCallingOrSelfPermission(
                BLUETOOTH_PRIVILEGED, "Need BLUETOOTH_PRIVILEGED permission");
        if (DBG) {
            Log.d(TAG, "Saved connectionPolicy " + device + " = " + connectionPolicy);
        }

        if (!mDatabaseManager.setProfileConnectionPolicy(device, BluetoothProfile.PAN,
                  connectionPolicy)) {
            return false;
        }
        if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            connect(device);
        } else if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            disconnect(device);
        }
        return true;
    }

    /**
     * Get the connection policy of the profile.
     *
     * <p> The connection policy can be any of:
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED},
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN},
     * {@link BluetoothProfile#CONNECTION_POLICY_UNKNOWN}
     *
     * @param device Bluetooth device
     * @return connection policy of the device
     * @hide
     */
    public int getConnectionPolicy(BluetoothDevice device) {
        return mDatabaseManager
                .getProfileConnectionPolicy(device, BluetoothProfile.PAN);
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(
                BLUETOOTH_PRIVILEGED, "Need BLUETOOTH_PRIVILEGED permission");
        List<BluetoothDevice> devices =
                getDevicesMatchingConnectionStates(new int[]{BluetoothProfile.STATE_CONNECTED});
        return devices;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> panDevices = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device : mPanDevices.keySet()) {
            int panDeviceState = getConnectionState(device);
            for (int state : states) {
                if (state == panDeviceState) {
                    panDevices.add(device);
                    break;
                }
            }
        }
        return panDevices;
    }

    protected static class ConnectState {
        public ConnectState(byte[] address, int state, int error, int localRole, int remoteRole) {
            this.addr = address;
            this.state = state;
            this.error = error;
            this.local_role = localRole;
            this.remote_role = remoteRole;
        }

        public byte[] addr;
        public int state;
        public int error;
        public int local_role;
        public int remote_role;
    }

    void onConnectStateChanged(byte[] address, int state, int error, int localRole,
            int remoteRole) {
        if (DBG) {
            Log.d(TAG, "onConnectStateChanged: " + state + ", local role:" + localRole
                    + ", remoteRole: " + remoteRole);
        }
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT_STATE_CHANGED);
        msg.obj = new ConnectState(address, state, error, localRole, remoteRole);
        mHandler.sendMessage(msg);
    }

    @VisibleForTesting
    void onControlStateChanged(int localRole, int state, int error, String ifname) {
        if (DBG) {
            Log.d(TAG, "onControlStateChanged: " + state + ", error: " + error + ", ifname: "
                    + ifname);
        }
        if (error == 0) {
            mPanIfName = ifname;
        }
    }


    void handlePanDeviceStateChange(BluetoothDevice device, String iface, int state,
            @LocalPanRole int localRole, @RemotePanRole int remoteRole) {
        if (DBG) {
            Log.d(TAG, "handlePanDeviceStateChange: device: " + device + ", iface: " + iface
                    + ", state: " + state + ", localRole:" + localRole + ", remoteRole:"
                    + remoteRole);
        }
        int prevState;

        BluetoothPanDevice panDevice = mPanDevices.get(device);
        if (panDevice == null) {
            Log.i(TAG, "state " + state + " Num of connected pan devices: " + mPanDevices.size());
            prevState = BluetoothProfile.STATE_DISCONNECTED;
            panDevice = new BluetoothPanDevice(state, iface, localRole, remoteRole);
            mPanDevices.put(device, panDevice);
        } else {
            prevState = panDevice.mState;
            panDevice.mState = state;
            panDevice.mLocalRole = localRole;
            panDevice.mRemoteRole = remoteRole;
            panDevice.mIface = iface;
        }

        // Avoid race condition that gets this class stuck in STATE_DISCONNECTING. While we
        // are in STATE_CONNECTING, if a BluetoothPan#disconnect call comes in, the original
        // connect call will put us in STATE_DISCONNECTED. Then, the disconnect completes and
        // changes the state to STATE_DISCONNECTING. All future calls to BluetoothPan#connect
        // will fail until the caller explicitly calls BluetoothPan#disconnect.
        if (prevState == BluetoothProfile.STATE_DISCONNECTED
                && state == BluetoothProfile.STATE_DISCONNECTING) {
            Log.d(TAG, "Ignoring state change from " + prevState + " to " + state);
            mPanDevices.remove(device);
            return;
        }

        Log.d(TAG, "handlePanDeviceStateChange preState: " + prevState + " state: " + state);
        if (prevState == state) {
            return;
        }
        if (remoteRole == BluetoothPan.LOCAL_PANU_ROLE) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                if ((!mTetherOn) || (localRole == BluetoothPan.LOCAL_PANU_ROLE)) {
                    Log.d(TAG, "handlePanDeviceStateChange BT tethering is off/Local role"
                            + " is PANU drop the connection");
                    mPanDevices.remove(device);
                    mNativeInterface.disconnect(Utils.getByteAddress(device));
                    return;
                }
                Log.d(TAG, "handlePanDeviceStateChange LOCAL_NAP_ROLE:REMOTE_PANU_ROLE");
                if (!mIsTethering) {
                    mIsTethering = true;
                    try {
                        for (IBluetoothPanCallback cb : mBluetoothTetheringCallbacks.values()) {
                            cb.onAvailable(iface);
                        }
                    } catch (RemoteException e) {
                        throw e.rethrowFromSystemServer();
                    }
                }
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                mPanDevices.remove(device);
                Log.i(TAG, "remote(PANU) is disconnected, Remaining connected PANU devices: "
                        + mPanDevices.size());
                if (mIsTethering && mPanDevices.size() == 0) {
                    try {
                        for (IBluetoothPanCallback cb : mBluetoothTetheringCallbacks.values()) {
                            cb.onUnavailable();
                        }
                    } catch (RemoteException e) {
                        throw e.rethrowFromSystemServer();
                    }
                    mIsTethering = false;
                }
            }
        } else if (mStarted) {
            // PANU Role = reverse Tether

            Log.d(TAG, "handlePanDeviceStateChange LOCAL_PANU_ROLE:REMOTE_NAP_ROLE state = " + state
                    + ", prevState = " + prevState);
            if (state == BluetoothProfile.STATE_CONNECTED) {
                mNetworkFactory = new BluetoothTetheringNetworkFactory(
                        getBaseContext(), getMainLooper(), this);
                mNetworkFactory.startReverseTether(iface);
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (mNetworkFactory != null) {
                    mNetworkFactory.stopReverseTether();
                    mNetworkFactory = null;
                }
                mPanDevices.remove(device);
            }
        }

        if (state == BluetoothProfile.STATE_CONNECTED) {
            MetricsLogger.logProfileConnectionEvent(BluetoothMetricsProto.ProfileId.PAN);
        }
        mAdapterService.updateProfileConnectionAdapterProperties(
                device, BluetoothProfile.PAN, state, prevState);

        /* Notifying the connection state change of the profile before sending the intent for
           connection state change, as it was causing a race condition, with the UI not being
           updated with the correct connection state. */
        Log.d(TAG, "Pan Device state : device: " + device + " State:" + prevState + "->" + state);
        Intent intent = new Intent(BluetoothPan.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothPan.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothPan.EXTRA_STATE, state);
        intent.putExtra(BluetoothPan.EXTRA_LOCAL_ROLE, localRole);
        sendBroadcast(intent, BLUETOOTH_CONNECT);
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        println(sb, "mMaxPanDevices: " + mMaxPanDevices);
        println(sb, "mPanIfName: " + mPanIfName);
        println(sb, "mTetherOn: " + mTetherOn);
        println(sb, "mPanDevices:");
        for (BluetoothDevice device : mPanDevices.keySet()) {
            println(sb, "  " + device + " : " + mPanDevices.get(device));
        }
    }

    @VisibleForTesting
    static class BluetoothPanDevice {
        private int mState;
        private String mIface;
        private int mLocalRole; // Which local role is this PAN device bound to
        private int mRemoteRole; // Which remote role is this PAN device bound to

        BluetoothPanDevice(int state, String iface, int localRole, int remoteRole) {
            mState = state;
            mIface = iface;
            mLocalRole = localRole;
            mRemoteRole = remoteRole;
        }
    }
}
