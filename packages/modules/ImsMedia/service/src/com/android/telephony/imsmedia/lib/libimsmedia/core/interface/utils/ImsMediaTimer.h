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

#ifndef IMS_MEDIA_TIMER_H
#define IMS_MEDIA_TIMER_H

#include <stdint.h>

typedef void* hTimerHandler;
typedef void (*fn_TimerCb)(hTimerHandler hTimer, void* pUserData);
struct IMNtpTime
{
    uint32_t ntpHigh32Bits;
    uint32_t ntpLow32Bits;
};

class ImsMediaTimer
{
public:
    static hTimerHandler TimerStart(
            uint32_t nDuration, bool bRepeat, fn_TimerCb pTimerCb, void* pUserData);
    static bool TimerStop(hTimerHandler hTimer, void** ppUserData);
    static void SetStartTimeInMicroSeconds(uint64_t time);
    static uint64_t GetTimeInMicroSeconds(void);
    static uint32_t GetTimeInMilliSeconds(void);
    static uint32_t GenerateRandom(uint32_t nRange);
    static int32_t Atomic_Inc(int32_t* v);
    static int32_t Atomic_Dec(int32_t* v);
    static void Sleep(unsigned int t);   // milliseconds
    static void USleep(unsigned int t);  // microseconds
};
#endif
