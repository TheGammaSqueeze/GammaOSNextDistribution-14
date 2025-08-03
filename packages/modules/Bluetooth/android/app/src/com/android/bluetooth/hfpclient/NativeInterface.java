/*
 * Copyright (c) 2017 The Android Open Source Project
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

/*
 * Defines the native inteface that is used by state machine/service to either or receive messages
 * from the native stack. This file is registered for the native methods in corresponding CPP file.
 */
package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.flags.Flags;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Defines native calls that are used by state machine/service to either send or receive
 * messages to/from the native stack. This file is registered for the native methods in
 * corresponding CPP file.
 */
public class NativeInterface {
    private static final String TAG = "NativeInterface";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private AdapterService mAdapterService;

    @GuardedBy("INSTANCE_LOCK")
    private static NativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private NativeInterface() {
        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when NativeInterface init");
    }

    /**
     * This class is a singleton because native library should only be loaded once
     *
     * @return default instance
     */
    public static NativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new NativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(NativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    // Native wrappers to help unit testing
    /**
     * Initialize native stack
     */
    @VisibleForTesting
    public void initialize() {
        initializeNative();
    }

    /**
     * Close and clean up native stack
     */
    @VisibleForTesting
    public void cleanup() {
        cleanupNative();
    }

    /**
     * Connect to the specified paired device
     *
     * @param device target device
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean connect(BluetoothDevice device) {
        return connectNative(getByteAddress(device));
    }

    /**
     * Disconnect from the specified paired device
     *
     * @param device target device
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean disconnect(BluetoothDevice device) {
        return disconnectNative(getByteAddress(device));
    }

    /**
     * Initiate audio connection to the specified paired device
     *
     * @param device target device
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean connectAudio(BluetoothDevice device) {
        return connectAudioNative(getByteAddress(device));
    }

    /**
     * Close audio connection from the specified paired device
     *
     * @param device target device
     * @return True on success, False on failure
     */
    public boolean disconnectAudio(BluetoothDevice device) {
        return disconnectAudioNative(getByteAddress(device));
    }

    /**
     * Initiate voice recognition to the specified paired device
     *
     * @param device target device
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean startVoiceRecognition(BluetoothDevice device) {
        return startVoiceRecognitionNative(getByteAddress(device));
    }

    /**
     * Close voice recognition to the specified paired device
     *
     * @param device target device
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean stopVoiceRecognition(BluetoothDevice device) {
        return stopVoiceRecognitionNative(getByteAddress(device));
    }

    /**
     * Set volume to the specified paired device
     *
     * @param device target device
     * @param volumeType type of volume as in
     *                  HeadsetClientHalConstants.VOLUME_TYPE_xxxx
     * @param volume  volume level
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean setVolume(BluetoothDevice device, int volumeType, int volume) {
        return setVolumeNative(getByteAddress(device), volumeType, volume);
    }

    /**
     * dial number from the specified paired device
     *
     * @param device target device
     * @param number  phone number to be dialed
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean dial(BluetoothDevice device, String number) {
        return dialNative(getByteAddress(device), number);
    }

    /**
     * Memory dialing from the specified paired device
     *
     * @param device target device
     * @param location  memory location
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean dialMemory(BluetoothDevice device, int location) {
        return dialMemoryNative(getByteAddress(device), location);
    }

    /**
     * Apply action to call
     *
     * @param device target device
     * @param action action (e.g. hold, terminate etc)
     * @param index call index
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean handleCallAction(BluetoothDevice device, int action, int index) {
        return handleCallActionNative(getByteAddress(device), action, index);
    }

    /**
     * Query current call status from the specified paired device
     *
     * @param device target device
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean queryCurrentCalls(BluetoothDevice device) {
        return queryCurrentCallsNative(getByteAddress(device));
    }

    /**
     * Query operator name from the specified paired device
     *
     * @param device target device
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean queryCurrentOperatorName(BluetoothDevice device) {
        return queryCurrentOperatorNameNative(getByteAddress(device));
    }

    /**
     * Retrieve subscriber number from the specified paired device
     *
     * @param device target device
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public  boolean retrieveSubscriberInfo(BluetoothDevice device) {
        return retrieveSubscriberInfoNative(getByteAddress(device));
    }

    /**
     * Transmit DTMF code
     *
     * @param device target device
     * @param code DTMF code
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean sendDtmf(BluetoothDevice device, byte code) {
        return sendDtmfNative(getByteAddress(device), code);
    }

    /**
     * Request last voice tag
     *
     * @param device target device
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean requestLastVoiceTagNumber(BluetoothDevice device) {
        return requestLastVoiceTagNumberNative(getByteAddress(device));
    }

    /**
     * Send an AT command
     *
     * @param device target device
     * @param atCmd command code
     * @param val1 command specific argurment1
     * @param val2 command specific argurment2
     * @param arg other command specific argurments
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean sendATCmd(BluetoothDevice device, int atCmd, int val1, int val2, String arg) {
        return sendATCmdNative(getByteAddress(device), atCmd, val1, val2, arg);
    }

    /**
     * Set call audio policy to the specified paired device
     *
     * @param cmd Android specific command string
     * @return True on success, False on failure
     */
    @VisibleForTesting
    public boolean sendAndroidAt(BluetoothDevice device, String cmd) {
        if (device == null) {
            Log.w(TAG, "Don't need to send " + cmd + " because no remote device");
            return false;
        }
        return sendAndroidAtNative(getByteAddress(device), cmd);
    }

    /**********************************************************************************************/
    /******************************************* native *******************************************/
    /**********************************************************************************************/

    private native void initializeNative();

    private native void cleanupNative();

    private static native boolean connectNative(byte[] address);

    private static native boolean disconnectNative(byte[] address);

    private static native boolean connectAudioNative(byte[] address);

    private static native boolean disconnectAudioNative(byte[] address);

    private static native boolean startVoiceRecognitionNative(byte[] address);

    private static native boolean stopVoiceRecognitionNative(byte[] address);

    private static native boolean setVolumeNative(byte[] address, int volumeType, int volume);

    private static native boolean dialNative(byte[] address, String number);

    private static native boolean dialMemoryNative(byte[] address, int location);

    private static native boolean handleCallActionNative(byte[] address, int action, int index);

    private static native boolean queryCurrentCallsNative(byte[] address);

    private static native boolean queryCurrentOperatorNameNative(byte[] address);

    private static native boolean retrieveSubscriberInfoNative(byte[] address);

    private static native boolean sendDtmfNative(byte[] address, byte code);

    private static native boolean requestLastVoiceTagNumberNative(byte[] address);

    private static native boolean sendATCmdNative(byte[] address, int atCmd, int val1, int val2,
            String arg);

    private static native boolean sendAndroidAtNative(byte[] address, String cmd);

    private BluetoothDevice getDevice(byte[] address) {
        return mAdapterService.getDeviceFromByte(address);
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        if (Flags.identityAddressNullIfUnknown()) {
            return Utils.getByteBrEdrAddress(device);
        } else {
            return mAdapterService.getByteIdentityAddress(device);
        }
    }

    // Callbacks from the native back into the java framework. All callbacks are routed via the
    // Service which will disambiguate which state machine the message should be routed through.
    @VisibleForTesting
    void onConnectionStateChanged(int state, int peerFeat, int chldFeat, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt = state;
        event.valueInt2 = peerFeat;
        event.valueInt3 = chldFeat;
        event.device = getDevice(address);
        // BluetoothAdapter.getDefaultAdapter().getRemoteDevice(Utils.getAddressStringFromByte
        // (address));
        if (DBG) {
            Log.d(TAG, "Device addr " + event.device + " State " + state);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onAudioStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onAudioStateChanged: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onAudioStateChanged: Ignoring message because service not available: "
                    + event);
        }
    }

    @VisibleForTesting
    void onVrStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_VR_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onVrStateChanged: event " + event);
        }

        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG,
                    "onVrStateChanged: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onNetworkState(int state, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_NETWORK_STATE);
        event.valueInt = state;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onNetworkStateChanged: event " + event);
        }

        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG,
                    "onNetworkStateChanged: Ignoring message because service not available: "
                            + event);
        }
    }

    @VisibleForTesting
    void onNetworkRoaming(int state, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_ROAMING_STATE);
        event.valueInt = state;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onNetworkRoaming: incoming: " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG,
                    "onNetworkRoaming: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onNetworkSignal(int signal, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_NETWORK_SIGNAL);
        event.valueInt = signal;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onNetworkSignal: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onNetworkSignal: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onBatteryLevel(int level, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_BATTERY_LEVEL);
        event.valueInt = level;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onBatteryLevel: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onBatteryLevel: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onCurrentOperator(String name, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_OPERATOR_NAME);
        event.valueString = name;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCurrentOperator: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG,
                    "onCurrentOperator: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onCall(int call, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CALL);
        event.valueInt = call;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCall: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCall: Ignoring message because service not available: " + event);
        }
    }

    /**
     * CIEV (Call indicators) notifying if call(s) are getting set up.
     *
     * Values include:
     * 0 - No current call is in setup
     * 1 - Incoming call process ongoing
     * 2 - Outgoing call process ongoing
     * 3 - Remote party being alerted for outgoing call
     */
    @VisibleForTesting
    void onCallSetup(int callsetup, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CALLSETUP);
        event.valueInt = callsetup;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCallSetup: device" + event.device);
            Log.d(TAG, "onCallSetup: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCallSetup: Ignoring message because service not available: " + event);
        }
    }

    /**
     * CIEV (Call indicators) notifying call held states.
     *
     * Values include:
     * 0 - No calls held
     * 1 - Call is placed on hold or active/held calls wapped (The AG has both an ACTIVE and HELD
     * call)
     * 2 - Call on hold, no active call
     */
    @VisibleForTesting
    void onCallHeld(int callheld, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CALLHELD);
        event.valueInt = callheld;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCallHeld: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCallHeld: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onRespAndHold(int respAndHold, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_RESP_AND_HOLD);
        event.valueInt = respAndHold;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onRespAndHold: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onRespAndHold: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onClip(String number, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CLIP);
        event.valueString = number;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onClip: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onClip: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onCallWaiting(String number, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CALL_WAITING);
        event.valueString = number;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCallWaiting: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCallWaiting: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onCurrentCalls(int index, int dir, int state, int mparty, String number,
            byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CURRENT_CALLS);
        event.valueInt = index;
        event.valueInt2 = dir;
        event.valueInt3 = state;
        event.valueInt4 = mparty;
        event.valueString = number;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCurrentCalls: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCurrentCalls: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onVolumeChange(int type, int volume, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_VOLUME_CHANGED);
        event.valueInt = type;
        event.valueInt2 = volume;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onVolumeChange: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onVolumeChange: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onCmdResult(int type, int cme, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CMD_RESULT);
        event.valueInt = type;
        event.valueInt2 = cme;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCmdResult: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCmdResult: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onSubscriberInfo(String number, int type, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_SUBSCRIBER_INFO);
        event.valueInt = type;
        event.valueString = number;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onSubscriberInfo: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG,
                    "onSubscriberInfo: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onInBandRing(int inBand, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_IN_BAND_RINGTONE);
        event.valueInt = inBand;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onInBandRing: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG,
                    "onInBandRing: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onLastVoiceTagNumber(String number, byte[] address) {
        Log.w(TAG, "onLastVoiceTagNumber not supported");
    }

    @VisibleForTesting
    void onRingIndication(byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_RING_INDICATION);
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onRingIndication: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG,
                    "onRingIndication: Ignoring message because service not available: " + event);
        }
    }

    @VisibleForTesting
    void onUnknownEvent(String eventString, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_UNKNOWN_EVENT);
        event.device = getDevice(address);
        event.valueString = eventString;
        if (DBG) {
            Log.d(TAG, "onUnknownEvent: event " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG,
                    "onUnknowEvent: Ignoring message because service not available: " + event);
        }
    }

}
