/******************************************************************************
 *
 *  Copyright (C) 2018 ST Microelectronics S.A.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 ******************************************************************************/

#ifndef _STESEAPI_H_
#define _STESEAPI_H_

#include <stdint.h>

typedef struct StEse_data {
  uint16_t len;    /*!< length of the buffer */
  uint8_t* p_data; /*!< pointer to a buffer */
} StEse_data;

typedef enum {
  ESESTATUS_SUCCESS,
  ESESTATUS_FAILED,
  ESESTATUS_INVALID_PARAMETER,
  ESESTATUS_NOT_INITIALISED,
  ESESTATUS_ALREADY_INITIALISED,
  ESESTATUS_FEATURE_NOT_SUPPORTED,
  ESESTATUS_CONNECTION_SUCCESS,
  ESESTATUS_CONNECTION_FAILED,
  ESESTATUS_BUSY,
  ESESTATUS_UNKNOWN_ERROR,
} ESESTATUS;

typedef enum {
  ESE_STATUS_CLOSE = 0x00,
  ESE_STATUS_OPEN,
} SpiEse_status;

/* SPI Control structure */
typedef struct ese_Context {
  SpiEse_status EseLibStatus; /* Indicate if Ese Lib is open or closed */
  void* pDevHandle;
} ese_Context_t;

/**
 * StEse_init
 *
 * This function initializes protocol stack instance variables
 *
 * @param  void
 *
 * @return This function return ESESTATUS_SUCCES (0) in case of success
 *         In case of failure returns other failure value.
 *
 */
ESESTATUS StEse_init();

/**
 * StEse_Transceive
 *
 * This function prepares the C-APDU, send to ESE and then receives the
 * response from ESE, decode it and returns data.
 *
 * @param pCmd: Command to eSE
 * @param pRsp: Response from eSE (Returned data to be freed
 *  after copying)
 *
 * @return ESESTATUS_SUCCESS On Success ESESTATUS_SUCCESS else proper error code
 *
 */
ESESTATUS StEse_Transceive(StEse_data* pCmd, StEse_data* pRsp);

/**
 * StEse_close
 *
 * This function close the ESE interface and free all resources.
 *
 * @param      void
 *
 * @return  ESESTATUS_SUCCESS Always return ESESTATUS_SUCCESS (0).
 *
 */

ESESTATUS StEse_close(void);

/**
 * StEseApi_isOpen
 *
 * This function checks if the hal is opened.
 *
 * @param    void
 *
 * @return   false if it is close, otherwise true
 *
 */
bool StEseApi_isOpen();

/**
 * StEse_getAtr
 *
 * This function get the last ATR received.
 *
 * @param    void
 *
 * @return   pointer to the ATR array.
 *
 */
uint8_t* StEse_getAtr(void);

/**
 * StEse_Reset
 *
 * This function get the last ATR received.
 *
 * @param    void
 *
 * @return   ESESTATUS_SUCCESS is successful, ESESTATUS_SUCCESS otherwise
 *
 */
ESESTATUS StEse_Reset(void);

#endif /* _STESEAPI_H_ */
