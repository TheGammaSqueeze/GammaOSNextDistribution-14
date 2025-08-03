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

#ifndef __SIGNALHANDLER_H__
#define __SIGNALHANDLER_H__

#include <signal.h>

namespace keymint::javacard {

class SignalHandler {
  public:
    /**
     * Retrieve instance of SignalHandler class
     */
    static SignalHandler* getInstance();
    /**
     * register signal Handler.
     * Use mPtrContext to pass handle which might be required in signal Handler
     */
    void installHandler(void* mPtrContext);

    /**
     * block Signals to prevent interrupts during critical parts of code
     */
    void blockSignals();

    /**
     * Unblock signal previously blocked using blockSignals()
     * Pending signals are delivered after they are unblocked.
     */
    void unblockSignals();

    /**
     * Check if Signal handler is registered
     */
    bool isHandlerRegistered();

  private:
    SignalHandler();
    bool isRegistered;
    void* mContext;
    static SignalHandler* instance;
    sigset_t blockedSignals, oldMask;
};

}  // namespace keymint::javacard
#endif  // __SIGNALHANDLER_H__
