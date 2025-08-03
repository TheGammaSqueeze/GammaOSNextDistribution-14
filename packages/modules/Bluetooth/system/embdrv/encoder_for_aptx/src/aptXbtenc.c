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
#include "aptXbtenc.h"

#include "AptxEncoder.h"
#include "AptxParameters.h"
#include "AptxTables.h"
#include "CodewordPacker.h"
#include "SyncInserter.h"
#include "swversion.h"

typedef struct aptxbtenc_t {
  /* m_endian should either be 0 (little endian) or 8 (big endian). */
  int32_t m_endian;

  /* m_sync_mode is an enumerated type and will be
     0 (stereo sync),
     1 (for dual mono sync), or
     2 (for dual channel with no autosync).
  */
  int32_t m_sync_mode;

  /* Autosync inserter & Checker for use with the stereo aptX codec. */
  /* The current phase of the sync word insertion (7 down to 0) */
  uint32_t m_syncWordPhase;

  /* Stereo channel aptX encoder (annotated to produce Kalimba test vectors
   * for it's I/O. This will process valid PCM from a WAV file). */
  /* Each Encoder_data structure requires 1592 bytes */
  Encoder_data m_encoderData[2];
  Qmf_storage m_qmf_l;
  Qmf_storage m_qmf_r;
} aptxbtenc;

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

static void clearmem(void* mem, int32_t sz) {
  int8_t* m = (int8_t*)mem;
  int32_t i = 0;
  for (; i < sz; i++) {
    *m = 0;
    m++;
  }
}

APTXBTENCEXPORT int SizeofAptxbtenc(void) { return (sizeof(aptxbtenc)); }

APTXBTENCEXPORT const char* aptxbtenc_version() { return (swversion); }

APTXBTENCEXPORT int aptxbtenc_init(void* _state, short endian) {
  aptxbtenc* state = (aptxbtenc*)_state;
  int32_t j = 0;
  int32_t k;
  int32_t t;

  clearmem(_state, sizeof(aptxbtenc));

  if (state == 0) {
    return 1;
  }
  state->m_syncWordPhase = 7L;

  if (endian == 0) {
    state->m_endian = 0;
  } else {
    state->m_endian = 8;
  }

  /* default setting should be stereo autosync,
  for backwards-compatibility with legacy applications that use this library */
  state->m_sync_mode = stereo;

  for (j = 0; j < 2; j++) {
    Encoder_data* encode_dat = &state->m_encoderData[j];
    uint32_t i;

    /* Create a quantiser and subband processor for each subband */
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

      for (t = 0; t < 48; t++) {
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

      for (k = 0; k < 24; k++) {
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

APTXBTENCEXPORT int aptxbtenc_setsync_mode(void* _state, int32_t sync_mode) {
  aptxbtenc* state = (aptxbtenc*)_state;
  state->m_sync_mode = sync_mode;

  return 0;
}

APTXBTENCEXPORT int aptxbtenc_encodestereo(void* _state, void* _pcmL,
                                           void* _pcmR, void* _buffer) {
  aptxbtenc* state = (aptxbtenc*)_state;
  int32_t* pcmL = (int32_t*)_pcmL;
  int32_t* pcmR = (int32_t*)_pcmR;
  int16_t* buffer = (int16_t*)_buffer;
  int16_t tmp_reg;
  int16_t tmp_out;
  // Feed the PCM to the dual aptX encoders
  aptxEncode(pcmL, &state->m_qmf_l, &state->m_encoderData[0]);
  aptxEncode(pcmR, &state->m_qmf_r, &state->m_encoderData[1]);

  // only insert sync information if we are not in non-autosync mode.
  // The Non-autosync mode changes only take effect in the packCodeword()
  // function.
  if (state->m_sync_mode != no_sync) {
    if (state->m_sync_mode == stereo) {
      // Insert the autosync information into the stereo quantised codes
      xbtEncinsertSync(&state->m_encoderData[0], &state->m_encoderData[1],
                       &state->m_syncWordPhase);
    } else {
      // Insert the autosync information into the two individual mono quantised
      // codes
      xbtEncinsertSyncDualMono(&state->m_encoderData[0],
                               &state->m_encoderData[1],
                               &state->m_syncWordPhase);
    }
  }

  aptxPostEncode(&state->m_encoderData[0]);
  aptxPostEncode(&state->m_encoderData[1]);

  // Pack the (possibly adjusted) codes into a 16-bit codeword per channel
  tmp_reg = packCodeword(&state->m_encoderData[0], state->m_sync_mode);
  // Swap bytes to output data in big-endian as expected by bc5 code...
  tmp_out = tmp_reg >> state->m_endian;
  tmp_out |= tmp_reg << state->m_endian;

  buffer[0] = tmp_out;
  tmp_reg = packCodeword(&state->m_encoderData[1], state->m_sync_mode);
  // Swap bytes to output data in big-endian as expected by bc5 code...
  tmp_out = tmp_reg >> state->m_endian;
  tmp_out |= tmp_reg << state->m_endian;

  buffer[1] = tmp_out;

  return 0;
}
