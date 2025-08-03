/*
 * Copyright 2022-2023 NXP
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

#include <phNxpNciHal_ext.h>

/*******************************************************************************
**
** Function         phNxpNciHal_isULPDetSupported()
**
** Description      this function is to check ULPDet feature is supported or not
**
** Returns          true or false
*******************************************************************************/
bool phNxpNciHal_isULPDetSupported();

/*******************************************************************************
**
** Function         phNxpNciHal_setULPDetFlag()
**
** Description      this function is called by Framework API to set ULPDet mode
**                  enable/disable
**
** Parameters       flag - true to enable ULPDet, false to disable
**
** Returns          true or false
*******************************************************************************/
void phNxpNciHal_setULPDetFlag(bool flag);

/*******************************************************************************
**
** Function         phNxpNciHal_getULPDetFlag()
**
** Description      this function get the ULPDet state, true if it is enabled
**                  false if it is disabled
**
** Returns          true or false
*******************************************************************************/
bool phNxpNciHal_getULPDetFlag();

/*******************************************************************************
**
** Function         phNxpNciHal_propConfULPDetMode()
**
** Description      this function applies the configurations to enable/disable
**                  ULPDet Mode
**
** Parameters       bEnable - true to enable, false to disable
**
** Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
*******************************************************************************/
NFCSTATUS phNxpNciHal_propConfULPDetMode(bool bEnable);

/*******************************************************************************
**
** Function         phNxpNciHal_handleULPDetCommand()
**
** Description      This handles the ULPDET command and sets the ULPDET flag
**
** Returns          It returns number of bytes received.
*******************************************************************************/
int phNxpNciHal_handleULPDetCommand(uint16_t data_len, const uint8_t* p_data);
