/******************************************************************************
 *
 *  Copyright 2020, 2022-2023 NXP
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

#define LOG_TAG "weaver-impl"
#include <weaver-impl.h>
#include <weaver_parser-impl.h>
#include <weaver_transport-impl.h>
#include <weaver_utils.h>

WeaverImpl *WeaverImpl::s_instance = NULL;
std::once_flag WeaverImpl::s_instanceFlag;

/**
 * \brief static function to get the singleton instance of WeaverImpl class
 *
 * \retval instance of WeaverImpl.
 */
WeaverImpl *WeaverImpl::getInstance() {
  /* call_once c++11 api which executes the passed function ptr exactly once,
   * even if called concurrently, from several threads
   */
  std::call_once(s_instanceFlag, &WeaverImpl::createInstance);
  return s_instance;
}

/* Private function to create the instance of self class
 * Same will be used for std::call_once
 */
void WeaverImpl::createInstance() {
  LOG_D(TAG, "Entry");
  s_instance = new WeaverImpl;
}

/**
 * \brief Function to initialize Weaver Interface
 *
 * \retval This function return Weaver_STATUS_OK (0) in case of success
 *         In case of failure returns other Status_Weaver.
 */
Status_Weaver WeaverImpl::Init() {
  LOG_D(TAG, "Entry");
  mTransport = WeaverTransportImpl::getInstance();
  mParser = WeaverParserImpl::getInstance();
  RETURN_IF_NULL(mTransport, WEAVER_STATUS_FAILED, "Transport is NULL");
  RETURN_IF_NULL(mParser, WEAVER_STATUS_FAILED, "Parser is NULL");
  std::vector<std::vector<uint8_t>> aid;
  mParser->getAppletId(aid);
  if (!mTransport->Init(std::move(aid))) {
    LOG_E(TAG, "Not able to Initialize Transport Interface");
    LOG_D(TAG, "Exit : FAILED");
    return WEAVER_STATUS_FAILED;
  }
  LOG_D(TAG, "Exit : SUCCESS");
  return WEAVER_STATUS_OK;
}

/**
 * \brief Function to read slot information
 * \param[out]   slotInfo - slot information values read out
 *
 * \retval This function return Weaver_STATUS_OK (0) in case of success
 *         In case of failure returns other Status_Weaver errorcodes.
 */
Status_Weaver WeaverImpl::GetSlots(SlotInfo &slotInfo) {
  LOG_D(TAG, "Entry");
  RETURN_IF_NULL(mTransport, WEAVER_STATUS_FAILED, "Transport is NULL");
  RETURN_IF_NULL(mParser, WEAVER_STATUS_FAILED, "Parser is NULL");
  Status_Weaver status = WEAVER_STATUS_FAILED;
  std::vector<uint8_t> getSlotCmd;
  std::vector<uint8_t> resp;
  /* transport library don't require open applet
   * open will be done as part of send */
  if (mParser->FrameGetSlotCmd(getSlotCmd) &&
      mTransport->Send(getSlotCmd, resp)) {
    status = WEAVER_STATUS_OK;
  } else {
    LOG_E(TAG, "Failed to perform getSlot Request");
  }
#ifndef INTERVAL_TIMER
  if (!close()) {
    // Channel Close Failed
    LOG_E(TAG, "Failed to Close Channel");
  }
#endif
  if (status == WEAVER_STATUS_OK) {
    status = mParser->ParseSlotInfo(std::move(resp), slotInfo);
    LOG_D(TAG, "Total Slots (%u) ", slotInfo.slots);
  } else {
    LOG_E(TAG, "Failed Parsing getSlot Response");
  }
  LOG_D(TAG, "Exit");
  return status;
}

/* Internal close api for transport close */
bool WeaverImpl::close() {
  LOG_D(TAG, "Entry");
  bool status = true;
  RETURN_IF_NULL(mTransport, WEAVER_STATUS_FAILED, "Transport is NULL");
  if (!mTransport->CloseApplet()) {
    status = false;
  }
  LOG_D(TAG, "Exit");
  return status;
}

/**
 * \brief Function to read value of specific key & slotId
 * \param[in]    slotId -       input slotId which's information to be read
 * \param[in]    key -          input key which's information to be read
 * \param[out]   readRespInfo - read information values to be read out
 *
 * \retval This function return Weaver_STATUS_OK (0) in case of success
 *         In case of failure returns other Status_Weaver errorcodes.
 */
Status_Weaver WeaverImpl::Read(uint32_t slotId, const std::vector<uint8_t> &key,
                               ReadRespInfo &readRespInfo) {
  LOG_D(TAG, "Entry");
  RETURN_IF_NULL(mTransport, WEAVER_STATUS_FAILED, "Transport is NULL");
  RETURN_IF_NULL(mParser, WEAVER_STATUS_FAILED, "Parser is NULL");
  Status_Weaver status = WEAVER_STATUS_FAILED;
  std::vector<uint8_t> cmd;
  std::vector<uint8_t> resp;
  std::vector<uint8_t> aid;
  /* transport library don't require open applet
   * open will be done as part of send */
  LOG_D(TAG, "Read from Slot (%u)", slotId);
  if (mParser->FrameReadCmd(slotId, key, cmd) &&
      mTransport->Send(cmd, resp)) {
    status = WEAVER_STATUS_OK;
  }
  if (status == WEAVER_STATUS_OK) {
    status = mParser->ParseReadInfo(resp, readRespInfo);
    if (status == WEAVER_STATUS_THROTTLE ||
        status == WEAVER_STATUS_INCORRECT_KEY) {
      cmd.clear();
      resp.clear();
      if (mParser->FrameGetDataCmd(WeaverParserImpl::sThrottleGetDataP1, (uint8_t)slotId, cmd) &&
          (mTransport->Send(cmd, resp))) {
        GetDataRespInfo getDataInfo;
        if (mParser->ParseGetDataInfo(std::move(resp), getDataInfo) == WEAVER_STATUS_OK) {
          /* convert timeout from getDataInfo sec to millisecond assign same to read response */
          readRespInfo.timeout = (getDataInfo.timeout * 1000);
        }
      }
    }
  } else {
    LOG_E(TAG, "Failed to perform Read Request for slot (%u)", slotId);
  }
#ifndef INTERVAL_TIMER
  if (!close()) {
    // Channel Close Failed
    LOG_E(TAG, "Failed to Close Channel");
  }
#endif
  LOG_D(TAG, "Exit");
  return status;
}

/**
 * \brief Function to write value to specific key & slotId
 * \param[in]    slotId -       input slotId where value to be write
 * \param[in]    key -          input key where value to be write
 * \param[in]   value -        input value which will be written
 *
 * \retval This function return Weaver_STATUS_OK (0) in case of success
 *         In case of failure returns other Status_Weaver.
 */
Status_Weaver WeaverImpl::Write(uint32_t slotId,
                                const std::vector<uint8_t> &key,
                                const std::vector<uint8_t> &value) {
  LOG_D(TAG, "Entry");
  RETURN_IF_NULL(mTransport, WEAVER_STATUS_FAILED, "Transport is NULL");
  RETURN_IF_NULL(mParser, WEAVER_STATUS_FAILED, "Parser is NULL");
  Status_Weaver status = WEAVER_STATUS_FAILED;
  std::vector<uint8_t> readCmd;
  std::vector<uint8_t> resp;
  std::vector<uint8_t> aid;
  /* transport library don't require open applet
   * open will be done as part of send */
  LOG_D(TAG, "Write to Slot (%u)", slotId);
  if (mParser->FrameWriteCmd(slotId, key, value, readCmd) &&
      mTransport->Send(readCmd, resp)) {
    status = WEAVER_STATUS_OK;
  }
#ifndef INTERVAL_TIMER
  if (!close()) {
    LOG_E(TAG, "Failed to Close Channel");
    // Channel Close Failed
  }
#endif
  if (status != WEAVER_STATUS_OK || (!mParser->isSuccess(std::move(resp)))) {
    status = WEAVER_STATUS_FAILED;
  }
  LOG_D(TAG, "Exit");
  return status;
}

/**
 * \brief Function to de-initialize Weaver Interface
 *
 * \retval This function return Weaver_STATUS_OK (0) in case of success
 *         In case of failure returns other Status_Weaver.
 */
Status_Weaver WeaverImpl::DeInit() {
  LOG_D(TAG, "Entry");
  if (mTransport != NULL) {
    mTransport->DeInit();
  }
  LOG_D(TAG, "Exit");
  return WEAVER_STATUS_OK;
}
