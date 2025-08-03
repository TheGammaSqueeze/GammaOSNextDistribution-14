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

#ifndef _WEAVER_INTERFACE_H_
#define _WEAVER_INTERFACE_H_

#include <weaver_common.h>

class WeaverInterface {
public:
  /**
   * \brief virtual Function to initialize Weaver Interface
   *
   * \retval This function return Weaver_STATUS_OK (0) in case of success
   *         In case of failure returns other Status_Weaver.
   */
  virtual Status_Weaver Init() = 0;

  /**
   * \brief virtual Function to read slot information
   * \param[out]   slotInfo - slot information values read out
   *
   * \retval This function return Weaver_STATUS_OK (0) in case of success
   *         In case of failure returns other Status_Weaver errorcodes.
   */
  virtual Status_Weaver GetSlots(SlotInfo &slotInfo) = 0;

  /**
   * \brief virtual Function to read value of specific key & slotId
   * \param[in]    slotId -       input slotId which's information to be read
   * \param[in]    key -          input key which's information to be read
   * \param[out]   readRespInfo - read information values to be read out
   *
   * \retval This function return Weaver_STATUS_OK (0) in case of success
   *         In case of failure returns other Status_Weaver errorcodes.
   */
  virtual Status_Weaver Read(uint32_t slotId, const std::vector<uint8_t> &key,
                             ReadRespInfo &readRespInfo) = 0;

  /**
   * \brief virtual Function to write value to specific key & slotId
   * \param[in]    slotId -       input slotId where value to be write
   * \param[in]    key -          input key where value to be write
   * \param[in]   value -        input value which will be written
   *
   * \retval This function return Weaver_STATUS_OK (0) in case of success
   *         In case of failure returns other Status_Weaver.
   */
  virtual Status_Weaver Write(uint32_t slotId, const std::vector<uint8_t> &key,
                              const std::vector<uint8_t> &value) = 0;

  /**
   * \brief virtual Function to de-initialize Weaver Interface
   *
   * \retval This function return Weaver_STATUS_OK (0) in case of success
   *         In case of failure returns other Status_Weaver.
   */
  virtual Status_Weaver DeInit() = 0;

  /**
   * \brief virtual destructor for Weaver Interface
   */
  virtual ~WeaverInterface(){};
};

#endif /* _WEAVER_INTERFACE_H_ */
