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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

abstract class AudioWiredDeviceBaseActivity extends PassFailButtons.Activity {
    private static final String TAG = AudioWiredDeviceBaseActivity.class.getSimpleName();

    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    private Button mSupportsBtn;
    private Button mDoesntSupportBtn;

    Button mStartBtn;
    Button mStopBtn;

    protected boolean mSupportsWiredPeripheral = true;
    protected String mConnectedPeripheralName;

    protected void enableTestButtons(boolean enabled) {
        mStartBtn.setEnabled(enabled);
        mStopBtn.setEnabled(!enabled);
    }

    protected abstract void calculatePass();

    private BroadcastReceiver mPluginReceiver = new PluginBroadcastReceiver();

    // ReportLog schema
    private static final String KEY_WIRED_PORT_SUPPORTED = "wired_port_supported";
    protected static final String KEY_SUPPORTS_PERIPHERALS = "supports_wired_peripherals";
    protected static final String KEY_ROUTING_RECEIVED = "routing_received";
    protected static final String KEY_CONNECTED_PERIPHERAL = "routing_connected_peripheral";

    AudioWiredDeviceBaseActivity() {
    }

    void connectProcessUI() {
        mStartBtn = (Button) findViewById(R.id.audio_routingnotification_startBtn);
        mStartBtn.setOnClickListener(mBtnClickListener);
        mStopBtn = (Button) findViewById(R.id.audio_routingnotification_stopBtn);
        mStopBtn.setOnClickListener(mBtnClickListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        this.registerReceiver(mPluginReceiver, filter);
    }

    @Override
    public void onPause() {
        // maybe this stuff in onStop()?
        this.unregisterReceiver(mPluginReceiver);
        super.onPause();
    }

    @Override
    public void onStop() {
        stopAudio();
        super.onStop();
    }

    //
    // UI Helpers
    //
    void hideHasWiredUI() {
        TextView tv = (TextView) findViewById(R.id.audio_wired_port_exists);
        tv.setText(getResources().getString(
                R.string.audio_disconnect_port_detected));
        mSupportsBtn.setVisibility(View.GONE);
        mDoesntSupportBtn.setVisibility(View.GONE);
    }

    protected void setup() {
        // The "Honor" system buttons
        (mSupportsBtn =
                (Button) findViewById(R.id.audio_wired_yes)).setOnClickListener(mBtnClickListener);
        (mDoesntSupportBtn =
                (Button) findViewById(R.id.audio_wired_no)).setOnClickListener(mBtnClickListener);
    }

    void startAudio() {
        // NOP by default
    }

    void stopAudio() {
        // NOP by default
    }

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.audio_wired_no) {
                mSupportsWiredPeripheral = false;
                mSupportsBtn.setEnabled(false);
                enableTestButtons(mSupportsWiredPeripheral);
                calculatePass();
            } else if (id == R.id.audio_wired_yes) {
                mSupportsWiredPeripheral = true;
                mDoesntSupportBtn.setEnabled(false);
                enableTestButtons(mSupportsWiredPeripheral);
                calculatePass();
            } else if (v.getId() == R.id.audio_routingnotification_startBtn) {
                startAudio();
            } else if (v.getId() == R.id.audio_routingnotification_stopBtn) {
                stopAudio();
            }
        }
    }

    /**
     * Receive a broadcast Intent when a headset is plugged in or unplugged.
     */
    public class PluginBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mSupportsWiredPeripheral = true;
            hideHasWiredUI();
            enableTestButtons(mSupportsWiredPeripheral);
        }
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    @Override
    public void recordTestResults() {
        super.recordTestResults();

        // Subclasses should submit after adding their data
        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_WIRED_PORT_SUPPORTED,
                mSupportsWiredPeripheral ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CONNECTED_PERIPHERAL,
                mConnectedPeripheralName,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }
}
