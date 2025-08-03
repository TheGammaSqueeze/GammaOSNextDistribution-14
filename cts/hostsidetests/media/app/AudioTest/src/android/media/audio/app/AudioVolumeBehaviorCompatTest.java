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

package android.media.audio.app;

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.app.compat.CompatChanges;
import android.content.Context;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioDeviceVolumeManager;
import android.media.AudioManager;
import android.media.VolumeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Tests the behavior of {@link AudioManager#getDeviceVolumeBehavior} when the ChangeId
 * {@link AudioManager#RETURN_DEVICE_VOLUME_BEHAVIOR_ABSOLUTE_ADJUST_ONLY} is enabled or disabled.
 */
@RunWith(AndroidJUnit4.class)
public class AudioVolumeBehaviorCompatTest {

    private AudioManager mAudioManager;
    private AudioDeviceVolumeManager mAudioDeviceVolumeManager;

    private static final List<Integer> SETTABLE_VOLUME_BEHAVIORS = Arrays.asList(
            AudioManager.DEVICE_VOLUME_BEHAVIOR_VARIABLE,
            AudioManager.DEVICE_VOLUME_BEHAVIOR_FULL,
            AudioManager.DEVICE_VOLUME_BEHAVIOR_FIXED);

    private static final AudioDeviceAttributes DEVICE_SPEAKER_OUT = new AudioDeviceAttributes(
            AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "");

    @Before
    public void setUp() throws Exception {
        mAudioManager = (AudioManager) InstrumentationRegistry.getInstrumentation().getContext()
                .getSystemService(Context.AUDIO_SERVICE);
        mAudioDeviceVolumeManager = (AudioDeviceVolumeManager) InstrumentationRegistry
                .getInstrumentation().getContext()
                .getSystemService(Context.AUDIO_DEVICE_VOLUME_SERVICE);

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(MODIFY_AUDIO_SETTINGS_PRIVILEGED,
                BLUETOOTH_PRIVILEGED);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void changeEnabled_getDeviceVolumeBehaviorReturnsAbsoluteVolumeAdjustOnlyBehavior() {
        assertTrue(CompatChanges.isChangeEnabled(240663182L));

        VolumeInfo volumeInfo = new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                .setMinVolumeIndex(0)
                .setMaxVolumeIndex(250)
                .setVolumeIndex(0)
                .build();

        int originalBehavior = mAudioManager.getDeviceVolumeBehavior(DEVICE_SPEAKER_OUT);
        // The original volume behavior must be settable using setDeviceVolumeBehavior.
        // The builtin speaker should be using such a volume behavior.
        if (!SETTABLE_VOLUME_BEHAVIORS.contains(originalBehavior)) {
            return;
        }

        try {
            mAudioDeviceVolumeManager.setDeviceAbsoluteVolumeAdjustOnlyBehavior(DEVICE_SPEAKER_OUT,
                    volumeInfo, Executors.newSingleThreadExecutor(),
                    new AbsoluteVolumeChangedListener(), true);

            int behavior = mAudioManager.getDeviceVolumeBehavior(DEVICE_SPEAKER_OUT);

            assertThat(behavior).isEqualTo(
                    AudioManager.DEVICE_VOLUME_BEHAVIOR_ABSOLUTE_ADJUST_ONLY);
        } finally {
            mAudioManager.setDeviceVolumeBehavior(DEVICE_SPEAKER_OUT, originalBehavior);
        }
    }

    @Test
    public void changeDisabled_getDeviceVolumeBehaviorReturnsFullVolumeBehavior() {
        assertFalse(CompatChanges.isChangeEnabled(240663182L));

        VolumeInfo volumeInfo = new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                .setMinVolumeIndex(0)
                .setMaxVolumeIndex(250)
                .setVolumeIndex(0)
                .build();

        int originalBehavior = mAudioManager.getDeviceVolumeBehavior(DEVICE_SPEAKER_OUT);
        // The original volume behavior must be settable using setDeviceVolumeBehavior.
        // The builtin speaker should be using such a volume behavior.
        if (!SETTABLE_VOLUME_BEHAVIORS.contains(originalBehavior)) {
            return;
        }

        try {
            mAudioDeviceVolumeManager.setDeviceAbsoluteVolumeAdjustOnlyBehavior(DEVICE_SPEAKER_OUT,
                    volumeInfo, Executors.newSingleThreadExecutor(),
                    new AbsoluteVolumeChangedListener(), true);

            int behavior = mAudioManager.getDeviceVolumeBehavior(DEVICE_SPEAKER_OUT);

            assertThat(behavior).isEqualTo(AudioManager.DEVICE_VOLUME_BEHAVIOR_FULL);
        } finally {
            mAudioManager.setDeviceVolumeBehavior(DEVICE_SPEAKER_OUT, originalBehavior);
        }
    }

    /**
     * No-op listener for volume changes when using absolute volume behavior.
     */
    private static class AbsoluteVolumeChangedListener implements
            AudioDeviceVolumeManager.OnAudioDeviceVolumeChangedListener {

        private AbsoluteVolumeChangedListener() { }

        @Override
        public void onAudioDeviceVolumeChanged(AudioDeviceAttributes audioDevice,
                VolumeInfo volumeInfo) { }

        @Override
        public void onAudioDeviceVolumeAdjusted(
                AudioDeviceAttributes audioDevice,
                VolumeInfo volumeInfo,
                @AudioManager.VolumeAdjustment int direction,
                @AudioDeviceVolumeManager.VolumeAdjustmentMode int mode
        ) { }
    }
}
