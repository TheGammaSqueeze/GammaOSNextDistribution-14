/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import android.app.AlertDialog;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.peripheralprofile.PeripheralProfile;
import com.android.cts.verifier.audio.peripheralprofile.ProfileManager;

public abstract class USBAudioPeripheralActivity extends PassFailButtons.Activity {
    private static final String TAG = "USBAudioPeripheralActivity";
    private static final boolean DEBUG = false;

    // Host Mode Support
    protected boolean mHasHostMode;

    // Record if pass the test
    protected boolean mHasPassedTest;

    // Profile
    protected ProfileManager mProfileManager = new ProfileManager();
    protected PeripheralProfile mSelectedProfile;

    // Peripheral
    AudioManager mAudioManager;
    protected boolean mIsPeripheralAttached;
    protected AudioDeviceInfo mOutputDevInfo;
    protected AudioDeviceInfo mInputDevInfo;

    protected final boolean mIsMandatedRequired;

    // Widgets
    private TextView mProfileNameTx;
    private TextView mProfileDescriptionTx;

    private TextView mPeripheralNameTx;

    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    // ReportLog Schema
    private static final String KEY_CLAIMS_HOST = "claims_host_mode";

    //
    // Common UI Handling
    //
    protected void connectUSBPeripheralUI() {
        findViewById(R.id.uap_tests_yes_btn).setOnClickListener(mBtnClickListener);
        findViewById(R.id.uap_tests_no_btn).setOnClickListener(mBtnClickListener);
        findViewById(R.id.uap_test_info_btn).setOnClickListener(mBtnClickListener);

        // Leave the default state in tact
        // enableTestUI(false);
    }

    private void showUAPInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.uap_test_hostmode_info_caption)
                .setMessage(R.string.uap_test_hostmode_info_text)
                .setPositiveButton(R.string.audio_general_ok, null)
                .show();
    }

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.uap_tests_yes_btn) {
                mHasHostMode = true;
                setUsbAudioStatus(mHasHostMode);
            } else if (id == R.id.uap_tests_no_btn) {
                mHasHostMode = false;
                // If the device doesn't support USB host mode, no test need to run.
                // In that case, the test should always be passed.
                mHasPassedTest = true;
                setUsbAudioStatus(mHasHostMode);
            } else if (id == R.id.uap_test_info_btn) {
                showUAPInfoDialog();
            }
        }
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    private void recordUSBAudioStatus(boolean has) {
        getReportLog().addValue(
                KEY_CLAIMS_HOST,
                has,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    protected void setUsbAudioStatus(boolean has) {
        // ReportLog
        recordUSBAudioStatus(has);

        // UI & Pass/Fail status
        getPassButton().setEnabled(!mHasHostMode || mHasPassedTest);
        findViewById(R.id.uap_tests_yes_btn).setEnabled(mHasHostMode);
        findViewById(R.id.uap_tests_no_btn).setEnabled(!mHasHostMode);
    }

    public USBAudioPeripheralActivity(boolean mandatedRequired) {
        // determine if to show "UNSUPPORTED" if the mandated peripheral is required.
        mIsMandatedRequired = mandatedRequired;

        mProfileManager.loadProfiles();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        mAudioManager.registerAudioDeviceCallback(new ConnectListener(), new Handler());
    }

    protected void connectPeripheralStatusWidgets() {
        mProfileNameTx = (TextView)findViewById(R.id.uap_profileNameTx);
        mProfileDescriptionTx =
            (TextView)findViewById(R.id.uap_profileDescriptionTx);
        mPeripheralNameTx = (TextView)findViewById(R.id.uap_peripheralNameTx);
    }

    private void showProfileStatus() {
        if (DEBUG) {
            Log.d(TAG, "showProfileStatus()" + (mSelectedProfile != null));
        }
        if (mSelectedProfile != null) {
            mProfileNameTx.setText(mSelectedProfile.getName());
            mProfileDescriptionTx.setText(mSelectedProfile.getDescription());
        } else {
            mProfileNameTx.setText("");
            mProfileDescriptionTx.setText("");
        }
    }

    private void showPeripheralStatus() {
        if (mIsPeripheralAttached) {
            String productName = "";
            if (mOutputDevInfo != null) {
                productName = mOutputDevInfo.getProductName().toString();
            } else if (mInputDevInfo != null) {
                productName = mInputDevInfo.getProductName().toString();
            }
            String ctrlText;
            if (mSelectedProfile == null && mIsMandatedRequired) {
                ctrlText = productName + " - UNSUPPORTED";
            } else {
                ctrlText = productName;
            }
            mPeripheralNameTx.setText(ctrlText);
        } else {
            mPeripheralNameTx.setText("Disconnected");
        }
    }

    private void scanPeripheralList(AudioDeviceInfo[] devices) {
        // Can't just use the first record because then we will only get
        // Source OR sink, not both even on devices that are both.
        mOutputDevInfo = null;
        mInputDevInfo = null;

        // Any valid peripherals
        for(AudioDeviceInfo devInfo : devices) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_DEVICE ||
                devInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                if (devInfo.isSink()) {
                    mOutputDevInfo = devInfo;
                }
                if (devInfo.isSource()) {
                    mInputDevInfo = devInfo;
                }
            }
        }
        mIsPeripheralAttached = mOutputDevInfo != null || mInputDevInfo != null;
        if (DEBUG) {
            Log.d(TAG, "mIsPeripheralAttached: " + mIsPeripheralAttached);
        }

        // any associated profiles?
        if (mIsPeripheralAttached) {
            if (mOutputDevInfo != null) {
                mSelectedProfile =
                    mProfileManager.getProfile(mOutputDevInfo.getProductName().toString());
            } else if (mInputDevInfo != null) {
                mSelectedProfile =
                    mProfileManager.getProfile(mInputDevInfo.getProductName().toString());
            }
        } else {
            mSelectedProfile = null;
        }
    }

    private class ConnectListener extends AudioDeviceCallback {
        /*package*/ ConnectListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            // Log.i(TAG, "onAudioDevicesAdded() num:" + addedDevices.length);

            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));

            showProfileStatus();
            showPeripheralStatus();
            updateConnectStatus();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            // Log.i(TAG, "onAudioDevicesRemoved() num:" + removedDevices.length);

            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));

            showProfileStatus();
            showPeripheralStatus();
            updateConnectStatus();
        }
    }

    abstract public void updateConnectStatus();
}

