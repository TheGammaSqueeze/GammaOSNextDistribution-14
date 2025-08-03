/**
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
/*------------------------------------------------------------------------------
 *
 *  All table definitions used for the quantizer.
 *
 *----------------------------------------------------------------------------*/

#ifndef APTXTABLES_H
#define APTXTABLES_H

#include "AptxParameters.h"

/* Quantisation threshold, logDelta increment and dither tables for 2-bit codes
 */
static const int32_t dq2bit16_sl1[3] = {
    -194080,
    194080,
    890562,
};

static const int32_t q2incr16[3] = {
    0,
    -33,
    136,
};

static const int32_t dq2dith16_sf1[3] = {
    194080,
    194080,
    502402,
};

static const int32_t dq2mLamb16[2] = {
    0,
    -77081,
};

/* Quantisation threshold, logDelta increment and dither tables for 3-bit codes
 */
static const int32_t dq3bit16_sl1[5] = {
    -163006, 163006, 542708, 1120554, 2669238,
};

static const int32_t q3incr16[5] = {
    0, -8, 33, 95, 262,
};

static const int32_t dq3dith16_sf1[5] = {
    163006, 163006, 216698, 361148, 1187538,
};

static const int32_t dq3mLamb16[4] = {
    0,
    -13423,
    -36113,
    -206598,
};

/* Quantisation threshold, logDelta increment and dither tables for 4-bit codes
 */
static const int32_t dq4bit16_sl1[9] = {
    -89806, 89806, 278502, 494338, 759442, 1113112, 1652322, 2720256, 5190186,
};

static const int32_t q4incr16[9] = {
    0, -14, 6, 29, 58, 96, 154, 270, 521,
};

static const int32_t dq4dith16_sf1[9] = {
    89806, 89806, 98890, 116946, 148158, 205512, 333698, 734236, 1735696,
};

static const int32_t dq4mLamb16[8] = {
    0, -2271, -4514, -7803, -14339, -32047, -100135, -250365,
};

/* Quantisation threshold, logDelta increment and dither tables for 7-bit codes
 */
static const int32_t dq7bit16_sl1[65] = {
    -9948,   9948,    29860,   49808,   69822,   89926,   110144,  130502,
    151026,  171738,  192666,  213832,  235264,  256982,  279014,  301384,
    324118,  347244,  370790,  394782,  419250,  444226,  469742,  495832,
    522536,  549890,  577936,  606720,  636290,  666700,  698006,  730270,
    763562,  797958,  833538,  870398,  908640,  948376,  989740,  1032874,
    1077948, 1125150, 1174700, 1226850, 1281900, 1340196, 1402156, 1468282,
    1539182, 1615610, 1698514, 1789098, 1888944, 2000168, 2125700, 2269750,
    2438670, 2642660, 2899462, 3243240, 3746078, 4535138, 5664098, 7102424,
    8897462,
};

static const int32_t q7incr16[65] = {
    0,   -21, -19, -17, -15, -12, -10, -8,  -6,  -4,  -1,  1,   3,
    6,   8,   10,  13,  15,  18,  20,  23,  26,  29,  31,  34,  37,
    40,  43,  47,  50,  53,  57,  60,  64,  68,  72,  76,  80,  85,
    89,  94,  99,  105, 110, 116, 123, 129, 136, 144, 152, 161, 171,
    182, 194, 207, 223, 241, 263, 291, 328, 382, 467, 522, 522, 522,
};

static const int32_t dq7dith16_sf1[65] = {
    9948,   9948,    9962,  9988,   10026,  10078,  10142,  10218,  10306,
    10408,  10520,   10646, 10784,  10934,  11098,  11274,  11462,  11664,
    11880,  12112,   12358, 12618,  12898,  13194,  13510,  13844,  14202,
    14582,  14988,   15422, 15884,  16380,  16912,  17484,  18098,  18762,
    19480,  20258,   21106, 22030,  23044,  24158,  25390,  26760,  28290,
    30008,  31954,   34172, 36728,  39700,  43202,  47382,  52462,  58762,
    66770,  77280,   91642, 112348, 144452, 199326, 303512, 485546, 643414,
    794914, 1000124,
};

static const int32_t dq7mLamb16[65] = {
    0,      -4,     -7,     -10,    -13,   -16,   -19,   -22,   -26,    -28,
    -32,    -35,    -38,    -41,    -44,   -47,   -51,   -54,   -58,    -62,
    -65,    -70,    -74,    -79,    -84,   -90,   -95,   -102,  -109,   -116,
    -124,   -133,   -143,   -154,   -166,  -180,  -195,  -212,  -231,   -254,
    -279,   -308,   -343,   -383,   -430,  -487,  -555,  -639,  -743,   -876,
    -1045,  -1270,  -1575,  -2002,  -2628, -3591, -5177, -8026, -13719, -26047,
    -45509, -39467, -37875, -51303, 0,
};

/* Array of structures containing subband parameters. */
static const SubbandParameters subbandParameters[NUMSUBBANDS] = {
    /* LL band */
    {0, dq7bit16_sl1, 0, dq7dith16_sf1, dq7mLamb16, q7incr16, 7, (18 * 256) - 1,
     -20, 24},

    /* LH band */
    {0, dq4bit16_sl1, 0, dq4dith16_sf1, dq4mLamb16, q4incr16, 4, (21 * 256) - 1,
     -23, 12},

    /* HL band */
    {0, dq2bit16_sl1, 0, dq2dith16_sf1, dq2mLamb16, q2incr16, 2, (23 * 256) - 1,
     -25, 6},

    /* HH band */
    {0, dq3bit16_sl1, 0, dq3dith16_sf1, dq3mLamb16, q3incr16, 3, (22 * 256) - 1,
     -24, 12}};

#endif  // APTXTABLES_H
