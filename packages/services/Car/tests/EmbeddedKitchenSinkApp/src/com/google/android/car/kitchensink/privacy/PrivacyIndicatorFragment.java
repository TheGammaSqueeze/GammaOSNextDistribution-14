/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.car.kitchensink.privacy;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.annotation.Nullable;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.io.IOException;

public class PrivacyIndicatorFragment extends Fragment {

    public static final String TAG = "PrivacyChip";
    public static final String FRAGMENT_NAME = "privacy_chip";
    private static final String PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;
    private boolean mFullScreen;
    private boolean mRecording;
    private MediaRecorder mMediaRecorder;
    private Button mMicView;
    private TextView mPermissionText;
    private boolean mHasAudioPermission;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.privacy_chip, container, false);

        getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mFullScreen = true;
        mRecording = false;
        mMicView = view.findViewById(R.id.mic_icon);
        Button barView = view.findViewById(R.id.bar_icon);
        mPermissionText = view.findViewById(R.id.permission_text);

        requestAudioPermission();

        barView.setOnClickListener(
                v -> {
                    if (mFullScreen) {
                        getActivity().getWindow()
                                .clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    } else {
                        getActivity().getWindow()
                                .setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    }
                    mFullScreen = !mFullScreen;
                }
        );

        mMicView.setOnClickListener(v -> {
            if (mRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });

        return view;
    }

    private void requestAudioPermission() {
        if (getContext().checkCallingOrSelfPermission(PERMISSION_RECORD_AUDIO)
                == PERMISSION_GRANTED) {
            setAudioPermissionEnableStatus();
            return;
        } else {
            setAudioPermissionDisableStatus();
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            setAudioPermissionEnableStatus();
                        }
                    }).launch(PERMISSION_RECORD_AUDIO);
        }
    }

    private void startRecording() {
        if (!mHasAudioPermission) {
            String msg = "Audio permission is not granted!";
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            Log.d(TAG, msg);
            return;
        }
        // Record to the external cache directory for visibility
        String fileName = getActivity().getExternalCacheDir().getAbsolutePath();
        fileName += "/audiorecordtest.3gp";
        mMediaRecorder = new MediaRecorder(getActivity().getApplicationContext());
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaRecorder.setOutputFile(fileName);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e("Privacy Chip Testing App", "prepare() failed");
        }

        mMediaRecorder.start();
        mMicView.setTextColor(Color.GREEN);
        mRecording = true;
    }

    private void stopRecording() {
        mRecording = false;
        mMicView.setTextColor(Color.WHITE);
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    private void setAudioPermissionEnableStatus() {
        mPermissionText.setText(R.string.audio_permission_granted);
        mHasAudioPermission = true;
    }

    private void setAudioPermissionDisableStatus() {
        mPermissionText.setText(R.string.audio_permission_not_granted);
        mHasAudioPermission = false;
    }
}
