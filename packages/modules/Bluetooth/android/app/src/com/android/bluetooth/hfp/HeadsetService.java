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

package com.android.bluetooth.hfp;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.MODIFY_PHONE_STATE;

import static com.android.bluetooth.Utils.enforceBluetoothPrivilegedPermission;
import static com.android.modules.utils.build.SdkLevel.isAtLeastU;

import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothHeadset;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.sysprop.BluetoothProperties;
import android.telecom.PhoneAccount;
import android.util.Log;

import com.android.bluetooth.BluetoothMetricsProto;
import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.AudioRoutingManager;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.hfpclient.HeadsetClientService;
import com.android.bluetooth.hfpclient.HeadsetClientStateMachine;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.bluetooth.telephony.BluetoothInCallService;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.SynchronousResultReceiver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Provides Bluetooth Headset and Handsfree profile, as a service in the Bluetooth application.
 *
 * Three modes for SCO audio:
 * Mode 1: Telecom call through {@link #phoneStateChanged(int, int, int, String, int, String,
 *         boolean)}
 * Mode 2: Virtual call through {@link #startScoUsingVirtualVoiceCall()}
 * Mode 3: Voice recognition through {@link #startVoiceRecognition(BluetoothDevice)}
 *
 * When one mode is active, other mode cannot be started. API user has to terminate existing modes
 * using the correct API or just {@link #disconnectAudio()} if user is a system service, before
 * starting a new mode.
 *
 * {@link #connectAudio()} will start SCO audio at one of the above modes, but won't change mode
 * {@link #disconnectAudio()} can happen in any mode to disconnect SCO
 *
 * When audio is disconnected, only Mode 1 Telecom call will be persisted, both Mode 2 virtual call
 * and Mode 3 voice call will be terminated upon SCO termination and client has to restart the mode.
 *
 * NOTE: SCO termination can either be initiated on the AG side or the HF side
 * TODO(b/79660380): As a workaround, voice recognition will be terminated if virtual call or
 * Telecom call is initiated while voice recognition is ongoing, in case calling app did not call
 * {@link #stopVoiceRecognition(BluetoothDevice)}
 *
 * AG - Audio Gateway, device running this {@link HeadsetService}, e.g. Android Phone
 * HF - Handsfree device, device running headset client, e.g. Wireless headphones or car kits
 */
public class HeadsetService extends ProfileService {
    private static final String TAG = "HeadsetService";
    private static final boolean DBG = false;

    /**
     * HFP AG owned/managed components
     */
    private static final String HFP_AG_IN_CALL_SERVICE =
            BluetoothInCallService.class.getCanonicalName();

    private static final String DISABLE_INBAND_RINGING_PROPERTY =
            "persist.bluetooth.disableinbandringing";
    private static final String REJECT_SCO_IF_HFPC_CONNECTED_PROPERTY =
            "bluetooth.hfp.reject_sco_if_hfpc_connected";
    private static final ParcelUuid[] HEADSET_UUIDS = {BluetoothUuid.HSP, BluetoothUuid.HFP};
    private static final int[] CONNECTING_CONNECTED_STATES =
            {BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED};
    private static final int DIALING_OUT_TIMEOUT_MS = 10000;
    private static final int CLCC_END_MARK_INDEX = 0;

    // Timeout for state machine thread join, to prevent potential ANR.
    private static final int SM_THREAD_JOIN_TIMEOUT_MS = 1000;

    private int mMaxHeadsetConnections = 1;
    private BluetoothDevice mActiveDevice;
    private AdapterService mAdapterService;
    private DatabaseManager mDatabaseManager;
    private HandlerThread mStateMachinesThread;
    private Handler mStateMachinesThreadHandler;
    private Handler mHandler;
    // This is also used as a lock for shared data in HeadsetService
    private final HashMap<BluetoothDevice, HeadsetStateMachine> mStateMachines = new HashMap<>();
    private HeadsetNativeInterface mNativeInterface;
    private HeadsetSystemInterface mSystemInterface;
    private boolean mAudioRouteAllowed = true;
    // Indicates whether SCO audio needs to be forced to open regardless ANY OTHER restrictions
    private boolean mForceScoAudio;
    private boolean mInbandRingingRuntimeDisable;
    private boolean mVirtualCallStarted;
    // Non null value indicates a pending dialing out event is going on
    private DialingOutTimeoutEvent mDialingOutTimeoutEvent;
    private boolean mVoiceRecognitionStarted;
    // Non null value indicates a pending voice recognition request from headset is going on
    private VoiceRecognitionTimeoutEvent mVoiceRecognitionTimeoutEvent;
    // Timeout when voice recognition is started by remote device
    @VisibleForTesting static int sStartVrTimeoutMs = 5000;
    private ArrayList<StateMachineTask> mPendingClccResponses = new ArrayList<>();
    private boolean mStarted;
    private static HeadsetService sHeadsetService;

    private final ServiceFactory mFactory = new ServiceFactory();

    public HeadsetService(Context ctx) {
        super(ctx);
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileHfpAgEnabled().orElse(false);
    }

    @Override
    public IProfileServiceBinder initBinder() {
        return new BluetoothHeadsetBinder(this);
    }

    @Override
    public void start() {
        Log.i(TAG, "start()");
        if (mStarted) {
            throw new IllegalStateException("start() called twice");
        }

        setComponentAvailable(HFP_AG_IN_CALL_SERVICE, true);

        // Step 1: Get AdapterService and DatabaseManager, should never be null
        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when HeadsetService starts");
        mDatabaseManager = Objects.requireNonNull(mAdapterService.getDatabase(),
                "DatabaseManager cannot be null when HeadsetService starts");
        // Step 2: Start handler thread for state machines
        mHandler = new Handler(Looper.getMainLooper());
        mStateMachinesThread = new HandlerThread("HeadsetService.StateMachines");
        mStateMachinesThread.start();
        // Step 3: Initialize system interface
        mSystemInterface = HeadsetObjectsFactory.getInstance().makeSystemInterface(this);
        // Step 4: Initialize native interface
        setHeadsetService(this);
        mMaxHeadsetConnections = mAdapterService.getMaxConnectedAudioDevices();
        mNativeInterface = HeadsetObjectsFactory.getInstance().getNativeInterface();
        // Add 1 to allow a pending device to be connecting or disconnecting
        mNativeInterface.init(mMaxHeadsetConnections + 1, isInbandRingingEnabled());
        // Step 5: Check if state machine table is empty, crash if not
        if (mStateMachines.size() > 0) {
            throw new IllegalStateException(
                    "start(): mStateMachines is not empty, " + mStateMachines.size()
                            + " is already created. Was stop() called properly?");
        }
        // Step 6: Setup broadcast receivers
        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(AudioManager.ACTION_VOLUME_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        registerReceiver(mHeadsetReceiver, filter);
        // Step 7: Mark service as started
        mStarted = true;
    }

    @Override
    public void stop() {
        Log.i(TAG, "stop()");
        if (!mStarted) {
            Log.w(TAG, "stop() called before start()");
            // Still return true because it is considered "stopped" and doesn't have any functional
            // impact on the user
            return;
        }
        // Step 7: Mark service as stopped
        mStarted = false;
        // Step 6: Tear down broadcast receivers
        unregisterReceiver(mHeadsetReceiver);
        synchronized (mStateMachines) {
            // Reset active device to null
            mActiveDevice = null;
            mInbandRingingRuntimeDisable = false;
            mForceScoAudio = false;
            mAudioRouteAllowed = true;
            mMaxHeadsetConnections = 1;
            mVoiceRecognitionStarted = false;
            mVirtualCallStarted = false;
            if (mDialingOutTimeoutEvent != null) {
                getStateMachinesThreadHandler()
                        .removeCallbacks(mDialingOutTimeoutEvent);
                mDialingOutTimeoutEvent = null;
            }
            if (mVoiceRecognitionTimeoutEvent != null) {
                getStateMachinesThreadHandler()
                        .removeCallbacks(mVoiceRecognitionTimeoutEvent);
                mVoiceRecognitionTimeoutEvent = null;
                if (mSystemInterface.getVoiceRecognitionWakeLock().isHeld()) {
                    mSystemInterface.getVoiceRecognitionWakeLock().release();
                }
            }
            // Step 5: Destroy state machines
            for (HeadsetStateMachine stateMachine : mStateMachines.values()) {
                HeadsetObjectsFactory.getInstance().destroyStateMachine(stateMachine);
            }
            mStateMachines.clear();
        }
        // Step 4: Destroy native interface
        mNativeInterface.cleanup();
        setHeadsetService(null);
        // Step 3: Destroy system interface
        mSystemInterface.stop();
        // Step 2: Stop handler thread
        try {
            mStateMachinesThread.quitSafely();
            mStateMachinesThread.join(SM_THREAD_JOIN_TIMEOUT_MS);
            mStateMachinesThread = null;
        } catch (InterruptedException e) {
            // Do not rethrow as we are shutting down anyway
        }

        mStateMachinesThreadHandler = null;

        // Unregister Handler and stop all queued messages.
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }

        // Step 1: Clear
        synchronized (mStateMachines) {
            mAdapterService = null;
        }
        setComponentAvailable(HFP_AG_IN_CALL_SERVICE, false);
    }

    @Override
    public void cleanup() {
        Log.i(TAG, "cleanup");
    }

    /**
     * Checks if this service object is able to accept binder calls
     *
     * @return True if the object can accept binder calls, False otherwise
     */
    public boolean isAlive() {
        return isAvailable() && mStarted;
    }

    /**
     * Get the {@link Looper} for the state machine thread. This is used in testing and helper
     * objects
     *
     * @return {@link Looper} for the state machine thread
     */
    @VisibleForTesting
    public Looper getStateMachinesThreadLooper() {
        return mStateMachinesThread.getLooper();
    }

    interface StateMachineTask {
        void execute(HeadsetStateMachine stateMachine);
    }

    private boolean doForStateMachine(BluetoothDevice device, StateMachineTask task) {
        synchronized (mStateMachines) {
            HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                return false;
            }
            task.execute(stateMachine);
        }
        return true;
    }

    private void doForEachConnectedStateMachine(StateMachineTask task) {
        synchronized (mStateMachines) {
            for (BluetoothDevice device : getConnectedDevices()) {
                task.execute(mStateMachines.get(device));
            }
        }
    }

    private void doForEachConnectedStateMachine(List<StateMachineTask> tasks) {
        synchronized (mStateMachines) {
            for (BluetoothDevice device : getConnectedDevices()) {
                for (StateMachineTask task : tasks) {
                    task.execute(mStateMachines.get(device));
                }
            }
        }
    }

    void onDeviceStateChanged(HeadsetDeviceState deviceState) {
        doForEachConnectedStateMachine(
                stateMachine -> stateMachine.sendMessage(HeadsetStateMachine.DEVICE_STATE_CHANGED,
                        deviceState));
    }

    /**
     * Handle messages from native (JNI) to Java. This needs to be synchronized to avoid posting
     * messages to state machine before start() is done
     *
     * @param stackEvent event from native stack
     */
    void messageFromNative(HeadsetStackEvent stackEvent) {
        Objects.requireNonNull(stackEvent.device,
                "Device should never be null, event: " + stackEvent);
        synchronized (mStateMachines) {
            HeadsetStateMachine stateMachine = mStateMachines.get(stackEvent.device);
            if (stackEvent.type == HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED) {
                switch (stackEvent.valueInt) {
                    case HeadsetHalConstants.CONNECTION_STATE_CONNECTED:
                    case HeadsetHalConstants.CONNECTION_STATE_CONNECTING: {
                        // Create new state machine if none is found
                        if (stateMachine == null) {
                            stateMachine = HeadsetObjectsFactory.getInstance()
                                    .makeStateMachine(stackEvent.device,
                                            mStateMachinesThread.getLooper(), this, mAdapterService,
                                            mNativeInterface, mSystemInterface);
                            mStateMachines.put(stackEvent.device, stateMachine);
                        }
                        break;
                    }
                }
            }
            if (stateMachine == null) {
                throw new IllegalStateException(
                        "State machine not found for stack event: " + stackEvent);
            }
            stateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT, stackEvent);
        }
    }

    private final BroadcastReceiver mHeadsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                Log.w(TAG, "mHeadsetReceiver, action is null");
                return;
            }
            switch (action) {
                case Intent.ACTION_BATTERY_CHANGED: {
                    int batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    if (batteryLevel < 0 || scale <= 0) {
                        Log.e(TAG, "Bad Battery Changed intent: batteryLevel=" + batteryLevel
                                + ", scale=" + scale);
                        return;
                    }
                    int cindBatteryLevel = Math.round(batteryLevel * 5 / ((float) scale));
                    mSystemInterface.getHeadsetPhoneState().setCindBatteryCharge(cindBatteryLevel);
                    break;
                }
                case AudioManager.ACTION_VOLUME_CHANGED: {
                    int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                    if (streamType == AudioManager.STREAM_BLUETOOTH_SCO) {
                        doForEachConnectedStateMachine(stateMachine -> stateMachine.sendMessage(
                                HeadsetStateMachine.INTENT_SCO_VOLUME_CHANGED, intent));
                    }
                    break;
                }
                case BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY: {
                    int requestType = intent.getIntExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                            BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    logD("Received BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY, device=" + device
                            + ", type=" + requestType);
                    if (requestType == BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS) {
                        synchronized (mStateMachines) {
                            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
                            if (stateMachine == null) {
                                Log.wtf(TAG, "Cannot find state machine for " + device);
                                return;
                            }
                            stateMachine.sendMessage(
                                    HeadsetStateMachine.INTENT_CONNECTION_ACCESS_REPLY, intent);
                        }
                    }
                    break;
                }
                default:
                    Log.w(TAG, "Unknown action " + action);
            }
        }
    };

    public void handleBondStateChanged(BluetoothDevice device, int fromState, int toState) {
        mHandler.post(() -> bondStateChanged(device, toState));
    }

    private void bondStateChanged(BluetoothDevice device, int state) {
        logD("Bond state changed for device: " + device + " state: " + state);
        if (state != BluetoothDevice.BOND_NONE) {
            return;
        }
        synchronized (mStateMachines) {
            HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                return;
            }
            if (stateMachine.getConnectionState() != BluetoothProfile.STATE_DISCONNECTED) {
                return;
            }
            removeStateMachine(device);
        }
    }

    /**
     * Handlers for incoming service calls
     */
    @VisibleForTesting
    static class BluetoothHeadsetBinder extends IBluetoothHeadset.Stub
            implements IProfileServiceBinder {
        private volatile HeadsetService mService;

        BluetoothHeadsetBinder(HeadsetService svc) {
            mService = svc;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private HeadsetService getService(AttributionSource source) {
            if (Utils.isInstrumentationTestMode()) {
                return mService;
            }
            if (!Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                    || !Utils.checkServiceAvailable(mService, TAG)
                    || !Utils.checkConnectPermissionForDataDelivery(mService, source, TAG)) {
                return null;
            }
            return mService;
        }

        @Override
        public void connect(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
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
                HeadsetService service = getService(source);
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
        public void getConnectedDevices(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
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
                HeadsetService service = getService(source);
                List<BluetoothDevice> defaultValue = new ArrayList<BluetoothDevice>(0);
                if (service != null) {
                    defaultValue = service.getDevicesMatchingConnectionStates(states);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionState(BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                int defaultValue = BluetoothProfile.STATE_DISCONNECTED;
                if (service != null) {
                    defaultValue = service.getConnectionState(device);
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
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.setConnectionPolicy(device, connectionPolicy);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionPolicy(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                int defaultValue = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.getConnectionPolicy(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isNoiseReductionSupported(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.isNoiseReductionSupported(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isVoiceRecognitionSupported(BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.isVoiceRecognitionSupported(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void startVoiceRecognition(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.startVoiceRecognition(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void stopVoiceRecognition(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.stopVoiceRecognition(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isAudioOn(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.isAudioOn();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isAudioConnected(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.isAudioConnected(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getAudioState(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                int defaultValue = BluetoothHeadset.STATE_AUDIO_DISCONNECTED;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.getAudioState(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void connectAudio(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                int defaultValue = BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.connectAudio();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void disconnectAudio(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                int defaultValue = BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.disconnectAudio();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setAudioRouteAllowed(boolean allowed, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    service.setAudioRouteAllowed(allowed);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getAudioRouteAllowed(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.getAudioRouteAllowed();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setForceScoAudio(boolean forced, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                if (service != null) {
                    service.setForceScoAudio(forced);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void startScoUsingVirtualVoiceCall(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.startScoUsingVirtualVoiceCall();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void stopScoUsingVirtualVoiceCall(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.stopScoUsingVirtualVoiceCall();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void phoneStateChanged(int numActive, int numHeld, int callState, String number,
            int type, String name, AttributionSource source) {
            HeadsetService service = getService(source);
            if (service != null) {
                service.phoneStateChanged(numActive, numHeld, callState, number, type, name, false);
            }
        }

        @Override
        public void clccResponse(int index, int direction, int status, int mode, boolean mpty,
                String number, int type, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                if (service != null) {
                    service.clccResponse(index, direction, status, mode, mpty, number, type);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void sendVendorSpecificResultCode(BluetoothDevice device, String command,
                String arg, AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.sendVendorSpecificResultCode(device, command, arg);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setActiveDevice(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                if (service != null) {
                    if (Flags.audioRoutingCentralization()) {
                        ((AudioRoutingManager) service.mAdapterService.getActiveDeviceManager())
                                .activateDeviceProfile(device, BluetoothProfile.HEADSET, receiver);
                    } else {
                        receiver.send(service.setActiveDevice(device));
                    }
                } else {
                    receiver.send(false);
                }
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getActiveDevice(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                BluetoothDevice defaultValue = null;
                if (service != null) {
                    defaultValue = service.getActiveDevice();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isInbandRingingEnabled(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                HeadsetService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.isInbandRingingEnabled();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
    }

    // API methods
    public static synchronized HeadsetService getHeadsetService() {
        if (sHeadsetService == null) {
            Log.w(TAG, "getHeadsetService(): service is NULL");
            return null;
        }
        if (!sHeadsetService.isAvailable()) {
            Log.w(TAG, "getHeadsetService(): service is not available");
            return null;
        }
        logD("getHeadsetService(): returning " + sHeadsetService);
        return sHeadsetService;
    }

    private static synchronized void setHeadsetService(HeadsetService instance) {
        logD("setHeadsetService(): set to: " + instance);
        sHeadsetService = instance;
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean connect(BluetoothDevice device) {
        if (getConnectionPolicy(device) == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.w(TAG, "connect: CONNECTION_POLICY_FORBIDDEN, device=" + device + ", "
                    + Utils.getUidPidString());
            return false;
        }
        ParcelUuid[] featureUuids = mAdapterService.getRemoteUuids(device);
        if (!BluetoothUuid.containsAnyUuid(featureUuids, HEADSET_UUIDS)) {
            Log.e(TAG, "connect: Cannot connect to " + device + ": no headset UUID, "
                    + Utils.getUidPidString());
            return false;
        }
        synchronized (mStateMachines) {
            Log.i(TAG, "connect: device=" + device + ", " + Utils.getUidPidString());
            HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                stateMachine = HeadsetObjectsFactory.getInstance()
                        .makeStateMachine(device, mStateMachinesThread.getLooper(), this,
                                mAdapterService, mNativeInterface, mSystemInterface);
                mStateMachines.put(device, stateMachine);
            }
            int connectionState = stateMachine.getConnectionState();
            if (connectionState == BluetoothProfile.STATE_CONNECTED
                    || connectionState == BluetoothProfile.STATE_CONNECTING) {
                Log.w(TAG, "connect: device " + device
                        + " is already connected/connecting, connectionState=" + connectionState);
                return false;
            }
            List<BluetoothDevice> connectingConnectedDevices =
                    getDevicesMatchingConnectionStates(CONNECTING_CONNECTED_STATES);
            boolean disconnectExisting = false;
            if (connectingConnectedDevices.size() >= mMaxHeadsetConnections) {
                // When there is maximum one device, we automatically disconnect the current one
                if (mMaxHeadsetConnections == 1) {
                    disconnectExisting = true;
                } else {
                    Log.w(TAG, "Max connection has reached, rejecting connection to " + device);
                    return false;
                }
            }
            if (disconnectExisting) {
                for (BluetoothDevice connectingConnectedDevice : connectingConnectedDevices) {
                    disconnect(connectingConnectedDevice);
                }
                setActiveDevice(null);
            }
            stateMachine.sendMessage(HeadsetStateMachine.CONNECT, device);
        }
        return true;
    }

    /**
     * Disconnects hfp from the passed in device
     *
     * @param device is the device with which we will disconnect hfp
     * @return true if hfp is disconnected, false if the device is not connected
     */
    public boolean disconnect(BluetoothDevice device) {
        Log.i(TAG, "disconnect: device=" + device + ", " + Utils.getUidPidString());
        synchronized (mStateMachines) {
            HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                Log.w(TAG, "disconnect: device " + device + " not ever connected/connecting");
                return false;
            }
            int connectionState = stateMachine.getConnectionState();
            if (connectionState != BluetoothProfile.STATE_CONNECTED
                    && connectionState != BluetoothProfile.STATE_CONNECTING) {
                Log.w(TAG, "disconnect: device " + device
                        + " not connected/connecting, connectionState=" + connectionState);
                return false;
            }
            stateMachine.sendMessage(HeadsetStateMachine.DISCONNECT, device);
        }
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        synchronized (mStateMachines) {
            for (HeadsetStateMachine stateMachine : mStateMachines.values()) {
                if (stateMachine.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    devices.add(stateMachine.getDevice());
                }
            }
        }
        return devices;
    }

    /**
     * Same as the API method {@link BluetoothHeadset#getDevicesMatchingConnectionStates(int[])}
     *
     * @param states an array of states from {@link BluetoothProfile}
     * @return a list of devices matching the array of connection states
     */
    @VisibleForTesting
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        synchronized (mStateMachines) {
            if (states == null || mAdapterService == null) {
                return devices;
            }
            final BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
            if (bondedDevices == null) {
                return devices;
            }
            for (BluetoothDevice device : bondedDevices) {
                final ParcelUuid[] featureUuids = mAdapterService.getRemoteUuids(device);
                if (!BluetoothUuid.containsAnyUuid(featureUuids, HEADSET_UUIDS)) {
                    continue;
                }
                int connectionState = getConnectionState(device);
                for (int state : states) {
                    if (connectionState == state) {
                        devices.add(device);
                        break;
                    }
                }
            }
        }
        return devices;
    }

    public int getConnectionState(BluetoothDevice device) {
        synchronized (mStateMachines) {
            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return stateMachine.getConnectionState();
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
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean setConnectionPolicy(BluetoothDevice device, int connectionPolicy) {
        Log.i(TAG, "setConnectionPolicy: device=" + device
                + ", connectionPolicy=" + connectionPolicy + ", " + Utils.getUidPidString());

        if (!mDatabaseManager.setProfileConnectionPolicy(device, BluetoothProfile.HEADSET,
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
                .getProfileConnectionPolicy(device, BluetoothProfile.HEADSET);
    }

    boolean isNoiseReductionSupported(BluetoothDevice device) {
        return mNativeInterface.isNoiseReductionSupported(device);
    }

    boolean isVoiceRecognitionSupported(BluetoothDevice device) {
        return mNativeInterface.isVoiceRecognitionSupported(device);
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    boolean startVoiceRecognition(BluetoothDevice device) {
        Log.i(TAG, "startVoiceRecognition: device=" + device + ", " + Utils.getUidPidString());
        synchronized (mStateMachines) {
            // TODO(b/79660380): Workaround in case voice recognition was not terminated properly
            if (mVoiceRecognitionStarted) {
                boolean status = stopVoiceRecognition(mActiveDevice);
                Log.w(TAG, "startVoiceRecognition: voice recognition is still active, just called "
                        + "stopVoiceRecognition, returned " + status + " on " + mActiveDevice
                        + ", please try again");
                mVoiceRecognitionStarted = false;
                return false;
            }
            if (!isAudioModeIdle()) {
                Log.w(TAG, "startVoiceRecognition: audio mode not idle, active device is "
                        + mActiveDevice);
                return false;
            }
            // Audio should not be on when no audio mode is active
            if (isAudioOn()) {
                // Disconnect audio so that API user can try later
                int status = disconnectAudio();
                Log.w(TAG, "startVoiceRecognition: audio is still active, please wait for audio to"
                        + " be disconnected, disconnectAudio() returned " + status
                        + ", active device is " + mActiveDevice);
                return false;
            }
            if (device == null) {
                Log.i(TAG, "device is null, use active device " + mActiveDevice + " instead");
                device = mActiveDevice;
            }
            boolean pendingRequestByHeadset = false;
            if (mVoiceRecognitionTimeoutEvent != null) {
                if (!mVoiceRecognitionTimeoutEvent.mVoiceRecognitionDevice.equals(device)) {
                    // TODO(b/79660380): Workaround when target device != requesting device
                    Log.w(TAG, "startVoiceRecognition: device " + device
                            + " is not the same as requesting device "
                            + mVoiceRecognitionTimeoutEvent.mVoiceRecognitionDevice
                            + ", fall back to requesting device");
                    device = mVoiceRecognitionTimeoutEvent.mVoiceRecognitionDevice;
                }
                getStateMachinesThreadHandler().removeCallbacks(mVoiceRecognitionTimeoutEvent);
                mVoiceRecognitionTimeoutEvent = null;
                if (mSystemInterface.getVoiceRecognitionWakeLock().isHeld()) {
                    mSystemInterface.getVoiceRecognitionWakeLock().release();
                }
                pendingRequestByHeadset = true;
            }
            if (!Objects.equals(device, mActiveDevice) && !setActiveDevice(device)) {
                Log.w(TAG, "startVoiceRecognition: failed to set " + device + " as active");
                return false;
            }
            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                Log.w(TAG, "startVoiceRecognition: " + device + " is never connected");
                return false;
            }
            int connectionState = stateMachine.getConnectionState();
            if (connectionState != BluetoothProfile.STATE_CONNECTED
                    && connectionState != BluetoothProfile.STATE_CONNECTING) {
                Log.w(TAG, "startVoiceRecognition: " + device + " is not connected or connecting");
                return false;
            }
            if (SystemProperties.getBoolean(REJECT_SCO_IF_HFPC_CONNECTED_PROPERTY, false)
                    && isHeadsetClientConnected()) {
                Log.w(TAG, "startVoiceRecognition: rejected SCO since HFPC is connected!");
                return false;
            }
            mVoiceRecognitionStarted = true;
            if (pendingRequestByHeadset) {
                stateMachine.sendMessage(HeadsetStateMachine.VOICE_RECOGNITION_RESULT,
                        1 /* success */, 0, device);
            } else {
                stateMachine.sendMessage(HeadsetStateMachine.VOICE_RECOGNITION_START, device);
            }
            stateMachine.sendMessage(HeadsetStateMachine.CONNECT_AUDIO, device);
        }
        return true;
    }

    boolean stopVoiceRecognition(BluetoothDevice device) {
        Log.i(TAG, "stopVoiceRecognition: device=" + device + ", " + Utils.getUidPidString());
        synchronized (mStateMachines) {
            if (!Objects.equals(mActiveDevice, device)) {
                Log.w(TAG, "startVoiceRecognition: requested device " + device
                        + " is not active, use active device " + mActiveDevice + " instead");
                device = mActiveDevice;
            }
            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                Log.w(TAG, "stopVoiceRecognition: " + device + " is never connected");
                return false;
            }
            int connectionState = stateMachine.getConnectionState();
            if (connectionState != BluetoothProfile.STATE_CONNECTED
                    && connectionState != BluetoothProfile.STATE_CONNECTING) {
                Log.w(TAG, "stopVoiceRecognition: " + device + " is not connected or connecting");
                return false;
            }
            if (!mVoiceRecognitionStarted) {
                Log.w(TAG, "stopVoiceRecognition: voice recognition was not started");
                return false;
            }
            mVoiceRecognitionStarted = false;
            stateMachine.sendMessage(HeadsetStateMachine.VOICE_RECOGNITION_STOP, device);
            stateMachine.sendMessage(HeadsetStateMachine.DISCONNECT_AUDIO, device);
        }
        return true;
    }

    boolean isAudioOn() {
        return getNonIdleAudioDevices().size() > 0;
    }

    boolean isAudioConnected(BluetoothDevice device) {
        synchronized (mStateMachines) {
            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                return false;
            }
            return stateMachine.getAudioState() == BluetoothHeadset.STATE_AUDIO_CONNECTED;
        }
    }

    int getAudioState(BluetoothDevice device) {
        synchronized (mStateMachines) {
            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                return BluetoothHeadset.STATE_AUDIO_DISCONNECTED;
            }
            return stateMachine.getAudioState();
        }
    }

    public void setAudioRouteAllowed(boolean allowed) {
        Log.i(TAG, "setAudioRouteAllowed: allowed=" + allowed + ", " + Utils.getUidPidString());
        mAudioRouteAllowed = allowed;
        mNativeInterface.setScoAllowed(allowed);
    }

    public boolean getAudioRouteAllowed() {
        return mAudioRouteAllowed;
    }

    public void setForceScoAudio(boolean forced) {
        Log.i(TAG, "setForceScoAudio: forced=" + forced + ", " + Utils.getUidPidString());
        mForceScoAudio = forced;
    }

    @VisibleForTesting
    public boolean getForceScoAudio() {
        return mForceScoAudio;
    }

    /**
     * Get first available device for SCO audio
     *
     * @return first connected headset device
     */
    @VisibleForTesting
    @Nullable
    public BluetoothDevice getFirstConnectedAudioDevice() {
        ArrayList<HeadsetStateMachine> stateMachines = new ArrayList<>();
        synchronized (mStateMachines) {
            List<BluetoothDevice> availableDevices =
                    getDevicesMatchingConnectionStates(CONNECTING_CONNECTED_STATES);
            for (BluetoothDevice device : availableDevices) {
                final HeadsetStateMachine stateMachine = mStateMachines.get(device);
                if (stateMachine == null) {
                    continue;
                }
                stateMachines.add(stateMachine);
            }
        }
        stateMachines.sort(Comparator.comparingLong(HeadsetStateMachine::getConnectingTimestampMs));
        if (stateMachines.size() > 0) {
            return stateMachines.get(0).getDevice();
        }
        return null;
    }

    /**
     * Process a change in the silence mode for a {@link BluetoothDevice}.
     *
     * @param device the device to change silence mode
     * @param silence true to enable silence mode, false to disable.
     * @return true on success, false on error
     */
    @VisibleForTesting
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean setSilenceMode(BluetoothDevice device, boolean silence) {
        Log.d(TAG, "setSilenceMode(" + device + "): " + silence);

        if (silence && Objects.equals(mActiveDevice, device)) {
            setActiveDevice(null);
        } else if (!silence && mActiveDevice == null) {
            // Set the device as the active device if currently no active device.
            setActiveDevice(device);
        }
        synchronized (mStateMachines) {
            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                Log.w(TAG, "setSilenceMode: device " + device
                        + " was never connected/connecting");
                return false;
            }
            stateMachine.setSilenceDevice(silence);
        }

        return true;
    }

    /**
     * Get the Bluetooth Audio Policy stored in the state machine
     *
     * @param device the device to change silence mode
     * @return a {@link BluetoothSinkAudioPolicy} object
     */
    public BluetoothSinkAudioPolicy getHfpCallAudioPolicy(BluetoothDevice device) {
        synchronized (mStateMachines) {
            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                Log.e(TAG, "getHfpCallAudioPolicy(), " + device
                        + " does not have a state machine");
                return null;
            }
            return stateMachine.getHfpCallAudioPolicy();
        }
    }

    /**
     * Remove the active device
     */
    private void removeActiveDevice() {
        synchronized (mStateMachines) {
            // As per b/202602952, if we remove the active device due to a disconnection,
            // we need to check if another device is connected and set it active instead.
            // Calling this before any other active related calls has the same effect as
            // a classic active device switch.
            BluetoothDevice fallbackDevice = getFallbackDevice();
            if (fallbackDevice != null && mActiveDevice != null
                    && getConnectionState(mActiveDevice) != BluetoothProfile.STATE_CONNECTED) {
                setActiveDevice(fallbackDevice);
                return;
            }
            // Clear the active device
            if (mVoiceRecognitionStarted) {
                if (!stopVoiceRecognition(mActiveDevice)) {
                    Log.w(TAG, "removeActiveDevice: fail to stopVoiceRecognition from "
                            + mActiveDevice);
                }
            }
            if (mVirtualCallStarted) {
                if (!stopScoUsingVirtualVoiceCall()) {
                    Log.w(TAG, "removeActiveDevice: fail to stopScoUsingVirtualVoiceCall from "
                            + mActiveDevice);
                }
            }
            if (getAudioState(mActiveDevice) != BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                int disconnectStatus = disconnectAudio(mActiveDevice);
                if (disconnectStatus != BluetoothStatusCodes.SUCCESS) {
                    Log.w(TAG, "removeActiveDevice: disconnectAudio failed on " + mActiveDevice
                            + " with status code " + disconnectStatus);
                }
            }
            mActiveDevice = null;
            mNativeInterface.setActiveDevice(null);
            broadcastActiveDevice(null);
        }
    }

    /**
     * Set the active device.
     *
     * @param device the active device
     * @return true on success, otherwise false
     */
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean setActiveDevice(BluetoothDevice device) {
        Log.i(TAG, "setActiveDevice: device=" + device + ", " + Utils.getUidPidString());
        if (device == null) {
            removeActiveDevice();
            return true;
        }
        synchronized (mStateMachines) {
            if (device.equals(mActiveDevice)) {
                Log.i(TAG, "setActiveDevice: device " + device + " is already active");
                return true;
            }
            if (getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
                Log.e(TAG, "setActiveDevice: Cannot set " + device
                        + " as active, device is not connected");
                return false;
            }
            if (!mNativeInterface.setActiveDevice(device)) {
                Log.e(TAG, "setActiveDevice: Cannot set " + device + " as active in native layer");
                return false;
            }
            BluetoothDevice previousActiveDevice = mActiveDevice;
            mActiveDevice = device;
            if (getAudioState(previousActiveDevice) != BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                int disconnectStatus = disconnectAudio(previousActiveDevice);
                if (disconnectStatus != BluetoothStatusCodes.SUCCESS) {
                    Log.e(TAG, "setActiveDevice: fail to disconnectAudio from "
                            + previousActiveDevice + " with status code " + disconnectStatus);
                    if (previousActiveDevice == null) {
                        removeActiveDevice();
                    } else {
                        mActiveDevice = previousActiveDevice;
                        mNativeInterface.setActiveDevice(previousActiveDevice);
                    }
                    return false;
                }
                broadcastActiveDevice(mActiveDevice);
            } else if (shouldPersistAudio()) {
                /* If HFP is getting active for a phonecall and there is LeAudio device active,
                 * Lets inactive LeAudio device as soon as possible so there is no CISes connected
                 * when SCO is created
                 */
                LeAudioService leAudioService = mFactory.getLeAudioService();
                if (leAudioService != null) {
                    Log.i(TAG, "Make sure there is no le audio device active.");
                    leAudioService.setInactiveForHfpHandover(mActiveDevice);
                }

                broadcastActiveDevice(mActiveDevice);
                int connectStatus = connectAudio(mActiveDevice);
                if (connectStatus != BluetoothStatusCodes.SUCCESS) {
                    Log.e(TAG, "setActiveDevice: fail to connectAudio to " + mActiveDevice
                            + " with status code " + connectStatus);
                    if (previousActiveDevice == null) {
                        removeActiveDevice();
                    } else {
                        mActiveDevice = previousActiveDevice;
                        mNativeInterface.setActiveDevice(previousActiveDevice);
                    }
                    return false;
                }
            } else {
                broadcastActiveDevice(mActiveDevice);
            }
        }
        return true;
    }

    /**
     * Get the active device.
     *
     * @return the active device or null if no device is active
     */
    public BluetoothDevice getActiveDevice() {
        synchronized (mStateMachines) {
            return mActiveDevice;
        }
    }

    public int connectAudio() {
        synchronized (mStateMachines) {
            BluetoothDevice device = mActiveDevice;
            if (device == null) {
                Log.w(TAG, "connectAudio: no active device, " + Utils.getUidPidString());
                return BluetoothStatusCodes.ERROR_NO_ACTIVE_DEVICES;
            }
            return connectAudio(device);
        }
    }

    int connectAudio(BluetoothDevice device) {
        Log.i(TAG, "connectAudio: device=" + device + ", " + Utils.getUidPidString());
        synchronized (mStateMachines) {
            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                Log.w(TAG, "connectAudio: device " + device + " was never connected/connecting");
                return BluetoothStatusCodes.ERROR_PROFILE_NOT_CONNECTED;
            }
            int scoConnectionAllowedState = isScoAcceptable(device);
            if (scoConnectionAllowedState != BluetoothStatusCodes.SUCCESS) {
                Log.w(TAG, "connectAudio, rejected SCO request to " + device);
                return scoConnectionAllowedState;
            }
            if (stateMachine.getConnectionState() != BluetoothProfile.STATE_CONNECTED) {
                Log.w(TAG, "connectAudio: profile not connected");
                return BluetoothStatusCodes.ERROR_PROFILE_NOT_CONNECTED;
            }
            if (stateMachine.getAudioState() != BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                logD("connectAudio: audio is not idle for device " + device);
                return BluetoothStatusCodes.SUCCESS;
            }
            if (isAudioOn()) {
                Log.w(TAG, "connectAudio: audio is not idle, current audio devices are "
                        + Arrays.toString(getNonIdleAudioDevices().toArray()));
                return BluetoothStatusCodes.ERROR_AUDIO_DEVICE_ALREADY_CONNECTED;
            }
            stateMachine.sendMessage(HeadsetStateMachine.CONNECT_AUDIO, device);
        }
        return BluetoothStatusCodes.SUCCESS;
    }

    private List<BluetoothDevice> getNonIdleAudioDevices() {
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        synchronized (mStateMachines) {
            for (HeadsetStateMachine stateMachine : mStateMachines.values()) {
                if (stateMachine.getAudioState() != BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    devices.add(stateMachine.getDevice());
                }
            }
        }
        return devices;
    }

    int disconnectAudio() {
        int disconnectResult = BluetoothStatusCodes.ERROR_NO_ACTIVE_DEVICES;
        synchronized (mStateMachines) {
            for (BluetoothDevice device : getNonIdleAudioDevices()) {
                disconnectResult = disconnectAudio(device);
                if (disconnectResult == BluetoothStatusCodes.SUCCESS) {
                    return disconnectResult;
                } else {
                    Log.e(TAG, "disconnectAudio() from " + device + " failed with status code "
                            + disconnectResult);
                }
            }
        }
        logD("disconnectAudio() no active audio connection");
        return disconnectResult;
    }

    int disconnectAudio(BluetoothDevice device) {
        synchronized (mStateMachines) {
            Log.i(TAG, "disconnectAudio: device=" + device + ", " + Utils.getUidPidString());
            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                Log.w(TAG, "disconnectAudio: device " + device + " was never connected/connecting");
                return BluetoothStatusCodes.ERROR_PROFILE_NOT_CONNECTED;
            }
            if (stateMachine.getAudioState() == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                Log.w(TAG, "disconnectAudio, audio is already disconnected for " + device);
                return BluetoothStatusCodes.ERROR_AUDIO_DEVICE_ALREADY_DISCONNECTED;
            }
            stateMachine.sendMessage(HeadsetStateMachine.DISCONNECT_AUDIO, device);
        }
        return BluetoothStatusCodes.SUCCESS;
    }

    boolean isVirtualCallStarted() {
        synchronized (mStateMachines) {
            return mVirtualCallStarted;
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    boolean startScoUsingVirtualVoiceCall() {
        Log.i(TAG, "startScoUsingVirtualVoiceCall: " + Utils.getUidPidString());
        synchronized (mStateMachines) {
            // TODO(b/79660380): Workaround in case voice recognition was not terminated properly
            if (mVoiceRecognitionStarted) {
                boolean status = stopVoiceRecognition(mActiveDevice);
                Log.w(TAG, "startScoUsingVirtualVoiceCall: voice recognition is still active, "
                        + "just called stopVoiceRecognition, returned " + status + " on "
                        + mActiveDevice + ", please try again");
                mVoiceRecognitionStarted = false;
                return false;
            }
            if (!isAudioModeIdle()) {
                Log.w(TAG, "startScoUsingVirtualVoiceCall: audio mode not idle, active device is "
                        + mActiveDevice);
                return false;
            }
            // Audio should not be on when no audio mode is active
            if (isAudioOn()) {
                // Disconnect audio so that API user can try later
                int status = disconnectAudio();
                Log.w(TAG, "startScoUsingVirtualVoiceCall: audio is still active, please wait for "
                        + "audio to be disconnected, disconnectAudio() returned " + status
                        + ", active device is " + mActiveDevice);
                return false;
            }
            if (mActiveDevice == null) {
                Log.w(TAG, "startScoUsingVirtualVoiceCall: no active device");
                return false;
            }
            if (SystemProperties.getBoolean(REJECT_SCO_IF_HFPC_CONNECTED_PROPERTY, false)
                    && isHeadsetClientConnected()) {
                Log.w(TAG, "startScoUsingVirtualVoiceCall: rejected SCO since HFPC is connected!");
                return false;
            }
            mVirtualCallStarted = true;
            // Send virtual phone state changed to initialize SCO
            phoneStateChanged(0, 0, HeadsetHalConstants.CALL_STATE_DIALING, "", 0, "", true);
            phoneStateChanged(0, 0, HeadsetHalConstants.CALL_STATE_ALERTING, "", 0, "", true);
            phoneStateChanged(1, 0, HeadsetHalConstants.CALL_STATE_IDLE, "", 0, "", true);
            return true;
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    boolean stopScoUsingVirtualVoiceCall() {
        Log.i(TAG, "stopScoUsingVirtualVoiceCall: " + Utils.getUidPidString());
        synchronized (mStateMachines) {
            // 1. Check if virtual call has already started
            if (!mVirtualCallStarted) {
                Log.w(TAG, "stopScoUsingVirtualVoiceCall: virtual call not started");
                return false;
            }
            mVirtualCallStarted = false;
            // 2. Send virtual phone state changed to close SCO
            phoneStateChanged(0, 0, HeadsetHalConstants.CALL_STATE_IDLE, "", 0, "", true);
        }
        return true;
    }

    class DialingOutTimeoutEvent implements Runnable {
        BluetoothDevice mDialingOutDevice;

        DialingOutTimeoutEvent(BluetoothDevice fromDevice) {
            mDialingOutDevice = fromDevice;
        }

        @Override
        public void run() {
            synchronized (mStateMachines) {
                mDialingOutTimeoutEvent = null;
                doForStateMachine(mDialingOutDevice, stateMachine -> stateMachine.sendMessage(
                        HeadsetStateMachine.DIALING_OUT_RESULT, 0 /* fail */, 0,
                        mDialingOutDevice));
            }
        }

        @Override
        public String toString() {
            return "DialingOutTimeoutEvent[" + mDialingOutDevice + "]";
        }
    }

    /**
     * Dial an outgoing call as requested by the remote device
     *
     * @param fromDevice remote device that initiated this dial out action
     * @param dialNumber number to dial
     * @return true on successful dial out
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean dialOutgoingCall(BluetoothDevice fromDevice, String dialNumber) {
        synchronized (mStateMachines) {
            Log.i(TAG, "dialOutgoingCall: from " + fromDevice);
            if (!isOnStateMachineThread()) {
                Log.e(TAG, "dialOutgoingCall must be called from state machine thread");
                return false;
            }
            if (mDialingOutTimeoutEvent != null) {
                Log.e(TAG, "dialOutgoingCall, already dialing by " + mDialingOutTimeoutEvent);
                return false;
            }
            if (isVirtualCallStarted()) {
                if (!stopScoUsingVirtualVoiceCall()) {
                    Log.e(TAG, "dialOutgoingCall failed to stop current virtual call");
                    return false;
                }
            }
            if (!setActiveDevice(fromDevice)) {
                Log.e(TAG, "dialOutgoingCall failed to set active device to " + fromDevice);
                return false;
            }
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                    Uri.fromParts(PhoneAccount.SCHEME_TEL, dialNumber, null));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            mDialingOutTimeoutEvent = new DialingOutTimeoutEvent(fromDevice);
            getStateMachinesThreadHandler()
                    .postDelayed(mDialingOutTimeoutEvent, DIALING_OUT_TIMEOUT_MS);
            return true;
        }
    }

    /**
     * Check if any connected headset has started dialing calls
     *
     * @return true if some device has started dialing calls
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean hasDeviceInitiatedDialingOut() {
        synchronized (mStateMachines) {
            return mDialingOutTimeoutEvent != null;
        }
    }

    class VoiceRecognitionTimeoutEvent implements Runnable {
        BluetoothDevice mVoiceRecognitionDevice;

        VoiceRecognitionTimeoutEvent(BluetoothDevice device) {
            mVoiceRecognitionDevice = device;
        }

        @Override
        public void run() {
            synchronized (mStateMachines) {
                if (mSystemInterface.getVoiceRecognitionWakeLock().isHeld()) {
                    mSystemInterface.getVoiceRecognitionWakeLock().release();
                }
                mVoiceRecognitionTimeoutEvent = null;
                doForStateMachine(mVoiceRecognitionDevice, stateMachine -> stateMachine.sendMessage(
                        HeadsetStateMachine.VOICE_RECOGNITION_RESULT, 0 /* fail */, 0,
                        mVoiceRecognitionDevice));
            }
        }

        @Override
        public String toString() {
            return "VoiceRecognitionTimeoutEvent[" + mVoiceRecognitionDevice + "]";
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    boolean startVoiceRecognitionByHeadset(BluetoothDevice fromDevice) {
        synchronized (mStateMachines) {
            Log.i(TAG, "startVoiceRecognitionByHeadset: from " + fromDevice);
            // TODO(b/79660380): Workaround in case voice recognition was not terminated properly
            if (mVoiceRecognitionStarted) {
                boolean status = stopVoiceRecognition(mActiveDevice);
                Log.w(TAG, "startVoiceRecognitionByHeadset: voice recognition is still active, "
                        + "just called stopVoiceRecognition, returned " + status + " on "
                        + mActiveDevice + ", please try again");
                mVoiceRecognitionStarted = false;
                return false;
            }
            if (fromDevice == null) {
                Log.e(TAG, "startVoiceRecognitionByHeadset: fromDevice is null");
                return false;
            }
            if (!isAudioModeIdle()) {
                Log.w(TAG, "startVoiceRecognitionByHeadset: audio mode not idle, active device is "
                        + mActiveDevice);
                return false;
            }
            // Audio should not be on when no audio mode is active
            if (isAudioOn()) {
                // Disconnect audio so that user can try later
                int status = disconnectAudio();
                Log.w(TAG, "startVoiceRecognitionByHeadset: audio is still active, please wait for"
                        + " audio to be disconnected, disconnectAudio() returned " + status
                        + ", active device is " + mActiveDevice);
                return false;
            }
            // Do not start new request until the current one is finished or timeout
            if (mVoiceRecognitionTimeoutEvent != null) {
                Log.w(TAG, "startVoiceRecognitionByHeadset: failed request from " + fromDevice
                        + ", already pending by " + mVoiceRecognitionTimeoutEvent);
                return false;
            }
            if (!setActiveDevice(fromDevice)) {
                Log.w(TAG, "startVoiceRecognitionByHeadset: failed to set " + fromDevice
                        + " as active");
                return false;
            }
            if (!mSystemInterface.activateVoiceRecognition()) {
                Log.w(TAG, "startVoiceRecognitionByHeadset: failed request from " + fromDevice);
                return false;
            }
            if (SystemProperties.getBoolean(REJECT_SCO_IF_HFPC_CONNECTED_PROPERTY, false)
                    && isHeadsetClientConnected()) {
                Log.w(TAG, "startVoiceRecognitionByHeadset: rejected SCO since HFPC is connected!");
                return false;
            }
            mVoiceRecognitionTimeoutEvent = new VoiceRecognitionTimeoutEvent(fromDevice);
            getStateMachinesThreadHandler()
                    .postDelayed(mVoiceRecognitionTimeoutEvent, sStartVrTimeoutMs);

            if (!mSystemInterface.getVoiceRecognitionWakeLock().isHeld()) {
                mSystemInterface.getVoiceRecognitionWakeLock().acquire(sStartVrTimeoutMs);
            }
            return true;
        }
    }

    boolean stopVoiceRecognitionByHeadset(BluetoothDevice fromDevice) {
        synchronized (mStateMachines) {
            Log.i(TAG, "stopVoiceRecognitionByHeadset: from " + fromDevice);
            if (!Objects.equals(fromDevice, mActiveDevice)) {
                Log.w(TAG, "stopVoiceRecognitionByHeadset: " + fromDevice
                        + " is not active, active device is " + mActiveDevice);
                return false;
            }
            if (!mVoiceRecognitionStarted && mVoiceRecognitionTimeoutEvent == null) {
                Log.w(TAG, "stopVoiceRecognitionByHeadset: voice recognition not started, device="
                        + fromDevice);
                return false;
            }
            if (mVoiceRecognitionTimeoutEvent != null) {
                if (mSystemInterface.getVoiceRecognitionWakeLock().isHeld()) {
                    mSystemInterface.getVoiceRecognitionWakeLock().release();
                }
                getStateMachinesThreadHandler()
                        .removeCallbacks(mVoiceRecognitionTimeoutEvent);

                mVoiceRecognitionTimeoutEvent = null;
            }
            if (mVoiceRecognitionStarted) {
                int disconnectStatus = disconnectAudio();
                if (disconnectStatus != BluetoothStatusCodes.SUCCESS) {
                    Log.w(TAG, "stopVoiceRecognitionByHeadset: failed to disconnect audio from "
                            + fromDevice + " with status code " + disconnectStatus);
                }
                mVoiceRecognitionStarted = false;
            }
            if (!mSystemInterface.deactivateVoiceRecognition()) {
                Log.w(TAG, "stopVoiceRecognitionByHeadset: failed request from " + fromDevice);
                return false;
            }
            return true;
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    void phoneStateChanged(int numActive, int numHeld, int callState, String number,
            int type, String name, boolean isVirtualCall) {
        enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, "Need MODIFY_PHONE_STATE permission");
        synchronized (mStateMachines) {
            // Should stop all other audio mode in this case
            if ((numActive + numHeld) > 0 || callState != HeadsetHalConstants.CALL_STATE_IDLE) {
                if (!isVirtualCall && mVirtualCallStarted) {
                    // stop virtual voice call if there is an incoming Telecom call update
                    stopScoUsingVirtualVoiceCall();
                }
                if (mVoiceRecognitionStarted) {
                    // stop voice recognition if there is any incoming call
                    stopVoiceRecognition(mActiveDevice);
                }
            }
            if (mDialingOutTimeoutEvent != null) {
                // Send result to state machine when dialing starts
                if (callState == HeadsetHalConstants.CALL_STATE_DIALING) {
                    getStateMachinesThreadHandler()
                            .removeCallbacks(mDialingOutTimeoutEvent);
                    doForStateMachine(mDialingOutTimeoutEvent.mDialingOutDevice,
                            stateMachine -> stateMachine.sendMessage(
                                    HeadsetStateMachine.DIALING_OUT_RESULT, 1 /* success */, 0,
                                    mDialingOutTimeoutEvent.mDialingOutDevice));
                } else if (callState == HeadsetHalConstants.CALL_STATE_ACTIVE
                        || callState == HeadsetHalConstants.CALL_STATE_IDLE) {
                    // Clear the timeout event when the call is connected or disconnected
                    if (!getStateMachinesThreadHandler()
                            .hasCallbacks(mDialingOutTimeoutEvent)) {
                        mDialingOutTimeoutEvent = null;
                    }
                }
            }
        }
        getStateMachinesThreadHandler().post(() -> {
            boolean isCallIdleBefore = mSystemInterface.isCallIdle();
            mSystemInterface.getHeadsetPhoneState().setNumActiveCall(numActive);
            mSystemInterface.getHeadsetPhoneState().setNumHeldCall(numHeld);
            mSystemInterface.getHeadsetPhoneState().setCallState(callState);
            // Suspend A2DP when call about is about to become active
            if (mActiveDevice != null && callState != HeadsetHalConstants.CALL_STATE_DISCONNECTED
                    && !mSystemInterface.isCallIdle() && isCallIdleBefore) {
                mSystemInterface.getAudioManager().setA2dpSuspended(true);
                if (isAtLeastU()) {
                    mSystemInterface.getAudioManager().setLeAudioSuspended(true);
                }
            }
        });
        doForEachConnectedStateMachine(
                stateMachine -> stateMachine.sendMessage(HeadsetStateMachine.CALL_STATE_CHANGED,
                        new HeadsetCallState(numActive, numHeld, callState, number, type, name)));
        getStateMachinesThreadHandler().post(() -> {
            if (callState == HeadsetHalConstants.CALL_STATE_IDLE
                    && mSystemInterface.isCallIdle() && !isAudioOn()) {
                // Resume A2DP when call ended and SCO is not connected
                mSystemInterface.getAudioManager().setA2dpSuspended(false);
                if (isAtLeastU()) {
                    mSystemInterface.getAudioManager().setLeAudioSuspended(false);
                }
            }
        });
        if (callState == HeadsetHalConstants.CALL_STATE_IDLE) {
            final HeadsetStateMachine stateMachine = mStateMachines.get(mActiveDevice);
            if (stateMachine == null) {
                Log.d(TAG, "phoneStateChanged: CALL_STATE_IDLE, mActiveDevice is Null");
            } else {
                BluetoothSinkAudioPolicy currentPolicy = stateMachine.getHfpCallAudioPolicy();
                if (currentPolicy != null && currentPolicy.getActiveDevicePolicyAfterConnection()
                        == BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED) {
                    /**
                     * If the active device was set because of the pick up audio policy
                     * and the connecting policy is NOT_ALLOWED, then after the call is
                     * terminated, we must de-activate this device.
                     * If there is a fallback mechanism, we should follow it.
                     */
                    removeActiveDevice();
                }
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    void clccResponse(int index, int direction, int status, int mode, boolean mpty,
            String number, int type) {
        enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, "Need MODIFY_PHONE_STATE permission");
        mPendingClccResponses.add(
                stateMachine -> stateMachine.sendMessage(HeadsetStateMachine.SEND_CLCC_RESPONSE,
                        new HeadsetClccResponse(index, direction, status, mode, mpty, number,
                                type)));
        if (index == CLCC_END_MARK_INDEX) {
            doForEachConnectedStateMachine(mPendingClccResponses);
            mPendingClccResponses.clear();
        }
    }

    private boolean sendVendorSpecificResultCode(BluetoothDevice device, String command,
            String arg) {
        synchronized (mStateMachines) {
            final HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                Log.w(TAG, "sendVendorSpecificResultCode: device " + device
                        + " was never connected/connecting");
                return false;
            }
            int connectionState = stateMachine.getConnectionState();
            if (connectionState != BluetoothProfile.STATE_CONNECTED) {
                return false;
            }
            // Currently we support only "+ANDROID".
            if (!command.equals(BluetoothHeadset.VENDOR_RESULT_CODE_COMMAND_ANDROID)) {
                Log.w(TAG, "Disallowed unsolicited result code command: " + command);
                return false;
            }
            stateMachine.sendMessage(HeadsetStateMachine.SEND_VENDOR_SPECIFIC_RESULT_CODE,
                    new HeadsetVendorSpecificResultCode(device, command, arg));
        }
        return true;
    }

    /**
     * Checks if headset devices are able to get inband ringing.
     *
     * @return True if inband ringing is enabled.
     */
    public boolean isInbandRingingEnabled() {
        boolean isInbandRingingSupported = getResources().getBoolean(
                com.android.bluetooth.R.bool.config_bluetooth_hfp_inband_ringing_support);

        boolean inbandRingtoneAllowedByPolicy = true;
        List<BluetoothDevice> audioConnectableDevices = getConnectedDevices();
        if (audioConnectableDevices.size() == 1) {
            BluetoothDevice connectedDevice = audioConnectableDevices.get(0);
            BluetoothSinkAudioPolicy callAudioPolicy =
                    getHfpCallAudioPolicy(connectedDevice);
            if (callAudioPolicy != null && callAudioPolicy.getInBandRingtonePolicy()
                    == BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED) {
                inbandRingtoneAllowedByPolicy = false;
            }
        }

        return isInbandRingingSupported && !SystemProperties.getBoolean(
                DISABLE_INBAND_RINGING_PROPERTY, false)
                && !mInbandRingingRuntimeDisable
                && inbandRingtoneAllowedByPolicy
                && !isHeadsetClientConnected();
    }

    private boolean isHeadsetClientConnected() {
        HeadsetClientService headsetClientService = HeadsetClientService.getHeadsetClientService();
        if (headsetClientService == null) {
            return false;
        }
        return !(headsetClientService.getConnectedDevices().isEmpty());
    }

    /**
     * Called from {@link HeadsetStateMachine} in state machine thread when there is a connection
     * state change
     *
     * @param device remote device
     * @param fromState from which connection state is the change
     * @param toState to which connection state is the change
     */
    @VisibleForTesting
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public void onConnectionStateChangedFromStateMachine(BluetoothDevice device, int fromState,
            int toState) {
        if (fromState != BluetoothProfile.STATE_CONNECTED
                && toState == BluetoothProfile.STATE_CONNECTED) {
            updateInbandRinging(device, true);
            MetricsLogger.logProfileConnectionEvent(BluetoothMetricsProto.ProfileId.HEADSET);
        }
        if (fromState != BluetoothProfile.STATE_DISCONNECTED
                && toState == BluetoothProfile.STATE_DISCONNECTED) {
            updateInbandRinging(device, false);
            if (device.equals(mActiveDevice)) {
                setActiveDevice(null);
            }
        }

        mAdapterService
                .getActiveDeviceManager()
                .profileConnectionStateChanged(BluetoothProfile.HEADSET, device, fromState,
                    toState);
        mAdapterService
                .getSilenceDeviceManager()
                .hfpConnectionStateChanged(device, fromState, toState);
        mAdapterService
                .getRemoteDevices()
                .handleHeadsetConnectionStateChanged(device, fromState, toState);
        mAdapterService.notifyProfileConnectionStateChangeToGatt(
                BluetoothProfile.HEADSET, fromState, toState);
        mAdapterService.handleProfileConnectionStateChange(
                BluetoothProfile.HEADSET, device, fromState, toState);
        mAdapterService.updateProfileConnectionAdapterProperties(
                device, BluetoothProfile.HEADSET, toState, fromState);
    }

    /**
     * Called from {@link HeadsetClientStateMachine} to update inband ringing status.
     */
    public void updateInbandRinging(BluetoothDevice device, boolean connected) {
        synchronized (mStateMachines) {
            List<BluetoothDevice> audioConnectableDevices = getConnectedDevices();
            final int enabled;
            final boolean inbandRingingRuntimeDisable = mInbandRingingRuntimeDisable;

            if (audioConnectableDevices.size() > 1 || isHeadsetClientConnected()) {
                mInbandRingingRuntimeDisable = true;
                enabled = 0;
            } else {
                mInbandRingingRuntimeDisable = false;
                enabled = 1;
            }

            final boolean updateAll = inbandRingingRuntimeDisable != mInbandRingingRuntimeDisable;

            Log.i(TAG, "updateInbandRinging():"
                    + " Device=" + device
                    + " enabled=" + enabled
                    + " connected=" + connected
                    + " Update all=" + updateAll);

            StateMachineTask sendBsirTask = stateMachine -> stateMachine
                            .sendMessage(HeadsetStateMachine.SEND_BSIR, enabled);

            if (updateAll) {
                doForEachConnectedStateMachine(sendBsirTask);
            } else if (connected) {
                // Same Inband ringing status, send +BSIR only to the new connected device
                doForStateMachine(device, sendBsirTask);
            }
        }
    }

    /**
     * Check if no audio mode is active
     *
     * @return false if virtual call, voice recognition, or Telecom call is active, true if all idle
     */
    private boolean isAudioModeIdle() {
        synchronized (mStateMachines) {
            if (mVoiceRecognitionStarted || mVirtualCallStarted || !mSystemInterface.isCallIdle()) {
                Log.i(TAG, "isAudioModeIdle: not idle, mVoiceRecognitionStarted="
                        + mVoiceRecognitionStarted + ", mVirtualCallStarted=" + mVirtualCallStarted
                        + ", isCallIdle=" + mSystemInterface.isCallIdle());
                return false;
            }
            return true;
        }
    }

    private boolean shouldCallAudioBeActive() {
        return mSystemInterface.isInCall() || (mSystemInterface.isRinging()
                && isInbandRingingEnabled());
    }

    /**
     * Only persist audio during active device switch when call audio is supposed to be active and
     * virtual call has not been started. Virtual call is ignored because AudioService and
     * applications should reconnect SCO during active device switch and forcing SCO connection
     * here will make AudioService think SCO is started externally instead of by one of its SCO
     * clients.
     *
     * @return true if call audio should be active and no virtual call is going on
     */
    private boolean shouldPersistAudio() {
        return !mVirtualCallStarted && shouldCallAudioBeActive();
    }

    /**
     * Called from {@link HeadsetStateMachine} in state machine thread when there is a audio
     * connection state change
     *
     * @param device remote device
     * @param fromState from which audio connection state is the change
     * @param toState to which audio connection state is the change
     */
    @VisibleForTesting
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public void onAudioStateChangedFromStateMachine(BluetoothDevice device, int fromState,
            int toState) {
        synchronized (mStateMachines) {
            if (toState == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                if (fromState != BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    if (mActiveDevice != null && !mActiveDevice.equals(device)
                            && shouldPersistAudio()) {
                        int connectStatus = connectAudio(mActiveDevice);
                        if (connectStatus != BluetoothStatusCodes.SUCCESS) {
                            Log.w(TAG, "onAudioStateChangedFromStateMachine, failed to connect"
                                    + " audio to new " + "active device " + mActiveDevice
                                    + ", after " + device + " is disconnected from SCO due to"
                                    + " status code " + connectStatus);
                        }
                    }
                }
                if (mVoiceRecognitionStarted) {
                    if (!stopVoiceRecognitionByHeadset(device)) {
                        Log.w(TAG, "onAudioStateChangedFromStateMachine: failed to stop voice "
                                + "recognition");
                    }
                }
                if (mVirtualCallStarted) {
                    if (!stopScoUsingVirtualVoiceCall()) {
                        Log.w(TAG, "onAudioStateChangedFromStateMachine: failed to stop virtual "
                                + "voice call");
                    }
                }
                // Unsuspend A2DP when SCO connection is gone and call state is idle
                if (mSystemInterface.isCallIdle()) {
                    mSystemInterface.getAudioManager().setA2dpSuspended(false);
                    if (isAtLeastU()) {
                        mSystemInterface.getAudioManager().setLeAudioSuspended(false);
                    }
                }
            }
        }
    }

    private void broadcastActiveDevice(BluetoothDevice device) {
        logD("broadcastActiveDevice: " + device);

        mAdapterService.handleActiveDeviceChange(BluetoothProfile.HEADSET, device);

        BluetoothStatsLog.write(
                BluetoothStatsLog.BLUETOOTH_ACTIVE_DEVICE_CHANGED,
                BluetoothProfile.HEADSET,
                mAdapterService.obfuscateAddress(device),
                mAdapterService.getMetricId(device));

        Intent intent = new Intent(BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_CONNECT,
                Utils.getTempAllowlistBroadcastOptions());
    }

    /**
     * Check whether it is OK to accept a headset connection from a remote device
     *
     * @param device remote device that initiates the connection
     * @return true if the connection is acceptable
     */
    public boolean okToAcceptConnection(BluetoothDevice device, boolean isOutgoingRequest) {
        // Check if this is an incoming connection in Quiet mode.
        if (mAdapterService.isQuietModeEnabled()) {
            Log.w(TAG, "okToAcceptConnection: return false as quiet mode enabled");
            return false;
        }
        // Check connection policy and accept or reject the connection.
        int connectionPolicy = getConnectionPolicy(device);
        int bondState = mAdapterService.getBondState(device);
        // Allow this connection only if the device is bonded. Any attempt to connect while
        // bonding would potentially lead to an unauthorized connection.
        if (bondState != BluetoothDevice.BOND_BONDED) {
            Log.w(TAG, "okToAcceptConnection: return false, bondState=" + bondState);
            return false;
        } else if (connectionPolicy != BluetoothProfile.CONNECTION_POLICY_UNKNOWN
                && connectionPolicy != BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            // Otherwise, reject the connection if connection policy is not valid.
            if (!isOutgoingRequest) {
                A2dpService a2dpService = A2dpService.getA2dpService();
                if (a2dpService != null && a2dpService.okToConnect(device, true)) {
                    Log.d(TAG, "okToAcceptConnection: return false,"
                            + " Fallback connection to allowed A2DP profile");
                    a2dpService.connect(device);
                    return false;
                }
            }
            Log.w(TAG, "okToAcceptConnection: return false, connectionPolicy=" + connectionPolicy);
            return false;
        }
        List<BluetoothDevice> connectingConnectedDevices =
                getDevicesMatchingConnectionStates(CONNECTING_CONNECTED_STATES);
        if (connectingConnectedDevices.size() >= mMaxHeadsetConnections) {
            Log.w(TAG, "Maximum number of connections " + mMaxHeadsetConnections
                    + " was reached, rejecting connection from " + device);
            return false;
        }
        return true;
    }

    /**
     * Checks if SCO should be connected at current system state. Returns
     * {@link BluetoothStatusCodes#SUCCESS} if SCO is allowed to be connected or an error code on
     * failure.
     *
     * @param device device for SCO to be connected
     * @return whether SCO can be connected
     */
    public int isScoAcceptable(BluetoothDevice device) {
        synchronized (mStateMachines) {
            if (device == null || !device.equals(mActiveDevice)) {
                Log.w(TAG, "isScoAcceptable: rejected SCO since " + device
                        + " is not the current active device " + mActiveDevice);
                return BluetoothStatusCodes.ERROR_NOT_ACTIVE_DEVICE;
            }
            if (SystemProperties.getBoolean(REJECT_SCO_IF_HFPC_CONNECTED_PROPERTY, false)
                    && isHeadsetClientConnected()) {
                Log.w(TAG, "isScoAcceptable: rejected SCO since HFPC is connected!");
                return BluetoothStatusCodes.ERROR_AUDIO_ROUTE_BLOCKED;
            }
            if (mForceScoAudio) {
                return BluetoothStatusCodes.SUCCESS;
            }
            if (!mAudioRouteAllowed) {
                Log.w(TAG, "isScoAcceptable: rejected SCO since audio route is not allowed");
                return BluetoothStatusCodes.ERROR_AUDIO_ROUTE_BLOCKED;
            }
            if (mVoiceRecognitionStarted || mVirtualCallStarted) {
                return BluetoothStatusCodes.SUCCESS;
            }
            if (shouldCallAudioBeActive()) {
                return BluetoothStatusCodes.SUCCESS;
            }
            Log.w(TAG, "isScoAcceptable: rejected SCO, inCall=" + mSystemInterface.isInCall()
                    + ", voiceRecognition=" + mVoiceRecognitionStarted + ", ringing="
                    + mSystemInterface.isRinging() + ", inbandRinging=" + isInbandRingingEnabled()
                    + ", isVirtualCallStarted=" + mVirtualCallStarted);
            return BluetoothStatusCodes.ERROR_CALL_ACTIVE;
        }
    }

    /**
     * Remove state machine in {@link #mStateMachines} for a {@link BluetoothDevice}
     *
     * @param device device whose state machine is to be removed.
     */
    void removeStateMachine(BluetoothDevice device) {
        synchronized (mStateMachines) {
            HeadsetStateMachine stateMachine = mStateMachines.get(device);
            if (stateMachine == null) {
                Log.w(TAG, "removeStateMachine(), " + device + " does not have a state machine");
                return;
            }
            Log.i(TAG, "removeStateMachine(), removing state machine for device: " + device);
            HeadsetObjectsFactory.getInstance().destroyStateMachine(stateMachine);
            mStateMachines.remove(device);
        }
    }

    private boolean isOnStateMachineThread() {
        final Looper myLooper = Looper.myLooper();
        return myLooper != null && (mStateMachinesThread != null) && (myLooper.getThread().getId()
                == mStateMachinesThread.getId());
    }

    /**
     * Retrieves the most recently connected device in the A2DP connected devices list.
     */
    public BluetoothDevice getFallbackDevice() {
        DatabaseManager dbManager = mAdapterService.getDatabase();
        return dbManager != null ? dbManager
            .getMostRecentlyConnectedDevicesInList(getFallbackCandidates(dbManager))
            : null;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    List<BluetoothDevice> getFallbackCandidates(DatabaseManager dbManager) {
        List<BluetoothDevice> fallbackCandidates = getConnectedDevices();
        List<BluetoothDevice> uninterestedCandidates = new ArrayList<>();
        for (BluetoothDevice device : fallbackCandidates) {
            byte[] deviceType = dbManager.getCustomMeta(device,
                    BluetoothDevice.METADATA_DEVICE_TYPE);
            BluetoothClass deviceClass = device.getBluetoothClass();
            if ((deviceClass != null
                    && deviceClass.getMajorDeviceClass()
                    == BluetoothClass.Device.WEARABLE_WRIST_WATCH)
                    || (deviceType != null
                    && BluetoothDevice.DEVICE_TYPE_WATCH.equals(new String(deviceType)))) {
                uninterestedCandidates.add(device);
            }
        }
        for (BluetoothDevice device : uninterestedCandidates) {
            fallbackCandidates.remove(device);
        }
        return fallbackCandidates;
    }

    @Override
    public void dump(StringBuilder sb) {
        boolean isScoOn = mSystemInterface.getAudioManager().isBluetoothScoOn();
        boolean isInbandRingingSupported = getResources().getBoolean(
                com.android.bluetooth.R.bool.config_bluetooth_hfp_inband_ringing_support);
        synchronized (mStateMachines) {
            super.dump(sb);
            ProfileService.println(sb, "mMaxHeadsetConnections: " + mMaxHeadsetConnections);
            ProfileService.println(sb, "DefaultMaxHeadsetConnections: "
                    + mAdapterService.getMaxConnectedAudioDevices());
            ProfileService.println(sb, "mActiveDevice: " + mActiveDevice);
            ProfileService.println(sb, "isInbandRingingEnabled: " + isInbandRingingEnabled());
            ProfileService.println(sb,
                    "isInbandRingingSupported: " + isInbandRingingSupported);
            ProfileService.println(sb,
                    "mInbandRingingRuntimeDisable: " + mInbandRingingRuntimeDisable);
            ProfileService.println(sb, "mAudioRouteAllowed: " + mAudioRouteAllowed);
            ProfileService.println(sb, "mVoiceRecognitionStarted: " + mVoiceRecognitionStarted);
            ProfileService.println(sb,
                    "mVoiceRecognitionTimeoutEvent: " + mVoiceRecognitionTimeoutEvent);
            ProfileService.println(sb, "mVirtualCallStarted: " + mVirtualCallStarted);
            ProfileService.println(sb, "mDialingOutTimeoutEvent: " + mDialingOutTimeoutEvent);
            ProfileService.println(sb, "mForceScoAudio: " + mForceScoAudio);
            ProfileService.println(sb, "mStarted: " + mStarted);
            ProfileService.println(sb, "AudioManager.isBluetoothScoOn(): " + isScoOn);
            ProfileService.println(sb, "Telecom.isInCall(): " + mSystemInterface.isInCall());
            ProfileService.println(sb, "Telecom.isRinging(): " + mSystemInterface.isRinging());
            for (HeadsetStateMachine stateMachine : mStateMachines.values()) {
                ProfileService.println(sb,
                        "==== StateMachine for " + stateMachine.getDevice() + " ====");
                stateMachine.dump(sb);
            }
        }
    }

    private static void logD(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }

    private Handler getStateMachinesThreadHandler() {
        if (mStateMachinesThreadHandler == null) {
            mStateMachinesThreadHandler = new Handler(mStateMachinesThread.getLooper());
        }
        return mStateMachinesThreadHandler;
    }
}
