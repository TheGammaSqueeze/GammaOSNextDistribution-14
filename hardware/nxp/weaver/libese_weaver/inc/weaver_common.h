/******************************************************************************
 *
 *  Copyright 2020, 2022 NXP
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

#ifndef _WEAVER_COMMON_H_
#define _WEAVER_COMMON_H_

#include <vector>

enum Status_Weaver {
  WEAVER_STATUS_OK,            // Success
  WEAVER_STATUS_FAILED,        // any failure
  WEAVER_STATUS_INCORRECT_KEY, // Specific to read api in case of incorrect key
  WEAVER_STATUS_THROTTLE,      // Specific to read api while throttle error
};

typedef struct SlotInfo {
  /** The number of slots available. */
  uint32_t slots;
  /** The number of bytes used for a key. */
  uint32_t keySize;
  /** The number of bytes used for a value. */
  uint32_t valueSize;
} SlotInfo;

typedef struct ReadRespInfo {
  /** The time to wait, in milliseconds, before making the next request. */
  uint32_t timeout;
  /** The value read from the slot or empty if the value was not read. */
  std::vector<uint8_t> value;
} ReadRespInfo;

typedef struct GetDataRespInfo {
  /** The time to wait, in seconds, before making the next request. */
  uint32_t timeout;
  /** The current number of failure count */
  uint16_t failure_count;
} GetDataRespInfo;

#endif /* _WEAVER_COMMON_H_ */
