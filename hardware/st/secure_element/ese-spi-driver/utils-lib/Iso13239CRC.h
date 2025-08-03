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
#ifndef ISO13239CRC_H_
#define ISO13239CRC_H_

//************************************ Defines *********************************
#define CRC_PRESET 0xFFFF
#define CRC_POLYNOMIAL 0x8408

#include <stdint.h>

//************************************ Structs *********************************

//************************************ Functions *******************************

/**
 * Computes the 16-bit CRC of the specified function.
 *
 * @param data The data to compute the CRC over.
 * @param len The length of the data.
 *
 * @return The CRC of the data. -1 if something went wrong.
 */
uint16_t computeCrc(uint8_t *data, int len);

#endif /* ISO13239CRC_H_ */
