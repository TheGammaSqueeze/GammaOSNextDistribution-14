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

package android.media.drmframework.cts;

import android.content.res.AssetFileDescriptor;
import android.media.MediaDrm;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaCodecBlockModelHelper;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.test.AndroidTestCase;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.Preconditions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

/**
 * Media DRM Codec tests with CONFIGURE_FLAG_USE_BLOCK_MODEL.
 */
@NonMainlineTest
@AppModeFull(reason = "Instant apps cannot access the SD card")
public class MediaDrmCodecBlockModelTest extends AndroidTestCase {
    private static final String TAG = "MediaDrmCodecBlockModelTest";
    private static final boolean VERBOSE = false;           // lots of logging

    private boolean mIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    static final String mInpPrefix = WorkDir.getMediaDirString();

    protected static AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        File inpFile = new File(mInpPrefix + res);
        Preconditions.assertTestFileExists(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    /**
     * Tests whether decoding a short encrypted group-of-pictures succeeds.
     * The test queues a few encrypted video frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    public void testDecodeShortEncryptedVideo() throws InterruptedException {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortEncryptedVideo(
                true /* obtainBlockForEachBuffer */));
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortEncryptedVideo(
                false /* obtainBlockForEachBuffer */));
    }

    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);

    private static final byte[] CLEAR_KEY_CENC = convert(new int[] {
            0x3f, 0x0a, 0x33, 0xf3, 0x40, 0x98, 0xb9, 0xe2,
            0x2b, 0xc0, 0x78, 0xe0, 0xa1, 0xb5, 0xe8, 0x54 });

    private static final byte[] DRM_INIT_DATA = convert(new int[] {
            // BMFF box header (4 bytes size + 'pssh')
            0x00, 0x00, 0x00, 0x34, 0x70, 0x73, 0x73, 0x68,
            // Full box header (version = 1 flags = 0)
            0x01, 0x00, 0x00, 0x00,
            // SystemID
            0x10, 0x77, 0xef, 0xec, 0xc0, 0xb2, 0x4d, 0x02, 0xac, 0xe3, 0x3c,
            0x1e, 0x52, 0xe2, 0xfb, 0x4b,
            // Number of key ids
            0x00, 0x00, 0x00, 0x01,
            // Key id
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
            0x30, 0x30, 0x30, 0x30, 0x30,
            // size of data, must be zero
            0x00, 0x00, 0x00, 0x00 });

    private static final long ENCRYPTED_CONTENT_FIRST_BUFFER_TIMESTAMP_US = 12083333;
    private static final long ENCRYPTED_CONTENT_LAST_BUFFER_TIMESTAMP_US = 15041666;

    private static byte[] convert(int[] intArray) {
        byte[] byteArray = new byte[intArray.length];
        for (int i = 0; i < intArray.length; ++i) {
            byteArray[i] = (byte)intArray[i];
        }
        return byteArray;
    }

    private MediaCodecBlockModelHelper.Result runDecodeShortEncryptedVideo(boolean obtainBlockForEachBuffer) {
        MediaExtractor extractor = new MediaExtractor();

        try (final MediaDrm drm = new MediaDrm(CLEARKEY_SCHEME_UUID)) {
            extractor.setDataSource(mInpPrefix + "llama_h264_main_720p_8000.mp4", null);
            extractor.selectTrack(0);
            extractor.seekTo(ENCRYPTED_CONTENT_FIRST_BUFFER_TIMESTAMP_US,
                    MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            drm.setOnEventListener(
                    (MediaDrm mediaDrm, byte[] sessionId, int event, int extra, byte[] data) -> {
                        if (event == MediaDrm.EVENT_KEY_REQUIRED
                                || event == MediaDrm.EVENT_KEY_EXPIRED) {
                            MediaDrmClearkeyTest.retrieveKeys(
                                    mediaDrm, "cenc", sessionId, DRM_INIT_DATA,
                                    MediaDrm.KEY_TYPE_STREAMING,
                                    new byte[][] { CLEAR_KEY_CENC });
                        }
                    });
            byte[] sessionId = drm.openSession();
            MediaDrmClearkeyTest.retrieveKeys(
                    drm, "cenc", sessionId, DRM_INIT_DATA, MediaDrm.KEY_TYPE_STREAMING,
                    new byte[][] { CLEAR_KEY_CENC });
            MediaCodecBlockModelHelper.Result result =
                MediaCodecBlockModelHelper.runDecodeShortVideo(
                        extractor, ENCRYPTED_CONTENT_LAST_BUFFER_TIMESTAMP_US,
                        obtainBlockForEachBuffer, null /* format */, null /* events */, sessionId);
            drm.closeSession(sessionId);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MediaCodecBlockModelHelper.Result runDecodeShortVideo(
            String inputResource,
            long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer) {
        return MediaCodecBlockModelHelper.runDecodeShortVideo(
                getMediaExtractorForMimeType(inputResource, "video/"),
                lastBufferTimestampUs, obtainBlockForEachBuffer, null, null, null);
    }

    private static MediaExtractor getMediaExtractorForMimeType(final String resource,
            String mimeTypePrefix) {
        MediaExtractor mediaExtractor = new MediaExtractor();
        try (AssetFileDescriptor afd = getAssetFileDescriptorFor(resource)) {
            mediaExtractor.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int trackIndex;
        for (trackIndex = 0; trackIndex < mediaExtractor.getTrackCount(); trackIndex++) {
            MediaFormat trackMediaFormat = mediaExtractor.getTrackFormat(trackIndex);
            if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith(mimeTypePrefix)) {
                mediaExtractor.selectTrack(trackIndex);
                break;
            }
        }
        if (trackIndex == mediaExtractor.getTrackCount()) {
            throw new IllegalStateException("couldn't get a video track");
        }
        mediaExtractor.seekTo(ENCRYPTED_CONTENT_FIRST_BUFFER_TIMESTAMP_US,
            MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        return mediaExtractor;
    }
}
