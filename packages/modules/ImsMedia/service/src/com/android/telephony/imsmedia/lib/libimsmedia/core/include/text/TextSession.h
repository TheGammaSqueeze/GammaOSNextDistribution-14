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

#ifndef TEXT_SESSION_H
#define TEXT_SESSION_H

#include <ImsMediaDefine.h>
#include <BaseSession.h>
#include <TextStreamGraphRtpTx.h>
#include <TextStreamGraphRtpRx.h>
#include <TextStreamGraphRtcp.h>
#include <RtpConfig.h>

class TextSession : public BaseSession
{
public:
    TextSession();
    virtual ~TextSession();
    virtual SessionState getState();
    virtual ImsMediaResult startGraph(RtpConfig* config);
    // BaseSessionCallback
    virtual void onEvent(int32_t type, uint64_t param1, uint64_t param2);
    ImsMediaResult sendRtt(const android::String8* text);

    /**
     * @brief move session to inactive. i.e., Rtp Tx and Rtp Rx graphs are stopped
     *
     * @return bool false if failed to move session to inactive
     */
    bool deactivate();

private:
    TextStreamGraphRtpTx* mGraphRtpTx;
    TextStreamGraphRtpRx* mGraphRtpRx;
    TextStreamGraphRtcp* mGraphRtcp;
};

#endif