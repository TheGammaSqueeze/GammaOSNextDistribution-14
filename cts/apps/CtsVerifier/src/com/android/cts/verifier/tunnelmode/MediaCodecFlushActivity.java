package com.android.cts.verifier.tunnelmode;

import android.media.AudioManager;
import android.media.cts.MediaCodecTunneledPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.File;

/**
 * Test for verifying tunnel mode implementations properly handle content flushing. Plays a stream
 * in tunnel mode, pause it, flush it, resume, and user can mark Pass/Fail depending on quality of
 * the AV Sync. More details in go/atv-tunnel-mode-s.
 * TODO: Implement the actual test. This is a placeholder implementation until the test design is
 * stable and approved.
 */
public class MediaCodecFlushActivity extends PassFailButtons.Activity {
    private static final String TAG = MediaCodecFlushActivity.class.getSimpleName();

    private SurfaceHolder mHolder;
    private int mAudioSessionId = 0;
    private MediaCodecTunneledPlayer mPlayer;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sv_play);
        setPassFailButtonClickListeners();
        disablePassButton();

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surface);
        mHolder = surfaceView.getHolder();
        mHolder.addCallback(new SurfaceHolder.Callback(){
                public void surfaceCreated(SurfaceHolder holder) {
                    // TODO: Implement a start button, rather than playing the video as soon as the
                    // surface is ready
                    playVideo();
                }

                public void surfaceChanged(
                        SurfaceHolder holder, int format, int width, int height) {}
                public void surfaceDestroyed(SurfaceHolder holder) {}
            });

        mHandler = new Handler(Looper.getMainLooper());

        AudioManager am = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
        mAudioSessionId = am.generateAudioSessionId();

        mPlayer = new MediaCodecTunneledPlayer(this, mHolder, true, mAudioSessionId);

        // TODO: Do not rely on the video being pre-loaded on the device
        Uri mediaUri = Uri.fromFile(new File("/data/local/tmp/video.webm"));
        mPlayer.setVideoDataSource(mediaUri, null);
        mPlayer.setAudioDataSource(mediaUri, null);
    }

    private void playVideo() {
        try {
            mPlayer.prepare();
            mPlayer.startCodec();
            mPlayer.play();
            mHandler.postDelayed(this::pauseStep, 5000);
        } catch(Exception e) {
            Log.d(TAG, "Could not play video", e);
        }
    }

    private void pauseStep() {
        try {
            mPlayer.pause();
            mHandler.postDelayed(this::flushStep, 3000);
        } catch(Exception e) {
            Log.d(TAG, "Could not pause video", e);
        }
    }

    private void flushStep() {
        try {
            mPlayer.flush();
            mHandler.postDelayed(this::resumeStep, 3000);
        } catch(Exception e) {
            Log.d(TAG, "Could not flush video", e);
        }
    }

    private void resumeStep() {
        try {
            mPlayer.resume();
            mHandler.postDelayed(this::enablePassButton, 3000);
        } catch(Exception e) {
            Log.d(TAG, "Could not resume video", e);
        }
    }

    private void enablePassButton() {
        getPassButton().setEnabled(true);
    }

    private void disablePassButton() {
        getPassButton().setEnabled(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mPlayer != null) {
            mPlayer.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPlayer != null) {
            mPlayer.reset();
            mPlayer = null;
        }
    }
}
