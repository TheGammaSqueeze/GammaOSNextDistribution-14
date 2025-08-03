/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.media.MediaCodecInfo.CodecProfileLevel.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * Wrapper class for testing encoders support for profile and level
 */
public class EncoderProfileLevelTestBase extends CodecEncoderTestBase {
    private static final String LOG_TAG = EncoderProfileLevelTestBase.class.getSimpleName();
    private static final int[] AVC_LEVELS =
            new int[]{AVCLevel1, AVCLevel1b, AVCLevel11, AVCLevel12, AVCLevel13, AVCLevel2,
                    AVCLevel21, AVCLevel22, AVCLevel3, AVCLevel31, AVCLevel32, AVCLevel4,
                    AVCLevel41, AVCLevel42, AVCLevel5, AVCLevel51, AVCLevel52, AVCLevel6,
                    AVCLevel61, AVCLevel62};
    private static final int[] MPEG2_LEVELS =
            new int[]{MPEG2LevelLL, MPEG2LevelML, MPEG2LevelH14, MPEG2LevelHL, MPEG2LevelHP};
    private static final int[] MPEG4_LEVELS =
            new int[]{MPEG4Level0, MPEG4Level0b, MPEG4Level1, MPEG4Level2, MPEG4Level3,
                    MPEG4Level3b, MPEG4Level4, MPEG4Level4a, MPEG4Level5, MPEG4Level6};
    private static final int[] VP9_LEVELS =
            new int[]{VP9Level1, VP9Level11, VP9Level2, VP9Level21, VP9Level3, VP9Level31,
                    VP9Level4, VP9Level41, VP9Level5, VP9Level51, VP9Level52, VP9Level6,
                    VP9Level61, VP9Level62};
    private static final int[] H263_LEVELS =
            new int[]{H263Level10, H263Level20, H263Level30, H263Level40, H263Level45,
                    H263Level50, H263Level60, H263Level70};
    private static final int[] AV1_LEVELS =
            new int[]{AV1Level2, AV1Level21, AV1Level3, AV1Level31, AV1Level4, AV1Level41,
                    AV1Level5, AV1Level51, AV1Level52, AV1Level53, AV1Level6, AV1Level61,
                    AV1Level62, AV1Level63};
    private static final int[] HEVC_LEVELS =
            new int[]{HEVCMainTierLevel1, HEVCHighTierLevel1, HEVCMainTierLevel2,
                    HEVCHighTierLevel2, HEVCMainTierLevel21, HEVCHighTierLevel21,
                    HEVCMainTierLevel3, HEVCHighTierLevel3, HEVCMainTierLevel31,
                    HEVCHighTierLevel31, HEVCMainTierLevel4, HEVCHighTierLevel4,
                    HEVCMainTierLevel41, HEVCHighTierLevel41, HEVCMainTierLevel5,
                    HEVCHighTierLevel5, HEVCMainTierLevel51, HEVCHighTierLevel51,
                    HEVCMainTierLevel52, HEVCHighTierLevel52, HEVCMainTierLevel6,
                    HEVCHighTierLevel6, HEVCHighTierLevel61, HEVCHighTierLevel62,
                    HEVCMainTierLevel61, HEVCMainTierLevel62};

    public static final HashMap<String, int[]> LEVEL_MAP = new HashMap<>();

    static {
        LEVEL_MAP.put(MediaFormat.MIMETYPE_VIDEO_AVC, AVC_LEVELS);
        LEVEL_MAP.put(MediaFormat.MIMETYPE_VIDEO_MPEG2, MPEG2_LEVELS);
        LEVEL_MAP.put(MediaFormat.MIMETYPE_VIDEO_MPEG4, MPEG4_LEVELS);
        LEVEL_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_LEVELS);
        LEVEL_MAP.put(MediaFormat.MIMETYPE_VIDEO_H263, H263_LEVELS);
        LEVEL_MAP.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_LEVELS);
        LEVEL_MAP.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_LEVELS);
    }

    private static int divUp(int num, int den) {
        return (num + den - 1) / den;
    }

    public static int getMinLevel(String mediaType, int width, int height, int frameRate,
            int bitrate, int profile) {
        switch (mediaType) {
            case MediaFormat.MIMETYPE_VIDEO_AVC:
                return getMinLevelAVC(width, height, frameRate, bitrate);
            case MediaFormat.MIMETYPE_VIDEO_HEVC:
                return getMinLevelHEVC(width, height, frameRate, bitrate);
            case MediaFormat.MIMETYPE_VIDEO_H263:
                return getMinLevelH263(width, height, frameRate, bitrate);
            case MediaFormat.MIMETYPE_VIDEO_MPEG2:
                return getMinLevelMPEG2(width, height, frameRate, bitrate);
            case MediaFormat.MIMETYPE_VIDEO_MPEG4:
                return getMinLevelMPEG4(width, height, frameRate, bitrate, profile);
            case MediaFormat.MIMETYPE_VIDEO_VP9:
                return getMinLevelVP9(width, height, frameRate, bitrate);
            case MediaFormat.MIMETYPE_VIDEO_AV1:
                return getMinLevelAV1(width, height, frameRate, bitrate);
            default:
                return -1;
        }
    }

    private static int getMinLevelAVC(int width, int height, int frameRate, int bitrate) {
        class LevelLimitAVC {
            private LevelLimitAVC(int level, int mbsPerSec, long mbs, int bitrate) {
                this.mLevel = level;
                this.mMbsPerSec = mbsPerSec;
                this.mMbs = mbs;
                this.mBitrate = bitrate;
            }

            private final int mLevel;
            private final int mMbsPerSec;
            private final long mMbs;
            private final int mBitrate;
        }
        LevelLimitAVC[] limitsAVC = {
                new LevelLimitAVC(AVCLevel1, 1485, 99, 64000),
                new LevelLimitAVC(AVCLevel1b, 1485, 99, 128000),
                new LevelLimitAVC(AVCLevel11, 3000, 396, 192000),
                new LevelLimitAVC(AVCLevel12, 6000, 396, 384000),
                new LevelLimitAVC(AVCLevel13, 11880, 396, 768000),
                new LevelLimitAVC(AVCLevel2, 11880, 396, 2000000),
                new LevelLimitAVC(AVCLevel21, 19800, 792, 4000000),
                new LevelLimitAVC(AVCLevel22, 20250, 1620, 4000000),
                new LevelLimitAVC(AVCLevel3, 40500, 1620, 10000000),
                new LevelLimitAVC(AVCLevel31, 108000, 3600, 14000000),
                new LevelLimitAVC(AVCLevel32, 216000, 5120, 20000000),
                new LevelLimitAVC(AVCLevel4, 245760, 8192, 20000000),
                new LevelLimitAVC(AVCLevel41, 245760, 8192, 50000000),
                new LevelLimitAVC(AVCLevel42, 522240, 8704, 50000000),
                new LevelLimitAVC(AVCLevel5, 589824, 22080, 135000000),
                new LevelLimitAVC(AVCLevel51, 983040, 36864, 240000000),
                new LevelLimitAVC(AVCLevel52, 2073600, 36864, 240000000),
                new LevelLimitAVC(AVCLevel6, 4177920, 139264, 240000000),
                new LevelLimitAVC(AVCLevel61, 8355840, 139264, 480000000),
                new LevelLimitAVC(AVCLevel62, 16711680, 139264, 800000000),
        };
        int blockSize = 16;
        int mbs = divUp(width, blockSize) * divUp(height, blockSize);
        float mbsPerSec = mbs * frameRate;
        for (LevelLimitAVC levelLimitsAVC : limitsAVC) {
            if (mbs <= levelLimitsAVC.mMbs && mbsPerSec <= levelLimitsAVC.mMbsPerSec
                    && bitrate <= levelLimitsAVC.mBitrate) {
                return levelLimitsAVC.mLevel;
            }
        }
        // if none of the levels suffice, select the highest level
        return AVCLevel62;
    }

    private static int getMinLevelHEVC(int width, int height, int frameRate, int bitrate) {
        class LevelLimitHEVC {
            private LevelLimitHEVC(int level, long pixelsPerSec, long pixelsPerFrame, int bitrate) {
                this.mLevel = level;
                this.mPixelsPerSec = pixelsPerSec;
                this.mPixelsPerFrame = pixelsPerFrame;
                this.mBitrate = bitrate;
            }

            private final int mLevel;
            private final long mPixelsPerSec;
            private final long mPixelsPerFrame;
            private final int mBitrate;
        }
        LevelLimitHEVC[] limitsHEVC = {
                new LevelLimitHEVC(HEVCMainTierLevel1, 552960, 36864, 128000),
                new LevelLimitHEVC(HEVCMainTierLevel2, 3686400, 122880, 1500000),
                new LevelLimitHEVC(HEVCMainTierLevel21, 7372800, 245760, 3000000),
                new LevelLimitHEVC(HEVCMainTierLevel3, 16588800, 552960, 6000000),
                new LevelLimitHEVC(HEVCMainTierLevel31, 33177600, 983040, 10000000),
                new LevelLimitHEVC(HEVCMainTierLevel4, 66846720, 2228224, 12000000),
                new LevelLimitHEVC(HEVCHighTierLevel4, 66846720, 2228224, 30000000),
                new LevelLimitHEVC(HEVCMainTierLevel41, 133693440, 2228224, 20000000),
                new LevelLimitHEVC(HEVCHighTierLevel41, 133693440, 2228224, 50000000),
                new LevelLimitHEVC(HEVCMainTierLevel5, 267386880, 8912896, 25000000),
                new LevelLimitHEVC(HEVCHighTierLevel5, 267386880, 8912896, 100000000),
                new LevelLimitHEVC(HEVCMainTierLevel51, 534773760, 8912896, 40000000),
                new LevelLimitHEVC(HEVCHighTierLevel51, 534773760, 8912896, 160000000),
                new LevelLimitHEVC(HEVCMainTierLevel52, 1069547520, 8912896, 60000000),
                new LevelLimitHEVC(HEVCHighTierLevel52, 1069547520, 8912896, 240000000),
                new LevelLimitHEVC(HEVCMainTierLevel6, 1069547520, 35651584, 60000000),
                new LevelLimitHEVC(HEVCHighTierLevel6, 1069547520, 35651584, 240000000),
                new LevelLimitHEVC(HEVCMainTierLevel61, 2139095040, 35651584, 120000000),
                new LevelLimitHEVC(HEVCHighTierLevel61, 2139095040, 35651584, 480000000),
                new LevelLimitHEVC(HEVCMainTierLevel62, 4278190080L, 35651584, 240000000),
                new LevelLimitHEVC(HEVCHighTierLevel62, 4278190080L, 35651584, 800000000),
        };
        int blockSize = 8;
        int blocks = divUp(width, blockSize) * divUp(height, blockSize);
        long pixelsPerFrame = blocks * blockSize * blockSize;
        long pixelsPerSec = pixelsPerFrame * frameRate;
        for (LevelLimitHEVC levelLimitsHEVC : limitsHEVC) {
            if (pixelsPerFrame <= levelLimitsHEVC.mPixelsPerFrame
                    && pixelsPerSec <= levelLimitsHEVC.mPixelsPerSec
                    && bitrate <= levelLimitsHEVC.mBitrate) {
                return levelLimitsHEVC.mLevel;
            }
        }
        // if none of the levels suffice, select the highest level
        return HEVCHighTierLevel62;
    }

    private static int getMinLevelH263(int width, int height, int frameRate, int bitrate) {
        class LevelLimitH263 {
            private LevelLimitH263(int level, long pixelsPerSec, int width, int height,
                    int frameRate, int bitrate) {
                this.mLevel = level;
                this.mPixelsPerSec = pixelsPerSec;
                this.mWidth = width;
                this.mHeight = height;
                this.mFrameRate = frameRate;
                this.mBitrate = bitrate;
            }

            private final int mLevel;
            private final long mPixelsPerSec;
            private final int mWidth;
            private final int mHeight;
            private final int mFrameRate;
            private final int mBitrate;
        }
        LevelLimitH263[] limitsH263 = {
                new LevelLimitH263(H263Level10, 380160, 176, 144, 15, 64000),
                new LevelLimitH263(H263Level45, 380160, 176, 144, 15, 128000),
                new LevelLimitH263(H263Level20, 1520640, 352, 288, 30, 128000),
                new LevelLimitH263(H263Level30, 3041280, 352, 288, 30, 384000),
                new LevelLimitH263(H263Level40, 3041280, 352, 288, 30, 2048000),
                new LevelLimitH263(H263Level50, 5068800, 352, 288, 60, 4096000),
                new LevelLimitH263(H263Level60, 10368000, 720, 288, 60, 8192000),
                new LevelLimitH263(H263Level70, 20736000, 720, 576, 60, 16384000),
        };
        int blockSize = 16;
        int mbs = divUp(width, blockSize) * divUp(height, blockSize);
        int size = mbs * blockSize * blockSize;
        int pixelsPerSec = size * frameRate;
        for (LevelLimitH263 levelLimitsH263 : limitsH263) {
            if (pixelsPerSec <= levelLimitsH263.mPixelsPerSec && height <= levelLimitsH263.mHeight
                    && width <= levelLimitsH263.mWidth && frameRate <= levelLimitsH263.mFrameRate
                    && bitrate <= levelLimitsH263.mBitrate) {
                return levelLimitsH263.mLevel;
            }
        }
        // if none of the levels suffice, select the highest level
        return H263Level70;
    }

    private static int getMinLevelVP9(int width, int height, int frameRate, int bitrate) {
        class LevelLimitVP9 {
            private LevelLimitVP9(int level, long pixelsPerSec, int size, int maxWH, int bitrate) {
                this.mLevel = level;
                this.mPixelsPerSec = pixelsPerSec;
                this.mSize = size;
                this.mMaxWH = maxWH;
                this.mBitrate = bitrate;
            }

            private final int mLevel;
            private final long mPixelsPerSec;
            private final int mSize;
            private final int mMaxWH;
            private final int mBitrate;
        }
        LevelLimitVP9[] limitsVP9 = {
                new LevelLimitVP9(VP9Level1, 829440, 36864, 512, 200000),
                new LevelLimitVP9(VP9Level11, 2764800, 73728, 768, 800000),
                new LevelLimitVP9(VP9Level2, 4608000, 122880, 960, 1800000),
                new LevelLimitVP9(VP9Level21, 9216000, 245760, 1344, 3600000),
                new LevelLimitVP9(VP9Level3, 20736000, 552960, 2048, 7200000),
                new LevelLimitVP9(VP9Level31, 36864000, 983040, 2752, 12000000),
                new LevelLimitVP9(VP9Level4, 83558400, 2228224, 4160, 18000000),
                new LevelLimitVP9(VP9Level41, 160432128, 2228224, 4160, 30000000),
                new LevelLimitVP9(VP9Level5, 311951360, 8912896, 8384, 60000000),
                new LevelLimitVP9(VP9Level51, 588251136, 8912896, 8384, 120000000),
                new LevelLimitVP9(VP9Level52, 1176502272, 8912896, 8384, 180000000),
                new LevelLimitVP9(VP9Level6, 1176502272, 35651584, 16832, 180000000),
                new LevelLimitVP9(VP9Level61, 2353004544L, 35651584, 16832, 240000000),
                new LevelLimitVP9(VP9Level62, 4706009088L, 35651584, 16832, 480000000),
        };
        int size = width * height;
        int pixelsPerSec = size * frameRate;
        int maxWH = Math.max(width, height);
        for (LevelLimitVP9 levelLimitsVP9 : limitsVP9) {
            if (pixelsPerSec <= levelLimitsVP9.mPixelsPerSec && size <= levelLimitsVP9.mSize
                    && maxWH <= levelLimitsVP9.mMaxWH && bitrate <= levelLimitsVP9.mBitrate) {
                return levelLimitsVP9.mLevel;
            }
        }
        // if none of the levels suffice, select the highest level
        return VP9Level62;
    }

    private static int getMinLevelMPEG2(int width, int height, int frameRate, int bitrate) {
        class LevelLimitMPEG2 {
            private LevelLimitMPEG2(int level, long pixelsPerSec, int width, int height,
                    int frameRate, int bitrate) {
                this.mLevel = level;
                this.mPixelsPerSec = pixelsPerSec;
                this.mWidth = width;
                this.mHeight = height;
                this.mFrameRate = frameRate;
                this.mBitrate = bitrate;
            }

            private final int mLevel;
            private final long mPixelsPerSec;
            private final int mWidth;
            private final int mHeight;
            private final int mFrameRate;
            private final int mBitrate;
        }
        // main profile limits, higher profiles will also support selected level
        LevelLimitMPEG2[] limitsMPEG2 = {
                new LevelLimitMPEG2(MPEG2LevelLL, 3041280, 352, 288, 30, 4000000),
                new LevelLimitMPEG2(MPEG2LevelML, 10368000, 720, 576, 30, 15000000),
                new LevelLimitMPEG2(MPEG2LevelH14, 47001600, 1440, 1088, 60, 60000000),
                new LevelLimitMPEG2(MPEG2LevelHL, 62668800, 1920, 1088, 60, 80000000),
                new LevelLimitMPEG2(MPEG2LevelHP, 125337600, 1920, 1088, 60, 80000000),
        };
        int blockSize = 16;
        int mbs = divUp(width, blockSize) * divUp(height, blockSize);
        int size = mbs * blockSize * blockSize;
        int pixelsPerSec = size * frameRate;
        for (LevelLimitMPEG2 levelLimitsMPEG2 : limitsMPEG2) {
            if (pixelsPerSec <= levelLimitsMPEG2.mPixelsPerSec && width <= levelLimitsMPEG2.mWidth
                    && height <= levelLimitsMPEG2.mHeight
                    && frameRate <= levelLimitsMPEG2.mFrameRate
                    && bitrate <= levelLimitsMPEG2.mBitrate) {
                return levelLimitsMPEG2.mLevel;
            }
        }
        // if none of the levels suffice, select the highest level
        return MPEG2LevelHP;
    }

    private static int getMinLevelMPEG4(int width, int height, int frameRate, int bitrate,
            int profile) {
        class LevelLimitMPEG4 {
            private LevelLimitMPEG4(int profile, int level, long pixelsPerSec, int width,
                    int height, int frameRate, int bitrate) {
                this.mProfile = profile;
                this.mLevel = level;
                this.mPixelsPerSec = pixelsPerSec;
                this.mWidth = width;
                this.mHeight = height;
                this.mFrameRate = frameRate;
                this.mBitrate = bitrate;
            }

            private final int mProfile;
            private final int mLevel;
            private final long mPixelsPerSec;
            private final int mWidth;
            private final int mHeight;
            private final int mFrameRate;
            private final int mBitrate;
        }
        // simple profile limits, higher profiles will also support selected level
        LevelLimitMPEG4[] limitsMPEG4 = {
                new LevelLimitMPEG4(MPEG4ProfileSimple, MPEG4Level0, 380160, 176, 144, 15, 64000),
                new LevelLimitMPEG4(MPEG4ProfileSimple, MPEG4Level1, 380160, 176, 144, 30, 64000),
                new LevelLimitMPEG4(MPEG4ProfileSimple, MPEG4Level0b, 380160, 176, 144, 15, 128000),
                new LevelLimitMPEG4(MPEG4ProfileSimple, MPEG4Level2, 1520640, 352, 288, 30, 128000),
                new LevelLimitMPEG4(MPEG4ProfileSimple, MPEG4Level3, 3041280, 352, 288, 30, 384000),
                new LevelLimitMPEG4(
                        MPEG4ProfileSimple, MPEG4Level4a, 9216000, 640, 480, 30, 4000000),
                new LevelLimitMPEG4(
                        MPEG4ProfileSimple, MPEG4Level5, 10368000, 720, 576, 30, 8000000),
                new LevelLimitMPEG4(
                        MPEG4ProfileSimple, MPEG4Level6, 27648000, 1280, 720, 30, 12000000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level1, 760320, 176, 144, 30, 128000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level2, 1520640, 352, 288, 30, 384000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level3, 3041280, 352, 288, 30, 768000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level3b, 3041280, 352, 288, 30, 1500000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level4, 6082560, 704, 576, 30, 3000000),
                new LevelLimitMPEG4(
                        MPEG4ProfileAdvancedSimple, MPEG4Level5, 12441600, 720, 576, 30, 8000000),
        };
        int blockSize = 16;
        int mbs = divUp(width, blockSize) * divUp(height, blockSize);
        int size = mbs * blockSize * blockSize;
        int pixelsPerSec = size * frameRate;
        if (profile != MPEG4ProfileAdvancedSimple && profile != MPEG4ProfileSimple) {
            throw new RuntimeException("Unrecognized profile " + profile + " for "
                    + MediaFormat.MIMETYPE_VIDEO_MPEG4);
        }
        for (LevelLimitMPEG4 levelLimitsMPEG4 : limitsMPEG4) {
            if (profile == levelLimitsMPEG4.mProfile
                    && pixelsPerSec <= levelLimitsMPEG4.mPixelsPerSec
                    && width <= levelLimitsMPEG4.mWidth && height <= levelLimitsMPEG4.mHeight
                    && frameRate <= levelLimitsMPEG4.mFrameRate
                    && bitrate <= levelLimitsMPEG4.mBitrate) {
                return levelLimitsMPEG4.mLevel;
            }
        }
        // if none of the levels suffice, select the highest level
        return MPEG4Level6;
    }

    private static int getMinLevelAV1(int width, int height, int frameRate, int bitrate) {
        class LevelLimitAV1 {
            private LevelLimitAV1(int level, int size, int width, int height, long pixelsPerSec,
                    int bitrate) {
                this.mLevel = level;
                this.mSize = size;
                this.mWidth = width;
                this.mHeight = height;
                this.mPixelsPerSec = pixelsPerSec;
                this.mBitrate = bitrate;
            }

            private final int mLevel;
            private final int mSize;
            private final int mWidth;
            private final int mHeight;
            private final long mPixelsPerSec;
            private final int mBitrate;
        }
        // taking bitrate from main profile, will also be supported by high profile
        LevelLimitAV1[] limitsAV1 = {
                new LevelLimitAV1(AV1Level2, 147456, 2048, 1152, 4423680, 1500000),
                new LevelLimitAV1(AV1Level21, 278784, 2816, 1584, 8363520, 3000000),
                new LevelLimitAV1(AV1Level3, 665856, 4352, 2448, 19975680, 6000000),
                new LevelLimitAV1(AV1Level31, 1065024, 5504, 3096, 31950720, 10000000),
                new LevelLimitAV1(AV1Level4, 2359296, 6144, 3456, 70778880, 30000000),
                new LevelLimitAV1(AV1Level41, 2359296, 6144, 3456, 141557760, 50000000),
                new LevelLimitAV1(AV1Level5, 8912896, 8192, 4352, 267386880, 100000000),
                new LevelLimitAV1(AV1Level51, 8912896, 8192, 4352, 534773760, 160000000),
                new LevelLimitAV1(AV1Level52, 8912896, 8192, 4352, 1069547520, 240000000),
                new LevelLimitAV1(AV1Level53, 8912896, 8192, 4352, 1069547520, 240000000),
                new LevelLimitAV1(AV1Level6, 35651584, 16384, 8704, 1069547520, 240000000),
                new LevelLimitAV1(AV1Level61, 35651584, 16384, 8704, 2139095040, 480000000),
                new LevelLimitAV1(AV1Level62, 35651584, 16384, 8704, 4278190080L, 800000000),
                new LevelLimitAV1(AV1Level63, 35651584, 16384, 8704, 4278190080L, 800000000),
        };
        int size = width * height;
        long pixelsPerSec = (long) size * frameRate;
        for (LevelLimitAV1 levelLimitsAV1 : limitsAV1) {
            if (size <= levelLimitsAV1.mSize && width <= levelLimitsAV1.mWidth
                    && height <= levelLimitsAV1.mHeight
                    && pixelsPerSec <= levelLimitsAV1.mPixelsPerSec
                    && bitrate <= levelLimitsAV1.mBitrate) {
                return levelLimitsAV1.mLevel;
            }
        }
        // if none of the levels suffice or high profile, select the highest level
        return AV1Level63;
    }

    protected BitStreamUtils.ParserBase mParser;
    protected Pair<Integer, Integer> mProfileLevel;
    protected boolean mGotCsd;

    public EncoderProfileLevelTestBase(String encoder, String mediaType,
            EncoderConfigParams[] encCfgParams, String allTestParams) {
        super(encoder, mediaType, encCfgParams, allTestParams);
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mParser = BitStreamUtils.getParserObject(mMediaType);
        mProfileLevel = null;
        mGotCsd = false;
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && mProfileLevel == null) {
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
                mProfileLevel = BitStreamUtils.getProfileLevelFromBitStream(buf, info, mParser);
                mGotCsd = true;
            } else {
                if ((mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_VP9) || mMediaType.equals(
                        MediaFormat.MIMETYPE_VIDEO_H263)) && mOutputCount == 0) {
                    ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
                    mProfileLevel = BitStreamUtils.getProfileLevelFromBitStream(buf, info, mParser);
                }
            }
        }
        super.dequeueOutput(bufferIndex, info);
    }

    private int getProfile(MediaFormat format, String msg) {
        // Query output profile. KEY_PROFILE gets precedence over KEY_AAC_PROFILE
        int aacProfile = format.getInteger(MediaFormat.KEY_AAC_PROFILE, -1);
        int profile = format.getInteger(MediaFormat.KEY_PROFILE, aacProfile);
        if (profile != -1) {
            return profile;
        } else {
            fail(msg + "profile key not present in format " + format + mTestConfig + mTestEnv);
        }
        return -1;
    }

    private int getLevel(MediaFormat format, String msg) {
        assertTrue(msg + "level not present in format " + format + mTestConfig + mTestEnv,
                format.containsKey(MediaFormat.KEY_LEVEL));
        return format.getInteger(MediaFormat.KEY_LEVEL);
    }

    protected void validateProfile(int exp, int got, String msg) {
        if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            if (exp == AVCProfileBaseline || exp == AVCProfileConstrainedBaseline) {
                assertTrue(String.format(msg + "Profile information mismatch, Expected %d, Got %d ",
                                exp, got) + mTestConfig + mTestEnv,
                        (got == AVCProfileBaseline || got == AVCProfileConstrainedBaseline));
                return;
            } else if (exp == AVCProfileHigh || exp == AVCProfileConstrainedHigh) {
                assertTrue(String.format(msg + "Profile information mismatch, Expected %d, Got %d ",
                                exp, got) + mTestConfig + mTestEnv,
                        (got == AVCProfileHigh || got == AVCProfileConstrainedHigh));
                return;
            }
        }
        assertEquals(String.format(msg + "Profile information mismatch, Expected %d, Got %d ",
                exp, got) + mTestConfig + mTestEnv, exp, got);
    }

    protected void validateLevel(int exp, int got, String msg) {
        assertEquals(String.format(msg + "Level information mismatch, Expected %d, Got %d ",
                exp, got) + mTestConfig + mTestEnv, exp, got);
    }

    protected void validateMinLevel(int min, int got, String msg) {
        String log = String.format(msg + "Level information unexpected, Expected at least %d,"
                + " Got %d ", min, got) + mTestConfig + mTestEnv;
        // H263 level 45 is out of order.
        if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_H263) && min == H263Level45) {
            // If we are expecting a min level45, then any level other than the ones below
            // level45 (level10) should be ok
            assertNotEquals(log, H263Level10, got);
        } else if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_H263) && got == H263Level45) {
            // If we got level45, then min level must be level10 or level45
            assertEquals(log, H263Level10, min);
        } else if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4)
                && mActiveEncCfg.mProfile == MPEG4ProfileAdvancedSimple && min == MPEG4Level1) {
            // In mpeg4, for advanced simple profile, level0 and level1 have same limits
            assertTrue(log, got == MPEG4Level0 || min <= got);
        } else {
            assertTrue(log, min <= got);
        }
    }

    protected void validateBitStreamForProfileAndLevel(int cfgProfile, int cfgLevel) {
        if (mProfileLevel != null) {
            validateProfile(cfgProfile, mProfileLevel.first, "Validating profile of bitstream : ");
            if (mProfileLevel.second != -1) {
                validateMinLevel(cfgLevel, mProfileLevel.second,
                        "Validating level of bitstream : ");
            }
        }
    }

    protected void validateFormatForProfileAndLevelWRTBitstream(MediaFormat format, String msg) {
        if (mProfileLevel != null) {
            validateProfile(mProfileLevel.first, getProfile(format, msg), msg);
            if (mProfileLevel.second != -1) {
                validateLevel(mProfileLevel.second, getLevel(format, msg), msg);
            }
        }
    }

    protected void validateFormatForProfileAndLevelWRTCfg(MediaFormat format, String msg) {
        validateProfile(mActiveEncCfg.mProfile, getProfile(format, msg), msg);
        if (mActiveEncCfg.mLevel != -1) {
            validateMinLevel(mActiveEncCfg.mLevel, getLevel(format, msg), msg);
        }
    }

    protected void validateFormatForProfileAndLevel(MediaFormat format, String msg) {
        validateFormatForProfileAndLevelWRTBitstream(format, msg + " wrt to bitstream : ");
        validateFormatForProfileAndLevelWRTCfg(format, msg + " wrt to cfg : ");
    }

    protected MediaFormat validateProfileAndLevel() throws IOException {
        // check if bitstream is in accordance with configured profile and level info.
        if (mProfileLevel != null) {
            validateBitStreamForProfileAndLevel(mActiveEncCfg.mProfile, mActiveEncCfg.mLevel);
        }

        // check if output format is in accordance with configured profile and level info.
        if (mCodecName.toUpperCase().startsWith("OMX")) {
            Log.i(LOG_TAG, "omx components don't present prof/level in outputformat");
        } else {
            validateFormatForProfileAndLevel(mOutFormat, "Testing output format : ");
        }

        // check if extracted output profile and level information are in accordance with
        // configured profile and level info
        if (mMuxOutput && !mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_H263)) {
            // Explicit signaling of header information such as profile, level etc is not
            // directly available as HLS in H263 header. That information is conveyed through
            // external means of RTP header
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(mMuxedOutputFile);
            assertEquals("Should be only 1 track \n" + mTestConfig + mTestEnv, 1,
                    extractor.getTrackCount());
            MediaFormat trackFormat = extractor.getTrackFormat(0);
            extractor.release();
            if (mGotCsd || (trackFormat.containsKey(MediaFormat.KEY_PROFILE)
                    || trackFormat.containsKey(MediaFormat.KEY_LEVEL))) {
                validateFormatForProfileAndLevel(trackFormat, "Testing extractor format :- ");
            }
            return trackFormat;
        }
        return null;
    }
}
