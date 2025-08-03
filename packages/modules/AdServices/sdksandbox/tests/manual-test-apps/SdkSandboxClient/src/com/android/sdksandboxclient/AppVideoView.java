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

package com.android.sdksandboxclient;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * Displays a video view in the app. This can be used to test the differences between a video ad
 * from the sandbox vs. an app's VideoView.
 *
 * <p>Open this activity using the following command: adb shell am start -n
 * com.android.sdksandboxclient/.AppVideoView --es "video-url" "[video url]"
 */
public class AppVideoView extends Activity {
    static final String VIDEO_URL_KEY = "video-url";

    private PlayerView mPlayerView;
    private EditText mVideoUrlEdit;
    private Button mStartAppVideoButton;

    private ExoPlayer mPlayer;
    private MediaItem mCurrentMediaItem;
    private boolean mAutoPlay;
    private long mAutoPlayPosition;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_app_video);

        mPlayerView = findViewById(R.id.app_video);
        mVideoUrlEdit = findViewById(R.id.app_video_url_edit);
        mStartAppVideoButton = findViewById(R.id.start_app_video_button);

        registerStartAppVideoButton();

        final Bundle extras = getIntent().getExtras();
        final String videoUrl = extras == null ? null : extras.getString(VIDEO_URL_KEY);
        if (videoUrl != null) {
            mVideoUrlEdit.setText(videoUrl);
            startVideo(videoUrl);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        initializePlayer();
        mPlayerView.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
        mPlayerView.onPause();
        releasePlayer();
    }

    private void registerStartAppVideoButton() {
        mStartAppVideoButton.setOnClickListener(
                v -> {
                    final String videoUrl = mVideoUrlEdit.getText().toString();
                    startVideo(videoUrl);
                });
    }

    private void startVideo(String videoUrl) {
        mCurrentMediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
        mAutoPlayPosition = C.TIME_UNSET;
        mAutoPlay = true;
        if (mPlayer != null) {
            mPlayer.setMediaItem(mCurrentMediaItem);
            mPlayer.prepare();
            mPlayer.play();
        }
    }

    private void initializePlayer() {
        if (mPlayer != null) {
            return;
        }

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build();

        mPlayer = new ExoPlayer.Builder(this).setAudioAttributes(audioAttributes, true).build();
        mPlayer.setPlayWhenReady(mAutoPlay);

        mPlayerView.setPlayer(mPlayer);

        if (mCurrentMediaItem != null) {
            mPlayer.setMediaItem(mCurrentMediaItem);
            boolean haveStartPosition = mAutoPlayPosition != C.TIME_UNSET;
            if (haveStartPosition) {
                mPlayer.seekTo(0, mAutoPlayPosition);
            }
            mPlayer.prepare();
        }
    }

    private void releasePlayer() {
        if (mPlayer == null) {
            return;
        }

        mAutoPlay = mPlayer.getPlayWhenReady();
        mAutoPlayPosition = mPlayer.getContentPosition();

        mPlayer.release();
        mPlayer = null;

        mPlayerView.setPlayer(null);
    }
}
