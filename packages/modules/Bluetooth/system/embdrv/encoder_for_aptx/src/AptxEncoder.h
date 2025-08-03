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
 *  All declarations relevant for aptxEncode. This function allows clients
 *  to invoke bt-aptX encoding on 4 new PCM samples,
 *  generating 4 new quantised codes. A separate function allows the
 *  packing of the 4 codes into a 16-bit word.
 *
 *----------------------------------------------------------------------------*/

#ifndef APTXENCODER_H
#define APTXENCODER_H
#ifdef _GCC
#pragma GCC visibility push(hidden)
#endif

#include "AptxParameters.h"
#include "DitherGenerator.h"
#include "Qmf.h"
#include "Quantiser.h"
#include "SubbandFunctionsCommon.h"

/* Function to carry out a single-channel aptX encode on 4 new PCM samples */
XBT_INLINE_ void aptxEncode(const int32_t pcm[4], Qmf_storage* Qmf_St,
                            Encoder_data* EncoderDataPt) {
  int32_t predVals[4];
  int32_t qCodes[4];
  int32_t aqmfOutputs[4];

  /* Extract the previous predicted values and quantised codes into arrays */
  for (int i = 0; i < 4; i++) {
    predVals[i] = EncoderDataPt->m_SubbandData[i].m_predData.m_predVal;
    qCodes[i] = EncoderDataPt->m_qdata[i].qCode;
  }

  /* Update codeword history, then generate new dither values. */
  EncoderDataPt->m_codewordHistory =
      xbtEncupdateCodewordHistory(qCodes, EncoderDataPt->m_codewordHistory);
  EncoderDataPt->m_dithSyncRandBit = xbtEncgenerateDither(
      EncoderDataPt->m_codewordHistory, EncoderDataPt->m_ditherOutputs);

  /* Run the analysis QMF */
  QmfAnalysisFilter(pcm, Qmf_St, predVals, aqmfOutputs);

  /* Run the quantiser for each subband */
  quantiseDifferenceLL(aqmfOutputs[0], EncoderDataPt->m_ditherOutputs[0],
                       EncoderDataPt->m_SubbandData[0].m_iqdata.delta,
                       &EncoderDataPt->m_qdata[0]);
  quantiseDifferenceLH(aqmfOutputs[1], EncoderDataPt->m_ditherOutputs[1],
                       EncoderDataPt->m_SubbandData[1].m_iqdata.delta,
                       &EncoderDataPt->m_qdata[1]);
  quantiseDifferenceHL(aqmfOutputs[2], EncoderDataPt->m_ditherOutputs[2],
                       EncoderDataPt->m_SubbandData[2].m_iqdata.delta,
                       &EncoderDataPt->m_qdata[2]);
  quantiseDifferenceHH(aqmfOutputs[3], EncoderDataPt->m_ditherOutputs[3],
                       EncoderDataPt->m_SubbandData[3].m_iqdata.delta,
                       &EncoderDataPt->m_qdata[3]);
}

XBT_INLINE_ void aptxPostEncode(Encoder_data* EncoderDataPt) {
  /* Run the remaining subband processing for each subband */
  /* Manual inlining on the 4 subband */
  processSubbandLL(EncoderDataPt->m_qdata[0].qCode,
                   EncoderDataPt->m_ditherOutputs[0],
                   &EncoderDataPt->m_SubbandData[0],
                   &EncoderDataPt->m_SubbandData[0].m_iqdata);

  processSubband(EncoderDataPt->m_qdata[1].qCode,
                 EncoderDataPt->m_ditherOutputs[1],
                 &EncoderDataPt->m_SubbandData[1],
                 &EncoderDataPt->m_SubbandData[1].m_iqdata);

  processSubbandHL(EncoderDataPt->m_qdata[2].qCode,
                   EncoderDataPt->m_ditherOutputs[2],
                   &EncoderDataPt->m_SubbandData[2],
                   &EncoderDataPt->m_SubbandData[2].m_iqdata);

  processSubband(EncoderDataPt->m_qdata[3].qCode,
                 EncoderDataPt->m_ditherOutputs[3],
                 &EncoderDataPt->m_SubbandData[3],
                 &EncoderDataPt->m_SubbandData[3].m_iqdata);
}

#ifdef _GCC
#pragma GCC visibility pop
#endif
#endif  // APTXENCODER_H
