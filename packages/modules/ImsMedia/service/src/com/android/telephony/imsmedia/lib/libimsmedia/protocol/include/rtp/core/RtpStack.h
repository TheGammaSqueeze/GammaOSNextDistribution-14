/** \addtogroup  RTP_Stack
 *  @{
 */

/**
 * @brief This represents the RTP stack. This class stores one instance of a the stack.
 * RTP sessions should be created as part of an RtpStack instance.
 * Each instance can have any number of unrelated RTP sessions which share only the profile as
 * defined by RtpStackProfile.
 */

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

#ifndef __RTP_STACK_H__
#define __RTP_STACK_H__

#include <RtpGlobal.h>
#include <RtpStackProfile.h>
#include <RtpSession.h>
#include <list>

class RtpSession;

class RtpStack
{
    /**
     * list of RtpSession currently active in the stack
     */
    std::list<RtpSession*> m_objRtpSessionList;

    /**
     * Profile for this stack
     */
    RtpStackProfile* m_pobjStackProfile;

public:
    /**
     * @brief Create stack with default profile
     */
    RtpStack();
    /**
     * @brief Delete stack
     */
    ~RtpStack();

    /**
     * @brief Create stack with pobjStackProfile. However application can modify
     * this profile at a later stage by using setStackProfile().
     * @param pobjStackProfile Configure the stack as per profile.
     */
    RtpStack(IN RtpStackProfile* pobjStackProfile);

    /**
     * @brief Creates a RTP session, assigns SSRC to it and adds to m_objRtpSessionList.
     * @return Created RtpSession object pointer
     */
    RtpSession* createRtpSession();

    /**
     * @brief finds whether pobjSession exists in RtpSessionList or not
     * @param pobjSession pointer to RtpSession that has to be searched
     * @return eRTP_SUCCESS if RTP session present in the m_objRtpSessionList
     */
    eRtp_Bool isValidRtpSession(IN RtpSession* pobjSession);

    /**
     * @brief Finds and deletes the RTP session from m_objRtpSessionList.
     * Memory of pobjSession will be freed
     * @param pobjSession pointer to RtpSession that has to be deleted
     * @return RTP_SUCCESS, if RTP session is deleted from m_objRtpSessionList
     */
    eRTP_STATUS_CODE deleteRtpSession(IN RtpSession* pobjSession);

    /**
     * @brief Get method for m_pobjStackProfile
     * @return current RtpStack profile
     */
    RtpStackProfile* getStackProfile();

    /**
     * @brief Set method for m_pobjStackProfile
     * @param pobjStackProfile pointer to RtpStack profile
     */
    RtpDt_Void setStackProfile(IN RtpStackProfile* pobjStackProfile);
};

#endif  //__RTP_STACK_H__

/** @}*/
