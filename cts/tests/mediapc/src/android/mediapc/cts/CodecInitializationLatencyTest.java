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

package android.mediapc.cts;

import static android.mediapc.cts.CodecTestBase.SELECT_ALL;
import static android.mediapc.cts.CodecTestBase.SELECT_AUDIO;
import static android.mediapc.cts.CodecTestBase.SELECT_HARDWARE;
import static android.mediapc.cts.CodecTestBase.SELECT_VIDEO;
import static android.mediapc.cts.CodecTestBase.codecFilter;
import static android.mediapc.cts.CodecTestBase.codecPrefix;
import static android.mediapc.cts.CodecTestBase.getMimesOfAvailableCodecs;
import static android.mediapc.cts.CodecTestBase.mediaTypePrefix;
import static android.mediapc.cts.CodecTestBase.selectCodecs;
import static android.mediapc.cts.CodecTestBase.selectHardwareCodecs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Utils;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The following test class validates the codec initialization latency (time for codec create +
 * configure) for the audio codecs and hardware video codecs available in the device, under the
 * load condition (Transcode + MediaRecorder session Audio(Microphone) and 1080p Video(Camera)).
 */
@RunWith(Parameterized.class)
public class CodecInitializationLatencyTest {
    private static final String LOG_TAG = CodecInitializationLatencyTest.class.getSimpleName();
    private static final boolean[] boolStates = {false, true};

    private static final String AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    private static final String AVC_TRANSCODE_FILE = "bbb_1920x1080_8mbps_60fps_avc.mp4";
    private static String AVC_DECODER_NAME;
    private static String AVC_ENCODER_NAME;
    private static final Map<String, String> mTestFiles = new HashMap<>();

    @Rule
    public final TestName mTestName = new TestName();

    static {
        // TODO(b/222006626): Add tests vectors for remaining media types
        // Audio media types
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_stereo_48kHz_128kbps_aac.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_AMR_NB, "bbb_mono_8kHz_12.2kbps_amrnb.3gp");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_AMR_WB, "bbb_1ch_16kHz_23kbps_amrwb.3gp");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_1ch_12kHz_lvl4_flac.mka");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_G711_ALAW, "bbb_2ch_8kHz_alaw.wav");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_G711_MLAW, "bbb_2ch_8kHz_mulaw.wav");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_1ch_8kHz_lame_cbr.mp3");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_MSGSM, "bbb_1ch_8kHz_gsm.wav");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_OPUS, "bbb_2ch_48kHz_opus.mka");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_1ch_8kHz.wav");
        mTestFiles.put(MediaFormat.MIMETYPE_AUDIO_VORBIS, "bbb_stereo_48kHz_128kbps_vorbis.ogg");

        // Video media types
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_1920x1080_4mbps_30fps_av1.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_1920x1080_6mbps_30fps_avc.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_H263, "bbb_cif_768kbps_30fps_h263.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_1920x1080_4mbps_30fps_hevc.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_1920x1080_12mbps_30fps_mpeg2.mp4");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_cif_768kbps_30fps_mpeg4.mkv");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_1920x1080_6mbps_30fps_vp8.webm");
        mTestFiles.put(MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_1920x1080_4mbps_30fps_vp9.webm");
    }

    private final String mMime;
    private final String mCodecName;

    private LoadStatus mTranscodeLoadStatus = null;
    private Thread mTranscodeLoadThread = null;
    private MediaRecorder mMediaRecorderLoad = null;
    private File mTempRecordedFile = null;
    private Surface mSurface = null;
    private Exception mException = null;

    @Before
    public void setUp() throws Exception {
        Utils.assumeDeviceMeetsPerformanceClassPreconditions();

        ArrayList<String> listOfAvcHwDecoders = selectHardwareCodecs(AVC, null, null, false);
        assumeFalse("Test requires h/w avc decoder", listOfAvcHwDecoders.isEmpty());
        AVC_DECODER_NAME = listOfAvcHwDecoders.get(0);

        ArrayList<String> listOfAvcHwEncoders = selectHardwareCodecs(AVC, null, null, true);
        assumeFalse("Test requires h/w avc encoder", listOfAvcHwEncoders.isEmpty());
        AVC_ENCODER_NAME = listOfAvcHwEncoders.get(0);

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        PackageManager packageManager = context.getPackageManager();
        assertNotNull(packageManager.getSystemAvailableFeatures());
        assumeTrue("The device doesn't have a camera",
                packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));
        assumeTrue("The device doesn't have a microphone",
                packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
        createSurface();
        startLoad();
    }

    @After
    public void tearDown() throws Exception {
        stopLoad();
        releaseSurface();
    }

    public CodecInitializationLatencyTest(String mimeType, String codecName) {
        mMime = mimeType;
        mCodecName = codecName;
    }

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);

    /**
     * Returns the list of parameters with mimetype and their codecs(for audio - all codecs,
     * video - hardware codecs).
     *
     * @return Collection of Parameters {0}_{1} -- MIME_CodecName
     */
    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> inputParams() {
        // Prepares the params list with the required Hardware video codecs and all available
        // audio codecs present in the device.
        final List<Object[]> argsList = new ArrayList<>();
        Set<String> mimeSet = getMimesOfAvailableCodecs(SELECT_VIDEO, SELECT_HARDWARE);
        mimeSet.addAll(getMimesOfAvailableCodecs(SELECT_AUDIO, SELECT_ALL));
        for (String mime : mimeSet) {
            if (mediaTypePrefix != null && !mime.startsWith(mediaTypePrefix)) {
                continue;
            }
            ArrayList<String> listOfCodecs;
            if (mime.startsWith("audio/")) {
                listOfCodecs = selectCodecs(mime, null, null, true);
                listOfCodecs.addAll(selectCodecs(mime, null, null, false));
            } else {
                listOfCodecs = selectHardwareCodecs(mime, null, null, true);
                listOfCodecs.addAll(selectHardwareCodecs(mime, null, null, false));
            }
            for (String codec : listOfCodecs) {
                if ((codecPrefix != null && !codec.startsWith(codecPrefix))
                        || (codecFilter != null && !codecFilter.matcher(codec).matches())) {
                    continue;
                }
                argsList.add(new Object[]{mime, codec});
            }
        }
        return argsList;
    }

    private MediaRecorder createMediaRecorderLoad(Surface surface) throws Exception {
        MediaRecorder mediaRecorder = new MediaRecorder(InstrumentationRegistry.getInstrumentation()
                .getContext());
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(mMime.equalsIgnoreCase(HEVC) ?
                MediaRecorder.VideoEncoder.HEVC : MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setOutputFile(mTempRecordedFile);
        mediaRecorder.setVideoSize(1920, 1080);
        mediaRecorder.setOrientationHint(0);
        mediaRecorder.setPreviewDisplay(surface);
        mediaRecorder.prepare();
        return mediaRecorder;
    }

    private void startLoad() throws Exception {
        // Create Transcode load (AVC Decoder(1080p) + AVC Encoder(720p))
        mTranscodeLoadStatus = new LoadStatus();
        mTranscodeLoadThread = new Thread(() -> {
            try {
                TranscodeLoad transcodeLoad = new TranscodeLoad(AVC, AVC_TRANSCODE_FILE,
                        AVC_DECODER_NAME, AVC_ENCODER_NAME, mTranscodeLoadStatus);
                transcodeLoad.doTranscode();
            } catch (Exception e) {
                mException = e;
            }
        });
        // Create MediaRecorder Session - Audio (Microphone) + 1080p Video (Camera)
        // Create a temp file to dump the MediaRecorder output. Later it will be deleted.
        mTempRecordedFile = new File(WorkDir.getMediaDirString() + "tempOut.mp4");
        mTempRecordedFile.createNewFile();
        mMediaRecorderLoad = createMediaRecorderLoad(mSurface);
        // Start the Loads
        mTranscodeLoadThread.start();
        mMediaRecorderLoad.start();
    }

    private void stopLoad() throws Exception {
        if (mTranscodeLoadStatus != null) {
            mTranscodeLoadStatus.setLoadFinished();
            mTranscodeLoadStatus = null;
        }
        if (mTranscodeLoadThread != null) {
            mTranscodeLoadThread.join();
            mTranscodeLoadThread = null;
        }
        if (mMediaRecorderLoad != null) {
            // Note that a RuntimeException is intentionally thrown to the application, if no valid
            // audio/video data has been received when stop() is called. This happens if stop() is
            // called immediately after start(). So sleep for 1000ms inorder to make sure some
            // data has been received between start() and stop().
            Thread.sleep(1000);
            mMediaRecorderLoad.stop();
            mMediaRecorderLoad.release();
            mMediaRecorderLoad = null;
            if (mTempRecordedFile != null && mTempRecordedFile.exists()) {
                mTempRecordedFile.delete();
                mTempRecordedFile = null;
            }
        }
        if (mException != null) throw mException;
    }

    private void createSurface() throws InterruptedException {
        mActivityRule.getActivity().waitTillSurfaceIsCreated();
        mSurface = mActivityRule.getActivity().getSurface();
        assertNotNull("Surface created is null.", mSurface);
        assertTrue("Surface created is invalid.", mSurface.isValid());
        mActivityRule.getActivity().setScreenParams(1920, 1080, true);
    }

    private void releaseSurface() {
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    /**
     * This test validates the initialization latency (time for codec create + configure) for
     * audio and hw video codecs.
     *
     * <p>Measurements are taken 5 * 2(sync/async) * [1 or 2]
     * (surface/non-surface for video) times. This also logs the stats: min, max, avg of the codec
     * initialization latencies.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
        "2.2.7.1/5.1/H-1-7",
        "2.2.7.1/5.1/H-1-8",
        "2.2.7.1/5.1/H-1-12",
        "2.2.7.1/5.1/H-1-13",})
    public void testInitializationLatency() throws Exception {
        MediaCodec codec = MediaCodec.createByCodecName(mCodecName);
        boolean isEncoder = codec.getCodecInfo().isEncoder();
        boolean isAudio = mMime.startsWith("audio/");
        codec.release();
        final int NUM_MEASUREMENTS = 5;
        // Test gathers initialization latency for a number of iterations and
        // percentile is a variable used to control how many of these iterations
        // need to meet the pass criteria. For eg. if NUM_MEASUREMENTS = 5, audio, sync and Async
        // modes which is a total of 10 iterations, this translates to index 7.
        final int percentile = 70;
        long sumOfCodecInitializationLatencyMs = 0;
        int count = 0;
        int numOfActualMeasurements =
                NUM_MEASUREMENTS * boolStates.length * ((!isEncoder && !isAudio) ? 2 : 1);
        long[] codecInitializationLatencyMs = new long[numOfActualMeasurements];
        for (int i = 0; i < NUM_MEASUREMENTS; i++) {
            for (boolean isAsync : boolStates) {
                long latency;
                if (isEncoder) {
                    EncoderInitializationLatency encoderInitializationLatency =
                            new EncoderInitializationLatency(mMime, mCodecName, isAsync);
                    latency = encoderInitializationLatency.calculateInitLatency();
                    codecInitializationLatencyMs[count] = latency;
                    sumOfCodecInitializationLatencyMs += latency;
                    count++;
                } else {
                    String testFile = mTestFiles.get(mMime);
                    assumeTrue("Add test vector for media type: " + mMime, testFile != null);
                    if (isAudio) {
                        DecoderInitializationLatency decoderInitializationLatency =
                                new DecoderInitializationLatency(mMime, mCodecName, testFile,
                                        isAsync, false);
                        latency = decoderInitializationLatency.calculateInitLatency();
                        codecInitializationLatencyMs[count] = latency;
                        sumOfCodecInitializationLatencyMs += latency;
                        count++;
                    } else {
                        for (boolean surfaceMode : boolStates) {
                            DecoderInitializationLatency decoderInitializationLatency =
                                    new DecoderInitializationLatency(mMime, mCodecName,
                                            testFile,
                                            isAsync, surfaceMode);
                            latency = decoderInitializationLatency.calculateInitLatency();
                            codecInitializationLatencyMs[count] = latency;
                            sumOfCodecInitializationLatencyMs += latency;
                            count++;
                        }
                    }
                }
            }
        }
        Arrays.sort(codecInitializationLatencyMs);

        String statsLog = String.format("CodecInitialization latency for mime: %s, " +
                "Codec: %s, in Ms :: ", mMime, mCodecName);
        Log.i(LOG_TAG, "Min " + statsLog + codecInitializationLatencyMs[0]);
        Log.i(LOG_TAG, "Max " + statsLog + codecInitializationLatencyMs[count - 1]);
        Log.i(LOG_TAG, "Avg " + statsLog + (sumOfCodecInitializationLatencyMs / count));
        long initializationLatency = codecInitializationLatencyMs[percentile * count / 100];

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.CodecInitLatencyRequirement r5_1__H_1_Latency =
                isEncoder ? isAudio ? pce.addR5_1__H_1_8() : pce.addR5_1__H_1_7(mMime)
                    : isAudio ? pce.addR5_1__H_1_13() : pce.addR5_1__H_1_12();

        r5_1__H_1_Latency.setCodecInitLatencyMs(initializationLatency);

        pce.submitAndCheck();
    }

    /**
     * The following class calculates the encoder initialization latency (time for codec create +
     * configure).
     *
     * <p>And also logs the time taken by the encoder for:
     * (create + configure + start),
     * (create + configure + start + first frame to enqueue),
     * (create + configure + start + first frame to dequeue).
     */
    static class EncoderInitializationLatency extends CodecEncoderTestBase {
        private static final String LOG_TAG = EncoderInitializationLatency.class.getSimpleName();

        private final String mEncoderName;
        private final boolean mIsAsync;

        EncoderInitializationLatency(String mime, String encoderName, boolean isAsync) {
            super(mime);
            mEncoderName = encoderName;
            mIsAsync = isAsync;
            mSampleRate = 8000;
            mFrameRate = 60;
        }

        private MediaFormat setUpFormat() throws IOException {
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, mMime);
            if (mIsAudio) {
                if (mMime.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                    format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 10000);
                } else {
                    format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
                }
                format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mSampleRate);
                format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            } else {
                MediaCodec codec = MediaCodec.createByCodecName(mEncoderName);
                MediaCodecInfo.CodecCapabilities codecCapabilities =
                        codec.getCodecInfo().getCapabilitiesForType(mMime);
                if (codecCapabilities.getVideoCapabilities().isSizeSupported(1920, 1080)) {
                    format.setInteger(MediaFormat.KEY_WIDTH, 1920);
                    format.setInteger(MediaFormat.KEY_HEIGHT, 1080);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, 8000000);
                } else if (codecCapabilities.getVideoCapabilities().isSizeSupported(1280, 720)) {
                    format.setInteger(MediaFormat.KEY_WIDTH, 1280);
                    format.setInteger(MediaFormat.KEY_HEIGHT, 720);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, 5000000);
                } else if (codecCapabilities.getVideoCapabilities().isSizeSupported(640, 480)) {
                    format.setInteger(MediaFormat.KEY_WIDTH, 640);
                    format.setInteger(MediaFormat.KEY_HEIGHT, 480);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
                } else if (codecCapabilities.getVideoCapabilities().isSizeSupported(352, 288)) {
                    format.setInteger(MediaFormat.KEY_WIDTH, 352);
                    format.setInteger(MediaFormat.KEY_HEIGHT, 288);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, 512000);
                } else {
                    format.setInteger(MediaFormat.KEY_WIDTH, 176);
                    format.setInteger(MediaFormat.KEY_HEIGHT, 144);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
                }
                codec.release();
                format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
                format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            }
            return format;
        }

        public long calculateInitLatency() throws Exception {
            MediaFormat format = setUpFormat();
            if (mIsAudio) {
                mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            } else {
                mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
            }
            setUpSource(mInputFile);
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            long enqueueTimeStamp = 0;
            long dequeueTimeStamp = 0;
            long baseTimeStamp = SystemClock.elapsedRealtimeNanos();
            mCodec = MediaCodec.createByCodecName(mEncoderName);
            resetContext(mIsAsync, false);
            mAsyncHandle.setCallBack(mCodec, mIsAsync);
            mCodec.configure(format, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
            long configureTimeStamp = SystemClock.elapsedRealtimeNanos();
            mCodec.start();
            long startTimeStamp = SystemClock.elapsedRealtimeNanos();
            if (mIsAsync) {
                // We will keep on feeding the input to encoder until we see the first dequeued
                // frame.
                while (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                    Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                    if (element != null) {
                        int bufferID = element.first;
                        MediaCodec.BufferInfo info = element.second;
                        if (info != null) {
                            dequeueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            dequeueOutput(bufferID, info);
                            break;
                        } else {
                            if (enqueueTimeStamp == 0) {
                                enqueueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            }
                            enqueueInput(bufferID);
                        }
                    }
                }
            } else {
                while (!mSawOutputEOS) {
                    if (!mSawInputEOS) {
                        int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                        if (inputBufferId > 0) {
                            if (enqueueTimeStamp == 0) {
                                enqueueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            }
                            enqueueInput(inputBufferId);
                        }
                    }
                    int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                    if (outputBufferId >= 0) {
                        dequeueTimeStamp = SystemClock.elapsedRealtimeNanos();
                        dequeueOutput(outputBufferId, outInfo);
                        break;
                    }
                }
            }
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();
            mCodec.release();
            Log.d(LOG_TAG, "Encode Mime: " + mMime + " Encoder: " + mEncoderName +
                    " Time for (create + configure) in ns: " +
                    (configureTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Encode Mime: " + mMime + " Encoder: " + mEncoderName +
                    " Time for (create + configure + start) in ns: " +
                    (startTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Encode Mime: " + mMime + " Encoder: " + mEncoderName +
                    " Time for (create + configure + start + first frame to enqueue) in ns: " +
                    (enqueueTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Encode Mime: " + mMime + " Encoder: " + mEncoderName +
                    " Time for (create + configure + start + first frame to dequeue) in ns: " +
                    (dequeueTimeStamp - baseTimeStamp));
            long timeToConfigureMs = (configureTimeStamp - baseTimeStamp) / 1000000;
            return timeToConfigureMs;
        }
    }

    /**
     * The following class calculates the decoder initialization latency (time for codec create +
     * configure).
     * And also logs the time taken by the decoder for:
     * (create + configure + start),
     * (create + configure + start + first frame to enqueue),
     * (create + configure + start + first frame to dequeue).
     */
    static class DecoderInitializationLatency extends CodecDecoderTestBase {
        private static final String LOG_TAG = DecoderInitializationLatency.class.getSimpleName();

        private final String mDecoderName;
        private final boolean mIsAsync;

        DecoderInitializationLatency(String mediaType, String decoderName, String testFile,
                boolean isAsync, boolean surfaceMode) {
            super(mediaType, testFile);
            mDecoderName = decoderName;
            mIsAsync = isAsync;
            mSurface = mIsAudio ? null :
                    surfaceMode ? MediaCodec.createPersistentInputSurface() : null;
        }

        public long calculateInitLatency() throws Exception {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            MediaFormat format = setUpSource(mTestFile);
            ArrayList<MediaFormat> formats = new ArrayList<>();
            formats.add(format);
            // If the decoder doesn't support the formats, then return Integer.MAX_VALUE to
            // indicate that all decode was not successful
            if (!areFormatsSupported(mDecoderName, formats)) {
                return Integer.MAX_VALUE;
            }
            long enqueueTimeStamp = 0;
            long dequeueTimeStamp = 0;
            long baseTimeStamp = SystemClock.elapsedRealtimeNanos();
            mCodec = MediaCodec.createByCodecName(mDecoderName);
            resetContext(mIsAsync, false);
            mAsyncHandle.setCallBack(mCodec, mIsAsync);
            mCodec.configure(format, mSurface, 0, null);
            long configureTimeStamp = SystemClock.elapsedRealtimeNanos();
            mCodec.start();
            long startTimeStamp = SystemClock.elapsedRealtimeNanos();
            if (mIsAsync) {
                // We will keep on feeding the input to decoder until we see the first dequeued
                // frame.
                while (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                    Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                    if (element != null) {
                        int bufferID = element.first;
                        MediaCodec.BufferInfo info = element.second;
                        if (info != null) {
                            dequeueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            dequeueOutput(bufferID, info);
                            break;
                        } else {
                            if (enqueueTimeStamp == 0) {
                                enqueueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            }
                            enqueueInput(bufferID);
                        }
                    }
                }
            } else {
                while (!mSawOutputEOS) {
                    if (!mSawInputEOS) {
                        int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                        if (inputBufferId >= 0) {
                            if (enqueueTimeStamp == 0) {
                                enqueueTimeStamp = SystemClock.elapsedRealtimeNanos();
                            }
                            enqueueInput(inputBufferId);
                        }
                    }
                    int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                    if (outputBufferId >= 0) {
                        dequeueTimeStamp = SystemClock.elapsedRealtimeNanos();
                        dequeueOutput(outputBufferId, outInfo);
                        break;
                    }
                }
            }
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();
            mCodec.release();
            if (mSurface != null) {
                mSurface.release();
            }
            Log.d(LOG_TAG, "Decode Mime: " + mMime + " Decoder: " + mDecoderName +
                    " Time for (create + configure) in ns: " +
                    (configureTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Decode Mime: " + mMime + " Decoder: " + mDecoderName +
                    " Time for (create + configure + start) in ns: " +
                    (startTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Decode Mime: " + mMime + " Decoder: " + mDecoderName +
                    " Time for (create + configure + start + first frame to enqueue) in ns: " +
                    (enqueueTimeStamp - baseTimeStamp));
            Log.d(LOG_TAG, "Decode Mime: " + mMime + " Decoder: " + mDecoderName +
                    " Time for (create + configure + start + first frame to dequeue) in ns: " +
                    (dequeueTimeStamp - baseTimeStamp));
            long timeToConfigureMs = (configureTimeStamp - baseTimeStamp) / 1000000;
            return timeToConfigureMs;
        }
    }
}
