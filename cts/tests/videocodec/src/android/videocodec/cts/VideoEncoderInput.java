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

package android.videocodec.cts;

import android.media.MediaFormat;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Class containing encoder input resources.
 */
public class VideoEncoderInput {
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    public static final HashMap<String, RawResource> RES_YUV_MAP = new HashMap<>();

    public static class CompressedResource {
        final String mMediaType;
        final String mResFile;

        CompressedResource(String mediaType, String resFile) {
            mMediaType = mediaType;
            mResFile = resFile;
        }

        @NonNull
        @Override
        public String toString() {
            return "CompressedResource{" + "res file ='" + mResFile + '\'' + '}';
        }

        public String uniqueLabel() {
            return mMediaType + mResFile;
        }
    }

    public static final CompressedResource BIRTHDAY_FULLHD_LANDSCAPE =
            new CompressedResource(MediaFormat.MIMETYPE_VIDEO_AVC, MEDIA_DIR
                    + "AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps.mp4");
    public static final CompressedResource SELFIEGROUP_FULLHD_PORTRAIT =
            new CompressedResource(MediaFormat.MIMETYPE_VIDEO_AVC, MEDIA_DIR
                    + "AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps.mp4");
    public static final CompressedResource RIVER_HD_LANDSCAPE =
            new CompressedResource(MediaFormat.MIMETYPE_VIDEO_AVC, MEDIA_DIR
                    + "AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-720p-30fps.mp4");

    private static int manhattanDistance(int w1, int h1, int w2, int h2) {
        return Math.abs(w1 - w2) + Math.abs(h1 - h2);
    }

    public static RawResource getRawResource(EncoderConfigParams cfg) {
        String key = null;
        int closestDistance = Integer.MAX_VALUE;
        for (Map.Entry<String, RawResource> element : RES_YUV_MAP.entrySet()) {
            if (element.getValue() == null) continue;
            int distance = manhattanDistance(cfg.mWidth, cfg.mHeight, element.getValue().mWidth,
                    element.getValue().mHeight);
            if (distance < closestDistance) {
                closestDistance = distance;
                key = element.getKey();
            }
        }
        return RES_YUV_MAP.get(key);
    }

    public static RawResource getRawResource(CompressedResource cRes) {
        return RES_YUV_MAP.get(cRes.uniqueLabel());
    }
}
