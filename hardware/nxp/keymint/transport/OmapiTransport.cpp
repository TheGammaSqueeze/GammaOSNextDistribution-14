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
 ** Copyright 2022-2023 NXP
 **
 *********************************************************************************/
#define LOG_TAG "OmapiTransport"
#if defined OMAPI_TRANSPORT
#include "OmapiTransport.h"

#include <arpa/inet.h>
#include <iomanip>
#include <map>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>
#include <vector>

#include <android-base/logging.h>
#include <android-base/stringprintf.h>

#include <EseTransportUtils.h>
#include <IntervalTimer.h>

#define UNUSED_V(a) a=a
#define RESP_CHANNEL_NOT_AVAILABLE 0x6881
#ifdef NXP_EXTNS
#define DEFAULT_SESSION_TIMEOUT_MSEC 1000
#endif

using android::base::StringPrintf;

namespace keymint::javacard {

std::string const ESE_READER_PREFIX = "eSE";
constexpr const char omapiServiceName[] = "android.se.omapi.ISecureElementService/default";

class SEListener : public ::aidl::android::se::omapi::BnSecureElementListener {};

#ifdef NXP_EXTNS

static std::mutex sCookiesMutex;
static uintptr_t sCookiesKeyCounter = 0;
static std::map<uintptr_t, OmapiTransport *> sCookies;

void omapiSessionTimerFunc(union sigval arg){
     LOG(INFO) << "Session Timer expired !!";
     OmapiTransport *obj = (OmapiTransport*)arg.sival_ptr;
     if(obj != nullptr)
       obj->closeChannel();
}

void OmapiTransport::BinderDiedCallback(void *cookie) {
  std::lock_guard lock(sCookiesMutex);
  if (auto it = sCookies.find(reinterpret_cast<uintptr_t>(cookie));
      it != sCookies.end()) {
    LOG(ERROR)
        << "Received binder died with cookie: " << cookie
        << ". OMAPI Service died, closing connection";
    it->second->closeConnection();
  } else {
    LOG(ERROR)
        << "Received binder died with cookie: " << cookie
        << ". OMAPI Service died, but no OmapiTransport.";
  }
}
#endif

OmapiTransport::~OmapiTransport() {
#ifdef NXP_EXTNS
  std::lock_guard sLock(sCookiesMutex);
  std::lock_guard mLock(mCookieKeysMutex);
  for (auto cookie : mCookieKeys) {
    LOG(INFO) << "OmapiTransport destructor cleaning up death recipient cookie("
              << cookie << ") as we no longer need to listen for service death.";
    sCookies.erase(cookie);
  }
#endif
}

bool OmapiTransport::initialize() {
    LOG(DEBUG) << "Initialize the secure element connection";

    // Get OMAPI vendor stable service handler
#ifdef NXP_EXTNS
    ::ndk::SpAIBinder ks2Binder(AServiceManager_checkService(omapiServiceName));
    omapiSeService = aidl::android::se::omapi::ISecureElementService::fromBinder(ks2Binder);
#else
    ::ndk::SpAIBinder ks2Binder(AServiceManager_getService(omapiServiceName));
    omapiSeService = aidl::android::se::omapi::ISecureElementService::fromBinder(ks2Binder);
#endif

    if (omapiSeService == nullptr) {
        LOG(ERROR) << "Failed to start omapiSeService null";
        return false;
    }

#ifdef NXP_EXTNS
    {
      std::lock_guard sLock(sCookiesMutex);
      uintptr_t cookieKey = sCookiesKeyCounter++;
      std::lock_guard mLock(mCookieKeysMutex);
      mCookieKeys.push_back(cookieKey);
      sCookies[cookieKey] = this;
      LOG(INFO) << "linkToDeath on OMAPI service with cookie: " << cookieKey;
      AIBinder_linkToDeath(omapiSeService->asBinder().get(),
                           mDeathRecipient.get(),
                           reinterpret_cast<void *>(cookieKey));
    }
#endif

    // reset readers, clear readers if already existing
    if (mVSReaders.size() > 0) {
        closeConnection();
    }

    std::vector<std::string> readers = {};
    // Get available readers
    auto status = omapiSeService->getReaders(&readers);
    if (!status.isOk()) {
        LOG(ERROR) << "getReaders failed to get available readers: " << status.getMessage();
        return false;
    }

    // Get SE readers handlers
    for (auto & readerName : readers) {
        std::shared_ptr<::aidl::android::se::omapi::ISecureElementReader> reader;
        status = omapiSeService->getReader(readerName, &reader);
        if (!status.isOk()) {
            LOG(ERROR) << "getReader for " << readerName.c_str() << " Failed: "
                       << status.getMessage();
            return false;
        }

        mVSReaders[readerName] = std::move(reader);
    }

    // Find eSE reader, as of now assumption is only eSE available on device
    LOG(DEBUG) << "Finding eSE reader";
    eSEReader = nullptr;
    if (mVSReaders.size() > 0) {
        for (const auto& [name, reader] : mVSReaders) {
            if (name.find(ESE_READER_PREFIX, 0) != std::string::npos) {
                LOG(DEBUG) << "eSE reader found: " << name;
                eSEReader = reader;
#ifdef NXP_EXTNS
                std::string prefTerminalName = "eSE1";
                if (name.compare(prefTerminalName) == 0x00 ) {
                    LOG(DEBUG) << "Found reader "<< prefTerminalName << " breaking.";
                    break;
                }
#endif
            }
        }
    }

    if (eSEReader == nullptr) {
        LOG(ERROR) << "secure element reader " << ESE_READER_PREFIX << " not found";
        return false;
    }

    return true;
}

bool OmapiTransport::internalTransmitApdu(
        std::shared_ptr<aidl::android::se::omapi::ISecureElementReader> reader,
        std::vector<uint8_t> apdu, std::vector<uint8_t>& transmitResponse) {
    auto mSEListener = ndk::SharedRefBase::make<SEListener>();
    LOG(DEBUG) << "internalTransmitApdu: trasmitting data to secure element";

    if (reader == nullptr) {
        LOG(ERROR) << "eSE reader is null";
        return false;
    }

    bool status = false;
    auto res = reader->isSecureElementPresent(&status);
    if (!res.isOk()) {
        LOG(ERROR) << "isSecureElementPresent error: " << res.getMessage();
        return false;
    }
    if (!status) {
        LOG(ERROR) << "secure element not found";
        return false;
    }

    res = reader->openSession(&session);
    if (!res.isOk()) {
        LOG(ERROR) << "openSession error: " << res.getMessage();
        return false;
    }
    if (session == nullptr) {
        LOG(ERROR) << "Could not open session null";
        return false;
    }

    res = session->openLogicalChannel(mSelectableAid, 0x00, mSEListener, &channel);
    if (!res.isOk()) {
        LOG(ERROR) << "openLogicalChannel error: " << res.getMessage();
        return false;
    }
    if (channel == nullptr) {
        LOG(ERROR) << "Could not open channel null";
        return false;
    }

    std::vector<uint8_t> selectResponse = {};
    res = channel->getSelectResponse(&selectResponse);
    if (!res.isOk()) {
        LOG(ERROR) << "getSelectResponse error: " << res.getMessage();
        return false;
    }

    if ((selectResponse.size() < 2) ||
        ((selectResponse[selectResponse.size() -1] & 0xFF) != 0x00) ||
        ((selectResponse[selectResponse.size() -2] & 0xFF) != 0x90)) {
        LOG(ERROR) << "Failed to select the Applet.";
        return false;
    }

    res = channel->transmit(apdu, &transmitResponse);
    if (channel != nullptr) channel->close();
    if (session != nullptr) session->close();

    LOG(INFO) << "STATUS OF TRNSMIT: " << res.getExceptionCode() << " Message: "
              << res.getMessage();
    if (!res.isOk()) {
        LOG(ERROR) << "transmit error: " << res.getMessage();
        return false;
    }

    return true;
}

bool OmapiTransport::openConnection() {
    // if already conection setup done, no need to initialise it again.
    if (isConnected()) {
        return true;
    }

    return initialize();
}

bool OmapiTransport::sendData(const vector<uint8_t>& inData, vector<uint8_t>& output) {
    std::vector<uint8_t> apdu(inData);
#ifdef INTERVAL_TIMER
     LOGD_OMAPI("stop the timer");
     mTimer.kill();
#endif
    if (!isConnected()) {
        // Try to initialize connection to eSE
        LOG(INFO) << "Failed to send data, try to initialize connection SE connection";
        if (!initialize()) {
            LOG(ERROR) << "Failed to send data, initialization not completed";
            closeConnection();
            return false;
        }
    }

    if (inData.size() == 0x00) {
        LOG(ERROR) << "Failed to send data, APDU is null";
        return false;
    }

    if (eSEReader != nullptr) {
        LOG(DEBUG) << "Sending apdu data to secure element: " << ESE_READER_PREFIX;
#ifdef NXP_EXTNS
        return internalProtectedTransmitApdu(eSEReader, std::move(apdu), output);
#else
        return internalTransmitApdu(eSEReader, apdu, output);
#endif
    } else {
        LOG(ERROR) << "secure element reader " << ESE_READER_PREFIX << " not found";
        return false;
    }
}

bool OmapiTransport::closeConnection() {
    LOG(DEBUG) << "Closing all connections";
    if (omapiSeService != nullptr) {
        if (mVSReaders.size() > 0) {
            for (const auto& [name, reader] : mVSReaders) {
                reader->closeSessions();
            }
            mVSReaders.clear();
        }
    }
#ifdef NXP_EXTNS
    if (omapiSeService != nullptr) {
      std::lock_guard sLock(sCookiesMutex);
      std::lock_guard mLock(mCookieKeysMutex);
      for (auto cookie : mCookieKeys) {
        LOG(INFO) << "unlinkToDeath on OMAPI service with cookie: " << cookie;
        AIBinder_unlinkToDeath(omapiSeService->asBinder().get(),
                               mDeathRecipient.get(),
                               reinterpret_cast<void *>(cookie));
        sCookies.erase(cookie);
      }
      mCookieKeys.clear();
      omapiSeService = nullptr;
    }
    session = nullptr;
    channel = nullptr;
#endif
    return true;
}

bool OmapiTransport::isConnected() {
    // Check already initialization completed or not
    if (omapiSeService != nullptr && eSEReader != nullptr) {
        LOG(DEBUG) << "Connection initialization already completed";
        return true;
    }

    LOG(DEBUG) << "Connection initialization not completed";
    return false;
}

#ifdef NXP_EXTNS

void OmapiTransport::setDefaultTimeout(int timeout) {
    if (mTimeout != timeout) {
        mTimeout = timeout;
    }
}

bool OmapiTransport::internalProtectedTransmitApdu(
        std::shared_ptr<aidl::android::se::omapi::ISecureElementReader> reader,
        std::vector<uint8_t> apdu, std::vector<uint8_t>& transmitResponse) {
    //auto mSEListener = std::make_shared<SEListener>();
    std::vector<uint8_t> selectResponse = {};
    const std::vector<uint8_t> sbAppletAID = {0xA0, 0x00, 0x00, 0x00, 0x62};

    if (reader == nullptr) {
        LOG(ERROR) << "eSE reader is null";
        return false;
    }

    bool status = false;
    auto res = reader->isSecureElementPresent(&status);
    if (!res.isOk()) {
        LOG(ERROR) << "isSecureElementPresent error: " << res.getMessage();
        return false;
    }
    if (!status) {
        LOG(ERROR) << "secure element not found";
        return false;
    }

    if (session == nullptr || ((session->isClosed(&status).isOk() && status))) {
        res = reader->openSession(&session);
        if (!res.isOk()) {
            LOG(ERROR) << "openSession error: " << res.getMessage();
            return false;
        }
        if (session == nullptr) {
            LOG(ERROR) << "Could not open session null";
            return false;
        }
    }

    if ((channel == nullptr || (channel->isClosed(&status).isOk() && status))) {
      if (!mSBAccessController.isOperationAllowed(apdu[APDU_INS_OFFSET])) {
        LOG(ERROR) << "Select / Command INS not allowed";
        prepareErrorRepsponse(transmitResponse);
        return false;
      }

      if (!openChannelToApplet()) {
        LOG(ERROR) << "openLogicalChannel error: " << res.getMessage();
        // Assume Applet selection Fail
        transmitResponse.push_back(APP_NOT_FOUND_SW1);
        transmitResponse.push_back(APP_NOT_FOUND_SW2);
        return false;
      }
      if (channel == nullptr) {
        LOG(ERROR) << "Could not open channel null";
        return false;
      }

      res = channel->getSelectResponse(&selectResponse);
      if (!res.isOk()) {
        LOG(ERROR) << "getSelectResponse error: " << res.getMessage();
        return false;
      }
      if ((selectResponse.size() < 2)
          || ((selectResponse[selectResponse.size() -1] & 0xFF) != 0x00)
          || ((selectResponse[selectResponse.size() -2] & 0xFF) != 0x90))
      {
          LOG(ERROR) << "Failed to select the Applet.";
          return false;
      }
      if (sbAppletAID == mSelectableAid) {
        mSBAccessController.parseResponse(selectResponse);
      }
    }

    status = false;
    if (mSBAccessController.isOperationAllowed(apdu[APDU_INS_OFFSET])) {
#ifdef ENABLE_DEBUG_LOG
      LOGD_OMAPI("constructed apdu: " << apdu);
#endif
      res = channel->transmit(apdu, &transmitResponse);
      status = true;
    } else {
        LOG(ERROR) << "command Ins:" << apdu[APDU_INS_OFFSET] << " not allowed";
        prepareErrorRepsponse(transmitResponse);
    }
#ifdef INTERVAL_TIMER
    int timeout = 0x00;
    if (mTimeout) {
        timeout = mTimeout;
    } else {
        timeout = ((kWeaverAID == mSelectableAid)
                       ? DEFAULT_SESSION_TIMEOUT_MSEC
                       : mSBAccessController.getSessionTimeout());
    }

    if (timeout == 0 || !res.isOk() ||
        ((transmitResponse.size() >= 2) &&
         (getApduStatus(transmitResponse) == RESP_CHANNEL_NOT_AVAILABLE))) {
      closeChannel(); // close immediately
    } else {
      LOGD_OMAPI("Set the timer with timeout " << timeout << " ms");
      if (!mTimer.set(timeout, this, omapiSessionTimerFunc)) {
        LOG(ERROR) << "Set Timer Failed !!!";
        closeChannel();
      }
    }
#else
    closeChannel();
#endif

    LOGD_OMAPI("STATUS OF TRNSMIT: " << res.getExceptionCode() << " Message: "
              << res.getMessage());
    if (!res.isOk()) {
        LOG(ERROR) << "transmit error: " << res.getMessage();
        return false;
    }
    return status;
}

void OmapiTransport::prepareErrorRepsponse(std::vector<uint8_t>& resp){
        resp.clear();
        resp.push_back(0xFF);
        resp.push_back(0xFF);
}

void OmapiTransport::closeChannel() {
  if (channel != nullptr)
    channel->close();
  LOGD_OMAPI("Channel closed");
}

bool OmapiTransport::openChannelToApplet() {
  auto mSEListener = ndk::SharedRefBase::make<SEListener>();
  const std::vector<uint8_t> sbAppletAID = {0xA0, 0x00, 0x00, 0x00, 0x62};
  uint8_t retry = 0;
  do {
    auto res = session->openLogicalChannel(mSelectableAid, 0x00, mSEListener,
                                           &channel);

    if ((mSelectableAid == sbAppletAID) &&
        (!res.isOk() || (channel == nullptr))) {
      res = session->openLogicalChannel(mSelectableAid, 0x02, mSEListener,
                                        &channel);
      if (!res.isOk() || (channel == nullptr)) {
        LOG(INFO) << " retry openLogicalChannel after 2 secs";
        usleep(2 * ONE_SEC);
        continue;
      }
    }
    return res.isOk();
  } while (++retry < MAX_RETRY_COUNT);

  return false;
}

#endif

}  // namespace keymint::javacard
#endif // OMAPI_TRANSPORT
