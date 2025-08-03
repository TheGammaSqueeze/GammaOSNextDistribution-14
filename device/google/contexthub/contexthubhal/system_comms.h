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

#ifndef _NANOHUB_SYSTEM_COMMS_H_
#define _NANOHUB_SYSTEM_COMMS_H_

#include <utils/Condition.h>

#include <chrono>
#include <condition_variable>
#include <map>
#include <mutex>
#include <vector>

#include <hardware/context_hub.h>
#include <nanohub/nanohub.h>

#include "nanohubhal.h"
#include "message_buf.h"

//rx: return 0 if handled, > 0 if not handled, < 0 if error happened

#define MSG_HANDLED 0

//messages to the HostIf nanoapp & their replies (mesages and replies both begin with u8 message_type)
#define NANOHUB_HAL_APP_MGMT      0x10 // (char cmd, u64 appId, u64 appMsk) -> (int errno, u32 results)

#define NANOHUB_HAL_APP_MGMT_START      0
#define NANOHUB_HAL_APP_MGMT_STOP       1
#define NANOHUB_HAL_APP_MGMT_UNLOAD     2
#define NANOHUB_HAL_APP_MGMT_DELETE     3

#define NANOHUB_HAL_SYS_MGMT      0x11 // (char cmd) -> (int errno)

#define NANOHUB_HAL_SYS_MGMT_ERASE      0
#define NANOHUB_HAL_SYS_MGMT_REBOOT     1

#define NANOHUB_HAL_APP_INFO      0x12

#define NANOHUB_HAL_APP_INFO_APPID          0x00
#define NANOHUB_HAL_APP_INFO_CRC            0x01
#define NANOHUB_HAL_APP_INFO_TID            0x02
#define NANOHUB_HAL_APP_INFO_VERSION        0x03
#define NANOHUB_HAL_APP_INFO_ADDR           0x04
#define NANOHUB_HAL_APP_INFO_SIZE           0x05
#define NANOHUB_HAL_APP_INFO_HEAP           0x06
#define NANOHUB_HAL_APP_INFO_DATA           0x07
#define NANOHUB_HAL_APP_INFO_BSS            0x08
#define NANOHUB_HAL_APP_INFO_CHRE_MAJOR     0x09
#define NANOHUB_HAL_APP_INFO_CHRE_MINOR     0x0A
#define NANOHUB_HAL_APP_INFO_END            0xFF

#define NANOHUB_HAL_SYS_INFO      0x13

#define NANOHUB_HAL_SYS_INFO_HEAP_FREE      0x0F
#define NANOHUB_HAL_SYS_INFO_RAM_SIZE       0x12
#define NANOHUB_HAL_SYS_INFO_EEDATA_SIZE    0x13
#define NANOHUB_HAL_SYS_INFO_EEDATA_FREE    0x14
#define NANOHUB_HAL_SYS_INFO_CODE_SIZE      0x15
#define NANOHUB_HAL_SYS_INFO_CODE_FREE      0x16
#define NANOHUB_HAL_SYS_INFO_SHARED_SIZE    0x17
#define NANOHUB_HAL_SYS_INFO_SHARED_FREE    0x18
#define NANOHUB_HAL_SYS_INFO_END            0xFF

#define NANOHUB_HAL_KEY_INFO      0x14
#define NANOHUB_HAL_START_UPLOAD  0x16
#define NANOHUB_HAL_CONT_UPLOAD   0x17
#define NANOHUB_HAL_FINISH_UPLOAD 0x18

#define NANOHUB_HAL_UPLOAD_ACCEPTED         0
#define NANOHUB_HAL_UPLOAD_WAIT             1
#define NANOHUB_HAL_UPLOAD_RESEND           2
#define NANOHUB_HAL_UPLOAD_RESTART          3
#define NANOHUB_HAL_UPLOAD_CANCEL           4
#define NANOHUB_HAL_UPLOAD_CANCEL_NO_RETRY  5
#define NANOHUB_HAL_UPLOAD_NO_SPACE         6

#define NANOHUB_APP_NOT_LOADED  (-1)
#define NANOHUB_APP_LOADED      (0)

#define NANOHUB_UPLOAD_CHUNK_SZ_MAX 64
#define NANOHUB_MEM_SZ_UNKNOWN      0xFFFFFFFFUL
#define NANOHUB_TID_UNKNOWN         0xFFFFFFFFUL

#define CONTEXT_HUB_START_APPS      8

namespace android {

namespace nanohub {

int system_comms_handle_rx(const nano_message_raw *msg);
int system_comms_handle_tx(const hub_message_t *outMsg);

struct MgmtStatus {
    union {
        uint32_t value;
        struct {
            uint8_t app;
            uint8_t task;
            uint8_t op;
            uint8_t erase;
        } __attribute__((packed));
    };
} __attribute__((packed));

struct NanohubMemInfo {
    //sizes
    uint32_t flashSz, blSz, osSz, sharedSz, eeSz;
    uint32_t ramSz;

    //use
    uint32_t blUse, osUse, sharedUse, eeUse;
    uint32_t ramUse;
} __attribute__((packed));

struct NanohubRsp {
    uint32_t mCmd;
    uint32_t mTransactionId;
    int32_t mStatus;
    explicit NanohubRsp(MessageBuf &buf, uint32_t transactionId, bool chre);
};

inline bool operator == (const hub_app_name_t &a, const hub_app_name_t &b) {
    return a.id == b.id;
}

inline bool operator != (const hub_app_name_t &a, const hub_app_name_t &b) {
    return !(a == b);
}

class SystemComm {
private:

    class AppManager;

    /*
     * Nanohub HAL sessions
     *
     * Session is an object that can group several message exchanges with FW,
     * maintain state, and be waited for completion by someone else.
     *
     * As of this moment, since all sessions are triggered by client thread,
     * and all the exchange is happening in local worker thread, it is only possible
     * for client thread to wait on session completion.
     * Allowing sessions to wait on each other will require a worker thread pool.
     * It is now unnecessary, and not implemented.
     */
    class ISession {
    public:
        virtual int setup(const hub_message_t *app_msg, uint32_t transactionId, AppManager &appManager) = 0;
        virtual int handleRx(MessageBuf &buf, uint32_t transactionId, AppManager &appManager, bool chre) = 0;
        virtual int getState() const = 0; // FSM state
        virtual int getStatus() const = 0; // execution status (result code)
        virtual void abort(int32_t) = 0;
        virtual ~ISession() {}
    };

    class SessionManager;

    class Session : public ISession {
        friend class SessionManager;

        mutable std::mutex mDoneMutex; // controls condition and state transitions
        std::condition_variable mDoneCond;
        volatile int mState;

    protected:
        mutable std::mutex mLock; // serializes message handling
        int32_t mStatus;

        enum {
            SESSION_INIT = 0,
            SESSION_DONE = 1,
            SESSION_USER = 2,
        };

        void complete() {
            std::unique_lock<std::mutex> lk(mDoneMutex);
            if (mState != SESSION_DONE) {
                mState = SESSION_DONE;
                lk.unlock();
                mDoneCond.notify_all();
            }
        }
        void abort(int32_t status) {
            std::lock_guard<std::mutex> _l(mLock);
            mStatus = status;
            complete();
        }
        void setState(int state) {
            if (state == SESSION_DONE) {
                complete();
            } else {
                std::lock_guard<std::mutex> _l(mDoneMutex);
                mState = state;
            }
        }
    public:
        Session() { mState = SESSION_INIT; mStatus = -1; }
        int getStatus() const {
            std::lock_guard<std::mutex> _l(mLock);
            return mStatus;
        }
        int wait() {
            std::unique_lock<std::mutex> lk(mDoneMutex);
            bool success = mDoneCond.wait_for(
                    lk, std::chrono::seconds(30),
                    [this] { return mState == SESSION_DONE; });
            if (!success) {
                ALOGE("Timed out waiting for response");
            }
            return success ? 0 : -1;
        }
        virtual int getState() const override {
            std::lock_guard<std::mutex> _l(mDoneMutex);
            return mState;
        }
        virtual bool isDone() const {
            std::lock_guard<std::mutex> _l(mDoneMutex);
            return mState == SESSION_DONE;
        }
        virtual bool isRunning() const {
            std::lock_guard<std::mutex> _l(mDoneMutex);
            return mState > SESSION_DONE;
        }
    };

    class AppMgmtSession : public Session {
        enum {
            TRANSFER = SESSION_USER,
            QUERY_START,
            START,
            STOP_TRANSFER,
            FINISH,
            RUN,
            STOP_RUN,
            RUN_FAILED,
            REBOOT,
            ERASE_TRANSFER,
            MGMT,
            INFO,
        };
        uint32_t mCmd; // LOAD_APP, UNLOAD_APP, ENABLE_APP, DISABLE_APP
        uint32_t mResult;
        std::vector<uint8_t> mData;
        uint32_t mLen;
        uint32_t mPos;
        uint32_t mNextPos;
        uint32_t mErrCnt;
        hub_app_name_t mAppName;
        uint32_t mFlashAddr;
        std::vector<hub_app_name_t> mAppList;

        int setupMgmt(const hub_message_t *appMsg, uint32_t transactionId, uint32_t cmd, AppManager &appManager);
        int handleTransfer(NanohubRsp &rsp, MessageBuf &, AppManager &appManager);
        int handleStopTransfer(NanohubRsp &rsp, MessageBuf &buf, AppManager &);
        int handleQueryStart(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager);
        int handleStart(NanohubRsp &rsp, MessageBuf &buf, AppManager &);
        int handleFinish(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager);
        int handleRun(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager);
        int handleStopRun(NanohubRsp &rsp, MessageBuf &buf, AppManager &);
        int handleReboot(NanohubRsp &rsp, MessageBuf &buf, AppManager &);
        int handleEraseTransfer(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager);
        int handleMgmt(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager);
        int handleInfo(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager);
    public:
        AppMgmtSession() {
            mCmd = 0;
            mResult = 0;
            mPos = 0;
            mLen = 0;
            memset(&mAppName, 0, sizeof(mAppName));
        }
        virtual int handleRx(MessageBuf &buf, uint32_t transactionId, AppManager &appManager, bool chre) override;
        virtual int setup(const hub_message_t *app_msg, uint32_t transactionId, AppManager &appManager) override;
    };

    class MemInfoSession : public Session {
    public:
        virtual int setup(const hub_message_t *app_msg, uint32_t transactionId, AppManager &) override;
        virtual int handleRx(MessageBuf &buf, uint32_t transactionId, AppManager &, bool chre) override;
    };

    class KeyInfoSession  : public Session {
        std::vector<uint8_t> mRsaKeyData;
        uint32_t mKeyNum;
        uint32_t mKeyOffset;
        int requestRsaKeys(uint32_t transactionId);
    public:
        virtual int setup(const hub_message_t *, uint32_t, AppManager &) override;
        virtual int handleRx(MessageBuf &buf, uint32_t transactionId, AppManager &, bool chre) override;
        bool haveKeys() const {
            std::lock_guard<std::mutex> _l(mLock);
            return mRsaKeyData.size() > 0 && !isRunning();
        }
    };

    class AppManager {
        struct AppData {
            uint32_t version, flashUse, ramUse;
            uint32_t tid, crc, flashAddr;
            uint8_t chre_major, chre_minor;
            bool chre, running, loaded;

            bool cached_start, cached_napp;
            uint32_t cached_version, cached_crc;
        };

        typedef std::map<uint64_t, std::unique_ptr<AppData>> AppMap;

        AppMap apps_;

    public:
        AppManager() {
            restoreApps();
        }
        void dumpAppInfo(std::string &result);
        bool saveApps();
        bool restoreApps();
        bool eraseApps();
        bool writeApp(hub_app_name_t &appName, const uint8_t *data, int32_t len);
        int32_t readApp(hub_app_name_t &appName, void **data);
        bool cmpApp(hub_app_name_t &appName, const uint8_t *data, uint32_t len);
        uint32_t readNanohubAppInfo(MessageBuf &buf);
        void sendAppInfoToApp(uint32_t transactionId);
        int getAppsToStart(std::vector<hub_app_name_t> &apps);
        bool setCachedCrc(hub_app_name_t &appName, uint32_t crc) {
            if (!isAppPresent(appName))
                return false;
            else {
                apps_[appName.id]->cached_napp = true;
                apps_[appName.id]->cached_crc = crc;
                apps_[appName.id]->cached_start = false;
                saveApps();
                return true;
            }
        }
        bool clearCachedApp(hub_app_name_t &appName) {
            if (!isAppPresent(appName))
                return false;
            else {
                apps_[appName.id]->cached_napp = false;
                apps_[appName.id]->cached_start = false;
                saveApps();
                return true;
            }
        }
        bool clearRunning(hub_app_name_t &appName) {
            if (!isAppLoaded(appName))
                return false;
            else {
                apps_[appName.id]->running = false;
                return true;
            }
        }

        bool setCachedVersion(hub_app_name_t &appName, uint32_t version) {
            if (!isAppPresent(appName))
                return false;
            else {
                apps_[appName.id]->cached_version = version;
                return true;
            }
        }
        bool setCachedStart(hub_app_name_t &appName, bool start) {
            if (!isAppPresent(appName))
                return false;
            else {
                apps_[appName.id]->cached_start = start;
                saveApps();
                return true;
            }
        }
        bool addNewApp(hub_app_name_t &appName, uint32_t version) {
            if (isAppLoaded(appName))
                return false;
            else
                apps_[appName.id] = std::unique_ptr<AppData>(new AppData);
            apps_[appName.id]->loaded = false;
            apps_[appName.id]->running = false;
            apps_[appName.id]->chre = false;
            apps_[appName.id]->cached_napp = false;
            apps_[appName.id]->cached_version = version;
            return true;
        }
        bool isAppPresent(hub_app_name_t &appName) {
            return apps_.count(appName.id) != 0;
        }
        bool isAppLoaded(hub_app_name_t &appName) {
            return apps_.count(appName.id) != 0 && apps_[appName.id]->loaded;
        }
        bool isAppRunning(hub_app_name_t &appName) {
            return apps_.count(appName.id) != 0 && apps_[appName.id]->running;
        }
        uint32_t getFlashAddr(hub_app_name_t &appName) {
            if (isAppPresent(appName))
                return apps_[appName.id]->flashAddr;
            else
                return 0xFFFFFFFF;
        }
    };

    class SessionManager {
        typedef std::map<int, Session* > SessionMap;

        std::mutex lock;
        SessionMap sessions_;

        bool isActive(const SessionMap::iterator &pos) const
        {
            return !pos->second->isDone();
        }
        void next(SessionMap::iterator &pos)
        {
            isActive(pos) ? pos++ : pos = sessions_.erase(pos);
        }

    public:
        int handleRx(MessageBuf &buf, uint32_t transactionId, AppManager &appManager, bool chre, bool &reboot, uint32_t &rebootStatus);
        int setup_and_add(int id, Session *session, const hub_message_t *appMsg, uint32_t transactionId, AppManager &appManager);
    } mSessions;

    const hub_app_name_t mHostIfAppName = {
        .id = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0)
    };

    static SystemComm *getSystem() {
        // this is thread-safe in c++11
        static SystemComm theInstance;
        return &theInstance;
    }

    SystemComm () = default;
    ~SystemComm() = default;

    int doHandleTx(const hub_message_t *txMsg, uint32_t transactionId);
    int doHandleRx(uint64_t appId, uint32_t transactionId, const char *data, int len, bool chre);
    void doDumpAppInfo(std::string &result);

    int doHandleRx(const nano_message_raw *rxMsg) {
        return doHandleRx(rxMsg->hdr.appId, 0, reinterpret_cast<const char*>(rxMsg->data), rxMsg->hdr.len, false);
    }

    int doHandleRx(const nano_message_chre *rxMsg) {
        return doHandleRx(rxMsg->hdr.appId, rxMsg->hdr.appEventId, reinterpret_cast<const char*>(rxMsg->data), rxMsg->hdr.len, true);
    }

    static void sendToApp(uint32_t typ, uint32_t transactionId, const void *data, uint32_t len) {
        if (NanoHub::messageTracingEnabled()) {
            dumpBuffer("HAL -> APP", get_hub_info()->os_app_name, transactionId, 0, data, len);
        }
        NanoHub::sendToApp(HubMessage(&get_hub_info()->os_app_name, typ, transactionId, ENDPOINT_BROADCAST, data, len));
    }
    static int sendToSystem(const void *data, size_t len, uint32_t transactionId);

    KeyInfoSession mKeySession;
    AppMgmtSession mAppMgmtSession;
    MemInfoSession mMemInfoSession;
    AppManager     mAppManager;

public:
    static int handleTx(const hub_message_t *txMsg, uint32_t transactionId) {
        return getSystem()->doHandleTx(txMsg, transactionId);
    }
    static int handleRx(const nano_message_raw *rxMsg) {
        return getSystem()->doHandleRx(rxMsg);
    }
    static int handleRx(const nano_message_chre *rxMsg) {
        return getSystem()->doHandleRx(rxMsg);
    }
    static void dumpAppInfo(std::string &result) {
        return getSystem()->doDumpAppInfo(result);
    }
};

}; // namespace nanohub

}; // namespace android

#endif
