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
package android.platform.test.scenario.mediacenter;

import android.platform.test.microbenchmark.Microbenchmark;
import android.platform.test.option.StringOption;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

@RunWith(Microbenchmark.class)
public class SwitchPlaybackMicrobenchmark extends SwitchPlayback {
    public static final String SONG_NAME = "song-name";
    private static final String DEFAULT_SONG_NAME = "A normal 1H song";

    @ClassRule
    public static StringOption mSongName =
            new StringOption(SONG_NAME).setDefault(DEFAULT_SONG_NAME);

    @BeforeClass
    public static void openApp() {
        sMediaCenterHelper.get().open();
        sMediaCenterHelper.get().dismissInitialDialogs();
        sMediaCenterHelper.get().selectMediaTrack(mSongName.get());
    }

    @AfterClass
    public static void closeApp() {
        sMediaCenterHelper.get().exit();
    }
}
