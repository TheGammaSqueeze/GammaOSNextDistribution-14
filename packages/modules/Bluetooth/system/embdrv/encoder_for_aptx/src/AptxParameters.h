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
 *  General shared aptX parameters.
 *
 *----------------------------------------------------------------------------*/

#ifndef APTXPARAMETERS_H
#define APTXPARAMETERS_H
#ifdef _GCC
#pragma GCC visibility push(hidden)
#endif

#include <stdint.h>

#include "CBStruct.h"

#if defined _MSC_VER
#define XBT_INLINE_ inline
#define _STDQMFOUTERCOEFF 1
#elif defined __clang__
#define XBT_INLINE_ static inline
#define _STDQMFOUTERCOEFF 1
#elif defined __GNUC__
#define XBT_INLINE_ inline
#define _STDQMFOUTERCOEFF 1
#else
#define XBT_INLINE_ static
#define _STDQMFOUTERCOEFF 1
#endif

/* Signed saturate to a 24bit value */
XBT_INLINE_ int32_t ssat24(int32_t val) {
  if (val > 8388607) {
    val = 8388607;
  }
  if (val < -8388608) {
    val = -8388608;
  }
  return val;
}

typedef union u_reg64 {
  uint64_t u64;
  int64_t s64;
  struct s_u32 {
#ifdef __BIGENDIAN
    uint32_t h;
    uint32_t l;
#else
    uint32_t l;
    uint32_t h;
#endif
  } u32;

  struct s_s32 {
#ifdef __BIGENDIAN
    int32_t h;
    int32_t l;
#else
    int32_t l;
    int32_t h;
#endif
  } s32;
} reg64_t;

typedef union u_reg32 {
  uint32_t u32;
  int32_t s32;

  struct s_u16 {
#ifdef __BIGENDIAN
    uint16_t h;
    uint16_t l;
#else
    uint16_t l;
    uint16_t h;
#endif
  } u16;
  struct s_s16 {
#ifdef __BIGENDIAN
    int16_t h;
    int16_t l;
#else
    int16_t l;
    int16_t h;
#endif
  } s16;
} reg32_t;

/* Each aptX enc/dec round consumes/produces 4 PCM samples */
static const uint32_t numPcmSamples = 4;

/* Symbolic constants for PCM data indices. */
enum { FirstPcm = 0, SecondPcm = 1, ThirdPcm = 2, FourthPcm = 3 };

/* Symbolic constants for sync modes. */
enum { stereo = 0, dualmono = 1, no_sync = 2 };

/* Number of subbands is fixed at 4 */
#define NUMSUBBANDS 4

/* Symbolic constants for subband identification. */
typedef enum { LL = 0, LH = 1, HL = 2, HH = 3 } bands;

/* Structure declaration to bind a set of subband parameters */
typedef struct {
  const int32_t* threshTable;
  const int32_t* threshTable_sl1;
  const int32_t* dithTable;
  const int32_t* dithTable_sh1;
  const int32_t* minusLambdaDTable;
  const int32_t* incrTable;
  int32_t numBits;
  int32_t maxLogDelta;
  int32_t minLogDelta;
  int32_t numZeros;
} SubbandParameters;

/* Struct required for the polecoeffcalculator function of bt-aptX encoder and
 * decoder*/
/* Size of structure: 16 Bytes */
typedef struct {
  /* 2-tap delay line for previous sgn values */
  reg32_t m_poleAdaptDelayLine;
  /* 2 pole filter coeffs */
  int32_t m_poleCoeff[2];
} PoleCoeff_data;

/* Struct required for the zerocoeffcalculator function of bt-aptX encoder and
 * decoder*/
/* Size of structure: 100 Bytes */
typedef struct {
  /* The zero filter length for this subband */
  int32_t m_numZeros;
  /* Maximum number of zeros for any subband is 24. */
  /* 24 zero filter coeffs */
  int32_t m_zeroCoeff[24];
} ZeroCoeff_data;

/* Struct required for the prediction filtering function of bt-aptX encoder and
 * decoder*/
/* Size of structure: 200+20=220 Bytes */
typedef struct {
  /* Number of zeros associated with this subband */
  int32_t m_numZeros;
  /* Zero data delay line (circular) */
  circularBuffer m_zeroDelayLine;
  /* 2-tap pole data delay line */
  int32_t m_poleDelayLine[2];
  /* Output from zero filter */
  int32_t m_zeroVal;
  /* Output from overall ARMA filter */
  int32_t m_predVal;
} Predictor_data;

/* Struct required for the Quantisation function of bt-aptX encoder and
 * decoder*/
/* Size of structure: 24 Bytes */
typedef struct {
  /* Number of bits in the quantised code for this subband */
  int32_t codeBits;
  /* Pointer to threshold table */
  const int32_t* thresholdTablePtr;
  const int32_t* thresholdTablePtr_sl1;
  /* Pointer to dither table */
  const int32_t* ditherTablePtr;
  /* Pointer to minus Lambda table */
  const int32_t* minusLambdaDTable;
  /* Output quantised code */
  int32_t qCode;
  /* Alternative quantised code for sync purposes */
  int32_t altQcode;
  /* Penalty associated with choosing alternative code */
  int32_t distPenalty;
} Quantiser_data;

/* Struct required for the inverse Quantisation function of bt-aptX encoder and
 * decoder*/
/* Size of structure: 32 Bytes */
typedef struct {
  /* Pointer to threshold table */
  const int32_t* thresholdTablePtr;
  const int32_t* thresholdTablePtr_sl1;
  /* Pointer to dither table */
  const int32_t* ditherTablePtr_sf1;
  /* Pointer to increment table */
  const int32_t* incrTablePtr;
  /* Upper and lower bounds for logDelta */
  int32_t maxLogDelta;
  int32_t minLogDelta;
  /* Delta (quantisation step size */
  int32_t delta;
  /* Delta, expressed as a log base 2 */
  uint16_t logDelta;
  /* Output dequantised signal */
  int32_t invQ;
  /* pointer to IQuant_tableLogT */
  const int32_t* iquantTableLogPtr;
} IQuantiser_data;

/* Subband data structure bt-aptX encoder*/
/* Size of structure: 116+220+32= 368 Bytes */
typedef struct {
  /* Subband processing consists of inverse quantisation, predictor
   * coefficient update, and predictor filtering. */
  ZeroCoeff_data m_ZeroCoeffData;
  PoleCoeff_data m_PoleCoeffData;
  /* structure holding the data associated with the predictor */
  Predictor_data m_predData;
  /* iqdata holds the data associated with the instance of inverse quantiser */
  IQuantiser_data m_iqdata;
} Subband_data;

/* Encoder data structure bt-aptX encoder*/
/* Size of structure: 368*4+24+4*24 = 1592 Bytes */
typedef struct {
  /* Subband processing consists of inverse quantisation, predictor
   * coefficient update, and predictor filtering. */
  Subband_data m_SubbandData[4];
  int32_t m_codewordHistory;
  int32_t m_dithSyncRandBit;
  int32_t m_ditherOutputs[4];
  /* structure holding data values for this quantiser */
  Quantiser_data m_qdata[4];
} Encoder_data;

/* Number of predictor pole filter coefficients is fixed at 2 for all subbands
 */
static const uint32_t numPoleFilterCoeffs = 2;

/* Subband-specific number of predictor zero filter coefficients. */
static const uint32_t numZeroFilterCoeffs[4] = {24, 12, 6, 12};

/* Delta is scaled by 4 positions within the quantiser and inverse quantiser. */
static const uint32_t deltaScale = 4;

#ifdef _GCC
#pragma GCC visibility pop
#endif
#endif  // APTXPARAMETERS_H
