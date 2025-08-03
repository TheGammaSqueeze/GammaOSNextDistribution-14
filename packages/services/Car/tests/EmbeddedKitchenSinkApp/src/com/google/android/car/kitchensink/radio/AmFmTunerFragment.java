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
 * limitations under the License.
 */

package com.google.android.car.kitchensink.radio;

import android.hardware.radio.Flags;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioTuner;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;

import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;

import com.google.android.car.kitchensink.R;

public final class AmFmTunerFragment extends RadioTunerFragment {

    private int mLastFmFrequency;
    private int mLastAmFrequency;
    private boolean mIsFmBand = true;

    private RadioButton mFmRadioButton;
    private RadioButton mAmRadioButton;
    private Switch mFmHdSwitch;
    private Switch mAmHdSwitch;
    private EditText mFrequencyInput;
    private RadioGroup mAmFmBandSelection;

    AmFmTunerFragment(RadioManager radioManager, int moduleId,
                      RadioManager.BandConfig fmBandConfig, RadioManager.BandConfig amBandConfig,
                      Handler handler, RadioTestFragment.TunerListener tunerListener) {
        super(radioManager, moduleId, handler, tunerListener);
        mLastFmFrequency = fmBandConfig.getLowerLimit();
        mLastAmFrequency = amBandConfig.getLowerLimit();
    }

    @Override
    void setupTunerView(View view) {
        mFmHdSwitch = view.findViewById(R.id.toggle_fm_hd_state);
        mAmHdSwitch = view.findViewById(R.id.toggle_am_hd_state);
        mFrequencyInput = view.findViewById(R.id.input_am_fm_frequency);
        mAmFmBandSelection = view.findViewById(R.id.button_fm_am_selection);
        mFmRadioButton = view.findViewById(R.id.button_radio_fm);
        mAmRadioButton = view.findViewById(R.id.button_radio_am);
        Button tuneButton = view.findViewById(R.id.button_radio_tune);
        Button stepUpButton = view.findViewById(R.id.button_radio_step_up);
        Button stepDownButton = view.findViewById(R.id.button_radio_step_down);

        mFmHdSwitch.setVisibility(View.VISIBLE);
        view.findViewById(R.id.text_fm_hd_state).setVisibility(View.VISIBLE);
        view.findViewById(R.id.layout_tune).setVisibility(View.VISIBLE);
        view.findViewById(R.id.layout_step).setVisibility(View.VISIBLE);

        if (Flags.hdRadioImproved()) {
            if (mRadioTuner.isConfigFlagSupported(RadioManager.CONFIG_FORCE_ANALOG_AM)) {
                mAmHdSwitch.setVisibility(View.VISIBLE);
                view.findViewById(R.id.text_am_hd_state).setVisibility(View.VISIBLE);
                mAmHdSwitch.setChecked(!mRadioTuner.isConfigFlagSet(
                        RadioManager.CONFIG_FORCE_ANALOG_AM));
            }
            if (!mRadioTuner.isConfigFlagSupported(RadioManager.CONFIG_FORCE_ANALOG_FM)) {
                mFmHdSwitch.setVisibility(View.INVISIBLE);
                view.findViewById(R.id.text_fm_hd_state).setVisibility(View.INVISIBLE);
            } else {
                mFmHdSwitch.setChecked(!mRadioTuner.isConfigFlagSet(
                        RadioManager.CONFIG_FORCE_ANALOG_FM));
            }
        } else {
            if (!mRadioTuner.isConfigFlagSupported(RadioManager.CONFIG_FORCE_ANALOG)) {
                mFmHdSwitch.setVisibility(View.INVISIBLE);
                view.findViewById(R.id.text_fm_hd_state).setVisibility(View.INVISIBLE);
            } else {
                mFmHdSwitch.setChecked(!mRadioTuner.isConfigFlagSet(
                        RadioManager.CONFIG_FORCE_ANALOG));
            }
        }
        mFmHdSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> handleHdEnable(/* isFm= */ true, isChecked));
        mAmHdSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> handleHdEnable(/* isFm= */ false, isChecked));
        tuneButton.setOnClickListener((v) -> tuneToInputStation());
        mFmRadioButton.setOnClickListener((v) -> handleBandSwitching(/* isFm= */ true));
        mAmRadioButton.setOnClickListener((v) -> handleBandSwitching(/* isFm= */ false));
        if (mIsFmBand) {
            mFmRadioButton.setChecked(true);
            mFrequencyInput.setText(ProgramSelectorExt.formatAmFmFrequency(mLastFmFrequency,
                    ProgramSelectorExt.NAME_NO_MODULATION));
        } else {
            mAmRadioButton.setChecked(true);
            mFrequencyInput.setText(ProgramSelectorExt.formatAmFmFrequency(mLastAmFrequency,
                    ProgramSelectorExt.NAME_NO_MODULATION));
        }
        stepUpButton.setOnClickListener((v) -> handleStep(RadioTuner.DIRECTION_UP));
        stepDownButton.setOnClickListener((v) -> handleStep(RadioTuner.DIRECTION_DOWN));

        mProgramInfoAdapter = new AmFmProgramInfoAdapter(getContext(), R.layout.program_info_item,
                new RadioManager.ProgramInfo[]{}, this);
    }

    private void tuneToInputStation() {
        int selectedButtonId = mAmFmBandSelection.getCheckedRadioButtonId();
        ProgramSelector sel;
        try {
            double frequencyInput = Double.parseDouble(mFrequencyInput.getText().toString());
            switch (selectedButtonId) {
                case R.id.button_radio_fm:
                    int fmFrequency = (int) Math.round(frequencyInput * 1_000);
                    sel = ProgramSelector.createAmFmSelector(RadioManager.BAND_FM, fmFrequency);
                    break;
                case R.id.button_radio_am:
                    int amFrequency = (int) Math.round(frequencyInput);
                    sel = ProgramSelector.createAmFmSelector(RadioManager.BAND_AM, amFrequency);
                    break;
                default:
                    mTuningTextView.setText(getString(R.string.radio_error,
                            "Unsupported input selector type"));
                    return;
            }
        } catch (Exception e) {
            mTuningTextView.setText(getString(R.string.radio_error, e.getMessage()));
            return;
        }
        handleTune(sel);
    }

    private void handleBandSwitching(boolean isFm) {
        if (isFm) {
            mFrequencyInput.setText(ProgramSelectorExt.formatAmFmFrequency(mLastFmFrequency,
                    ProgramSelectorExt.NAME_NO_MODULATION));
        } else {
            mFrequencyInput.setText(ProgramSelectorExt.formatAmFmFrequency(mLastAmFrequency,
                    ProgramSelectorExt.NAME_NO_MODULATION));
        }
        mIsFmBand = isFm;
        ProgramSelector initialSel = isFm ? ProgramSelector.createAmFmSelector(
                RadioManager.BAND_FM, mLastFmFrequency)
                : ProgramSelector.createAmFmSelector(RadioManager.BAND_AM, mLastAmFrequency);
        handleTune(initialSel);
    }

    private void handleStep(int direction) {
        if (mRadioTuner == null) {
            mTuningTextView.setText(getString(R.string.radio_error, NULL_TUNER_WARNING));
            return;
        }
        mTuningTextView.setText(getString(R.string.radio_status, TUNING_TEXT));
        try {
            mRadioTuner.step(direction, /* skipSubChannel= */ false);
        } catch (Exception e) {
            mTuningTextView.setText(getString(R.string.radio_error, e.getMessage()));
        }
        mListener.onTunerPlay();
    }

    private void handleHdEnable(boolean isFm, boolean hdEnabled) {
        if (mRadioTuner == null) {
            mTuningTextView.setText(getString(R.string.radio_error, NULL_TUNER_WARNING));
            return;
        }
        mTuningTextView.setText(getString(R.string.empty));
        int configFlag;
        if (Flags.hdRadioImproved()) {
            configFlag = isFm ? RadioManager.CONFIG_FORCE_ANALOG_FM
                    : RadioManager.CONFIG_FORCE_ANALOG_AM;
        } else {
            configFlag = RadioManager.CONFIG_FORCE_ANALOG;
        }
        try {
            mRadioTuner.setConfigFlag(configFlag, !hdEnabled);
        } catch (Exception e) {
            mTuningTextView.setText(getString(R.string.radio_error, e.getMessage()));
        }
    }

    @Override
    CharSequence getChannelName(RadioManager.ProgramInfo info) {
        CharSequence channelText = null;
        if (info != null) {
            int primaryIdType = info.getSelector().getPrimaryId().getType();
            if (primaryIdType == ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY
                    || primaryIdType == ProgramSelector.IDENTIFIER_TYPE_HD_STATION_ID_EXT) {
                channelText = ProgramSelectorExt.getDisplayName(info.getSelector(), /* flags= */ 0);
                long amFmFrequency = ProgramSelectorExt.getFrequency(info.getSelector());
                mIsFmBand = ProgramSelectorExt.isFmFrequency(amFmFrequency);
                if (mIsFmBand) {
                    mLastFmFrequency = (int) amFmFrequency;
                } else if (ProgramSelectorExt.isAmFrequency(amFmFrequency)) {
                    mLastAmFrequency = (int) amFmFrequency;
                }
            }
        }
        if (mViewCreated) {
            if (channelText == null) {
                channelText = getString(R.string.radio_na);
            }
            if (mIsFmBand) {
                if (!mFmRadioButton.isChecked()) {
                    mFmRadioButton.setChecked(true);
                }
            } else {
                if (!mAmRadioButton.isChecked()) {
                    mAmRadioButton.setChecked(true);
                }
            }
        }
        return channelText;
    }

    @Override
    void updateConfigFlag(int flag, boolean value) {
        if (flag == RadioManager.CONFIG_FORCE_ANALOG) {
            mFmHdSwitch.setChecked(!value);
            mAmHdSwitch.setChecked(!value);
        } else if (Flags.hdRadioImproved()) {
            if (flag == RadioManager.CONFIG_FORCE_ANALOG_FM) {
                mFmHdSwitch.setChecked(!value);
            } else if (flag == RadioManager.CONFIG_FORCE_ANALOG_AM) {
                mAmHdSwitch.setChecked(!value);
            }
        }
    }
}
