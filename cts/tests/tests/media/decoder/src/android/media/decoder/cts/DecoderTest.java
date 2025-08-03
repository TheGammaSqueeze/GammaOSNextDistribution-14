/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel31;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel32;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel4;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel42;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31;
import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41;
import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.ImageFormat;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.CodecState;
import android.media.cts.MediaCodecTunneledPlayer;
import android.media.cts.MediaCodecWrapper;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.media.cts.NdkMediaCodec;
import android.media.cts.SdkMediaCodec;
import android.media.cts.TestUtils;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.DynamicConfigDeviceSide;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.Preconditions;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.zip.CRC32;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "There should be no instant apps specific behavior related to decoders")
@RunWith(AndroidJUnit4.class)
public class DecoderTest extends MediaTestBase {
    private static final String TAG = "DecoderTest";
    private static final String REPORT_LOG_NAME = "CtsMediaDecoderTestCases";

    public static final boolean WAS_LAUNCHED_ON_S_OR_LATER =
            SystemProperties.getInt("ro.product.first_api_level",
                                    Build.VERSION_CODES.CUR_DEVELOPMENT)
                    >= Build.VERSION_CODES.S;

    private static boolean IS_AT_LEAST_R = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    private static boolean IS_BEFORE_S = ApiLevelUtil.isBefore(Build.VERSION_CODES.S);
    private static boolean IS_AFTER_T = ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU);

    private static final int RESET_MODE_NONE = 0;
    private static final int RESET_MODE_RECONFIGURE = 1;
    private static final int RESET_MODE_FLUSH = 2;
    private static final int RESET_MODE_EOS_FLUSH = 3;

    private static final String[] CSD_KEYS = new String[] { "csd-0", "csd-1" };

    private static final int CONFIG_MODE_NONE = 0;
    private static final int CONFIG_MODE_QUEUE = 1;

    public static final int CODEC_ALL = 0; // All codecs must support
    public static final int CODEC_ANY = 1; // At least one codec must support
    public static final int CODEC_DEFAULT = 2; // Default codec must support
    public static final int CODEC_OPTIONAL = 3; // Codec support is optional

    short[] mMasterBuffer;
    static final String mInpPrefix = WorkDir.getMediaDirString();

    private MediaCodecTunneledPlayer mMediaCodecPlayer;
    private static final int SLEEP_TIME_MS = 1000;
    private static final long PLAY_TIME_MS = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);

    private static final String MODULE_NAME = "CtsMediaDecoderTestCases";
    private DynamicConfigDeviceSide dynamicConfig;

    static final Map<String, String> sDefaultDecoders = new HashMap<>();

    protected static AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        File inpFile = new File(mInpPrefix + res);
        Preconditions.assertTestFileExists(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();

        // read primary file into memory
        AssetFileDescriptor masterFd = getAssetFileDescriptorFor("sinesweepraw.raw");
        long masterLength = masterFd.getLength();
        mMasterBuffer = new short[(int) (masterLength / 2)];
        InputStream is = masterFd.createInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        for (int i = 0; i < mMasterBuffer.length; i++) {
            int lo = bis.read();
            int hi = bis.read();
            if (hi >= 128) {
                hi -= 256;
            }
            int sample = hi * 256 + lo;
            mMasterBuffer[i] = (short) sample;
        }
        bis.close();
        masterFd.close();

        dynamicConfig = new DynamicConfigDeviceSide(MODULE_NAME);
    }

    @After
    @Override
    public void tearDown() {
        // ensure MediaCodecPlayer resources are released even if an exception is thrown.
        if (mMediaCodecPlayer != null) {
            mMediaCodecPlayer.reset();
            mMediaCodecPlayer = null;
        }
        super.tearDown();
    }

    static boolean isDefaultCodec(String codecName, String mime) throws IOException {
        if (sDefaultDecoders.containsKey(mime)) {
            return sDefaultDecoders.get(mime).equalsIgnoreCase(codecName);
        }
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        boolean isDefault = codec.getName().equalsIgnoreCase(codecName);
        sDefaultDecoders.put(mime, codec.getName());
        codec.release();

        return isDefault;
    }

    // TODO: add similar tests for other audio and video formats
    @Test
    public void testBug11696552() throws Exception {
        MediaCodec mMediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        MediaFormat mFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, 48000 /* frequency */, 2 /* channels */);
        mFormat.setByteBuffer("csd-0", ByteBuffer.wrap( new byte [] {0x13, 0x10} ));
        mFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
        mMediaCodec.configure(mFormat, null, null, 0);
        mMediaCodec.start();
        int index = mMediaCodec.dequeueInputBuffer(250000);
        mMediaCodec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        mMediaCodec.dequeueOutputBuffer(info, 250000);
    }

    // The allowed errors in the following tests are the actual maximum measured
    // errors with the standard decoders, plus 10%.
    // This should allow for some variation in decoders, while still detecting
    // phase and delay errors, channel swap, etc.
    @Test
    public void testDecodeMp3Lame() throws Exception {
        decode("sinesweepmp3lame.mp3", 804.f);
        testTimeStampOrdering("sinesweepmp3lame.mp3");
    }
    @Test
    public void testDecodeMp3Smpb() throws Exception {
        decode("sinesweepmp3smpb.mp3", 413.f);
        testTimeStampOrdering("sinesweepmp3smpb.mp3");
    }
    @Test
    public void testDecodeM4a() throws Exception {
        decode("sinesweepm4a.m4a", 124.f);
        testTimeStampOrdering("sinesweepm4a.m4a");
    }
    @Test
    public void testDecodeOgg() throws Exception {
        decode("sinesweepogg.ogg", 168.f);
        testTimeStampOrdering("sinesweepogg.ogg");
    }
    @Test
    public void testDecodeOggMkv() throws Exception {
        decode("sinesweepoggmkv.mkv", 168.f);
        testTimeStampOrdering("sinesweepoggmkv.mkv");
    }
    @Test
    public void testDecodeOggMp4() throws Exception {
        decode("sinesweepoggmp4.mp4", 168.f);
        testTimeStampOrdering("sinesweepoggmp4.mp4");
    }
    @Test
    public void testDecodeWav() throws Exception {
        decode("sinesweepwav.wav", 0.0f);
        testTimeStampOrdering("sinesweepwav.wav");
    }
    @Test
    public void testDecodeWav24() throws Exception {
        decode("sinesweepwav24.wav", 0.0f);
        testTimeStampOrdering("sinesweepwav24.wav");
    }
    @Test
    public void testDecodeFlacMkv() throws Exception {
        decode("sinesweepflacmkv.mkv", 0.0f);
        testTimeStampOrdering("sinesweepflacmkv.mkv");
    }
    @Test
    public void testDecodeFlac() throws Exception {
        decode("sinesweepflac.flac", 0.0f);
        testTimeStampOrdering("sinesweepflac.flac");
    }
    @Test
    public void testDecodeFlac24() throws Exception {
        decode("sinesweepflac24.flac", 0.0f);
        testTimeStampOrdering("sinesweepflac24.flac");
    }
    @Test
    public void testDecodeFlacMp4() throws Exception {
        decode("sinesweepflacmp4.mp4", 0.0f);
        testTimeStampOrdering("sinesweepflacmp4.mp4");
    }

    @Test
    public void testDecodeMonoMp3() throws Exception {
        monoTest("monotestmp3.mp3", 44100);
        testTimeStampOrdering("monotestmp3.mp3");
    }

    @Test
    public void testDecodeMonoM4a() throws Exception {
        monoTest("monotestm4a.m4a", 44100);
        testTimeStampOrdering("monotestm4a.m4a");
    }

    @Test
    public void testDecodeMonoOgg() throws Exception {
        monoTest("monotestogg.ogg", 44100);
        testTimeStampOrdering("monotestogg.ogg");
    }
    @Test
    public void testDecodeMonoOggMkv() throws Exception {
        monoTest("monotestoggmkv.mkv", 44100);
        testTimeStampOrdering("monotestoggmkv.mkv");
    }
    @Test
    public void testDecodeMonoOggMp4() throws Exception {
        monoTest("monotestoggmp4.mp4", 44100);
        testTimeStampOrdering("monotestoggmp4.mp4");
    }

    @Test
    public void testDecodeMonoGsm() throws Exception {
        String fileName = "monotestgsm.wav";
        Preconditions.assertTestFileExists(mInpPrefix + fileName);
        if (MediaUtils.hasCodecsForResource(mInpPrefix + fileName)) {
            monoTest(fileName, 8000);
            testTimeStampOrdering(fileName);
        } else {
            MediaUtils.skipTest("not mandatory");
        }
    }

    @Test
    public void testDecodeAacTs() throws Exception {
        testTimeStampOrdering("sinesweeptsaac.m4a");
    }

    @Test
    public void testDecodeVorbis() throws Exception {
        testTimeStampOrdering("sinesweepvorbis.mkv");
    }
    @Test
    public void testDecodeVorbisMp4() throws Exception {
        testTimeStampOrdering("sinesweepvorbismp4.mp4");
    }

    @Test
    public void testDecodeOpus() throws Exception {
        testTimeStampOrdering("sinesweepopus.mkv");
    }
    @Test
    public void testDecodeOpusMp4() throws Exception {
        testTimeStampOrdering("sinesweepopusmp4.mp4");
    }

    @CddTest(requirement="5.1.3")
    @Test
    public void testDecodeG711ChannelsAndRates() throws Exception {
        String[] mimetypes = { MediaFormat.MIMETYPE_AUDIO_G711_ALAW,
                               MediaFormat.MIMETYPE_AUDIO_G711_MLAW };
        int[] sampleRates = { 8000 };
        int[] channelMasks = { AudioFormat.CHANNEL_OUT_MONO,
                               AudioFormat.CHANNEL_OUT_STEREO,
                               AudioFormat.CHANNEL_OUT_5POINT1 };

        verifyChannelsAndRates(mimetypes, sampleRates, channelMasks);
    }

    @CddTest(requirement="5.1.3")
    @Test
    public void testDecodeOpusChannelsAndRates() throws Exception {
        String[] mimetypes = { MediaFormat.MIMETYPE_AUDIO_OPUS };
        int[] sampleRates = { 8000, 12000, 16000, 24000, 48000 };
        int[] channelMasks = { AudioFormat.CHANNEL_OUT_MONO,
                               AudioFormat.CHANNEL_OUT_STEREO,
                               AudioFormat.CHANNEL_OUT_5POINT1 };

        verifyChannelsAndRates(mimetypes, sampleRates, channelMasks);
    }

    private void verifyChannelsAndRates(String[] mimetypes, int[] sampleRates,
                                       int[] channelMasks) throws Exception {

        if (!MediaUtils.check(IS_AT_LEAST_R, "test invalid before Android 11")) return;

        for (String mimetype : mimetypes) {
            // ensure we find a codec for all listed mime/channel/rate combinations
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (int sampleRate : sampleRates) {
                for (int channelMask : channelMasks) {
                    int channelCount = AudioFormat.channelCountFromOutChannelMask(channelMask);
                    MediaFormat desiredFormat = MediaFormat.createAudioFormat(
                                mimetype,
                                sampleRate,
                                channelCount);
                    String codecname = mcl.findDecoderForFormat(desiredFormat);

                    assertNotNull("findDecoderForFormat() failed for mime=" + mimetype
                                    + " sampleRate=" + sampleRate + " channelCount=" + channelCount,
                            codecname);
                }
            }

            // check all mime-matching codecs successfully configure the desired rate/channels
            ArrayList<MediaCodecInfo> codecInfoList = getDecoderMediaCodecInfoList(mimetype);
            if (codecInfoList == null) {
                continue;
            }
            for (MediaCodecInfo codecInfo : codecInfoList) {
                MediaCodec codec = MediaCodec.createByCodecName(codecInfo.getName());
                for (int sampleRate : sampleRates) {
                    for (int channelMask : channelMasks) {
                        int channelCount = AudioFormat.channelCountFromOutChannelMask(channelMask);

                        codec.reset();
                        MediaFormat desiredFormat = MediaFormat.createAudioFormat(
                                mimetype,
                                sampleRate,
                                channelCount);
                        codec.configure(desiredFormat, null, null, 0);
                        codec.start();

                        Log.d(TAG, "codec: " + codecInfo.getName() +
                                " sample rate: " + sampleRate +
                                " channelcount:" + channelCount);

                        MediaFormat actual = codec.getInputFormat();
                        int actualChannels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT, -1);
                        int actualSampleRate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE, -1);
                        assertTrue("channels: configured " + actualChannels +
                                   " != desired " + channelCount, actualChannels == channelCount);
                        assertTrue("sample rate: configured " + actualSampleRate +
                                   " != desired " + sampleRate, actualSampleRate == sampleRate);
                    }
                }
                codec.release();
            }
        }
    }

    private ArrayList<MediaCodecInfo> getDecoderMediaCodecInfoList(String mimeType) {
        MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        ArrayList<MediaCodecInfo> decoderInfos = new ArrayList<MediaCodecInfo>();
        for (MediaCodecInfo codecInfo : mediaCodecList.getCodecInfos()) {
            if (!codecInfo.isEncoder() && isMimeTypeSupported(codecInfo, mimeType)) {
                decoderInfos.add(codecInfo);
            }
        }
        return decoderInfos;
    }

    private boolean isMimeTypeSupported(MediaCodecInfo codecInfo, String mimeType) {
        for (String type : codecInfo.getSupportedTypes()) {
            if (type.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testDecode51M4a() throws Exception {
        for (String codecName : codecsFor("sinesweep51m4a.m4a")) {
            decodeToMemory(codecName, "sinesweep51m4a.m4a", RESET_MODE_NONE, CONFIG_MODE_NONE, -1,
                    null);
        }
    }

    private void testTimeStampOrdering(final String res) throws Exception {
        for (String codecName : codecsFor(res)) {
            List<Long> timestamps = new ArrayList<Long>();
            decodeToMemory(codecName, res, RESET_MODE_NONE, CONFIG_MODE_NONE, -1, timestamps);
            Long lastTime = Long.MIN_VALUE;
            for (int i = 0; i < timestamps.size(); i++) {
                Long thisTime = timestamps.get(i);
                assertTrue(codecName + ": timetravel occurred: " + lastTime + " > " + thisTime,
                       thisTime >= lastTime);
                lastTime = thisTime;
            }
        }
    }

    @Test
    public void testTrackSelection() throws Exception {
        testTrackSelection("video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz.mp4");
        testTrackSelection(
                "video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz_fragmented.mp4");
        testTrackSelection(
                "video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz_dash.mp4");
    }

    @Test
    public void testTrackSelectionMkv() throws Exception {
        Log.d(TAG, "testTrackSelectionMkv!!!!!! ");
        testTrackSelection("mkv_avc_adpcm_ima.mkv");
        Log.d(TAG, "mkv_avc_adpcm_ima finished!!!!!! ");
        testTrackSelection("mkv_avc_adpcm_ms.mkv");
        Log.d(TAG, "mkv_avc_adpcm_ms finished!!!!!! ");
        testTrackSelection("mkv_avc_wma.mkv");
        Log.d(TAG, "mkv_avc_wma finished!!!!!! ");
        testTrackSelection("mkv_avc_mp2.mkv");
        Log.d(TAG, "mkv_avc_mp2 finished!!!!!! ");
    }

    @Test
    public void testBFrames() throws Exception {
        int testsRun =
            testBFrames("video_h264_main_b_frames.mp4") +
            testBFrames("video_h264_main_b_frames_frag.mp4");
        if (testsRun == 0) {
            MediaUtils.skipTest("no codec found");
        }
    }

    public int testBFrames(final String res) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        Preconditions.assertTestFileExists(mInpPrefix + res);
        ex.setDataSource(mInpPrefix + res);
        MediaFormat format = ex.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        assertTrue("not a video track. Wrong test file?", mime.startsWith("video/"));
        if (!MediaUtils.canDecode(format)) {
            ex.release();
            return 0; // skip
        }
        MediaCodec dec = MediaCodec.createDecoderByType(mime);
        Surface s = getActivity().getSurfaceHolder().getSurface();
        dec.configure(format, s, null, 0);
        dec.start();
        ByteBuffer[] buf = dec.getInputBuffers();
        ex.selectTrack(0);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long lastPresentationTimeUsFromExtractor = -1;
        long lastPresentationTimeUsFromDecoder = -1;
        boolean inputoutoforder = false;
        while(true) {
            int flags = ex.getSampleFlags();
            long time = ex.getSampleTime();
            if (time >= 0 && time < lastPresentationTimeUsFromExtractor) {
                inputoutoforder = true;
            }
            lastPresentationTimeUsFromExtractor = time;
            int bufidx = dec.dequeueInputBuffer(5000);
            if (bufidx >= 0) {
                int n = ex.readSampleData(buf[bufidx], 0);
                if (n < 0) {
                    flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    time = 0;
                    n = 0;
                }
                dec.queueInputBuffer(bufidx, 0, n, time, flags);
                ex.advance();
            }
            int status = dec.dequeueOutputBuffer(info, 5000);
            if (status >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
                assertTrue("out of order timestamp from decoder",
                        info.presentationTimeUs > lastPresentationTimeUsFromDecoder);
                dec.releaseOutputBuffer(status, true);
                lastPresentationTimeUsFromDecoder = info.presentationTimeUs;
            }
        }
        assertTrue("extractor timestamps were ordered, wrong test file?", inputoutoforder);
        dec.release();
        ex.release();
        return 1;
      }

    /**
     * Test ColorAspects of all the AVC decoders. Decoders should handle
     * the colors aspects presented in both the mp4 atom 'colr' and VUI
     * in the bitstream correctly. The following table lists the color
     * aspects contained in the color box and VUI for the test stream.
     * P = primaries, T = transfer, M = coeffs, R = range. '-' means
     * empty value.
     *                                      |     colr     |    VUI
     * -------------------------------------------------------------------
     *         File Name                    |  P  T  M  R  |  P  T  M  R
     * -------------------------------------------------------------------
     *  color_176x144_bt709_lr_sdr_h264     |  1  1  1  0  |  -  -  -  -
     *  color_176x144_bt601_625_fr_sdr_h264 |  1  6  6  0  |  5  2  2  1
     *  color_176x144_bt601_525_lr_sdr_h264 |  6  5  4  0  |  2  6  6  0
     *  color_176x144_srgb_lr_sdr_h264      |  2  0  2  1  |  1  13 1  0
     */
    @Test
    public void testH264ColorAspects() throws Exception {
        testColorAspects(
                "color_176x144_bt709_lr_sdr_h264.mp4", 1 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT709,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        testColorAspects(
                "color_176x144_bt601_625_fr_sdr_h264.mp4", 2 /* testId */,
                MediaFormat.COLOR_RANGE_FULL, MediaFormat.COLOR_STANDARD_BT601_PAL,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        testColorAspects(
                "color_176x144_bt601_525_lr_sdr_h264.mp4", 3 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT601_NTSC,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        testColorAspects(
                "color_176x144_srgb_lr_sdr_h264.mp4", 4 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT709,
                2 /* MediaFormat.COLOR_TRANSFER_SRGB */);
    }

    /**
     * Test ColorAspects of all the HEVC decoders. Decoders should handle
     * the colors aspects presented in both the mp4 atom 'colr' and VUI
     * in the bitstream correctly. The following table lists the color
     * aspects contained in the color box and VUI for the test stream.
     * P = primaries, T = transfer, M = coeffs, R = range. '-' means
     * empty value.
     *                                      |     colr     |    VUI
     * -------------------------------------------------------------------
     *         File Name                    |  P  T  M  R  |  P  T  M  R
     * -------------------------------------------------------------------
     *  color_176x144_bt709_lr_sdr_h265     |  1  1  1  0  |  -  -  -  -
     *  color_176x144_bt601_625_fr_sdr_h265 |  1  6  6  0  |  5  2  2  1
     *  color_176x144_bt601_525_lr_sdr_h265 |  6  5  4  0  |  2  6  6  0
     *  color_176x144_srgb_lr_sdr_h265      |  2  0  2  1  |  1  13 1  0
     */
    @Test
    public void testH265ColorAspects() throws Exception {
        testColorAspects(
                "color_176x144_bt709_lr_sdr_h265.mp4", 1 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT709,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        testColorAspects(
                "color_176x144_bt601_625_fr_sdr_h265.mp4", 2 /* testId */,
                MediaFormat.COLOR_RANGE_FULL, MediaFormat.COLOR_STANDARD_BT601_PAL,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        testColorAspects(
                "color_176x144_bt601_525_lr_sdr_h265.mp4", 3 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT601_NTSC,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        testColorAspects(
                "color_176x144_srgb_lr_sdr_h265.mp4", 4 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT709,
                2 /* MediaFormat.COLOR_TRANSFER_SRGB */);
        // Test the main10 streams with surface as the decoder might
        // support opaque buffers only.
        testColorAspects(
                "color_176x144_bt2020_lr_smpte2084_h265.mp4", 5 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT2020,
                MediaFormat.COLOR_TRANSFER_ST2084,
                getActivity().getSurfaceHolder().getSurface());
        testColorAspects(
                "color_176x144_bt2020_lr_hlg_h265.mp4", 6 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT2020,
                MediaFormat.COLOR_TRANSFER_HLG,
                getActivity().getSurfaceHolder().getSurface());
    }

    /**
     * Test ColorAspects of all the MPEG2 decoders if avaiable. Decoders should
     * handle the colors aspects presented in both the mp4 atom 'colr' and Sequence
     * in the bitstream correctly. The following table lists the color aspects
     * contained in the color box and SeqInfo for the test stream.
     * P = primaries, T = transfer, M = coeffs, R = range. '-' means
     * empty value.
     *                                       |     colr     |    SeqInfo
     * -------------------------------------------------------------------
     *         File Name                     |  P  T  M  R  |  P  T  M  R
     * -------------------------------------------------------------------
     *  color_176x144_bt709_lr_sdr_mpeg2     |  1  1  1  0  |  -  -  -  -
     *  color_176x144_bt601_625_lr_sdr_mpeg2 |  1  6  6  0  |  5  2  2  0
     *  color_176x144_bt601_525_lr_sdr_mpeg2 |  6  5  4  0  |  2  6  6  0
     *  color_176x144_srgb_lr_sdr_mpeg2      |  2  0  2  0  |  1  13 1  0
     */
    @Test
    public void testMPEG2ColorAspectsTV() throws Exception {
        testColorAspects(
                "color_176x144_bt709_lr_sdr_mpeg2.mp4", 1 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT709,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        testColorAspects(
                "color_176x144_bt601_625_lr_sdr_mpeg2.mp4", 2 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT601_PAL,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        testColorAspects(
                "color_176x144_bt601_525_lr_sdr_mpeg2.mp4", 3 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT601_NTSC,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        testColorAspects(
                "color_176x144_srgb_lr_sdr_mpeg2.mp4", 4 /* testId */,
                MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT709,
                2 /* MediaFormat.COLOR_TRANSFER_SRGB */);
    }

    private void testColorAspects(
            final String res, int testId, int expectRange, int expectStandard, int expectTransfer)
            throws Exception {
        testColorAspects(
                res, testId, expectRange, expectStandard, expectTransfer, null /*surface*/);
    }

    private void testColorAspects(
            final String res, int testId, int expectRange, int expectStandard, int expectTransfer,
            Surface surface) throws Exception {
        Preconditions.assertTestFileExists(mInpPrefix + res);
        MediaFormat format = MediaUtils.getTrackFormatForResource(mInpPrefix + res, "video");
        MediaFormat mimeFormat = new MediaFormat();
        mimeFormat.setString(MediaFormat.KEY_MIME, format.getString(MediaFormat.KEY_MIME));

        for (String decoderName: MediaUtils.getDecoderNames(mimeFormat)) {
            if (!MediaUtils.supports(decoderName, format)) {
                MediaUtils.skipTest(decoderName + " cannot play resource " + mInpPrefix + res);
            } else {
                testColorAspects(decoderName, res, testId,
                        expectRange, expectStandard, expectTransfer, surface);
            }
        }
    }

    private void testColorAspects(
            String decoderName, final String res, int testId, int expectRange,
            int expectStandard, int expectTransfer, Surface surface) throws Exception {
        Preconditions.assertTestFileExists(mInpPrefix + res);
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(mInpPrefix + res);
        MediaFormat format = ex.getTrackFormat(0);
        MediaCodec dec = MediaCodec.createByCodecName(decoderName);
        dec.configure(format, surface, null, 0);
        dec.start();
        ByteBuffer[] buf = dec.getInputBuffers();
        ex.selectTrack(0);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean getOutputFormat = false;
        boolean rangeMatch = false;
        boolean colorMatch = false;
        boolean transferMatch = false;
        int colorRange = 0;
        int colorStandard = 0;
        int colorTransfer = 0;

        while (true) {
            if (!sawInputEOS) {
                int flags = ex.getSampleFlags();
                long time = ex.getSampleTime();
                int bufidx = dec.dequeueInputBuffer(200 * 1000);
                if (bufidx >= 0) {
                    int n = ex.readSampleData(buf[bufidx], 0);
                    if (n < 0) {
                        flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        sawInputEOS = true;
                        n = 0;
                    }
                    dec.queueInputBuffer(bufidx, 0, n, time, flags);
                    ex.advance();
                } else {
                    assertEquals(
                            "codec.dequeueInputBuffer() unrecognized return value: " + bufidx,
                            MediaCodec.INFO_TRY_AGAIN_LATER, bufidx);
                }
            }

            int status = dec.dequeueOutputBuffer(info, sawInputEOS ? 3000 * 1000 : 100 * 1000);
            if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat fmt = dec.getOutputFormat();
                colorRange = fmt.containsKey("color-range") ? fmt.getInteger("color-range") : 0;
                colorStandard = fmt.containsKey("color-standard") ? fmt.getInteger("color-standard") : 0;
                colorTransfer = fmt.containsKey("color-transfer") ? fmt.getInteger("color-transfer") : 0;
                rangeMatch = colorRange == expectRange;
                colorMatch = colorStandard == expectStandard;
                transferMatch = colorTransfer == expectTransfer;
                getOutputFormat = true;
                // Test only needs to check the color format in the first format changed event.
                break;
            } else if (status >= 0) {
                // Test should get at least one format changed event before getting first frame.
                assertTrue(getOutputFormat);
                break;
            } else {
                assertFalse(
                        "codec.dequeueOutputBuffer() timeout after seeing input EOS",
                        status == MediaCodec.INFO_TRY_AGAIN_LATER && sawInputEOS);
            }
        }

        String reportName = decoderName + "_colorAspectsTest Test " + testId +
                " (Get R: " + colorRange + " S: " + colorStandard + " T: " + colorTransfer + ")" +
                " (Expect R: " + expectRange + " S: " + expectStandard + " T: " + expectTransfer + ")";
        Log.d(TAG, reportName);

        DeviceReportLog log = new DeviceReportLog("CtsMediaDecoderTestCases", "color_aspects_test");
        log.addValue("decoder_name", decoderName, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("test_id", testId, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValues(
                "rst_actual", new int[] { colorRange, colorStandard, colorTransfer },
                ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValues(
                "rst_expected", new int[] { expectRange, expectStandard, expectTransfer },
                ResultType.NEUTRAL, ResultUnit.NONE);

        if (rangeMatch && colorMatch && transferMatch) {
            log.setSummary("result", 1, ResultType.HIGHER_BETTER, ResultUnit.COUNT);
        } else {
            log.setSummary("result", 0, ResultType.HIGHER_BETTER, ResultUnit.COUNT);
        }
        log.submit(getInstrumentation());

        assertTrue(rangeMatch && colorMatch && transferMatch);

        dec.release();
        ex.release();
    }

    private void testTrackSelection(final String res) throws Exception {
        MediaExtractor ex1 = new MediaExtractor();
        Preconditions.assertTestFileExists(mInpPrefix + res);
        try {
            ex1.setDataSource(mInpPrefix + res);

            ByteBuffer buf1 = ByteBuffer.allocate(1024*1024);
            ArrayList<Integer> vid = new ArrayList<Integer>();
            ArrayList<Integer> aud = new ArrayList<Integer>();

            // scan the file once and build lists of audio and video samples
            ex1.selectTrack(0);
            ex1.selectTrack(1);
            while(true) {
                int n1 = ex1.readSampleData(buf1, 0);
                if (n1 < 0) {
                    break;
                }
                int idx = ex1.getSampleTrackIndex();
                if (idx == 0) {
                    vid.add(n1);
                } else if (idx == 1) {
                    aud.add(n1);
                } else {
                    fail("unexpected track index: " + idx);
                }
                ex1.advance();
            }

            // read the video track once, then rewind and do it again, and
            // verify we get the right samples
            ex1.release();
            ex1 = new MediaExtractor();
            ex1.setDataSource(mInpPrefix + res);
            ex1.selectTrack(0);
            for (int i = 0; i < 2; i++) {
                ex1.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                int idx = 0;
                while(true) {
                    int n1 = ex1.readSampleData(buf1, 0);
                    if (n1 < 0) {
                        assertEquals(vid.size(), idx);
                        break;
                    }
                    assertEquals(vid.get(idx++).intValue(), n1);
                    ex1.advance();
                }
            }

            // read the audio track once, then rewind and do it again, and
            // verify we get the right samples
            ex1.release();
            ex1 = new MediaExtractor();
            ex1.setDataSource(mInpPrefix + res);
            ex1.selectTrack(1);
            for (int i = 0; i < 2; i++) {
                ex1.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                int idx = 0;
                while(true) {
                    int n1 = ex1.readSampleData(buf1, 0);
                    if (n1 < 0) {
                        assertEquals(aud.size(), idx);
                        break;
                    }
                    assertEquals(aud.get(idx++).intValue(), n1);
                    ex1.advance();
                }
            }

            // read the video track first, then rewind and get the audio track instead, and
            // verify we get the right samples
            ex1.release();
            ex1 = new MediaExtractor();
            ex1.setDataSource(mInpPrefix + res);
            for (int i = 0; i < 2; i++) {
                ex1.selectTrack(i);
                ex1.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                int idx = 0;
                while(true) {
                    int n1 = ex1.readSampleData(buf1, 0);
                    if (i == 0) {
                        if (n1 < 0) {
                            assertEquals(vid.size(), idx);
                            break;
                        }
                        assertEquals(vid.get(idx++).intValue(), n1);
                    } else if (i == 1) {
                        if (n1 < 0) {
                            assertEquals(aud.size(), idx);
                            break;
                        }
                        assertEquals(aud.get(idx++).intValue(), n1);
                    } else {
                        fail("unexpected track index: " + idx);
                    }
                    ex1.advance();
                }
                ex1.unselectTrack(i);
            }

            // read the video track first, then rewind, enable the audio track in addition
            // to the video track, and verify we get the right samples
            ex1.release();
            ex1 = new MediaExtractor();
            ex1.setDataSource(mInpPrefix + res);
            for (int i = 0; i < 2; i++) {
                ex1.selectTrack(i);
                ex1.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                int vididx = 0;
                int audidx = 0;
                while(true) {
                    int n1 = ex1.readSampleData(buf1, 0);
                    if (n1 < 0) {
                        // we should have read all audio and all video samples at this point
                        assertEquals(vid.size(), vididx);
                        if (i == 1) {
                            assertEquals(aud.size(), audidx);
                        }
                        break;
                    }
                    int trackidx = ex1.getSampleTrackIndex();
                    if (trackidx == 0) {
                        assertEquals(vid.get(vididx++).intValue(), n1);
                    } else if (trackidx == 1) {
                        assertEquals(aud.get(audidx++).intValue(), n1);
                    } else {
                        fail("unexpected track index: " + trackidx);
                    }
                    ex1.advance();
                }
            }

            // read both tracks from the start, then rewind and verify we get the right
            // samples both times
            ex1.release();
            ex1 = new MediaExtractor();
            ex1.setDataSource(mInpPrefix + res);
            for (int i = 0; i < 2; i++) {
                ex1.selectTrack(0);
                ex1.selectTrack(1);
                ex1.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                int vididx = 0;
                int audidx = 0;
                while(true) {
                    int n1 = ex1.readSampleData(buf1, 0);
                    if (n1 < 0) {
                        // we should have read all audio and all video samples at this point
                        assertEquals(vid.size(), vididx);
                        assertEquals(aud.size(), audidx);
                        break;
                    }
                    int trackidx = ex1.getSampleTrackIndex();
                    if (trackidx == 0) {
                        assertEquals(vid.get(vididx++).intValue(), n1);
                    } else if (trackidx == 1) {
                        assertEquals(aud.get(audidx++).intValue(), n1);
                    } else {
                        fail("unexpected track index: " + trackidx);
                    }
                    ex1.advance();
                }
            }

        } finally {
            if (ex1 != null) {
                ex1.release();
            }
        }
    }

    @Test
    public void testDecodeFragmented() throws Exception {
        testDecodeFragmented("video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz.mp4",
                "video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz_fragmented.mp4");
        testDecodeFragmented("video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz.mp4",
                "video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz_dash.mp4");
    }

    private void testDecodeFragmented(final String reference, final String teststream)
            throws Exception {
        Preconditions.assertTestFileExists(mInpPrefix + reference);
        Preconditions.assertTestFileExists(mInpPrefix + teststream);
        try {
            MediaExtractor ex1 = new MediaExtractor();
            ex1.setDataSource(mInpPrefix + reference);
            MediaExtractor ex2 = new MediaExtractor();
            ex2.setDataSource(mInpPrefix + teststream);

            assertEquals("different track count", ex1.getTrackCount(), ex2.getTrackCount());

            ByteBuffer buf1 = ByteBuffer.allocate(1024*1024);
            ByteBuffer buf2 = ByteBuffer.allocate(1024*1024);

            for (int i = 0; i < ex1.getTrackCount(); i++) {
                // note: this assumes the tracks are reported in the order in which they appear
                // in the file.
                ex1.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                ex1.selectTrack(i);
                ex2.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                ex2.selectTrack(i);

                while(true) {
                    int n1 = ex1.readSampleData(buf1, 0);
                    int n2 = ex2.readSampleData(buf2, 0);
                    assertEquals("different buffer size on track " + i, n1, n2);

                    if (n1 < 0) {
                        break;
                    }
                    // see bug 13008204
                    buf1.limit(n1);
                    buf2.limit(n2);
                    buf1.rewind();
                    buf2.rewind();

                    assertEquals("limit does not match return value on track " + i,
                            n1, buf1.limit());
                    assertEquals("limit does not match return value on track " + i,
                            n2, buf2.limit());

                    assertEquals("buffer data did not match on track " + i, buf1, buf2);

                    ex1.advance();
                    ex2.advance();
                }
                ex1.unselectTrack(i);
                ex2.unselectTrack(i);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Verify correct decoding of MPEG-4 AAC-LC mono and stereo streams
     */
    @Test
    public void testDecodeAacLcM4a() throws Exception {
        // mono
        decodeNtest("sinesweep1_1ch_8khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep1_1ch_11khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep1_1ch_12khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep1_1ch_16khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep1_1ch_22khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep1_1ch_24khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep1_1ch_32khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep1_1ch_44khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep1_1ch_48khz_aot2_mp4.m4a", 40.f);
        // stereo
        decodeNtest("sinesweep_2ch_8khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep_2ch_11khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep_2ch_12khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep_2ch_16khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep_2ch_22khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep_2ch_24khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep_2ch_32khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep_2ch_44khz_aot2_mp4.m4a", 40.f);
        decodeNtest("sinesweep_2ch_48khz_aot2_mp4.m4a", 40.f);
    }

    /**
     * Verify correct decoding of MPEG-4 AAC-LC 5.0 and 5.1 channel streams
     */
    @Test
    public void testDecodeAacLcMcM4a() throws Exception {
        for (String codecName : codecsFor("noise_6ch_48khz_aot2_mp4.m4a")) {
            AudioParameter decParams = new AudioParameter();
            short[] decSamples = decodeToMemory(codecName, decParams,
                    "noise_6ch_48khz_aot2_mp4.m4a", RESET_MODE_NONE,
                    CONFIG_MODE_NONE, -1, null);
            checkEnergy(decSamples, decParams, 6);
            decParams.reset();

            decSamples = decodeToMemory(codecName, decParams, "noise_5ch_44khz_aot2_mp4.m4a",
                    RESET_MODE_NONE, CONFIG_MODE_NONE, -1, null);
            checkEnergy(decSamples, decParams, 5);
            decParams.reset();
        }
    }

    /**
     * Verify correct decoding of MPEG-4 HE-AAC mono and stereo streams
     */
    @Test
    public void testDecodeHeAacM4a() throws Exception {
        Object [][] samples = {
                //  {resource, numChannels},
                {"noise_1ch_24khz_aot5_dr_sbr_sig1_mp4.m4a", 1},
                {"noise_1ch_24khz_aot5_ds_sbr_sig1_mp4.m4a", 1},
                {"noise_1ch_32khz_aot5_dr_sbr_sig2_mp4.m4a", 1},
                {"noise_1ch_44khz_aot5_dr_sbr_sig0_mp4.m4a", 1},
                {"noise_1ch_44khz_aot5_ds_sbr_sig2_mp4.m4a", 1},
                {"noise_2ch_24khz_aot5_dr_sbr_sig2_mp4.m4a", 2},
                {"noise_2ch_32khz_aot5_ds_sbr_sig2_mp4.m4a", 2},
                {"noise_2ch_48khz_aot5_dr_sbr_sig1_mp4.m4a", 2},
                {"noise_2ch_48khz_aot5_ds_sbr_sig1_mp4.m4a", 2},
        };

        for (Object [] sample: samples) {
            for (String codecName : codecsFor((String)sample[0], CODEC_DEFAULT)) {
                AudioParameter decParams = new AudioParameter();
                short[] decSamples = decodeToMemory(codecName, decParams,
                        (String)sample[0] /* resource */, RESET_MODE_NONE, CONFIG_MODE_NONE,
                        -1, null);
                checkEnergy(decSamples, decParams, (Integer)sample[1] /* number of channels */);
                decParams.reset();
            }
        }
    }

    /**
     * Verify correct decoding of MPEG-4 HE-AAC 5.0 and 5.1 channel streams
     */
    @Test
    public void testDecodeHeAacMcM4a() throws Exception {
        Object [][] samples = {
                //  {resource, numChannels},
                {"noise_5ch_48khz_aot5_dr_sbr_sig1_mp4.m4a", 5},
                {"noise_6ch_44khz_aot5_dr_sbr_sig2_mp4.m4a", 6},
        };
        for (Object [] sample: samples) {
            for (String codecName : codecsFor((String)sample[0] /* resource */, CODEC_DEFAULT)) {
                AudioParameter decParams = new AudioParameter();
                short[] decSamples = decodeToMemory(codecName, decParams,
                        (String)sample[0] /* resource */, RESET_MODE_NONE, CONFIG_MODE_NONE,
                        -1, null);
                checkEnergy(decSamples, decParams, (Integer)sample[1] /* number of channels */);
                decParams.reset();
            }
        }
    }

    /**
     * Verify correct decoding of MPEG-4 HE-AAC v2 stereo streams
     */
    @Test
    public void testDecodeHeAacV2M4a() throws Exception {
        String [] samples = {
                "noise_2ch_24khz_aot29_dr_sbr_sig0_mp4.m4a",
                "noise_2ch_44khz_aot29_dr_sbr_sig1_mp4.m4a",
                "noise_2ch_48khz_aot29_dr_sbr_sig2_mp4.m4a"
        };
        for (String sample: samples) {
            for (String codecName : codecsFor(sample, CODEC_DEFAULT)) {
                AudioParameter decParams = new AudioParameter();
                short[] decSamples = decodeToMemory(codecName, decParams, sample,
                        RESET_MODE_NONE, CONFIG_MODE_NONE, -1, null);
                checkEnergy(decSamples, decParams, 2);
            }
        }
    }

    /**
     * Verify correct decoding of MPEG-4 AAC-ELD mono and stereo streams
     */
    @Test
    public void testDecodeAacEldM4a() throws Exception {
        // mono
        decodeNtest("sinesweep1_1ch_16khz_aot39_fl480_mp4.m4a", 40.f, CODEC_DEFAULT);
        decodeNtest("sinesweep1_1ch_22khz_aot39_fl512_mp4.m4a", 40.f, CODEC_DEFAULT);
        decodeNtest("sinesweep1_1ch_24khz_aot39_fl480_mp4.m4a", 40.f, CODEC_DEFAULT);
        decodeNtest("sinesweep1_1ch_32khz_aot39_fl512_mp4.m4a", 40.f, CODEC_DEFAULT);
        decodeNtest("sinesweep1_1ch_44khz_aot39_fl480_mp4.m4a", 40.f, CODEC_DEFAULT);
        decodeNtest("sinesweep1_1ch_48khz_aot39_fl512_mp4.m4a", 40.f, CODEC_DEFAULT);

        // stereo
        decodeNtest("sinesweep_2ch_16khz_aot39_fl512_mp4.m4a", 40.f, CODEC_DEFAULT);
        decodeNtest("sinesweep_2ch_22khz_aot39_fl480_mp4.m4a", 40.f, CODEC_DEFAULT);
        decodeNtest("sinesweep_2ch_24khz_aot39_fl512_mp4.m4a", 40.f, CODEC_DEFAULT);
        decodeNtest("sinesweep_2ch_32khz_aot39_fl480_mp4.m4a", 40.f, CODEC_DEFAULT);
        decodeNtest("sinesweep_2ch_44khz_aot39_fl512_mp4.m4a", 40.f, CODEC_DEFAULT);
        decodeNtest("sinesweep_2ch_48khz_aot39_fl480_mp4.m4a", 40.f, CODEC_DEFAULT);

        AudioParameter decParams = new AudioParameter();

        Object [][] samples = {
                //  {resource, numChannels},
                {"noise_1ch_16khz_aot39_ds_sbr_fl512_mp4.m4a", 1},
                {"noise_1ch_24khz_aot39_ds_sbr_fl512_mp4.m4a", 1},
                {"noise_1ch_32khz_aot39_dr_sbr_fl480_mp4.m4a", 1},
                {"noise_1ch_44khz_aot39_ds_sbr_fl512_mp4.m4a", 1},
                {"noise_1ch_44khz_aot39_ds_sbr_fl512_mp4.m4a", 1},
                {"noise_1ch_48khz_aot39_dr_sbr_fl480_mp4.m4a", 1},
                {"noise_2ch_22khz_aot39_ds_sbr_fl512_mp4.m4a", 2},
                {"noise_2ch_32khz_aot39_ds_sbr_fl512_mp4.m4a", 2},
                {"noise_2ch_44khz_aot39_dr_sbr_fl480_mp4.m4a", 2},
                {"noise_2ch_48khz_aot39_ds_sbr_fl512_mp4.m4a", 2},
        };
        for (Object [] sample: samples) {
            for (String codecName : codecsFor((String)sample[0], CODEC_DEFAULT)) {
                short[] decSamples = decodeToMemory(codecName, decParams,
                        (String)sample[0] /* resource */, RESET_MODE_NONE, CONFIG_MODE_NONE,
                        -1, null);
                checkEnergy(decSamples, decParams, (Integer)sample[1] /* number of channels */);
                decParams.reset();
            }
        }
    }

    /**
     * Perform a segmented energy analysis on given audio signal samples and run several tests on
     * the energy values.
     *
     * The main purpose is to verify whether an AAC decoder implementation applies Spectral Band
     * Replication (SBR) and Parametric Stereo (PS) correctly. Both tools are inherent parts to the
     * MPEG-4 HE-AAC and HE-AAC v2 audio codecs.
     *
     * In addition, this test can verify the correct decoding of multi-channel (e.g. 5.1 channel)
     * streams or the creation of a mixdown signal.
     *
     * Note: This test procedure is not an MPEG Conformance Test and can not serve as a replacement.
     *
     * @param decSamples the decoded audio samples to be tested
     * @param decParams the audio parameters of the given audio samples (decSamples)
     * @param encNch the encoded number of audio channels (number of channels of the original
     *               input)
     * @param nrgRatioThresh threshold to classify the energy ratios ]0.0, 1.0[
     * @throws RuntimeException
     */
    protected void checkEnergy(short[] decSamples, AudioParameter decParams, int encNch,
                             float nrgRatioThresh) throws RuntimeException
    {
        final int nSegPerBlk = 4;                          // the number of segments per block
        final int nCh = decParams.getNumChannels();        // the number of input channels
        final int nBlkSmp = decParams.getSamplingRate();   // length of one (LB/HB) block [samples]
        final int nSegSmp = nBlkSmp / nSegPerBlk;          // length of one segment [samples]
        final int smplPerChan = decSamples.length / nCh;   // actual # samples per channel (total)

        final int nSegSmpTot = nSegSmp * nCh;              // actual # samples per segment (all ch)
        final int nSegChOffst = 2 * nSegPerBlk;            // signal offset between chans [segments]
        final int procNch = Math.min(nCh, encNch);         // the number of channels to be analyzed
        if (encNch > 4) {
            assertTrue(String.format("multichannel content (%dch) was downmixed (%dch)",
                    encNch, nCh), procNch > 4);
        }
        assertTrue(String.format("got less channels(%d) than encoded (%d)", nCh, encNch),
                nCh >= encNch);

        final int encEffNch = (encNch > 5) ? encNch-1 : encNch;  // all original configs with more
                                                           // ... than five channel have an LFE */
        final int expSmplPerChan = Math.max(encEffNch, 2) * nSegChOffst * nSegSmp;
        final boolean isDmx = nCh < encNch;                // flag telling that input is dmx signal
        int effProcNch = procNch;                          // the num analyzed channels with signal

        assertTrue("got less input samples than expected", smplPerChan >= expSmplPerChan);

        // get the signal offset by counting zero samples at the very beginning (over all channels)
        final int zeroSigThresh = 1;                     // sample value threshold for signal search
        int signalStart = smplPerChan;                   // receives the number of samples that
                                                         // ... are in front of the actual signal
        int noiseStart = signalStart;                    // receives the number of null samples
                                                         // ... (per chan) at the very beginning
        for (int smpl = 0; smpl < decSamples.length; smpl++) {
            int value = Math.abs(decSamples[smpl]);
            if (value > 0 && noiseStart == signalStart) {
                noiseStart = smpl / nCh;                   // store start of prepended noise
            }                                              // ... (can be same as signalStart)
            if (value > zeroSigThresh) {
                signalStart = smpl / nCh;                  // store signal start offset [samples]
                break;
            }
        }
        signalStart = (signalStart > noiseStart+1) ? signalStart : noiseStart;
        assertTrue ("no signal found in any channel!", signalStart < smplPerChan);
        final int totSeg = (smplPerChan-signalStart) / nSegSmp; // max num seg that fit into signal
        final int totSmp = nSegSmp * totSeg;               // max num relevant samples (per channel)
        assertTrue("no segments left to test after signal search", totSeg > 0);

        // get the energies and the channel offsets by searching for the first segment above the
        //  energy threshold
        final double zeroMaxNrgRatio = 0.001f;             // ratio of zeroNrgThresh to the max nrg
        double zeroNrgThresh = nSegSmp * nSegSmp;          // threshold to classify segment energies
        double totMaxNrg = 0.0f;                           // will store the max seg nrg over all ch
        double[][] nrg = new double[procNch][totSeg];      // array receiving the segment energies
        int[] offset = new int[procNch];                   // array for channel offsets
        boolean[] sigSeg = new boolean[totSeg];            // array receiving the segment ...
                                                           // ... energy status over all channels
        for (int ch = 0; ch < procNch; ch++) {
            offset[ch] = -1;
            for (int seg = 0; seg < totSeg; seg++) {
                final int smpStart = (signalStart * nCh) + (seg * nSegSmpTot) + ch;
                final int smpStop = smpStart + nSegSmpTot;
                for (int smpl = smpStart; smpl < smpStop; smpl += nCh) {
                    nrg[ch][seg] += decSamples[smpl] * decSamples[smpl];  // accumulate segment nrg
                }
                if (nrg[ch][seg] > zeroNrgThresh && offset[ch] < 0) { // store 1st segment (index)
                    offset[ch] = seg / nSegChOffst;        // ... per ch which has energy above the
                }                                          // ... threshold to get the ch offsets
                if (nrg[ch][seg] > totMaxNrg) {
                    totMaxNrg = nrg[ch][seg];              // store the max segment nrg over all ch
                }
                sigSeg[seg] |= nrg[ch][seg] > zeroNrgThresh;  // store whether the channel has
                                                           // ... energy in this segment
            }
            if (offset[ch] < 0) {                          // if one channel has no signal it is
                effProcNch -= 1;                           // ... most probably the LFE
                offset[ch] = effProcNch;                   // the LFE is no effective channel
            }
            if (ch == 0) {                                 // recalculate the zero signal threshold
                zeroNrgThresh = zeroMaxNrgRatio * totMaxNrg; // ... based on the 1st channels max
            }                                              // ... energy for all subsequent checks
        }
        // check the channel mapping
        assertTrue("more than one LFE detected", effProcNch >= procNch - 1);
        assertTrue(String.format("less samples decoded than expected: %d < %d",
                decSamples.length-(signalStart * nCh), totSmp * effProcNch),
                decSamples.length-(signalStart * nCh) >= totSmp * effProcNch);
        if (procNch >= 5) {                                // for multi-channel signals the only
            final int[] frontChMap1 = {2, 0, 1};           // valid front channel orders are L, R, C
            final int[] frontChMap2 = {0, 1, 2};           // or C, L, R (L=left, R=right, C=center)
            if ( !(Arrays.equals(Arrays.copyOfRange(offset, 0, 3), frontChMap1)
                    || Arrays.equals(Arrays.copyOfRange(offset, 0, 3), frontChMap2)) ) {
                fail("wrong front channel mapping");
            }
        }
        // check whether every channel occurs exactly once
        int[] chMap = new int[nCh];                        // mapping array to sort channels
        for (int ch = 0; ch < effProcNch; ch++) {
            int occurred = 0;
            for (int idx = 0; idx < procNch; idx++) {
                if (offset[idx] == ch) {
                    occurred += 1;
                    chMap[ch] = idx;                       // create mapping table to address chans
                }                                          // ... from front to back
            }                                              // the LFE must be last
            assertTrue(String.format("channel %d occurs %d times in the mapping", ch, occurred),
                    occurred == 1);
        }

        // go over all segment energies in all channels and check them
        double refMinNrg = zeroNrgThresh;                  // reference min energy for the 1st ch;
                                                           // others will be compared against 1st
        for (int ch = 0; ch < procNch; ch++) {
            int idx = chMap[ch];                           // resolve channel mapping
            final int ofst = offset[idx] * nSegChOffst;    // signal offset [segments]
            if (ch < effProcNch && ofst < totSeg) {
                int nrgSegEnd;                             // the last segment that has energy
                int nrgSeg;                                // the number of segments with energy
                if ((encNch <= 2) && (ch == 0)) {          // the first channel of a mono or ...
                    nrgSeg = totSeg;                       // stereo signal has full signal ...
                } else {                                   // all others have one LB + one HB block
                    nrgSeg = Math.min(totSeg, (2 * nSegPerBlk) + ofst) - ofst;
                }
                nrgSegEnd = ofst + nrgSeg;
                // find min and max energy of all segments that should have signal
                double minNrg = nrg[idx][ofst];            // channels minimum segment energy
                double maxNrg = nrg[idx][ofst];            // channels maximum segment energy
                for (int seg = ofst+1; seg < nrgSegEnd; seg++) {          // values of 1st segment
                    if (nrg[idx][seg] < minNrg) minNrg = nrg[idx][seg];   // ... already assigned
                    if (nrg[idx][seg] > maxNrg) maxNrg = nrg[idx][seg];
                }
                assertTrue(String.format("max energy of channel %d is zero", ch),
                        maxNrg > 0.0f);
                assertTrue(String.format("channel %d has not enough energy", ch),
                        minNrg >= refMinNrg);              // check the channels minimum energy
                if (ch == 0) {                             // use 85% of 1st channels min energy as
                    refMinNrg = minNrg * 0.85f;            // ... reference the other chs must meet
                } else if (isDmx && (ch == 1)) {           // in case of mixdown signal the energy
                    refMinNrg *= 0.50f;                    // ... can be lower depending on the
                }                                          // ... downmix equation
                // calculate and check the energy ratio
                final double nrgRatio = minNrg / maxNrg;
                assertTrue(String.format("energy ratio of channel %d below threshold", ch),
                        nrgRatio >= nrgRatioThresh);
                if (!isDmx) {
                    if (nrgSegEnd < totSeg) {
                        // consider that some noise can extend into the subsequent segment
                        // allow this to be at max 20% of the channels minimum energy
                        assertTrue(String.format("min energy after noise above threshold (%.2f)",
                                nrg[idx][nrgSegEnd]),
                                nrg[idx][nrgSegEnd] < minNrg * 0.20f);
                        nrgSegEnd += 1;
                    }
                } else {                                   // ignore all subsequent segments
                    nrgSegEnd = totSeg;                    // ... in case of a mixdown signal
                }
                // zero-out the verified energies to simplify the subsequent check
                for (int seg = ofst; seg < nrgSegEnd; seg++) nrg[idx][seg] = 0.0f;
            }
            // check zero signal parts
            for (int seg = 0; seg < totSeg; seg++) {
                assertTrue(String.format("segment %d in channel %d has signal where should " +
                        "be none (%.2f)", seg, ch, nrg[idx][seg]), nrg[idx][seg] < zeroNrgThresh);
            }
        }
        // test whether each segment has energy in at least one channel
        for (int seg = 0; seg < totSeg; seg++) {
            assertTrue(String.format("no channel has energy in segment %d", seg), sigSeg[seg]);
        }
    }

    private void checkEnergy(short[] decSamples, AudioParameter decParams, int encNch)
            throws RuntimeException {
        checkEnergy(decSamples, decParams, encNch, 0.50f);  // default energy ratio threshold: 0.50
    }

    /**
     * Calculate the RMS of the difference signal between a given signal and the reference samples
     * located in mMasterBuffer.
     * @param signal the decoded samples to test
     * @return RMS of error signal
     * @throws RuntimeException
     */
    private double getRmsError(short[] signal) throws RuntimeException {
        long totalErrorSquared = 0;
        int stride = mMasterBuffer.length / signal.length;
        assertEquals("wrong data size", mMasterBuffer.length, signal.length * stride);

        for (int i = 0; i < signal.length; i++) {
            short sample = signal[i];
            short mastersample = mMasterBuffer[i * stride];
            int d = sample - mastersample;
            totalErrorSquared += d * d;
        }
        long avgErrorSquared = (totalErrorSquared / signal.length);
        return Math.sqrt(avgErrorSquared);
    }

    /**
     * Decode a given input stream and compare the output against the reference signal. The RMS of
     * the error signal must be below the given threshold (maxerror).
     * Important note about the test signals: this method expects test signals to have been
     *   "stretched" relative to the reference signal. The reference, sinesweepraw, is 3s long at
     *   44100Hz. For instance for comparing this reference to a test signal at 8000Hz, the test
     *   signal needs to be 44100/8000 = 5.5125 times longer, containing frequencies 5.5125
     *   times lower than the reference.
     * @param testinput the file to decode
     * @param maxerror  the maximum allowed root mean squared error
     * @throws Exception
     */
    private void decodeNtest(final String testinput, float maxerror) throws Exception {
        decodeNtest(testinput, maxerror, CODEC_ALL);
    }

    private void decodeNtest(final String testinput, float maxerror, int codecSupportMode)
            throws Exception {
        String localTag = TAG + "#decodeNtest";

        for (String codecName: codecsFor(testinput, codecSupportMode)) {
            AudioParameter decParams = new AudioParameter();
            short[] decoded = decodeToMemory(codecName, decParams, testinput,
                    RESET_MODE_NONE, CONFIG_MODE_NONE, -1, null);
            double rmse = getRmsError(decoded);

            assertTrue(codecName + ": decoding error too big: " + rmse, rmse <= maxerror);
            Log.v(localTag, String.format("rms = %f (max = %f)", rmse, maxerror));
        }
    }

    private void monoTest(final String res, int expectedLength) throws Exception {
        for (String codecName: codecsFor(res)) {
            short [] mono = decodeToMemory(codecName, res,
                    RESET_MODE_NONE, CONFIG_MODE_NONE, -1, null);
            if (mono.length == expectedLength) {
                // expected
            } else if (mono.length == expectedLength * 2) {
                // the decoder output 2 channels instead of 1, check that the left and right channel
                // are identical
                for (int i = 0; i < mono.length; i += 2) {
                    assertEquals(codecName + ": mismatched samples at " + i, mono[i], mono[i+1]);
                }
            } else {
                fail(codecName + ": wrong number of samples: " + mono.length);
            }

            short [] mono2 = decodeToMemory(codecName, res,
                    RESET_MODE_RECONFIGURE, CONFIG_MODE_NONE, -1, null);

            assertEquals(codecName + ": count different after reconfigure: ",
                    mono.length, mono2.length);
            for (int i = 0; i < mono.length; i++) {
                assertEquals(codecName + ": samples at " + i + " don't match", mono[i], mono2[i]);
            }

            short [] mono3 = decodeToMemory(codecName, res,
                    RESET_MODE_FLUSH, CONFIG_MODE_NONE, -1, null);

            assertEquals(codecName + ": count different after flush: ", mono.length, mono3.length);
            for (int i = 0; i < mono.length; i++) {
                assertEquals(codecName + ": samples at " + i + " don't match", mono[i], mono3[i]);
            }
        }
    }

    protected static List<String> codecsFor(String resource) throws IOException {
        return codecsFor(resource, CODEC_ALL);
    }

    protected static List<String> codecsFor(String resource, int codecSupportMode)
            throws IOException {

        // CODEC_DEFAULT behaviors started with S
        if (IS_BEFORE_S) {
            codecSupportMode = CODEC_ALL;
        }
        MediaExtractor ex = new MediaExtractor();
        AssetFileDescriptor fd = getAssetFileDescriptorFor(resource);
        try {
            ex.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        } finally {
            fd.close();
        }
        MediaCodecInfo[] codecInfos = new MediaCodecList(
                MediaCodecList.REGULAR_CODECS).getCodecInfos();
        ArrayList<String> matchingCodecs = new ArrayList<String>();
        MediaFormat format = ex.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        for (MediaCodecInfo info: codecInfos) {
            if (info.isEncoder()) {
                continue;
            }
            try {
                MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                if (caps != null) {
                    // do we test this codec in current mode?
                    if (!TestUtils.isTestableCodecInCurrentMode(info.getName())) {
                        Log.i(TAG, "skip codec " + info.getName() + " in current mode");
                        continue;
                    }
                    if (codecSupportMode == CODEC_ALL) {
                        if (IS_AFTER_T) {
                            // This is an extractor failure as often as it is a codec failure
                            assertTrue(info.getName() + " does not declare support for "
                                    + format.toString(),
                                    caps.isFormatSupported(format));
                        }
                        matchingCodecs.add(info.getName());
                    } else if (codecSupportMode == CODEC_DEFAULT) {
                        if (caps.isFormatSupported(format)) {
                            matchingCodecs.add(info.getName());
                        } else if (isDefaultCodec(info.getName(), mime)) {
                            // This is an extractor failure as often as it is a codec failure
                            fail(info.getName() + " which is a default decoder for mime " + mime
                                   + ", does not declare support for " + format.toString());
                        }
                    } else {
                        fail("Unhandled codec support mode " + codecSupportMode);
                    }
                }
            } catch (IllegalArgumentException e) {
                // type is not supported
            }
        }
        if (TestUtils.isMtsMode()) {
            // not fatal in MTS mode
            Assume.assumeTrue("no MTS-mode codecs found for format " + format.toString(),
                            matchingCodecs.size() != 0);
        } else {
            // but fatal in CTS mode
            assertTrue("no codecs found for format " + format.toString(),
                            matchingCodecs.size() != 0);
        }
        return matchingCodecs;
    }

    /**
     * @param testinput the file to decode
     * @param maxerror the maximum allowed root mean squared error
     * @throws IOException
     */
    private void decode(final String testinput, float maxerror) throws IOException {

        for (String codecName: codecsFor(testinput)) {
            short[] decoded = decodeToMemory(codecName, testinput,
                    RESET_MODE_NONE, CONFIG_MODE_NONE, -1, null);

            assertEquals(codecName + ": wrong data size", mMasterBuffer.length, decoded.length);

            double rmse = getRmsError(decoded);

            assertTrue(codecName + ": decoding error too big: " + rmse, rmse <= maxerror);

            int[] resetModes = new int[] { RESET_MODE_NONE, RESET_MODE_RECONFIGURE,
                    RESET_MODE_FLUSH, RESET_MODE_EOS_FLUSH };
            int[] configModes = new int[] { CONFIG_MODE_NONE, CONFIG_MODE_QUEUE };

            for (int conf : configModes) {
                for (int reset : resetModes) {
                    if (conf == CONFIG_MODE_NONE && reset == RESET_MODE_NONE) {
                        // default case done outside of loop
                        continue;
                    }
                    if (conf == CONFIG_MODE_QUEUE && !hasAudioCsd(testinput)) {
                        continue;
                    }

                    String params = String.format("(using reset: %d, config: %s)", reset, conf);
                    short[] decoded2 = decodeToMemory(codecName, testinput, reset, conf, -1, null);
                    assertEquals(codecName + ": count different with reconfigure" + params,
                            decoded.length, decoded2.length);
                    for (int i = 0; i < decoded.length; i++) {
                        assertEquals(codecName + ": samples don't match" + params,
                                decoded[i], decoded2[i]);
                    }
                }
            }
        }
    }

    private boolean hasAudioCsd(final String testinput) throws IOException {
        AssetFileDescriptor fd = null;
        try {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(mInpPrefix + testinput);
            MediaFormat format = extractor.getTrackFormat(0);

            return format.containsKey(CSD_KEYS[0]);

        } finally {
            if (fd != null) {
                fd.close();
            }
        }
    }

    protected static int getOutputFormatInteger(MediaCodec codec, String key) {
        if (codec == null) {
            fail("Null MediaCodec before attempting to retrieve output format key " + key);
        }
        MediaFormat format = null;
        try {
            format = codec.getOutputFormat();
        } catch (Exception e) {
            fail("Exception " + e + " when attempting to obtain output format");
        }
        if (format == null) {
            fail("Null output format returned from MediaCodec");
        }
        try {
            return format.getInteger(key);
        } catch (NullPointerException e) {
            fail("Key " + key + " not present in output format");
        } catch (ClassCastException e) {
            fail("Key " + key + " not stored as integer in output format");
        } catch (Exception e) {
            fail("Exception " + e + " when attempting to retrieve output format key " + key);
        }
        // never used
        return Integer.MIN_VALUE;
    }

    // Class handling all audio parameters relevant for testing
    protected static class AudioParameter {

        public AudioParameter() {
            reset();
        }

        public void reset() {
            mNumChannels = 0;
            mSamplingRate = 0;
            mChannelMask = 0;
        }

        public int getNumChannels() {
            return mNumChannels;
        }

        public int getSamplingRate() {
            return mSamplingRate;
        }

        public int getChannelMask() {
            return mChannelMask;
        }

        public void setNumChannels(int numChannels) {
            mNumChannels = numChannels;
        }

        public void setSamplingRate(int samplingRate) {
            mSamplingRate = samplingRate;
        }

        public void setChannelMask(int mask) {
            mChannelMask = mask;
        }

        private int mNumChannels;
        private int mSamplingRate;
        private int mChannelMask;
    }

    private short[] decodeToMemory(String codecName, final String testinput, int resetMode,
            int configMode, int eossample, List<Long> timestamps) throws IOException {

        AudioParameter audioParams = new AudioParameter();
        return decodeToMemory(codecName, audioParams, testinput,
                resetMode, configMode, eossample, timestamps);
    }

    private short[] decodeToMemory(String codecName, AudioParameter audioParams,
            final String testinput, int resetMode, int configMode, int eossample,
            List<Long> timestamps) throws IOException {
        String localTag = TAG + "#decodeToMemory";
        Log.v(localTag, String.format("reset = %d; config: %s", resetMode, configMode));
        short [] decoded = new short[0];
        int decodedIdx = 0;

        MediaExtractor extractor;
        MediaCodec codec;
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        extractor = new MediaExtractor();
        extractor.setDataSource(mInpPrefix + testinput);

        assertEquals("wrong number of tracks", 1, extractor.getTrackCount());
        MediaFormat format = extractor.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        assertTrue("not an audio file", mime.startsWith("audio/"));

        MediaFormat configFormat = format;
        codec = MediaCodec.createByCodecName(codecName);
        if (configMode == CONFIG_MODE_QUEUE && format.containsKey(CSD_KEYS[0])) {
            configFormat = MediaFormat.createAudioFormat(mime,
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));

            configFormat.setLong(MediaFormat.KEY_DURATION,
                    format.getLong(MediaFormat.KEY_DURATION));
            String[] keys = new String[] { "max-input-size", "encoder-delay", "encoder-padding" };
            for (String k : keys) {
                if (format.containsKey(k)) {
                    configFormat.setInteger(k, format.getInteger(k));
                }
            }
        }
        Log.v(localTag, "configuring with " + configFormat);
        codec.configure(configFormat, null /* surface */, null /* crypto */, 0 /* flags */);

        codec.start();
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();

        if (resetMode == RESET_MODE_RECONFIGURE) {
            codec.stop();
            codec.configure(configFormat, null /* surface */, null /* crypto */, 0 /* flags */);
            codec.start();
            codecInputBuffers = codec.getInputBuffers();
            codecOutputBuffers = codec.getOutputBuffers();
        } else if (resetMode == RESET_MODE_FLUSH) {
            codec.flush();
        }

        extractor.selectTrack(0);

        if (configMode == CONFIG_MODE_QUEUE) {
            queueConfig(codec, format);
        }

        // start decoding
        final long kTimeOutUs = 5000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int noOutputCounter = 0;
        int samplecounter = 0;
        while (!sawOutputEOS && noOutputCounter < 50) {
            noOutputCounter++;
            if (!sawInputEOS) {
                int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

                    int sampleSize =
                        extractor.readSampleData(dstBuf, 0 /* offset */);

                    long presentationTimeUs = 0;

                    if (sampleSize < 0 && eossample > 0) {
                        fail("test is broken: never reached eos sample");
                    }
                    if (sampleSize < 0) {
                        Log.d(TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        if (samplecounter == eossample) {
                            sawInputEOS = true;
                        }
                        samplecounter++;
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    codec.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!sawInputEOS) {
                        extractor.advance();
                    }
                }
            }

            int res = codec.dequeueOutputBuffer(info, kTimeOutUs);

            if (res >= 0) {
                //Log.d(TAG, "got frame, size " + info.size + "/" + info.presentationTimeUs);

                if (info.size > 0) {
                    noOutputCounter = 0;
                    if (timestamps != null) {
                        timestamps.add(info.presentationTimeUs);
                    }
                }
                if (info.size > 0 &&
                        resetMode != RESET_MODE_NONE && resetMode != RESET_MODE_EOS_FLUSH) {
                    // once we've gotten some data out of the decoder, reset and start again
                    if (resetMode == RESET_MODE_RECONFIGURE) {
                        codec.stop();
                        codec.configure(configFormat, null /* surface */, null /* crypto */,
                                0 /* flags */);
                        codec.start();
                        codecInputBuffers = codec.getInputBuffers();
                        codecOutputBuffers = codec.getOutputBuffers();
                        if (configMode == CONFIG_MODE_QUEUE) {
                            queueConfig(codec, format);
                        }
                    } else /* resetMode == RESET_MODE_FLUSH */ {
                        codec.flush();
                    }
                    resetMode = RESET_MODE_NONE;
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                    sawInputEOS = false;
                    samplecounter = 0;
                    if (timestamps != null) {
                        timestamps.clear();
                    }
                    continue;
                }

                int outputBufIndex = res;
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                if (decodedIdx + (info.size / 2) >= decoded.length) {
                    decoded = Arrays.copyOf(decoded, decodedIdx + (info.size / 2));
                }

                buf.position(info.offset);
                for (int i = 0; i < info.size; i += 2) {
                    decoded[decodedIdx++] = buf.getShort();
                }

                codec.releaseOutputBuffer(outputBufIndex, false /* render */);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    if (resetMode == RESET_MODE_EOS_FLUSH) {
                        resetMode = RESET_MODE_NONE;
                        codec.flush();
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                        sawInputEOS = false;
                        samplecounter = 0;
                        decoded = new short[0];
                        decodedIdx = 0;
                        if (timestamps != null) {
                            timestamps.clear();
                        }
                    } else {
                        sawOutputEOS = true;
                    }
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();

                Log.d(TAG, "output buffers have changed.");
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = codec.getOutputFormat();
                audioParams.setNumChannels(oformat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                audioParams.setSamplingRate(oformat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                Log.d(TAG, "output format has changed to " + oformat);
            } else {
                Log.d(TAG, "dequeueOutputBuffer returned " + res);
            }
        }
        if (noOutputCounter >= 50) {
            fail("decoder stopped outputing data");
        }

        codec.stop();
        codec.release();
        return decoded;
    }

    private static void queueConfig(MediaCodec codec, MediaFormat format) {
        for (String csdKey : CSD_KEYS) {
            if (!format.containsKey(csdKey)) {
                continue;
            }
            ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
            int inputBufIndex = codec.dequeueInputBuffer(-1);
            if (inputBufIndex < 0) {
                fail("failed to queue configuration buffer " + csdKey);
            } else {
                ByteBuffer csd = (ByteBuffer) format.getByteBuffer(csdKey).rewind();
                Log.v(TAG + "#queueConfig", String.format("queueing %s:%s", csdKey, csd));
                codecInputBuffers[inputBufIndex].put(csd);
                codec.queueInputBuffer(
                        inputBufIndex,
                        0 /* offset */,
                        csd.limit(),
                        0 /* presentation time (us) */,
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
            }
        }
    }

    @Test
    public void testDecodeM4aWithEOSOnLastBuffer() throws Exception {
        testDecodeWithEOSOnLastBuffer("sinesweepm4a.m4a");
    }

    @Test
    public void testDecodeMp3WithEOSOnLastBuffer() throws Exception {
        testDecodeWithEOSOnLastBuffer("sinesweepmp3lame.mp3");
        testDecodeWithEOSOnLastBuffer("sinesweepmp3smpb.mp3");
    }

    @Test
    public void testDecodeOpusWithEOSOnLastBuffer() throws Exception {
        testDecodeWithEOSOnLastBuffer("sinesweepopus.mkv");
        testDecodeWithEOSOnLastBuffer("sinesweepopusmp4.mp4");
    }

    @Test
    public void testDecodeWavWithEOSOnLastBuffer() throws Exception {
        testDecodeWithEOSOnLastBuffer("sinesweepwav.wav");
    }

    @Test
    public void testDecodeFlacWithEOSOnLastBuffer() throws Exception {
        testDecodeWithEOSOnLastBuffer("sinesweepflacmkv.mkv");
        testDecodeWithEOSOnLastBuffer("sinesweepflac.flac");
        testDecodeWithEOSOnLastBuffer("sinesweepflacmp4.mp4");
    }

    @Test
    public void testDecodeOggWithEOSOnLastBuffer() throws Exception {
        testDecodeWithEOSOnLastBuffer("sinesweepogg.ogg");
        testDecodeWithEOSOnLastBuffer("sinesweepoggmkv.mkv");
        testDecodeWithEOSOnLastBuffer("sinesweepoggmp4.mp4");
    }

    /* setting EOS on the last full input buffer should be equivalent to setting EOS on an empty
     * input buffer after all the full ones. */
    private void testDecodeWithEOSOnLastBuffer(final String res) throws Exception {
        int numsamples = countSamples(res);
        assertTrue(numsamples != 0);

        for (String codecName: codecsFor(res)) {
            List<Long> timestamps1 = new ArrayList<Long>();
            short[] decode1 = decodeToMemory(codecName, res,
                    RESET_MODE_NONE, CONFIG_MODE_NONE, -1, timestamps1);

            List<Long> timestamps2 = new ArrayList<Long>();
            short[] decode2 = decodeToMemory(codecName, res,
                    RESET_MODE_NONE, CONFIG_MODE_NONE, numsamples - 1,
                    timestamps2);

            // check that data and timestamps are the same for EOS-on-last and EOS-after-last
            assertEquals(decode1.length, decode2.length);
            assertTrue(Arrays.equals(decode1, decode2));
            assertEquals(timestamps1.size(), timestamps2.size());
            assertTrue(timestamps1.equals(timestamps2));

            // ... and that this is also true when reconfiguring the codec
            timestamps2.clear();
            decode2 = decodeToMemory(codecName, res,
                    RESET_MODE_RECONFIGURE, CONFIG_MODE_NONE, -1, timestamps2);
            assertTrue(Arrays.equals(decode1, decode2));
            assertTrue(timestamps1.equals(timestamps2));
            timestamps2.clear();
            decode2 = decodeToMemory(codecName, res,
                    RESET_MODE_RECONFIGURE, CONFIG_MODE_NONE, numsamples - 1, timestamps2);
            assertEquals(decode1.length, decode2.length);
            assertTrue(Arrays.equals(decode1, decode2));
            assertTrue(timestamps1.equals(timestamps2));

            // ... and that this is also true when flushing the codec
            timestamps2.clear();
            decode2 = decodeToMemory(codecName, res,
                    RESET_MODE_FLUSH, CONFIG_MODE_NONE, -1, timestamps2);
            assertTrue(Arrays.equals(decode1, decode2));
            assertTrue(timestamps1.equals(timestamps2));
            timestamps2.clear();
            decode2 = decodeToMemory(codecName, res,
                    RESET_MODE_FLUSH, CONFIG_MODE_NONE, numsamples - 1,
                    timestamps2);
            assertEquals(decode1.length, decode2.length);
            assertTrue(Arrays.equals(decode1, decode2));
            assertTrue(timestamps1.equals(timestamps2));
        }
    }

    private int countSamples(final String res) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(mInpPrefix + res);
        extractor.selectTrack(0);
        int numsamples = extractor.getSampleTime() < 0 ? 0 : 1;
        while (extractor.advance()) {
            numsamples++;
        }
        return numsamples;
    }

    private void testDecode(final String testVideo, int frameNum) throws Exception {
        if (!MediaUtils.checkCodecForResource(mInpPrefix + testVideo, 0 /* track */)) {
            return; // skip
        }

        // Decode to Surface.
        Surface s = getActivity().getSurfaceHolder().getSurface();
        int frames1 = countFrames(testVideo, RESET_MODE_NONE, -1 /* eosframe */, s);
        assertEquals("wrong number of frames decoded", frameNum, frames1);

        // Decode to buffer.
        int frames2 = countFrames(testVideo, RESET_MODE_NONE, -1 /* eosframe */, null);
        assertEquals("different number of frames when using Surface", frames1, frames2);
    }

    @Test
    public void testCodecBasicH264() throws Exception {
        testDecode("video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4", 240);
    }

    @Test
    public void testCodecBasicHEVC() throws Exception {
        testDecode(
                "bbb_s1_720x480_mp4_hevc_mp3_1600kbps_30fps_aac_he_6ch_240kbps_48000hz.mp4", 300);
    }

    @Test
    public void testCodecBasicH263() throws Exception {
        testDecode("video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz.3gp", 122);
    }

    @Test
    public void testCodecBasicMpeg2() throws Exception {
        testDecode("video_480x360_mp4_mpeg2_1500kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 300);
    }

    @Test
    public void testCodecBasicMpeg4() throws Exception {
        testDecode("video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz.mp4", 249);
    }

    @Test
    public void testCodecBasicVP8() throws Exception {
        testDecode("video_480x360_webm_vp8_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm", 240);
    }

    @Test
    public void testCodecBasicVP9() throws Exception {
        testDecode("video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm", 240);
    }

    @Test
    public void testCodecBasicAV1() throws Exception {
        testDecode("video_480x360_webm_av1_400kbps_30fps_vorbis_stereo_128kbps_48000hz.webm", 300);
    }

    @Test
    public void testH264Decode320x240() throws Exception {
        testDecode("bbb_s1_320x240_mp4_h264_mp2_800kbps_30fps_aac_lc_5ch_240kbps_44100hz.mp4", 300);
    }

    @Test
    public void testH264Decode720x480() throws Exception {
        testDecode("bbb_s1_720x480_mp4_h264_mp3_2mbps_30fps_aac_lc_5ch_320kbps_48000hz.mp4", 300);
    }

    @Test
    public void testH264Decode30fps1280x720Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(
                    MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720, 30,
                    AVCProfileHigh, AVCLevel31, 8000000));
        }
    }

    @Test
    public void testH264SecureDecode30fps1280x720Tv() throws Exception {
        if (checkTv()) {
            verifySecureVideoDecodeSupport(
                    MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720, 30,
                    AVCProfileHigh, AVCLevel31, 8000000);
        }
    }

    @Test
    public void testH264Decode30fps1280x720() throws Exception {
        testDecode("bbb_s4_1280x720_mp4_h264_mp31_8mbps_30fps_aac_he_mono_40kbps_44100hz.mp4", 300);
    }

    @Test
    public void testH264Decode60fps1280x720Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(
                    MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720, 60,
                    AVCProfileHigh, AVCLevel32, 8000000));
            testDecode(
                    "bbb_s3_1280x720_mp4_h264_hp32_8mbps_60fps_aac_he_v2_stereo_48kbps_48000hz.mp4",
                    600);
        }
    }

    @Test
    public void testH264SecureDecode60fps1280x720Tv() throws Exception {
        if (checkTv()) {
            verifySecureVideoDecodeSupport(
                    MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720, 60,
                    AVCProfileHigh, AVCLevel32, 8000000);
        }
    }

    @Test
    public void testH264Decode60fps1280x720() throws Exception {
        testDecode("bbb_s3_1280x720_mp4_h264_mp32_8mbps_60fps_aac_he_v2_6ch_144kbps_44100hz.mp4",
                600);
    }

    @Test
    public void testH264Decode30fps1920x1080Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(
                    MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080, 30,
                    AVCProfileHigh, AVCLevel4, 20000000));
            testDecode(
                    "bbb_s4_1920x1080_wide_mp4_h264_hp4_20mbps_30fps_aac_lc_6ch_384kbps_44100hz.mp4",
                    150);
        }
    }

    @Test
    public void testH264SecureDecode30fps1920x1080Tv() throws Exception {
        if (checkTv()) {
            verifySecureVideoDecodeSupport(
                    MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080, 30,
                    AVCProfileHigh, AVCLevel4, 20000000);
        }
    }

    @Test
    public void testH264Decode30fps1920x1080() throws Exception {
        testDecode("bbb_s4_1920x1080_wide_mp4_h264_mp4_20mbps_30fps_aac_he_5ch_200kbps_44100hz.mp4",
                150);
    }

    @Test
    public void testH264Decode60fps1920x1080Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(
                    MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080, 60,
                    AVCProfileHigh, AVCLevel42, 20000000));
            testDecode("bbb_s2_1920x1080_mp4_h264_hp42_20mbps_60fps_aac_lc_6ch_384kbps_48000hz.mp4",
                    300);
        }
    }

    @Test
    public void testH264SecureDecode60fps1920x1080Tv() throws Exception {
        if (checkTv()) {
            verifySecureVideoDecodeSupport(
                    MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080, 60,
                    AVCProfileHigh, AVCLevel42, 20000000);
        }
    }

    @Test
    public void testH264Decode60fps1920x1080() throws Exception {
        testDecode("bbb_s2_1920x1080_mp4_h264_mp42_20mbps_60fps_aac_he_v2_5ch_160kbps_48000hz.mp4",
                300);
        testDecode("bbb_s2_1920x1080_mkv_h264_mp42_20mbps_60fps_aac_he_v2_5ch_160kbps_48000hz.mkv",
                300);
    }

    @Test
    public void testH265Decode25fps1280x720() throws Exception {
        testDecode("video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv", 240);
    }

    @Test
    public void testVP8Decode320x180() throws Exception {
        testDecode("bbb_s1_320x180_webm_vp8_800kbps_30fps_opus_5ch_320kbps_48000hz.webm", 300);
    }

    @Test
    public void testVP8Decode640x360() throws Exception {
        testDecode("bbb_s1_640x360_webm_vp8_2mbps_30fps_vorbis_5ch_320kbps_48000hz.webm", 300);
    }

    @Test
    public void testVP8Decode30fps1280x720Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(MediaFormat.MIMETYPE_VIDEO_VP8, 1280, 720, 30));
        }
    }

    @Test
    public void testVP8Decode30fps1280x720() throws Exception {
        testDecode("bbb_s4_1280x720_webm_vp8_8mbps_30fps_opus_mono_64kbps_48000hz.webm", 300);
    }

    @Test
    public void testVP8Decode60fps1280x720Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(MediaFormat.MIMETYPE_VIDEO_VP8, 1280, 720, 60));
        }
    }

    @Test
    public void testVP8Decode60fps1280x720() throws Exception {
        testDecode("bbb_s3_1280x720_webm_vp8_8mbps_60fps_opus_6ch_384kbps_48000hz.webm", 600);
    }

    @Test
    public void testVP8Decode30fps1920x1080Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(MediaFormat.MIMETYPE_VIDEO_VP8, 1920, 1080, 30));
        }
    }

    @Test
    public void testVP8Decode30fps1920x1080() throws Exception {
        testDecode("bbb_s4_1920x1080_wide_webm_vp8_20mbps_30fps_vorbis_6ch_384kbps_44100hz.webm",
                150);
    }

    @Test
    public void testVP8Decode60fps1920x1080Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(MediaFormat.MIMETYPE_VIDEO_VP8, 1920, 1080, 60));
        }
    }

    @Test
    public void testVP8Decode60fps1920x1080() throws Exception {
        testDecode("bbb_s2_1920x1080_webm_vp8_20mbps_60fps_vorbis_6ch_384kbps_48000hz.webm", 300);
    }

    @Test
    public void testVP9Decode320x180() throws Exception {
        testDecode("bbb_s1_320x180_webm_vp9_0p11_600kbps_30fps_vorbis_mono_64kbps_48000hz.webm",
                300);
    }

    @Test
    public void testVP9Decode640x360() throws Exception {
        testDecode("bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                300);
    }

    @Test
    public void testVP9Decode30fps1280x720Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(MediaFormat.MIMETYPE_VIDEO_VP9, 1280, 720, 30));
        }
    }

    @Test
    public void testVP9Decode30fps1280x720() throws Exception {
        testDecode("bbb_s4_1280x720_webm_vp9_0p31_4mbps_30fps_opus_stereo_128kbps_48000hz.webm",
                300);
    }

    @Test
    public void testVP9Decode60fps1920x1080() throws Exception {
        testDecode("bbb_s2_1920x1080_webm_vp9_0p41_10mbps_60fps_vorbis_6ch_384kbps_22050hz.webm",
                300);
    }

    @Test
    public void testVP9Decode30fps3840x2160() throws Exception {
        testDecode("bbb_s4_3840x2160_webm_vp9_0p5_20mbps_30fps_vorbis_6ch_384kbps_24000hz.webm",
                150);
    }

    @Test
    public void testVP9Decode60fps3840x2160() throws Exception {
        testDecode("bbb_s2_3840x2160_webm_vp9_0p51_20mbps_60fps_vorbis_6ch_384kbps_32000hz.webm",
                300);
    }

    @Test
    public void testAV1Decode320x180() throws Exception {
        testDecode("video_320x180_webm_av1_200kbps_30fps_vorbis_stereo_128kbps_48000hz.webm", 300);
    }

    @Test
    public void testAV1Decode640x360() throws Exception {
        testDecode("video_640x360_webm_av1_470kbps_30fps_vorbis_stereo_128kbps_48000hz.webm", 300);
    }

    @Test
    public void testAV1Decode30fps1280x720() throws Exception {
        testDecode("video_1280x720_webm_av1_2000kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                300);
    }

    @Test
    public void testAV1Decode60fps1920x1080() throws Exception {
        testDecode("video_1920x1080_webm_av1_7000kbps_60fps_vorbis_stereo_128kbps_48000hz.webm",
                300);
    }

    @Test
    public void testAV1Decode30fps3840x2160() throws Exception {
        testDecode("video_3840x2160_webm_av1_11000kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                150);
    }

    @Test
    public void testAV1Decode60fps3840x2160() throws Exception {
        testDecode("video_3840x2160_webm_av1_18000kbps_60fps_vorbis_stereo_128kbps_48000hz.webm",
                300);
    }

    @Test
    public void testHEVCDecode352x288() throws Exception {
        testDecode("bbb_s1_352x288_mp4_hevc_mp2_600kbps_30fps_aac_he_stereo_96kbps_48000hz.mp4",
                300);
    }

    @Test
    public void testHEVCDecode720x480() throws Exception {
        testDecode("bbb_s1_720x480_mp4_hevc_mp3_1600kbps_30fps_aac_he_6ch_240kbps_48000hz.mp4",
                300);
    }

    @Test
    public void testHEVCDecode30fps1280x720Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(
                    MediaFormat.MIMETYPE_VIDEO_HEVC, 1280, 720, 30,
                    HEVCProfileMain, HEVCMainTierLevel31, 4000000));
        }
    }

    @Test
    public void testHEVCDecode30fps1280x720() throws Exception {
        testDecode("bbb_s4_1280x720_mp4_hevc_mp31_4mbps_30fps_aac_he_stereo_80kbps_32000hz.mp4",
                300);
    }

    @Test
    public void testHEVCDecode30fps1920x1080Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(
                    MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080, 30,
                    HEVCProfileMain, HEVCMainTierLevel41, 5000000));
        }
    }

    @Test
    public void testHEVCDecode60fps1920x1080() throws Exception {
        testDecode("bbb_s2_1920x1080_mp4_hevc_mp41_10mbps_60fps_aac_lc_6ch_384kbps_22050hz.mp4",
                300);
    }

    @Test
    public void testHEVCDecode30fps3840x2160() throws Exception {
        testDecode("bbb_s4_3840x2160_mp4_hevc_mp5_20mbps_30fps_aac_lc_6ch_384kbps_24000hz.mp4",
                150);
    }

    @Test
    public void testHEVCDecode60fps3840x2160() throws Exception {
        testDecode("bbb_s2_3840x2160_mp4_hevc_mp51_20mbps_60fps_aac_lc_6ch_384kbps_32000hz.mp4",
                300);
    }

    @Test
    public void testMpeg2Decode352x288() throws Exception {
        testDecode("video_352x288_mp4_mpeg2_1000kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 300);
    }

    @Test
    public void testMpeg2Decode720x480() throws Exception {
        testDecode("video_720x480_mp4_mpeg2_2000kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 300);
    }

    @Test
    public void testMpeg2Decode30fps1280x720Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(MediaFormat.MIMETYPE_VIDEO_MPEG2, 1280, 720, 30));
        }
    }

    @Test
    public void testMpeg2Decode30fps1280x720() throws Exception {
        testDecode("video_1280x720_mp4_mpeg2_6000kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 150);
    }

    @Test
    public void testMpeg2Decode30fps1920x1080Tv() throws Exception {
        if (checkTv()) {
            assertTrue(MediaUtils.canDecodeVideo(MediaFormat.MIMETYPE_VIDEO_MPEG2, 1920, 1080, 30));
        }
    }

    @Test
    public void testMpeg2Decode30fps1920x1080() throws Exception {
        testDecode("video_1920x1080_mp4_mpeg2_12000kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 150);
    }

    @Test
    public void testMpeg2Decode30fps3840x2160() throws Exception {
        testDecode("video_3840x2160_mp4_mpeg2_20000kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 150);
    }

    private void testCodecEarlyEOS(final String res, int eosFrame) throws Exception {
        if (!MediaUtils.checkCodecForResource(mInpPrefix + res, 0 /* track */)) {
            return; // skip
        }
        Surface s = getActivity().getSurfaceHolder().getSurface();
        int frames1 = countFrames(res, RESET_MODE_NONE, eosFrame, s);
        assertEquals("wrong number of frames decoded", eosFrame, frames1);
    }

    @Test
    public void testCodecEarlyEOSH263() throws Exception {
        testCodecEarlyEOS("video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz.3gp",
                64 /* eosframe */);
    }

    @Test
    public void testCodecEarlyEOSH264() throws Exception {
        testCodecEarlyEOS("video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                120 /* eosframe */);
    }

    @Test
    public void testCodecEarlyEOSHEVC() throws Exception {
        testCodecEarlyEOS("video_480x360_mp4_hevc_650kbps_30fps_aac_stereo_128kbps_48000hz.mp4",
                120 /* eosframe */);
    }

    @Test
    public void testCodecEarlyEOSMpeg2() throws Exception {
        testCodecEarlyEOS("vdeo_480x360_mp4_mpeg2_1500kbps_30fps_aac_stereo_128kbps_48000hz.mp4",
                120 /* eosframe */);
    }

    @Test
    public void testCodecEarlyEOSMpeg4() throws Exception {
        testCodecEarlyEOS("video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                120 /* eosframe */);
    }

    @Test
    public void testCodecEarlyEOSVP8() throws Exception {
        testCodecEarlyEOS("video_480x360_webm_vp8_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm",
                120 /* eosframe */);
    }

    @Test
    public void testCodecEarlyEOSVP9() throws Exception {
        testCodecEarlyEOS(
                "video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm",
                120 /* eosframe */);
    }

    @Test
    public void testCodecEarlyEOSAV1() throws Exception {
        testCodecEarlyEOS("video_480x360_webm_av1_400kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                120 /* eosframe */);
    }

    @Test
    public void testCodecResetsH264WithoutSurface() throws Exception {
        testCodecResets("video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                null);
    }

    @Test
    public void testCodecResetsH264WithSurface() throws Exception {
        Surface s = getActivity().getSurfaceHolder().getSurface();
        testCodecResets("video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4", s);
    }

    @Test
    public void testCodecResetsHEVCWithoutSurface() throws Exception {
        testCodecResets("bbb_s1_720x480_mp4_hevc_mp3_1600kbps_30fps_aac_he_6ch_240kbps_48000hz.mp4",
                null);
    }

    @Test
    public void testCodecResetsHEVCWithSurface() throws Exception {
        Surface s = getActivity().getSurfaceHolder().getSurface();
        testCodecResets("bbb_s1_720x480_mp4_hevc_mp3_1600kbps_30fps_aac_he_6ch_240kbps_48000hz.mp4",
                s);
    }

    @Test
    public void testCodecResetsMpeg2WithoutSurface() throws Exception {
        testCodecResets("video_1280x720_mp4_mpeg2_6000kbps_30fps_aac_stereo_128kbps_48000hz.mp4",
                null);
    }

    @Test
    public void testCodecResetsMpeg2WithSurface() throws Exception {
        Surface s = getActivity().getSurfaceHolder().getSurface();
        testCodecResets("video_176x144_mp4_mpeg2_105kbps_25fps_aac_stereo_128kbps_44100hz.mp4", s);
    }

    @Test
    public void testCodecResetsH263WithoutSurface() throws Exception {
        testCodecResets("video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz.3gp",null);
    }

    @Test
    public void testCodecResetsH263WithSurface() throws Exception {
        Surface s = getActivity().getSurfaceHolder().getSurface();
        testCodecResets("video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz.3gp", s);
    }

    @Test
    public void testCodecResetsMpeg4WithoutSurface() throws Exception {
        testCodecResets("video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                null);
    }

    @Test
    public void testCodecResetsMpeg4WithSurface() throws Exception {
        Surface s = getActivity().getSurfaceHolder().getSurface();
        testCodecResets("video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz.mp4", s);
    }

    @Test
    public void testCodecResetsVP8WithoutSurface() throws Exception {
        testCodecResets("video_480x360_webm_vp8_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm",
                null);
    }

    @Test
    public void testCodecResetsVP8WithSurface() throws Exception {
        Surface s = getActivity().getSurfaceHolder().getSurface();
        testCodecResets("video_480x360_webm_vp8_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm",
                s);
    }

    @Test
    public void testCodecResetsVP9WithoutSurface() throws Exception {
        testCodecResets("video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm",
                null);
    }

    @Test
    public void testCodecResetsAV1WithoutSurface() throws Exception {
        testCodecResets("video_480x360_webm_av1_400kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                null);
    }

    @Test
    public void testCodecResetsVP9WithSurface() throws Exception {
        Surface s = getActivity().getSurfaceHolder().getSurface();
        testCodecResets("video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm",
                s);
    }

    @Test
    public void testCodecResetsAV1WithSurface() throws Exception {
        Surface s = getActivity().getSurfaceHolder().getSurface();
        testCodecResets("video_480x360_webm_av1_400kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                s);
    }

//    public void testCodecResetsOgg() throws Exception {
//        testCodecResets("sinesweepogg.ogg", null);
//    }

    @Test
    public void testCodecResetsMp3() throws Exception {
        testCodecReconfig("sinesweepmp3lame.mp3");
        // NOTE: replacing testCodecReconfig call soon
//        testCodecResets("sinesweepmp3lame.mp3, null);
    }

    @Test
    public void testCodecResetsM4a() throws Exception {
        testCodecReconfig("sinesweepm4a.m4a");
        // NOTE: replacing testCodecReconfig call soon
//        testCodecResets("sinesweepm4a.m4a", null);
    }

    private void testCodecReconfig(final String audio) throws Exception {
        int size1 = countSize(audio, RESET_MODE_NONE, -1 /* eosframe */);
        int size2 = countSize(audio, RESET_MODE_RECONFIGURE, -1 /* eosframe */);
        assertEquals("different output size when using reconfigured codec", size1, size2);
    }

    private void testCodecResets(final String video, Surface s) throws Exception {
        if (!MediaUtils.checkCodecForResource(mInpPrefix + video, 0 /* track */)) {
            return; // skip
        }

        int frames1 = countFrames(video, RESET_MODE_NONE, -1 /* eosframe */, s);
        int frames2 = countFrames(video, RESET_MODE_RECONFIGURE, -1 /* eosframe */, s);
        int frames3 = countFrames(video, RESET_MODE_FLUSH, -1 /* eosframe */, s);
        assertEquals("different number of frames when using reconfigured codec", frames1, frames2);
        assertEquals("different number of frames when using flushed codec", frames1, frames3);
    }

    private static void verifySecureVideoDecodeSupport(
            String mime, int width, int height, float rate, int profile, int level, int bitrate) {
        MediaFormat baseFormat = new MediaFormat();
        baseFormat.setString(MediaFormat.KEY_MIME, mime);
        baseFormat.setFeatureEnabled(CodecCapabilities.FEATURE_SecurePlayback, true);

        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        format.setFeatureEnabled(CodecCapabilities.FEATURE_SecurePlayback, true);
        format.setFloat(MediaFormat.KEY_FRAME_RATE, rate);
        format.setInteger(MediaFormat.KEY_PROFILE, profile);
        format.setInteger(MediaFormat.KEY_LEVEL, level);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);

        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        if (mcl.findDecoderForFormat(baseFormat) == null) {
            MediaUtils.skipTest("no secure decoder for " + mime);
            return;
        }
        assertNotNull("no decoder for " + format, mcl.findDecoderForFormat(format));
    }

    private static MediaCodec createDecoder(MediaFormat format) {
        return MediaUtils.getDecoder(format);
    }

    // for video
    private int countFrames(final String video, int resetMode, int eosframe, Surface s)
            throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(mInpPrefix + video);
        extractor.selectTrack(0);

        int numframes = decodeWithChecks(null /* decoderName */, extractor,
                CHECKFLAG_RETURN_OUTPUTFRAMES | CHECKFLAG_COMPAREINPUTOUTPUTPTSMATCH,
                resetMode, s, eosframe, null, null);

        extractor.release();
        return numframes;
    }

    // for audio
    private int countSize(final String audio, int resetMode, int eosframe)
            throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(mInpPrefix + audio);

        extractor.selectTrack(0);

        // fails CHECKFLAG_COMPAREINPUTOUTPUTPTSMATCH
        int outputSize = decodeWithChecks(null /* decoderName */, extractor,
                CHECKFLAG_RETURN_OUTPUTSIZE, resetMode, null,
                eosframe, null, null);

        extractor.release();
        return outputSize;
    }

    /*
    * Test all decoders' EOS behavior.
    */
    private void testEOSBehavior(final String movie, int stopatsample) throws Exception {
        testEOSBehavior(movie, new int[] {stopatsample});
    }

    /*
    * Test all decoders' EOS behavior.
    */
    private void testEOSBehavior(final String movie, int[] stopAtSample) throws Exception {
        Surface s = null;
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(mInpPrefix + movie);
        extractor.selectTrack(0); // consider variable looping on track
        MediaFormat format = extractor.getTrackFormat(0);

        String[] decoderNames = MediaUtils.getDecoderNames(format);
        for (String decoderName: decoderNames) {
            List<Long> outputChecksums = new ArrayList<Long>();
            List<Long> outputTimestamps = new ArrayList<Long>();
            Arrays.sort(stopAtSample);
            int last = stopAtSample.length - 1;

            // decode reference (longest sequence to stop at + 100) and
            // store checksums/pts in outputChecksums and outputTimestamps
            // (will fail CHECKFLAG_COMPAREINPUTOUTPUTSAMPLEMATCH)
            decodeWithChecks(decoderName, extractor,
                    CHECKFLAG_SETCHECKSUM | CHECKFLAG_SETPTS | CHECKFLAG_COMPAREINPUTOUTPUTPTSMATCH,
                    RESET_MODE_NONE, s,
                    stopAtSample[last] + 100, outputChecksums, outputTimestamps);

            // decode stopAtSample requests in reverse order (longest to
            // shortest) and compare to reference checksums/pts in
            // outputChecksums and outputTimestamps
            for (int i = last; i >= 0; --i) {
                if (true) { // reposition extractor
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                } else { // create new extractor
                    extractor.release();
                    extractor = new MediaExtractor();
                    extractor.setDataSource(mInpPrefix + movie);
                    extractor.selectTrack(0); // consider variable looping on track
                }
                decodeWithChecks(decoderName, extractor,
                        CHECKFLAG_COMPARECHECKSUM | CHECKFLAG_COMPAREPTS
                        | CHECKFLAG_COMPAREINPUTOUTPUTSAMPLEMATCH
                        | CHECKFLAG_COMPAREINPUTOUTPUTPTSMATCH,
                        RESET_MODE_NONE, s,
                        stopAtSample[i], outputChecksums, outputTimestamps);
            }
            extractor.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
        }

        extractor.release();
    }

    private static final int CHECKFLAG_SETCHECKSUM = 1 << 0;
    private static final int CHECKFLAG_COMPARECHECKSUM = 1 << 1;
    private static final int CHECKFLAG_SETPTS = 1 << 2;
    private static final int CHECKFLAG_COMPAREPTS = 1 << 3;
    private static final int CHECKFLAG_COMPAREINPUTOUTPUTSAMPLEMATCH = 1 << 4;
    private static final int CHECKFLAG_COMPAREINPUTOUTPUTPTSMATCH = 1 << 5;
    private static final int CHECKFLAG_RETURN_OUTPUTFRAMES = 1 << 6;
    private static final int CHECKFLAG_RETURN_OUTPUTSIZE = 1 << 7;

    /**
     * Decodes frames with parameterized checks and return values.
     * If decoderName is provided, mediacodec will create that decoder. Otherwise,
     * mediacodec will use the default decoder provided by platform.
     * The integer return can be selected through the checkFlags variable.
     */
    private static int decodeWithChecks(
            String decoderName, MediaExtractor extractor,
            int checkFlags, int resetMode, Surface surface, int stopAtSample,
            List<Long> outputChecksums, List<Long> outputTimestamps)
            throws Exception {
        int trackIndex = extractor.getSampleTrackIndex();
        MediaFormat format = extractor.getTrackFormat(trackIndex);
        String mime = format.getString(MediaFormat.KEY_MIME);
        boolean isAudio = mime.startsWith("audio/");
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        MediaCodec codec =
                decoderName == null ? createDecoder(format) : MediaCodec.createByCodecName(decoderName);
        Log.i("@@@@", "using codec: " + codec.getName());
        codec.configure(format, surface, null /* crypto */, 0 /* flags */);
        codec.start();
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();

        if (resetMode == RESET_MODE_RECONFIGURE) {
            codec.stop();
            codec.configure(format, surface, null /* crypto */, 0 /* flags */);
            codec.start();
            codecInputBuffers = codec.getInputBuffers();
            codecOutputBuffers = codec.getOutputBuffers();
        } else if (resetMode == RESET_MODE_FLUSH) {
            codec.flush();

            // We must always queue CSD after a flush that is potentially
            // before we receive output format has changed.
            queueConfig(codec, format);
        }

        // start decode loop
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        MediaFormat outFormat = codec.getOutputFormat();
        long kTimeOutUs = 5000; // 5ms timeout
        String outMime = format.getString(MediaFormat.KEY_MIME);
        if ((surface == null) && (outMime != null) && outMime.startsWith("video/")) {
            int outWidth = outFormat.getInteger(MediaFormat.KEY_WIDTH);
            int outHeight = outFormat.getInteger(MediaFormat.KEY_HEIGHT);
            // in the 4K decoding case in byte buffer mode, set kTimeOutUs to 10ms as decode may
            // involve a memcpy
            if (outWidth * outHeight >= 8000000) {
                kTimeOutUs = 10000;
            }
        }

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int deadDecoderCounter = 0;
        int samplenum = 0;
        int numframes = 0;
        int outputSize = 0;
        int width = 0;
        int height = 0;
        boolean dochecksum = false;
        ArrayList<Long> timestamps = new ArrayList<Long>();
        if ((checkFlags & CHECKFLAG_SETPTS) != 0) {
            outputTimestamps.clear();
        }
        if ((checkFlags & CHECKFLAG_SETCHECKSUM) != 0) {
            outputChecksums.clear();
        }
        boolean advanceDone = true;
        while (!sawOutputEOS && deadDecoderCounter < 100) {
            // handle input
            if (!sawInputEOS) {
                int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

                    int sampleSize =
                            extractor.readSampleData(dstBuf, 0 /* offset */);
                    assertEquals("end of stream should match extractor.advance()", sampleSize >= 0,
                            advanceDone);
                    long presentationTimeUs = extractor.getSampleTime();
                    advanceDone = extractor.advance();
                    // int flags = extractor.getSampleFlags();
                    // Log.i("@@@@", "read sample " + samplenum + ":" +
                    // extractor.getSampleFlags()
                    // + " @ " + extractor.getSampleTime() + " size " +
                    // sampleSize);

                    if (sampleSize < 0) {
                        assertFalse("advance succeeded after failed read", advanceDone);
                        Log.d(TAG, "saw input EOS.");
                        sawInputEOS = true;
                        assertEquals("extractor.readSampleData() must return -1 at end of stream",
                                -1, sampleSize);
                        assertEquals("extractor.getSampleTime() must return -1 at end of stream",
                                -1, presentationTimeUs);
                        sampleSize = 0; // required otherwise queueInputBuffer
                                        // returns invalid.
                    } else {
                        timestamps.add(presentationTimeUs);
                        samplenum++; // increment before comparing with stopAtSample
                        if (samplenum == stopAtSample) {
                            Log.d(TAG, "saw input EOS (stop at sample).");
                            sawInputEOS = true; // tag this sample as EOS
                        }
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

            deadDecoderCounter++;
            if (outputBufIndex >= 0) {
                if (info.size > 0) { // Disregard 0-sized buffers at the end.
                    deadDecoderCounter = 0;
                    if (resetMode != RESET_MODE_NONE) {
                        // once we've gotten some data out of the decoder, reset
                        // and start again
                        if (resetMode == RESET_MODE_RECONFIGURE) {
                            codec.stop();
                            codec.configure(format, surface /* surface */, null /* crypto */,
                                    0 /* flags */);
                            codec.start();
                            codecInputBuffers = codec.getInputBuffers();
                            codecOutputBuffers = codec.getOutputBuffers();
                        } else if (resetMode == RESET_MODE_FLUSH) {
                            codec.flush();
                        } else {
                            fail("unknown resetMode: " + resetMode);
                        }
                        // restart at beginning, clear resetMode
                        resetMode = RESET_MODE_NONE;
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                        sawInputEOS = false;
                        numframes = 0;
                        timestamps.clear();
                        if ((checkFlags & CHECKFLAG_SETPTS) != 0) {
                            outputTimestamps.clear();
                        }
                        if ((checkFlags & CHECKFLAG_SETCHECKSUM) != 0) {
                            outputChecksums.clear();
                        }
                        continue;
                    }
                    if ((checkFlags & CHECKFLAG_COMPAREPTS) != 0) {
                        assertTrue("number of frames (" + numframes
                                + ") exceeds number of reference timestamps",
                                numframes < outputTimestamps.size());
                        assertEquals("frame ts mismatch at frame " + numframes,
                                (long) outputTimestamps.get(numframes), info.presentationTimeUs);
                    } else if ((checkFlags & CHECKFLAG_SETPTS) != 0) {
                        outputTimestamps.add(info.presentationTimeUs);
                    }
                    if ((checkFlags & (CHECKFLAG_SETCHECKSUM | CHECKFLAG_COMPARECHECKSUM)) != 0) {
                        long sum = 0;   // note: checksum is 0 if buffer format unrecognized
                        if (dochecksum) {
                            Image image = codec.getOutputImage(outputBufIndex);
                            // use image to do crc if it's available
                            // fall back to buffer if image is not available
                            if (image != null) {
                                sum = checksum(image);
                            } else {
                                // TODO: add stride - right now just use info.size (as before)
                                //sum = checksum(codecOutputBuffers[outputBufIndex], width, height,
                                //        stride);
                                ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufIndex);
                                outputBuffer.position(info.offset);
                                sum = checksum(outputBuffer, info.size);
                            }
                        }
                        if ((checkFlags & CHECKFLAG_COMPARECHECKSUM) != 0) {
                            assertTrue("number of frames (" + numframes
                                    + ") exceeds number of reference checksums",
                                    numframes < outputChecksums.size());
                            Log.d(TAG, "orig checksum: " + outputChecksums.get(numframes)
                                    + " new checksum: " + sum);
                            assertEquals("frame data mismatch at frame " + numframes,
                                    (long) outputChecksums.get(numframes), sum);
                        } else if ((checkFlags & CHECKFLAG_SETCHECKSUM) != 0) {
                            outputChecksums.add(sum);
                        }
                    }
                    if ((checkFlags & CHECKFLAG_COMPAREINPUTOUTPUTPTSMATCH) != 0) {
                        assertTrue("output timestamp " + info.presentationTimeUs
                                + " without corresponding input timestamp"
                                , timestamps.remove(info.presentationTimeUs));
                    }
                    outputSize += info.size;
                    numframes++;
                }
                // Log.d(TAG, "got frame, size " + info.size + "/" +
                // info.presentationTimeUs +
                // "/" + numframes + "/" + info.flags);
                codec.releaseOutputBuffer(outputBufIndex, true /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = codec.getOutputFormat();
                if (oformat.containsKey(MediaFormat.KEY_COLOR_FORMAT) &&
                        oformat.containsKey(MediaFormat.KEY_WIDTH) &&
                        oformat.containsKey(MediaFormat.KEY_HEIGHT)) {
                    int colorFormat = oformat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                    width = oformat.getInteger(MediaFormat.KEY_WIDTH);
                    height = oformat.getInteger(MediaFormat.KEY_HEIGHT);
                    dochecksum = isRecognizedFormat(colorFormat); // only checksum known raw
                                                                  // buf formats
                    Log.d(TAG, "checksum fmt: " + colorFormat + " dim " + width + "x" + height);
                } else {
                    dochecksum = false; // check with audio later
                    width = height = 0;
                    Log.d(TAG, "output format has changed to (unknown video) " + oformat);
                }
            } else {
                assertEquals(
                        "codec.dequeueOutputBuffer() unrecognized return index: "
                                + outputBufIndex,
                        MediaCodec.INFO_TRY_AGAIN_LATER, outputBufIndex);
            }
        }
        codec.stop();
        codec.release();

        assertTrue("last frame didn't have EOS", sawOutputEOS);
        if ((checkFlags & CHECKFLAG_COMPAREINPUTOUTPUTSAMPLEMATCH) != 0) {
            assertEquals("I!=O", samplenum, numframes);
            if (stopAtSample != 0) {
                assertEquals("did not stop with right number of frames", stopAtSample, numframes);
            }
        }
        return (checkFlags & CHECKFLAG_RETURN_OUTPUTSIZE) != 0 ? outputSize :
                (checkFlags & CHECKFLAG_RETURN_OUTPUTFRAMES) != 0 ? numframes :
                        0;
    }

    @Test
    public void testEOSBehaviorH264() throws Exception {
        // this video has an I frame at 44
        testEOSBehavior("video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                new int[]{1, 44, 45, 55});
    }
    @Test
    public void testEOSBehaviorHEVC() throws Exception {
        testEOSBehavior("video_480x360_mp4_hevc_650kbps_30fps_aac_stereo_128kbps_48000hz.mp4",
                new int[]{1, 17, 23, 49});
    }

    @Test
    public void testEOSBehaviorMpeg2() throws Exception {
        testEOSBehavior("video_480x360_mp4_mpeg2_1500kbps_30fps_aac_stereo_128kbps_48000hz.mp4",
                17);
        testEOSBehavior("video_480x360_mp4_mpeg2_1500kbps_30fps_aac_stereo_128kbps_48000hz.mp4",
                23);
        testEOSBehavior("video_480x360_mp4_mpeg2_1500kbps_30fps_aac_stereo_128kbps_48000hz.mp4",
                49);
    }

    @Test
    public void testEOSBehaviorH263() throws Exception {
        // this video has an I frame every 12 frames.
        testEOSBehavior("video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz.3gp",
                new int[]{1, 24, 25, 48, 50});
    }

    @Test
    public void testEOSBehaviorMpeg4() throws Exception {
        // this video has an I frame every 12 frames
        testEOSBehavior("video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                new int[]{1, 24, 25, 48, 50, 2});
    }

    @Test
    public void testEOSBehaviorVP8() throws Exception {
        // this video has an I frame at 46
        testEOSBehavior("video_480x360_webm_vp8_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm",
                new int[]{1, 46, 47, 57, 45});
    }

    @Test
    public void testEOSBehaviorVP9() throws Exception {
        // this video has an I frame at 44
        testEOSBehavior("video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm",
                new int[]{1, 44, 45, 55, 43});
    }

    @Test
    public void testEOSBehaviorAV1() throws Exception {
        // this video has an I frame at 44
        testEOSBehavior("video_480x360_webm_av1_400kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                new int[]{1, 44, 45, 55, 43});
    }

    /* from EncodeDecodeTest */
    private static boolean isRecognizedFormat(int colorFormat) {
        // Log.d(TAG, "color format: " + String.format("0x%08x", colorFormat));
        switch (colorFormat) {
        // these are the formats we know how to handle for this test
            case CodecCapabilities.COLOR_FormatYUV420Planar:
            case CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
            case CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar:
                /*
                 * TODO: Check newer formats or ignore.
                 * OMX_SEC_COLOR_FormatNV12Tiled = 0x7FC00002
                 * OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03: N4/N7_2
                 * OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar32m = 0x7FA30C04: N5
                 */
                return true;
            default:
                return false;
        }
    }

    private static long checksum(ByteBuffer buf, int size) {
        int cap = buf.capacity();
        assertTrue("checksum() params are invalid: size = " + size + " cap = " + cap,
                size > 0 && size <= cap);
        CRC32 crc = new CRC32();
        if (buf.hasArray()) {
            crc.update(buf.array(), buf.position() + buf.arrayOffset(), size);
        } else {
            int pos = buf.position();
            final int rdsize = Math.min(4096, size);
            byte bb[] = new byte[rdsize];
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

    private static long checksum(ByteBuffer buf, int width, int height, int stride) {
        int cap = buf.capacity();
        assertTrue("checksum() params are invalid: w x h , s = "
                + width + " x " + height + " , " + stride + " cap = " + cap,
                width > 0 && width <= stride && height > 0 && height * stride <= cap);
        // YUV 4:2:0 should generally have a data storage height 1.5x greater
        // than the declared image height, representing the UV planes.
        //
        // We only check Y frame for now. Somewhat unknown with tiling effects.
        //
        //long tm = System.nanoTime();
        final int lineinterval = 1; // line sampling frequency
        CRC32 crc = new CRC32();
        if (buf.hasArray()) {
            byte b[] = buf.array();
            int offs = buf.arrayOffset();
            for (int i = 0; i < height; i += lineinterval) {
                crc.update(b, i * stride + offs, width);
            }
        } else { // almost always ends up here due to direct buffers
            int pos = buf.position();
            if (true) { // this {} is 80x times faster than else {} below.
                byte[] bb = new byte[width]; // local line buffer
                for (int i = 0; i < height; i += lineinterval) {
                    buf.position(pos + i * stride);
                    buf.get(bb, 0, width);
                    crc.update(bb, 0, width);
                }
            } else {
                for (int i = 0; i < height; i += lineinterval) {
                    buf.position(pos + i * stride);
                    for (int j = 0; j < width; ++j) {
                        crc.update(buf.get());
                    }
                }
            }
            buf.position(pos);
        }
        //tm = System.nanoTime() - tm;
        //Log.d(TAG, "checksum time " + tm);
        return crc.getValue();
    }

    private static long checksum(Image image) {
        int format = image.getFormat();
        assertEquals("unsupported image format", ImageFormat.YUV_420_888, format);

        CRC32 crc = new CRC32();

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        Image.Plane[] planes = image.getPlanes();
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();

            int width, height, rowStride, pixelStride, x, y;
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            if (i == 0) {
                width = imageWidth;
                height = imageHeight;
            } else {
                width = imageWidth / 2;
                height = imageHeight /2;
            }
            // local contiguous pixel buffer
            byte[] bb = new byte[width * height];
            if (buf.hasArray()) {
                byte b[] = buf.array();
                int offs = buf.arrayOffset();
                if (pixelStride == 1) {
                    for (y = 0; y < height; ++y) {
                        System.arraycopy(bb, y * width, b, y * rowStride + offs, width);
                    }
                } else {
                    // do it pixel-by-pixel
                    for (y = 0; y < height; ++y) {
                        int lineOffset = offs + y * rowStride;
                        for (x = 0; x < width; ++x) {
                            bb[y * width + x] = b[lineOffset + x * pixelStride];
                        }
                    }
                }
            } else { // almost always ends up here due to direct buffers
                int pos = buf.position();
                if (pixelStride == 1) {
                    for (y = 0; y < height; ++y) {
                        buf.position(pos + y * rowStride);
                        buf.get(bb, y * width, width);
                    }
                } else {
                    // local line buffer
                    byte[] lb = new byte[rowStride];
                    // do it pixel-by-pixel
                    for (y = 0; y < height; ++y) {
                        buf.position(pos + y * rowStride);
                        // we're only guaranteed to have pixelStride * (width - 1) + 1 bytes
                        buf.get(lb, 0, pixelStride * (width - 1) + 1);
                        for (x = 0; x < width; ++x) {
                            bb[y * width + x] = lb[x * pixelStride];
                        }
                    }
                }
                buf.position(pos);
            }
            crc.update(bb, 0, width * height);
        }

        return crc.getValue();
    }

    @Test
    public void testFlush() throws Exception {
        testFlush("loudsoftwav.wav");
        testFlush("loudsoftogg.ogg");
        testFlush("loudsoftoggmkv.mkv");
        testFlush("loudsoftoggmp4.mp4");
        testFlush("loudsoftmp3.mp3");
        testFlush("loudsoftaac.aac");
        testFlush("loudsoftfaac.m4a");
        testFlush("loudsoftitunes.m4a");
    }

    private void testFlush(final String resource) throws Exception {
        MediaExtractor extractor;
        MediaCodec codec;
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        extractor = new MediaExtractor();
        extractor.setDataSource(mInpPrefix + resource);

        assertEquals("wrong number of tracks", 1, extractor.getTrackCount());
        MediaFormat format = extractor.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        assertTrue("not an audio file", mime.startsWith("audio/"));

        codec = MediaCodec.createDecoderByType(mime);
        assertNotNull("couldn't find codec " + mime, codec);

        codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
        codec.start();
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();

        extractor.selectTrack(0);

        // decode a bit of the first part of the file, and verify the amplitude
        short maxvalue1 = getAmplitude(extractor, codec);

        // flush the codec and seek the extractor a different position, then decode a bit more
        // and check the amplitude
        extractor.seekTo(8000000, 0);
        codec.flush();
        short maxvalue2 = getAmplitude(extractor, codec);

        assertTrue("first section amplitude too low", maxvalue1 > 20000);
        assertTrue("second section amplitude too high", maxvalue2 < 5000);
        codec.stop();
        codec.release();

    }

    private short getAmplitude(MediaExtractor extractor, MediaCodec codec) {
        short maxvalue = 0;
        int numBytesDecoded = 0;
        final long kTimeOutUs = 5000;
        ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while(numBytesDecoded < 44100 * 2) {
            int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);

            if (inputBufIndex >= 0) {
                ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

                int sampleSize = extractor.readSampleData(dstBuf, 0 /* offset */);
                long presentationTimeUs = extractor.getSampleTime();

                codec.queueInputBuffer(
                        inputBufIndex,
                        0 /* offset */,
                        sampleSize,
                        presentationTimeUs,
                        0 /* flags */);

                extractor.advance();
            }
            int res = codec.dequeueOutputBuffer(info, kTimeOutUs);

            if (res >= 0) {

                int outputBufIndex = res;
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                buf.position(info.offset);
                for (int i = 0; i < info.size; i += 2) {
                    short sample = buf.getShort();
                    if (maxvalue < sample) {
                        maxvalue = sample;
                    }
                    int idx = (numBytesDecoded + i) / 2;
                }

                numBytesDecoded += info.size;

                codec.releaseOutputBuffer(outputBufIndex, false /* render */);
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = codec.getOutputFormat();
            }
        }
        return maxvalue;
    }

    /* return true if a particular video feature is supported for the given mimetype */
    private boolean isVideoFeatureSupported(String mimeType, String feature) {
        MediaFormat format = MediaFormat.createVideoFormat( mimeType, 1920, 1080);
        format.setFeatureEnabled(feature, true);
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        String codecName = mcl.findDecoderForFormat(format);
        return (codecName == null) ? false : true;
    }

    /**
     * Test tunneled video playback mode if supported
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void tunneledVideoPlayback(String mimeType, String videoName) throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        // When video codecs are started, large chunks of contiguous physical memory need to be
        // allocated, which, on low-RAM devices, can trigger high CPU usage for moving memory
        // around to create contiguous space for the video decoder. This can cause an increase in
        // startup time for playback.
        ActivityManager activityManager = mContext.getSystemService(ActivityManager.class);
        long firstFrameRenderedTimeoutSeconds = activityManager.isLowRamDevice() ? 3 : 1;

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(firstFrameRenderedTimeoutSeconds));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        final long durationMs = mMediaCodecPlayer.getDuration();
        final long timeOutMs = System.currentTimeMillis() + durationMs + 5 * 1000; // add 5 sec
        while (!mMediaCodecPlayer.isEnded()) {
            assertTrue("Tunneled video playback timeout exceeded",
                    timeOutMs > System.currentTimeMillis());
            Thread.sleep(SLEEP_TIME_MS);
            if (mMediaCodecPlayer.getCurrentPosition() >= mMediaCodecPlayer.getDuration()) {
                Log.d(TAG, "testTunneledVideoPlayback -- current pos = " +
                        mMediaCodecPlayer.getCurrentPosition() +
                        ">= duration = " + mMediaCodecPlayer.getDuration());
                break;
            }
        }
        // mMediaCodecPlayer.reset() handled in TearDown();
    }

    /**
     * Test tunneled video playback mode with HEVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPlaybackHevc() throws Exception {
        tunneledVideoPlayback(MediaFormat.MIMETYPE_VIDEO_HEVC,
                    "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test tunneled video playback mode with AVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPlaybackAvc() throws Exception {
        tunneledVideoPlayback(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test tunneled video playback mode with VP9 if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPlaybackVp9() throws Exception {
        tunneledVideoPlayback(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test tunneled video playback flush if supported
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void testTunneledVideoFlush(String mimeType, String videoName) throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        mMediaCodecPlayer.pause();
        mMediaCodecPlayer.flush();
        // mMediaCodecPlayer.reset() handled in TearDown();
    }

    /**
     * Test tunneled video playback flush with HEVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoFlushHevc() throws Exception {
        testTunneledVideoFlush(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test tunneled video playback flush with AVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoFlushAvc() throws Exception {
        testTunneledVideoFlush(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test tunneled video playback flush with VP9 if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoFlushVp9() throws Exception {
        testTunneledVideoFlush(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test that the first frame is rendered when video peek is on in tunneled mode.
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void testTunneledVideoPeekOn(String mimeType, String videoName, float frameRate)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        // Setup tunnel mode test media player
        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        // Frame rate is needed by some devices to initialize the display hardware
        mMediaCodecPlayer.setFrameRate(frameRate);

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();
        mMediaCodecPlayer.setVideoPeek(true); // Enable video peek

        // Queue the first video frame, which should not be rendered imminently
        mMediaCodecPlayer.queueOneVideoFrame();

        // Assert that onFirstTunnelFrameReady is called
        final int waitForFrameReadyMs = 150;
        Thread.sleep(waitForFrameReadyMs);
        assertTrue(String.format("onFirstTunnelFrameReady not called within %d milliseconds",
                        waitForFrameReadyMs),
                mMediaCodecPlayer.isFirstTunnelFrameReady());

        // This is long due to high-latency display pipelines on TV devices
        final int waitForRenderingMs = 1000;
        Thread.sleep(waitForRenderingMs);

        // Assert that video peek is enabled and working
        assertNotEquals(String.format("First frame not rendered within %d milliseconds",
                        waitForRenderingMs), CodecState.UNINITIALIZED_TIMESTAMP,
                mMediaCodecPlayer.getCurrentPosition());

        // mMediaCodecPlayer.reset() handled in TearDown();
    }

    /**
     * Test that the first frame is rendered when video peek is on for HEVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOnHevc() throws Exception {
        // Requires vendor support of the TUNNEL_PEEK feature
        Assume.assumeTrue("First API level is not Android 12 or later.",
                WAS_LAUNCHED_ON_S_OR_LATER);
        testTunneledVideoPeekOn(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv", 25);
    }

    /**
     * Test that the first frame is rendered when video peek is on for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOnAvc() throws Exception {
        // Requires vendor support of the TUNNEL_PEEK feature
        Assume.assumeTrue("First API level is not Android 12 or later.",
                WAS_LAUNCHED_ON_S_OR_LATER);
        testTunneledVideoPeekOn(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4", 25);
    }

    /**
     * Test that the first frame is rendered when video peek is on for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOnVp9() throws Exception {
        // Requires vendor support of the TUNNEL_PEEK feature
        Assume.assumeTrue("First API level is not Android 12 or later.",
                WAS_LAUNCHED_ON_S_OR_LATER);
        testTunneledVideoPeekOn(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                30);
    }

    /**
     * Test that peek off doesn't render the first frame until turned on in tunneled mode.
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void testTunneledVideoPeekOff(String mimeType, String videoName, float frameRate)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        // Setup tunnel mode test media player
        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        // Frame rate is needed by some devices to initialize the display hardware
        mMediaCodecPlayer.setFrameRate(frameRate);

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();
        mMediaCodecPlayer.setVideoPeek(false); // Disable video peek

        // Queue the first video frame, which should not be rendered yet
        mMediaCodecPlayer.queueOneVideoFrame();

        // Assert that onFirstTunnelFrameReady is called
        final int waitForFrameReadyMs = 150;
        Thread.sleep(waitForFrameReadyMs);
        assertTrue(String.format("onFirstTunnelFrameReady not called within %d milliseconds",
                        waitForFrameReadyMs),
                mMediaCodecPlayer.isFirstTunnelFrameReady());

        // This is long due to high-latency display pipelines on TV devices
        final int waitForRenderingMs = 1000;
        Thread.sleep(waitForRenderingMs);

        // Assert the video frame has not been peeked yet
        assertEquals("First frame rendered while peek disabled", CodecState.UNINITIALIZED_TIMESTAMP,
                mMediaCodecPlayer.getCurrentPosition());

        // Enable video peek
        mMediaCodecPlayer.setVideoPeek(true);
        Thread.sleep(waitForRenderingMs);

        // Assert that the first frame was rendered
        assertNotEquals(String.format(
                        "First frame not rendered within %d milliseconds after peek is enabled",
                        waitForRenderingMs), CodecState.UNINITIALIZED_TIMESTAMP,
                mMediaCodecPlayer.getCurrentPosition());

        // mMediaCodecPlayer.reset() handled in TearDown();
    }

    /**
     * Test that peek off doesn't render the first frame until turned on for HEC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOffHevc() throws Exception {
        // Requires vendor support of the TUNNEL_PEEK feature
        Assume.assumeTrue("First API level is not Android 12 or later.",
                WAS_LAUNCHED_ON_S_OR_LATER);
        testTunneledVideoPeekOff(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv", 25);
    }

    /**
     * Test that peek off doesn't render the first frame until turned on for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOffAvc() throws Exception {
        // Requires vendor support of the TUNNEL_PEEK feature
        Assume.assumeTrue("First API level is not Android 12 or later.",
                WAS_LAUNCHED_ON_S_OR_LATER);
        testTunneledVideoPeekOff(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4", 25);
    }

    /**
     * Test that peek off doesn't render the first frame until turned on for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodec#PARAMETER_KEY_TUNNEL_PEEK"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledVideoPeekOffVp9() throws Exception {
        // Requires vendor support of the TUNNEL_PEEK feature
        Assume.assumeTrue("First API level is not Android 12 or later.",
                WAS_LAUNCHED_ON_S_OR_LATER);
        testTunneledVideoPeekOff(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm",
                30);
    }

   /**
    * Test that audio timestamps don't progress during audio PTS gaps in tunneled mode.
    *
    * See: https://source.android.com/docs/devices/tv/multimedia-tunneling#behavior
    */
   private void testTunneledAudioProgressWithPtsGaps(String mimeType, String fileName)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = mContext.getSystemService(AudioManager.class);

        mMediaCodecPlayer = new MediaCodecTunneledPlayer(mContext,
                getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        final Uri mediaUri = Uri.fromFile(new File(mInpPrefix, fileName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        // After 100 ms of playback, simulate a PTS gap of 100 ms
        Thread.sleep(100);
        mMediaCodecPlayer.setAudioTrackOffsetNs(100L * 1000000);

        // Verify that at some point in time in the future, the framePosition stopped advancing.
        // This should happen when the PTS gap is encountered - silence is rendered to fill the
        // PTS gap, but this silence should not cause framePosition to advance.
        {
            final long ptsGapTimeoutMs = 3000;
            long startTimeMs = System.currentTimeMillis();
            AudioTimestamp previousTimestamp;
            do {
                assertTrue(String.format("No audio PTS gap after %d milliseconds", ptsGapTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < ptsGapTimeoutMs);
                previousTimestamp = mMediaCodecPlayer.getTimestamp();
                Thread.sleep(50);
            } while (mMediaCodecPlayer.getTimestamp().framePosition
                    != previousTimestamp.framePosition);
        }

        // Allow the playback to advance past the PTS gap and back to normal operation
        Thread.sleep(500);

        // Sleep till framePosition stabilizes, i.e. playback is complete
        {
            long endOfPlayackTimeoutMs = 20000;
            long startTimeMs = System.currentTimeMillis();
            AudioTimestamp previousTimestamp;
            do {
                assertTrue(String.format("No end of playback after %d milliseconds",
                                endOfPlayackTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < endOfPlayackTimeoutMs);
                previousTimestamp = mMediaCodecPlayer.getTimestamp();
                Thread.sleep(100);
            } while (mMediaCodecPlayer.getTimestamp().framePosition
                    != previousTimestamp.framePosition);
        }

        // Verify if number of frames written and played are same even if PTS gaps were present
        // in the playback.
        assertEquals("Number of frames written != Number of frames played",
                mMediaCodecPlayer.getAudioFramesWritten(),
                mMediaCodecPlayer.getTimestamp().framePosition);
    }

    /**
     * Test that audio timestamps don't progress during audio PTS gaps for HEVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPtsGapsHevc() throws Exception {
        testTunneledAudioProgressWithPtsGaps(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test that audio timestamps don't progress during audio PTS gaps for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPtsGapsAvc() throws Exception {
        testTunneledAudioProgressWithPtsGaps(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test that audio timestamps don't progress during audio PTS gaps for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPtsGapsVp9() throws Exception {
        testTunneledAudioProgressWithPtsGaps(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test that audio timestamps stop progressing during underrun in tunneled mode.
     *
     * See: https://source.android.com/docs/devices/tv/multimedia-tunneling#behavior
     */
    private void testTunneledAudioProgressWithUnderrun(String mimeType, String fileName)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = mContext.getSystemService(AudioManager.class);

        mMediaCodecPlayer = new MediaCodecTunneledPlayer(mContext,
                getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        final Uri mediaUri = Uri.fromFile(new File(mInpPrefix, fileName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        // After 200 ms of playback, stop writing to the AudioTrack to simulate underrun
        Thread.sleep(200);
        mMediaCodecPlayer.stopDrainingAudioOutputBuffers(true);

        // Sleep till framePosition stabilizes, i.e. AudioTrack is in an underrun condition
        {
            long endOfPlayackTimeoutMs = 3000;
            long startTimeMs = System.currentTimeMillis();
            AudioTimestamp previousTimestamp;
            do {
                assertTrue(String.format("No underrun after %d milliseconds",
                                endOfPlayackTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < endOfPlayackTimeoutMs);
                previousTimestamp = mMediaCodecPlayer.getTimestamp();
                Thread.sleep(100);
            } while (mMediaCodecPlayer.getTimestamp().framePosition
                    != previousTimestamp.framePosition);
        }

        // After 200 ms of starving the AudioTrack, resume writing
        Thread.sleep(200);
        mMediaCodecPlayer.stopDrainingAudioOutputBuffers(false);

        // Sleep till framePosition stabilizes, i.e. playback is complete
        {
            long endOfPlayackTimeoutMs = 20000;
            long startTimeMs = System.currentTimeMillis();
            AudioTimestamp previousTimestamp;
            do {
                assertTrue(String.format("No end of playback after %d milliseconds",
                                endOfPlayackTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < endOfPlayackTimeoutMs);
                previousTimestamp = mMediaCodecPlayer.getTimestamp();
                Thread.sleep(100);
            } while (mMediaCodecPlayer.getTimestamp().framePosition
                    != previousTimestamp.framePosition);
        }

        // Verify if number of frames written and played are same even if an underrun condition
        // occurs.
        assertEquals("Number of frames written != Number of frames played",
                mMediaCodecPlayer.getAudioFramesWritten(),
                mMediaCodecPlayer.getTimestamp().framePosition);
    }

    /**
     * Test that audio timestamps stop progressing during underrun for HEVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithUnderrunHevc() throws Exception {
        testTunneledAudioProgressWithUnderrun(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test that audio timestamps stop progressing during underrun for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithUnderrunAvc() throws Exception {
        testTunneledAudioProgressWithUnderrun(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test that audio timestamps stop progressing during underrun for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithUnderrunVp9() throws Exception {
        testTunneledAudioProgressWithUnderrun(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test accurate video rendering after a flush in tunneled mode.
     *
     * Test On some devices, queuing content when the player is paused, then triggering a flush,
     * then queuing more content does not behave as expected. The queued content gets lost and the
     * flush is really only applied once playback has resumed.
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void testTunneledAccurateVideoFlush(String mimeType, String videoName)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        // Below are some timings used throughout this test.
        //
        // Maximum allowed time between start of playback and first frame displayed
        final long maxAllowedTimeToFirstFrameMs = 500;
        // Maximum allowed time between issuing a pause and the last frame being displayed
        final long maxDrainTimeMs = 200;

        // Setup tunnel mode test media player
        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();
        // Video peek might interfere with the test: we want to ensure that queuing more data during
        // a pause does not cause displaying more video frames, which is precisely what video peek
        // does.
        mMediaCodecPlayer.setVideoPeek(false);

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        // Allow some time for playback to commence
        Thread.sleep(500);

        // Pause playback
        mMediaCodecPlayer.pause();

        // Wait for audio to pause
        AudioTimestamp pauseAudioTimestamp;
        {
            AudioTimestamp currentAudioTimestamp = mMediaCodecPlayer.getTimestamp();
            long startTimeMs = System.currentTimeMillis();
            do {
                // If it takes longer to pause, the UX won't feel responsive to the user
                int audioPauseTimeoutMs = 250;
                assertTrue(String.format("No audio pause after %d milliseconds",
                                audioPauseTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < audioPauseTimeoutMs);
                pauseAudioTimestamp = currentAudioTimestamp;
                Thread.sleep(50);
                currentAudioTimestamp = mMediaCodecPlayer.getTimestamp();
            } while (currentAudioTimestamp.framePosition != pauseAudioTimestamp.framePosition);
        }
        long pauseAudioSystemTimeMs = pauseAudioTimestamp.nanoTime / 1000 / 1000;

        // Wait for video to pause
        long pauseVideoSystemTimeNs;
        long pauseVideoPositionUs;
        {
            long currentVideoSystemTimeNs = mMediaCodecPlayer.getCurrentRenderedSystemTimeNano();
            long startTimeMs = System.currentTimeMillis();
            do {
                int videoUnderrunTimeoutMs = 2000;
                assertTrue(String.format("No video pause after %d milliseconds",
                                videoUnderrunTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < videoUnderrunTimeoutMs);
                pauseVideoSystemTimeNs = currentVideoSystemTimeNs;
                Thread.sleep(250); // onFrameRendered can get delayed in the Framework
                currentVideoSystemTimeNs = mMediaCodecPlayer.getCurrentRenderedSystemTimeNano();
            } while (currentVideoSystemTimeNs != pauseVideoSystemTimeNs);
            pauseVideoPositionUs = mMediaCodecPlayer.getVideoTimeUs();
        }
        long pauseVideoSystemTimeMs = pauseVideoSystemTimeNs / 1000 / 1000;

        // Video should not continue running for a long period of time after audio pauses
        long pauseVideoToleranceMs = 500;
        assertTrue(String.format(
                        "Video ran %d milliseconds longer than audio (video:%d audio:%d)",
                        pauseVideoToleranceMs, pauseVideoSystemTimeMs, pauseAudioSystemTimeMs),
                pauseVideoSystemTimeMs - pauseAudioSystemTimeMs < pauseVideoToleranceMs);

        // Verify that playback stays paused
        Thread.sleep(500);
        assertEquals(mMediaCodecPlayer.getTimestamp().framePosition,
                pauseAudioTimestamp.framePosition);
        assertEquals(mMediaCodecPlayer.getCurrentRenderedSystemTimeNano(), pauseVideoSystemTimeNs);
        assertEquals(mMediaCodecPlayer.getVideoTimeUs(), pauseVideoPositionUs);

        // Verify audio and video are roughly in sync when paused
        long framePosition = mMediaCodecPlayer.getTimestamp().framePosition;
        long playbackRateFps = mMediaCodecPlayer.getAudioTrack().getPlaybackRate();
        long pauseAudioPositionMs = pauseAudioTimestamp.framePosition * 1000 / playbackRateFps;
        long pauseVideoPositionMs = pauseVideoPositionUs / 1000;
        long deltaMs = pauseVideoPositionMs - pauseAudioPositionMs;
        assertTrue(String.format(
                        "Video is %d milliseconds out of sync from audio (video:%d audio:%d)",
                        deltaMs, pauseVideoPositionMs, pauseAudioPositionMs),
                deltaMs > -80 && deltaMs < pauseVideoToleranceMs);

        // Flush both audio and video pipelines
        mMediaCodecPlayer.flush();

        // The flush should not cause any frame to be displayed.
        // Wait for the max startup latency to see if one (incorrectly) arrives.
        Thread.sleep(maxAllowedTimeToFirstFrameMs);
        assertEquals("Video frame rendered after flush", mMediaCodecPlayer.getVideoTimeUs(),
                CodecState.UNINITIALIZED_TIMESTAMP);

        // Ensure video peek is disabled before queuing the next frame, otherwise it will
        // automatically be rendered when queued.
        mMediaCodecPlayer.setVideoPeek(false);

        // We rewind to the beginning of the stream (to a key frame) and queue one frame, but
        // pretend like we're seeking 1 second forward in the stream.
        long presentationTimeOffsetUs = pauseVideoPositionUs + 1000 * 1000;
        mMediaCodecPlayer.seekToBeginning(presentationTimeOffsetUs);
        Long queuedVideoTimestamp = mMediaCodecPlayer.queueOneVideoFrame();
        assertNotNull("Failed to queue a video frame", queuedVideoTimestamp);

        // The enqueued frame should not be rendered while we're paused.
        // Wait for the max startup latency to see if it (incorrectly) arrives.
        Thread.sleep(maxAllowedTimeToFirstFrameMs);
        assertEquals("Video frame rendered during pause", mMediaCodecPlayer.getVideoTimeUs(),
                CodecState.UNINITIALIZED_TIMESTAMP);

        // Resume playback
        mMediaCodecPlayer.resume();
        Thread.sleep(maxAllowedTimeToFirstFrameMs);
        // Verify that the first rendered frame was the first queued frame
        ImmutableList<Long> renderedVideoTimestamps =
                mMediaCodecPlayer.getRenderedVideoFrameTimestampList();
        assertFalse(String.format("No frame rendered after resume within %d ms",
                        maxAllowedTimeToFirstFrameMs), renderedVideoTimestamps.isEmpty());
        assertEquals("First rendered video frame does not match first queued video frame",
                renderedVideoTimestamps.get(0), queuedVideoTimestamp);
        // mMediaCodecPlayer.reset() handled in TearDown();
    }

    /**
     * Test accurate video rendering after a video MediaCodec flush with HEVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAccurateVideoFlushHevc() throws Exception {
        testTunneledAccurateVideoFlush(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test accurate video rendering after a video MediaCodec flush with AVC if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAccurateVideoFlushAvc() throws Exception {
        testTunneledAccurateVideoFlush(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test accurate video rendering after a video MediaCodec flush with VP9 if supported
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAccurateVideoFlushVp9() throws Exception {
        testTunneledAccurateVideoFlush(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test that audio timestamps stop progressing during pause in tunneled mode.
     */
    private void testTunneledAudioProgressWithPause(String mimeType, String videoName)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                    "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        long firstVideoPosition = mMediaCodecPlayer.getVideoTimeUs();
        assertNotEquals("onFrameRendered was not called",
                firstVideoPosition, CodecState.UNINITIALIZED_TIMESTAMP);
        AudioTimestamp firstAudioTimestamp = mMediaCodecPlayer.getTimestamp();
        assertNotEquals("Audio timestamp is null", firstAudioTimestamp, null);
        assertNotEquals("Audio timestamp has a zero frame position",
                firstAudioTimestamp.framePosition, 0);

        // Expected stabilization wait is 60ms. We triple to 180ms to prevent flakiness
        // and still test basic functionality.
        final int sleepTimeMs = 180;
        Thread.sleep(sleepTimeMs);
        mMediaCodecPlayer.pause();
        // pause might take some time to ramp volume down.
        Thread.sleep(sleepTimeMs);
        AudioTimestamp audioTimestampAfterPause = mMediaCodecPlayer.getTimestamp();
        // Verify the video has advanced beyond the first position.
        assertTrue(mMediaCodecPlayer.getVideoTimeUs() > firstVideoPosition);
        // Verify that the timestamp has advanced beyond the first timestamp.
        assertTrue(audioTimestampAfterPause.nanoTime > firstAudioTimestamp.nanoTime);

        Thread.sleep(sleepTimeMs);
        // Verify that the timestamp does not advance after pause.
        assertEquals(audioTimestampAfterPause.nanoTime, mMediaCodecPlayer.getTimestamp().nanoTime);
    }


    /**
     * Test that audio timestamps stop progressing during pause for HEVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPauseHevc() throws Exception {
        testTunneledAudioProgressWithPause(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test that audio timestamps stop progressing during pause for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPauseAvc() throws Exception {
        testTunneledAudioProgressWithPause(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test that audio timestamps stop progressing during pause for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioProgressWithPauseVp9() throws Exception {
        testTunneledAudioProgressWithPause(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    /**
     * Test that audio underrun pauses video and resumes in-sync in tunneled mode.
     *
     * TODO(b/182915887): Test all the codecs advertised by the DUT for the provided test content
     */
    private void tunneledAudioUnderrun(String mimeType, String videoName)
            throws Exception {
        if (!MediaUtils.check(isVideoFeatureSupported(mimeType, FEATURE_TunneledPlayback),
                "No tunneled video playback codec found for MIME " + mimeType)) {
            return;
        }

        AudioManager am = mContext.getSystemService(AudioManager.class);
        mMediaCodecPlayer = new MediaCodecTunneledPlayer(
                mContext, getActivity().getSurfaceHolder(), true, am.generateAudioSessionId());

        Uri mediaUri = Uri.fromFile(new File(mInpPrefix, videoName));
        mMediaCodecPlayer.setAudioDataSource(mediaUri, null);
        mMediaCodecPlayer.setVideoDataSource(mediaUri, null);
        assertTrue("MediaCodecPlayer.prepare() failed!", mMediaCodecPlayer.prepare());
        mMediaCodecPlayer.startCodec();

        mMediaCodecPlayer.play();
        sleepUntil(() ->
                mMediaCodecPlayer.getCurrentPosition() > CodecState.UNINITIALIZED_TIMESTAMP
                && mMediaCodecPlayer.getTimestamp() != null
                && mMediaCodecPlayer.getTimestamp().framePosition > 0,
                Duration.ofSeconds(1));
        assertNotEquals("onFrameRendered was not called",
                mMediaCodecPlayer.getVideoTimeUs(), CodecState.UNINITIALIZED_TIMESTAMP);
        assertNotEquals("Audio timestamp is null", mMediaCodecPlayer.getTimestamp(), null);
        assertNotEquals("Audio timestamp has a zero frame position",
                mMediaCodecPlayer.getTimestamp().framePosition, 0);

        // Simulate underrun by starving the audio track of data
        mMediaCodecPlayer.stopDrainingAudioOutputBuffers(true);

        // Wait for audio underrun
        AudioTimestamp underrunAudioTimestamp;
        {
            AudioTimestamp currentAudioTimestamp = mMediaCodecPlayer.getTimestamp();
            long startTimeMs = System.currentTimeMillis();
            do {
                int audioUnderrunTimeoutMs = 1000;
                assertTrue(String.format("No audio underrun after %d milliseconds",
                                System.currentTimeMillis() - startTimeMs),
                        System.currentTimeMillis() - startTimeMs < audioUnderrunTimeoutMs);
                underrunAudioTimestamp = currentAudioTimestamp;
                Thread.sleep(50);
                currentAudioTimestamp = mMediaCodecPlayer.getTimestamp();
            } while (currentAudioTimestamp.framePosition != underrunAudioTimestamp.framePosition);
        }

        // Wait until video playback pauses due to underrunning audio
        long pausedVideoTimeUs = -1;
        {
            long currentVideoTimeUs = mMediaCodecPlayer.getVideoTimeUs();
            long startTimeMs = System.currentTimeMillis();
            do {
                int videoPauseTimeoutMs = 2000;
                assertTrue(String.format("No video pause after %d milliseconds",
                                videoPauseTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < videoPauseTimeoutMs);
                pausedVideoTimeUs = currentVideoTimeUs;
                Thread.sleep(250); // onFrameRendered messages can get delayed in the Framework
                currentVideoTimeUs = mMediaCodecPlayer.getVideoTimeUs();
            } while (currentVideoTimeUs != pausedVideoTimeUs);
        }

        // Retrieve index for the video rendered frame at the time of video pausing
        int pausedVideoRenderedTimestampIndex =
                mMediaCodecPlayer.getRenderedVideoFrameTimestampList().size() - 1;

        // Resume audio playback with a negative offset, in order to simulate a desynchronisation.
        // TODO(b/202710709): Use timestamp relative to last played video frame before pause
        mMediaCodecPlayer.setAudioTrackOffsetNs(-100L * 1000000);
        mMediaCodecPlayer.stopDrainingAudioOutputBuffers(false);

        // Wait until audio playback resumes
        AudioTimestamp postResumeAudioTimestamp;
        {
            AudioTimestamp previousAudioTimestamp;
            long startTimeMs = System.currentTimeMillis();
            do {
                int audioResumeTimeoutMs = 1000;
                assertTrue(String.format("Audio has not resumed after %d milliseconds",
                                audioResumeTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < audioResumeTimeoutMs);
                previousAudioTimestamp = mMediaCodecPlayer.getTimestamp();
                Thread.sleep(50);
                postResumeAudioTimestamp = mMediaCodecPlayer.getTimestamp();
            } while (postResumeAudioTimestamp.framePosition
                    == previousAudioTimestamp.framePosition);
        }

        // Now that audio playback has resumed, wait until video playback resumes
        {
            // We actually don't care about trying to capture the exact time video resumed, because
            // we can just look at the historical list of rendered video timestamps
            long postResumeVideoTimeUs;
            long previousVideoTimeUs;
            long startTimeMs = System.currentTimeMillis();
            do {
                int videoResumeTimeoutMs = 2000;
                assertTrue(String.format("Video has not resumed after %d milliseconds",
                                videoResumeTimeoutMs),
                        System.currentTimeMillis() - startTimeMs < videoResumeTimeoutMs);
                previousVideoTimeUs = mMediaCodecPlayer.getVideoTimeUs();
                Thread.sleep(50);
                postResumeVideoTimeUs = mMediaCodecPlayer.getVideoTimeUs();
            } while (postResumeVideoTimeUs == previousVideoTimeUs);
        }

        // The system time when rendering the first audio frame after the resume
        long playbackRateFps = mMediaCodecPlayer.getAudioTrack().getPlaybackRate();
        long playedFrames = postResumeAudioTimestamp.framePosition
                - underrunAudioTimestamp.framePosition + 1;
        double elapsedTimeNs = playedFrames * (1000.0 * 1000.0 * 1000.0 / playbackRateFps);
        long resumeAudioSystemTimeNs = postResumeAudioTimestamp.nanoTime - (long) elapsedTimeNs;
        long resumeAudioSystemTimeMs = resumeAudioSystemTimeNs / 1000 / 1000;

        // The system time when rendering the first video frame after video playback resumes
        long resumeVideoSystemTimeMs = mMediaCodecPlayer.getRenderedVideoFrameSystemTimeList()
                .get(pausedVideoRenderedTimestampIndex + 1) / 1000 / 1000;

        // Verify that video resumes in a reasonable amount of time after audio resumes
        // Note: Because a -100ms PTS gap is introduced, the video should resume 100ms later
        resumeAudioSystemTimeMs += 100;
        long resumeDeltaMs = resumeVideoSystemTimeMs - resumeAudioSystemTimeMs;
        assertTrue(String.format("Video started %s milliseconds before audio resumed "
                        + "(video:%d audio:%d)", resumeDeltaMs * -1, resumeVideoSystemTimeMs,
                        resumeAudioSystemTimeMs),
                resumeDeltaMs > 0); // video is expected to start after audio resumes
        assertTrue(String.format(
                        "Video started %d milliseconds after audio resumed (video:%d audio:%d)",
                        resumeDeltaMs, resumeVideoSystemTimeMs, resumeAudioSystemTimeMs),
                resumeDeltaMs <= 600); // video starting 300ms after audio is barely noticeable

        // Determine the system time of the audio frame that matches the presentation timestamp of
        // the resumed video frame
        long resumeVideoPresentationTimeUs = mMediaCodecPlayer.getRenderedVideoFrameTimestampList()
                .get(pausedVideoRenderedTimestampIndex + 1);
        long matchingAudioFramePosition =
                resumeVideoPresentationTimeUs * playbackRateFps / 1000 / 1000;
        playedFrames = matchingAudioFramePosition - postResumeAudioTimestamp.framePosition;
        elapsedTimeNs = playedFrames * (1000.0 * 1000.0 * 1000.0 / playbackRateFps);
        long matchingAudioSystemTimeNs = postResumeAudioTimestamp.nanoTime + (long) elapsedTimeNs;
        long matchingAudioSystemTimeMs = matchingAudioSystemTimeNs / 1000 / 1000;

        // Verify that video and audio are in sync at the time when video resumes
        // Note: Because a -100ms PTS gap is introduced, the video should resume 100ms later
        matchingAudioSystemTimeMs += 100;
        long avSyncOffsetMs =  resumeVideoSystemTimeMs - matchingAudioSystemTimeMs;
        assertTrue(String.format("Video is %d milliseconds out of sync of audio after resuming "
                        + "(video:%d, audio:%d)", avSyncOffsetMs, resumeVideoSystemTimeMs,
                        matchingAudioSystemTimeMs),
                // some leniency in AV sync is required because Android TV STB/OTT OEMs often have
                // to tune for imperfect downstream TVs (that have processing delays on the video)
                // by knowingly producing HDMI output that has audio and video mildly out of sync
                Math.abs(avSyncOffsetMs) <= 80);
    }

    /**
     * Test that audio underrun pauses video and resumes in-sync for HEVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioUnderrunHevc() throws Exception {
        tunneledAudioUnderrun(MediaFormat.MIMETYPE_VIDEO_HEVC,
                "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv");
    }

    /**
     * Test that audio underrun pauses video and resumes in-sync for AVC in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioUnderrunAvc() throws Exception {
        tunneledAudioUnderrun(MediaFormat.MIMETYPE_VIDEO_AVC,
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    /**
     * Test that audio underrun pauses video and resumes in-sync for VP9 in tunneled mode.
     */
    @Test
    @ApiTest(apis={"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testTunneledAudioUnderrunVp9() throws Exception {
        tunneledAudioUnderrun(MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    private void sleepUntil(Supplier<Boolean> supplier, Duration maxWait) throws Exception {
        final long deadLineMs = System.currentTimeMillis() + maxWait.toMillis();
        do {
            Thread.sleep(50);
        } while (!supplier.get() && System.currentTimeMillis() < deadLineMs);
    }

    /**
     * Returns list of CodecCapabilities advertising support for the given MIME type.
     */
    private static List<CodecCapabilities> getCodecCapabilitiesForMimeType(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        List<CodecCapabilities> caps = new ArrayList<CodecCapabilities>();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (codecInfo.isAlias()) {
                continue;
            }
            if (codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    caps.add(codecInfo.getCapabilitiesForType(mimeType));
                }
            }
        }
        return caps;
    }

    /**
     * Returns true if there exists a codec supporting the given MIME type that meets the
     * minimum specification for VR high performance requirements.
     *
     * The requirements are as follows:
     *   - At least 243000 blocks per second (where blocks are defined as 16x16 -- note this
     *   is equivalent to 1920x1080@30fps)
     *   - Feature adaptive-playback present
     */
    private static boolean doesMimeTypeHaveMinimumSpecVrReadyCodec(String mimeType) {
        List<CodecCapabilities> caps = getCodecCapabilitiesForMimeType(mimeType);
        for (CodecCapabilities c : caps) {
            if (!c.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback)) {
                continue;
            }

            if (!c.getVideoCapabilities().areSizeAndRateSupported(1920, 1080, 30.0)) {
                continue;
            }

            return true;
        }

        return false;
    }

    /**
     * Returns true if there exists a codec supporting the given MIME type that meets VR high
     * performance requirements.
     *
     * The requirements are as follows:
     *   - At least 972000 blocks per second (where blocks are defined as 16x16 -- note this
     *   is equivalent to 3840x2160@30fps)
     *   - At least 4 concurrent instances
     *   - Feature adaptive-playback present
     */
    private static boolean doesMimeTypeHaveVrReadyCodec(String mimeType) {
        List<CodecCapabilities> caps = getCodecCapabilitiesForMimeType(mimeType);
        for (CodecCapabilities c : caps) {
            if (c.getMaxSupportedInstances() < 4) {
                continue;
            }

            if (!c.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback)) {
                continue;
            }

            if (!c.getVideoCapabilities().areSizeAndRateSupported(3840, 2160, 30.0)) {
                continue;
            }

            return true;
        }

        return false;
    }

    @Test
    public void testVrHighPerformanceH264() throws Exception {
        if (!supportsVrHighPerformance()) {
            MediaUtils.skipTest(TAG, "FEATURE_VR_MODE_HIGH_PERFORMANCE not present");
            return;
        }

        boolean h264IsReady = doesMimeTypeHaveVrReadyCodec(MediaFormat.MIMETYPE_VIDEO_AVC);
        assertTrue("Did not find a VR ready H.264 decoder", h264IsReady);
    }

    @Test
    public void testVrHighPerformanceHEVC() throws Exception {
        if (!supportsVrHighPerformance()) {
            MediaUtils.skipTest(TAG, "FEATURE_VR_MODE_HIGH_PERFORMANCE not present");
            return;
        }

        // Test minimum mandatory requirements.
        assertTrue(doesMimeTypeHaveMinimumSpecVrReadyCodec(MediaFormat.MIMETYPE_VIDEO_HEVC));

        boolean hevcIsReady = doesMimeTypeHaveVrReadyCodec(MediaFormat.MIMETYPE_VIDEO_HEVC);
        if (!hevcIsReady) {
            Log.d(TAG, "HEVC isn't required to be VR ready");
            return;
        }
    }

    @Test
    public void testVrHighPerformanceVP9() throws Exception {
        if (!supportsVrHighPerformance()) {
            MediaUtils.skipTest(TAG, "FEATURE_VR_MODE_HIGH_PERFORMANCE not present");
            return;
        }

        // Test minimum mandatory requirements.
        assertTrue(doesMimeTypeHaveMinimumSpecVrReadyCodec(MediaFormat.MIMETYPE_VIDEO_VP9));

        boolean vp9IsReady = doesMimeTypeHaveVrReadyCodec(MediaFormat.MIMETYPE_VIDEO_VP9);
        if (!vp9IsReady) {
            Log.d(TAG, "VP9 isn't required to be VR ready");
            return;
        }
    }

    private boolean supportsVrHighPerformance() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void testLowLatencyVp9At1280x720() throws Exception {
        testLowLatencyVideo(
                "video_1280x720_webm_vp9_csd_309kbps_25fps_vorbis_stereo_128kbps_48000hz.webm", 300,
                false /* useNdk */);
        testLowLatencyVideo(
                "video_1280x720_webm_vp9_csd_309kbps_25fps_vorbis_stereo_128kbps_48000hz.webm", 300,
                true /* useNdk */);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void testLowLatencyVp9At1920x1080() throws Exception {
        testLowLatencyVideo(
                "bbb_s2_1920x1080_webm_vp9_0p41_10mbps_60fps_vorbis_6ch_384kbps_22050hz.webm", 300,
                false /* useNdk */);
        testLowLatencyVideo(
                "bbb_s2_1920x1080_webm_vp9_0p41_10mbps_60fps_vorbis_6ch_384kbps_22050hz.webm", 300,
                true /* useNdk */);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void testLowLatencyVp9At3840x2160() throws Exception {
        testLowLatencyVideo(
                "bbb_s2_3840x2160_webm_vp9_0p51_20mbps_60fps_vorbis_6ch_384kbps_32000hz.webm", 300,
                false /* useNdk */);
        testLowLatencyVideo(
                "bbb_s2_3840x2160_webm_vp9_0p51_20mbps_60fps_vorbis_6ch_384kbps_32000hz.webm", 300,
                true /* useNdk */);
    }

    @NonMainlineTest
    @Test
    public void testLowLatencyAVCAt1280x720() throws Exception {
        testLowLatencyVideo(
                "video_1280x720_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4", 300,
                false /* useNdk */);
        testLowLatencyVideo(
                "video_1280x720_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4", 300,
                true /* useNdk */);
    }

    @NonMainlineTest
    @Test
    public void testLowLatencyHEVCAt480x360() throws Exception {
        testLowLatencyVideo(
                "video_480x360_mp4_hevc_650kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 300,
                false /* useNdk */);
        testLowLatencyVideo(
                "video_480x360_mp4_hevc_650kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 300,
                true /* useNdk */);
    }

    private void testLowLatencyVideo(String testVideo, int frameCount, boolean useNdk)
            throws Exception {
        AssetFileDescriptor fd = getAssetFileDescriptorFor(testVideo);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        fd.close();

        MediaFormat format = null;
        int trackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            format = extractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                trackIndex = i;
                break;
            }
        }

        assertTrue("No video track was found", trackIndex >= 0);

        extractor.selectTrack(trackIndex);
        format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency,
                true /* enable */);

        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        String decoderName = mcl.findDecoderForFormat(format);
        if (decoderName == null) {
            MediaUtils.skipTest("no low latency decoder for " + format);
            return;
        }
        String entry = (useNdk ? "NDK" : "SDK");
        Log.v(TAG, "found " + entry + " decoder " + decoderName + " for format: " + format);

        Surface surface = getActivity().getSurfaceHolder().getSurface();
        MediaCodecWrapper decoder = null;
        if (useNdk) {
            decoder = new NdkMediaCodec(decoderName);
        } else {
            decoder = new SdkMediaCodec(MediaCodec.createByCodecName(decoderName));
        }
        format.removeFeature(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency);
        format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        decoder.configure(format, 0 /* flags */, surface);
        decoder.start();

        if (!useNdk) {
            decoder.getInputBuffers();
        }
        ByteBuffer[] codecOutputBuffers = decoder.getOutputBuffers();
        String decoderOutputFormatString = null;

        // start decoding
        final long kTimeOutUs = 1000000;  // 1 second
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int bufferCounter = 0;
        long[] latencyMs = new long[frameCount];
        boolean waitingForOutput = false;
        long startTimeMs = System.currentTimeMillis();
        while (bufferCounter < frameCount) {
            if (!waitingForOutput) {
                int inputBufferId = decoder.dequeueInputBuffer(kTimeOutUs);
                if (inputBufferId < 0) {
                    Log.v(TAG, "no input buffer");
                    break;
                }

                ByteBuffer dstBuf = decoder.getInputBuffer(inputBufferId);

                int sampleSize = extractor.readSampleData(dstBuf, 0 /* offset */);
                long presentationTimeUs = 0;
                if (sampleSize < 0) {
                    Log.v(TAG, "had input EOS, early termination at frame " + bufferCounter);
                    break;
                } else {
                    presentationTimeUs = extractor.getSampleTime();
                }

                startTimeMs = System.currentTimeMillis();
                decoder.queueInputBuffer(
                        inputBufferId,
                        0 /* offset */,
                        sampleSize,
                        presentationTimeUs,
                        0 /* flags */);

                extractor.advance();
                waitingForOutput = true;
            }

            int outputBufferId = decoder.dequeueOutputBuffer(info, kTimeOutUs);

            if (outputBufferId >= 0) {
                waitingForOutput = false;
                //Log.d(TAG, "got output, size " + info.size + ", time " + info.presentationTimeUs);
                latencyMs[bufferCounter++] = System.currentTimeMillis() - startTimeMs;
                // TODO: render the frame and find the rendering time to calculate the total delay
                decoder.releaseOutputBuffer(outputBufferId, false /* render */);
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = decoder.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                decoderOutputFormatString = decoder.getOutputFormatString();
                Log.d(TAG, "output format has changed to " + decoderOutputFormatString);
            } else {
                fail("No output buffer returned without frame delay, status " + outputBufferId);
            }
        }

        assertTrue("No INFO_OUTPUT_FORMAT_CHANGED from decoder", decoderOutputFormatString != null);

        long latencyMean = 0;
        long latencyMax = 0;
        int maxIndex = 0;
        for (int i = 0; i < bufferCounter; ++i) {
            latencyMean += latencyMs[i];
            if (latencyMs[i] > latencyMax) {
                latencyMax = latencyMs[i];
                maxIndex = i;
            }
        }
        if (bufferCounter > 0) {
            latencyMean /= bufferCounter;
        }
        Log.d(TAG, entry + " latency average " + latencyMean + " ms, max " + latencyMax +
                " ms at frame " + maxIndex);

        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, "video_decoder_latency");
        String mime = format.getString(MediaFormat.KEY_MIME);
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        log.addValue("codec_name", decoderName, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("mime_type", mime, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("width", width, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("height", height, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("video_res", testVideo, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("decode_to", surface == null ? "buffer" : "surface",
                ResultType.NEUTRAL, ResultUnit.NONE);

        log.addValue("average_latency", latencyMean, ResultType.LOWER_BETTER, ResultUnit.MS);
        log.addValue("max_latency", latencyMax, ResultType.LOWER_BETTER, ResultUnit.MS);

        log.submit(getInstrumentation());

        decoder.stop();
        decoder.release();
        extractor.release();
    }
}
