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

#ifndef _WEAVER_TRANSPORT_H_
#define _WEAVER_TRANSPORT_H_

#include <vector>

class WeaverTransport {
public:
  /**
   * \brief virtual Function to initialize Weaver Transport Interface
   *
   * \param[in]    aid -  applet ids to be set to transport interface
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool Init(std::vector<std::vector<uint8_t>> aid) = 0;

  /**
   * \brief virtual Function to open applet connection
   *
   * \param[in]    data -         command for open applet
   * \param[out]   resp -         response from applet
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool OpenApplet(std::vector<uint8_t> data,
                          std::vector<uint8_t> &resp) = 0;

  /**
   * \brief virtual Function to close applet connection
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool CloseApplet() = 0;

  /**
   * \brief virtual Function to send commands to applet
   *
   * \param[in]    data -         command to be send to applet
   * \param[out]   resp -         response from applet
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool Send(std::vector<uint8_t> data, std::vector<uint8_t> &resp) = 0;

  /**
   * \brief virtual Function to de-initialize Weaver Transport Interface
   *
   * \retval This function return true in case of success
   *         In case of failure returns false.
   */
  virtual bool DeInit() = 0;

  /**
   * \brief virtual destructor for Weaver Transport Interface
   */
  virtual ~WeaverTransport(){};
};

#endif /* _WEAVER_TRANSPORT_H_ */
