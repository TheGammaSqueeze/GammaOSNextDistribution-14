/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <gtest/gtest.h>
#include <stddef.h>

#include <string.h>

#include "chpp/common/wwan_types.h"
#include "chpp/memory.h"

namespace {

// clang-format off
const chreWwanCellInfo kChreCells[] = {
  {
    .timeStamp = 1234,
    .cellInfoType = CHRE_WWAN_CELL_INFO_TYPE_LTE,
    .timeStampType = CHRE_WWAN_CELL_TIMESTAMP_TYPE_MODEM,
    .registered = 1,
    .reserved = 111,  // ignored
    .CellInfo = {
      .lte = {
        .cellIdentityLte = {
          .mcc = 777,
          .mnc = 888,
          .ci = 4321,
          .pci = 333,
          .tac = 9876,
          .earfcn = 5432,
        },
        .signalStrengthLte = {
          .signalStrength = 27,
          .rsrp = 96,
          .rsrq = 18,
          .rssnr = 157,
          .cqi = 13,
          .timingAdvance = INT32_MAX,
        }
      }
    }
  },
  {
    .timeStamp = 1235,
    .cellInfoType = CHRE_WWAN_CELL_INFO_TYPE_WCDMA,
    .timeStampType = CHRE_WWAN_CELL_TIMESTAMP_TYPE_ANTENNA,
    .registered = 0,
    .CellInfo = {
      .wcdma = {
        .cellIdentityWcdma = {
          .mcc = 123,
          .mnc = 456,
          .lac = 789,
          .cid = 012,
          .psc = 345,
          .uarfcn = 678,
        },
        .signalStrengthWcdma = {
          .signalStrength = 99,
          .bitErrorRate = INT32_MAX,
        }
      }
    }
  },
};
const chreWwanCellInfoResult kChreResultOneCell = {
  .errorCode = 0,
  .cellInfoCount = 1,
  .cells = kChreCells,
};
const chreWwanCellInfoResult kChreResultTwoCell = {
  .errorCode = 0,
  .cellInfoCount = 2,
  .cells = kChreCells,
};
// clang-format on

void compareCellInfo(const chreWwanCellInfo *chre,
                     const ChppWwanCellInfo *chppIn) {
  // Local variable needed to avoid unaligned memory access.
  ChppWwanCellInfo chppLocal;
  memcpy(&chppLocal, chppIn, sizeof(ChppWwanCellInfo));
  const ChppWwanCellInfo *chpp = &chppLocal;

  EXPECT_EQ(chpp->timeStamp, chre->timeStamp);
  EXPECT_EQ(chpp->cellInfoType, chre->cellInfoType);
  EXPECT_EQ(chpp->timeStampType, chre->timeStampType);
  EXPECT_EQ(chpp->registered, chre->registered);

  switch (chpp->cellInfoType) {
    case CHRE_WWAN_CELL_INFO_TYPE_LTE:
      EXPECT_EQ(chpp->CellInfo.lte.cellIdentityLte.mcc,
                chre->CellInfo.lte.cellIdentityLte.mcc);
      EXPECT_EQ(chpp->CellInfo.lte.cellIdentityLte.mnc,
                chre->CellInfo.lte.cellIdentityLte.mnc);
      EXPECT_EQ(chpp->CellInfo.lte.cellIdentityLte.ci,
                chre->CellInfo.lte.cellIdentityLte.ci);
      EXPECT_EQ(chpp->CellInfo.lte.cellIdentityLte.pci,
                chre->CellInfo.lte.cellIdentityLte.pci);
      EXPECT_EQ(chpp->CellInfo.lte.cellIdentityLte.tac,
                chre->CellInfo.lte.cellIdentityLte.tac);
      EXPECT_EQ(chpp->CellInfo.lte.cellIdentityLte.earfcn,
                chre->CellInfo.lte.cellIdentityLte.earfcn);

      EXPECT_EQ(chpp->CellInfo.lte.signalStrengthLte.signalStrength,
                chre->CellInfo.lte.signalStrengthLte.signalStrength);
      EXPECT_EQ(chpp->CellInfo.lte.signalStrengthLte.rsrp,
                chre->CellInfo.lte.signalStrengthLte.rsrp);
      EXPECT_EQ(chpp->CellInfo.lte.signalStrengthLte.rsrq,
                chre->CellInfo.lte.signalStrengthLte.rsrq);
      EXPECT_EQ(chpp->CellInfo.lte.signalStrengthLte.rssnr,
                chre->CellInfo.lte.signalStrengthLte.rssnr);
      EXPECT_EQ(chpp->CellInfo.lte.signalStrengthLte.cqi,
                chre->CellInfo.lte.signalStrengthLte.cqi);
      EXPECT_EQ(chpp->CellInfo.lte.signalStrengthLte.timingAdvance,
                chre->CellInfo.lte.signalStrengthLte.timingAdvance);
      break;

    case CHRE_WWAN_CELL_INFO_TYPE_WCDMA:
      EXPECT_EQ(chpp->CellInfo.wcdma.cellIdentityWcdma.mcc,
                chre->CellInfo.wcdma.cellIdentityWcdma.mcc);
      EXPECT_EQ(chpp->CellInfo.wcdma.cellIdentityWcdma.mnc,
                chre->CellInfo.wcdma.cellIdentityWcdma.mnc);
      EXPECT_EQ(chpp->CellInfo.wcdma.cellIdentityWcdma.lac,
                chre->CellInfo.wcdma.cellIdentityWcdma.lac);
      EXPECT_EQ(chpp->CellInfo.wcdma.cellIdentityWcdma.cid,
                chre->CellInfo.wcdma.cellIdentityWcdma.cid);
      EXPECT_EQ(chpp->CellInfo.wcdma.cellIdentityWcdma.psc,
                chre->CellInfo.wcdma.cellIdentityWcdma.psc);
      EXPECT_EQ(chpp->CellInfo.wcdma.cellIdentityWcdma.uarfcn,
                chre->CellInfo.wcdma.cellIdentityWcdma.uarfcn);

      EXPECT_EQ(chpp->CellInfo.wcdma.signalStrengthWcdma.signalStrength,
                chre->CellInfo.wcdma.signalStrengthWcdma.signalStrength);
      EXPECT_EQ(chpp->CellInfo.wcdma.signalStrengthWcdma.bitErrorRate,
                chre->CellInfo.wcdma.signalStrengthWcdma.bitErrorRate);
      break;

    default:
      // Other types not supported yet
      ASSERT_TRUE(false);
  }
}

//! Test for correct handling of a malformed ChppWwanCellInfoResult (e.g. short
//! payload, invalid offset/length)
void testMalformedCellInfoResultDecoding(struct ChppWwanCellInfoResult *chpp,
                                         size_t inputSize) {
  chreWwanCellInfoResult *chreMalformed;

  inputSize--;
  chreMalformed = chppWwanCellInfoResultToChre(chpp, inputSize);
  EXPECT_EQ(chreMalformed, nullptr);
  inputSize++;

  chpp->cells.offset++;
  chreMalformed = chppWwanCellInfoResultToChre(chpp, inputSize);
  EXPECT_EQ(chreMalformed, nullptr);
  chpp->cells.offset--;

  chpp->cells.length++;
  chreMalformed = chppWwanCellInfoResultToChre(chpp, inputSize);
  EXPECT_EQ(chreMalformed, nullptr);
  chpp->cells.length--;

  chpp->cellInfoCount++;
  chreMalformed = chppWwanCellInfoResultToChre(chpp, inputSize);
  EXPECT_EQ(chreMalformed, nullptr);
  chpp->cellInfoCount--;

  chpp->cellInfoCount--;
  chreMalformed = chppWwanCellInfoResultToChre(chpp, inputSize);
  EXPECT_EQ(chreMalformed, nullptr);
  chpp->cellInfoCount++;
}

TEST(WwanConvert, EncodeErrorCode) {
  const chreWwanCellInfoResult chreResult = {
      .version = 200,  // ignored
      .errorCode = 2,
      .cellInfoCount = 0,
      .reserved = 3,         // ignored
      .cookie = (void *)-1,  // ignored
      .cells = nullptr,
  };

  ChppWwanCellInfoResultWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;
  bool result =
      chppWwanCellInfoResultFromChre(&chreResult, &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);
  EXPECT_EQ(outputSize, sizeof(ChppWwanCellInfoResultWithHeader));

  ChppWwanCellInfoResult *chpp = &chppWithHeader->payload;
  EXPECT_EQ(chpp->version, CHRE_WWAN_CELL_INFO_RESULT_VERSION);
  EXPECT_EQ(chpp->errorCode, chreResult.errorCode);
  EXPECT_EQ(chpp->cellInfoCount, chreResult.cellInfoCount);
  EXPECT_EQ(chpp->reserved, 0);
  EXPECT_EQ(chpp->cookie, 0u);
  EXPECT_EQ(chpp->cells.offset, 0);
  EXPECT_EQ(chpp->cells.length, 0);

  chppFree(chppWithHeader);
}

TEST(WwanConvert, DecodeErrorCode) {
  const ChppWwanCellInfoResult chpp = {
      .version = 200,  // ignored
      .errorCode = 2,
      .cellInfoCount = 0,
      .reserved = 3,  // ignored
      .cookie = 100,  // ignored
      .cells.offset = 0,
      .cells.length = 0,
  };

  size_t outputSize = sizeof(struct ChppWwanCellInfoResult);
  chreWwanCellInfoResult *chre =
      chppWwanCellInfoResultToChre(&chpp, outputSize);
  ASSERT_NE(chre, nullptr);

  EXPECT_EQ(chre->version, CHRE_WWAN_CELL_INFO_RESULT_VERSION);
  EXPECT_EQ(chre->errorCode, chpp.errorCode);
  EXPECT_EQ(chre->cellInfoCount, chpp.cellInfoCount);
  EXPECT_EQ(chre->reserved, 0);
  EXPECT_EQ(chre->cookie, (void *)0);

  // Handling of short payload
  chreWwanCellInfoResult *chreShort =
      chppWwanCellInfoResultToChre(&chpp, outputSize - 1);
  ASSERT_EQ(chreShort, nullptr);

  chppFree(chre);
}

TEST(WwanConvert, EncodeOneCell) {
  ChppWwanCellInfoResultWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;
  bool result = chppWwanCellInfoResultFromChre(&kChreResultOneCell,
                                               &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);
  EXPECT_EQ(outputSize, sizeof(ChppWwanCellInfoResultWithHeader) +
                            sizeof(ChppWwanCellInfo));

  ChppWwanCellInfoResult *chpp = &chppWithHeader->payload;
  EXPECT_EQ(chpp->errorCode, kChreResultOneCell.errorCode);
  EXPECT_EQ(chpp->cellInfoCount, kChreResultOneCell.cellInfoCount);
  EXPECT_EQ(chpp->cells.offset, sizeof(ChppWwanCellInfoResult));
  EXPECT_EQ(chpp->cells.length, sizeof(ChppWwanCellInfo));

  ChppWwanCellInfo *chppCell =
      (ChppWwanCellInfo *)((uint8_t *)chpp + chpp->cells.offset);
  EXPECT_EQ(chppCell->reserved, 0);
  compareCellInfo(&kChreCells[0], chppCell);

  chppFree(chppWithHeader);
}

TEST(WwanConvert, EncodeDecodeOneCell) {
  ChppWwanCellInfoResultWithHeader *chppWithHeader;
  size_t outputSize;
  bool result = chppWwanCellInfoResultFromChre(&kChreResultOneCell,
                                               &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);

  ChppWwanCellInfoResult *chpp = &chppWithHeader->payload;
  ChppWwanCellInfo *chppCell =
      (ChppWwanCellInfo *)((uint8_t *)chpp + chpp->cells.offset);

  chpp->version = 100;  // ignored
  chpp->reserved = 10;  // ignored
  chpp->cookie = -10;   // ignored

  size_t inputSize = outputSize - sizeof(struct ChppAppHeader);

  chreWwanCellInfoResult *chre = chppWwanCellInfoResultToChre(chpp, inputSize);
  ASSERT_NE(chre, nullptr);

  EXPECT_EQ(chre->version, CHRE_WWAN_CELL_INFO_RESULT_VERSION);
  EXPECT_EQ(chre->reserved, 0);
  EXPECT_EQ(chre->cookie, (void *)0);

  EXPECT_EQ(chre->errorCode, chpp->errorCode);
  EXPECT_EQ(chre->errorCode, kChreResultOneCell.errorCode);
  EXPECT_EQ(chre->cellInfoCount, chpp->cellInfoCount);
  EXPECT_EQ(chre->cellInfoCount, kChreResultOneCell.cellInfoCount);

  EXPECT_EQ(chre->cells[0].reserved, 0);
  compareCellInfo(&kChreCells[0], chppCell);
  compareCellInfo(&chre->cells[0], chppCell);

  testMalformedCellInfoResultDecoding(chpp, inputSize);

  chppFree(chppWithHeader);
  chppFree(chre);
}

TEST(WwanConvert, EncodeTwoCells) {
  ChppWwanCellInfoResultWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;
  bool result = chppWwanCellInfoResultFromChre(&kChreResultTwoCell,
                                               &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);
  EXPECT_EQ(outputSize, sizeof(ChppWwanCellInfoResultWithHeader) +
                            2 * sizeof(ChppWwanCellInfo));

  ChppWwanCellInfoResult *chpp = &chppWithHeader->payload;
  EXPECT_EQ(chpp->errorCode, kChreResultTwoCell.errorCode);
  EXPECT_EQ(chpp->cellInfoCount, kChreResultTwoCell.cellInfoCount);
  EXPECT_EQ(chpp->cells.offset, sizeof(ChppWwanCellInfoResult));
  EXPECT_EQ(chpp->cells.length, 2 * sizeof(ChppWwanCellInfo));

  ChppWwanCellInfo *chppCells =
      (ChppWwanCellInfo *)((uint8_t *)chpp + chpp->cells.offset);
  EXPECT_EQ(chppCells[0].reserved, 0);
  EXPECT_EQ(chppCells[1].reserved, 0);
  compareCellInfo(&kChreCells[0], &chppCells[0]);
  compareCellInfo(&kChreCells[1], &chppCells[1]);

  // Ensure unused bytes in the union are zeroed out
  uint8_t *pastEnd =
      (uint8_t *)&chppCells[1].CellInfo.wcdma.signalStrengthWcdma.bitErrorRate +
      sizeof(chppCells[1].CellInfo.wcdma.signalStrengthWcdma.bitErrorRate);
  size_t sizePastEnd = sizeof(chreWwanCellInfo::chreWwanCellInfoPerRat) -
                       sizeof(chreWwanCellInfoWcdma);
  uint8_t zeros[sizePastEnd];
  memset(zeros, 0, sizePastEnd);
  EXPECT_EQ(memcmp(pastEnd, zeros, sizeof(zeros)), 0);

  chppFree(chppWithHeader);
}

TEST(WwanConvert, EncodeDecodeTwoCells) {
  ChppWwanCellInfoResultWithHeader *chppWithHeader;
  size_t outputSize;
  bool result = chppWwanCellInfoResultFromChre(&kChreResultTwoCell,
                                               &chppWithHeader, &outputSize);
  ASSERT_TRUE(result);
  ASSERT_NE(chppWithHeader, nullptr);

  ChppWwanCellInfoResult *chpp = &chppWithHeader->payload;
  ChppWwanCellInfo *chppCells =
      (ChppWwanCellInfo *)((uint8_t *)chpp + chpp->cells.offset);

  chpp->version = 100;  // ignored
  chpp->reserved = 10;  // ignored
  chpp->cookie = -10;   // ignored

  size_t inputSize = outputSize - sizeof(struct ChppAppHeader);

  chreWwanCellInfoResult *chre = chppWwanCellInfoResultToChre(chpp, inputSize);
  ASSERT_NE(chre, nullptr);

  EXPECT_EQ(chre->version, CHRE_WWAN_CELL_INFO_RESULT_VERSION);
  EXPECT_EQ(chre->reserved, 0);
  EXPECT_EQ(chre->cookie, (void *)0);

  EXPECT_EQ(chre->errorCode, chpp->errorCode);
  EXPECT_EQ(chpp->errorCode, kChreResultTwoCell.errorCode);
  EXPECT_EQ(chre->cellInfoCount, chpp->cellInfoCount);
  EXPECT_EQ(chpp->cellInfoCount, kChreResultTwoCell.cellInfoCount);

  EXPECT_EQ(chre->cells[0].reserved, 0);
  compareCellInfo(&kChreCells[0], &chppCells[0]);
  compareCellInfo(&chre->cells[0], &chppCells[0]);

  EXPECT_EQ(chre->cells[1].reserved, 0);
  compareCellInfo(&kChreCells[1], &chppCells[1]);
  compareCellInfo(&chre->cells[1], &chppCells[1]);

  testMalformedCellInfoResultDecoding(chpp, inputSize);

  chppFree(chppWithHeader);
  chppFree(chre);
}

}  // namespace
