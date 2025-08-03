/*
 **
 ** Copyright 2020, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */
/******************************************************************************
 **
 ** The original Work has been changed by NXP.
 **
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
 ** Copyright 2020-2021, 2023 NXP
 **
 *********************************************************************************/
#define LOG_TAG "HalToHalTransport"

#include <vector>
#include <signal.h>
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <iomanip>

#include <HalToHalTransport.h>
#include <EseTransportUtils.h>
#include <IntervalTimer.h>

namespace keymint::javacard {
void SessionTimerFunc(union sigval arg){
     LOG(INFO) << "Session Timer expired !!";
     HalToHalTransport *obj = (HalToHalTransport*)arg.sival_ptr;
     if(obj != nullptr)
       obj->closeConnection();
}
bool HalToHalTransport::openConnection() {
	  return mAppletConnection.connectToSEService();
}

bool HalToHalTransport::sendData(const vector<uint8_t>& inData, vector<uint8_t>& output) {
    bool status = false;
    std::vector<uint8_t> cApdu(inData);
#ifdef INTERVAL_TIMER
     LOGD_OMAPI("stop the timer");
     mTimer.kill();
#endif
     if (!isConnected()) {
         std::vector<uint8_t> selectResponse;
         status = mAppletConnection.openChannelToApplet(selectResponse);
         if (!status) {
             LOG(ERROR) << " Failed to open Logical Channel ,response " << selectResponse;
             output = std::move(selectResponse);
             return status;
         }
     }
    status = mAppletConnection.transmit(cApdu, output);
    if (output.size() < 2 ||
        (output.size() >= 2 && (output.at(output.size() - 2) == LOGICAL_CH_NOT_SUPPORTED_SW1 &&
                                output.at(output.size() - 1) == LOGICAL_CH_NOT_SUPPORTED_SW2))) {
        LOGD_OMAPI("transmit failed ,close the channel");
        return mAppletConnection.close();
    }
#ifdef INTERVAL_TIMER
     int timeout = mAppletConnection.getSessionTimeout();
     if(timeout == 0) {
       closeConnection(); //close immediately
     } else {
       LOGD_OMAPI("Set the timer with timeout " << timeout << " ms");
       mTimer.set(mAppletConnection.getSessionTimeout(), this, SessionTimerFunc);
     }
#endif
    return status;
}

bool HalToHalTransport::closeConnection() {
    return mAppletConnection.close();
}

bool HalToHalTransport::isConnected() {
    return mAppletConnection.isChannelOpen();
}
} // namespace keymint::javacard
