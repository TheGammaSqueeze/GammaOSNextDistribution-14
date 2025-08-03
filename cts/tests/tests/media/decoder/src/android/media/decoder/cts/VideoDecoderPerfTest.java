/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.media.MediaCodec;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.media.cts.TestArgs;
import android.media.cts.TestUtils;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.MediaPerfUtils;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(Parameterized.class)
public class VideoDecoderPerfTest extends MediaTestBase {
    private static final String TAG = "VideoDecoderPerfTest";
    private static final String REPORT_LOG_NAME = "CtsMediaDecoderTestCases";
    private static final int TOTAL_FRAMES = 30000;
    private static final int MIN_FRAMES = 3000;
    private static final int MAX_TIME_MS = 120000;  // 2 minutes
    private static final int MAX_TEST_TIMEOUT_MS = 300000;  // 5 minutes
    private static final int MIN_TEST_MS = 10000;  // 10 seconds
    private static final int NUMBER_OF_REPEATS = 2;

    private static final String AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String H263 = MediaFormat.MIMETYPE_VIDEO_H263;
    private static final String HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    private static final String MPEG2 = MediaFormat.MIMETYPE_VIDEO_MPEG2;
    private static final String MPEG4 = MediaFormat.MIMETYPE_VIDEO_MPEG4;
    private static final String VP8 = MediaFormat.MIMETYPE_VIDEO_VP8;
    private static final String VP9 = MediaFormat.MIMETYPE_VIDEO_VP9;
    private static final String AV1 = MediaFormat.MIMETYPE_VIDEO_AV1;

    private static final boolean GOOG = true;
    private static final boolean OTHER = false;

    private static final int MAX_SIZE_SAMPLES_IN_MEMORY_BYTES = 12 << 20;  // 12MB

    private final String mDecoderName;
    private final String mMediaType;
    private final String[] mResources;

    // each sample contains the buffer and the PTS offset from the frame index
    LinkedList<Pair<ByteBuffer, Double>> mSamplesInMemory = new LinkedList<Pair<ByteBuffer, Double>>();
    private MediaFormat mDecInputFormat;
    private MediaFormat mDecOutputFormat;
    private int mBitrate;

    private boolean mSkipRateChecking = false;
    private boolean mUpdatedSwCodec = false;
    static final String mInpPrefix = WorkDir.getMediaDirString();

    static private List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String)arg[0];
            if (TestArgs.shouldSkipMediaType(mediaType)) {
                continue;
            }
            String[] decoders = MediaUtils.getDecoderNamesForMime(mediaType);
            for (String decoder : decoders) {
                if (TestArgs.shouldSkipCodec(decoder)) {
                    continue;
                }
                Object[] testArgs = new Object[argLength + 1];
                // Add codec name as first argument and then copy all other arguments passed
                testArgs[0] = decoder;
                System.arraycopy(arg, 0, testArgs, 1, argLength);
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{3}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                // MediaType, resources, graphics display resolution
                {AVC, sAvcMedia0320x0240, "qvga"},
                {AVC, sAvcMedia0720x0480, "sd"},
                {AVC, sAvcMedia1280x0720, "hd"},
                {AVC, sAvcMedia1920x1080, "fullhd"},

                {H263, sH263Media0176x0144, "qcif"},
                {H263, sH263Media0352x0288, "cif"},

                {HEVC, sHevcMedia0352x0288, "cif"},
                {HEVC, sHevcMedia0640x0360, "vga"},
                {HEVC, sHevcMedia0720x0480, "sd"},
                {HEVC, sHevcMedia1280x0720, "hd"},
                {HEVC, sHevcMedia1920x1080, "fullhd"},
                {HEVC, sHevcMedia3840x2160, "uhd"},

                {MPEG4, sMpeg4Media0176x0144, "qcif"},
                {MPEG4, sMpeg4Media0480x0360, "360p"},
                {MPEG4, sMpeg4Media1280x0720, "hd"},

                {VP8, sVp8Media0320x0180, "qvga"},
                {VP8, sVp8Media0640x0360, "vga"},
                {VP8, sVp8Media1280x0720, "hd"},
                {VP8, sVp8Media1920x1080, "fullhd"},

                {VP9, sVp9Media0320x0180, "qvga"},
                {VP9, sVp9Media0640x0360, "vga"},
                {VP9, sVp9Media1280x0720, "hd"},
                {VP9, sVp9Media1920x1080, "fullhd"},
                {VP9, sVp9Media3840x2160, "uhd"},

                {AV1, sAv1Media0352x0288, "cif"},
                {AV1, sAv1Media0640x0360, "vga"},
                {AV1, sAv1Media0720x0480, "sd"},
                {AV1, sAv1Media1280x0720, "hd"},
                {AV1, sAv1Media1920x1080, "fullhd"},
        });
        return prepareParamList(exhaustiveArgsList);
    }

    public VideoDecoderPerfTest(String decodername, String mediaType, String[] resources,
            @SuppressWarnings("unused") String gfxcode) {
        mDecoderName = decodername;
        mMediaType = mediaType;
        mResources = resources;
    }

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        Bundle bundle = InstrumentationRegistry.getArguments();
        mSkipRateChecking = TextUtils.equals("true", bundle.getString("mts-media"));

        mUpdatedSwCodec =
                !TestUtils.isMainlineModuleFactoryVersion("com.google.android.media.swcodec");
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    private void decode(String name, final String resource, MediaFormat format) throws Exception {
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        String mime = format.getString(MediaFormat.KEY_MIME);

        // Ensure we can finish this test within the test timeout. Allow 25% slack (4/5).
        long maxTimeMs = Math.min(
                MAX_TEST_TIMEOUT_MS * 4 / 5 / NUMBER_OF_REPEATS, MAX_TIME_MS);
        // reduce test run on non-real device to maximum of 2 seconds
        if (MediaUtils.onFrankenDevice()) {
            maxTimeMs = Math.min(2000, maxTimeMs);
        }
        double measuredFps[] = new double[NUMBER_OF_REPEATS];

        for (int i = 0; i < NUMBER_OF_REPEATS; ++i) {
            // Decode to Surface.
            Log.d(TAG, "round #" + i + ": " + name + " for " + maxTimeMs + " msecs to surface");
            Surface s = getActivity().getSurfaceHolder().getSurface();
            // only verify the result for decode to surface case.
            measuredFps[i] = doDecode(name, resource, width, height, s, i, maxTimeMs);

            // We don't test decoding to buffer.
            // Log.d(TAG, "round #" + i + " decode to buffer");
            // doDecode(name, video, width, height, null, i, maxTimeMs);
        }

        // allow improvements in mainline-updated google-supplied software codecs.
        boolean fasterIsOk = mUpdatedSwCodec & TestUtils.isMainlineCodec(name);
        String error =
            MediaPerfUtils.verifyAchievableFrameRates(name, mime, width, height,
                           fasterIsOk,  measuredFps);
        // Performance numbers only make sense on real devices, so skip on non-real devices
        if ((MediaUtils.onFrankenDevice() || mSkipRateChecking) && error != null) {
            if (TestUtils.isMtsMode() && TestUtils.isMainlineCodec(name)) {
                assumeFalse(error, error.startsWith("Failed to get "));
            } else {
                // ensure there is data, but don't insist that it is correct
                assertFalse(error, error.startsWith("Failed to get "));
            }
        } else {
            assertNull(error, error);
        }
        mSamplesInMemory.clear();
    }

    private double doDecode(String name, final String filename, int w, int h, Surface surface,
            int round, long maxTimeMs) throws Exception {
        final String video = mInpPrefix + filename;
        Preconditions.assertTestFileExists(video);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(video);
        extractor.selectTrack(0);
        int trackIndex = extractor.getSampleTrackIndex();
        MediaFormat format = extractor.getTrackFormat(trackIndex);
        String mime = format.getString(MediaFormat.KEY_MIME);

        // use frame rate to calculate PTS offset used for PTS scaling
        double frameRate = 0.; // default - 0 is used for using zero PTS offset
        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
        } else if (!mime.equals(MediaFormat.MIMETYPE_VIDEO_VP8)
                && !mime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
            fail("need framerate info for video file");
        }

        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        if (mSamplesInMemory.size() == 0) {
            int totalMemory = 0;
            ByteBuffer tmpBuf = ByteBuffer.allocate(w * h * 3 / 2);
            int sampleSize = 0;
            int index = 0;
            long firstPTS = 0;
            double presentationOffset = 0.;
            while ((sampleSize = extractor.readSampleData(tmpBuf, 0 /* offset */)) > 0) {
                if (totalMemory + sampleSize > MAX_SIZE_SAMPLES_IN_MEMORY_BYTES) {
                    break;
                }
                if (mSamplesInMemory.size() == 0) {
                    firstPTS = extractor.getSampleTime();
                }
                ByteBuffer copied = ByteBuffer.allocate(sampleSize);
                copied.put(tmpBuf);
                if (frameRate > 0.) {
                    // presentation offset is an offset from the frame index
                    presentationOffset =
                        (extractor.getSampleTime() - firstPTS) * frameRate / 1e6 - index;
                }
                mSamplesInMemory.addLast(Pair.create(copied, presentationOffset));
                totalMemory += sampleSize;
                ++index;
                extractor.advance();
            }
            Log.d(TAG, mSamplesInMemory.size() + " samples in memory for " +
                    (totalMemory / 1024) + " KB.");
            // bitrate normalized to 30fps
            mBitrate = (int)Math.round(totalMemory * 30. * 8. / mSamplesInMemory.size());
        }
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);

        int sampleIndex = 0;

        extractor.release();

        MediaCodec codec = MediaCodec.createByCodecName(name);
        VideoCapabilities cap = codec.getCodecInfo().getCapabilitiesForType(mime).getVideoCapabilities();
        frameRate = cap.getSupportedFrameRatesFor(w, h).getUpper();
        codec.configure(format, surface, null /* crypto */, 0 /* flags */);
        codec.start();
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();
        mDecInputFormat = codec.getInputFormat();

        // start decode loop
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        final long kTimeOutUs = 1000; // 1ms timeout
        double[] frameTimeUsDiff = new double[TOTAL_FRAMES - 1];
        long lastOutputTimeUs = 0;
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int inputNum = 0;
        int outputNum = 0;
        long start = System.currentTimeMillis();
        while (!sawOutputEOS) {
            // handle input
            if (!sawInputEOS) {
                int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                    // sample contains the buffer and the PTS offset normalized to frame index
                    Pair<ByteBuffer, Double> sample =
                            mSamplesInMemory.get(sampleIndex++ % mSamplesInMemory.size());
                    sample.first.rewind();
                    int sampleSize = sample.first.remaining();
                    dstBuf.put(sample.first);
                    // use max supported framerate to compute pts
                    long presentationTimeUs = (long)((inputNum + sample.second) * 1e6 / frameRate);

                    long elapsed = System.currentTimeMillis() - start;
                    sawInputEOS = ((++inputNum == TOTAL_FRAMES)
                                   || (elapsed > maxTimeMs)
                                   || (elapsed > MIN_TEST_MS && outputNum > MIN_FRAMES));
                    if (sawInputEOS) {
                        Log.d(TAG, "saw input EOS (stop at sample).");
                    }
                    codec.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                } else {
                    assertEquals(
                            "codec.dequeueInputBuffer() unrecognized return value: " + inputBufIndex,
                            MediaCodec.INFO_TRY_AGAIN_LATER, inputBufIndex);
                }
            }

            // handle output
            int outputBufIndex = codec.dequeueOutputBuffer(info, kTimeOutUs);

            if (outputBufIndex >= 0) {
                if (info.size > 0) { // Disregard 0-sized buffers at the end.
                    long nowUs = (System.nanoTime() + 500) / 1000;
                    if (outputNum > 1) {
                        frameTimeUsDiff[outputNum - 1] = nowUs - lastOutputTimeUs;
                    }
                    lastOutputTimeUs = nowUs;
                    outputNum++;
                }
                codec.releaseOutputBuffer(outputBufIndex, false /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mDecOutputFormat = codec.getOutputFormat();
                int width = mDecOutputFormat.getInteger(MediaFormat.KEY_WIDTH);
                int height = mDecOutputFormat.getInteger(MediaFormat.KEY_HEIGHT);
                Log.d(TAG, "output resolution " + width + "x" + height);
            } else {
                assertEquals(
                        "codec.dequeueOutputBuffer() unrecognized return index: "
                                + outputBufIndex,
                        MediaCodec.INFO_TRY_AGAIN_LATER, outputBufIndex);
            }
        }
        long finish = System.currentTimeMillis();
        int validDataNum = outputNum - 1;
        frameTimeUsDiff = Arrays.copyOf(frameTimeUsDiff, validDataNum);
        codec.stop();
        codec.release();

        Log.d(TAG, "input num " + inputNum + " vs output num " + outputNum);

        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, "video_decoder_performance");
        String message = MediaPerfUtils.addPerformanceHeadersToLog(
                log, "decoder stats: decodeTo=" + ((surface == null) ? "buffer" : "surface"),
                round, name, format, mDecInputFormat, mDecOutputFormat);
        log.addValue("video_res", video, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("decode_to", surface == null ? "buffer" : "surface",
                ResultType.NEUTRAL, ResultUnit.NONE);

        double fps = outputNum / ((finish - start) / 1000.0);
        log.addValue("average_fps", fps, ResultType.HIGHER_BETTER, ResultUnit.FPS);

        MediaUtils.Stats stats = new MediaUtils.Stats(frameTimeUsDiff);
        fps = MediaPerfUtils.addPerformanceStatsToLog(log, stats, message);
        log.submit(InstrumentationRegistry.getInstrumentation());
        return fps;
    }

    private MediaFormat[] getVideoTrackFormats(String... resources) throws Exception {
        MediaFormat[] formats = new MediaFormat[resources.length];
        for (int i = 0; i < resources.length; ++i) {
            Preconditions.assertTestFileExists(mInpPrefix + resources[i]);
            formats[i] = MediaUtils.getTrackFormatForResource(mInpPrefix + resources[i], "video/");
        }
        return formats;
    }

    private void perf(final String decoderName, final String[] resources) throws Exception {
        MediaFormat[] formats = getVideoTrackFormats(resources);
        // Decode/measure the first supported video resource
        for (int i = 0; i < resources.length; ++i) {
            if (MediaUtils.supports(decoderName, formats[i])) {
                decode(decoderName, resources[i], formats[i]);
                break;
            }
        }
    }

    // AVC tests

    private static final String[] sAvcMedia0320x0240 = {
        "bbb_s1_320x240_mp4_h264_mp2_800kbps_30fps_aac_lc_5ch_240kbps_44100hz.mp4",
    };

    private static final String[] sAvcMedia0720x0480 = {
        "bbb_s1_720x480_mp4_h264_mp3_2mbps_30fps_aac_lc_5ch_320kbps_48000hz.mp4",
    };

    // prefer highest effective bitrate, then high profile
    private static final String[] sAvcMedia1280x0720 = {
        "bbb_s4_1280x720_mp4_h264_mp31_8mbps_30fps_aac_he_mono_40kbps_44100hz.mp4",
        "bbb_s3_1280x720_mp4_h264_hp32_8mbps_60fps_aac_he_v2_stereo_48kbps_48000hz.mp4",
        "bbb_s3_1280x720_mp4_h264_mp32_8mbps_60fps_aac_he_v2_6ch_144kbps_44100hz.mp4",
    };

    // prefer highest effective bitrate, then high profile
    private static final String[] sAvcMedia1920x1080 = {
        "bbb_s4_1920x1080_wide_mp4_h264_hp4_20mbps_30fps_aac_lc_6ch_384kbps_44100hz.mp4",
        "bbb_s4_1920x1080_wide_mp4_h264_mp4_20mbps_30fps_aac_he_5ch_200kbps_44100hz.mp4",
        "bbb_s2_1920x1080_mp4_h264_hp42_20mbps_60fps_aac_lc_6ch_384kbps_48000hz.mp4",
        "bbb_s2_1920x1080_mp4_h264_mp42_20mbps_60fps_aac_he_v2_5ch_160kbps_48000hz.mp4",
    };

    // H263 tests

    private static final String[] sH263Media0176x0144 = {
        "video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz.3gp",
    };

    private static final String[] sH263Media0352x0288 = {
        "video_352x288_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz.3gp",
    };

    // No media for H263 704x576

    // No media for H263 1408x1152

    // HEVC tests

    private static final String[] sHevcMedia0352x0288 = {
        "bbb_s1_352x288_mp4_hevc_mp2_600kbps_30fps_aac_he_stereo_96kbps_48000hz.mp4",
    };

    private static final String[] sHevcMedia0640x0360 = {
        "bbb_s1_640x360_mp4_hevc_mp21_1600kbps_30fps_aac_he_6ch_288kbps_44100hz.mp4",
    };

    private static final String[] sHevcMedia0720x0480 = {
        "bbb_s1_720x480_mp4_hevc_mp3_1600kbps_30fps_aac_he_6ch_240kbps_48000hz.mp4",
    };

    private static final String[] sHevcMedia1280x0720 = {
        "bbb_s4_1280x720_mp4_hevc_mp31_4mbps_30fps_aac_he_stereo_80kbps_32000hz.mp4",
    };

    private static final String[] sHevcMedia1920x1080 = {
        "bbb_s2_1920x1080_mp4_hevc_mp41_10mbps_60fps_aac_lc_6ch_384kbps_22050hz.mp4",
    };

    // prefer highest effective bitrate
    private static final String[] sHevcMedia3840x2160 = {
        "bbb_s4_3840x2160_mp4_hevc_mp5_20mbps_30fps_aac_lc_6ch_384kbps_24000hz.mp4",
        "bbb_s2_3840x2160_mp4_hevc_mp51_20mbps_60fps_aac_lc_6ch_384kbps_32000hz.mp4",
    };

    // MPEG2 tests

    // No media for MPEG2 176x144

    // No media for MPEG2 352x288

    // No media for MPEG2 640x480

    // No media for MPEG2 1280x720

    // No media for MPEG2 1920x1080

    // MPEG4 tests

    private static final String[] sMpeg4Media0176x0144 = {
        "video_176x144_mp4_mpeg4_300kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
    };

    private static final String[] sMpeg4Media0480x0360 = {
        "video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
    };

    // No media for MPEG4 640x480

    private static final String[] sMpeg4Media1280x0720 = {
        "video_1280x720_mp4_mpeg4_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
    };

    // VP8 tests

    private static final String[] sVp8Media0320x0180 = {
        "bbb_s1_320x180_webm_vp8_800kbps_30fps_opus_5ch_320kbps_48000hz.webm",
    };

    private static final String[] sVp8Media0640x0360 = {
        "bbb_s1_640x360_webm_vp8_2mbps_30fps_vorbis_5ch_320kbps_48000hz.webm",
    };

    // prefer highest effective bitrate
    private static final String[] sVp8Media1280x0720 = {
        "bbb_s4_1280x720_webm_vp8_8mbps_30fps_opus_mono_64kbps_48000hz.webm",
        "bbb_s3_1280x720_webm_vp8_8mbps_60fps_opus_6ch_384kbps_48000hz.webm",
    };

    // prefer highest effective bitrate
    private static final String[] sVp8Media1920x1080 = {
        "bbb_s4_1920x1080_wide_webm_vp8_20mbps_30fps_vorbis_6ch_384kbps_44100hz.webm",
        "bbb_s2_1920x1080_webm_vp8_20mbps_60fps_vorbis_6ch_384kbps_48000hz.webm",
    };

    // VP9 tests

    private static final String[] sVp9Media0320x0180 = {
        "bbb_s1_320x180_webm_vp9_0p11_600kbps_30fps_vorbis_mono_64kbps_48000hz.webm",
    };

    private static final String[] sVp9Media0640x0360 = {
        "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
    };

    private static final String[] sVp9Media1280x0720 = {
        "bbb_s4_1280x720_webm_vp9_0p31_4mbps_30fps_opus_stereo_128kbps_48000hz.webm",
    };

    private static final String[] sVp9Media1920x1080 = {
        "bbb_s2_1920x1080_webm_vp9_0p41_10mbps_60fps_vorbis_6ch_384kbps_22050hz.webm",
    };

    // prefer highest effective bitrate
    private static final String[] sVp9Media3840x2160 = {
        "bbb_s4_3840x2160_webm_vp9_0p5_20mbps_30fps_vorbis_6ch_384kbps_24000hz.webm",
        "bbb_s2_3840x2160_webm_vp9_0p51_20mbps_60fps_vorbis_6ch_384kbps_32000hz.webm",
    };

    // AV1 tests

    private static final String[] sAv1Media0352x0288 = {
        "bbb_s1_352x288_mp4_av1_355kbps_30fps_aac_lc_stereo_128kbps_48000hz.mp4",
    };

    private static final String[] sAv1Media0640x0360 = {
        "bbb_s1_640x360_mp4_av1_994kbps_30fps_aac_lc_6ch_342kbps_48000hz.mp4",
    };

    private static final String[] sAv1Media0720x0480 = {
        "bbb_s1_720x480_mp4_av1_977kbps_30fps_aac_lc_6ch_341kbps_48000hz.mp4",
    };

    private static final String[] sAv1Media1280x0720 = {
        "bbb_s4_1280x720_mp4_av1_2387kbps_30fps_aac_lc_stereo_130kbps_32000hz.mp4",
    };

    private static final String[] sAv1Media1920x1080 = {
        "bbb_s2_1920x1080_mp4_av1_5010kbps_60fps_aac_lc_6ch_348kbps_22050hz.mp4",
    };

    @Test
    public void testPerf() throws Exception {
        perf(mDecoderName, mResources);
    }
}

