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

#include <sys/time.h>
#include <stdlib.h>
#include <netinet/in.h>
#include <RtpOsUtil.h>

RtpOsUtil::RtpOsUtil() {}

RtpOsUtil::~RtpOsUtil() {}

RtpDt_Void RtpOsUtil::GetNtpTime(tRTP_NTP_TIME& pstNtpTime)
{
    struct timeval stAndrodTp;

    if (gettimeofday(&stAndrodTp, nullptr) != -1)
    {
        // To convert a UNIX timestamp (seconds since 1970) to NTP time, add 2,208,988,800 seconds
        uint64_t ntpts = ((unsigned long long)(stAndrodTp.tv_sec + 2208988800UL) << 32) +
                (unsigned long long)(stAndrodTp.tv_usec / 1e6 * 4294967296.);

        pstNtpTime.m_uiNtpHigh32Bits = (ntpts >> 32);
        pstNtpTime.m_uiNtpLow32Bits = ntpts;
    }
}

RtpDt_Void RtpOsUtil::Srand()
{
    struct timeval stSysTime;
    gettimeofday(&stSysTime, nullptr);
    RtpDt_UInt32 uiSeed = stSysTime.tv_usec * 1000;
    srand(uiSeed);
}

RtpDt_UInt32 RtpOsUtil::Rand()
{
    RtpOsUtil::Srand();
    return rand();
}

RtpDt_UInt32 RtpOsUtil::Ntohl(RtpDt_UInt32 uiNetlong)
{
    return ntohl(uiNetlong);
}

RtpDt_Double RtpOsUtil::RRand()
{
    tRTP_NTP_TIME stNtpTs = {0, 0};
    RtpOsUtil::Srand();
    RtpDt_Double dRandNum = static_cast<RtpDt_Double>(rand()) / static_cast<RtpDt_Double>(RAND_MAX);
    RtpOsUtil::GetNtpTime(stNtpTs);
    RtpDt_Double dTemp = ((dRandNum * stNtpTs.m_uiNtpHigh32Bits) +
            (stNtpTs.m_uiNtpLow32Bits / static_cast<RtpDt_Double>(RTP_MILLISEC_MICRO)));

    if (dTemp > RTP_ZERO)
    {
        return 1.0 / dTemp;
    }
    else
    {
        return 1.0;
    }
}
