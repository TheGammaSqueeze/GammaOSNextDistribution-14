/*
 * Copyright 2019 Google Inc. All rights reserved.
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

package android.media.player.cts;

import android.media.MediaFormat;
import android.media.cts.MediaCodecPlayerTestBase;
import android.media.cts.MediaStubActivity2;
import android.net.Uri;
import android.platform.test.annotations.AppModeFull;
import android.view.Surface;

import java.io.File;
import java.util.List;

@AppModeFull(reason = "Instant apps cannot access the SD card")
public class MediaCodecPlayerTest extends MediaCodecPlayerTestBase<MediaStubActivity2> {

    private static final String MIME_VIDEO_AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final String CLEAR_AUDIO_PATH = "llama_aac_audio.mp4";
    private static final String CLEAR_VIDEO_PATH = "llama_h264_main_720p_8000.mp4";

    private static final int VIDEO_WIDTH_CLEAR = 1280;
    private static final int VIDEO_HEIGHT_CLEAR = 674;

    public MediaCodecPlayerTest() {
        super(MediaStubActivity2.class);
    }

    private void playOnSurfaces(List<Surface> surfaces) throws Exception {
        testPlayback(MIME_VIDEO_AVC,
                new String[0],
                Uri.fromFile(new File(MEDIA_DIR, CLEAR_AUDIO_PATH)),
                Uri.fromFile(new File(MEDIA_DIR, CLEAR_VIDEO_PATH)),
                VIDEO_WIDTH_CLEAR,
                VIDEO_HEIGHT_CLEAR,
                surfaces);
    }

    public void testPlaybackSwitchViews() throws Exception {
        playOnSurfaces(getActivity().getSurfaces());
    }
}
