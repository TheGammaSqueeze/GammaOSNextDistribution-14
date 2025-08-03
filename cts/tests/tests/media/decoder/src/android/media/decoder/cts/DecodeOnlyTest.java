/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.app.ActivityManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "There should be no instant apps specific behavior related to decoders")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@RunWith(AndroidJUnit4.class)
@ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
public class DecodeOnlyTest extends MediaTestBase {
    public static final boolean WAS_LAUNCHED_ON_U_OR_LATER =
            SystemProperties.getInt("ro.product.first_api_level",
                                    Build.VERSION_CODES.CUR_DEVELOPMENT)
                    >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

    private static final String MEDIA_DIR_STRING = WorkDir.getMediaDirString();
    private static final String HEVC_VIDEO =
            "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv";
    private static final String AVC_VIDEO =
            "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4";
    private static final String VP9_VIDEO =
            "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm";
    private static final String MIME_VIDEO_PREFIX = "video/";
    private static final String MIME_AUDIO_PREFIX = "audio/";
    private static final long EOS_TIMESTAMP_TUNNEL_MODE = Long.MAX_VALUE;

    static {
        System.loadLibrary("ctsmediadecodertest_jni");
    }

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    /**
     * When testing perfect seek, assert that the first frame rendered after seeking is the exact
     * frame we seeked to
     */
    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOnAvc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(AVC_VIDEO, true);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOnVp9() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(VP9_VIDEO, true);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOnHevc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(HEVC_VIDEO, true);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOffAvc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(AVC_VIDEO, false);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOffVp9() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(VP9_VIDEO, false);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOffHevc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(HEVC_VIDEO, false);
    }

    /**
     * In trick play, we expect to receive/render the non DECODE_ONLY frames only
     */
    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledTrickPlayHevc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledTrickPlay(HEVC_VIDEO);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledTrickPlayAvc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledTrickPlay(AVC_VIDEO);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledTrickPlayVp9() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledTrickPlay(VP9_VIDEO);
    }

    private static native boolean nativeTestNonTunneledTrickPlay(String fileName, Surface surface,
                      boolean isAsync);

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void nativeTestNonTunneledTrickPlayHevc() {
        boolean[] boolStates = {true, false};
        for (boolean isAsync : boolStates) {
            assertTrue(nativeTestNonTunneledTrickPlay(MEDIA_DIR_STRING + HEVC_VIDEO,
                    getActivity().getSurfaceHolder().getSurface(), isAsync));
        }
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testNonTunneledTrickPlayHevc() throws Exception {
        testNonTunneledTrickPlay(HEVC_VIDEO);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testNonTunneledTrickPlayAvc() throws Exception {
        testNonTunneledTrickPlay(AVC_VIDEO);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testNonTunneledTrickPlayVp9() throws Exception {
        testNonTunneledTrickPlay(VP9_VIDEO);
    }

    private void testNonTunneledTrickPlay(String fileName) throws Exception {
        Preconditions.assertTestFileExists(MEDIA_DIR_STRING + fileName);
        // create the video extractor
        MediaExtractor videoExtractor = createMediaExtractor(fileName);

        // choose the first track that has the prefix "video/" and select it
        int videoTrackIndex = getFirstTrackWithMimePrefix(MIME_VIDEO_PREFIX, videoExtractor);
        videoExtractor.selectTrack(videoTrackIndex);

        // create the video codec
        MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);
        String mime = videoFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec videoCodec = MediaCodec.createDecoderByType(mime);

        AtomicBoolean done = new AtomicBoolean(false);
        List<Long> expectedPresentationTimes = new ArrayList<>();
        List<Long> receivedPresentationTimes = new ArrayList<>();

        // set a callback on the video codec to process the frames
        videoCodec.setCallback(new MediaCodec.Callback() {
            private boolean mEosQueued;
            int mDecodeOnlyCounter = 0;

            // before queueing a frame, check if it is the last frame and set the EOS flag
            // to the frame if that's the case. If the frame is to be only decoded
            // (every other frame), then set the DECODE_ONLY flag to the frame. Only frames not
            // tagged with EOS or DECODE_ONLY are expected to be rendered and added
            // to expectedPresentationTimes
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                if (!mEosQueued) {
                    ByteBuffer inputBuffer = videoCodec.getInputBuffer(index);
                    int sampleSize = videoExtractor.readSampleData(inputBuffer, 0);
                    long presentationTime = videoExtractor.getSampleTime();
                    int flags = 0;
                    if (sampleSize < 0) {
                        flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        sampleSize = 0;
                        mEosQueued = true;
                    } else if (mDecodeOnlyCounter % 2 == 0) {
                        flags = MediaCodec.BUFFER_FLAG_DECODE_ONLY;
                    } else {
                        expectedPresentationTimes.add(presentationTime);
                    }
                    mDecodeOnlyCounter++;
                    videoCodec.queueInputBuffer(index, 0, sampleSize, presentationTime, flags);
                    videoExtractor.advance();
                }
            }

            // the frames received here are the frames that are rendered, not the DECODE_ONLY ones,
            // if the DECODE_ONLY flag behaves correctly, receivedPresentationTimes should exactly
            // match expectedPresentationTimes
            // When the codec receives the EOS frame, set the done variable true, which will exit
            // the loop below, signaling that the codec has finished processing the video and
            // should now assert on the contents of the lists
            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index,
                    MediaCodec.BufferInfo info) {
                videoCodec.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    done.set(true);
                } else {
                    receivedPresentationTimes.add(info.presentationTimeUs);
                }
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

            }
        });

        videoCodec.configure(videoFormat, getActivity().getSurfaceHolder().getSurface(), null, 0);
        // start the video the codec with track selected
        videoCodec.start();

        // keep looping until the codec receives the EOS frame
        while (!done.get()) {
            Thread.sleep(100);
        }

        videoCodec.stop();
        videoCodec.release();

        Collections.sort(expectedPresentationTimes);
        assertEquals(expectedPresentationTimes, receivedPresentationTimes);
    }

    private void testTunneledTrickPlay(String fileName) throws Exception {
        Preconditions.assertTestFileExists(MEDIA_DIR_STRING + fileName);

        // generate the audio session id needed for tunnel mode playback
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        int audioSessionId = audioManager.generateAudioSessionId();

        // create the video extractor
        MediaExtractor videoExtractor = createMediaExtractor(fileName);

        // choose the first track that has the prefix "video/" and select it
        int videoTrackIndex = getFirstTrackWithMimePrefix(MIME_VIDEO_PREFIX, videoExtractor);
        videoExtractor.selectTrack(videoTrackIndex);

        // create the video codec for tunneled play
        MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);
        videoFormat.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback,
                true);
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        String codecName = mcl.findDecoderForFormat(videoFormat);
        Assume.assumeTrue("Codec is not supported on this device",
                codecName != null);
        videoFormat.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, audioSessionId);
        MediaCodec videoCodec = MediaCodec.createByCodecName(codecName);

        // create the audio extractor
        MediaExtractor audioExtractor = createMediaExtractor(fileName);

        // choose the first track that has the prefix "audio/" and select it
        int audioTrackIndex = getFirstTrackWithMimePrefix(MIME_AUDIO_PREFIX, audioExtractor);
        audioExtractor.selectTrack(audioTrackIndex);

        // create the audio codec
        MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrackIndex);
        String mime = audioFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec audioCodec = MediaCodec.createDecoderByType(mime);

        // audio track used by audio codec
        AudioTrack audioTrack = createAudioTrack(audioFormat, audioSessionId);

        List<Long> expectedPresentationTimes = new ArrayList<>();

        videoCodec.setCallback(new MediaCodec.Callback() {
            int mDecodeOnlyCounter = 0;

            // before queueing a frame, check if it is the last frame and set the EOS flag
            // to the frame if that's the case. If the frame is to be only decoded
            // (every other frame), then set the DECODE_ONLY flag to the frame. Only frames not
            // tagged with EOS or DECODE_ONLY are expected to be rendered and added
            // to expectedPresentationTimes
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                ByteBuffer inputBuffer = videoCodec.getInputBuffer(index);
                int sampleSize = videoExtractor.readSampleData(inputBuffer, 0);
                long presentationTime = videoExtractor.getSampleTime();
                int flags = 0;
                if (sampleSize < 0) {
                    flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    sampleSize = 0;
                } else if (mDecodeOnlyCounter % 2 == 0) {
                    flags = MediaCodec.BUFFER_FLAG_DECODE_ONLY;
                } else {
                    expectedPresentationTimes.add(presentationTime);
                }
                mDecodeOnlyCounter++;
                videoCodec.queueInputBuffer(index, 0, sampleSize, presentationTime, flags);
                videoExtractor.advance();
            }

            // nothing to do here - in tunneled mode, the frames are rendered directly by the
            // hardware, they are not sent back to the codec for extra processing
            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index,
                    MediaCodec.BufferInfo info) {
                Assert.fail("onOutputBufferAvailable should not be called in tunnel mode.");
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                Assert.fail("Encountered unexpected error while decoding video: " + e.getMessage());
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

            }
        });
        videoCodec.configure(videoFormat, getActivity().getSurfaceHolder().getSurface(), null, 0);
        AtomicBoolean done = new AtomicBoolean(false);
        // Since data is written to AudioTrack in a blocking manner, run it in a separate thread to
        // not block other operations.
        HandlerThread audioThread = new HandlerThread("audioThread");
        audioThread.start();
        audioCodec.setCallback(new AudioCallback(audioCodec, audioExtractor, audioTrack, done),
                new Handler(audioThread.getLooper()));
        audioCodec.configure(audioFormat, null, null, 0);

        List<Long> renderedPresentationTimes = new ArrayList<>();

        // a listener on rendered frames, if it is the last frame (EOS), then set the boolean to
        // true and exit the loop below, else add the frame to renderedPresentationTimes
        // to expectedPresentationTimes should be equal at the end of the test
        videoCodec.setOnFrameRenderedListener((codec, presentationTimeUs, nanoTime) -> {
            if (presentationTimeUs == EOS_TIMESTAMP_TUNNEL_MODE) {
                done.set(true);
            } else {
                renderedPresentationTimes.add(presentationTimeUs);
            }
        }, new Handler(Looper.getMainLooper()));

        // start media playback
        videoCodec.start();
        audioCodec.start();
        audioTrack.play();

        // keep looping until the codec receives the EOS frame
        while (!done.get()) {
            Thread.sleep(100);
        }
        audioTrack.stop();
        audioTrack.release();
        videoCodec.stop();
        videoCodec.release();
        audioCodec.stop();
        audioCodec.release();

        Collections.sort(expectedPresentationTimes);
        Collections.sort(renderedPresentationTimes);
        assertEquals(expectedPresentationTimes, renderedPresentationTimes);
    }

    private void sleepUntil(Supplier<Boolean> supplier, Duration maxWait) throws Exception {
        final long deadLineMs = System.currentTimeMillis() + maxWait.toMillis();
        do {
            Thread.sleep(50);
        } while (!supplier.get() && System.currentTimeMillis() < deadLineMs);
    }

    private void testTunneledPerfectSeek(String fileName,
            final boolean initialPeek) throws Exception {
        Preconditions.assertTestFileExists(MEDIA_DIR_STRING + fileName);

        // generate the audio session id needed for tunnel mode playback
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        int audioSessionId = audioManager.generateAudioSessionId();

        // create the video extractor
        MediaExtractor videoExtractor = createMediaExtractor(fileName);

        // choose the first track that has the prefix "video/" and select it
        int videoTrackIndex = getFirstTrackWithMimePrefix(MIME_VIDEO_PREFIX, videoExtractor);
        videoExtractor.selectTrack(videoTrackIndex);

        // create the video codec for tunneled play
        MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);
        videoFormat.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback,
                true);
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        String codecName = mcl.findDecoderForFormat(videoFormat);
        Assume.assumeTrue("Codec is not supported on this device",
                codecName != null);
        videoFormat.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, audioSessionId);
        MediaCodec videoCodec = MediaCodec.createByCodecName(codecName);

        // create the audio extractor
        MediaExtractor audioExtractor = createMediaExtractor(fileName);

        // choose the first track that has the prefix "audio/" and select it
        int audioTrackIndex = getFirstTrackWithMimePrefix(MIME_AUDIO_PREFIX, audioExtractor);
        audioExtractor.selectTrack(audioTrackIndex);

        // create the audio codec
        MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrackIndex);
        String mime = audioFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec audioCodec = MediaCodec.createDecoderByType(mime);

        // audio track used by audio codec
        AudioTrack audioTrack = createAudioTrack(audioFormat, audioSessionId);

        // Frames at 2s of each file are not key frame
        AtomicLong seekTime = new AtomicLong(2000 * 1000);
        videoExtractor.seekTo(seekTime.get(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        audioExtractor.seekTo(seekTime.get(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

        List<Long> expectedPresentationTimes = new ArrayList<>();
        AtomicBoolean done = new AtomicBoolean(false);
        AtomicBoolean hasDecodeOnlyFrames = new AtomicBoolean(false);
        videoCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                if (!done.get()) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(index);
                    int sampleSize = videoExtractor.readSampleData(inputBuffer, 0);
                    long presentationTime = videoExtractor.getSampleTime();
                    int flags = 0;
                    if (sampleSize < 0) {
                        flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        sampleSize = 0;
                    } else if (presentationTime < seekTime.get()) {
                        flags = MediaCodec.BUFFER_FLAG_DECODE_ONLY;
                        hasDecodeOnlyFrames.set(true);
                    } else {
                        expectedPresentationTimes.add(presentationTime);
                    }
                    codec.queueInputBuffer(index, 0, sampleSize, presentationTime, flags);
                    videoExtractor.advance();
                }
            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index,
                    MediaCodec.BufferInfo info) {
                Assert.fail("onOutputBufferAvailable should not be called in tunnel mode.");
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                Assert.fail("Encountered unexpected error while decoding video: " + e.getMessage());
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

            }
        });
        videoCodec.configure(videoFormat, getActivity().getSurfaceHolder().getSurface(), null, 0);
        // Since data is written to AudioTrack in a blocking manner, run it in a separate thread to
        // not block other operations.
        HandlerThread audioThread = new HandlerThread("audioThread");
        audioThread.start();
        audioCodec.setCallback(new AudioCallback(audioCodec, audioExtractor, audioTrack, done),
                new Handler(audioThread.getLooper()));
        audioCodec.configure(audioFormat, null, null, 0);

        List<Long> renderedPresentationTimes = new ArrayList<>();
        // play for 500ms
        videoCodec.setOnFrameRenderedListener((codec, presentationTimeUs, nanoTime) -> {
            renderedPresentationTimes.add(presentationTimeUs);
            if (presentationTimeUs >= seekTime.get() + 500 * 1000) {
                done.set(true);
            }
        }, new Handler(Looper.getMainLooper()));

        AtomicBoolean firstTunnelFrameReady = new AtomicBoolean(false);
        videoCodec.setOnFirstTunnelFrameReadyListener(new Handler(Looper.getMainLooper()),
                (codec) -> {
                    firstTunnelFrameReady.set(true);
                });

        boolean isPeeking = setKeyTunnelPeek(videoCodec, initialPeek ? 1 : 0);
        // start media playback
        videoCodec.start();
        audioCodec.start();

        // When video codecs are started, large chunks of contiguous physical memory need to be
        // allocated, which, on low-RAM devices, can trigger high CPU usage for moving memory
        // around to create contiguous space for the video decoder. This can cause an increase in
        // startup time for playback.
        ActivityManager activityManager = mContext.getSystemService(ActivityManager.class);
        final int firstFrameReadyTimeoutSeconds = activityManager.isLowRamDevice() ? 3 : 1;
        sleepUntil(firstTunnelFrameReady::get, Duration.ofSeconds(firstFrameReadyTimeoutSeconds));
        assertTrue(String.format("onFirstTunnelFrameReady not called within %d seconds",
                firstFrameReadyTimeoutSeconds), firstTunnelFrameReady.get());

        final int waitForRenderingMs = 1000;
        // Sleep for 1s here to ensure that either (1) when peek is on, high-latency display
        // pipelines have enough time to render the first frame, or (2) when peek is off, the
        // frame isn't rendered after long time.
        Thread.sleep(waitForRenderingMs);
        if (isPeeking) {
            assertEquals(1, renderedPresentationTimes.size());
            assertEquals(seekTime.get(), (long) renderedPresentationTimes.get(0));
        } else {
            assertTrue(renderedPresentationTimes.isEmpty());
        }

        assertTrue("No DECODE_ONLY frames have been produced, "
                        + "try changing the offset for the seek. To do this, find a timestamp "
                        + "that falls between two sync frames to ensure that there will "
                        + "be a few DECODE_ONLY frames. For example \"ffprobe -show_frames $video\""
                        + " can be used to list all the frames of a certain video and will show"
                        + " info about key frames and their timestamps.",
                hasDecodeOnlyFrames.get());

        // Run the playback to verify that frame at seek time is also rendered when peek is off.
        audioTrack.play();
        while (!done.get()) {
            Thread.sleep(100);
        }
        if (!isPeeking) {
            assertFalse(renderedPresentationTimes.isEmpty());
            assertEquals(seekTime.get(), (long) renderedPresentationTimes.get(0));
        }

        audioTrack.pause();
        // Just be safe that pause may take some time.
        Thread.sleep(500);
        videoCodec.flush();
        audioCodec.flush();
        audioTrack.flush();

        // Frames at 7s of each file are not key frame, and there is non-zero key frame before it
        seekTime.set(7000 * 1000);
        videoExtractor.seekTo(seekTime.get(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        audioExtractor.seekTo(seekTime.get(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        expectedPresentationTimes.clear();
        renderedPresentationTimes.clear();

        // a listener on rendered frames, if it is the last frame (EOS), then set the boolean to
        // true and exit the loop below, else add the frame to renderedPresentationTimes
        // to expectedPresentationTimes should be equal at the end of the test
        // renderedPresentationTimes should only contain frames starting with desired seek timestamp
        videoCodec.setOnFrameRenderedListener((codec, presentationTimeUs, nanoTime) -> {
            if (presentationTimeUs == EOS_TIMESTAMP_TUNNEL_MODE) {
                done.set(true);
            } else {
                renderedPresentationTimes.add(presentationTimeUs);
            }
        }, new Handler(Looper.getMainLooper()));

        // Restart media playback
        firstTunnelFrameReady.set(false);
        isPeeking = setKeyTunnelPeek(videoCodec, isPeeking ? 0 : 1);
        done.set(false);
        videoCodec.start();
        audioCodec.start();
        sleepUntil(firstTunnelFrameReady::get, Duration.ofSeconds(firstFrameReadyTimeoutSeconds));
        assertTrue(String.format("onFirstTunnelFrameReady not called within %d seconds",
                firstFrameReadyTimeoutSeconds), firstTunnelFrameReady.get());

        // Sleep for 1s here to ensure that either (1) when peek is on, high-latency display
        // pipelines have enough time to render the first frame, or (2) when peek is off, the
        // frame isn't rendered after long time.
        Thread.sleep(waitForRenderingMs);
        if (isPeeking) {
            assertEquals(1, renderedPresentationTimes.size());
            assertEquals(seekTime.get(), (long) renderedPresentationTimes.get(0));
        } else {
            assertTrue(renderedPresentationTimes.isEmpty());
        }

        if (!isPeeking) {
            // First frame should be rendered immediately after setting peek on.
            isPeeking = setKeyTunnelPeek(videoCodec, 1);
            // This is long due to high-latency display pipelines on TV devices.
            Thread.sleep(waitForRenderingMs);
            assertEquals(1, renderedPresentationTimes.size());
            assertEquals(seekTime.get(), (long) renderedPresentationTimes.get(0));
        }

        audioTrack.play();
        // keep looping until the codec receives the EOS frame
        while (!done.get()) {
            Thread.sleep(100);
        }

        audioTrack.stop();
        audioTrack.release();
        videoCodec.stop();
        videoCodec.release();
        audioCodec.stop();
        audioCodec.release();
        Collections.sort(expectedPresentationTimes);
        Collections.sort(renderedPresentationTimes);
        assertEquals(expectedPresentationTimes, renderedPresentationTimes);
        assertEquals(seekTime.get(), (long) renderedPresentationTimes.get(0));
    }

    // 1 is on, 0 is off.
    private boolean setKeyTunnelPeek(MediaCodec videoCodec, int value) {
        Bundle parameters = new Bundle();
        parameters.putInt(MediaCodec.PARAMETER_KEY_TUNNEL_PEEK, value);
        videoCodec.setParameters(parameters);
        return value != 0;
    }


    private AudioTrack createAudioTrack(MediaFormat audioFormat, int audioSessionId) {
        int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int channelConfig;

        switch (channelCount) {
            case 1:
                channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                break;
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            default:
                throw new IllegalArgumentException();
        }

        int minBufferSize =
                AudioTrack.getMinBufferSize(
                        sampleRate,
                        channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT);
        AudioAttributes audioAttributes = (new AudioAttributes.Builder())
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .setFlags(AudioAttributes.FLAG_HW_AV_SYNC)
                .build();
        AudioFormat af = (new AudioFormat.Builder())
                .setChannelMask(channelConfig)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .build();
        return new AudioTrack(audioAttributes, af, 2 * minBufferSize,
                AudioTrack.MODE_STREAM, audioSessionId);
    }

    private int getFirstTrackWithMimePrefix(String prefix, MediaExtractor videoExtractor) {
        int trackIndex = -1;
        for (int i = 0; i < videoExtractor.getTrackCount(); ++i) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith(prefix)) {
                trackIndex = i;
                break;
            }
        }
        assertTrue("Video track was not found.", trackIndex >= 0);
        return trackIndex;
    }

    private MediaExtractor createMediaExtractor(String fileName) throws IOException {
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(MEDIA_DIR_STRING + fileName);
        return mediaExtractor;
    }

    private static class AudioCallback extends MediaCodec.Callback {
        private final MediaCodec mAudioCodec;
        private final MediaExtractor mAudioExtractor;
        private final AudioTrack mAudioTrack;
        private final AtomicBoolean mDone;

        AudioCallback(MediaCodec audioCodec, MediaExtractor audioExtractor,
                AudioTrack audioTrack, AtomicBoolean done) {
            this.mAudioCodec = audioCodec;
            this.mAudioExtractor = audioExtractor;
            this.mAudioTrack = audioTrack;
            this.mDone = done;
        }

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            ByteBuffer audioInputBuffer = mAudioCodec.getInputBuffer(index);
            int audioSampleSize = mAudioExtractor.readSampleData(audioInputBuffer, 0);
            long presentationTime = mAudioExtractor.getSampleTime();
            int flags = 0;
            if (audioSampleSize < 0) {
                flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                audioSampleSize = 0;
                presentationTime = 0;
            }
            mAudioCodec.queueInputBuffer(index, 0, audioSampleSize, presentationTime, flags);
            mAudioExtractor.advance();
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index,
                MediaCodec.BufferInfo info) {
            if (!mDone.get()) {
                ByteBuffer outputBuffer = mAudioCodec.getOutputBuffer(index);
                byte[] audioArray = new byte[info.size];
                outputBuffer.get(audioArray);
                outputBuffer.clear();
                ByteBuffer audioData = ByteBuffer.wrap(audioArray);
                int writtenSize = 0;
                // This is a workaround, just to avoid blocking audio track write and codec
                // crashes caused by invalid index after audio track pause and codec flush.
                // b/291959069 to fix the outstanding callback issue.
                while (!mDone.get()) {
                    int written = mAudioTrack.write(audioData, info.size - writtenSize,
                            AudioTrack.WRITE_BLOCKING, info.presentationTimeUs * 1000);
                    if (written >= 0) {
                        writtenSize += written;
                        if (writtenSize >= info.size) {
                            break;
                        }
                        // When audio track is not in playing state, the write operation does not
                        // block in WRITE_BLOCKING mode. And when audio track is full, the audio
                        // data can not be fully written. Must sleep here to wait for free spaces.
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {
                        }
                    } else {
                        Assert.fail("AudioTrack write failure.");
                    }
                }
                mAudioCodec.releaseOutputBuffer(index, false);
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {

        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
        }
    }
}
