/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.media.muxer.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MetadataRetriever;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.IntStream;

@AppModeFull(reason = "No interaction with system server")
@RunWith(AndroidJUnit4.class)
public class MediaMuxerTest {
    private static final String TAG = "MediaMuxerTest";
    private static final boolean VERBOSE = false;
    private static final int MAX_SAMPLE_SIZE = 1024 * 1024;
    private static final float LATITUDE = 0.0000f;
    private static final float LONGITUDE  = -180.0f;
    private static final float BAD_LATITUDE = 91.0f;
    private static final float BAD_LONGITUDE = -181.0f;
    private static final float TOLERANCE = 0.0002f;
    private static final long OFFSET_TIME_US = 29 * 60 * 1000000L; // 29 minutes
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private final boolean mAndroid11 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        Preconditions.assertTestFileExists(MEDIA_DIR + res);
        File inpFile = new File(MEDIA_DIR + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    @Test
    public void testWebmOutput() throws Exception {
        final String source =
                "video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm";
        String outputFilePath = File.createTempFile("testWebmOutput", ".webm")
                .getAbsolutePath();
        cloneAndVerify(
                source,
                outputFilePath,
                2,
                90,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM,
                false /* signalEos */);
    }

    @Test
    public void testHEICOutputInHEIF() throws Exception {
        final String source = "sample_heif.heic";
        String outputFilePath = File.createTempFile("testHEICOutputInHEIF", ".heic")
            .getAbsolutePath();
        cloneAndVerify(
            source,
            outputFilePath,
            4 /* expectedTrackCount */,
            0 /* degrees */,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF,
            false /* signalEos */);
    }

    @Test
    public void testAVIFOutputInHEIF() throws Exception {
        final String source = "sample_avif.avif";
        String outputFilePath = File.createTempFile("testAVIFOutputInHEIF", ".avif")
                .getAbsolutePath();
        cloneAndVerify(
            source,
            outputFilePath,
            1 /* expectedTrackCount */,
            0 /* degrees */,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF,
            false /* signalEos */);
    }

    @Test
    public void testAV1OutputInMP4() throws Exception {
        final String source =
                "video_1280x720_mp4_av1_2000kbps_30fps_aac_stereo_128kbps_44100hz.mp4";
        String outputFilePath = File.createTempFile("testAV1OutputInMP4", ".mp4").getAbsolutePath();
        cloneAndVerify(
                source,
                outputFilePath,
                2 /* expectedTrackCount */,
                0 /* degrees */,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                true /* signalEos */);
    }

    @Test
    public void testAV1VideoOnlyOutputInMP4() throws Exception {
        final String source = "video_1280x720_mp4_av1_2000kbps_30fps.mp4";
        String outputFilePath =
                File.createTempFile("testAV1VideoOnlyOutputInMP4", ".mp4").getAbsolutePath();
        cloneAndVerify(
                source,
                outputFilePath,
                1 /* expectedTrackCount */,
                0 /* degrees */,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                true /* signalEos */);
    }

    /**
     * Test: make sure the muxer handles dovi profile 8.4 video track only file correctly.
     */
    @Test
    public void testDolbyVisionVideoOnlyP8() throws Exception {
        final String source = "video_dovi_1920x1080_60fps_dvhe_08_04.mp4";
        String outputFilePath = File.createTempFile("MediaMuxerTest_dolbyvisionP8videoOnly", ".mp4")
                .getAbsolutePath();
        try {
            cloneAndVerify(
                    source,
                    outputFilePath,
                    2 /* expectedTrackCount */,
                    180 /* degrees */,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                    false /* signalEos */,
                    MediaMuxerTest::filterOutNonDolbyVisionFormat);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    /**
     * Test: make sure the muxer handles dovi profile 9.2 video track only file correctly.
     */
    @Test
    public void testDolbyVisionVideoOnlyP9() throws Exception {
        final String source = "video_dovi_1920x1080_60fps_dvav_09_02.mp4";
        String outputFilePath = File.createTempFile("MediaMuxerTest_dolbyvisionP9videoOnly", ".mp4")
                .getAbsolutePath();
        try {
            cloneAndVerify(
                    source,
                    outputFilePath,
                    2 /* expectedTrackCount */,
                    180 /* degrees */,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                    false /* signalEos */,
                    MediaMuxerTest::filterOutNonDolbyVisionFormat);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    private static MediaFormat filterOutNonDolbyVisionFormat(MediaFormat format) {
        String mime = format.getString(MediaFormat.KEY_MIME);
        return mime.equals(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) ? format : null;
    }

    /**
     * Test: makes sure if audio and video muxing using MPEG4Writer works well when there are frame
     * drops as in b/63590381 and b/64949961 while B Frames encoding is enabled.
     */
    @Test
    public void testSimulateAudioBVideoFramesDropIssues() throws Exception {
        final String source = "video_h264_main_b_frames.mp4";
        String outputFilePath = File.createTempFile(
            "MediaMuxerTest_testSimulateAudioBVideoFramesDropIssues", ".mp4").getAbsolutePath();
        try {
            simulateVideoFramesDropIssuesAndMux(source, outputFilePath, 2 /* track index */,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            verifyAFewSamplesTimestamp(source, outputFilePath);
            verifySamplesMatch(source, outputFilePath, 66667 /* sample around 0 sec */, 0);
            verifySamplesMatch(
                    source, outputFilePath, 8033333 /*  sample around 8 sec */, OFFSET_TIME_US);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    /**
     * Test: makes sure muxing works well when video with B Frames are muxed using MPEG4Writer
     * and a few frames drop.
     */
    @Test
    public void testTimestampsBVideoOnlyFramesDropOnce() throws Exception {
        final String source = "video_480x360_mp4_h264_bframes_495kbps_30fps_editlist.mp4";
        String outputFilePath = File.createTempFile(
            "MediaMuxerTest_testTimestampsBVideoOnlyFramesDropOnce", ".mp4").getAbsolutePath();
        try {
            HashSet<Integer> samplesDropSet = new HashSet<Integer>();
            // Drop frames from sample index 56 to 76, I frame at 56.
            IntStream.rangeClosed(56, 76).forEach(samplesDropSet::add);
            // No start offsets for any track.
            cloneMediaWithSamplesDropAndStartOffsets(source, outputFilePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, samplesDropSet, null);
            verifyTSWithSamplesDropAndStartOffset(
                    source, true /* has B frames */, outputFilePath, samplesDropSet, null);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    /**
     * Test: makes sure if video muxing while framedrops occurs twice using MPEG4Writer
     * works with B Frames.
     */
    @Test
    public void testTimestampsBVideoOnlyFramesDropTwice() throws Exception {
        final String source = "video_480x360_mp4_h264_bframes_495kbps_30fps_editlist.mp4";
        String outputFilePath = File.createTempFile(
            "MediaMuxerTest_testTimestampsBVideoOnlyFramesDropTwice", ".mp4").getAbsolutePath();
        try {
            HashSet<Integer> samplesDropSet = new HashSet<Integer>();
            // Drop frames with sample index 57 to 67, P frame at 57.
            IntStream.rangeClosed(57, 67).forEach(samplesDropSet::add);
            // Drop frames with sample index 173 to 200, B frame at 173.
            IntStream.rangeClosed(173, 200).forEach(samplesDropSet::add);
            // No start offsets for any track.
            cloneMediaWithSamplesDropAndStartOffsets(source, outputFilePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, samplesDropSet, null);
            verifyTSWithSamplesDropAndStartOffset(
                    source, true /* has B frames */, outputFilePath, samplesDropSet, null);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    /**
     * Test: makes sure if audio/video muxing while framedrops once using MPEG4Writer
     * works with B Frames.
     */
    @Test
    public void testTimestampsAudioBVideoFramesDropOnce() throws Exception {
        final String source = "video_h264_main_b_frames.mp4";
        String outputFilePath = File.createTempFile(
            "MediaMuxerTest_testTimestampsAudioBVideoFramesDropOnce", ".mp4").getAbsolutePath();
        try {
            HashSet<Integer> samplesDropSet = new HashSet<Integer>();
            // Drop frames from sample index 56 to 76, I frame at 56.
            IntStream.rangeClosed(56, 76).forEach(samplesDropSet::add);
            // No start offsets for any track.
            cloneMediaWithSamplesDropAndStartOffsets(source, outputFilePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, samplesDropSet, null);
            verifyTSWithSamplesDropAndStartOffset(
                    source, true /* has B frames */, outputFilePath, samplesDropSet, null);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    /**
     * Test: makes sure if audio/video muxing while framedrops twice using MPEG4Writer
     * works with B Frames.
     */
    @Test
    public void testTimestampsAudioBVideoFramesDropTwice() throws Exception {
        final String source = "video_h264_main_b_frames.mp4";
        String outputFilePath = File.createTempFile(
            "MediaMuxerTest_testTimestampsAudioBVideoFramesDropTwice", ".mp4").getAbsolutePath();
        try {
            HashSet<Integer> samplesDropSet = new HashSet<Integer>();
            // Drop frames with sample index 57 to 67, P frame at 57.
            IntStream.rangeClosed(57, 67).forEach(samplesDropSet::add);
            // Drop frames with sample index 173 to 200, B frame at 173.
            IntStream.rangeClosed(173, 200).forEach(samplesDropSet::add);
            // No start offsets for any track.
            cloneMediaWithSamplesDropAndStartOffsets(source, outputFilePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, samplesDropSet, null);
            verifyTSWithSamplesDropAndStartOffset(
                    source, true /* has B frames */, outputFilePath, samplesDropSet, null);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    /**
     * Test: makes sure if audio/video muxing using MPEG4Writer works with B Frames
     * when video frames start later than audio.
     */
    @Test
    public void testTimestampsAudioBVideoStartOffsetVideo() throws Exception {
        Vector<Integer> startOffsetUsVect = new Vector<Integer>();
        // Video starts at 400000us.
        startOffsetUsVect.add(400000);
        // Audio starts at 0us.
        startOffsetUsVect.add(0);
        checkTimestampsAudioBVideoDiffStartOffsets(startOffsetUsVect);
    }

    /**
     * Test: makes sure if audio/video muxing using MPEG4Writer works with B Frames
     * when video and audio samples start after zero, video later than audio.
     */
    @Test
    public void testTimestampsAudioBVideoStartOffsetVideoAudio() throws Exception {
        Vector<Integer> startOffsetUsVect = new Vector<Integer>();
        // Video starts at 400000us.
        startOffsetUsVect.add(400000);
        // Audio starts at 200000us.
        startOffsetUsVect.add(200000);
        checkTimestampsAudioBVideoDiffStartOffsets(startOffsetUsVect);
    }

    /**
     * Test: makes sure if audio/video muxing using MPEG4Writer works with B Frames
     * when video and audio samples start after zero, audio later than video.
     */
    @Test
    public void testTimestampsAudioBVideoStartOffsetAudioVideo() throws Exception {
        if (!MediaUtils.check(mAndroid11, "test needs Android 11")) return;

        Vector<Integer> startOffsetUsVect = new Vector<Integer>();
        // Video starts at 200000us.
        startOffsetUsVect.add(200000);
        // Audio starts at 400000us.
        startOffsetUsVect.add(400000);
        checkTimestampsAudioBVideoDiffStartOffsets(startOffsetUsVect);
    }

    /**
     * Test: makes sure if audio/video muxing using MPEG4Writer works with B Frames
     * when video starts after zero and audio starts before zero.
     */
    @Test
    public void testTimestampsAudioBVideoStartOffsetNegativeAudioVideo() throws Exception {
        if (!MediaUtils.check(mAndroid11, "test needs Android 11")) return;

        Vector<Integer> startOffsetUsVect = new Vector<Integer>();
        // Video starts at 200000us.
        startOffsetUsVect.add(200000);
        // Audio starts at -23220us, multiple of duration of one frame (1024/44100hz)
        startOffsetUsVect.add(-23220);
        checkTimestampsAudioBVideoDiffStartOffsets(startOffsetUsVect);
    }

    /**
     * Test: makes sure if audio/video muxing using MPEG4Writer works with B Frames when audio
     * samples start later than video.
     */
    @Test
    public void testTimestampsAudioBVideoStartOffsetAudio() throws Exception {
        if (!MediaUtils.check(mAndroid11, "test needs Android 11")) return;

        Vector<Integer> startOffsetUsVect = new Vector<Integer>();
        // Video starts at 0us.
        startOffsetUsVect.add(0);
        // Audio starts at 400000us.
        startOffsetUsVect.add(400000);
        checkTimestampsAudioBVideoDiffStartOffsets(startOffsetUsVect);
    }

    /**
     * Test: make sure if audio/video muxing works good with different start offsets for
     * audio and video, audio later than video at 0us.
     */
    @Test
    public void testTimestampsStartOffsetAudio() throws Exception {
        if (!MediaUtils.check(mAndroid11, "test needs Android 11")) return;

        Vector<Integer> startOffsetUsVect = new Vector<Integer>();
        // Video starts at 0us.
        startOffsetUsVect.add(0);
        // Audio starts at 500000us.
        startOffsetUsVect.add(500000);
        checkTimestampsWithStartOffsets(startOffsetUsVect);
    }

    /**
     * Test: make sure if audio/video muxing works good with different start offsets for
     * audio and video, video later than audio at 0us.
     */
    @Test
    public void testTimestampsStartOffsetVideo() throws Exception {
        if (!MediaUtils.check(mAndroid11, "test needs Android 11")) return;

        Vector<Integer> startOffsetUsVect = new Vector<Integer>();
        // Video starts at 500000us.
        startOffsetUsVect.add(500000);
        // Audio starts at 0us.
        startOffsetUsVect.add(0);
        checkTimestampsWithStartOffsets(startOffsetUsVect);
    }

    /**
     * Test: make sure if audio/video muxing works good with different start offsets for
     * audio and video, audio later than video, positive offsets for both.
     */
    @Test
    public void testTimestampsStartOffsetVideoAudio() throws Exception {
        if (!MediaUtils.check(mAndroid11, "test needs Android 11")) return;

        Vector<Integer> startOffsetUsVect = new Vector<Integer>();
        // Video starts at 250000us.
        startOffsetUsVect.add(250000);
        // Audio starts at 500000us.
        startOffsetUsVect.add(500000);
        checkTimestampsWithStartOffsets(startOffsetUsVect);
    }

    /**
     * Test: make sure if audio/video muxing works good with different start offsets for
     * audio and video, video later than audio, positive offets for both.
     */
    @Test
    public void testTimestampsStartOffsetAudioVideo() throws Exception {
        if (!MediaUtils.check(mAndroid11, "test needs Android 11")) return;

        Vector<Integer> startOffsetUsVect = new Vector<Integer>();
        // Video starts at 500000us.
        startOffsetUsVect.add(500000);
        // Audio starts at 250000us.
        startOffsetUsVect.add(250000);
        checkTimestampsWithStartOffsets(startOffsetUsVect);
    }

    /**
     * Test: make sure if audio/video muxing works good with different start offsets for
     * audio and video, video later than audio, audio before zero.
     */
    @Test
    public void testTimestampsStartOffsetNegativeAudioVideo() throws Exception {
        if (!MediaUtils.check(mAndroid11, "test needs Android 11")) return;

        Vector<Integer> startOffsetUsVect = new Vector<Integer>();
        // Video starts at 50000us.
        startOffsetUsVect.add(50000);
        // Audio starts at -23220us, multiple of duration of one frame (1024/44100hz)
        startOffsetUsVect.add(-23220);
        checkTimestampsWithStartOffsets(startOffsetUsVect);
    }

    @Test
    public void testAdditionOfHdrStaticMetadata() throws Exception {
        String outputFilePath =
                File.createTempFile("MediaMuxerTest_testAdditionOfHdrStaticMetadata", ".mp4")
                        .getAbsolutePath();
        // HDR static metadata encoding the following information (format defined in CTA-861.3 -
        // Static Metadata Descriptor, includes descriptor ID):
        // Mastering display color primaries:
        //   R: x=0.677980 y=0.321980, G: x=0.245000 y=0.703000, B: x=0.137980 y=0.052000,
        //   White point: x=0.312680 y=0.328980
        // Mastering display luminance min: 0.0000 cd/m2, max: 1000 cd/m2
        // Maximum Content Light Level: 1100 cd/m2
        // Maximum Frame-Average Light Level: 180 cd/m2
        byte[] inputHdrStaticMetadata =
                Util.getBytesFromHexString("006b84e33eda2f4e89f31a280a123d4140e80300004c04b400");
        Function<MediaFormat, MediaFormat> staticMetadataAdditionFunction =
                (mediaFormat) -> {
                    if (!mediaFormat.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                        return mediaFormat;
                    }
                    MediaFormat result = new MediaFormat(mediaFormat);
                    result.setByteBuffer(
                            MediaFormat.KEY_HDR_STATIC_INFO,
                            ByteBuffer.wrap(inputHdrStaticMetadata));
                    return result;
                };
        try {
            cloneMediaUsingMuxer(
                    /* srcMedia= */ "video_h264_main_b_frames.mp4",
                    outputFilePath,
                    /* expectedTrackCount= */ 2,
                    /* degrees= */ 0,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                    /* signalEOS= */ false,
                    staticMetadataAdditionFunction);
            assertArrayEquals(
                    inputHdrStaticMetadata, getVideoColorInfo(outputFilePath).hdrStaticInfo);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    @Test
    public void testAdditionOfInvalidHdrStaticMetadataIsIgnored() throws Exception {
        String outputFilePath =
                File.createTempFile(
                                "MediaMuxerTest_testAdditionOfInvalidHdrStaticMetadataIsIgnored",
                                ".mp4")
                        .getAbsolutePath();
        Function<MediaFormat, MediaFormat> staticMetadataAdditionFunction =
                (mediaFormat) -> {
                    MediaFormat result = new MediaFormat(mediaFormat);
                    // The input static info should be ignored, because its size is invalid (26 vs
                    // expected 25).
                    result.setByteBuffer(
                            MediaFormat.KEY_HDR_STATIC_INFO, ByteBuffer.allocateDirect(26));
                    return result;
                };
        try {
            cloneMediaUsingMuxer(
                    /* srcMedia= */ "video_h264_main_b_frames.mp4",
                    outputFilePath,
                    /* expectedTrackCount= */ 2,
                    /* degrees= */ 0,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                    /* signalEOS= */ false,
                    staticMetadataAdditionFunction);
            assertNull(getVideoColorInfo(outputFilePath));
        } finally {
            new File(outputFilePath).delete();
        }
    }

    /**
     * Test: makes sure if audio/video muxing using MPEG4Writer works with B Frames
     * when video and audio samples start after different times.
     */
    private void checkTimestampsAudioBVideoDiffStartOffsets(Vector<Integer> startOffsetUs)
            throws Exception {
        MPEG4CheckTimestampsAudioBVideoDiffStartOffsets(startOffsetUs);
        // TODO: uncomment webm testing once bugs related to timestamps in webmwriter are fixed.
        // WebMCheckTimestampsAudioBVideoDiffStartOffsets(startOffsetUsVect);
    }

    private void MPEG4CheckTimestampsAudioBVideoDiffStartOffsets(Vector<Integer> startOffsetUs)
            throws Exception {
        if (VERBOSE) {
            Log.v(TAG, "MPEG4CheckTimestampsAudioBVideoDiffStartOffsets");
        }
        final String source = "video_h264_main_b_frames.mp4";
        String outputFilePath = File.createTempFile(
            "MediaMuxerTest_testTimestampsAudioBVideoDiffStartOffsets", ".mp4").getAbsolutePath();
        try {
            cloneMediaWithSamplesDropAndStartOffsets(source, outputFilePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, null, startOffsetUs);
            verifyTSWithSamplesDropAndStartOffset(
                    source, true /* has B frames */, outputFilePath, null, startOffsetUs);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    /*
     * Check if timestamps are written consistently across all formats supported by MediaMuxer.
     */
    private void checkTimestampsWithStartOffsets(Vector<Integer> startOffsetUsVect)
            throws Exception {
        MPEG4CheckTimestampsWithStartOffsets(startOffsetUsVect);
        // TODO: uncomment webm testing once bugs related to timestamps in webmwriter are fixed.
        // WebMCheckTimestampsWithStartOffsets(startOffsetUsVect);
        // TODO: need to add other formats, OGG, AAC, AMR
    }

    /**
     * Make sure if audio/video muxing using MPEG4Writer works good with different start
     * offsets for audio and video.
     */
    private void MPEG4CheckTimestampsWithStartOffsets(Vector<Integer> startOffsetUsVect)
            throws Exception {
        if (VERBOSE) {
            Log.v(TAG, "MPEG4CheckTimestampsWithStartOffsets");
        }
        final String source = "video_480x360_mp4_h264_500kbps_30fps_aac_stereo_128kbps_44100hz.mp4";
        String outputFilePath =
            File.createTempFile("MediaMuxerTest_MPEG4CheckTimestampsWithStartOffsets", ".mp4")
                .getAbsolutePath();
        try {
            cloneMediaWithSamplesDropAndStartOffsets(source, outputFilePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, null, startOffsetUsVect);
            verifyTSWithSamplesDropAndStartOffset(
                    source, false /* no B frames */, outputFilePath, null, startOffsetUsVect);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    /**
     * Make sure if audio/video muxing using WebMWriter works good with different start
     * offsets for audio and video.
     */
    private void WebMCheckTimestampsWithStartOffsets(Vector<Integer> startOffsetUsVect)
            throws Exception {
        if (VERBOSE) {
            Log.v(TAG, "WebMCheckTimestampsWithStartOffsets");
        }
        final String source =
                "video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm";
        String outputFilePath =
            File.createTempFile("MediaMuxerTest_WebMCheckTimestampsWithStartOffsets", ".webm")
                .getAbsolutePath();
        try {
            cloneMediaWithSamplesDropAndStartOffsets(source, outputFilePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, null, startOffsetUsVect);
            verifyTSWithSamplesDropAndStartOffset(
                    source, false /* no B frames */, outputFilePath, null, startOffsetUsVect);
        } finally {
            new File(outputFilePath).delete();
        }
    }

    /** Clones a media file and then compares against the source file to make sure they match. */
    private void cloneAndVerify(
            final String srcMedia,
            String outputMediaFile,
            int expectedTrackCount,
            int degrees,
            int fmt,
            boolean signalEos)
            throws IOException {
        cloneAndVerify(
                srcMedia,
                outputMediaFile,
                expectedTrackCount,
                degrees,
                fmt,
                signalEos,
                Function.identity());
    }

    /**
     * Clones a given file using MediaMuxer and verifies the output matches the input.
     *
     * <p>See {@link #cloneMediaUsingMuxer} for information about the parameters.
     */
    private void cloneAndVerify(
            final String srcMedia,
            String outputMediaFile,
            int expectedTrackCount,
            int degrees,
            int fmt,
            boolean signalEos,
            Function<MediaFormat, MediaFormat> muxerInputTrackFormatTransformer)
            throws IOException {
        try {
            cloneMediaUsingMuxer(
                    srcMedia,
                    outputMediaFile,
                    expectedTrackCount,
                    degrees,
                    fmt,
                    signalEos,
                    muxerInputTrackFormatTransformer);
            if (fmt == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 ||
                    fmt == MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP) {
                verifyAttributesMatch(srcMedia, outputMediaFile, degrees);
                verifyLocationInFile(outputMediaFile);
            }
            // Verify timestamp of all samples.
            verifyTSWithSamplesDropAndStartOffset(
                    srcMedia, false /* no B frames */,outputMediaFile, null, null);
        } finally {
            new File(outputMediaFile).delete();
        }
    }

    /**
     * Clones a given file using MediaMuxer.
     *
     * @param srcMedia Input file path passed to extractor
     * @param dstMediaPath Output file path passed to muxer
     * @param expectedTrackCount Expected number of tracks in the input file
     * @param degrees orientation hint in degrees
     * @param fmt one of the values defined in {@link MediaMuxer.OutputFormat}.
     * @param signalEos explicitly signal EOS by sending an empty packet after muxing. Only works
     *     fmt is MUXER_OUTPUT_MPEG_4.
     * @param muxerInputTrackFormatTransformer Function applied on the MediaMuxer input formats. If
     *     the function returns null for a given MediaFormat, the corresponding track is discarded
     *     and not passed to MediaMuxer.
     * @throws IOException if muxer failed to open output file for write.
     */
    private void cloneMediaUsingMuxer(
            final String srcMedia,
            String dstMediaPath,
            int expectedTrackCount,
            int degrees,
            int fmt,
            boolean signalEos,
            Function<MediaFormat, MediaFormat> muxerInputTrackFormatTransformer)
            throws IOException {
        // Set up MediaExtractor to read from the source.
        AssetFileDescriptor srcFd = getAssetFileDescriptorFor(srcMedia);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(srcFd.getFileDescriptor(), srcFd.getStartOffset(),
                srcFd.getLength());

        int trackCount = extractor.getTrackCount();
        assertEquals("wrong number of tracks", expectedTrackCount, trackCount);

        // Set up MediaMuxer for the destination.
        MediaMuxer muxer;
        muxer = new MediaMuxer(dstMediaPath, fmt);

        // Set up the tracks.
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            MediaFormat muxedFormat = muxerInputTrackFormatTransformer.apply(format);
            if (muxedFormat != null) {
                extractor.selectTrack(i);
                int dstIndex = muxer.addTrack(muxedFormat);
                indexMap.put(i, dstIndex);
            }
        }

        // Copy the samples from MediaExtractor to MediaMuxer.
        boolean sawEOS = false;
        int bufferSize = MAX_SAMPLE_SIZE;
        int frameCount = 0;
        int offset = 100;

        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        BufferInfo bufferInfo = new BufferInfo();

        if (degrees >= 0) {
            muxer.setOrientationHint(degrees);
        }

        if (fmt == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 ||
            fmt == MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP) {
            // Test setLocation out of bound cases
            try {
                muxer.setLocation(BAD_LATITUDE, LONGITUDE);
                fail("setLocation succeeded with bad argument: [" + BAD_LATITUDE + "," + LONGITUDE
                    + "]");
            } catch (IllegalArgumentException e) {
                // Expected
            }
            try {
                muxer.setLocation(LATITUDE, BAD_LONGITUDE);
                fail("setLocation succeeded with bad argument: [" + LATITUDE + "," + BAD_LONGITUDE
                    + "]");
            } catch (IllegalArgumentException e) {
                // Expected
            }

            muxer.setLocation(LATITUDE, LONGITUDE);
        }

        muxer.start();
        while (!sawEOS) {
            bufferInfo.offset = offset;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);

            if (bufferInfo.size < 0) {
                if (VERBOSE) {
                    Log.d(TAG, "saw input EOS.");
                }
                sawEOS = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                bufferInfo.flags = extractor.getSampleFlags();
                int trackIndex = extractor.getSampleTrackIndex();

                muxer.writeSampleData(indexMap.get(trackIndex), dstBuf,
                        bufferInfo);
                extractor.advance();

                frameCount++;
                if (VERBOSE) {
                    Log.d(TAG, "Frame (" + frameCount + ") " +
                            "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                            " Flags:" + bufferInfo.flags +
                            " TrackIndex:" + trackIndex +
                            " Size(KB) " + bufferInfo.size / 1024);
                }
            }
        }

        // By explicitly passing in an EOS buffer with the correct timestamp, we can control the
        // duration of the last sample so that the duration of the produced file matches the input
        // file. This is needed for input files whose last sample's duration doesn't match the
        // preceding one, because the MP4 MediaMuxer uses the duration of the sample second to last
        // for the last sample by default.
        if (signalEos) {
            assertEquals(
                    "signalEos is only supported for MP4",
                    fmt,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            // Signal EOS with the correct duration for the last frame.
            bufferInfo.size = 0;
            bufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                bufferInfo.presentationTimeUs = format.getLong(MediaFormat.KEY_DURATION, 0);
                if (bufferInfo.presentationTimeUs != 0) {
                    muxer.writeSampleData(indexMap.get(i), dstBuf, bufferInfo);
                }
            }
        }

        muxer.stop();
        muxer.release();
        extractor.release();
        srcFd.close();
        return;
    }

    /**
     * Compares some attributes using MediaMetadataRetriever to make sure the
     * cloned media file matches the source file.
     */
    private void verifyAttributesMatch(final String srcMedia, String testMediaPath,
            int degrees) throws IOException {
        AssetFileDescriptor testFd = getAssetFileDescriptorFor(srcMedia);

        MediaMetadataRetriever retrieverSrc = new MediaMetadataRetriever();
        retrieverSrc.setDataSource(testFd.getFileDescriptor(),
                testFd.getStartOffset(), testFd.getLength());

        MediaMetadataRetriever retrieverTest = new MediaMetadataRetriever();
        retrieverTest.setDataSource(testMediaPath);

        String testDegrees = retrieverTest.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        if (testDegrees != null) {
            assertEquals("Different degrees", degrees,
                    Integer.parseInt(testDegrees));
        }

        String heightSrc = retrieverSrc.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String heightTest = retrieverTest.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        assertEquals("Different height", heightSrc,
                heightTest);

        String widthSrc = retrieverSrc.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String widthTest = retrieverTest.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        assertEquals("Different width", widthSrc,
                widthTest);

        //TODO: need to check each individual track's duration also.
        String durationSrc = retrieverSrc.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION);
        String durationTest = retrieverTest.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION);
        assertEquals("Different duration", durationSrc,
                durationTest);

        retrieverSrc.release();
        retrieverTest.release();
        testFd.close();
    }

    private void verifyLocationInFile(String fileName) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(fileName);
        String location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
        assertNotNull("No location information found in file " + fileName, location);


        // parsing String location and recover the location information in floats
        // Make sure the tolerance is very small - due to rounding errors.

        // Trim the trailing slash, if any.
        int lastIndex = location.lastIndexOf('/');
        if (lastIndex != -1) {
            location = location.substring(0, lastIndex);
        }

        // Get the position of the -/+ sign in location String, which indicates
        // the beginning of the longitude.
        int minusIndex = location.lastIndexOf('-');
        int plusIndex = location.lastIndexOf('+');

        assertTrue("+ or - is not found or found only at the beginning [" + location + "]",
                (minusIndex > 0 || plusIndex > 0));
        int index = Math.max(minusIndex, plusIndex);

        float latitude = Float.parseFloat(location.substring(0, index));
        float longitude = Float.parseFloat(location.substring(index));
        assertTrue("Incorrect latitude: " + latitude + " [" + location + "]",
                Math.abs(latitude - LATITUDE) <= TOLERANCE);
        assertTrue("Incorrect longitude: " + longitude + " [" + location + "]",
                Math.abs(longitude - LONGITUDE) <= TOLERANCE);
        retriever.release();
    }

    /**
     * Uses 2 MediaExtractor, seeking to the same position, reads the sample and
     * makes sure the samples match.
     */
    private void verifySamplesMatch(final String srcMedia, String testMediaPath, int seekToUs,
            long offsetTimeUs) throws IOException {
        AssetFileDescriptor testFd = getAssetFileDescriptorFor(srcMedia);
        MediaExtractor extractorSrc = new MediaExtractor();
        extractorSrc.setDataSource(testFd.getFileDescriptor(),
                testFd.getStartOffset(), testFd.getLength());
        int trackCount = extractorSrc.getTrackCount();
        final int videoTrackIndex = 0;

        MediaExtractor extractorTest = new MediaExtractor();
        extractorTest.setDataSource(testMediaPath);

        assertEquals("wrong number of tracks", trackCount,
                extractorTest.getTrackCount());

        // Make sure the format is the same and select them
        for (int i = 0; i < trackCount; i++) {
            MediaFormat formatSrc = extractorSrc.getTrackFormat(i);
            MediaFormat formatTest = extractorTest.getTrackFormat(i);

            String mimeIn = formatSrc.getString(MediaFormat.KEY_MIME);
            String mimeOut = formatTest.getString(MediaFormat.KEY_MIME);
            if (!(mimeIn.equals(mimeOut))) {
                fail("format didn't match on track No." + i +
                        formatSrc.toString() + "\n" + formatTest.toString());
            }
            extractorSrc.selectTrack(videoTrackIndex);
            extractorTest.selectTrack(videoTrackIndex);

            // Pick a time and try to compare the frame.
            extractorSrc.seekTo(seekToUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            extractorTest.seekTo(seekToUs + offsetTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            int bufferSize = MAX_SAMPLE_SIZE;
            ByteBuffer byteBufSrc = ByteBuffer.allocate(bufferSize);
            ByteBuffer byteBufTest = ByteBuffer.allocate(bufferSize);

            int srcBufSize = extractorSrc.readSampleData(byteBufSrc, 0);
            int testBufSize = extractorTest.readSampleData(byteBufTest, 0);

            if (!(byteBufSrc.equals(byteBufTest))) {
                if (VERBOSE) {
                    Log.d(TAG,
                            "srcTrackIndex:" + extractorSrc.getSampleTrackIndex()
                                    + "  testTrackIndex:" + extractorTest.getSampleTrackIndex());
                    Log.d(TAG,
                            "srcTSus:" + extractorSrc.getSampleTime()
                                    + " testTSus:" + extractorTest.getSampleTime());
                    Log.d(TAG, "srcBufSize:" + srcBufSize + "testBufSize:" + testBufSize);
                }
                fail("byteBuffer didn't match");
            }
            extractorSrc.unselectTrack(i);
            extractorTest.unselectTrack(i);
        }
        extractorSrc.release();
        extractorTest.release();
        testFd.close();
    }

    /**
     * Using MediaMuxer and MediaExtractor to mux a media file from another file while skipping
     * some video frames as in the issues b/63590381 and b/64949961.
     */
    private void simulateVideoFramesDropIssuesAndMux(final String srcMedia, String dstMediaPath,
            int expectedTrackCount, int fmt) throws IOException {
        // Set up MediaExtractor to read from the source.
        AssetFileDescriptor srcFd = getAssetFileDescriptorFor(srcMedia);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(srcFd.getFileDescriptor(), srcFd.getStartOffset(),
            srcFd.getLength());

        int trackCount = extractor.getTrackCount();
        assertEquals("wrong number of tracks", expectedTrackCount, trackCount);

        // Set up MediaMuxer for the destination.
        MediaMuxer muxer;
        muxer = new MediaMuxer(dstMediaPath, fmt);

        // Set up the tracks.
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);

        for (int i = 0; i < trackCount; i++) {
            extractor.selectTrack(i);
            MediaFormat format = extractor.getTrackFormat(i);
            int dstIndex = muxer.addTrack(format);
            indexMap.put(i, dstIndex);
        }

        // Copy the samples from MediaExtractor to MediaMuxer.
        boolean sawEOS = false;
        int bufferSize = MAX_SAMPLE_SIZE;
        int sampleCount = 0;
        int offset = 0;
        int videoSampleCount = 0;
        // Counting frame index values starting from 1
        final int muxAllTypeVideoFramesUntilIndex = 136; // I/P/B frames passed as it is until this
        final int muxAllTypeVideoFramesFromIndex = 171; // I/P/B frames passed as it is from this
        final int pFrameBeforeARandomBframeIndex = 137;
        final int bFrameAfterPFrameIndex = pFrameBeforeARandomBframeIndex+1;

        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        BufferInfo bufferInfo = new BufferInfo();

        muxer.start();
        while (!sawEOS) {
            bufferInfo.offset = 0;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);
            if (bufferInfo.size < 0) {
                if (VERBOSE) {
                    Log.d(TAG, "saw input EOS.");
                }
                sawEOS = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                bufferInfo.flags = extractor.getSampleFlags();
                int trackIndex = extractor.getSampleTrackIndex();
                // Video track at index 0, skip some video frames while muxing.
                if (trackIndex == 0) {
                    ++videoSampleCount;
                    if (VERBOSE) {
                        Log.v(TAG, "videoSampleCount : " + videoSampleCount);
                    }
                    if (videoSampleCount <= muxAllTypeVideoFramesUntilIndex
                            || videoSampleCount == bFrameAfterPFrameIndex) {
                        // Write frame as it is.
                        muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                    } else if (videoSampleCount == pFrameBeforeARandomBframeIndex
                            || videoSampleCount >= muxAllTypeVideoFramesFromIndex) {
                        // Adjust time stamp for this P frame to a few frames later, say ~5seconds
                        bufferInfo.presentationTimeUs += OFFSET_TIME_US;
                        muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                    } else {
                        // Skip frames after bFrameAfterPFrameIndex
                        // and before muxAllTypeVideoFramesFromIndex.
                        if (VERBOSE) {
                            Log.i(TAG, "skipped this frame");
                        }
                    }
                } else {
                    // write audio data as it is continuously
                    muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                }
                extractor.advance();
                sampleCount++;
                if (VERBOSE) {
                    Log.d(TAG, "Frame (" + sampleCount + ") " +
                            "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                            " Flags:" + bufferInfo.flags +
                            " TrackIndex:" + trackIndex +
                            " Size(bytes) " + bufferInfo.size );
                }
            }
        }

        muxer.stop();
        muxer.release();
        extractor.release();
        srcFd.close();

        return;
    }

    /**
     * Uses two MediaExtractor's and checks whether timestamps of first few and another few
     *  from last sync frame matches
     */
    private void verifyAFewSamplesTimestamp(final String srcMedia, String testMediaPath)
            throws IOException {
        final int numFramesTSCheck = 10; // Num frames to be checked for its timestamps

        AssetFileDescriptor srcFd = getAssetFileDescriptorFor(srcMedia);
        MediaExtractor extractorSrc = new MediaExtractor();
        extractorSrc.setDataSource(srcFd.getFileDescriptor(),
            srcFd.getStartOffset(), srcFd.getLength());
        MediaExtractor extractorTest = new MediaExtractor();
        extractorTest.setDataSource(testMediaPath);

        int trackCount = extractorSrc.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractorSrc.getTrackFormat(i);
            extractorSrc.selectTrack(i);
            extractorTest.selectTrack(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                // Check time stamps for numFramesTSCheck frames from 33333us.
                checkNumFramesTimestamp(33333, 0, numFramesTSCheck, extractorSrc, extractorTest);
                // Check time stamps for numFramesTSCheck frames from 9333333 -
                // sync frame after framedrops at index 172 of video track.
                checkNumFramesTimestamp(
                        9333333, OFFSET_TIME_US, numFramesTSCheck, extractorSrc, extractorTest);
            } else if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                // Check timestamps for all audio frames. Test file has 427 audio frames.
                checkNumFramesTimestamp(0, 0, 427, extractorSrc, extractorTest);
            }
            extractorSrc.unselectTrack(i);
            extractorTest.unselectTrack(i);
        }

        extractorSrc.release();
        extractorTest.release();
        srcFd.close();
    }

    private void checkNumFramesTimestamp(long seekTimeUs, long offsetTimeUs, int numFrames,
            MediaExtractor extractorSrc, MediaExtractor extractorTest) {
        long srcSampleTimeUs = -1;
        long testSampleTimeUs = -1;
        extractorSrc.seekTo(seekTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        extractorTest.seekTo(seekTimeUs + offsetTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        while (numFrames-- > 0 ) {
            srcSampleTimeUs = extractorSrc.getSampleTime();
            testSampleTimeUs = extractorTest.getSampleTime();
            if (srcSampleTimeUs == -1 || testSampleTimeUs == -1) {
                fail("either of tracks reached end of stream");
            }
            if ((srcSampleTimeUs + offsetTimeUs) != testSampleTimeUs) {
                if (VERBOSE) {
                    Log.d(TAG, "srcTrackIndex:" + extractorSrc.getSampleTrackIndex() +
                        "  testTrackIndex:" + extractorTest.getSampleTrackIndex());
                    Log.d(TAG, "srcTSus:" + srcSampleTimeUs + " testTSus:" + testSampleTimeUs);
                }
                fail("timestamps didn't match");
            }
            extractorSrc.advance();
            extractorTest.advance();
        }
    }

    /**
     * Using MediaMuxer and MediaExtractor to mux a media file from another file while skipping
     * 0 or more video frames and desired start offsets for each track.
     * startOffsetUsVect : order of tracks is the same as in the input file
     */
    private void cloneMediaWithSamplesDropAndStartOffsets(final String srcMedia, String dstMediaPath,
            int fmt, HashSet<Integer> samplesDropSet, Vector<Integer> startOffsetUsVect)
            throws IOException {
        // Set up MediaExtractor to read from the source.
        AssetFileDescriptor srcFd = getAssetFileDescriptorFor(srcMedia);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(srcFd.getFileDescriptor(), srcFd.getStartOffset(),
            srcFd.getLength());

        int trackCount = extractor.getTrackCount();

        // Set up MediaMuxer for the destination.
        MediaMuxer muxer;
        muxer = new MediaMuxer(dstMediaPath, fmt);

        // Set up the tracks.
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);

        int videoTrackIndex = 100;
        int videoStartOffsetUs = 0;
        int audioTrackIndex = 100;
        int audioStartOffsetUs = 0;
        for (int i = 0; i < trackCount; i++) {
            extractor.selectTrack(i);
            MediaFormat format = extractor.getTrackFormat(i);
            int dstIndex = muxer.addTrack(format);
            indexMap.put(i, dstIndex);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                videoTrackIndex = i;
                // Make sure there's an entry for video track.
                if (startOffsetUsVect != null && (videoTrackIndex < startOffsetUsVect.size())) {
                    videoStartOffsetUs = startOffsetUsVect.get(videoTrackIndex);
                }
            }
            if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                audioTrackIndex = i;
                // Make sure there's an entry for audio track.
                if (startOffsetUsVect != null && (audioTrackIndex < startOffsetUsVect.size())) {
                    audioStartOffsetUs = startOffsetUsVect.get(audioTrackIndex);
                }
            }
        }

        // Copy the samples from MediaExtractor to MediaMuxer.
        boolean sawEOS = false;
        int bufferSize = MAX_SAMPLE_SIZE;
        int sampleCount = 0;
        int offset = 0;
        int videoSampleCount = 0;

        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        BufferInfo bufferInfo = new BufferInfo();

        muxer.start();
        while (!sawEOS) {
            bufferInfo.offset = 0;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);
            if (bufferInfo.size < 0) {
                if (VERBOSE) {
                    Log.d(TAG, "saw input EOS.");
                }
                sawEOS = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                bufferInfo.flags = extractor.getSampleFlags();
                int trackIndex = extractor.getSampleTrackIndex();
                if (VERBOSE) {
                    Log.v(TAG, "TrackIndex:" + trackIndex + " PresentationTimeUs:" +
                                bufferInfo.presentationTimeUs + " Flags:" + bufferInfo.flags +
                                " Size(bytes)" + bufferInfo.size);
                }
                if (trackIndex == videoTrackIndex) {
                    ++videoSampleCount;
                    if (VERBOSE) {
                        Log.v(TAG, "videoSampleCount : " + videoSampleCount);
                    }
                    if (samplesDropSet == null || (!samplesDropSet.contains(videoSampleCount))) {
                        // Write video frame with start offset adjustment.
                        bufferInfo.presentationTimeUs += videoStartOffsetUs;
                        muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                    }
                    else {
                        if (VERBOSE) {
                            Log.v(TAG, "skipped this frame");
                        }
                    }
                } else {
                    // write audio sample with start offset adjustment.
                    bufferInfo.presentationTimeUs += audioStartOffsetUs;
                    muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                }
                extractor.advance();
                sampleCount++;
                if (VERBOSE) {
                    Log.i(TAG, "Sample (" + sampleCount + ")" +
                            " TrackIndex:" + trackIndex +
                            " PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                            " Flags:" + bufferInfo.flags +
                            " Size(bytes)" + bufferInfo.size );
                }
            }
        }

        muxer.stop();
        muxer.release();
        extractor.release();
        srcFd.close();

        return;
    }

    /*
     * Uses MediaExtractors and checks whether timestamps of all samples except in samplesDropSet
     *  and with start offsets adjustments for each track match.
     */
    private void verifyTSWithSamplesDropAndStartOffset(final String srcMedia, boolean hasBframes,
            String testMediaPath, HashSet<Integer> samplesDropSet,
            Vector<Integer> startOffsetUsVect) throws IOException {
        AssetFileDescriptor srcFd = getAssetFileDescriptorFor(srcMedia);
        MediaExtractor extractorSrc = new MediaExtractor();
        extractorSrc.setDataSource(srcFd.getFileDescriptor(),
            srcFd.getStartOffset(), srcFd.getLength());
        MediaExtractor extractorTest = new MediaExtractor();
        extractorTest.setDataSource(testMediaPath);

        int videoTrackIndex = -1;
        int videoStartOffsetUs = 0;
        int minStartOffsetUs = Integer.MAX_VALUE;
        int trackCount = extractorSrc.getTrackCount();

        /*
         * When all track's start offsets are positive, MPEG4Writer makes the start timestamp of the
         * earliest track as zero and adjusts all other tracks' timestamp accordingly.
         */
        // TODO: need to confirm if the above logic holds good with all others writers we support.
        if (startOffsetUsVect != null) {
            for (int startOffsetUs : startOffsetUsVect) {
                minStartOffsetUs = Math.min(startOffsetUs, minStartOffsetUs);
            }
        } else {
            minStartOffsetUs = 0;
        }

        if (minStartOffsetUs < 0) {
            /*
             * Atleast one of the start offsets were negative. We have some test cases with negative
             * offsets for audio, minStartOffset has to be reset as Writer won't adjust any of the
             * track's timestamps.
             */
            minStartOffsetUs = 0;
        }

        // Select video track.
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractorSrc.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                videoTrackIndex = i;
                if (startOffsetUsVect != null && videoTrackIndex < startOffsetUsVect.size()) {
                    videoStartOffsetUs = startOffsetUsVect.get(videoTrackIndex);
                }
                extractorSrc.selectTrack(videoTrackIndex);
                extractorTest.selectTrack(videoTrackIndex);
                checkVideoSamplesTimeStamps(extractorSrc, hasBframes, extractorTest, samplesDropSet,
                    videoStartOffsetUs - minStartOffsetUs);
                extractorSrc.unselectTrack(videoTrackIndex);
                extractorTest.unselectTrack(videoTrackIndex);
            }
        }

        int audioTrackIndex = -1;
        int audioSampleCount = 0;
        int audioStartOffsetUs = 0;
        //select audio track
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractorSrc.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                audioTrackIndex = i;
                if (startOffsetUsVect != null && audioTrackIndex < startOffsetUsVect.size()) {
                    audioStartOffsetUs = startOffsetUsVect.get(audioTrackIndex);
                }
                extractorSrc.selectTrack(audioTrackIndex);
                extractorTest.selectTrack(audioTrackIndex);
                checkAudioSamplesTimestamps(
                        extractorSrc, extractorTest, audioStartOffsetUs - minStartOffsetUs);
            }
        }

        extractorSrc.release();
        extractorTest.release();
        srcFd.close();
    }

    // Check timestamps of all video samples.
    private void checkVideoSamplesTimeStamps(MediaExtractor extractorSrc, boolean hasBFrames,
            MediaExtractor extractorTest, HashSet<Integer> samplesDropSet, int videoStartOffsetUs) {
        long srcSampleTimeUs = -1;
        long testSampleTimeUs = -1;
        boolean srcAdvance = false;
        boolean testAdvance = false;
        int videoSampleCount = 0;

        extractorSrc.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        extractorTest.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        if (VERBOSE) {
            Log.v(TAG, "srcTrackIndex:" + extractorSrc.getSampleTrackIndex() +
                        "  testTrackIndex:" + extractorTest.getSampleTrackIndex());
            Log.v(TAG, "videoStartOffsetUs:" + videoStartOffsetUs);
        }

        do {
            ++videoSampleCount;
            srcSampleTimeUs = extractorSrc.getSampleTime();
            testSampleTimeUs = extractorTest.getSampleTime();
            if (VERBOSE) {
                Log.v(TAG, "videoSampleCount:" + videoSampleCount);
                Log.i(TAG, "srcTSus:" + srcSampleTimeUs + " testTSus:" + testSampleTimeUs);
            }
            if (samplesDropSet == null || !samplesDropSet.contains(videoSampleCount)) {
                if (srcSampleTimeUs == -1 || testSampleTimeUs == -1) {
                    if (VERBOSE) {
                        Log.v(TAG, "srcUs:" + srcSampleTimeUs + "testUs:" + testSampleTimeUs);
                    }
                    fail("either source or test track reached end of stream");
                }
                /* Stts values within 0.1ms(100us) difference are fudged to save too many
                 * stts entries in MPEG4Writer.
                 */
                else if (Math.abs(srcSampleTimeUs + videoStartOffsetUs - testSampleTimeUs) > 100) {
                    if (VERBOSE) {
                        Log.v(TAG, "Fail:video timestamps didn't match");
                        Log.v(TAG,
                            "srcTrackIndex:" + extractorSrc.getSampleTrackIndex()
                                + "  testTrackIndex:" + extractorTest.getSampleTrackIndex());
                        Log.v(TAG, "srcTSus:" + srcSampleTimeUs + " testTSus:" + testSampleTimeUs);
                  }
                    fail("video timestamps didn't match");
                }
                testAdvance = extractorTest.advance();
            }
            srcAdvance = extractorSrc.advance();
        } while (srcAdvance && testAdvance);
        if (srcAdvance != testAdvance) {
            if (VERBOSE) {
                Log.v(TAG, "videoSampleCount:" + videoSampleCount);
            }
            fail("either video track has not reached its last sample");
        }
    }

    private void checkAudioSamplesTimestamps(MediaExtractor extractorSrc,
                MediaExtractor extractorTest, int audioStartOffsetUs) {
        long srcSampleTimeUs = -1;
        long testSampleTimeUs = -1;
        boolean srcAdvance = false;
        boolean testAdvance = false;
        int audioSampleCount = 0;

        extractorSrc.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        if (audioStartOffsetUs >= 0) {
            // Added edit list support for maintaining only the diff in start offsets of tracks.
            // TODO: Remove this once we add support for preserving absolute timestamps as well.
            extractorTest.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        } else {
            extractorTest.seekTo(audioStartOffsetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }
        if (VERBOSE) {
            Log.v(TAG, "audioStartOffsetUs:" + audioStartOffsetUs);
            Log.v(TAG, "srcTrackIndex:" + extractorSrc.getSampleTrackIndex() +
                        "  testTrackIndex:" + extractorTest.getSampleTrackIndex());
        }
        // Check timestamps of all audio samples.
        do {
            ++audioSampleCount;
            srcSampleTimeUs = extractorSrc.getSampleTime();
            testSampleTimeUs = extractorTest.getSampleTime();
            if (VERBOSE) {
                Log.v(TAG, "audioSampleCount:" + audioSampleCount);
                Log.v(TAG, "srcTSus:" + srcSampleTimeUs + " testTSus:" + testSampleTimeUs);
            }

            if (srcSampleTimeUs == -1 || testSampleTimeUs == -1) {
                if (VERBOSE) {
                    Log.v(TAG, "srcTSus:" + srcSampleTimeUs + " testTSus:" + testSampleTimeUs);
                }
                fail("either source or test track reached end of stream");
            }
            // > 1us to ignore any round off errors.
            else if (Math.abs(srcSampleTimeUs + audioStartOffsetUs - testSampleTimeUs) > 1) {
                fail("audio timestamps didn't match");
            }
            testAdvance = extractorTest.advance();
            srcAdvance = extractorSrc.advance();
        } while (srcAdvance && testAdvance);
        if (srcAdvance != testAdvance) {
            fail("either audio track has not reached its last sample");
        }
    }

    /** Returns the static HDR metadata in the given {@code file}, or null if not present. */
    private ColorInfo getVideoColorInfo(String path)
            throws ExecutionException, InterruptedException {
        TrackGroupArray trackGroupArray =
                MetadataRetriever.retrieveMetadata(getContext(), MediaItem.fromUri(path)).get();
        for (int i = 0; i < trackGroupArray.length; i++) {
            Format format = trackGroupArray.get(i).getFormat(0);
            if (format.sampleMimeType.startsWith("video/")) {
                return format.colorInfo;
            }
        }
        return null;
    }
}

