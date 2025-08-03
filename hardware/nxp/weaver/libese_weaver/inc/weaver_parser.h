/******************************************************************************
 *
 *  Copyright 2020, 2023 NXP
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

#ifndef _WEAVER_PARSER_H_
#define _WEAVER_PARSER_H_

#include <weaver_common.h>

class WeaverParser {
public:
  /**
   * \brief virtual Function to Frame weaver applet request command for getSlots
   *
   * \param[out]    request - framed getslots command as vector
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool FrameGetSlotCmd(std::vector<uint8_t> &request) = 0;

  /**
   * \brief virtual Function to Frame weaver applet request command for open
   *
   * \param[out]    request - framed open command as vector
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool FrameOpenCmd(std::vector<uint8_t> &request) = 0;

  /**
   * \brief virtual Function to Frame weaver applet request command for read
   *
   * \param[in]     slotId  - input slotId to be used in read request.
   * \param[in]     key     - input key to be used in read request.
   * \param[out]    request - framed read command as vector
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool FrameReadCmd(uint32_t slotId, const std::vector<uint8_t> &key,
                            std::vector<uint8_t> &request) = 0;

  /**
   * \brief virtual Function to Frame weaver applet request command for write
   *
   * \param[in]     slotId  - input slotId to be used in write request.
   * \param[in]     key     - input key to be used in write request.
   * \param[in]     value   - input value to be used in write request.
   * \param[out]    request - framed write command as vector
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool FrameWriteCmd(uint32_t slotId, const std::vector<uint8_t> &key,
                             const std::vector<uint8_t> &value,
                             std::vector<uint8_t> &request) = 0;

  /**
   * \brief virtual Function to Frame weaver applet request command for get data
   *
   * \param[in]     p1      - p1 value for get Data command.
   * \param[in]     p2      - p2 value for get Data command.
   * \param[out]    request - framed get data command as vector
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool FrameGetDataCmd(uint8_t p1, uint8_t p2,
                            std::vector<uint8_t> &request) = 0;

  /**
   * \brief virtual Function to Parse getSlots response
   *
   * \param[in]     response  - response from applet.
   * \param[out]    slotInfo  - parsed slots Information read out from applet
   * response.
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual Status_Weaver ParseSlotInfo(std::vector<uint8_t> response,
                                      SlotInfo &slotInfo) = 0;

  /**
   * \brief virtual Function to Parse read response
   *
   * \param[in]     response  - response from applet.
   * \param[out]    readInfo  - parsed read Information read out from applet
   * response.
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual Status_Weaver ParseReadInfo(std::vector<uint8_t> response,
                                      ReadRespInfo &readInfo) = 0;

  /**
   * \brief virtual Function to Parse get data response
   *
   * \param[in]     response  - response from applet.
   * \param[out]    readInfo  - parsed Get data Information read out from applet
   * response.
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual Status_Weaver ParseGetDataInfo(std::vector<uint8_t> response,
                                      GetDataRespInfo &getDataInfo) = 0;

  /**
   * \brief virtual Function to check if response from applet is Success or not
   *
   * \param[in]     response  - response from applet.
   *
   * \retval This function return true if response code from applet is success
   *         and false in other cases.
   */
  virtual bool isSuccess(std::vector<uint8_t> response) = 0;

  /**
   * \brief virtual Function to get Weaver Applet ID
   *
   * \param[out]    aid  - applet ids of the weaver applet.
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool getAppletId(std::vector<std::vector<uint8_t>> &aid) = 0;

  /**
   * \brief virtual destructor for weaver parser
   */
  virtual ~WeaverParser(){};
};
#endif /* _WEAVER_PARSER_H_ */
