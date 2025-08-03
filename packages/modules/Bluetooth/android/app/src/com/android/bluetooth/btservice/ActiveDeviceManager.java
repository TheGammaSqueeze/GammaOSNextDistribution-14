/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.ArraySet;
import android.util.Log;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.hearingaid.HearingAidService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The active device manager is responsible for keeping track of the
 * connected A2DP/HFP/AVRCP/HearingAid/LE audio devices and select which device is
 * active (for each profile).
 * The active device manager selects a fallback device when the currently active device
 * is disconnected, and it selects BT devices that are lastly activated one.
 *
 * Current policy (subject to change):
 * 1) If the maximum number of connected devices is one, the manager doesn't
 *    do anything. Each profile is responsible for automatically selecting
 *    the connected device as active. Only if the maximum number of connected
 *    devices is more than one, the rules below will apply.
 * 2) The selected A2DP active device is the one used for AVRCP as well.
 * 3) The HFP active device might be different from the A2DP active device.
 * 4) The Active Device Manager always listens for the change of active devices.
 *    When it changed (e.g., triggered indirectly by user action on the UI),
 *    the new active device is marked as the current active device for that profile.
 * 5) If there is a HearingAid active device, then A2DP, HFP and LE audio active devices
 *    must be set to null (i.e., A2DP, HFP and LE audio cannot have active devices).
 *    The reason is that A2DP, HFP or LE audio cannot be used together with HearingAid.
 * 6) If there are no connected devices (e.g., during startup, or after all
 *    devices have been disconnected, the active device per profile
 *    (A2DP/HFP/HearingAid/LE audio) is selected as follows:
 * 6.1) The last connected HearingAid device is selected as active.
 *      If there is an active A2DP, HFP or LE audio device, those must be set to null.
 * 6.2) The last connected A2DP, HFP or LE audio device is selected as active.
 *      However, if there is an active HearingAid device, then the
 *      A2DP, HFP, or LE audio active device is not set (must remain null).
 * 7) If the currently active device (per profile) is disconnected, the
 *    Active Device Manager just marks that the profile has no active device,
 *    and the lastly activated BT device that is still connected would be selected.
 * 8) If there is already an active device, however, if active device change notified
 *    with a null device, the corresponding profile is marked as having no active device.
 * 9) If a wired audio device is connected, the audio output is switched
 *    by the Audio Framework itself to that device. We detect this here,
 *    and the active device for each profile (A2DP/HFP/HearingAid/LE audio) is set
 *    to null to reflect the output device state change. However, if the
 *    wired audio device is disconnected, we don't do anything explicit
 *    and apply the default behavior instead:
 * 9.1) If the wired headset is still the selected output device (i.e. the
 *      active device is set to null), the Phone itself will become the output
 *      device (i.e., the active device will remain null). If music was
 *      playing, it will stop.
 * 9.2) If one of the Bluetooth devices is the selected active device
 *      (e.g., by the user in the UI), disconnecting the wired audio device
 *      will have no impact. E.g., music will continue streaming over the
 *      active Bluetooth device.
 */
public class ActiveDeviceManager implements AdapterService.BluetoothStateCallback {
    private static final String TAG = ActiveDeviceManager.class.getSimpleName();
    private static final boolean DBG = true;
    @VisibleForTesting
    static final int A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS = 5_000;

    private final AdapterService mAdapterService;
    private DatabaseManager mDbManager;
    private final ServiceFactory mFactory;
    private HandlerThread mHandlerThread = null;
    private Handler mHandler = null;
    private final AudioManager mAudioManager;
    private final AudioManagerAudioDeviceCallback mAudioManagerAudioDeviceCallback;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final List<BluetoothDevice> mA2dpConnectedDevices = new ArrayList<>();
    @GuardedBy("mLock")
    private final List<BluetoothDevice> mHfpConnectedDevices = new ArrayList<>();
    @GuardedBy("mLock")
    private final List<BluetoothDevice> mHearingAidConnectedDevices = new ArrayList<>();
    @GuardedBy("mLock")
    private final List<BluetoothDevice> mLeAudioConnectedDevices = new ArrayList<>();
    @GuardedBy("mLock")
    private final List<BluetoothDevice> mLeHearingAidConnectedDevices = new ArrayList<>();
    @GuardedBy("mLock")
    private List<BluetoothDevice> mPendingLeHearingAidActiveDevice = new ArrayList<>();
    @GuardedBy("mLock")
    private BluetoothDevice mA2dpActiveDevice = null;
    @GuardedBy("mLock")
    private BluetoothDevice mHfpActiveDevice = null;
    @GuardedBy("mLock")
    private final Set<BluetoothDevice> mHearingAidActiveDevices = new ArraySet<>();
    @GuardedBy("mLock")
    private BluetoothDevice mLeAudioActiveDevice = null;
    @GuardedBy("mLock")
    private BluetoothDevice mLeHearingAidActiveDevice = null;
    @GuardedBy("mLock")
    private BluetoothDevice mPendingActiveDevice = null;

    private BluetoothDevice mClassicDeviceToBeActivated = null;
    private BluetoothDevice mClassicDeviceNotToBeActivated = null;

    @Override
    public void onBluetoothStateChange(int prevState, int newState) {
        mHandler.post(() -> handleAdapterStateChanged(newState));
    }

    /**
     * Called when audio profile connection state changed
     *
     * @param profile The Bluetooth profile of which connection state changed
     * @param device The device of which connection state was changed
     * @param fromState The previous connection state of the device
     * @param toState The new connection state of the device
     */
    public void profileConnectionStateChanged(
            int profile, BluetoothDevice device, int fromState, int toState) {
        if (toState == BluetoothProfile.STATE_CONNECTED) {
            switch (profile) {
                case BluetoothProfile.A2DP:
                    mHandler.post(() -> handleA2dpConnected(device));
                    break;
                case BluetoothProfile.HEADSET:
                    mHandler.post(() -> handleHfpConnected(device));
                    break;
                case BluetoothProfile.LE_AUDIO:
                    mHandler.post(() -> handleLeAudioConnected(device));
                    break;
                case BluetoothProfile.HEARING_AID:
                    mHandler.post(() -> handleHearingAidConnected(device));
                    break;
                case BluetoothProfile.HAP_CLIENT:
                    mHandler.post(() -> handleHapConnected(device));
                    break;
            }
        } else if (fromState == BluetoothProfile.STATE_CONNECTED) {
            switch (profile) {
                case BluetoothProfile.A2DP:
                    mHandler.post(() -> handleA2dpDisconnected(device));
                    break;
                case BluetoothProfile.HEADSET:
                    mHandler.post(() -> handleHfpDisconnected(device));
                    break;
                case BluetoothProfile.LE_AUDIO:
                    mHandler.post(() -> handleLeAudioDisconnected(device));
                    break;
                case BluetoothProfile.HEARING_AID:
                    mHandler.post(() -> handleHearingAidDisconnected(device));
                    break;
                case BluetoothProfile.HAP_CLIENT:
                    mHandler.post(() -> handleHapDisconnected(device));
                    break;
            }
        }
    }

    /**
     * Called when active state of audio profiles changed
     *
     * @param profile The Bluetooth profile of which active state changed
     * @param device The device currently activated. {@code null} if no device is active
     */
    public void profileActiveDeviceChanged(int profile, BluetoothDevice device) {
        switch (profile) {
            case BluetoothProfile.A2DP:
                mHandler.post(() -> handleA2dpActiveDeviceChanged(device));
                break;
            case BluetoothProfile.HEADSET:
                mHandler.post(() -> handleHfpActiveDeviceChanged(device));
                break;
            case BluetoothProfile.LE_AUDIO:
                mHandler.post(() -> handleLeAudioActiveDeviceChanged(device));
                break;
            case BluetoothProfile.HEARING_AID:
                mHandler.post(() -> handleHearingAidActiveDeviceChanged(device));
                break;
        }
    }

    private void handleAdapterStateChanged(int currentState) {
        if (DBG) {
            Log.d(TAG, "handleAdapterStateChanged: currentState=" + currentState);
        }
        if (currentState == BluetoothAdapter.STATE_ON) {
            resetState();
        }
    }

    /**
     * Handles the active device logic for when A2DP is connected. Does the following:
     * 1. Check if a hearing aid device is active. We will always prefer hearing aid devices, so if
     * one is active, we will not make this A2DP device active.
     * 2. If there is no hearing aid device active, we will make this A2DP device active.
     * 3. We will make this device active for HFP if it's already connected to HFP
     * 4. If dual mode is disabled, we clear the LE Audio active device to ensure mutual exclusion
     * between classic and LE audio.
     *
     * @param device is the device that was connected to A2DP
     */
    private void handleA2dpConnected(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleA2dpConnected: " + device);
            }
            if (mA2dpConnectedDevices.contains(device)) {
                if (DBG) {
                    Log.d(TAG, "This device is already connected: " + device);
                }
                return;
            }
            mA2dpConnectedDevices.add(device);
            if (isBroadcastingAudio()) {
                Log.i(
                        TAG,
                        "LE Audio Broadcast is streaming, skip setting A2dp device as active: "
                                + device);
                if (mPendingActiveDevice != null) {
                    mHandler.removeCallbacksAndMessages(mPendingActiveDevice);
                }
                return;
            }

            if (mHearingAidActiveDevices.isEmpty() && mLeHearingAidActiveDevice == null) {
                // New connected device: select it as active
                // Activate HFP and A2DP at the same time if both profile already connected.
                if (mHfpConnectedDevices.contains(device)) {
                    boolean a2dpMadeActive = setA2dpActiveDevice(device);
                    boolean hfpMadeActive = setHfpActiveDevice(device);
                    if ((a2dpMadeActive || hfpMadeActive) && !Utils.isDualModeAudioEnabled()) {
                        setLeAudioActiveDevice(null, true);
                    }
                    return;
                }
                // Activate A2DP if audio mode is normal or HFP is not supported or enabled.
                if (mDbManager.getProfileConnectionPolicy(device, BluetoothProfile.HEADSET)
                                != BluetoothProfile.CONNECTION_POLICY_ALLOWED
                        || mAudioManager.getMode() == AudioManager.MODE_NORMAL) {
                    boolean a2dpMadeActive = setA2dpActiveDevice(device);
                    if (a2dpMadeActive && !Utils.isDualModeAudioEnabled()) {
                        setLeAudioActiveDevice(null, true);
                    }
                } else {
                    if (DBG) {
                        Log.d(TAG, "A2DP activation is suspended until HFP connected: "
                                + device);
                    }
                    if (mPendingActiveDevice != null) {
                        mHandler.removeCallbacksAndMessages(mPendingActiveDevice);
                    }
                    mPendingActiveDevice = device;
                    // Activate A2DP if HFP is failed to connect.
                    mHandler.postDelayed(
                            () -> {
                                Log.w(TAG, "HFP connection timeout. Activate A2DP for " + device);
                                setA2dpActiveDevice(device);
                            },
                            mPendingActiveDevice,
                            A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
                }
            }
        }
    }

    /**
     * Handles the active device logic for when HFP is connected. Does the following:
     * 1. Check if a hearing aid device is active. We will always prefer hearing aid devices, so if
     * one is active, we will not make this HFP device active.
     * 2. If there is no hearing aid device active, we will make this HFP device active.
     * 3. We will make this device active for A2DP if it's already connected to A2DP
     * 4. If dual mode is disabled, we clear the LE Audio active device to ensure mutual exclusion
     * between classic and LE audio.
     *
     * @param device is the device that was connected to A2DP
     */
    private void handleHfpConnected(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleHfpConnected: " + device);
            }
            if (mHfpConnectedDevices.contains(device)) {
                if (DBG) {
                    Log.d(TAG, "This device is already connected: " + device);
                }
                return;
            }
            mHfpConnectedDevices.add(device);
            if (isBroadcastingAudio()) {
                Log.i(
                        TAG,
                        "LE Audio Broadcast is streaming, skip setting Hfp device as active: "
                                + device);
                if (mPendingActiveDevice != null) {
                    mHandler.removeCallbacksAndMessages(mPendingActiveDevice);
                }
                return;
            }

            if (mHearingAidActiveDevices.isEmpty() && mLeHearingAidActiveDevice == null) {
                // New connected device: select it as active
                // Activate HFP and A2DP at the same time once both profile connected.
                if (mA2dpConnectedDevices.contains(device)) {
                    boolean a2dpMadeActive = setA2dpActiveDevice(device);
                    boolean hfpMadeActive = setHfpActiveDevice(device);

                    /* Make LEA inactive if device is made active for any classic audio profile
                    and dual mode is disabled */
                    if ((a2dpMadeActive || hfpMadeActive) && !Utils.isDualModeAudioEnabled()) {
                        setLeAudioActiveDevice(null, true);
                    }
                    return;
                }
                // Activate HFP if audio mode is not normal or A2DP is not supported or enabled.
                if (mDbManager.getProfileConnectionPolicy(device, BluetoothProfile.A2DP)
                                != BluetoothProfile.CONNECTION_POLICY_ALLOWED
                        || mAudioManager.getMode() != AudioManager.MODE_NORMAL) {
                    if (isWatch(device)) {
                        Log.i(TAG, "Do not set hfp active for watch device " + device);
                        return;
                    }
                    // Tries to make the device active for HFP
                    boolean hfpMadeActive = setHfpActiveDevice(device);

                    // Makes LEA inactive if device is made active for HFP & dual mode is disabled
                    if (hfpMadeActive && !Utils.isDualModeAudioEnabled()) {
                        setLeAudioActiveDevice(null, true);
                    }
                } else {
                    if (DBG) {
                        Log.d(TAG, "HFP activation is suspended until A2DP connected: "
                                + device);
                    }
                    if (mPendingActiveDevice != null) {
                        mHandler.removeCallbacksAndMessages(mPendingActiveDevice);
                    }
                    mPendingActiveDevice = device;
                    // Activate HFP if A2DP is failed to connect.
                    mHandler.postDelayed(
                            () -> {
                                Log.w(TAG, "A2DP connection timeout. Activate HFP for " + device);
                                setHfpActiveDevice(device);
                            },
                            mPendingActiveDevice,
                            A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
                }
            }
        }
    }

    private void handleHearingAidConnected(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleHearingAidConnected: " + device);
            }
            if (mHearingAidConnectedDevices.contains(device)) {
                if (DBG) {
                    Log.d(TAG, "This device is already connected: " + device);
                }
                return;
            }
            mHearingAidConnectedDevices.add(device);
            if (isBroadcastingAudio()) {
                Log.i(
                        TAG,
                        "LE Audio Broadcast is streaming, skip setting HearingAid device as "
                                + "active:  "
                                + device);
                return;
            }
            // New connected device: select it as active
            if (setHearingAidActiveDevice(device)) {
                setA2dpActiveDevice(null, true);
                setHfpActiveDevice(null);
                setLeAudioActiveDevice(null, true);
            }
        }
    }

    private void handleLeAudioConnected(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleLeAudioConnected: " + device);
            }

            final LeAudioService leAudioService = mFactory.getLeAudioService();
            if (leAudioService == null || device == null) {
                return;
            }
            leAudioService.deviceConnected(device);

            if (mLeAudioConnectedDevices.contains(device)) {
                if (DBG) {
                    Log.d(TAG, "This device is already connected: " + device);
                }
                return;
            }

            mLeAudioConnectedDevices.add(device);
            if (isBroadcastingAudio()) {
                Log.i(
                        TAG,
                        "LE Audio Broadcast is streaming, skip setting le audio device as active: "
                                + device);
                return;
            }

            if (mHearingAidActiveDevices.isEmpty()
                    && mLeHearingAidActiveDevice == null
                    && mPendingLeHearingAidActiveDevice.isEmpty()) {
                // New connected device: select it as active
                boolean leAudioMadeActive = setLeAudioActiveDevice(device);
                if (leAudioMadeActive && !Utils.isDualModeAudioEnabled()) {
                    setA2dpActiveDevice(null, true);
                    setHfpActiveDevice(null);
                }
            } else if (mPendingLeHearingAidActiveDevice.contains(device)) {
                if (setLeHearingAidActiveDevice(device)) {
                    setHearingAidActiveDevice(null, true);
                    setA2dpActiveDevice(null, true);
                    setHfpActiveDevice(null);
                }
            }
        }
    }

    private void handleHapConnected(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleHapConnected: " + device);
            }
            if (mLeHearingAidConnectedDevices.contains(device)) {
                if (DBG) {
                    Log.d(TAG, "This device is already connected: " + device);
                }
                return;
            }
            mLeHearingAidConnectedDevices.add(device);
            if (isBroadcastingAudio()) {
                Log.i(
                        TAG,
                        "LE Audio Broadcast is streaming, skip setting Hap device as active: "
                                + device);
                return;
            }

            if (!mLeAudioConnectedDevices.contains(device)) {
                mPendingLeHearingAidActiveDevice.add(device);
            } else if (Objects.equals(mLeAudioActiveDevice, device)) {
                mLeHearingAidActiveDevice = device;
            } else {
                // New connected device: select it as active
                if (setLeHearingAidActiveDevice(device)) {
                    setHearingAidActiveDevice(null, true);
                    setA2dpActiveDevice(null, true);
                    setHfpActiveDevice(null);
                }
            }
        }
    }

    private void handleA2dpDisconnected(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleA2dpDisconnected: " + device
                        + ", mA2dpActiveDevice=" + mA2dpActiveDevice);
            }
            mA2dpConnectedDevices.remove(device);
            if (Objects.equals(mA2dpActiveDevice, device)) {
                if (!setFallbackDeviceActiveLocked()) {
                    setA2dpActiveDevice(null, false);
                }
            }
        }
    }

    private void handleHfpDisconnected(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleHfpDisconnected: " + device
                        + ", mHfpActiveDevice=" + mHfpActiveDevice);
            }
            mHfpConnectedDevices.remove(device);
            if (Objects.equals(mHfpActiveDevice, device)) {
                if (mHfpConnectedDevices.isEmpty()) {
                    setHfpActiveDevice(null);
                }
                setFallbackDeviceActiveLocked();
            }
        }
    }

    private void handleHearingAidDisconnected(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleHearingAidDisconnected: " + device
                        + ", mHearingAidActiveDevices=" + mHearingAidActiveDevices);
            }
            mHearingAidConnectedDevices.remove(device);
            if (mHearingAidActiveDevices.remove(device) && mHearingAidActiveDevices.isEmpty()) {
                if (!setFallbackDeviceActiveLocked()) {
                    setHearingAidActiveDevice(null, false);
                }
            }
        }
    }

    private void handleLeAudioDisconnected(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleLeAudioDisconnected: " + device
                        + ", mLeAudioActiveDevice=" + mLeAudioActiveDevice);
            }

            final LeAudioService leAudioService = mFactory.getLeAudioService();
            if (leAudioService == null || device == null) {
                return;
            }

            mLeAudioConnectedDevices.remove(device);
            mLeHearingAidConnectedDevices.remove(device);

            boolean hasFallbackDevice = false;
            if (Objects.equals(mLeAudioActiveDevice, device)) {
                hasFallbackDevice = setFallbackDeviceActiveLocked();
                if (!hasFallbackDevice) {
                    setLeAudioActiveDevice(null, false);
                }
            }
            leAudioService.deviceDisconnected(device, hasFallbackDevice);
        }
    }

    private void handleHapDisconnected(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleHapDisconnected: " + device
                        + ", mLeHearingAidActiveDevice=" + mLeHearingAidActiveDevice);
            }
            mLeHearingAidConnectedDevices.remove(device);
            mPendingLeHearingAidActiveDevice.remove(device);
            if (Objects.equals(mLeHearingAidActiveDevice, device)) {
                mLeHearingAidActiveDevice = null;
            }
        }
    }

    /**
     * Handles the active device logic for when the A2DP active device changes. Does the following:
     * 1. Clear the active hearing aid.
     * 2. If dual mode is enabled and all supported classic audio profiles are enabled, makes this
     * device active for LE Audio. If not, clear the LE Audio active device.
     * 3. Make HFP active for this device if it is already connected to HFP.
     * 4. Stores the new A2DP active device.
     *
     * @param device is the device that was connected to A2DP
     */
    private void handleA2dpActiveDeviceChanged(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleA2dpActiveDeviceChanged: " + device
                        + ", mA2dpActiveDevice=" + mA2dpActiveDevice);
            }
            if (!Objects.equals(mA2dpActiveDevice, device)) {
                if (device != null) {
                    setHearingAidActiveDevice(null, true);
                }
                if (Utils.isDualModeAudioEnabled()
                        && mAdapterService.isAllSupportedClassicAudioProfilesActive(device)) {
                    setLeAudioActiveDevice(device);
                } else {
                    setLeAudioActiveDevice(null, true);
                }
            }
            // Just assign locally the new value
            mA2dpActiveDevice = device;

            // Activate HFP if needed.
            if (device != null) {
                if (Objects.equals(mClassicDeviceNotToBeActivated, device)) {
                    mHandler.removeCallbacksAndMessages(mClassicDeviceNotToBeActivated);
                    mClassicDeviceNotToBeActivated = null;
                    return;
                }
                if (Objects.equals(mClassicDeviceToBeActivated, device)) {
                    mHandler.removeCallbacksAndMessages(mClassicDeviceToBeActivated);
                    mClassicDeviceToBeActivated = null;
                }

                if (mClassicDeviceToBeActivated != null) {
                    mClassicDeviceNotToBeActivated = mClassicDeviceToBeActivated;
                    mHandler.removeCallbacksAndMessages(mClassicDeviceToBeActivated);
                    mHandler.postDelayed(
                            () -> mClassicDeviceNotToBeActivated = null,
                            mClassicDeviceNotToBeActivated,
                            A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
                    mClassicDeviceToBeActivated = null;
                }
                if (!Objects.equals(mHfpActiveDevice, device)
                        && mHfpConnectedDevices.contains(device)
                        && mDbManager.getProfileConnectionPolicy(device, BluetoothProfile.HEADSET)
                                == BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
                    mClassicDeviceToBeActivated = device;
                    setHfpActiveDevice(device);
                    mHandler.postDelayed(
                            () -> mClassicDeviceToBeActivated = null,
                            mClassicDeviceToBeActivated,
                            A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
                }
            }
        }
    }

    /**
     * Handles the active device logic for when the HFP active device changes. Does the following:
     * 1. Clear the active hearing aid.
     * 2. If dual mode is enabled and all supported classic audio profiles are enabled, makes this
     * device active for LE Audio. If not, clear the LE Audio active device.
     * 3. Make A2DP active for this device if it is already connected to A2DP.
     * 4. Stores the new HFP active device.
     *
     * @param device is the device that was connected to A2DP
     */
    private void handleHfpActiveDeviceChanged(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleHfpActiveDeviceChanged: " + device
                        + ", mHfpActiveDevice=" + mHfpActiveDevice);
            }
            if (!Objects.equals(mHfpActiveDevice, device)) {
                if (device != null) {
                    setHearingAidActiveDevice(null, true);
                }
                if (Utils.isDualModeAudioEnabled()
                        && mAdapterService.isAllSupportedClassicAudioProfilesActive(device)) {
                    setLeAudioActiveDevice(device);
                } else {
                    setLeAudioActiveDevice(null, true);
                }
            }
            // Just assign locally the new value
            mHfpActiveDevice = device;

            // Activate A2DP if needed.
            if (device != null) {
                if (Objects.equals(mClassicDeviceNotToBeActivated, device)) {
                    mHandler.removeCallbacksAndMessages(mClassicDeviceNotToBeActivated);
                    mClassicDeviceNotToBeActivated = null;
                    return;
                }
                if (Objects.equals(mClassicDeviceToBeActivated, device)) {
                    mHandler.removeCallbacksAndMessages(mClassicDeviceToBeActivated);
                    mClassicDeviceToBeActivated = null;
                }

                if (mClassicDeviceToBeActivated != null) {
                    mClassicDeviceNotToBeActivated = mClassicDeviceToBeActivated;
                    mHandler.removeCallbacksAndMessages(mClassicDeviceToBeActivated);
                    mHandler.postDelayed(
                            () -> mClassicDeviceNotToBeActivated = null,
                            mClassicDeviceNotToBeActivated,
                            A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
                    mClassicDeviceToBeActivated = null;
                }
                if (!Objects.equals(mA2dpActiveDevice, device)
                        && mA2dpConnectedDevices.contains(device)
                        && mDbManager.getProfileConnectionPolicy(device, BluetoothProfile.A2DP)
                                == BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
                    mClassicDeviceToBeActivated = device;
                    setA2dpActiveDevice(device);
                    mHandler.postDelayed(
                            () -> mClassicDeviceToBeActivated = null,
                            mClassicDeviceToBeActivated,
                            A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
                }
            }
        }
    }

    private void handleHearingAidActiveDeviceChanged(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleHearingAidActiveDeviceChanged: " + device
                        + ", mHearingAidActiveDevices=" + mHearingAidActiveDevices);
            }
            // Just assign locally the new value
            final HearingAidService hearingAidService = mFactory.getHearingAidService();
            if (hearingAidService != null) {
                long hiSyncId = hearingAidService.getHiSyncId(device);
                if (device != null && getHearingAidActiveHiSyncIdLocked() == hiSyncId) {
                    mHearingAidActiveDevices.add(device);
                } else {
                    mHearingAidActiveDevices.clear();
                    mHearingAidActiveDevices.addAll(
                            hearingAidService.getConnectedPeerDevices(hiSyncId));
                }
            }
            if (device != null) {
                setA2dpActiveDevice(null, true);
                setHfpActiveDevice(null);
                setLeAudioActiveDevice(null, true);
            }
        }
    }

    private void handleLeAudioActiveDeviceChanged(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "handleLeAudioActiveDeviceChanged: " + device
                        + ", mLeAudioActiveDevice=" + mLeAudioActiveDevice);
            }
            if (device != null && !mLeAudioConnectedDevices.contains(device)) {
                mLeAudioConnectedDevices.add(device);
            }
            // Just assign locally the new value
            if (device != null && !Objects.equals(mLeAudioActiveDevice, device)) {
                if (!Utils.isDualModeAudioEnabled()) {
                    setA2dpActiveDevice(null, true);
                    setHfpActiveDevice(null);
                }
                setHearingAidActiveDevice(null, true);
            }

            if (mLeHearingAidConnectedDevices.contains(device)) {
                mLeHearingAidActiveDevice = device;
            }

            mLeAudioActiveDevice = device;
        }
    }

    /** Notifications of audio device connection and disconnection events. */
    @SuppressLint("AndroidFrameworkRequiresPermission")
    private class AudioManagerAudioDeviceCallback extends AudioDeviceCallback {
        private boolean isWiredAudioHeadset(AudioDeviceInfo deviceInfo) {
            switch (deviceInfo.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return true;
                default:
                    break;
            }
            return false;
        }

        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (DBG) {
                Log.d(TAG, "onAudioDevicesAdded");
            }
            boolean hasAddedWiredDevice = false;
            for (AudioDeviceInfo deviceInfo : addedDevices) {
                if (DBG) {
                    Log.d(TAG, "Audio device added: " + deviceInfo.getProductName() + " type: "
                            + deviceInfo.getType());
                }
                if (isWiredAudioHeadset(deviceInfo)) {
                    hasAddedWiredDevice = true;
                    break;
                }
            }
            if (hasAddedWiredDevice) {
                wiredAudioDeviceConnected();
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
        }
    }

    ActiveDeviceManager(AdapterService service, ServiceFactory factory) {
        mAdapterService = service;
        mDbManager = mAdapterService.getDatabase();
        mFactory = factory;
        mAudioManager = service.getSystemService(AudioManager.class);
        mAudioManagerAudioDeviceCallback = new AudioManagerAudioDeviceCallback();
    }

    void start() {
        if (DBG) {
            Log.d(TAG, "start()");
        }

        mHandlerThread = new HandlerThread("BluetoothActiveDeviceManager");
        BluetoothMethodProxy mp = BluetoothMethodProxy.getInstance();
        mp.threadStart(mHandlerThread);
        mHandler = new Handler(mp.handlerThreadGetLooper(mHandlerThread));

        mAudioManager.registerAudioDeviceCallback(mAudioManagerAudioDeviceCallback, mHandler);
        mAdapterService.registerBluetoothStateCallback((command) -> mHandler.post(command), this);
    }

    void cleanup() {
        if (DBG) {
            Log.d(TAG, "cleanup()");
        }

        mAudioManager.unregisterAudioDeviceCallback(mAudioManagerAudioDeviceCallback);
        mAdapterService.unregisterBluetoothStateCallback(this);
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        resetState();
    }

    /**
     * Get the {@link Looper} for the handler thread. This is used in testing and helper
     * objects
     *
     * @return {@link Looper} for the handler thread
     */
    @VisibleForTesting
    public Looper getHandlerLooper() {
        if (mHandler == null) {
            return null;
        }
        return mHandler.getLooper();
    }

    private boolean setA2dpActiveDevice(@NonNull BluetoothDevice device) {
        return setA2dpActiveDevice(device, false);
    }

    private boolean setA2dpActiveDevice(@Nullable BluetoothDevice device,
            boolean hasFallbackDevice) {
        if (DBG) {
            Log.d(TAG, "setA2dpActiveDevice(" + device + ")"
                    + (device == null ? " hasFallbackDevice=" + hasFallbackDevice : ""));
        }
        synchronized (mLock) {
            if (mPendingActiveDevice != null) {
                mHandler.removeCallbacksAndMessages(mPendingActiveDevice);
                mPendingActiveDevice = null;
            }
        }

        final A2dpService a2dpService = mFactory.getA2dpService();
        if (a2dpService == null) {
            return false;
        }

        boolean success = false;
        if (device == null) {
            success = a2dpService.removeActiveDevice(!hasFallbackDevice);
        } else {
            success = a2dpService.setActiveDevice(device);
        }

        if (!success) {
            return false;
        }

        synchronized (mLock) {
            mA2dpActiveDevice = device;
        }
        return true;
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    private boolean setHfpActiveDevice(BluetoothDevice device) {
        synchronized (mLock) {
            if (DBG) {
                Log.d(TAG, "setHfpActiveDevice(" + device + ")");
            }
            if (mPendingActiveDevice != null) {
                mHandler.removeCallbacksAndMessages(mPendingActiveDevice);
                mPendingActiveDevice = null;
            }
            final HeadsetService headsetService = mFactory.getHeadsetService();
            if (headsetService == null) {
                return false;
            }
            BluetoothSinkAudioPolicy audioPolicy = headsetService.getHfpCallAudioPolicy(device);
            if (audioPolicy != null && audioPolicy.getActiveDevicePolicyAfterConnection()
                    == BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED) {
                return false;
            }
            if (!headsetService.setActiveDevice(device)) {
                return false;
            }
            mHfpActiveDevice = device;
        }
        return true;
    }

    private boolean setHearingAidActiveDevice(@NonNull BluetoothDevice device) {
        return setHearingAidActiveDevice(device, false);
    }

    private boolean setHearingAidActiveDevice(@Nullable BluetoothDevice device,
            boolean hasFallbackDevice) {
        if (DBG) {
            Log.d(TAG, "setHearingAidActiveDevice(" + device + ")"
                    + (device == null ? " hasFallbackDevice=" + hasFallbackDevice : ""));
        }

        final HearingAidService hearingAidService = mFactory.getHearingAidService();
        if (hearingAidService == null) {
            return false;
        }

        synchronized (mLock) {
            if (device == null) {
                if (!hearingAidService.removeActiveDevice(!hasFallbackDevice)) {
                    return false;
                }
                mHearingAidActiveDevices.clear();
                return true;
            }

            long hiSyncId = hearingAidService.getHiSyncId(device);
            if (getHearingAidActiveHiSyncIdLocked() == hiSyncId) {
                mHearingAidActiveDevices.add(device);
                return true;
            }

            if (!hearingAidService.setActiveDevice(device)) {
                return false;
            }
            mHearingAidActiveDevices.clear();
            mHearingAidActiveDevices.addAll(hearingAidService.getConnectedPeerDevices(hiSyncId));
        }
        return true;
    }

    private boolean setLeAudioActiveDevice(@NonNull BluetoothDevice device) {
        return setLeAudioActiveDevice(device, false);
    }

    private boolean setLeAudioActiveDevice(@Nullable BluetoothDevice device,
            boolean hasFallbackDevice) {
        if (DBG) {
            Log.d(TAG, "setLeAudioActiveDevice(" + device + ")"
                    + (device == null ? " hasFallbackDevice=" + hasFallbackDevice : ""));
        }
        synchronized (mLock) {
            final LeAudioService leAudioService = mFactory.getLeAudioService();
            if (leAudioService == null) {
                return false;
            }
            boolean success;
            if (device == null) {
                success = leAudioService.removeActiveDevice(hasFallbackDevice);
            } else {
                if (Flags.leaudioActiveDeviceManagerGroupHandlingFix()) {
                    if ((mLeAudioActiveDevice != null)
                            && (Objects.equals(
                                    mLeAudioActiveDevice, leAudioService.getLeadDevice(device)))) {
                        if (DBG) {
                            Log.d(TAG, "New LeAudioDevice is a part of an active group");
                        }
                        return true;
                    }
                }
                success = leAudioService.setActiveDevice(device);
            }

            if (!success) {
                return false;
            }

            if (Flags.leaudioActiveDeviceManagerGroupHandlingFix()) {
                mLeAudioActiveDevice = leAudioService.getLeadDevice(device);
            } else {
                mLeAudioActiveDevice = device;
            }

            if (device == null) {
                mLeHearingAidActiveDevice = null;
                mPendingLeHearingAidActiveDevice.remove(device);
            }
        }
        return true;
    }

    private boolean setLeHearingAidActiveDevice(BluetoothDevice device) {
        synchronized (mLock) {
            if (!Objects.equals(mLeAudioActiveDevice, device)) {
                if (!setLeAudioActiveDevice(device)) {
                    return false;
                }
            }
            if (Objects.equals(mLeAudioActiveDevice, device)) {
                // setLeAudioActiveDevice succeed
                mLeHearingAidActiveDevice = device;
                mPendingLeHearingAidActiveDevice.remove(device);
                return true;
            }
        }
        return false;
    }

    /**
     * TODO: This method can return true when a fallback device for an unrelated profile is found.
     *       Take disconnected profile as an argument, and find the exact fallback device.
     *       Also, split this method to smaller methods for better readability.
     *
     * @return true when the fallback device is activated, false otherwise
     */
    @GuardedBy("mLock")
    private boolean setFallbackDeviceActiveLocked() {
        if (DBG) {
            Log.d(TAG, "setFallbackDeviceActive");
        }
        mDbManager = mAdapterService.getDatabase();
        List<BluetoothDevice> connectedHearingAidDevices = new ArrayList<>();
        if (!mHearingAidConnectedDevices.isEmpty()) {
            connectedHearingAidDevices.addAll(mHearingAidConnectedDevices);
        }
        if (!mLeHearingAidConnectedDevices.isEmpty()) {
            connectedHearingAidDevices.addAll(mLeHearingAidConnectedDevices);
        }
        if (!connectedHearingAidDevices.isEmpty()) {
            BluetoothDevice device =
                    mDbManager.getMostRecentlyConnectedDevicesInList(connectedHearingAidDevices);
            if (device != null) {
                if (mHearingAidConnectedDevices.contains(device)) {
                    if (DBG) {
                        Log.d(TAG, "Found a hearing aid fallback device: " + device);
                    }
                    setHearingAidActiveDevice(device);
                    setA2dpActiveDevice(null, true);
                    setHfpActiveDevice(null);
                    setLeAudioActiveDevice(null, true);
                } else {
                    if (DBG) {
                        Log.d(TAG, "Found a LE hearing aid fallback device: " + device);
                    }
                    setLeHearingAidActiveDevice(device);
                    setHearingAidActiveDevice(null, true);
                    setA2dpActiveDevice(null, true);
                    setHfpActiveDevice(null);
                }
                return true;
            }
        }

        A2dpService a2dpService = mFactory.getA2dpService();
        BluetoothDevice a2dpFallbackDevice = null;
        if (a2dpService != null) {
            a2dpFallbackDevice = a2dpService.getFallbackDevice();
        }

        HeadsetService headsetService = mFactory.getHeadsetService();
        BluetoothDevice headsetFallbackDevice = null;
        if (headsetService != null) {
            headsetFallbackDevice = headsetService.getFallbackDevice();
        }

        List<BluetoothDevice> connectedDevices = new ArrayList<>();
        connectedDevices.addAll(mLeAudioConnectedDevices);
        switch (mAudioManager.getMode()) {
            case AudioManager.MODE_NORMAL:
                if (a2dpFallbackDevice != null) {
                    connectedDevices.add(a2dpFallbackDevice);
                }
                break;
            case AudioManager.MODE_RINGTONE:
                if (headsetFallbackDevice != null && headsetService.isInbandRingingEnabled()) {
                    connectedDevices.add(headsetFallbackDevice);
                }
                break;
            default:
                if (headsetFallbackDevice != null) {
                    connectedDevices.add(headsetFallbackDevice);
                }
        }
        BluetoothDevice device = mDbManager.getMostRecentlyConnectedDevicesInList(connectedDevices);
        if (device != null) {
            if (mAudioManager.getMode() == AudioManager.MODE_NORMAL) {
                if (Objects.equals(a2dpFallbackDevice, device)) {
                    if (DBG) {
                        Log.d(TAG, "Found an A2DP fallback device: " + device);
                    }
                    setA2dpActiveDevice(device);
                    if (Objects.equals(headsetFallbackDevice, device)) {
                        setHfpActiveDevice(device);
                    } else {
                        setHfpActiveDevice(null);
                    }
                    /* If dual mode is enabled, LEA will be made active once all supported
                        classic audio profiles are made active for the device. */
                    if (!Utils.isDualModeAudioEnabled()) {
                        setLeAudioActiveDevice(null, true);
                    }
                    setHearingAidActiveDevice(null, true);
                } else {
                    if (DBG) {
                        Log.d(TAG, "Found a LE audio fallback device: " + device);
                    }
                    if (!setLeAudioActiveDevice(device)) {
                        return false;
                    }

                    if (!Utils.isDualModeAudioEnabled()) {
                        setA2dpActiveDevice(null, true);
                        setHfpActiveDevice(null);
                    }
                    setHearingAidActiveDevice(null, true);
                }
            } else {
                if (Objects.equals(headsetFallbackDevice, device)) {
                    if (DBG) {
                        Log.d(TAG, "Found a HFP fallback device: " + device);
                    }
                    setHfpActiveDevice(device);
                    if (Objects.equals(a2dpFallbackDevice, device)) {
                        setA2dpActiveDevice(a2dpFallbackDevice);
                    } else {
                        setA2dpActiveDevice(null, true);
                    }
                    if (!Utils.isDualModeAudioEnabled()) {
                        setLeAudioActiveDevice(null, true);
                    }
                    setHearingAidActiveDevice(null, true);
                } else {
                    if (DBG) {
                        Log.d(TAG, "Found a LE audio fallback device: " + device);
                    }
                    setLeAudioActiveDevice(device);
                    if (!Utils.isDualModeAudioEnabled()) {
                        setA2dpActiveDevice(null, true);
                        setHfpActiveDevice(null);
                    }
                    setHearingAidActiveDevice(null, true);
                }
            }
            return true;
        }

        if (DBG) {
            Log.d(TAG, "No fallback devices are found");
        }
        return false;
    }

    private void resetState() {
        synchronized (mLock) {
            mA2dpConnectedDevices.clear();
            mA2dpActiveDevice = null;

            mHfpConnectedDevices.clear();
            mHfpActiveDevice = null;

            mHearingAidConnectedDevices.clear();
            mHearingAidActiveDevices.clear();

            mLeAudioConnectedDevices.clear();
            mLeAudioActiveDevice = null;

            mLeHearingAidConnectedDevices.clear();
            mLeHearingAidActiveDevice = null;
            mPendingLeHearingAidActiveDevice.clear();
        }
    }

    @VisibleForTesting
    BluetoothDevice getA2dpActiveDevice() {
        return mA2dpActiveDevice;
    }

    @VisibleForTesting
    BluetoothDevice getHfpActiveDevice() {
        return mHfpActiveDevice;
    }

    @VisibleForTesting
    Set<BluetoothDevice> getHearingAidActiveDevices() {
        return mHearingAidActiveDevices;
    }

    @VisibleForTesting
    BluetoothDevice getLeAudioActiveDevice() {
        return mLeAudioActiveDevice;
    }

    @GuardedBy("mLock")
    private long getHearingAidActiveHiSyncIdLocked() {
        final HearingAidService hearingAidService = mFactory.getHearingAidService();
        if (hearingAidService != null && !mHearingAidActiveDevices.isEmpty()) {
            return hearingAidService.getHiSyncId(mHearingAidActiveDevices.iterator().next());
        }
        return BluetoothHearingAid.HI_SYNC_ID_INVALID;
    }

    /**
     * Checks CoD and metadata to determine if the device is a watch
     * @param device the remote device
     * @return {@code true} if it's a watch, {@code false} otherwise
     */
    private boolean isWatch(BluetoothDevice device) {
        // Check CoD
        BluetoothClass deviceClass = device.getBluetoothClass();
        if (deviceClass != null && deviceClass.getDeviceClass()
                == BluetoothClass.Device.WEARABLE_WRIST_WATCH) {
            return true;
        }

        // Check metadata
        byte[] deviceType = mDbManager.getCustomMeta(device, BluetoothDevice.METADATA_DEVICE_TYPE);
        if (deviceType == null) {
            return false;
        }
        String deviceTypeStr = new String(deviceType);
        if (deviceTypeStr.equals(BluetoothDevice.DEVICE_TYPE_WATCH)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if le audio broadcasting is ON
     *
     * @return {@code true} if is broadcasting audio, {@code false} otherwise
     */
    private boolean isBroadcastingAudio() {
        if (!Flags.leaudioBroadcastAudioHandoverPolicies()) {
            // disable this if feature flag is false
            return false;
        }

        final LeAudioService leAudioService = mFactory.getLeAudioService();
        return leAudioService != null && !leAudioService.getAllBroadcastMetadata().isEmpty();
    }

    /**
     * Called when a wired audio device is connected.
     * It might be called multiple times each time a wired audio device is connected.
     */
    @VisibleForTesting
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    void wiredAudioDeviceConnected() {
        if (DBG) {
            Log.d(TAG, "wiredAudioDeviceConnected");
        }
        setA2dpActiveDevice(null, true);
        setHfpActiveDevice(null);
        setHearingAidActiveDevice(null, true);
        setLeAudioActiveDevice(null, true);
    }
}
