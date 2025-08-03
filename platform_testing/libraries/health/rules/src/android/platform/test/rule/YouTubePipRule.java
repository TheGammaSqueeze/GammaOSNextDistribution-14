/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.platform.test.rule;

import android.os.SystemClock;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IYouTubeHelper;

import androidx.annotation.VisibleForTesting;

import org.junit.runner.Description;

/** This rule allows to execute CUJ while YouTube in pip state. */
public class YouTubePipRule extends TestWatcher {

    @VisibleForTesting static final String YOUTUBE_PLAYBACK_TIMEOUT = "youtube-playback-time";
    long playbackTimeout = 2000;

    @VisibleForTesting static final String VIDEO_NAME = "video-name";
    String videoName = "test-one-hour-video";

    private static HelperAccessor<IYouTubeHelper> sYouTubeHelper =
            new HelperAccessor<>(IYouTubeHelper.class).withPrefix("YouTubeHelper");

    @Override
    protected void starting(Description description) {
        playbackTimeout = Long.valueOf(getArguments().getString(YOUTUBE_PLAYBACK_TIMEOUT, "2000"));
        videoName = getArguments().getString(VIDEO_NAME, "test-one-hour-video");

        sYouTubeHelper.get().open();
        sYouTubeHelper.get().goToYourVideos();
        SystemClock.sleep(playbackTimeout);
        sYouTubeHelper.get().playYourVideo(videoName);
        SystemClock.sleep(playbackTimeout);
        sYouTubeHelper.get().goToYouTubePip();
        SystemClock.sleep(playbackTimeout);
    }

    @Override
    protected void finished(Description description) {
        executeShellCommand(String.format("am force-stop %s", sYouTubeHelper.get().getPackage()));
    }
}
