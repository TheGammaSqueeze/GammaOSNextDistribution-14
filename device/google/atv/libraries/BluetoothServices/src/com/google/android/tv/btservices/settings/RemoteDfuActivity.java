/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.tv.btservices.settings;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.tv.btservices.BluetoothDeviceService;
import com.google.android.tv.btservices.BluetoothUtils;
import com.google.android.tv.btservices.Configuration;
import com.google.android.tv.btservices.R;
import com.google.android.tv.btservices.remote.DfuManager;
import com.google.android.tv.btservices.remote.RemoteProxy.DfuResult;
import com.google.android.tv.btservices.SimplifiedConnection;

public class RemoteDfuActivity extends Activity implements DfuManager.Listener {

    public static final String EXTRA_BT_ADDRESS = "extra_bt_address";

    private static final String TAG = "Atv.RmtDfuActivity";

    private static final int BEFORE_FINISH_DELAY_MS = 4000;
    private static final int PROGRESS_BAR_MAX = 100;
    private static final int PLEASE_WAIT_TIMEOUT_MS = 5000;

    private final ServiceConnection mBtDeviceServiceConnection = new SimplifiedConnection() {

        @Override
        protected void cleanUp() {
            if (mBtDeviceServiceBinder != null) {
                mBtDeviceServiceBinder.removeListener(RemoteDfuActivity.this);
            }
            mBtDeviceServiceBound = false;
            mBtDeviceServiceBinder = null;
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBtDeviceServiceBinder = (BluetoothDeviceService.LocalBinder) service;
            mBtDeviceServiceBound = true;
            mBtDeviceServiceBinder.addListener(RemoteDfuActivity.this);
            mHandler.post(RemoteDfuActivity.this::attemptDfu);
        }
    };

    private BluetoothDevice mDevice;
    private String mBtAddress;
    private boolean mBtDeviceServiceBound;
    private BluetoothDeviceService.LocalBinder mBtDeviceServiceBinder;
    private final Handler mHandler = new Handler();
    private ImageView mRemoteIcon;
    private TextView mTitleView;
    private TextView mSummaryView;
    private ProgressBar mProgressBar;
    private ImageView mCheckIcon;
    private Runnable mPleaseWaitTimeout = this::onErrorBeforeUpgradeFinished;

    private static final int NO_UPGRADE_ERR = R.string.settings_bt_update_not_necessary;
    private static final int UPDATE_ERR = R.string.settings_bt_update_error;
    private static final int BEFORE_UPDATE_FINISHED_ERR = R.string.settings_bt_update_failed;
    private static final int LOW_BATTERY_ERR = R.string.settings_bt_battery_low;

    private void error(int stringResId) {
        // No longer care about the "Please wait" state
        if (mHandler.hasCallbacks(mPleaseWaitTimeout)) {
            mHandler.removeCallbacks(mPleaseWaitTimeout);
        }
        mTitleView.setText(stringResId);
        Log.w(TAG, "error: " + getString(stringResId));
        mSummaryView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.GONE);
        mHandler.postDelayed(this::finish, BEFORE_FINISH_DELAY_MS);
    }

    private void onErrorBeforeUpgradeFinished() {
        error(BEFORE_UPDATE_FINISHED_ERR);
    }

    private void onDone() {
        // We've recovered from the "Please wait" state.
        if (mHandler.hasCallbacks(mPleaseWaitTimeout)) {
            mHandler.removeCallbacks(mPleaseWaitTimeout);
        }

        mProgressBar.setVisibility(View.VISIBLE);
        mCheckIcon.setVisibility(View.VISIBLE);
        mHandler.postDelayed(this::finish, BEFORE_FINISH_DELAY_MS);
    }

    private void onDoneNeedsPairing() {
        // We've recovered from the "Please wait" state.
        if (mHandler.hasCallbacks(mPleaseWaitTimeout)) {
            mHandler.removeCallbacks(mPleaseWaitTimeout);
        }

        Runnable showDone = () -> {
            mProgressBar.setVisibility(View.VISIBLE);
            mCheckIcon.setVisibility(View.VISIBLE);
        };
        Runnable showPairingNeeded = () -> {
            mProgressBar.setVisibility(View.INVISIBLE);
            mCheckIcon.setVisibility(View.INVISIBLE);
            mSummaryView.setVisibility(View.INVISIBLE);
            mTitleView.setVisibility(View.VISIBLE);
            mTitleView.setText(R.string.settings_bt_update_needs_repair);
        };
        Runnable startPairingAndFinish = () -> {
            BluetoothDeviceService.forgetAndRepair(this, mDevice);
            finish();
        };

        final long beforePairingMsgMs = BEFORE_FINISH_DELAY_MS;
        mHandler.post(showDone);
        mHandler.postDelayed(showPairingNeeded, beforePairingMsgMs);
        mHandler.postDelayed(startPairingAndFinish, beforePairingMsgMs + BEFORE_FINISH_DELAY_MS);
    }

    private void onProgress(double progress) {
        // We've recovered from the "Please wait" state.
        if (mHandler.hasCallbacks(mPleaseWaitTimeout)) {
            mHandler.removeCallbacks(mPleaseWaitTimeout);
        }

        mProgressBar.setVisibility(View.VISIBLE);
        mProgressBar.setProgress((int) Math.round(progress * PROGRESS_BAR_MAX));
    }


    private void onUnknownState() {
        // We've entered an unknown state. It could be that update has failed. In this case, post a
        // "Please wait" message to the user and then timeout.
        mTitleView.setText(R.string.settings_bt_update_please_wait);
        mSummaryView.setVisibility(View.GONE);
        mHandler.removeCallbacks(mPleaseWaitTimeout);
        mHandler.postDelayed(mPleaseWaitTimeout, PLEASE_WAIT_TIMEOUT_MS);
    }

    private void attemptDfu() {
        if (mBtDeviceServiceBinder == null) {
            error(UPDATE_ERR);
            return;
        }

        if (TextUtils.isEmpty(mBtAddress)) {
            Log.e(TAG, "bt address is empty");
            error(UPDATE_ERR);
            return;
        }

        if (!mBtDeviceServiceBinder.hasUpgrade(mDevice)) {
            error(NO_UPGRADE_ERR);
            return;
        }

        if (mBtDeviceServiceBinder.isBatteryLow(mDevice)) {
            error(LOW_BATTERY_ERR);
            return;
        }
        mBtDeviceServiceBinder.startDfu(mDevice);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remote_dfu);

        mRemoteIcon = findViewById(R.id.icon);
        mTitleView = findViewById(R.id.fullscreen_title);
        mSummaryView = findViewById(R.id.fullscreen_summary);
        mProgressBar = findViewById(R.id.fullscreen_progressbar);
        mCheckIcon = findViewById(R.id.check_icon);
        mProgressBar.setMin(0);
        mProgressBar.setMax(PROGRESS_BAR_MAX);

        mRemoteIcon.setClipToOutline(false);
        if (!showRemoteControlIcon()) {
            mRemoteIcon.setVisibility(View.INVISIBLE);
        }

        Intent intent = getIntent();
        mBtAddress = intent.getStringExtra(EXTRA_BT_ADDRESS);
        mDevice = BluetoothDeviceService.findDevice(mBtAddress);

        if (TextUtils.isEmpty(mBtAddress)) {
            error(UPDATE_ERR);
            return;
        }
        if (!mBtDeviceServiceBound) {
            bindService(new Intent(this, BluetoothUtils.getBluetoothDeviceServiceClass(this)),
                    mBtDeviceServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onDestroy() {
        if (mBtDeviceServiceBound) {
            mBtDeviceServiceBinder.removeListener(this);
            unbindService(mBtDeviceServiceConnection);
        }
        super.onDestroy();
    }

    // DfuManager.Listener implementation
    @Override
    public void onDfuProgress(BluetoothDevice device, DfuResult state) {
        if (!TextUtils.equals(mBtAddress, device.getAddress())) {
            return;
        }

        if (state == null) {
            Log.e(TAG, "unknown state");
            mHandler.post(this::onUnknownState);
            return;
        }

        final double progress = state.progress();
        switch (state.code()) {
            case DfuResult.SUCCESS:
                Log.i(TAG, "onDfuProgress: success");
                mHandler.post(this::onDone);
                break;
            case DfuResult.SUCCESS_NEEDS_PAIRING:
                Log.i(TAG, "onDfuProgress: success needs pairing");
                mHandler.post(this::onDoneNeedsPairing);
                break;
            case DfuResult.IN_PROGRESS:
                mHandler.post(() -> onProgress(progress));
                break;
            case DfuResult.UNKNOWN_FAILURE:
            case DfuResult.GATT_DISCONNECTED:
                Log.i(TAG, "onDfuProgress: other=" + state.code());
            default:
                mHandler.post(this::onErrorBeforeUpgradeFinished);
                break;
        }
    }

    private boolean showRemoteControlIcon() {
        return Configuration.get(this).isEnabled(R.bool.show_remote_icon_in_dfu);
    }
}
