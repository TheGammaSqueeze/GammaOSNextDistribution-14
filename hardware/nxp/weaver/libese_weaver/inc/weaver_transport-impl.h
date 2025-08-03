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

#ifndef _WEAVER_TRANSPORT_IMPL_H_
#define _WEAVER_TRANSPORT_IMPL_H_

#include <mutex>
#include <weaver_transport.h>

class WeaverTransportImpl : public WeaverTransport {
public:
  /**
   * \brief Function to initialize Weaver Transport Interface
   *
   * \param[in]    aid -  applet id to be set to transport interface
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool Init(std::vector<std::vector<uint8_t>> aid) override;

  /**
   * \brief Function to open applet connection
   *
   * \param[in]    data -         command for open applet
   * \param[out]   resp -         response from applet
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool OpenApplet(std::vector<uint8_t> data,
                  std::vector<uint8_t> &resp) override;

  /**
   * \brief Function to close applet connection
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool CloseApplet() override;

  /**
   * \brief Function to send commands to applet
   *
   * \param[in]    data -         command to be send to applet
   * \param[out]   resp -         response from applet
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool Send(std::vector<uint8_t> data, std::vector<uint8_t> &resp) override;

  /**
   * \brief Function to de-initialize Weaver Transport Interface
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  bool DeInit() override;

  /**
   * \brief static function to get the singleton instance of WeaverTransportImpl
   * class
   *
   * \retval instance of WeaverTransportImpl.
   */
  static WeaverTransportImpl *getInstance();

private:
  /* Private constructor to make class singleton*/
  WeaverTransportImpl() = default;
  /* Private destructor to make class singleton*/
  ~WeaverTransportImpl() = default;
  /* Private copy constructor to make class singleton*/
  WeaverTransportImpl(const WeaverTransportImpl &) = delete;
  /* Private operator overload to make class singleton*/
  WeaverTransportImpl &operator=(const WeaverTransportImpl &) = delete;
  /* Private api to detect if device boot completed or not*/
  bool isDeviceBootCompleted();

  /* Private self instance for singleton purpose*/
  static WeaverTransportImpl *s_instance;
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
  /* Private wrapper function to send apdu.
   * It will try with alternate aids if sending is failed.
   */
  bool sendInternal(std::vector<uint8_t> data, std::vector<uint8_t> &resp);
};

#endif /* _WEAVER_TRANSPORT_IMPL_H_ */
