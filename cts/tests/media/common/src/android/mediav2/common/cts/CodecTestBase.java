/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing;
import static android.media.MediaCodecInfo.CodecProfileLevel.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.Surface;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * This class comprises of routines that are generic to media codec component trying and testing.
 * <p>
 * A media codec component essentially processes input data to generate output data. The
 * component uses a set of input and output buffers. At a simplistic level, the client requests
 * (or receive) an empty input buffer, fills it up with data and sends it to the codec for
 * processing. The codec uses up the data and transforms it into one of its empty output
 * buffers. Finally, the client asks (or receive) a filled output buffer, consume its contents and
 * release it back to the codec.
 * <p>
 * The type of data that a component receives and sends is dependent on the component. For
 * instance video encoders expect raw yuv/rgb data and send compressed data. A video decoder
 * receives compressed access-unit and sends reconstructed yuv. But the processes surrounding
 * this enqueue and dequeue remain common to all components. Operations like configure, requesting
 * the component for an empty input buffer, feeding the component a filled input buffer,
 * requesting the component for an output buffer, releasing the processed output buffer, Sending
 * state transition commands, waiting on component to send all outputs, closing the component and
 * releasing the resources, ... remain more or less identical to all components. The routines
 * that manage these generic processes are maintained by this class. Not only the methods that
 * are responsible for component trying but also methods that test its functionality are covered
 * here. A video component is expected to give same number of outputs as inputs. The output
 * buffer timestamps of an audio component or a decoder component is expected to be strictly
 * increasing. The routines that enforce these generic rules of all components at all times are
 * part of this class. Besides these, mediaType specific constants, helper utilities to test
 * specific components or mediaTypes are covered here.
 * <p>
 * enqueueInput and dequeueOutput are routines responsible for filling the input buffer and
 * handing the received output buffer respectively. These shall be component specific, hence they
 * are abstract methods. Any client intending to use this class shall implement these methods
 * basing on the component under test.
 * <p>
 * In simple terms, the CodecTestBase is a wrapper class comprising generic routines for
 * component trying and testing.
 */
public abstract class CodecTestBase {
    public static final boolean IS_Q = ApiLevelUtil.getApiLevel() == Build.VERSION_CODES.Q;
    public static final boolean IS_AT_LEAST_R = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    public static final boolean IS_AT_LEAST_T =
            ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU);
    //TODO(b/248315681) Remove codenameEquals() check once devices return correct version for U
    public static final boolean IS_AT_LEAST_U = ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU)
            || ApiLevelUtil.codenameEquals("UpsideDownCake");
    public static final boolean IS_BEFORE_U = !IS_AT_LEAST_U;
    public static final boolean FIRST_SDK_IS_AT_LEAST_T =
            ApiLevelUtil.isFirstApiAtLeast(Build.VERSION_CODES.TIRAMISU);
    public static final boolean VNDK_IS_AT_LEAST_T =
            SystemProperties.getInt("ro.vndk.version", Build.VERSION_CODES.CUR_DEVELOPMENT)
                    >= Build.VERSION_CODES.TIRAMISU;
    public static final boolean VNDK_IS_BEFORE_U =
            SystemProperties.getInt("ro.vndk.version", Build.VERSION_CODES.CUR_DEVELOPMENT)
                    < Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    public static final boolean VNDK_IS_AT_MOST_U =
            SystemProperties.getInt("ro.vndk.version", Build.VERSION_CODES.CUR_DEVELOPMENT)
                    <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    public static final boolean BOARD_SDK_IS_AT_LEAST_T =
            SystemProperties.getInt("ro.board.api_level", Build.VERSION_CODES.CUR_DEVELOPMENT)
                    >= Build.VERSION_CODES.TIRAMISU;
    public static final int ANDROID_VENDOR_API_202404 = 202404;
    public static final boolean BOARD_SDK_IS_AT_LEAST_202404 =
            SystemProperties.getInt("ro.board.api_level", Build.VERSION_CODES.CUR_DEVELOPMENT)
                    >= ANDROID_VENDOR_API_202404;
    public static final boolean IS_HDR_EDITING_SUPPORTED;
    public static final boolean IS_HDR_CAPTURE_SUPPORTED;
    private static final String LOG_TAG = CodecTestBase.class.getSimpleName();

    public static final ArrayList<String> HDR_INFO_IN_BITSTREAM_CODECS = new ArrayList<>();
    public static final String HDR_STATIC_INFO =
            "00 d0 84 80 3e c2 33 c4 86 4c 1d b8 0b 13 3d 42 40 a0 0f 32 00 10 27 df 0d";
    public static final String HDR_STATIC_INCORRECT_INFO =
            "00 d0 84 80 3e c2 33 c4 86 10 27 d0 07 13 3d 42 40 a0 0f 32 00 10 27 df 0d";
    public static final String CODEC_PREFIX_KEY = "codec-prefix";
    public static final String CODEC_FILTER_KEY = "codec-filter";
    public static final String MEDIA_TYPE_PREFIX_KEY = "media-type-prefix";
    public static final String MEDIA_TYPE_SEL_KEY = "media-type-sel";
    public static final Map<String, String> CODEC_SEL_KEY_MEDIA_TYPE_MAP = new HashMap<>();
    public static final Map<String, String> DEFAULT_ENCODERS = new HashMap<>();
    public static final Map<String, String> DEFAULT_DECODERS = new HashMap<>();
    public static final HashMap<String, int[]> PROFILE_MAP = new HashMap<>();
    public static final HashMap<String, int[]> PROFILE_SDR_MAP = new HashMap<>();
    public static final HashMap<String, int[]> PROFILE_HLG_MAP = new HashMap<>();
    public static final HashMap<String, int[]> PROFILE_HDR10_MAP = new HashMap<>();
    public static final HashMap<String, int[]> PROFILE_HDR10_PLUS_MAP = new HashMap<>();
    public static final HashMap<String, int[]> PROFILE_HDR_MAP = new HashMap<>();
    public static final boolean ENABLE_LOGS = false;
    public static final int PER_TEST_TIMEOUT_LARGE_TEST_MS = 300000;
    public static final int PER_TEST_TIMEOUT_SMALL_TEST_MS = 60000;
    public static final int UNSPECIFIED = 0;
    // Maintain Timeouts in sync with their counterpart in NativeMediaCommon.h
    // block at most 5ms while looking for io buffers
    public static final long Q_DEQ_TIMEOUT_US = 5000;
    // max poll counter before test aborts and returns error
    public static final int RETRY_LIMIT = 100;
    public static final String INVALID_CODEC = "unknown.codec_";
    static final int[] MPEG2_PROFILES = new int[]{MPEG2ProfileSimple, MPEG2ProfileMain,
            MPEG2Profile422, MPEG2ProfileSNR, MPEG2ProfileSpatial, MPEG2ProfileHigh};
    static final int[] MPEG4_PROFILES = new int[]{MPEG4ProfileSimple, MPEG4ProfileSimpleScalable,
            MPEG4ProfileCore, MPEG4ProfileMain, MPEG4ProfileNbit, MPEG4ProfileScalableTexture,
            MPEG4ProfileSimpleFace, MPEG4ProfileSimpleFBA, MPEG4ProfileBasicAnimated,
            MPEG4ProfileHybrid, MPEG4ProfileAdvancedRealTime, MPEG4ProfileCoreScalable,
            MPEG4ProfileAdvancedCoding, MPEG4ProfileAdvancedCore, MPEG4ProfileAdvancedScalable,
            MPEG4ProfileAdvancedSimple};
    static final int[] H263_PROFILES = new int[]{H263ProfileBaseline, H263ProfileH320Coding,
            H263ProfileBackwardCompatible, H263ProfileISWV2, H263ProfileISWV3,
            H263ProfileHighCompression, H263ProfileInternet, H263ProfileInterlace,
            H263ProfileHighLatency};
    static final int[] VP8_PROFILES = new int[]{VP8ProfileMain};
    static final int[] AVC_SDR_PROFILES = new int[]{AVCProfileBaseline, AVCProfileMain,
            AVCProfileExtended, AVCProfileHigh, AVCProfileConstrainedBaseline,
            AVCProfileConstrainedHigh};
    static final int[] AVC_HLG_PROFILES = new int[]{AVCProfileHigh10};
    static final int[] AVC_HDR_PROFILES = AVC_HLG_PROFILES;
    static final int[] AVC_PROFILES = combine(AVC_SDR_PROFILES, AVC_HDR_PROFILES);
    static final int[] VP9_SDR_PROFILES = new int[]{VP9Profile0};
    static final int[] VP9_HLG_PROFILES = new int[]{VP9Profile2};
    static final int[] VP9_HDR10_PROFILES = new int[]{VP9Profile2HDR};
    static final int[] VP9_HDR10_PLUS_PROFILES = new int[]{VP9Profile2HDR10Plus};
    static final int[] VP9_HDR_PROFILES =
            combine(VP9_HLG_PROFILES, combine(VP9_HDR10_PROFILES, VP9_HDR10_PLUS_PROFILES));
    static final int[] VP9_PROFILES = combine(VP9_SDR_PROFILES, VP9_HDR_PROFILES);
    static final int[] HEVC_SDR_PROFILES = new int[]{HEVCProfileMain};
    static final int[] HEVC_HLG_PROFILES = new int[]{HEVCProfileMain10};
    static final int[] HEVC_HDR10_PROFILES = new int[]{HEVCProfileMain10HDR10};
    static final int[] HEVC_HDR10_PLUS_PROFILES = new int[]{HEVCProfileMain10HDR10Plus};
    static final int[] HEVC_HDR_PROFILES =
            combine(HEVC_HLG_PROFILES, combine(HEVC_HDR10_PROFILES, HEVC_HDR10_PLUS_PROFILES));
    static final int[] HEVC_PROFILES = combine(HEVC_SDR_PROFILES, HEVC_HDR_PROFILES);
    static final int[] AV1_SDR_PROFILES = new int[]{AV1ProfileMain8};
    static final int[] AV1_HLG_PROFILES = new int[]{AV1ProfileMain10};
    static final int[] AV1_HDR10_PROFILES = new int[]{AV1ProfileMain10HDR10};
    static final int[] AV1_HDR10_PLUS_PROFILES = new int[]{AV1ProfileMain10HDR10Plus};
    static final int[] AV1_HDR_PROFILES =
            combine(AV1_HLG_PROFILES, combine(AV1_HDR10_PROFILES, AV1_HDR10_PLUS_PROFILES));
    static final int[] AV1_PROFILES = combine(AV1_SDR_PROFILES, AV1_HDR_PROFILES);
    static final int[] AAC_PROFILES = new int[]{AACObjectMain, AACObjectLC, AACObjectSSR,
            AACObjectLTP, AACObjectHE, AACObjectScalable, AACObjectERLC, AACObjectERScalable,
            AACObjectLD, AACObjectELD, AACObjectXHE};
    public static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    public static final int MAX_DISPLAY_HEIGHT_CURRENT =
            Arrays.stream(CONTEXT.getSystemService(DisplayManager.class).getDisplays())
                    .map(Display::getSupportedModes)
                    .flatMap(Stream::of)
                    .max(Comparator.comparing(Display.Mode::getPhysicalHeight))
                    .orElseThrow(() -> new RuntimeException("Failed to determine max height"))
                    .getPhysicalHeight();
    public static final int MAX_DISPLAY_WIDTH_CURRENT =
            Arrays.stream(CONTEXT.getSystemService(DisplayManager.class).getDisplays())
                    .map(Display::getSupportedModes)
                    .flatMap(Stream::of)
                    .max(Comparator.comparing(Display.Mode::getPhysicalHeight))
                    .orElseThrow(() -> new RuntimeException("Failed to determine max height"))
                    .getPhysicalWidth();
    public static final int MAX_DISPLAY_WIDTH_LAND =
            Math.max(MAX_DISPLAY_WIDTH_CURRENT, MAX_DISPLAY_HEIGHT_CURRENT);
    public static final int MAX_DISPLAY_HEIGHT_LAND =
            Math.min(MAX_DISPLAY_WIDTH_CURRENT, MAX_DISPLAY_HEIGHT_CURRENT);

    public static final String HDR10_INFO_SCENE_A =
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00"
                    + "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9"
                    + "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00"
                    + "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00";
    public static final String HDR10_INFO_SCENE_B =
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00"
                    + "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9"
                    + "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00"
                    + "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00";
    public static final String HDR10_INFO_SCENE_C =
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00"
                    + "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9"
                    + "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00"
                    + "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00";
    public static final String HDR10_INFO_SCENE_D =
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00"
                    + "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9"
                    + "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00"
                    + "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00";

    public static final String HDR10_INCORRECT_INFO_SCENE_A =
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00"
                    + "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9"
                    + "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00"
                    + "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00";
    public static final String HDR10_INCORRECT_INFO_SCENE_B =
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00"
                    + "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9"
                    + "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00"
                    + "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 01";
    public static final String HDR10_INCORRECT_INFO_SCENE_C =
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00"
                    + "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9"
                    + "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00"
                    + "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 02";
    public static final String HDR10_INCORRECT_INFO_SCENE_D =
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00"
                    + "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9"
                    + "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00"
                    + "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 03";

    public static String mediaTypeSelKeys;
    public static String codecPrefix;
    public static String mediaTypePrefix;
    public static Pattern codecFilter;

    public enum SupportClass {
        CODEC_ALL, // All codecs must support
        CODEC_ANY, // At least one codec must support
        CODEC_DEFAULT, // Default codec must support
        CODEC_HW, // If the component is hardware, then it must support
        CODEC_SHOULD, // Codec support is optional, but recommended
        CODEC_HW_RECOMMENDED, // Codec support is optional, but strongly recommended if component
        // is hardware accelerated
        CODEC_OPTIONAL; // Codec support is optional

        public static String toString(SupportClass supportRequirements) {
            switch (supportRequirements) {
                case CODEC_ALL:
                    return "CODEC_ALL";
                case CODEC_ANY:
                    return "CODEC_ANY";
                case CODEC_DEFAULT:
                    return "CODEC_DEFAULT";
                case CODEC_HW:
                    return "CODEC_HW";
                case CODEC_SHOULD:
                    return "CODEC_SHOULD";
                case CODEC_HW_RECOMMENDED:
                    return "CODEC_HW_RECOMMENDED";
                case CODEC_OPTIONAL:
                    return "CODEC_OPTIONAL";
                default:
                    return "Unknown support class";
            }
        }
    }

    public enum ComponentClass {
        ALL,
        SOFTWARE,
        HARDWARE;

        public static String toString(ComponentClass selectSwitch) {
            switch (selectSwitch) {
                case ALL:
                    return "all";
                case SOFTWARE:
                    return "software only";
                case HARDWARE:
                    return "hardware accelerated";
                default:
                    return "Unknown select switch";
            }
        }
    }

    public CodecTestBase(String encoder, String mediaType, String allTestParams) {
        mCodecName = encoder;
        mMediaType = mediaType;
        mAllTestParams = allTestParams;
        mAsyncHandle = new CodecAsyncHandler();
        mIsAudio = mMediaType.startsWith("audio/");
        mIsVideo = mMediaType.startsWith("video/");
    }

    protected final String mCodecName;
    protected final String mMediaType;
    protected final String mAllTestParams;  // logging

    protected final boolean mIsAudio;
    protected final boolean mIsVideo;
    protected CodecAsyncHandler mAsyncHandle;
    protected boolean mIsCodecInAsyncMode;
    protected boolean mSawInputEOS;
    protected boolean mSawOutputEOS;
    protected boolean mSignalEOSWithLastFrame;
    protected int mInputCount;
    protected int mOutputCount;
    protected long mPrevOutputPts;
    protected boolean mSignalledOutFormatChanged;
    protected MediaFormat mOutFormat;

    protected StringBuilder mTestConfig = new StringBuilder();
    protected StringBuilder mTestEnv = new StringBuilder();

    protected boolean mSaveToMem;
    protected OutputManager mOutputBuff;

    protected MediaCodec mCodec;
    protected Surface mSurface;
    // NOTE: mSurface is a place holder of current Surface used by the CodecTestBase.
    // This doesn't own the handle. The ownership with instances that manage
    // SurfaceView or TextureView, ... They hold the responsibility of calling release().
    protected CodecTestActivity mActivity;
    protected ImageSurface mImageSurface;

    public static final MediaCodecList MEDIA_CODEC_LIST_ALL;
    public static final MediaCodecList MEDIA_CODEC_LIST_REGULAR;
    static {
        MEDIA_CODEC_LIST_ALL = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MEDIA_CODEC_LIST_REGULAR = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        IS_HDR_CAPTURE_SUPPORTED = isHDRCaptureSupported();
        IS_HDR_EDITING_SUPPORTED = isHDREditingSupported();
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("vp8", MediaFormat.MIMETYPE_VIDEO_VP8);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("vp9", MediaFormat.MIMETYPE_VIDEO_VP9);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("av1", MediaFormat.MIMETYPE_VIDEO_AV1);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("avc", MediaFormat.MIMETYPE_VIDEO_AVC);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("hevc", MediaFormat.MIMETYPE_VIDEO_HEVC);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("mpeg4", MediaFormat.MIMETYPE_VIDEO_MPEG4);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("h263", MediaFormat.MIMETYPE_VIDEO_H263);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("mpeg2", MediaFormat.MIMETYPE_VIDEO_MPEG2);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("vraw", MediaFormat.MIMETYPE_VIDEO_RAW);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("amrnb", MediaFormat.MIMETYPE_AUDIO_AMR_NB);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("amrwb", MediaFormat.MIMETYPE_AUDIO_AMR_WB);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("mp3", MediaFormat.MIMETYPE_AUDIO_MPEG);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("aac", MediaFormat.MIMETYPE_AUDIO_AAC);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("vorbis", MediaFormat.MIMETYPE_AUDIO_VORBIS);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("opus", MediaFormat.MIMETYPE_AUDIO_OPUS);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("g711alaw", MediaFormat.MIMETYPE_AUDIO_G711_ALAW);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("g711mlaw", MediaFormat.MIMETYPE_AUDIO_G711_MLAW);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("araw", MediaFormat.MIMETYPE_AUDIO_RAW);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("flac", MediaFormat.MIMETYPE_AUDIO_FLAC);
        CODEC_SEL_KEY_MEDIA_TYPE_MAP.put("gsm", MediaFormat.MIMETYPE_AUDIO_MSGSM);

        android.os.Bundle args = InstrumentationRegistry.getArguments();
        mediaTypeSelKeys = args.getString(MEDIA_TYPE_SEL_KEY);
        codecPrefix = args.getString(CODEC_PREFIX_KEY);
        mediaTypePrefix = args.getString(MEDIA_TYPE_PREFIX_KEY);
        String codecFilterStr = args.getString(CODEC_FILTER_KEY);
        if (codecFilterStr != null) {
            codecFilter = Pattern.compile(codecFilterStr);
        }

        PROFILE_SDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_AVC, AVC_SDR_PROFILES);
        PROFILE_SDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_SDR_PROFILES);
        PROFILE_SDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_H263, H263_PROFILES);
        PROFILE_SDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_MPEG2, MPEG2_PROFILES);
        PROFILE_SDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_MPEG4, MPEG4_PROFILES);
        PROFILE_SDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP8, VP8_PROFILES);
        PROFILE_SDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_SDR_PROFILES);
        PROFILE_SDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_SDR_PROFILES);
        PROFILE_SDR_MAP.put(MediaFormat.MIMETYPE_AUDIO_AAC, AAC_PROFILES);

        PROFILE_HLG_MAP.put(MediaFormat.MIMETYPE_VIDEO_AVC, AVC_HLG_PROFILES);
        PROFILE_HLG_MAP.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_HLG_PROFILES);
        PROFILE_HLG_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HLG_PROFILES);
        PROFILE_HLG_MAP.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HLG_PROFILES);

        PROFILE_HDR10_MAP.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_HDR10_PROFILES);
        PROFILE_HDR10_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR10_PROFILES);
        PROFILE_HDR10_MAP.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HDR10_PROFILES);

        PROFILE_HDR10_PLUS_MAP.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_HDR10_PLUS_PROFILES);
        PROFILE_HDR10_PLUS_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR10_PLUS_PROFILES);
        PROFILE_HDR10_PLUS_MAP.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HDR10_PLUS_PROFILES);

        PROFILE_HDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_AVC, AVC_HDR_PROFILES);
        PROFILE_HDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_HDR_PROFILES);
        PROFILE_HDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR_PROFILES);
        PROFILE_HDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HDR_PROFILES);

        PROFILE_MAP.put(MediaFormat.MIMETYPE_VIDEO_AVC, AVC_PROFILES);
        PROFILE_MAP.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_PROFILES);
        PROFILE_MAP.put(MediaFormat.MIMETYPE_VIDEO_H263, H263_PROFILES);
        PROFILE_MAP.put(MediaFormat.MIMETYPE_VIDEO_MPEG2, MPEG2_PROFILES);
        PROFILE_MAP.put(MediaFormat.MIMETYPE_VIDEO_MPEG4, MPEG4_PROFILES);
        PROFILE_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP8, VP8_PROFILES);
        PROFILE_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_PROFILES);
        PROFILE_MAP.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_PROFILES);
        PROFILE_MAP.put(MediaFormat.MIMETYPE_AUDIO_AAC, AAC_PROFILES);

        HDR_INFO_IN_BITSTREAM_CODECS.add(MediaFormat.MIMETYPE_VIDEO_AV1);
        HDR_INFO_IN_BITSTREAM_CODECS.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        HDR_INFO_IN_BITSTREAM_CODECS.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
    }

    static int[] combine(int[] first, int[] second) {
        int[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static boolean isMediaTypeLossless(String mediaType) {
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) return true;
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_RAW)) return true;
        return false;
    }

    // some media types decode a pre-roll amount before playback. This would mean that decoding
    // after seeking may not return the exact same values as would be obtained by decoding the
    // stream straight through
    public static boolean isMediaTypeOutputUnAffectedBySeek(String mediaType) {
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) return true;
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_RAW)) return true;
        if (mediaType.startsWith("video/")) return true;
        return false;
    }

    public static boolean hasDecoder(String mediaType) {
        return CodecTestBase.selectCodecs(mediaType, null, null, false).size() != 0;
    }

    public static boolean hasEncoder(String mediaType) {
        return CodecTestBase.selectCodecs(mediaType, null, null, true).size() != 0;
    }

    public static void checkFormatSupport(String codecName, String mediaType, boolean isEncoder,
            ArrayList<MediaFormat> formats, String[] features, SupportClass supportRequirements)
            throws IOException {
        if (!areFormatsSupported(codecName, mediaType, formats)) {
            switch (supportRequirements) {
                case CODEC_ALL:
                    fail("format(s) not supported by codec: " + codecName + " for mediaType : "
                            + mediaType + " formats: " + formats);
                    break;
                case CODEC_ANY:
                    if (selectCodecs(mediaType, formats, features, isEncoder).isEmpty()) {
                        fail("format(s) not supported by any component for mediaType : " + mediaType
                                + " formats: " + formats);
                    }
                    break;
                case CODEC_DEFAULT:
                    if (isDefaultCodec(codecName, mediaType, isEncoder)) {
                        fail("format(s) not supported by default codec : " + codecName
                                + "for mediaType : " + mediaType + " formats: " + formats);
                    }
                    break;
                case CODEC_HW:
                    if (isHardwareAcceleratedCodec(codecName)) {
                        fail("format(s) not supported by codec: " + codecName + " for mediaType : "
                                + mediaType + " formats: " + formats);
                    }
                    break;
                case CODEC_SHOULD:
                    Assume.assumeTrue(String.format("format(s) not supported by codec: %s for"
                            + " mediaType : %s. It is recommended to support it",
                            codecName, mediaType), false);
                    break;
                case CODEC_HW_RECOMMENDED:
                    Assume.assumeTrue(String.format(
                            "format(s) not supported by codec: %s for mediaType : %s. It is %s "
                                    + "recommended to support it", codecName, mediaType,
                            isHardwareAcceleratedCodec(codecName) ? "strongly" : ""), false);
                    break;
                case CODEC_OPTIONAL:
                default:
                    // the later assumeTrue() ensures we skip the test for unsupported codecs
                    break;
            }
            Assume.assumeTrue("format(s) not supported by codec: " + codecName + " for mediaType : "
                    + mediaType, false);
        }
    }

    public static boolean isFeatureSupported(String name, String mediaType, String feature) {
        for (MediaCodecInfo codecInfo : MEDIA_CODEC_LIST_ALL.getCodecInfos()) {
            if (name.equals(codecInfo.getName())) {
                return codecInfo.getCapabilitiesForType(mediaType).isFeatureSupported(feature);
            }
        }
        return false;
    }

    public static boolean isHDRCaptureSupported() {
        // If the device supports HDR, hlg support should always return true
        if (!MediaUtils.hasCamera()) return false;
        CameraManager cm = CONTEXT.getSystemService(CameraManager.class);
        try {
            String[] cameraIds = cm.getCameraIdList();
            for (String id : cameraIds) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                int[] caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                if (IntStream.of(caps).anyMatch(x -> x
                        == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)) {
                    Set<Long> profiles =
                            ch.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)
                                    .getSupportedProfiles();
                    if (profiles.contains(DynamicRangeProfiles.HLG10)) return true;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(LOG_TAG, "encountered " + e.getMessage()
                    + " marking hdr capture to be available to catch your attention");
            return true;
        }
        return false;
    }

    public static boolean isHDREditingSupported() {
        for (MediaCodecInfo codecInfo : MEDIA_CODEC_LIST_REGULAR.getCodecInfos()) {
            if (!codecInfo.isEncoder()) {
                continue;
            }
            for (String mediaType : codecInfo.getSupportedTypes()) {
                CodecCapabilities caps = codecInfo.getCapabilitiesForType(mediaType);
                if (caps != null && caps.isFeatureSupported(FEATURE_HdrEditing)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean doesAnyFormatHaveHDRProfile(String mediaType,
            ArrayList<MediaFormat> formats) {
        int[] profileArray = PROFILE_HDR_MAP.get(mediaType);
        if (profileArray != null) {
            for (MediaFormat format : formats) {
                assertEquals(mediaType, format.getString(MediaFormat.KEY_MIME));
                int profile = format.getInteger(MediaFormat.KEY_PROFILE, -1);
                if (IntStream.of(profileArray).anyMatch(x -> x == profile)) return true;
            }
        }
        return false;
    }

    public static boolean doesCodecSupportHDRProfile(String codecName, String mediaType) {
        int[] hdrProfiles = PROFILE_HDR_MAP.get(mediaType);
        if (hdrProfiles == null) {
            return false;
        }
        for (MediaCodecInfo codecInfo : MEDIA_CODEC_LIST_REGULAR.getCodecInfos()) {
            if (!codecName.equals(codecInfo.getName())) {
                continue;
            }
            CodecCapabilities caps = codecInfo.getCapabilitiesForType(mediaType);
            if (caps == null) {
                return false;
            }
            for (CodecProfileLevel pl : caps.profileLevels) {
                if (IntStream.of(hdrProfiles).anyMatch(x -> x == pl.profile)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean canDisplaySupportHDRContent() {
        DisplayManager displayManager = CONTEXT.getSystemService(DisplayManager.class);
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY).getHdrCapabilities()
                .getSupportedHdrTypes().length > 0;
    }

    public static boolean areFormatsSupported(String name, String mediaType,
            List<MediaFormat> formats) throws IOException {
        for (MediaCodecInfo codecInfo : MEDIA_CODEC_LIST_ALL.getCodecInfos()) {
            if (name.equals(codecInfo.getName())) {
                MediaCodecInfo.CodecCapabilities cap = codecInfo.getCapabilitiesForType(mediaType);
                boolean isSupported = true;
                if (formats != null) {
                    for (int i = 0; i < formats.size() && isSupported; i++) {
                        isSupported = cap.isFormatSupported(formats.get(i));
                    }
                }
                if (isSupported) return true;
            }
        }
        return false;
    }

    public static boolean hasSupportForColorFormat(String name, String mediaType, int colorFormat) {
        for (MediaCodecInfo codecInfo : MEDIA_CODEC_LIST_ALL.getCodecInfos()) {
            if (name.equals(codecInfo.getName())) {
                MediaCodecInfo.CodecCapabilities cap = codecInfo.getCapabilitiesForType(mediaType);
                for (int c : cap.colorFormats) {
                    if (c == colorFormat) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isDefaultCodec(String codecName, String mediaType, boolean isEncoder)
            throws IOException {
        Map<String, String> mDefaultCodecs = isEncoder ? DEFAULT_ENCODERS : DEFAULT_DECODERS;
        if (mDefaultCodecs.containsKey(mediaType)) {
            return mDefaultCodecs.get(mediaType).equalsIgnoreCase(codecName);
        }
        MediaCodec codec = isEncoder ? MediaCodec.createEncoderByType(mediaType)
                : MediaCodec.createDecoderByType(mediaType);
        boolean isDefault = codec.getName().equalsIgnoreCase(codecName);
        mDefaultCodecs.put(mediaType, codec.getName());
        codec.release();
        return isDefault;
    }

    public static boolean isVendorCodec(String codecName) {
        for (MediaCodecInfo codecInfo : MEDIA_CODEC_LIST_ALL.getCodecInfos()) {
            if (codecName.equals(codecInfo.getName())) {
                return codecInfo.isVendor();
            }
        }
        return false;
    }

    public static boolean isSoftwareCodec(String codecName) {
        for (MediaCodecInfo codecInfo : MEDIA_CODEC_LIST_ALL.getCodecInfos()) {
            if (codecName.equals(codecInfo.getName())) {
                return codecInfo.isSoftwareOnly();
            }
        }
        return false;
    }

    public static boolean isHardwareAcceleratedCodec(String codecName) {
        for (MediaCodecInfo codecInfo : MEDIA_CODEC_LIST_ALL.getCodecInfos()) {
            if (codecName.equals(codecInfo.getName())) {
                return codecInfo.isHardwareAccelerated();
            }
        }
        return false;
    }

    /**
     * Stop the current codec session and transfer component to uninitialized state.
     * <p>
     * Some legacy OMX components do not properly clear the internal state at stop().
     * Workaround the issue with resetting the component.
     */
    public static void endCodecSession(MediaCodec codec) {
        if (codec.getName().toUpperCase(Locale.getDefault()).startsWith("OMX")) {
            codec.reset();
        } else {
            codec.stop();
        }
    }

    protected static String paramToString(Object[] param) {
        StringBuilder paramStr = new StringBuilder("[  ");
        for (int j = 0; j < param.length - 1; j++) {
            Object o = param[j];
            if (o == null) {
                paramStr.append("null, ");
            } else if (o instanceof String[]) {
                int length = Math.min(((String[]) o).length, 3);
                paramStr.append("{");
                for (int i = 0; i < length; i++) {
                    paramStr.append(((String[]) o)[i]).append(", ");
                }
                paramStr.delete(paramStr.length() - 2, paramStr.length())
                        .append(length == ((String[]) o).length ? "}, " : ", ... }, ");
            } else if (o instanceof int[]) {
                paramStr.append("{");
                for (int i = 0; i < ((int[]) o).length; i++) {
                    paramStr.append(((int[]) o)[i]).append(", ");
                }
                paramStr.delete(paramStr.length() - 2, paramStr.length()).append("}, ");
            } else if (o instanceof Map) {
                int length = 0;
                paramStr.append("{ ");
                Map map = (Map) o;
                for (Object key : map.keySet()) {
                    paramStr.append(key).append(" = ").append(map.get(key)).append(", ");
                    length++;
                    if (length > 1) break;
                }
                paramStr.delete(paramStr.length() - 2, paramStr.length())
                        .append(length == map.size() ? "}, " : ", ... }, ");
            } else if (o instanceof EncoderConfigParams[]) {
                int length = Math.min(((EncoderConfigParams[]) o).length, 3);
                paramStr.append("{");
                for (int i = 0; i < ((EncoderConfigParams[]) o).length; i++) {
                    paramStr.append(((EncoderConfigParams[]) o)[i]).append(", ");
                }
                paramStr.delete(paramStr.length() - 2, paramStr.length())
                        .append(length == ((EncoderConfigParams[]) o).length ? "}, " : ", ... }, ");
            } else paramStr.append(o).append(", ");
        }
        paramStr.delete(paramStr.length() - 2, paramStr.length()).append("  ]");
        return paramStr.toString();
    }

    public static ArrayList<String> compileRequiredMediaTypeList(boolean isEncoder,
            boolean needAudio, boolean needVideo) {
        Set<String> list = new HashSet<>();
        if (!isEncoder) {
            if (MediaUtils.hasAudioOutput() && needAudio) {
                // sec 5.1.2
                list.add(MediaFormat.MIMETYPE_AUDIO_AAC);
                list.add(MediaFormat.MIMETYPE_AUDIO_FLAC);
                list.add(MediaFormat.MIMETYPE_AUDIO_MPEG);
                list.add(MediaFormat.MIMETYPE_AUDIO_VORBIS);
                list.add(MediaFormat.MIMETYPE_AUDIO_RAW);
                list.add(MediaFormat.MIMETYPE_AUDIO_OPUS);
            }
            if (MediaUtils.isHandheld() || MediaUtils.isTablet() || MediaUtils.isTv()
                    || MediaUtils.isAutomotive()) {
                // sec 2.2.2, 2.3.2, 2.5.2
                if (needAudio) {
                    list.add(MediaFormat.MIMETYPE_AUDIO_AAC);
                }
                if (needVideo) {
                    list.add(MediaFormat.MIMETYPE_VIDEO_AVC);
                    list.add(MediaFormat.MIMETYPE_VIDEO_MPEG4);
                    list.add(MediaFormat.MIMETYPE_VIDEO_VP8);
                    list.add(MediaFormat.MIMETYPE_VIDEO_VP9);
                }
            }
            if (MediaUtils.isHandheld() || MediaUtils.isTablet()) {
                // sec 2.2.2
                if (needAudio) {
                    list.add(MediaFormat.MIMETYPE_AUDIO_AMR_NB);
                    list.add(MediaFormat.MIMETYPE_AUDIO_AMR_WB);
                }
                if (needVideo) {
                    list.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
                    if (IS_AT_LEAST_U) {
                        list.add(MediaFormat.MIMETYPE_VIDEO_AV1);
                    }
                }
            }
            if (MediaUtils.isTv() && needVideo) {
                // sec 2.3.2
                list.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
                list.add(MediaFormat.MIMETYPE_VIDEO_MPEG2);
                if (IS_AT_LEAST_U) {
                    list.add(MediaFormat.MIMETYPE_VIDEO_AV1);
                }
            }
        } else {
            if (MediaUtils.hasMicrophone() && needAudio) {
                // sec 5.1.1
                // TODO(b/154423550)
                // list.add(MediaFormat.MIMETYPE_AUDIO_RAW);
                list.add(MediaFormat.MIMETYPE_AUDIO_FLAC);
                list.add(MediaFormat.MIMETYPE_AUDIO_OPUS);
            }
            if (MediaUtils.isHandheld() || MediaUtils.isTablet() || MediaUtils.isTv()
                    || MediaUtils.isAutomotive()) {
                // sec 2.2.2, 2.3.2, 2.5.2
                if (needAudio) {
                    list.add(MediaFormat.MIMETYPE_AUDIO_AAC);
                }
                if (needVideo) {
                    if ((MediaUtils.isHandheld() || MediaUtils.isTablet() || MediaUtils.isTv())
                            && IS_AT_LEAST_U) {
                        list.add(MediaFormat.MIMETYPE_VIDEO_AV1);
                    }
                    list.add(MediaFormat.MIMETYPE_VIDEO_AVC);
                    list.add(MediaFormat.MIMETYPE_VIDEO_VP8);
                }
            }
            if ((MediaUtils.isHandheld() || MediaUtils.isTablet()) && needAudio) {
                // sec 2.2.2
                list.add(MediaFormat.MIMETYPE_AUDIO_AMR_NB);
                list.add(MediaFormat.MIMETYPE_AUDIO_AMR_WB);
            }
        }
        return new ArrayList<>(list);
    }

    private static ArrayList<String> compileCompleteTestMediaTypesList(boolean isEncoder,
            boolean needAudio, boolean needVideo) {
        ArrayList<String> mediaTypes = new ArrayList<>();
        if (mediaTypeSelKeys == null) {
            ArrayList<String> cddRequiredMediaTypesList =
                    compileRequiredMediaTypeList(isEncoder, needAudio, needVideo);
            MediaCodecInfo[] codecInfos = MEDIA_CODEC_LIST_REGULAR.getCodecInfos();
            for (MediaCodecInfo codecInfo : codecInfos) {
                if (codecInfo.isEncoder() != isEncoder) continue;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias()) continue;
                String[] types = codecInfo.getSupportedTypes();
                for (String type : types) {
                    if (mediaTypePrefix != null && !type.startsWith(mediaTypePrefix)) {
                        continue;
                    }
                    if (!needAudio && type.startsWith("audio/")) continue;
                    if (!needVideo && type.startsWith("video/")) continue;
                    if (!mediaTypes.contains(type)) {
                        mediaTypes.add(type);
                    }
                }
            }
            if (mediaTypePrefix != null) {
                return mediaTypes;
            }
            // feature_video_output is not exposed to package manager. Testing for video output
            // ports, such as VGA, HDMI, DisplayPort, or a wireless port for display is also not
            // direct.
            /* sec 5.2: device implementations include an embedded screen display with the
            diagonal length of at least 2.5 inches or include a video output port or declare the
            support of a camera */
            if (isEncoder && needVideo
                    && (MediaUtils.hasCamera() || MediaUtils.getScreenSizeInInches() >= 2.5)
                    && !mediaTypes.contains(MediaFormat.MIMETYPE_VIDEO_AVC)
                    && !mediaTypes.contains(MediaFormat.MIMETYPE_VIDEO_VP8)) {
                // Add required cdd mediaTypes here so that respective codec tests fail.
                mediaTypes.add(MediaFormat.MIMETYPE_VIDEO_AVC);
                mediaTypes.add(MediaFormat.MIMETYPE_VIDEO_VP8);
                Log.e(LOG_TAG, "device must support at least one of VP8 or AVC video encoders");
            }
            for (String mediaType : cddRequiredMediaTypesList) {
                if (!mediaTypes.contains(mediaType)) {
                    // Add required cdd mediaTypes here so that respective codec tests fail.
                    mediaTypes.add(mediaType);
                    Log.e(LOG_TAG, "no codec found for mediaType " + mediaType
                            + " as required by cdd");
                }
            }
        } else {
            for (Map.Entry<String, String> entry : CODEC_SEL_KEY_MEDIA_TYPE_MAP.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (mediaTypeSelKeys.contains(key) && !mediaTypes.contains(value)) {
                    mediaTypes.add(value);
                }
            }
        }
        return mediaTypes;
    }

    public static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList,
            boolean isEncoder, boolean needAudio, boolean needVideo, boolean mustTestAllCodecs) {
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo,
                mustTestAllCodecs, ComponentClass.ALL);
    }

    public static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList,
            boolean isEncoder, boolean needAudio, boolean needVideo, boolean mustTestAllCodecs,
            ComponentClass selectSwitch) {
        ArrayList<String> mediaTypes = compileCompleteTestMediaTypesList(isEncoder,
                needAudio, needVideo);
        ArrayList<String> cddRequiredMediaTypesList =
                compileRequiredMediaTypeList(isEncoder, needAudio, needVideo);
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (String mediaType : mediaTypes) {
            ArrayList<String> totalListOfCodecs =
                    selectCodecs(mediaType, null, null, isEncoder, selectSwitch);
            ArrayList<String> listOfCodecs = new ArrayList<>();
            if (codecPrefix != null || codecFilter != null) {
                for (String codec : totalListOfCodecs) {
                    if ((codecPrefix != null && codec.startsWith(codecPrefix))
                            || (codecFilter != null && codecFilter.matcher(codec).matches())) {
                        listOfCodecs.add(codec);
                    }
                }
            } else {
                listOfCodecs = totalListOfCodecs;
            }
            if (mustTestAllCodecs && listOfCodecs.size() == 0 && codecPrefix == null) {
                listOfCodecs.add(INVALID_CODEC + mediaType);
            }
            boolean miss = true;
            for (Object[] arg : exhaustiveArgsList) {
                if (mediaType.equals(arg[0])) {
                    for (String codec : listOfCodecs) {
                        Object[] argUpdate = new Object[argLength + 2];
                        argUpdate[0] = codec;
                        System.arraycopy(arg, 0, argUpdate, 1, argLength);
                        argUpdate[argLength + 1] = paramToString(argUpdate);
                        argsList.add(argUpdate);
                    }
                    miss = false;
                }
            }
            if (miss && mustTestAllCodecs) {
                if (!cddRequiredMediaTypesList.contains(mediaType)) {
                    Log.w(LOG_TAG, "no test vectors available for optional mediaType type "
                            + mediaType);
                    continue;
                }
                for (String codec : listOfCodecs) {
                    Object[] argUpdate = new Object[argLength + 2];
                    argUpdate[0] = codec;
                    argUpdate[1] = mediaType;
                    System.arraycopy(exhaustiveArgsList.get(0), 1, argUpdate, 2, argLength - 1);
                    argUpdate[argLength + 1] = paramToString(argUpdate);
                    argsList.add(argUpdate);
                }
            }
        }
        return argsList;
    }

    public static ArrayList<String> selectCodecs(String mediaType, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder) {
        return selectCodecs(mediaType, formats, features, isEncoder, ComponentClass.ALL);
    }

    public static ArrayList<String> selectCodecs(String mediaType, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder, ComponentClass selectSwitch) {
        MediaCodecInfo[] codecInfos = MEDIA_CODEC_LIST_REGULAR.getCodecInfos();
        ArrayList<String> listOfCodecs = new ArrayList<>();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (codecInfo.isEncoder() != isEncoder) continue;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias()) continue;
            if (selectSwitch == ComponentClass.HARDWARE && !codecInfo.isHardwareAccelerated()) {
                continue;
            } else if (selectSwitch == ComponentClass.SOFTWARE && !codecInfo.isSoftwareOnly()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mediaType)) {
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

    public static int getWidth(MediaFormat format) {
        int width = format.getInteger(MediaFormat.KEY_WIDTH, -1);
        if (format.containsKey("crop-left") && format.containsKey("crop-right")) {
            width = format.getInteger("crop-right") + 1 - format.getInteger("crop-left");
        }
        return width;
    }

    public static int getHeight(MediaFormat format) {
        int height = format.getInteger(MediaFormat.KEY_HEIGHT, -1);
        if (format.containsKey("crop-top") && format.containsKey("crop-bottom")) {
            height = format.getInteger("crop-bottom") + 1 - format.getInteger("crop-top");
        }
        return height;
    }

    public static byte[] loadByteArrayFromString(final String str) {
        if (str == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("[0-9a-fA-F]{2}");
        Matcher matcher = pattern.matcher(str);
        // allocate a large enough byte array first
        byte[] tempArray = new byte[str.length() / 2];
        int i = 0;
        while (matcher.find()) {
            tempArray[i++] = (byte) Integer.parseInt(matcher.group(), 16);
        }
        return Arrays.copyOfRange(tempArray, 0, i);
    }

    public static String byteArrayToHexString(byte[] bytes) {
        final char[] hexArray =
                {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        char[] hexChars = new char[bytes.length * 3];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }

    protected abstract void enqueueInput(int bufferIndex) throws IOException;

    protected abstract void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info);

    @Rule
    public final TestName mTestName = new TestName();

    @Before
    public void setUpCodecTestBase() {
        mTestConfig.setLength(0);
        mTestConfig.append("\n##################       Test Details        ####################\n");
        mTestConfig.append("Test Name :- ").append(mTestName.getMethodName()).append("\n");
        mTestConfig.append("Test Parameters :- ").append(mAllTestParams).append("\n");
        if (mCodecName != null && mCodecName.startsWith(INVALID_CODEC)) {
            fail("no valid component available for current test \n" + mTestConfig);
        }
    }

    @After
    public void tearDownCodecTestBase() {
        mSurface = null;
        if (mActivity != null) {
            mActivity.finish();
            mActivity = null;
        }
        if (mImageSurface != null) {
            mImageSurface.release();
            mImageSurface = null;
        }
        if (mCodec != null) {
            mCodec.release();
            mCodec = null;
        }
    }

    protected void configureCodec(MediaFormat format, boolean isAsync,
            boolean signalEOSWithLastFrame, boolean isEncoder) {
        configureCodec(format, isAsync, signalEOSWithLastFrame, isEncoder, 0);
    }

    protected void configureCodec(MediaFormat format, boolean isAsync,
            boolean signalEOSWithLastFrame, boolean isEncoder, int flag) {
        if (IS_AT_LEAST_R && ((flag & MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL) != 0)) {
            if (!isAsync || !signalEOSWithLastFrame) {
                throw new RuntimeException("Block model feature testing requires mode of operation"
                        + " to be asynchronous and eos to be signalled along with last frame");
            }
        }
        resetContext(isAsync, signalEOSWithLastFrame);
        mAsyncHandle.setCallBack(mCodec, isAsync);
        // signalEOS flag has nothing to do with configure. We are using this flag to try all
        // available configure apis
        if (signalEOSWithLastFrame) {
            mCodec.configure(format, mSurface, null,
                    (isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0) | flag);
        } else {
            mCodec.configure(format, mSurface,
                    (isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0) | flag, null);
        }
        mTestEnv.setLength(0);
        mTestEnv.append("###################      Test Environment       #####################\n");
        mTestEnv.append(String.format("Component under test :- %s \n", mCodecName));
        mTestEnv.append("Format under test :- ").append(format).append("\n");
        mTestEnv.append(String.format("Component operating in :- %s mode \n",
                (isAsync ? "asynchronous" : "synchronous")));
        mTestEnv.append(String.format("Component received input eos :- %s \n",
                (signalEOSWithLastFrame ? "with full buffer" : "with empty buffer")));
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec configured");
        }
    }

    public OutputManager getOutputManager() {
        return mOutputBuff;
    }

    public MediaFormat getOutputFormat() {
        return mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() : mOutFormat;
    }

    public int getOutputCount() {
        return mOutputCount;
    }

    protected void flushCodec() {
        mCodec.flush();
        // TODO(b/147576107): is it ok to clearQueues right away or wait for some signal
        mAsyncHandle.clearQueues();
        mSawInputEOS = false;
        mSawOutputEOS = false;
        mInputCount = 0;
        mOutputCount = 0;
        mPrevOutputPts = Long.MIN_VALUE;
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec flushed");
        }
    }

    protected void reConfigureCodec(MediaFormat format, boolean isAsync,
            boolean signalEOSWithLastFrame, boolean isEncoder) {
        /* TODO(b/147348711) */
        if (false) mCodec.stop();
        else mCodec.reset();
        configureCodec(format, isAsync, signalEOSWithLastFrame, isEncoder);
    }

    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        mAsyncHandle.resetContext();
        mIsCodecInAsyncMode = isAsync;
        mSawInputEOS = false;
        mSawOutputEOS = false;
        mSignalEOSWithLastFrame = signalEOSWithLastFrame;
        mInputCount = 0;
        mOutputCount = 0;
        mPrevOutputPts = Long.MIN_VALUE;
        mSignalledOutFormatChanged = false;
    }

    protected void enqueueEOS(int bufferIndex) {
        if (!mSawInputEOS) {
            mCodec.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mSawInputEOS = true;
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "Queued End of Stream");
            }
        }
    }

    protected void doWork(int frameLimit) throws InterruptedException, IOException {
        int frameCount = 0;
        if (mIsCodecInAsyncMode) {
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS && frameCount < frameLimit) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        // <id, info> corresponds to output callback. Handle it accordingly
                        dequeueOutput(bufferID, info);
                    } else {
                        // <id, null> corresponds to input callback. Handle it accordingly
                        enqueueInput(bufferID);
                        frameCount++;
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mSawInputEOS && frameCount < frameLimit) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
                int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueInput(inputBufferId);
                    frameCount++;
                }
            }
        }
    }

    protected void queueEOS() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        dequeueOutput(bufferID, info);
                    } else {
                        enqueueEOS(element.first);
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawInputEOS) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
                int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueEOS(inputBufferId);
                }
            }
        }
    }

    protected void waitForAllOutputs() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!mAsyncHandle.hasSeenError() && !mSawOutputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
                if (element != null) {
                    dequeueOutput(element.first, element.second);
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawOutputEOS) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
            }
        }
        validateTestState();
    }

    void validateTestState() {
        assertFalse("Encountered error in async mode. \n" + mTestConfig + mTestEnv
                + mAsyncHandle.getErrMsg(), mAsyncHandle.hasSeenError());
        if (mInputCount > 0) {
            assertTrue(String.format("fed %d input frames, received no output frames \n",
                    mInputCount) + mTestConfig + mTestEnv, mOutputCount > 0);
        }
        /*if (mInputCount == 0 && mInputCount != mOutputCount) {
            String msg = String.format("The number of output frames received is not same as number "
                            + "of input frames queued. Output count is %d, Input count is %d \n",
                    mOutputCount, mInputCount);
            // check the pts lists to see what frames are dropped, the below call is needed to
            // get useful error messages
            mOutputBuff.isOutPtsListIdenticalToInpPtsList(true);
            fail(msg + mTestConfig + mTestEnv + mOutputBuff.getErrMsg());
        }*/
    }

    protected void insertHdrDynamicInfo(byte[] info) {
        final Bundle params = new Bundle();
        params.putByteArray(MediaFormat.KEY_HDR10_PLUS_INFO, info);
        mCodec.setParameters(params);
    }

    public boolean isFormatSimilar(MediaFormat inpFormat, MediaFormat outFormat) {
        if (inpFormat == null || outFormat == null) return false;
        String inpMediaType = inpFormat.getString(MediaFormat.KEY_MIME);
        String outMediaType = outFormat.getString(MediaFormat.KEY_MIME);
        // not comparing input and output mediaTypes because for a codec, mediaType is raw on one
        // side and encoded type on the other
        if (outMediaType.startsWith("audio/")) {
            return (inpFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, -1)
                    == outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, -2))
                    && (inpFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, -1)
                    == outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, -2))
                    && inpMediaType.startsWith("audio/");
        } else if (outMediaType.startsWith("video/")) {
            return getWidth(inpFormat) == getWidth(outFormat)
                    && getHeight(inpFormat) == getHeight(outFormat)
                    && inpMediaType.startsWith("video/");
        }
        return true;
    }

    protected PersistableBundle validateMetrics(String codec) {
        PersistableBundle metrics = mCodec.getMetrics();
        assertNotNull("error! MediaCodec.getMetrics() returns null \n" + mTestConfig + mTestEnv,
                metrics);
        assertEquals("error! metrics#MetricsConstants.CODEC is not as expected \n" + mTestConfig
                + mTestEnv, metrics.getString(MediaCodec.MetricsConstants.CODEC), codec);
        assertEquals("error! metrics#MetricsConstants.MODE is not as expected \n" + mTestConfig
                        + mTestEnv, mIsAudio ? MediaCodec.MetricsConstants.MODE_AUDIO :
                        MediaCodec.MetricsConstants.MODE_VIDEO,
                metrics.getString(MediaCodec.MetricsConstants.MODE));
        return metrics;
    }

    protected PersistableBundle validateMetrics(String codec, MediaFormat format) {
        PersistableBundle metrics = validateMetrics(codec);
        if (mIsVideo) {
            assertEquals("error! metrics#MetricsConstants.WIDTH is not as expected\n" + mTestConfig
                            + mTestEnv, metrics.getInt(MediaCodec.MetricsConstants.WIDTH),
                    getWidth(format));
            assertEquals("error! metrics#MetricsConstants.HEIGHT is not as expected\n" + mTestConfig
                            + mTestEnv, metrics.getInt(MediaCodec.MetricsConstants.HEIGHT),
                    getHeight(format));
        }
        assertEquals("error! metrics#MetricsConstants.SECURE is not as expected\n" + mTestConfig
                + mTestEnv, 0, metrics.getInt(MediaCodec.MetricsConstants.SECURE));
        return metrics;
    }

    public void validateColorAspects(MediaFormat fmt, int range, int standard, int transfer) {
        int colorRange = fmt.getInteger(MediaFormat.KEY_COLOR_RANGE, UNSPECIFIED);
        int colorStandard = fmt.getInteger(MediaFormat.KEY_COLOR_STANDARD, UNSPECIFIED);
        int colorTransfer = fmt.getInteger(MediaFormat.KEY_COLOR_TRANSFER, UNSPECIFIED);
        if (range > UNSPECIFIED) {
            assertEquals("error! color range mismatch \n" + mTestConfig + mTestEnv, range,
                    colorRange);
        }
        if (standard > UNSPECIFIED) {
            assertEquals("error! color standard mismatch \n" + mTestConfig + mTestEnv, standard,
                    colorStandard);
        }
        if (transfer > UNSPECIFIED) {
            assertEquals("error! color transfer mismatch \n" + mTestConfig + mTestEnv, transfer,
                    colorTransfer);
        }
    }

    protected void validateHDRInfo(String hdrInfoKey, ByteBuffer hdrInfoRef, ByteBuffer hdrInfoTest,
            Long framePts) {
        if (!hdrInfoRef.equals(hdrInfoTest)) {
            StringBuilder msg = new StringBuilder(
                    "###################       Error Details         #####################\n");
            byte[] ref = new byte[hdrInfoRef.capacity()];
            hdrInfoRef.get(ref);
            hdrInfoRef.rewind();
            byte[] test = new byte[hdrInfoTest.capacity()];
            hdrInfoTest.get(test);
            hdrInfoTest.rewind();
            msg.append("ref info :- \n");
            for (byte b : ref) {
                msg.append(String.format("%2x ", b));
            }
            msg.append("\ntest info :- \n");
            for (byte b : test) {
                msg.append(String.format("%2x ", b));
            }
            fail("Frame pts " + framePts + ": error! mismatch seen between ref and test info of "
                    + hdrInfoKey + "\n" + mTestConfig + mTestEnv + msg);
        }
    }

    protected void validateHDRInfo(MediaFormat fmt, String hdrInfoKey, ByteBuffer hdrInfoRef,
            Long framePts) {
        ByteBuffer hdrInfo = fmt.getByteBuffer(hdrInfoKey, null);
        assertNotNull("error! no " + hdrInfoKey + " present in format : " + fmt + "\n "
                + mTestConfig + mTestEnv, hdrInfo);
        validateHDRInfo(hdrInfoKey, hdrInfoRef, hdrInfo, framePts);
    }

    protected void setUpSurface(CodecTestActivity activity) throws InterruptedException {
        activity.waitTillSurfaceIsCreated();
        mSurface = activity.getSurface();
        assertNotNull("Surface created is null \n" + mTestConfig + mTestEnv, mSurface);
        assertTrue("Surface created is invalid \n" + mTestConfig + mTestEnv, mSurface.isValid());
    }

    protected void setUpSurface(int width, int height, int format, int maxImages,
            Function<Image, Boolean> predicate) {
        mImageSurface.createSurface(width, height, format, maxImages, predicate);
        mSurface = mImageSurface.getSurface();
        assertNotNull("Surface created is null \n" + mTestConfig + mTestEnv, mSurface);
        assertTrue("Surface created is invalid \n" + mTestConfig + mTestEnv, mSurface.isValid());
    }
}
