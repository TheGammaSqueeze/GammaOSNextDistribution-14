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

import android.annotation.Nullable;
import android.hardware.radio.Flags;
import android.hardware.radio.ProgramList;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioMetadata;
import android.hardware.radio.RadioTuner;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.android.car.broadcastradio.support.platform.ProgramInfoExt;

import com.google.android.car.kitchensink.R;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class RadioTunerFragment extends Fragment {

    private static final String TAG = RadioTunerFragment.class.getSimpleName();
    protected static final CharSequence NULL_TUNER_WARNING = "Tuner cannot be null";
    protected static final CharSequence TUNING_TEXT = "Tuning...";
    private static final CharSequence TUNING_COMPLETION_TEXT = "Tuning completes";

    protected final RadioTuner mRadioTuner;
    protected final RadioTestFragment.TunerListener mListener;
    private final ProgramList mProgramList;
    protected boolean mViewCreated = false;

    protected ProgramInfoAdapter mProgramInfoAdapter;

    private CheckBox mSeekChannelCheckBox;
    protected TextView mTuningTextView;
    private TextView mCurrentStationTextView;
    protected TextView mCurrentChannelTextView;
    private TextView mCurrentSongTitleTextView;
    private TextView mCurrentArtistTextView;

    RadioTunerFragment(RadioManager radioManager, int moduleId, Handler handler,
                       RadioTestFragment.TunerListener tunerListener) {
        mRadioTuner = radioManager.openTuner(moduleId, /* config= */ null, /* withAudio= */ true,
                new RadioTunerCallbackImpl(), handler);
        mListener = Objects.requireNonNull(tunerListener, "Tuner listener can not be null");
        if (mRadioTuner == null) {
            mProgramList =  null;
        } else {
            mProgramList = mRadioTuner.getDynamicProgramList(/* filter= */ null);
        }
    }

    RadioTuner getRadioTuner() {
        return mRadioTuner;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.radio_tuner_fragment, container,
                /* attachToRoot= */ false);
        Button closeButton = view.findViewById(R.id.button_radio_close);
        Button cancelButton = view.findViewById(R.id.button_radio_cancel);
        mTuningTextView = view.findViewById(R.id.text_tuning_status);
        mSeekChannelCheckBox = view.findViewById(R.id.selection_seek_skip_subchannels);
        Button seekUpButton = view.findViewById(R.id.button_radio_seek_up);
        Button seekDownButton = view.findViewById(R.id.button_radio_seek_down);
        ListView programListView = view.findViewById(R.id.radio_program_list);
        mCurrentStationTextView = view.findViewById(R.id.radio_current_station_info);
        mCurrentChannelTextView = view.findViewById(R.id.radio_current_channel_info);
        mCurrentSongTitleTextView = view.findViewById(R.id.radio_current_song_info);
        mCurrentArtistTextView = view.findViewById(R.id.radio_current_artist_info);

        registerProgramListListener();

        closeButton.setOnClickListener((v) -> handleClose());
        cancelButton.setOnClickListener((v) -> handleCancel());
        seekUpButton.setOnClickListener((v) -> handleSeek(RadioTuner.DIRECTION_UP));
        seekDownButton.setOnClickListener((v) -> handleSeek(RadioTuner.DIRECTION_DOWN));

        setupTunerView(view);
        programListView.setAdapter(mProgramInfoAdapter);

        mViewCreated = true;
        Log.i(TAG, "onCreateView done");
        return view;
    }

    void setupTunerView(View view) {
        mProgramInfoAdapter = new ProgramInfoAdapter(getContext(), R.layout.program_info_item,
                new RadioManager.ProgramInfo[]{}, this);
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "onDestroyView");
        handleClose();
        super.onDestroyView();
    }

    private void registerProgramListListener() {
        if (mProgramList == null) {
            Log.e(TAG, "Can not get program list");
            return;
        }
        OnCompleteListenerImpl onCompleteListener = new OnCompleteListenerImpl();
        mProgramList.addOnCompleteListener(getContext().getMainExecutor(), onCompleteListener);
    }

    void handleTune(ProgramSelector sel) {
        if (mRadioTuner == null) {
            mTuningTextView.setText(getString(R.string.radio_error, NULL_TUNER_WARNING));
            return;
        }
        mTuningTextView.setText(getString(R.string.radio_status, TUNING_TEXT));
        try {
            mRadioTuner.tune(sel);
        } catch (Exception e) {
            mTuningTextView.setText(getString(R.string.radio_error, e.getMessage()));
        }
        mListener.onTunerPlay();
    }

    private void handleSeek(int direction) {
        if (mRadioTuner == null) {
            mTuningTextView.setText(getString(R.string.radio_error, NULL_TUNER_WARNING));
            return;
        }
        mTuningTextView.setText(getString(R.string.radio_status, TUNING_TEXT));
        try {
            mRadioTuner.seek(direction, mSeekChannelCheckBox.isChecked());
        } catch (Exception e) {
            mTuningTextView.setText(getString(R.string.radio_error, e.getMessage()));
        }
        mListener.onTunerPlay();
    }

    private void handleClose() {
        if (mRadioTuner == null) {
            mTuningTextView.setText(getString(R.string.radio_error, NULL_TUNER_WARNING));
            return;
        }
        mTuningTextView.setText(getString(R.string.empty));
        try {
            mRadioTuner.close();
            mListener.onTunerClosed();
        } catch (Exception e) {
            mTuningTextView.setText(getString(R.string.radio_error, e.getMessage()));
        }
    }

    private void handleCancel() {
        if (mRadioTuner == null) {
            mTuningTextView.setText(getString(R.string.radio_error, NULL_TUNER_WARNING));
            return;
        }
        try {
            mRadioTuner.cancel();
        } catch (Exception e) {
            mTuningTextView.setText(getString(R.string.radio_error, e.getMessage()));
        }
        mTuningTextView.setText(getString(R.string.radio_status, "Canceled"));
    }

    private void setTuningStatus(RadioManager.ProgramInfo info) {
        if (!mViewCreated) {
            return;
        }
        if (info == null) {
            mTuningTextView.setText(getString(R.string.radio_error, "Program info is null"));
            return;
        } else if (info.getSelector().getPrimaryId().getType()
                != ProgramSelector.IDENTIFIER_TYPE_HD_STATION_ID_EXT) {
            if (mTuningTextView.getText().toString().contains(TUNING_TEXT)) {
                mTuningTextView.setText(getString(R.string.radio_status, TUNING_COMPLETION_TEXT));
            }
            return;
        }
        if (Flags.hdRadioImproved()) {
            if (info.isSignalAcquired()) {
                if (!info.isHdSisAvailable()) {
                    mTuningTextView.setText(getString(R.string.radio_status,
                            "Signal is acquired"));
                } else {
                    if (!info.isHdAudioAvailable()) {
                        mTuningTextView.setText(getString(R.string.radio_status,
                                "HD SIS is available"));
                    } else {
                        mTuningTextView.setText(getString(R.string.radio_status,
                                TUNING_COMPLETION_TEXT));
                    }
                }
            }
        } else {
            mTuningTextView.setText(getString(R.string.radio_status, TUNING_COMPLETION_TEXT));
        }
    }

    private void setProgramInfo(RadioManager.ProgramInfo info) {
        if (!mViewCreated) {
            return;
        }
        CharSequence channelText = getChannelName(info);
        mCurrentChannelTextView.setText(getString(R.string.radio_current_channel_info,
                channelText));
        mCurrentStationTextView.setText(getString(R.string.radio_current_station_info,
                getMetadataText(info, RadioMetadata.METADATA_KEY_RDS_PS)));
        mCurrentArtistTextView.setText(getString(R.string.radio_current_song_info,
                getMetadataText(info, RadioMetadata.METADATA_KEY_TITLE)));
        mCurrentSongTitleTextView.setText(getString(R.string.radio_current_artist_info,
                getMetadataText(info, RadioMetadata.METADATA_KEY_ARTIST)));
    }

    CharSequence getChannelName(RadioManager.ProgramInfo info) {
        return "";
    }

    CharSequence getMetadataText(RadioManager.ProgramInfo info, String metadataType) {
        String naText = getString(R.string.radio_na);
        if (info == null || info.getMetadata() == null) {
            return naText;
        }
        CharSequence metadataText = info.getMetadata().getString(metadataType);
        return metadataText == null ? naText : metadataText;
    }

    void updateConfigFlag(int flag, boolean value) {
    }

    private final class RadioTunerCallbackImpl extends RadioTuner.Callback {
        @Override
        public void onProgramInfoChanged(RadioManager.ProgramInfo info) {
            setProgramInfo(info);
            setTuningStatus(info);
        }

        @Override
        public void onConfigFlagUpdated(int flag, boolean value) {
            if (!mViewCreated) {
                return;
            }
            updateConfigFlag(flag, value);
        }

        @Override
        public void onTuneFailed(int result, @Nullable ProgramSelector selector) {
            if (!mViewCreated) {
                return;
            }
            String warning = "onTuneFailed:";
            if (selector != null) {
                warning += " for selector " + selector;
            }
            mTuningTextView.setText(getString(R.string.radio_error, warning));
        }
    }

    private final class OnCompleteListenerImpl implements ProgramList.OnCompleteListener {
        @Override
        public void onComplete() {
            if (mProgramList == null) {
                Log.e(TAG, "Program list is null");
            }
            List<RadioManager.ProgramInfo> list = mProgramList.toList();
            Comparator<RadioManager.ProgramInfo> selectorComparator =
                    new ProgramInfoExt.ProgramInfoComparator();
            list.sort(selectorComparator);
            mProgramInfoAdapter.updateProgramInfos(list.toArray(new RadioManager.ProgramInfo[0]));
        }
    }
}
