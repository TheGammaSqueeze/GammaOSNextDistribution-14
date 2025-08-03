/******************************************************************************
 *
 *  Copyright 2020 NXP
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

#ifndef _WEAVER_IMPL_H_
#define _WEAVER_IMPL_H_

#include <mutex>
#include <weaver_interface.h>
#include <weaver_parser.h>
#include <weaver_transport.h>

class WeaverImpl : public WeaverInterface {
public:
  /**
   * \brief Function to initialize Weaver Interface
   *
   * \retval This function return Weaver_STATUS_OK (0) in case of success
   *         In case of failure returns other Status_Weaver.
   */
  Status_Weaver Init() override;

  /**
   * \brief Function to read slot information
   * \param[out]   slotInfo - slot information values read out
   *
   * \retval This function return Weaver_STATUS_OK (0) in case of success
   *         In case of failure returns other Status_Weaver errorcodes.
   */
  Status_Weaver GetSlots(SlotInfo &slotInfo) override;

  /**
   * \brief Function to read value of specific key & slotId
   * \param[in]    slotId -       input slotId which's information to be read
   * \param[in]    key -          input key which's information to be read
   * \param[out]   readRespInfo - read information values to be read out
   *
   * \retval This function return Weaver_STATUS_OK (0) in case of success
   *         In case of failure returns other Status_Weaver errorcodes.
   */
  Status_Weaver Read(uint32_t slotId, const std::vector<uint8_t> &key,
                     ReadRespInfo &readRespInfo) override;

  /**
   * \brief Function to write value to specific key & slotId
   * \param[in]    slotId -       input slotId where value to be write
   * \param[in]    key -          input key where value to be write
   * \param[in]   value -        input value which will be written
   *
   * \retval This function return Weaver_STATUS_OK (0) in case of success
   *         In case of failure returns other Status_Weaver.
   */
  Status_Weaver Write(uint32_t slotId, const std::vector<uint8_t> &key,
                      const std::vector<uint8_t> &value) override;

  /**
   * \brief Function to de-initialize Weaver Interface
   *
   * \retval This function return Weaver_STATUS_OK (0) in case of success
   *         In case of failure returns other Status_Weaver.
   */
  Status_Weaver DeInit() override;

  /**
   * \brief static function to get the singleton instance of WeaverImpl class
   *
   * \retval instance of WeaverImpl.
   */
  static WeaverImpl *getInstance();

private:
  /* Transport interface to be use for communication */
  WeaverTransport *mTransport;
  /* Parser interface to frame weaver commands and parse response*/
  WeaverParser *mParser;
  /* Internal close api for transport close */
  bool close();
  /* Private constructor to make class singleton*/
  WeaverImpl() = default;
  /* Private destructor to make class singleton*/
  ~WeaverImpl() = default;
  /* Private copy constructor to make class singleton*/
  WeaverImpl(const WeaverImpl &) = delete;
  /* Private operator overload to make class singleton*/
  WeaverImpl &operator=(const WeaverImpl &) = delete;

  /* Private self instance for singleton purpose*/
  static WeaverImpl *s_instance;

  /* Private once flag (c++11) for singleton purpose.
   * once_flag should pass to multiple calls of
   * std::call_once allows those calls to coordinate with each other
   * such a way only one will actually run to completion.
   */
  static std::once_flag s_instanceFlag;

  /* Private function to create the instance of self class
   * Same will be used for std::call_once
   */
  static void createInstance();
};

#endif /* _WEAVER_IMPL_H_ */
