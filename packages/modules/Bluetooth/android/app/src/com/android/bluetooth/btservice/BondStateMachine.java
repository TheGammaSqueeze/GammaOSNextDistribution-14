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

package com.android.bluetooth.btservice;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import android.annotation.RequiresPermission;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.bluetooth.OobData;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
import com.android.bluetooth.btservice.RemoteDevices.DeviceProperties;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hfpclient.HeadsetClientService;
import com.android.bluetooth.hid.HidHostService;
import com.android.bluetooth.pbapclient.PbapClientService;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * This state machine handles Bluetooth Adapter State.
 * States:
 *      {@link StableState} :  No device is in bonding / unbonding state.
 *      {@link PendingCommandState} : Some device is in bonding / unbonding state.
 * TODO(BT) This class can be removed and this logic moved to the stack.
 */

final class BondStateMachine extends StateMachine {
    private static final String TAG = "BluetoothBondStateMachine";

    static final int CREATE_BOND = 1;
    static final int CANCEL_BOND = 2;
    static final int REMOVE_BOND = 3;
    static final int BONDING_STATE_CHANGE = 4;
    static final int SSP_REQUEST = 5;
    static final int PIN_REQUEST = 6;
    static final int UUID_UPDATE = 10;
    static final int BONDED_INTENT_DELAY = 11;
    static final int BOND_STATE_NONE = 0;
    static final int BOND_STATE_BONDING = 1;
    static final int BOND_STATE_BONDED = 2;

    static int sPendingUuidUpdateTimeoutMillis = 3000; // 3s

    private AdapterService mAdapterService;
    private AdapterProperties mAdapterProperties;
    private RemoteDevices mRemoteDevices;
    private BluetoothAdapter mAdapter;

    private PendingCommandState mPendingCommandState = new PendingCommandState();
    private StableState mStableState = new StableState();

    public static final String OOBDATAP192 = "oobdatap192";
    public static final String OOBDATAP256 = "oobdatap256";
    public static final String DISPLAY_PASSKEY = "display_passkey";
    public static final String DELAY_RETRY_COUNT = "delay_retry_count";
    public static final short DELAY_MAX_RETRIES = 30;
    public static final int BOND_RETRY_DELAY_MS = 500;

    @VisibleForTesting Set<BluetoothDevice> mPendingBondedDevices = new HashSet<>();

    private BondStateMachine(AdapterService service, AdapterProperties prop,
            RemoteDevices remoteDevices) {
        super("BondStateMachine:");
        addState(mStableState);
        addState(mPendingCommandState);
        mRemoteDevices = remoteDevices;
        mAdapterService = service;
        mAdapterProperties = prop;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        setInitialState(mStableState);
    }

    public static BondStateMachine make(AdapterService service, AdapterProperties prop,
            RemoteDevices remoteDevices) {
        Log.d(TAG, "make");
        BondStateMachine bsm = new BondStateMachine(service, prop, remoteDevices);
        bsm.start();
        return bsm;
    }

    public synchronized void doQuit() {
        quitNow();
    }

    private void cleanup() {
        mAdapterService = null;
        mRemoteDevices = null;
        mAdapterProperties = null;
    }

    @Override
    protected void onQuitting() {
        cleanup();
    }

    private class StableState extends State {
        @Override
        public void enter() {
            infoLog("StableState(): Entering Off State");
        }

        @Override
        public synchronized boolean processMessage(Message msg) {

            BluetoothDevice dev = (BluetoothDevice) msg.obj;

            switch (msg.what) {

                case CREATE_BOND:
                    /* BOND_BONDED event is send after keys are exchanged, but BTIF layer would
                    still use bonding control blocks until service discovery is finished. If
                    next pairing is started while previous still makes service discovery, it
                    would fail. Check the busy status of BTIF instead, and wait with starting
                    the bond. */
                    if (Flags.delayBondingWhenBusy()
                            && mAdapterService.getNative().pairingIsBusy()) {
                        short retry_no =
                                (msg.getData() != null)
                                        ? msg.getData().getShort(DELAY_RETRY_COUNT)
                                        : 0;
                        Log.d(
                                TAG,
                                "Delay CREATE_BOND because native is busy - attempt no "
                                        + retry_no);

                        if (retry_no < DELAY_MAX_RETRIES) {
                            retry_no++;

                            Message new_msg = obtainMessage();
                            new_msg.copyFrom(msg);

                            if (new_msg.getData() == null) {
                                Bundle bundle = new Bundle();
                                new_msg.setData(bundle);
                            }
                            new_msg.getData().putShort(DELAY_RETRY_COUNT, retry_no);

                            sendMessageDelayed(new_msg, BOND_RETRY_DELAY_MS);
                            return true;
                        } else {
                            Log.w(TAG, "Native was busy - the bond will most likely fail!");
                        }
                    }

                    OobData p192Data = (msg.getData() != null)
                            ? msg.getData().getParcelable(OOBDATAP192) : null;
                    OobData p256Data = (msg.getData() != null)
                            ? msg.getData().getParcelable(OOBDATAP256) : null;
                    createBond(dev, msg.arg1, p192Data, p256Data, true);
                    break;
                case REMOVE_BOND:
                    removeBond(dev, true);
                    break;
                case BONDING_STATE_CHANGE:
                    int newState = msg.arg1;
                /* if incoming pairing, transition to pending state */
                    if (newState == BluetoothDevice.BOND_BONDING) {
                        deferMessage(msg);
                        transitionTo(mPendingCommandState);
                    } else if (newState == BluetoothDevice.BOND_NONE) {
                    /* if the link key was deleted by the stack */
                        sendIntent(dev, newState, 0, false);
                    } else {
                        Log.e(TAG, "In stable state, received invalid newState: "
                                + state2str(newState));
                    }
                    break;
                case BONDED_INTENT_DELAY:
                    if (mPendingBondedDevices.contains(dev)) {
                        sendIntent(dev, BluetoothDevice.BOND_BONDED, 0, true);
                    }
                    break;
                case UUID_UPDATE:
                    if (mPendingBondedDevices.contains(dev)) {
                        sendIntent(dev, BluetoothDevice.BOND_BONDED, 0, false);
                    }
                    break;
                case CANCEL_BOND:
                default:
                    Log.e(TAG, "Received unhandled state: " + msg.what);
                    return false;
            }
            return true;
        }
    }

    private class PendingCommandState extends State {
        private final ArrayList<BluetoothDevice> mDevices = new ArrayList<BluetoothDevice>();

        @Override
        public void enter() {
            infoLog("Entering PendingCommandState State");
        }

        @Override
        public synchronized boolean processMessage(Message msg) {
            BluetoothDevice dev = (BluetoothDevice) msg.obj;

            DeviceProperties devProp = mRemoteDevices.getDeviceProperties(dev);
            boolean result = false;
            if ((mDevices.contains(dev) || mPendingBondedDevices.contains(dev))
                    && msg.what != CANCEL_BOND && msg.what != BONDING_STATE_CHANGE
                    && msg.what != SSP_REQUEST && msg.what != PIN_REQUEST) {
                deferMessage(msg);
                return true;
            }

            switch (msg.what) {
                case CREATE_BOND:
                    OobData p192Data = (msg.getData() != null)
                            ? msg.getData().getParcelable(OOBDATAP192) : null;
                    OobData p256Data = (msg.getData() != null)
                            ? msg.getData().getParcelable(OOBDATAP256) : null;
                    result = createBond(dev, msg.arg1, p192Data, p256Data, false);
                    break;
                case REMOVE_BOND:
                    result = removeBond(dev, false);
                    break;
                case CANCEL_BOND:
                    result = cancelBond(dev);
                    break;
                case BONDING_STATE_CHANGE:
                    int newState = msg.arg1;
                    int reason = getUnbondReasonFromHALCode(msg.arg2);
                    // Bond is explicitly removed if we are in pending command state
                    if (newState == BluetoothDevice.BOND_NONE
                            && reason == BluetoothDevice.BOND_SUCCESS) {
                        reason = BluetoothDevice.UNBOND_REASON_REMOVED;
                    }
                    sendIntent(dev, newState, reason, false);
                    if (newState != BluetoothDevice.BOND_BONDING) {
                        // This is either none/bonded, remove and transition, and also set
                        // result=false to avoid adding the device to mDevices.
                        mDevices.remove(dev);
                        result = false;
                        if (mDevices.isEmpty()) {
                            transitionTo(mStableState);
                        }
                        if (newState == BluetoothDevice.BOND_NONE) {
                            mAdapterService.setPhonebookAccessPermission(dev,
                                    BluetoothDevice.ACCESS_UNKNOWN);
                            mAdapterService.setMessageAccessPermission(dev,
                                    BluetoothDevice.ACCESS_UNKNOWN);
                            mAdapterService.setSimAccessPermission(dev,
                                    BluetoothDevice.ACCESS_UNKNOWN);
                            // Set the profile Priorities to undefined
                            clearProfilePriority(dev);
                        }
                    } else if (!mDevices.contains(dev)) {
                        result = true;
                    }
                    break;
                case SSP_REQUEST:
                    if (devProp == null) {
                        errorLog("devProp is null, maybe the device is disconnected");
                        break;
                    }

                    int passkey = msg.arg1;
                    int variant = msg.arg2;
                    boolean displayPasskey =
                            (msg.getData() != null)
                                    ? msg.getData().getByte(DISPLAY_PASSKEY) == 1 /* 1 == true */
                                    : false;
                    sendDisplayPinIntent(
                            devProp.getAddress(),
                            displayPasskey ? Optional.of(passkey) : Optional.empty(),
                            variant);
                    break;
                case PIN_REQUEST:
                    if (devProp == null) {
                        errorLog("devProp is null, maybe the device is disconnected");
                        break;
                    }

                    BluetoothClass btClass = dev.getBluetoothClass();
                    int btDeviceClass = btClass == null ? 0 : btClass.getDeviceClass();
                    if (btDeviceClass == BluetoothClass.Device.PERIPHERAL_KEYBOARD
                            || btDeviceClass
                                    == BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING) {
                        // Its a keyboard. Follow the HID spec recommendation of creating the
                        // passkey and displaying it to the user. If the keyboard doesn't follow
                        // the spec recommendation, check if the keyboard has a fixed PIN zero
                        // and pair.
                        //TODO: Maintain list of devices that have fixed pin
                        // Generate a variable 6-digit PIN in range of 100000-999999
                        // This is not truly random but good enough.
                        int pin = 100000 + (int) Math.floor((Math.random() * (999999 - 100000)));
                        sendDisplayPinIntent(
                                devProp.getAddress(),
                                Optional.of(pin),
                                BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN);
                        break;
                    }

                    if (msg.arg2 == 1) { // Minimum 16 digit pin required here
                        sendDisplayPinIntent(
                                devProp.getAddress(),
                                Optional.empty(),
                                BluetoothDevice.PAIRING_VARIANT_PIN_16_DIGITS);
                    } else {
                        // In PIN_REQUEST, there is no passkey to display.So do not send the
                        // EXTRA_PAIRING_KEY type in the intent
                        sendDisplayPinIntent(
                                devProp.getAddress(),
                                Optional.empty(),
                                BluetoothDevice.PAIRING_VARIANT_PIN);
                    }
                    break;
                default:
                    Log.e(TAG, "Received unhandled event:" + msg.what);
                    return false;
            }
            if (result) {
                mDevices.add(dev);
            }
            return true;
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private boolean cancelBond(BluetoothDevice dev) {
        if (dev.getBondState() == BluetoothDevice.BOND_BONDING) {
            byte[] addr = Utils.getBytesFromAddress(dev.getAddress());
            if (!mAdapterService.getNative().cancelBond(addr)) {
                Log.e(TAG, "Unexpected error while cancelling bond:");
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean removeBond(BluetoothDevice dev, boolean transition) {
        DeviceProperties devProp = mRemoteDevices.getDeviceProperties(dev);
        if (devProp != null && devProp.getBondState() == BluetoothDevice.BOND_BONDED) {
            byte[] addr = Utils.getBytesFromAddress(dev.getAddress());
            if (!mAdapterService.getNative().removeBond(addr)) {
                Log.e(TAG, "Unexpected error while removing bond:");
            } else {
                if (transition) {
                    transitionTo(mPendingCommandState);
                }
                return true;
            }
        }

        Log.w(
                TAG,
                dev.getAddressForLogging()
                        + " cannot be removed since "
                        + ((devProp == null)
                                ? "properties are empty"
                                : "bond state is " + devProp.getBondState()));
        return false;
    }

    @RequiresPermission(allOf = {
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.INTERACT_ACROSS_USERS,
    })
    private boolean createBond(BluetoothDevice dev, int transport, OobData remoteP192Data,
            OobData remoteP256Data, boolean transition) {
        if (dev.getBondState() == BluetoothDevice.BOND_NONE) {
            infoLog("Bond address is:" + dev + ", transport is: " + transport);
            byte[] addr = Utils.getBytesFromAddress(dev.getAddress());
            int addrType = dev.getAddressType();
            boolean result;
            // If we have some data
            if (remoteP192Data != null || remoteP256Data != null) {
                BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_BOND_STATE_CHANGED,
                      mAdapterService.obfuscateAddress(dev), transport, dev.getType(),
                      BluetoothDevice.BOND_BONDING,
                      BluetoothProtoEnums.BOND_SUB_STATE_LOCAL_START_PAIRING_OOB,
                      BluetoothProtoEnums.UNBOND_REASON_UNKNOWN, mAdapterService.getMetricId(dev));
                result =
                        mAdapterService
                                .getNative()
                                .createBondOutOfBand(
                                        addr, transport, remoteP192Data, remoteP256Data);
            } else {
                BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_BOND_STATE_CHANGED,
                      mAdapterService.obfuscateAddress(dev), transport, dev.getType(),
                      BluetoothDevice.BOND_BONDING,
                      BluetoothProtoEnums.BOND_SUB_STATE_LOCAL_START_PAIRING,
                      BluetoothProtoEnums.UNBOND_REASON_UNKNOWN, mAdapterService.getMetricId(dev));
                result = mAdapterService.getNative().createBond(addr, addrType, transport);
            }
            BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_DEVICE_NAME_REPORTED,
                    mAdapterService.getMetricId(dev), dev.getName());
            BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_BOND_STATE_CHANGED,
                    mAdapterService.obfuscateAddress(dev), transport, dev.getType(),
                    BluetoothDevice.BOND_BONDING,
                    remoteP192Data == null && remoteP256Data == null
                            ? BluetoothProtoEnums.BOND_SUB_STATE_UNKNOWN
                            : BluetoothProtoEnums.BOND_SUB_STATE_LOCAL_OOB_DATA_PROVIDED,
                    BluetoothProtoEnums.UNBOND_REASON_UNKNOWN);

            if (!result) {
                BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_BOND_STATE_CHANGED,
                        mAdapterService.obfuscateAddress(dev), transport, dev.getType(),
                        BluetoothDevice.BOND_NONE, BluetoothProtoEnums.BOND_SUB_STATE_UNKNOWN,
                        BluetoothDevice.UNBOND_REASON_REPEATED_ATTEMPTS);
                // Using UNBOND_REASON_REMOVED for legacy reason
                sendIntent(dev, BluetoothDevice.BOND_NONE, BluetoothDevice.UNBOND_REASON_REMOVED,
                        false);
                return false;
            } else if (transition) {
                transitionTo(mPendingCommandState);
            }
            return true;
        }
        return false;
    }

    // Defining these properly would break current api
    private static int PERIPHERAL_GAMEPAD = BluetoothClass.Device.Major.PERIPHERAL | 0x08;
    private static int PERIPHERAL_REMOTE = BluetoothClass.Device.Major.PERIPHERAL | 0x0C;

    private static List<Pair<String, Integer>> accConfirmSkip = new ArrayList<>();

    static {
        // Jarvis, SHIELD Remote 2015
        accConfirmSkip.add(new Pair<>("SHIELD Remote", PERIPHERAL_REMOTE));
        // Thunderstrike, SHIELD Controller 2017
        accConfirmSkip.add(new Pair<>("NVIDIA Controller v01.04", PERIPHERAL_GAMEPAD));
    };

    private boolean isSkipConfirmationAccessory(BluetoothDevice device) {
        for (Pair<String, Integer> entry : accConfirmSkip) {
            if (device.getName().equals(entry.first)
                    && device.getBluetoothClass().getDeviceClass() == entry.second) {
                return true;
            }
        }

        return false;
    }

    private void sendDisplayPinIntent(byte[] address, Optional<Integer> maybePin, int variant) {
        BluetoothDevice device = mRemoteDevices.getDevice(address);
        if (device != null && device.isBondingInitiatedLocally()
                && isSkipConfirmationAccessory(device)) {
            device.setPairingConfirmation(true);
            return;
        }
        Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevices.getDevice(address));
        maybePin.ifPresent(pin -> intent.putExtra(BluetoothDevice.EXTRA_PAIRING_KEY, pin));
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, variant);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        // Workaround for Android Auto until pre-accepting pairing requests is added.
        intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        Utils.sendOrderedBroadcast(mAdapterService, intent, BLUETOOTH_CONNECT,
                Utils.getTempAllowlistBroadcastOptions(), null/* resultReceiver */,
                null/* scheduler */, Activity.RESULT_OK/* initialCode */, null/* initialData */,
                null/* initialExtras */);
    }

    @VisibleForTesting
    @RequiresPermission(allOf = {
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.INTERACT_ACROSS_USERS,
    })
    void sendIntent(BluetoothDevice device, int newState, int reason,
            boolean isTriggerFromDelayMessage) {
        DeviceProperties devProp = mRemoteDevices.getDeviceProperties(device);
        int oldState = BluetoothDevice.BOND_NONE;
        if (newState != BluetoothDevice.BOND_NONE
                && newState != BluetoothDevice.BOND_BONDING
                && newState != BluetoothDevice.BOND_BONDED) {
            infoLog("Invalid bond state " + newState);
            return;
        }
        if (devProp != null) {
            oldState = devProp.getBondState();
        }
        if (isTriggerFromDelayMessage && (oldState != BluetoothDevice.BOND_BONDED
                || newState != BluetoothDevice.BOND_BONDED
                || !mPendingBondedDevices.contains(device))) {
            infoLog("Invalid state when doing delay send bonded intent, oldState: " + oldState
                    + ", newState: " + newState + ", in PendingBondedDevices list? "
                    + mPendingBondedDevices.contains(device));
            return;
        }
        if (mPendingBondedDevices.contains(device)) {
            mPendingBondedDevices.remove(device);
            if (oldState == BluetoothDevice.BOND_BONDED) {
                if (newState == BluetoothDevice.BOND_BONDING) {
                    mAdapterProperties.onBondStateChanged(device, newState);
                }
                oldState = BluetoothDevice.BOND_BONDING;
            } else {
                // Should not enter here.
                throw new IllegalArgumentException("Invalid old state " + oldState);
            }
        }
        if (oldState == newState) {
            return;
        }
        BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_BOND_STATE_CHANGED,
                mAdapterService.obfuscateAddress(device), 0, device.getType(),
                newState, BluetoothProtoEnums.BOND_SUB_STATE_LOCAL_BOND_STATE_INTENT_SENT, reason,
                mAdapterService.getMetricId(device));
        BluetoothClass deviceClass = device.getBluetoothClass();
        int classOfDevice = deviceClass == null ? 0 : deviceClass.getClassOfDevice();
        BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_CLASS_OF_DEVICE_REPORTED,
                mAdapterService.obfuscateAddress(device), classOfDevice,
                mAdapterService.getMetricId(device));
        mAdapterProperties.onBondStateChanged(device, newState);

        if (!isTriggerFromDelayMessage && newState == BluetoothDevice.BOND_BONDED
                && devProp != null && devProp.getUuids() == null) {
            infoLog(device + " is bonded, wait for SDP complete to broadcast bonded intent");
            if (!mPendingBondedDevices.contains(device)) {
                mPendingBondedDevices.add(device);
                Message msg = obtainMessage(BONDED_INTENT_DELAY);
                msg.obj = device;
                sendMessageDelayed(msg, sPendingUuidUpdateTimeoutMillis);
            }
            if (oldState == BluetoothDevice.BOND_NONE) {
                // Broadcast NONE->BONDING for NONE->BONDED case.
                newState = BluetoothDevice.BOND_BONDING;
            } else {
                return;
            }
        }

        mAdapterService.handleBondStateChanged(device, oldState, newState);

        if (newState == BluetoothDevice.BOND_NONE) {
            // Remove the permissions for unbonded devices
            mAdapterService.setMessageAccessPermission(device, BluetoothDevice.ACCESS_UNKNOWN);
            mAdapterService.setPhonebookAccessPermission(device, BluetoothDevice.ACCESS_UNKNOWN);
            mAdapterService.setSimAccessPermission(device, BluetoothDevice.ACCESS_UNKNOWN);
        }

        Intent intent = new Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_BOND_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, oldState);
        if (newState == BluetoothDevice.BOND_NONE) {
            intent.putExtra(BluetoothDevice.EXTRA_UNBOND_REASON, reason);
        }
        mAdapterService.onBondStateChanged(device, newState);
        mAdapterService.sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_CONNECT,
                Utils.getTempAllowlistBroadcastOptions());
        infoLog("Bond State Change Intent:" + device + " " + state2str(oldState) + " => "
                + state2str(newState));
    }

    void bondStateChangeCallback(int status, byte[] address, int newState, int hciReason) {
        BluetoothDevice device = mRemoteDevices.getDevice(address);

        if (device == null) {
            infoLog("No record of the device:" + device);
            // This device will be added as part of the BONDING_STATE_CHANGE intent processing
            // in sendIntent above
            device = mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
        }

        infoLog("bondStateChangeCallback: Status: " + status + " Address: " + device + " newState: "
                + newState + " hciReason: " + hciReason);

        Message msg = obtainMessage(BONDING_STATE_CHANGE);
        msg.obj = device;

        if (newState == BOND_STATE_BONDED) {
            msg.arg1 = BluetoothDevice.BOND_BONDED;
        } else if (newState == BOND_STATE_BONDING) {
            msg.arg1 = BluetoothDevice.BOND_BONDING;
        } else {
            msg.arg1 = BluetoothDevice.BOND_NONE;
        }
        msg.arg2 = status;

        sendMessage(msg);
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    void sspRequestCallback(byte[] address, byte[] name, int cod, int pairingVariant, int passkey) {
        //TODO(BT): Get wakelock and update name and cod
        BluetoothDevice bdDevice = mRemoteDevices.getDevice(address);
        if (bdDevice == null) {
            mRemoteDevices.addDeviceProperties(address);
        }
        infoLog("sspRequestCallback: " + Utils.getRedactedAddressStringFromByte(address)
                + " name: " + Arrays.toString(name)
                + " cod: " + cod
                + " pairingVariant " + pairingVariant
                + " passkey: " + (Build.isDebuggable() ? passkey : "******"));
        int variant;
        boolean displayPasskey = false;
        switch (pairingVariant) {

            case AbstractionLayer.BT_SSP_VARIANT_PASSKEY_CONFIRMATION:
                variant = BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION;
                displayPasskey = true;
                break;

            case AbstractionLayer.BT_SSP_VARIANT_CONSENT:
                variant = BluetoothDevice.PAIRING_VARIANT_CONSENT;
                break;

            case AbstractionLayer.BT_SSP_VARIANT_PASSKEY_ENTRY:
                variant = BluetoothDevice.PAIRING_VARIANT_PASSKEY;
                break;

            case AbstractionLayer.BT_SSP_VARIANT_PASSKEY_NOTIFICATION:
                variant = BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY;
                displayPasskey = true;
                break;

            default:
                errorLog("SSP Pairing variant not present");
                return;
        }
        BluetoothDevice device = mRemoteDevices.getDevice(address);
        if (device == null) {
            warnLog("Device is not known for:" + Utils.getRedactedAddressStringFromByte(address));
            mRemoteDevices.addDeviceProperties(address);
            device = Objects.requireNonNull(mRemoteDevices.getDevice(address));
        }

        BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_BOND_STATE_CHANGED,
                mAdapterService.obfuscateAddress(device), 0, device.getType(),
                BluetoothDevice.BOND_BONDING,
                BluetoothProtoEnums.BOND_SUB_STATE_LOCAL_SSP_REQUESTED, 0);

        Message msg = obtainMessage(SSP_REQUEST);
        msg.obj = device;
        if (displayPasskey) {
            msg.arg1 = passkey;
            Bundle bundle = new Bundle();
            bundle.putByte(BondStateMachine.DISPLAY_PASSKEY, (byte) 1 /* true */);
            msg.setData(bundle);
        }
        msg.arg2 = variant;
        sendMessage(msg);
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    void pinRequestCallback(byte[] address, byte[] name, int cod, boolean min16Digits) {
        //TODO(BT): Get wakelock and update name and cod

        BluetoothDevice bdDevice = mRemoteDevices.getDevice(address);
        if (bdDevice == null) {
            mRemoteDevices.addDeviceProperties(address);
            bdDevice = Objects.requireNonNull(mRemoteDevices.getDevice(address));
        }

        BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_BOND_STATE_CHANGED,
                mAdapterService.obfuscateAddress(bdDevice), 0, bdDevice.getType(),
                BluetoothDevice.BOND_BONDING,
                BluetoothProtoEnums.BOND_SUB_STATE_LOCAL_PIN_REQUESTED, 0);

        infoLog("pinRequestCallback: " + bdDevice
                + " name:" + Utils.getName(bdDevice) + " cod:" + new BluetoothClass(cod));

        Message msg = obtainMessage(PIN_REQUEST);
        msg.obj = bdDevice;
        msg.arg2 = min16Digits ? 1 : 0; // Use arg2 to pass the min16Digit boolean

        sendMessage(msg);
    }

    /*
     * Check whether has the specific message in message queue
     */
    @VisibleForTesting
    public boolean hasMessage(int what) {
        return hasMessages(what);
    }

    /*
     * Remove the specific message from message queue
     */
    @VisibleForTesting
    public void removeMessage(int what) {
        removeMessages(what);
    }

    @RequiresPermission(allOf = {
            android.Manifest.permission.BLUETOOTH_PRIVILEGED,
            android.Manifest.permission.MODIFY_PHONE_STATE,
    })
    private void clearProfilePriority(BluetoothDevice device) {
        HidHostService hidService = HidHostService.getHidHostService();
        A2dpService a2dpService = A2dpService.getA2dpService();
        HeadsetService headsetService = HeadsetService.getHeadsetService();
        HeadsetClientService headsetClientService = HeadsetClientService.getHeadsetClientService();
        A2dpSinkService a2dpSinkService = A2dpSinkService.getA2dpSinkService();
        PbapClientService pbapClientService = PbapClientService.getPbapClientService();

        if (hidService != null) {
            hidService.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        }
        if (a2dpService != null) {
            a2dpService.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        }
        if (headsetService != null) {
            headsetService.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        }
        if (headsetClientService != null) {
            headsetClientService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        }
        if (a2dpSinkService != null) {
            a2dpSinkService.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        }
        if (pbapClientService != null) {
            pbapClientService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        }
    }

    private String state2str(int state) {
        if (state == BluetoothDevice.BOND_NONE) {
            return "BOND_NONE";
        } else if (state == BluetoothDevice.BOND_BONDING) {
            return "BOND_BONDING";
        } else if (state == BluetoothDevice.BOND_BONDED) {
            return "BOND_BONDED";
        } else return "UNKNOWN(" + state + ")";
    }

    private void infoLog(String msg) {
        Log.i(TAG, msg);
    }

    private void errorLog(String msg) {
        Log.e(TAG, msg);
    }

    private void warnLog(String msg) {
        Log.w(TAG, msg);
    }

    private int getUnbondReasonFromHALCode(int reason) {
        if (reason == AbstractionLayer.BT_STATUS_SUCCESS) {
            return BluetoothDevice.BOND_SUCCESS;
        } else if (reason == AbstractionLayer.BT_STATUS_RMT_DEV_DOWN) {
            return BluetoothDevice.UNBOND_REASON_REMOTE_DEVICE_DOWN;
        } else if (reason == AbstractionLayer.BT_STATUS_AUTH_FAILURE) {
            return BluetoothDevice.UNBOND_REASON_AUTH_FAILED;
        } else if (reason == AbstractionLayer.BT_STATUS_AUTH_REJECTED) {
            return BluetoothDevice.UNBOND_REASON_AUTH_REJECTED;
        } else if (reason == AbstractionLayer.BT_STATUS_AUTH_TIMEOUT) {
            return BluetoothDevice.UNBOND_REASON_AUTH_TIMEOUT;
        }

        /* default */
        return BluetoothDevice.UNBOND_REASON_REMOVED;
    }
}
