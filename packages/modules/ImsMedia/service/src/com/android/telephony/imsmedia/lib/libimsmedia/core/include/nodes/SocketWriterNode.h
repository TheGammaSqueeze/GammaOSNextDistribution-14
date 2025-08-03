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

#ifndef SOCKET_WRITER_NODE_H
#define SOCKET_WRITER_NODE_H

#include <BaseNode.h>
#include <ISocket.h>

class SocketWriterNode : public BaseNode
{
public:
    SocketWriterNode(BaseSessionCallback* callback = nullptr);
    virtual ~SocketWriterNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
            uint32_t timestamp, bool mark, uint32_t nSeqNum,
            ImsMediaSubType nDataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0);

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

private:
    int mLocalFd;
    ISocket* mSocket;
    kProtocolType mProtocolType;
    RtpAddress mLocalAddress;
    RtpAddress mPeerAddress;
    int8_t mDscp;
    bool mSocketOpened;
    bool mDisableSocket;
};

#endif