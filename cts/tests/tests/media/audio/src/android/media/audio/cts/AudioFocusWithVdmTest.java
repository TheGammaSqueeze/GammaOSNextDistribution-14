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

package android.media.audio.cts;

import static android.Manifest.permission.ACTIVITY_EMBEDDING;
import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED;
import static android.Manifest.permission.QUERY_AUDIO_STATE;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_AUDIO;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.tryAcquireUninterruptibly;

import static org.junit.Assume.assumeNotNull;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.internal.annotations.GuardedBy;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Virtual device manager cannot be accessed by instant apps")
public class AudioFocusWithVdmTest {

    private static final VirtualDeviceParams VIRTUAL_DEVICE_PARAMS_DEFAULT =
            new VirtualDeviceParams.Builder().build();
    private static final VirtualDeviceParams VIRTUAL_DEVICE_PARAMS_CUSTOM_POLICY =
            new VirtualDeviceParams.Builder().setDevicePolicy(POLICY_TYPE_AUDIO,
                    DEVICE_POLICY_CUSTOM).build();
    private static final AudioAttributes AUDIO_ATTRIBUTES_MEDIA =
            new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            MODIFY_AUDIO_SETTINGS_PRIVILEGED, // ensures focus request is independent of proc state
            QUERY_AUDIO_STATE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;

    @Before
    public void setUp() {
        Context context = getApplicationContext();
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        assumeNotNull(mVirtualDeviceManager);
    }


    /**
     * The test below tests the following scenario:
     *
     * 1. There's media playback going on on non-VDM context.
     * 2. Audio focus is requested within VDM context, where the virtual device associated
     *    with the context has custom device policy for audio.
     *
     * It is expected that the native player doesn't loose the focus and at the same time,
     * focus requested for VDM context will be granted.
     */
    @Test
    public void testAudioFocusRequestOnVdmContextWithCustomDevicePolicy() {
        Context defaultContext = getApplicationContext();
        try (VirtualDeviceManager.VirtualDevice vd = mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                VIRTUAL_DEVICE_PARAMS_CUSTOM_POLICY);
             PlaybackHelperForTest defaultDevicePlayback = new PlaybackHelperForTest(
                     defaultContext);
             PlaybackHelperForTest vdmDevicePlayback = new PlaybackHelperForTest(
                     vd.createContext())) {

            // Audio playing within default context starts first, focus should be granted.
            int defaultFocusRequestResult = defaultDevicePlayback.requestFocus();
            assertThat(defaultFocusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            defaultDevicePlayback.startPlayback();

            int vdmFocusRequestResult = vdmDevicePlayback.requestFocus();
            assertThat(vdmFocusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            // None of the players should loose focus.
            assertThat(defaultDevicePlayback.getLastFocusChange().isEmpty()).isTrue();
            assertThat(vdmDevicePlayback.getLastFocusChange().isEmpty()).isTrue();
        }
    }

    /**
     * The test below tests the following scenario:
     *
     * 1. There's media playback going on on non-VDM context.
     * 2. Audio focus is requested within VDM context, where the virtual device associated
     *    with the context has default device policy for audio.
     *
     * It is expected that the first player will loose audio focus.
     */
    @Test
    public void testAudioFocusRequestOnVdmContextWithDefaultDevicePolicy() {
        Context defaultContext = getApplicationContext();
        try (VirtualDeviceManager.VirtualDevice vd = mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                VIRTUAL_DEVICE_PARAMS_DEFAULT);
             PlaybackHelperForTest defaultDevicePlayback = new PlaybackHelperForTest(
                     defaultContext);
             PlaybackHelperForTest vdmDevicePlayback = new PlaybackHelperForTest(
                     vd.createContext())) {

            // Audio playing within default context starts first, focus should be granted.
            int defaultFocusRequestResult = defaultDevicePlayback.requestFocus();
            assertThat(defaultFocusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            defaultDevicePlayback.startPlayback();

            int vdmFocusRequestResult = vdmDevicePlayback.requestFocus();
            assertThat(vdmFocusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            // Since the mVirtualDeviceManager is configured with default device polic
            assertThat(defaultDevicePlayback.getLastFocusChange().isPresent()).isTrue();
            assertThat(defaultDevicePlayback.getLastFocusChange().get()).isEqualTo(AUDIOFOCUS_LOSS);
            assertThat(vdmDevicePlayback.getLastFocusChange().isEmpty()).isTrue();
        }
    }

    /**
     * Helper class to manage playback client for test.
     */
    private static class PlaybackHelperForTest implements AutoCloseable {
        private final Context mContext;
        private MediaPlayer mMediaPlayer;
        private AudioFocusListenerForTest mFocusListener;
        private AudioManager mAudioManager;
        private AudioFocusRequest mAudioFocusRequest;

        PlaybackHelperForTest(Context context) {
            mContext = context;
            mAudioManager = context.getSystemService(AudioManager.class);
        }

        public int requestFocus() {
            if (mAudioFocusRequest != null) {
                // Abandon previous focus request.
                mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
            }
            mFocusListener = new AudioFocusListenerForTest();
            mAudioFocusRequest = new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(mFocusListener).build();
            return mAudioManager.requestAudioFocus(mAudioFocusRequest);
        }

        public Optional<Integer> getLastFocusChange() {
            return mFocusListener == null ? Optional.empty() : mFocusListener.getLastFocusChange();
        }

        public void startPlayback() {
            mMediaPlayer = MediaPlayer.create(mContext, R.raw.sine1khzs40dblong);
            mMediaPlayer.start();
        }

        @Override
        public void close() {
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
            }
            if (mAudioManager != null && mAudioFocusRequest != null) {
                mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
            }
        }
    }

    private static class AudioFocusListenerForTest implements
            AudioManager.OnAudioFocusChangeListener {
        private final Object mLock = new Object();
        private final Semaphore mChangeEventSignal = new Semaphore(0);
        @GuardedBy("mLock")
        private Optional<Integer> mLastFocusChange = Optional.empty();

        public Optional<Integer> getLastFocusChange() {
            Optional lastChange;
            synchronized (mLock) {
                lastChange = mLastFocusChange;
            }
            if (lastChange.isEmpty()) {
                boolean unused = tryAcquireUninterruptibly(
                        mChangeEventSignal, 100, TimeUnit.MILLISECONDS);
            }

            synchronized (mLock) {
                return mLastFocusChange;
            }
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            synchronized (mLock) {
                mLastFocusChange = Optional.of(focusChange);
            }
            mChangeEventSignal.release();
        }
    }
}
