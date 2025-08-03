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

#include <ISocket.h>
#include <ImsMediaSocket.h>

ISocket* ISocket::GetInstance(
        uint32_t localPort, const char* peerIpAddress, uint32_t peerPort, eSocketClass eSocket)
{
    ISocket* pSocket = nullptr;

    if (eSocket == SOCKET_CLASS_DEFAULT)
    {
        pSocket = static_cast<ISocket*>(
                ImsMediaSocket::GetInstance(localPort, peerIpAddress, peerPort));

        if (pSocket != nullptr)
        {
            pSocket->mSocketClass = SOCKET_CLASS_DEFAULT;
        }
    }

    return pSocket;
}

void ISocket::ReleaseInstance(ISocket* pSocket)
{
    if (pSocket->mSocketClass == SOCKET_CLASS_DEFAULT)
    {
        ImsMediaSocket::ReleaseInstance(static_cast<ImsMediaSocket*>(pSocket));
    }
}