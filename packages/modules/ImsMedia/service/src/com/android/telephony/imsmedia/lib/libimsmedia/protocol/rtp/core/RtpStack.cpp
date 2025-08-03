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

#include <RtpStack.h>
#include <RtpStackUtil.h>
#include <RtpTrace.h>

RtpStack::RtpStack() :
        m_objRtpSessionList(std::list<RtpSession*>()),
        m_pobjStackProfile(nullptr)
{
}

RtpStack::~RtpStack()
{
    // clear stack profile
    if (m_pobjStackProfile != nullptr)
    {
        delete m_pobjStackProfile;
    }

    // delete all RTP session objects.
    for (auto& pobjRtpSession : m_objRtpSessionList)
    {
        pobjRtpSession->deleteRtpSession();
    }
    m_objRtpSessionList.clear();
}

RtpStack::RtpStack(IN RtpStackProfile* pobjStackProfile)
{
    m_pobjStackProfile = pobjStackProfile;
}

RtpSession* RtpStack::createRtpSession()
{
    RtpDt_UInt32 uiTermNum = m_pobjStackProfile->getTermNumber();

    RtpSession* pobjRtpSession = new RtpSession(this);
    if (pobjRtpSession == nullptr)
    {
        RTP_TRACE_WARNING("Memory allocation error.", RTP_ZERO, RTP_ZERO);
        return nullptr;
    }

    // add session into m_objRtpSessionList
    m_objRtpSessionList.push_back(pobjRtpSession);

    // generate SSRC
    RtpDt_UInt32 uiSsrc = RtpStackUtil::generateNewSsrc(uiTermNum);
    pobjRtpSession->setSsrc(uiSsrc);

    return pobjRtpSession;
}

eRtp_Bool RtpStack::isValidRtpSession(IN RtpSession* pobjSession)
{
    for (auto& pobjRtpSesItem : m_objRtpSessionList)
    {
        // get Rtp Session from list
        if (pobjRtpSesItem->compareRtpSessions(pobjSession) == eRTP_SUCCESS)
        {
            return eRTP_SUCCESS;
        }
    }

    return eRTP_FAILURE;
}

eRTP_STATUS_CODE RtpStack::deleteRtpSession(IN RtpSession* pobjRtpSession)
{
    if (pobjRtpSession == nullptr)
    {
        RTP_TRACE_WARNING("deleteRtpSession, pobjRtpSession is NULL.", RTP_ZERO, RTP_ZERO);
        return RTP_INVALID_PARAMS;
    }

    eRtp_Bool bisRtpSes = eRTP_SUCCESS;
    bisRtpSes = isValidRtpSession(pobjRtpSession);

    if (bisRtpSes == eRTP_SUCCESS)
    {
        pobjRtpSession->deleteRtpSession();
        m_objRtpSessionList.remove(pobjRtpSession);

        return RTP_SUCCESS;
    }

    return RTP_FAILURE;
}

RtpStackProfile* RtpStack::getStackProfile()
{
    return m_pobjStackProfile;
}

RtpDt_Void RtpStack::setStackProfile(IN RtpStackProfile* pobjStackProfile)
{
    m_pobjStackProfile = pobjStackProfile;
}
