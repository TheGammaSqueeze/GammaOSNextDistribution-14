/*
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 ** http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 **
 ** Copyright 2021 NXP
 **
 */

#define LOG_TAG "OmapiTransport_SignalHandler"

#include <android-base/logging.h>
#include <log/log.h>
#include <signal.h>
#include <iomanip>
#include <vector>

#include <AppletConnection.h>
#include <SignalHandler.h>

keymint::javacard::AppletConnection* g_AppClient = nullptr;

#define NUM_OF_SIGNALS 6
int handledSignals[] = {SIGTRAP, SIGFPE, SIGILL, SIGSEGV, SIGBUS, SIGTERM};
struct sigaction old_action[NUM_OF_SIGNALS] = {};

namespace keymint::javacard {

/* Handle Fatal signals to close opened Logical channel before
 * the process dies
 */
void customSignalHandler(int sig, siginfo_t* info, void* ucontext) {
    LOG(WARNING) << "received signal " << sig;
    if (g_AppClient != nullptr) g_AppClient->close();
    // default handling of the received signal
    for (int i = 0; i < NUM_OF_SIGNALS; i++) {
        if (handledSignals[i] == sig) {
            if (old_action[i].sa_sigaction != nullptr) {
                LOG(INFO) << "execute originally installed handler" << sig;
                (*(old_action[i].sa_sigaction))(sig, info, ucontext);
            } else if (old_action[i].sa_handler == SIG_DFL || old_action[i].sa_handler == SIG_IGN) {
                signal(sig, old_action[i].sa_handler);  // reset to old handler
                raise(sig);
            }
            break;
        }
    }
}

/**
 * Register signal Handler.
 * Use mPtrC to pass handle/data which might be required in signal Handler context
 * marks registration OK if at least one of signal is registered successfully
 */

void SignalHandler::installHandler(void* mPtr) {
    mContext = mPtr;
    g_AppClient = (keymint::javacard::AppletConnection*)mContext;
    int reg_signals = 0;
    for (int i = 0; i < NUM_OF_SIGNALS; i++) {
        struct sigaction enable_act = {};
        enable_act.sa_sigaction = customSignalHandler;
        enable_act.sa_flags = SA_RESTART | SA_SIGINFO;
        if (sigaction(handledSignals[i], &enable_act, &(old_action[i])) != 0) {
            LOG(ERROR) << "Unable to set up signal handler for signal " << handledSignals[i]
                       << "errno " << errno;
        } else {
            reg_signals++;
        }
    }
    if (reg_signals > 0) isRegistered = true;
}

SignalHandler::SignalHandler() {
    sigemptyset(&blockedSignals);
    sigemptyset(&oldMask);
    mContext = nullptr;
    isRegistered = false;
}

SignalHandler* SignalHandler::instance = nullptr;

SignalHandler* SignalHandler::getInstance() {
    if (!instance) {
        instance = new SignalHandler();
    }
    return instance;
}

bool SignalHandler::isHandlerRegistered() {
    return isRegistered;
}

/**
 * block Signals to prevent interrupts during critical parts of code
 */

void SignalHandler::blockSignals() {
    if (!isRegistered) return;
    sigemptyset(&blockedSignals);
    sigemptyset(&oldMask);

    for (int i = 0; i < NUM_OF_SIGNALS; i++) {
        sigaddset(&blockedSignals, handledSignals[i]);
    }
    if (sigprocmask(SIG_BLOCK, &blockedSignals, &oldMask) < 0) {
        LOG(ERROR) << "failed to block signal " << errno;
    }
}

/**
 * Unblock signal previously blocked using blockSignals()
 * Pending signals are delivered after they are unblocked.
 */

void SignalHandler::unblockSignals() {
    if (!isRegistered) return;
    if (sigprocmask(SIG_SETMASK, &oldMask, NULL) < 0) {
        LOG(ERROR) << "failed to set sigmask " << errno;
    }
}

}  // namespace keymint::javacard
