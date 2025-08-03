/******************************************************************************
 *
 *  Copyright 2023-2024 NXP
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

#pragma once

#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <cppbor.h>
#include <cppbor_parse.h>

// Timeout value in seconds for invalid data status
#define INVALID_DATA_STATUS_TIMER_VALUE 0

// Default timeout value in seconds for clear approved status.
#define CLEAR_APPROVE_STATUS_TIMER_VALUE 60

// index 0 & 1 in hours, index 2 & 3 in seconds [hr] [hr] : [secs] [secs]
#define TIMEOUT_VECTOR_SIZE 4

#define DEFAULT_SESSION_TIMEOUT (3 * 1000) // 3 secs,default value

#define APDU_CLS 0x80
#define APDU_P1 0x00
#define APDU_P2 0x00
#define APDU_RESP_STATUS_OK 0x9000
#define INDEX_STATUS_VAL 0x00
#define INDEX_TIMER_VAL 0x01

using android::base::StringPrintf;

enum class Instruction {
  INS_VERIFY_PIN = 0x20,
  INS_CLEAR_APPROVED_STATUS = 0x30,
};

/**
 * AuthSecretHelper is a helper class for AuthSecret HAL implementation.
 *
 */
class AuthSecretHelper {
public:
  /**
   * \brief static function to get the singleton instance of
   *        AuthSecretHelper class
   *
   * \retval timeout value.
   */
  static AuthSecretHelper *getInstance();
  /**
   * \brief Extracts timeout value from applet if applicable,
   *        else returns default value.
   *
   * \retval timeout value.
   *
   * \param[data] Response APDU data from VERIFY PIN command.
   */
  uint64_t extractTimeoutValue(std::vector<uint8_t> data);

  /**
   * \brief Check the status of VERIFY PIN command response
   *        CBOR data.
   *
   * \retval true if VERIFY PIN is success, else returns false.
   *
   * \param[resp] Response APDU data from VERIFY PIN command.
   */
  bool checkVerifyStatus(std::vector<uint8_t> resp);

  /**
   * \brief Function to frame the input data in to CBOR format
   *        apdu
   *
   * \retval returns true if constructing CBOR APDU is success,
   *         else returns false.
   *
   * \param[ins] Input instrution type.
   * \param[input] Input payload data
   * \param[out] Pointer for output CBOR APDU vector.
   * \param[timeout] Timeout value as vector for VERIFY PIN Ins
   */
  bool constructApdu(Instruction ins, const std::vector<uint8_t> &input,
                     std::vector<uint8_t> &out, std::vector<uint8_t> timeout);

private:
  static AuthSecretHelper *sInstance;
};
