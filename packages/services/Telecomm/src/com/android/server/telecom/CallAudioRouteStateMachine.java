/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom;


import android.app.ActivityManager;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.IAudioService;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.CallAudioState;
import android.telecom.Log;
import android.telecom.Logging.Session;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.SomeArgs;
import com.android.internal.util.IState;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;
import com.android.server.telecom.flags.FeatureFlags;

import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * This class describes the available routes of a call as a state machine.
 * Transitions are caused solely by the commands sent as messages. Possible values for msg.what
 * are defined as event constants in this file.
 *
 * The eight states are all instances of the abstract base class, {@link AudioState}. Each state
 * is a combination of one of the four audio routes (earpiece, wired headset, bluetooth, and
 * speakerphone) and audio focus status (active or quiescent).
 *
 * Messages are processed first by the processMessage method in the base class, AudioState.
 * Any messages not completely handled by AudioState are further processed by the same method in
 * the route-specific abstract classes: {@link EarpieceRoute}, {@link HeadsetRoute},
 * {@link BluetoothRoute}, and {@link SpeakerRoute}. Finally, messages that are not handled at
 * this level are then processed by the classes corresponding to the state instances themselves.
 *
 * There are several variables carrying additional state. These include:
 * mAvailableRoutes: A bitmask describing which audio routes are available
 * mWasOnSpeaker: A boolean indicating whether we should switch to speakerphone after disconnecting
 *     from a wired headset
 * mIsMuted: a boolean indicating whether the audio is muted
 */
public class CallAudioRouteStateMachine extends StateMachine implements CallAudioRouteAdapter {

    public static class Factory {
        public CallAudioRouteStateMachine create(
                Context context,
                CallsManager callsManager,
                BluetoothRouteManager bluetoothManager,
                WiredHeadsetManager wiredHeadsetManager,
                StatusBarNotifier statusBarNotifier,
                CallAudioManager.AudioServiceFactory audioServiceFactory,
                int earpieceControl,
                Executor asyncTaskExecutor,
                CallAudioCommunicationDeviceTracker communicationDeviceTracker,
                FeatureFlags featureFlags) {
            return new CallAudioRouteStateMachine(context,
                    callsManager,
                    bluetoothManager,
                    wiredHeadsetManager,
                    statusBarNotifier,
                    audioServiceFactory,
                    earpieceControl,
                    asyncTaskExecutor,
                    communicationDeviceTracker,
                    featureFlags);
        }
    }
    /** Values for CallAudioRouteStateMachine constructor's earPieceRouting arg. */
    public static final int EARPIECE_FORCE_DISABLED = 0;
    public static final int EARPIECE_FORCE_ENABLED  = 1;
    public static final int EARPIECE_AUTO_DETECT    = 2;

    /** Direct the audio stream through the device's earpiece. */
    public static final int ROUTE_EARPIECE      = CallAudioState.ROUTE_EARPIECE;

    /** Direct the audio stream through Bluetooth. */
    public static final int ROUTE_BLUETOOTH     = CallAudioState.ROUTE_BLUETOOTH;

    /** Direct the audio stream through a wired headset. */
    public static final int ROUTE_WIRED_HEADSET = CallAudioState.ROUTE_WIRED_HEADSET;

    /** Direct the audio stream through the device's speakerphone. */
    public static final int ROUTE_SPEAKER       = CallAudioState.ROUTE_SPEAKER;

    /** Direct the audio stream through another device. */
    public static final int ROUTE_STREAMING     = CallAudioState.ROUTE_STREAMING;

    /** Valid values for the first argument for SWITCH_BASELINE_ROUTE */
    public static final int NO_INCLUDE_BLUETOOTH_IN_BASELINE = 0;
    public static final int INCLUDE_BLUETOOTH_IN_BASELINE = 1;

    @VisibleForTesting
    public static final SparseArray<String> AUDIO_ROUTE_TO_LOG_EVENT = new SparseArray<String>() {{
        put(CallAudioState.ROUTE_BLUETOOTH, LogUtils.Events.AUDIO_ROUTE_BT);
        put(CallAudioState.ROUTE_EARPIECE, LogUtils.Events.AUDIO_ROUTE_EARPIECE);
        put(CallAudioState.ROUTE_SPEAKER, LogUtils.Events.AUDIO_ROUTE_SPEAKER);
        put(CallAudioState.ROUTE_WIRED_HEADSET, LogUtils.Events.AUDIO_ROUTE_HEADSET);
    }};

    private static final String ACTIVE_EARPIECE_ROUTE_NAME = "ActiveEarpieceRoute";
    private static final String ACTIVE_BLUETOOTH_ROUTE_NAME = "ActiveBluetoothRoute";
    private static final String ACTIVE_SPEAKER_ROUTE_NAME = "ActiveSpeakerRoute";
    private static final String ACTIVE_HEADSET_ROUTE_NAME = "ActiveHeadsetRoute";
    private static final String RINGING_BLUETOOTH_ROUTE_NAME = "RingingBluetoothRoute";
    private static final String QUIESCENT_EARPIECE_ROUTE_NAME = "QuiescentEarpieceRoute";
    private static final String QUIESCENT_BLUETOOTH_ROUTE_NAME = "QuiescentBluetoothRoute";
    private static final String QUIESCENT_SPEAKER_ROUTE_NAME = "QuiescentSpeakerRoute";
    private static final String QUIESCENT_HEADSET_ROUTE_NAME = "QuiescentHeadsetRoute";

    public static final String NAME = CallAudioRouteStateMachine.class.getName();

    @Override
    protected void onPreHandleMessage(Message msg) {
        if (msg.obj != null && msg.obj instanceof SomeArgs) {
            Session session = (Session) ((SomeArgs) msg.obj).arg1;
            String messageCodeName = MESSAGE_CODE_TO_NAME.get(msg.what, "unknown");
            Log.continueSession(session, "CARSM.pM_" + messageCodeName);
            Log.i(this, "Message received: %s=%d, arg1=%d", messageCodeName, msg.what, msg.arg1);
        }
    }

    @Override
    protected void onPostHandleMessage(Message msg) {
        Log.endSession();
        if (msg.obj != null && msg.obj instanceof SomeArgs) {
            ((SomeArgs) msg.obj).recycle();
        }
    }

    abstract class AudioState extends State {
        @Override
        public void enter() {
            super.enter();
            Log.addEvent(mCallsManager.getForegroundCall(), LogUtils.Events.AUDIO_ROUTE,
                    "Entering state " + getName());
            if (isActive()) {
                Log.addEvent(mCallsManager.getForegroundCall(),
                        AUDIO_ROUTE_TO_LOG_EVENT.get(getRouteCode(), LogUtils.Events.AUDIO_ROUTE));
            }
        }

        @Override
        public void exit() {
            Log.addEvent(mCallsManager.getForegroundCall(), LogUtils.Events.AUDIO_ROUTE,
                    "Leaving state " + getName());
            super.exit();
        }

        @Override
        public boolean processMessage(Message msg) {
            int addedRoutes = 0;
            int removedRoutes = 0;
            boolean isHandled = NOT_HANDLED;

            Log.i(this, "Processing message %s",
                    MESSAGE_CODE_TO_NAME.get(msg.what, Integer.toString(msg.what)));
            switch (msg.what) {
                case CONNECT_WIRED_HEADSET:
                    Log.addEvent(mCallsManager.getForegroundCall(), LogUtils.Events.AUDIO_ROUTE,
                            "Wired headset connected");
                    removedRoutes |= ROUTE_EARPIECE;
                    addedRoutes |= ROUTE_WIRED_HEADSET;
                    break;
                case DISCONNECT_WIRED_HEADSET:
                    Log.addEvent(mCallsManager.getForegroundCall(), LogUtils.Events.AUDIO_ROUTE,
                            "Wired headset disconnected");
                    removedRoutes |= ROUTE_WIRED_HEADSET;
                    if (mDoesDeviceSupportEarpieceRoute) {
                        addedRoutes |= ROUTE_EARPIECE;
                    }
                    break;
                case BT_ACTIVE_DEVICE_PRESENT:
                    Log.addEvent(mCallsManager.getForegroundCall(), LogUtils.Events.AUDIO_ROUTE,
                            "Bluetooth active device present");
                    break;
                case BT_ACTIVE_DEVICE_GONE:
                    Log.addEvent(mCallsManager.getForegroundCall(), LogUtils.Events.AUDIO_ROUTE,
                            "Bluetooth active device gone");
                    break;
                case BLUETOOTH_DEVICE_LIST_CHANGED:
                    Log.addEvent(mCallsManager.getForegroundCall(), LogUtils.Events.AUDIO_ROUTE,
                            "Bluetooth device list changed");
                    Collection<BluetoothDevice> connectedDevices =
                            mBluetoothRouteManager.getConnectedDevices();
                    if (connectedDevices.size() > 0) {
                        addedRoutes |= ROUTE_BLUETOOTH;
                    } else {
                        removedRoutes |= ROUTE_BLUETOOTH;
                    }
                    isHandled = HANDLED;
                    break;
                case SWITCH_BASELINE_ROUTE:
                    sendInternalMessage(calculateBaselineRouteMessage(false,
                            msg.arg1 == INCLUDE_BLUETOOTH_IN_BASELINE));
                    return HANDLED;
                case USER_SWITCH_BASELINE_ROUTE:
                    sendInternalMessage(calculateBaselineRouteMessage(true,
                            msg.arg1 == INCLUDE_BLUETOOTH_IN_BASELINE));
                    return HANDLED;
                case USER_SWITCH_BLUETOOTH:
                    // If the user tries to switch to BT, reset the explicitly-switched-away flag.
                    mHasUserExplicitlyLeftBluetooth = false;
                    return NOT_HANDLED;
                case SWITCH_FOCUS:
                    // Perform BT hearing aid active device caching/restoration
                    if (mAudioFocusType != NO_FOCUS && msg.arg1 == NO_FOCUS) {
                        mBluetoothRouteManager.restoreHearingAidDevice();
                    } else if (mAudioFocusType == NO_FOCUS && msg.arg1 != NO_FOCUS) {
                        mBluetoothRouteManager.cacheHearingAidDevice();
                    }
                    mAudioFocusType = msg.arg1;
                    return NOT_HANDLED;
                default:
                    return NOT_HANDLED;
            }

            if (addedRoutes != 0 || removedRoutes != 0
                    || msg.what == BLUETOOTH_DEVICE_LIST_CHANGED) {
                mAvailableRoutes = modifyRoutes(mAvailableRoutes, removedRoutes, addedRoutes, true);
                mDeviceSupportedRoutes = modifyRoutes(mDeviceSupportedRoutes, removedRoutes,
                        addedRoutes, false);
                updateSystemAudioState();
            }

            return isHandled;
        }

        // Behavior will depend on whether the state is an active one or a quiescent one.
        abstract public void updateSystemAudioState();
        abstract public boolean isActive();
        abstract public int getRouteCode();
    }

    class ActiveEarpieceRoute extends EarpieceRoute {
        @Override
        public String getName() {
            return ACTIVE_EARPIECE_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void enter() {
            super.enter();
            setSpeakerphoneOn(false);
            if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
                mCommunicationDeviceTracker.setCommunicationDevice(
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, null);
            }
            CallAudioState newState = new CallAudioState(mIsMuted, ROUTE_EARPIECE,
                    mAvailableRoutes, null,
                    mBluetoothRouteManager.getConnectedDevices());
            setSystemAudioState(newState, true);
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                case SPEAKER_OFF:
                    // Nothing to do here
                    return HANDLED;
                case BT_AUDIO_CONNECTED:
                    transitionTo(mActiveBluetoothRoute);
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
                            mCommunicationDeviceTracker.clearCommunicationDevice(
                                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
                        }
                        if (mAudioFocusType == ACTIVE_FOCUS
                                || mBluetoothRouteManager.isInbandRingingEnabled()) {
                            String address = (msg.obj instanceof SomeArgs) ?
                                    (String) ((SomeArgs) msg.obj).arg2 : null;
                            // Omit transition to ActiveBluetoothRoute
                            setBluetoothOn(address);
                        } else {
                            transitionTo(mRingingBluetoothRoute);
                        }
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
                            mCommunicationDeviceTracker.clearCommunicationDevice(
                                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
                        }
                        transitionTo(mActiveHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case CONNECT_DOCK:
                    // fall through; we want to switch to speaker mode when docked and in a call.
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
                        mCommunicationDeviceTracker.clearCommunicationDevice(
                                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
                    }
                    setSpeakerphoneOn(true);
                    // fall through
                case SPEAKER_ON:
                    transitionTo(mActiveSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == NO_FOCUS) {
                        reinitialize();
                        mCallAudioManager.notifyAudioOperationsComplete();
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QuiescentEarpieceRoute extends EarpieceRoute {
        @Override
        public String getName() {
            return QUIESCENT_EARPIECE_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enter() {
            super.enter();
            mHasUserExplicitlyLeftBluetooth = false;
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                case SPEAKER_ON:
                    // Ignore speakerphone state changes outside of calls.
                case SPEAKER_OFF:
                    // Nothing to do here
                    return HANDLED;
                case BT_AUDIO_CONNECTED:
                    Log.w(this, "BT Audio came on in quiescent earpiece route.");
                    transitionTo(mActiveBluetoothRoute);
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        transitionTo(mQuiescentBluetoothRoute);
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mQuiescentHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case CONNECT_DOCK:
                    // fall through; we want to go to the quiescent speaker route when out of a call
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    transitionTo(mQuiescentSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == ACTIVE_FOCUS || msg.arg1 == RINGING_FOCUS) {
                        transitionTo(mActiveEarpieceRoute);
                    } else {
                        mCallAudioManager.notifyAudioOperationsComplete();
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    abstract class EarpieceRoute extends AudioState {
        @Override
        public int getRouteCode() {
            return CallAudioState.ROUTE_EARPIECE;
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case CONNECT_WIRED_HEADSET:
                    sendInternalMessage(SWITCH_HEADSET);
                    return HANDLED;
                case BT_ACTIVE_DEVICE_PRESENT:
                    if (!mHasUserExplicitlyLeftBluetooth) {
                        sendInternalMessage(SWITCH_BLUETOOTH);
                    } else {
                        Log.i(this, "Not switching to BT route from earpiece because user has " +
                                "explicitly disconnected.");
                    }
                    return HANDLED;
                case BT_ACTIVE_DEVICE_GONE:
                    // No change in audio route required
                    return HANDLED;
                case DISCONNECT_WIRED_HEADSET:
                    Log.e(this, new IllegalStateException(),
                            "Wired headset should not go from connected to not when on " +
                            "earpiece");
                    return HANDLED;
                case BT_AUDIO_DISCONNECTED:
                    // This may be sent as a confirmation by the BT stack after switch off BT.
                    return HANDLED;
                case DISCONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                case STREAMING_FORCE_ENABLED:
                    transitionTo(mStreamingState);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class ActiveHeadsetRoute extends HeadsetRoute {
        @Override
        public String getName() {
            return ACTIVE_HEADSET_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void enter() {
            super.enter();
            setSpeakerphoneOn(false);
            if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
                mCommunicationDeviceTracker.setCommunicationDevice(
                        AudioDeviceInfo.TYPE_WIRED_HEADSET, null);
            }
            CallAudioState newState = new CallAudioState(mIsMuted, ROUTE_WIRED_HEADSET,
                    mAvailableRoutes, null, mBluetoothRouteManager.getConnectedDevices());
            setSystemAudioState(newState, true);
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
                            mCommunicationDeviceTracker.clearCommunicationDevice(
                                    AudioDeviceInfo.TYPE_WIRED_HEADSET);
                        }
                        transitionTo(mActiveEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case BT_AUDIO_CONNECTED:
                    transitionTo(mActiveBluetoothRoute);
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        if (mAudioFocusType == ACTIVE_FOCUS
                                || mBluetoothRouteManager.isInbandRingingEnabled()) {
                            String address = (msg.obj instanceof SomeArgs) ?
                                    (String) ((SomeArgs) msg.obj).arg2 : null;
                            if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
                                mCommunicationDeviceTracker.clearCommunicationDevice(
                                        AudioDeviceInfo.TYPE_WIRED_HEADSET);
                            }
                            // Omit transition to ActiveBluetoothRoute until actual connection.
                            setBluetoothOn(address);
                        } else {
                            transitionTo(mRingingBluetoothRoute);
                        }
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                case SPEAKER_OFF:
                    // Nothing to do
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
                        mCommunicationDeviceTracker.clearCommunicationDevice(
                                AudioDeviceInfo.TYPE_WIRED_HEADSET);
                    }
                    setSpeakerphoneOn(true);
                    // fall through
                case SPEAKER_ON:
                    transitionTo(mActiveSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == NO_FOCUS) {
                        reinitialize();
                        mCallAudioManager.notifyAudioOperationsComplete();
                    }
                    return HANDLED;
                case STREAMING_FORCE_ENABLED:
                    transitionTo(mStreamingState);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QuiescentHeadsetRoute extends HeadsetRoute {
        @Override
        public String getName() {
            return QUIESCENT_HEADSET_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enter() {
            super.enter();
            mHasUserExplicitlyLeftBluetooth = false;
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mQuiescentEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case BT_AUDIO_CONNECTED:
                    transitionTo(mActiveBluetoothRoute);
                    Log.w(this, "BT Audio came on in quiescent headset route.");
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        transitionTo(mQuiescentBluetoothRoute);
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                case SPEAKER_ON:
                    // Ignore speakerphone state changes outside of calls.
                case SPEAKER_OFF:
                    // Nothing to do
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    transitionTo(mQuiescentSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == ACTIVE_FOCUS || msg.arg1 == RINGING_FOCUS) {
                        transitionTo(mActiveHeadsetRoute);
                    } else {
                        mCallAudioManager.notifyAudioOperationsComplete();
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    abstract class HeadsetRoute extends AudioState {
        @Override
        public int getRouteCode() {
            return CallAudioState.ROUTE_WIRED_HEADSET;
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case CONNECT_WIRED_HEADSET:
                    Log.e(this, new IllegalStateException(),
                            "Wired headset should already be connected.");
                    return HANDLED;
                case BT_ACTIVE_DEVICE_PRESENT:
                    if (!mHasUserExplicitlyLeftBluetooth) {
                        sendInternalMessage(SWITCH_BLUETOOTH);
                    } else {
                        Log.i(this, "Not switching to BT route from headset because user has " +
                                "explicitly disconnected.");
                    }
                    return HANDLED;
                case BT_ACTIVE_DEVICE_GONE:
                    // No change in audio route required
                    return HANDLED;
                case DISCONNECT_WIRED_HEADSET:
                    if (mWasOnSpeaker) {
                        setSpeakerphoneOn(true);
                        sendInternalMessage(SWITCH_SPEAKER);
                    } else {
                        sendInternalMessage(SWITCH_BASELINE_ROUTE, INCLUDE_BLUETOOTH_IN_BASELINE);
                    }
                    return HANDLED;
                case BT_AUDIO_DISCONNECTED:
                    // This may be sent as a confirmation by the BT stack after switch off BT.
                    return HANDLED;
                case CONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                case DISCONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    // Note: transitions to/from this class work a bit differently -- we delegate to
    // BluetoothRouteManager to manage all Bluetooth state, so instead of transitioning to one of
    // the bluetooth states immediately when there's an request to do so, we wait for
    // BluetoothRouteManager to report its state before we go into this state.
    class ActiveBluetoothRoute extends BluetoothRoute {
        @Override
        public String getName() {
            return ACTIVE_BLUETOOTH_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void enter() {
            super.enter();
            setSpeakerphoneOn(false);
            // Try arbitrarily connecting to BT audio if we haven't already. This handles
            // the edge case of when the audio route is in a quiescent route while in-call and
            // the BT connection fails to be set. Previously, the logic was to setBluetoothOn in
            // ACTIVE_FOCUS but the route would still remain in a quiescent route, so instead we
            // should be transitioning directly into the active route.
            if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
                setBluetoothOn(null);
            }
            if (mFeatureFlags.updateRouteMaskWhenBtConnected()) {
                mAvailableRoutes |= ROUTE_BLUETOOTH;
            }
            CallAudioState newState = new CallAudioState(mIsMuted, ROUTE_BLUETOOTH,
                    mAvailableRoutes, mBluetoothRouteManager.getBluetoothAudioConnectedDevice(),
                    mBluetoothRouteManager.getConnectedDevices());
            setSystemAudioState(newState, true);
            updateInternalCallAudioState();
            // Do not send RINGER_MODE_CHANGE if no Bluetooth SCO audio device is available
            if (mBluetoothRouteManager.getBluetoothAudioConnectedDevice() != null) {
                mCallAudioManager.onRingerModeChange();
            }
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public void handleBtInitiatedDisconnect() {
            // There's special-case state transitioning here -- if BT tells us that
            // something got disconnected, we don't want to disconnect BT before
            // transitioning, since BT might be trying to connect another device in the
            // meantime.
            int command = calculateBaselineRouteMessage(false, false);
            switch (command) {
                case SWITCH_EARPIECE:
                    transitionTo(mActiveEarpieceRoute);
                    break;
                case SWITCH_HEADSET:
                    transitionTo(mActiveHeadsetRoute);
                    break;
                case SWITCH_SPEAKER:
                    setSpeakerphoneOn(true);
                    transitionTo(mActiveSpeakerRoute);
                    break;
                default:
                    Log.w(this, "Got unexpected code " + command + " when processing a"
                            + " BT-initiated audio disconnect");
                    // Some fallback logic to make sure we make it off the bluetooth route.
                    super.handleBtInitiatedDisconnect();
                    break;
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case USER_SWITCH_EARPIECE:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        setBluetoothOff();
                        transitionTo(mActiveEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case BT_AUDIO_CONNECTED:
                    // Send ringer mode change because we transit to ActiveBluetoothState even
                    // when HFP is connecting
                    mCallAudioManager.onRingerModeChange();
                    // Update the in-call app on the new active BT device in case that changed.
                    updateSystemAudioState();
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    String address = (msg.obj instanceof SomeArgs) ?
                            (String) ((SomeArgs) msg.obj).arg2 : null;
                    setBluetoothOn(address);
                    return HANDLED;
                case USER_SWITCH_HEADSET:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        setBluetoothOff();
                        transitionTo(mActiveHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case USER_SWITCH_SPEAKER:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_SPEAKER:
                    setSpeakerphoneOn(true);
                    // fall through
                case SPEAKER_ON:
                    setBluetoothOff();
                    transitionTo(mActiveSpeakerRoute);
                    return HANDLED;
                case SPEAKER_OFF:
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == NO_FOCUS) {
                        // Only disconnect audio here instead of routing away from BT entirely.
                        if (mFeatureFlags.transitRouteBeforeAudioDisconnectBt()) {
                            transitionTo(mQuiescentBluetoothRoute);
                            mBluetoothRouteManager.disconnectAudio();
                        } else {
                            mBluetoothRouteManager.disconnectAudio();
                            reinitialize();
                        }
                        mCallAudioManager.notifyAudioOperationsComplete();
                    } else if (msg.arg1 == RINGING_FOCUS
                            && !mBluetoothRouteManager.isInbandRingingEnabled()) {
                        setBluetoothOff();
                        transitionTo(mRingingBluetoothRoute);
                    }
                    return HANDLED;
                case BT_AUDIO_DISCONNECTED:
                    handleBtInitiatedDisconnect();
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    // This state is only used when the device doesn't support in-band ring. If it does,
    // ActiveBluetoothRoute is used instead.
    class RingingBluetoothRoute extends BluetoothRoute {
        @Override
        public String getName() {
            return RINGING_BLUETOOTH_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enter() {
            super.enter();
            setSpeakerphoneOn(false);
            // Do not enable SCO audio here, since RING is being sent to the headset.
            CallAudioState newState = new CallAudioState(mIsMuted, ROUTE_BLUETOOTH,
                    mAvailableRoutes, mBluetoothRouteManager.getBluetoothAudioConnectedDevice(),
                    mBluetoothRouteManager.getConnectedDevices());
            setSystemAudioState(newState);
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case USER_SWITCH_EARPIECE:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mActiveEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case BT_AUDIO_CONNECTED:
                    transitionTo(mActiveBluetoothRoute);
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    // Nothing to do
                    return HANDLED;
                case USER_SWITCH_HEADSET:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mActiveHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case USER_SWITCH_SPEAKER:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_SPEAKER:
                    setSpeakerphoneOn(true);
                    // fall through
                case SPEAKER_ON:
                    transitionTo(mActiveSpeakerRoute);
                    return HANDLED;
                case SPEAKER_OFF:
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == NO_FOCUS) {
                        reinitialize();
                        mCallAudioManager.notifyAudioOperationsComplete();
                    } else if (msg.arg1 == ACTIVE_FOCUS) {
                        setBluetoothOn(null);
                    }
                    return HANDLED;
                case BT_AUDIO_DISCONNECTED:
                    // Ignore this -- audio disconnecting while ringing w/o in-band should not
                    // cause a route switch, since the device is still connected.
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QuiescentBluetoothRoute extends BluetoothRoute {
        @Override
        public String getName() {
            return QUIESCENT_BLUETOOTH_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enter() {
            super.enter();
            mHasUserExplicitlyLeftBluetooth = false;
            if (mFeatureFlags.resetMuteWhenEnteringQuiescentBtRoute()) {
                setMuteOn(false);
            }
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mQuiescentEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case BT_AUDIO_CONNECTED:
                    transitionTo(mActiveBluetoothRoute);
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                case SPEAKER_ON:
                    // Ignore speakerphone state changes outside of calls.
                case SPEAKER_OFF:
                    // Nothing to do
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mQuiescentHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    transitionTo(mQuiescentSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == ACTIVE_FOCUS) {
                        // It is possible that the connection to BT will fail while in-call, in
                        // which case, we want to transition into the active route.
                        if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
                            transitionTo(mActiveBluetoothRoute);
                        } else {
                            setBluetoothOn(null);
                        }
                    } else if (msg.arg1 == RINGING_FOCUS) {
                        if (mBluetoothRouteManager.isInbandRingingEnabled()) {
                            setBluetoothOn(null);
                        } else {
                            transitionTo(mRingingBluetoothRoute);
                        }
                    } else {
                        mCallAudioManager.notifyAudioOperationsComplete();
                    }
                    return HANDLED;
                case BT_AUDIO_DISCONNECTED:
                    // Ignore this -- audio disconnecting while quiescent should not cause a
                    // route switch, since the device is still connected.
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    abstract class BluetoothRoute extends AudioState {
        @Override
        public int getRouteCode() {
            return CallAudioState.ROUTE_BLUETOOTH;
        }

        public void handleBtInitiatedDisconnect() {
            sendInternalMessage(SWITCH_BASELINE_ROUTE, NO_INCLUDE_BLUETOOTH_IN_BASELINE);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case CONNECT_WIRED_HEADSET:
                    sendInternalMessage(SWITCH_HEADSET);
                    return HANDLED;
                case BT_ACTIVE_DEVICE_PRESENT:
                    Log.w(this, "Bluetooth active device should not"
                            + " have been null while we were in BT route.");
                    return HANDLED;
                case BT_ACTIVE_DEVICE_GONE:
                    handleBtInitiatedDisconnect();
                    mWasOnSpeaker = false;
                    return HANDLED;
                case DISCONNECT_WIRED_HEADSET:
                    // No change in audio route required
                    return HANDLED;
                case CONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                case DISCONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                case STREAMING_FORCE_ENABLED:
                    transitionTo(mStreamingState);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class ActiveSpeakerRoute extends SpeakerRoute {
        @Override
        public String getName() {
            return ACTIVE_SPEAKER_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void enter() {
            super.enter();
            // Don't set speakerphone on here -- we might end up in this state by following
            // the speaker state that some other app commanded.
            mWasOnSpeaker = true;
            CallAudioState newState = new CallAudioState(mIsMuted, ROUTE_SPEAKER,
                    mAvailableRoutes, null, mBluetoothRouteManager.getConnectedDevices());
            setSystemAudioState(newState, true);
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch(msg.what) {
                case USER_SWITCH_EARPIECE:
                    mWasOnSpeaker = false;
                    // fall through
                case SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mActiveEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case BT_AUDIO_CONNECTED:
                    transitionTo(mActiveBluetoothRoute);
                    return HANDLED;
                case USER_SWITCH_BLUETOOTH:
                    mWasOnSpeaker = false;
                    // fall through
                case SWITCH_BLUETOOTH:
                    String address = (msg.obj instanceof SomeArgs) ?
                            (String) ((SomeArgs) msg.obj).arg2 : null;
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        if (mAudioFocusType == ACTIVE_FOCUS
                                || mBluetoothRouteManager.isInbandRingingEnabled()) {
                            // Omit transition to ActiveBluetoothRoute
                            setBluetoothOn(address);
                        } else {
                            transitionTo(mRingingBluetoothRoute);
                        }
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case USER_SWITCH_HEADSET:
                    mWasOnSpeaker = false;
                    // fall through
                case SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mActiveHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    // Nothing to do
                    return HANDLED;
                case SPEAKER_ON:
                    // Expected, since we just transitioned here
                    return HANDLED;
                case SPEAKER_OFF:
                    // Check if we already requested to connect to other devices and just waiting
                    // for their response. In some cases, this SPEAKER_OFF message may come in
                    // before the response, we can just ignore the message here to not re-evaluate
                    // the baseline route incorrectly
                    if (!mBluetoothRouteManager.isBluetoothAudioConnectedOrPending()) {
                        sendInternalMessage(SWITCH_BASELINE_ROUTE, INCLUDE_BLUETOOTH_IN_BASELINE);
                    }
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == NO_FOCUS) {
                        reinitialize();
                        mCallAudioManager.notifyAudioOperationsComplete();
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QuiescentSpeakerRoute extends SpeakerRoute {
        @Override
        public String getName() {
            return QUIESCENT_SPEAKER_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enter() {
            super.enter();
            mHasUserExplicitlyLeftBluetooth = false;
            // Omit setting mWasOnSpeaker to true here, since this does not reflect a call
            // actually being on speakerphone.
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch(msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mQuiescentEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case BT_AUDIO_CONNECTED:
                    transitionTo(mActiveBluetoothRoute);
                    Log.w(this, "BT audio reported as connected while in quiescent speaker");
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        transitionTo(mQuiescentBluetoothRoute);
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mQuiescentHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                case SPEAKER_ON:
                    // Nothing to do
                    return HANDLED;
                case DISCONNECT_DOCK:
                    // Fall-through; same as if speaker goes off, we want to switch baseline.
                case SPEAKER_OFF:
                    sendInternalMessage(SWITCH_BASELINE_ROUTE, INCLUDE_BLUETOOTH_IN_BASELINE);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == ACTIVE_FOCUS || msg.arg1 == RINGING_FOCUS) {
                        setSpeakerphoneOn(true);
                        transitionTo(mActiveSpeakerRoute);
                    } else {
                        mCallAudioManager.notifyAudioOperationsComplete();
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    abstract class SpeakerRoute extends AudioState {
        @Override
        public int getRouteCode() {
            return CallAudioState.ROUTE_SPEAKER;
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case CONNECT_WIRED_HEADSET:
                    sendInternalMessage(SWITCH_HEADSET);
                    return HANDLED;
                case BT_ACTIVE_DEVICE_PRESENT:
                    if (!mHasUserExplicitlyLeftBluetooth) {
                        sendInternalMessage(SWITCH_BLUETOOTH);
                    } else {
                        Log.i(this, "Not switching to BT route from speaker because user has " +
                                "explicitly disconnected.");
                    }
                    return HANDLED;
                case BT_ACTIVE_DEVICE_GONE:
                    // No change in audio route required
                    return HANDLED;
                case DISCONNECT_WIRED_HEADSET:
                    // No change in audio route required
                    return HANDLED;
                case BT_AUDIO_DISCONNECTED:
                    // This may be sent as a confirmation by the BT stack after switch off BT.
                    return HANDLED;
                case CONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                case DISCONNECT_DOCK:
                    sendInternalMessage(SWITCH_BASELINE_ROUTE, INCLUDE_BLUETOOTH_IN_BASELINE);
                    return HANDLED;
                case STREAMING_FORCE_ENABLED:
                    transitionTo(mStreamingState);
                    return HANDLED;
               default:
                    return NOT_HANDLED;
            }
        }
    }

    class StreamingState extends AudioState {
        @Override
        public void enter() {
            super.enter();
            updateSystemAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public int getRouteCode() {
            return CallAudioState.ROUTE_STREAMING;
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                case SPEAKER_OFF:
                    // Nothing to do here
                    return HANDLED;
                case SPEAKER_ON:
                    // fall through
                case BT_AUDIO_CONNECTED:
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == NO_FOCUS) {
                        reinitialize();
                        mCallAudioManager.notifyAudioOperationsComplete();
                    }
                    return HANDLED;
                case STREAMING_FORCE_DISABLED:
                    reinitialize();
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    private final BroadcastReceiver mMuteChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("CARSM.mCR");
            try {
                if (AudioManager.ACTION_MICROPHONE_MUTE_CHANGED.equals(intent.getAction())) {
                    if (mCallsManager.isInEmergencyCall()) {
                        Log.i(this, "Mute was externally changed when there's an emergency call. " +
                                "Forcing mute back off.");
                        sendInternalMessage(MUTE_OFF);
                    } else {
                        sendInternalMessage(MUTE_EXTERNALLY_CHANGED);
                    }
                } else if (AudioManager.STREAM_MUTE_CHANGED_ACTION.equals(intent.getAction())) {
                    int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                    boolean isStreamMuted = intent.getBooleanExtra(
                            AudioManager.EXTRA_STREAM_VOLUME_MUTED, false);

                    if (streamType == AudioManager.STREAM_RING && !isStreamMuted) {
                        Log.i(this, "Ring stream was un-muted.");
                        mCallAudioManager.onRingerModeChange();
                    }
                } else {
                    Log.w(this, "Received non-mute-change intent");
                }
            } finally {
                Log.endSession();
            }
        }
    };

    private final BroadcastReceiver mSpeakerPhoneChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("CARSM.mSPCR");
            try {
                if (AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED.equals(intent.getAction())) {
                    if (mAudioManager != null) {
                        if (mAudioManager.isSpeakerphoneOn()) {
                            sendInternalMessage(SPEAKER_ON);
                        } else {
                            sendInternalMessage(SPEAKER_OFF);
                        }
                    }
                } else {
                    Log.w(this, "Received non-speakerphone-change intent");
                }
            } finally {
                Log.endSession();
            }
        }
    };

    private final ActiveEarpieceRoute mActiveEarpieceRoute = new ActiveEarpieceRoute();
    private final ActiveHeadsetRoute mActiveHeadsetRoute = new ActiveHeadsetRoute();
    private final ActiveBluetoothRoute mActiveBluetoothRoute = new ActiveBluetoothRoute();
    private final ActiveSpeakerRoute mActiveSpeakerRoute = new ActiveSpeakerRoute();
    private final RingingBluetoothRoute mRingingBluetoothRoute = new RingingBluetoothRoute();
    private final QuiescentEarpieceRoute mQuiescentEarpieceRoute = new QuiescentEarpieceRoute();
    private final QuiescentHeadsetRoute mQuiescentHeadsetRoute = new QuiescentHeadsetRoute();
    private final QuiescentBluetoothRoute mQuiescentBluetoothRoute = new QuiescentBluetoothRoute();
    private final QuiescentSpeakerRoute mQuiescentSpeakerRoute = new QuiescentSpeakerRoute();
    private final StreamingState mStreamingState = new StreamingState();

    private final Executor mAsyncTaskExecutor;

    /**
     * A few pieces of hidden state. Used to avoid exponential explosion of number of explicit
     * states
     */
    private int mDeviceSupportedRoutes;
    private int mAvailableRoutes;
    private int mAudioFocusType = NO_FOCUS;
    private boolean mWasOnSpeaker;
    private boolean mIsMuted;

    private final Context mContext;
    private final CallsManager mCallsManager;
    private final AudioManager mAudioManager;
    private final BluetoothRouteManager mBluetoothRouteManager;
    private final WiredHeadsetManager mWiredHeadsetManager;
    private final StatusBarNotifier mStatusBarNotifier;
    private final CallAudioManager.AudioServiceFactory mAudioServiceFactory;
    private boolean mDoesDeviceSupportEarpieceRoute;
    private final TelecomSystem.SyncRoot mLock;
    private boolean mHasUserExplicitlyLeftBluetooth = false;

    private HashMap<String, Integer> mStateNameToRouteCode;
    private HashMap<Integer, AudioState> mRouteCodeToQuiescentState;

    // CallAudioState is used as an interface to communicate with many other system components.
    // No internal state transitions should depend on this variable.
    private CallAudioState mCurrentCallAudioState;
    private CallAudioState mLastKnownCallAudioState;

    private CallAudioManager mCallAudioManager;
    private CallAudioCommunicationDeviceTracker mCommunicationDeviceTracker;
    private FeatureFlags mFeatureFlags;

    public CallAudioRouteStateMachine(
            Context context,
            CallsManager callsManager,
            BluetoothRouteManager bluetoothManager,
            WiredHeadsetManager wiredHeadsetManager,
            StatusBarNotifier statusBarNotifier,
            CallAudioManager.AudioServiceFactory audioServiceFactory,
            int earpieceControl,
            Executor asyncTaskExecutor,
            CallAudioCommunicationDeviceTracker communicationDeviceTracker,
            FeatureFlags featureFlags) {
        super(NAME);
        mContext = context;
        mCallsManager = callsManager;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mBluetoothRouteManager = bluetoothManager;
        mWiredHeadsetManager = wiredHeadsetManager;
        mStatusBarNotifier = statusBarNotifier;
        mAudioServiceFactory = audioServiceFactory;
        mLock = callsManager.getLock();
        mAsyncTaskExecutor = asyncTaskExecutor;
        mCommunicationDeviceTracker = communicationDeviceTracker;
        mFeatureFlags = featureFlags;
        createStates(earpieceControl);
    }

    /** Used for testing only */
    public CallAudioRouteStateMachine(
            Context context,
            CallsManager callsManager,
            BluetoothRouteManager bluetoothManager,
            WiredHeadsetManager wiredHeadsetManager,
            StatusBarNotifier statusBarNotifier,
            CallAudioManager.AudioServiceFactory audioServiceFactory,
            int earpieceControl, Looper looper, Executor asyncTaskExecutor,
            CallAudioCommunicationDeviceTracker communicationDeviceTracker,
            FeatureFlags featureFlags) {
        super(NAME, looper);
        mContext = context;
        mCallsManager = callsManager;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mBluetoothRouteManager = bluetoothManager;
        mWiredHeadsetManager = wiredHeadsetManager;
        mStatusBarNotifier = statusBarNotifier;
        mAudioServiceFactory = audioServiceFactory;
        mLock = callsManager.getLock();
        mAsyncTaskExecutor = asyncTaskExecutor;
        mCommunicationDeviceTracker = communicationDeviceTracker;
        mFeatureFlags = featureFlags;
        createStates(earpieceControl);
    }

    private void createStates(int earpieceControl) {
        switch (earpieceControl) {
            case EARPIECE_FORCE_DISABLED:
                mDoesDeviceSupportEarpieceRoute = false;
                break;
            case EARPIECE_FORCE_ENABLED:
                mDoesDeviceSupportEarpieceRoute = true;
                break;
            default:
                mDoesDeviceSupportEarpieceRoute = checkForEarpieceSupport();
        }

        addState(mActiveEarpieceRoute);
        addState(mActiveHeadsetRoute);
        addState(mActiveBluetoothRoute);
        addState(mActiveSpeakerRoute);
        addState(mRingingBluetoothRoute);
        addState(mQuiescentEarpieceRoute);
        addState(mQuiescentHeadsetRoute);
        addState(mQuiescentBluetoothRoute);
        addState(mQuiescentSpeakerRoute);
        addState(mStreamingState);


        mStateNameToRouteCode = new HashMap<>(8);
        mStateNameToRouteCode.put(mQuiescentEarpieceRoute.getName(), ROUTE_EARPIECE);
        mStateNameToRouteCode.put(mQuiescentBluetoothRoute.getName(), ROUTE_BLUETOOTH);
        mStateNameToRouteCode.put(mQuiescentHeadsetRoute.getName(), ROUTE_WIRED_HEADSET);
        mStateNameToRouteCode.put(mQuiescentSpeakerRoute.getName(), ROUTE_SPEAKER);
        mStateNameToRouteCode.put(mRingingBluetoothRoute.getName(), ROUTE_BLUETOOTH);
        mStateNameToRouteCode.put(mActiveEarpieceRoute.getName(), ROUTE_EARPIECE);
        mStateNameToRouteCode.put(mActiveBluetoothRoute.getName(), ROUTE_BLUETOOTH);
        mStateNameToRouteCode.put(mActiveHeadsetRoute.getName(), ROUTE_WIRED_HEADSET);
        mStateNameToRouteCode.put(mActiveSpeakerRoute.getName(), ROUTE_SPEAKER);
        mStateNameToRouteCode.put(mStreamingState.getName(), ROUTE_STREAMING);

        mRouteCodeToQuiescentState = new HashMap<>(4);
        mRouteCodeToQuiescentState.put(ROUTE_EARPIECE, mQuiescentEarpieceRoute);
        mRouteCodeToQuiescentState.put(ROUTE_BLUETOOTH, mQuiescentBluetoothRoute);
        mRouteCodeToQuiescentState.put(ROUTE_SPEAKER, mQuiescentSpeakerRoute);
        mRouteCodeToQuiescentState.put(ROUTE_WIRED_HEADSET, mQuiescentHeadsetRoute);
        mRouteCodeToQuiescentState.put(ROUTE_STREAMING, mStreamingState);
    }

    public void setCallAudioManager(CallAudioManager callAudioManager) {
        mCallAudioManager = callAudioManager;
    }

    /**
     * Initializes the state machine with info on initial audio route, supported audio routes,
     * and mute status.
     */
    public void initialize() {
        CallAudioState initState = getInitialAudioState();
        initialize(initState);
    }

    public void initialize(CallAudioState initState) {
        if ((initState.getRoute() & getCurrentCallSupportedRoutes()) == 0) {
            Log.e(this, new IllegalArgumentException(), "Route %d specified when supported call" +
                    " routes are: %d", initState.getRoute(), getCurrentCallSupportedRoutes());
        }

        mCurrentCallAudioState = initState;
        mLastKnownCallAudioState = initState;
        mDeviceSupportedRoutes = initState.getSupportedRouteMask();
        mAvailableRoutes = mDeviceSupportedRoutes & getCurrentCallSupportedRoutes();
        mIsMuted = initState.isMuted();
        mWasOnSpeaker = false;
        IntentFilter micMuteChangedFilter = new IntentFilter(
                AudioManager.ACTION_MICROPHONE_MUTE_CHANGED);
        micMuteChangedFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(mMuteChangeReceiver, micMuteChangedFilter);

        IntentFilter muteChangedFilter = new IntentFilter(AudioManager.STREAM_MUTE_CHANGED_ACTION);
        muteChangedFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(mMuteChangeReceiver, muteChangedFilter);

        IntentFilter speakerChangedFilter = new IntentFilter(
                AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED);
        speakerChangedFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(mSpeakerPhoneChangeReceiver, speakerChangedFilter);

        mStatusBarNotifier.notifyMute(initState.isMuted());
        // We used to call mStatusBarNotifier.notifySpeakerphone, but that makes no sense as there
        // is never a call at this boot (init) time.
        setInitialState(mRouteCodeToQuiescentState.get(initState.getRoute()));
        start();
    }

    /**
     * Getter for the current CallAudioState object that the state machine is keeping track of.
     * Used for compatibility purposes.
     */
    public CallAudioState getCurrentCallAudioState() {
        return mCurrentCallAudioState;
    }

    public void sendMessageWithSessionInfo(int message, int arg) {
        sendMessageWithSessionInfo(message, arg, (String) null);
    }

    public void sendMessageWithSessionInfo(int message) {
        sendMessageWithSessionInfo(message, 0, (String) null);
    }

    public void sendMessageWithSessionInfo(int message, int arg, String data) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        args.arg2 = data;
        sendMessage(message, arg, 0, args);
    }

    public void sendMessageWithSessionInfo(int message, int arg, BluetoothDevice bluetoothDevice) {
        // ignore, only used in CallAudioRouteController
    }

    @Override
    public void sendMessage(int message, Runnable r) {
        super.sendMessage(message, r);
    }

    /**
     * This is for state-independent changes in audio route (i.e. muting or runnables)
     * @param msg that couldn't be handled.
     */
    @Override
    protected void unhandledMessage(Message msg) {
        switch (msg.what) {
            case MUTE_ON:
                setMuteOn(true);
                updateSystemMuteState();
                return;
            case MUTE_OFF:
                setMuteOn(false);
                updateSystemMuteState();
                return;
            case MUTE_EXTERNALLY_CHANGED:
                mIsMuted = mAudioManager.isMicrophoneMute();
                if (isInActiveState()) {
                    updateSystemMuteState();
                }
                return;
            case TOGGLE_MUTE:
                if (mIsMuted) {
                    sendInternalMessage(MUTE_OFF);
                } else {
                    sendInternalMessage(MUTE_ON);
                }
                return;
            case UPDATE_SYSTEM_AUDIO_ROUTE:
                if (mFeatureFlags.availableRoutesNeverUpdatedAfterSetSystemAudioState()) {
                    // Ensure available routes is updated.
                    updateRouteForForegroundCall();
                    // Ensure current audio state gets updated to take this into account.
                    updateInternalCallAudioState();
                    // Either resend the current audio state as it stands, or update to reflect any
                    // changes put into place based on mAvailableRoutes
                    setSystemAudioState(mCurrentCallAudioState, true);
                } else {
                    updateInternalCallAudioState();
                    updateRouteForForegroundCall();
                    resendSystemAudioState();
                }
                return;
            case RUN_RUNNABLE:
                java.lang.Runnable r = (java.lang.Runnable) msg.obj;
                r.run();
                return;
            default:
                Log.e(this, new IllegalStateException(), "Unexpected message code %d", msg.what);
        }
    }

    public void quitStateMachine() {
        quitNow();
    }

    public void dump(IndentingPrintWriter pw) {
        pw.print("Current state: ");
        pw.println(getCurrentState().getName());
        pw.println("Pending messages:");
        pw.increaseIndent();
        dumpPendingMessages(pw);
        pw.decreaseIndent();
    }

    public void dumpPendingMessages(IndentingPrintWriter pw) {
        getAdapterHandler().getLooper().dump(pw::println, "");
    }

    public boolean isHfpDeviceAvailable() {
        return mBluetoothRouteManager.isBluetoothAvailable();
    }

    private void setSpeakerphoneOn(boolean on) {
        Log.i(this, "turning speaker phone %s", on);
        final boolean hasAnyCalls = mCallsManager.hasAnyCalls();
        // These APIs are all via two-way binder calls so can potentially block Telecom.  Since none
        // of this has to happen in the Telecom lock we'll offload it to the async executor.
        boolean speakerOn = false;
        if (mFeatureFlags.callAudioCommunicationDeviceRefactor()) {
            if (on) {
                speakerOn = mCommunicationDeviceTracker.setCommunicationDevice(
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null);
            } else {
                mCommunicationDeviceTracker.clearCommunicationDevice(
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
            }
        } else {
            speakerOn = processLegacySpeakerCommunicationDevice(on);
        }
        mStatusBarNotifier.notifySpeakerphone(hasAnyCalls && speakerOn);
    }

    private void setBluetoothOn(String address) {
        if (mBluetoothRouteManager.isBluetoothAvailable()) {
            BluetoothDevice connectedDevice =
                    mBluetoothRouteManager.getBluetoothAudioConnectedDevice();
            if (address == null && connectedDevice != null) {
                // null means connect to any device, so if we're already connected to some device,
                // that means we can just tell ourselves that it's connected.
                // Do still try to connect audio though, so that BluetoothRouteManager knows that
                // there's an active call.
                Log.i(this, "Bluetooth audio already on.");
                sendInternalMessage(BT_AUDIO_CONNECTED);
                mBluetoothRouteManager.connectBluetoothAudio(connectedDevice.getAddress());
                return;
            }
            if (connectedDevice == null || !Objects.equals(address, connectedDevice.getAddress())) {
                Log.i(this, "connecting bluetooth audio: %s", address);
                mBluetoothRouteManager.connectBluetoothAudio(address);
            }
        }
    }

    private void setBluetoothOff() {
        if (mBluetoothRouteManager.isBluetoothAvailable()) {
            if (mBluetoothRouteManager.isBluetoothAudioConnectedOrPending()) {
                Log.i(this, "disconnecting bluetooth audio");
                mBluetoothRouteManager.disconnectBluetoothAudio();
            }
        }
    }

    private void setMuteOn(boolean mute) {
        mIsMuted = mute;
        Log.addEvent(mCallsManager.getForegroundCall(), mute ?
                LogUtils.Events.MUTE : LogUtils.Events.UNMUTE);
        if (mute != mAudioManager.isMicrophoneMute() && isInActiveState()) {
            IAudioService audio = mAudioServiceFactory.getAudioService();
            Log.i(this, "changing microphone mute state to: %b [serviceIsNull=%b]",
                    mute, audio == null);
            if (audio != null) {
                try {
                    // We use the audio service directly here so that we can specify
                    // the current user. Telecom runs in the system_server process which
                    // may run as a separate user from the foreground user. If we
                    // used AudioManager directly, we would change mute for the system's
                    // user and not the current foreground, which we want to avoid.
                    audio.setMicrophoneMute(mute, mContext.getOpPackageName(),
                            getCurrentUserId(), mContext.getAttributionTag());
                } catch (RemoteException e) {
                    Log.e(this, e, "Remote exception while toggling mute.");
                }
                // TODO: Check microphone state after attempting to set to ensure that
                // our state corroborates AudioManager's state.
            }
        }
    }

    private void updateSystemMuteState() {
        CallAudioState newCallAudioState = new CallAudioState(mIsMuted,
                mCurrentCallAudioState.getRoute(),
                mAvailableRoutes,
                mCurrentCallAudioState.getActiveBluetoothDevice(),
                mBluetoothRouteManager.getConnectedDevices());
        setSystemAudioState(newCallAudioState);
        updateInternalCallAudioState();
    }

    /**
     * Updates the CallAudioState object from current internal state. The result is used for
     * external communication only.
     */
    private void updateInternalCallAudioState() {
        IState currentState = getCurrentState();
        if (currentState == null) {
            Log.e(this, new IllegalStateException(), "Current state should never be null" +
                    " when updateInternalCallAudioState is called.");
            mCurrentCallAudioState = new CallAudioState(
                    mIsMuted, mCurrentCallAudioState.getRoute(), mAvailableRoutes,
                    mBluetoothRouteManager.getBluetoothAudioConnectedDevice(),
                    mBluetoothRouteManager.getConnectedDevices());
            return;
        }
        int currentRoute = mStateNameToRouteCode.get(currentState.getName());
        mCurrentCallAudioState = new CallAudioState(mIsMuted, currentRoute, mAvailableRoutes,
                mBluetoothRouteManager.getBluetoothAudioConnectedDevice(),
                mBluetoothRouteManager.getConnectedDevices());
    }

    private void setSystemAudioState(CallAudioState newCallAudioState) {
        setSystemAudioState(newCallAudioState, false);
    }

    private void resendSystemAudioState() {
        setSystemAudioState(mLastKnownCallAudioState, true);
    }

    @VisibleForTesting
    public CallAudioState getLastKnownCallAudioState() {
        return mLastKnownCallAudioState;
    }

    private void setSystemAudioState(CallAudioState newCallAudioState, boolean force) {
        synchronized (mLock) {
            Log.i(this, "setSystemAudioState: changing from %s to %s", mLastKnownCallAudioState,
                    newCallAudioState);
            if (force || !newCallAudioState.equals(mLastKnownCallAudioState)) {
                mStatusBarNotifier.notifyMute(newCallAudioState.isMuted());
                mCallsManager.onCallAudioStateChanged(mLastKnownCallAudioState, newCallAudioState);
                updateAudioStateForTrackedCalls(newCallAudioState);
                mLastKnownCallAudioState = newCallAudioState;
            }
        }
    }

    private void updateAudioStateForTrackedCalls(CallAudioState newCallAudioState) {
        Set<Call> calls = mCallsManager.getTrackedCalls();
        for (Call call : calls) {
            if (call != null && call.getConnectionService() != null) {
                call.getConnectionService().onCallAudioStateChanged(call, newCallAudioState);
            }
        }
    }

    private int calculateSupportedRoutes() {
        int routeMask = CallAudioState.ROUTE_SPEAKER;

        if (mWiredHeadsetManager.isPluggedIn()) {
            routeMask |= CallAudioState.ROUTE_WIRED_HEADSET;
        } else if (mDoesDeviceSupportEarpieceRoute){
            routeMask |= CallAudioState.ROUTE_EARPIECE;
        }

        if (mBluetoothRouteManager.isBluetoothAvailable()) {
            routeMask |=  CallAudioState.ROUTE_BLUETOOTH;
        }

        return routeMask;
    }

    private void sendInternalMessage(int messageCode) {
        sendInternalMessage(messageCode, 0);
    }

    private void sendInternalMessage(int messageCode, int arg1) {
        // Internal messages are messages which the state machine sends to itself in the
        // course of processing externally-sourced messages. We want to send these messages at
        // the front of the queue in order to make actions appear atomic to the user and to
        // prevent scenarios such as these:
        // 1. State machine handler thread is suspended for some reason.
        // 2. Headset gets connected (sends CONNECT_HEADSET).
        // 3. User switches to speakerphone in the UI (sends SWITCH_SPEAKER).
        // 4. State machine handler is un-suspended.
        // 5. State machine handler processes the CONNECT_HEADSET message and sends
        //    SWITCH_HEADSET at end of queue.
        // 6. State machine handler processes SWITCH_SPEAKER.
        // 7. State machine handler processes SWITCH_HEADSET.
        Session subsession = Log.createSubsession();
        if(subsession != null) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = subsession;
            sendMessageAtFrontOfQueue(messageCode, arg1, 0, args);
        } else {
            sendMessageAtFrontOfQueue(messageCode, arg1);
        }
    }

    private CallAudioState getInitialAudioState() {
        int supportedRouteMask = calculateSupportedRoutes() & getCurrentCallSupportedRoutes();
        final int route;

        if ((supportedRouteMask & ROUTE_BLUETOOTH) != 0
                && mBluetoothRouteManager.hasBtActiveDevice()) {
            route = ROUTE_BLUETOOTH;
        } else if ((supportedRouteMask & ROUTE_WIRED_HEADSET) != 0) {
            route = ROUTE_WIRED_HEADSET;
        } else if ((supportedRouteMask & ROUTE_EARPIECE) != 0) {
            route = ROUTE_EARPIECE;
        } else {
            route = ROUTE_SPEAKER;
        }

        return new CallAudioState(false, route, supportedRouteMask, null,
                mBluetoothRouteManager.getConnectedDevices());
    }

    private int getCurrentUserId() {
        final long ident = Binder.clearCallingIdentity();
        try {
            UserInfo currentUser = ActivityManager.getService().getCurrentUser();
            return currentUser.id;
        } catch (RemoteException e) {
            // Activity manager not running, nothing we can do assume user 0.
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        return UserHandle.USER_OWNER;
    }

    public boolean isInActiveState() {
        AudioState currentState = (AudioState) getCurrentState();
        if (currentState == null) {
            Log.w(this, "Current state is null, assuming inactive state");
            return false;
        }
        return currentState.isActive();
    }

    private boolean checkForEarpieceSupport() {
        AudioDeviceInfo[] deviceList = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device: deviceList) {
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                return true;
            }
        }
        // No earpiece found
        return false;
    }

    private boolean isWatchActiveOrOnlyWatchesAvailable() {
        if (!mFeatureFlags.ignoreAutoRouteToWatchDevice()) {
            Log.i(this, "isWatchActiveOrOnlyWatchesAvailable: Flag is disabled.");
            return false;
        }

        boolean containsWatchDevice = false;
        boolean containsNonWatchDevice = false;
        Collection<BluetoothDevice> connectedBtDevices =
                mBluetoothRouteManager.getConnectedDevices();

        for (BluetoothDevice connectedDevice: connectedBtDevices) {
            if (mBluetoothRouteManager.isWatch(connectedDevice)) {
                containsWatchDevice = true;
            } else {
                containsNonWatchDevice = true;
            }
        }

        // Don't ignore switch if watch is already the active device.
        boolean isActiveDeviceWatch = mBluetoothRouteManager.isWatch(
                mBluetoothRouteManager.getBluetoothAudioConnectedDevice());
        Log.i(this, "isWatchActiveOrOnlyWatchesAvailable: contains watch: %s, contains "
                + "non-wearable device: %s, is active device a watch: %s.",
                containsWatchDevice, containsNonWatchDevice, isActiveDeviceWatch);
        return containsWatchDevice && !containsNonWatchDevice && !isActiveDeviceWatch;
    }

    private boolean processLegacySpeakerCommunicationDevice(boolean on) {
        AudioDeviceInfo speakerDevice = null;
        for (AudioDeviceInfo info : mAudioManager.getAvailableCommunicationDevices()) {
            if (info.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                speakerDevice = info;
                break;
            }
        }
        boolean speakerOn = false;
        if (speakerDevice != null && on) {
            boolean result = mAudioManager.setCommunicationDevice(speakerDevice);
            if (result) {
                speakerOn = true;
            }
        } else {
            AudioDeviceInfo curDevice = mAudioManager.getCommunicationDevice();
            if (curDevice != null
                    && curDevice.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                mAudioManager.clearCommunicationDevice();
            }
        }
        return speakerOn;
    }

    private int calculateBaselineRouteMessage(boolean isExplicitUserRequest,
            boolean includeBluetooth) {
        boolean isSkipEarpiece = false;
        if (!isExplicitUserRequest) {
            synchronized (mLock) {
                // Check video calls to skip earpiece since the baseline for video
                // calls should be the speakerphone route
                isSkipEarpiece = mCallsManager.hasVideoCall();
            }
        }
        if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0
                && !mHasUserExplicitlyLeftBluetooth
                && includeBluetooth && !isWatchActiveOrOnlyWatchesAvailable()) {
            return isExplicitUserRequest ? USER_SWITCH_BLUETOOTH : SWITCH_BLUETOOTH;
        } else if ((mAvailableRoutes & ROUTE_EARPIECE) != 0 && !isSkipEarpiece) {
            return isExplicitUserRequest ? USER_SWITCH_EARPIECE : SWITCH_EARPIECE;
        } else if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
            return isExplicitUserRequest ? USER_SWITCH_HEADSET : SWITCH_HEADSET;
        } else {
            return isExplicitUserRequest ? USER_SWITCH_SPEAKER : SWITCH_SPEAKER;
        }
    }

    private void reinitialize() {
        CallAudioState initState = getInitialAudioState();
        mDeviceSupportedRoutes = initState.getSupportedRouteMask();
        mAvailableRoutes = mDeviceSupportedRoutes & getCurrentCallSupportedRoutes();
        mIsMuted = initState.isMuted();
        setSpeakerphoneOn(initState.getRoute() == CallAudioState.ROUTE_SPEAKER);
        setMuteOn(mIsMuted);
        mWasOnSpeaker = false;
        mHasUserExplicitlyLeftBluetooth = false;
        mLastKnownCallAudioState = initState;
        transitionTo(mRouteCodeToQuiescentState.get(initState.getRoute()));
    }

    private void updateRouteForForegroundCall() {
        mAvailableRoutes = mDeviceSupportedRoutes & getCurrentCallSupportedRoutes();

        CallAudioState currentState = getCurrentCallAudioState();

        // Move to baseline route in the case the current route is no longer available.
        if ((mAvailableRoutes & currentState.getRoute()) == 0) {
            sendInternalMessage(calculateBaselineRouteMessage(false, true));
        }
    }

    private int getCurrentCallSupportedRoutes() {
        int supportedRoutes = CallAudioState.ROUTE_ALL;

        if (mCallsManager.getForegroundCall() != null) {
            supportedRoutes &= mCallsManager.getForegroundCall().getSupportedAudioRoutes();
        }

        return supportedRoutes;
    }

    private int modifyRoutes(int base, int remove, int add, boolean considerCurrentCall) {
        base &= ~remove;

        if (considerCurrentCall) {
            add &= getCurrentCallSupportedRoutes();
        }

        base |= add;

        return base;
    }

    public Handler getAdapterHandler() {
        return getHandler();
    }
}
