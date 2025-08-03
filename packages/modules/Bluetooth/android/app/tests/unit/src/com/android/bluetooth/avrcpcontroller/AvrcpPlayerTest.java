/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.avrcpcontroller;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.net.Uri;
import android.support.v4.media.session.PlaybackStateCompat;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AvrcpPlayerTest {
    private static final int TEST_PLAYER_ID = 1;
    private static final int TEST_PLAYER_TYPE = AvrcpPlayer.TYPE_VIDEO;
    private static final int TEST_PLAYER_SUB_TYPE = AvrcpPlayer.SUB_TYPE_AUDIO_BOOK;
    private static final String TEST_NAME = "test_name";
    private static final int TEST_FEATURE = AvrcpPlayer.FEATURE_PLAY;
    private static final int TEST_PLAY_STATUS = PlaybackStateCompat.STATE_STOPPED;
    private static final int TEST_PLAY_TIME = 1;

    private final AvrcpItem mAvrcpItem = new AvrcpItem.Builder().build();
    private final byte[] mTestAddress = new byte[]{01, 01, 01, 01, 01, 01};
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice = null;

    @Mock
    private PlayerApplicationSettings mPlayerApplicationSettings;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestDevice = mAdapter.getRemoteDevice(mTestAddress);
    }

    @Test
    public void buildAvrcpPlayer() {
        AvrcpPlayer.Builder builder = new AvrcpPlayer.Builder();
        builder.setDevice(mTestDevice);
        builder.setPlayerId(TEST_PLAYER_ID);
        builder.setPlayerType(TEST_PLAYER_TYPE);
        builder.setPlayerSubType(TEST_PLAYER_SUB_TYPE);
        builder.setName(TEST_NAME);
        builder.setSupportedFeature(TEST_FEATURE);
        builder.setPlayStatus(TEST_PLAY_STATUS);
        builder.setCurrentTrack(mAvrcpItem);

        AvrcpPlayer avrcpPlayer = builder.build();

        assertThat(avrcpPlayer.getDevice()).isEqualTo(mTestDevice);
        assertThat(avrcpPlayer.getId()).isEqualTo(TEST_PLAYER_ID);
        assertThat(avrcpPlayer.getName()).isEqualTo(TEST_NAME);
        assertThat(avrcpPlayer.supportsFeature(TEST_FEATURE)).isTrue();
        assertThat(avrcpPlayer.getPlayStatus()).isEqualTo(TEST_PLAY_STATUS);
        assertThat(avrcpPlayer.getCurrentTrack()).isEqualTo(mAvrcpItem);
        assertThat(avrcpPlayer.getPlaybackState().getActions()).isEqualTo(
                PlaybackStateCompat.ACTION_PREPARE | PlaybackStateCompat.ACTION_PLAY);
    }

    @Test
    public void setAndGetPlayTime() {
        AvrcpPlayer avrcpPlayer = new AvrcpPlayer.Builder().build();

        avrcpPlayer.setPlayTime(TEST_PLAY_TIME);

        assertThat(avrcpPlayer.getPlayTime()).isEqualTo(TEST_PLAY_TIME);
    }

    @Test
    public void setPlayStatus() {
        AvrcpPlayer avrcpPlayer = new AvrcpPlayer.Builder().build();
        avrcpPlayer.setPlayTime(TEST_PLAY_TIME);

        avrcpPlayer.setPlayStatus(PlaybackStateCompat.STATE_PLAYING);
        assertThat(avrcpPlayer.getPlaybackState().getPlaybackSpeed()).isEqualTo(1);

        avrcpPlayer.setPlayStatus(PlaybackStateCompat.STATE_PAUSED);
        assertThat(avrcpPlayer.getPlaybackState().getPlaybackSpeed()).isEqualTo(0);

        avrcpPlayer.setPlayStatus(PlaybackStateCompat.STATE_FAST_FORWARDING);
        assertThat(avrcpPlayer.getPlaybackState().getPlaybackSpeed()).isEqualTo(3);

        avrcpPlayer.setPlayStatus(PlaybackStateCompat.STATE_REWINDING);
        assertThat(avrcpPlayer.getPlaybackState().getPlaybackSpeed()).isEqualTo(-3);
    }

    @Test
    public void setSupportedPlayerApplicationSettings() {
        when(mPlayerApplicationSettings.supportsSetting(
                PlayerApplicationSettings.REPEAT_STATUS)).thenReturn(true);
        when(mPlayerApplicationSettings.supportsSetting(
                PlayerApplicationSettings.SHUFFLE_STATUS)).thenReturn(true);
        AvrcpPlayer avrcpPlayer = new AvrcpPlayer.Builder().build();
        long expectedActions =
                PlaybackStateCompat.ACTION_PREPARE | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                        | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;

        avrcpPlayer.setSupportedPlayerApplicationSettings(mPlayerApplicationSettings);

        assertThat(avrcpPlayer.getPlaybackState().getActions()).isEqualTo(expectedActions);
    }

    @Test
    public void supportsSetting() {
        int settingType = 1;
        int settingValue = 1;
        when(mPlayerApplicationSettings.supportsSetting(settingType, settingValue)).thenReturn(
                true);
        AvrcpPlayer avrcpPlayer = new AvrcpPlayer.Builder().build();

        avrcpPlayer.setSupportedPlayerApplicationSettings(mPlayerApplicationSettings);

        assertThat(avrcpPlayer.supportsSetting(settingType, settingValue)).isTrue();
    }

    @Test
    public void updateAvailableActions() {
        byte[] supportedFeatures = new byte[16];
        setSupportedFeature(supportedFeatures, AvrcpPlayer.FEATURE_STOP);
        setSupportedFeature(supportedFeatures, AvrcpPlayer.FEATURE_PAUSE);
        setSupportedFeature(supportedFeatures, AvrcpPlayer.FEATURE_REWIND);
        setSupportedFeature(supportedFeatures, AvrcpPlayer.FEATURE_FAST_FORWARD);
        setSupportedFeature(supportedFeatures, AvrcpPlayer.FEATURE_FORWARD);
        setSupportedFeature(supportedFeatures, AvrcpPlayer.FEATURE_PREVIOUS);
        long expectedActions = PlaybackStateCompat.ACTION_PREPARE | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_REWIND
                | PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

        AvrcpPlayer avrcpPlayer = new AvrcpPlayer.Builder().setSupportedFeatures(
                supportedFeatures).build();

        assertThat(avrcpPlayer.getPlaybackState().getActions()).isEqualTo(expectedActions);
    }

    @Test
    public void toString_returnsInfo() {
        AvrcpPlayer avrcpPlayer = new AvrcpPlayer.Builder().setPlayerId(TEST_PLAYER_ID).setName(
                TEST_NAME).setCurrentTrack(mAvrcpItem).build();

        assertThat(avrcpPlayer.toString()).isEqualTo(
                "<AvrcpPlayer id=" + TEST_PLAYER_ID + " name=" + TEST_NAME + " track="
                        + mAvrcpItem + " playState=" + avrcpPlayer.getPlaybackState() + ">");
    }

    @Test
    public void notifyImageDownload() {
        String uuid = "1111";
        Uri uri = Uri.parse("http://test.com");
        AvrcpItem trackWithDifferentUuid = new AvrcpItem.Builder().build();
        AvrcpItem trackWithSameUuid = new AvrcpItem.Builder().build();
        trackWithSameUuid.setCoverArtUuid(uuid);
        AvrcpPlayer avrcpPlayer = new AvrcpPlayer.Builder().build();

        assertThat(avrcpPlayer.notifyImageDownload(uuid, uri)).isFalse();

        avrcpPlayer.updateCurrentTrack(trackWithDifferentUuid);
        assertThat(avrcpPlayer.notifyImageDownload(uuid, uri)).isFalse();

        avrcpPlayer.updateCurrentTrack(trackWithSameUuid);
        assertThat(avrcpPlayer.notifyImageDownload(uuid, uri)).isTrue();
    }

    private void setSupportedFeature(byte[] supportedFeatures, int feature) {
        int byteNumber = feature / 8;
        byte bitMask = (byte) (1 << (feature % 8));
        supportedFeatures[byteNumber] = (byte) (supportedFeatures[byteNumber] | bitMask);
    }
}
