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
 *  Subband processing consists of:
 *  inverse quantisation (defined in a separate file),
 *  predictor coefficient update (Pole and Zero Coeff update),
 *  predictor filtering.
 *
 *----------------------------------------------------------------------------*/

#ifndef SUBBANDFUNCTIONS_H
#define SUBBANDFUNCTIONS_H
#ifdef _GCC
#pragma GCC visibility push(hidden)
#endif

#include "AptxParameters.h"

XBT_INLINE_ void updatePredictorPoleCoefficients(
    const int32_t invQ, const int32_t prevZfiltOutput,
    PoleCoeff_data* PoleCoeffDataPt) {
  int32_t adaptSum;
  int32_t sgnP[3];
  int32_t newCoeffs[2];
  int32_t Bacc;
  int32_t acc;
  int32_t acc2;
  int32_t tmp3_round0;
  int16_t tmp2_round0;
  int16_t tmp_round0;
  /* Various constants in various Q formats */
  const int32_t oneQ22 = 4194304L;
  const int32_t minusOneQ22 = -4194304L;
  const int32_t pointFiveQ21 = 1048576L;
  const int32_t minusPointFiveQ21 = -1048576L;
  const int32_t pointSevenFiveQ22 = 3145728L;
  const int32_t minusPointSevenFiveQ22 = -3145728L;
  const int32_t oneMinusTwoPowerMinusFourQ22 = 3932160L;

  /* Symbolic indices for the pole coefficient arrays. Here we are using a1
   * to represent the first pole filter coefficient and a2 the second. This
   * seems to be common ADPCM terminology. */
  enum { a1 = 0, a2 = 1 };

  /* Symbolic indices for the sgn array (k, k-1 and k-2 respectively) */
  enum { k = 0, k_1 = 1, k_2 = 2 };

  /* Form the sum of the inverse quantiser and previous zero filter values */
  adaptSum = invQ + prevZfiltOutput;
  adaptSum = ssat24(adaptSum);

  /* Form the sgn of the sum just formed (note +1 and -1 are Q22) */
  if (adaptSum < 0L) {
    sgnP[k] = minusOneQ22;
    sgnP[k_1] = -(((int32_t)PoleCoeffDataPt->m_poleAdaptDelayLine.s16.l) << 22);
    sgnP[k_2] = -(((int32_t)PoleCoeffDataPt->m_poleAdaptDelayLine.s16.h) << 22);
    PoleCoeffDataPt->m_poleAdaptDelayLine.s16.h =
        PoleCoeffDataPt->m_poleAdaptDelayLine.s16.l;
    PoleCoeffDataPt->m_poleAdaptDelayLine.s16.l = -1;
  }
  if (adaptSum == 0L) {
    sgnP[k] = 0L;
    sgnP[k_1] = 0L;
    sgnP[k_2] = 0L;
    PoleCoeffDataPt->m_poleAdaptDelayLine.s16.h =
        PoleCoeffDataPt->m_poleAdaptDelayLine.s16.l;
    PoleCoeffDataPt->m_poleAdaptDelayLine.s16.l = 1;
  }
  if (adaptSum > 0L) {
    sgnP[k] = oneQ22;
    sgnP[k_1] = ((int32_t)PoleCoeffDataPt->m_poleAdaptDelayLine.s16.l) << 22;
    sgnP[k_2] = ((int32_t)PoleCoeffDataPt->m_poleAdaptDelayLine.s16.h) << 22;
    PoleCoeffDataPt->m_poleAdaptDelayLine.s16.h =
        PoleCoeffDataPt->m_poleAdaptDelayLine.s16.l;
    PoleCoeffDataPt->m_poleAdaptDelayLine.s16.l = 1;
  }

  /* Clear the accumulator and form -a1(k) * sgn(p(k))sgn(p(k-1)) in Q21. Clip
   * it to +/- 0.5 (Q21) so that we can take f(a1) = 4 * a1. This is a partial
   * result for the new a2 */
  acc = 0;
  acc -= PoleCoeffDataPt->m_poleCoeff[a1] * (sgnP[k_1] >> 22);

  tmp3_round0 = acc & 0x3L;

  acc += 0x001;
  acc >>= 1;
  if (tmp3_round0 == 0x001L) {
    acc--;
  }

  newCoeffs[a2] = acc;

  if (newCoeffs[a2] < minusPointFiveQ21) {
    newCoeffs[a2] = minusPointFiveQ21;
  }
  if (newCoeffs[a2] > pointFiveQ21) {
    newCoeffs[a2] = pointFiveQ21;
  }

  /* Load the accumulator with sgn(p(k))sgn(p(k-2)) right-shifted by 3. The
   * 3-position shift is to multiply it by 0.25 and convert from Q22 to Q21. */
  Bacc = (sgnP[k_2] >> 3);
  /* Add the current a2 update value to the accumulator (Q21) */
  Bacc += newCoeffs[a2];
  /* Shift the accumulator right by 4 positions.
   * Right 7 places to multiply by 2^(-7)
   * Left 2 places to scale by 4 (0.25A + B -> A + 4B)
   * Left 1 place to convert from Q21 to Q22 */
  Bacc >>= 4;
  /* Add a2(k-1) * (1 - 2^(-7)) to the accumulator. Note that the constant is
   * expressed as Q23, hence the product is Q22. Get the accumulator value
   * back out. */
  acc2 = PoleCoeffDataPt->m_poleCoeff[a2] << 8;
  acc2 -= PoleCoeffDataPt->m_poleCoeff[a2] << 1;
  Bacc = (int32_t)((uint32_t)Bacc << 8);
  Bacc += acc2;

  tmp2_round0 = (int16_t)Bacc & 0x01FFL;

  Bacc += 0x0080L;
  Bacc >>= 8;

  if (tmp2_round0 == 0x0080L) {
    Bacc--;
  }

  newCoeffs[a2] = Bacc;

  /* Clip the new a2(k) value to +/- 0.75 (Q22) */
  if (newCoeffs[a2] < minusPointSevenFiveQ22) {
    newCoeffs[a2] = minusPointSevenFiveQ22;
  }
  if (newCoeffs[a2] > pointSevenFiveQ22) {
    newCoeffs[a2] = pointSevenFiveQ22;
  }
  PoleCoeffDataPt->m_poleCoeff[a2] = newCoeffs[a2];

  /* Form sgn(p(k))sgn(p(k-1)) * (3 * 2^(-8)). The constant is Q23, hence the
   * product is Q22. */
  /* Add a1(k-1) * (1 - 2^(-8)) to the accumulator. The constant is Q23, hence
   * the product is Q22. Get the value from the accumulator. */
  acc2 = PoleCoeffDataPt->m_poleCoeff[a1] << 8;
  acc2 -= PoleCoeffDataPt->m_poleCoeff[a1];
  acc2 += (sgnP[k_1] << 2);
  acc2 -= (sgnP[k_1]);

  tmp_round0 = (int16_t)acc2 & 0x01FF;

  acc2 += 0x0080;
  acc = (acc2 >> 8);
  if (tmp_round0 == 0x0080) {
    acc--;
  }

  newCoeffs[a1] = acc;

  /* Clip the new value of a1(k) to +/- (1 - 2^4 - a2(k)). The constant 1 -
   * 2^4 is expressed in Q22 format (as is a1 and a2) */
  if (newCoeffs[a1] < (newCoeffs[a2] - oneMinusTwoPowerMinusFourQ22)) {
    newCoeffs[a1] = newCoeffs[a2] - oneMinusTwoPowerMinusFourQ22;
  }
  if (newCoeffs[a1] > (oneMinusTwoPowerMinusFourQ22 - newCoeffs[a2])) {
    newCoeffs[a1] = oneMinusTwoPowerMinusFourQ22 - newCoeffs[a2];
  }

  PoleCoeffDataPt->m_poleCoeff[a1] = newCoeffs[a1];
}

#ifdef _GCC
#pragma GCC visibility pop
#endif
#endif  // SUBBANDFUNCTIONS_H
