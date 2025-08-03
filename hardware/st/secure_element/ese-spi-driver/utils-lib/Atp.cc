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
#define LOG_TAG "Atp"
#include "Atp.h"
#include "Iso13239CRC.h"
#include "android_logmsg.h"

Atp ATP = {.bwt = 0x0690, .checksumType = CRC, .ifsc = 0xFE};

uint8_t gATP[ATP_MAX_ALLOWED_LENGTH];
//************************************ Functions *******************************

/*******************************************************************************
**
** Function         Atp_getChecksumValue
**
** Description      Gets the value of the checksum stored in the array.
**
** Parameters       array                 - array that contains the checksum.
**                  checksumStartPosition - checksum start position in array.
**
** Returns          checksum value
**
*******************************************************************************/
uint16_t Atp_getChecksumValue(uint8_t *array, int checksumStartPosition) {
  return (uint16_t)((uint8_t)array[checksumStartPosition + 1] << 8) |
         (uint8_t)array[checksumStartPosition];
}

/*******************************************************************************
**
** Function         Atp_setAtp
**
** Description     Sets the ATP struct available for the whole system.
**
** Parameters       baAtp - ATP as a byte array.
**
** Returns          0 If everything is Ok, -1 otherwise.
**
*******************************************************************************/
int Atp_setAtp(uint8_t *baAtp) {
  uint8_t i;
  Atp tmpAtp;

  // Length
  tmpAtp.len = (uint8_t)baAtp[LEN_OFFSET_IN_ATP];

  if (tmpAtp.len > ATP_MAX_ALLOWED_LENGTH) {
    return -1;
  }
  memcpy(gATP, baAtp, tmpAtp.len);
  tmpAtp.checksum = Atp_getChecksumValue(baAtp, CHECKSUM_OFFSET_IN_ATP);

  // Check CRC
  if (computeCrc(baAtp, LEN_LENGTH_IN_ATP + tmpAtp.len - CRC_LENGTH_IN_ATP) !=
      tmpAtp.checksum) {
    STLOG_HAL_E("Error computing CRC");
    return -1;
  }

  // CHECKSUM TYPE
  if (baAtp[CHECKSUM_TYPE_OFFSET_IN_ATP] == 0) {
    tmpAtp.checksumType = LRC;
  } else if (baAtp[CHECKSUM_TYPE_OFFSET_IN_ATP] == 1) {
    tmpAtp.checksumType = CRC;
  } else {
    // Unexpected value.
    STLOG_HAL_E("Unexpected value of checksum type.");
    return -1;
  }

  // Vendor ID
  for (i = 0; i < VENDOR_ID_LENGTH_IN_ATP; i++) {
    tmpAtp.vendorID[i] = baAtp[VENDOR_ID_OFFSET_IN_ATP + i];
  }

  // BWT
  tmpAtp.bwt = 0;
  for (i = 0; i < BWT_LENGTH_IN_ATP; i++) {
    tmpAtp.bwt =
        (uint16_t)(tmpAtp.bwt << 8) + (uint8_t)baAtp[BWT_OFFSET_IN_ATP + i];
  }

  // CWT
  tmpAtp.cwt = (uint8_t)baAtp[CWT_OFFSET_IN_ATP];

  // PWT
  tmpAtp.pwt = (uint8_t)baAtp[PWT_OFFSET_IN_ATP];

  // MSF
  STLOG_HAL_V("Configuring ATP %i", tmpAtp.msf);
  tmpAtp.msf = 0;
  for (i = 0; i < MSF_LENGTH_IN_ATP; i++) {
    tmpAtp.msf =
        (uint16_t)(tmpAtp.msf << 8) + (uint8_t)baAtp[MSF_OFFSET_IN_ATP + i];
    STLOG_HAL_V("Loop ATP %i", baAtp[MSF_OFFSET_IN_ATP + i]);
  }

  // IFSC
  tmpAtp.ifsc = (uint8_t)baAtp[IFSC_OFFSET_IN_ATP];

  // HISTORICAL CHARACTER
  for (i = 0; i < HISTORICAL_CHARACTER_LENGTH_IN_ATP; i++) {
    tmpAtp.historicalCharacter[i] =
        baAtp[HISTORICAL_CHARACTER_OFFSET_IN_ATP + i];
  }

  // Set the actual ATP and return with no error.
  ATP = tmpAtp;
  return 0;
}

/*******************************************************************************
**
** Function         Atp_getAtp
**
** Description     Gets the ATP stored.
**
**
** Returns          pointer to the ATP array
**
*******************************************************************************/
uint8_t *Atp_getAtp() { return &gATP[0]; }
