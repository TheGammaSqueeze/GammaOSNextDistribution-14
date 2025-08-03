/*
 * Copyright 2023 The Android Open Source Project
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

import android.support.v4.media.session.PlaybackStateCompat;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AvrcpControllerNativeInterfaceTest {
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";
    private static final byte[] REMOTE_DEVICE_ADDRESS_AS_ARRAY = new byte[] {0, 0, 0, 0, 0, 0};

    @Test
    public void createFromNativeMediaItem() {
        long uid = 1;
        int type = 2;
        int[] attrIds = new int[] {0x01}; // MEDIA_ATTRIBUTE_TITLE
        String[] attrVals = new String[] {"test_title"};

        AvrcpItem item =
                AvrcpControllerNativeInterface.createFromNativeMediaItem(
                        REMOTE_DEVICE_ADDRESS_AS_ARRAY,
                        uid,
                        type,
                        "unused_name",
                        attrIds,
                        attrVals);

        assertThat(item.getDevice().getAddress()).isEqualTo(REMOTE_DEVICE_ADDRESS);
        assertThat(item.getItemType()).isEqualTo(AvrcpItem.TYPE_MEDIA);
        assertThat(item.getType()).isEqualTo(type);
        assertThat(item.getUid()).isEqualTo(uid);
        assertThat(item.getUuid()).isNotNull(); // Random uuid
        assertThat(item.getTitle()).isEqualTo(attrVals[0]);
        assertThat(item.isPlayable()).isTrue();
    }

    @Test
    public void createFromNativeFolderItem() {
        long uid = 1;
        int type = 2;
        String folderName = "test_folder_name";
        int playable = 0x01; // Playable folder

        AvrcpItem item =
                AvrcpControllerNativeInterface.createFromNativeFolderItem(
                        REMOTE_DEVICE_ADDRESS_AS_ARRAY, uid, type, folderName, playable);

        assertThat(item.getDevice().getAddress()).isEqualTo(REMOTE_DEVICE_ADDRESS);
        assertThat(item.getItemType()).isEqualTo(AvrcpItem.TYPE_FOLDER);
        assertThat(item.getType()).isEqualTo(type);
        assertThat(item.getUid()).isEqualTo(uid);
        assertThat(item.getUuid()).isNotNull(); // Random uuid
        assertThat(item.getDisplayableName()).isEqualTo(folderName);
        assertThat(item.isPlayable()).isTrue();
    }

    @Test
    public void createFromNativePlayerItem() {
        int playerId = 1;
        String name = "test_name";
        byte[] transportFlags = new byte[] {1, 0, 0, 0, 0, 0, 0, 0};
        int playStatus = 0x04; // JNI_PLAY_STATUS_REV_SEEK;
        int playerType = AvrcpPlayer.TYPE_AUDIO; // No getter exists

        AvrcpPlayer player =
                AvrcpControllerNativeInterface.createFromNativePlayerItem(
                        REMOTE_DEVICE_ADDRESS_AS_ARRAY,
                        playerId,
                        name,
                        transportFlags,
                        playStatus,
                        playerType);

        assertThat(player.getDevice().getAddress()).isEqualTo(REMOTE_DEVICE_ADDRESS);
        assertThat(player.getId()).isEqualTo(playerId);
        assertThat(player.supportsFeature(0)).isTrue();
        assertThat(player.getName()).isEqualTo(name);
        assertThat(player.getPlayStatus()).isEqualTo(PlaybackStateCompat.STATE_REWINDING);
    }
}
