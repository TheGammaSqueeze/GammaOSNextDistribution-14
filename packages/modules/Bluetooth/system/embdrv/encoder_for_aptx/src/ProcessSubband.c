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
#include "AptxParameters.h"
#include "SubbandFunctions.h"
#include "SubbandFunctionsCommon.h"

/*  This function carries out all subband processing (common to both encode and
 * decode). */
void processSubband(const int32_t qCode, const int32_t ditherVal,
                    Subband_data* SubbandDataPt, IQuantiser_data* iqDataPt) {
  /* Inverse quantisation */
  invertQuantisation(qCode, ditherVal, iqDataPt);

  /* Predictor pole coefficient update */
  updatePredictorPoleCoefficients(iqDataPt->invQ,
                                  SubbandDataPt->m_predData.m_zeroVal,
                                  &SubbandDataPt->m_PoleCoeffData);

  /* Predictor filtering */
  performPredictionFiltering(iqDataPt->invQ, SubbandDataPt);
}

/* processSubbandLL is used for the LL subband only. */
void processSubbandLL(const int32_t qCode, const int32_t ditherVal,
                      Subband_data* SubbandDataPt, IQuantiser_data* iqDataPt) {
  /* Inverse quantisation */
  invertQuantisation(qCode, ditherVal, iqDataPt);

  /* Predictor pole coefficient update */
  updatePredictorPoleCoefficients(iqDataPt->invQ,
                                  SubbandDataPt->m_predData.m_zeroVal,
                                  &SubbandDataPt->m_PoleCoeffData);

  /* Predictor filtering */
  performPredictionFilteringLL(iqDataPt->invQ, SubbandDataPt);
}

/* processSubbandHL is used for the HL subband only. */
void processSubbandHL(const int32_t qCode, const int32_t ditherVal,
                      Subband_data* SubbandDataPt, IQuantiser_data* iqDataPt) {
  /* Inverse quantisation */
  invertQuantisationHL(qCode, ditherVal, iqDataPt);

  /* Predictor pole coefficient update */
  updatePredictorPoleCoefficients(iqDataPt->invQ,
                                  SubbandDataPt->m_predData.m_zeroVal,
                                  &SubbandDataPt->m_PoleCoeffData);

  /* Predictor filtering */
  performPredictionFilteringHL(iqDataPt->invQ, SubbandDataPt);
}
