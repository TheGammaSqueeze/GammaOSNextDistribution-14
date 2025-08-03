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

/** \addtogroup  RTP_Stack
 *  @{
 */

#ifndef __RTP_ACTIVE_SESSIONDB_H__
#define __RTP_ACTIVE_SESSIONDB_H__

#include <RtpGlobal.h>
#include <list>

/**
 * @class    RtpSessionManager
 * @brief    Maintains the active rtp sessions in list
 */
class RtpSessionManager
{
private:
    // RtpSessionManager pointer.
    static RtpSessionManager* m_pInstance;

    // maintains the list of active rtp sessions
    std::list<RtpDt_Void*> m_objActiveSessionList;

    // constructor
    RtpSessionManager();

    // destructor
    ~RtpSessionManager();

public:
    // creates RtpSessionManager instance.
    static RtpSessionManager* getInstance();

    // adds rtp session to the list
    RtpDt_Void addRtpSession(IN RtpDt_Void* pvData);

    // removes rtp session from the list
    RtpDt_Void removeRtpSession(IN RtpDt_Void* pvData);

    // returns true if rtp session exists in list
    eRtp_Bool isValidRtpSession(IN RtpDt_Void* pvData);
};

#endif /* __RTP_ACTIVE_SESSIONDB_H__*/

/** @}*/
