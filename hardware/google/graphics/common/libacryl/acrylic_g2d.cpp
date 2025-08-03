/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)

#include "acrylic_g2d.h"

#include <alloca.h>
#include <exynos_format.h> // hardware/smasung_slsi/exynos/include
#include <hardware/hwcomposer2.h>
#include <log/log.h>
#include <mali_gralloc_formats.h>
#include <sys/ioctl.h>
#include <system/graphics.h>
#include <utils/Trace.h>

#include <algorithm>
#include <cstring>

enum {
    G2D_CSC_STD_UNDEFINED = -1,
    G2D_CSC_STD_601       = 0,
    G2D_CSC_STD_709       = 1,
    G2D_CSC_STD_2020      = 2,
    G2D_CSC_STD_P3        = 3,

    G2D_CSC_STD_COUNT     = 4,
};

enum {
    G2D_CSC_RANGE_LIMITED,
    G2D_CSC_RANGE_FULL,

    G2D_CSC_RANGE_COUNT,
};

static char csc_std_to_matrix_index[] = {
    G2D_CSC_STD_709,                          // HAL_DATASPACE_STANDARD_UNSPECIFIED
    G2D_CSC_STD_709,                          // HAL_DATASPACE_STANDARD_BT709
    G2D_CSC_STD_601,                          // HAL_DATASPACE_STANDARD_BT601_625
    G2D_CSC_STD_601,                          // HAL_DATASPACE_STANDARD_BT601_625_UNADJUSTED
    G2D_CSC_STD_601,                          // HAL_DATASPACE_STANDARD_BT601_525
    G2D_CSC_STD_601,                          // HAL_DATASPACE_STANDARD_BT601_525_UNADJUSTED
    G2D_CSC_STD_2020,                         // HAL_DATASPACE_STANDARD_BT2020
    G2D_CSC_STD_2020,                         // HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE
    static_cast<char>(G2D_CSC_STD_UNDEFINED), // HAL_DATASPACE_STANDARD_BT470M
    G2D_CSC_STD_709,                          // HAL_DATASPACE_STANDARD_FILM
    G2D_CSC_STD_P3,                           // HAL_DATASPACE_STANDARD_DCI_P3
    static_cast<char>(G2D_CSC_STD_UNDEFINED), // HAL_DATASPACE_STANDARD_ADOBE_RGB
};

static uint16_t YCbCr2sRGBCoefficients[G2D_CSC_STD_COUNT * G2D_CSC_RANGE_COUNT][9] = {
    {0x0254, 0x0000, 0x0331, 0x0254, 0xFF37, 0xFE60, 0x0254, 0x0409, 0x0000}, // 601 limited
    {0x0200, 0x0000, 0x02BE, 0x0200, 0xFF54, 0xFE9B, 0x0200, 0x0377, 0x0000}, // 601 full
    {0x0254, 0x0000, 0x0396, 0x0254, 0xFF93, 0xFEEF, 0x0254, 0x043A, 0x0000}, // 709 limited
    {0x0200, 0x0000, 0x0314, 0x0200, 0xFFA2, 0xFF16, 0x0200, 0x03A1, 0x0000}, // 709 full
    {0x0254, 0x0000, 0x035B, 0x0254, 0xFFA0, 0xFEB3, 0x0254, 0x0449, 0x0000}, // 2020 limited
    {0x0200, 0x0000, 0x02E2, 0x0200, 0xFFAE, 0xFEE2, 0x0200, 0x03AE, 0x0000}, // 2020 full
    {0x0254, 0x0000, 0x03AE, 0x0254, 0xFF96, 0xFEEE, 0x0254, 0x0456, 0x0000}, // DCI-P3 limited
    {0x0200, 0x0000, 0x0329, 0x0200, 0xFFA5, 0xFF15, 0x0200, 0x03B9, 0x0000}, // DCI-P3 full
};

static uint16_t sRGB2YCbCrCoefficients[G2D_CSC_STD_COUNT * G2D_CSC_RANGE_COUNT][9] = {
    {0x0083, 0x0102, 0x0032, 0xFFB4, 0xFF6B, 0x00E1, 0x00E1, 0xFF44, 0xFFDB}, // 601 limited
    {0x0099, 0x012D, 0x003A, 0xFFA8, 0xFF53, 0x0106, 0x0106, 0xFF25, 0xFFD5}, // 601 full
    {0x005D, 0x013A, 0x0020, 0xFFCC, 0xFF53, 0x00E1, 0x00E1, 0xFF34, 0xFFEB}, // 709 limited
    {0x006D, 0x016E, 0x0025, 0xFFC4, 0xFF36, 0x0106, 0x0106, 0xFF12, 0xFFE8}, // 709 full
    {0x0074, 0x012A, 0x001A, 0xFFC1, 0xFF5A, 0x00E1, 0x00E1, 0xFF31, 0xFFEE}, // 2020 limited
    {0x0087, 0x015B, 0x001E, 0xFFB7, 0xFF43, 0x0106, 0x0106, 0xFF0F, 0xFFEB}, // 2020 full
    {0x006B, 0x0171, 0x0023, 0xFFC6, 0xFF3A, 0x0100, 0x0100, 0xFF16, 0xFFEA}, // DCI-P3 limited(full)
    {0x006B, 0x0171, 0x0023, 0xFFC6, 0xFF3A, 0x0100, 0x0100, 0xFF16, 0xFFEA}, // DCI-P3 full
};

#define CSC_MATRIX_REGISTER_COUNT 9
#define CSC_MATRIX_REGISTER_SIZE  (CSC_MATRIX_REGISTER_COUNT * sizeof(uint32_t))

static inline bool g2dfmt_is_ycbcr(unsigned int g2dfmt)
{
    g2dfmt &= G2D_DATAFMT_MASK;
    return (G2D_DATAFMT_YUV_MIN <= g2dfmt) && (g2dfmt <= G2D_DATAFMT_YUV_MAX);
}

class CSCMatrixWriter {
    enum { CSC_MATRIX_MAX_COUNT = 4, CSC_MATRIX_INVALID_INDEX = 200 };
    enum { CSC_MATRIX_SRC_BASE = 0x2000, CSC_MATRIX_DST_BASE = 0x2100 };
public:
    CSCMatrixWriter(unsigned int g2dfmt, unsigned int dataspace, uint32_t *command)
                    : mMatrixCount(0), mMatrixTargetIndex(CSC_MATRIX_INVALID_INDEX) {
        // Ignore if unsupported dataspace is specified.
        // G2D also works for the case.
        // But the correctness of the result is not guaranteed.
        if (g2dfmt_is_ycbcr(g2dfmt)) {
            mMatrixTargetIndex = findMatrixIndex(dataspace);
            if ((dataspace & HAL_DATASPACE_RANGE_FULL) != 0)
                *command = G2D_LAYER_YCBCRMODE_WIDE;
        }
    }

    bool configure(unsigned int g2dfmt, unsigned int dataspace, uint32_t *command) {
        if (!g2dfmt_is_ycbcr(g2dfmt))
            return true;

        unsigned int spcidx = findMatrixIndex(dataspace);

        if (spcidx == CSC_MATRIX_INVALID_INDEX)
            return false;

        if ((dataspace & HAL_DATASPACE_RANGE_FULL) != 0)
            *command = G2D_LAYER_YCBCRMODE_WIDE;

        for (int i = 0; i < mMatrixCount; i++) {
            if (mMatrixIndex[i] == spcidx) {
                *command |= i;
                return true;
            }
        }

        if (mMatrixCount == CSC_MATRIX_MAX_COUNT) {
            ALOGE("Too many CSC requirements");
            return false;
        }

        mMatrixIndex[mMatrixCount] = spcidx;

        *command |= mMatrixCount++;

        return true;
    }

    unsigned int getRegisterCount() {
        unsigned int count = CSC_MATRIX_REGISTER_COUNT * mMatrixCount;
        if (mMatrixTargetIndex != CSC_MATRIX_INVALID_INDEX)
            count += CSC_MATRIX_REGISTER_COUNT;
        return count;
    }

    unsigned int write(g2d_reg regs[]) {
        unsigned int count = 0;

        if (mMatrixTargetIndex != CSC_MATRIX_INVALID_INDEX) {
            writeSingle(CSC_MATRIX_DST_BASE,
                        &regs[count], sRGB2YCbCrCoefficients[mMatrixTargetIndex]);
            count += CSC_MATRIX_REGISTER_COUNT;
        }

        for (int m = 0; m < mMatrixCount; m++) {
            writeSingle(CSC_MATRIX_SRC_BASE + m * CSC_MATRIX_REGISTER_SIZE,
                        &regs[count], YCbCr2sRGBCoefficients[mMatrixIndex[m]]);
            count += CSC_MATRIX_REGISTER_COUNT;
        }

        return count;
    }

private:
    void writeSingle(unsigned int base, g2d_reg regs[], uint16_t matrix[9]) {
        for (unsigned int idx = 0; idx < CSC_MATRIX_REGISTER_COUNT; idx++) {
            regs[idx].offset = base;
            regs[idx].value = matrix[idx];
            base += sizeof(uint32_t);
        }
    }

    unsigned int findMatrixIndex(unsigned int dataspace) {
        unsigned int index, colorspace;

        colorspace = (dataspace & HAL_DATASPACE_STANDARD_MASK) >> HAL_DATASPACE_STANDARD_SHIFT;
        if (colorspace >= ARRSIZE(csc_std_to_matrix_index)) {
            ALOGE("Data space %d is not supported by G2D", dataspace);
            return CSC_MATRIX_INVALID_INDEX;
        }

        index = csc_std_to_matrix_index[colorspace] * G2D_CSC_RANGE_COUNT;
        if ((dataspace & HAL_DATASPACE_RANGE_FULL) != 0)
            index++;

        return index;
    }

    unsigned int mMatrixIndex[CSC_MATRIX_MAX_COUNT];
    int mMatrixCount;
    unsigned int mMatrixTargetIndex;
};

#define G2D_FILTER_COEF_BASE 0x6000
#define G2D_FILTER_COEF_REG(idx) (0x6000 + (idx) * 0x400)
#define G2D_FILTER_C_OFFSET 0x200
#define G2D_SCALE_FACTOR(from, to) ((static_cast<uint32_t>(from) << G2D_SCALEFACTOR_FRACBITS) / (to))

#define SI11(v)                              static_cast<uint32_t>((v) & 0x7FF)
#define FILTER_HCOEF(a, b, c, d, e, f, g, h) {SI11(a), SI11(b), SI11(c), SI11(d), SI11(e), SI11(f), SI11(g), SI11(h)}
#define FILTER_VCOEF(a, b, c, d)             {SI11(a), SI11(b), SI11(c), SI11(d)}

#define NUM_HORI_COEFFICIENTS 8
#define NUM_VERT_COEFFICIENTS 4
#define NUM_FILTER_PHASE 9
#define NUM_FILTER_COEF_SETS 7

#define NUM_VERT_COEF_REGS (NUM_FILTER_PHASE * NUM_VERT_COEFFICIENTS)
#define NUM_HORI_COEF_REGS (NUM_FILTER_PHASE * NUM_HORI_COEFFICIENTS)

static uint32_t g2dHoriFilterCoef[NUM_FILTER_COEF_SETS][NUM_FILTER_PHASE][NUM_HORI_COEFFICIENTS] = {
    { // Upsampling
        FILTER_HCOEF(   0,   0,   0, 512,   0,   0,   0,   0), FILTER_HCOEF(  -2,   8, -25, 509,  30,  -9,   2,  -1), FILTER_HCOEF(  -4,  14, -46, 499,  64, -19,   5,  -1),
        FILTER_HCOEF(  -5,  20, -62, 482, 101, -30,   8,  -2), FILTER_HCOEF(  -5,  23, -73, 458, 142, -41,  12,  -3), FILTER_HCOEF(  -6,  25, -80, 429, 185, -53,  15,  -3),
        FILTER_HCOEF(  -6,  26, -83, 395, 228, -63,  19,  -4), FILTER_HCOEF(  -6,  25, -82, 357, 273, -71,  21,  -5), FILTER_HCOEF(  -5,  23, -78, 316, 316, -78,  23,  -5),
    }, { // x7/8 Downsampling
        FILTER_HCOEF(  12, -32,  56, 444,  52, -32,  12,   0), FILTER_HCOEF(   9, -24,  29, 445,  82, -39,  13,  -3), FILTER_HCOEF(   7, -16,   6, 438, 112, -46,  14,  -3),
        FILTER_HCOEF(   5,  -9, -14, 426, 144, -52,  15,  -3), FILTER_HCOEF(   3,  -3, -30, 410, 177, -58,  16,  -3), FILTER_HCOEF(   2,   2, -43, 390, 211, -63,  16,  -3),
        FILTER_HCOEF(   1,   7, -53, 365, 244, -66,  16,  -2), FILTER_HCOEF(   0,  10, -60, 338, 277, -66,  15,  -2), FILTER_HCOEF(  -1,  13, -65, 309, 309, -65,  13,  -1),
    }, { // x6/8 Downsampling
        FILTER_HCOEF(   8, -44, 100, 384, 100, -44,   8,   0), FILTER_HCOEF(   9, -40,  77, 382, 123, -47,   8,   0), FILTER_HCOEF(   8, -36,  57, 377, 147, -49,   7,   1),
        FILTER_HCOEF(   8, -32,  38, 369, 171, -49,   5,   2), FILTER_HCOEF(   8, -27,  20, 358, 196, -48,   3,   2), FILTER_HCOEF(   7, -22,   5, 344, 221, -47,   1,   3),
        FILTER_HCOEF(   7, -18,  -9, 329, 245, -43,  -2,   3), FILTER_HCOEF(   5, -13, -20, 310, 268, -37,  -5,   4), FILTER_HCOEF(   5,  -9, -30, 290, 290, -30,  -9,   5),
    }, { // x5/8 Downsampling
        FILTER_HCOEF(  -3, -31, 130, 320, 130, -31,  -3,   0), FILTER_HCOEF(  -3, -32, 113, 319, 147, -29,  -6,   3), FILTER_HCOEF(  -1, -33,  97, 315, 165, -26,  -8,   3),
        FILTER_HCOEF(   0, -32,  81, 311, 182, -22, -11,   3), FILTER_HCOEF(   1, -31,  66, 304, 199, -17, -13,   3), FILTER_HCOEF(   2, -30,  52, 296, 216, -11, -16,   3),
        FILTER_HCOEF(   2, -28,  38, 286, 232,  -3, -18,   3), FILTER_HCOEF(   3, -25,  26, 274, 247,   5, -21,   3), FILTER_HCOEF(   3, -23,  15, 261, 261,  15, -23,   3),
    }, { // x4/8 Downsampling
        FILTER_HCOEF( -11,   0, 140, 255, 140,   0, -12,   0), FILTER_HCOEF( -10,  -4, 129, 254, 151,   5, -13,   0), FILTER_HCOEF(  -9,  -7, 117, 253, 163,  10, -14,  -1),
        FILTER_HCOEF(  -8, -10, 106, 250, 174,  16, -15,  -1), FILTER_HCOEF(  -7, -12,  95, 246, 185,  22, -16,  -1), FILTER_HCOEF(  -6, -14,  85, 241, 195,  29, -16,  -2),
        FILTER_HCOEF(  -5, -15,  74, 236, 204,  37, -17,  -2), FILTER_HCOEF(  -5, -16,  64, 229, 214,  46, -17,  -3), FILTER_HCOEF(  -4, -17,  55, 222, 222,  55, -17,  -4),
    }, { // x3/8 Downsampling
        FILTER_HCOEF(  -5,  31, 133, 195, 133,  31,  -6,   0), FILTER_HCOEF(  -5,  27, 126, 195, 139,  37,  -4,  -3), FILTER_HCOEF(  -5,  23, 119, 194, 146,  41,  -3,  -3),
        FILTER_HCOEF(  -5,  19, 112, 193, 152,  47,  -2,  -4), FILTER_HCOEF(  -5,  16, 105, 191, 158,  53,  -2,  -4), FILTER_HCOEF(  -5,  12,  98, 189, 163,  59,   0,  -4),
        FILTER_HCOEF(  -5,  10,  91, 185, 169,  65,   1,  -4), FILTER_HCOEF(  -5,   7,  84, 182, 174,  71,   3,  -4), FILTER_HCOEF(  -5,   5,  78, 178, 178,  78,   5,  -5),
    }, { // x2/8 Downsampling
        FILTER_HCOEF(  10,  52, 118, 152, 118,  52,  10,   0), FILTER_HCOEF(   9,  48, 114, 152, 122,  56,  11,   0), FILTER_HCOEF(   7,  45, 110, 151, 125,  60,  13,   1),
        FILTER_HCOEF(   6,  41, 106, 150, 129,  64,  15,   1), FILTER_HCOEF(   5,  38, 102, 149, 132,  68,  17,   1), FILTER_HCOEF(   4,  35,  98, 148, 135,  72,  19,   1),
        FILTER_HCOEF(   4,  31,  94, 146, 138,  77,  21,   1), FILTER_HCOEF(   3,  29,  89, 145, 140,  81,  23,   2), FILTER_HCOEF(   2,  26,  85, 143, 143,  85,  26,   2),
    }
};

static uint32_t g2dVertFilterCoef[NUM_FILTER_COEF_SETS][NUM_FILTER_PHASE][NUM_VERT_COEFFICIENTS] = {
    {    // Upsampling
        FILTER_VCOEF(   0, 512,   0,   0), FILTER_VCOEF( -15, 508,  20,  -1), FILTER_VCOEF( -25, 495,  45,  -3),
        FILTER_VCOEF( -31, 473,  75,  -5), FILTER_VCOEF( -33, 443, 110,  -8), FILTER_VCOEF( -33, 408, 148, -11),
        FILTER_VCOEF( -31, 367, 190, -14), FILTER_VCOEF( -27, 324, 234, -19), FILTER_VCOEF( -23, 279, 279, -23),
    }, { // x7/8 Downsampling
        FILTER_VCOEF(  32, 448,  32,   0), FILTER_VCOEF(  17, 446,  55,  -6), FILTER_VCOEF(   3, 437,  79,  -7),
        FILTER_VCOEF(  -7, 421, 107,  -9), FILTER_VCOEF( -14, 399, 138, -11), FILTER_VCOEF( -18, 373, 170, -13),
        FILTER_VCOEF( -20, 343, 204, -15), FILTER_VCOEF( -20, 310, 240, -18), FILTER_VCOEF( -19, 275, 275, -19),
    }, { // x6/8 Downsampling
        FILTER_VCOEF(  61, 390,  61,   0), FILTER_VCOEF(  46, 390,  83,  -7), FILTER_VCOEF(  31, 383, 106,  -8),
        FILTER_VCOEF(  19, 371, 130,  -8), FILTER_VCOEF(   9, 356, 156,  -9), FILTER_VCOEF(   2, 337, 183, -10),
        FILTER_VCOEF(  -3, 315, 210, -10), FILTER_VCOEF(  -7, 291, 238, -10), FILTER_VCOEF(  -9, 265, 265,  -9),
    }, { // x5/8 Downsampling
        FILTER_VCOEF(  85, 341,  86,   0), FILTER_VCOEF(  71, 341, 105,  -5), FILTER_VCOEF(  56, 336, 124,  -4),
        FILTER_VCOEF(  43, 328, 145,  -4), FILTER_VCOEF(  32, 317, 166,  -3), FILTER_VCOEF(  23, 304, 187,  -2),
        FILTER_VCOEF(  16, 288, 209,  -1), FILTER_VCOEF(   9, 271, 231,   1), FILTER_VCOEF(   5, 251, 251,   5),
    }, { // x4/8 Downsampling
        FILTER_VCOEF( 104, 304, 104,   0), FILTER_VCOEF(  89, 302, 120,   1), FILTER_VCOEF(  76, 298, 136,   2),
        FILTER_VCOEF(  63, 293, 153,   3), FILTER_VCOEF(  52, 285, 170,   5), FILTER_VCOEF(  42, 275, 188,   7),
        FILTER_VCOEF(  33, 264, 205,  10), FILTER_VCOEF(  26, 251, 221,  14), FILTER_VCOEF(  20, 236, 236,  20),
    }, { // x3/8 Downsampling
        FILTER_VCOEF( 118, 276, 118,   0), FILTER_VCOEF( 103, 273, 129,   7), FILTER_VCOEF(  90, 270, 143,   9),
        FILTER_VCOEF(  78, 266, 157,  11), FILTER_VCOEF(  67, 260, 171,  14), FILTER_VCOEF(  57, 253, 185,  17),
        FILTER_VCOEF(  48, 244, 199,  21), FILTER_VCOEF(  40, 234, 211,  27), FILTER_VCOEF(  33, 223, 223,  33),
    }, { // x2/8 Downsampling
        FILTER_VCOEF( 127, 258, 127,   0), FILTER_VCOEF( 111, 252, 135,  14), FILTER_VCOEF( 100, 250, 147,  15),
        FILTER_VCOEF(  88, 247, 159,  18), FILTER_VCOEF(  78, 242, 171,  21), FILTER_VCOEF(  68, 237, 182,  25),
        FILTER_VCOEF(  59, 230, 193,  30), FILTER_VCOEF(  50, 222, 204,  36), FILTER_VCOEF(  43, 213, 213,  43),
    }
};

static unsigned int findFilterCoefficientsIndex(uint32_t factor)
{
    static uint32_t choicetable[NUM_FILTER_COEF_SETS] = {
        G2D_SCALE_FACTOR(8, 8), G2D_SCALE_FACTOR(8, 7), G2D_SCALE_FACTOR(8, 6), G2D_SCALE_FACTOR(8, 5),
        G2D_SCALE_FACTOR(8, 4), G2D_SCALE_FACTOR(8, 3), G2D_SCALE_FACTOR(8, 2),
    };

    for (unsigned int i = 0; i < NUM_FILTER_COEF_SETS; i++)
        if (factor <= choicetable[i])
            return i;
    // if i == NUM_FILTER_COEF_SETS, the downsampling factor is larger than 4.0 which is not supported by the filter.
    return NUM_FILTER_COEF_SETS - 1;
}

template<typename CoefT>
static unsigned int __writeFilterCoefficients(CoefT &coef_set, unsigned int index, uint32_t base, g2d_reg regs[])
{
    // The default value of filter coefficients are values of 8:8/zoom-in
    // So, do not update redundantly.
    if (index == 0)
        return 0;

    unsigned int cnt = 0;

    for (auto &coef_table: coef_set[index]) {
        uint32_t coef_base = base;
        // register offsets of filter coeffcients are ordered by phase number then tab index
        // while values are ordered by tab index then phase number in the above coefficients array.
        // So register offset is increased by the number of tabs instead of 1.
        // The coefficients array is specified in the order of tab index due to readability
        // because the coefficient values are also specified in the same order in UM.
        for (auto coef: coef_table) {
            regs[cnt].offset = coef_base;
            regs[cnt].value = coef;
            coef_base += (sizeof(coef_set[index]) / sizeof(coef_table)) * sizeof(coef_table[0]);
            cnt++;
        }
        base += sizeof(uint32_t);
    }

    return cnt;
}

void getChromaScaleFactor(uint32_t colormode, unsigned int *hfactor, unsigned int *vfactor)
{
    switch (colormode & G2D_DATAFMT_MASK) {
        case G2D_DATAFMT_YUV420SP:
        case G2D_DATAFMT_YUV420P:
            *hfactor /= 2;
            [[fallthrough]];
        case G2D_DATAFMT_YUV422SP:
        case G2D_DATAFMT_YUV422I:
            *vfactor /= 2;
            break;
        default:
            break;
    }
}

static unsigned int writeFilterCoefficients(uint32_t hfactor, uint32_t vfactor, uint32_t colormode,
                                            unsigned layer_index, g2d_reg regs[])
{
    unsigned int hindex = findFilterCoefficientsIndex(hfactor);
    unsigned int vindex = findFilterCoefficientsIndex(vfactor);
    // Filter coefficients of 1:1 and upsampling are configured to the filter by default (reset value)
    if ((hindex == 0) && (vindex == 0))
        return 0;

    unsigned int base = G2D_FILTER_COEF_REG(layer_index);
    unsigned int cnt = 0;
    // Y Coefficients
    cnt += __writeFilterCoefficients(g2dVertFilterCoef, vindex, base, regs);
    cnt += __writeFilterCoefficients(g2dHoriFilterCoef, hindex, base + sizeof(g2dVertFilterCoef[0]), regs + cnt);
    if (IS_YUV(colormode)) {
        // C Coefficients
        getChromaScaleFactor(colormode, &hfactor, &vfactor);

        hindex = findFilterCoefficientsIndex(hfactor);
        vindex = findFilterCoefficientsIndex(vfactor);
        base += G2D_FILTER_C_OFFSET;
        cnt += __writeFilterCoefficients(g2dVertFilterCoef, vindex, base, regs + cnt);
        cnt += __writeFilterCoefficients(g2dHoriFilterCoef, hindex, base + sizeof(g2dVertFilterCoef[0]), regs + cnt);
    }

    return cnt;
}

static unsigned int getFilterCoefficientCount(uint32_t *src_cmds[], unsigned int layer_count)
{
    unsigned int count = 0;


    for (unsigned int i = 0; i < layer_count; i++) {
        unsigned int layer_coef_cnt = 0;
        unsigned int hfactor = src_cmds[i][G2DSFR_SRC_XSCALE];
        unsigned int vfactor = src_cmds[i][G2DSFR_SRC_YSCALE];

        if (hfactor > G2D_SCALE_FACTOR(8, 8))
            layer_coef_cnt += NUM_HORI_COEF_REGS;
        if (vfactor > G2D_SCALE_FACTOR(8, 8))
            layer_coef_cnt += NUM_VERT_COEF_REGS;
        if (IS_YUV(src_cmds[i][G2DSFR_IMG_COLORMODE])) {
            getChromaScaleFactor(src_cmds[i][G2DSFR_IMG_COLORMODE], &hfactor, &vfactor);
            if (hfactor > G2D_SCALE_FACTOR(8, 8))
                layer_coef_cnt += NUM_HORI_COEF_REGS;
            if (vfactor > G2D_SCALE_FACTOR(8, 8))
                layer_coef_cnt += NUM_VERT_COEF_REGS;
        }

        count += layer_coef_cnt;
    }

    return count;
}

static void show_g2d_layer(const char *title, int idx, const g2d_layer &layer)
{
    ALOGD("%s%d: flags %#x, fence %d, buffer_type %d, num_buffers %d", title, idx,
          layer.flags, layer.fence, layer.buffer_type, layer.num_buffers);
    for (unsigned int i = 0; i < layer.num_buffers; i++) {
        ALOGD("         buf[%d] ptr %p, fd %d, offset %u, length %u",
              i, layer.buffer[i].userptr,
              layer.buffer[i].dmabuf.fd, layer.buffer[i].dmabuf.offset,
              layer.buffer[i].length);
    }
}

static void show_g2d_commands(const g2d_commands &cmds)
{
    for (unsigned int i = 0; i < G2DSFR_DST_FIELD_COUNT; i++)
        ALOGD("DST[%02d]: %#010x", i, cmds.target[i]);

    for (unsigned int idx = 0; idx < G2D_MAX_IMAGES; idx++) {
        if (cmds.source[idx]) {
            for (unsigned int i = 0; i < G2DSFR_SRC_FIELD_COUNT; i++)
                ALOGD("SRC[%02d][%02d]: %#010x", idx, i, cmds.source[idx][i]);
        }
    }

    if (cmds.extra) {
        for (unsigned int i = 0; i < cmds.num_extra_regs; i++)
            ALOGD("EXTRA: offset %#010x, value %#010x",
                  cmds.extra[i].offset, cmds.extra[i].value);
    }
}

static void show_g2d_task(const g2d_task &task)
{
    ALOGD("Showing the content of G2D task descriptor ver %#010x", task.version);
    ALOGD("source count %d, flags %#x, priority %d, num_release_fences %d",
          task.num_source, task.flags, task.priority, task.num_release_fences);
    show_g2d_layer("Target", 0, task.target);
    for (unsigned int i = 0; i < task.num_source; i++)
        show_g2d_layer("Source", i, task.source[i]);
    show_g2d_commands(task.commands);
}

#ifdef LIBACRYL_DEBUG
static void debug_show_g2d_task(const g2d_task &task)
{
    ALOGD("Showing the content of G2D task descriptor ver %#010x", task.version);
    ALOGD("source count %d, flags %#x, priority %d, num_release_fences %d",
          task.num_source, task.flags, task.priority, task.num_release_fences);
    show_g2d_layer("Target", 0, task.target);
    for (unsigned int i = 0; i < task.num_source; i++)
        show_g2d_layer("Source", i, task.source[i]);
    show_g2d_commands(task.commands);
}
#else
#define debug_show_g2d_task(task) do { } while (0)
#endif

struct g2d_fmt {
    uint32_t halfmt;
    uint32_t g2dfmt;
    uint32_t num_bufs;
    uint32_t rgb_bpp;
};

static g2d_fmt __halfmt_to_g2dfmt_legacy[] = {
//  {halfmt,                                      g2dfmt,  num_buffers, rgbbpp}
    {HAL_PIXEL_FORMAT_RGBA_8888,                  G2D_FMT_ABGR8888,  1, 4},
    {HAL_PIXEL_FORMAT_BGRA_8888,                  G2D_FMT_ARGB8888,  1, 4},
    {HAL_PIXEL_FORMAT_RGBX_8888,                  G2D_FMT_XBGR8888,  1, 4},
    {HAL_PIXEL_FORMAT_RGBA_1010102,               G2D_FMT_ABGR2101010, 1, 4},
    {HAL_PIXEL_FORMAT_RGB_888,                    G2D_FMT_RGB888,    1, 3},
    {HAL_PIXEL_FORMAT_RGB_565,                    G2D_FMT_RGB565,    1, 2},
//  {HAL_PIXEL_FORMAT_YV12,                       G2D_FMT_YV12,      1, 0},
//  {HAL_PIXEL_FORMAT_EXYNOS_YV12_M,              G2D_FMT_YV12,      3, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P,         G2D_FMT_YV12,      1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_PN,        G2D_FMT_YV12,      1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P_M,       G2D_FMT_YV12,      3, 0},
    {HAL_PIXEL_FORMAT_YCrCb_420_SP,               G2D_FMT_NV21,      1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M,      G2D_FMT_NV21,      2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL, G2D_FMT_NV21,      2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,        G2D_FMT_NV12,      1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M,      G2D_FMT_NV12,      2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN,       G2D_FMT_NV12,      1, 0},
    {HAL_PIXEL_FORMAT_YCBCR_P010,                 G2D_FMT_NV12_P010_LGCY, 1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M,        G2D_FMT_NV12_P010_LGCY, 2, 0},
    {HAL_PIXEL_FORMAT_YCbCr_422_I,                G2D_FMT_YUYV,      1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_422_I,         G2D_FMT_YVYU,      1, 0},
    {HAL_PIXEL_FORMAT_YCbCr_422_SP,               G2D_FMT_NV16,      1, 0},
    // TODO: add p010
};

static g2d_fmt __halfmt_to_g2dfmt[] = {
//  {halfmt,                                      g2dfmt,  num_buffers, rgbbpp}
    {HAL_PIXEL_FORMAT_RGBA_8888,                  G2D_FMT_ABGR8888,  1, 4},
    {HAL_PIXEL_FORMAT_BGRA_8888,                  G2D_FMT_ARGB8888,  1, 4},
    {HAL_PIXEL_FORMAT_RGBX_8888,                  G2D_FMT_XBGR8888,  1, 4},
    {HAL_PIXEL_FORMAT_RGBA_1010102,               G2D_FMT_ABGR2101010, 1, 4},
    {HAL_PIXEL_FORMAT_RGB_888,                    G2D_FMT_RGB888,    1, 3},
    {HAL_PIXEL_FORMAT_RGB_565,                    G2D_FMT_RGB565,    1, 2},
    {HAL_PIXEL_FORMAT_YV12,                       G2D_FMT_YV12,      1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YV12_M,              G2D_FMT_YV12,      3, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P,         G2D_FMT_YUV420P,   1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_PN,        G2D_FMT_YUV420P,   1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P_M,       G2D_FMT_YUV420P,   3, 0},
    {HAL_PIXEL_FORMAT_YCrCb_420_SP,               G2D_FMT_NV21,      1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M,      G2D_FMT_NV21,      2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL, G2D_FMT_NV21,      2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,        G2D_FMT_NV12,      1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M,      G2D_FMT_NV12,      2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN,       G2D_FMT_NV12,      1, 0},
    {HAL_PIXEL_FORMAT_GOOGLE_NV12_SP,             G2D_FMT_NV12,      1, 0},
    {MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I,  G2D_FMT_NV12,      1, 0},
    {HAL_PIXEL_FORMAT_YCBCR_P010,                 G2D_FMT_NV12_P010, 1, 0},
    {HAL_PIXEL_FORMAT_GOOGLE_NV12_SP_10B,         G2D_FMT_NV12_P010, 1, 0},
    {MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I, G2D_FMT_NV12_P010, 1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M,        G2D_FMT_NV12_P010, 2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_SPN,      G2D_FMT_NV12_P010, 1, 0},
    {MALI_GRALLOC_FORMAT_INTERNAL_P010,           G2D_FMT_NV12_P010, 1, 0},
    {HAL_PIXEL_FORMAT_YCbCr_422_I,                G2D_FMT_YUYV,      1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_422_I,         G2D_FMT_YVYU,      1, 0},
    {HAL_PIXEL_FORMAT_YCbCr_422_SP,               G2D_FMT_NV16,      1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC,     G2D_FMT_NV12_SBWC, 2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC,      G2D_FMT_NV12_SBWC, 1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC, G2D_FMT_NV12_SBWC_10B, 2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC,  G2D_FMT_NV12_SBWC_10B, 1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC,     G2D_FMT_NV21_SBWC, 2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC, G2D_FMT_NV21_SBWC_10B, 2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50, G2D_FMT_NV12_SBWC, 2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40, G2D_FMT_NV12_SBWC_10B, 2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80, G2D_FMT_NV12_SBWC_10B, 2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50, G2D_FMT_NV12_SBWC, 2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50, G2D_FMT_NV12_SBWC, 1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75, G2D_FMT_NV12_SBWC, 2,0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75, G2D_FMT_NV12_SBWC, 1,0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40, G2D_FMT_NV12_SBWC_10B, 2, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40, G2D_FMT_NV12_SBWC_10B, 1, 0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60, G2D_FMT_NV12_SBWC_10B, 2,0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60, G2D_FMT_NV12_SBWC_10B, 1,0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80, G2D_FMT_NV12_SBWC_10B, 2,0},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80, G2D_FMT_NV12_SBWC_10B, 1,0},
};

static g2d_fmt *halfmt_to_g2dfmt(struct g2d_fmt *tbl, size_t tbl_len, uint32_t halfmt)
{
    for (size_t i = 0 ; i < tbl_len; i++) {
        if (tbl[i].halfmt == halfmt)
            return &tbl[i];
    }

    ALOGE("Unable to find the proper G2D format for HAL format %#x", halfmt);

    return NULL;
}

AcrylicCompositorG2D::AcrylicCompositorG2D(const HW2DCapability &capability, bool newcolormode)
    : Acrylic(capability), mDev((capability.maxLayerCount() > 2) ? "/dev/g2d" : "/dev/fimg2d"),
      mMaxSourceCount(0), mPriority(-1)
{
    memset(&mTask, 0, sizeof(mTask));

    mVersion = 0;
    if (mDev.ioctl(G2D_IOC_VERSION, &mVersion) < 0)
        ALOGERR("Failed to get G2D command version");
    ALOGI("G2D API Version %d", mVersion);

    halfmt_to_g2dfmt_tbl = newcolormode ? __halfmt_to_g2dfmt : __halfmt_to_g2dfmt_legacy;
    len_halfmt_to_g2dfmt_tbl = newcolormode ? ARRSIZE(__halfmt_to_g2dfmt) : ARRSIZE(__halfmt_to_g2dfmt_legacy);

    mUsePolyPhaseFilter = getCapabilities().supportedMinDecimation() == hw2d_coord_t{4, 4};

    ALOGD_TEST("Created a new Acrylic for G2D on %p", this);
}

AcrylicCompositorG2D::~AcrylicCompositorG2D()
{
    delete [] mTask.source;
    delete [] mTask.commands.target;
    for (unsigned int i = 0; i < mMaxSourceCount; i++)
        delete [] mTask.commands.source[i];

    ALOGD_TEST("Deleting Acrylic for G2D on %p", this);
}

unsigned int AcrylicCompositorG2D::updateFilterCoefficients(unsigned int layercount, g2d_reg regs[])
{
    if (!mUsePolyPhaseFilter)
        return 0;

    unsigned int cnt = 0;

    for (unsigned int i = 0; i < layercount; i++)
        cnt += writeFilterCoefficients(mTask.commands.source[i][G2DSFR_SRC_XSCALE],
                                       mTask.commands.source[i][G2DSFR_SRC_YSCALE],
                                       mTask.commands.source[i][G2DSFR_IMG_COLORMODE],
                                       i, regs + cnt);

    return cnt;
}

#define SBWC_BLOCK_WIDTH 32
#define SBWC_BLOCK_HEIGHT 4
#define SBWC_BLOCK_SIZE(bit) (SBWC_BLOCK_WIDTH * SBWC_BLOCK_HEIGHT * (bit) / 8)

#define SBWC_HEADER_ALIGN 16
#define SBWC_PAYLOAD_ALIGN 32

#define SBWC_HEADER_STRIDE(w) \
	ALIGN(((w) / SBWC_BLOCK_WIDTH / 2), SBWC_HEADER_ALIGN)
#define SBWC_PAYLOAD_STRIDE(w, dep)\
	ALIGN(((w) / SBWC_BLOCK_WIDTH) * SBWC_BLOCK_SIZE(dep), \
	      SBWC_PAYLOAD_ALIGN)

#define SBWC_LOSSY_PAYLOAD_STRIDE(w, block_byte) \
	ALIGN(((w) / SBWC_BLOCK_WIDTH) * (block_byte), \
	      SBWC_PAYLOAD_ALIGN)

static uint32_t mfc_stride_formats[] = {
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC,
};

struct lossy_formats {
        unsigned int halfmt;
        unsigned int blocksize;
};

static lossy_formats sbwc_lossy_formats[] {
   {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50,            64},
   {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40,        64},
   {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50,             64},
   {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40,          64},
   {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75,             96},
   {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60,         96},
   {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75,              96},
   {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60,          96},
   {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80,         128},
   {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80,          128},
};


bool AcrylicCompositorG2D::prepareImage(AcrylicCanvas &layer, struct g2d_layer &image, uint32_t cmd[], int index)
{
    image.flags = 0;

    if (layer.getFence() >= 0) {
        image.flags |= G2D_LAYERFLAG_ACQUIRE_FENCE;
        image.fence = layer.getFence();
    }

    if (layer.isProtected())
        image.flags |= G2D_LAYERFLAG_SECURE;

    g2d_fmt *g2dfmt = halfmt_to_g2dfmt(halfmt_to_g2dfmt_tbl, len_halfmt_to_g2dfmt_tbl, layer.getFormat());
    if (!g2dfmt)
        return false;

    image.flags &= ~G2D_LAYERFLAG_MFC_STRIDE;
    for (size_t i = 0; i < ARRSIZE(mfc_stride_formats); i++) {
        if (layer.getFormat() == mfc_stride_formats[i]) {
            image.flags |= G2D_LAYERFLAG_MFC_STRIDE;
            break;
        }
    }

    if (layer.getBufferType() == AcrylicCanvas::MT_EMPTY) {
        image.buffer_type = G2D_BUFTYPE_EMPTY;
    } else {
        if (layer.getBufferCount() < g2dfmt->num_bufs) {
            ALOGE("HAL Format %#x requires %d buffers but %d buffers are given",
                    layer.getFormat(), g2dfmt->num_bufs, layer.getBufferCount());
            return false;
        }

        if (layer.getBufferType() == AcrylicCanvas::MT_DMABUF) {
            image.buffer_type = G2D_BUFTYPE_DMABUF;
            for (unsigned int i = 0; i < g2dfmt->num_bufs; i++) {
                image.buffer[i].dmabuf.fd = layer.getDmabuf(i);
                image.buffer[i].dmabuf.offset = layer.getOffset(i);
                image.buffer[i].length = layer.getBufferLength(i);
            }
        } else {
            LOGASSERT(layer.getBufferType() == AcrylicCanvas::MT_USERPTR,
                      "Unknown buffer type %d", layer.getBufferType());
            image.buffer_type = G2D_BUFTYPE_USERPTR;
            for (unsigned int i = 0; i < g2dfmt->num_bufs; i++) {
                image.buffer[i].userptr = layer.getUserptr(i);
                image.buffer[i].length = layer.getBufferLength(i);
            }
        }
    }

    image.num_buffers = g2dfmt->num_bufs;

    hw2d_coord_t xy = layer.getImageDimension();

    cmd[G2DSFR_IMG_COLORMODE] = g2dfmt->g2dfmt;
    if (layer.isUOrder())
        cmd[G2DSFR_IMG_COLORMODE] |= G2D_DATAFORMAT_UORDER;

    if (layer.isCompressed()) {
        // AFBC forces RGB swizzling order to BGR for RGB565
        if (g2dfmt->g2dfmt == G2D_FMT_RGB565)
            cmd[G2DSFR_IMG_COLORMODE] = G2D_FMT_BGR565;
        cmd[G2DSFR_IMG_COLORMODE] |= G2D_DATAFORMAT_AFBC;
        cmd[G2DSFR_IMG_STRIDE] = 0;
    } else if (layer.isCompressedWideblk()) {
        if (g2dfmt->g2dfmt == G2D_FMT_RGB565) cmd[G2DSFR_IMG_COLORMODE] = G2D_FMT_BGR565;
        cmd[G2DSFR_IMG_COLORMODE] |= G2D_DATAFORMAT_AFBC;
        cmd[G2DSFR_IMG_STRIDE] = 0;

        /*Add AFBC image flags for 32x8 block size*/
        image.flags |= G2D_LAYERFLAG_AFBC_WIDEBLK;
    } else if (g2dfmt->g2dfmt & G2D_DATAFORMAT_SBWC) {
        cmd[G2DSFR_IMG_STRIDE] = 0;
    } else {
        cmd[G2DSFR_IMG_STRIDE] = g2dfmt->rgb_bpp * xy.hori;
    }

    unsigned int payload = 0, header = 0, lossyByteNum = 0;

    if (g2dfmt->g2dfmt & G2D_DATAFORMAT_SBWC) {
        unsigned int blocksize;
        unsigned int isLossy = 0;
        unsigned int format = layer.getFormat();

        for (unsigned int i = 0; i < ARRSIZE(sbwc_lossy_formats); i++) {
            if (format == sbwc_lossy_formats[i].halfmt) {
                isLossy = 1;
                blocksize = sbwc_lossy_formats[i].blocksize;
                break;
            }
        }

        if (isLossy) {
            lossyByteNum = (blocksize >> 1) | isLossy;
            payload = SBWC_LOSSY_PAYLOAD_STRIDE(xy.hori, blocksize);
        } else {
            payload = SBWC_PAYLOAD_STRIDE(xy.hori, (g2dfmt->g2dfmt & G2D_FMT_YCBCR_10BIT) ? 10 : 8);
            header = SBWC_HEADER_STRIDE(xy.hori);
        }
    }

    if (index < 0) {
        cmd[G2DSFR_DST_Y_HEADER_STRIDE] = header;
        cmd[G2DSFR_DST_C_HEADER_STRIDE] = header;
        cmd[G2DSFR_DST_Y_PAYLOAD_STRIDE] = payload;
        cmd[G2DSFR_DST_C_PAYLOAD_STRIDE] = payload;
        cmd[G2DSFR_DST_SBWCINFO] = lossyByteNum;
    } else {
        cmd[G2DSFR_SRC_Y_HEADER_STRIDE] = header;
        cmd[G2DSFR_SRC_C_HEADER_STRIDE] = header;
        cmd[G2DSFR_SRC_Y_PAYLOAD_STRIDE] = payload;
        cmd[G2DSFR_SRC_C_PAYLOAD_STRIDE] = payload;
        cmd[G2DSFR_SRC_SBWCINFO] = lossyByteNum;
    }

    cmd[G2DSFR_IMG_LEFT]   = 0;
    cmd[G2DSFR_IMG_TOP]    = 0;
    cmd[G2DSFR_IMG_RIGHT]  = xy.hori;
    cmd[G2DSFR_IMG_BOTTOM] = xy.vert;
    cmd[G2DSFR_IMG_WIDTH]  = xy.hori;
    cmd[G2DSFR_IMG_HEIGHT] = xy.vert;

    return true;
}

static void setSolidLayer(struct g2d_layer &image, uint32_t cmd[], hw2d_coord_t xy)
{
    image.flags = G2D_LAYERFLAG_COLORFILL;
    image.buffer_type = G2D_BUFTYPE_EMPTY;
    image.num_buffers = 0;

    cmd[G2DSFR_IMG_COLORMODE] = G2D_FMT_ARGB8888;
    cmd[G2DSFR_IMG_STRIDE] = 4 * xy.hori;

    cmd[G2DSFR_IMG_WIDTH]  = xy.hori;
    cmd[G2DSFR_IMG_HEIGHT] = xy.vert;

    cmd[G2DSFR_SRC_SELECT] = G2D_LAYERSEL_COLORFILL;
    cmd[G2DSFR_SRC_COMMAND] = G2D_LAYERCMD_VALID;

    cmd[G2DSFR_SRC_ROTATE] = 0;
    cmd[G2DSFR_SRC_SCALECONTROL] = 0;
    cmd[G2DSFR_SRC_XSCALE] = G2D_SCALE_FACTOR(1, 1);
    cmd[G2DSFR_SRC_YSCALE] = G2D_SCALE_FACTOR(1, 1);
    cmd[G2DSFR_SRC_XPHASE] = 0;
    cmd[G2DSFR_SRC_YPHASE] = 0;
    cmd[G2DSFR_SRC_YCBCRMODE] = 0;
    cmd[G2DSFR_SRC_HDRMODE] = 0;
    cmd[G2DSFR_SRC_Y_HEADER_STRIDE] = 0;
    cmd[G2DSFR_SRC_C_HEADER_STRIDE] = 0;
    cmd[G2DSFR_SRC_Y_PAYLOAD_STRIDE] = 0;
    cmd[G2DSFR_SRC_C_PAYLOAD_STRIDE] = 0;
    cmd[G2DSFR_SRC_SBWCINFO] = 0;
}

bool AcrylicCompositorG2D::prepareSolidLayer(AcrylicCanvas &canvas, struct g2d_layer &image, uint32_t cmd[])
{
    hw2d_coord_t xy = canvas.getImageDimension();

    setSolidLayer(image, cmd, xy);

    uint16_t a, r, g, b;
    getBackgroundColor(&r, &g, &b, &a);

    cmd[G2DSFR_SRC_COLOR]  = (a & 0xFF00) << 16;
    cmd[G2DSFR_SRC_COLOR] |= (r & 0xFF00) << 8;
    cmd[G2DSFR_SRC_COLOR] |= (g & 0xFF00) << 0;
    cmd[G2DSFR_SRC_COLOR] |= (b & 0xFF00) >> 8;

    cmd[G2DSFR_IMG_LEFT]   = 0;
    cmd[G2DSFR_IMG_TOP]    = 0;
    cmd[G2DSFR_IMG_RIGHT]  = xy.hori;
    cmd[G2DSFR_IMG_BOTTOM] = xy.vert;

    cmd[G2DSFR_SRC_DSTLEFT]   = 0;
    cmd[G2DSFR_SRC_DSTTOP]    = 0;
    cmd[G2DSFR_SRC_DSTRIGHT]  = xy.hori;
    cmd[G2DSFR_SRC_DSTBOTTOM] = xy.vert;

    cmd[G2DSFR_SRC_ALPHA] = 0;
    cmd[G2DSFR_SRC_BLEND] = 0;

    return true;
}

bool AcrylicCompositorG2D::prepareSolidLayer(AcrylicLayer &layer, struct g2d_layer &image, uint32_t cmd[], hw2d_coord_t target_size, unsigned int index)
{
    hw2d_coord_t xy = layer.getImageDimension();

    setSolidLayer(image, cmd, xy);

    cmd[G2DSFR_SRC_COLOR]  = layer.getSolidColor();

    hw2d_rect_t crop = layer.getImageRect();
    cmd[G2DSFR_IMG_LEFT]   = crop.pos.hori;
    cmd[G2DSFR_IMG_TOP]    = crop.pos.vert;
    cmd[G2DSFR_IMG_RIGHT]  = crop.size.hori + crop.pos.hori;
    cmd[G2DSFR_IMG_BOTTOM] = crop.size.vert + crop.pos.vert;

    hw2d_rect_t window = layer.getTargetRect();
    if (area_is_zero(window))
        window.size = target_size;
    cmd[G2DSFR_SRC_DSTLEFT]   = window.pos.hori;
    cmd[G2DSFR_SRC_DSTTOP]    = window.pos.vert;
    cmd[G2DSFR_SRC_DSTRIGHT]  = window.size.hori + window.pos.hori;
    cmd[G2DSFR_SRC_DSTBOTTOM] = window.size.vert + window.pos.vert;

    uint8_t alpha = layer.getPlaneAlpha();
    cmd[G2DSFR_SRC_ALPHA] = (alpha << 24) | (alpha << 16) | (alpha << 8) | alpha;
    if ((layer.getCompositingMode() == HWC_BLENDING_PREMULT) ||
            (layer.getCompositingMode() == HWC2_BLEND_MODE_PREMULTIPLIED)) {
        cmd[G2DSFR_SRC_BLEND] = G2D_BLEND_SRCOVER;
    } else if ((layer.getCompositingMode() == HWC_BLENDING_COVERAGE) ||
               (layer.getCompositingMode() == HWC2_BLEND_MODE_COVERAGE)) {
        cmd[G2DSFR_SRC_BLEND] = G2D_BLEND_NONE;
    } else {
        cmd[G2DSFR_SRC_BLEND] = G2D_BLEND_SRCCOPY;
    }

    /* bottom layer always is opaque */
    if (index == 0) {
       cmd[G2DSFR_SRC_COMMAND] |= G2D_LAYERCMD_OPAQUE;
       if (alpha < 255)
           cmd[G2DSFR_SRC_COMMAND] |= G2D_LAYERCMD_PREMULT_GLOBALALPHA;
    } else {
       cmd[G2DSFR_SRC_COMMAND] |= G2D_LAYERCMD_ALPHABLEND;
    }

    return true;
}

bool AcrylicCompositorG2D::prepareSource(AcrylicLayer &layer, struct g2d_layer &image, uint32_t cmd[],
                                             hw2d_coord_t target_size, unsigned int index, unsigned int image_index)
{
    if (layer.isSolidColor()) {
        prepareSolidLayer(layer, image, cmd, target_size, image_index);

        return true;
    }

    if (!prepareImage(layer, image, cmd, index))
        return false;

    cmd[G2DSFR_SRC_SELECT] = 0;

    hw2d_rect_t crop = layer.getImageRect();
    cmd[G2DSFR_IMG_LEFT]   = crop.pos.hori;
    cmd[G2DSFR_IMG_TOP]    = crop.pos.vert;
    cmd[G2DSFR_IMG_RIGHT]  = crop.size.hori + crop.pos.hori;
    cmd[G2DSFR_IMG_BOTTOM] = crop.size.vert + crop.pos.vert;

    hw2d_rect_t window = layer.getTargetRect();
    if (area_is_zero(window))
        window.size = target_size;
    cmd[G2DSFR_SRC_DSTLEFT]   = window.pos.hori;
    cmd[G2DSFR_SRC_DSTTOP]    = window.pos.vert;
    cmd[G2DSFR_SRC_DSTRIGHT]  = window.size.hori + window.pos.hori;
    cmd[G2DSFR_SRC_DSTBOTTOM] = window.size.vert + window.pos.vert;

    if (layer.isCompressed() || layer.isCompressedWideblk()) {
        cmd[G2DSFR_IMG_WIDTH]--;
        cmd[G2DSFR_IMG_HEIGHT]--;
    }

    cmd[G2DSFR_SRC_ROTATE] = 0;
    // HAL FLIP value: FLIP_H=0x01, FLIP_V=0x02
    // G2D FLIP value: FLIP_Y=0x05, FLIP_X=0x04
    unsigned int flip = layer.getTransform() & (HAL_TRANSFORM_FLIP_H | HAL_TRANSFORM_FLIP_V);
    if (!!(layer.getTransform() & HAL_TRANSFORM_ROT_90)) {
        window.size.swap();

        cmd[G2DSFR_SRC_ROTATE] |= G2D_ROTATEDIR_ROT90CCW;
        if (!flip || (flip == (HAL_TRANSFORM_FLIP_H | HAL_TRANSFORM_FLIP_V)))
            flip = ~flip & (HAL_TRANSFORM_FLIP_H | HAL_TRANSFORM_FLIP_V);
    }

    cmd[G2DSFR_SRC_ROTATE] |= flip << G2D_ROTATEDIR_FLIP_SHIFT;

    cmd[G2DSFR_SRC_XSCALE] = G2D_SCALE_FACTOR(crop.size.hori, window.size.hori);
    cmd[G2DSFR_SRC_YSCALE] = G2D_SCALE_FACTOR(crop.size.vert, window.size.vert);
    // Configure interpolation only if it is required.
    // Otherwise, G2D needs more bandwidth because it interpolates pixels
    // even though it is not required.
    if ((cmd[G2DSFR_SRC_XSCALE] | cmd[G2DSFR_SRC_YSCALE]) == G2D_SCALE_FACTOR(1, 1))
        cmd[G2DSFR_SRC_SCALECONTROL] = 0;
    else if (mUsePolyPhaseFilter)
        cmd[G2DSFR_SRC_SCALECONTROL] = (index << G2D_SCALECONTROL_FILTERCOEF_SHIFT) | G2D_SCALECONTROL_POLYPHASE;
    else
        cmd[G2DSFR_SRC_SCALECONTROL] = G2D_SCALECONTROL_BILINEAR;

    // TODO: Configure initial phases according to the scale factors
     cmd[G2DSFR_SRC_XPHASE] = 0;
     cmd[G2DSFR_SRC_YPHASE] = 0;

    uint8_t alpha = layer.getPlaneAlpha();
    cmd[G2DSFR_SRC_ALPHA] = (alpha << 24) | (alpha << 16) | (alpha << 8) | alpha;
    if ((layer.getCompositingMode() == HWC_BLENDING_PREMULT) ||
            (layer.getCompositingMode() == HWC2_BLEND_MODE_PREMULTIPLIED)) {
        cmd[G2DSFR_SRC_BLEND] = G2D_BLEND_SRCOVER;
    } else if ((layer.getCompositingMode() == HWC_BLENDING_COVERAGE) ||
               (layer.getCompositingMode() == HWC2_BLEND_MODE_COVERAGE)) {
        cmd[G2DSFR_SRC_BLEND] = G2D_BLEND_NONE;
    } else {
        cmd[G2DSFR_SRC_BLEND] = G2D_BLEND_SRCCOPY;

        // HWC_BLEND_NONE is used not to appear its lower layer to target layer.
        // But, when G2D output is reused by DPU, lower layer could appear to target layer.
        // To prevent this, when blend mode is HWC_BLEND_NONE, make alpha channel max.
        // Example case is as follow.
        // If G2D composites several layers and topmost layer is HWC_BLEND_NONE
        // and has alpha lower than max, that alpha value remains in target buffer.
        // And if this result layer is recomposited with lower layer by DPU
        // lower layer color appears to final result layer.
        if ((cmd[G2DSFR_IMG_COLORMODE] == G2D_FMT_ABGR8888) ||
            (cmd[G2DSFR_IMG_COLORMODE] == G2D_FMT_ARGB8888) ||
            (cmd[G2DSFR_IMG_COLORMODE] == G2D_FMT_ABGR2101010)) {
            cmd[G2DSFR_IMG_COLORMODE] &= ~G2D_SWZ_ALPHA_MASK;
            cmd[G2DSFR_IMG_COLORMODE] |= G2D_SWZ_ALPHA_ONE;
        }
    }

    cmd[G2DSFR_SRC_COMMAND] = G2D_LAYERCMD_VALID;

    /* bottom layer always is opaque */
    if (image_index == 0) {
       cmd[G2DSFR_SRC_COMMAND] |= G2D_LAYERCMD_OPAQUE;
       if (alpha < 255)
           cmd[G2DSFR_SRC_COMMAND] |= G2D_LAYERCMD_PREMULT_GLOBALALPHA;
    } else {
       cmd[G2DSFR_SRC_COMMAND] |= G2D_LAYERCMD_ALPHABLEND;
    }

    cmd[G2DSFR_SRC_YCBCRMODE] = 0;
    cmd[G2DSFR_SRC_HDRMODE] = 0;

    return true;
}

bool AcrylicCompositorG2D::reallocLayer(unsigned int layercount)
{
    if (mMaxSourceCount >= layercount)
        return true;

    if (!mTask.commands.target) {
        mTask.commands.target = new uint32_t[G2DSFR_DST_FIELD_COUNT];
        if (!mTask.commands.target) {
            ALOGE("Failed to allocate command buffer for target image");
            return false;
        }

	memset(mTask.commands.target, 0, sizeof(uint32_t) * G2DSFR_DST_FIELD_COUNT);
    }

    delete [] mTask.source;
    for (unsigned int i = 0; i < mMaxSourceCount; i++)
        delete [] mTask.commands.source[i];

    mMaxSourceCount = 0;

    mTask.source = new g2d_layer[layercount];
    if (!mTask.source) {
        ALOGE("Failed to allocate %u source image descriptors", layercount);
        return false;
    }

    for (unsigned int i = 0; i < layercount; i++) {
        mTask.commands.source[i] = new uint32_t[G2DSFR_SRC_FIELD_COUNT];
        if (mTask.commands.source[i] == NULL) {
            ALOGE("Failed to allocate command buffer for source image");
            while (i-- > 0)
                delete [] mTask.commands.source[i];

            delete [] mTask.source;
            mTask.source = NULL;

            return false;
        }

	memset(mTask.commands.source[i], 0, sizeof(uint32_t) * G2DSFR_SRC_FIELD_COUNT);
    }

    mMaxSourceCount = layercount;

    return true;
}

int AcrylicCompositorG2D::ioctlG2D(void)
{
    if (mVersion == 1) {
        if (mDev.ioctl(G2D_IOC_PROCESS, &mTask) < 0)
            return -errno;
    } else {
        struct g2d_compat_task task;

        memcpy(&task, &mTask, sizeof(mTask) - sizeof(mTask.commands));
        memcpy(task.commands.target, mTask.commands.target, sizeof(task.commands.target));

        for (unsigned int i = 0; i < mMaxSourceCount; i++)
            task.commands.source[i] = mTask.commands.source[i];

        task.commands.extra = mTask.commands.extra;
        task.commands.num_extra_regs = mTask.commands.num_extra_regs;

        if (mDev.ioctl(G2D_IOC_COMPAT_PROCESS, &task) < 0)
            return -errno;

        mTask.flags = task.flags;
        mTask.laptime_in_usec = task.laptime_in_usec;

        for (unsigned int i = 0; i < mTask.num_release_fences; i++)
            mTask.release_fence[i] = task.release_fence[i];
    }

    return 0;
}

bool AcrylicCompositorG2D::executeG2D(int fence[], unsigned int num_fences, bool nonblocking)
{
    ATRACE_CALL();
    if (!validateAllLayers())
        return false;

    unsigned int layercount = layerCount();

    // Set invalid fence fd to the entries exceeds the number of source and destination images
    for (unsigned int i = layercount; i < num_fences; i++)
        fence[i] = -1;

    if (num_fences > layercount + 1)
        num_fences = layercount + 1;

    bool hasBackground = hasBackgroundColor();

    g2d_fmt *g2dfmt = halfmt_to_g2dfmt(halfmt_to_g2dfmt_tbl, len_halfmt_to_g2dfmt_tbl, getCanvas().getFormat());
    if (g2dfmt && (g2dfmt->g2dfmt & G2D_DATAFORMAT_SBWC))
        hasBackground = true;

    if (hasBackground) {
        layercount++;

        if (layercount > getCapabilities().maxLayerCount()) {
            ALOGE("Too many layers %d with the default background color configured", layerCount());
            return false;
        }
    }

    if (!reallocLayer(layercount))
        return false;

    sortLayers();

    mTask.flags = 0;

    if (!prepareImage(getCanvas(), mTask.target, mTask.commands.target, -1)) {
        ALOGE("Failed to configure the target image");
        return false;
    }

    if (getCanvas().isOTF())
        mTask.flags |= G2D_FLAG_HWFC;

    unsigned int baseidx = 0;

    if (hasBackground) {
        baseidx++;
        prepareSolidLayer(getCanvas(), mTask.source[0], mTask.commands.source[0]);
    }

    mTask.commands.target[G2DSFR_DST_YCBCRMODE] = 0;

    CSCMatrixWriter cscMatrixWriter(mTask.commands.target[G2DSFR_IMG_COLORMODE],
                                    getCanvas().getDataspace(),
                                    &mTask.commands.target[G2DSFR_DST_YCBCRMODE]);

    mTask.commands.target[G2DSFR_DST_YCBCRMODE] |= (G2D_LAYER_YCBCRMODE_OFFX | G2D_LAYER_YCBCRMODE_OFFY);

    for (unsigned int i = baseidx; i < layercount; i++) {
        AcrylicLayer &layer = *getLayer(i - baseidx);

        if (!prepareSource(layer, mTask.source[i],
                           mTask.commands.source[i], getCanvas().getImageDimension(),
                           i, i - baseidx)) {
            ALOGE("Failed to configure source layer %u", i - baseidx);
            return false;
        }

        if (!cscMatrixWriter.configure(mTask.commands.source[i][G2DSFR_IMG_COLORMODE],
                                       layer.getDataspace(),
                                       &mTask.commands.source[i][G2DSFR_SRC_YCBCRMODE])) {
            ALOGE("Failed to configure CSC coefficient of layer %d for dataspace %u",
                  i, layer.getDataspace());
            return false;
        }

        if (layer.getLayerHDR()) {
            mHdrWriter.setLayerStaticMetadata(i, layer.getDataspace(),
                                              layer.getMinMasteringLuminance(),
                                              layer.getMaxMasteringLuminance());

            bool alpha_premult = (layer.getCompositingMode() == HWC_BLENDING_PREMULT)
                                 || (layer.getCompositingMode() == HWC2_BLEND_MODE_PREMULTIPLIED);
            mHdrWriter.setLayerImageInfo(i, layer.getFormat(), alpha_premult);
            mHdrWriter.setLayerOpaqueData(i, layer.getLayerData(), layer.getLayerDataLength());
        }
    }

    mHdrWriter.setTargetInfo(getCanvas().getDataspace(), getTargetDisplayInfo());
    mHdrWriter.setTargetDisplayLuminance(getMinTargetDisplayLuminance(), getMaxTargetDisplayLuminance());

    mHdrWriter.getCommands();
    mHdrWriter.getLayerHdrMode(mTask);

    mTask.num_source = layercount;

    if (nonblocking)
        mTask.flags |= G2D_FLAG_NONBLOCK;

    mTask.num_release_fences = num_fences;
    mTask.release_fence = reinterpret_cast<int *>(alloca(sizeof(int) * num_fences));

    mTask.commands.num_extra_regs = cscMatrixWriter.getRegisterCount() +
                                    mHdrWriter.getCommandCount();
    if (mUsePolyPhaseFilter)
        mTask.commands.num_extra_regs += getFilterCoefficientCount(mTask.commands.source, layercount);

    mTask.commands.extra = reinterpret_cast<g2d_reg *>(alloca(sizeof(g2d_reg) * mTask.commands.num_extra_regs));

    g2d_reg *regs = mTask.commands.extra;

    regs += cscMatrixWriter.write(regs);

    regs += updateFilterCoefficients(layercount, regs);

    mHdrWriter.write(regs);

    debug_show_g2d_task(mTask);

    if (ioctlG2D() < 0) {
        ALOGERR("Failed to process a task");
        show_g2d_task(mTask);
        return false;
    }

    mHdrWriter.putCommands();

    if (!!(mTask.flags & G2D_FLAG_ERROR)) {
        ALOGE("Error occurred during processing a task to G2D");
        show_g2d_task(mTask);
        return false;
    }

    getCanvas().clearSettingModified();
    getCanvas().setFence(-1);

    for (unsigned int i = 0; i < layerCount(); i++) {
        getLayer(i)->clearSettingModified();
        getLayer(i)->setFence(-1);
    }

    for (unsigned int i = 0; i < num_fences; i++)
        fence[i] = mTask.release_fence[i];

    return true;
}

bool AcrylicCompositorG2D::execute(int fence[], unsigned int num_fences)
{
    if (!executeG2D(fence, num_fences, true)) {
        // Clearing all acquire fences because their buffers are expired.
        // The clients should configure everything again to start new execution
        for (unsigned int i = 0; i < layerCount(); i++)
            getLayer(i)->setFence(-1);
        getCanvas().setFence(-1);

        return false;
    }

    return true;
}

bool AcrylicCompositorG2D::execute(int *handle)
{
    if (!executeG2D(NULL, 0, handle ? true : false)) {
        // Clearing all acquire fences because their buffers are expired.
        // The clients should configure everything again to start new execution
        for (unsigned int i = 0; i < layerCount(); i++)
            getLayer(i)->setFence(-1);
        getCanvas().setFence(-1);

        return false;
    }

    if (handle != NULL)
        *handle = 1; /* dummy handle */

    return true;
}

bool AcrylicCompositorG2D::waitExecution(int __unused handle)
{
    ALOGD_TEST("Waiting for execution of m2m1shot2 G2D completed by handle %d", handle);

    return true;
}

bool AcrylicCompositorG2D::requestPerformanceQoS(AcrylicPerformanceRequest *request)
{
    g2d_performance data;

    memset(&data, 0, sizeof(data));

    if (!request || (request->getFrameCount() == 0)) {
        if (mDev.ioctl(G2D_IOC_PERFORMANCE, &data) < 0) {
            ALOGERR("Failed to cancel performance request");
            return false;
        }

        ALOGD_TEST("Canceled performance request");
        return true;
    }

    ALOGD_TEST("Requesting performance: frame count %d:", request->getFrameCount());
    for (int i = 0; i < request->getFrameCount(); i++) {
        AcrylicPerformanceRequestFrame *frame = request->getFrame(i);
        uint64_t bandwidth = 0;
        bool src_yuv420_8b;
        bool src_rotate;

        src_rotate = false;
        src_yuv420_8b = false;

        unsigned int bpp;
        uint8_t planecount;
        uint32_t equiv_fmt;
        for (int idx = 0; idx < frame->getLayerCount(); idx++) {
            AcrylicPerformanceRequestLayer *layer = &(frame->mLayers[idx]);
            uint64_t layer_bw, pixelcount;
            int32_t is_scaling;
            uint32_t src_hori = layer->mSourceRect.size.hori;
            uint32_t src_vert = layer->mSourceRect.size.vert;
            uint32_t dst_hori = layer->mTargetRect.size.hori;
            uint32_t dst_vert = layer->mTargetRect.size.vert;
            data.frame[i].layer[idx].crop_width = src_hori;
            data.frame[i].layer[idx].crop_height = src_vert;
            data.frame[i].layer[idx].window_width = dst_hori;
            data.frame[i].layer[idx].window_height = dst_vert;

            // Src layer crop size is used when calculating read bandwidth.
            // Crop coordinates should be aligned in multiples of 16.
            pixelcount = (ALIGN(layer->mSourceRect.pos.hori + src_hori, 16) -
                          ALIGN_DOWN(layer->mSourceRect.pos.hori, 16)) *
                         (ALIGN(layer->mSourceRect.pos.vert + src_vert, 16) -
                          ALIGN_DOWN(layer->mSourceRect.pos.vert, 16));

            bpp = halfmt_bpp(layer->mPixFormat);
            planecount = halfmt_plane_count(layer->mPixFormat);
            equiv_fmt = find_format_equivalent(layer->mPixFormat);

            if (equiv_fmt == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC ||
                equiv_fmt == HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC)
                data.frame[i].layer[idx].layer_attr |= G2D_PERF_LAYER_SBWC;
            else if (layer->mAttribute & AcrylicCanvas::ATTR_COMPRESSED) {
                if (planecount == 1)
                    data.frame[i].layer[idx].layer_attr |= G2D_PERF_LAYER_RGB_AFBC;
                else
                    data.frame[i].layer[idx].layer_attr |= G2D_PERF_LAYER_YUV_AFBC;
            } else if (planecount == 2)
                data.frame[i].layer[idx].layer_attr |= G2D_PERF_LAYER_YUV2P;

            // src_yuv420_8b is used when calculating write bandwidth
            if (bpp == 12) src_yuv420_8b = true;

            layer_bw = pixelcount * bpp;
            // Below is checking if scaling is involved.
            // Comparisons are replaced by additions to avoid branches.
            if (!!(layer->mTransform & HAL_TRANSFORM_ROT_90)) {
                src_rotate = true;
                data.frame[i].layer[idx].layer_attr |= G2D_PERF_LAYER_ROTATE;

                is_scaling = src_hori - dst_vert;
                is_scaling += src_vert - dst_hori;
            } else {
                is_scaling = src_hori - dst_hori;
                is_scaling += src_vert - dst_vert;
            }
            // Weight to the bandwidth when scaling is involved is 1.125.
            // It is multiplied by 16 to avoid multiplication with a real number.
            // We also get benefit from shift instead of multiplication.
            if (is_scaling == 0) {
                layer_bw <<= 4; // layer_bw * 16
            } else {
                layer_bw = (layer_bw << 4) + (layer_bw << 1); // layer_bw * 18
            }

            bandwidth += layer_bw;
            ALOGD_TEST("        LAYER[%d]: BW %llu FMT %#x(%u) (%dx%d)@(%dx%d)on(%dx%d) --> (%dx%d)@(%dx%d) TRFM %#x",
                    idx, static_cast<unsigned long long>(layer_bw), layer->mPixFormat, bpp,
                    layer->mSourceRect.size.hori, layer->mSourceRect.size.vert,
                    layer->mSourceRect.pos.hori, layer->mSourceRect.pos.vert,
                    layer->mSourceDimension.hori, layer->mSourceDimension.vert,
                    layer->mTargetRect.size.hori, layer->mTargetRect.size.vert,
                    layer->mTargetRect.pos.hori, layer->mTargetRect.pos.vert, layer->mTransform);
        }

        bandwidth *= frame->mFrameRate;
        bandwidth >>= 17; // divide by 16(weight), 8(bpp) and 1024(kilobyte)

        data.frame[i].bandwidth_read = static_cast<uint32_t>(bandwidth);

        bpp = halfmt_bpp(frame->mTargetPixFormat);
        bandwidth = frame->mTargetDimension.hori * frame->mTargetDimension.vert;
        bandwidth *= frame->mFrameRate * bpp;

        // When src rotation is involved, src format includes yuv420(8bit-depth)
        // and dst format is yuv420(8bit-depth), weight to the write bandwidth is 2.
        // RSH 12 : bw * 2 / (bits_per_byte * kilobyte)
        // RHS 13 : bw * 1 / (bits_per_byte * kilobyte)
        bandwidth >>= ((bpp == 12) && src_yuv420_8b && src_rotate) ? 12 : 13;
        data.frame[i].bandwidth_write = static_cast<uint32_t>(bandwidth);

        if (frame->mHasBackgroundLayer)
            data.frame[i].frame_attr |= G2D_PERF_FRAME_SOLIDCOLORFILL;

        data.frame[i].num_layers = frame->getLayerCount();
        data.frame[i].target_pixelcount = frame->mTargetDimension.vert * frame->mTargetDimension.hori;
        data.frame[i].frame_rate = frame->mFrameRate;

        ALOGD_TEST("    FRAME[%d]: BW:(%u, %u) Layercount %d, Framerate %d, Target %dx%d, FMT %#x Background? %d",
            i, data.frame[i].bandwidth_read, data.frame[i].bandwidth_write, data.frame[i].num_layers, frame->mFrameRate,
            frame->mTargetDimension.hori, frame->mTargetDimension.vert, frame->mTargetPixFormat,
            frame->mHasBackgroundLayer);
    }

    data.num_frame = request->getFrameCount();

    if (mDev.ioctl(G2D_IOC_PERFORMANCE, &data) < 0) {
        ALOGERR("Failed to request performance");
        return false;
    }

    return true;
}

int AcrylicCompositorG2D::prioritize(int priority)
{
    static int32_t g2d_priorities[] = {
            G2D_LOW_PRIORITY,     // 0
            G2D_MEDIUM_PRIORITY,  // 1
            G2D_HIGH_PRIORITY,    // 2
    };

    if (priority == mPriority)
        return 0;

    if (Acrylic::prioritize(priority) < 0)
        return -1;

    int32_t arg;

    if (priority > 2)
        arg = G2D_HIGHEST_PRIORITY;
    else if (priority < 0)
        arg = G2D_DEFAULT_PRIORITY;
    else
        arg = g2d_priorities[priority];

    if (mDev.ioctl(G2D_IOC_PRIORITY, &arg) < 0) {
        if (errno != EBUSY) {
            ALOGERR("Failed to set priority on a context of G2D");
            return -1;
        }

        ALOGD("G2D Driver returned EBUSY but the priority of %d(%d) is successfully applied", priority, arg);
        return 1;
    }

    ALOGD_TEST("Applied the priority of %d(%d) successfully", priority, arg);

    mPriority = priority;

    return 0;
}
