/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef IMS_MEDIA_NW_UTIL_H
#define IMS_MEDIA_NW_UTIL_H

#include <ImsMediaDefine.h>

class ImsMediaNetworkUtil
{
public:
    /**
     * @brief Get the local Ip address and port from the socket
     *
     * @param nSocketFD The socket file descriptor to extract the ip and port
     * @param pIPAddress The ip address formed in text
     * @param len The length of the ip address array
     * @param port The port to extract from the socket
     * @return true Returns when there is no error to extract the data
     * @return false  Returns when there is error returns with the arguments
     */
    static bool getLocalIpPortFromSocket(
            const int nSocketFD, char* pIPAddress, int len, unsigned int& port);

    /**
     * @brief Get the remote Ip address and port from the socket connected to
     *
     * @param nSocketFD The socket file descriptor to extract the ip and port
     * @param pIPAddress The ip address formed in text
     * @param len The length of the ip address array
     * @param port The port to extract from the socket
     * @return true Returns when there is no error to extract the data
     * @return false  Returns when there is error returns with the arguments
     */
    static bool getRemoteIpPortFromSocket(
            const int nSocketFD, char* pIPAddress, int len, unsigned int& port);

    /**
     * @brief create socket with the given local ip address and the port information
     *
     * @param pIPAddr The local Ip address formed in text
     * @param port The local port number
     * @param af The ip version
     * @return int Returns socket file descriptor, it is -1 when it is invalid to create socket with
     * the given function arguments
     */
    static int openSocket(const char* pIPAddr, unsigned int port, int af);

    /**
     * @brief connect extisting socket to certain remote address with the given arguments
     *
     * @param socketFd The target socket fd to connect the remote ip address and port number
     * @param pIPAddr The remote Ip address form in text
     * @param port The remote port number
     * @param af The ip version
     * @return true Returns when the socket connects succefully
     * @return false Returns when the socket connects fail
     */
    static bool connectSocket(const int socketFd, const char* pIPAddr, unsigned int port, int af);

    /**
     * @brief close the socket
     *
     * @param socketFd The socket file descriptor
     */
    static void closeSocket(int& socketFd);
};

#endif  // IMS_MEDIA_NW_UTIL_H