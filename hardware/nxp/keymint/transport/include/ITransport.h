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
** Copyright 2021-2023 NXP
**
*********************************************************************************/
#pragma once
#include <memory>
#include <vector>

namespace keymint::javacard {
using std::shared_ptr;
using std::vector;

/**
 * ITransport is an interface with a set of virtual methods that allow communication between the
 * HAL and the applet on the secure element.
 */
class ITransport {
  public:
    virtual ~ITransport() {}

    explicit ITransport(__attribute__((unused))
                        const std::vector<uint8_t> &mAppletAID){};

#ifdef NXP_EXTNS
    /**
     * Sets Applet AID.
     */
    virtual bool setAppletAid(const vector<uint8_t> &aid) {
      (void)aid;
      return false;
    }
#endif
    /**
     * Opens connection.
     */
    virtual bool openConnection() = 0;
    /**
     * Send data over communication channel and receives data back from the remote end.
     */
    virtual bool sendData(const vector<uint8_t>& inData, vector<uint8_t>& output) = 0;
    /**
     * Closes the connection.
     */
    virtual bool closeConnection() = 0;
    /**
     * Returns the state of the connection status. Returns true if the connection is active, false
     * if connection is broken.
     */
    virtual bool isConnected() = 0;
};
}  // namespace keymint::javacard
