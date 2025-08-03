/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define STATSD_DEBUG false  // STOPSHIP if true
#include "Log.h"

#include <android/binder_ibinder.h>
#include <android/binder_ibinder_platform.h>
#include <android/binder_interface_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <stdio.h>
#include <sys/random.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <utils/Looper.h>

#include "StatsService.h"
#include "flags/FlagProvider.h"
#include "packages/UidMap.h"
#include "socket/StatsSocketListener.h"

using namespace android;
using namespace android::os::statsd;
using ::ndk::SharedRefBase;
using std::shared_ptr;
using std::make_shared;

shared_ptr<StatsService> gStatsService = nullptr;
sp<StatsSocketListener> gSocketListener = nullptr;
int gCtrlPipe[2];

void signalHandler(int sig) {
    ALOGW("statsd terminated on receiving signal %d.", sig);
    const char c = 'q';
    write(gCtrlPipe[1], &c, 1);
}

void registerSignalHandlers()
{
    struct sigaction sa;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;

    sa.sa_handler = SIG_IGN;
    // ShellSubscriber uses SIGPIPE as a signal to detect the end of the
    // client process. Don't prematurely exit(1) here. Instead, ignore the
    // signal and allow the write call to return EPIPE.
    sigaction(SIGPIPE, &sa, nullptr);

    pipe2(gCtrlPipe, O_CLOEXEC);
    sa.sa_handler = signalHandler;
    sigaction(SIGTERM, &sa, nullptr);
}

void initSeedRandom() {
    unsigned int seed = 0;
    // getrandom() reads bytes from urandom source into buf. If getrandom()
    // is unable to read from urandom source, then it returns -1 and we set
    // out seed to be time(nullptr) as a fallback.
    if (TEMP_FAILURE_RETRY(
                getrandom(static_cast<void*>(&seed), sizeof(unsigned int), GRND_NONBLOCK)) < 0) {
        seed = time(nullptr);
    }
    srand(seed);
}

int main(int /*argc*/, char** /*argv*/) {
    // Set up the looper
    sp<Looper> looper(Looper::prepare(0 /* opts */));

    // Set up the binder
    ABinderProcess_setThreadPoolMaxThreadCount(9);
    ABinderProcess_startThreadPool();

    // Initialize boot flags
    FlagProvider::getInstance().initBootFlags({STATSD_INIT_COMPLETED_NO_DELAY_FLAG});

    std::shared_ptr<LogEventQueue> eventQueue =
            std::make_shared<LogEventQueue>(50000); /*buffer limit. Buffer is NOT pre-allocated*/

    sp<UidMap> uidMap = UidMap::getInstance();

    std::shared_ptr<LogEventFilter> logEventFilter = std::make_shared<LogEventFilter>();

    const int initEventDelay = FlagProvider::getInstance().getBootFlagBool(
                                       STATSD_INIT_COMPLETED_NO_DELAY_FLAG, FLAG_FALSE)
                                       ? 0
                                       : StatsService::kStatsdInitDelaySecs;
    initSeedRandom();
    // Create the service
    gStatsService =
            SharedRefBase::make<StatsService>(uidMap, eventQueue, logEventFilter, initEventDelay);
    auto binder = gStatsService->asBinder();

    // We want to be able to ask for the selinux context of callers:
    if (__builtin_available(android __ANDROID_API_U__, *)) {
        AIBinder_setRequestingSid(binder.get(), true);
    }

    // TODO(b/149582373): Set DUMP_FLAG_PROTO once libbinder_ndk supports
    // setting dumpsys priorities.
    binder_status_t status = AServiceManager_addService(binder.get(), "stats");
    if (status != STATUS_OK) {
        ALOGE("Failed to add service as AIDL service");
        return -1;
    }

    gStatsService->sayHiToStatsCompanion();

    gStatsService->Startup();

    gSocketListener = new StatsSocketListener(eventQueue, logEventFilter);

    ALOGI("Statsd starts to listen to socket.");
    // Backlog and /proc/sys/net/unix/max_dgram_qlen set to large value
    if (gSocketListener->startListener(600)) {
        exit(1);
    }

    // Use self-pipe to notify this thread to gracefully quit
    // when receiving SIGTERM
    registerSignalHandlers();
    std::thread([] {
        while (true) {
            char c;
            int i = read(gCtrlPipe[0], &c, 1);
            if (i < 0) {
                if (errno == EINTR) continue;
            }
            gSocketListener->stopListener();
            gStatsService->Terminate();
            // return the signal handler to its default disposition, then raise the signal again
            signal(SIGTERM, SIG_DFL);
            // this is a sync call which leads to immediate process termination and
            // no destructors are called, semantically similar to call exit(1) here
            raise(SIGTERM);
        }
    }).detach();

    // Loop forever -- the reports run on this thread in a handler, and the
    // binder calls remain responsive in their pool of one thread.
    while (true) {
        looper->pollAll(-1 /* timeoutMillis */);
    }
    ALOGW("statsd escaped from its loop.");

    return 1;
}
