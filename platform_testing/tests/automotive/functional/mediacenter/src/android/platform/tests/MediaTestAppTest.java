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
import android.platform.helpers.IAutoHomeHelper;
import android.platform.helpers.IAutoMediaHelper;
import android.platform.helpers.IAutoTestMediaAppHelper;
import android.platform.test.option.StringOption;

import androidx.test.runner.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaTestAppTest {
    private static final String MEDIA_APP = "media-app";
    private static final String TEST_MEDIA_APP = "Test Media App";

    @ClassRule
    public static StringOption mMediaTestApp =
            new StringOption(MEDIA_APP).setRequired(false);

    private static HelperAccessor<IAutoMediaHelper> sMediaCenterHelper =
            new HelperAccessor<>(IAutoMediaHelper.class);
    private static HelperAccessor<IAutoTestMediaAppHelper> sTestMediaAppHelper =
            new HelperAccessor<>(IAutoTestMediaAppHelper.class);
    private static HelperAccessor<IAutoHomeHelper> sAutoHomeHelper =
            new HelperAccessor<>(IAutoHomeHelper.class);

    @BeforeClass
    public static void setup() {
        // Load songs on Test Media App
        sAutoHomeHelper.get().openMediaWidget();
        sMediaCenterHelper.get().openMediaAppMenuItems();
        String mediaAppName = TEST_MEDIA_APP;
        if (mMediaTestApp != null
                && mMediaTestApp.get() != null && !mMediaTestApp.get().isEmpty()) {
            mediaAppName = mMediaTestApp.get();
        }
        sMediaCenterHelper.get().openApp(mediaAppName);
        assertTrue("Not a media app",
                sMediaCenterHelper.get().getMediaAppTitle().equals(mediaAppName));
        sMediaCenterHelper.get().openMediaAppSettingsPage();
        sTestMediaAppHelper.get().loadMediaInLocalMediaTestApp();
    }

    @Test
    public void testPlayPauseMedia() {
        sMediaCenterHelper.get().playMedia();
        assertTrue("Song not playing.", sMediaCenterHelper.get().isPlaying());
        sMediaCenterHelper.get().pauseMedia();
        assertFalse("Song not paused.", sMediaCenterHelper.get().isPlaying());
    }

    @Test
    public void testNextTrack() {
        String currentSong = sMediaCenterHelper.get().getMediaTrackName();
        sMediaCenterHelper.get().clickNextTrack();
        assertNotEquals(
                "Song playing has not been changed",
                currentSong,
                sMediaCenterHelper.get().getMediaTrackName());
    }
}
