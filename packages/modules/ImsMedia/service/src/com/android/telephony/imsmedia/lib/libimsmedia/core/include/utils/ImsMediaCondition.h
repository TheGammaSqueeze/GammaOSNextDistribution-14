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

#ifndef IMS_MEDIA_CONDITION_H
#define IMS_MEDIA_CONDITION_H

#include <stdint.h>
#include <pthread.h>

class ImsMediaCondition
{
public:
    ImsMediaCondition();
    ~ImsMediaCondition();

    /**
     * @brief Wait the current thread
     *
     */
    void wait();

    /**
     * @brief Wait the current thread until the timer expired
     *
     * @param time The relative time in milliseconds unit
     * @return true Returned when the timer expires
     * @return false Returned when the thread is stopped by signal
     */
    bool wait_timeout(int64_t time);
    void signal();
    void reset();

private:
    void IncCount(uint32_t* pnCount);

    pthread_mutex_t* mMutex;
    pthread_cond_t* mCondition;
    uint32_t mWaitFlag;
    uint32_t mSignalFlag;
    uint32_t mWaitCount;
    uint32_t mSignalCount;
};

#endif  // IMS_MEDIA_CONDITION_H
