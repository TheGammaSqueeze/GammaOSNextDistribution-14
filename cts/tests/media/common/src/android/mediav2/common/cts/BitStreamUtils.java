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

import static android.media.MediaCodecInfo.CodecProfileLevel.*;
import static android.media.MediaFormat.PICTURE_TYPE_B;
import static android.media.MediaFormat.PICTURE_TYPE_I;
import static android.media.MediaFormat.PICTURE_TYPE_P;
import static android.media.MediaFormat.PICTURE_TYPE_UNKNOWN;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Pair;

import org.junit.Assert;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class contains utility functions that parse compressed bitstream and returns metadata
 * necessary for validation. This is by no means a thorough parser that is capable of parsing all
 * syntax elements of a bitstream. This is designed to handle only the requirements of mediav2
 * test suite.
 * <p>
 * Currently this class hosts utils that can,
 * <ul>
 *     <li>Return frame type of the access units of avc, hevc, av1.</li>
 *     <li>Return profile/level information of avc, hevc, av1, vp9, mpeg4, h263, aac</li>
 * </ul>
 */
public class BitStreamUtils {
    public static int getHashMapVal(HashMap<Integer, Integer> obj, int key) {
        Integer val = obj.get(key);
        return val == null ? -1 : val;
    }

    static class ParsableBitArray {
        protected final byte[] mData;
        protected final int mOffset;
        protected final int mLimit;
        protected int mCurrByteOffset;
        protected int mCurrBitOffset;

        ParsableBitArray(byte[] data, int offset, int limit) {
            mData = data;
            mOffset = offset;
            mLimit = limit;
            mCurrByteOffset = offset;
            mCurrBitOffset = 0;
        }

        public boolean readBit() {
            if (mCurrByteOffset >= mLimit) {
                throw new ArrayIndexOutOfBoundsException(
                        String.format("Accessing bytes at offset %d, buffer limit %d",
                                mCurrByteOffset, mLimit));
            }
            boolean returnValue = (mData[mCurrByteOffset] & (0x80 >> mCurrBitOffset)) != 0;
            if (++mCurrBitOffset == 8) {
                mCurrBitOffset = 0;
                mCurrByteOffset++;
            }
            return returnValue;
        }

        public int readBits(int numBits) {
            if (numBits > 32) {
                throw new IllegalArgumentException("readBits Exception: maximum storage space of "
                        + "return value of readBits : 32, less than bits to read : " + numBits);
            }
            int value = 0;
            for (int i = 0; i < numBits; i++) {
                value <<= 1;
                value |= (readBit() ? 1 : 0);
            }
            return value;
        }

        public long readBitsLong(int numBits) {
            if (numBits > 64) {
                throw new IllegalArgumentException("readBitsLong Exception: maximum storage space "
                        + "of return value of readBits : 64, less than bits to read : " + numBits);
            }
            long value = 0;
            for (int i = 0; i < numBits; i++) {
                value <<= 1;
                value |= (readBit() ? 1 : 0);
            }
            return value;
        }
    }

    static class NalParsableBitArray extends ParsableBitArray {
        NalParsableBitArray(byte[] data, int offset, int limit) {
            super(data, offset, limit);
        }

        @Override
        public boolean readBit() {
            // emulation prevention
            if (mCurrBitOffset == 0 && (mCurrByteOffset - 2 > mOffset)
                    && mData[mCurrByteOffset] == (byte) 0x03
                    && mData[mCurrByteOffset - 1] == (byte) 0x00
                    && mData[mCurrByteOffset - 2] == (byte) 0x00) {
                mCurrByteOffset++;
            }
            return super.readBit();
        }

        public int readUEV() {
            int leadingZeros = 0;
            while (!readBit()) {
                leadingZeros++;
            }
            return (1 << leadingZeros) - 1 + (leadingZeros > 0 ? readBits(leadingZeros) : 0);
        }
    }

    static class ObuParsableBitArray extends ParsableBitArray {
        ObuParsableBitArray(byte[] data, int offset, int limit) {
            super(data, offset, limit);
        }

        public long uvlc() {
            int leadingZeros = 0;
            while (true) {
                boolean done = readBit();
                if (done) {
                    break;
                }
                leadingZeros++;
            }
            if (leadingZeros >= 32) {
                return (1L << 32) - 1;
            }
            int value = readBits(leadingZeros);
            return value + (1L << leadingZeros) - 1;
        }

        public int[] leb128() {
            int value = 0, bytesRead = 0;
            for (int count = 0; count < 8; count++) {
                int leb128_byte = readBits(8);
                value |= (leb128_byte & 0x7f) << (count * 7);
                bytesRead++;
                if ((leb128_byte & 0x80) == 0) break;
            }
            return new int[]{bytesRead, value};
        }
    }

    public abstract static class ParserBase {
        protected byte[] mData;
        protected int mOffset;
        protected int mLimit;

        protected void set(byte[] data, int offset, int limit) {
            mData = data;
            mOffset = offset;
            mLimit = limit;
        }

        public abstract int getFrameType();

        public abstract Pair<Integer, Integer> getProfileLevel(boolean isCsd);

        // .first = profile, .second = level
        public Pair<Integer, Integer> plToPair(int profile, int level) {
            return Pair.create(profile, level);
        }
    }

    static class Mpeg4Parser extends ParserBase {
        @Override
        public int getFrameType() {
            return PICTURE_TYPE_UNKNOWN;
        }

        @Override
        public Pair<Integer, Integer> getProfileLevel(@SuppressWarnings("unused") boolean isCsd) {
            ParsableBitArray bitArray = new ParsableBitArray(mData, mOffset, mLimit);
            Assert.assertEquals(0, bitArray.readBits(8));
            Assert.assertEquals(0, bitArray.readBits(8));
            Assert.assertEquals(1, bitArray.readBits(8));
            Assert.assertEquals(0xb0, bitArray.readBits(8));
            int profileLevel = bitArray.readBits(8);
            switch (profileLevel) {
                case 0x08: return plToPair(MPEG4ProfileSimple, MPEG4Level0);
                case 0x01: return plToPair(MPEG4ProfileSimple, MPEG4Level1);
                case 0x02: return plToPair(MPEG4ProfileSimple, MPEG4Level2);
                case 0x03: return plToPair(MPEG4ProfileSimple, MPEG4Level3);
                case 0xf0: return plToPair(MPEG4ProfileAdvancedSimple, MPEG4Level0);
                case 0xf1: return plToPair(MPEG4ProfileAdvancedSimple, MPEG4Level1);
                case 0xf2: return plToPair(MPEG4ProfileAdvancedSimple, MPEG4Level2);
                case 0xf3: return plToPair(MPEG4ProfileAdvancedSimple, MPEG4Level3);
                case 0xf7: return plToPair(MPEG4ProfileAdvancedSimple, MPEG4Level3b);
                case 0xf4: return plToPair(MPEG4ProfileAdvancedSimple, MPEG4Level4);
                case 0xf5: return plToPair(MPEG4ProfileAdvancedSimple, MPEG4Level5);
                default: return null;
            }
        }
    }

    static class H263Parser extends ParserBase {
        @Override
        public int getFrameType() {
            return PICTURE_TYPE_UNKNOWN;
        }

        @Override
        public Pair<Integer, Integer> getProfileLevel(@SuppressWarnings("unused") boolean isCsd) {
            ParsableBitArray bitArray = new ParsableBitArray(mData, mOffset, mLimit);
            Assert.assertEquals("bad psc", 0x20, bitArray.readBits(22));
            bitArray.readBits(8); // tr
            Assert.assertEquals(1, bitArray.readBits(1));
            Assert.assertEquals(0, bitArray.readBits(1));
            bitArray.readBits(1);  // split screen
            bitArray.readBits(1);  // camera indicator
            bitArray.readBits(1);  // freeze indicator
            int sourceFormat = bitArray.readBits(3);
            int picType;
            int umv = 0, sac = 0, ap = 0, pb = 0;
            int aic = 0, df = 0, ss = 0, rps = 0, isd = 0, aiv = 0, mq = 0;
            int rpr = 0, rru = 0;
            if (sourceFormat == 7) {
                int ufep = bitArray.readBits(3);
                if (ufep == 1) {
                    sourceFormat = bitArray.readBits(3);
                    bitArray.readBits(1); // custom pcf
                    umv = bitArray.readBits(1);
                    sac = bitArray.readBits(1);
                    ap = bitArray.readBits(1);
                    aic = bitArray.readBits(1);
                    df = bitArray.readBits(1);
                    ss = bitArray.readBits(1);
                    rps = bitArray.readBits(1);
                    isd = bitArray.readBits(1);
                    aiv = bitArray.readBits(1);
                    mq = bitArray.readBits(1);
                    Assert.assertEquals(1, bitArray.readBits(1));
                    Assert.assertEquals(0, bitArray.readBits(3));
                }
                picType = bitArray.readBits(3);
                rpr = bitArray.readBits(1);
                rru = bitArray.readBits(1);
                bitArray.readBits(1);  // rtype
                Assert.assertEquals(0, bitArray.readBits(1));  // reserved
                Assert.assertEquals(0, bitArray.readBits(1));  // reserved
                Assert.assertEquals(1, bitArray.readBits(1));  // start code emulation
            } else {
                picType = bitArray.readBits(1);
                umv = bitArray.readBits(1);
                sac = bitArray.readBits(1);
                ap = bitArray.readBits(1);
                pb = bitArray.readBits(1);
            }
            int profile = H263ProfileBaseline;
            if (ap == 1) profile = H263ProfileBackwardCompatible;
            if (aic == 1 && df == 1 && ss == 1 && mq == 1) profile = H263ProfileISWV2;
            return plToPair(profile, -1);
        }
    }

    static class AvcParser extends ParserBase {
        private static final int NO_NAL_UNIT_FOUND = -1;
        private static final HashMap<Integer, Integer> LEVEL_MAP = new HashMap<>() {
            {
                put(10, AVCLevel1);
                put(11, AVCLevel11);
                put(12, AVCLevel12);
                put(13, AVCLevel13);
                put(20, AVCLevel2);
                put(21, AVCLevel21);
                put(22, AVCLevel22);
                put(30, AVCLevel3);
                put(31, AVCLevel31);
                put(32, AVCLevel32);
                put(40, AVCLevel4);
                put(41, AVCLevel41);
                put(42, AVCLevel42);
                put(50, AVCLevel5);
                put(51, AVCLevel51);
                put(52, AVCLevel52);
                put(60, AVCLevel6);
                put(61, AVCLevel61);
                put(62, AVCLevel62);
            }
        };

        private int getNalUnitStartOffset(byte[] dataArray, int start, int limit) {
            for (int pos = start; pos + 3 < limit; pos++) {
                if ((pos + 3) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 1) {
                    return pos + 3;
                } else if ((pos + 4) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 0 && dataArray[pos + 3] == 1) {
                    return pos + 4;
                }
            }
            return NO_NAL_UNIT_FOUND;
        }

        private static int getNalUnitType(byte[] dataArray, int nalUnitOffset) {
            return dataArray[nalUnitOffset] & 0x1F;
        }

        @Override
        public int getFrameType() {
            for (int pos = mOffset; pos < mLimit; ) {
                int offset = getNalUnitStartOffset(mData, pos, mLimit);
                if (offset == NO_NAL_UNIT_FOUND) return PICTURE_TYPE_UNKNOWN;
                int nalUnitType = getNalUnitType(mData, offset);
                if (nalUnitType == 1 || nalUnitType == 2 || nalUnitType == 5) {  // coded slice
                    NalParsableBitArray bitArray = new NalParsableBitArray(mData, offset, mLimit);
                    Assert.assertEquals(0, bitArray.readBits(1)); // forbidden zero bit
                    bitArray.readBits(7); // nal_ref_idc + nal_unit_type
                    bitArray.readUEV(); // first_mb_in_slice
                    int sliceType = bitArray.readUEV();
                    if (sliceType % 5 == 0) {
                        return PICTURE_TYPE_P;
                    } else if (sliceType % 5 == 1) {
                        return PICTURE_TYPE_B;
                    } else if (sliceType % 5 == 2) {
                        return PICTURE_TYPE_I;
                    } else {
                        return PICTURE_TYPE_UNKNOWN;
                    }
                }
                pos = offset;
            }
            return PICTURE_TYPE_UNKNOWN;
        }

        @Override
        public Pair<Integer, Integer> getProfileLevel(@SuppressWarnings("unused") boolean isCsd) {
            for (int pos = mOffset; pos < mLimit; ) {
                int offset = getNalUnitStartOffset(mData, pos, mLimit);
                if (offset == NO_NAL_UNIT_FOUND) return null;
                if (getNalUnitType(mData, offset) == 7) { // seq_parameter_set_rbsp
                    NalParsableBitArray bitArray = new NalParsableBitArray(mData, offset, mLimit);
                    Assert.assertEquals(0, bitArray.readBits(1)); // forbidden zero bit
                    bitArray.readBits(7); // nal_ref_idc + nal_unit_type
                    int profileIdc = bitArray.readBits(8);
                    int constraintSet0Flag = bitArray.readBits(1);
                    int constraintSet1Flag = bitArray.readBits(1);
                    int constraintSet2Flag = bitArray.readBits(1);
                    int constraintSet3Flag = bitArray.readBits(1);
                    int constraintSet4Flag = bitArray.readBits(1);
                    int constraintSet5Flag = bitArray.readBits(1);
                    Assert.assertEquals(0, bitArray.readBits(2)); // reserved zero 2 bits
                    int levelIdc = bitArray.readBits(8);

                    int profile = -1;
                    if (constraintSet0Flag == 1 || profileIdc == 66) {
                        profile = constraintSet1Flag == 1 ? AVCProfileConstrainedBaseline :
                                AVCProfileBaseline;
                    } else if (constraintSet1Flag == 1 || profileIdc == 77) {
                        profile = AVCProfileMain;
                    } else if (constraintSet2Flag == 1 || profileIdc == 88) {
                        profile = AVCProfileExtended;
                    } else if (profileIdc == 100) {
                        profile = (constraintSet4Flag == 1 && constraintSet5Flag == 1)
                                ? AVCProfileConstrainedHigh : AVCProfileHigh;
                    } else if (profileIdc == 110) {
                        profile = AVCProfileHigh10;
                    } else if (profileIdc == 122) {
                        profile = AVCProfileHigh422;
                    } else if (profileIdc == 244) {
                        profile = AVCProfileHigh444;
                    }

                    // In bitstreams conforming to the Baseline, Constrained Baseline, Main, or
                    // Extended profiles :
                    // - If level_idc is equal to 11 and constraint_set3_flag is equal to 1, the
                    // indicated level is level 1b.
                    // - Otherwise (level_idc is not equal to 11 or constraint_set3_flag is not
                    // equal to 1), level_idc is equal to a value of ten times the level number
                    // (of the indicated level) specified in Table A-1.
                    int level;
                    if ((levelIdc == 11) && (profile == AVCProfileBaseline
                            || profile == AVCProfileConstrainedBaseline || profile == AVCProfileMain
                            || profile == AVCProfileExtended)) {
                        level = constraintSet3Flag == 1 ? AVCLevel1b : AVCLevel11;
                    } else if ((levelIdc == 9) && (profile == AVCProfileHigh
                            || profile == AVCProfileHigh10 || profile == AVCProfileHigh422
                            || profile == AVCProfileHigh444)) {
                        // In bitstreams conforming to the High, High 10, High 4:2:2, High 4:4:4
                        // Predictive, High 10 Intra, High 4:2:2 Intra, High 4:4:4 Intra, or
                        // CAVLC 4:4:4 Intra profiles,
                        // - If level_idc is equal to 9, the indicated level is level 1b.
                        // - Otherwise (level_idc is not equal to 9), level_idc is equal to a
                        // value of ten times the level number (of the indicated level) specified
                        // in Table A-1
                        level = AVCLevel1b;
                    } else {
                        level = getHashMapVal(LEVEL_MAP, levelIdc);
                    }
                    return plToPair(profile, level);
                }
                pos = offset;
            }
            return null;
        }
    }

    static class HevcParser extends ParserBase {
        private static final int NO_NAL_UNIT_FOUND = -1;
        private static final int TRAIL_N = 0;
        private static final int RASL_R = 9;
        private static final int BLA_W_LP = 16;
        private static final int RSV_IRAP_VCL23 = 23;
        private static final HashMap<Integer, Integer> LEVEL_MAP_MAIN_TIER = new HashMap<>() {
            {
                put(30, HEVCMainTierLevel1);
                put(60, HEVCMainTierLevel2);
                put(63, HEVCMainTierLevel21);
                put(90, HEVCMainTierLevel3);
                put(93, HEVCMainTierLevel31);
                put(120, HEVCMainTierLevel4);
                put(123, HEVCMainTierLevel41);
                put(150, HEVCMainTierLevel5);
                put(153, HEVCMainTierLevel51);
                put(156, HEVCMainTierLevel52);
                put(180, HEVCMainTierLevel6);
                put(183, HEVCMainTierLevel61);
                put(186, HEVCMainTierLevel62);
            }
        };
        private static final HashMap<Integer, Integer> LEVEL_MAP_HIGH_TIER = new HashMap<>() {
            {
                put(120, HEVCHighTierLevel4);
                put(123, HEVCHighTierLevel41);
                put(150, HEVCHighTierLevel5);
                put(153, HEVCHighTierLevel51);
                put(156, HEVCHighTierLevel52);
                put(180, HEVCHighTierLevel6);
                put(183, HEVCHighTierLevel61);
                put(186, HEVCHighTierLevel62);
            }
        };

        private int getNalUnitStartOffset(byte[] dataArray, int start, int limit) {
            for (int pos = start; pos + 3 < limit; pos++) {
                if ((pos + 3) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 1) {
                    return pos + 3;
                } else if ((pos + 4) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 0 && dataArray[pos + 3] == 1) {
                    return pos + 4;
                }
            }
            return NO_NAL_UNIT_FOUND;
        }

        private static int getNalUnitType(byte[] dataArray, int nalUnitOffset) {
            return (dataArray[nalUnitOffset] & 0x7E) >> 1;
        }

        @Override
        public int getFrameType() {
            for (int pos = mOffset; pos < mLimit; ) {
                int offset = getNalUnitStartOffset(mData, pos, mLimit);
                if (offset == NO_NAL_UNIT_FOUND) return PICTURE_TYPE_UNKNOWN;
                int nalUnitType = getNalUnitType(mData, offset);
                if ((nalUnitType >= TRAIL_N && nalUnitType <= RASL_R) || (nalUnitType >= BLA_W_LP
                        && nalUnitType <= RSV_IRAP_VCL23)) { // codec slice
                    NalParsableBitArray bitArray = new NalParsableBitArray(mData, offset, mLimit);
                    bitArray.readBits(16); // nal_unit_header

                    // Parsing slice_segment_header values from H.265/HEVC Table 7.3.6.1
                    boolean firstSliceSegmentInPicFlag = bitArray.readBit();
                    if (!firstSliceSegmentInPicFlag) return PICTURE_TYPE_UNKNOWN;
                    if (nalUnitType >= BLA_W_LP && nalUnitType <= RSV_IRAP_VCL23) {
                        bitArray.readBit();  // no_output_of_prior_pics_flag
                    }
                    bitArray.readUEV(); // slice_pic_parameter_set_id
                    // FIXME: Assumes num_extra_slice_header_bits element of PPS data to be 0
                    int sliceType = bitArray.readUEV();
                    if (sliceType == 0) {
                        return PICTURE_TYPE_B;
                    } else if (sliceType == 1) {
                        return PICTURE_TYPE_P;
                    } else if (sliceType == 2) {
                        return PICTURE_TYPE_I;
                    } else {
                        return PICTURE_TYPE_UNKNOWN;
                    }
                }
                pos = offset;
            }
            return PICTURE_TYPE_UNKNOWN;
        }

        @Override
        public Pair<Integer, Integer> getProfileLevel(@SuppressWarnings("unused") boolean isCsd) {
            for (int pos = mOffset; pos < mLimit; ) {
                int offset = getNalUnitStartOffset(mData, pos, mLimit);
                if (offset == NO_NAL_UNIT_FOUND) return null;
                if (getNalUnitType(mData, offset) == 33) { // sps_nut
                    NalParsableBitArray bitArray = new NalParsableBitArray(mData, offset, mLimit);
                    bitArray.readBits(16); // nal unit header
                    bitArray.readBits(4); // sps video parameter set id
                    bitArray.readBits(3); // sps_max_sub_layers_minus1
                    bitArray.readBits(1); // sps temporal id nesting flag
                    // profile_tier_level
                    bitArray.readBits(2); // generalProfileSpace
                    int generalTierFlag = bitArray.readBits(1);
                    int generalProfileIdc = bitArray.readBits(5);
                    int[] generalProfileCompatibility = new int[32];
                    for (int j = 0; j < generalProfileCompatibility.length; j++) {
                        generalProfileCompatibility[j] = bitArray.readBits(1);
                    }
                    bitArray.readBits(1); // general progressive source flag
                    bitArray.readBits(1); // general interlaced source flag
                    bitArray.readBits(1); // general non packed constraint flag
                    bitArray.readBits(1); // general frame only constraint flag

                    // interpretation of next 44 bits is dependent on generalProfileIdc and
                    // generalProfileCompatibility; but we do not use them in this validation
                    // process, so we're skipping over them.
                    bitArray.readBitsLong(44);
                    int generalLevelIdc = bitArray.readBits(8);

                    int profile = -1;
                    if (generalProfileIdc == 1 || generalProfileCompatibility[1] == 1) {
                        profile = HEVCProfileMain;
                    } else if (generalProfileIdc == 2 || generalProfileCompatibility[2] == 1) {
                        profile = HEVCProfileMain10;
                    } else if (generalProfileIdc == 3 || generalProfileCompatibility[3] == 1) {
                        profile = HEVCProfileMainStill;
                    }

                    return plToPair(profile, getHashMapVal(
                            generalTierFlag == 0 ? LEVEL_MAP_MAIN_TIER : LEVEL_MAP_HIGH_TIER,
                            generalLevelIdc));
                }
                pos = offset;
            }
            return null;
        }
    }

    static class Vp9Parser extends ParserBase {
        private static final HashMap<Integer, Integer> PROFILE_MAP = new HashMap<>() {
            {
                put(0, VP9Profile0);
                put(1, VP9Profile1);
                put(2, VP9Profile2);
                put(3, VP9Profile3);
            }
        };
        private static final HashMap<Integer, Integer> LEVEL_MAP = new HashMap<>() {
            {
                put(10, VP9Level1);
                put(11, VP9Level11);
                put(20, VP9Level2);
                put(21, VP9Level21);
                put(30, VP9Level3);
                put(31, VP9Level31);
                put(40, VP9Level4);
                put(41, VP9Level41);
                put(50, VP9Level5);
                put(51, VP9Level51);
                put(60, VP9Level6);
                put(61, VP9Level61);
                put(62, VP9Level62);
            }
        };

        private Pair<Integer, Integer> getProfileLevelFromCSD() { // parse vp9 codecprivate
            int profile = -1, level = -1;
            for (int pos = mOffset; pos < mLimit; ) {
                ParsableBitArray bitArray = new ParsableBitArray(mData, pos + mOffset, mLimit);
                int id = bitArray.readBits(8);
                int len = bitArray.readBits(8);
                pos += 2;
                int val = bitArray.readBits(len * 8);
                pos += len;
                if (id == 1 || id == 2) {
                    Assert.assertEquals(1, len);
                    if (id == 1) profile = val;
                    else level = val;
                }
                if (profile != -1 && level != -1) break;
            }
            return plToPair(getHashMapVal(PROFILE_MAP, profile), getHashMapVal(LEVEL_MAP, level));
        }

        private Pair<Integer, Integer> getProfileFromFrameHeader() { // parse uncompressed header
            ParsableBitArray bitArray = new ParsableBitArray(mData, mOffset, mLimit);
            bitArray.readBits(2); // frame marker
            int profileLBit = bitArray.readBits(1);
            int profileHBit = bitArray.readBits(1);
            int profile = profileHBit << 1 + profileLBit;
            return plToPair(getHashMapVal(PROFILE_MAP, profile), -1);
        }

        @Override
        public int getFrameType() {
            return PICTURE_TYPE_UNKNOWN;
        }

        @Override
        public Pair<Integer, Integer> getProfileLevel(boolean isCsd) {
            return isCsd ? getProfileLevelFromCSD() : getProfileFromFrameHeader();
        }
    }

    static class Av1Parser extends ParserBase {
        private static final int OBU_SEQUENCE_HEADER = 1;
        private static final int OBU_FRAME_HEADER = 3;
        private static final int OBU_FRAME = 6;
        private static final int OBP_KEY_FRAME = 0;
        private static final int OBP_INTER_FRAME = 1;
        private static final int OBP_INTRA_ONLY_FRAME = 2;
        private static final int OBP_SWITCH_FRAME = 3;
        private static final int NUM_REF_FRAMES = 8;

        static class ObuInfo {
            private int mObuType;
            private int mTemporalId;
            private int mSpatialId;
            private int mHeaderSize;
            private int mSizeFieldSize;
            private int mDataSize;

            int getTotalObuSize() {
                return mHeaderSize + mSizeFieldSize + mDataSize;
            }

            int getObuDataOffset() {
                return mHeaderSize + mSizeFieldSize;
            }
        }

        static class SeqHeaderObu {
            public int seqProfile;
            public int reducedStillPictureHeader;
            public final int[] seqLevelIdx = new int[32];
            public int timingInfoPresentFlag;
            public int equalPictureInterval;
            public int decoderModelInfoPresentFlag;
            public int bufferDelayLengthMinus1;
            public int bufferRemovalTimeLengthMinus1;
            public int framePresentationTimeLengthMinus1;
            public int initialDisplayDelayPresentFlag;
            public int operatingPointsCntMinus1;
            public final int[] operatingPointIdc = new int[32];
            public final int[] seqTier = new int[32];
            public final int[] decoderModelPresentForThisOp = new int[32];
            public int frameIdNumbersPresentFlag;
            public int deltaFrameIdLengthMinus2;
            public int additionalFrameIdLengthMinus1;
            public int seqForceScreenContentTools;
            public int seqForceIntegerMv;
            public int orderHintBits;
            public int enableHighBitDepth;
        }

        static class FrameHeaderObu {
            public int frameType;
            public int showFrame;
            public int showExistingFrame;
            public int errorResilientMode;
        }

        private final SeqHeaderObu mSeqHeader = new SeqHeaderObu();
        private final int[] mRefFrameType = new int[NUM_REF_FRAMES];

        // obu header size, obu size field, obu size
        private ObuInfo parseObuHeader(byte[] dataArray, int pos, int limit) {
            int obuHeaderSize = 1;
            ObuInfo obuDetails = new ObuInfo();
            ObuParsableBitArray bitArray = new ObuParsableBitArray(dataArray, pos, limit);
            bitArray.readBits(1);  // forbidden bit
            obuDetails.mObuType = bitArray.readBits(4); // obu type
            int extensionFlag = bitArray.readBits(1);
            int hasSizeField = bitArray.readBits(1);
            Assert.assertEquals(1, hasSizeField);
            bitArray.readBits(1); // reserved 1bit
            if (extensionFlag == 1) {
                obuDetails.mTemporalId = bitArray.readBits(3);
                obuDetails.mSpatialId = bitArray.readBits(2);
                bitArray.readBits(3);
                obuHeaderSize++;
            }
            int[] obuSizeInfo = bitArray.leb128();
            obuDetails.mHeaderSize = obuHeaderSize;
            obuDetails.mSizeFieldSize = obuSizeInfo[0];
            obuDetails.mDataSize = obuSizeInfo[1];
            return obuDetails;
        }

        private void parseSequenceHeader(ObuParsableBitArray bitArray) {
            mSeqHeader.seqProfile = bitArray.readBits(3);
            bitArray.readBits(1); // still picture
            mSeqHeader.reducedStillPictureHeader = bitArray.readBits(1);
            if (mSeqHeader.reducedStillPictureHeader == 1) {
                mSeqHeader.seqLevelIdx[0] = bitArray.readBits(5);
            } else {
                mSeqHeader.timingInfoPresentFlag = bitArray.readBits(1);
                if (mSeqHeader.timingInfoPresentFlag == 1) {
                    bitArray.readBitsLong(32); // num_units_in_display_tick
                    bitArray.readBitsLong(32); // time_scale
                    mSeqHeader.equalPictureInterval = bitArray.readBits(1);
                    if (mSeqHeader.equalPictureInterval == 1) {
                        bitArray.uvlc(); // num_ticks_per_picture_minus_1
                    }
                    mSeqHeader.decoderModelInfoPresentFlag = bitArray.readBits(1);
                    if (mSeqHeader.decoderModelInfoPresentFlag == 1) {
                        mSeqHeader.bufferDelayLengthMinus1 = bitArray.readBits(5);
                        bitArray.readBitsLong(32); // num_units_in_decoding_tick
                        mSeqHeader.bufferRemovalTimeLengthMinus1 = bitArray.readBits(5);
                        mSeqHeader.framePresentationTimeLengthMinus1 = bitArray.readBits(5);
                    }
                } else {
                    mSeqHeader.decoderModelInfoPresentFlag = 0;
                }
                mSeqHeader.initialDisplayDelayPresentFlag = bitArray.readBits(1);
                mSeqHeader.operatingPointsCntMinus1 = bitArray.readBits(5);
                for (int i = 0; i <= mSeqHeader.operatingPointsCntMinus1; i++) {
                    mSeqHeader.operatingPointIdc[i] = bitArray.readBits(12);
                    mSeqHeader.seqLevelIdx[i] = bitArray.readBits(5);
                    if (mSeqHeader.seqLevelIdx[i] > 7) {
                        mSeqHeader.seqTier[i] = bitArray.readBits(1);
                    }
                    if (mSeqHeader.decoderModelInfoPresentFlag == 1) {
                        mSeqHeader.decoderModelPresentForThisOp[i] = bitArray.readBits(1);
                        if (mSeqHeader.decoderModelPresentForThisOp[i] == 1) {
                            int n = mSeqHeader.bufferDelayLengthMinus1 + 1;
                            bitArray.readBits(n); // decoder_buffer_delay
                            bitArray.readBits(n); // encoder_buffer_delay
                            bitArray.readBits(1); // low_delay_mode_flag
                        }
                    } else {
                        mSeqHeader.decoderModelPresentForThisOp[i] = 0;
                    }
                    if (mSeqHeader.initialDisplayDelayPresentFlag == 1) {
                        if (bitArray.readBits(1) == 1) {
                            bitArray.readBits(4); // initial_display_delay_minus_1
                        }
                    }
                }
            }
            int frameWidthBitsMinus1 = bitArray.readBits(4);
            int frameHeightBitsMinus1 = bitArray.readBits(4);
            bitArray.readBits(frameWidthBitsMinus1 + 1); // max_frame_width_minus_1
            bitArray.readBits(frameHeightBitsMinus1 + 1); // max_frame_height_minus_1
            if (mSeqHeader.reducedStillPictureHeader == 1) {
                mSeqHeader.frameIdNumbersPresentFlag = 0;
            } else {
                mSeqHeader.frameIdNumbersPresentFlag = bitArray.readBits(1);
            }
            if (mSeqHeader.frameIdNumbersPresentFlag == 1) {
                mSeqHeader.deltaFrameIdLengthMinus2 = bitArray.readBits(4);
                mSeqHeader.additionalFrameIdLengthMinus1 = bitArray.readBits(3);
            }
            bitArray.readBits(1); // use_128x128_superblock
            bitArray.readBits(1); // enable_filter_intra
            bitArray.readBits(1); // enable_intra_edge_filter
            if (mSeqHeader.reducedStillPictureHeader == 1) {
                mSeqHeader.seqForceScreenContentTools = 2;
                mSeqHeader.seqForceIntegerMv = 2;
                mSeqHeader.orderHintBits = 0;
            } else {
                bitArray.readBits(1); // enable_interintra_compound
                bitArray.readBits(1); // enable_masked_compound
                bitArray.readBits(1); // enable_warped_motion
                bitArray.readBits(1); // enable_dual_filter
                int enableOrderHint = bitArray.readBits(1);
                if (enableOrderHint == 1) {
                    bitArray.readBits(1); // enable_jnt_comp
                    bitArray.readBits(1); // enable_ref_frame_mvs
                }
                int seqChooseScreenContentTools = bitArray.readBits(1);
                if (seqChooseScreenContentTools == 1) {
                    mSeqHeader.seqForceScreenContentTools = 2;
                } else {
                    mSeqHeader.seqForceScreenContentTools = bitArray.readBits(1);
                }
                if (mSeqHeader.seqForceScreenContentTools > 0) {
                    int seqChooseIntegerMv = bitArray.readBits(1);
                    if (seqChooseIntegerMv == 1) {
                        mSeqHeader.seqForceIntegerMv = 2;
                    } else {
                        mSeqHeader.seqForceIntegerMv = bitArray.readBits(1);
                    }
                } else {
                    mSeqHeader.seqForceIntegerMv = 2;
                }
                if (enableOrderHint == 1) {
                    int orderHintBitsMinus1 = bitArray.readBits(3);
                    mSeqHeader.orderHintBits = orderHintBitsMinus1 + 1;
                } else {
                    mSeqHeader.orderHintBits = 0;
                }
            }
            bitArray.readBits(1); // enable super res
            bitArray.readBits(1); // enable cdef
            bitArray.readBits(1); // enable restoration
            mSeqHeader.enableHighBitDepth = bitArray.readBits(1);
        }

        private FrameHeaderObu parseFrameHeader(ObuParsableBitArray bitArray, int temporalId,
                int spatialId) {
            FrameHeaderObu frameHeader = new FrameHeaderObu();
            int idLen = 0;
            if (mSeqHeader.frameIdNumbersPresentFlag == 1) {
                idLen = mSeqHeader.additionalFrameIdLengthMinus1
                        + mSeqHeader.deltaFrameIdLengthMinus2 + 3;
            }
            int allFrames = (1 << NUM_REF_FRAMES) - 1;
            int refreshFrameFlags = 0;
            int frameIsIntra;
            if (mSeqHeader.reducedStillPictureHeader == 1) {
                frameHeader.frameType = OBP_KEY_FRAME;
                frameIsIntra = 1;
                frameHeader.showFrame = 1;
            } else {
                frameHeader.showExistingFrame = bitArray.readBits(1);
                if (frameHeader.showExistingFrame == 1) {
                    int frameToShowMapIdx = bitArray.readBits(3);
                    if (mSeqHeader.decoderModelInfoPresentFlag == 1
                            && mSeqHeader.equalPictureInterval == 0) {
                        int n = mSeqHeader.framePresentationTimeLengthMinus1 + 1;
                        bitArray.readBits(n); // frame_presentation_time
                    }
                    if (mSeqHeader.frameIdNumbersPresentFlag == 1) {
                        bitArray.readBits(idLen); // display_frame_id
                    }
                    frameHeader.frameType = mRefFrameType[frameToShowMapIdx];
                    if (frameHeader.frameType == OBP_KEY_FRAME) {
                        refreshFrameFlags = allFrames;
                    }
                    return frameHeader;
                }
                frameHeader.frameType = bitArray.readBits(2);
                frameIsIntra = (frameHeader.frameType == OBP_INTRA_ONLY_FRAME
                        || frameHeader.frameType == OBP_KEY_FRAME) ? 1 : 0;
                frameHeader.showFrame = bitArray.readBits(1);
                if (frameHeader.showFrame == 1 && mSeqHeader.decoderModelInfoPresentFlag == 1
                        && mSeqHeader.equalPictureInterval == 0) {
                    int n = mSeqHeader.framePresentationTimeLengthMinus1 + 1;
                    bitArray.readBits(n); // frame_presentation_time
                }
                if (frameHeader.showFrame == 0) {
                    bitArray.readBits(1); // showable_frame
                }
                if (frameHeader.frameType == OBP_SWITCH_FRAME || (
                        frameHeader.frameType == OBP_KEY_FRAME && frameHeader.showFrame == 1)) {
                    frameHeader.errorResilientMode = 1;
                } else {
                    frameHeader.errorResilientMode = bitArray.readBits(1);
                }
            }
            bitArray.readBits(1); // disable_cdf_update
            int allowScreenContentTools;
            if (mSeqHeader.seqForceScreenContentTools == 2) {
                allowScreenContentTools = bitArray.readBits(1);
            } else {
                allowScreenContentTools = mSeqHeader.seqForceScreenContentTools;
            }
            if (allowScreenContentTools == 1 && mSeqHeader.seqForceIntegerMv == 2) {
                bitArray.readBits(1); // force_integer_mv
            }
            if (mSeqHeader.frameIdNumbersPresentFlag == 1) {
                bitArray.readBits(idLen); // current_frame_id
            }
            if (frameHeader.frameType != OBP_SWITCH_FRAME
                    && mSeqHeader.reducedStillPictureHeader == 0) {
                bitArray.readBits(1); // frame_size_override_flag
            }
            bitArray.readBits(mSeqHeader.orderHintBits); // order_hint
            if (frameIsIntra != 1 && frameHeader.errorResilientMode == 0) {
                bitArray.readBits(3); // primary_ref_frame
            }

            if (mSeqHeader.decoderModelInfoPresentFlag == 1) {
                int bufferRemovalTimePresentFlag = bitArray.readBits(1);
                if (bufferRemovalTimePresentFlag == 1) {
                    for (int i = 0; i <= mSeqHeader.operatingPointsCntMinus1; i++) {
                        if (mSeqHeader.decoderModelPresentForThisOp[i] == 1) {
                            int opPtIdc = mSeqHeader.operatingPointIdc[i];
                            boolean inTemporalLayer = ((opPtIdc >> temporalId) & 1) == 1;
                            boolean inSpatialLayer = ((opPtIdc >> (spatialId + 8)) & 1) == 1;
                            if (opPtIdc == 0 || (inTemporalLayer && inSpatialLayer)) {
                                int n = mSeqHeader.bufferRemovalTimeLengthMinus1 + 1;
                                bitArray.readBits(n); // buffer_removal_time
                            }
                        }
                    }
                }
            }

            if (frameHeader.frameType == OBP_SWITCH_FRAME || (frameHeader.frameType == OBP_KEY_FRAME
                    && frameHeader.showFrame == 1)) {
                refreshFrameFlags = allFrames;
            } else {
                refreshFrameFlags = bitArray.readBits(8);
            }

            for (int i = 0; i < 8; i++) {
                if ((refreshFrameFlags >> i & 1) == 1) {
                    mRefFrameType[i] = frameHeader.frameType;
                }
            }
            return frameHeader;
        }

        // parse av1 codec configuration record
        private Pair<Integer, Integer> getProfileLevelFromCSD() {
            int profile = -1;
            ParsableBitArray bitArray = new ParsableBitArray(mData, mOffset, mLimit);
            Assert.assertEquals(1, bitArray.readBits(1));  // marker
            Assert.assertEquals(1, bitArray.readBits(7));  // version
            int seqProfile = bitArray.readBits(3);
            int seqLevelIdx0 = bitArray.readBits(5);
            bitArray.readBits(1);  // seqTier0
            int highBitDepth = bitArray.readBits(1);
            bitArray.readBits(1);  // is input 12 bit;
            if (seqProfile == 0) {
                profile = highBitDepth == 0 ? AV1ProfileMain8 : AV1ProfileMain10;
            }

            int level = AV1Level2 << seqLevelIdx0;
            return plToPair(profile, level);
        }

        // parse av1 sequence header
        private Pair<Integer, Integer> getProfileLevelFromSeqHeader() {
            for (int pos = mOffset; pos < mLimit; ) {
                ObuInfo obuDetails = parseObuHeader(mData, pos, mLimit);
                ObuParsableBitArray bitArray =
                        new ObuParsableBitArray(mData, pos + obuDetails.getObuDataOffset(),
                                pos + obuDetails.getTotalObuSize());
                if (obuDetails.mObuType == OBU_SEQUENCE_HEADER) {
                    int profile = -1;
                    parseSequenceHeader(bitArray);
                    if (mSeqHeader.seqProfile == 0) {
                        profile = mSeqHeader.enableHighBitDepth == 0 ? AV1ProfileMain8 :
                                AV1ProfileMain10;
                    }

                    int level = AV1Level2 << mSeqHeader.seqLevelIdx[0];
                    return plToPair(profile, level);
                }
                pos += obuDetails.getTotalObuSize();
            }
            return null;
        }

        @Override
        public int getFrameType() {
            ArrayList<FrameHeaderObu> headers = new ArrayList<>();
            for (int pos = mOffset; pos < mLimit; ) {
                ObuInfo obuDetails = parseObuHeader(mData, pos, mLimit);
                ObuParsableBitArray bitArray =
                        new ObuParsableBitArray(mData, pos + obuDetails.getObuDataOffset(),
                                pos + obuDetails.getTotalObuSize());
                if (obuDetails.mObuType == OBU_SEQUENCE_HEADER) {
                    parseSequenceHeader(bitArray);
                } else if (obuDetails.mObuType == OBU_FRAME_HEADER
                        || obuDetails.mObuType == OBU_FRAME) {
                    FrameHeaderObu frameHeader = parseFrameHeader(bitArray, obuDetails.mTemporalId,
                            obuDetails.mSpatialId);
                    headers.add(frameHeader);
                }
                pos += obuDetails.getTotalObuSize();
            }
            for (FrameHeaderObu frameHeader : headers) {
                if (frameHeader.showFrame == 1 || frameHeader.showExistingFrame == 1) {
                    if (frameHeader.frameType == OBP_KEY_FRAME
                            || frameHeader.frameType == OBP_INTRA_ONLY_FRAME) {
                        return PICTURE_TYPE_I;
                    } else if (frameHeader.frameType == OBP_INTER_FRAME) {
                        return PICTURE_TYPE_P;
                    }
                    return PICTURE_TYPE_UNKNOWN;
                }
            }
            return PICTURE_TYPE_UNKNOWN;
        }

        @Override
        public Pair<Integer, Integer> getProfileLevel(boolean isCsd) {
            return isCsd ? getProfileLevelFromCSD() : getProfileLevelFromSeqHeader();
        }
    }

    static class AacParser extends ParserBase {
        @Override
        public int getFrameType() {
            return PICTURE_TYPE_UNKNOWN;
        }

        @Override
        public Pair<Integer, Integer> getProfileLevel(@SuppressWarnings("unused") boolean isCsd) {
            // parse AudioSpecificConfig() of ISO 14496 Part 3
            ParsableBitArray bitArray = new ParsableBitArray(mData, mOffset, mLimit);
            int audioObjectType = bitArray.readBits(5);
            if (audioObjectType == 31) {
                audioObjectType = 32 + bitArray.readBits(6); // audio object type ext
            }
            return plToPair(audioObjectType, -1);
        }
    }

    public static ParserBase getParserObject(String mediaType) {
        switch (mediaType) {
            case MediaFormat.MIMETYPE_VIDEO_MPEG4:
                return new Mpeg4Parser();
            case MediaFormat.MIMETYPE_VIDEO_H263:
                return new H263Parser();
            case MediaFormat.MIMETYPE_VIDEO_AVC:
                return new AvcParser();
            case MediaFormat.MIMETYPE_VIDEO_HEVC:
                return new HevcParser();
            case MediaFormat.MIMETYPE_VIDEO_AV1:
                return new Av1Parser();
            case MediaFormat.MIMETYPE_VIDEO_VP9:
                return new Vp9Parser();
            case MediaFormat.MIMETYPE_AUDIO_AAC:
                return new AacParser();
        }
        return null;
    }

    public static int getFrameTypeFromBitStream(ByteBuffer buf, MediaCodec.BufferInfo info,
            ParserBase o) {
        if (o == null) return PICTURE_TYPE_UNKNOWN;
        byte[] dataArray = new byte[info.size];
        buf.position(info.offset);
        buf.get(dataArray);
        o.set(dataArray, 0, info.size);
        return o.getFrameType();
    }

    public static Pair<Integer, Integer> getProfileLevelFromBitStream(ByteBuffer buf,
            MediaCodec.BufferInfo info, ParserBase o) {
        if (o == null) return null;
        byte[] dataArray = new byte[info.size];
        buf.position(info.offset);
        buf.get(dataArray);
        o.set(dataArray, 0, info.size);
        return o.getProfileLevel((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0);
    }
}
