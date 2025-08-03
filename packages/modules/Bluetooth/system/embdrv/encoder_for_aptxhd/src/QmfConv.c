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
 *  This file includes convolution functions required for the Qmf.
 *
 *----------------------------------------------------------------------------*/

#include "Qmf.h"

void AsmQmfConvO_HD(const int32_t* p1dl_buffPtr, const int32_t* p2dl_buffPtr,
                    const int32_t* coeffPtr, int32_t* convSumDiff) {
  /* Since all manipulated data are "int16_t" it is possible to
   * reduce the number of loads by using int32_t type and manipulating
   * pairs of data
   */

  int32_t acc;
  // Manual inlining as IAR compiler does not seem to do it itself...
  // WARNING: This inlining assumes that m_qmfDelayLineLength == 16
  int32_t tmp_round0;
  int64_t local_acc0;
  int64_t local_acc1;

  int32_t coeffVal0;
  int32_t coeffVal1;
  int32_t data0;
  int32_t data1;
  int32_t data2;
  int32_t data3;
  int32_t phaseConv[2];
  int32_t convSum;
  int32_t convDiff;

  coeffVal0 = (*(coeffPtr));
  coeffVal1 = (*(coeffPtr + 1));
  data0 = (*(p1dl_buffPtr));
  data1 = (*(p2dl_buffPtr));
  data2 = (*(p1dl_buffPtr - 1));
  data3 = (*(p2dl_buffPtr + 1));

  local_acc0 = ((int64_t)(coeffVal0) * (int64_t)data0);
  local_acc1 = ((int64_t)(coeffVal0) * (int64_t)data1);
  local_acc0 += ((int64_t)(coeffVal1) * (int64_t)data2);
  local_acc1 += ((int64_t)(coeffVal1) * (int64_t)data3);

  coeffVal0 = (*(coeffPtr + 2));
  coeffVal1 = (*(coeffPtr + 3));
  data0 = (*(p1dl_buffPtr - 2));
  data1 = (*(p2dl_buffPtr + 2));
  data2 = (*(p1dl_buffPtr - 3));
  data3 = (*(p2dl_buffPtr + 3));

  local_acc0 += ((int64_t)(coeffVal0) * (int64_t)data0);
  local_acc1 += ((int64_t)(coeffVal0) * (int64_t)data1);
  local_acc0 += ((int64_t)(coeffVal1) * (int64_t)data2);
  local_acc1 += ((int64_t)(coeffVal1) * (int64_t)data3);

  coeffVal0 = (*(coeffPtr + 4));
  coeffVal1 = (*(coeffPtr + 5));
  data0 = (*(p1dl_buffPtr - 4));
  data1 = (*(p2dl_buffPtr + 4));
  data2 = (*(p1dl_buffPtr - 5));
  data3 = (*(p2dl_buffPtr + 5));

  local_acc0 += ((int64_t)(coeffVal0) * (int64_t)data0);
  local_acc1 += ((int64_t)(coeffVal0) * (int64_t)data1);
  local_acc0 += ((int64_t)(coeffVal1) * (int64_t)data2);
  local_acc1 += ((int64_t)(coeffVal1) * (int64_t)data3);

  coeffVal0 = (*(coeffPtr + 6));
  coeffVal1 = (*(coeffPtr + 7));
  data0 = (*(p1dl_buffPtr - 6));
  data1 = (*(p2dl_buffPtr + 6));
  data2 = (*(p1dl_buffPtr - 7));
  data3 = (*(p2dl_buffPtr + 7));

  local_acc0 += ((int64_t)(coeffVal0) * (int64_t)data0);
  local_acc1 += ((int64_t)(coeffVal0) * (int64_t)data1);
  local_acc0 += ((int64_t)(coeffVal1) * (int64_t)data2);
  local_acc1 += ((int64_t)(coeffVal1) * (int64_t)data3);

  coeffVal0 = (*(coeffPtr + 8));
  coeffVal1 = (*(coeffPtr + 9));
  data0 = (*(p1dl_buffPtr - 8));
  data1 = (*(p2dl_buffPtr + 8));
  data2 = (*(p1dl_buffPtr - 9));
  data3 = (*(p2dl_buffPtr + 9));

  local_acc0 += ((int64_t)(coeffVal0) * (int64_t)data0);
  local_acc1 += ((int64_t)(coeffVal0) * (int64_t)data1);
  local_acc0 += ((int64_t)(coeffVal1) * (int64_t)data2);
  local_acc1 += ((int64_t)(coeffVal1) * (int64_t)data3);

  coeffVal0 = (*(coeffPtr + 10));
  coeffVal1 = (*(coeffPtr + 11));
  data0 = (*(p1dl_buffPtr - 10));
  data1 = (*(p2dl_buffPtr + 10));
  data2 = (*(p1dl_buffPtr - 11));
  data3 = (*(p2dl_buffPtr + 11));

  local_acc0 += ((int64_t)(coeffVal0) * (int64_t)data0);
  local_acc1 += ((int64_t)(coeffVal0) * (int64_t)data1);
  local_acc0 += ((int64_t)(coeffVal1) * (int64_t)data2);
  local_acc1 += ((int64_t)(coeffVal1) * (int64_t)data3);

  coeffVal0 = (*(coeffPtr + 12));
  coeffVal1 = (*(coeffPtr + 13));
  data0 = (*(p1dl_buffPtr - 12));
  data1 = (*(p2dl_buffPtr + 12));
  data2 = (*(p1dl_buffPtr - 13));
  data3 = (*(p2dl_buffPtr + 13));

  local_acc0 += ((int64_t)(coeffVal0) * (int64_t)data0);
  local_acc1 += ((int64_t)(coeffVal0) * (int64_t)data1);
  local_acc0 += ((int64_t)(coeffVal1) * (int64_t)data2);
  local_acc1 += ((int64_t)(coeffVal1) * (int64_t)data3);

  coeffVal0 = (*(coeffPtr + 14));
  coeffVal1 = (*(coeffPtr + 15));
  data0 = (*(p1dl_buffPtr - 14));
  data1 = (*(p2dl_buffPtr + 14));
  data2 = (*(p1dl_buffPtr - 15));
  data3 = (*(p2dl_buffPtr + 15));

  local_acc0 += ((int64_t)(coeffVal0) * (int64_t)data0);
  local_acc1 += ((int64_t)(coeffVal0) * (int64_t)data1);
  local_acc0 += ((int64_t)(coeffVal1) * (int64_t)data2);
  local_acc1 += ((int64_t)(coeffVal1) * (int64_t)data3);

  tmp_round0 = (int32_t)local_acc0;

  local_acc0 += 0x00400000L;
  acc = (int32_t)(local_acc0 >> 23);

  if ((((tmp_round0 << 8) ^ 0x40000000) == 0)) {
    acc--;
  }

  if (acc > 8388607) {
    acc = 8388607;
  }
  if (acc < -8388608) {
    acc = -8388608;
  }

  phaseConv[0] = acc;

  tmp_round0 = (int32_t)local_acc1;

  local_acc1 += 0x00400000L;
  acc = (int32_t)(local_acc1 >> 23);
  if ((((tmp_round0 << 8) ^ 0x40000000) == 0)) {
    acc--;
  }

  if (acc > 8388607) {
    acc = 8388607;
  }
  if (acc < -8388608) {
    acc = -8388608;
  }

  phaseConv[1] = acc;

  convSum = phaseConv[1] + phaseConv[0];
  if (convSum > 8388607) {
    convSum = 8388607;
  }
  if (convSum < -8388608) {
    convSum = -8388608;
  }

  convDiff = phaseConv[1] - phaseConv[0];
  if (convDiff > 8388607) {
    convDiff = 8388607;
  }
  if (convDiff < -8388608) {
    convDiff = -8388608;
  }

  *(convSumDiff) = convSum;
  *(convSumDiff + 2) = convDiff;
}

void AsmQmfConvI_HD(const int32_t* p1dl_buffPtr, const int32_t* p2dl_buffPtr,
                    const int32_t* coeffPtr, int32_t* filterOutputs) {
  int32_t acc;
  // WARNING: This inlining assumes that m_qmfDelayLineLength == 16
  int32_t tmp_round0;
  int64_t local_acc0;
  int64_t local_acc1;

  int32_t coeffVal0;
  int32_t coeffVal1;
  int32_t data0;
  int32_t data1;
  int32_t data2;
  int32_t data3;
  int32_t phaseConv[2];
  int32_t convSum;
  int32_t convDiff;

  coeffVal0 = (*(coeffPtr));
  coeffVal1 = (*(coeffPtr + 1));
  data0 = (*(p1dl_buffPtr));
  data1 = (*(p2dl_buffPtr));
  data2 = (*(p1dl_buffPtr - 1));
  data3 = (*(p2dl_buffPtr + 1));

  local_acc0 = ((int64_t)(coeffVal0)*data0);
  local_acc1 = ((int64_t)(coeffVal0)*data1);
  local_acc0 += ((int64_t)(coeffVal1)*data2);
  local_acc1 += ((int64_t)(coeffVal1)*data3);

  coeffVal0 = (*(coeffPtr + 2));
  coeffVal1 = (*(coeffPtr + 3));
  data0 = (*(p1dl_buffPtr - 2));
  data1 = (*(p2dl_buffPtr + 2));
  data2 = (*(p1dl_buffPtr - 3));
  data3 = (*(p2dl_buffPtr + 3));

  local_acc0 += ((int64_t)(coeffVal0)*data0);
  local_acc1 += ((int64_t)(coeffVal0)*data1);
  local_acc0 += ((int64_t)(coeffVal1)*data2);
  local_acc1 += ((int64_t)(coeffVal1)*data3);

  coeffVal0 = (*(coeffPtr + 4));
  coeffVal1 = (*(coeffPtr + 5));
  data0 = (*(p1dl_buffPtr - 4));
  data1 = (*(p2dl_buffPtr + 4));
  data2 = (*(p1dl_buffPtr - 5));
  data3 = (*(p2dl_buffPtr + 5));

  local_acc0 += ((int64_t)(coeffVal0)*data0);
  local_acc1 += ((int64_t)(coeffVal0)*data1);
  local_acc0 += ((int64_t)(coeffVal1)*data2);
  local_acc1 += ((int64_t)(coeffVal1)*data3);

  coeffVal0 = (*(coeffPtr + 6));
  coeffVal1 = (*(coeffPtr + 7));
  data0 = (*(p1dl_buffPtr - 6));
  data1 = (*(p2dl_buffPtr + 6));
  data2 = (*(p1dl_buffPtr - 7));
  data3 = (*(p2dl_buffPtr + 7));

  local_acc0 += ((int64_t)(coeffVal0)*data0);
  local_acc1 += ((int64_t)(coeffVal0)*data1);
  local_acc0 += ((int64_t)(coeffVal1)*data2);
  local_acc1 += ((int64_t)(coeffVal1)*data3);

  coeffVal0 = (*(coeffPtr + 8));
  coeffVal1 = (*(coeffPtr + 9));
  data0 = (*(p1dl_buffPtr - 8));
  data1 = (*(p2dl_buffPtr + 8));
  data2 = (*(p1dl_buffPtr - 9));
  data3 = (*(p2dl_buffPtr + 9));

  local_acc0 += ((int64_t)(coeffVal0)*data0);
  local_acc1 += ((int64_t)(coeffVal0)*data1);
  local_acc0 += ((int64_t)(coeffVal1)*data2);
  local_acc1 += ((int64_t)(coeffVal1)*data3);

  coeffVal0 = (*(coeffPtr + 10));
  coeffVal1 = (*(coeffPtr + 11));
  data0 = (*(p1dl_buffPtr - 10));
  data1 = (*(p2dl_buffPtr + 10));
  data2 = (*(p1dl_buffPtr - 11));
  data3 = (*(p2dl_buffPtr + 11));

  local_acc0 += ((int64_t)(coeffVal0)*data0);
  local_acc1 += ((int64_t)(coeffVal0)*data1);
  local_acc0 += ((int64_t)(coeffVal1)*data2);
  local_acc1 += ((int64_t)(coeffVal1)*data3);

  coeffVal0 = (*(coeffPtr + 12));
  coeffVal1 = (*(coeffPtr + 13));
  data0 = (*(p1dl_buffPtr - 12));
  data1 = (*(p2dl_buffPtr + 12));
  data2 = (*(p1dl_buffPtr - 13));
  data3 = (*(p2dl_buffPtr + 13));

  local_acc0 += ((int64_t)(coeffVal0)*data0);
  local_acc1 += ((int64_t)(coeffVal0)*data1);
  local_acc0 += ((int64_t)(coeffVal1)*data2);
  local_acc1 += ((int64_t)(coeffVal1)*data3);

  coeffVal0 = (*(coeffPtr + 14));
  coeffVal1 = (*(coeffPtr + 15));
  data0 = (*(p1dl_buffPtr - 14));
  data1 = (*(p2dl_buffPtr + 14));
  data2 = (*(p1dl_buffPtr - 15));
  data3 = (*(p2dl_buffPtr + 15));

  local_acc0 += ((int64_t)(coeffVal0)*data0);
  local_acc1 += ((int64_t)(coeffVal0)*data1);
  local_acc0 += ((int64_t)(coeffVal1)*data2);
  local_acc1 += ((int64_t)(coeffVal1)*data3);

  tmp_round0 = (int32_t)local_acc0;

  local_acc0 += 0x00400000L;
  acc = (int32_t)(local_acc0 >> 23);

  if ((((tmp_round0 << 8) ^ 0x40000000) == 0)) {
    acc--;
  }

  if (acc > 8388607) {
    acc = 8388607;
  }
  if (acc < -8388608) {
    acc = -8388608;
  }

  phaseConv[0] = acc;

  tmp_round0 = (int32_t)local_acc1;

  local_acc1 += 0x00400000L;
  acc = (int32_t)(local_acc1 >> 23);
  if ((((tmp_round0 << 8) ^ 0x40000000) == 0)) {
    acc--;
  }

  if (acc > 8388607) {
    acc = 8388607;
  }
  if (acc < -8388608) {
    acc = -8388608;
  }

  phaseConv[1] = acc;

  convSum = phaseConv[1] + phaseConv[0];
  if (convSum > 8388607) {
    convSum = 8388607;
  }
  if (convSum < -8388608) {
    convSum = -8388608;
  }

  *(filterOutputs) = convSum;

  convDiff = phaseConv[1] - phaseConv[0];
  if (convDiff > 8388607) {
    convDiff = 8388607;
  }
  if (convDiff < -8388608) {
    convDiff = -8388608;
  }

  *(filterOutputs + 1) = convDiff;
}
