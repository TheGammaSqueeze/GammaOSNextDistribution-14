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

package android.platform.tests;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertNotEquals;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoMediaHelper;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BluetoothAudioTest {
    private HelperAccessor<IAutoMediaHelper> mBluetoothAudioHelper;

    public BluetoothAudioTest() throws Exception {
        mBluetoothAudioHelper = new HelperAccessor<>(IAutoMediaHelper.class);
    }

    @Before
    public void openMediaFacet() {
        mBluetoothAudioHelper.get().open();
    }

    @After
    public void goBackToMediaFacet() {
        mBluetoothAudioHelper.get().goBackToMediaHomePage();
    }

    @Test
    public void testPlayPauseMedia() {
        mBluetoothAudioHelper.get().pauseMedia();
        assertFalse("Song not paused.", mBluetoothAudioHelper.get().isPlaying());
        mBluetoothAudioHelper.get().playMedia();
        assertTrue("Song not playing.", mBluetoothAudioHelper.get().isPlaying());
    }

    @Test
    public void testNextTrack() {
        String currentSong = mBluetoothAudioHelper.get().getMediaTrackName();
        mBluetoothAudioHelper.get().clickNextTrack();
        assertNotEquals(
                "Song playing has not been changed",
                currentSong,
                mBluetoothAudioHelper.get().getMediaTrackName());
    }
}
