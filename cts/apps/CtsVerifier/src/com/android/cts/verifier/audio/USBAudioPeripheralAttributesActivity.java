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

import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.R; // needed to access resource in CTSVerifier project namespace.
import com.android.cts.verifier.audio.audiolib.AudioUtils;
import com.android.cts.verifier.audio.peripheralprofile.ListsHelper;
import com.android.cts.verifier.audio.peripheralprofile.PeripheralProfile;

@CddTest(requirement = "7.7.2/H-1-1,H-4-4,H-4-5,H-4-6,H-4-7")
public class USBAudioPeripheralAttributesActivity extends USBAudioPeripheralActivity {
    private static final String TAG = "USBAudioPeripheralAttributesActivity";

    private TextView mInChanMasksTx;
    private TextView mInPosMasksTx;
    private TextView mInEncodingsTx;
    private TextView mInRatesTx;

    private TextView mOutChanMaskTx;
    private TextView mOutPosMasksTx;
    private TextView mOutEncodingsTx;
    private TextView mOutRatesTx;

    private TextView mTestStatusTx;

    private static final String NA_STRING = "----";

    public USBAudioPeripheralAttributesActivity() {
        super(true); // Mandated peripheral is required
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uap_attribs_panel);

        connectPeripheralStatusWidgets();

        mInChanMasksTx = (TextView) findViewById(R.id.uap_inChanMasksTx);
        mInPosMasksTx = (TextView) findViewById(R.id.uap_inPosMasksTx);
        mInEncodingsTx = (TextView) findViewById(R.id.uap_inEncodingsTx);
        mInRatesTx = (TextView) findViewById(R.id.uap_inRatesTx);

        mOutChanMaskTx = (TextView) findViewById(R.id.uap_outChanMasksTx);
        mOutPosMasksTx = (TextView) findViewById(R.id.uap_outPosMasksTx);
        mOutEncodingsTx = (TextView) findViewById(R.id.uap_outEncodingsTx);
        mOutRatesTx = (TextView) findViewById(R.id.uap_outRatesTx);

        mTestStatusTx = (TextView) findViewById(R.id.uap_attribsStatusTx);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.usbaudio_attribs_test, R.string.usbaudio_attribs_info, -1);

        connectUSBPeripheralUI();
    }

    //
    // USBAudioPeripheralActivity
    //
    @Override
    protected void setUsbAudioStatus(boolean supportsUsbAudio) {
        super.setUsbAudioStatus(supportsUsbAudio);
        if (!supportsUsbAudio) {
            mTestStatusTx.setText("Pass - No USB Host Mode Support.");
        }
    }

    // Helpers
    private void formatChannelIndexMasks(int[] masks, TextView tx) {
        StringBuilder sb = new StringBuilder();
        sb.append("  index:");
        for (int mask : masks) {
            sb.append(AudioUtils.channelIndexMaskToString(mask));
            sb.append(" ");
        }
        tx.setText(sb.toString());
    }

    private void formatChannelPositionMasks(int[] masks, boolean isInput, TextView tx) {
        StringBuilder sb = new StringBuilder();
        sb.append("  pos:");
        for (int mask : masks) {
            if (isInput) {
                sb.append(AudioUtils.channelInPositionMaskToString(mask));
            } else {
                sb.append(AudioUtils.channelOutPositionMaskToString(mask));
            }
            sb.append(" ");
        }
        tx.setText(sb.toString());
    }

    private void formatEncodings(int[] encodings, TextView tx) {
        StringBuilder sb = new StringBuilder();
        sb.append("  encodings:");
        for (int encoding : encodings) {
            sb.append(AudioUtils.encodingToString(encoding));
            sb.append(" ");
        }
        tx.setText(sb.toString());
    }

    private static void formatRates(int[] rates, TextView tx) {
        StringBuilder sb = new StringBuilder();
        sb.append("  rates:");
        for (int rate : rates) {
            sb.append(rate);
            sb.append(" ");
        }
        tx.setText(sb.toString());
    }

    private void updateConnectedPeripheralAttribs() {
        StringBuilder sb;

        // input
        if (mInputDevInfo != null) {
            // Channel Index Masks
            formatChannelIndexMasks(mInputDevInfo.getChannelIndexMasks(), mInChanMasksTx);

            // Channel Position Masks
            formatChannelPositionMasks(mInputDevInfo.getChannelMasks(), true, mInPosMasksTx);

            // encodings
            formatEncodings(mInputDevInfo.getEncodings(), mInEncodingsTx);

            // rates
            formatRates(mInputDevInfo.getSampleRates(), mInRatesTx);
        } else {
            // No input
            mInChanMasksTx.setText(NA_STRING);
            mInPosMasksTx.setText(NA_STRING);
            mInEncodingsTx.setText(NA_STRING);
            mInRatesTx.setText(NA_STRING);
        }

        // output
        if (mOutputDevInfo != null) {
            // Channel Index Masks
            formatChannelIndexMasks(mOutputDevInfo.getChannelIndexMasks(), mOutChanMaskTx);

            // Channel Position Masks
            formatChannelPositionMasks(
                    mOutputDevInfo.getChannelMasks(), false, mOutPosMasksTx);

            // encodings
            formatEncodings(mOutputDevInfo.getEncodings(), mOutEncodingsTx);

            // rates
            formatRates(mOutputDevInfo.getSampleRates(), mOutRatesTx);
        } else {
            mOutChanMaskTx.setText(NA_STRING);
            mOutPosMasksTx.setText(NA_STRING);
            mOutEncodingsTx.setText(NA_STRING);
            mOutRatesTx.setText(NA_STRING);
        }
    }

    public void updateConnectStatus() {
        updateConnectedPeripheralAttribs();

        boolean outPass = false;
        boolean inPass = false;
        if (mIsPeripheralAttached && mSelectedProfile != null) {
            boolean match = true;
            StringBuilder metaSb = new StringBuilder();

            // Outputs
            if (mOutputDevInfo != null) {
                AudioDeviceInfo deviceInfo = mOutputDevInfo;
                PeripheralProfile.ProfileAttributes attribs =
                    mSelectedProfile.getOutputAttributes();
                StringBuilder sb = new StringBuilder();

                // Channel Counts
                if (deviceInfo.getChannelCounts().length == 0) {
                    sb.append("Output - No Peripheral Channel Counts\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getChannelCounts(), attribs.mChannelCounts)) {
                    sb.append("Output - Channel Counts Mismatch" +
                            " d" + ListsHelper.textFormatDecimal(deviceInfo.getChannelCounts()) +
                            " p" + ListsHelper.textFormatDecimal(attribs.mChannelCounts) +"\n");
                }

                // Encodings
                if (deviceInfo.getEncodings().length == 0) {
                    sb.append("Output - No Peripheral Encodings\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getEncodings(), attribs.mEncodings)) {
                    sb.append("Output - Encodings Mismatch" +
                            " d" + ListsHelper.textFormatHex(deviceInfo.getEncodings()) +
                            " p" + ListsHelper.textFormatHex(attribs.mEncodings) + "\n");
                }

                // Sample Rates
                if (deviceInfo.getSampleRates().length == 0) {
                    sb.append("Output - No Peripheral Sample Rates\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getSampleRates(), attribs.mSampleRates)) {
                    sb.append("Output - Sample Rates Mismatch" +
                            " d" + ListsHelper.textFormatHex(deviceInfo.getSampleRates()) +
                            " p" + ListsHelper.textFormatHex(attribs.mSampleRates) + "\n");
                }

                // Do not compare channel masks here as the USB devices may only report
                // supported channel counts. In that case, different HALs may have different
                // mapping from channel count to channel mask.

                // Report
                if (sb.toString().length() == 0){
                    metaSb.append("Output - Match\n");
                    outPass = true;
                } else {
                    metaSb.append(sb.toString());
                }
            } else {
                // No output device to test, so pass it.
                outPass = true;
            }

            // Inputs
            if (mInputDevInfo != null) {
                AudioDeviceInfo deviceInfo = mInputDevInfo;
                PeripheralProfile.ProfileAttributes attribs =
                    mSelectedProfile.getInputAttributes();
                StringBuilder sb = new StringBuilder();

                // Channel Counts
                if (deviceInfo.getChannelCounts().length == 0) {
                    sb.append("Input - No Peripheral Channel Counts\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getChannelCounts(), attribs.mChannelCounts)) {
                    sb.append("Input - Channel Counts Mismatch" +
                            " d" + ListsHelper.textFormatDecimal(deviceInfo.getChannelCounts()) +
                            " p" + ListsHelper.textFormatDecimal(attribs.mChannelCounts) + "\n");
                }

                // Encodings
                if (deviceInfo.getEncodings().length == 0) {
                    sb.append("Input - No Peripheral Encodings\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getEncodings(), attribs.mEncodings)) {
                    sb.append("Input - Encodings Mismatch" +
                            " d" + ListsHelper.textFormatHex(deviceInfo.getEncodings()) +
                            " p" + ListsHelper.textFormatHex(attribs.mEncodings) + "\n");
                }

                // Sample Rates
                if (deviceInfo.getSampleRates().length == 0) {
                    sb.append("Input - No Peripheral Sample Rates\n");
                } else if (!ListsHelper.isSubset(deviceInfo.getSampleRates(), attribs.mSampleRates)) {
                    sb.append("Input - Sample Rates Mismatch" +
                            " d" + ListsHelper.textFormatDecimal(deviceInfo.getSampleRates()) +
                            " p" + ListsHelper.textFormatDecimal(attribs.mSampleRates) + "\n");
                }

                // Do not compare channel masks here as the USB devices may only report
                // supported channel counts. In that case, different HALs may have different
                // mapping from channel count to channel mask.
                if (sb.toString().length() == 0){
                    metaSb.append("Input - Match\n");
                    inPass = true;
                } else {
                    metaSb.append(sb.toString());
                }
            } else {
                // No input device, so pass it.
                inPass = true;
            }

            if (outPass && inPass) {
                metaSb.append(getResources().getString(R.string.audio_general_pass));
            } else {
                metaSb.append(getResources().getString(R.string.audio_general_fail));
            }
            mTestStatusTx.setText(metaSb.toString());
        } else {
            mTestStatusTx.setText("No Peripheral or No Matching Profile.");
        }

        mHasPassedTest = (outPass && inPass);
        // Headset not publicly available, violates CTS Verifier additional equipment guidelines.
        getPassButton().setEnabled(mHasPassedTest);
    }
}
