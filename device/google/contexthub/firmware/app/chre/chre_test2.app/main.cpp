/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <stdint.h>
#include <inttypes.h>
#include <chre.h>

#define APP_LABEL "CHRE App 2: "

/* chre.h does not define printf format attribute for chreLog() */
void chreLog(enum chreLogLevel level, const char *str, ...) __attribute__ ((__format__ (__printf__, 2, 3)));

#define EVT_LOCAL_SETUP CHRE_EVENT_FIRST_USER_VALUE

struct MyTimer {
    uint64_t startTime;
    uint32_t timerId;
};

struct ExtMsg
{
    uint8_t msg;
    uint32_t val;
} __attribute__((packed));

static const uint64_t kOneSecond = UINT64_C(1000000000); // in nanoseconds

static uint32_t mMyTid;
static uint64_t mMyAppId;
static int mCnt;
static struct MyTimer mTimer;

bool nanoappStart(void)
{
    mMyAppId = chreGetAppId();
    mMyTid = chreGetInstanceId();
    mCnt = 3;
    chreSendEvent(EVT_LOCAL_SETUP, NULL, NULL, mMyTid);
    chreLog(CHRE_LOG_INFO, APP_LABEL "init: offset: %" PRId64, chreGetEstimatedHostTimeOffset());
    chreConfigureNanoappInfoEvents(true);
    return true;
}

void nanoappEnd(void)
{
    chreLog(CHRE_LOG_INFO, APP_LABEL "terminating");
}

void nanoappHandleEvent(uint32_t srcTid, uint16_t evtType, const void* evtData)
{
    switch (evtType) {
    case  EVT_LOCAL_SETUP:
    {
        uint32_t instanceId = chreGetInstanceId();
        uint64_t appId = chreGetAppId();
        struct chreNanoappInfo info;

        mTimer.startTime = chreGetTime();
        mTimer.timerId = chreTimerSet(kOneSecond, &mTimer, false);
        chreLog(CHRE_LOG_INFO, APP_LABEL "started with tid %04" PRIX32
                               " timerid %" PRIu32
                               "\n", mMyTid, mTimer.timerId);
        chreLog(CHRE_LOG_INFO, APP_LABEL "appId=%016llx; instanceId=%ld",
            appId, instanceId);
        if (chreGetNanoappInfoByInstanceId(instanceId, &info)) {
            chreLog(CHRE_LOG_INFO, APP_LABEL "info by instanceId; appId=%08llx; version=%ld; instanceId=%ld",
                info.appId, info.version, info.instanceId);
        } else {
            chreLog(CHRE_LOG_INFO, APP_LABEL "error getting info by instance id");
        }
        if (chreGetNanoappInfoByAppId(appId, &info)) {
            chreLog(CHRE_LOG_INFO, APP_LABEL "info by appId; appId=%08llx; version=%ld; instanceId=%ld",
                info.appId, info.version, info.instanceId);
        } else {
             chreLog(CHRE_LOG_INFO, APP_LABEL "error getting info by app id");
        }
        break;
    }
    case CHRE_EVENT_TIMER:
    {
        const struct MyTimer *t = (const struct MyTimer *)evtData;

        chreLog(CHRE_LOG_INFO, APP_LABEL "received timer %" PRIu32
                               " (START: %" PRIu64 " TIME: %" PRIu64 " OFFSET: %" PRId64
                               ") cnt: %d\n", t->timerId, t->startTime, chreGetTime(),
                               chreGetEstimatedHostTimeOffset(),  mCnt);
        if (mCnt-- <= 0) {
            chreTimerCancel(t->timerId);
            chreAbort(0x0001);
            chreAbort(0x0002);
        }
        break;
    }
    case CHRE_EVENT_MESSAGE_FROM_HOST:
    {
        const struct chreMessageFromHostData *msg = (const struct chreMessageFromHostData *)evtData;
        const uint8_t *data = (const uint8_t *)msg->message;
        const size_t size = msg->messageSize;
        const uint32_t type = msg->messageType;
        const uint16_t endpoint = msg->hostEndpoint;
        chreLog(CHRE_LOG_INFO, APP_LABEL "message=%p; code=%d; size=%zu; type=%ld; endpoint=%d",
                data, (data && size) ? data[0] : 0, size, type, endpoint);
        break;
    }
    case CHRE_EVENT_NANOAPP_STARTED:
    {
        const struct chreNanoappInfo *msg = (const struct chreNanoappInfo *)evtData;
        const uint64_t appId = msg->appId;
        const uint32_t version = msg->version;
        const uint32_t instanceId = msg->instanceId;
        chreLog(CHRE_LOG_INFO, APP_LABEL "app started; appId=%08llx; version=%ld; instanceId=%ld",
                appId, version, instanceId);
        break;
    }
    case CHRE_EVENT_NANOAPP_STOPPED:
    {
        const struct chreNanoappInfo *msg = (const struct chreNanoappInfo *)evtData;
        const uint64_t appId = msg->appId;
        const uint32_t version = msg->version;
        const uint32_t instanceId = msg->instanceId;
        chreLog(CHRE_LOG_INFO, APP_LABEL "app stopped; appId=%08llx; version=%ld; instanceId=%ld",
                appId, version, instanceId);
        break;
    }
    }
}
