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

#include <ImsMediaTimer.h>
#include <ImsMediaTrace.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <chrono>
#include <thread>
#include <utils/Atomic.h>
#include <limits.h>
#include <mutex>
#include <list>
#include <algorithm>

struct TimerInstance
{
    fn_TimerCb mTimerCb;
    uint32_t mDuration;
    bool mRepeat;
    void* mUserData;
    bool mTerminateThread;
    uint32_t mStartTimeMillisecond;
    std::mutex mMutex;
};

static std::mutex gMutexList;
static std::list<TimerInstance*> gTimerList;
static std::uint64_t gStartTime = 0;

static void AddTimerToList(TimerInstance* timer)
{
    std::lock_guard<std::mutex> guard(gMutexList);
    gTimerList.push_back(timer);
}

static void DeleteTimerFromList(TimerInstance* timer)
{
    std::lock_guard<std::mutex> guard(gMutexList);
    gTimerList.remove(timer);
}

static bool IsValidTimer(const TimerInstance* timer)
{
    std::lock_guard<std::mutex> guard(gMutexList);

    if (gTimerList.empty())
    {
        return false;
    }

    auto result = std::find(gTimerList.begin(), gTimerList.end(), timer);
    return (result != gTimerList.end());
}

static void* ImsMediaTimer_run(void* arg)
{
    TimerInstance* timer = reinterpret_cast<TimerInstance*>(arg);

    if (timer == nullptr)
    {
        return nullptr;
    }

    uint32_t sleepTime = timer->mDuration;

    if (timer->mDuration > 10 && timer->mDuration < 100)
    {
        sleepTime = 10;
    }
    else if (timer->mDuration >= 100 && timer->mDuration < 1000)
    {
        sleepTime = timer->mDuration / 10;
    }
    else if (timer->mDuration >= 1000)
    {
        sleepTime = 100;
    }

    for (;;)
    {
        if (timer->mTerminateThread)
        {
            break;
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(sleepTime));

        if (timer->mTerminateThread)
        {
            break;
        }

        uint32_t currTimeMillisecond = ImsMediaTimer::GetTimeInMilliSeconds();
        uint32_t nTimeDiff = currTimeMillisecond - timer->mStartTimeMillisecond;

        if (nTimeDiff >= timer->mDuration)
        {
            if (timer->mRepeat)
            {
                timer->mStartTimeMillisecond = currTimeMillisecond;
            }

            {  // Critical section
                std::lock_guard<std::mutex> guard(timer->mMutex);
                if (timer->mTerminateThread)
                {
                    break;
                }

                if (timer->mTimerCb)
                {
                    timer->mTimerCb(timer, timer->mUserData);
                }
            }

            if (!timer->mRepeat)
            {
                break;
            }
        }
    }

    DeleteTimerFromList(timer);

    if (timer != nullptr)
    {
        delete timer;
        timer = nullptr;
    }

    return nullptr;
}

hTimerHandler ImsMediaTimer::TimerStart(
        uint32_t duration, bool repeat, fn_TimerCb timerCallback, void* userData)
{
    TimerInstance* timer = new TimerInstance;

    if (timer == nullptr)
    {
        return nullptr;
    }

    timer->mTimerCb = timerCallback;
    timer->mDuration = duration;
    timer->mRepeat = repeat;
    timer->mUserData = userData;
    timer->mTerminateThread = false;

    IMLOGD3("[TimerStart] duration[%u], repeat[%d], userData[%x]", timer->mDuration, repeat,
            timer->mUserData);

    timer->mStartTimeMillisecond = ImsMediaTimer::GetTimeInMilliSeconds();
    AddTimerToList(timer);

    std::thread t1(&ImsMediaTimer_run, timer);
    t1.detach();
    return (hTimerHandler)timer;
}

bool ImsMediaTimer::TimerStop(hTimerHandler hTimer, void** puserData)
{
    TimerInstance* timer = reinterpret_cast<TimerInstance*>(hTimer);
    IMLOGD1("[TimerStop] timer[%x]", timer);

    if (timer == nullptr)
    {
        return false;
    }

    if (IsValidTimer(timer) == false)
    {
        return false;
    }

    {
        std::lock_guard<std::mutex> guard(timer->mMutex);
        IMLOGD1("[TimerStop] mutex taken timer[%x]", timer);

        timer->mTerminateThread = true;

        if (puserData)
        {
            *puserData = timer->mUserData;
        }
    }

    return true;
}

void ImsMediaTimer::SetStartTimeInMicroSeconds(uint64_t time)
{
    gStartTime = time;
}

uint64_t ImsMediaTimer::GetTimeInMicroSeconds(void)
{
    struct timespec time;
    clock_gettime(CLOCK_MONOTONIC, &time);
    return gStartTime + (time.tv_sec * 1000000) + (time.tv_nsec / 1000);
}

uint32_t ImsMediaTimer::GetTimeInMilliSeconds(void)
{
    return (ImsMediaTimer::GetTimeInMicroSeconds() / 1000 % UINT_MAX);
}

uint32_t ImsMediaTimer::GenerateRandom(uint32_t nRange)
{
    uint32_t rand;
    struct timespec time;
    clock_gettime(CLOCK_MONOTONIC, &time);
    rand = (time.tv_sec * 13) + (time.tv_nsec / 1000000);

    if (0 == nRange)
    {
        return rand * 7;
    }

    return (rand * 7) % nRange;
}

int32_t ImsMediaTimer::Atomic_Inc(int32_t* v)
{
    return android_atomic_inc(v);
}
int32_t ImsMediaTimer::Atomic_Dec(int32_t* v)
{
    return android_atomic_dec(v);
}

void ImsMediaTimer::Sleep(unsigned int t)
{
    std::this_thread::sleep_for(std::chrono::milliseconds(t));
}

void ImsMediaTimer::USleep(unsigned int t)
{
    std::this_thread::sleep_for(std::chrono::microseconds(t));
}