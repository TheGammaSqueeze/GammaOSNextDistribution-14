/*****************************************************************************
 * Copyright ©2017-2019 Gemalto – a Thales Company. All rights Reserved.
 *
 * This copy is licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *     http://www.apache.org/licenses/LICENSE-2.0 or https://www.apache.org/licenses/LICENSE-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.

 ****************************************************************************/

/**
 * @file
 * $Author$
 * $Revision$
 * $Date$
 *
 * T=1 checksum algorithms.
 *
 */

#ifndef CHECKSUM_H
#define CHECKSUM_H

/* Both function return checksum.
 *
 * When computing checksum. caller is responsible to store value at block end.
 * In case of CRC16, this is lowest byte first, followed by higher byte.
 *
 * When verifying checksum, function returns zero for a correct checksum.
 *
 */

unsigned lrc8(const void *s, size_t n);
unsigned crc_ccitt(uint16_t crc,  const void *s, size_t n);

#endif /* CHECKSUM_H */
