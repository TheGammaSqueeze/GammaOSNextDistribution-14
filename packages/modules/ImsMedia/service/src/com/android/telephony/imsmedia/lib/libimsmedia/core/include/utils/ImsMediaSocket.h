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

#ifndef IMS_MEDIA_SOCKET_H
#define IMS_MEDIA_SOCKET_H

#include <ImsMediaDefine.h>
#include <ImsMediaCondition.h>
#include <ISocket.h>
#include <stdint.h>
#include <list>
#include <mutex>

class ImsMediaSocket : public ISocket
{
public:
    static ImsMediaSocket* GetInstance(
            uint32_t localPort, const char* peerIpAddress, uint32_t peerPort);
    static void ReleaseInstance(ImsMediaSocket* node);

private:
    ImsMediaSocket();
    virtual ~ImsMediaSocket();
    static void StartSocketMonitor();
    static void StopSocketMonitor();
    static void SocketMonitorThread();
    static uint32_t SetSocketFD(void* pReadFds, void* pWriteFds, void* pExceptFds);
    static void ReadDataFromSocket(void* pReadfds);
    bool isValidDscp(int32_t dscp);
    int32_t convertDscpToTos(int32_t dscp);

public:
    /**
     * @brief Set the local ip address and port number
     */
    virtual void SetLocalEndpoint(const char* ipAddress, const uint32_t port);

    /**
     * @brief Set the peer ip address and port number
     */
    virtual void SetPeerEndpoint(const char* ipAddress, const uint32_t port);
    virtual int GetLocalPort();
    virtual int GetPeerPort();
    virtual char* GetLocalIPAddress();
    virtual char* GetPeerIPAddress();

    /**
     * @brief Add socket file descriptor to the list, stack into list is done only once when the
     * socket reference counter is zero
     *
     * @param socketFd The unique socket file descriptor
     * @return true Returns when the give argument is valid
     * @return false Returns when the give argument is invalid
     */
    virtual bool Open(int socketFd = -1);

    /**
     * @brief Add socket listener to the rx socket list for callback when the socket listener is not
     * null, if the listener is null, remove the socket instance from the rx socket list
     *
     * @param listener The listener to decide add or remove from the rx socket list.
     */
    virtual void Listen(ISocketListener* listener);

    /**
     * @brief Send data to registered socket
     *
     * @param pData The data array
     * @param nDataSize The length of data
     * @return int32_t The length of data which is sent successfully, return -1 when it is failed
     * to send
     */
    virtual int32_t SendTo(uint8_t* pData, uint32_t nDataSize);

    /**
     * @brief Receive data to the give buffer
     *
     * @param pData The data buffer to copy
     * @param nBufferSize The size of buffer
     * @return int32_t The length of data which is received successfully, return -1 when it is
     * failed to received or has invalid arguments
     */
    virtual int32_t ReceiveFrom(uint8_t* pData, uint32_t nBufferSize);

    /**
     * @brief Retrieve optional data from the socket
     *
     * @param type The type of the socket option to receive
     * @param value The value to received, if method returns true, the value is valid
     */
    virtual bool RetrieveOptionMsg(uint32_t type, int32_t& value);

    /**
     * @brief Remove the socket from the socket list
     */
    virtual void Close();

    /**
     * @brief Set the socket option, calls setsockopt
     *
     * @param nOption The option type defined as kSocketOption
     * @param nOptionValue The value to set
     * @return true Returns when the setsockopt returns valid status
     * @return false Returns when the setsockopt returns -1
     */
    virtual bool SetSocketOpt(kSocketOption nOption, int32_t nOptionValue);
    int32_t GetSocketFd();
    ISocketListener* GetListener();

private:
    static std::list<ImsMediaSocket*> slistSocket;
    static std::list<ImsMediaSocket*> slistRxSocket;
    static int32_t sRxSocketCount;
    static bool mSocketListUpdated;
    static bool mTerminateMonitor;
    static std::mutex sMutexRxSocket;
    static std::mutex sMutexSocketList;
    static ImsMediaCondition mConditionExit;
    int32_t mSocketFd;
    int32_t mRefCount;
    ISocketListener* mListener;
    kIpVersion mLocalIPVersion;
    kIpVersion mPeerIPVersion;
    char mLocalIP[MAX_IP_LEN]{};
    char mPeerIP[MAX_IP_LEN]{};
    uint32_t mLocalPort;
    uint32_t mPeerPort;
    bool mRemoteIpFiltering;
};

#endif