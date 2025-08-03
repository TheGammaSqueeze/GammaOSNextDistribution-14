/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.bluetooth.a2dpsink;

import static java.util.Objects.requireNonNull;

import android.annotation.RequiresPermission;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothA2dpSink;
import android.content.AttributionSource;
import android.content.Context;
import android.media.AudioManager;
import android.os.Looper;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.SynchronousResultReceiver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides Bluetooth A2DP Sink profile, as a service in the Bluetooth application.
 */
public class A2dpSinkService extends ProfileService {
    private static final String TAG = A2dpSinkService.class.getSimpleName();
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private final Map<BluetoothDevice, A2dpSinkStateMachine> mDeviceStateMap =
            new ConcurrentHashMap<>(1);

    private final A2dpSinkNativeInterface mNativeInterface;
    private final Looper mLooper;

    private final Object mActiveDeviceLock = new Object();

    @GuardedBy("mActiveDeviceLock")
    private BluetoothDevice mActiveDevice = null;

    private final Object mStreamHandlerLock = new Object();

    @GuardedBy("mStreamHandlerLock")
    private A2dpSinkStreamHandler mA2dpSinkStreamHandler;

    private static A2dpSinkService sService;

    private int mMaxConnectedAudioDevices;

    private AdapterService mAdapterService;
    private DatabaseManager mDatabaseManager;

    public A2dpSinkService(Context ctx) {
        super(ctx);
        mNativeInterface = requireNonNull(A2dpSinkNativeInterface.getInstance());
        mLooper = Looper.getMainLooper();
    }

    @VisibleForTesting
    A2dpSinkService(Context ctx, A2dpSinkNativeInterface nativeInterface, Looper looper) {
        super(ctx);
        mNativeInterface = requireNonNull(nativeInterface);
        mLooper = looper;
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileA2dpSinkEnabled().orElse(false);
    }

    @Override
    public void start() {
        mAdapterService =
                requireNonNull(
                        AdapterService.getAdapterService(),
                        "AdapterService cannot be null when A2dpSinkService starts");
        mDatabaseManager =
                requireNonNull(
                        AdapterService.getAdapterService().getDatabase(),
                        "DatabaseManager cannot be null when A2dpSinkService starts");

        mMaxConnectedAudioDevices = mAdapterService.getMaxConnectedAudioDevices();
        mNativeInterface.init(mMaxConnectedAudioDevices);

        synchronized (mStreamHandlerLock) {
            mA2dpSinkStreamHandler = new A2dpSinkStreamHandler(this, mNativeInterface);
        }

        setA2dpSinkService(this);
    }

    @Override
    public void stop() {
        setA2dpSinkService(null);
        mNativeInterface.cleanup();
        for (A2dpSinkStateMachine stateMachine : mDeviceStateMap.values()) {
            stateMachine.quitNow();
        }
        mDeviceStateMap.clear();
        synchronized (mStreamHandlerLock) {
            if (mA2dpSinkStreamHandler != null) {
                mA2dpSinkStreamHandler.cleanup();
                mA2dpSinkStreamHandler = null;
            }
        }
    }

    public static synchronized A2dpSinkService getA2dpSinkService() {
        return sService;
    }

    /**
     * Testing API to inject a mockA2dpSinkService.
     */
    @VisibleForTesting
    public static synchronized void setA2dpSinkService(A2dpSinkService service) {
        sService = service;
    }

    /**
     * Set the device that should be allowed to actively stream
     */
    public boolean setActiveDevice(BluetoothDevice device) {
        Log.i(TAG, "setActiveDevice(device=" + device + ")");
        synchronized (mActiveDeviceLock) {
            if (mNativeInterface.setActiveDevice(device)) {
                mActiveDevice = device;
                return true;
            }
            return false;
        }
    }

    /**
     * Get the device that is allowed to be actively streaming
     */
    public BluetoothDevice getActiveDevice() {
        synchronized (mActiveDeviceLock) {
            return mActiveDevice;
        }
    }

    /**
     * Request audio focus such that the designated device can stream audio
     */
    public void requestAudioFocus(BluetoothDevice device, boolean request) {
        synchronized (mStreamHandlerLock) {
            if (mA2dpSinkStreamHandler == null) return;
            mA2dpSinkStreamHandler.requestAudioFocus(request);
        }
    }

    /**
     * Get the current Bluetooth Audio focus state
     *
     * @return AudioManger.AUDIOFOCUS_* states on success, or AudioManager.ERROR on error
     */
    public int getFocusState() {
        synchronized (mStreamHandlerLock) {
            if (mA2dpSinkStreamHandler == null) return AudioManager.ERROR;
            return mA2dpSinkStreamHandler.getFocusState();
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    boolean isA2dpPlaying(BluetoothDevice device) {
        enforceCallingOrSelfPermission(
                BLUETOOTH_PRIVILEGED, "Need BLUETOOTH_PRIVILEGED permission");
        synchronized (mStreamHandlerLock) {
            if (mA2dpSinkStreamHandler == null) return false;
            return mA2dpSinkStreamHandler.isPlaying();
        }
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new A2dpSinkServiceBinder(this);
    }

    //Binder object: Must be static class or memory leak may occur
    @VisibleForTesting
    static class A2dpSinkServiceBinder extends IBluetoothA2dpSink.Stub
            implements IProfileServiceBinder {
        private A2dpSinkService mService;

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private A2dpSinkService getService(AttributionSource source) {
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

        A2dpSinkServiceBinder(A2dpSinkService svc) {
            mService = svc;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @Override
        public void connect(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                A2dpSinkService service = getService(source);
                boolean result = false;
                if (service != null) {
                    result = service.connect(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void disconnect(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                A2dpSinkService service = getService(source);
                boolean result = false;
                if (service != null) {
                    result = service.disconnect(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectedDevices(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                A2dpSinkService service = getService(source);
                List<BluetoothDevice> result = new ArrayList<BluetoothDevice>(0);
                if (service != null) {
                    result = service.getConnectedDevices();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getDevicesMatchingConnectionStates(int[] states,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                A2dpSinkService service = getService(source);
                List<BluetoothDevice> result = new ArrayList<BluetoothDevice>(0);
                if (service != null) {
                    result = service.getDevicesMatchingConnectionStates(states);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionState(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                A2dpSinkService service = getService(source);
                int result = BluetoothProfile.STATE_DISCONNECTED;
                if (service != null) {
                    result = service.getConnectionState(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setConnectionPolicy(BluetoothDevice device, int connectionPolicy,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                A2dpSinkService service = getService(source);
                boolean result = false;
                if (service != null) {
                    result = service.setConnectionPolicy(device, connectionPolicy);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionPolicy(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                A2dpSinkService service = getService(source);
                int result = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
                if (service != null) {
                    result = service.getConnectionPolicy(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isA2dpPlaying(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                A2dpSinkService service = getService(source);
                boolean result = false;
                if (service != null) {
                    result = service.isA2dpPlaying(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getAudioConfig(BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                A2dpSinkService service = getService(source);
                BluetoothAudioConfig result = null;
                if (service != null) {
                    result = service.getAudioConfig(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
    }

    /* Generic Profile Code */

    /**
     * Connect the given Bluetooth device.
     *
     * @return true if connection is successful, false otherwise.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        if (device == null) {
            throw new IllegalArgumentException("Null device");
        }
        if (DBG) {
            StringBuilder sb = new StringBuilder();
            dump(sb);
            Log.d(TAG, " connect device: " + device
                    + ", InstanceMap start state: " + sb.toString());
        }
        if (getConnectionPolicy(device) == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.w(TAG, "Connection not allowed: <" + device
                    + "> is CONNECTION_POLICY_FORBIDDEN");
            return false;
        }

        A2dpSinkStateMachine stateMachine = getOrCreateStateMachine(device);
        if (stateMachine != null) {
            stateMachine.connect();
            return true;
        } else {
            // a state machine instance doesn't exist yet, and the max has been reached.
            Log.e(TAG, "Maxed out on the number of allowed A2DP Sink connections. "
                    + "Connect request rejected on " + device);
            return false;
        }
    }

    /**
     * Disconnect the given Bluetooth device.
     *
     * @return true if disconnect is successful, false otherwise.
     */
    public boolean disconnect(BluetoothDevice device) {
        if (DBG) {
            StringBuilder sb = new StringBuilder();
            dump(sb);
            Log.d(TAG, "A2DP disconnect device: " + device
                    + ", InstanceMap start state: " + sb.toString());
        }

        if (device == null) {
            throw new IllegalArgumentException("Null device");
        }

        A2dpSinkStateMachine stateMachine = mDeviceStateMap.get(device);
        // a state machine instance doesn't exist. maybe it is already gone?
        if (stateMachine == null) {
            return false;
        }
        int connectionState = stateMachine.getState();
        if (connectionState == BluetoothProfile.STATE_DISCONNECTED
                || connectionState == BluetoothProfile.STATE_DISCONNECTING) {
            return false;
        }
        // upon completion of disconnect, the state machine will remove itself from the available
        // devices map
        stateMachine.disconnect();
        return true;
    }

    /**
     * Remove a device's state machine.
     *
     * <p>Called by the state machines when they disconnect.
     *
     * <p>Visible for testing so it can be mocked and verified on.
     */
    void removeStateMachine(A2dpSinkStateMachine stateMachine) {
        if (stateMachine == null) {
            return;
        }
        mDeviceStateMap.remove(stateMachine.getDevice());
        stateMachine.quitNow();
    }

    public List<BluetoothDevice> getConnectedDevices() {
        return getDevicesMatchingConnectionStates(new int[]{BluetoothAdapter.STATE_CONNECTED});
    }

    protected A2dpSinkStateMachine getOrCreateStateMachine(BluetoothDevice device) {
        A2dpSinkStateMachine newStateMachine =
                new A2dpSinkStateMachine(mLooper, device, this, mNativeInterface);
        A2dpSinkStateMachine existingStateMachine =
                mDeviceStateMap.putIfAbsent(device, newStateMachine);
        // Given null is not a valid value in our map, ConcurrentHashMap will return null if the
        // key was absent and our new value was added. We should then start and return it. Else
        // we quit the new one so we don't leak a thread
        if (existingStateMachine == null) {
            newStateMachine.start();
            return newStateMachine;
        }
        return existingStateMachine;
    }

    @VisibleForTesting
    protected A2dpSinkStateMachine getStateMachineForDevice(BluetoothDevice device) {
        return mDeviceStateMap.get(device);
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        if (DBG) Log.d(TAG, "getDevicesMatchingConnectionStates" + Arrays.toString(states));
        List<BluetoothDevice> deviceList = new ArrayList<>();
        BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
        int connectionState;
        for (BluetoothDevice device : bondedDevices) {
            connectionState = getConnectionState(device);
            if (DBG) Log.d(TAG, "Device: " + device + "State: " + connectionState);
            for (int i = 0; i < states.length; i++) {
                if (connectionState == states[i]) {
                    deviceList.add(device);
                }
            }
        }
        if (DBG) Log.d(TAG, deviceList.toString());
        Log.d(TAG, "GetDevicesDone");
        return deviceList;
    }

    /**
     * Get the current connection state of the profile
     *
     * @param device is the remote bluetooth device
     * @return {@link BluetoothProfile#STATE_DISCONNECTED} if this profile is disconnected,
     * {@link BluetoothProfile#STATE_CONNECTING} if this profile is being connected,
     * {@link BluetoothProfile#STATE_CONNECTED} if this profile is connected, or
     * {@link BluetoothProfile#STATE_DISCONNECTING} if this profile is being disconnected
     */
    public int getConnectionState(BluetoothDevice device) {
        if (device == null) return BluetoothProfile.STATE_DISCONNECTED;
        A2dpSinkStateMachine stateMachine = mDeviceStateMap.get(device);
        return (stateMachine == null) ? BluetoothProfile.STATE_DISCONNECTED
                : stateMachine.getState();
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

        if (!mDatabaseManager.setProfileConnectionPolicy(device, BluetoothProfile.A2DP_SINK,
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
     * @param device the remote device
     * @return connection policy of the specified device
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public int getConnectionPolicy(BluetoothDevice device) {
        enforceCallingOrSelfPermission(
                BLUETOOTH_PRIVILEGED, "Need BLUETOOTH_PRIVILEGED permission");
        return mDatabaseManager
                .getProfileConnectionPolicy(device, BluetoothProfile.A2DP_SINK);
    }


    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        ProfileService.println(sb, "Active Device = " + getActiveDevice());
        ProfileService.println(sb, "Max Connected Devices = " + mMaxConnectedAudioDevices);
        ProfileService.println(sb, "Devices Tracked = " + mDeviceStateMap.size());
        for (A2dpSinkStateMachine stateMachine : mDeviceStateMap.values()) {
            ProfileService.println(sb,
                    "==== StateMachine for " + stateMachine.getDevice() + " ====");
            stateMachine.dump(sb);
        }
    }

    BluetoothAudioConfig getAudioConfig(BluetoothDevice device) {
        if (device == null) return null;
        A2dpSinkStateMachine stateMachine = mDeviceStateMap.get(device);
        // a state machine instance doesn't exist. maybe it is already gone?
        if (stateMachine == null) {
            return null;
        }
        return stateMachine.getAudioConfig();
    }

    /**
     * Receive and route a stack event from the JNI
     */
    protected void messageFromNative(StackEvent event) {
        switch (event.mType) {
            case StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED:
                onConnectionStateChanged(event);
                return;
            case StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED:
                onAudioStateChanged(event);
                return;
            case StackEvent.EVENT_TYPE_AUDIO_CONFIG_CHANGED:
                onAudioConfigChanged(event);
                return;
            default:
                Log.e(TAG, "Received unknown stack event of type " + event.mType);
                return;
        }
    }

    private void onConnectionStateChanged(StackEvent event) {
        BluetoothDevice device = event.mDevice;
        if (device == null) {
            return;
        }
        A2dpSinkStateMachine stateMachine = getOrCreateStateMachine(device);
        stateMachine.onStackEvent(event);
    }

    private void onAudioStateChanged(StackEvent event) {
        int state = event.mState;
        synchronized (mStreamHandlerLock) {
            if (mA2dpSinkStreamHandler == null) {
                Log.e(TAG, "Received audio state change before we've been started");
                return;
            } else if (state == StackEvent.AUDIO_STATE_STARTED) {
                mA2dpSinkStreamHandler.obtainMessage(
                        A2dpSinkStreamHandler.SRC_STR_START).sendToTarget();
            } else if (state == StackEvent.AUDIO_STATE_STOPPED
                    || state == StackEvent.AUDIO_STATE_REMOTE_SUSPEND) {
                mA2dpSinkStreamHandler.obtainMessage(
                        A2dpSinkStreamHandler.SRC_STR_STOP).sendToTarget();
            } else {
                Log.w(TAG, "Unhandled audio state change, state=" + state);
            }
        }
    }

    private void onAudioConfigChanged(StackEvent event) {
        BluetoothDevice device = event.mDevice;
        if (device == null) {
            return;
        }
        A2dpSinkStateMachine stateMachine = getStateMachineForDevice(device);
        if (stateMachine == null) {
            Log.w(
                    TAG,
                    "Received audio config changed event for an unconnected device, device="
                            + device);
            return;
        }
        stateMachine.onStackEvent(event);
    }

    void connectionStateChanged(BluetoothDevice device, int fromState, int toState) {
        mAdapterService.notifyProfileConnectionStateChangeToGatt(
                BluetoothProfile.A2DP_SINK, fromState, toState);
        mAdapterService.updateProfileConnectionAdapterProperties(
                device, BluetoothProfile.A2DP_SINK, toState, fromState);
    }
}
