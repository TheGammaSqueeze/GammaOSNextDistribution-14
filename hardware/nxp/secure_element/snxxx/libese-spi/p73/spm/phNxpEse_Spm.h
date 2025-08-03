/******************************************************************************
 *
 *  Copyright 2018-2019, 2023 NXP
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
 * \addtogroup SPI_Power_Management
 *
 * @{ */

#ifndef _PHNXPESE_SPM_H
#define _PHNXPESE_SPM_H

#include <phEseStatus.h>
#include <phNxpEseFeatures.h>
/*! SPI Power Manager (SPM) possible error codes */
typedef enum spm_power {
  SPM_POWER_DISABLE = 0,
  SPM_POWER_ENABLE,       /*!< SPM power disable */
  SPM_POWER_RESET,        /*!< SPM Reset pwer */
  SPM_POWER_PRIO_ENABLE,  /*!< SPM prio mode enable */
  SPM_POWER_PRIO_DISABLE, /*!< SPM prio mode disable */
  SPM_RECOVERY_RESET
} spm_power_t;

/*! SPI Power Manager (SPM) possible states */
typedef enum spm_state {
  SPM_STATE_INVALID = 0x0000,      /*!< Nfc i2c driver misbehaving */
  SPM_STATE_IDLE = 0x0100,         /*!< ESE is free to use */
  SPM_STATE_WIRED = 0x0200,        /*!< p61 is being accessed by DWP (NFCC)*/
  SPM_STATE_SPI = 0x0400,          /*!< ESE is being accessed by SPI */
  SPM_STATE_DWNLD = 0x0800,        /*!< NFCC fw download is in progress */
  SPM_STATE_SPI_PRIO = 0x1000,     /*!< Start of p61 access by SPI on priority*/
  SPM_STATE_SPI_PRIO_END = 0x2000, /*!< End of p61 access by SPI on priority*/
  SPM_STATE_SPI_FAILED = 0x0010,   /*SPI open/close failed*/
  SPM_STATE_JCOP_DWNLD = 0x8000    /*!< P73 state JCOP Download*/
} spm_state_t;

/**
 * \ingroup SPI_Power_Management
 * \brief This function opens the nfc i2c driver to manage power
 *                  and synchronization for ese secure element.
 *
 * \param[in]    pDevHandle       - Device handle to open.
 *
 * \retval       -On Success ESESTATUS_SUCCESS else proper error code
 */
ESESTATUS phNxpEse_SPM_Init(void* pDevHandle);

/**
 * \ingroup SPI_Power_Management
 * \brief TThis function closes the nfc i2c driver node.
 *
 *
 * \retval       -On Success ESESTATUS_SUCCESS else proper error code
 */
ESESTATUS phNxpEse_SPM_DeInit(void);

/**
 * \ingroup SPI_Power_Management
 * \brief This function request to the nfc i2c driver
 * to enable/disable power to ese. This api should be called
 *before sending any apdu to ese/once apdu exchange is done.
 *
 * \param[in]    arg       -input can be of  type spm_power_t.
 *
 * \retval       -On Success ESESTATUS_SUCCESS else proper error code
 */
ESESTATUS phNxpEse_SPM_ConfigPwr(spm_power_t arg);

/**
 * \ingroup SPI_Power_Management
 * \brief This function gets the current power state of ESE
 * \param[in]    current_state       -input is of type spm_state_t.
 *
 * \retval       -On Success ESESTATUS_SUCCESS else proper error code
 */
ESESTATUS phNxpEse_SPM_GetState(spm_state_t* current_state);

/**
 * \ingroup SPI_Power_Management
 * \brief  This function set the SPM power state
 *
 * \param[in]    arg - state value.
 *
 * \retval       -On Success ESESTATUS_SUCCESS else proper error code
 */
ESESTATUS phNxpEse_SPM_SetState(long arg);

/**
 * \ingroup SPI_Power_Management
 * \brief  phNxpEse_SPM_RelAccess
 *
 *
 * \retval       -On Success ESESTATUS_SUCCESS else proper error code
 */
ESESTATUS phNxpEse_SPM_RelAccess(void);

/**
 * \ingroup SPI_Power_Management
 * \brief   This function request to the nfc i2c driver
 *                  to set the chip type and power scheme.
 *
 * \param[in]    arg - set power scheme from config.
 *
 * \retval       -On Success ESESTATUS_SUCCESS else proper error code
 */
ESESTATUS phNxpEse_SPM_SetPwrScheme(long arg);

/**
 * \ingroup SPI_Power_Management
 * \brief    This function request to the nfc i2c driver
 *                  to set the chip type and power scheme.
 *
 * \param[in]    arg - arg.
 *
 * \retval       -On Success ESESTATUS_SUCCESS else proper error code
 */
ESESTATUS phNxpEse_SPM_DisablePwrControl(unsigned long arg);
/**
 * \ingroup SPI_Power_Management
 * \brief   This function is used to set the ese Update state.
 *
 * \param[in]    arg - eSE update status started/completed.
 *
 * \retval       -On Success ESESTATUS_SUCCESS else proper error code
 */
ESESTATUS phNxpEse_SPM_SetEseClientUpdateState(long arg);

/**
 * \ingroup SPI_Power_Management
 * \brief   This function is used to set the ese Update state.
 *
 * \param[in]    arg -  JCOP update status started/completed..
 *
 * \retval       -On Success ESESTATUS_SUCCESS else proper error code
 */
ESESTATUS phNxpEse_SPM_SetJcopDwnldState(long arg);

#endif /*  _PHNXPESE_SPM_H    */
/** @} */
