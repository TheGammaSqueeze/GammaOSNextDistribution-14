/*
 *  Copyright 2010-2023 NXP
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

/*
 * Download Component
 * Download Interface routines implementation
 */

#include <dlfcn.h>
#include <phDnldNfc_Internal.h>
#include <phNxpConfig.h>
#include <phNxpLog.h>
#include <phTmlNfc.h>

#include <string>

#include "NxpNfcCapability.h"

#if (NXP_NFC_RECOVERY == TRUE)
#include <phDnldNfc_UpdateSeq.h>
#endif

static void* pFwHandle; /* Global firmware handle*/
uint16_t wMwVer = 0;    /* Middleware version no */
uint16_t wFwVer = 0;    /* Firmware version no */
uint8_t gRecFWDwnld;    /* flag set to true to indicate recovery FW download */
phTmlNfc_i2cfragmentation_t fragmentation_enabled = I2C_FRAGMENATATION_DISABLED;
static pphDnldNfc_DlContext_t gpphDnldContext = NULL; /* Download contex */

/*******************************************************************************
**
** Function         phDnldNfc_Reset
**
** Description      Performs a soft reset of the download module
**
** Parameters       pNotify  - notify caller after getting response
**                  pContext - caller context
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - reset request to NFCC is successful
**                  NFCSTATUS_FAILED - reset request failed due to internal
**                                     error
**                  NFCSTATUS_NOT_ALLOWED - command not allowed
**                  Other command specific errors
**
*******************************************************************************/
NFCSTATUS phDnldNfc_Reset(pphDnldNfc_RspCb_t pNotify, void* pContext) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;

  if ((NULL == pNotify) || (NULL == pContext)) {
    NXPLOG_FWDNLD_E("Invalid Input Parameters!!");
    wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
  } else {
    if (phDnldNfc_TransitionIdle != gpphDnldContext->tDnldInProgress) {
      NXPLOG_FWDNLD_E("Dnld Cmd Request in Progress..Cannot Continue!!");
      wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_BUSY);
    } else {
      (gpphDnldContext->FrameInp.Type) = phDnldNfc_FTNone;
      (gpphDnldContext->tCmdId) = PH_DL_CMD_RESET;
      (gpphDnldContext->tRspBuffInfo.pBuff) = NULL;
      (gpphDnldContext->tRspBuffInfo.wLen) = 0;
      (gpphDnldContext->tUserData.pBuff) = NULL;
      (gpphDnldContext->tUserData.wLen) = 0;
      (gpphDnldContext->UserCb) = pNotify;
      (gpphDnldContext->UserCtxt) = pContext;

      wStatus = phDnldNfc_CmdHandler(gpphDnldContext, phDnldNfc_EventReset);

      if (NFCSTATUS_PENDING == wStatus) {
        NXPLOG_FWDNLD_D("Reset Request submitted successfully");
      } else {
        NXPLOG_FWDNLD_E("Reset Request Failed!!");
      }
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_GetVersion
**
** Description      Retrieves Hardware version, ROM Code version, Protected Data
**                  version, Trim data version, User data version, and Firmware
**                  version information
**
** Parameters       pVersionInfo - response buffer which gets updated with
**                                 complete version info from NFCC
**                  pNotify - notify caller after getting response
**                  pContext - caller context
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - GetVersion request to NFCC is successful
**                  NFCSTATUS_FAILED - GetVersion request failed due to internal
**                                     error
**                  NFCSTATUS_NOT_ALLOWED - command not allowed
**                  Other command specific errors
**
*******************************************************************************/
NFCSTATUS phDnldNfc_GetVersion(pphDnldNfc_Buff_t pVersionInfo,
                               pphDnldNfc_RspCb_t pNotify, void* pContext) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;

  if ((NULL == pVersionInfo) || (NULL == pNotify) || (NULL == pContext)) {
    NXPLOG_FWDNLD_E("Invalid Input Parameters!!");
    wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
  } else {
    if (phDnldNfc_TransitionIdle != gpphDnldContext->tDnldInProgress) {
      NXPLOG_FWDNLD_E("Dnld Cmd Request in Progress..Cannot Continue!!");
      wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_BUSY);
    } else {
      if ((NULL != pVersionInfo->pBuff) && (0 != pVersionInfo->wLen)) {
        (gpphDnldContext->tRspBuffInfo.pBuff) = pVersionInfo->pBuff;
        (gpphDnldContext->tRspBuffInfo.wLen) = pVersionInfo->wLen;
        (gpphDnldContext->FrameInp.Type) = phDnldNfc_FTNone;
        (gpphDnldContext->tCmdId) = PH_DL_CMD_GETVERSION;
        (gpphDnldContext->tUserData.pBuff) = NULL;
        (gpphDnldContext->tUserData.wLen) = 0;
        (gpphDnldContext->UserCb) = pNotify;
        (gpphDnldContext->UserCtxt) = pContext;

        wStatus = phDnldNfc_CmdHandler(gpphDnldContext, phDnldNfc_EventGetVer);

        if (NFCSTATUS_PENDING == wStatus) {
          NXPLOG_FWDNLD_D("GetVersion Request submitted successfully");
        } else {
          NXPLOG_FWDNLD_E("GetVersion Request Failed!!");
        }
      } else {
        NXPLOG_FWDNLD_E("Invalid Buff Parameters!!");
        wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
      }
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_GetSessionState
**
** Description      Retrieves the current session state of NFCC
**
** Parameters       pSession - response buffer which gets updated with complete
**                             version info from NFCC
**                  pNotify - notify caller after getting response
**                  pContext - caller context
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - GetSessionState request to NFCC is
**                                      successful
**                  NFCSTATUS_FAILED - GetSessionState request failed due to
**                                     internal error
**                  NFCSTATUS_NOT_ALLOWED - command not allowed
**                  Other command specific errors
**
*******************************************************************************/
NFCSTATUS phDnldNfc_GetSessionState(pphDnldNfc_Buff_t pSession,
                                    pphDnldNfc_RspCb_t pNotify,
                                    void* pContext) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;

  if ((NULL == pSession) || (NULL == pNotify) || (NULL == pContext)) {
    NXPLOG_FWDNLD_E("Invalid Input Parameters!!");
    wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
  } else {
    if (phDnldNfc_TransitionIdle != gpphDnldContext->tDnldInProgress) {
      NXPLOG_FWDNLD_E("Dnld Cmd Request in Progress..Cannot Continue!!");
      wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_BUSY);
    } else {
      if ((NULL != pSession->pBuff) && (0 != pSession->wLen)) {
        (gpphDnldContext->tRspBuffInfo.pBuff) = pSession->pBuff;
        (gpphDnldContext->tRspBuffInfo.wLen) = pSession->wLen;
        (gpphDnldContext->FrameInp.Type) = phDnldNfc_FTNone;
        (gpphDnldContext->tCmdId) = PH_DL_CMD_GETSESSIONSTATE;
        (gpphDnldContext->tUserData.pBuff) = NULL;
        (gpphDnldContext->tUserData.wLen) = 0;
        (gpphDnldContext->UserCb) = pNotify;
        (gpphDnldContext->UserCtxt) = pContext;

        wStatus =
            phDnldNfc_CmdHandler(gpphDnldContext, phDnldNfc_EventGetSesnSt);

        if (NFCSTATUS_PENDING == wStatus) {
          NXPLOG_FWDNLD_D("GetSessionState Request submitted successfully");
        } else {
          NXPLOG_FWDNLD_E("GetSessionState Request Failed!!");
        }
      } else {
        NXPLOG_FWDNLD_E("Invalid Buff Parameters!!");
        wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
      }
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_CheckIntegrity
**
** Description      Inspects the integrity of EEPROM and FLASH contents of the
**                  NFCC, provides CRC for each section
**                  NOTE: The user data section CRC is valid only after fresh
**                        download
**
** Parameters       bChipVer - current ChipVersion for including additional
**                             parameters in request payload
**                  pCRCData - response buffer which gets updated with
**                             respective section CRC status and CRC bytes from
**                             NFCC
**                  pNotify - notify caller after getting response
**                  pContext - caller context
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - CheckIntegrity request is successful
**                  NFCSTATUS_FAILED - CheckIntegrity request failed due to
**                                     internal error
**                  NFCSTATUS_NOT_ALLOWED - command not allowed
**                  Other command specific errors
**
*******************************************************************************/
NFCSTATUS phDnldNfc_CheckIntegrity(uint8_t bChipVer, pphDnldNfc_Buff_t pCRCData,
                                   pphDnldNfc_RspCb_t pNotify, void* pContext) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;

  if ((NULL == pNotify) || (NULL == pContext)) {
    NXPLOG_FWDNLD_E("Invalid Input Parameters!!");
    wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
  } else {
    if (phDnldNfc_TransitionIdle != gpphDnldContext->tDnldInProgress) {
      NXPLOG_FWDNLD_E("Dnld Cmd Request in Progress..Cannot Continue!!");
      wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_BUSY);
    } else {
      if ((PHDNLDNFC_HWVER_MRA2_1 == bChipVer) ||
          (PHDNLDNFC_HWVER_MRA2_2 == bChipVer) ||
          (IS_CHIP_TYPE_EQ(pn551) &&
           ((PHDNLDNFC_HWVER_PN551_MRA1_0 == bChipVer))) ||
          ((IS_CHIP_TYPE_EQ(pn553) || IS_CHIP_TYPE_EQ(pn557)) &&
           ((PHDNLDNFC_HWVER_PN553_MRA1_0 == bChipVer) ||
            (PHDNLDNFC_HWVER_PN553_MRA1_0_UPDATED & bChipVer) ||
            ((PHDNLDNFC_HWVER_PN557_MRA1_0 == bChipVer)))) ||
          (IS_CHIP_TYPE_EQ(sn100u) &&
           (PHDNLDNFC_HWVER_VENUS_MRA1_0 & bChipVer)) ||
          ((IS_CHIP_TYPE_EQ(sn220u) || IS_CHIP_TYPE_EQ(pn560)) &&
           (PHDNLDNFC_HWVER_VULCAN_MRA1_0 & bChipVer)) ||
          (IS_CHIP_TYPE_EQ(sn300u) &&
           (PHDNLDNFC_HWVER_EOS_MRA2_0 & bChipVer))) {
        (gpphDnldContext->FrameInp.Type) = phDnldNfc_ChkIntg;
      } else {
        (gpphDnldContext->FrameInp.Type) = phDnldNfc_FTNone;
      }

      if ((NULL != pCRCData->pBuff) && (0 != pCRCData->wLen)) {
        (gpphDnldContext->tRspBuffInfo.pBuff) = pCRCData->pBuff;
        (gpphDnldContext->tRspBuffInfo.wLen) = pCRCData->wLen;
        (gpphDnldContext->tCmdId) = PH_DL_CMD_CHECKINTEGRITY;
        (gpphDnldContext->tUserData.pBuff) = NULL;
        (gpphDnldContext->tUserData.wLen) = 0;
        (gpphDnldContext->UserCb) = pNotify;
        (gpphDnldContext->UserCtxt) = pContext;

        wStatus =
            phDnldNfc_CmdHandler(gpphDnldContext, phDnldNfc_EventIntegChk);

        if (NFCSTATUS_PENDING == wStatus) {
          NXPLOG_FWDNLD_D("CheckIntegrity Request submitted successfully");
        } else {
          NXPLOG_FWDNLD_E("CheckIntegrity Request Failed!!");
        }
      } else {
        NXPLOG_FWDNLD_E("Invalid Buff Parameters!!");
        wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
      }
    }
  }

  return wStatus;
}
/*******************************************************************************
**
** Function         phDnldNfc_ReadLog
**
** Description      Retrieves log data from EEPROM
**
** Parameters       pData - response buffer which gets updated with data from
**                          EEPROM
**                  pNotify - notify caller after getting response
**                  pContext - caller context
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - Read request to NFCC is successful
**                  NFCSTATUS_FAILED - Read request failed due to internal error
**                  NFCSTATUS_NOT_ALLOWED - command not allowed
**                  Other command specific errors
**
*******************************************************************************/
NFCSTATUS phDnldNfc_ReadLog(pphDnldNfc_Buff_t pData, pphDnldNfc_RspCb_t pNotify,
                            void* pContext) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;

  if ((NULL == pNotify) || (NULL == pData) || (NULL == pContext)) {
    NXPLOG_FWDNLD_E("Invalid Input Parameters!!");
    wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
  } else {
    if (phDnldNfc_TransitionIdle != gpphDnldContext->tDnldInProgress) {
      NXPLOG_FWDNLD_E("Dnld Cmd Request in Progress..Cannot Continue!!");
      wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_BUSY);
    } else {
      if ((NULL != pData->pBuff) && (0 != pData->wLen)) {
        (gpphDnldContext->tCmdId) = PH_DL_CMD_READ;
        (gpphDnldContext->FrameInp.Type) = phDnldNfc_FTRead;
        (gpphDnldContext->FrameInp.dwAddr) = PHDNLDNFC_EEPROM_LOG_START_ADDR;
        (gpphDnldContext->tRspBuffInfo.pBuff) = pData->pBuff;
        (gpphDnldContext->tRspBuffInfo.wLen) = pData->wLen;
        (gpphDnldContext->tUserData.pBuff) = NULL;
        (gpphDnldContext->tUserData.wLen) = 0;
        (gpphDnldContext->UserCb) = pNotify;
        (gpphDnldContext->UserCtxt) = pContext;

        memset(&(gpphDnldContext->tRWInfo), 0,
               sizeof(gpphDnldContext->tRWInfo));

        wStatus = phDnldNfc_CmdHandler(gpphDnldContext, phDnldNfc_EventRead);

        if (NFCSTATUS_PENDING == wStatus) {
          NXPLOG_FWDNLD_D("Read Request submitted successfully");
        } else {
          NXPLOG_FWDNLD_E("Read Request Failed!!");
        }
      } else {
        NXPLOG_FWDNLD_E("Invalid Buff Parameters!!");
        wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
      }
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_Write
**
** Description      Writes requested  data of length len to desired EEPROM/FLASH
**                  address
**
** Parameters       bRecoverSeq - flag to indicate whether recover sequence data
**                                needs to be written or not
**                  pData - data buffer to write into EEPROM/FLASH by user
**                  pNotify - notify caller after getting response
**                  pContext - caller context
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - Write request to NFCC is successful
**                  NFCSTATUS_FAILED - Write request failed due to internal
**                                     error
**                  NFCSTATUS_NOT_ALLOWED - command not allowed
**                  Other command specific errors
**
*******************************************************************************/
NFCSTATUS phDnldNfc_Write(bool_t bRecoverSeq, pphDnldNfc_Buff_t pData,
                          pphDnldNfc_RspCb_t pNotify, void* pContext) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;
  uint8_t* pImgPtr = NULL;
  uint32_t wLen = 0;
  phDnldNfc_Buff_t tImgBuff;

  if ((NULL == pNotify) || (NULL == pContext)) {
    NXPLOG_FWDNLD_E("Invalid Input Parameters!!");
    wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
  } else {
    if (phDnldNfc_TransitionIdle != gpphDnldContext->tDnldInProgress) {
      NXPLOG_FWDNLD_E("Dnld Cmd Request in Progress..Cannot Continue!!");
      wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_BUSY);
    } else {
      if (NULL != pData) {
        pImgPtr = pData->pBuff;
        wLen = pData->wLen;
      } else {
        if (bRecoverSeq == false) {
          pImgPtr = (uint8_t*)gpphDnldContext->nxp_nfc_fw;
          wLen = gpphDnldContext->nxp_nfc_fw_len;

        } else {
          if (IS_CHIP_TYPE_GE(sn100u)) {
            if (PH_DL_STATUS_PLL_ERROR == (gpphDnldContext->tLastStatus)) {
              wStatus = phDnldNfc_LoadRecInfo();
            } else if (PH_DL_STATUS_SIGNATURE_ERROR ==
                       (gpphDnldContext->tLastStatus)) {
              wStatus = phDnldNfc_LoadPKInfo();
            } else {
            }
          }

          if (NFCSTATUS_SUCCESS == wStatus) {
            pImgPtr = (uint8_t*)gpphDnldContext->nxp_nfc_fwp;
            wLen = gpphDnldContext->nxp_nfc_fwp_len;
          } else {
            NXPLOG_FWDNLD_E("Platform Recovery Image extraction Failed!!");
            pImgPtr = NULL;
            wLen = 0;
          }
        }
      }

      if ((NULL != pImgPtr) && (0 != wLen)) {
        tImgBuff.pBuff = pImgPtr;
        tImgBuff.wLen = wLen;

        (gpphDnldContext->tCmdId) = PH_DL_CMD_WRITE;
        (gpphDnldContext->FrameInp.Type) = phDnldNfc_FTWrite;
        (gpphDnldContext->tRspBuffInfo.pBuff) = NULL;
        (gpphDnldContext->tRspBuffInfo.wLen) = 0;
        (gpphDnldContext->tUserData.pBuff) = pImgPtr;
        (gpphDnldContext->tUserData.wLen) = wLen;
        (gpphDnldContext->bResendLastFrame) = false;

        memset(&(gpphDnldContext->tRWInfo), 0,
               sizeof(gpphDnldContext->tRWInfo));
        (gpphDnldContext->tRWInfo.bFirstWrReq) = true;
        (gpphDnldContext->UserCb) = pNotify;
        (gpphDnldContext->UserCtxt) = pContext;

        wStatus = phDnldNfc_CmdHandler(gpphDnldContext, phDnldNfc_EventWrite);

        if (NFCSTATUS_PENDING == wStatus) {
          NXPLOG_FWDNLD_D("Write Request submitted successfully");
        } else {
          NXPLOG_FWDNLD_E("Write Request Failed!!");
        }
      } else {
        NXPLOG_FWDNLD_E("Download Image Primitives extraction failed!!");
        wStatus = NFCSTATUS_FAILED;
      }
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_Log
**
** Description      Provides a full page free write to EEPROM
**
** Parameters       pData - data buffer to write into EEPROM/FLASH by user
**                  pNotify - notify caller after getting response
**                  pContext - caller context
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - Write request to NFCC is successful
**                  NFCSTATUS_FAILED - Write request failed due to internal
**                                     error
**                  NFCSTATUS_NOT_ALLOWED - command not allowed
**                  Other command specific error
**
*******************************************************************************/
NFCSTATUS phDnldNfc_Log(pphDnldNfc_Buff_t pData, pphDnldNfc_RspCb_t pNotify,
                        void* pContext) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;

  if ((NULL == pNotify) || (NULL == pData) || (NULL == pContext)) {
    NXPLOG_FWDNLD_E("Invalid Input Parameters!!");
    wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
  } else {
    if (phDnldNfc_TransitionIdle != gpphDnldContext->tDnldInProgress) {
      NXPLOG_FWDNLD_E("Dnld Cmd Request in Progress..Cannot Continue!!");
      wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_BUSY);
    } else {
      if ((NULL != (pData->pBuff)) &&
          ((0 != (pData->wLen) && (PHDNLDNFC_MAX_LOG_SIZE >= (pData->wLen))))) {
        (gpphDnldContext->tCmdId) = PH_DL_CMD_LOG;
        (gpphDnldContext->FrameInp.Type) = phDnldNfc_FTLog;
        (gpphDnldContext->tRspBuffInfo.pBuff) = NULL;
        (gpphDnldContext->tRspBuffInfo.wLen) = 0;
        (gpphDnldContext->tUserData.pBuff) = (pData->pBuff);
        (gpphDnldContext->tUserData.wLen) = (pData->wLen);

        memset(&(gpphDnldContext->tRWInfo), 0,
               sizeof(gpphDnldContext->tRWInfo));
        (gpphDnldContext->UserCb) = pNotify;
        (gpphDnldContext->UserCtxt) = pContext;

        wStatus = phDnldNfc_CmdHandler(gpphDnldContext, phDnldNfc_EventLog);

        if (NFCSTATUS_PENDING == wStatus) {
          NXPLOG_FWDNLD_D("Log Request submitted successfully");
        } else {
          NXPLOG_FWDNLD_E("Log Request Failed!!");
        }
      } else {
        NXPLOG_FWDNLD_E("Invalid Input Parameters for Log!!");
        wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
      }
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_Force
**
** Description      Used as an emergency recovery procedure for NFCC due to
**                  corrupt settings of system platform specific parameters by
**                  the host
**
** Parameters       pInputs - input buffer which contains  clk src & clk freq
**                            settings for desired platform
**                  pNotify - notify caller after getting response
**                  pContext - caller context
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - Emergency Recovery request is successful
**                  NFCSTATUS_FAILED - Emergency Recovery failed due to internal
**                                     error
**                  NFCSTATUS_NOT_ALLOWED - command not allowed
**                  Other command specific errors
**
*******************************************************************************/
NFCSTATUS phDnldNfc_Force(pphDnldNfc_Buff_t pInputs, pphDnldNfc_RspCb_t pNotify,
                          void* pContext) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;
  uint8_t bClkSrc = 0x00, bClkFreq = 0x00;
  uint8_t bPldVal[3] = {
      0x11, 0x00, 0x00}; /* default values to be used if input not provided */

  if ((NULL == pNotify) || (NULL == pContext)) {
    NXPLOG_FWDNLD_E("Invalid Input Parameters!!");
    wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
  } else {
    if (phDnldNfc_TransitionIdle != gpphDnldContext->tDnldInProgress) {
      NXPLOG_FWDNLD_E("Dnld Cmd Request in Progress..Cannot Continue!!");
      wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_BUSY);
    } else {
      (gpphDnldContext->tCmdId) = PH_DL_CMD_FORCE;
      (gpphDnldContext->FrameInp.Type) = phDnldNfc_FTForce;
      (gpphDnldContext->tRspBuffInfo.pBuff) = NULL;
      (gpphDnldContext->tRspBuffInfo.wLen) = 0;

      if ((0 != (pInputs->wLen)) || (NULL != (pInputs->pBuff))) {
        if (CLK_SRC_XTAL == (pInputs->pBuff[0])) {
          bClkSrc = phDnldNfc_ClkSrcXtal;
        } else if (CLK_SRC_PLL == (pInputs->pBuff[0])) {
          bClkSrc = phDnldNfc_ClkSrcPLL;
          if (CLK_FREQ_13MHZ == (pInputs->pBuff[1])) {
            bClkFreq = phDnldNfc_ClkFreq_13Mhz;
          } else if (CLK_FREQ_19_2MHZ == (pInputs->pBuff[1])) {
            bClkFreq = phDnldNfc_ClkFreq_19_2Mhz;
          } else if (CLK_FREQ_24MHZ == (pInputs->pBuff[1])) {
            bClkFreq = phDnldNfc_ClkFreq_24Mhz;
          } else if (CLK_FREQ_26MHZ == (pInputs->pBuff[1])) {
            bClkFreq = phDnldNfc_ClkFreq_26Mhz;
          } else if (CLK_FREQ_38_4MHZ == (pInputs->pBuff[1])) {
            bClkFreq = phDnldNfc_ClkFreq_38_4Mhz;
          } else if (CLK_FREQ_52MHZ == (pInputs->pBuff[1])) {
            bClkFreq = phDnldNfc_ClkFreq_52Mhz;
          } else if (CLK_FREQ_32MHZ == (pInputs->pBuff[1])) {
            bClkFreq = phDnldNfc_ClkFreq_32Mhz;
          } else if (CLK_FREQ_48MHZ == (pInputs->pBuff[1])) {
            bClkFreq = phDnldNfc_ClkFreq_48Mhz;
          } else {
            NXPLOG_FWDNLD_E(
                "Invalid Clk Frequency !! Using default value of 19.2Mhz..");
            bClkFreq = phDnldNfc_ClkFreq_19_2Mhz;
          }

        } else if (CLK_SRC_PADDIRECT == (pInputs->pBuff[0])) {
          bClkSrc = phDnldNfc_ClkSrcPad;
        } else {
          NXPLOG_FWDNLD_E("Invalid Clk src !! Using default value of PLL..");
          bClkSrc = phDnldNfc_ClkSrcPLL;
        }

        bPldVal[0] = 0U;
        bPldVal[0] = ((bClkSrc << 3U) | bClkFreq);
      } else {
        NXPLOG_FWDNLD_E("Clk src inputs not provided!! Using default values..");
      }

      (gpphDnldContext->tUserData.pBuff) = bPldVal;
      (gpphDnldContext->tUserData.wLen) = sizeof(bPldVal);

      memset(&(gpphDnldContext->tRWInfo), 0, sizeof(gpphDnldContext->tRWInfo));
      (gpphDnldContext->UserCb) = pNotify;
      (gpphDnldContext->UserCtxt) = pContext;

      wStatus = phDnldNfc_CmdHandler(gpphDnldContext, phDnldNfc_EventForce);

      if (NFCSTATUS_PENDING == wStatus) {
        NXPLOG_FWDNLD_D("Force Command Request submitted successfully");
      } else {
        NXPLOG_FWDNLD_E("Force Command Request Failed!!");
      }
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_SetHwDevHandle
**
** Description      Stores the HwDev handle to download context. The handle is
**                  required for subsequent operations. Also, sets the I2C
**                  fragmentation length as per the chip type.
**
** Parameters       None
**
** Returns          None                -
**
*******************************************************************************/
void phDnldNfc_SetHwDevHandle(void) {
  if (NULL == gpphDnldContext) {
    NXPLOG_FWDNLD_D("Allocating Mem for Dnld Context..");
    /* Create the memory for Download Mgmt Context */
    gpphDnldContext =
        (pphDnldNfc_DlContext_t)calloc(1, sizeof(phDnldNfc_DlContext_t));
    if (gpphDnldContext == NULL) {
      NXPLOG_FWDNLD_E("Error Allocating Mem for Dnld Context..");
      return;
    }
  } else {
    if (gpphDnldContext->tCmdRspFrameInfo.aFrameBuff != NULL) {
      free(gpphDnldContext->tCmdRspFrameInfo.aFrameBuff);
    }
    (void)memset((void*)gpphDnldContext, 0, sizeof(phDnldNfc_DlContext_t));
  }
  // Set the gpphDnldContext->nxp_i2c_fragment_len as per chiptype
  phDnldNfc_SetI2CFragmentLength();
  gpphDnldContext->tCmdRspFrameInfo.aFrameBuff =
      (uint8_t*)calloc(gpphDnldContext->nxp_i2c_fragment_len, sizeof(uint8_t));
  if (gpphDnldContext->tCmdRspFrameInfo.aFrameBuff == NULL) {
    NXPLOG_FWDNLD_E("Error Allocating Mem for Dnld Context aFrameBuff..");
  }
  // Update the write Fragmentation Length at TML layer
  phTmlNfc_IoCtl(phTmlNfc_e_setFragmentSize);
  return;
}

/*******************************************************************************
**
** Function         phDnldNfc_ReSetHwDevHandle
**
** Description      Frees the HwDev handle to download context.
**
** Parameters       None
**
** Returns          None                -
**
*******************************************************************************/
void phDnldNfc_ReSetHwDevHandle(void) {
  if (gpphDnldContext != NULL) {
    NXPLOG_FWDNLD_D("Freeing Mem for Dnld Context..");
    free(gpphDnldContext);
    gpphDnldContext = NULL;
  }
  phTmlNfc_IoCtl(phTmlNfc_e_setFragmentSize);
}

/*******************************************************************************
**
** Function         phDnldNfc_RawReq
**
** Description      Sends raw frame request to NFCC.
**                  It is currently used for sending an NCI RESET cmd after
**                  doing a production key update
**
** Parameters       pFrameData - input buffer, contains raw frame packet to be
**                               sent to NFCC
**                  pRspData - response buffer received from NFCC
**                  pNotify - notify caller after getting response
**                  pContext - caller context
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - GetSessionState request to NFCC is
**                                      successful
**                  NFCSTATUS_FAILED - GetSessionState request failed due to
**                                     internal error
**                  NFCSTATUS_NOT_ALLOWED - command not allowed
**                  Other command specific errors
**
*******************************************************************************/
NFCSTATUS phDnldNfc_RawReq(pphDnldNfc_Buff_t pFrameData,
                           pphDnldNfc_Buff_t pRspData,
                           pphDnldNfc_RspCb_t pNotify, void* pContext) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;

  if ((NULL == pFrameData) || (NULL == pNotify) || (NULL == pRspData) ||
      (NULL == pContext)) {
    NXPLOG_FWDNLD_E("Invalid Input Parameters!!");
    wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
  } else {
    if (phDnldNfc_TransitionIdle != gpphDnldContext->tDnldInProgress) {
      NXPLOG_FWDNLD_E("Raw Cmd Request in Progress..Cannot Continue!!");
      wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_BUSY);
    } else {
      if (((NULL != pFrameData->pBuff) && (0 != pFrameData->wLen)) &&
          ((NULL != pRspData->pBuff) && (0 != pRspData->wLen))) {
        (gpphDnldContext->tRspBuffInfo.pBuff) = pRspData->pBuff;
        (gpphDnldContext->tRspBuffInfo.wLen) = pRspData->wLen;
        (gpphDnldContext->FrameInp.Type) = phDnldNfc_FTRaw;
        (gpphDnldContext->tCmdId) = PH_DL_CMD_NONE;
        (gpphDnldContext->tUserData.pBuff) = pFrameData->pBuff;
        (gpphDnldContext->tUserData.wLen) = pFrameData->wLen;
        (gpphDnldContext->UserCb) = pNotify;
        (gpphDnldContext->UserCtxt) = pContext;

        wStatus = phDnldNfc_CmdHandler(gpphDnldContext, phDnldNfc_EventRaw);

        if (NFCSTATUS_PENDING == wStatus) {
          NXPLOG_FWDNLD_D("RawFrame Request submitted successfully");
        } else {
          NXPLOG_FWDNLD_E("RawFrame Request Failed!!");
        }
      } else {
        NXPLOG_FWDNLD_E("Invalid Buff Parameters!!");
        wStatus = PHNFCSTVAL(CID_NFC_DNLD, NFCSTATUS_INVALID_PARAMETER);
      }
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_InitImgInfo
**
** Description      Extracts image information and stores it in respective
**                  variables, to be used internally for write operation
**
** Parameters       bMinimalFw - flag indicates for minimal FW Image
**                  degradedFwDnld - Indicates if degraded FW download is
**                  requested
**
** Returns          NFC status
**
*******************************************************************************/
NFCSTATUS phDnldNfc_InitImgInfo(bool bMinimalFw, bool degradedFwDnld) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;
  uint8_t* pImageInfo = NULL;
  uint32_t ImageInfoLen = 0;
  unsigned long fwType = FW_FORMAT_SO;

  /* if memory is not allocated then allocate memory for download context
   * structure */
  phDnldNfc_SetHwDevHandle();

  gpphDnldContext->FwFormat = FW_FORMAT_UNKNOWN;
  phDnldNfc_SetDlRspTimeout((uint16_t)PHDNLDNFC_RSP_TIMEOUT);
  if (bMinimalFw) {
    fwType = FW_FORMAT_ARRAY;
  } else if (GetNxpNumValue(NAME_NXP_FW_TYPE, &fwType, sizeof(fwType)) ==
             true) {
    /*Read Firmware file name from config file*/
    NXPLOG_FWDNLD_D("firmware type from conf file: %lu", fwType);
  } else {
    NXPLOG_FWDNLD_W("firmware type not found. Taking default value: %lu",
                    fwType);
  }

  if (fwType == FW_FORMAT_ARRAY) {
    gpphDnldContext->FwFormat = FW_FORMAT_ARRAY;
#if (NXP_NFC_RECOVERY == TRUE)
    pImageInfo = (uint8_t*)gphDnldNfc_DlSequence;
    ImageInfoLen = gphDnldNfc_DlSeqSz;
#endif
    wStatus = NFCSTATUS_SUCCESS;
  } else if (fwType == FW_FORMAT_BIN) {
    gpphDnldContext->FwFormat = FW_FORMAT_BIN;
    wStatus = phDnldNfc_LoadBinFW(&pImageInfo, &ImageInfoLen);
  } else if (fwType == FW_FORMAT_SO) {
    gpphDnldContext->FwFormat = FW_FORMAT_SO;
    wStatus = phDnldNfc_LoadFW(Fw_Lib_Path, &pImageInfo, &ImageInfoLen,
                               degradedFwDnld);
  } else {
    NXPLOG_FWDNLD_E("firmware file format mismatch!!!\n");
    return NFCSTATUS_FAILED;
  }

  NXPLOG_FWDNLD_D("FW Image Length - ImageInfoLen %d", ImageInfoLen);
  NXPLOG_FWDNLD_D("FW Image Info Pointer - pImageInfo %p", pImageInfo);

  if ((pImageInfo == NULL) || (ImageInfoLen == 0)) {
    NXPLOG_FWDNLD_E(
        "Image extraction Failed - invalid imginfo or imginfolen!!");
    wStatus = NFCSTATUS_FAILED;
  }

  if (wStatus != NFCSTATUS_SUCCESS) {
    NXPLOG_FWDNLD_E("Error loading FW file !!\n");
  }

  /* get the MW version */
  if (NFCSTATUS_SUCCESS == wStatus) {
    // NXPLOG_FWDNLD_D("MW Major Version Num - %x",NXP_MW_VERSION_MAJ);
    // NXPLOG_FWDNLD_D("MW Minor Version Num - %x",NXP_MW_VERSION_MIN);
    wMwVer = (((uint16_t)(NXP_MW_VERSION_MAJ) << 8U) | (NXP_MW_VERSION_MIN));
  }

  if (NFCSTATUS_SUCCESS == wStatus) {
    gpphDnldContext->nxp_nfc_fw = (uint8_t*)pImageInfo;
    gpphDnldContext->nxp_nfc_fw_len = ImageInfoLen;
    if ((NULL != gpphDnldContext->nxp_nfc_fw) &&
        (0 != gpphDnldContext->nxp_nfc_fw_len)) {
      uint16_t offsetFwMajorNum, offsetFwMinorNum;
      if (IS_CHIP_TYPE_GE(sn220u) || IS_CHIP_TYPE_EQ(pn560)) {
        offsetFwMajorNum = ((uint16_t)(gpphDnldContext->nxp_nfc_fw[795]) << 8U);
        offsetFwMinorNum = ((uint16_t)(gpphDnldContext->nxp_nfc_fw[794]));
      } else {
        offsetFwMajorNum = ((uint16_t)(gpphDnldContext->nxp_nfc_fw[5]) << 8U);
        offsetFwMinorNum = ((uint16_t)(gpphDnldContext->nxp_nfc_fw[4]));
      }
      NXPLOG_FWDNLD_D("FW Major Version Num - %x", offsetFwMajorNum);
      NXPLOG_FWDNLD_D("FW Minor Version Num - %x", offsetFwMinorNum);
      /* get the FW version */
      wFwVer = (offsetFwMajorNum | offsetFwMinorNum);

      NXPLOG_FWDNLD_D("FW Image Length - %d", ImageInfoLen);
      NXPLOG_FWDNLD_D("FW Image Info Pointer - %p", pImageInfo);
      wStatus = NFCSTATUS_SUCCESS;
    } else {
      NXPLOG_FWDNLD_E("Image details extraction Failed!!");
      wStatus = NFCSTATUS_FAILED;
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_LoadRecInfo
**
** Description      Extracts recovery sequence image information and stores it
**                  in respective variables, to be used internally for write
**                  operation
**
** Parameters       None
**
** Returns          NFC status
**
*******************************************************************************/
NFCSTATUS phDnldNfc_LoadRecInfo(void) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;
  uint8_t* pImageInfo = NULL;
  uint32_t ImageInfoLen = 0;

  /* if memory is not allocated then allocate memory for donwload context
   * structure */
  phDnldNfc_SetHwDevHandle();
  wStatus = phDnldNfc_LoadFW(PLATFORM_LIB_PATH, &pImageInfo, &ImageInfoLen);
  if ((pImageInfo == NULL) || (ImageInfoLen == 0)) {
    NXPLOG_FWDNLD_E(
        "Image extraction Failed - invalid imginfo or imginfolen!!");
    wStatus = NFCSTATUS_FAILED;
  }

  /* load the PLL recovery image library */
  if (wStatus != NFCSTATUS_SUCCESS) {
    NXPLOG_FWDNLD_E("Error loading FW file... !!\n");
  }

  if (NFCSTATUS_SUCCESS == wStatus) {
    /* fetch the PLL recovery image pointer and the image length */
    gpphDnldContext->nxp_nfc_fwp = (uint8_t*)pImageInfo;
    gpphDnldContext->nxp_nfc_fwp_len = ImageInfoLen;

    if ((NULL != gpphDnldContext->nxp_nfc_fwp) &&
        (0 != gpphDnldContext->nxp_nfc_fwp_len)) {
      NXPLOG_FWDNLD_D("Recovery Image Length - %d", ImageInfoLen);
      NXPLOG_FWDNLD_D("Recovery Image Info Pointer - %p", pImageInfo);
      wStatus = NFCSTATUS_SUCCESS;
    } else {
      NXPLOG_FWDNLD_E("Recovery Image details extraction Failed!!");
      wStatus = NFCSTATUS_FAILED;
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_LoadPKInfo
**
** Description      Extracts production sequence image information and stores it
**                  in respective variables, to be used internally for write
**                  operation
**
** Parameters       None
**
** Returns          NFC status
**
*******************************************************************************/
NFCSTATUS phDnldNfc_LoadPKInfo(void) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;
  uint8_t* pImageInfo = NULL;
  uint32_t ImageInfoLen = 0;

  /* if memory is not allocated then allocate memory for donwload context
   * structure */
  phDnldNfc_SetHwDevHandle();

  /* load the PKU image library */
  wStatus = phDnldNfc_LoadFW(PKU_LIB_PATH, &pImageInfo, &ImageInfoLen);
  if ((pImageInfo == NULL) || (ImageInfoLen == 0)) {
    NXPLOG_FWDNLD_E(
        "Image extraction Failed - invalid imginfo or imginfolen!!");
    wStatus = NFCSTATUS_FAILED;
  }

  if (wStatus != NFCSTATUS_SUCCESS) {
    NXPLOG_FWDNLD_E("Error loading FW File pku !!\n");
  }

  if (NFCSTATUS_SUCCESS == wStatus) {
    /* fetch the PKU image pointer and the image length */
    gpphDnldContext->nxp_nfc_fwp = (uint8_t*)pImageInfo;
    gpphDnldContext->nxp_nfc_fwp_len = ImageInfoLen;

    if ((NULL != gpphDnldContext->nxp_nfc_fwp) &&
        (0 != gpphDnldContext->nxp_nfc_fwp_len)) {
      NXPLOG_FWDNLD_D("PKU Image Length - %d", ImageInfoLen);
      NXPLOG_FWDNLD_D("PKU Image Info Pointer - %p", pImageInfo);
      wStatus = NFCSTATUS_SUCCESS;
    } else {
      NXPLOG_FWDNLD_E("PKU Image details extraction Failed!!");
      wStatus = NFCSTATUS_FAILED;
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_CloseFwLibHandle
**
** Description      Closes previously opened fw library handle as part of
**                  dynamic loader processing
**
** Parameters       None
**
** Returns          None
**
*******************************************************************************/
void phDnldNfc_CloseFwLibHandle(void) {
  NFCSTATUS wStatus = NFCSTATUS_FAILED;
  if (gpphDnldContext->FwFormat == FW_FORMAT_SO) {
    wStatus = phDnldNfc_UnloadFW();
    if (wStatus != NFCSTATUS_SUCCESS) {
      NXPLOG_FWDNLD_E("free library FAILED !!\n");
    } else {
      NXPLOG_FWDNLD_E("free library SUCCESS !!\n");
    }
  } else if (gpphDnldContext->FwFormat == FW_FORMAT_BIN) {
    if (pFwHandle != NULL) {
      free(pFwHandle);
      pFwHandle = NULL;
    }
  }
  return;
}

/*******************************************************************************
**
** Function         phDnldNfc_LoadFW
**
** Description      Load the firmware version form firmware lib
**
** Parameters       pathName    - Firmware image path
**                  pImgInfo    - Firmware image handle
**                  pImgInfoLen - Firmware image length
**                  degradedFwDnld - Indicates if degraded FW download is
**                  requested
**
** Returns          NFC status
**
*******************************************************************************/
NFCSTATUS phDnldNfc_LoadFW(const char* pathName, uint8_t** pImgInfo,
                           uint32_t* pImgInfoLen, bool degradedFwDnld) {
  void* pImageInfo = NULL;
  void* pImageInfoLen = NULL;
  const char* pFwSymbol = "gphDnldNfc_DlSeq";
  const char* pFwSymbolSz = "gphDnldNfc_DlSeqSz";
  /* check for path name */
  if (pathName == NULL) pathName = nfcFL._FW_LIB_PATH.c_str();

  /* check if the handle is not NULL then free the library */
  if (pFwHandle != NULL) {
    phDnldNfc_CloseFwLibHandle();
    pFwHandle = NULL;
  }

  /* load the DLL file */
  pFwHandle = dlopen(pathName, RTLD_LAZY);
  NXPLOG_FWDNLD_D("@@@%s", pathName);

  /* if library load failed then handle will be NULL */
  if (pFwHandle == NULL) {
    NXPLOG_FWDNLD_E(
        "NULL handler : unable to load the library file, specify correct path");
    return NFCSTATUS_FAILED;
  }

  dlerror(); /* Clear any existing error */

  if (degradedFwDnld) {
    NXPLOG_FWDNLD_D("%s: Loading Degraded FW info", __func__);
    pFwSymbol = "gphDnldNfc_DlSeq_DegradedFw";
    pFwSymbolSz = "gphDnldNfc_DlSeqSz_DegradedFw";
  }

  /* load the address of download image pointer and image size */
  pImageInfo = (void*)dlsym(pFwHandle, pFwSymbol);

  if (dlerror() || (NULL == pImageInfo)) {
    NXPLOG_FWDNLD_E("Problem loading symbol : %s", pFwSymbol);
    return NFCSTATUS_FAILED;
  }
  (*pImgInfo) = (*(uint8_t**)pImageInfo);

  pImageInfoLen = (void*)dlsym(pFwHandle, pFwSymbolSz);
  if (dlerror() || (NULL == pImageInfoLen)) {
    NXPLOG_FWDNLD_E("Problem loading symbol : %s", pFwSymbolSz);
    return NFCSTATUS_FAILED;
  }

  if (IS_CHIP_TYPE_GE(sn100u)) {
    (*pImgInfoLen) = (uint32_t)(*((uint32_t*)pImageInfoLen));
  } else {
    (*pImgInfoLen) = (uint16_t)(*((uint16_t*)pImageInfoLen));
  }
  NXPLOG_FWDNLD_D("FW image loaded for chipType %s",
                  pConfigFL->product[nfcFL.chipType]);
  return NFCSTATUS_SUCCESS;
}

/*******************************************************************************
**
** Function         phDnldNfc_LoadBinFW
**
** Description      Load the firmware version form firmware lib
**
** Parameters       pImgInfo    - Firmware image handle
**                  pImgInfoLen - Firmware image length
**
** Returns          NFC status
**
*******************************************************************************/
NFCSTATUS phDnldNfc_LoadBinFW(uint8_t** pImgInfo, uint32_t* pImgInfoLen) {
  FILE* pFile = NULL;
  long fileSize = 0;
  long bytesRead = 0;
  long ftellFileSize = 0;

  /* check for path name */
  if (nfcFL._FW_BIN_PATH.c_str() == NULL) {
    NXPLOG_FWDNLD_E("Invalid FW file path!!!\n");
    return NFCSTATUS_FAILED;
  }

  /* check if the handle is not NULL then free the memory*/
  if (pFwHandle != NULL) {
    phDnldNfc_CloseFwLibHandle();
    pFwHandle = NULL;
  }

  /* Open the FW binary image file to be read */
  pFile = fopen(nfcFL._FW_BIN_PATH.c_str(), "r");
  if (NULL == pFile) {
    NXPLOG_FWDNLD_E("Failed to load FW binary image file!!!\n");
    return NFCSTATUS_FAILED;
  }

  /* Seek to the end of the file */
  fseek(pFile, 0, SEEK_END);

  /* get the actual length of the file */
  ftellFileSize = ftell(pFile);

  if (ftellFileSize > 0) {
    fileSize = ftellFileSize;
  } else {
    fileSize = 0;
  }

  /* Seek to the start of the file, to move file handle back to start of file*/
  fseek(pFile, 0, SEEK_SET);

  /* allocate the memory to read the FW binary image */
  pFwHandle = (void*)malloc(sizeof(uint8_t) * fileSize);

  /* check for valid memory allocation */
  if (NULL == pFwHandle) {
    NXPLOG_FWDNLD_E("Failed to allocate memory to load FW image !!!\n");
    fclose(pFile);
    return NFCSTATUS_FAILED;
  }

  /* Read the actual contents of the FW binary image */
  bytesRead =
      (uint32_t)fread(pFwHandle, sizeof(uint8_t), (size_t)fileSize, pFile);
  if (bytesRead != fileSize) {
    NXPLOG_FWDNLD_E("Unable to read the specified size from file !!!\n");
    fclose(pFile);
    free(pFwHandle);
    pFwHandle = NULL;
    return NFCSTATUS_FAILED;
  }

  /* Update the image info pointer to the caller */
  *pImgInfo = (uint8_t*)pFwHandle;
  *pImgInfoLen = (uint32_t)(bytesRead & 0xFFFFFFFF);

  /* close the FW binary image file */
  fclose(pFile);
  return NFCSTATUS_SUCCESS;
}

/*******************************************************************************
**
** Function         phDnldNfc_UnloadFW
**
** Description      Deinit the firmware handle
**
** Parameters       None
**
** Returns          NFC status
**
*******************************************************************************/
NFCSTATUS phDnldNfc_UnloadFW(void) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;
  int32_t status;

  /* check if the handle is not NULL then free the library */
  if (pFwHandle != NULL) {
    status = dlclose(pFwHandle);
    pFwHandle = NULL;

    dlerror(); /* Clear any existing error */
    if (status != 0) {
      wStatus = NFCSTATUS_FAILED;
      NXPLOG_FWDNLD_E("Free library file failed");
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phDnldNfc_SetDlRspTimeout
**
** Description      This function sets the timeout value dnld cmd response
**
** Parameters       timeout : timeout value for dnld response
**
** Returns          None
**
*******************************************************************************/
void phDnldNfc_SetDlRspTimeout(uint16_t timeout) {
  gpphDnldContext->TimerInfo.rspTimeout = timeout;
  NXPLOG_FWDNLD_E("phDnldNfc_SetDlRspTimeout timeout value =%x", timeout);
}

/*******************************************************************************
**
** Function         phDnldNfc_SetI2CFragmentLength
**
** Description      sets the fragment length as per chip being used
**
** Parameters       None
**
** Returns          None                -
**
*******************************************************************************/
void phDnldNfc_SetI2CFragmentLength() {
  if (NULL != gpphDnldContext) {
    if (IS_CHIP_TYPE_EQ(sn300u)) {
      gpphDnldContext->nxp_i2c_fragment_len = PH_TMLNFC_FRGMENT_SIZE_SN300;
    } else if (IS_CHIP_TYPE_GE(sn100u)) {
      gpphDnldContext->nxp_i2c_fragment_len = PH_TMLNFC_FRGMENT_SIZE_SNXXX;
    } else {
      gpphDnldContext->nxp_i2c_fragment_len = PH_TMLNFC_FRGMENT_SIZE_PN557;
    }
    NXPLOG_FWDNLD_D("fragment len set %u",
                    gpphDnldContext->nxp_i2c_fragment_len);
  } else {
    NXPLOG_FWDNLD_E("Error setting the fragment length");
  }
  return;
}
