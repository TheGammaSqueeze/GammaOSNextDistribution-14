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

#ifndef IMS_SOCKET_H
#define IMS_SOCKET_H

#include <ImsMediaDefine.h>
#include <stdint.h>

enum eSocketMode
{
    SOCKET_MODE_TX,
    SOCKET_MODE_RX,
};

class ISocketListener
{
public:
    ISocketListener() {}
    virtual ~ISocketListener() {}
    /**
     * @brief Read data from the socket
     */
    virtual void OnReadDataFromSocket() = 0;
};

class ISocketBridgeDataListener
{
public:
    ISocketBridgeDataListener() {}
    virtual ~ISocketBridgeDataListener() {}
    virtual void OnSocketDataFromBridge(uint8_t* pData, uint32_t nDataSize) = 0;
};

enum eSocketClass
{
    SOCKET_CLASS_DEFAULT = 0,
    SOCKET_CLASS_PROXY = 1,
};

class ISocket
{
public:
    static ISocket* GetInstance(uint32_t localPort, const char* peerIpAddress, uint32_t peerPort,
            eSocketClass eSocket = SOCKET_CLASS_DEFAULT);
    static void ReleaseInstance(ISocket* pSocket);

protected:
    virtual ~ISocket() {}

public:
    virtual void SetLocalEndpoint(const char* ipAddress, const uint32_t port) = 0;
    virtual void SetPeerEndpoint(const char* ipAddress, const uint32_t port) = 0;
    virtual int GetLocalPort() = 0;
    virtual int GetPeerPort() = 0;
    virtual char* GetLocalIPAddress() = 0;
    virtual char* GetPeerIPAddress() = 0;
    virtual bool Open(int localFd = 0) = 0;
    virtual void Listen(ISocketListener* listener) = 0;
    virtual int32_t SendTo(uint8_t* pData, uint32_t nDataSize) = 0;
    virtual int32_t ReceiveFrom(uint8_t* pData, uint32_t nBufferSize) = 0;
    virtual bool RetrieveOptionMsg(uint32_t type, int32_t& value) = 0;
    virtual void Close() = 0;
    virtual bool SetSocketOpt(kSocketOption nOption, int32_t nOptionValue) = 0;

protected:
    eSocketClass mSocketClass;
};

#endif