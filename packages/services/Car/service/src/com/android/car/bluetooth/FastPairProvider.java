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

package com.android.car.bluetooth;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.car.builtin.util.Slogf;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.R;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;

/**
 * An advertiser for the Bluetooth LE based Fast Pair service. FastPairProvider enables easy
 * Bluetooth pairing between a peripheral and a phone participating in the Fast Pair Seeker role.
 * When the seeker finds a compatible peripheral a notification prompts the user to begin pairing if
 * desired.  A peripheral should call startAdvertising when it is appropriate to pair, and
 * stopAdvertising when pairing is complete or it is no longer appropriate to pair.
 */
public class FastPairProvider {
    private static final String TAG = CarLog.tagFor(FastPairProvider.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    static final String THREAD_NAME = "FastPairProvider";

    private final int mModelId;
    private final String mAntiSpoofKey;
    private final boolean mAutomaticAcceptance;
    private final Context mContext;
    private boolean mStarted;
    private int mScanMode;
    private final BluetoothAdapter mBluetoothAdapter;
    private final FastPairAdvertiser mFastPairAdvertiser;
    private FastPairGattServer mFastPairGattServer;
    private final FastPairAccountKeyStorage mFastPairAccountKeyStorage;

    FastPairAdvertiser.Callbacks mAdvertiserCallbacks = new FastPairAdvertiser.Callbacks() {
        @Override
        public void onRpaUpdated(BluetoothDevice device) {
            mFastPairGattServer.updateLocalRpa(device);
        }
    };

    FastPairGattServer.Callbacks mGattServerCallbacks = new FastPairGattServer.Callbacks() {
        @Override
        public void onPairingCompleted(boolean successful) {
            if (DBG) {
                Slogf.d(TAG, "onPairingCompleted %s", successful);
            }
            // TODO (243171615): Reassess advertising transitions against specification
            if (successful || mScanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                advertiseAccountKeys();
            }
        }
    };

    /**
     * Listen for changes in the Bluetooth adapter state and scan mode.
     *
     * When the adapter is
     * - ON: Ensure our GATT Server is up and that we are advertising either the model ID or account
     *       key filter, based on current scan mode.
     * - OTHERWISE: Ensure our GATT server is off.
     *
     * When the scan mode is:
     * - CONNECTABLE / DISCOVERABLE: Advertise the model ID if we are actively discovering as well.
     *   If we are not, then stop advertising temporarily. See below for why this is done.
     * - CONNECTABLE: Advertise account key filter
     * - NONE: Do not advertise anything.
     */
    BroadcastReceiver mDiscoveryModeChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case Intent.ACTION_USER_UNLOCKED:
                    if (DBG) {
                        Slogf.d(TAG, "User unlocked");
                    }
                    mFastPairAccountKeyStorage.load();
                    break;

                // TODO (243171615): Reassess advertising transitions against specification
                case BluetoothAdapter.ACTION_SCAN_MODE_CHANGED:
                    int newScanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE,
                            BluetoothAdapter.ERROR);
                    boolean isDiscovering = mBluetoothAdapter.isDiscovering();
                    boolean isFastPairing = mFastPairGattServer.isConnected();
                    if (DBG) {
                        Slogf.d(TAG, "Scan mode changed, old=%s, new=%s, discovering=%b,"
                                + " fastpairing=%b", BluetoothUtils.getScanModeName(mScanMode),
                                BluetoothUtils.getScanModeName(newScanMode), isDiscovering,
                                isFastPairing);
                    }
                    mScanMode = newScanMode;
                    if (mScanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                        // While the specification says we should always be advertising *something*
                        // it turns out the other applications implement other Fast Pair based
                        // features that also want to advertise (Smart Setup, for example, which is
                        // another Fast Pair based feature outside of BT Pairing facilitation).
                        // Seeker devices can only handle one 0xFE2C advertisement at a time. To
                        // reduce the chance of clashing, we only advertise our Model ID when we're
                        // sure we have the intent to pair. Otherwise, if we're in the discoverable
                        // state without intent to pair, then it may be another application. We stop
                        // advertising all together.
                        if (isDiscovering) {
                            advertiseModelId();
                        } else {
                            stopAdvertising();
                        }
                    } else if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                        advertiseAccountKeys();
                    }
                    break;

                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    int oldState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE,
                            BluetoothAdapter.ERROR);
                    if (DBG) {
                        Slogf.d(TAG, "Adapter state changed, old=%s, new=%s",
                                BluetoothUtils.getAdapterStateName(oldState),
                                BluetoothUtils.getAdapterStateName(newState));
                    }
                    if (newState == BluetoothAdapter.STATE_ON) {
                        startGatt();
                    } else {
                        stopGatt();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * FastPairProvider constructor which loads Fast Pair variables from the device specific
     * resource overlay.
     *
     * @param context user specific context on which all Bluetooth operations shall occur.
     */
    public FastPairProvider(Context context) {
        mContext = context;

        Resources res = mContext.getResources();
        mModelId = res.getInteger(R.integer.fastPairModelId);
        mAntiSpoofKey = res.getString(R.string.fastPairAntiSpoofKey);
        mAutomaticAcceptance = res.getBoolean(R.bool.fastPairAutomaticAcceptance);

        mBluetoothAdapter = mContext.getSystemService(BluetoothManager.class).getAdapter();
        mFastPairAccountKeyStorage = new FastPairAccountKeyStorage(mContext, 5);
        mFastPairAdvertiser = new FastPairAdvertiser(mContext);
        mFastPairGattServer = new FastPairGattServer(mContext, mModelId, mAntiSpoofKey,
                mGattServerCallbacks, mAutomaticAcceptance, mFastPairAccountKeyStorage);
    }

    /**
     * Determine if Fast Pair Provider is enabled based on the configuration parameters read in.
     */
    boolean isEnabled() {
        return !(mModelId == 0 || TextUtils.isEmpty(mAntiSpoofKey));
    }

    /**
     * Is the Fast Pair Provider Started
     *
     * Being started means our advertiser exists and we are listening for events that would signal
     * for us to create our GATT Server/Service.
     */
    boolean isStarted() {
        return mStarted;
    }

    /**
     * Start the Fast Pair provider which will register for Bluetooth broadcasts.
     */
    public void start() {
        if (mStarted) return;
        if (!isEnabled()) {
            Slogf.w(TAG, "Fast Pair Provider not configured, disabling, model=%d, key=%s",
                    mModelId, TextUtils.isEmpty(mAntiSpoofKey) ? "N/A" : "Set");
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mDiscoveryModeChanged, filter);
        mStarted = true;
    }

    /**
     * Stop the Fast Pair provider which will unregister the broadcast receiver.
     */
    public void stop() {
        if (!mStarted) return;
        stopGatt();
        stopAdvertising();
        mContext.unregisterReceiver(mDiscoveryModeChanged);
        mStarted = false;
    }

    void advertiseModelId() {
        if (DBG) Slogf.i(TAG, "Advertise model ID");
        mFastPairAdvertiser.stopAdvertising();
        mFastPairAdvertiser.advertiseModelId(mModelId, mAdvertiserCallbacks);
    }

    void advertiseAccountKeys() {
        if (DBG) Slogf.i(TAG, "Advertise account key filter");
        mFastPairAdvertiser.stopAdvertising();
        mFastPairAdvertiser.advertiseAccountKeys(mFastPairAccountKeyStorage.getAllAccountKeys(),
                mAdvertiserCallbacks);
    }

    void stopAdvertising() {
        if (DBG) Slogf.i(TAG, "Stop all advertising");
        mFastPairAdvertiser.stopAdvertising();
    }

    void startGatt() {
        if (DBG) Slogf.i(TAG, "Start Fast Pair GATT server");
        mFastPairGattServer.start();
    }

    void stopGatt() {
        if (DBG) Slogf.i(TAG, "Stop Fast Pair GATT server");
        mFastPairGattServer.stop();
    }

    /**
     * Dump current status of the Fast Pair provider
     *
     * This will get printed with the output of:
     * adb shell dumpsys activity service com.android.car/.CarPerUserService
     *
     * @param writer
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("FastPairProvider:");
        writer.increaseIndent();
        writer.println("Status         : " + (isEnabled() ? "Enabled" : "Disabled"));
        writer.println("Model ID       : " + mModelId);
        writer.println("Anti-Spoof Key : " + (TextUtils.isEmpty(mAntiSpoofKey) ? "N/A" : "Set"));
        writer.println("State          : " + (isEnabled() ? "Started" : "Stopped"));
        if (isEnabled()) {
            mFastPairAdvertiser.dump(writer);
            mFastPairGattServer.dump(writer);
            mFastPairAccountKeyStorage.dump(writer);
        }
        writer.decreaseIndent();
    }
}
