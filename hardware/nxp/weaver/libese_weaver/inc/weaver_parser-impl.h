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

#ifndef _WEAVER_PARSER_IMPL_H_
#define _WEAVER_PARSER_IMPL_H_

#include <mutex>
#include <weaver_parser.h>


class WeaverParserImpl : public WeaverParser {
public:
  static const uint8_t  sThrottleGetDataP1 = 0xDF;
  /**
   * \brief Function to Frame weaver applet request command for getSlots
   *
   * \param[out]    request - framed getslots command as vector
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool FrameGetSlotCmd(std::vector<uint8_t> &request);

  /**
   * \brief Function to Frame weaver applet request command for open
   *
   * \param[out]    request - framed open command as vector
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool FrameOpenCmd(std::vector<uint8_t> &request);

  /**
   * \brief Function to Frame weaver applet request command for read
   *
   * \param[in]     slotId  - input slotId to be used in read request.
   * \param[in]     key     - input key to be used in read request.
   * \param[out]    request - framed read command as vector
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool FrameReadCmd(uint32_t slotId, const std::vector<uint8_t> &key,
                    std::vector<uint8_t> &request);

  /**
   * \brief Function to Frame weaver applet request command for write
   *
   * \param[in]     slotId  - input slotId to be used in write request.
   * \param[in]     key     - input key to be used in write request.
   * \param[in]     value   - input value to be used in write request.
   * \param[out]    request - framed write command as vector
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool FrameWriteCmd(uint32_t slotId, const std::vector<uint8_t> &key,
                     const std::vector<uint8_t> &value,
                     std::vector<uint8_t> &request);

  /**
   * \brief Function to Frame weaver applet request command for get data
   *
   * \param[in]     p1      - p1 value for get Data command.
   * \param[in]     p2      - p2 value for get Data command.
   * \param[out]    request - framed get data command as vector
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool FrameGetDataCmd(uint8_t p1, uint8_t p2,
                     std::vector<uint8_t> &request);

  /**
   * \brief Function to Parse getSlots response
   *
   * \param[in]     response  - response from applet.
   * \param[out]    slotInfo  - parsed slots Information read out from applet
   * response.
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  Status_Weaver ParseSlotInfo(std::vector<uint8_t> response,
                              SlotInfo &slotInfo);

  /**
   * \brief Function to Parse read response
   *
   * \param[in]     response  - response from applet.
   * \param[out]    readInfo  - parsed read Information read out from applet
   * response.
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  Status_Weaver ParseReadInfo(std::vector<uint8_t> response,
                              ReadRespInfo &readInfo);

  /**
   * \brief Function to Parse get data response
   *
   * \param[in]     response  - response from applet.
   * \param[out]    readInfo  - parsed Get data Information read out from applet
   * response.
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  Status_Weaver ParseGetDataInfo(std::vector<uint8_t> response,
                                GetDataRespInfo &getDataInfo);

  /**
   * \brief Function to check if response from applet is Success or not
   *
   * \param[in]     response  - response from applet.
   *
   * \retval This function return true if response code from applet is success
   *         and false in other cases.
   */
  bool isSuccess(std::vector<uint8_t> response);

  /**
   * \brief Function to get Weaver Applet ID
   *
   * \param[out]    aid  - applet ids of the weaver applet.
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool getAppletId(std::vector<std::vector<uint8_t>> &aid);

  /**
   * \brief static function to get the singleton instance of WeaverParserImpl
   * class
   *
   * \retval instance of WeaverParserImpl.
   */
  static WeaverParserImpl *getInstance();

private:
  /* Internal error codes for Parser Implementation */
  enum APP_ERR_CODE {
    APP_SUCCESS,
    APP_FAILED,
    APP_INVALID_LEN,
    APP_INVALID_SLOT,
    APP_INVALID_P1_P2,
    APP_UNKNOWN_ERR,
  };

  /**
   * \brief Private internal Function to check the response status code
   *
   * \param[in]    response  - response from  weaver applet.
   *
   * \retval This function return errorcode from APP_ERR_CODE type
   */
  APP_ERR_CODE checkStatus(std::vector<uint8_t> response);
  /* Private constructor to make class singleton*/
  WeaverParserImpl() = default;
  /* Private destructor to make class singleton*/
  ~WeaverParserImpl() = default;
  /* Private copy constructor to make class singleton*/
  WeaverParserImpl(const WeaverParserImpl &) = delete;
  /* Private operator overload to make class singleton*/
  WeaverParserImpl &operator=(const WeaverParserImpl &) = delete;

  /* Private self instance for singleton purpose*/
  static WeaverParserImpl *s_instance;
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

#endif /* _WEAVER_PARSER_IMPL_H_ */
