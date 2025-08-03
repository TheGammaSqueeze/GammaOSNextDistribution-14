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

#ifndef SUBBANDFUNCTIONSCOMMON_H
#define SUBBANDFUNCTIONSCOMMON_H

enum reg64_reg { reg64_H = 1, reg64_L = 0 };

void processSubband(const int32_t qCode, const int32_t ditherVal,
                    Subband_data* SubbandDataPt, IQuantiser_data* iqDataPt);
void processSubbandLL(const int32_t qCode, const int32_t ditherVal,
                      Subband_data* SubbandDataPt, IQuantiser_data* iqDataPt);
void processSubbandHL(const int32_t qCode, const int32_t ditherVal,
                      Subband_data* SubbandDataPt, IQuantiser_data* iqDataPt);

/* Function to carry out inverse quantisation for LL, LH and HH subband types */
XBT_INLINE_ void invertQuantisation(const int32_t qCode,
                                    const int32_t ditherVal,
                                    IQuantiser_data* iqdata_pt) {
  int32_t invQ;
  int32_t index;
  int32_t acc;
  reg64_t tmp_r64;
  int64_t tmp_acc;
  int32_t tmp_accL;
  int32_t tmp_accH;
  uint32_t tmp_round0;
  uint32_t tmp_round1;

  unsigned u16t;
  /* log delta leak value (Q23) */
  const uint32_t logDeltaLeakVal = 0x7F6CL;

  /* Turn the quantised code back into an index into the threshold table. This
   * involves bitwise inversion of the code (if -ve) and adding 1 (phantom
   * element at table base). Then set invQ to be +/- the threshold value,
   * depending on the code sign. */
  index = qCode;
  if (qCode < 0) {
    index = (~index);
  }
  index = index + 1;
  invQ = iqdata_pt->thresholdTablePtr_sl1[index];
  if (qCode < 0) {
    invQ = -invQ;
  }

  /* Load invQ into the accumulator. Add the product of the dither value times
   * the indexed dither table value. Then get the result back from the
   * accumulator as an updated invQ. */
  tmp_r64.s64 = ((int64_t)ditherVal * iqdata_pt->ditherTablePtr_sf1[index]);
  tmp_r64.s32.h += invQ >> 1;

  acc = tmp_r64.s32.h;

  tmp_round1 = tmp_r64.s32.h & 0x00000001L;
  if (tmp_r64.u32.l >= 0x80000000) {
    acc++;
  }
  if (tmp_round1 == 0 && tmp_r64.s32.l == (int32_t)0x80000000L) {
    acc--;
  }
  acc = ssat24(acc);

  invQ = acc;

  /* Scale invQ by the current delta value. Left-shift the result (in the
   * accumulator) by 4 positions for the delta scaling. Get the updated invQ
   * back from the accumulator. */

  u16t = iqdata_pt->logDelta;
  tmp_acc = ((int64_t)invQ * iqdata_pt->delta);
  tmp_accL = u16t * logDeltaLeakVal;
  tmp_accH = iqdata_pt->incrTablePtr[index];
  acc = (int32_t)(tmp_acc >> (23 - deltaScale));
  invQ = ssat24(acc);

  /* Now update the value of logDelta. Load the accumulator with the index
   * value of the logDelta increment table. Add the product of the current
   * logDelta scaled by a leaky coefficient (16310 in Q14). Get the value back
   * from the accumulator. */
  tmp_accH += tmp_accL >> (32 - 17);

  acc = tmp_accH;

  tmp_r64.u32.l = ((uint32_t)tmp_accL << 17);
  tmp_r64.s32.h = tmp_accH;

  tmp_round0 = tmp_r64.u32.l;
  tmp_round1 = (int32_t)(tmp_r64.u64 >> 1);
  if (tmp_round0 >= 0x80000000L) {
    acc++;
  }
  if (tmp_round1 == 0x40000000L) {
    acc--;
  }

  /* Limit the updated logDelta between 0 and its subband-specific maximum. */
  if (acc < 0) {
    acc = 0;
  }
  if (acc > iqdata_pt->maxLogDelta) {
    acc = iqdata_pt->maxLogDelta;
  }

  iqdata_pt->logDelta = (uint16_t)acc;

  /* The updated value of delta is the logTable output (indexed by 5 bits from
   * the updated logDelta) shifted by a value involving the logDelta minimum
   * and the updated logDelta itself. */
  iqdata_pt->delta = iqdata_pt->iquantTableLogPtr[(acc >> 3) & 0x1f] >>
                     (22 - 25 - iqdata_pt->minLogDelta - (acc >> 8));

  iqdata_pt->invQ = invQ;
}

/* Function to carry out inverse quantisation for a HL subband type */
XBT_INLINE_ void invertQuantisationHL(const int32_t qCode,
                                      const int32_t ditherVal,
                                      IQuantiser_data* iqdata_pt) {
  int32_t invQ;
  int32_t index;
  int32_t acc;
  reg64_t tmp_r64;
  int64_t tmp_acc;
  int32_t tmp_accL;
  int32_t tmp_accH;
  uint32_t tmp_round0;
  uint32_t tmp_round1;

  unsigned u16t;
  /* log delta leak value (Q23) */
  const uint32_t logDeltaLeakVal = 0x7F6CL;

  /* Turn the quantised code back into an index into the threshold table. This
   * involves bitwise inversion of the code (if -ve) and adding 1 (phantom
   * element at table base). Then set invQ to be +/- the threshold value,
   * depending on the code sign. */
  index = qCode;
  if (qCode < 0) {
    index = (~index);
  }
  index = index + 1;
  invQ = iqdata_pt->thresholdTablePtr_sl1[index];
  if (qCode < 0) {
    invQ = -invQ;
  }

  /* Load invQ into the accumulator. Add the product of the dither value times
   * the indexed dither table value. Then get the result back from the
   * accumulator as an updated invQ. */
  tmp_r64.s64 = ((int64_t)ditherVal * iqdata_pt->ditherTablePtr_sf1[index]);
  tmp_r64.s32.h += invQ >> 1;

  acc = tmp_r64.s32.h;

  tmp_round1 = tmp_r64.s32.h & 0x00000001L;
  if (tmp_r64.u32.l >= 0x80000000) {
    acc++;
  }
  if (tmp_round1 == 0 && tmp_r64.u32.l == 0x80000000L) {
    acc--;
  }
  acc = ssat24(acc);

  invQ = acc;

  /* Scale invQ by the current delta value. Left-shift the result (in the
   * accumulator) by 4 positions for the delta scaling. Get the updated invQ
   * back from the accumulator. */
  u16t = iqdata_pt->logDelta;
  tmp_acc = ((int64_t)invQ * iqdata_pt->delta);
  tmp_accL = u16t * logDeltaLeakVal;
  tmp_accH = iqdata_pt->incrTablePtr[index];
  acc = (int32_t)(tmp_acc >> (23 - deltaScale));
  invQ = acc;

  /* Now update the value of logDelta. Load the accumulator with the index
   * value of the logDelta increment table. Add the product of the current
   * logDelta scaled by a leaky coefficient (16310 in Q14). Get the value back
   * from the accumulator. */
  tmp_accH += tmp_accL >> (32 - 17);

  acc = tmp_accH;

  tmp_r64.u32.l = ((uint32_t)tmp_accL << 17);
  tmp_r64.s32.h = tmp_accH;

  tmp_round0 = tmp_r64.u32.l;
  tmp_round1 = (int32_t)(tmp_r64.u64 >> 1);
  if (tmp_round0 >= 0x80000000L) {
    acc++;
  }
  if (tmp_round1 == 0x40000000L) {
    acc--;
  }

  /* Limit the updated logDelta between 0 and its subband-specific maximum. */
  if (acc < 0) {
    acc = 0;
  }
  if (acc > iqdata_pt->maxLogDelta) {
    acc = iqdata_pt->maxLogDelta;
  }

  iqdata_pt->logDelta = (uint16_t)acc;

  /* The updated value of delta is the logTable output (indexed by 5 bits from
   * the updated logDelta) shifted by a value involving the logDelta minimum
   * and the updated logDelta itself. */
  iqdata_pt->delta = iqdata_pt->iquantTableLogPtr[(acc >> 3) & 0x1f] >>
                     (22 - 25 - iqdata_pt->minLogDelta - (acc >> 8));

  iqdata_pt->invQ = invQ;
}

/* Function to carry out prediction ARMA filtering for the current subband
 * performPredictionFiltering should only be used for HH and LH subband! */
XBT_INLINE_ void performPredictionFiltering(const int32_t invQ,
                                            Subband_data* SubbandDataPt) {
  int32_t poleVal;
  int32_t acc;
  int64_t accL;
  uint32_t pointer;
  int32_t poleDelayLine;
  int32_t predVal;
  int32_t* zeroCoeffPt = SubbandDataPt->m_ZeroCoeffData.m_zeroCoeff;
  int32_t* poleCoeff = SubbandDataPt->m_PoleCoeffData.m_poleCoeff;
  int32_t zData0;
  int32_t* cbuf_pt;
  int32_t invQincr_pos;
  int32_t invQincr_neg;
  int32_t k;
  int32_t oldZData;
  /* Pole coefficient and data indices */
  enum { a1 = 0, a2 = 1 };

  /* Write the newest pole input sample to the pole delay line.
   * Ensure the sum of the current dequantised error and the previous
   * predictor output is saturated if necessary. */
  poleDelayLine = invQ + SubbandDataPt->m_predData.m_predVal;

  poleDelayLine = ssat24(poleDelayLine);

  /* Pole filter convolution. Shift convolution result 1 place to the left
   * before retrieving it, since the pole coefficients are Q22 (data is Q23)
   * and we want a Q23 result */
  accL = ((int64_t)poleCoeff[a2] *
          (int64_t)SubbandDataPt->m_predData.m_poleDelayLine[a2]);
  /* Update the pole delay line for the next pass by writing the new input
   * sample into the 2nd element */
  SubbandDataPt->m_predData.m_poleDelayLine[a2] = poleDelayLine;
  accL += ((int64_t)poleCoeff[a1] * (int64_t)poleDelayLine);
  poleVal = (int32_t)(accL >> 22);
  poleVal = ssat24(poleVal);

  /* Create (2^(-7)) * sgn(invQ) in Q22 format. */
  if (invQ == 0) {
    invQincr_pos = 0L;
  } else {
    invQincr_pos = 0x800000;
  }
  if (invQ < 0L) {
    invQincr_pos = -invQincr_pos;
  }

  invQincr_neg = 0x0080 - invQincr_pos;
  invQincr_pos += 0x0080;

  pointer = (SubbandDataPt->m_predData.m_zeroDelayLine.pointer++) + 12;
  cbuf_pt = &SubbandDataPt->m_predData.m_zeroDelayLine.buffer[pointer];
  /* partial manual unrolling to improve performance */
  if (SubbandDataPt->m_predData.m_zeroDelayLine.pointer >= 12) {
    SubbandDataPt->m_predData.m_zeroDelayLine.pointer = 0;
  }

  SubbandDataPt->m_predData.m_zeroDelayLine.modulo = invQ;

  /* Iterate over the number of coefficients for this subband */
  oldZData = invQ;
  accL = 0;
  for (k = 0; k < 12; k++) {
    uint32_t tmp_round0;
    int32_t coeffValue;

    zData0 = (*(cbuf_pt--));
    coeffValue = *(zeroCoeffPt + k);
    if (zData0 < 0L) {
      acc = invQincr_neg - coeffValue;
    } else {
      acc = invQincr_pos - coeffValue;
    }
    tmp_round0 = acc;
    acc = (acc >> 8) + coeffValue;
    if (((tmp_round0 << 23) ^ 0x80000000) == 0) {
      acc--;
    }
    accL += (int64_t)acc * (int64_t)(oldZData);
    oldZData = zData0;
    *(zeroCoeffPt + k) = acc;
  }

  acc = (int32_t)(accL >> 22);
  acc = ssat24(acc);
  /* Predictor output is the sum of the pole and zero filter outputs. Ensure
   * this is saturated, if necessary. */
  predVal = acc + poleVal;
  predVal = ssat24(predVal);
  SubbandDataPt->m_predData.m_zeroVal = acc;
  SubbandDataPt->m_predData.m_predVal = predVal;

  /* Update the zero filter delay line by writing the new input sample to the
   * circular buffer. */
  SubbandDataPt->m_predData.m_zeroDelayLine
      .buffer[SubbandDataPt->m_predData.m_zeroDelayLine.pointer] =
      SubbandDataPt->m_predData.m_zeroDelayLine.modulo;
  SubbandDataPt->m_predData.m_zeroDelayLine
      .buffer[SubbandDataPt->m_predData.m_zeroDelayLine.pointer + 12] =
      SubbandDataPt->m_predData.m_zeroDelayLine.modulo;
}

XBT_INLINE_ void performPredictionFilteringLL(const int32_t invQ,
                                              Subband_data* SubbandDataPt) {
  int32_t poleVal;
  int32_t acc;
  int64_t accL;
  uint32_t pointer;
  int32_t poleDelayLine;
  int32_t predVal;
  int32_t* zeroCoeffPt = SubbandDataPt->m_ZeroCoeffData.m_zeroCoeff;
  int32_t* poleCoeff = SubbandDataPt->m_PoleCoeffData.m_poleCoeff;
  int32_t* cbuf_pt;
  int32_t invQincr_pos;
  int32_t invQincr_neg;
  int32_t k;
  int32_t oldZData;
  /* Pole coefficient and data indices */
  enum { a1 = 0, a2 = 1 };

  /* Write the newest pole input sample to the pole delay line.
   * Ensure the sum of the current dequantised error and the previous
   * predictor output is saturated if necessary. */
  poleDelayLine = invQ + SubbandDataPt->m_predData.m_predVal;

  poleDelayLine = ssat24(poleDelayLine);

  /* Pole filter convolution. Shift convolution result 1 place to the left
   * before retrieving it, since the pole coefficients are Q22 (data is Q23)
   * and we want a Q23 result */
  accL = ((int64_t)poleCoeff[a2] *
          (int64_t)SubbandDataPt->m_predData.m_poleDelayLine[a2]);
  /* Update the pole delay line for the next pass by writing the new input
   * sample into the 2nd element */
  SubbandDataPt->m_predData.m_poleDelayLine[a2] = poleDelayLine;
  accL += ((int64_t)poleCoeff[a1] * (int64_t)poleDelayLine);
  poleVal = (int32_t)(accL >> 22);
  poleVal = ssat24(poleVal);
  // store poleVal to free one register.
  SubbandDataPt->m_predData.m_predVal = poleVal;

  /* Create (2^(-7)) * sgn(invQ) in Q22 format. */
  if (invQ == 0) {
    invQincr_pos = 0L;
  } else {
    invQincr_pos = 0x800000;
  }
  if (invQ < 0L) {
    invQincr_pos = -invQincr_pos;
  }

  invQincr_neg = 0x0080 - invQincr_pos;
  invQincr_pos += 0x0080;

  pointer = (SubbandDataPt->m_predData.m_zeroDelayLine.pointer++) + 24;
  cbuf_pt = &SubbandDataPt->m_predData.m_zeroDelayLine.buffer[pointer];
  /* partial manual unrolling to improve performance */
  if (SubbandDataPt->m_predData.m_zeroDelayLine.pointer >= 24) {
    SubbandDataPt->m_predData.m_zeroDelayLine.pointer = 0;
  }

  SubbandDataPt->m_predData.m_zeroDelayLine.modulo = invQ;

  /* Iterate over the number of coefficients for this subband */

  oldZData = invQ;
  accL = 0;
  for (k = 0; k < 24; k++) {
    int32_t zData0;
    int32_t coeffValue;

    zData0 = (*(cbuf_pt--));
    coeffValue = *(zeroCoeffPt + k);
    if (zData0 < 0L) {
      acc = invQincr_neg - coeffValue;
    } else {
      acc = invQincr_pos - coeffValue;
    }
    if (((acc << 23) ^ 0x80000000) == 0) {
      coeffValue--;
    }
    acc = (acc >> 8) + coeffValue;
    accL += (int64_t)acc * (int64_t)(oldZData);
    oldZData = zData0;
    *(zeroCoeffPt + k) = acc;
  }

  acc = (int32_t)(accL >> 22);
  acc = ssat24(acc);
  /* Predictor output is the sum of the pole and zero filter outputs. Ensure
   * this is saturated, if necessary. */
  // recover value of PoleVal stored at beginning of routine...
  predVal = acc + SubbandDataPt->m_predData.m_predVal;
  predVal = ssat24(predVal);
  SubbandDataPt->m_predData.m_zeroVal = acc;
  SubbandDataPt->m_predData.m_predVal = predVal;

  /* Update the zero filter delay line by writing the new input sample to the
   * circular buffer. */
  SubbandDataPt->m_predData.m_zeroDelayLine
      .buffer[SubbandDataPt->m_predData.m_zeroDelayLine.pointer] =
      SubbandDataPt->m_predData.m_zeroDelayLine.modulo;
  SubbandDataPt->m_predData.m_zeroDelayLine
      .buffer[SubbandDataPt->m_predData.m_zeroDelayLine.pointer + 24] =
      SubbandDataPt->m_predData.m_zeroDelayLine.modulo;
}

XBT_INLINE_ void performPredictionFilteringHL(const int32_t invQ,
                                              Subband_data* SubbandDataPt) {
  int32_t poleVal;
  int32_t acc;
  int64_t accL;
  uint32_t pointer;
  int32_t poleDelayLine;
  int32_t predVal;
  int32_t* zeroCoeffPt = SubbandDataPt->m_ZeroCoeffData.m_zeroCoeff;
  int32_t* poleCoeff = SubbandDataPt->m_PoleCoeffData.m_poleCoeff;
  int32_t zData0;
  int32_t* cbuf_pt;
  int32_t invQincr_pos;
  int32_t invQincr_neg;
  int32_t k;
  int32_t oldZData;
  const int32_t roundCte = 0x80000000;
  /* Pole coefficient and data indices */
  enum { a1 = 0, a2 = 1 };

  /* Write the newest pole input sample to the pole delay line.
   * Ensure the sum of the current dequantised error and the previous
   * predictor output is saturated if necessary. */
  poleDelayLine = invQ + SubbandDataPt->m_predData.m_predVal;

  poleDelayLine = ssat24(poleDelayLine);

  /* Pole filter convolution. Shift convolution result 1 place to the left
   * before retrieving it, since the pole coefficients are Q22 (data is Q23)
   * and we want a Q23 result */
  accL = ((int64_t)poleCoeff[a2] *
          (int64_t)SubbandDataPt->m_predData.m_poleDelayLine[a2]);
  /* Update the pole delay line for the next pass by writing the new input
   * sample into the 2nd element */
  SubbandDataPt->m_predData.m_poleDelayLine[a2] = poleDelayLine;
  accL += ((int64_t)poleCoeff[a1] * (int64_t)poleDelayLine);
  poleVal = (int32_t)(accL >> 22);
  poleVal = ssat24(poleVal);

  /* Create (2^(-7)) * sgn(invQ) in Q22 format. */
  invQincr_pos = 0L;
  if (invQ != 0) {
    invQincr_pos = 0x800000;
  }
  if (invQ < 0L) {
    invQincr_pos = -invQincr_pos;
  }

  invQincr_neg = 0x0080 - invQincr_pos;
  invQincr_pos += 0x0080;

  pointer = (SubbandDataPt->m_predData.m_zeroDelayLine.pointer++) + 6;
  cbuf_pt = &SubbandDataPt->m_predData.m_zeroDelayLine.buffer[pointer];
  /* partial manual unrolling to improve performance */
  if (SubbandDataPt->m_predData.m_zeroDelayLine.pointer >= 6) {
    SubbandDataPt->m_predData.m_zeroDelayLine.pointer = 0;
  }

  SubbandDataPt->m_predData.m_zeroDelayLine.modulo = invQ;

  /* Iterate over the number of coefficients for this subband */
  oldZData = invQ;
  accL = 0;

  for (k = 0; k < 6; k++) {
    uint32_t tmp_round0;
    int32_t coeffValue;

    zData0 = (*(cbuf_pt--));
    coeffValue = *(zeroCoeffPt + k);
    if (zData0 < 0L) {
      acc = invQincr_neg - coeffValue;
    } else {
      acc = invQincr_pos - coeffValue;
    }
    tmp_round0 = acc;
    acc = (acc >> 8) + coeffValue;
    if (((tmp_round0 << 23) ^ roundCte) == 0) {
      acc--;
    }
    accL += (int64_t)acc * (int64_t)(oldZData);
    oldZData = zData0;
    *(zeroCoeffPt + k) = acc;
  }

  acc = (int32_t)(accL >> 22);
  acc = ssat24(acc);
  /* Predictor output is the sum of the pole and zero filter outputs. Ensure
   * this is saturated, if necessary. */
  predVal = acc + poleVal;
  predVal = ssat24(predVal);
  SubbandDataPt->m_predData.m_zeroVal = acc;
  SubbandDataPt->m_predData.m_predVal = predVal;

  /* Update the zero filter delay line by writing the new input sample to the
   * circular buffer. */
  SubbandDataPt->m_predData.m_zeroDelayLine
      .buffer[SubbandDataPt->m_predData.m_zeroDelayLine.pointer] =
      SubbandDataPt->m_predData.m_zeroDelayLine.modulo;
  SubbandDataPt->m_predData.m_zeroDelayLine
      .buffer[SubbandDataPt->m_predData.m_zeroDelayLine.pointer + 6] =
      SubbandDataPt->m_predData.m_zeroDelayLine.modulo;
}

#endif  // SUBBANDFUNCTIONSCOMMON_H
