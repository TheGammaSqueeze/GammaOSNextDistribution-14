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

#include <ImsMediaDefine.h>
#include <JitterNetworkAnalyser.h>
#include <ImsMediaTimer.h>
#include <ImsMediaTrace.h>
#include <numeric>
#include <cmath>
#include <algorithm>
#include <climits>

#define MAX_JITTER_LIST_SIZE    (150)
#define PACKET_INTERVAL         (20)    // milliseconds
#define BUFFER_INCREASE_TH      (200)   // milliseconds
#define BUFFER_DECREASE_TH      (2000)  // milliseconds
#define MARGIN_WEIGHT           (1.0f)
#define BUFFER_IN_DECREASE_SIZE (2)
#define ROUNDUP_MARGIN          (10)  // milliseconds

JitterNetworkAnalyser::JitterNetworkAnalyser()
{
    mMinJitterBufferSize = 0;
    mMaxJitterBufferSize = 0;
    mBufferIncThreshold = BUFFER_INCREASE_TH;
    mBufferDecThreshold = BUFFER_DECREASE_TH;
    mBufferStepSize = BUFFER_IN_DECREASE_SIZE;
    mBufferWeight = MARGIN_WEIGHT;

    IMLOGD4("[JitterNetworkAnalyser] incThreshold=%d, decThreshold=%d, stepSize=%d, "
            "weight[%.3f]",
            mBufferIncThreshold, mBufferDecThreshold, mBufferStepSize, mBufferWeight);
    Reset();
}

JitterNetworkAnalyser::~JitterNetworkAnalyser() {}

void JitterNetworkAnalyser::Reset()
{
    mNetworkStatus = NETWORK_STATUS_NORMAL;
    mGoodStatusEnteringTime = 0;
    mBadStatusChangedTime = 0;

    {
        std::lock_guard<std::mutex> guard(mMutex);
        mListAccumDeltas.clear();
        mPrevTimestamp = 0;
        mPrevArrivalTime = 0;
        mTimeLateArrivals = 0;
        mPrevDelta = 0;
        minJitterInBeginning = INT_MAX;
    }
}

void JitterNetworkAnalyser::SetMinMaxJitterBufferSize(
        uint32_t nMinBufferSize, uint32_t nMaxBufferSize)
{
    mMinJitterBufferSize = nMinBufferSize;
    mMaxJitterBufferSize = nMaxBufferSize;
}

void JitterNetworkAnalyser::SetJitterOptions(
        uint32_t incThreshold, uint32_t decThreshold, uint32_t stepSize, double weight)
{
    mBufferIncThreshold = incThreshold;
    mBufferDecThreshold = decThreshold;
    mBufferStepSize = stepSize;
    mBufferWeight = weight;

    IMLOGD4("[SetJitterOptions] incThreshold=%d, decThreshold=%d, stepSize=%d, weight[%.3f]",
            mBufferIncThreshold, mBufferDecThreshold, mBufferStepSize, mBufferWeight);
}

template <typename Map>
typename Map::const_iterator getGreatestLess(Map const& m, typename Map::key_type const& k)
{
    typename Map::const_iterator it = m.lower_bound(k);
    if (it != m.begin())
    {
        return --it;
    }
    return m.end();
}

int32_t JitterNetworkAnalyser::CalculateTransitTimeDifference(
        uint32_t timestamp, uint32_t arrivalTime)
{
    std::lock_guard<std::mutex> guard(mMutex);

    if (mPrevTimestamp == 0)
    {
        mPrevTimestamp = timestamp;
        mPrevArrivalTime = arrivalTime;
        minJitterInBeginning = INT_MAX;
        return 0;
    }

    int32_t inputTimestampGap = timestamp - mPrevTimestamp;
    int32_t inputTimeGap = arrivalTime - mPrevArrivalTime;
    int32_t delta = inputTimeGap - inputTimestampGap;

    mPrevTimestamp = timestamp;
    mPrevArrivalTime = arrivalTime;

    mPrevDelta += delta;
    mListAccumDeltas.push_back(
            minJitterInBeginning == INT_MAX ? mPrevDelta : mPrevDelta - minJitterInBeginning);

    if (mListAccumDeltas.size() > MAX_JITTER_LIST_SIZE)
    {
        mListAccumDeltas.pop_front();
    }
    else
    {
        // for normalization
        if (minJitterInBeginning > mPrevDelta)
        {
            minJitterInBeginning = mPrevDelta;
        }
    }

    return delta;
}

void JitterNetworkAnalyser::SetLateArrivals(uint32_t time)
{
    mTimeLateArrivals = time;
}

double JitterNetworkAnalyser::CalculateDeviation(double* pMean)
{
    std::lock_guard<std::mutex> guard(mMutex);

    if (mListAccumDeltas.empty())
    {
        *pMean = 0;
        return 0.0f;
    }

    double mean = std::accumulate(mListAccumDeltas.begin(), mListAccumDeltas.end(), 0.0f) /
            mListAccumDeltas.size();

    *pMean = mean;

    double dev = sqrt(std::accumulate(mListAccumDeltas.begin(), mListAccumDeltas.end(), 0.0f,
                              [mean](int x, int y)
                              {
                                  return x + std::pow(y - mean, 2);
                              }) /
            mListAccumDeltas.size());

    return dev;
}

int32_t JitterNetworkAnalyser::GetMaxJitterValue()
{
    std::lock_guard<std::mutex> guard(mMutex);

    if (mListAccumDeltas.empty())
    {
        return 0;
    }

    return *std::max_element(mListAccumDeltas.begin(), mListAccumDeltas.end());
}

uint32_t JitterNetworkAnalyser::GetNextJitterBufferSize(
        uint32_t nCurrJitterBufferSize, uint32_t currentTime)
{
    uint32_t nextJitterBuffer = nCurrJitterBufferSize;
    NETWORK_STATUS networkStatus;

    double dev, mean;
    double calcJitterSize = 0;
    int32_t maxJitter = GetMaxJitterValue();
    dev = CalculateDeviation(&mean);
    calcJitterSize = (double)maxJitter * mBufferWeight + ROUNDUP_MARGIN;
    uint32_t bufferSize = calcJitterSize / PACKET_INTERVAL;

    if (bufferSize > nCurrJitterBufferSize)
    {
        networkStatus = NETWORK_STATUS_BAD;
    }
    else if (bufferSize < nCurrJitterBufferSize - 1)
    {
        networkStatus = NETWORK_STATUS_GOOD;
    }
    else
    {
        networkStatus = NETWORK_STATUS_NORMAL;
    }

    IMLOGD_PACKET6(IM_PACKET_LOG_JITTER,
            "[GetNextJitterBufferSize] size=%4.2f, mean=%4.2f, dev=%4.2f, max=%d, curr=%d, "
            "status=%d",
            calcJitterSize, mean, dev, maxJitter, nCurrJitterBufferSize, networkStatus);

    switch (networkStatus)
    {
        case NETWORK_STATUS_BAD:
        {
            nextJitterBuffer = bufferSize;

            if (nextJitterBuffer > mMaxJitterBufferSize)
            {
                nextJitterBuffer = mMaxJitterBufferSize;
            }
            else if (nextJitterBuffer < mMinJitterBufferSize)
            {
                nextJitterBuffer = mMinJitterBufferSize;
            }

            IMLOGD_PACKET2(IM_PACKET_LOG_JITTER,
                    "[GetNextJitterBufferSize] increase curr=%d, next=%d", nCurrJitterBufferSize,
                    nextJitterBuffer);
            break;
        }
        case NETWORK_STATUS_GOOD:
        {
            if (mNetworkStatus != NETWORK_STATUS_GOOD)
            {
                mGoodStatusEnteringTime = currentTime;
            }
            else
            {
                uint32_t nTimeDiff = currentTime - mGoodStatusEnteringTime;

                if (nTimeDiff >= mBufferDecThreshold &&
                        (mTimeLateArrivals == 0 ||
                                currentTime - mTimeLateArrivals > mBufferDecThreshold))
                {
                    uint32_t decreaseStep = nCurrJitterBufferSize - bufferSize;

                    decreaseStep > mBufferStepSize
                            ? nextJitterBuffer = nCurrJitterBufferSize - mBufferStepSize
                            : nextJitterBuffer = nCurrJitterBufferSize - decreaseStep;

                    if (nextJitterBuffer < mMinJitterBufferSize)
                    {
                        nextJitterBuffer = mMinJitterBufferSize;
                    }

                    IMLOGD_PACKET2(IM_PACKET_LOG_JITTER,
                            "[GetNextJitterBufferSize] decrease curr=%d, next=%d",
                            nCurrJitterBufferSize, nextJitterBuffer);
                    networkStatus = NETWORK_STATUS_NORMAL;
                }
            }

            break;
        }
        default:
            nextJitterBuffer = nCurrJitterBufferSize;
            break;
    }

    mNetworkStatus = networkStatus;
    return nextJitterBuffer;
}
