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
 *  This file includes the coefficient tables or the two convolution function
 *  It also includes the definition of Qmf_storage and the prototype of all
 *  necessary functions required to implement the QMF filtering.
 *
 *----------------------------------------------------------------------------*/

#ifndef QMF_H
#define QMF_H

#include "AptxParameters.h"

typedef struct {
  int16_t QmfL_buf[32];
  int16_t QmfH_buf[32];
  int32_t QmfLH_buf[32];
  int32_t QmfHL_buf[32];
  int32_t QmfLL_buf[32];
  int32_t QmfHH_buf[32];
  int32_t QmfI_pt;
  int32_t QmfO_pt;
} Qmf_storage;

/* Outer QMF filter for Enhanced aptX is a symmetrical 32-tap filter (16
 * different coefficients). The table in defined in QmfConv.c */
#ifndef _STDQMFOUTERCOEFF
static const int32_t Qmf_outerCoeffs[12] = {
    /* (C(1/30)C(3/28)), C(5/26), C(7/24) */
    0xFE6302DA,
    0xFFFFDA75,
    0x0000AA6A,
    /*  C(9/22), C(11/20), C(13/18), C(15/16) */
    0xFFFE273E,
    0x00041E95,
    0xFFF710B5,
    0x002AC12E,
    /*  C(17/14), C(19/12), (C(21/10)C(23/8)) */
    0x000AA328,
    0xFFFD8D1F,
    0x211E6BDB,
    /* (C(25/6)C(27/4)), (C(29/2)C(31/0)) */
    0x0DB7D8C5,
    0xFC7F02B0,
};
#else
static const int32_t Qmf_outerCoeffs[16] = {
    730,    -413,    -9611, 43626, -121026, 269973, -585547, 2801966,
    697128, -160481, 27611, 8478,  -10043,  3511,   688,     -897,
};
#endif

/* Each inner QMF filter for Enhanced aptX is a symmetrical 32-tap filter (16
 * different coefficients) */
static const int32_t Qmf_innerCoeffs[16] = {
    1033,   -584,    -13592, 61697, -171156, 381799, -828088, 3962579,
    985888, -226954, 39048,  11990, -14203,  4966,   973,     -1268,
};

void AsmQmfConvI(const int32_t* p1dl_buffPtr, const int32_t* p2dl_buffPtr,
                 const int32_t* coeffPtr, int32_t* filterOutputs);
void AsmQmfConvO(const int16_t* p1dl_buffPtr, const int16_t* p2dl_buffPtr,
                 const int32_t* coeffPtr, int32_t* convSumDiff);

XBT_INLINE_ void QmfAnalysisFilter(const int32_t pcm[4], Qmf_storage* Qmf_St,
                                   const int32_t predVals[4],
                                   int32_t* aqmfOutputs) {
  int32_t convSumDiff[4];
  int32_t filterOutputs[4];

  int32_t lc_QmfO_pt = (Qmf_St->QmfO_pt);
  int32_t lc_QmfI_pt = (Qmf_St->QmfI_pt);

  /* Symbolic constants to represent the first and second set out outer filter
   * outputs. */
  enum { FirstOuterOutputs = 0, SecondOuterOutputs = 1 };

  /* Load outer filter phase1 and phase2 delay lines with the first 2 PCM
   * samples. Convolve the filter and get the 2 convolution results. */
  Qmf_St->QmfL_buf[lc_QmfO_pt + 16] = (int16_t)pcm[FirstPcm];
  Qmf_St->QmfL_buf[lc_QmfO_pt] = (int16_t)pcm[FirstPcm];
  Qmf_St->QmfH_buf[lc_QmfO_pt + 16] = (int16_t)pcm[SecondPcm];
  Qmf_St->QmfH_buf[lc_QmfO_pt++] = (int16_t)pcm[SecondPcm];
  lc_QmfO_pt &= 0xF;

  AsmQmfConvO(&Qmf_St->QmfL_buf[lc_QmfO_pt + 15], &Qmf_St->QmfH_buf[lc_QmfO_pt],
              Qmf_outerCoeffs, &convSumDiff[0]);

  /* Load outer filter phase1 and phase2 delay lines with the second 2 PCM
   * samples. Convolve the filter and get the 2 convolution results. */
  Qmf_St->QmfL_buf[lc_QmfO_pt + 16] = (int16_t)pcm[ThirdPcm];
  Qmf_St->QmfL_buf[lc_QmfO_pt] = (int16_t)pcm[ThirdPcm];
  Qmf_St->QmfH_buf[lc_QmfO_pt + 16] = (int16_t)pcm[FourthPcm];
  Qmf_St->QmfH_buf[lc_QmfO_pt++] = (int16_t)pcm[FourthPcm];
  lc_QmfO_pt &= 0xF;

  AsmQmfConvO(&Qmf_St->QmfL_buf[lc_QmfO_pt + 15], &Qmf_St->QmfH_buf[lc_QmfO_pt],
              Qmf_outerCoeffs, &convSumDiff[1]);

  /* Load the first inner filter phase1 and phase2 delay lines with the 2
   * convolution sum (low-pass) outer filter outputs. Convolve the filter and
   * get the 2 convolution results. The first 2 analysis filter outputs are
   * the sum and difference values for the first inner filter convolutions. */
  Qmf_St->QmfLL_buf[lc_QmfI_pt + 16] = convSumDiff[0];
  Qmf_St->QmfLL_buf[lc_QmfI_pt] = convSumDiff[0];
  Qmf_St->QmfLH_buf[lc_QmfI_pt + 16] = convSumDiff[1];
  Qmf_St->QmfLH_buf[lc_QmfI_pt] = convSumDiff[1];

  AsmQmfConvI(&Qmf_St->QmfLL_buf[lc_QmfI_pt + 16],
              &Qmf_St->QmfLH_buf[lc_QmfI_pt + 1], &Qmf_innerCoeffs[0],
              &filterOutputs[LL]);

  /* Load the second inner filter phase1 and phase2 delay lines with the 2
   * convolution difference (high-pass) outer filter outputs. Convolve the
   * filter and get the 2 convolution results. The second 2 analysis filter
   * outputs are the sum and difference values for the second inner filter
   * convolutions. */
  Qmf_St->QmfHL_buf[lc_QmfI_pt + 16] = convSumDiff[2];
  Qmf_St->QmfHL_buf[lc_QmfI_pt] = convSumDiff[2];
  Qmf_St->QmfHH_buf[lc_QmfI_pt + 16] = convSumDiff[3];
  Qmf_St->QmfHH_buf[lc_QmfI_pt++] = convSumDiff[3];
  lc_QmfI_pt &= 0xF;

  AsmQmfConvI(&Qmf_St->QmfHL_buf[lc_QmfI_pt + 15],
              &Qmf_St->QmfHH_buf[lc_QmfI_pt], &Qmf_innerCoeffs[0],
              &filterOutputs[HL]);

  /* Subtracted the previous predicted value from the filter output on a
   * per-subband basis. Ensure these values are saturated, if necessary.
   * Manual unrolling */
  aqmfOutputs[LL] = filterOutputs[LL] - predVals[LL];
  aqmfOutputs[LL] = ssat24(aqmfOutputs[LL]);

  aqmfOutputs[LH] = filterOutputs[LH] - predVals[LH];
  aqmfOutputs[LH] = ssat24(aqmfOutputs[LH]);

  aqmfOutputs[HL] = filterOutputs[HL] - predVals[HL];
  aqmfOutputs[HL] = ssat24(aqmfOutputs[HL]);

  aqmfOutputs[HH] = filterOutputs[HH] - predVals[HH];
  aqmfOutputs[HH] = ssat24(aqmfOutputs[HH]);

  (Qmf_St->QmfO_pt) = lc_QmfO_pt;
  (Qmf_St->QmfI_pt) = lc_QmfI_pt;
}

#endif  // QMF_H
