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

#include "Quantiser.h"

XBT_INLINE_ int32_t BsearchLL(const int32_t absDiffSignalShifted,
                              const int32_t delta,
                              const int32_t* dqbitTablePrt) {
  int32_t qCode = 0;
  reg64_t tmp_acc;
  int32_t tmp = 0;
  int32_t lc_delta = delta << 8;

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[128];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode = 128;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 64];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 64;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 32];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 32;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 16];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 16;
  }
  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 8];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 8;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 4];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 4;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 2];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 2;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 1];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode++;
  }

  return (qCode);
}

XBT_INLINE_ int32_t BsearchHL(const int32_t absDiffSignalShifted,
                              const int32_t delta,
                              const int32_t* dqbitTablePrt) {
  int32_t qCode = 0;
  reg64_t tmp_acc;
  int32_t tmp = 0;
  int32_t lc_delta = delta << 8;

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[4];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode = 4;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 2];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 2;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 1];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode++;
  }

  return (qCode);
}

XBT_INLINE_ int32_t BsearchHH(const int32_t absDiffSignalShifted,
                              const int32_t delta,
                              const int32_t* dqbitTablePrt) {
  int32_t qCode = 0;
  reg64_t tmp_acc;
  int32_t tmp = 0;
  int32_t lc_delta = delta << 8;

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[8];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode = 8;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 4];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 4;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 2];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 2;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 1];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode++;
  }

  return (qCode);
}

void quantiseDifference_HDHL(const int32_t diffSignal, const int32_t ditherVal,
                             const int32_t delta, Quantiser_data* qdata_pt) {
  int32_t absDiffSignal = 0;
  int32_t absDiffSignalShifted = 0;
  int32_t index = 0;
  int32_t dithSquared = 0;
  int32_t minusLambdaD = 0;
  int32_t acc = 0;
  int32_t threshDiff = 0;
  reg64_t tmp_acc;
  reg64_t tmp_reg64;
  int32_t tmp_accL = 0;
  int32_t tmp_qCode = 0;
  int32_t tmp_altQcode = 0;
  uint32_t tmp_round0 = 0;
  int32_t _delta = 0;

  /* Form the absolute value of the difference signal and maintain a version
   * that is right-shifted 4 places for delta scaling. */
  absDiffSignal = -diffSignal;
  if (diffSignal >= 0) {
    absDiffSignal = diffSignal;
  }
  absDiffSignal = ssat24(absDiffSignal);
  absDiffSignalShifted = absDiffSignal >> deltaScale;

  /* Binary search for the quantised code. This search terminates with the
   * table index of the LARGEST threshold table value for which
   * absDiffSignalShifted >= (delta * threshold)
   */
  index =
      BsearchHL(absDiffSignalShifted, delta, qdata_pt->thresholdTablePtr_sl1);

  /* We actually wanted the SMALLEST magnitude quantised code for which
   * absDiffSignalShifted < (delta * threshold)
   * i.e. the code with the next highest magnitude than the one we actually
   * found. We could add +1 to the code magnitude to do this, but we need to
   * subtract 1 from the code magnitude to compensate for the "phantom
   * element" at the base of the quantisation table. These two effects cancel
   * out, so we leave the value of code alone. However, we need to form code+1
   * to get the proper index into the both the threshold and dither tables,
   * since we must skip over the phantom element at the base. */
  qdata_pt->qCode = index;

  /* Square the dither and get the value back from the ALU
   * (saturated/rounded). */
  tmp_acc.s64 = ((int64_t)ditherVal * (int64_t)ditherVal);

  acc = tmp_acc.s32.h;
  tmp_round0 = (uint32_t)acc << 8;

  acc = (acc >> 6) + 1;
  acc >>= 1;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }
  acc = ssat24(acc);

  dithSquared = acc;

  /* Form the negative difference of the dither values at index and index-1.
   * Load the accumulator with this value divided by 2. Ensure saturation is
   * applied to the difference calculation. */
  minusLambdaD = qdata_pt->minusLambdaDTable[index];

  tmp_accL = (1 << 23) - dithSquared;
  tmp_acc.s64 = (int64_t)tmp_accL * minusLambdaD;

  tmp_round0 = tmp_acc.s32.l << 8;

  acc = (tmp_acc.u32.l >> 22) | (tmp_acc.s32.h << 10);
  acc++;
  acc >>= 1;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }

  /* Add the threshold table values at index and index-1 to the accumulated
   * value. */
  acc += qdata_pt->thresholdTablePtr_sl1[index + 1] >> 1;
  //// worst case value for acc = 0x000d3e08 + 0x43E1DB = 511FE3
  acc += qdata_pt->thresholdTablePtr_sl1[index] >> 1;
  //// worst case value for acc = 0x511FE3 + 0x362FEC = 874FCF

  // saturation required
  acc = ssat24(acc);

  /* Form the threshold table difference at index and index-1. Ensure
   * saturation is applied to the difference calculation. */
  threshDiff = qdata_pt->thresholdTablePtr_sl1[index + 1] -
               qdata_pt->thresholdTablePtr_sl1[index];

  /* Based on the sign of the difference signal, either add or subtract the
   * threshold table difference from the accumulated value. Recover the final
   * accumulated value (saturated/rounded) */
  if (diffSignal < 0) {
    threshDiff = -threshDiff;
  }
  tmp_reg64.s64 = ((int64_t)ditherVal * (int64_t)threshDiff);

  tmp_reg64.s32.h += acc;
  acc = tmp_reg64.s32.h;

  if (tmp_reg64.u32.l >= 0x80000000) {
    acc++;
  }
  tmp_round0 = (tmp_reg64.u32.l >> 1) | (tmp_reg64.s32.h << 31);

  acc = ssat24(acc);

  if (tmp_round0 == 0x40000000L) {
    acc--;
  }
  _delta = -delta << 8;

  acc = (int32_t)((uint32_t)acc << 4);

  /* Form (absDiffSignal * 0.125) - (acc * delta), which is the final distance
   * signal used to determine if dithering alters the quantised code value or
   * not. */
  // worst case value for delta is 0x7d400
  tmp_reg64.s64 = ((int64_t)acc * (int64_t)_delta);
  tmp_reg64.s32.h += absDiffSignal;
  tmp_round0 = (tmp_reg64.u32.l >> 4) | (tmp_reg64.s32.h << 28);
  acc = tmp_reg64.s32.h + (1 << 2);
  acc >>= 3;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }

  tmp_qCode = qdata_pt->qCode;
  tmp_altQcode = tmp_qCode - 1;
  /* Check the sign of the distance penalty. Get the sign from the
   * full-precision accumulator, as done in the Kalimba code. */
  if (tmp_reg64.s32.h < 0) {
    /* The distance is -ve. The optimum code needs decremented by 1 and the
     * alternative code is 1 greater than this. Get the rounded version of the
     * -ve distance penalty and negate this (form distance magnitude) before
     *  writing the value out */
    tmp_qCode = tmp_altQcode;
    tmp_altQcode++;
    acc = -acc;
  }

  qdata_pt->distPenalty = acc;
  /* If the difference signal is negative, bitwise invert the code (restores
   * sign to the magnitude). */
  if (diffSignal < 0) {
    tmp_qCode = ~tmp_qCode;
    tmp_altQcode = ~tmp_altQcode;
  }
  qdata_pt->altQcode = tmp_altQcode;
  qdata_pt->qCode = tmp_qCode;
}

void quantiseDifference_HDHH(const int32_t diffSignal, const int32_t ditherVal,
                             const int32_t delta, Quantiser_data* qdata_pt) {
  int32_t absDiffSignal;
  int32_t absDiffSignalShifted;
  int32_t index;
  int32_t dithSquared;
  int32_t minusLambdaD;
  int32_t acc;
  int32_t threshDiff;
  reg64_t tmp_acc;
  reg64_t tmp_reg64;
  int32_t tmp_accL;
  int32_t tmp_qCode;
  int32_t tmp_altQcode;
  uint32_t tmp_round0;
  int32_t _delta;

  /* Form the absolute value of the difference signal and maintain a version
   * that is right-shifted 4 places for delta scaling. */
  absDiffSignal = -diffSignal;
  if (diffSignal >= 0) {
    absDiffSignal = diffSignal;
  }
  absDiffSignal = ssat24(absDiffSignal);
  absDiffSignalShifted = absDiffSignal >> deltaScale;

  /* Binary search for the quantised code. This search terminates with the
   * table index of the LARGEST threshold table value for which
   * absDiffSignalShifted >= (delta * threshold)
   */
  index =
      BsearchHH(absDiffSignalShifted, delta, qdata_pt->thresholdTablePtr_sl1);

  /* We actually wanted the SMALLEST magnitude quantised code for which
   * absDiffSignalShifted < (delta * threshold)
   * i.e. the code with the next highest magnitude than the one we actually
   * found. We could add +1 to the code magnitude to do this, but we need to
   * subtract 1 from the code magnitude to compensate for the "phantom
   * element" at the base of the quantisation table. These two effects cancel
   * out, so we leave the value of code alone. However, we need to form code+1
   * to get the proper index into the both the threshold and dither tables,
   * since we must skip over the phantom element at the base. */
  qdata_pt->qCode = index;

  /* Square the dither and get the value back from the ALU
   * (saturated/rounded). */
  tmp_acc.s64 = ((int64_t)ditherVal * (int64_t)ditherVal);

  acc = tmp_acc.s32.h;
  tmp_round0 = (uint32_t)acc << 8;

  acc = (acc >> 6) + 1;
  acc >>= 1;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }

  acc = ssat24(acc);

  dithSquared = acc;

  /* Form the negative difference of the dither values at index and index-1.
   * Load the accumulator with this value divided by 2. Ensure saturation is
   * applied to the difference calculation. */
  minusLambdaD = qdata_pt->minusLambdaDTable[index];

  tmp_accL = (1 << 23) - dithSquared;
  tmp_acc.s64 = (int64_t)tmp_accL * minusLambdaD;

  tmp_round0 = tmp_acc.s32.l << 8;

  acc = (tmp_acc.u32.l >> 22) | (tmp_acc.s32.h << 10);
  acc++;
  acc >>= 1;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }

  /* Add the threshold table values at index and index-1 to the accumulated
   * value. */
  acc += qdata_pt->thresholdTablePtr_sl1[index + 1] >> 1;
  //// worst case value for acc = 0x000d3e08 + 0x43E1DB = 511FE3
  acc += qdata_pt->thresholdTablePtr_sl1[index] >> 1;
  //// worst case value for acc = 0x511FE3 + 0x362FEC = 874FCF

  // saturation required
  acc = ssat24(acc);

  /* Form the threshold table difference at index and index-1. Ensure
   * saturation is applied to the difference calculation. */
  threshDiff = qdata_pt->thresholdTablePtr_sl1[index + 1] -
               qdata_pt->thresholdTablePtr_sl1[index];

  /* Based on the sign of the difference signal, either add or subtract the
   * threshold table difference from the accumulated value. Recover the final
   * accumulated value (saturated/rounded) */
  if (diffSignal < 0) {
    threshDiff = -threshDiff;
  }
  tmp_reg64.s64 = ((int64_t)ditherVal * (int64_t)threshDiff);
  tmp_reg64.s32.h += acc;
  acc = tmp_reg64.s32.h;

  if (tmp_reg64.u32.l >= 0x80000000) {
    acc++;
  }
  tmp_round0 = (tmp_reg64.u32.l >> 1) | (tmp_reg64.s32.h << 31);

  acc = ssat24(acc);

  if (tmp_round0 == 0x40000000L) {
    acc--;
  }
  _delta = -delta << 8;

  acc = (int32_t)((uint32_t)acc << 4);

  /* Form (absDiffSignal * 0.125) - (acc * delta), which is the final distance
   * signal used to determine if dithering alters the quantised code value or
   * not. */
  // worst case value for delta is 0x7d400
  tmp_reg64.s64 = ((int64_t)acc * (int64_t)_delta);
  tmp_reg64.s32.h += absDiffSignal;
  tmp_round0 = (tmp_reg64.u32.l >> 4) | (tmp_reg64.s32.h << 28);
  acc = tmp_reg64.s32.h + (1 << 2);
  acc >>= 3;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }

  tmp_qCode = qdata_pt->qCode;
  tmp_altQcode = tmp_qCode - 1;
  /* Check the sign of the distance penalty. Get the sign from the
   * full-precision accumulator, as done in the Kalimba code. */
  if (tmp_reg64.s32.h < 0) {
    /* The distance is -ve. The optimum code needs decremented by 1 and the
     * alternative code is 1 greater than this. Get the rounded version of the
     * -ve distance penalty and negate this (form distance magnitude) before
     *  writing the value out */
    tmp_qCode = tmp_altQcode;
    tmp_altQcode++;
    acc = -acc;
  }

  qdata_pt->distPenalty = acc;
  /* If the difference signal is negative, bitwise invert the code (restores
   * sign to the magnitude). */
  if (diffSignal < 0) {
    tmp_qCode = ~tmp_qCode;
    tmp_altQcode = ~tmp_altQcode;
  }
  qdata_pt->altQcode = tmp_altQcode;
  qdata_pt->qCode = tmp_qCode;
}

void quantiseDifference_HDLL(const int32_t diffSignal, const int32_t ditherVal,
                             const int32_t delta, Quantiser_data* qdata_pt) {
  int32_t absDiffSignal;
  int32_t absDiffSignalShifted;
  int32_t index;
  int32_t dithSquared;
  int32_t minusLambdaD;
  int32_t acc;
  int32_t threshDiff;
  reg64_t tmp_acc;
  reg64_t tmp_reg64;
  int32_t tmp_accL;
  int32_t tmp_qCode;
  int32_t tmp_altQcode;
  uint32_t tmp_round0;
  int32_t _delta;

  /* Form the absolute value of the difference signal and maintain a version
   * that is right-shifted 4 places for delta scaling. */
  absDiffSignal = -diffSignal;
  if (diffSignal >= 0) {
    absDiffSignal = diffSignal;
  }
  absDiffSignal = ssat24(absDiffSignal);
  absDiffSignalShifted = absDiffSignal >> deltaScale;

  /* Binary search for the quantised code. This search terminates with the
   * table index of the LARGEST threshold table value for which
   * absDiffSignalShifted >= (delta * threshold)
   */
  index =
      BsearchLL(absDiffSignalShifted, delta, qdata_pt->thresholdTablePtr_sl1);

  /* We actually wanted the SMALLEST magnitude quantised code for which
   * absDiffSignalShifted < (delta * threshold)
   * i.e. the code with the next highest magnitude than the one we actually
   * found. We could add +1 to the code magnitude to do this, but we need to
   * subtract 1 from the code magnitude to compensate for the "phantom
   * element" at the base of the quantisation table. These two effects cancel
   * out, so we leave the value of code alone. However, we need to form code+1
   * to get the proper index into the both the threshold and dither tables,
   * since we must skip over the phantom element at the base. */
  qdata_pt->qCode = index;

  /* Square the dither and get the value back from the ALU
   * (saturated/rounded). */

  tmp_acc.s64 = ((int64_t)ditherVal * (int64_t)ditherVal);

  acc = tmp_acc.s32.h;
  tmp_round0 = (uint32_t)acc << 8;

  acc = (acc >> 6) + 1;
  acc >>= 1;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }

  acc = ssat24(acc);

  dithSquared = acc;

  /* Form the negative difference of the dither values at index and index-1.
   * Load the accumulator with this value divided by 2. Ensure saturation is
   * applied to the difference calculation. */
  minusLambdaD = qdata_pt->minusLambdaDTable[index];

  tmp_accL = (1 << 23) - dithSquared;
  tmp_acc.s64 = (int64_t)tmp_accL * minusLambdaD;

  tmp_round0 = tmp_acc.s32.l << 8;

  acc = (tmp_acc.u32.l >> 22) | (tmp_acc.s32.h << 10);
  acc++;
  acc >>= 1;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }

  /* Add the threshold table values at index and index-1 to the accumulated
   * value. */

  acc += qdata_pt->thresholdTablePtr_sl1[index + 1] >> 1;
  //// worst case value for acc = 0x000d3e08 + 0x43E1DB = 511FE3
  acc += qdata_pt->thresholdTablePtr_sl1[index] >> 1;
  //// worst case value for acc = 0x511FE3 + 0x362FEC = 874FCF
  // saturation required
  acc = ssat24(acc);

  /* Form the threshold table difference at index and index-1. Ensure
   * saturation is applied to the difference calculation. */
  threshDiff = qdata_pt->thresholdTablePtr_sl1[index + 1] -
               qdata_pt->thresholdTablePtr_sl1[index];

  /* Based on the sign of the difference signal, either add or subtract the
   * threshold table difference from the accumulated value. Recover the final
   * accumulated value (saturated/rounded) */

  if (diffSignal < 0) {
    threshDiff = -threshDiff;
  }
  tmp_reg64.s64 = ((int64_t)ditherVal * (int64_t)threshDiff);
  tmp_reg64.s32.h += acc;
  acc = tmp_reg64.s32.h;

  if (tmp_reg64.u32.l >= 0x80000000) {
    acc++;
  }
  tmp_round0 = (tmp_reg64.u32.l >> 1) | (tmp_reg64.s32.h << 31);

  acc = ssat24(acc);

  if (tmp_round0 == 0x40000000L) {
    acc--;
  }
  _delta = -delta << 8;

  acc = (int32_t)((uint32_t)acc << 4);

  /* Form (absDiffSignal * 0.125) - (acc * delta), which is the final distance
   * signal used to determine if dithering alters the quantised code value or
   * not. */
  // worst case value for delta is 0x7d400

  tmp_reg64.s64 = ((int64_t)acc * (int64_t)_delta);
  tmp_reg64.s32.h += absDiffSignal;
  tmp_round0 = (tmp_reg64.u32.l >> 4) | (tmp_reg64.s32.h << 28);
  acc = tmp_reg64.s32.h + (1 << 2);
  acc >>= 3;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }

  tmp_qCode = qdata_pt->qCode;
  tmp_altQcode = tmp_qCode - 1;
  /* Check the sign of the distance penalty. Get the sign from the
   * full-precision accumulator, as done in the Kalimba code. */

  if (tmp_reg64.s32.h < 0) {
    /* The distance is -ve. The optimum code needs decremented by 1 and the
     * alternative code is 1 greater than this. Get the rounded version of the
     * -ve distance penalty and negate this (form distance magnitude) before
     *  writing the value out */
    tmp_qCode = tmp_altQcode;
    tmp_altQcode++;
    acc = -acc;
  }

  qdata_pt->distPenalty = acc;
  /* If the difference signal is negative, bitwise invert the code (restores
   * sign to the magnitude). */
  if (diffSignal < 0) {
    tmp_qCode = ~tmp_qCode;
    tmp_altQcode = ~tmp_altQcode;
  }
  qdata_pt->altQcode = tmp_altQcode;
  qdata_pt->qCode = tmp_qCode;
}

static int32_t BsearchLH(const int32_t absDiffSignalShifted,
                         const int32_t delta, const int32_t* dqbitTablePrt) {
  int32_t qCode;
  reg64_t tmp_acc;
  int32_t tmp;
  int32_t lc_delta = delta << 8;

  qCode = 0;

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[16];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode = 16;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 8];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 8;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 4];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 4;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 2];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode += 2;
  }

  tmp_acc.s64 = (int64_t)lc_delta * (int64_t)dqbitTablePrt[qCode + 1];
  tmp_acc.s32.h -= absDiffSignalShifted;
  tmp = tmp_acc.s32.h | (tmp_acc.u32.l >> 1);
  if (tmp <= 0) {
    qCode++;
  }

  return (qCode);
}

void quantiseDifference_HDLH(const int32_t diffSignal, const int32_t ditherVal,
                             const int32_t delta, Quantiser_data* qdata_pt) {
  int32_t absDiffSignal = 0;
  int32_t absDiffSignalShifted = 0;
  int32_t index = 0;
  int32_t dithSquared = 0;
  int32_t minusLambdaD = 0;
  int32_t acc = 0;
  int32_t threshDiff = 0;
  reg64_t tmp_acc;
  reg64_t tmp_reg64;
  int32_t tmp_accL = 0;
  int32_t tmp_qCode = 0;
  int32_t tmp_altQcode = 0;

  uint32_t tmp_round0 = 0;
  int32_t _delta = 0;

  /* Form the absolute value of the difference signal and maintain a version
   * that is right-shifted 4 places for delta scaling. */
  absDiffSignal = -diffSignal;
  if (diffSignal >= 0) {
    absDiffSignal = diffSignal;
  }
  absDiffSignal = ssat24(absDiffSignal);
  absDiffSignalShifted = absDiffSignal >> deltaScale;

  /* Binary search for the quantised code. This search terminates with the
   * table index of the LARGEST threshold table value for which
   * absDiffSignalShifted >= (delta * threshold)
   */

  /* first iteration */
  index =
      BsearchLH(absDiffSignalShifted, delta, qdata_pt->thresholdTablePtr_sl1);

  /* We actually wanted the SMALLEST magnitude quantised code for which
   * absDiffSignalShifted < (delta * threshold)
   * i.e. the code with the next highest magnitude than the one we actually
   * found. We could add +1 to the code magnitude to do this, but we need to
   * subtract 1 from the code magnitude to compensate for the "phantom
   * element" at the base of the quantisation table. These two effects cancel
   * out, so we leave the value of code alone. However, we need to form code+1
   * to get the proper index into the both the threshold and dither tables,
   * since we must skip over the phantom element at the base. */
  qdata_pt->qCode = index;

  /* Square the dither and get the value back from the ALU
   * (saturated/rounded). */

  tmp_reg64.s64 = ((int64_t)ditherVal * (int64_t)ditherVal);

  acc = tmp_reg64.s32.h;

  tmp_round0 = (uint32_t)acc << 8;

  acc = (acc >> 6) + 1;
  acc >>= 1;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }
  acc = ssat24(acc);

  dithSquared = acc;

  /* Form the negative difference of the dither values at index and index-1.
   * Load the accumulator with this value divided by 2. Ensure saturation is
   * applied to the difference calculation. */

  minusLambdaD = qdata_pt->minusLambdaDTable[index];

  tmp_accL = (1 << 23) - dithSquared;
  tmp_acc.s64 = (int64_t)tmp_accL * minusLambdaD;

  tmp_round0 = tmp_acc.s32.l << 8;

  acc = (int32_t)(tmp_acc.u32.l >> 22) | (tmp_acc.s32.h << 10);
  if (tmp_round0 == 0x40000000L) {
    acc -= 2;
  }
  acc++;

  /* Add the threshold table values at index and index-1 to the accumulated
   * value. */

  acc += qdata_pt->thresholdTablePtr_sl1[index + 1];
  //// worst case value for acc = 0x000d3e08 + 0x43E1DB = 511FE3
  acc += qdata_pt->thresholdTablePtr_sl1[index];
  acc >>= 1;

  // saturation required
  acc = ssat24(acc);

  /* Form the threshold table difference at index and index-1. Ensure
   * saturation is applied to the difference calculation. */
  threshDiff = qdata_pt->thresholdTablePtr_sl1[index + 1] -
               qdata_pt->thresholdTablePtr_sl1[index];

  /* Based on the sign of the difference signal, either add or subtract the
   * threshold table difference from the accumulated value. Recover the final
   * accumulated value (saturated/rounded) */

  if (diffSignal < 0) {
    threshDiff = -threshDiff;
  }
  tmp_reg64.s64 = ((int64_t)ditherVal * (int64_t)threshDiff);

  tmp_reg64.s32.h += acc;
  acc = tmp_reg64.s32.h;

  if (tmp_reg64.u32.l >= 0x80000000) {
    acc++;
  }
  tmp_round0 = (tmp_reg64.u32.l >> 1) | (tmp_reg64.s32.h << 31);

  acc = ssat24(acc);

  if (tmp_round0 == 0x40000000L) {
    acc--;
  }
  _delta = -delta << 8;

  acc = (int32_t)((uint32_t)acc << 4);

  /* Form (absDiffSignal * 0.125) - (acc * delta), which is the final distance
   * signal used to determine if dithering alters the quantised code value or
   * not. */
  // worst case value for delta is 0x7d400

  tmp_reg64.s64 = ((int64_t)acc * (int64_t)_delta);
  tmp_reg64.s32.h += absDiffSignal;
  tmp_round0 = (tmp_reg64.u32.l >> 4) | (tmp_reg64.s32.h << 28);
  acc = tmp_reg64.s32.h + (1 << 2);
  acc >>= 3;
  if (tmp_round0 == 0x40000000L) {
    acc--;
  }

  tmp_qCode = qdata_pt->qCode;
  tmp_altQcode = tmp_qCode - 1;
  /* Check the sign of the distance penalty. Get the sign from the
   * full-precision accumulator, as done in the Kalimba code. */

  if (tmp_reg64.s32.h < 0) {
    /* The distance is -ve. The optimum code needs decremented by 1 and the
     * alternative code is 1 greater than this. Get the rounded version of the
     * -ve distance penalty and negate this (form distance magnitude) before
     *  writing the value out */
    tmp_qCode = tmp_altQcode;
    tmp_altQcode++;
    acc = -acc;
  }

  qdata_pt->distPenalty = acc;
  /* If the difference signal is negative, bitwise invert the code (restores
   * sign to the magnitude). */
  if (diffSignal < 0) {
    tmp_qCode = ~tmp_qCode;
    tmp_altQcode = ~tmp_altQcode;
  }
  qdata_pt->altQcode = tmp_altQcode;
  qdata_pt->qCode = tmp_qCode;
}
