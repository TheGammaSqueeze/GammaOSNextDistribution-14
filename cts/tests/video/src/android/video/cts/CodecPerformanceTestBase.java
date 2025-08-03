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

package android.video.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.TestArgs;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Range;
import android.view.Surface;

import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

class CodecPerformanceTestBase {
    private static final String LOG_TAG = CodecPerformanceTestBase.class.getSimpleName();
    static final long Q_DEQ_TIMEOUT_US = 5000; // block at most 5ms while looking for io buffers
    static final int PER_TEST_TIMEOUT_LARGE_TEST_MS = 300000;
    static final int MIN_FRAME_COUNT = 500;
    static final int SELECT_ALL = 0; // Select all codecs
    static final int SELECT_HARDWARE = 1; // Select Hardware codecs only
    static final int SELECT_SOFTWARE = 2; // Select Software codecs only
    // allowed tolerance in measured fps vs expected fps, i.e. codecs achieving fps
    // that is greater than (FPS_TOLERANCE_FACTOR * expectedFps) will be considered as
    // passing the test
    static final double FPS_TOLERANCE_FACTOR;
    static final boolean IS_AT_LEAST_VNDK_S;

    static final int DEVICE_INITIAL_SDK;
    static final int VNDK_VERSION;

    // Some older devices can not support concurrent instances of both decoder and encoder
    // at max resolution. To handle such cases, this test is limited to test the
    // resolutions that are less than half of max supported frame sizes of encoder.
    static final boolean EXCLUDE_ENCODER_MAX_RESOLUTION;

    // Some older devices can not support concurrent instances of both decoder and encoder
    // for operating rates > 0 and < 30
    static final boolean EXCLUDE_ENCODER_OPRATE_0_TO_30;

    static final String mInputPrefix = WorkDir.getMediaDirString();

    ArrayList<MediaCodec.BufferInfo> mBufferInfos;
    ByteBuffer mBuff;

    final String mDecoderName;
    final String mTestFile;
    final int mKeyPriority;
    final float mMaxOpRateScalingFactor;

    String mDecoderMime;
    int mWidth;
    int mHeight;
    int mFrameRate;

    boolean mSawDecInputEOS = false;
    boolean mSawDecOutputEOS = false;
    int mDecInputNum = 0;
    int mDecOutputNum = 0;
    int mSampleIndex = 0;

    MediaCodec mDecoder;
    MediaFormat mDecoderFormat;
    Surface mSurface;
    double mOperatingRateExpected;

    static final float[] SCALING_FACTORS_LIST = new float[]{2.5f, 1.25f, 1.0f, 0.75f, 0.0f, -1.0f};
    static final int[] KEY_PRIORITIES_LIST = new int[]{1, 0};

    static {
        // os.Build.VERSION.DEVICE_INITIAL_SDK_INT can be used here, but it was called
        // os.Build.VERSION.FIRST_SDK_INT in Android R and below. Using DEVICE_INITIAL_SDK_INT
        // will mean that the tests built in Android S can't be run on Android R and below.
        DEVICE_INITIAL_SDK = SystemProperties.getInt("ro.product.first_api_level", 0);

        VNDK_VERSION = SystemProperties.getInt("ro.vndk.version",
                Build.VERSION_CODES.CUR_DEVELOPMENT);

        // fps tolerance factor is kept quite low for devices with Android R VNDK or lower
        FPS_TOLERANCE_FACTOR = VNDK_VERSION <= Build.VERSION_CODES.R ? 0.67 : 0.95;

        IS_AT_LEAST_VNDK_S = VNDK_VERSION > Build.VERSION_CODES.R;

        // Encoders on devices launched on Android Q and lower aren't tested at maximum resolution
        EXCLUDE_ENCODER_MAX_RESOLUTION = DEVICE_INITIAL_SDK <= Build.VERSION_CODES.Q;

        // Encoders on devices launched on Android R and lower aren't tested when operating rate
        // that is set is > 0 and < 30.
        // This includes devices launched on Android S with R or lower vendor partition.
        EXCLUDE_ENCODER_OPRATE_0_TO_30 =
            !IS_AT_LEAST_VNDK_S || (DEVICE_INITIAL_SDK <= Build.VERSION_CODES.R);
    }

    @Before
    public void prologue() {
        assumeTrue("For VNDK R and below, operating rate <= 0 isn't tested",
                IS_AT_LEAST_VNDK_S || mMaxOpRateScalingFactor > 0.0);

        assumeTrue("For devices launched on Android P and below, operating rate tests are disabled",
                DEVICE_INITIAL_SDK > Build.VERSION_CODES.P);

        if (DEVICE_INITIAL_SDK <= Build.VERSION_CODES.Q) {
            assumeTrue("For devices launched with Android Q and below, operating rate tests are " +
                            "limited to operating rate scaling factor > 0.0 and <= 1.25",
                    mMaxOpRateScalingFactor > 0.0 && mMaxOpRateScalingFactor <= 1.25);
        }
    }

    public CodecPerformanceTestBase(String decoderName, String testFile, int keyPriority,
            float maxOpRateScalingFactor) {
        mDecoderName = decoderName;
        mTestFile = testFile;
        mKeyPriority = keyPriority;
        mMaxOpRateScalingFactor = maxOpRateScalingFactor;
        mBufferInfos = new ArrayList<>();
    }

    static MediaFormat getVideoFormat(String filePath) throws IOException {
        final String input = mInputPrefix + filePath;
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(input);
        for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
            MediaFormat format = extractor.getTrackFormat(trackID);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                extractor.release();
                return format;
            }
        }
        extractor.release();
        return null;
    }

    static ArrayList<String> selectCodecs(String mime, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder) {
        return selectCodecs(mime, formats, features, isEncoder, SELECT_ALL);
    }

    static ArrayList<String> selectHardwareCodecs(String mime, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder) {
        return selectCodecs(mime, formats, features, isEncoder, SELECT_HARDWARE);
    }

    static ArrayList<String> selectCodecs(String mime, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder, int selectCodecOption) {
        ArrayList<String> listOfCodecs = new ArrayList<>();
        if (TestArgs.shouldSkipMediaType(mime)) {
            return listOfCodecs;
        }
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();

        for (MediaCodecInfo codecInfo : codecInfos) {
            if (TestArgs.shouldSkipCodec(codecInfo.getName())) {
                continue;
            }
            if (codecInfo.isEncoder() != isEncoder) continue;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias()) continue;
            if (selectCodecOption == SELECT_HARDWARE && !codecInfo.isHardwareAccelerated())
                continue;
            else if (selectCodecOption == SELECT_SOFTWARE && !codecInfo.isSoftwareOnly())
                continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mime)) {
                    boolean isOk = true;
                    MediaCodecInfo.CodecCapabilities codecCapabilities =
                            codecInfo.getCapabilitiesForType(type);
                    if (formats != null) {
                        for (MediaFormat format : formats) {
                            if (!codecCapabilities.isFormatSupported(format)) {
                                isOk = false;
                                break;
                            }
                        }
                    }
                    if (features != null) {
                        for (String feature : features) {
                            if (!codecCapabilities.isFeatureSupported(feature)) {
                                isOk = false;
                                break;
                            }
                        }
                    }
                    if (isOk) listOfCodecs.add(codecInfo.getName());
                }
            }
        }
        return listOfCodecs;
    }

    MediaFormat setUpDecoderInput() throws IOException {
        final String input = mInputPrefix + mTestFile;
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(input);
        for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
            MediaFormat format = extractor.getTrackFormat(trackID);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                extractor.selectTrack(trackID);
                File file = new File(input);
                int bufferSize = (int) file.length();
                mBuff = ByteBuffer.allocate(bufferSize);
                int offset = 0;
                long maxPTS = 0;
                while (true) {
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    bufferInfo.size = extractor.readSampleData(mBuff, offset);
                    if (bufferInfo.size < 0) break;
                    bufferInfo.offset = offset;
                    bufferInfo.presentationTimeUs = extractor.getSampleTime();
                    maxPTS = Math.max(maxPTS, bufferInfo.presentationTimeUs);
                    int flags = extractor.getSampleFlags();
                    bufferInfo.flags = 0;
                    if ((flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        bufferInfo.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    }
                    mBufferInfos.add(bufferInfo);
                    extractor.advance();
                    offset += bufferInfo.size;
                }

                // If the clip doesn't have sufficient frames, loopback by copying bufferInfos
                // from the start of the list and incrementing the timestamp.
                int actualBufferInfosCount = mBufferInfos.size();
                long ptsOffset;
                while (mBufferInfos.size() < MIN_FRAME_COUNT) {
                    ptsOffset = maxPTS + 1000000L;
                    for (int i = 0; i < actualBufferInfosCount; i++) {
                        MediaCodec.BufferInfo tmpBufferInfo = mBufferInfos.get(i);
                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        bufferInfo.set(tmpBufferInfo.offset, tmpBufferInfo.size,
                                ptsOffset + tmpBufferInfo.presentationTimeUs,
                                tmpBufferInfo.flags);
                        maxPTS = Math.max(maxPTS, bufferInfo.presentationTimeUs);
                        mBufferInfos.add(bufferInfo);
                        if (mBufferInfos.size() >= MIN_FRAME_COUNT) break;
                    }
                }
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                bufferInfo.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                mBufferInfos.add(bufferInfo);
                mDecoderMime = format.getString(MediaFormat.KEY_MIME);
                mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                mFrameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE, 30);
                extractor.release();
                return format;
            }
        }
        extractor.release();
        fail("No video track found in file: " + mTestFile);
        return null;
    }

    // TODO (b/193458026) Limit max expected fps
    static int getMaxExpectedFps(int width, int height) {
        int numSamples = width * height;
        if (numSamples > 3840 * 2160 * 2) { // 8K
            return 30;
        } else if (numSamples > 1920 * 1088 * 2) { // 4K
            return 120;
        } else {
            return 240;
        }
    }

    int getMaxOperatingRate(String codecName, String mime) throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(codecName);
        MediaCodecInfo mediaCodecInfo = codec.getCodecInfo();
        List<MediaCodecInfo.VideoCapabilities.PerformancePoint> pps = mediaCodecInfo
                .getCapabilitiesForType(mime).getVideoCapabilities()
                .getSupportedPerformancePoints();
        assertTrue(pps.size() > 0);
        MediaCodecInfo.VideoCapabilities.PerformancePoint cpp =
                new MediaCodecInfo.VideoCapabilities.PerformancePoint(mWidth, mHeight, mFrameRate);
        int macroblocks = cpp.getMaxMacroBlocks();
        int maxOperatingRate = -1;
        for (MediaCodecInfo.VideoCapabilities.PerformancePoint pp : pps) {
            if (pp.covers(cpp)) {
                maxOperatingRate = Math.max(Math.min(pp.getMaxFrameRate(),
                        (int) pp.getMaxMacroBlockRate() / macroblocks), maxOperatingRate);
            }
        }
        codec.release();
        assumeTrue("Codec doesn't advertise performance point for " + mWidth + "x" + mHeight,
                maxOperatingRate != -1);
        return maxOperatingRate;
    }

    int getEncoderMinComplexity(String codecName, String mime) throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(codecName);
        MediaCodecInfo mediaCodecInfo = codec.getCodecInfo();
        int minComplexity = -1;
        if (mediaCodecInfo.isEncoder()) {
            Range<Integer> complexityRange = mediaCodecInfo
                    .getCapabilitiesForType(mime).getEncoderCapabilities()
                    .getComplexityRange();
            minComplexity = complexityRange.getLower();
        }
        codec.release();
        return minComplexity;
    }

    static int getMaxFrameSize(String codecName, String mime) throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(codecName);
        MediaCodecInfo.CodecCapabilities codecCapabilities =
                codec.getCodecInfo().getCapabilitiesForType(mime);
        MediaCodecInfo.VideoCapabilities vc = codecCapabilities.getVideoCapabilities();
        Range<Integer> heights = vc.getSupportedHeights();
        Range<Integer> widths = vc.getSupportedWidthsFor(heights.getUpper());
        int maxFrameSize = heights.getUpper() * widths.getUpper();
        codec.release();
        return maxFrameSize;
    }

    void enqueueDecoderInput(int bufferIndex) {
        MediaCodec.BufferInfo info = mBufferInfos.get(mSampleIndex++);
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            ByteBuffer dstBuf = mDecoder.getInputBuffer(bufferIndex);
            dstBuf.put(mBuff.array(), info.offset, info.size);
            mDecInputNum++;
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawDecInputEOS = true;
        }
        mDecoder.queueInputBuffer(bufferIndex, 0, info.size, info.presentationTimeUs, info.flags);
    }

    void dequeueDecoderOutput(int bufferIndex, MediaCodec.BufferInfo info, boolean render) {
        if (info.size > 0) {
            mDecOutputNum++;
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawDecOutputEOS = true;
        }
        mDecoder.releaseOutputBuffer(bufferIndex, render);
    }
}
