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
 * limitations under the License
 */

package com.android.server.telecom;

import static com.android.server.telecom.AudioRoute.BT_AUDIO_ROUTE_TYPES;
import static com.android.server.telecom.AudioRoute.TYPE_INVALID;

import android.app.ActivityManager;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.IAudioService;
import android.media.audiopolicy.AudioProductStrategy;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.CallAudioState;
import android.telecom.Log;
import android.telecom.Logging.Session;
import android.util.ArrayMap;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.SomeArgs;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CallAudioRouteController implements CallAudioRouteAdapter {
    private static final long TIMEOUT_LIMIT = 2000L;
    private static final AudioRoute DUMMY_ROUTE = new AudioRoute(TYPE_INVALID, null, null);
    private static final Map<Integer, Integer> ROUTE_MAP;
    static {
        ROUTE_MAP = new ArrayMap<>();
        ROUTE_MAP.put(AudioRoute.TYPE_EARPIECE, CallAudioState.ROUTE_EARPIECE);
        ROUTE_MAP.put(AudioRoute.TYPE_WIRED, CallAudioState.ROUTE_WIRED_HEADSET);
        ROUTE_MAP.put(AudioRoute.TYPE_SPEAKER, CallAudioState.ROUTE_SPEAKER);
        ROUTE_MAP.put(AudioRoute.TYPE_DOCK, CallAudioState.ROUTE_SPEAKER);
        ROUTE_MAP.put(AudioRoute.TYPE_BLUETOOTH_SCO, CallAudioState.ROUTE_BLUETOOTH);
        ROUTE_MAP.put(AudioRoute.TYPE_BLUETOOTH_HA, CallAudioState.ROUTE_BLUETOOTH);
        ROUTE_MAP.put(AudioRoute.TYPE_BLUETOOTH_LE, CallAudioState.ROUTE_BLUETOOTH);
        ROUTE_MAP.put(AudioRoute.TYPE_STREAMING, CallAudioState.ROUTE_STREAMING);
    }

    private final CallsManager mCallsManager;
    private final Context mContext;
    private AudioManager mAudioManager;
    private CallAudioManager mCallAudioManager;
    private final BluetoothRouteManager mBluetoothRouteManager;
    private final CallAudioManager.AudioServiceFactory mAudioServiceFactory;
    private final Handler mHandler;
    private final WiredHeadsetManager mWiredHeadsetManager;
    private Set<AudioRoute> mAvailableRoutes;
    private AudioRoute mCurrentRoute;
    private AudioRoute mEarpieceWiredRoute;
    private AudioRoute mSpeakerDockRoute;
    private AudioRoute mStreamingRoute;
    private Set<AudioRoute> mStreamingRoutes;
    private Map<AudioRoute, BluetoothDevice> mBluetoothRoutes;
    private Map<Integer, AudioRoute> mTypeRoutes;
    private PendingAudioRoute mPendingAudioRoute;
    private AudioRoute.Factory mAudioRouteFactory;
    private int mFocusType;
    private final Object mLock = new Object();
    private final BroadcastReceiver mSpeakerPhoneChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("CARC.mSPCR");
            try {
                if (AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED.equals(intent.getAction())) {
                    if (mAudioManager != null) {
                        AudioDeviceInfo info = mAudioManager.getCommunicationDevice();
                        if ((info != null) &&
                                (info.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)) {
                            sendMessageWithSessionInfo(SPEAKER_ON);
                        } else {
                            sendMessageWithSessionInfo(SPEAKER_OFF);
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
    private final BroadcastReceiver mMuteChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("CARC.mCR");
            try {
                if (AudioManager.ACTION_MICROPHONE_MUTE_CHANGED.equals(intent.getAction())) {
                    if (mCallsManager.isInEmergencyCall()) {
                        Log.i(this, "Mute was externally changed when there's an emergency call. "
                                + "Forcing mute back off.");
                        sendMessageWithSessionInfo(MUTE_OFF);
                    } else {
                        sendMessageWithSessionInfo(MUTE_EXTERNALLY_CHANGED);
                    }
                } else if (AudioManager.STREAM_MUTE_CHANGED_ACTION.equals(intent.getAction())) {
                    int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                    boolean isStreamMuted = intent.getBooleanExtra(
                            AudioManager.EXTRA_STREAM_VOLUME_MUTED, false);

                    if (streamType == AudioManager.STREAM_RING && !isStreamMuted
                            && mCallAudioManager != null) {
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
    private CallAudioState mCallAudioState;
    private boolean mIsMute;
    private boolean mIsPending;
    private boolean mIsActive;

    public CallAudioRouteController(
            Context context,
            CallsManager callsManager,
            CallAudioManager.AudioServiceFactory audioServiceFactory,
            AudioRoute.Factory audioRouteFactory,
            WiredHeadsetManager wiredHeadsetManager,
            BluetoothRouteManager bluetoothRouteManager) {
        mContext = context;
        mCallsManager = callsManager;
        mAudioManager = context.getSystemService(AudioManager.class);
        mAudioServiceFactory = audioServiceFactory;
        mAudioRouteFactory = audioRouteFactory;
        mWiredHeadsetManager = wiredHeadsetManager;
        mIsMute = false;
        mBluetoothRouteManager = bluetoothRouteManager;
        mFocusType = NO_FOCUS;
        HandlerThread handlerThread = new HandlerThread(this.getClass().getSimpleName());
        handlerThread.start();

        // Register broadcast receivers
        IntentFilter speakerChangedFilter = new IntentFilter(
                AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED);
        speakerChangedFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        context.registerReceiver(mSpeakerPhoneChangeReceiver, speakerChangedFilter);

        IntentFilter micMuteChangedFilter = new IntentFilter(
                AudioManager.ACTION_MICROPHONE_MUTE_CHANGED);
        micMuteChangedFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        context.registerReceiver(mMuteChangeReceiver, micMuteChangedFilter);

        IntentFilter muteChangedFilter = new IntentFilter(AudioManager.STREAM_MUTE_CHANGED_ACTION);
        muteChangedFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        context.registerReceiver(mMuteChangeReceiver, muteChangedFilter);

        // Create handler
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                synchronized (this) {
                    preHandleMessage(msg);
                    String address;
                    BluetoothDevice bluetoothDevice;
                    int focus;
                    @AudioRoute.AudioRouteType int type;
                    switch (msg.what) {
                        case CONNECT_WIRED_HEADSET:
                            handleWiredHeadsetConnected();
                            break;
                        case DISCONNECT_WIRED_HEADSET:
                            handleWiredHeadsetDisconnected();
                            break;
                        case CONNECT_DOCK:
                            handleDockConnected();
                            break;
                        case DISCONNECT_DOCK:
                            handleDockDisconnected();
                            break;
                        case BLUETOOTH_DEVICE_LIST_CHANGED:
                            break;
                        case BT_ACTIVE_DEVICE_PRESENT:
                            type = msg.arg1;
                            address = (String) ((SomeArgs) msg.obj).arg2;
                            handleBtActiveDevicePresent(type, address);
                            break;
                        case BT_ACTIVE_DEVICE_GONE:
                            type = msg.arg1;
                            handleBtActiveDeviceGone(type);
                            break;
                        case BT_DEVICE_ADDED:
                            type = msg.arg1;
                            bluetoothDevice = (BluetoothDevice) ((SomeArgs) msg.obj).arg2;
                            handleBtConnected(type, bluetoothDevice);
                            break;
                        case BT_DEVICE_REMOVED:
                            type = msg.arg1;
                            bluetoothDevice = (BluetoothDevice) ((SomeArgs) msg.obj).arg2;
                            handleBtDisconnected(type, bluetoothDevice);
                            break;
                        case SWITCH_EARPIECE:
                        case USER_SWITCH_EARPIECE:
                            handleSwitchEarpiece();
                            break;
                        case SWITCH_BLUETOOTH:
                        case USER_SWITCH_BLUETOOTH:
                            address = (String) ((SomeArgs) msg.obj).arg2;
                            handleSwitchBluetooth(address);
                            break;
                        case SWITCH_HEADSET:
                        case USER_SWITCH_HEADSET:
                            handleSwitchHeadset();
                            break;
                        case SWITCH_SPEAKER:
                        case USER_SWITCH_SPEAKER:
                            handleSwitchSpeaker();
                            break;
                        case USER_SWITCH_BASELINE_ROUTE:
                            handleSwitchBaselineRoute();
                            break;
                        case SPEAKER_ON:
                            handleSpeakerOn();
                            break;
                        case SPEAKER_OFF:
                            handleSpeakerOff();
                            break;
                        case STREAMING_FORCE_ENABLED:
                            handleStreamingEnabled();
                            break;
                        case STREAMING_FORCE_DISABLED:
                            handleStreamingDisabled();
                            break;
                        case BT_AUDIO_CONNECTED:
                            bluetoothDevice = (BluetoothDevice) ((SomeArgs) msg.obj).arg2;
                            handleBtAudioActive(bluetoothDevice);
                            break;
                        case BT_AUDIO_DISCONNECTED:
                            bluetoothDevice = (BluetoothDevice) ((SomeArgs) msg.obj).arg2;
                            handleBtAudioInactive(bluetoothDevice);
                            break;
                        case MUTE_ON:
                            handleMuteChanged(true);
                            break;
                        case MUTE_OFF:
                            handleMuteChanged(false);
                            break;
                        case MUTE_EXTERNALLY_CHANGED:
                            handleMuteChanged(mAudioManager.isMasterMute());
                            break;
                        case SWITCH_FOCUS:
                            focus = msg.arg1;
                            handleSwitchFocus(focus);
                            break;
                        case EXIT_PENDING_ROUTE:
                            handleExitPendingRoute();
                            break;
                        default:
                            break;
                    }
                    postHandleMessage(msg);
                }
            }
        };
    }
    @Override
    public void initialize() {
        mAvailableRoutes = new HashSet<>();
        mBluetoothRoutes = new ArrayMap<>();
        mTypeRoutes = new ArrayMap<>();
        mStreamingRoutes = new HashSet<>();
        mPendingAudioRoute = new PendingAudioRoute(this, mAudioManager);
        mStreamingRoute = new AudioRoute(AudioRoute.TYPE_STREAMING, null, null);
        mStreamingRoutes.add(mStreamingRoute);

        int supportMask = calculateSupportedRouteMask();
        if ((supportMask & CallAudioState.ROUTE_SPEAKER) != 0) {
            // Create spekaer routes
            mSpeakerDockRoute = mAudioRouteFactory.create(AudioRoute.TYPE_SPEAKER, null,
                    mAudioManager);
            if (mSpeakerDockRoute == null) {
                Log.w(this, "Can't find available audio device info for route TYPE_SPEAKER");
            } else {
                mTypeRoutes.put(AudioRoute.TYPE_SPEAKER, mSpeakerDockRoute);
                mAvailableRoutes.add(mSpeakerDockRoute);
            }
        }

        if ((supportMask & CallAudioState.ROUTE_WIRED_HEADSET) != 0) {
            // Create wired headset routes
            mEarpieceWiredRoute = mAudioRouteFactory.create(AudioRoute.TYPE_WIRED, null,
                    mAudioManager);
            if (mEarpieceWiredRoute == null) {
                Log.w(this, "Can't find available audio device info for route TYPE_WIRED_HEADSET");
            } else {
                mTypeRoutes.put(AudioRoute.TYPE_WIRED, mEarpieceWiredRoute);
                mAvailableRoutes.add(mEarpieceWiredRoute);
            }
        } else if ((supportMask & CallAudioState.ROUTE_EARPIECE) != 0) {
            // Create earpiece routes
            mEarpieceWiredRoute = mAudioRouteFactory.create(AudioRoute.TYPE_EARPIECE, null,
                    mAudioManager);
            if (mEarpieceWiredRoute == null) {
                Log.w(this, "Can't find available audio device info for route TYPE_EARPIECE");
            } else {
                mTypeRoutes.put(AudioRoute.TYPE_EARPIECE, mEarpieceWiredRoute);
                mAvailableRoutes.add(mEarpieceWiredRoute);
            }
        }

        // set current route
        if (mEarpieceWiredRoute != null) {
            mCurrentRoute = mEarpieceWiredRoute;
        } else {
            mCurrentRoute = mSpeakerDockRoute;
        }
        mIsActive = false;
        mCallAudioState = new CallAudioState(mIsMute, ROUTE_MAP.get(mCurrentRoute.getType()),
                supportMask, null, new HashSet<>());
    }

    @Override
    public void sendMessageWithSessionInfo(int message) {
        sendMessageWithSessionInfo(message, 0, (String) null);
    }

    @Override
    public void sendMessageWithSessionInfo(int message, int arg) {
        sendMessageWithSessionInfo(message, arg, (String) null);
    }

    @Override
    public void sendMessageWithSessionInfo(int message, int arg, String data) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        args.arg2 = data;
        sendMessage(message, arg, 0, args);
    }

    @Override
    public void sendMessageWithSessionInfo(int message, int arg, BluetoothDevice bluetoothDevice) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        args.arg2 = bluetoothDevice;
        sendMessage(message, arg, 0, args);
    }

    @Override
    public void sendMessage(int message, Runnable r) {
        r.run();
    }

    private void sendMessage(int what, int arg1, int arg2, Object obj) {
        mHandler.sendMessage(Message.obtain(mHandler, what, arg1, arg2, obj));
    }

    @Override
    public void setCallAudioManager(CallAudioManager callAudioManager) {
        mCallAudioManager = callAudioManager;
    }

    @Override
    public CallAudioState getCurrentCallAudioState() {
        return null;
    }

    @Override
    public boolean isHfpDeviceAvailable() {
        return !mBluetoothRoutes.isEmpty();
    }

    @Override
    public Handler getAdapterHandler() {
        return mHandler;
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
    }

    private void preHandleMessage(Message msg) {
        if (msg.obj instanceof SomeArgs) {
            Session session = (Session) ((SomeArgs) msg.obj).arg1;
            String messageCodeName = MESSAGE_CODE_TO_NAME.get(msg.what, "unknown");
            Log.continueSession(session, "CARC.pM_" + messageCodeName);
            Log.i(this, "Message received: %s=%d, arg1=%d", messageCodeName, msg.what, msg.arg1);
        }
    }

    private void postHandleMessage(Message msg) {
        Log.endSession();
        if (msg.obj instanceof SomeArgs) {
            ((SomeArgs) msg.obj).recycle();
        }
    }

    public boolean isActive() {
        return mIsActive;
    }

    public boolean isPending() {
        return mIsPending;
    }

    private void routeTo(boolean active, AudioRoute destRoute) {
        if (!destRoute.equals(mStreamingRoute) && !getAvailableRoutes().contains(destRoute)) {
            return;
        }
        if (mIsPending) {
            if (destRoute.equals(mPendingAudioRoute.getDestRoute()) && (mIsActive == active)) {
                return;
            }
            Log.i(this, "Override current pending route destination from %s(active=%b) to "
                            + "%s(active=%b)",
                    mPendingAudioRoute.getDestRoute(), mIsActive, destRoute, active);
            // override pending route while keep waiting for still pending messages for the
            // previous pending route
            mIsActive = active;
            mPendingAudioRoute.setOrigRoute(mIsActive, mPendingAudioRoute.getDestRoute());
            mPendingAudioRoute.setDestRoute(active, destRoute);
        } else {
            if (mCurrentRoute.equals(destRoute) && (mIsActive == active)) {
                return;
            }
            Log.i(this, "Enter pending route, orig%s(active=%b), dest%s(active=%b)", mCurrentRoute,
                    mIsActive, destRoute, active);
            // route to pending route
            if (getAvailableRoutes().contains(mCurrentRoute)) {
                mPendingAudioRoute.setOrigRoute(mIsActive, mCurrentRoute);
            } else {
                // Avoid waiting for pending messages for an unavailable route
                mPendingAudioRoute.setOrigRoute(mIsActive, DUMMY_ROUTE);
            }
            mPendingAudioRoute.setDestRoute(active, destRoute);
            mIsActive = active;
            mIsPending = true;
        }
        mPendingAudioRoute.evaluatePendingState();
        postTimeoutMessage();
    }

    private void postTimeoutMessage() {
        // reset timeout handler
        mHandler.removeMessages(PENDING_ROUTE_TIMEOUT);
        mHandler.postDelayed(() -> mHandler.sendMessage(
                Message.obtain(mHandler, PENDING_ROUTE_TIMEOUT)), TIMEOUT_LIMIT);
    }

    private void handleWiredHeadsetConnected() {
        AudioRoute wiredHeadsetRoute = null;
        try {
            wiredHeadsetRoute = mAudioRouteFactory.create(AudioRoute.TYPE_WIRED, null,
                    mAudioManager);
        } catch (IllegalArgumentException e) {
            Log.e(this, e, "Can't find available audio device info for route type:"
                    + AudioRoute.DEVICE_TYPE_STRINGS.get(AudioRoute.TYPE_WIRED));
        }

        if (wiredHeadsetRoute != null) {
            mAvailableRoutes.add(wiredHeadsetRoute);
            mAvailableRoutes.remove(mEarpieceWiredRoute);
            mTypeRoutes.put(AudioRoute.TYPE_WIRED, wiredHeadsetRoute);
            mEarpieceWiredRoute = wiredHeadsetRoute;
            routeTo(mIsActive, wiredHeadsetRoute);
            onAvailableRoutesChanged();
        }
    }

    public void handleWiredHeadsetDisconnected() {
        // Update audio route states
        AudioRoute wiredHeadsetRoute = mTypeRoutes.remove(AudioRoute.TYPE_WIRED);
        if (wiredHeadsetRoute != null) {
            mAvailableRoutes.remove(wiredHeadsetRoute);
            mEarpieceWiredRoute = null;
        }
        AudioRoute earpieceRoute = mTypeRoutes.get(AudioRoute.TYPE_EARPIECE);
        if (earpieceRoute != null) {
            mAvailableRoutes.add(earpieceRoute);
            mEarpieceWiredRoute = earpieceRoute;
        }
        onAvailableRoutesChanged();

        // Route to expected state
        if (mCurrentRoute.equals(wiredHeadsetRoute)) {
            routeTo(mIsActive, getBaseRoute(true));
        }
    }

    private void handleDockConnected() {
        AudioRoute dockRoute = null;
        try {
            dockRoute = mAudioRouteFactory.create(AudioRoute.TYPE_DOCK, null, mAudioManager);
        } catch (IllegalArgumentException e) {
            Log.e(this, e, "Can't find available audio device info for route type:"
                    + AudioRoute.DEVICE_TYPE_STRINGS.get(AudioRoute.TYPE_WIRED));
        }

        if (dockRoute != null) {
            mAvailableRoutes.add(dockRoute);
            mAvailableRoutes.remove(mSpeakerDockRoute);
            mTypeRoutes.put(AudioRoute.TYPE_DOCK, dockRoute);
            mSpeakerDockRoute = dockRoute;
            routeTo(mIsActive, dockRoute);
            onAvailableRoutesChanged();
        }
    }

    public void handleDockDisconnected() {
        // Update audio route states
        AudioRoute dockRoute = mTypeRoutes.get(AudioRoute.TYPE_DOCK);
        if (dockRoute != null) {
            mAvailableRoutes.remove(dockRoute);
            mSpeakerDockRoute = null;
        }
        AudioRoute speakerRoute = mTypeRoutes.get(AudioRoute.TYPE_SPEAKER);
        if (speakerRoute != null) {
            mAvailableRoutes.add(speakerRoute);
            mSpeakerDockRoute = speakerRoute;
        }
        onAvailableRoutesChanged();

        // Route to expected state
        if (mCurrentRoute.equals(dockRoute)) {
            routeTo(mIsActive, getBaseRoute(true));
        }
    }

    private void handleStreamingEnabled() {
        if (!mCurrentRoute.equals(mStreamingRoute)) {
            routeTo(mIsActive, mStreamingRoute);
        } else {
            Log.i(this, "ignore enable streaming, already in streaming");
        }
    }

    private void handleStreamingDisabled() {
        if (mCurrentRoute.equals(mStreamingRoute)) {
            mCurrentRoute = DUMMY_ROUTE;
            onAvailableRoutesChanged();
            routeTo(mIsActive, getBaseRoute(true));
        } else {
            Log.i(this, "ignore disable streaming, not in streaming");
        }
    }

    private void handleBtAudioActive(BluetoothDevice bluetoothDevice) {
        if (mIsPending) {
            if (Objects.equals(mPendingAudioRoute.getDestRoute().getBluetoothAddress(),
                    bluetoothDevice.getAddress())) {
                mPendingAudioRoute.onMessageReceived(BT_AUDIO_CONNECTED);
            }
        } else {
            // ignore, not triggered by telecom
        }
    }

    private void handleBtAudioInactive(BluetoothDevice bluetoothDevice) {
        if (mIsPending) {
            if (Objects.equals(mPendingAudioRoute.getOrigRoute().getBluetoothAddress(),
                    bluetoothDevice.getAddress())) {
                mPendingAudioRoute.onMessageReceived(BT_AUDIO_DISCONNECTED);
            }
        } else {
            // ignore, not triggered by telecom
        }
    }

    private void handleBtConnected(@AudioRoute.AudioRouteType int type,
                                   BluetoothDevice bluetoothDevice) {
        AudioRoute bluetoothRoute = null;
        bluetoothRoute = mAudioRouteFactory.create(type, bluetoothDevice.getAddress(),
                mAudioManager);
        if (bluetoothRoute == null) {
            Log.w(this, "Can't find available audio device info for route type:"
                    + AudioRoute.DEVICE_TYPE_STRINGS.get(type));
        } else {
            Log.i(this, "bluetooth route added: " + bluetoothRoute);
            mAvailableRoutes.add(bluetoothRoute);
            mBluetoothRoutes.put(bluetoothRoute, bluetoothDevice);
            onAvailableRoutesChanged();
        }
    }

    private void handleBtDisconnected(@AudioRoute.AudioRouteType int type,
                                      BluetoothDevice bluetoothDevice) {
        // Clean up unavailable routes
        AudioRoute bluetoothRoute = getBluetoothRoute(type, bluetoothDevice.getAddress());
        if (bluetoothRoute != null) {
            Log.i(this, "bluetooth route removed: " + bluetoothRoute);
            mBluetoothRoutes.remove(bluetoothRoute);
            mAvailableRoutes.remove(bluetoothRoute);
            onAvailableRoutesChanged();
        }

        // Fallback to an available route
        if (Objects.equals(mCurrentRoute, bluetoothRoute)) {
            routeTo(mIsActive, getBaseRoute(false));
        }
    }

    private void handleBtActiveDevicePresent(@AudioRoute.AudioRouteType int type,
                                             String deviceAddress) {
        AudioRoute bluetoothRoute = getBluetoothRoute(type, deviceAddress);
        if (bluetoothRoute != null) {
            Log.i(this, "request to route to bluetooth route: %s(active=%b)", bluetoothRoute,
                    mIsActive);
            routeTo(mIsActive, bluetoothRoute);
        }
    }

    private void handleBtActiveDeviceGone(@AudioRoute.AudioRouteType int type) {
        if ((mIsPending && mPendingAudioRoute.getDestRoute().getType() == type)
                || (!mIsPending && mCurrentRoute.getType() == type)) {
            // Fallback to an available route
            routeTo(mIsActive, getBaseRoute(true));
        }
    }

    private void handleMuteChanged(boolean mute) {
        mIsMute = mute;
        if (mIsMute != mAudioManager.isMasterMute() && mIsActive) {
            IAudioService audioService = mAudioServiceFactory.getAudioService();
            Log.i(this, "changing microphone mute state to: %b [serviceIsNull=%b]", mute,
                    audioService == null);
            if (audioService != null) {
                try {
                    audioService.setMicrophoneMute(mute, mContext.getOpPackageName(),
                            mCallsManager.getCurrentUserHandle().getIdentifier(),
                            mContext.getAttributionTag());
                } catch (RemoteException e) {
                    Log.e(this, e, "Remote exception while toggling mute.");
                    return;
                }
            }
        }
        onMuteStateChanged(mIsMute);
    }

    private void handleSwitchFocus(int focus) {
        mFocusType = focus;
        switch (focus) {
            case NO_FOCUS -> {
                if (mIsActive) {
                    handleMuteChanged(false);
                    routeTo(false, mCurrentRoute);
                }
            }
            case ACTIVE_FOCUS -> {
                if (!mIsActive) {
                    routeTo(true, getBaseRoute(true));
                }
            }
            case RINGING_FOCUS -> {
                if (!mIsActive) {
                    AudioRoute route = getBaseRoute(true);
                    BluetoothDevice device = mBluetoothRoutes.get(route);
                    if (device != null && !mBluetoothRouteManager.isInbandRingEnabled(device)) {
                        routeTo(false, route);
                    } else {
                        routeTo(true, route);
                    }
                } else {
                    // active
                    BluetoothDevice device = mBluetoothRoutes.get(mCurrentRoute);
                    if (device != null && !mBluetoothRouteManager.isInbandRingEnabled(device)) {
                        routeTo(false, mCurrentRoute);
                    }
                }
            }
        }
    }

    public void handleSwitchEarpiece() {
        AudioRoute earpieceRoute = mTypeRoutes.get(AudioRoute.TYPE_EARPIECE);
        if (earpieceRoute != null && getAvailableRoutes().contains(earpieceRoute)) {
            routeTo(mIsActive, earpieceRoute);
        } else {
            Log.i(this, "ignore switch earpiece request");
        }
    }

    private void handleSwitchBluetooth(String address) {
        Log.i(this, "handle switch to bluetooth with address %s", address);
        AudioRoute bluetoothRoute = null;
        BluetoothDevice bluetoothDevice = null;
        for (AudioRoute route : getAvailableRoutes()) {
            if (Objects.equals(address, route.getBluetoothAddress())) {
                bluetoothRoute = route;
                bluetoothDevice = mBluetoothRoutes.get(route);
                break;
            }
        }

        if (bluetoothRoute != null && bluetoothDevice != null) {
            if (mFocusType == RINGING_FOCUS) {
                routeTo(mBluetoothRouteManager.isInbandRingEnabled(bluetoothDevice) && mIsActive,
                        bluetoothRoute);
            } else {
                routeTo(mIsActive, bluetoothRoute);
            }
        } else {
            Log.i(this, "ignore switch bluetooth request");
        }
    }

    private void handleSwitchHeadset() {
        AudioRoute headsetRoute = mTypeRoutes.get(AudioRoute.TYPE_WIRED);
        if (headsetRoute != null && getAvailableRoutes().contains(headsetRoute)) {
            routeTo(mIsActive, headsetRoute);
        } else {
            Log.i(this, "ignore switch speaker request");
        }
    }

    private void handleSwitchSpeaker() {
        if (mSpeakerDockRoute != null && getAvailableRoutes().contains(mSpeakerDockRoute)) {
            routeTo(mIsActive, mSpeakerDockRoute);
        } else {
            Log.i(this, "ignore switch speaker request");
        }
    }

    private void handleSwitchBaselineRoute() {
        routeTo(mIsActive, getBaseRoute(true));
    }

    private void handleSpeakerOn() {
        if (isPending()) {
            mPendingAudioRoute.onMessageReceived(SPEAKER_ON);
        } else {
            if (mSpeakerDockRoute != null && getAvailableRoutes().contains(mSpeakerDockRoute)) {
                routeTo(mIsActive, mSpeakerDockRoute);
                // Since the route switching triggered by this message, we need to manually send it
                // again so that we won't stuck in the pending route
                if (mIsActive) {
                    sendMessageWithSessionInfo(SPEAKER_ON);
                }
            }
        }
    }

    private void handleSpeakerOff() {
        if (isPending()) {
            mPendingAudioRoute.onMessageReceived(SPEAKER_OFF);
        } else if (mCurrentRoute.getType() == AudioRoute.TYPE_SPEAKER) {
            routeTo(mIsActive, getBaseRoute(true));
            // Since the route switching triggered by this message, we need to manually send it
            // again so that we won't stuck in the pending route
            if (mIsActive) {
                sendMessageWithSessionInfo(SPEAKER_OFF);
            }
            onAvailableRoutesChanged();
        }
    }

    public void handleExitPendingRoute() {
        if (mIsPending) {
            Log.i(this, "Exit pending route and enter %s(active=%b)",
                    mPendingAudioRoute.getDestRoute(), mIsActive);
            mCurrentRoute = mPendingAudioRoute.getDestRoute();
            mIsPending = false;
            onCurrentRouteChanged();
        }
    }

    private void onCurrentRouteChanged() {
        synchronized (mLock) {
            BluetoothDevice activeBluetoothDevice = null;
            int route = ROUTE_MAP.get(mCurrentRoute.getType());
            if (route == CallAudioState.ROUTE_STREAMING) {
                updateCallAudioState(new CallAudioState(mIsMute, route, route));
                return;
            }
            if (route == CallAudioState.ROUTE_BLUETOOTH) {
                activeBluetoothDevice = mBluetoothRoutes.get(mCurrentRoute);
            }
            updateCallAudioState(new CallAudioState(mIsMute, route,
                    mCallAudioState.getRawSupportedRouteMask(), activeBluetoothDevice,
                    mCallAudioState.getSupportedBluetoothDevices()));
        }
    }

    private void onAvailableRoutesChanged() {
        synchronized (mLock) {
            int routeMask = 0;
            Set<BluetoothDevice> availableBluetoothDevices = new HashSet<>();
            for (AudioRoute route : getAvailableRoutes()) {
                routeMask |= ROUTE_MAP.get(route.getType());
                if (BT_AUDIO_ROUTE_TYPES.contains(route.getType())) {
                    availableBluetoothDevices.add(mBluetoothRoutes.get(route));
                }
            }
            updateCallAudioState(new CallAudioState(mIsMute, mCallAudioState.getRoute(), routeMask,
                    mCallAudioState.getActiveBluetoothDevice(), availableBluetoothDevices));
        }
    }

    private void onMuteStateChanged(boolean mute) {
        updateCallAudioState(new CallAudioState(mute, mCallAudioState.getRoute(),
                mCallAudioState.getSupportedRouteMask(), mCallAudioState.getActiveBluetoothDevice(),
                mCallAudioState.getSupportedBluetoothDevices()));
    }

    private void updateCallAudioState(CallAudioState callAudioState) {
        Log.i(this, "updateCallAudioState: " + callAudioState);
        CallAudioState oldState = mCallAudioState;
        mCallAudioState = callAudioState;
        mCallsManager.onCallAudioStateChanged(oldState, mCallAudioState);
        updateAudioStateForTrackedCalls(mCallAudioState);
    }

    private void updateAudioStateForTrackedCalls(CallAudioState newCallAudioState) {
        Set<Call> calls = mCallsManager.getTrackedCalls();
        for (Call call : calls) {
            if (call != null && call.getConnectionService() != null) {
                call.getConnectionService().onCallAudioStateChanged(call, newCallAudioState);
            }
        }
    }

    private AudioRoute getPreferredAudioRouteFromStrategy() {
        // Get audio produce strategy
        AudioProductStrategy strategy = null;
        final AudioAttributes attr = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build();
        List<AudioProductStrategy> strategies = AudioManager.getAudioProductStrategies();
        for (AudioProductStrategy s : strategies) {
            if (s.supportsAudioAttributes(attr)) {
                strategy = s;
            }
        }
        if (strategy == null) {
            return null;
        }

        // Get preferred device
        AudioDeviceAttributes deviceAttr = mAudioManager.getPreferredDeviceForStrategy(strategy);
        if (deviceAttr == null) {
            return null;
        }

        // Get corresponding audio route
        @AudioRoute.AudioRouteType int type = AudioRoute.DEVICE_INFO_TYPETO_AUDIO_ROUTE_TYPE.get(
                deviceAttr.getType());
        if (BT_AUDIO_ROUTE_TYPES.contains(type)) {
            return getBluetoothRoute(type, deviceAttr.getAddress());
        } else {
            return mTypeRoutes.get(deviceAttr.getType());

        }
    }

    private AudioRoute getPreferredAudioRouteFromDefault(boolean includeBluetooth) {
        if (mBluetoothRoutes.isEmpty() || !includeBluetooth) {
            return mEarpieceWiredRoute != null ? mEarpieceWiredRoute : mSpeakerDockRoute;
        } else {
            // Most recent active route will always be the last in the array
            return mBluetoothRoutes.keySet().stream().toList().get(mBluetoothRoutes.size() - 1);
        }
    }

    private int calculateSupportedRouteMask() {
        int routeMask = CallAudioState.ROUTE_SPEAKER;

        if (mWiredHeadsetManager.isPluggedIn()) {
            routeMask |= CallAudioState.ROUTE_WIRED_HEADSET;
        } else {
            AudioDeviceInfo[] deviceList = mAudioManager.getDevices(
                    AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device: deviceList) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                    routeMask |= CallAudioState.ROUTE_EARPIECE;
                    break;
                }
            }
        }
        return routeMask;
    }

    @VisibleForTesting
    public Set<AudioRoute> getAvailableRoutes() {
        if (mCurrentRoute.equals(mStreamingRoute)) {
            return mStreamingRoutes;
        } else {
            return mAvailableRoutes;
        }
    }

    public AudioRoute getCurrentRoute() {
        return mCurrentRoute;
    }

    private AudioRoute getBluetoothRoute(@AudioRoute.AudioRouteType int audioRouteType,
                                         String address) {
        for (AudioRoute route : mBluetoothRoutes.keySet()) {
            if (route.getType() == audioRouteType && route.getBluetoothAddress().equals(address)) {
                return route;
            }
        }
        return null;
    }

    public AudioRoute getBaseRoute(boolean includeBluetooth) {
        AudioRoute destRoute = getPreferredAudioRouteFromStrategy();
        if (destRoute == null) {
            destRoute = getPreferredAudioRouteFromDefault(includeBluetooth);
        }
        if (destRoute != null && !getAvailableRoutes().contains(destRoute)) {
            destRoute = null;
        }
        return destRoute;
    }

    @VisibleForTesting
    public void setAudioManager(AudioManager audioManager) {
        mAudioManager = audioManager;
    }

    @VisibleForTesting
    public void setAudioRouteFactory(AudioRoute.Factory audioRouteFactory) {
        mAudioRouteFactory = audioRouteFactory;
    }

    @VisibleForTesting
    public void setActive(boolean active) {
        if (active) {
            mFocusType = ACTIVE_FOCUS;
        } else {
            mFocusType = NO_FOCUS;
        }
        mIsActive = active;
    }
}
