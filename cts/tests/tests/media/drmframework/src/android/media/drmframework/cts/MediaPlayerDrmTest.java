/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.media.drmframework.cts;

import android.net.Uri;
import android.platform.test.annotations.AppModeFull;

import java.io.File;

/**
 * Tests for the MediaPlayer API and local video/audio playback.
 */
@AppModeFull(reason = "TODO: evaluate and port to instant")
public class MediaPlayerDrmTest extends MediaPlayerDrmTestBase {

    private static final String LOG_TAG = "MediaPlayerDrmTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    // Assets
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final Uri CENC_AUDIO_URL =
            Uri.fromFile(new File(MEDIA_DIR + "car_cenc-20120827-8c-pssh.mp4"));

    // Tests
    public void testCAR_CLEARKEY_AUDIO_DOWNLOADED_V0_SYNC() throws Exception {
        playAudio(CENC_AUDIO_URL, ModularDrmTestType.V0_SYNC_TEST);
    }

    public void testCAR_CLEARKEY_AUDIO_DOWNLOADED_V1_ASYNC() throws Exception {
        playAudio(CENC_AUDIO_URL, ModularDrmTestType.V1_ASYNC_TEST);
    }

    public void testCAR_CLEARKEY_AUDIO_DOWNLOADED_V2_SYNC_CONFIG() throws Exception {
        playAudio(CENC_AUDIO_URL, ModularDrmTestType.V2_SYNC_CONFIG_TEST);
    }

    public void testCAR_CLEARKEY_AUDIO_DOWNLOADED_V3_ASYNC_DRMPREPARED() throws Exception {
        playAudio(CENC_AUDIO_URL, ModularDrmTestType.V3_ASYNC_DRMPREPARED_TEST);
    }

    public void testCAR_CLEARKEY_AUDIO_DOWNLOADED_V5_ASYNC_WITH_HANDLER() throws Exception {
        playAudio(CENC_AUDIO_URL, ModularDrmTestType.V5_ASYNC_DRMPREPARED_TEST_WITH_HANDLER);
    }

    // helpers
    private void playAudio(Uri uri, ModularDrmTestType testType) throws Exception {
        playModularDrmVideo(uri, 0 /* width */, 0 /* height */, testType);
    }
}
