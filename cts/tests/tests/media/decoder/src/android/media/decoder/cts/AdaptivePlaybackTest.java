/*
 * Copyright (C) 2014 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.media.cts.OutputSurface;
import android.media.cts.TestArgs;
import android.opengl.GLES20;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.Surface;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.CRC32;

import javax.microedition.khronos.opengles.GL10;

@MediaHeavyPresubmitTest
@AppModeFull
@RunWith(Parameterized.class)
public class AdaptivePlaybackTest extends MediaTestBase {

    private static final boolean sIsAtLeastS = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    private static final String TAG = "AdaptivePlaybackTest";
    private boolean verify = false;
    private static final int MIN_FRAMES_BEFORE_DRC = 2;
    private CRC32 mCRC;

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        mCRC = new CRC32();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    @Parameterized.Parameter(0)
    public String mCodecName;

    @Parameterized.Parameter(1)
    public CodecList mCodecs;

    public static Iterable<Codec> H264(CodecFactory factory) {
        return factory.createCodecList(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                "video_1280x720_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                "bbb_s1_720x480_mp4_h264_mp3_2mbps_30fps_aac_lc_5ch_320kbps_48000hz.mp4");
    }

    public static Iterable<Codec> HEVC(CodecFactory factory) {
        return factory.createCodecList(
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                "bbb_s1_720x480_mp4_hevc_mp3_1600kbps_30fps_aac_he_6ch_240kbps_48000hz.mp4",
                "bbb_s4_1280x720_mp4_hevc_mp31_4mbps_30fps_aac_he_stereo_80kbps_32000hz.mp4",
                "bbb_s1_352x288_mp4_hevc_mp2_600kbps_30fps_aac_he_stereo_96kbps_48000hz.mp4");
    }

    public static Iterable<Codec> Mpeg2(CodecFactory factory) {
        return factory.createCodecList(
                MediaFormat.MIMETYPE_VIDEO_MPEG2,
                "video_640x360_mp4_mpeg2_2000kbps_30fps_aac_stereo_128kbps_48000hz.mp4",
                "video_1280x720_mp4_mpeg2_3000kbps_30fps_aac_stereo_128kbps_48000hz.mp4");
    }

    public static Iterable<Codec> H263(CodecFactory factory) {
        return factory.createCodecList(
                MediaFormat.MIMETYPE_VIDEO_H263,
                "video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz.3gp",
                "video_352x288_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz.3gp");
    }

    public static Iterable<Codec> Mpeg4(CodecFactory factory) {
        return factory.createCodecList(
                MediaFormat.MIMETYPE_VIDEO_MPEG4,
                "video_1280x720_mp4_mpeg4_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                "video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                "video_176x144_mp4_mpeg4_300kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    public static Iterable<Codec> VP8(CodecFactory factory) {
        return factory.createCodecList(
                MediaFormat.MIMETYPE_VIDEO_VP8,
                "video_480x360_webm_vp8_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm",
                "bbb_s3_1280x720_webm_vp8_8mbps_60fps_opus_6ch_384kbps_48000hz.webm",
                "bbb_s1_320x180_webm_vp8_800kbps_30fps_opus_5ch_320kbps_48000hz.webm");
    }

    public static Iterable<Codec> VP9(CodecFactory factory) {
        return factory.createCodecList(
                MediaFormat.MIMETYPE_VIDEO_VP9,
                "video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm",
                "bbb_s4_1280x720_webm_vp9_0p31_4mbps_30fps_opus_stereo_128kbps_48000hz.webm",
                "bbb_s1_320x180_webm_vp9_0p11_600kbps_30fps_vorbis_mono_64kbps_48000hz.webm");
    }

    public static Iterable<Codec> AV1(CodecFactory factory) {
        return factory.createCodecList(
                MediaFormat.MIMETYPE_VIDEO_AV1,
                "video_480x360_webm_av1_400kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                "video_1280x720_webm_av1_2000kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                "video_320x180_webm_av1_200kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    static CodecFactory ALL = new CodecFactory();
    static CodecFactory SW  = new SWCodecFactory();
    static CodecFactory HW  = new HWCodecFactory();

    public static Iterable<Codec> H264()  { return H264(ALL);  }
    public static Iterable<Codec> HEVC()  { return HEVC(ALL);  }
    public static Iterable<Codec> VP8()   { return VP8(ALL);   }
    public static Iterable<Codec> VP9()   { return VP9(ALL);   }
    public static Iterable<Codec> AV1()   { return AV1(ALL);   }
    public static Iterable<Codec> Mpeg2() { return Mpeg2(ALL); }
    public static Iterable<Codec> Mpeg4() { return Mpeg4(ALL); }
    public static Iterable<Codec> H263()  { return H263(ALL);  }

    public Iterable<Codec> AllCodecs() {
        return chain(H264(ALL), HEVC(ALL), VP8(ALL), VP9(ALL), AV1(ALL), Mpeg2(ALL), Mpeg4(ALL), H263(ALL));
    }

    public Iterable<Codec> SWCodecs() {
        return chain(H264(SW), HEVC(SW), VP8(SW), VP9(SW), AV1(SW), Mpeg2(SW), Mpeg4(SW), H263(SW));
    }

    public Iterable<Codec> HWCodecs() {
        return chain(H264(HW), HEVC(HW), VP8(HW), VP9(HW), AV1(HW), Mpeg2(HW), Mpeg4(HW), H263(HW));
    }

    /* tests for adaptive codecs */
    MediaTest adaptiveEarlyEos     = new EarlyEosTest().adaptive();
    MediaTest adaptiveEosFlushSeek = new EosFlushSeekTest().adaptive();
    MediaTest adaptiveSkipAhead    = new AdaptiveSkipTest(true /* forward */);
    MediaTest adaptiveSkipBack     = new AdaptiveSkipTest(false /* forward */);

    /* DRC tests for adaptive codecs */
    MediaTest adaptiveReconfigDrc      = new ReconfigDrcTest().adaptive();
    MediaTest adaptiveSmallReconfigDrc = new ReconfigDrcTest().adaptiveSmall();
    MediaTest adaptiveDrc      = new AdaptiveDrcTest(); /* adaptive */
    MediaTest adaptiveSmallDrc = new AdaptiveDrcTest().adaptiveSmall();

    /* tests for regular codecs */
    MediaTest earlyEos          = new EarlyEosTest();
    MediaTest eosFlushSeek      = new EosFlushSeekTest();
    MediaTest flushConfigureDrc = new ReconfigDrcTest();

    MediaTest[] allTests = {
        adaptiveEarlyEos,
        adaptiveEosFlushSeek,
        adaptiveSkipAhead,
        adaptiveSkipBack,
        adaptiveReconfigDrc,
        adaptiveSmallReconfigDrc,
        adaptiveDrc,
        adaptiveSmallDrc,
        earlyEos,
        eosFlushSeek,
        flushConfigureDrc,
    };

    /* helpers to run sets of tests */
    public void runEOS() { ex(AllCodecs(), new MediaTest[] {
        adaptiveEarlyEos,
        adaptiveEosFlushSeek,
        adaptiveReconfigDrc,
        adaptiveSmallReconfigDrc,
        earlyEos,
        eosFlushSeek,
        flushConfigureDrc,
    }); }

    public void runAll() { ex(AllCodecs(), allTests); }
    public void runSW()  { ex(SWCodecs(),  allTests); }
    public void runHW()  { ex(HWCodecs(),  allTests); }

    public void verifyAll() { verify = true; try { runAll(); } finally { verify = false; } }
    public void verifySW()  { verify = true; try { runSW();  } finally { verify = false; } }
    public void verifyHW()  { verify = true; try { runHW();  } finally { verify = false; } }

    public void runH264()  { ex(H264(),  allTests); }
    public void runHEVC()  { ex(HEVC(),  allTests); }
    public void runVP8()   { ex(VP8(),   allTests); }
    public void runVP9()   { ex(VP9(),   allTests); }
    public void runAV1()   { ex(AV1(),   allTests); }
    public void runMpeg2() { ex(Mpeg2(), allTests); }
    public void runMpeg4() { ex(Mpeg4(), allTests); }
    public void runH263()  { ex(H263(),  allTests); }

    public void onlyH264HW()  { ex(H264(HW),  allTests); }
    public void onlyHEVCHW()  { ex(HEVC(HW),  allTests); }
    public void onlyVP8HW()   { ex(VP8(HW),   allTests); }
    public void onlyVP9HW()   { ex(VP9(HW),   allTests); }
    public void onlyAV1HW()   { ex(AV1(HW),   allTests); }
    public void onlyMpeg2HW() { ex(Mpeg2(HW), allTests); }
    public void onlyMpeg4HW() { ex(Mpeg4(HW), allTests); }
    public void onlyH263HW()  { ex(H263(HW),  allTests); }

    public void onlyH264SW()  { ex(H264(SW),  allTests); }
    public void onlyHEVCSW()  { ex(HEVC(SW),  allTests); }
    public void onlyVP8SW()   { ex(VP8(SW),   allTests); }
    public void onlyVP9SW()   { ex(VP9(SW),   allTests); }
    public void onlyAV1SW()   { ex(AV1(SW),   allTests); }
    public void onlyMpeg2SW() { ex(Mpeg2(SW), allTests); }
    public void onlyMpeg4SW() { ex(Mpeg4(SW), allTests); }
    public void onlyH263SW()  { ex(H263(SW),  allTests); }

    public void bytebuffer() { ex(H264(SW), new EarlyEosTest().byteBuffer()); }
    public void onlyTexture() { ex(H264(HW), new EarlyEosTest().texture()); }

    static private List<Object[]> prepareParamList(List<Object> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        for (Object arg : exhaustiveArgsList) {
            if (arg instanceof CodecList) {
                CodecList codecList = (CodecList)arg;
                for (Codec codec : codecList) {
                    if (TestArgs.shouldSkipCodec(codec.name)) {
                        continue;
                    }
                    Object[] testArgs = new Object[2];
                    testArgs[0] = codec.name;
                    CodecList subList = new CodecList();
                    subList.add(codec);
                    testArgs[1] = subList;
                    argsList.add(testArgs);
                }
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}")
    public static Collection<Object[]> input() {
        final List<Object> exhaustiveArgsList = Arrays.asList(new Object[]{
                H264(), HEVC(), VP8(), VP9(), AV1(), Mpeg2(), Mpeg4(), H263()
        });
        return prepareParamList(exhaustiveArgsList);
    }

    /* individual tests */
    @Test
    public void test_adaptiveEarlyEos() {
        ex(mCodecs, adaptiveEarlyEos);
    }

    @Test
    public void test_adaptiveEosFlushSeek() {
        ex(mCodecs, adaptiveEosFlushSeek);
    }

    @Test
    public void test_adaptiveSkipAhead() {
        ex(mCodecs, adaptiveSkipAhead);
    }

    @Test
    public void test_adaptiveSkipBack() {
        ex(mCodecs, adaptiveSkipBack);
    }

    @Test
    public void test_adaptiveReconfigDrc() {
        ex(mCodecs, adaptiveReconfigDrc);
    }

    @Test
    public void test_adaptiveSmallReconfigDrc() {
        ex(mCodecs, adaptiveSmallReconfigDrc);
    }

    @Test
    public void test_adaptiveDrc() {
        ex(mCodecs, adaptiveDrc);
    }

    @Test
    public void test_AdaptiveDrcEarlyEosTest() {
        String mime = mCodecs.get(0).mediaList[0].getMime();
        assumeFalse(mime.equals(MediaFormat.MIMETYPE_VIDEO_H263) ||
                mime.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4));
        ex(mCodecs, new AdaptiveDrcEarlyEosTest());
    }

    @Test
    public void test_adaptiveSmallDrc() {
        String mime = mCodecs.get(0).mediaList[0].getMime();
        assumeFalse(mime.equals(MediaFormat.MIMETYPE_VIDEO_H263) ||
                mime.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4));
        ex(mCodecs, adaptiveSmallDrc);
    }

    @Test
    public void test_earlyEos() {
        ex(mCodecs, earlyEos);
    }

    @Test
    public void test_eosFlushSeek() {
        ex(mCodecs, eosFlushSeek);
    }

    @Test
    public void test_flushConfigureDrc() {
        ex(mCodecs, flushConfigureDrc);
    }

    /* only use unchecked exceptions to allow brief test methods */
    private void ex(Iterable<Codec> codecList, MediaTest test) {
        ex(codecList, new MediaTest[] { test } );
    }

    private void ex(Iterable<Codec> codecList, MediaTest[] testList) {
        if (codecList == null) {
            Log.i(TAG, "CodecList was empty. Skipping test.");
            return;
        }

        TestList tests = new TestList();
        for (Codec c : codecList) {
            for (MediaTest test : testList) {
                if (test.isValid(c)) {
                    test.addTests(tests, c);
                }
            }
        }
        try {
            tests.run();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /* need an inner class to have access to the activity */
    abstract class ActivityTest extends MediaTest {
        TestSurface mNullSurface = new ActivitySurface(null);
        protected TestSurface getSurface() {
            if (mUseSurface) {
                return new ActivitySurface(getActivity().getSurfaceHolder().getSurface());
            } else if (mUseSurfaceTexture) {
                return new DecoderSurface(1280, 720, mCRC);
            }
            return mNullSurface;
        }
    }

    static final int NUM_FRAMES = 50;

    /**
     * Queue some frames with an EOS on the last one.  Test that we have decoded as many
     * frames as we queued.  This tests the EOS handling of the codec to see if all queued
     * (and out-of-order) frames are actually decoded and returned.
     *
     * Also test flushing prior to sending CSD, and immediately after sending CSD.
     */
    class EarlyEosTest extends ActivityTest {
        // using bitfields to create a directed state graph that terminates at FLUSH_NEVER
        static final int FLUSH_BEFORE_CSD = (1 << 1);
        static final int FLUSH_AFTER_CSD = (1 << 0);
        static final int FLUSH_NEVER = 0;

        public boolean isValid(Codec c) {
            return getFormat(c) != null;
        }
        public void addTests(TestList tests, final Codec c) {
            int state = FLUSH_BEFORE_CSD;
            for (int i = NUM_FRAMES / 2; i > 0; --i, state >>= 1) {
                final int queuedFrames = i;
                final int earlyFlushMode = state;
                tests.add(
                    new Step("testing early EOS at " + queuedFrames, this, c) {
                        public void run() {
                            Decoder decoder = new Decoder(c.name);
                            try {
                                MediaFormat fmt = stepFormat();
                                MediaFormat configFmt = fmt;
                                if (earlyFlushMode == FLUSH_BEFORE_CSD) {
                                    // flush before CSD requires not submitting CSD with configure
                                    configFmt = Media.removeCSD(fmt);
                                }
                                decoder.configureAndStart(configFmt, stepSurface());
                                if (earlyFlushMode != FLUSH_NEVER) {
                                    decoder.flush();
                                    // We must always queue CSD after a flush that is potentially
                                    // before we receive output format has changed.  This should
                                    // work even after we receive the format change.
                                    decoder.queueCSD(fmt);
                                }
                                int decodedFrames = -decoder.queueInputBufferRange(
                                        stepMedia(),
                                        0 /* startFrame */,
                                        queuedFrames,
                                        true /* sendEos */,
                                        true /* waitForEos */);
                                if (decodedFrames <= 0) {
                                    Log.w(TAG, "Did not receive EOS -- negating frame count");
                                }
                                decoder.stop();
                                if (decodedFrames != queuedFrames) {
                                    warn("decoded " + decodedFrames + " frames out of " +
                                            queuedFrames + " queued");
                                }
                            } finally {
                                warn(decoder.getWarnings());
                                decoder.releaseQuietly();
                            }
                        }
                    });
                if (verify) {
                    i >>= 1;
                }
            }
        }
    }

    /**
     * Similar to EarlyEosTest, but we keep the component alive and running in between the steps.
     * This is how seeking should be done if all frames must be outputted.  This also tests that
     * PTS can be repeated after flush.
     */
    class EosFlushSeekTest extends ActivityTest {
        Decoder mDecoder; // test state
        public boolean isValid(Codec c) {
            return getFormat(c) != null;
        }
        public void addTests(TestList tests, final Codec c) {
            tests.add(
                new Step("testing EOS & flush before seek - init", this, c) {
                    public void run() {
                        mDecoder = new Decoder(c.name);
                        mDecoder.configureAndStart(stepFormat(), stepSurface());
                    }});

            for (int i = NUM_FRAMES; i > 0; i--) {
                final int queuedFrames = i;
                tests.add(
                    new Step("testing EOS & flush before seeking after " + queuedFrames +
                            " frames", this, c) {
                        public void run() {
                            int decodedFrames = -mDecoder.queueInputBufferRange(
                                    stepMedia(),
                                    0 /* startFrame */,
                                    queuedFrames,
                                    true /* sendEos */,
                                    true /* waitForEos */);
                            if (decodedFrames != queuedFrames) {
                                warn("decoded " + decodedFrames + " frames out of " +
                                        queuedFrames + " queued");
                            }
                            warn(mDecoder.getWarnings());
                            mDecoder.clearWarnings();
                            mDecoder.flush();
                            // First run will trigger output format change exactly once,
                            // and subsequent runs should not trigger format change.
                            // this part of test is new for Android12
                            if (sIsAtLeastS) {
                                assertEquals(1, mDecoder.getOutputFormatChangeCount());
                            }
                        }
                    });
                if (verify) {
                    i >>= 1;
                }
            }

            tests.add(
                new Step("testing EOS & flush before seek - finally", this, c) {
                    public void run() {
                        try {
                            mDecoder.stop();
                        } finally {
                            mDecoder.release();
                        }
                    }});
        }
    }

    /**
     * Similar to EosFlushSeekTest, but we change the media size between the steps.
     * This is how dynamic resolution switching can be done on codecs that do not support
     * adaptive playback.
     */
    class ReconfigDrcTest extends ActivityTest {
        Decoder mDecoder;  // test state
        public boolean isValid(Codec c) {
            return getFormat(c) != null && c.mediaList.length > 1;
        }
        public void addTests(TestList tests, final Codec c) {
            tests.add(
                new Step("testing DRC with reconfigure - init", this, c) {
                    public void run() {
                        mDecoder = new Decoder(c.name);
                    }});

            for (int i = NUM_FRAMES, ix = 0; i > 0; i--, ix++) {
                final int queuedFrames = i;
                final int mediaIx = ix % c.mediaList.length;
                tests.add(
                    new Step("testing DRC with reconfigure after " + queuedFrames + " frames",
                            this, c, mediaIx) {
                        public void run() {
                            try {
                                mDecoder.configureAndStart(stepFormat(), stepSurface());
                                int decodedFrames = -mDecoder.queueInputBufferRange(
                                        stepMedia(),
                                        0 /* startFrame */,
                                        queuedFrames,
                                        true /* sendEos */,
                                        true /* waitForEos */);
                                if (decodedFrames != queuedFrames) {
                                    warn("decoded " + decodedFrames + " frames out of " +
                                            queuedFrames + " queued");
                                }
                                warn(mDecoder.getWarnings());
                                mDecoder.clearWarnings();
                                mDecoder.flush();
                            } finally {
                                mDecoder.stop();
                            }
                        }
                    });
                if (verify) {
                    i >>= 1;
                }
            }
            tests.add(
                new Step("testing DRC with reconfigure - finally", this, c) {
                    public void run() {
                        mDecoder.release();
                    }});
        }
    }

    /* ADAPTIVE-ONLY TESTS - only run on codecs that support adaptive playback */

    /**
     * Test dynamic resolution change support.  Queue various sized media segments
     * with different resolutions, verify that all queued frames were decoded.  Here
     * PTS will grow between segments.
     */
    class AdaptiveDrcTest extends ActivityTest {
        Decoder mDecoder;
        int mAdjustTimeUs;
        int mDecodedFrames;
        int mQueuedFrames;

        public AdaptiveDrcTest() {
            super();
            adaptive();
        }
        public boolean isValid(Codec c) {
            checkAdaptiveFormat();
            return c.adaptive && c.mediaList.length > 1;
        }
        public void addTests(TestList tests, final Codec c) {
            tests.add(
                new Step("testing DRC with no reconfigure - init", this, c) {
                    public void run() throws Throwable {
                        // FIXME wait 2 seconds to allow system to free up previous codecs
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {}
                        mDecoder = new Decoder(c.name);
                        mDecoder.configureAndStart(stepFormat(), stepSurface());
                        mAdjustTimeUs = 0;
                        mDecodedFrames = 0;
                        mQueuedFrames = 0;
                    }});

            for (int i = NUM_FRAMES, ix = 0; i >= MIN_FRAMES_BEFORE_DRC; i--, ix++) {
                final int mediaIx = ix % c.mediaList.length;
                final int segmentSize = i;
                tests.add(
                    new Step("testing DRC with no reconfigure after " + i + " frames",
                            this, c, mediaIx) {
                        public void run() throws Throwable {
                            mQueuedFrames += segmentSize;
                            boolean lastSequence = segmentSize == MIN_FRAMES_BEFORE_DRC;
                            if (verify) {
                                lastSequence = (segmentSize >> 1) <= MIN_FRAMES_BEFORE_DRC;
                            }
                            int frames = mDecoder.queueInputBufferRange(
                                    stepMedia(),
                                    0 /* startFrame */,
                                    segmentSize,
                                    lastSequence /* sendEos */,
                                    lastSequence /* expectEos */,
                                    mAdjustTimeUs,
                                    // Try sleeping after first queue so that we can verify
                                    // output format change event happens at the right time.
                                    true /* sleepAfterFirstQueue */);
                            if (lastSequence && frames >= 0) {
                                warn("did not receive EOS, received " + frames + " frames");
                            } else if (!lastSequence && frames < 0) {
                                warn("received EOS, received " + (-frames) + " frames");
                            }
                            warn(mDecoder.getWarnings());
                            mDecoder.clearWarnings();

                            mDecodedFrames += Math.abs(frames);
                            mAdjustTimeUs += 1 + stepMedia().getTimestampRangeValue(
                                    0, segmentSize, Media.RANGE_END);
                        }});
                if (verify) {
                    i >>= 1;
                }
            }
            tests.add(
                new Step("testing DRC with no reconfigure - init", this, c) {
                    public void run() throws Throwable {
                        if (mDecodedFrames != mQueuedFrames) {
                            warn("decoded " + mDecodedFrames + " frames out of " +
                                    mQueuedFrames + " queued");
                        }
                        try {
                            mDecoder.stop();
                        } finally {
                            mDecoder.release();
                        }
                    }
                });
        }
    };

    /**
     * Queue EOS shortly after a dynamic resolution change.  Test that all frames were
     * decoded.
     */
    class AdaptiveDrcEarlyEosTest extends ActivityTest {
        public AdaptiveDrcEarlyEosTest() {
            super();
            adaptive();
        }
        public boolean isValid(Codec c) {
            checkAdaptiveFormat();
            return c.adaptive && c.mediaList.length > 1;
        }
        public Step testStep(final Codec c, final int framesBeforeDrc,
                final int framesBeforeEos) {
            return new Step("testing DRC with no reconfigure after " + framesBeforeDrc +
                    " frames and subsequent EOS after " + framesBeforeEos + " frames",
                    this, c) {
                public void run() throws Throwable {
                    Decoder decoder = new Decoder(c.name);
                    int queuedFrames = framesBeforeDrc + framesBeforeEos;
                    int framesA = 0;
                    int framesB = 0;
                    try {
                        decoder.configureAndStart(stepFormat(), stepSurface());
                        Media media = c.mediaList[0];

                        framesA = decoder.queueInputBufferRange(
                                media,
                                0 /* startFrame */,
                                framesBeforeDrc,
                                false /* sendEos */,
                                false /* expectEos */);
                        if (framesA < 0) {
                            warn("received unexpected EOS, received " + (-framesA) + " frames");
                        }
                        long adjustTimeUs = 1 + media.getTimestampRangeValue(
                                0, framesBeforeDrc, Media.RANGE_END);

                        media = c.mediaList[1];
                        framesB = decoder.queueInputBufferRange(
                                media,
                                0 /* startFrame */,
                                framesBeforeEos,
                                true /* sendEos */,
                                true /* expectEos */,
                                adjustTimeUs,
                                false /* sleepAfterFirstQueue */);
                        if (framesB >= 0) {
                            warn("did not receive EOS, received " + (-framesB) + " frames");
                        }
                        decoder.stop();
                        warn(decoder.getWarnings());
                    } finally {
                        int decodedFrames = Math.abs(framesA) + Math.abs(framesB);
                        if (decodedFrames != queuedFrames) {
                            warn("decoded " + decodedFrames + " frames out of " + queuedFrames +
                                    " queued");
                        }
                        decoder.release();
                    }
                }
            };
        }
        public void addTests(TestList tests, Codec c) {
            for (int drcFrame = 6; drcFrame >= MIN_FRAMES_BEFORE_DRC; drcFrame--) {
                for (int eosFrame = 6; eosFrame >= 1; eosFrame--) {
                    tests.add(testStep(c, drcFrame, eosFrame));
                }
            }
        }
    };

    /**
     * Similar to AdaptiveDrcTest, but tests that PTS can change at adaptive boundaries both
     * forward and backward without the need to flush.
     */
    class AdaptiveSkipTest extends ActivityTest {
        boolean forward;
        public AdaptiveSkipTest(boolean fwd) {
            forward = fwd;
            adaptive();
        }
        public boolean isValid(Codec c) {
            checkAdaptiveFormat();
            return c.adaptive;
        }
        Decoder mDecoder;
        int mAdjustTimeUs = 0;
        int mDecodedFrames = 0;
        int mQueuedFrames = 0;
        public void addTests(TestList tests, final Codec c) {
            tests.add(
                new Step("testing flushless skipping - init", this, c) {
                    public void run() throws Throwable {
                        mDecoder = new Decoder(c.name);
                        mDecoder.configureAndStart(stepFormat(), stepSurface());
                        mAdjustTimeUs = 0;
                        mDecodedFrames = 0;
                        mQueuedFrames = 0;
                    }});

            for (int i = 2, ix = 0; i <= NUM_FRAMES; i++, ix++) {
                final int mediaIx = ix % c.mediaList.length;
                final int segmentSize = i;
                final boolean lastSequence;
                if (verify) {
                    lastSequence = (segmentSize << 1) + 1 > NUM_FRAMES;
                } else {
                    lastSequence = segmentSize >= NUM_FRAMES;
                }
                tests.add(
                    new Step("testing flushless skipping " + (forward ? "forward" : "backward") +
                            " after " + i + " frames", this, c) {
                        public void run() throws Throwable {
                            int frames = mDecoder.queueInputBufferRange(
                                stepMedia(),
                                0 /* startFrame */,
                                segmentSize,
                                lastSequence /* sendEos */,
                                lastSequence /* expectEos */,
                                mAdjustTimeUs,
                                false /* sleepAfterFirstQueue */);
                            if (lastSequence && frames >= 0) {
                                warn("did not receive EOS, received " + frames + " frames");
                            } else if (!lastSequence && frames < 0) {
                                warn("received unexpected EOS, received " + (-frames) + " frames");
                            }
                            warn(mDecoder.getWarnings());
                            mDecoder.clearWarnings();

                            mQueuedFrames += segmentSize;
                            mDecodedFrames += Math.abs(frames);
                            if (forward) {
                                mAdjustTimeUs += 10000000 + stepMedia().getTimestampRangeValue(
                                        0, segmentSize, Media.RANGE_DURATION);
                            }
                        }});
                if (verify) {
                    i <<= 1;
                }
            }

            tests.add(
                new Step("testing flushless skipping - finally", this, c) {
                    public void run() throws Throwable {
                        if (mDecodedFrames != mQueuedFrames) {
                            warn("decoded " + mDecodedFrames + " frames out of " + mQueuedFrames +
                                    " queued");
                        }
                        try {
                            mDecoder.stop();
                        } finally {
                            mDecoder.release();
                        }
                    }});
        }
    };

    // not yet used
    static long checksum(ByteBuffer buf, int size, CRC32 crc) {
        assertTrue(size >= 0);
        assertTrue(size <= buf.capacity());
        crc.reset();
        if (buf.hasArray()) {
            crc.update(buf.array(), buf.arrayOffset(), size);
        } else {
           int pos = buf.position();
           buf.rewind();
           final int rdsize = Math.min(4096, size);
           byte[] bb = new byte[rdsize];
           int chk;
           for (int i = 0; i < size; i += chk) {
                chk = Math.min(rdsize, size - i);
                buf.get(bb, 0, chk);
                crc.update(bb, 0, chk);
            }
            buf.position(pos);
        }
        return crc.getValue();
    }

    /* ====================================================================== */
    /*                              UTILITY FUNCTIONS                         */
    /* ====================================================================== */
    static String byteBufferToString(ByteBuffer buf, int start, int len) {
        int oldPosition = buf.position();
        buf.position(start);
        int strlen = 2; // {}
        boolean ellipsis = len < buf.limit();
        if (ellipsis) {
            strlen += 3; // ...
        } else {
            len = buf.limit();
        }
        strlen += 3 * len - (len > 0 ? 1 : 0); // XX,XX
        char[] res = new char[strlen];
        res[0] = '{';
        res[strlen - 1] = '}';
        if (ellipsis) {
            res[strlen - 2] = res[strlen - 3] = res[strlen - 4] = '.';
        }
        for (int i = 1; i < len; i++) {
            res[i * 3] = ',';
        }
        for (int i = 0; i < len; i++) {
            byte b = buf.get();
            int d = (b >> 4) & 15;
            res[i * 3 + 1] = (char)(d + (d > 9 ? 'a' - 10 : '0'));
            d = (b & 15);
            res[i * 3 + 2] = (char)(d + (d > 9 ? 'a' - 10 : '0'));
        }
        buf.position(oldPosition);
        return new String(res);
    }

    static <E> Iterable<E> chain(Iterable<E> ... iterables) {
        /* simple chainer using ArrayList */
        ArrayList<E> items = new ArrayList<E>();
        for (Iterable<E> it: iterables) {
            for (E el: it) {
                items.add(el);
            }
        }
        return items;
    }

    class Decoder implements MediaCodec.OnFrameRenderedListener {
        private final static String TAG = "AdaptiveDecoder";
        final long kTimeOutUs = 5000;
        final long kCSDTimeOutUs = 1000000;
        // Sufficiently large number of frames to expect actual render on surface
        static final int RENDERED_FRAMES_THRESHOLD  = 32;
        MediaCodec mCodec;
        ByteBuffer[] mInputBuffers;
        ByteBuffer[] mOutputBuffers;
        TestSurface mSurface;
        boolean mDoChecksum;
        boolean mQueuedEos;
        ArrayList<Long> mTimeStamps;
        // We might add items when iterating mWarnings.
        // Use CopyOnWrieArrayList to avoid ConcurrentModificationException.
        CopyOnWriteArrayList<String> mWarnings;
        Vector<Long> mRenderedTimeStamps; // using Vector as it is implicitly synchronized
        long mLastRenderNanoTime;
        int mFramesNotifiedRendered;
        // True iff previous dequeue request returned INFO_OUTPUT_FORMAT_CHANGED.
        boolean mOutputFormatChanged;
        // Number of output format change event
        int mOutputFormatChangeCount;
        // Save the timestamps of the first frame of each sequence.
        // Note: this is the only time output format change could happen.
        ArrayList<Long> mFirstQueueTimestamps;
        Object mRenderLock = new Object();

        public Decoder(String codecName) {
            MediaCodec codec = null;
            try {
                codec = MediaCodec.createByCodecName(codecName);
            } catch (Exception e) {
                throw new RuntimeException("couldn't create codec " + codecName, e);
            }
            Log.i(TAG, "using codec: " + codec.getName());
            mCodec = codec;
            mDoChecksum = false;
            mQueuedEos = false;
            mTimeStamps = new ArrayList<Long>();
            mWarnings = new CopyOnWriteArrayList<String>();
            mRenderedTimeStamps = new Vector<Long>();
            mLastRenderNanoTime = System.nanoTime();
            mFramesNotifiedRendered = 0;
            mOutputFormatChanged = false;
            mOutputFormatChangeCount = 0;
            mFirstQueueTimestamps = new ArrayList<Long>();

            codec.setOnFrameRenderedListener(this, null);
        }

        public void onFrameRendered(MediaCodec codec, long presentationTimeUs, long nanoTime) {
            final long NSECS_IN_1SEC = 1000000000;
            if (!mRenderedTimeStamps.remove(presentationTimeUs)) {
                warn("invalid (rendered) timestamp " + presentationTimeUs + ", rendered " +
                        mRenderedTimeStamps);
            }
            assert nanoTime > mLastRenderNanoTime;
            mLastRenderNanoTime = nanoTime;
            synchronized (mRenderLock) {
                ++mFramesNotifiedRendered;
                mRenderLock.notifyAll();
            }
            assert nanoTime > System.nanoTime() - NSECS_IN_1SEC;
        }

        public String getName() {
            return mCodec.getName();
        }

        public Iterable<String> getWarnings() {
            return mWarnings;
        }

        private void warn(String warning) {
            mWarnings.add(warning);
            Log.w(TAG, warning);
        }

        public void clearWarnings() {
            mWarnings.clear();
        }

        public int getOutputFormatChangeCount() {
            return mOutputFormatChangeCount;
        }

        public void configureAndStart(MediaFormat format, TestSurface surface) {
            mSurface = surface;
            Log.i(TAG, "configure(" + format + ", " + mSurface.getSurface() + ")");
            mCodec.configure(format, mSurface.getSurface(), null /* crypto */, 0 /* flags */);
            Log.i(TAG, "start");
            mCodec.start();

            // inject some minimal setOutputSurface test
            // TODO: change this test to also change the surface midstream
            try {
                mCodec.setOutputSurface(null);
                fail("should not be able to set surface to NULL");
            } catch (IllegalArgumentException e) {}
            mCodec.setOutputSurface(mSurface.getSurface());

            mInputBuffers = mCodec.getInputBuffers();
            mOutputBuffers = mCodec.getOutputBuffers();
            Log.i(TAG, "configured " + mInputBuffers.length + " input[" +
                  mInputBuffers[0].capacity() + "] and " +
                  mOutputBuffers.length + "output[" +
                  (mOutputBuffers[0] == null ? null : mOutputBuffers[0].capacity()) + "]");
            mQueuedEos = false;
            mRenderedTimeStamps.clear();
            mLastRenderNanoTime = System.nanoTime();
            mFramesNotifiedRendered = 0;
        }

        public void stop() {
            Log.i(TAG, "stop");
            if (mRenderedTimeStamps.size() > RENDERED_FRAMES_THRESHOLD) {
                synchronized (mRenderLock) {
                    long untilMs = System.currentTimeMillis() + 1000;
                    while (mFramesNotifiedRendered == 0) {
                        long nowMs = System.currentTimeMillis();
                        if (nowMs >= untilMs) {
                            break;
                        }
                        try {
                            mRenderLock.wait(untilMs - nowMs);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    Log.i(TAG, "waited for " + (System.currentTimeMillis() + 1000 - untilMs)
                            + "ms for rendering");
                }
                assertFalse("rendered " + mRenderedTimeStamps.size()
                        + " frames, but none have been notified.", mFramesNotifiedRendered == 0);
            }
            mCodec.stop();
        }

        public void flush() {
            Log.i(TAG, "flush");
            mCodec.flush();
            mQueuedEos = false;
            mTimeStamps.clear();
        }

        public String dequeueAndReleaseOutputBuffer(MediaCodec.BufferInfo info) {
            int ix = mCodec.dequeueOutputBuffer(info, kTimeOutUs);
            if (ix == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mOutputBuffers = mCodec.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
                return null;
            } else if (ix == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = mCodec.getOutputFormat();
                Log.d(TAG, "output format has changed to " + format);
                int colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                mDoChecksum = isRecognizedFormat(colorFormat);
                mOutputFormatChanged = true;
                ++mOutputFormatChangeCount;
                return null;
            } else if (ix < 0) {
                Log.v(TAG, "no output");
                return null;
            }
            /* create checksum */
            long sum = 0;

            Log.v(TAG, "dequeue #" + ix + " => { [" + info.size + "] flags=" + info.flags +
                    " @" + info.presentationTimeUs + "}");

            // we get a nonzero size for valid decoded frames
            boolean doRender = (info.size != 0);

            if (doRender) {
                mRenderedTimeStamps.add(info.presentationTimeUs);
                if (!mTimeStamps.remove(info.presentationTimeUs)) {
                    warn("invalid (decoded) timestamp " + info.presentationTimeUs + ", queued " +
                            mTimeStamps);
                }
            }

            if (mSurface.getSurface() == null) {
                if (mDoChecksum) {
                    sum = checksum(mOutputBuffers[ix], info.size, mCRC);
                }
                mCodec.releaseOutputBuffer(ix, doRender);
            } else if (doRender) {
                // If using SurfaceTexture, as soon as we call releaseOutputBuffer, the
                // buffer will be forwarded to SurfaceTexture to convert to a texture.
                // The API doesn't guarantee that the texture will be available before
                // the call returns, so we need to wait for the onFrameAvailable callback
                // to fire.  If we don't wait, we risk dropping frames.
                mSurface.prepare();
                mCodec.releaseOutputBuffer(ix, doRender);
                mSurface.waitForDraw();
                if (mDoChecksum) {
                    sum = mSurface.checksum();
                }
            } else {
                mCodec.releaseOutputBuffer(ix, doRender);
            }

            if (mOutputFormatChanged) {
                // Previous dequeue was output format change; format change must
                // correspond to a new sequence, so it must happen right before
                // the first frame of one of the sequences.
                // this part of test is new for Android12
                if (sIsAtLeastS) {
                    assertTrue("Codec " + getName() + " cannot find formatchange " + info.presentationTimeUs +
                        " in " + mFirstQueueTimestamps,
                        mFirstQueueTimestamps.remove(info.presentationTimeUs));
                }
                mOutputFormatChanged = false;
            }

            return String.format(Locale.US, "{pts=%d, flags=%x, data=0x%x}",
                                 info.presentationTimeUs, info.flags, sum);
        }

        /* returns true iff queued a frame */
        public boolean queueInputBuffer(Media media, int frameIx, boolean EOS) {
            return queueInputBuffer(media, frameIx, EOS, 0);
        }

        public boolean queueInputBuffer(Media media, int frameIx, boolean EOS, long adjustTimeUs) {
            if (mQueuedEos) {
                return false;
            }

            int ix = mCodec.dequeueInputBuffer(kTimeOutUs);

            if (ix < 0) {
                return false;
            }

            ByteBuffer buf = mInputBuffers[ix];
            Media.Frame frame = media.getFrame(frameIx);
            buf.clear();

            long presentationTimeUs = adjustTimeUs;
            int flags = 0;
            if (frame != null) {
                buf.put((ByteBuffer)frame.buf.clear());
                presentationTimeUs += frame.presentationTimeUs;
                flags = frame.flags;
            }

            if (EOS) {
                flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mQueuedEos = true;
            }

            mTimeStamps.add(presentationTimeUs);
            Log.v(TAG, "queue { [" + buf.position() + "]=" + byteBufferToString(buf, 0, 16) +
                    " flags=" + flags + " @" + presentationTimeUs + "} => #" + ix);
            mCodec.queueInputBuffer(
                    ix, 0 /* offset */, buf.position(), presentationTimeUs, flags);
            return true;
        }

        /* returns number of frames received multiplied by -1 if received EOS, 1 otherwise */
        public int queueInputBufferRange(
                Media media, int frameStartIx, int frameEndIx, boolean sendEosAtEnd,
                boolean waitForEos) {
            return queueInputBufferRange(
                    media, frameStartIx, frameEndIx, sendEosAtEnd, waitForEos, 0, false);
        }

        public void queueCSD(MediaFormat format) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            for (int csdIx = 0; ; ++csdIx) {
                ByteBuffer csdBuf = format.getByteBuffer("csd-" + csdIx);
                if (csdBuf == null) {
                    break;
                }

                int ix = mCodec.dequeueInputBuffer(kCSDTimeOutUs);
                if (ix < 0) {
                    fail("Could not dequeue input buffer for CSD #" + csdIx);
                    return;
                }

                ByteBuffer buf = mInputBuffers[ix];
                buf.clear();
                buf.put((ByteBuffer)csdBuf.clear());
                csdBuf.clear();
                Log.v(TAG, "queue-CSD { [" + buf.position() + "]=" +
                        byteBufferToString(buf, 0, 16) + "} => #" + ix);
                mCodec.queueInputBuffer(
                        ix, 0 /* offset */, buf.position(), 0 /* timeUs */,
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
            }
        }

        public int queueInputBufferRange(
                Media media, int frameStartIx, int frameEndIx, boolean sendEosAtEnd,
                boolean waitForEos, long adjustTimeUs, boolean sleepAfterFirstQueue) {
            final int targetNumFramesDecoded = Math.min(frameEndIx - frameStartIx - 16, 0);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int frameIx = frameStartIx;
            int numFramesDecoded = 0;
            boolean sawOutputEos = false;
            int deadDecoderCounter = 0;
            ArrayList<String> frames = new ArrayList<String>();
            String buf = null;
            // After all input buffers are queued, dequeue as many output buffers as possible.
            while ((waitForEos && !sawOutputEos) || frameIx < frameEndIx ||
                   numFramesDecoded < targetNumFramesDecoded) {
                if (frameIx < frameEndIx) {
                    if (queueInputBuffer(
                            media,
                            frameIx,
                            sendEosAtEnd && (frameIx + 1 == frameEndIx),
                            adjustTimeUs)) {
                        if (frameIx == frameStartIx) {
                            if (sleepAfterFirstQueue) {
                                // MediaCodec detects and processes output format change upon
                                // the first frame. It must not send the event prematurely with
                                // pending buffers to be dequeued. Sleep after the first frame
                                // with new resolution to make sure MediaCodec had enough time
                                // to process the frame with pending buffers.
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {}
                            }
                            mFirstQueueTimestamps.add(mTimeStamps.get(mTimeStamps.size() - 1));
                        }
                        frameIx++;
                    }
                }

                buf = dequeueAndReleaseOutputBuffer(info);
                if (buf != null) {
                    // Some decoders output a 0-sized buffer at the end. Disregard those.
                    if (info.size > 0) {
                        deadDecoderCounter = 0;
                        numFramesDecoded++;
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "saw output EOS.");
                        sawOutputEos = true;
                    }
                }
                if (++deadDecoderCounter >= 100) {
                    warn("have not received an output frame for a while");
                    break;
                }
            }

            if (numFramesDecoded < targetNumFramesDecoded) {
                fail("Queued " + (frameEndIx - frameStartIx) + " frames but only received " +
                        numFramesDecoded);
            }
            return (sawOutputEos ? -1 : 1) * numFramesDecoded;
        }

        void release() {
            Log.i(TAG, "release");
            mCodec.release();
            mSurface.release();
            mInputBuffers = null;
            mOutputBuffers = null;
            mCodec = null;
            mSurface = null;
        }

        // don't fail on exceptions in release()
        void releaseQuietly() {
            try {
                Log.i(TAG, "release");
                mCodec.release();
            } catch (Throwable e) {
                Log.e(TAG, "Exception while releasing codec", e);
            }
            mSurface.release();
            mInputBuffers = null;
            mOutputBuffers = null;
            mCodec = null;
            mSurface = null;
        }
    };

    /* from EncodeDecodeTest */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private int countFrames(
            String codecName, MediaCodecInfo codecInfo, Media media, int eosframe, TestSurface s)
            throws Exception {
        Decoder codec = new Decoder(codecName);
        codec.configureAndStart(media.getFormat(), s /* surface */);

        int numframes = codec.queueInputBufferRange(
                media, 0, eosframe, true /* sendEos */, true /* waitForEos */);
        if (numframes >= 0) {
            Log.w(TAG, "Did not receive EOS");
        } else {
            numframes *= -1;
        }

        codec.stop();
        codec.release();
        return numframes;
    }
}

/* ====================================================================== */
/*                             Video Media Asset                          */
/* ====================================================================== */
class Media {
    private final static String TAG = "AdaptiveMedia";
    private MediaFormat mFormat;
    private MediaFormat mAdaptiveFormat;
    static class Frame {
        long presentationTimeUs;
        int flags;
        ByteBuffer buf;
        public Frame(long _pts, int _flags, ByteBuffer _buf) {
            presentationTimeUs = _pts;
            flags = _flags;
            buf = _buf;
        }
    };
    private Frame[] mFrames;

    public Frame getFrame(int ix) {
        /* this works even on short sample as frame is allocated as null */
        if (ix >= 0 && ix < mFrames.length) {
            return mFrames[ix];
        }
        return null;
    }
    private Media(MediaFormat format, MediaFormat adaptiveFormat, int numFrames) {
        /* need separate copies of format as once we add adaptive flags to
           MediaFormat, we cannot remove them */
        mFormat = format;
        mAdaptiveFormat = adaptiveFormat;
        mFrames = new Frame[numFrames];
    }

    public MediaFormat getFormat() {
        return mFormat;
    }

    public static MediaFormat removeCSD(MediaFormat orig) {
        MediaFormat copy = MediaFormat.createVideoFormat(
                orig.getString(orig.KEY_MIME),
                orig.getInteger(orig.KEY_WIDTH), orig.getInteger(orig.KEY_HEIGHT));
        for (String k : new String[] {
                orig.KEY_FRAME_RATE, orig.KEY_MAX_WIDTH, orig.KEY_MAX_HEIGHT,
                orig.KEY_MAX_INPUT_SIZE
        }) {
            if (orig.containsKey(k)) {
                try {
                    copy.setInteger(k, orig.getInteger(k));
                } catch (ClassCastException e) {
                    try {
                        copy.setFloat(k, orig.getFloat(k));
                    } catch (ClassCastException e2) {
                        // Could not copy value. Don't fail here, as having non-standard
                        // value types for defined keys is permissible by the media API
                        // for optional keys.
                    }
                }
            }
        }
        return copy;
    }

    public MediaFormat getAdaptiveFormat(int width, int height, int maxInputSize) {
        mAdaptiveFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
        mAdaptiveFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);
        mAdaptiveFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
        return mAdaptiveFormat;
    }

    public String getMime() {
        return mFormat.getString(MediaFormat.KEY_MIME);
    }

    public int getMaxInputSize() {
        return mFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
    }

    public void setMaxInputSize(int maxInputSize) {
        mFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
    }

    public int getWidth() {
        return mFormat.getInteger(MediaFormat.KEY_WIDTH);
    }

    public int getHeight() {
        return mFormat.getInteger(MediaFormat.KEY_HEIGHT);
    }

    public final static int RANGE_START = 0;
    public final static int RANGE_END = 1;
    public final static int RANGE_DURATION = 2;

    public long getTimestampRangeValue(int frameStartIx, int frameEndIx, int kind) {
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (int frameIx = frameStartIx; frameIx < frameEndIx; frameIx++) {
            Frame frame = getFrame(frameIx);
            if (frame != null) {
                if (min > frame.presentationTimeUs) {
                    min = frame.presentationTimeUs;
                }
                if (max < frame.presentationTimeUs) {
                    max = frame.presentationTimeUs;
                }
            }
        }
        if (kind == RANGE_START) {
            return min;
        } else if (kind == RANGE_END) {
            return max;
        } else if (kind == RANGE_DURATION) {
            return max - min;
        } else {
            throw new IllegalArgumentException("kind is not valid: " + kind);
        }
    }

    public static Media read(final String video, int numFrames)
            throws java.io.IOException {

        Preconditions.assertTestFileExists(video);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(video);

        Media media = new Media(
                extractor.getTrackFormat(0), extractor.getTrackFormat(0), numFrames);
        extractor.selectTrack(0);

        Log.i(TAG, "format=" + media.getFormat());
        ArrayList<ByteBuffer> csds = new ArrayList<ByteBuffer>();
        for (String tag: new String[] { "csd-0", "csd-1" }) {
            if (media.getFormat().containsKey(tag)) {
                ByteBuffer csd = media.getFormat().getByteBuffer(tag);
                Log.i(TAG, tag + "=" + AdaptivePlaybackTest.byteBufferToString(csd, 0, 16));
                csds.add(csd);
            }
        }

        int maxInputSize = 0;
        ByteBuffer readBuf = ByteBuffer.allocate(2000000);
        for (int ix = 0; ix < numFrames; ix++) {
            int sampleSize = extractor.readSampleData(readBuf, 0 /* offset */);

            if (sampleSize < 0) {
                throw new IllegalArgumentException("media is too short at " + ix + " frames");
            } else {
                readBuf.position(0).limit(sampleSize);
                for (ByteBuffer csd: csds) {
                    sampleSize += csd.capacity();
                }

                if (maxInputSize < sampleSize) {
                    maxInputSize = sampleSize;
                }

                ByteBuffer buf = ByteBuffer.allocate(sampleSize);
                for (ByteBuffer csd: csds) {
                    csd.clear();
                    buf.put(csd);
                    csd.clear();
                    Log.i(TAG, "csd[" + csd.capacity() + "]");
                }
                Log.i(TAG, "frame-" + ix + "[" + sampleSize + "]");
                csds.clear();
                buf.put(readBuf);
                media.mFrames[ix] = new Frame(
                    extractor.getSampleTime(),
                    extractor.getSampleFlags(),
                    buf);
                extractor.advance();
            }
        }
        extractor.release();

        /* Override MAX_INPUT_SIZE in format, as CSD is being combined
         * with one of the input buffers */
        media.setMaxInputSize(maxInputSize);
        return media;
    }
}

/* ====================================================================== */
/*                      Codec, CodecList and CodecFactory                 */
/* ====================================================================== */
class Codec {
    private final static String TAG = "AdaptiveCodec";

    public String name;
    public CodecCapabilities capabilities;
    public Media[] mediaList;
    public boolean adaptive;
    public boolean vendor;
    public Codec(MediaCodecInfo info, CodecCapabilities c, Media[] m) {
        name = info.getName();
        capabilities = c;
        List<Media> medias = new ArrayList<Media>();

        if (capabilities == null) {
            adaptive = false;
            vendor = true;
        } else {
            Log.w(TAG, "checking capabilities of " + name + " for " + m[0].getMime());
            adaptive = capabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback);
            vendor = info.isVendor();
            for (Media media : m) {
                if (media.getHeight() >= 720 &&
                        !capabilities.isFormatSupported(media.getFormat())) {
                    // skip if 720p and up is unsupported
                    Log.w(TAG, "codec " + name + " doesn't support " + media.getFormat());
                    continue;
                }
                medias.add(media);
            }
        }

        if (medias.size() < 2) {
            Log.e(TAG, "codec " + name + " doesn't support required resolutions");
        }
        mediaList = medias.subList(0, 2).toArray(new Media[2]);
    }
}

class CodecList extends ArrayList<Codec> {}

/* all codecs of mime, plus named codec if exists */
class CodecFamily extends CodecList {
    private final static String TAG = "AdaptiveCodecFamily";
    private static final int NUM_FRAMES = AdaptivePlaybackTest.NUM_FRAMES;
    static final String mInpPrefix = WorkDir.getMediaDirString();

    public CodecFamily(String mime, final String ... resources) {
        try {
            if (TestArgs.shouldSkipMediaType(mime)) {
                return;
            }

            /* read all media */
            Media[] mediaList = new Media[resources.length];
            for (int i = 0; i < resources.length; i++) {
                Log.v(TAG, "reading media " + mInpPrefix + resources[i]);
                Media media = Media.read(mInpPrefix + resources[i], NUM_FRAMES);
                assert media.getMime().equals(mime):
                        "test stream " + mInpPrefix + resources[i] + " has " + media.getMime() +
                        " mime type instead of " + mime;

                /* assuming the first timestamp is the smallest */
                long firstPTS = media.getFrame(0).presentationTimeUs;
                long smallestPTS = media.getTimestampRangeValue(0, NUM_FRAMES, Media.RANGE_START);

                assert firstPTS == smallestPTS:
                        "first frame timestamp (" + firstPTS + ") is not smallest (" +
                        smallestPTS + ")";

                mediaList[i] = media;
            }

            /* enumerate codecs */
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
                if (codecInfo.isAlias()) {
                    continue;
                }
                if (codecInfo.isEncoder()) {
                    continue;
                }
                for (String type : codecInfo.getSupportedTypes()) {
                    if (type.equals(mime)) {
                        add(new Codec(
                                codecInfo,
                                codecInfo.getCapabilitiesForType(mime),
                                mediaList));
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            Log.wtf("Constructor failed", t);
            throw new RuntimeException("constructor failed", t);
        }
    }
}

/* all codecs of mime, except named codec if exists */
class CodecFamilySpecific extends CodecList {
    public CodecFamilySpecific(String mime, boolean isGoogle, final String ... resources) {
        for (Codec c: new CodecFamily(mime, resources)) {
            if (!c.vendor == isGoogle) {
                add(c);
            }
        }
    }
}

class CodecFactory {
    public CodecList createCodecList(String mime, final String ...resources) {
        return new CodecFamily(mime, resources);
    }
}

class SWCodecFactory extends CodecFactory {
    public CodecList createCodecList(String mime, final String ...resources) {
        return new CodecFamilySpecific(mime, true, resources);
    }
}

class HWCodecFactory extends CodecFactory {
    public CodecList createCodecList(String mime, final String ...resources) {
        return new CodecFamilySpecific(mime, false, resources);
    }
}

/* ====================================================================== */
/*                  Test Steps, Test (Case)s, and Test List               */
/* ====================================================================== */
class StepRunner implements Runnable {
    public StepRunner(Step s) {
        mStep = s;
        mThrowed = null;
    }
    public void run() {
        try {
            mStep.run();
        } catch (Throwable e) {
            mThrowed = e;
        }
    }
    public void throwThrowed() throws Throwable {
        if (mThrowed != null) {
            throw mThrowed;
        }
    }
    private Throwable mThrowed;
    private Step mStep;
}

class TestList extends ArrayList<Step> {
    private final static String TAG = "AdaptiveTestList";
    public void run() throws Throwable {
        Throwable res = null;
        for (Step step: this) {
            try {
                Log.i(TAG, step.getDescription());
                if (step.stepSurface().needsToRunInSeparateThread()) {
                    StepRunner runner = new StepRunner(step);
                    Thread th = new Thread(runner, "stepWrapper");
                    th.start();
                    th.join();
                    runner.throwThrowed();
                } else {
                    step.run();
                }
            } catch (Throwable e) {
                Log.e(TAG, "while " + step.getDescription(), e);
                res = e;
                mFailedSteps++;
            } finally {
                mWarnings += step.getWarnings();
            }
        }
        if (res != null) {
            throw new RuntimeException(
                mFailedSteps + " failed steps, " + mWarnings + " warnings",
                res);
        }
    }
    public int getWarnings() {
        return mWarnings;
    }
    public int getFailures() {
        return mFailedSteps;
    }
    private int mFailedSteps;
    private int mWarnings;
}

abstract class MediaTest {
    public static final int FORMAT_ADAPTIVE_LARGEST = 1;
    public static final int FORMAT_ADAPTIVE_FIRST = 2;
    public static final int FORMAT_REGULAR = 3;

    protected int mFormatType;
    protected boolean mUseSurface;
    protected boolean mUseSurfaceTexture;

    public MediaTest() {
        mFormatType = FORMAT_REGULAR;
        mUseSurface = true;
        mUseSurfaceTexture = false;
    }

    public MediaTest adaptive() {
        mFormatType = FORMAT_ADAPTIVE_LARGEST;
        return this;
    }

    public MediaTest adaptiveSmall() {
        mFormatType = FORMAT_ADAPTIVE_FIRST;
        return this;
    }

    public MediaTest byteBuffer() {
        mUseSurface = false;
        mUseSurfaceTexture = false;
        return this;
    }

    public MediaTest texture() {
        mUseSurface = false;
        mUseSurfaceTexture = true;
        return this;
    }

    public void checkAdaptiveFormat() {
        assert mFormatType != FORMAT_REGULAR:
                "must be used with adaptive format";
    }

    abstract protected TestSurface getSurface();

    /* TRICKY: format is updated in each test run as we are actually reusing the
       same 2 MediaFormat objects returned from MediaExtractor.  Therefore,
       format must be explicitly obtained in each test step.

       returns null if codec does not support the format.
       */
    protected MediaFormat getFormat(Codec c) {
        return getFormat(c, 0);
    }

    protected MediaFormat getFormat(Codec c, int i) {
        MediaFormat format = null;
        if (mFormatType == FORMAT_REGULAR) {
            format = c.mediaList[i].getFormat();
        } else if (mFormatType == FORMAT_ADAPTIVE_FIRST && c.adaptive) {
            format = c.mediaList[i].getAdaptiveFormat(
                c.mediaList[i].getWidth(), c.mediaList[i].getHeight(), c.mediaList[i].getMaxInputSize());
            for (Media media : c.mediaList) {
                /* get the largest max input size for all media and use that */
                if (media.getMaxInputSize() > format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, media.getMaxInputSize());
                }
            }
        } else if (mFormatType == FORMAT_ADAPTIVE_LARGEST && c.adaptive) {
            /* update adaptive format to max size used */
            format = c.mediaList[i].getAdaptiveFormat(0, 0, 0);
            for (Media media : c.mediaList) {
                /* get the largest width, and the largest height independently */
                if (media.getWidth() > format.getInteger(MediaFormat.KEY_MAX_WIDTH)) {
                    format.setInteger(MediaFormat.KEY_MAX_WIDTH, media.getWidth());
                }
                if (media.getHeight() > format.getInteger(MediaFormat.KEY_MAX_HEIGHT)) {
                    format.setInteger(MediaFormat.KEY_MAX_HEIGHT, media.getHeight());
                }
                if (media.getMaxInputSize() > format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, media.getMaxInputSize());
                }
            }
        }
        return format;
    }

    public boolean isValid(Codec c) { return true; }
    public abstract void addTests(TestList tests, Codec c);
}

abstract class Step {
    private static final String TAG = "AdaptiveStep";

    public Step(String title, MediaTest instance, Codec codec, Media media) {
        mTest = instance;
        mCodec = codec;
        mMedia = media;
        mDescription = title + " on " + stepSurface().getSurface() + " using " +
            mCodec.name + " and " + stepFormat();
    }
    public Step(String title, MediaTest instance, Codec codec, int mediaIx) {
        this(title, instance, codec, codec.mediaList[mediaIx]);
    }
    public Step(String title, MediaTest instance, Codec codec) {
        this(title, instance, codec, 0);
    }
    public Step(String description) {
        mDescription = description;
    }
    public Step() { }

    public abstract void run() throws Throwable;

    private String mDescription;
    private MediaTest mTest;
    private Codec mCodec;
    private Media mMedia;
    private int mWarnings;

    /* TRICKY: use non-standard getter names so that we don't conflict with the getters
       in the Test classes, as most test Steps are defined as anonymous classes inside
       the test classes. */
    public MediaFormat stepFormat() {
        int ix = Arrays.asList(mCodec.mediaList).indexOf(mMedia);
        return mTest.getFormat(mCodec, ix);
    }

    public TestSurface stepSurface() {
        return mTest.getSurface();
    }

    public Media  stepMedia()       { return mMedia; }

    public String getDescription() { return mDescription; }
    public int    getWarnings()    { return mWarnings; }

    public void warn(String message) {
        Log.e(TAG, "WARNING: " + message + " in " + getDescription());
        mWarnings++;
    }
    public void warn(String message, Throwable t) {
        Log.e(TAG, "WARNING: " + message + " in " + getDescription(), t);
        mWarnings++;
    }
    public void warn(Iterable<String> warnings) {
        for (String warning: warnings) {
            warn(warning);
        }
    }
}

interface TestSurface {
    public Surface getSurface();
    public long checksum();
    public void release();
    public void prepare();         // prepare surface prior to render
    public void waitForDraw();     // wait for rendering to take place
    public boolean needsToRunInSeparateThread();
}

class DecoderSurface extends OutputSurface implements TestSurface {
    private ByteBuffer mBuf;
    int mWidth;
    int mHeight;
    CRC32 mCRC;

    public DecoderSurface(int width, int height, CRC32 crc) {
        super(width, height);
        mWidth = width;
        mHeight = height;
        mCRC = crc;
        mBuf = ByteBuffer.allocateDirect(4 * width * height);
    }

    public void prepare() {
        makeCurrent();
    }

    public void waitForDraw() {
        awaitNewImage();
        drawImage();
    }

    public long checksum() {
        mBuf.position(0);
        GLES20.glReadPixels(0, 0, mWidth, mHeight, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, mBuf);
        mBuf.position(0);
        return AdaptivePlaybackTest.checksum(mBuf, mBuf.capacity(), mCRC);
    }

    public void release() {
        super.release();
        mBuf = null;
    }

    public boolean needsToRunInSeparateThread() {
        return true;
    }
}

class ActivitySurface implements TestSurface {
    private Surface mSurface;
    public ActivitySurface(Surface s) {
        mSurface = s;
    }
    public Surface getSurface() {
        return mSurface;
    }
    public void prepare() { }
    public void waitForDraw() { }
    public long checksum() {
        return 0;
    }
    public void release() {
        // don't release activity surface, as it is reusable
    }
    public boolean needsToRunInSeparateThread() {
        return false;
    }
}
