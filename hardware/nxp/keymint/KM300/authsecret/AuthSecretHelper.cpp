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
#define LOG_TAG "AuthSecret-Hal"
#include "AuthSecretHelper.h"

AuthSecretHelper *AuthSecretHelper::sInstance = nullptr;

AuthSecretHelper *AuthSecretHelper::getInstance() {
  if (sInstance == nullptr) {
    sInstance = new AuthSecretHelper;
  }
  return sInstance;
}

bool AuthSecretHelper::constructApdu(Instruction ins,
                                   const std::vector<uint8_t> &input,
                                   std::vector<uint8_t> &out,
                                   std::vector<uint8_t> timeout) {
  /* Insert CLA, INS, P1, P2*/
  out.push_back(static_cast<uint8_t>(APDU_CLS));
  out.push_back(static_cast<uint8_t>(ins));
  out.push_back(static_cast<uint8_t>(APDU_P1));
  out.push_back(static_cast<uint8_t>(APDU_P2));

  switch (ins) {
  case Instruction::INS_VERIFY_PIN: {
    cppbor::Array array;
    if (input.size()) {
      array.add(input);
    }
    if (timeout.size()) {
      array.add(timeout);
    }
    std::vector<uint8_t> command = array.encode();
    out.push_back(static_cast<uint8_t>(command.size()));
    out.insert(out.end(), command.begin(), command.end());
  } break;
  case Instruction::INS_CLEAR_APPROVED_STATUS:
    /* Nothing to do. No Payload for Clear approved status*/
    break;
  default:
    LOG(ERROR) << "Unknown INS. constructApdu failed";
    return false;
  }

  /* Insert LE */
  out.push_back(static_cast<uint8_t>(0x00));
  return true;
}

uint64_t AuthSecretHelper::extractTimeoutValue(std::vector<uint8_t> data) {
  LOG(INFO) << StringPrintf("%s: Enter", __func__);

  auto [parsedData, _, errMsg] = cppbor::parse(data);
  if (!parsedData) {
    LOG(ERROR) << StringPrintf("parsedData is null");
    return INVALID_DATA_STATUS_TIMER_VALUE;
  }
  auto dataArray = parsedData->asArray();
  if (!dataArray) {
    LOG(ERROR) << StringPrintf("parsedData is not proper CBOR Array");
    return INVALID_DATA_STATUS_TIMER_VALUE;
  }

  uint64_t timeout = CLEAR_APPROVE_STATUS_TIMER_VALUE;
  if ((dataArray->size() > 1) && (dataArray->get(INDEX_TIMER_VAL)->asBstr())) {
    std::vector<uint8_t> timeoutVector =
        dataArray->get(INDEX_TIMER_VAL)->asBstr()->value();
    if (timeoutVector.size() == TIMEOUT_VECTOR_SIZE) {
      timeout = (((timeoutVector[0] << 8) | (timeoutVector[1])) * 60 * 60) +
                ((timeoutVector[2] << 8) | timeoutVector[3]);
    }
  }

  LOG(INFO) << StringPrintf("%s:Exit", __func__);
  return timeout;
}

bool AuthSecretHelper::checkVerifyStatus(std::vector<uint8_t> resp) {
  bool status = false;

  auto [parsedData, _, errMsg] = cppbor::parse(resp);
  if (!parsedData) {
    LOG(ERROR) << StringPrintf("parsedData is null");
    return status;
  }
  auto dataArray = parsedData->asArray();
  if (!dataArray) {
    LOG(ERROR) << StringPrintf("parsedData is not proper CBOR Array");
    return status;
  }

  /* Get Item 1 (status) in response CBOR apdu, if value is 0 (uint) status is
   * OK. */
  if ((dataArray->size() > 0) && (dataArray->get(INDEX_STATUS_VAL)->asUint())) {
    uint64_t value = dataArray->get(INDEX_STATUS_VAL)->asUint()->value();
    if (!value) {
      status = true;
    }
  }
  return status;
}