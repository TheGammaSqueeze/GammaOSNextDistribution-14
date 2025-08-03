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
#ifndef ATP_H_
#define ATP_H_

//************************************ Includes ********************************
#include <stdint.h>
#include <string.h>
//************************************ Defines *********************************

#define ATP_MAX_ALLOWED_LENGTH 39

#define EXPECTED_ATP_LENGTH 37

#define DEFAULT_PWT 50

#define LEN_LENGTH_IN_ATP 1
#define LEN_OFFSET_IN_ATP 0
#define VENDOR_ID_OFFSET_IN_ATP 1
#define VENDOR_ID_LENGTH_IN_ATP 5
#define BWT_OFFSET_IN_ATP 6
#define BWT_LENGTH_IN_ATP 2
#define CWT_OFFSET_IN_ATP 8
#define PWT_OFFSET_IN_ATP 9
#define MSF_OFFSET_IN_ATP 10
#define MSF_LENGTH_IN_ATP 2
#define CHECKSUM_TYPE_OFFSET_IN_ATP 12
#define IFSC_OFFSET_IN_ATP 13
#define HISTORICAL_CHARACTER_OFFSET_IN_ATP 14
#define HISTORICAL_CHARACTER_LENGTH_IN_ATP 22
#define CHECKSUM_OFFSET_IN_ATP 36
#define CRC_LENGTH_IN_ATP 2

//************************************ Structs *********************************
typedef enum { LRC, CRC } ChecksumType;

typedef struct {
  uint8_t len;
  char vendorID[5];
  uint16_t bwt;
  uint8_t cwt;
  uint8_t pwt;
  uint16_t msf;
  ChecksumType checksumType;
  uint8_t ifsc;
  char historicalCharacter[22];
  uint16_t checksum;
} Atp;

/**
 * This is the extern field that the whole system will have access to.
 */
extern Atp ATP;

/**
 * Gets the value of the checksum stored in the array.
 *
 * @param array The array that contains the checksum.
 * @param checksumStartPosition The position where the checksum starts in array.
 *
 * @return The value of the checksum.
 */
uint16_t Atp_getChecksumValue(uint8_t *array, int checksumStartPosition);

/**
 * Sets the ATP struct that will be available for the whole system.
 *
 * @param baAtp The ATP as a byte array.
 *
 * @return 0 If everything is Ok, -1 otherwise.
 */
int Atp_setAtp(uint8_t *baAtp);

/**
 * Gets the ATP stored
 *
 *
 * @return pointer to the ATP array.
 */
uint8_t *Atp_getAtp();

#endif /* ATP_H_ */
