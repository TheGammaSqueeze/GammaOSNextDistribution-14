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

package android.media.decoder.cts;

import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.android.compatibility.common.util.Preconditions;

import java.io.IOException;

public class DecoderSetup {
    private static final String MIME_VIDEO_PREFIX = "video/";
    private static final String MIME_AUDIO_PREFIX = "audio/";

    public static MediaExtractor createMediaExtractor(String fileName) throws IOException {
        String filePath = WorkDir.getMediaDirString() + fileName;
        Preconditions.assertTestFileExists(filePath);
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(filePath);
        return mediaExtractor;
    }

    public static int getFirstVideoTrack(MediaExtractor extractor) {
        return getFirstTrackWithMimePrefix(MIME_VIDEO_PREFIX, extractor);
    }

    public static int getFirstAudioTrack(MediaExtractor extractor) {
        return getFirstTrackWithMimePrefix(MIME_AUDIO_PREFIX, extractor);
    }

    private static int getFirstTrackWithMimePrefix(String prefix, MediaExtractor extractor) {
        int trackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); ++i) {
            MediaFormat format = extractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith(prefix)) {
                trackIndex = i;
                break;
            }
        }
        assertTrue("Track matching mime '" + prefix + "' not found.", trackIndex >= 0);
        return trackIndex;
    }

    public static MediaCodec createCodecFor(MediaFormat format) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        String codecName = mcl.findDecoderForFormat(format);
        if (codecName == null) {
            return null;
        }
        // TODO(b/234833109): Implement accurate onFrameRendered callbacks for OMX.
        if (codecName.contains("OMX")) {
            return null;
        }
        try {
            return MediaCodec.createByCodecName(codecName);
        } catch (IOException ex) {
            return null;
        }
    }
}
