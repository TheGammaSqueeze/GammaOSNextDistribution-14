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

package android.mediav2.common.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_FIRST;
import static android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_LAST;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.graphics.ImageFormat;
import android.media.AudioFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.PersistableBundle;
import android.util.Log;

import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper class for trying and testing encoder components.
 */
public class CodecEncoderTestBase extends CodecTestBase {
    private static final String LOG_TAG = CodecEncoderTestBase.class.getSimpleName();

    protected final EncoderConfigParams[] mEncCfgParams;

    protected EncoderConfigParams mActiveEncCfg;
    protected RawResource mActiveRawRes;
    protected boolean mIsLoopBack;
    protected int mLoopBackFrameLimit;

    protected byte[] mInputData;
    protected int mInputBufferReadOffset;
    protected int mNumBytesSubmitted;
    protected long mInputOffsetPts;

    protected ArrayList<MediaCodec.BufferInfo> mInfoList = new ArrayList<>();

    protected boolean mMuxOutput;
    protected String mMuxedOutputFile;
    protected MediaMuxer mMuxer;
    protected int mTrackID = -1;

    public CodecEncoderTestBase(String encoder, String mediaType,
            EncoderConfigParams[] encCfgParams, String allTestParams) {
        super(encoder, mediaType, allTestParams);
        mEncCfgParams = encCfgParams;
    }

    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_MP4 = new ArrayList<>(
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_MPEG4, MediaFormat.MIMETYPE_VIDEO_H263,
                    MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                    MediaFormat.MIMETYPE_AUDIO_AAC));
    static {
        if (CodecTestBase.IS_AT_LEAST_U) {
            MEDIATYPE_LIST_FOR_TYPE_MP4.add(MediaFormat.MIMETYPE_VIDEO_AV1);
        }
    }
    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_WEBM =
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_VP8, MediaFormat.MIMETYPE_VIDEO_VP9,
                    MediaFormat.MIMETYPE_AUDIO_VORBIS, MediaFormat.MIMETYPE_AUDIO_OPUS);
    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_3GP =
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_MPEG4, MediaFormat.MIMETYPE_VIDEO_H263,
                    MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_AUDIO_AAC,
                    MediaFormat.MIMETYPE_AUDIO_AMR_NB, MediaFormat.MIMETYPE_AUDIO_AMR_WB);
    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_OGG =
            Collections.singletonList(MediaFormat.MIMETYPE_AUDIO_OPUS);

    public static final float ACCEPTABLE_WIRELESS_TX_QUALITY = 20.0f;  // psnr in dB
    public static final float ACCEPTABLE_AV_SYNC_ERROR = 22.0f; // duration in ms

    /**
     * Selects encoder input color format in byte buffer mode. As of now ndk tests support only
     * 420p, 420sp. COLOR_FormatYUV420Flexible although can represent any form of yuv, it doesn't
     * work in ndk due to lack of AMediaCodec_GetInputImage()
     */
    public static int findByteBufferColorFormat(String encoder, String mediaType)
            throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(encoder);
        MediaCodecInfo.CodecCapabilities cap =
                codec.getCodecInfo().getCapabilitiesForType(mediaType);
        int colorFormat = -1;
        for (int c : cap.colorFormats) {
            if (c == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                    || c == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                Log.v(LOG_TAG, "selecting color format: " + c);
                colorFormat = c;
                break;
            }
        }
        codec.release();
        return colorFormat;
    }

    public static void muxOutput(String filePath, int muxerFormat, MediaFormat format,
            ByteBuffer buffer, ArrayList<MediaCodec.BufferInfo> infos) throws IOException {
        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(filePath, muxerFormat);
            int trackID = muxer.addTrack(format);
            muxer.start();
            for (MediaCodec.BufferInfo info : infos) {
                muxer.writeSampleData(trackID, buffer, info);
            }
            muxer.stop();
        } finally {
            if (muxer != null) muxer.release();
        }
    }

    public static boolean isMediaTypeContainerPairValid(String mediaType, int format) {
        boolean result = false;
        if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) {
            result = MEDIATYPE_LIST_FOR_TYPE_MP4.contains(mediaType)
                    || mediaType.startsWith("application/");
        } else if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM) {
            result = MEDIATYPE_LIST_FOR_TYPE_WEBM.contains(mediaType);
        } else if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP) {
            result = MEDIATYPE_LIST_FOR_TYPE_3GP.contains(mediaType);
        } else if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG) {
            result = MEDIATYPE_LIST_FOR_TYPE_OGG.contains(mediaType);
        }
        return result;
    }

    public static int getMuxerFormatForMediaType(String mediaType) {
        for (int muxFormat = MUXER_OUTPUT_FIRST; muxFormat <= MUXER_OUTPUT_LAST; muxFormat++) {
            if (isMediaTypeContainerPairValid(mediaType, muxFormat)) {
                return muxFormat;
            }
        }
        fail("no configured muxer support for " + mediaType);
        return MUXER_OUTPUT_LAST;
    }

    public static String getTempFilePath(String infix) throws IOException {
        return File.createTempFile("tmp" + infix, ".bin").getAbsolutePath();
    }

    public static void validateEncodedPSNR(String inpMediaType, String inpFile,
            String outMediaType, String outFile, boolean allowInpResize, boolean allowInpLoopBack,
            double perFramePsnrThreshold) throws IOException, InterruptedException {
        CompareStreams cs = new CompareStreams(inpMediaType, inpFile, outMediaType, outFile,
                allowInpResize, allowInpLoopBack);
        validateEncodedPSNR(cs.getFramesPSNR(), perFramePsnrThreshold);
        cs.cleanUp();
    }

    public static void validateEncodedPSNR(RawResource inp, String outMediaType, String outFile,
            boolean allowInpResize, boolean allowInpLoopBack, double perFramePsnrThreshold)
            throws IOException, InterruptedException {
        CompareStreams cs = new CompareStreams(inp, outMediaType, outFile, allowInpResize,
                allowInpLoopBack);
        validateEncodedPSNR(cs.getFramesPSNR(), perFramePsnrThreshold);
        cs.cleanUp();
    }

    public static void validateEncodedPSNR(@NonNull ArrayList<double[]> framesPSNR,
            double perFramePsnrThreshold) {
        StringBuilder msg = new StringBuilder();
        boolean isOk = true;
        for (int j = 0; j < framesPSNR.size(); j++) {
            double[] framePSNR = framesPSNR.get(j);
            // https://www.itu.int/dms_pub/itu-t/opb/tut/T-TUT-ASC-2020-HSTP1-PDF-E.pdf
            // weighted psnr (6 * psnrY + psnrU + psnrV) / 8;
            // stronger weighting of luma PSNR is to compensate for the fact that most of the
            // bits are used to describe luma information
            double weightPSNR = (6 * framePSNR[0] + framePSNR[1] + framePSNR[2]) / 8;
            if (weightPSNR < perFramePsnrThreshold) {
                msg.append(String.format(
                        "Frame %d - PSNR Y: %f, PSNR U: %f, PSNR V: %f, Weighted PSNR: %f < "
                                + "Threshold %f \n",
                        j, framePSNR[0], framePSNR[1], framePSNR[2], weightPSNR,
                        perFramePsnrThreshold));
                isOk = false;
            }
        }
        assertTrue("Encountered frames with PSNR less than configured threshold "
                + perFramePsnrThreshold + "dB \n" + msg, isOk);
    }

    public static String bitRateModeToString(int mode) {
        switch (mode) {
            case MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR:
                return "cbr";
            case MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR:
                return "vbr";
            case MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ:
                return "cq";
            case MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD:
                return "cbrwithfd";
            default:
                return "unknown";
        }
    }

    public static String rangeToString(int range) {
        switch (range) {
            case UNSPECIFIED:
                return "unspecified";
            case MediaFormat.COLOR_RANGE_FULL:
                return "full";
            case MediaFormat.COLOR_RANGE_LIMITED:
                return "limited";
            default:
                return "unknown";
        }
    }

    public static String colorStandardToString(int standard) {
        switch (standard) {
            case UNSPECIFIED:
                return "unspecified";
            case MediaFormat.COLOR_STANDARD_BT709:
                return "bt709";
            case MediaFormat.COLOR_STANDARD_BT601_PAL:
                return "bt601pal";
            case MediaFormat.COLOR_STANDARD_BT601_NTSC:
                return "bt601ntsc";
            case MediaFormat.COLOR_STANDARD_BT2020:
                return "bt2020";
            default:
                return "unknown";
        }
    }

    public static String colorTransferToString(int transfer) {
        switch (transfer) {
            case UNSPECIFIED:
                return "unspecified";
            case MediaFormat.COLOR_TRANSFER_LINEAR:
                return "linear";
            case MediaFormat.COLOR_TRANSFER_SDR_VIDEO:
                return "sdr";
            case MediaFormat.COLOR_TRANSFER_HLG:
                return "hlg";
            case MediaFormat.COLOR_TRANSFER_ST2084:
                return "st2084";
            default:
                return "unknown";
        }
    }

    public static String colorFormatToString(int colorFormat, int bitDepth) {
        switch (colorFormat) {
            case COLOR_FormatYUV420Flexible:
                return "yuv420flexible";
            case COLOR_FormatYUVP010:
                return "yuvp010";
            case COLOR_FormatSurface:
                if (bitDepth == 8) {
                    return "surfacergb888";
                } else if (bitDepth == 10) {
                    return "surfaceabgr2101010";
                } else {
                    return "unknown";
                }
            default:
                return "unknown";
        }
    }

    public static String audioEncodingToString(int enc) {
        switch (enc) {
            case AudioFormat.ENCODING_INVALID:
                return "invalid";
            case AudioFormat.ENCODING_PCM_16BIT:
                return "pcm16";
            case AudioFormat.ENCODING_PCM_FLOAT:
                return "pcmfloat";
            default:
                return "unknown";
        }
    }

    @Before
    public void setUpCodecEncoderTestBase() {
        assertTrue("Testing a mediaType that is neither audio nor video is not supported \n"
                + mTestConfig, mIsAudio || mIsVideo);
    }

    public void deleteMuxedFile() {
        if (mMuxedOutputFile != null) {
            File file = new File(mMuxedOutputFile);
            if (file.exists()) {
                assertTrue("unable to delete file " + mMuxedOutputFile, file.delete());
            }
            mMuxedOutputFile = null;
        }
    }

    @After
    public void tearDown() {
        if (mMuxer != null) {
            mMuxer.release();
            mMuxer = null;
        }
        deleteMuxedFile();
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mInputBufferReadOffset = 0;
        mNumBytesSubmitted = 0;
        mInputOffsetPts = 0;
        mInfoList.clear();
    }

    protected void setUpSource(String inpPath) throws IOException {
        Preconditions.assertTestFileExists(inpPath);
        try (FileInputStream fInp = new FileInputStream(inpPath)) {
            int size = (int) new File(inpPath).length();
            mInputData = new byte[size];
            fInp.read(mInputData, 0, size);
        }
    }

    protected void fillImage(Image image) {
        int format = image.getFormat();
        assertTrue("unexpected image format \n" + mTestConfig + mTestEnv,
                format == ImageFormat.YUV_420_888 || format == ImageFormat.YCBCR_P010);
        int bytesPerSample = (ImageFormat.getBitsPerPixel(format) * 2) / (8 * 3);  // YUV420
        assertEquals("Invalid bytes per sample \n" + mTestConfig + mTestEnv, bytesPerSample,
                mActiveRawRes.mBytesPerSample);

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        int offset = mInputBufferReadOffset;
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();
            int width = imageWidth;
            int height = imageHeight;
            int tileWidth = mActiveRawRes.mWidth;
            int tileHeight = mActiveRawRes.mHeight;
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (i != 0) {
                width = imageWidth / 2;
                height = imageHeight / 2;
                tileWidth = mActiveRawRes.mWidth / 2;
                tileHeight = mActiveRawRes.mHeight / 2;
            }
            if (pixelStride == bytesPerSample) {
                if (width == rowStride && width == tileWidth && height == tileHeight) {
                    buf.put(mInputData, offset, width * height * bytesPerSample);
                } else {
                    for (int z = 0; z < height; z += tileHeight) {
                        int rowsToCopy = Math.min(height - z, tileHeight);
                        for (int y = 0; y < rowsToCopy; y++) {
                            for (int x = 0; x < width; x += tileWidth) {
                                int colsToCopy = Math.min(width - x, tileWidth);
                                buf.position((z + y) * rowStride + x * bytesPerSample);
                                buf.put(mInputData, offset + y * tileWidth * bytesPerSample,
                                        colsToCopy * bytesPerSample);
                            }
                        }
                    }
                }
            } else {
                // do it pixel-by-pixel
                for (int z = 0; z < height; z += tileHeight) {
                    int rowsToCopy = Math.min(height - z, tileHeight);
                    for (int y = 0; y < rowsToCopy; y++) {
                        int lineOffset = (z + y) * rowStride;
                        for (int x = 0; x < width; x += tileWidth) {
                            int colsToCopy = Math.min(width - x, tileWidth);
                            for (int w = 0; w < colsToCopy; w++) {
                                for (int bytePos = 0; bytePos < bytesPerSample; bytePos++) {
                                    buf.position(lineOffset + (x + w) * pixelStride + bytePos);
                                    buf.put(mInputData[offset + y * tileWidth * bytesPerSample
                                            + w * bytesPerSample + bytePos]);
                                }
                            }
                        }
                    }
                }
            }
            offset += tileWidth * tileHeight * bytesPerSample;
        }
    }

    void fillByteBuffer(ByteBuffer inputBuffer) {
        int offset = 0, frmOffset = mInputBufferReadOffset;
        for (int plane = 0; plane < 3; plane++) {
            int width = mActiveEncCfg.mWidth;
            int height = mActiveEncCfg.mHeight;
            int tileWidth = mActiveRawRes.mWidth;
            int tileHeight = mActiveRawRes.mHeight;
            if (plane != 0) {
                width = mActiveEncCfg.mWidth / 2;
                height = mActiveEncCfg.mHeight / 2;
                tileWidth = mActiveRawRes.mWidth / 2;
                tileHeight = mActiveRawRes.mHeight / 2;
            }
            for (int k = 0; k < height; k += tileHeight) {
                int rowsToCopy = Math.min(height - k, tileHeight);
                for (int j = 0; j < rowsToCopy; j++) {
                    for (int i = 0; i < width; i += tileWidth) {
                        int colsToCopy = Math.min(width - i, tileWidth);
                        inputBuffer.position(
                                offset + (k + j) * width * mActiveRawRes.mBytesPerSample
                                        + i * mActiveRawRes.mBytesPerSample);
                        inputBuffer.put(mInputData,
                                frmOffset + j * tileWidth * mActiveRawRes.mBytesPerSample,
                                colsToCopy * mActiveRawRes.mBytesPerSample);
                    }
                }
            }
            offset += width * height * mActiveRawRes.mBytesPerSample;
            frmOffset += tileWidth * tileHeight * mActiveRawRes.mBytesPerSample;
        }
    }

    protected void enqueueInput(int bufferIndex) {
        if (mIsLoopBack && mInputBufferReadOffset >= mInputData.length) {
            mInputBufferReadOffset = 0;
        }
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        if (mInputBufferReadOffset >= mInputData.length) {
            enqueueEOS(bufferIndex);
        } else {
            int size;
            int flags = 0;
            long pts = mInputOffsetPts;
            if (mIsAudio) {
                pts += mNumBytesSubmitted * 1000000L / ((long) mActiveRawRes.mBytesPerSample
                        * mActiveEncCfg.mChannelCount * mActiveEncCfg.mSampleRate);
                size = Math.min(inputBuffer.capacity(), mInputData.length - mInputBufferReadOffset);
                assertEquals(0, size % ((long) mActiveRawRes.mBytesPerSample
                        * mActiveEncCfg.mChannelCount));
                inputBuffer.put(mInputData, mInputBufferReadOffset, size);
                if (mSignalEOSWithLastFrame) {
                    if (mIsLoopBack ? (mInputCount + 1 >= mLoopBackFrameLimit) :
                            (mInputBufferReadOffset + size >= mInputData.length)) {
                        flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        mSawInputEOS = true;
                    }
                }
                mInputBufferReadOffset += size;
            } else {
                pts += mInputCount * 1000000L / mActiveEncCfg.mFrameRate;
                size = mActiveRawRes.mBytesPerSample * mActiveEncCfg.mWidth * mActiveEncCfg.mHeight
                        * 3 / 2;
                int frmSize = mActiveRawRes.mBytesPerSample * mActiveRawRes.mWidth
                        * mActiveRawRes.mHeight * 3 / 2;
                if (mInputBufferReadOffset + frmSize > mInputData.length) {
                    fail("received partial frame to encode \n" + mTestConfig + mTestEnv);
                } else {
                    Image img = mCodec.getInputImage(bufferIndex);
                    assertNotNull("getInputImage() expected to return non-null for video \n"
                            + mTestConfig + mTestEnv, img);
                    fillImage(img);
                }
                if (mSignalEOSWithLastFrame) {
                    if (mIsLoopBack ? (mInputCount + 1 >= mLoopBackFrameLimit) :
                            (mInputBufferReadOffset + frmSize >= mInputData.length)) {
                        flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        mSawInputEOS = true;
                    }
                }
                mInputBufferReadOffset += frmSize;
            }
            mNumBytesSubmitted += size;
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts
                        + " flags: " + flags);
            }
            mCodec.queueInputBuffer(bufferIndex, 0, size, pts, flags);
            mOutputBuff.saveInPTS(pts);
            mInputCount++;
        }
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: "
                    + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0) {
            ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
            if (mSaveToMem) {
                MediaCodec.BufferInfo copy = new MediaCodec.BufferInfo();
                copy.set(mOutputBuff.getOutStreamSize(), info.size, info.presentationTimeUs,
                        info.flags);
                mInfoList.add(copy);

                mOutputBuff.checksum(buf, info);
                mOutputBuff.saveToMemory(buf, info);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mOutputBuff.saveOutPTS(info.presentationTimeUs);
                mOutputCount++;
            }
            if (mMuxer != null) {
                if (mTrackID == -1) {
                    mTrackID = mMuxer.addTrack(mCodec.getOutputFormat());
                    mMuxer.start();
                }
                mMuxer.writeSampleData(mTrackID, buf, info);
            }
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    @Override
    protected void doWork(int frameLimit) throws IOException, InterruptedException {
        mLoopBackFrameLimit = frameLimit;
        if (mMuxOutput) {
            int muxerFormat = getMuxerFormatForMediaType(mMediaType);
            mMuxedOutputFile = getTempFilePath((mActiveEncCfg.mInputBitDepth == 10) ? "10bit" : "");
            mMuxer = new MediaMuxer(mMuxedOutputFile, muxerFormat);
        }
        super.doWork(frameLimit);
    }

    @Override
    protected void waitForAllOutputs() throws InterruptedException {
        super.waitForAllOutputs();
        if (mMuxOutput) {
            if (mTrackID != -1) {
                mMuxer.stop();
                mTrackID = -1;
            }
            if (mMuxer != null) {
                mMuxer.release();
                mMuxer = null;
            }
        }
    }

    @Override
    protected PersistableBundle validateMetrics(String codec, MediaFormat format) {
        PersistableBundle metrics = super.validateMetrics(codec, format);
        assertEquals("error! metrics#MetricsConstants.MIME_TYPE is not as expected \n" + mTestConfig
                + mTestEnv, metrics.getString(MediaCodec.MetricsConstants.MIME_TYPE), mMediaType);
        assertEquals("error! metrics#MetricsConstants.ENCODER is not as expected \n" + mTestConfig
                + mTestEnv, 1, metrics.getInt(MediaCodec.MetricsConstants.ENCODER));
        return metrics;
    }

    public void encodeToMemory(String encoder, EncoderConfigParams cfg, RawResource res,
            int frameLimit, boolean saveToMem, boolean muxOutput)
            throws IOException, InterruptedException {
        mSaveToMem = saveToMem;
        mMuxOutput = muxOutput;
        mOutputBuff = new OutputManager();
        mInfoList.clear();
        mActiveEncCfg = cfg;
        mActiveRawRes = res;
        mCodec = MediaCodec.createByCodecName(encoder);
        setUpSource(mActiveRawRes.mFileName);
        configureCodec(mActiveEncCfg.getFormat(), false, true, true);
        mCodec.start();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mActiveRawRes = null;
        mActiveEncCfg = null;
        mSaveToMem = false;
        mMuxOutput = false;
    }

    public void setLoopBack(boolean loopBack) {
        mIsLoopBack = loopBack;
    }

    public String getMuxedOutputFilePath() {
        return mMuxedOutputFile;
    }

    protected void validateTestState() {
        super.validateTestState();
        if ((mIsAudio || (mIsVideo && mActiveEncCfg.mMaxBFrames == 0))
                && !mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts)) {
            fail("Output timestamps are not strictly increasing \n" + mTestConfig + mTestEnv
                    + mOutputBuff.getErrMsg());
        }
        if (mIsVideo) {
            if (!mOutputBuff.isOutPtsListIdenticalToInpPtsList((mActiveEncCfg.mMaxBFrames != 0))) {
                fail("Input pts list and Output pts list are not identical \n" + mTestConfig
                        + mTestEnv + mOutputBuff.getErrMsg());
            }
        }
    }
}
