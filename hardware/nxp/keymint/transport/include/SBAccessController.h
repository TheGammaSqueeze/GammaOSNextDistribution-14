/******************************************************************************
 *
 *  Copyright 2021 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#ifndef _SBACCESSCONTROLLER_H_
#define _SBACCESSCONTROLLER_H_
#include <IntervalTimer.h>
#include <vector>

#define EARLY_BOOT_ENDED_CMD (0x35)  // INS Received from VOLD when earlyboot state ends
#define BEGIN_OPERATION_CMD (0x30)   // begin()
#define FINISH_OPERATION_CMD (0x32)  // finish()
#define ABORT_OPERATION_CMD (0x33)   // abort()

// Session timeout values during Applet upgrade
#define SMALLEST_SESSION_TIMEOUT (0)       // 0 msec, during actual upgrade process
#define UPGRADE_SESSION_TIMEOUT (5 * 100)  // 500 msecs, teared scenario

#define SB_ACCESS_BLOCK_TIMER (40 * 1000)  // 40 secs,Block access to SB applet during upgrade

// Other Session timeout
#define REGULAR_SESSION_TIMEOUT (3 * 1000)     // 3 secs,default value
#define CRYPTO_OP_SESSION_TIMEOUT (20 * 1000)  // 20 sec,for begin() operation

enum BOOTSTATE {
    SB_EARLY_BOOT = 0,
    SB_EARLY_BOOT_ENDED,
};
namespace keymint::javacard {
class SBAccessController {
  public:
    /**
     * Constructor
     */
    SBAccessController() : mIsUpdateInProgress(false), mBootState(SB_EARLY_BOOT) {}

    /**
     * Controls Applet selection
     * 1) Not allowed when actual upgrade is in progress for 40 secs
     * 2) Only allowed for allow listed cmds during early boot in upgrade teared case
     * 3) Allowed in all other cases
     * Params : void
     * Returns : true if Applet select is allowed else false
     */
    bool isSelectAllowed();

    /**
     * Parses SELECT cmd response to record if Applet upgrade is in progress
     * Params : R-APDU to SELECT cmd
     * Returns : void
     */
    void parseResponse(std::vector<uint8_t>& responseApdu);

    /**
     * Determines if current INS is allowed
     * Params : one bytes INS value
     * Returns : true if cmd is allowed else false
     */
    bool isOperationAllowed(uint8_t cmdIns);

    /**
     * Provides session timeout value for Logical channel mgmt
     * 1) UPGRADE_SESSION_TIMEOUT for upgrade teared scenario during early boot
     * 2) SMALLEST_SESSION_TIMEOUT during actual upgrade process
     * 3) CRYPTO_OP_SESSION_TIMEOUT for crypto begin()
     * 4) REGULAR_SESSION_TIMEOUT for all other operations
     * Params : void
     * Returns : Session timeout value in ms
     */
    int getSessionTimeout();
    /**
     * Helper function to check if all allowed cmds
     * are received to mark mBootState as BOOT_ENDED
     * Params: void
     * Returns: void
     */
    void updateBootState();

  private:
    bool mIsUpdateInProgress;  // stores Applet upgrade state
    BOOTSTATE mBootState;

    IntervalTimer mTimer;        // track Applet upgrade progress
    IntervalTimer mTimerCrypto;  // track crypto operations
    void startTimer(bool isStart, IntervalTimer& t, int timeout,
                    void (*timerFunc)(union sigval arg));
};
}  // namespace keymint::javacard
#endif /* _SBACCESSCONTROLLER_H_ */
