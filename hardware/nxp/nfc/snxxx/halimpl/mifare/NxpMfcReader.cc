/******************************************************************************
 *
 *  Copyright 2019-2023 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#include "NxpMfcReader.h"

#include <log/log.h>
#include <phNfcCompId.h>
#include <phNxpLog.h>
#include <phNxpNciHal_Adaptation.h>
#include <phNxpNciHal_ext.h>

#include "phNxpNciHal.h"

extern bool sendRspToUpperLayer;
extern bool bEnableMfcExtns;

NxpMfcReader& NxpMfcReader::getInstance() {
  static NxpMfcReader msNxpMfcReader;
  return msNxpMfcReader;
}

/*******************************************************************************
**
** Function         Write
**
** Description      Wrapper API to handle Mifare Transceive to TAG_CMD interface
**                  RAW read write.
**
** Returns          It returns number of bytes successfully written to NFCC.
**
*******************************************************************************/
int NxpMfcReader::Write(uint16_t mfcDataLen, const uint8_t* pMfcData) {
  // Eg:- From the App pMfcData- {|PART1-00 00 06 C1 04| PART2-01 00 00 00|}
  uint16_t mfcTagCmdBuffLen = 0;
  uint8_t mfcTagCmdBuff[MAX_MFC_BUFF_SIZE] = {0};
  uint16_t mfcTagCmdRemainingCmdLen = mfcDataLen;

  if (mfcDataLen > MAX_MFC_BUFF_SIZE) {
    android_errorWriteLog(0x534e4554, "169259605");
    mfcDataLen = MAX_MFC_BUFF_SIZE;
  } else if (mfcDataLen < MIN_MFC_BUFF_SIZE) {
    android_errorWriteLog(0x534e4554, "287677822");
    NXPLOG_NCIHAL_E("%s: mfcDataLen is below 4 bytes", __func__);
    return 0;
  }
  memcpy(mfcTagCmdBuff, pMfcData, mfcDataLen);
  if (mfcDataLen >= 3) mfcTagCmdBuffLen = mfcDataLen - NCI_HEADER_SIZE;
  BuildMfcCmd(&mfcTagCmdBuff[3], &mfcTagCmdBuffLen);

  mfcTagCmdBuff[2] = mfcTagCmdBuffLen;

  if (checkIsMFCIncDecRestore(pMfcData[3])) {
    if (sem_init(&mNacksem, 0, 0) != 0) {
      NXPLOG_NCIHAL_E("%s : sem_init failed", __func__);
      return 0;
    }
  }
  int writtenDataLen = phNxpNciHal_write_internal(
      mfcTagCmdBuffLen + NCI_HEADER_SIZE, mfcTagCmdBuff);

  /* send TAG_CMD part 2 for Mifare increment ,decrement and restore commands */
  if (checkIsMFCIncDecRestore(pMfcData[3])) {
    MfcWaitForAck();
    if (isAck) {
      NXPLOG_NCIHAL_D("part 1 command Acked");
      SendIncDecRestoreCmdPart2(
          mfcTagCmdRemainingCmdLen - MFC_TAG_INCR_DECR_CMD_PART1_LEN,
          &pMfcData[0]);
    } else {
      NXPLOG_NCIHAL_E("part 1 command NACK");
    }
    sem_destroy(&mNacksem);
  }
  return writtenDataLen;
}

/*******************************************************************************
**
** Function         BuildMfcCmd
**
** Description      builds the TAG CMD for Mifare Classic Tag.
**
** Returns          None
**
*******************************************************************************/
void NxpMfcReader::BuildMfcCmd(uint8_t* pData, uint16_t* pLength) {
  uint16_t cmdBuffLen = *pLength;
  memcpy(mMfcTagCmdIntfData.sendBuf, pData, cmdBuffLen);
  mMfcTagCmdIntfData.sendBufLen = cmdBuffLen;

  switch (pData[0]) {
    case eMifareAuthentA:
    case eMifareAuthentB:
      BuildAuthCmd();
      break;
    case eMifareRead16:
      BuildReadCmd();
      break;
    case eMifareWrite16:
      AuthForWrite();
      BuildWrite16Cmd();
      break;
    case eMifareInc:
    case eMifareDec:
      BuildIncDecCmd();
      break;
    default:
      BuildRawCmd();
      break;
  }

  memcpy(pData, mMfcTagCmdIntfData.sendBuf, (mMfcTagCmdIntfData.sendBufLen));
  *pLength = (mMfcTagCmdIntfData.sendBufLen);
  return;
}

/*******************************************************************************
**
** Function         BuildAuthCmd
**
** Description      builds the TAG CMD for Mifare Auth.
**
** Returns          None
**
*******************************************************************************/
void NxpMfcReader::BuildAuthCmd() {
  uint8_t byKey = 0x00, noOfKeys = 0x00;
  bool isPreloadedKey = false;

  if (mMfcTagCmdIntfData.sendBuf[0] == eMifareAuthentB) {
    byKey |= MFC_ENABLE_KEY_B;
  }
  uint8_t aMfckeys[MFC_NUM_OF_KEYS][MFC_KEY_SIZE] = MFC_KEYS;
  noOfKeys = sizeof(aMfckeys) / MFC_KEY_SIZE;
  for (uint8_t byIndex = 0; byIndex < noOfKeys; byIndex++) {
    if ((memcmp(aMfckeys[byIndex], &mMfcTagCmdIntfData.sendBuf[6],
                MFC_AUTHKEYLEN) == 0x00)) {
      byKey = byKey | byIndex;
      isPreloadedKey = true;
      break;
    }
  }
  CalcSectorAddress();
  mMfcTagCmdIntfData.sendBufLen = 0x03;
  if (!isPreloadedKey) {
    byKey |= MFC_EMBEDDED_KEY;
    memmove(&mMfcTagCmdIntfData.sendBuf[3], &mMfcTagCmdIntfData.sendBuf[6],
            MFC_AUTHKEYLEN);
    mMfcTagCmdIntfData.sendBufLen += MFC_AUTHKEYLEN;
  }

  mMfcTagCmdIntfData.sendBuf[0] = eMfcAuthReq;
  mMfcTagCmdIntfData.sendBuf[1] = mMfcTagCmdIntfData.byAddr;
  mMfcTagCmdIntfData.sendBuf[2] = byKey;
  return;
}

/*******************************************************************************
**
** Function         CalcSectorAddress
**
** Description      This function update the sector address for Mifare classic
**
** Returns          None
**
*******************************************************************************/
void NxpMfcReader::CalcSectorAddress() {
  uint8_t BlockNumber = mMfcTagCmdIntfData.sendBuf[1];
  if (BlockNumber >= MFC_4K_BLK128) {
    mMfcTagCmdIntfData.byAddr =
        (uint8_t)(MFC_SECTOR_NO32 +
                  ((BlockNumber - MFC_4K_BLK128) / MFC_BYTES_PER_BLOCK));
  } else {
    mMfcTagCmdIntfData.byAddr = BlockNumber / MFC_BLKS_PER_SECTOR;
  }

  return;
}

/*******************************************************************************
**
** Function         BuildReadCmd
**
** Description      builds the TAG CMD for Mifare Read.
**
** Returns          None
**
*******************************************************************************/
void NxpMfcReader::BuildReadCmd() { BuildRawCmd(); }

/*******************************************************************************
**
** Function         checkIsMFCIncDecRestore
**
** Description      Check command is MF Increment/Decrement or Restore.
**
** Returns          True/False
**
*******************************************************************************/
bool NxpMfcReader::checkIsMFCIncDecRestore(uint8_t cmdInst) {
  return (cmdInst == eMifareDec || cmdInst == eMifareInc ||
          cmdInst == eMifareRestore);
}

/*******************************************************************************
**
** Function         BuildWrite16Cmd
**
** Description      builds the TAG CMD for Mifare write part 2.
**
** Returns          None
**
*******************************************************************************/
void NxpMfcReader::BuildWrite16Cmd() {
  mMfcTagCmdIntfData.sendBuf[0] = eMfRawDataXchgHdr;
  mMfcTagCmdIntfData.sendBufLen = mMfcTagCmdIntfData.sendBufLen - 1;
  uint8_t buff[mMfcTagCmdIntfData.sendBufLen];
  memset(buff, 0, mMfcTagCmdIntfData.sendBufLen);
  memcpy(buff, mMfcTagCmdIntfData.sendBuf + 2,
         (mMfcTagCmdIntfData.sendBufLen - 1));
  memcpy(mMfcTagCmdIntfData.sendBuf + 1, buff,
         (mMfcTagCmdIntfData.sendBufLen - 1));
}

/*******************************************************************************
**
** Function         BuildRawCmd
**
** Description      builds the TAG CMD for Raw transceive.
**
** Returns          None
**
*******************************************************************************/
void NxpMfcReader::BuildRawCmd() {
  mMfcTagCmdIntfData.sendBufLen = mMfcTagCmdIntfData.sendBufLen + 1;
  uint8_t buff[mMfcTagCmdIntfData.sendBufLen];
  memset(buff, 0, mMfcTagCmdIntfData.sendBufLen);
  memcpy(buff, mMfcTagCmdIntfData.sendBuf, mMfcTagCmdIntfData.sendBufLen);
  memcpy(mMfcTagCmdIntfData.sendBuf + 1, buff, mMfcTagCmdIntfData.sendBufLen);
  mMfcTagCmdIntfData.sendBuf[0] = eMfRawDataXchgHdr;
}

/*******************************************************************************
**
** Function         BuildIncDecCmd
**
** Description      builds the TAG CMD for Mifare Inc/Dec.
**
** Returns          None
**
*******************************************************************************/
void NxpMfcReader::BuildIncDecCmd() {
  mMfcTagCmdIntfData.sendBufLen = 0x03;  // eMfRawDataXchgHdr + cmd +
                                         // blockaddress
  uint8_t buff[mMfcTagCmdIntfData.sendBufLen];
  memset(buff, 0, mMfcTagCmdIntfData.sendBufLen);
  memcpy(buff, mMfcTagCmdIntfData.sendBuf, mMfcTagCmdIntfData.sendBufLen);
  memcpy(mMfcTagCmdIntfData.sendBuf + 1, buff, mMfcTagCmdIntfData.sendBufLen);
  mMfcTagCmdIntfData.sendBuf[0] = eMfRawDataXchgHdr;
}

/*******************************************************************************
**
** Function         AuthForWrite
**
** Description      send Mifare write Part 1.
**
** Returns          None
**
*******************************************************************************/
void NxpMfcReader::AuthForWrite() {
  sendRspToUpperLayer = false;
  NFCSTATUS status = NFCSTATUS_FAILED;
  uint8_t authForWriteBuff[] = {0x00,
                                0x00,
                                0x03,
                                (uint8_t)eMfRawDataXchgHdr,
                                (uint8_t)mMfcTagCmdIntfData.sendBuf[0],
                                (uint8_t)mMfcTagCmdIntfData.sendBuf[1]};

  status = phNxpNciHal_send_ext_cmd(
      sizeof(authForWriteBuff) / sizeof(authForWriteBuff[0]), authForWriteBuff);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Mifare Auth for Transceive failed");
  }
  return;
}

/*******************************************************************************
**
** Function         SendIncDecRestoreCmdPart2
**
** Description      send Mifare Inc/Dec/Restore Command Part 2.
**
** Returns          None
**
*******************************************************************************/
void NxpMfcReader::SendIncDecRestoreCmdPart2(uint16_t mfcDataLen,
                                             const uint8_t* mfcData) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  bool isError = false;
  /* Build TAG_CMD part 2 for Mifare increment ,decrement and restore commands*/
  uint8_t incDecRestorePart2[] = {0x00, 0x00, 0x05, (uint8_t)eMfRawDataXchgHdr,
                                  0x00, 0x00, 0x00, 0x00};
  uint8_t incDecRestorePart2Size =
      (sizeof(incDecRestorePart2) / sizeof(incDecRestorePart2[0]));
  if (mfcData[3] == eMifareInc || mfcData[3] == eMifareDec) {
    if (mfcDataLen > MFC_TAG_INCR_DECR_CMD_PART2_LEN) {
      isError = true;
      incDecRestorePart2Size = MFC_TAG_INCR_DECR_CMD_PART2_LEN;
    } else if (mfcDataLen < MFC_TAG_INCR_DECR_CMD_PART2_LEN) {
      isError = true;
      incDecRestorePart2Size = mfcDataLen;
    }
  }
  if (isError) {
    android_errorWriteLog(0x534e4554, "238177877");
  }
  for (int i = 4; i < incDecRestorePart2Size; i++) {
    incDecRestorePart2[i] = mfcData[i + 1];
  }
  sendRspToUpperLayer = false;
  status = phNxpNciHal_send_ext_cmd(incDecRestorePart2Size, incDecRestorePart2);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Mifare Cmd for inc/dec/Restore part 2 failed");
  }
  return;
}

/*******************************************************************************
**
** Function          AnalyzeMfcResp
**
** Description      Analyze type of MFC response and build MFC response from
**                  Tag cmd Intf response?
**
** Returns          NFCSTATUS_SUCCESS - Data Reception is successful
**                  NFCSTATUS_FAILED  - Data Reception failed
**
*******************************************************************************/
NFCSTATUS NxpMfcReader::AnalyzeMfcResp(uint8_t* pBuff, uint16_t* pBufflen) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  uint16_t wPldDataSize = 0;
  MfcRespId_t RecvdExtnRspId = eInvalidRsp;

  if (0 == (*pBufflen)) {
    status = NFCSTATUS_FAILED;
  } else {
    RecvdExtnRspId = (MfcRespId_t)pBuff[0];
    NXPLOG_NCIHAL_E("%s: RecvdExtnRspId=%d", __func__, RecvdExtnRspId);
    switch (RecvdExtnRspId) {
      case eMfXchgDataRsp: {
        NFCSTATUS writeRespStatus = NFCSTATUS_SUCCESS;
        /* check the status byte */
        if (*pBufflen == 3) {
          if ((pBuff[0] == 0x10) && (pBuff[1] != 0x0A)) {
            NXPLOG_NCIHAL_E("Mifare Error in payload response");
            *pBufflen = 0x1;
            pBuff[0] = NFCSTATUS_FAILED;
            return NFCSTATUS_FAILED;
          } else {
            pBuff[0] = NFCSTATUS_SUCCESS;
            return NFCSTATUS_SUCCESS;
          }
        }
        writeRespStatus = pBuff[*pBufflen - 1];

        if (NFCSTATUS_SUCCESS == writeRespStatus) {
          status = NFCSTATUS_SUCCESS;
          uint16_t wRecvDataSz = 0;

          wPldDataSize =
              ((*pBufflen) - (MFC_EXTN_ID_SIZE + MFC_EXTN_STATUS_SIZE));
          wRecvDataSz = MAX_MFC_BUFF_SIZE;
          if ((wPldDataSize) <= wRecvDataSz) {
            /* Extract the data part from pBuff[2] & fill it to be sent to
             * upper layer */
            memcpy(&(pBuff[0]), &(pBuff[1]), wPldDataSize);
            /* update the number of bytes received from lower layer,excluding
             * the status byte */
            *pBufflen = wPldDataSize;
          } else {
            status = NFCSTATUS_FAILED;
          }
        } else {
          status = NFCSTATUS_FAILED;
        }
      } break;

      case eMfcAuthRsp: {
        if (*pBufflen < 2) {
          status = NFCSTATUS_FAILED;
          break;
        }
        /* check the status byte */
        if (NFCSTATUS_SUCCESS == pBuff[1]) {
          status = NFCSTATUS_SUCCESS;
          /* DataLen = TotalRecvdLen - (sizeof(RspId) + sizeof(Status)) */
          wPldDataSize =
              ((*pBufflen) - (MFC_EXTN_ID_SIZE + MFC_EXTN_STATUS_SIZE));
          /* Extract the data part from pBuff[2] & fill it to be sent to upper
           * layer */
          pBuff[0] = pBuff[1];
          /* update the number of bytes received from lower layer,excluding
           * the status byte */
          *pBufflen = wPldDataSize + 1;
        } else {
          pBuff[0] = pBuff[1];
          *pBufflen = 1;
          status = NFCSTATUS_FAILED;
        }
      } break;
      default: {
        status = NFCSTATUS_FAILED;
      } break;
    }
  }
  return status;
}

/*******************************************************************************
**
** Function         CheckMfcResponse
**
** Description      This function is called to check if it's a valid Mfc
**                  response data
**
** Returns          NFCSTATUS_SUCCESS
**                  NFCSTATUS_FAILED
**
*******************************************************************************/
NFCSTATUS NxpMfcReader::CheckMfcResponse(uint8_t* pTransceiveData,
                                         uint16_t transceiveDataLen) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;

  if (transceiveDataLen == 3) {
    if ((pTransceiveData)[0] == 0x10 && (pTransceiveData)[1] != 0x0A) {
      NXPLOG_NCIHAL_E("Mifare Error in payload response");
      transceiveDataLen = 0x1;
      pTransceiveData += 1;
      return NFCSTATUS_FAILED;
    }
  }
  if ((pTransceiveData)[0] == 0x40) {
    pTransceiveData += 1;
    transceiveDataLen = 0x01;
    if ((pTransceiveData)[0] == 0x03) {
      transceiveDataLen = 0x00;
      status = NFCSTATUS_FAILED;
    }
  } else if ((pTransceiveData)[0] == 0x10) {
    pTransceiveData += 1;
    transceiveDataLen = 0x10;
  }
  return status;
}

/*******************************************************************************
**
** Function         MfcAckReceived
**
** Description      This function is called to notify that MFC
**                  response data is received
**
** Returns          NFCSTATUS_SUCCESS
**                  NFCSTATUS_FAILED
**
*******************************************************************************/
void NxpMfcReader::MfcNotifyOnAckReceived(uint8_t* buff) {
  const uint8_t NCI_RF_CONN_ID = 0;
  /*
   * If Mifare Activated & received RF data packet
   */
  if (bEnableMfcExtns && (buff[0] == NCI_RF_CONN_ID)) {
    int sem_val;
    isAck = (buff[3] == NFCSTATUS_SUCCESS);
    sem_getvalue(&mNacksem, &sem_val);
    if (sem_val == 0) {
      if (sem_post(&mNacksem) == -1) {
        NXPLOG_NCIHAL_E("%s : sem_post failed", __func__);
      }
    }
  }
}

/*******************************************************************************
**
** Function         MfcWaitForAck
**
** Description      This function is called to wait for MFC NACK
**
** Returns          NFCSTATUS_SUCCESS
**                  NFCSTATUS_FAILED
**
*******************************************************************************/
NFCSTATUS NxpMfcReader::MfcWaitForAck() {
  NFCSTATUS status = NFCSTATUS_FAILED;
  int sem_timedout = 2, s;
  struct timespec ts;
  isAck = false;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  ts.tv_sec += sem_timedout;
  while ((s = sem_timedwait_monotonic_np(&mNacksem, &ts)) == -1 && errno == EINTR) {
    continue; /* Restart if interrupted by handler */
  }
  if (s != -1) {
    status = NFCSTATUS_SUCCESS;
  }
  return status;
}
