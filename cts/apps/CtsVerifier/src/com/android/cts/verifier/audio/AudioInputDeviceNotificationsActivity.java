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
 * limitations under the License.
 */

package com.android.cts.verifier.audio;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;

/**
 * Tests Audio Device Connection events for output by prompting the user to insert/remove a
 * wired headset (or microphone) and noting the presence (or absence) of notifications.
 */
public class AudioInputDeviceNotificationsActivity extends AudioWiredDeviceBaseActivity {
    Context mContext;

    TextView mConnectView;
    TextView mDisconnectView;
    TextView mInfoView;

    boolean mHandledInitialAddedMessage = false;
    boolean mConnectReceived = false;
    boolean mDisconnectReceived = false;

    private class TestAudioDeviceCallback extends AudioDeviceCallback {
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            // we will get this message when we setup the handler, so ignore the first one.
            if (!mHandledInitialAddedMessage) {
                mHandledInitialAddedMessage = true;
                return;
            }
            if (addedDevices.length != 0) {
                AudioDeviceInfo devInfo = addedDevices[addedDevices.length - 1];
                mConnectView.setText(
                        mContext.getResources().getString(
                                R.string.audio_dev_notification_connectMsg,
                                AudioDeviceUtils.formatDeviceName(devInfo)));
                mConnectReceived = true;
                getPassButton().setEnabled(mConnectReceived && mDisconnectReceived);
            }
        }

        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            if (removedDevices.length != 0) {
                AudioDeviceInfo devInfo = removedDevices[removedDevices.length - 1];
                mDisconnectView.setText(
                        mContext.getResources().getString(
                                R.string.audio_dev_notification_disconnectMsg,
                                AudioDeviceUtils.formatDeviceName(devInfo)));
                mDisconnectReceived = true;
                getPassButton().setEnabled(mConnectReceived && mDisconnectReceived);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_dev_notify);

        mContext = this;

        mConnectView = (TextView) findViewById(R.id.audio_dev_notification_connect_msg);
        mDisconnectView = (TextView) findViewById(R.id.audio_dev_notification_disconnect_msg);

        mInfoView = (TextView) findViewById(R.id.info_text);
        mInfoView.setText(mContext.getResources().getString(
                R.string.audio_devices_notification_instructions));

        findViewById(R.id.audio_dev_notification_connect_clearmsgs_btn)
                .setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        mConnectView.setText("");
                        mConnectReceived = false;
                        mDisconnectView.setText("");
                        mDisconnectReceived = false;
                        calculatePass();
                    }
                });

        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        audioManager.registerAudioDeviceCallback(new TestAudioDeviceCallback(), null);

        // "Honor System" buttons
        super.setup();

        setInfoResources(R.string.audio_in_devices_notifications_test,
                R.string.audio_in_devices_infotext, -1);
        setPassFailButtonClickListeners();

        calculatePass();
    }

    @Override
    protected void enableTestButtons(boolean enabled) {
        mInfoView.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void calculatePass() {
        getPassButton().setEnabled(!mSupportsWiredPeripheral
                || (mConnectReceived && mDisconnectReceived));
    }
}
