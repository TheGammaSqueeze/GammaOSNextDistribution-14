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
 *  Prototype declaration of the CodewordPacker Function
 *
 *  This functions allows a client to supply an array of 4 quantised codes
 *  (1 per subband) and obtain a packed version as a 24-bit aptX HD codeword.
 *
 *----------------------------------------------------------------------------*/

#ifndef CODEWORDPACKER_H
#define CODEWORDPACKER_H

#include "AptxParameters.h"

/* This functions allows a client to supply an array of 4 quantised codes
 * (1 per subband) and obtain a packed version as a 24-bit aptX HD codeword. */
XBT_INLINE_ int32_t packCodeword(Encoder_data* EncoderDataPt) {
  int32_t syncContribution;
  int32_t hhCode;
  int32_t codeword;

  /* The per-channel contribution to derive the current sync bit is the XOR of
   * the 4 code lsbs and the random dither bit. The SyncInserter engineers it
   * such that the XOR of the sync contributions from the left and right
   * channel give the actual sync bit value. The per-channel sync bit
   * contribution overwrites the HH code lsb in the packed codeword. */
  syncContribution =
      (EncoderDataPt->m_qdata[0].qCode ^ EncoderDataPt->m_qdata[1].qCode ^
       EncoderDataPt->m_qdata[2].qCode ^ EncoderDataPt->m_qdata[3].qCode ^
       EncoderDataPt->m_dithSyncRandBit) &
      0x1;
  hhCode = (EncoderDataPt->m_qdata[HH].qCode & 0x1eL) | syncContribution;

  /* Pack the 24-bit codeword with the appropriate number of lsbs from each
   * quantised code (LL=9, LH=6, HL=4, HH=5). */
  codeword = (EncoderDataPt->m_qdata[LL].qCode & 0x1ff) |
             ((EncoderDataPt->m_qdata[LH].qCode & 0x3f) << 9) |
             ((EncoderDataPt->m_qdata[HL].qCode & 0xf) << 15) | (hhCode << 19);

  return codeword;
}

#endif  // CODEWORDPACKER_H
