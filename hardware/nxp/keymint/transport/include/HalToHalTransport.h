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
 ** Copyright 2020-2021 NXP
 **
 *********************************************************************************/
#pragma once
#include "ITransport.h"
#include <AppletConnection.h>
#include <IntervalTimer.h>
#include <memory>
#include <vector>

namespace keymint::javacard {
using std::shared_ptr;
using std::vector;
/**
 * HalToHalTransport is derived from ITransport. This class gets the OMAPI service binder instance and uses IPC to
 * communicate with OMAPI service. OMAPI inturn communicates with hardware via ISecureElement.
 */
class HalToHalTransport : public ITransport {

public:
    HalToHalTransport(const std::vector<uint8_t>& mAppletAID)
        : ITransport(mAppletAID),
          mAppletConnection(mAppletAID) {}

    /**
     * Gets the binder instance of ISEService, gets the reader corresponding to secure element, establishes a session
     * and opens a basic channel.
     */
    bool openConnection() override;
    /**
     * Transmists the data over the opened basic channel and receives the data back.
     */
    bool sendData(const vector<uint8_t>& inData, vector<uint8_t>& output) override;
    /**
     * Closes the connection.
     */
    bool closeConnection() override;
    /**
     * Returns the state of the connection status. Returns true if the connection is active, false if connection is
     * broken.
     */
    bool isConnected() override;
private:
    AppletConnection mAppletConnection;
    IntervalTimer mTimer;

};
}  // namespace keymint::javacard