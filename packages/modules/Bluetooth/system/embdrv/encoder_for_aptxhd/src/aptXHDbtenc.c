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
#include "aptXHDbtenc.h"

#include "AptxEncoder.h"
#include "AptxParameters.h"
#include "AptxTables.h"
#include "CodewordPacker.h"
#include "SyncInserter.h"
#include "swversion.h"

typedef struct aptxhdbtenc_t {
  /* m_endian should either be 0 (little endian) or 8 (big endian). */
  int32_t m_endian;

  /* Autosync inserter & Checker for use with the stereo aptX HD codec. */
  /* The current phase of the sync word insertion (7 down to 0) */
  uint32_t m_syncWordPhase;

  /* Stereo channel aptX HD encoder (annotated to produce Kalimba test vectors
   * for it's I/O. This will process valid PCM from a WAV file). */
  /* Each Encoder_data structure requires 1592 bytes */
  Encoder_data m_encoderData[2];
  Qmf_storage m_qmf_l;
  Qmf_storage m_qmf_r;
} aptxhdbtenc;

/* Constants */
/* Log to linear lookup table used in inverse quantiser*/
/* Size of Table: 32*4 = 128 bytes */
static const int32_t IQuant_tableLogT[32] = {
    16384 * 256, 16744 * 256, 17112 * 256, 17488 * 256, 17864 * 256,
    18256 * 256, 18656 * 256, 19064 * 256, 19480 * 256, 19912 * 256,
    20344 * 256, 20792 * 256, 21248 * 256, 21712 * 256, 22192 * 256,
    22672 * 256, 23168 * 256, 23680 * 256, 24200 * 256, 24728 * 256,
    25264 * 256, 25824 * 256, 26384 * 256, 26968 * 256, 27552 * 256,
    28160 * 256, 28776 * 256, 29408 * 256, 30048 * 256, 30704 * 256,
    31376 * 256, 32064 * 256};

static void clearmem_HD(void* mem, int32_t sz) {
  int8_t* m = (int8_t*)mem;
  int32_t i = 0;
  for (; i < sz; i++) {
    *m = 0;
    m++;
  }
}

APTXHDBTENCEXPORT int SizeofAptxhdbtenc() { return (sizeof(aptxhdbtenc)); }

APTXHDBTENCEXPORT const char* aptxhdbtenc_version() { return (swversion); }

APTXHDBTENCEXPORT int aptxhdbtenc_init(void* _state, short endian) {
  aptxhdbtenc* state = (aptxhdbtenc*)_state;

  clearmem_HD(_state, sizeof(aptxhdbtenc));

  if (state == 0) {
    return 1;
  }
  state->m_syncWordPhase = 7L;

  if (endian == 0) {
    state->m_endian = 0;
  } else {
    state->m_endian = 8;
  }

  for (int j = 0; j < 2; j++) {
    Encoder_data* encode_dat = &state->m_encoderData[j];
    uint32_t i;

    /* Create a quantiser and subband processor for each suband */
    for (i = LL; i <= HH; i++) {
      encode_dat->m_codewordHistory = 0L;

      encode_dat->m_qdata[i].thresholdTablePtr =
          subbandParameters[i].threshTable;
      encode_dat->m_qdata[i].thresholdTablePtr_sl1 =
          subbandParameters[i].threshTable_sl1;
      encode_dat->m_qdata[i].ditherTablePtr = subbandParameters[i].dithTable;
      encode_dat->m_qdata[i].minusLambdaDTable =
          subbandParameters[i].minusLambdaDTable;
      encode_dat->m_qdata[i].codeBits = subbandParameters[i].numBits;
      encode_dat->m_qdata[i].qCode = 0L;
      encode_dat->m_qdata[i].altQcode = 0L;
      encode_dat->m_qdata[i].distPenalty = 0L;

      /* initialisation of inverseQuantiser data */
      encode_dat->m_SubbandData[i].m_iqdata.thresholdTablePtr =
          subbandParameters[i].threshTable;
      encode_dat->m_SubbandData[i].m_iqdata.thresholdTablePtr_sl1 =
          subbandParameters[i].threshTable_sl1;
      encode_dat->m_SubbandData[i].m_iqdata.ditherTablePtr_sf1 =
          subbandParameters[i].dithTable_sh1;
      encode_dat->m_SubbandData[i].m_iqdata.incrTablePtr =
          subbandParameters[i].incrTable;
      encode_dat->m_SubbandData[i].m_iqdata.maxLogDelta =
          subbandParameters[i].maxLogDelta;
      encode_dat->m_SubbandData[i].m_iqdata.minLogDelta =
          subbandParameters[i].minLogDelta;
      encode_dat->m_SubbandData[i].m_iqdata.delta = 0;
      encode_dat->m_SubbandData[i].m_iqdata.logDelta = 0;
      encode_dat->m_SubbandData[i].m_iqdata.invQ = 0;
      encode_dat->m_SubbandData[i].m_iqdata.iquantTableLogPtr =
          &IQuant_tableLogT[0];

      // Initializing data for predictor filter
      encode_dat->m_SubbandData[i].m_predData.m_zeroDelayLine.modulo =
          subbandParameters[i].numZeros;

      for (int t = 0; t < 48; t++) {
        encode_dat->m_SubbandData[i].m_predData.m_zeroDelayLine.buffer[t] = 0;
      }

      encode_dat->m_SubbandData[i].m_predData.m_zeroDelayLine.pointer = 0;
      /* Initialise the previous zero filter output and predictor output to zero
       */
      encode_dat->m_SubbandData[i].m_predData.m_zeroVal = 0L;
      encode_dat->m_SubbandData[i].m_predData.m_predVal = 0L;
      encode_dat->m_SubbandData[i].m_predData.m_numZeros =
          subbandParameters[i].numZeros;
      /* Initialise the contents of the pole data delay line to zero */
      encode_dat->m_SubbandData[i].m_predData.m_poleDelayLine[0] = 0L;
      encode_dat->m_SubbandData[i].m_predData.m_poleDelayLine[1] = 0L;

      for (int k = 0; k < 24; k++) {
        encode_dat->m_SubbandData[i].m_ZeroCoeffData.m_zeroCoeff[k] = 0;
      }

      // Initializing data for zerocoeff update function.
      encode_dat->m_SubbandData[i].m_ZeroCoeffData.m_numZeros =
          subbandParameters[i].numZeros;

      /* Initializing data for PoleCoeff Update function.
       * Fill the adaptation delay line with +1 initially */
      encode_dat->m_SubbandData[i].m_PoleCoeffData.m_poleAdaptDelayLine.s32 =
          0x00010001;

      /* Zero the pole coefficients */
      encode_dat->m_SubbandData[i].m_PoleCoeffData.m_poleCoeff[0] = 0L;
      encode_dat->m_SubbandData[i].m_PoleCoeffData.m_poleCoeff[1] = 0L;
    }
  }
  return 0;
}

APTXHDBTENCEXPORT int aptxhdbtenc_encodestereo(void* _state, void* _pcmL,
                                               void* _pcmR, void* _buffer) {
  aptxhdbtenc* state = (aptxhdbtenc*)_state;
  int32_t* pcmL = (int32_t*)_pcmL;
  int32_t* pcmR = (int32_t*)_pcmR;
  int32_t* buffer = (int32_t*)_buffer;

  // Feed the PCM to the dual aptX HD encoders
  aptxhdEncode(pcmL, &state->m_qmf_l, &state->m_encoderData[0]);
  aptxhdEncode(pcmR, &state->m_qmf_r, &state->m_encoderData[1]);

  // Insert the autosync information into the stereo quantised codes
  xbtEncinsertSync(&state->m_encoderData[0], &state->m_encoderData[1],
                   &state->m_syncWordPhase);

  aptxhdPostEncode(&state->m_encoderData[0]);
  aptxhdPostEncode(&state->m_encoderData[1]);

  // Pack the (possibly adjusted) codes into a 24-bit codeword per channel
  buffer[0] = packCodeword(&state->m_encoderData[0]);
  buffer[1] = packCodeword(&state->m_encoderData[1]);

  return 0;
}
