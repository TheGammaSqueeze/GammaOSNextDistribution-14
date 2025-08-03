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

#ifndef JITTERNETWORKANALYSER_H_INCLUDED
#define JITTERNETWORKANALYSER_H_INCLUDED

#include <stdint.h>
#include <list>
#include <map>
#include <mutex>

enum NETWORK_STATUS
{
    NETWORK_STATUS_BAD,
    NETWORK_STATUS_NORMAL,
    NETWORK_STATUS_GOOD
};

class JitterNetworkAnalyser
{
public:
    JitterNetworkAnalyser();
    virtual ~JitterNetworkAnalyser();
    void Reset();
    // initialze network analyser
    void SetMinMaxJitterBufferSize(uint32_t nMinBufferSize, uint32_t nMaxBufferSize);
    void SetJitterOptions(
            uint32_t incThreshold, uint32_t decThreshold, uint32_t stepSize, double zValue);

    /**
     * @brief Get the next jitter buffer size
     *
     * @param nCurrJitterBufferSize The current jiter buffer size
     * @param currentTime The current timestamp when invoked this method with milliseconds unit
     * @return uint32_t The next jitter buffer size
     */
    uint32_t GetNextJitterBufferSize(uint32_t nCurrJitterBufferSize, uint32_t currentTime);

    /**
     * @brief Calculate transit time difference
     *
     * @param timestamp The rtp timestamp of the packet in milliseconds
     * @param arrivalTime The received timestamp of the packet in milliseconds
     * @return int32_t The calculated transit time difference of the packet
     */
    int32_t CalculateTransitTimeDifference(uint32_t timestamp, uint32_t arrivalTime);

    /**
     * @brief Add the late arrival packet time to the list to monitor the latest time of the late
     * arrival packet
     *
     * @param time The time of current late arrival appears
     */
    void SetLateArrivals(uint32_t time);

private:
    double CalculateDeviation(double* pMean);
    int32_t GetMaxJitterValue();

    std::mutex mMutex;
    uint32_t mMinJitterBufferSize;
    uint32_t mMaxJitterBufferSize;
    uint32_t mPrevTimestamp;
    uint32_t mPrevArrivalTime;
    std::list<int32_t> mListAccumDeltas;
    int32_t mPrevDelta;
    int32_t minJitterInBeginning;
    uint32_t mTimeLateArrivals;
    NETWORK_STATUS mNetworkStatus;
    uint32_t mGoodStatusEnteringTime;
    uint32_t mBadStatusChangedTime;
    uint32_t mBufferIncThreshold;
    uint32_t mBufferDecThreshold;
    uint32_t mBufferStepSize;
    double mBufferWeight;
};

#endif  // JITTERNETWORKANALYSER_H_INCLUDED
