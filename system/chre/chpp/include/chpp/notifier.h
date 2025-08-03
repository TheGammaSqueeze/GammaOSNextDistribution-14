/*
 * Copyright (C) 2020 The Android Open Source Project
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

/*
 * Implementation Notes
 * Each platform must supply a platform-specific platform_notifier.h to provide
 * definitions and a platform-specific notifier.c to provide the implementation
 * for the definitions in this file.
 */

#ifndef CHPP_SYNC_H_
#define CHPP_SYNC_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Platform-specific condition variable struct defined in the platform's
 * platform_notifier.h that enables the funcions defined here.
 */
struct ChppNotifier;

/**
 * Initializes the platform-specific ChppNotifier.
 * Will be called before notifier is used anywhere else.
 *
 * @param notifier Points to the ChppNotifier being initialized.
 */
static void chppNotifierInit(struct ChppNotifier *notifier);

/**
 * Deinitializes the platform-specific ChppNotifier.
 *
 * @param notifier Points to the ChppNotifier being deinitialized.
 */
static void chppNotifierDeinit(struct ChppNotifier *notifier);

/**
 * Returns the signal value in chppNotifierSignal(), if any. Resets the signal
 * value afterwards.
 *
 * @param notifier Points to the ChppNotifier being used.
 *
 * @return The signal value indicated in chppNotifierSignal().
 */
static uint32_t chppNotifierGetSignal(struct ChppNotifier *notifier);

/**
 * Waits on a platform-specific notifier until it is signaled through
 * chppNotifierSignal(). Similar to chppNotifierTimedWait(), but without the
 * timeout.
 *
 * @param notifier Points to the ChppNotifier being used.
 *
 * @return The signal value indicated in chppNotifierSignal().
 */
static uint32_t chppNotifierWait(struct ChppNotifier *notifier);

/**
 * Waits on a platform-specific notifier until it is signaled through
 * chppNotifierSignal() or until the specified timeout.
 *
 * Note that while the timeout is specified as a uint64_t, in practice, CHPP
 * would only use values up to CHPP_TRANSPORT_TX_TIMEOUT_NS.
 *
 * @param notifier Points to the ChppNotifier being used.
 * @param timeoutNs Timeout in nanoseconds.
 *
 * @return The signal value indicated in chppNotifierSignal().
 */
static uint32_t chppNotifierTimedWait(struct ChppNotifier *notifier,
                                      uint64_t timeoutNs);

/**
 * Signals chppNotifierTimedWait() with the specified signal value.
 *
 * The signal values can be defined by the user of this class. Note that it is
 * expected for different signals to be bitwise exclusive, i.e. each bit in the
 * uint32_t should indicate a specific type of signal event. This allows for
 * multiple events to be handled simultaneously in chppNotifierTimedWait().
 *
 * @param notifier Points to the ChppNotifier being used.
 * @param signal The value where each bit represents a different event.
 *               As such the value 0 will not notify any event.
 */
static void chppNotifierSignal(struct ChppNotifier *notifier, uint32_t signal);

#ifdef __cplusplus
}
#endif

#include "chpp/platform/platform_notifier.h"

#endif  // CHPP_SYNC_H_
