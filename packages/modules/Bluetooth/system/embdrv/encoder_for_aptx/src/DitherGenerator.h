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
 *  These functions allow clients to update an internal codeword history
 *  attribute from previously-generated quantised codes, and to generate a new
 *  pseudo-random dither value per subband from this internal attribute.
 *
 *----------------------------------------------------------------------------*/

#ifndef DITHERGENERATOR_H
#define DITHERGENERATOR_H

#include "AptxParameters.h"

/* This function updates an internal bit-pool (private
 * variable in DitherGenerator) based on bits obtained from
 * previously encoded or received aptX codewords. */
XBT_INLINE_ int32_t xbtEncupdateCodewordHistory(const int32_t quantisedCodes[4],
                                                int32_t m_codewordHistory) {
  int32_t newBits;
  int32_t updatedCodewordHistory;

  const int32_t llMask = 0x3L;
  const int32_t lhMask = 0x2L;
  const int32_t hlMask = 0x1L;
  const uint32_t lhShift = 1;
  const uint32_t hlShift = 3;
  /* Shift value to left-justify a 24-bit value in a 32-bit signed variable*/
  const uint32_t leftJustifyShift = 8;
  const uint32_t numNewBits = 4;

  /* Make a 4-bit vector from particular bits from 3 quantised codes */
  newBits = (quantisedCodes[LL] & llMask) +
            ((quantisedCodes[LH] & lhMask) << lhShift) +
            ((quantisedCodes[HL] & hlMask) << hlShift);

  /* Add the 4 new bits to the codeword history. Note that this is a 24-bit
   * value LEFT-JUSTIFIED in a 32-bit signed variable. Maintaining the history
   * as signed is useful in the dither generation process below. */
  updatedCodewordHistory =
      (m_codewordHistory << numNewBits) + (newBits << leftJustifyShift);

  return updatedCodewordHistory;
}

/* Function to generate a dither value for each subband based
 * on the current contents of the codewordHistory bit-pool. */
XBT_INLINE_ int32_t xbtEncgenerateDither(int32_t m_codewordHistory,
                                         int32_t* m_ditherOutputs) {
  int32_t history24b;
  int32_t upperAcc;
  int32_t lowerAcc;
  int32_t accSum;
  int64_t tmp_acc;
  int32_t ditherSample;
  int32_t m_dithSyncRandBit;

  /* Fixed value to multiply codeword history variable by */
  const uint32_t dithConstMultiplier = 0x4f1bbbL;
  /* Shift value to left-justify a 24-bit value in a 32-bit signed variable*/
  const uint32_t leftJustifyShift = 8;

  /* AND mask to retain only the lower 24 bits of a variable */
  const int32_t keepLower24bitsMask = 0xffffffL;

  /* Convert the codeword history to a 24-bit signed value. This can be done
   * cheaply with a 8-position right-shift since it is maintained as 24-bits
   * value left-justified in a signed 32-bit variable. */
  history24b = m_codewordHistory >> (leftJustifyShift - 1);

  /* Multiply the history by a fixed constant. The constant has already been
   * shifted right by 1 position to compensate for the left-shift introduced
   * on the product by the fractional multiplier. */
  tmp_acc = ((int64_t)history24b * (int64_t)dithConstMultiplier);

  /* Get the upper and lower 24-bit values from the accumulator, and form
   * their sum. */
  upperAcc = ((int32_t)(tmp_acc >> 24)) & 0x00FFFFFFL;
  lowerAcc = ((int32_t)tmp_acc) & 0x00FFFFFFL;
  accSum = upperAcc + lowerAcc;

  /* The dither sample is the 2 msbs of lowerAcc and the 22 lsbs of accSum */
  ditherSample = ((lowerAcc >> 22) + (accSum << 2)) & keepLower24bitsMask;

  /* The sign bit of 24-bit accSum is saved as a random bit to
   * assist in the aptX sync insertion process. */
  m_dithSyncRandBit = (accSum >> 23) & 0x1;

  /* Successive dither outputs for the 4 subbands are versions of ditherSample
   * offset by a further 5-position left shift for each subband. Also apply a
   * constant left-shift of 8 to turn the values into signed 24-bit values
   * left-justified in the 32-bit ditherOutput variable. */
  m_ditherOutputs[HH] = ditherSample << leftJustifyShift;
  m_ditherOutputs[HL] = ditherSample << (5 + leftJustifyShift);
  m_ditherOutputs[LH] = ditherSample << (10 + leftJustifyShift);
  m_ditherOutputs[LL] = ditherSample << (15 + leftJustifyShift);

  return m_dithSyncRandBit;
};

#endif  // DITHERGENERATOR_H
