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

package com.android.cts.verifier.tunnelmode;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.cts.MediaCodecTunneledPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Test for verifying that the Volume level changes are reflected both with and without tunnel mode
 * playback for PCM audio
 */
public class VolumeLevelChangesActivity extends PassFailButtons.Activity {

    private static final String TAG = VolumeLevelChangesActivity.class.getSimpleName();

    private SurfaceHolder mHolder;
    private int mAudioSessionId = 0;
    private MediaCodecTunneledPlayer mPlayer;
    private Handler mHandler;

    private Integer mCurrentStep;
    private static final int STEP_ADJUST_VOLUME_FOR_NON_TUNNELED_PLAYBACK = 1;
    private static final int STEP_ASK_USER_ABOUT_TUNNEL_VOLUME = 2;
    private static final int STEP_ADJUST_VOLUME_FOR_TUNNELED_PLAYBACK = 3;
    private static final int STEP_ASK_USER_ABOUT_NON_TUNNEL_VOLUME = 4;
    private static final int STEP_DONE = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tunnel_mode_volume_changes);
        enablePassButton(false);

        AudioManager am = getApplicationContext().getSystemService(AudioManager.class);
        mAudioSessionId = am.generateAudioSessionId();

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surface);
        mHolder = surfaceView.getHolder();
        mHolder.addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) {
                mCurrentStep = STEP_ADJUST_VOLUME_FOR_NON_TUNNELED_PLAYBACK;
                process(mCurrentStep);
            }

            public void surfaceChanged(
                    SurfaceHolder holder, int format, int width, int height) {
            }

            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        });

        mHandler = new Handler(Looper.getMainLooper());
    }

    private Uri getAsResourceUri(int resId) {
        Resources res = getResources();
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(res.getResourcePackageName(resId))
                .appendPath(res.getResourceTypeName(resId))
                .appendPath(res.getResourceEntryName(resId))
                .build();
    }

    private void enablePassButton(boolean enable) {
        getPassButton().setEnabled(enable);
    }

    /** Handler for ok button */
    public void onOkClicked(View view) {
        mCurrentStep = getNextStep(mCurrentStep);
        process(mCurrentStep);
    }

    private void process(int step) {
        switch (step) {
            case STEP_ADJUST_VOLUME_FOR_NON_TUNNELED_PLAYBACK: {
                restartVideo(/* shouldUseTunnelMode= */false);

                TextView instruction = findViewById(R.id.instruction);
                instruction.setText(R.string.increase_volume_level);

                Button okButton = findViewById(R.id.okButton);
                okButton.setText(R.string.ok_button_volume_level);

                Button cancelButton = findViewById(R.id.cancelButton);
                cancelButton.setVisibility(View.INVISIBLE);

                break;
            }
            case STEP_ASK_USER_ABOUT_TUNNEL_VOLUME:
            case STEP_ASK_USER_ABOUT_NON_TUNNEL_VOLUME: {
                boolean shouldUseTunnelMode = (step == STEP_ASK_USER_ABOUT_TUNNEL_VOLUME);
                restartVideo(shouldUseTunnelMode);

                TextView instruction = findViewById(R.id.instruction);
                instruction.setText(R.string.is_same_volume);

                Button okButton = findViewById(R.id.okButton);
                okButton.setText(R.string.yes_button_volume_level);

                Button cancelButton = findViewById(R.id.cancelButton);
                cancelButton.setText(R.string.no_button_volume_level);
                cancelButton.setVisibility(View.VISIBLE);

                break;
            }
            case STEP_ADJUST_VOLUME_FOR_TUNNELED_PLAYBACK: {
                TextView instruction = findViewById(R.id.instruction);
                instruction.setText(R.string.decrease_volume_level);

                Button okButton = findViewById(R.id.okButton);
                okButton.setText(R.string.ok_button_volume_level);

                Button cancelButton = findViewById(R.id.cancelButton);
                cancelButton.setVisibility(View.INVISIBLE);

                break;
            }
            case STEP_DONE: {
                stopVideo();
                setTestResultAndFinish(true);
                break;
            }
        }
    }

    private int getNextStep(int step) {
        switch (step) {
            case STEP_ADJUST_VOLUME_FOR_NON_TUNNELED_PLAYBACK:
                return STEP_ASK_USER_ABOUT_TUNNEL_VOLUME;
            case STEP_ASK_USER_ABOUT_TUNNEL_VOLUME:
                return STEP_ADJUST_VOLUME_FOR_TUNNELED_PLAYBACK;
            case STEP_ADJUST_VOLUME_FOR_TUNNELED_PLAYBACK:
                return STEP_ASK_USER_ABOUT_NON_TUNNEL_VOLUME;
            case STEP_ASK_USER_ABOUT_NON_TUNNEL_VOLUME:
                return STEP_DONE;
            default:
                return -1;
        }
    }

    private void restartVideo(boolean shouldUseTunnelMode) {
        if (mPlayer != null) {
            stopVideo();
        }
        mPlayer = new MediaCodecTunneledPlayer(this, mHolder, shouldUseTunnelMode, mAudioSessionId);

        Uri mediaUri = getAsResourceUri(
                R.raw.bbb_s4_1280x720_vp9_0p31_4mbps_30fps_stereo_128kbps_48000hz);
        mPlayer.setVideoDataSource(mediaUri, null);
        mPlayer.setAudioDataSource(mediaUri, null);

        playVideo();
    }

    /** Handler for cancel button */
    public void onCancelClicked(View view) {
        int dialogMessage =
                (mCurrentStep == STEP_ASK_USER_ABOUT_TUNNEL_VOLUME)
                        ? R.string.volume_level_changes_failure_non_tunnel_to_tunnel
                        : R.string.volume_level_changes_failure_tunnel_to_non_tunnel;

        new AlertDialog.Builder(view.getContext())
                .setMessage(dialogMessage)
                .setCancelable(true)
                .setPositiveButton(R.string.ok_button_volume_level,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                stopVideo();
                                setTestResultAndFinish(false);
                                dialog.cancel();
                            }
                        })
                .show();
    }

    private void playVideo() {
        try {
            mPlayer.prepare();
            mPlayer.setLoopEnabled(true);
            mPlayer.startCodec();
            mPlayer.play();
        } catch (Exception e) {
            Log.d(TAG, "Could not play the video.", e);
        }
    }

    private void stopVideo() {
        mPlayer.pause();
        mPlayer.reset();
    }

}
