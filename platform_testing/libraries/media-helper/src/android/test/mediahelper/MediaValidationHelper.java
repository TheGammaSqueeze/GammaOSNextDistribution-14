/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.test.mediahelper;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Helper functions for validating media files. */
public class MediaValidationHelper {
    private static final String TAG = MediaValidationHelper.class.getSimpleName();
    private static final String VIDEO_MIME_TYPE = "video";
    private static final String AUDIO_MIME_TYPE = "audio";

    /**
     * Validate video file.
     *
     * <p>This function validates that video contains at least 1 video track and 1 audio track. Also
     * validate that the video track has expected height and width. It also checks the video track
     * duration is bigger than the specified length.
     *
     * @param fileDescriptor The {@link FileDescriptor} of the video file.
     * @param expHeight Expected video track height.
     * @param expWidth Expected video track width.
     * @param minDurationMillis Minimum duration in milli seconds. The video track duration should
     *     be longer than it.
     * @throws IOException
     */
    public static void validateVideo(
            FileDescriptor fileDescriptor, int expHeight, int expWidth, long minDurationMillis)
            throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(fileDescriptor);
        int numTracks = extractor.getTrackCount();
        assertThat(numTracks).isGreaterThan(1);
        Log.d(TAG, String.format("Number of tracks: %d", numTracks));
        boolean hasVideoTrack = false;
        boolean hasAudioTrack = false;
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            Log.d(TAG, String.format("Mime type: %s; format: %s", mime, format.toString()));
            if (mime.contains(VIDEO_MIME_TYPE)) {
                hasVideoTrack = true;
                validateVideoTrackMediaFormat(format, expHeight, expWidth, minDurationMillis);
                // TODO(b/164484222): Validate video track frame drop.
            } else if (mime.contains(AUDIO_MIME_TYPE)) {
                hasAudioTrack = true;
            }
        }
        assertThat(hasVideoTrack).isTrue();
        assertThat(hasAudioTrack).isTrue();
    }

    /**
     * Validates that video data {@link MediaFormat} is equal to the expected width and height. And
     * the duration is longer than certain value.
     *
     * @param format The {@link MediaFormat} of the video track.
     * @param expHeight Expected video track height.
     * @param expWidth Expected video track width.
     * @param minDurationMillis Minimum duration in milli seconds. The video track duration should
     *     be longer than it.
     */
    private static void validateVideoTrackMediaFormat(
        MediaFormat format, int expHeight, int expWidth, long minDurationMillis) {
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
            long durationMillis =
                TimeUnit.MICROSECONDS.toMillis(format.getLong(MediaFormat.KEY_DURATION));
            int width = format.getInteger(MediaFormat.KEY_WIDTH);
            int height = format.getInteger(MediaFormat.KEY_HEIGHT);
            Log.d(
                TAG,
                String.format(
                    "Duration: %d; Width: %d; Height: %d", durationMillis, width, height));
            assertThat(durationMillis).isGreaterThan(minDurationMillis);
            assertThat(width).isEqualTo(expWidth);
            assertThat(height).isEqualTo(expHeight);
        }
    }
    /**
     * Validates that the image file height and weight is greater than certain value.
     *
     * @param fileDescriptor The {@link FileDescriptor} of the image.
     * @param minHeight Minimum height. The image height should be greater than this value.
     * @param minWidth Minimum width. The image width should be greater than this value.
     */
    public static void validateImage(FileDescriptor fileDescriptor, int minHeight, int minWidth) {
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();
        Log.d(TAG, String.format("Height: %d; Width: %d", height, width));
        assertThat(height).isGreaterThan(minHeight);
        assertThat(width).isGreaterThan(minWidth);
    }
}
