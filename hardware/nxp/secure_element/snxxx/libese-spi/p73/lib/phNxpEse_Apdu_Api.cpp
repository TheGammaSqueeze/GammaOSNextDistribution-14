/******************************************************************************
 *
 *  Copyright 2018-2019,2022 NXP
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
/**
 * \addtogroup ISO7816-4_application_protocol_implementation
 *
 * @{ */
#define LOG_TAG "NxpEseHal"
#include <ese_logs.h>
#include <log/log.h>
#include <phNxpEse_Apdu_Api.h>
#include <phNxpEse_Api.h>

static ESESTATUS phNxpEse_7816_FrameCmd(pphNxpEse_7816_cpdu_t pCmd,
                                        uint8_t** pcmd_data, uint32_t* cmd_len);

/******************************************************************************
 * Function         phNxpEse_7816_Transceive
 *
 * Description      This function prepares C-APDU and sends to p61 and receives
 *                  response from the p61. also it parses all required fields of
 *                  the response PDU.
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
ESESTATUS phNxpEse_7816_Transceive(pphNxpEse_7816_cpdu_t pCmd,
                                   pphNxpEse_7816_rpdu_t pRsp) {
  ESESTATUS status = ESESTATUS_FAILED;
  NXP_LOG_ESE_D(" %s Enter \n", __FUNCTION__);

  uint32_t cmd_len = 0;
  uint8_t* pCmd_data = NULL;
  phNxpEse_data pCmdTrans;
  phNxpEse_data pRspTrans;
  phNxpEse_memset(&pCmdTrans, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&pRspTrans, 0x00, sizeof(phNxpEse_data));

  if (NULL == pCmd || NULL == pRsp) {
    NXP_LOG_ESE_E(" %s Invalid parameter \n", __FUNCTION__);
    status = ESESTATUS_INVALID_PARAMETER;
  } else if (pCmd->cpdu_type > 1) {
    NXP_LOG_ESE_E(" %s Invalid cpdu type \n", __FUNCTION__);
    status = ESESTATUS_INVALID_CPDU_TYPE;
  } else if (0 < pCmd->le_type && NULL == pRsp->pdata) {
    /* if response is requested, but no valid res buffer
     * provided by application */
    NXP_LOG_ESE_E(" %s Invalid response buffer \n", __FUNCTION__);
    status = ESESTATUS_INVALID_BUFFER;
  } else {
    status = phNxpEse_7816_FrameCmd(pCmd, &pCmd_data, &cmd_len);
    if (ESESTATUS_SUCCESS == status) {
      pCmdTrans.len = cmd_len;
      pCmdTrans.p_data = pCmd_data;
      status = phNxpEse_Transceive(&pCmdTrans, &pRspTrans);
      if (ESESTATUS_SUCCESS != status) {
        NXP_LOG_ESE_E(" %s phNxpEse_Transceive Failed \n", __FUNCTION__);
        if ((pRspTrans.len > 0) && (pRspTrans.p_data != NULL)) {
          pRsp->sw2 = *(pRspTrans.p_data + (pRspTrans.len - 1));
          pRspTrans.len--;
          pRsp->sw1 = *(pRspTrans.p_data + (pRspTrans.len - 1));
          pRspTrans.len--;
          pRsp->len = pRspTrans.len;
        }
      } else {
        if ((pRspTrans.len > 0) && (pRspTrans.p_data != NULL)) {
          pRsp->sw2 = *(pRspTrans.p_data + (pRspTrans.len - 1));
          pRspTrans.len--;
          pRsp->sw1 = *(pRspTrans.p_data + (pRspTrans.len - 1));
          pRspTrans.len--;
          pRsp->len = pRspTrans.len;
          NXP_LOG_ESE_D("pRsp->len %d", pRsp->len);
          if (pRspTrans.len > 0 && NULL != pRsp->pdata) {
            phNxpEse_memcpy(pRsp->pdata, pRspTrans.p_data, pRspTrans.len);
            status = ESESTATUS_SUCCESS;
          } else if (pRspTrans.len == 0) {
            status = ESESTATUS_SUCCESS;
          } else {
            /* if application response buffer is null and data is present */
            NXP_LOG_ESE_E("Invalid Res buffer");
            status = ESESTATUS_FAILED;
          }
          NXP_LOG_ESE_D("Freeing memory pRspTrans.p_data ");
          phNxpEse_free(pRspTrans.p_data);
          pRspTrans.p_data = NULL;
          pRspTrans.len = 0;
        } else {
          NXP_LOG_ESE_E("pRspTrans.len error = %d", pRspTrans.len);
          status = ESESTATUS_FAILED;
        }
      }
      if (pCmd_data != NULL) {
        NXP_LOG_ESE_D("Freeing memory pCmd_data");
        phNxpEse_free(pCmd_data);
      }
    }
  }
  NXP_LOG_ESE_D(" %s Exit status 0x%x \n", __FUNCTION__, status);
  return status;
}

/**
 * \ingroup ISO7816-4_application_protocol_implementation
 * \brief Frames ISO7816-4 command.
 *                  pCmd: 7816 command structure.
 *                  pcmd_data: command buffer pointer.
 *
 * \param[in]       pCmd- Structure pointer passed from
 *application
 * \param[in]        **pcmd_data - Hold the allocated memory buffer for
 *command.
 * \param[in]        *cmd_len - Hold the buffer length, update by this
 *function
 *
 * \retval  ESESTATUS_SUCCESS on Success else proper error code
 *
 */

static ESESTATUS phNxpEse_7816_FrameCmd(pphNxpEse_7816_cpdu_t pCmd,
                                        uint8_t** pcmd_data,
                                        uint32_t* cmd_len) {
  uint32_t cmd_total_len = MIN_HEADER_LEN; /* header is always 4 bytes */
  uint8_t* pbuff = NULL;
  uint32_t index = 0;
  uint8_t lc_len = 0;
  uint8_t le_len = 0;

  NXP_LOG_ESE_D("%s  pCmd->lc = %d, pCmd->le_type = %d", __FUNCTION__, pCmd->lc,
                pCmd->le_type);
  /* calculate the total buffer length */
  if (pCmd->lc > 0) {
    if (pCmd->cpdu_type == 0) {
      cmd_total_len += 1; /* 1 byte LC */
      lc_len = 1;
    } else {
      cmd_total_len += 3; /* 3 byte LC */
      lc_len = 3;
    }

    cmd_total_len += pCmd->lc; /* add data length */
    if (pCmd->pdata == NULL) {
      NXP_LOG_ESE_E("%s Invalid data buffer from application ", __FUNCTION__);
      return ESESTATUS_INVALID_BUFFER;
    }
  } else {
    lc_len = 0;
    NXP_LOG_ESE_D("%s lc (data) field is not present %d", __FUNCTION__,
                  pCmd->lc);
  }

  if (pCmd->le_type > 0) {
    if (pCmd->le_type == 1) {
      cmd_total_len += 1; /* 1 byte LE */
      le_len = 1;
    } else if ((pCmd->le_type == 2 || pCmd->le_type == 3)) {
      /* extended le */
      if ((pCmd->le_type == 3) && (lc_len == 0)) {
        /* if data field not present, than only LE would be three bytes */
        cmd_total_len += 3; /* 3 byte LE */
        le_len = 3;
      } else if ((pCmd->le_type == 2) && (lc_len != 0)) {
        /* if data field present, LE would be two bytes */
        cmd_total_len += 2; /* 2 byte LE */
        le_len = 2;
      } else {
        NXP_LOG_ESE_E("%s wrong LE type  %d", __FUNCTION__, pCmd->le_type);
        cmd_total_len += pCmd->le_type;
        le_len = pCmd->le_type;
      }
    } else {
      NXP_LOG_ESE_E("%s wrong cpdu_type value %d", __FUNCTION__,
                    pCmd->cpdu_type);
      return ESESTATUS_INVALID_CPDU_TYPE;
    }
  } else {
    le_len = 0;
    NXP_LOG_ESE_D("%s le field is not present", __FUNCTION__);
  }
  NXP_LOG_ESE_D("%s cmd_total_len = %d, le_len = %d, lc_len = %d", __FUNCTION__,
                cmd_total_len, le_len, lc_len);

  pbuff = (uint8_t*)phNxpEse_calloc(cmd_total_len, sizeof(uint8_t));
  if (pbuff == NULL) {
    NXP_LOG_ESE_E("%s Error allocating memory", __FUNCTION__);
    return ESESTATUS_INSUFFICIENT_RESOURCES;
  }
  *cmd_len = cmd_total_len;
  *pcmd_data = pbuff;
  index = 0;

  *(pbuff + index) = pCmd->cla;
  index++;

  *(pbuff + index) = pCmd->ins;
  index++;

  *(pbuff + index) = pCmd->p1;
  index++;

  *(pbuff + index) = pCmd->p2;
  index++;

  /* lc_len can be 0, 1 or 3 bytes */
  if (lc_len > 0) {
    if (lc_len == 1) {
      *(pbuff + index) = pCmd->lc;
      index++;
    } else {
      /* extended cmd buffer*/
      *(pbuff + index) = 0x00; /* three byte lc */
      index++;
      *(pbuff + index) = ((pCmd->lc & 0xFF00) >> 8);
      index++;
      *(pbuff + index) = (pCmd->lc & 0x00FF);
      index++;
    }
    /* copy the lc bytes data */
    phNxpEse_memcpy((pbuff + index), pCmd->pdata, pCmd->lc);
    index += pCmd->lc;
  }
  /* le_len can be 0, 1, 2 or 3 bytes */
  if (le_len > 0) {
    if (le_len == 1) {
      if (pCmd->le == 256) {
        /* if le is 256 assign max value*/
        *(pbuff + index) = 0x00;
      } else {
        *(pbuff + index) = (uint8_t)pCmd->le;
      }
      index++;
    } else {
      if (pCmd->le == 65536) {
        if (le_len == 3) {
          /* assign max value */
          *(pbuff + index) = 0x00; /* three byte le */
          index++;
        }
        *(pbuff + index) = 0x00;
        index++;
        *(pbuff + index) = 0x00;
        index++;
      } else {
        if (le_len == 3) {
          *(pbuff + index) = 0x00; /* three byte le */
          index++;
        }
        *(pbuff + index) = ((pCmd->le & 0x0000FF00) >> 8);
        index++;
        *(pbuff + index) = (pCmd->le & 0x000000FF);
        index++;
      }
    }
  }
  NXP_LOG_ESE_D("Exit %s cmd_total_len = %d, index = %d", __FUNCTION__, index,
                cmd_total_len);
  return ESESTATUS_SUCCESS;
}
/** @} */
