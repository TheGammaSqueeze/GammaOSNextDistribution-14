/******************************************************************************
 *
 *  Copyright 2018-2019,2021 NXP
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

#ifndef _PHNXPESE_APDU_H
#define _PHNXPESE_APDU_H
#include <phEseStatus.h>

/**
 * \brief 7816-4 APDU command Header Size
 *
 */

#define MIN_HEADER_LEN 4

/**
 * \ingroup ISO7816-4_application_protocol_implementation
 * \brief Command data unit structure params
 *
 */
typedef struct phNxpEse_7816_cpdu {
  uint8_t cla;       /*!< Class of instruction */
  uint8_t ins;       /*!< Instruction code */
  uint8_t p1;        /*!< Instruction parameter 1 */
  uint8_t p2;        /*!< Instruction parameter 2 */
  uint16_t lc;       /*!< No of data present in the data field of the command */
  uint8_t cpdu_type; /*!< 0 - short len, 1 = extended len, this field is valid
                        only if le > 0*/
  uint8_t* pdata;    /*!< application data*/
  uint8_t le_type; /*!< 0 - Le absent ,1 - one byte le,2 - two byte le or 3 - 3
                      byte le*/
  uint32_t le;     /*!< le value field */
} phNxpEse_7816_cpdu_t;

/**
 * \ingroup ISO7816-4_application_protocol_implementation
 * \brief Command data unit structure params
 *
 */
typedef phNxpEse_7816_cpdu_t* pphNxpEse_7816_cpdu_t;

/**
 * \ingroup ISO7816-4_application_protocol_implementation
 * \brief Response data unit structure params
 *
 */
typedef struct phNxpEse_7816_rpdu {
  uint8_t sw1;    /*!< Status byte most significant byte */
  uint8_t sw2;    /*!< Status byte least significant byte */
  uint8_t* pdata; /*!< Buffer allocated by caller*/
  uint16_t len;   /*!< Length of the buffer, updated by calling api */
} phNxpEse_7816_rpdu_t;

/**
 * \ingroup ISO7816-4_application_protocol_implementation
 * \brief Response data unit structure params
 *
 */
typedef phNxpEse_7816_rpdu_t* pphNxpEse_7816_rpdu_t;

/**
 * \ingroup ISO7816-4_application_protocol_implementation
 * \brief This function prepares C-APDU and sends to p61 and receives response
 *from the p61.
 * also it parses all required fields of the response PDU.
 *
 * \param[in]       pCmd - CMD to p61
 * \param[out]      pRsp - RSP from p61(all required memory
 *allocated by caller)
 *
 * \retval ESESTATUS_SUCCESS - On Success #pphNxpEse_7816_rpdu_t all fields are
 *filled correctly.
 *          else proper error code.
 * \retval ESESTATUS_INVALID_PARAMETER - If any invalid buffer passed from
 *application \n
 * \retval ESESTATUS_INSUFFICIENT_RESOURCES - Any problem occurred during
 *allocating the memory \n
 * \retval ESESTATUS_INVALID_BUFFER - If any invalid buffer received \n
 * \retval ESESTATUS_FAILED - Any other error occurred. \n
 */

ESESTATUS phNxpEse_7816_Transceive(pphNxpEse_7816_cpdu_t pCmd,
                                   pphNxpEse_7816_rpdu_t pRsp);

#endif /*  _PHNXPESE_APDU_H    */
/** @} */
