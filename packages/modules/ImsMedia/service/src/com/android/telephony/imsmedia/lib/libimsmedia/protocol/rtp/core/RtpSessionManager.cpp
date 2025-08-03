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

#include <RtpSessionManager.h>

RtpSessionManager* RtpSessionManager::m_pInstance = nullptr;

RtpSessionManager::RtpSessionManager() :
        m_objActiveSessionList(std::list<RtpDt_Void*>())
{
}

RtpSessionManager::~RtpSessionManager() {}

RtpSessionManager* RtpSessionManager::getInstance()
{
    if (m_pInstance == nullptr)
    {
        m_pInstance = new RtpSessionManager();
    }
    return m_pInstance;
}

RtpDt_Void RtpSessionManager::addRtpSession(IN RtpDt_Void* pvData)
{
    m_objActiveSessionList.push_back(pvData);
    return;
}

RtpDt_Void RtpSessionManager::removeRtpSession(IN RtpDt_Void* pvData)
{
    m_objActiveSessionList.remove(pvData);
    return;
}

eRtp_Bool RtpSessionManager::isValidRtpSession(IN RtpDt_Void* pvData)
{
    for (const auto& pobjActiveSession : m_objActiveSessionList)
    {
        if (pobjActiveSession == nullptr)
        {
            return eRTP_FALSE;
        }

        if (pobjActiveSession == pvData)
        {
            return eRTP_TRUE;
        }
    }

    return eRTP_FALSE;
}
