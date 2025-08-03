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

package android.media.bettertogether.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.hardware.hdmi.HdmiControlManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link MediaSessionTestActivity} which has called {@link Activity#setMediaController}.
 */
@NonMainlineTest
@LargeTest
@RunWith(AndroidJUnit4.class)
public class MediaActivityTest {
    private static final String TAG = "MediaActivityTest";
    private static final int WAIT_TIME_MS = 5000;
    private static final int TIME_SLICE = 50;
    private static final List<Integer> ALL_VOLUME_STREAMS = new ArrayList();
    static {
        ALL_VOLUME_STREAMS.add(AudioManager.STREAM_ACCESSIBILITY);
        ALL_VOLUME_STREAMS.add(AudioManager.STREAM_ALARM);
        ALL_VOLUME_STREAMS.add(AudioManager.STREAM_DTMF);
        ALL_VOLUME_STREAMS.add(AudioManager.STREAM_MUSIC);
        ALL_VOLUME_STREAMS.add(AudioManager.STREAM_NOTIFICATION);
        ALL_VOLUME_STREAMS.add(AudioManager.STREAM_RING);
        ALL_VOLUME_STREAMS.add(AudioManager.STREAM_SYSTEM);
        ALL_VOLUME_STREAMS.add(AudioManager.STREAM_VOICE_CALL);
    }

    private Instrumentation mInstrumentation;
    private Context mContext;
    private ActivityScenario<MediaSessionTestActivity> mActivityScenario;
    private Activity mActivity;

    private boolean mUseFixedVolume;
    private AudioManager mAudioManager;
    private Map<Integer, Integer> mStreamVolumeMap = new HashMap<>();
    private MediaSession mSession;

    private HdmiControlManager mHdmiControlManager;
    private int mHdmiEnableStatus;

    @Before
    public void setUp() throws Exception {

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.HDMI_CEC);

        mContext = mInstrumentation.getContext();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mUseFixedVolume = mAudioManager.isVolumeFixed();
        mHdmiControlManager = mContext.getSystemService(HdmiControlManager.class);
        if (mHdmiControlManager != null) {
            mHdmiEnableStatus = mHdmiControlManager.getHdmiCecEnabled();
            mHdmiControlManager.setHdmiCecEnabled(HdmiControlManager.HDMI_CEC_CONTROL_DISABLED);
        }

        mStreamVolumeMap.clear();
        for (Integer stream : ALL_VOLUME_STREAMS) {
            mStreamVolumeMap.put(stream, mAudioManager.getStreamVolume(stream));
        }

        mSession = new MediaSession(mContext, TAG);

        // Set volume stream other than STREAM_MUSIC.
        // STREAM_MUSIC is the new default stream for changing volume, so it doesn't precisely test
        // whether the session is prioritized for volume control or not.
        mSession.setPlaybackToLocal(new AudioAttributes.Builder()
                .setLegacyStreamType(AudioManager.STREAM_RING).build());

        Intent intent = new Intent(mContext, MediaSessionTestActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MediaSessionTestActivity.KEY_SESSION_TOKEN, mSession.getSessionToken());

        mActivityScenario = ActivityScenario.launch(intent);
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        mActivityScenario.onActivity(activity -> {
            mActivity = activity;
            activityReferenceObtained.open();
        });
        activityReferenceObtained.block(/* timeoutMs= */ 10000);

        assertWithMessage("Failed to acquire activity reference.")
                .that(mActivity)
                .isNotNull();

        assertWithMessage(
                "Failed to bring MediaSessionTestActivity due to the screen lock setting. Ensure "
                        + "screen lock isn't set before running CTS test.")
                .that(mActivity.getMediaController())
                .isNotNull();

        mInstrumentation.waitForIdleSync();
    }

    @After
    public void cleanUp() {
        if (mSession != null) {
            mSession.release();
            mSession = null;
        }
        if (mHdmiControlManager != null) {
            mHdmiControlManager.setHdmiCecEnabled(mHdmiEnableStatus);
        }

        try {
            if (mActivityScenario != null) {
                mActivityScenario.close();
            }
        } catch (IllegalStateException e) {
        }

        for (int stream : mStreamVolumeMap.keySet()) {
            int volume = mStreamVolumeMap.get(stream);
            try {
                mAudioManager.setStreamVolume(stream, volume, /* flag= */ 0);
            } catch (SecurityException e) {
                Log.w(TAG, "Failed to restore volume. The test probably had changed DnD mode"
                        + ", stream=" + stream + ", originalVolume="
                        + volume + ", currentVolume=" + mAudioManager.getStreamVolume(stream));
            }
        }
    }

    /**
     * Tests whether volume key changes volume with the session's stream.
     */
    @Test
    public void testVolumeKey_whileSessionAlive() throws Exception {
        if (mUseFixedVolume) {
            Log.i(TAG, "testVolumeKey_whileSessionAlive skipped due to full volume device");
            return;
        }

        final int testStream = mSession.getController().getPlaybackInfo().getAudioAttributes()
                .getVolumeControlStream();
        final int testKeyCode;
        if (mStreamVolumeMap.get(testStream) == mAudioManager.getStreamMinVolume(testStream)) {
            testKeyCode = KeyEvent.KEYCODE_VOLUME_UP;
        } else {
            testKeyCode = KeyEvent.KEYCODE_VOLUME_DOWN;
        }

        // The key event can be ignored and show volume panel instead. Use polling.
        assertWithMessage("failed to adjust stream volume that foreground activity want")
                .that(pollingCheck(() -> {
                    sendKeyEvent(testKeyCode);
                    return mStreamVolumeMap.get(testStream)
                            != mAudioManager.getStreamVolume(testStream);
                }))
                .isTrue();
    }

    /**
     * Tests whether volume key changes a stream volume even after the session is released,
     * without being ignored.
     */
    @Test
    public void testVolumeKey_afterSessionReleased() throws Exception {
        if (mUseFixedVolume) {
            Log.i(TAG, "testVolumeKey_afterSessionReleased skipped due to full volume device");
            return;
        }

        mSession.release();

        // The key event can be ignored and show volume panel instead. Use polling.
        boolean downKeySuccess = pollingCheck(() -> {
            sendKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN);
            return checkAnyStreamVolumeChanged();
        });
        if (downKeySuccess) {
            // Volume down key has changed a stream volume. Test success.
            return;
        }

        // Volume may not have been changed because the target stream's volume level was minimum.
        // Try again with the up key.
        assertThat(pollingCheck(() -> {
            sendKeyEvent(KeyEvent.KEYCODE_VOLUME_UP);
            return checkAnyStreamVolumeChanged();
        })).isTrue();
    }

    @Test
    public void testMediaKey_whileSessionAlive() throws Exception {
        int testKeyEvent = KeyEvent.KEYCODE_MEDIA_PLAY;

        // Note: No extra setup for the session is needed after Activity#setMediaController().
        // i.e. No playback nor activeness is required.
        CountDownLatch latch = new CountDownLatch(2);
        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                assertThat(event.getKeyCode()).isEqualTo(testKeyEvent);
                latch.countDown();
                return true;
            }
        }, new Handler(Looper.getMainLooper()));

        sendKeyEvent(testKeyEvent);

        assertThat(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testMediaKey_whileSessionReleased() throws Exception {
        int testKeyEvent = KeyEvent.KEYCODE_MEDIA_PLAY;

        CountDownLatch latch = new CountDownLatch(1);
        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                assertWithMessage("Released session shouldn't be able"
                        + " to receive key event in any case").fail();
                latch.countDown();
                return true;
            }
        }, new Handler(Looper.getMainLooper()));
        mSession.release();

        sendKeyEvent(testKeyEvent);

        assertThat(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS)).isFalse();
    }

    private void sendKeyEvent(int keyCode) {
        final long downTime = SystemClock.uptimeMillis();
        final KeyEvent down = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0);
        final long upTime = SystemClock.uptimeMillis();
        final KeyEvent up = new KeyEvent(downTime, upTime, KeyEvent.ACTION_UP, keyCode, 0);
        try {
            mInstrumentation.sendKeySync(down);
            mInstrumentation.sendKeySync(up);
        } catch (SecurityException e) {
            throw new IllegalStateException(
                "MediaSessionTestActivity isn't in the foreground."
                        + " Ensure no screen lock before running CTS test"
                        + ", and do not touch screen while the test is running.");
        }
    }

    private boolean checkAnyStreamVolumeChanged() {
        for (int stream : mStreamVolumeMap.keySet()) {
            int volume = mStreamVolumeMap.get(stream);
            if (mAudioManager.getStreamVolume(stream) != volume) {
                return true;
            }
        }
        return false;
    }

    private static boolean pollingCheck(Callable<Boolean> condition) throws Exception {
        long pollingCount = WAIT_TIME_MS / TIME_SLICE;
        while (!condition.call() && pollingCount-- > 0) {
            try {
                Thread.sleep(TIME_SLICE);
            } catch (InterruptedException e) {
                assertWithMessage("unexpected InterruptedException").fail();
            }
        }
        return pollingCount >= 0;
    }
}
