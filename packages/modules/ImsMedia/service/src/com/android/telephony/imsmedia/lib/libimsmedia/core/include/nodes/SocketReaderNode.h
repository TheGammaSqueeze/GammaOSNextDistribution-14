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

#ifndef SOCKET_READER_NODE_H
#define SOCKET_READER_NODE_H

#include <BaseNode.h>
#include <ISocket.h>
#include <mutex>

class SocketReaderNode : public BaseNode, public ISocketListener
{
public:
    SocketReaderNode(BaseSessionCallback* callback = nullptr);
    virtual ~SocketReaderNode();
    virtual kBaseNodeId GetNodeId();
    virtual bool Prepare();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual ImsMediaResult UpdateConfig(void* config);
    virtual void OnReadDataFromSocket();

    /**
     * @brief Set the local socket file descriptor
     */
    void SetLocalFd(int fd);

    /**
     * @brief Set the local ip address and port number
     */
    void SetLocalAddress(const RtpAddress& address);

    /**
     * @brief Set the peer ip address and port number
     */
    void SetPeerAddress(const RtpAddress& address);

    /**
     * @brief Set the protocol type defined as kProtocolType
     */
    void SetProtocolType(kProtocolType type) { mProtocolType = type; }

    /* Get socket is opened */
    bool IsSocketOpened() { return mSocketOpened; }

protected:
    bool OpenSocket();
    void CloseSocket();

    int mLocalFd;
    kProtocolType mProtocolType;
    ISocket* mSocket;
    RtpAddress mLocalAddress;
    RtpAddress mPeerAddress;
    bool mSocketOpened;
    std::mutex mMutex;
    uint8_t mBuffer[DEFAULT_MTU];
    bool mReceiveTtl;
};

#endif